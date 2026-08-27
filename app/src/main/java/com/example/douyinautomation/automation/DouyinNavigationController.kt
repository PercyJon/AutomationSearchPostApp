package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deliberately bounded state machine. M2 inspects and navigates to each verified direct-message
 * page, submits one ASCII space as a non-delivery safety probe, and advances only after Douyin's
 * blank-message notice is observed. Per-user messaging failures are recorded and skipped after
 * bounded retries; only risk, verification, login, and other task-level safety conditions require
 * manual handoff.
 */
class DouyinNavigationController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val logger: DiagnosticLogger,
    private val inspector: NodeTreeInspector,
    private val pageDetector: PageDetector,
    private val selector: SelectorEngine,
    private val gestures: GestureEngine,
    private val screenshotCapture: ScreenshotCapture,
    @Volatile private var ocr: MlKitOcrEngine?,
) {
    private val mutex = Mutex()
    private val windowContextReader = DouyinWindowContextReader(service, inspector)
    /** P0-only visual evidence source; it is bounded to the supplied comment bubble asset. */
    private val commentIconTemplateMatcher: ImageMatcher = VerifiedTemplateMatcher(
        provider = AlphaMaskedCommentIconMatcher(service.assets),
        minimumConfidence = COMMENT_ICON_TEMPLATE_MIN_CONFIDENCE,
    )
    /** P0-only visual evidence source for the supplied like and collect action-rail anchors. */
    private val actionRailAnchorTemplateMatcher: ImageMatcher = VerifiedTemplateMatcher(
        provider = AlphaMaskedActionIconMatcher(service.assets),
        minimumConfidence = ACTION_RAIL_ANCHOR_TEMPLATE_MIN_CONFIDENCE,
    )
    /** Opaque comment-author references persisted with the active task checkpoint. */
    private val processedCommentCandidateFingerprints = LinkedHashSet<String>()
    /** Isolated P4-B comment-surface runner; the existing profile runner remains unchanged. */
    private val commentRuntime = CommentPrivateMessageRuntime(
        service = service,
        scope = scope,
        logger = logger,
        inspector = inspector,
        pageDetector = pageDetector,
        selector = selector,
        gestures = gestures,
        enrichWithOcr = { base ->
            captureContextWithOcr(
                base = base,
                tag = "comment_next_video_rail",
                region = OcrRegion.VIDEO_ACTION_RAIL,
            ) ?: base
        },
        restoredCommentCandidateFingerprints = { processedCommentCandidateFingerprints.toSet() },
        onCommentCandidateProcessed = { fingerprint ->
            if (processedCommentCandidateFingerprints.add(fingerprint)) {
                persistTaskCheckpoint()
            }
        },
        onTerminal = { terminal ->
            // Runtime timeouts happen on its watchdog coroutine. Queue terminal handling so the
            // controller's single action mutex remains the only owner of task lifecycle fields.
            scope.launch { mutex.withLock { finishCommentRuntime(terminal) } }
        },
    )
    /** Private-message phase orchestration; concrete effects remain controller-owned callbacks. */
    private val privateMessageFlow = PrivateMessageFlow(
        completeAtMessagePage = { completeAtMessagePage() },
        completeEmptyProbe = { completeEmptyMessageProbe() },
        skipRestricted = { skipRestrictedUser("Douyin requires following before private messaging") },
        skipDirectMessageFailure = {
            skipMessageSendFailure("Douyin rejected the message because of the recipient's messaging settings")
        },
        scheduleEntryPostcondition = { scheduleMessageEntryPostconditionCheck() },
        skipMessageResultFailure = {
            skipMessageSendFailure("Douyin rejected the message because of the recipient's messaging settings")
        },
        pauseMessageResultRisk = {
            pause("A verification or risk screen appeared after sending; manual handoff required")
        },
        skipEmptyProbeFailure = {
            skipMessageSendFailure("The blank-message probe was rejected by the recipient's messaging settings")
        },
        pauseEmptyProbeRisk = {
            pause("A verification or risk screen appeared during the blank-message probe; manual handoff required")
        },
    )
    /** Search-stage action dispatch; concrete search effects remain controller-owned callbacks. */
    private val searchFlow = SearchFlow(
        openSearch = ::openSearch,
        recoverInitialSurface = { context -> recoverInitialSurface(context, requireHome = true) },
        enterKeyword = ::enterKeyword,
        reuseResultsQuery = { context -> reuseSearchResultsQueryField(context, "search_entry_wait") },
        selectUserTab = ::selectUserTab,
        selectVisibleUser = { context -> selectVisibleUser(context) },
    )
    /** User-result terminal and continuation dispatch; row processing remains controller-owned. */
    private val userSelectionFlow = UserSelectionFlow(
        completeAtUserLimit = { maxUsers -> completeTaskAtUserLimit(maxUsers) },
        resumeRemoteAnchor = { context -> resumeRemoteTaskFromAnchor(context) },
        handleAccountHelpEnd = { handleAccountHelpOnlyEnd() },
    )
    /** Bounded timeout recovery dispatch; concrete recovery effects remain controller-owned. */
    private val recoveryFlow = RecoveryFlow(
        waitForStartupAd = { marker, timeoutDescription ->
            waitForStartupAdTimeoutRecovery(marker, timeoutDescription)
        },
        retryKeywordPreservingBudget = { context -> retryKeywordAfterSearchResultsTimeout(context) },
        enterKeyword = ::enterKeyword,
        reuseSearchEntryQuery = { context ->
            reuseSearchResultsQueryField(context, "search_entry_timeout_recovery")
        },
        openSearch = ::openSearch,
        reuseHomeQuery = { context -> reuseSearchResultsQueryField(context, "home_timeout_recovery") },
        recoverInitialSurface = { context -> recoverInitialSurface(context, requireHome = true) },
        selectVisibleUser = { context -> selectVisibleUser(context) },
        selectUserTab = ::selectUserTab,
        openPrivateMessage = ::openPrivateMessage,
        skipProfileRecoveryFailure = { page -> skipProfileRecoveryFailure(page) },
        pauseTimeout = { reason -> pause(reason) },
    )
    private var phase = AutomationPhase.IDLE
    @Volatile private var taskActive = false
    private var keyword: String? = null
    private var activeTaskSnapshot: TaskSnapshot? = null
    private var taskQueryIndex: Int = 0
    /** Logical result-page number used only for the optional B3 remote checkpoint. */
    private var remotePageNumber: Int = 1
    /** A remote task with existing progress must locate this anchor before selecting any row. */
    private var remoteResumePending = false
    private var remoteResumeAnchor: UserResultIdentity? = null
    private var remoteResumeTargetPageNumber: Int? = null
    private var remoteResumeMaxSwipes = 0
    /** True when an await helper already advanced/finished the task and its caller must return. */
    private var queryTransitionHandled = false
    private var pendingStartMessage: String = ""
    private var pendingSafetyProbe: Boolean = true
    private var pausedPhase: AutomationPhase? = null
    private var timeoutJob: Job? = null
    private var initialObservationJob: Job? = null
    private var messageEntryPostconditionJob: Job? = null
    private var messageResultJob: Job? = null
    private var profilePostconditionJob: Job? = null
    /** Pending local tasks are consumed only after the current task reaches a terminal state. */
    private val queuedTaskSnapshots = SequentialTaskQueue<TaskSnapshot>()
    /** Durable contract for the active local batch; null for legacy and remote single-task runs. */
    private var localTaskQueueSession: LocalTaskQueueSession? = null
    private var initialOcrAttempts = 0
    /** Bounded FULL-frame OCR used only to confirm a leftover comment sheet at launch. */
    private var nestedCommentSurfaceOcrAttempts = 0
    /** Bounded nav-band OCR used only to classify comment-task launch HOME; never taps search. */
    private var commentLaunchHomeNavOcrAttempts = 0
    /** True from normal target launch until initial HOME/search evidence is positively classified. */
    private var initialHomeClassificationPending = false
    /** One sanitized geometry dump per WAITING_FOR_HOME UNKNOWN session; never includes node text. */
    private var homeUnknownGeometryDumpSaved = false
    /** Number of bounded blind BACK actions used while the target tree is temporarily unavailable. */
    private var initialBlindBackAttempts = 0
    private var latestContext: ScreenContext? = null
    /**
     * Last target-app snapshot that contained OCR blocks.  On some Douyin/OEM builds a
     * node-only callback immediately follows the OCR-enriched callback and would otherwise
     * replace the only snapshot that proves the current screen is a profile.
     */
    private var latestOcrContext: ScreenContext? = null
    private var userTabRevealAttempts = 0
    private var restrictedUserSkips = 0
    private var timeoutRecoveryAttempts = 0
    /** Bottom edge of the row currently being processed, used to pick the next visible row. */
    private var lastProcessedUserAnchorBottom: Float? = null
    /** Row identities already handled in this run; this survives small overlapping page swipes. */
    private val processedUserIdentities = LinkedHashSet<String>()
    /** Opaque SHA-256 references restored from durable checkpoints after process recreation. */
    private val processedIdentityFingerprints = LinkedHashSet<String>()
    /** Compatibility-only references from checkpoints written before SHA-256 migration. */
    private val legacyProcessedIdentityHashes = LinkedHashSet<Int>()
    /** Rich identity aliases are retained so OCR punctuation/spacing drift cannot reopen a row. */
    private val processedUserIdentityRecords = ArrayList<UserResultIdentity>()
    /** Fingerprint of the currently selected row, used to finish its task audit record. */
    private var currentUserIdentityFingerprint: String? = null
    /** List-row name retained as a hint when the profile header must replace a clipped label. */
    private var currentUserDisplayName: String? = null
    /** Source of the list-row identity; OCR-backed rows receive an additional profile check. */
    private var currentUserDisplayNameSource: UserResultIdentity.Source? = null
    private var cachedUserResultsViewportSignature: String? = null
    private var cachedUserResultsOcrBlocks: List<OcrTextBlock> = emptyList()
    /** Signature captured before the latest vertical swipe; used to reject a stale first tree. */
    private var userResultsSignatureBeforeSwipe: String? = null
    /** OCR-backed page transitions require two matching observations before an action is allowed. */
    private var lastOcrPageSignature: String? = null
    private var ocrPageStableObservations: Int = 0
    /**
     * A comment task reaches its source profile through the ordinary search/user-tab route, then
     * hands control to [commentRuntime]. Keep that handoff explicit so a stale task snapshot can
     * never silently leave the runner polling a profile without receiving it.
     */
    private var commentProfileHandoffObserved = false
    /**
     * Search-target mode must ignore a queued profile snapshot from before the selected result
     * was tapped. CURRENT_PROFILE does not use this barrier because its current page is the
     * operator's explicit target.
     */
    private var commentProfileHandoffNotBeforeMillis: Long = 0L
    /**
     * Search-entry comment tasks deliberately defer the isolated comment runtime until the
     * source profile has actually been reached.  The ordinary search/User-tab controller is
     * responsible for that first leg, so a service rebind on a result page can never spend the
     * comment runner's profile watchdog before there is a profile for it to inspect.
     */
    private var pendingCommentRuntimeSnapshot: CommentPrivateMessageSnapshot? = null

    fun installOcrEngine(engine: MlKitOcrEngine?) {
        ocr = engine
    }

    /** True only while an explicitly started POC run is waiting for or performing a step. */
    fun shouldUseOcrFallback(): Boolean = taskActive && ocr != null

    /**
     * A CURRENT_PROFILE task can enter an already-open ordinary video through a verified
     * comment action rail.  That structural proof is sufficient for the existing handoff and
     * is deliberately stronger than a generic UNKNOWN-page OCR fallback.  Avoid holding the
     * first accessibility event behind a full-screen OCR pass when the same strict acceptance
     * check would immediately open the comment sheet anyway.
     */
    fun shouldBypassOcrForCurrentProfileCommentEntry(context: ScreenContext): Boolean {
        val commentConfig = activeTaskSnapshot?.commentConfig ?: return false
        if (!taskActive ||
            phase != AutomationPhase.WAITING_FOR_PROFILE ||
            commentConfig.entryMode != CommentPrivateMessageEntryMode.CURRENT_PROFILE
        ) {
            return false
        }
        return isCurrentCommentEntrySurface(
            context = context,
            detection = pageDetector.detect(context),
            commentConfig = commentConfig,
        )
    }

    /**
     * Returning from a commenter DM only needs node proof of the comment sheet. A full-screen
     * UNKNOWN page_probe during that wait blocks the next tree sample without changing BACK
     * count or sheet confirmation.
     */
    fun shouldBypassOcrForCommentSurfaceReturn(): Boolean =
        taskActive &&
            CommentReturnBackPolicy.shouldBypassUnknownPageOcrDuringReturn(
                awaitingCommentSurfaceReturn = commentRuntime.isAwaitingCommentSurfaceReturn,
            )

    /** True while the M2 blank-message result is still being awaited, regardless of OCR state. */
    fun shouldProbeEmptyMessageResult(): Boolean =
        taskActive && phase == AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT

    /** True while the comment runtime is awaiting its own blank-message safety probe result. */
    fun shouldProbeCommentBlankMessage(): Boolean = taskActive && commentRuntime.isProbingBlankMessage

    /**
     * Some profile chats expose no editable Accessibility node.  During the bounded entry
     * post-condition, allow the service to take a throttled OCR sample so the chat is not
     * mistaken for the still-visible profile.
     */
    fun shouldProbePrivateMessageEntryWithOcr(): Boolean =
        taskActive && phase == AutomationPhase.WAITING_FOR_DIRECT_MESSAGE && ocr != null

    /** M2 also needs OCR while a transient blank-message toast overlays a valid chat page. */
    fun shouldProbeEmptyMessageWithOcr(): Boolean =
        shouldProbeEmptyMessageResult() && ocr != null

    /**
     * Handles text carried by a transient Accessibility notification (usually Douyin's toast).
     * Toasts do not necessarily change the node tree, so this path must be independent of the
     * normal window inspection loop. The text is kept in-memory only and is never written to the
     * diagnostic log.
     */
    suspend fun onTransientAccessibilityText(values: List<String>) {
        if (values.isEmpty()) return
        // The comment runtime runs its own blank-message probe with a transient toast confirmation
        // and must receive the event even though the classic phase is not WAITING_FOR_EMPTY_MESSAGE_RESULT.
        // Forward lock-free: the probe polls while this controller's mutex is held by onScreenObserved
        // (handoffCommentProfileObservation -> commentRuntime.onObserved -> probeBlankMessage), so a
        // mutex-guarded forward would deadlock and never deliver the toast.
        if (taskActive && commentRuntime.isProbingBlankMessage) {
            commentRuntime.onTransientAccessibilityText(values)
            if (phase != AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT) return
        }
        mutex.withLock {
            if (!taskActive || phase != AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT) return@withLock
            val base = latestContext ?: currentWindowContext() ?: return@withLock
            val transientContext = base.copy(
                ocrBlocks = values.map { value -> OcrTextBlock(text = value) },
                capturedAtMillis = System.currentTimeMillis(),
            )
            val detection = pageDetector.detect(transientContext)
            logger.info(
                "empty_message_probe_accessibility_event",
                attributes = mapOf("page" to detection.kind.name, "signals" to values.size),
            )
            when (detection.kind) {
                PageKind.MESSAGE_EMPTY_REJECTED -> completeEmptyMessageProbe()
                PageKind.HUMAN_INTERVENTION -> pause("A verification or risk screen appeared during the blank-message probe; manual handoff required")
                PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                    "The blank-message probe was rejected by the recipient's messaging settings",
                )
                else -> Unit
            }
        }
    }

    suspend fun handle(command: AutomationCommand) = mutex.withLock {
        when (command) {
            is AutomationCommand.Start -> {
                queuedTaskSnapshots.clear()
                localTaskQueueSession = null
                AutomationStore.clearLocalTaskQueueSession()
                start(
                    searchKeyword = command.keyword,
                    startMessage = command.message,
                    safetyProbe = command.safetyProbe,
                    taskSnapshot = command.taskSnapshot,
                    remoteResume = command.remoteResume,
                    suspendedBeforeStart = command.suspendedBeforeStart,
                )
            }
            is AutomationCommand.StartBatch -> startBatch(command.tasks)
            AutomationCommand.ResumeSavedTask -> resumeSavedTask()
            is AutomationCommand.SendMessage -> sendMessageOnce(command.message)
            AutomationCommand.Pause -> pause("Paused by the operator")
            AutomationCommand.Resume -> resume()
            AutomationCommand.Stop -> stop()
            AutomationCommand.CaptureDiagnostics -> captureDiagnosticsInternal("operator_request")
            AutomationCommand.DumpNodeTree -> dumpNodeTreeInternal("operator_request")
        }
    }

    suspend fun onScreenObserved(context: ScreenContext, detection: PageDetection) = mutex.withLock {
        AutomationStore.publishObservation(detection)

        // A deliberately suspended CURRENT_PROFILE task must not act on navigation events, but
        // it still needs the freshest target-app snapshot when the operator later presses the
        // overlay's explicit Resume action.  In particular, an expanded application overlay can
        // briefly shadow rootInActiveWindow at that moment on some OEM builds.  Retaining this
        // read-only Douyin snapshot lets resume validate the already-visible normal video rather
        // than asking the operator to press Resume a second time.  It is revalidated (and aged
        // out) before any comment action is dispatched.
        if (phase == AutomationPhase.SUSPENDED_BEFORE_START &&
            detection.kind != PageKind.OUTSIDE_TARGET &&
            context.packageName == TargetAppLauncher.DOUYIN_PACKAGE
        ) {
            latestContext = context
            if (context.ocrBlocks.isNotEmpty() || detection.reasons.any { it.contains("OCR", ignoreCase = true) }) {
                latestOcrContext = context
            }
            return
        }
        if (!taskActive || detection.kind == PageKind.OUTSIDE_TARGET) return
        // Do not replace the last Douyin context when the diagnostics activity briefly becomes
        // the active window. This keeps operator-requested node dumps useful while the target app
        // remains underneath the translucent diagnostics surface.
        latestContext = context
        if (context.ocrBlocks.isNotEmpty() || detection.reasons.any { it.contains("OCR", ignoreCase = true) }) {
            latestOcrContext = context
        }
        // A suspended comment task must not react to the accessibility events generated while the
        // operator navigates to the intended profile. Only the explicit overlay Resume action may
        // hand the verified profile to the comment runtime.
        if (phase == AutomationPhase.SUSPENDED_BEFORE_START) return
        var nestedSurfaceContext = context
        val nodeDetectedSheet = CommentSurfaceDetector.detect(nestedSurfaceContext).isCommentSurface
        if (
            NestedLaunchSurfacePolicy.shouldProbeCommentSurfaceOcr(
                phase = phase,
                pageIsUnknown = detection.kind == PageKind.UNKNOWN,
                nodeDetectedSheet = nodeDetectedSheet,
                hasOcrBlocks = nestedSurfaceContext.ocrBlocks.isNotEmpty(),
                attempts = nestedCommentSurfaceOcrAttempts,
                maxAttempts = TuningConstants.NavigationLifecycle.NESTED_COMMENT_SURFACE_OCR_MAX_ATTEMPTS,
            )
        ) {
            nestedCommentSurfaceOcrAttempts++
            nestedSurfaceContext = captureContextWithOcr(
                nestedSurfaceContext,
                "nested_comment_surface",
                OcrRegion.FULL,
            ) ?: nestedSurfaceContext
            logger.info(
                "initial_nested_comment_surface_ocr",
                attributes = mapOf(
                    "attempt" to nestedCommentSurfaceOcrAttempts,
                    "ocr_blocks" to nestedSurfaceContext.ocrBlocks.size,
                    "sheet" to CommentSurfaceDetector.detect(nestedSurfaceContext).isCommentSurface,
                ),
            )
        }
        val commentSurface = CommentSurfaceDetector.detect(nestedSurfaceContext)
        if (
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = phase,
                isCommentSurface = commentSurface.isCommentSurface,
            )
        ) {
            logger.info(
                "initial_nested_comment_surface_recovery",
                message = "A verified comment sheet is open; using bounded BACK instead of treating the launch surface as unclassified home",
                attributes = mapOf(
                    "confidence" to commentSurface.confidence,
                    "reason_count" to commentSurface.reasons.size,
                    "ocr_blocks" to nestedSurfaceContext.ocrBlocks.size,
                ),
            )
            recoverInitialSurface(
                context,
                requireHome = true,
                suppressSearchUntilBack = NestedLaunchSurfacePolicy.shouldForceInitialBack(
                    ocrConfirmedSheet = commentSurface.isCommentSurface,
                    nodeDetectedSheet = nodeDetectedSheet,
                ),
            )
            return
        }
        var effectiveDetection = detection
        var homeNavContext = nestedSurfaceContext
        var acceptedByCommentHomeNavOcr = false
        if (
            CommentLaunchHomeOcrPolicy.shouldClassify(
                isCommentTask = isCommentPrivateMessageTask(),
                phase = phase,
                pageIsUnknown = effectiveDetection.kind == PageKind.UNKNOWN,
                isCommentSurface = commentSurface.isCommentSurface,
            )
        ) {
            if (
                CommentLaunchHomeOcrPolicy.shouldProbe(
                    hasOcrBlocks = homeNavContext.ocrBlocks.isNotEmpty(),
                    attempts = commentLaunchHomeNavOcrAttempts,
                    maxAttempts = TuningConstants.NavigationLifecycle.COMMENT_LAUNCH_HOME_NAV_OCR_MAX_ATTEMPTS,
                )
            ) {
                commentLaunchHomeNavOcrAttempts++
                homeNavContext = captureContextWithOcr(
                    homeNavContext,
                    "comment_launch_home_nav",
                    OcrRegion.FULL,
                ) ?: homeNavContext
            }
            val homeHits = CommentLaunchHomeOcrPolicy.bandHits(homeNavContext)
            val homeDetection = CommentLaunchHomeOcrPolicy.classify(homeNavContext)
            logger.info(
                "initial_comment_home_nav_ocr",
                attributes = mapOf(
                    "attempt" to commentLaunchHomeNavOcrAttempts,
                    "ocr_blocks" to homeNavContext.ocrBlocks.size,
                    "top_hits" to homeHits.top,
                    "bottom_hits" to homeHits.bottom,
                    "home" to (homeDetection != null),
                ),
            )
            if (homeDetection != null) {
                effectiveDetection = homeDetection
                // This policy has already established top and bottom home navigation bands and
                // excluded a comment sheet. Generic OCR stability can be reset by concurrent
                // raw accessibility events, which otherwise keeps a visually confirmed HOME in
                // WAITING_FOR_HOME until timeout. Accept this narrowly-scoped classification;
                // search remains node → structural → constrained fallback.
                acceptedByCommentHomeNavOcr = true
                AutomationStore.publishObservation(effectiveDetection)
            }
        }
        if (
            InitialHomeSurfacePolicy.shouldDeferUnknownRecovery(
                phase = phase,
                detectedPage = effectiveDetection.kind,
                initialClassificationPending = initialHomeClassificationPending,
            )
        ) {
            logger.info(
                "initial_unknown_deferred",
                message = "The launch surface is not classified yet; continuing bounded observation without BACK",
            )
            logWaitingForHomeUnknownDiagnosis(
                context = homeNavContext,
                detectedPage = detection.kind,
                normalizedPage = effectiveDetection.kind,
                observationAttempt = 0,
                stableObservations = 0,
                ocrSkippedReason = when {
                    homeNavContext.ocrBlocks.isNotEmpty() -> "none"
                    isCommentPrivateMessageTask() -> "comment_task"
                    else -> "none"
                },
            )
            return
        }
        if (detection.kind == PageKind.HUMAN_INTERVENTION) {
            pause("Verification or risk screen detected; manual handoff required")
            return
        }
        if (detection.kind == PageKind.LOGIN) {
            pause("Douyin login is required; complete it manually before retrying")
            return
        }
        if (detection.kind == PageKind.LIVE_ROOM_SESSION) {
            exitLiveRoom(context)
            return
        }
        if (detection.kind == PageKind.LIVE_ROOM) {
            swipeLiveRoomAway()
            return
        }
        if (!acceptedByCommentHomeNavOcr && !confirmOcrBackedPage(context, effectiveDetection)) return
        // An OCR-backed HOME/SEARCH result must pass its existing consecutive-observation gate
        // before it may release the UNKNOWN launch-surface safety hold. Otherwise one transient
        // positive frame can be followed by UNKNOWN and restart the bounded BACK recovery.
        if (phase == AutomationPhase.WAITING_FOR_HOME && effectiveDetection.kind != PageKind.UNKNOWN) {
            initialHomeClassificationPending = false
        }

        // Comment tasks reuse the already-validated search/profile navigation until a user
        // profile is reached, then switch to the isolated comment runtime. CURRENT_PROFILE also
        // accepts the operator's already-open ordinary video when its verified comment rail is
        // present. Never let the normal profile-to-DM branch click a private-message control for
        // a comment task.
        if (isCommentPrivateMessageTask() &&
            (effectiveDetection.kind == PageKind.USER_PROFILE || isCurrentProfileCommentTask())
        ) {
            if (handoffCommentProfileObservation(context, effectiveDetection, source = "accessibility_event")) {
                return
            }
            // A result-row tap can be followed by one delayed profile tree from before that tap.
            // It cannot prove the newly selected source profile, so the handoff above correctly
            // rejects it. Never fall through to the legacy profile → private-message branch in
            // this state: that would open a direct-message thread for a stale/source profile
            // before any verified comment avatar has been selected. The bounded profile
            // post-condition poll will consume the fresh profile snapshot instead.
            if (phase == AutomationPhase.WAITING_FOR_PROFILE) return
        }

        if (searchFlow.onPageObserved(phase, context, effectiveDetection.kind)) return

        if (privateMessageFlow.onPageObserved(phase, effectiveDetection.kind)) return

        when (phase) {
            AutomationPhase.WAITING_FOR_PROFILE -> when (effectiveDetection.kind) {
                PageKind.USER_PROFILE -> openPrivateMessage(context)
                else -> Unit
            }

            else -> Unit
        }
    }

    private suspend fun start(
        searchKeyword: String,
        startMessage: String,
        safetyProbe: Boolean,
        taskSnapshot: TaskSnapshot?,
        remoteResume: RemoteTaskResume?,
        suspendedBeforeStart: Boolean,
    ) {
        taskSnapshot?.let { snapshot ->
            AutomationTaskLimitPolicy.taskValidationError(snapshot)?.let { reason ->
                AutomationStore.publishFailure(reason)
                logger.warn("task_start_rejected_limit", message = reason)
                return
            }
            if (
                TaskStartAuthorizationPolicy.decide(
                    snapshot = snapshot,
                    remoteResume = remoteResume,
                    authorizedRemoteTaskIds = RemoteTaskAuthorizationStore.authorizedTaskIds(service),
                ) == TaskStartAuthorization.REMOTE_NOT_AUTHORIZED
            ) {
                val reason = "远程任务 #${remoteResume?.taskId ?: snapshot.taskId} 未在本机授权任务 ID 清单中"
                AutomationStore.publishFailure(reason)
                logger.warn("remote_task_start_rejected_not_authorized", message = reason)
                return
            }
        }
        val commentConfig = taskSnapshot?.commentConfig
            ?.takeIf { taskSnapshot.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE }
        if (taskSnapshot?.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE && commentConfig == null) {
            val reason = "评论私信任务缺少评论处理配置"
            logger.warn("comment_task_invalid", message = reason)
            AutomationStore.publishFailure(reason)
            return
        }
        val startsFromCurrentProfile = commentConfig?.entryMode == CommentPrivateMessageEntryMode.CURRENT_PROFILE
        if (suspendedBeforeStart && !startsFromCurrentProfile) {
            AutomationStore.publishFailure("仅“当前用户主页”评论任务支持挂起后恢复")
            logger.warn("comment_task_suspend_rejected", message = "Suspended start requires CURRENT_PROFILE entry mode")
            return
        }
        val sanitizedKeyword = taskSnapshot?.composedQueries?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: searchKeyword.trim()
        if (sanitizedKeyword.isEmpty() && !startsFromCurrentProfile) {
            AutomationStore.publishFailure("Enter a search keyword before starting the POC.")
            return
        }
        if (!TargetAppLauncher.isInstalled(service)) {
            AutomationStore.publishFailure("Douyin is not installed on this device.")
            logger.error("target_not_installed")
            return
        }

        if (taskActive) {
            logger.warn("start_ignored_active")
            return
        }
        if (remoteResume != null && !RemoteTaskResumePolicy.canResumeExactly(remoteResume.progress)) {
            AutomationStore.publishFailure("远程任务已有进度但缺少最后用户锚点，已停止以避免重复处理")
            logger.error(
                "remote_resume_rejected_missing_anchor",
                message = "Remote progress cannot be resumed safely without the last user anchor",
                attributes = mapOf("remote_task_id_hash" to remoteResume.taskId.hashCode()),
            )
            return
        }

        taskActive = true
        keyword = sanitizedKeyword
        activeTaskSnapshot = taskSnapshot
        taskQueryIndex = 0
        remotePageNumber = 1
        remoteResumePending = remoteResume?.progress?.let { progress ->
            RemoteTaskResumePolicy.requiresAnchor(progress)
        } == true
        remoteResumeAnchor = remoteResume?.progress?.lastUserKey
            ?.takeIf(String::isNotBlank)
            ?.let { key -> UserResultIdentityMatcher.remoteAnchorIdentity(key, remoteResume.progress.lastUserName) }
        remoteResumeTargetPageNumber = remoteResume?.progress?.lastPageNumber?.coerceAtLeast(1)
        remoteResumeMaxSwipes = (remoteResumeTargetPageNumber ?: 1)
            .plus(TuningConstants.NavigationFlow.REMOTE_RESUME_EXTRA_SWIPES)
            .coerceAtMost(TuningConstants.NavigationFlow.MAX_REMOTE_RESUME_SWIPES)
        queryTransitionHandled = false
        pendingStartMessage = taskSnapshot?.messageTemplate?.trim().orEmpty().ifBlank { startMessage.trim() }
        pendingSafetyProbe = taskSnapshot?.let { snapshot ->
            snapshot.executionMode == TaskExecutionMode.SAFE_BLANK_PROBE
        } ?: safetyProbe
        logger.info(
            "task_message_mode",
            attributes = mapOf(
                "safety_probe" to pendingSafetyProbe,
                "message_length" to pendingStartMessage.length,
                "marketing_reason" to (taskSnapshot?.frozenMarketingContent?.reason ?: "none"),
                "resolved_index" to (taskSnapshot?.frozenMarketingContent?.resolvedIndex ?: -1),
            ),
        )
        pausedPhase = null
        initialOcrAttempts = 0
        nestedCommentSurfaceOcrAttempts = 0
        commentLaunchHomeNavOcrAttempts = 0
        homeUnknownGeometryDumpSaved = false
        initialBlindBackAttempts = 0
        // A prior POC can leave a profile/result snapshot in memory. Startup normalization must
        // inspect the freshly launched target surface rather than reuse that stale tree.
        latestContext = null
        latestOcrContext = null
        userTabRevealAttempts = 0
        restrictedUserSkips = 0
        timeoutRecoveryAttempts = 0
        lastProcessedUserAnchorBottom = null
        processedUserIdentities.clear()
        processedUserIdentityRecords.clear()
        processedIdentityFingerprints.clear()
        legacyProcessedIdentityHashes.clear()
        processedCommentCandidateFingerprints.clear()
        // Rehydrate the complete terminal identity ledger when the backend provides it. The last
        // user anchor alone is not enough when the feed reorders or clips that row; known earlier
        // identities let the controller choose the first genuinely new visible row instead of
        // swiping through several pages waiting for an OCR key that may no longer be present.
        remoteResume?.progress?.let { progress ->
            val knownKeys = (progress.processedUserKeys + listOfNotNull(progress.lastUserKey)).distinct()
            knownKeys.forEach { key ->
                val savedName = key.takeIf { it == progress.lastUserKey }?.let { progress.lastUserName }
                val identity = UserResultIdentityMatcher.remoteAnchorIdentity(key, savedName)
                if (processedUserIdentities.add(identity.key)) {
                    processedUserIdentityRecords += identity
                }
                processedIdentityFingerprints += identity.fingerprint
            }
        }
        currentUserIdentityFingerprint = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null
        cachedUserResultsViewportSignature = null
        cachedUserResultsOcrBlocks = emptyList()
        userResultsSignatureBeforeSwipe = null
        commentProfileHandoffObserved = false
        commentProfileHandoffNotBeforeMillis = 0L
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        profilePostconditionJob?.cancel()
        initialObservationJob?.cancel()
        commentRuntime.stop()
        pendingCommentRuntimeSnapshot = null
        // Create/switch the persisted task history entry before publishing the first phase.
        // In a queued batch, publishing LAUNCHING_TARGET while currentTaskId still points to
        // the previous task would regress its terminal COMPLETED status back to RUNNING.
        AutomationStore.beginTask(sanitizedKeyword, taskSnapshot)
        if (suspendedBeforeStart) {
            phase = AutomationPhase.SUSPENDED_BEFORE_START
            AutomationStore.publishPhase(phase)
            logger.info(
                "comment_task_suspended_before_start",
                attributes = mapOf("task_type" to taskSnapshot?.taskType?.name.orEmpty()),
            )
            return
        }
        phase = AutomationPhase.LAUNCHING_TARGET
        AutomationStore.publishPhase(phase)
        logger.info(
            "poc_started",
            attributes = mapOf(
                "target" to TargetAppLauncher.DOUYIN_PACKAGE,
                "task_type" to (taskSnapshot?.taskType?.name ?: "LEGACY"),
                "has_comment_config" to (commentConfig != null),
            ),
        )

        if (commentConfig != null) {
            val runtimeSnapshot = requireNotNull(taskSnapshot?.commentConfig)
            if (startsFromCurrentProfile) {
                commentRuntime.start(runtimeSnapshot)
                // The task form is hosted by our app, so tapping “立即开始” necessarily puts
                // our activity in the foreground even when the operator prepared a Douyin
                // profile first. Bring the existing Douyin task back to the foreground before
                // polling; otherwise currentWindowContext() only sees our own form and the
                // comment runtime times out waiting for a profile that is still behind it.
                gestures.awaitExternalActionSlot("target_app_launch")
                when (val result = TargetAppLauncher.launch(service)) {
                    LaunchResult.Started -> {
                        phase = AutomationPhase.WAITING_FOR_PROFILE
                        AutomationStore.publishPhase(phase)
                        logger.info("comment_current_profile_entry_waiting")
                        delay(TuningConstants.NavigationFlow.CURRENT_PROFILE_ENTRY_SETTLE_DELAY_MS)
                        scheduleCurrentProfileCommentObservation()
                        return
                    }

                    is LaunchResult.Failed -> {
                        failTaskWithoutManualHandoff("无法重新打开抖音用户主页：${result.reason}")
                        return
                    }
                }
            } else {
                // Do not start the comment-stage watchdog while ordinary search navigation is
                // still locating the source profile. It is started atomically by the profile
                // handoff below, after a verified USER_PROFILE observation.
                pendingCommentRuntimeSnapshot = runtimeSnapshot
            }
            logger.info("comment_search_entry_using_profile_navigation")
        }

        gestures.awaitExternalActionSlot("target_app_launch")
        when (val result = TargetAppLauncher.launch(service)) {
            LaunchResult.Started -> {
                // Douyin may show a full-screen promotion immediately after cold start. Give
                // the launch surface a short bounded settle window, then rely on the existing
                // page-confirmation observer rather than acting on an unverified first frame.
                logger.info(
                    "initial_screen_settle_started",
                    attributes = mapOf("wait_ms" to TuningConstants.NavigationLifecycle.INITIAL_SCREEN_SETTLE_DELAY_MS),
                )
                delay(TuningConstants.NavigationLifecycle.INITIAL_SCREEN_SETTLE_DELAY_MS)
                logger.info("initial_screen_settle_completed")
                initialHomeClassificationPending = true
                await(
                    nextPhase = AutomationPhase.WAITING_FOR_HOME,
                    timeoutDescription = "Douyin home or search page was not detected",
                )
                logger.info("initial_observation_scheduled")
                scheduleInitialObservation()
            }

            is LaunchResult.Failed -> pause("Could not open Douyin: ${result.reason}")
        }
    }

    private suspend fun startBatch(tasks: List<TaskSnapshot>) {
        if (taskActive) {
            logger.warn("start_batch_ignored_active")
            return
        }
        if (tasks.size > AutomationExecutionLimits.MAX_TASKS_PER_LOCAL_QUEUE) {
            AutomationStore.publishFailure("待办队列最多只能执行 ${AutomationExecutionLimits.MAX_TASKS_PER_LOCAL_QUEUE} 个任务")
            logger.warn("task_batch_rejected", attributes = mapOf("task_count" to tasks.size, "reason" to "queue_limit"))
            return
        }
        val queueType = LocalTaskQueuePolicy.validate(tasks)
        if (queueType == null) {
            AutomationStore.publishFailure("待办任务只能选择同一种类型，且评论任务必须使用“搜索指定用户”入口")
            logger.warn(
                "task_batch_rejected",
                attributes = mapOf("task_count" to tasks.size, "reason" to "mixed_or_ineligible_type"),
            )
            return
        }
        if (tasks.any { snapshot -> snapshot.composedQueries.none { it.isNotBlank() } }) {
            AutomationStore.publishFailure("没有可执行的待办任务")
            return
        }
        val session = LocalTaskQueueSession(
            queueId = java.util.UUID.randomUUID().toString(),
            queueType = queueType,
            tasks = tasks,
            updatedAtMillis = System.currentTimeMillis(),
        )
        localTaskQueueSession = session
        AutomationStore.saveLocalTaskQueueSession(session)
        queuedTaskSnapshots.replace(tasks.drop(1))
        logger.info(
            "task_batch_started",
            attributes = mapOf("task_count" to tasks.size, "queue_type" to queueType.name),
        )
        val first = session.activeTask
        start(
            searchKeyword = first.composedQueries.firstOrNull().orEmpty(),
            startMessage = first.messageTemplate.orEmpty(),
            safetyProbe = first.executionMode == TaskExecutionMode.SAFE_BLANK_PROBE,
            taskSnapshot = first,
            remoteResume = null,
            suspendedBeforeStart = false,
        )
    }

    /**
     * Continue a local batch after a task has finished.  A short settle delay gives Douyin time to
     * return to its stable surface before the next launch, while the same controller mutex keeps
     * two task starts from overlapping.
     */
    private fun scheduleNextQueuedTask(): Boolean {
        val next = queuedTaskSnapshots.poll() ?: return false
        val queueId = localTaskQueueSession?.queueId
        scope.launch {
            delay(TuningConstants.NavigationFlow.NEXT_TASK_SETTLE_DELAY_MS)
            mutex.withLock {
                if (queueId != null && localTaskQueueSession?.queueId != queueId) {
                    logger.info("task_batch_next_cancelled", attributes = mapOf("reason" to "queue_replaced"))
                    return@withLock
                }
                if (taskActive) {
                    queuedTaskSnapshots.replace(listOf(next) + queuedTaskSnapshots.asList())
                    return@withLock
                }
                val advancedSession = localTaskQueueSession?.advance(System.currentTimeMillis())
                if (advancedSession != null &&
                    (advancedSession.status != LocalTaskQueueStatus.RUNNING ||
                        advancedSession.activeTask.taskId != next.taskId)
                ) {
                    queuedTaskSnapshots.replace(listOf(next) + queuedTaskSnapshots.asList())
                    logger.error(
                        "task_batch_next_rejected",
                        attributes = mapOf("reason" to "session_mismatch"),
                    )
                    return@withLock
                }
                if (advancedSession != null) {
                    localTaskQueueSession = advancedSession
                    AutomationStore.saveLocalTaskQueueSession(advancedSession)
                }
                start(
                    searchKeyword = next.composedQueries.firstOrNull().orEmpty(),
                    startMessage = next.messageTemplate.orEmpty(),
                    safetyProbe = next.executionMode == TaskExecutionMode.SAFE_BLANK_PROBE,
                    taskSnapshot = next,
                    remoteResume = null,
                    suspendedBeforeStart = false,
                )
            }
        }
        return true
    }

    private fun publishTaskTerminal(
        phase: AutomationPhase,
        error: String? = null,
    ) {
        val terminalRoute = LocalTaskQueueTerminalPolicy.route(
            hasPendingTask = !queuedTaskSnapshots.isEmpty,
            hasLocalQueueSession = localTaskQueueSession != null,
        )
        val hasNext = terminalRoute == LocalTaskQueueTerminalRoute.START_NEXT_TASK
        if (phase == AutomationPhase.FAILED) {
            AutomationStore.publishFailure(error ?: "任务执行失败", openRecords = !hasNext)
        } else {
            AutomationStore.publishPhase(phase, error = error, openRecords = !hasNext)
        }
        when (terminalRoute) {
            LocalTaskQueueTerminalRoute.START_NEXT_TASK -> scheduleNextQueuedTask()
            LocalTaskQueueTerminalRoute.COMPLETE_QUEUE -> {
                localTaskQueueSession?.let { session ->
                    val completedSession = session.advance(System.currentTimeMillis())
                    localTaskQueueSession = completedSession
                    AutomationStore.saveLocalTaskQueueSession(completedSession)
                }
            }

            LocalTaskQueueTerminalRoute.FINISH_STANDALONE -> Unit
        }
    }

    private fun finishCommentRuntime(terminal: CommentRuntimeTerminal) {
        if (!taskActive || activeTaskSnapshot?.taskType != AutomationTaskType.COMMENT_PRIVATE_MESSAGE) return
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        when (terminal.outcome) {
            CommentRuntimeTerminal.Outcome.COMPLETED -> {
                commentRuntime.stop()
                taskActive = false
                phase = AutomationPhase.COMPLETED_TASK
                publishTaskTerminal(phase)
                logger.info("comment_task_completed", message = terminal.reason)
            }

            CommentRuntimeTerminal.Outcome.FAILED -> {
                commentRuntime.stop()
                taskActive = false
                phase = AutomationPhase.FAILED
                publishTaskTerminal(phase, error = terminal.reason)
                logger.error("comment_task_failed", message = terminal.reason)
            }

            CommentRuntimeTerminal.Outcome.PAUSED -> {
                taskActive = true
                pause(terminal.reason)
            }

            CommentRuntimeTerminal.Outcome.SKIPPED_PROFILE -> {
                // The profile was safely dismissed because it is private or has no works. Keep
                // the comment runtime alive, return to the user-result phase, and let the normal
                // semantic row selector choose the next profile.
                taskActive = true
                phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                AutomationStore.publishPhase(phase)
                commentRuntime.rearmAfterSkippedProfile()
                logger.info("comment_profile_skipped", message = terminal.reason)
            }
        }
    }

    /** Polls an already-open profile or ordinary video for the CURRENT_PROFILE entry mode. */
    private fun scheduleCurrentProfileCommentObservation() {
        initialObservationJob?.cancel()
        initialObservationJob = scope.launch {
            // Launching an already-open Douyin profile is asynchronous on several OEM builds.
            // A single short probe window can therefore sample the app's transition surface (or
            // our own activity) and then leave the runtime waiting until its watchdog expires.
            // Keep polling for the whole bounded entry timeout; this is still a fixed, low-rate
            // diagnostic loop and does not scroll or click anything by itself.
            repeat(TuningConstants.NavigationFlow.CURRENT_PROFILE_OBSERVATION_ATTEMPTS) { attempt ->
                delay(TuningConstants.NavigationFlow.CURRENT_PROFILE_OBSERVATION_INTERVAL_MS)
                // After a verified profile/video has been handed to the isolated runtime, that
                // runtime owns its own event and post-condition loops. Continuing this entry
                // poll would take needless full-screen OCR samples while it is opening comments
                // or processing a commenter, competing with the active step for no new signal.
                if (!taskActive || !commentRuntime.isRunning || commentProfileHandoffObserved) return@launch
                // A custom-rendered profile/video can expose a fresh node tree as UNKNOWN while
                // the preceding accessibility callback has already produced an OCR-enriched
                // target-app snapshot. Prefer that recent snapshot for this bounded poll; using
                // a node-only tree here would discard the profile/video evidence and leave the
                // comment state machine waiting until its watchdog expires.
                val context = currentProfileObservationContext()
                if (!taskActive || !commentRuntime.isRunning || commentProfileHandoffObserved) return@launch
                if (context == null) {
                    logger.info(
                        "comment_current_profile_probe_waiting",
                        attributes = mapOf("attempt" to (attempt + 1), "reason" to "douyin_window_unavailable"),
                    )
                    return@repeat
                }
                val detection = pageDetector.detect(context)
                logger.info(
                    "comment_current_profile_probe",
                    attributes = mapOf(
                        "attempt" to (attempt + 1),
                        "page" to detection.kind.name,
                        "confidence" to detection.confidence,
                        "nodes" to context.nodes.size,
                        "ocr_blocks" to context.ocrBlocks.size,
                    ),
                )
                if (detection.kind != PageKind.OUTSIDE_TARGET) {
                    commentRuntime.onObserved(context, detection)
                }
            }
        }
    }

    /**
     * Returns the freshest usable target-app snapshot for CURRENT_PROFILE polling. Accessibility
     * callbacks and direct root reads are not synchronized on OEM builds: a root read may be a
     * node-only UNKNOWN tree immediately after the callback's OCR probe recognized the prepared
     * profile or ordinary video. Retain a short-lived enriched snapshot so the state machine can
     * act on the verified page without clicking from stale coordinates or waiting for a second
     * unrelated event.
     */
    private suspend fun currentProfileObservationContext(): ScreenContext? {
        val live = currentWindowContext()
        val enriched = latestOcrContext
            ?.takeIf { it.packageName == TargetAppLauncher.DOUYIN_PACKAGE }
            ?.takeIf { System.currentTimeMillis() - it.capturedAtMillis <= TuningConstants.NavigationFlow.CURRENT_PROFILE_CONTEXT_MAX_AGE_MS }
        val latest = latestContext
            ?.takeIf { it.packageName == TargetAppLauncher.DOUYIN_PACKAGE }
            ?.takeIf { System.currentTimeMillis() - it.capturedAtMillis <= TuningConstants.NavigationFlow.CURRENT_PROFILE_CONTEXT_MAX_AGE_MS }
        val liveDetection = live?.let(pageDetector::detect)
        val enrichedDetection = enriched?.let(pageDetector::detect)
        val latestDetection = latest?.let(pageDetector::detect)
        logger.info(
            "comment_current_profile_context_candidates",
            attributes = mapOf(
                "live_page" to (liveDetection?.kind?.name ?: "NONE"),
                "enriched_page" to (enrichedDetection?.kind?.name ?: "NONE"),
                "enriched_blocks" to (enriched?.ocrBlocks?.size ?: 0),
                "latest_page" to (latestDetection?.kind?.name ?: "NONE"),
                "latest_blocks" to (latest?.ocrBlocks?.size ?: 0),
            ),
        )
        val commentConfig = activeTaskSnapshot?.commentConfig
        val directVideoCandidate = listOfNotNull(live, enriched, latest).firstOrNull { candidate ->
            commentConfig != null && isCurrentCommentEntrySurface(
                candidate,
                pageDetector.detect(candidate),
                commentConfig,
            )
        }
        val selected = when {
            liveDetection?.kind == PageKind.USER_PROFILE -> live
            enrichedDetection?.kind == PageKind.USER_PROFILE -> enriched
            latestDetection?.kind == PageKind.USER_PROFILE -> latest
            // Prefer a freshly observed, fully verified normal-video surface over a sparse
            // live root that was captured while our progress overlay was expanded.  The caller
            // still validates this context again before it can open a comment panel.
            directVideoCandidate != null -> directVideoCandidate
            live != null -> live
            else -> enriched ?: latest
        }
        // If the direct root read wins the race with the service OCR callback, take one bounded
        // full-screen sample from the already-open profile/video. This is still a read-only
        // probe; it never clicks or swipes and gives the comment state machine the same OCR-backed
        // page evidence that a normal accessibility callback would provide.
        if (selected != null && shouldBypassOcrForCurrentProfileCommentEntry(selected)) {
            return selected
        }
        if (selected != null &&
            pageDetector.detect(selected).kind == PageKind.UNKNOWN &&
            selected.packageName == TargetAppLauncher.DOUYIN_PACKAGE
        ) {
            return captureContextWithOcr(selected, "current_profile_probe", OcrRegion.FULL) ?: selected
        }
        return selected
    }

    private suspend fun resumeSavedTask(forceInitialRestart: Boolean = false) {
        val checkpoint = AutomationStore.getSavedCheckpoint()
        if (checkpoint == null) {
            AutomationStore.publishFailure("没有可恢复的任务检查点")
            logger.warn("saved_task_resume_rejected", message = "No private task checkpoint is available")
            return
        }
        if (taskActive) {
            logger.warn("saved_task_resume_ignored_active")
            return
        }
        if (
            TaskStartAuthorizationPolicy.decide(
                snapshot = checkpoint.snapshot,
                remoteResume = null,
                authorizedRemoteTaskIds = RemoteTaskAuthorizationStore.authorizedTaskIds(service),
            ) == TaskStartAuthorization.REMOTE_NOT_AUTHORIZED
        ) {
            AutomationStore.publishFailure("远程任务 #${checkpoint.snapshot.taskId} 未在本机授权任务 ID 清单中")
            logger.warn("remote_task_resume_rejected_not_authorized")
            return
        }
        AutomationTaskLimitPolicy.taskValidationError(checkpoint.snapshot)?.let { reason ->
            AutomationStore.publishFailure(reason)
            logger.warn("saved_task_resume_rejected_limit", message = reason)
            return
        }
        AutomationStore.getLocalTaskQueueSession()
            ?.takeIf { session ->
                session.status == LocalTaskQueueStatus.RUNNING &&
                    session.activeTask.taskId == checkpoint.snapshot.taskId
            }
            ?.let(::restoreLocalTaskQueue)
        val query = checkpoint.snapshot.composedQueries.getOrNull(checkpoint.queryIndex)
        val restoredCommentConfig = checkpoint.snapshot.commentConfig
            ?.takeIf { checkpoint.snapshot.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE }
        val resumesCurrentProfileCommentTask = restoredCommentConfig?.entryMode ==
            CommentPrivateMessageEntryMode.CURRENT_PROFILE
        if (query.isNullOrBlank() && !resumesCurrentProfileCommentTask) {
            AutomationStore.publishFailure("任务检查点中的搜索词无效")
            AutomationStore.clearTaskCheckpoint()
            return
        }
        logger.info(
            "saved_task_resume_requested",
            attributes = mapOf("query_index" to checkpoint.queryIndex, "query_count" to checkpoint.snapshot.composedQueries.size),
        )
        taskActive = true
        keyword = query.orEmpty()
        activeTaskSnapshot = checkpoint.snapshot
        taskQueryIndex = checkpoint.queryIndex
        remotePageNumber = 1
        remoteResumePending = false
        remoteResumeAnchor = null
        remoteResumeTargetPageNumber = null
        remoteResumeMaxSwipes = 0
        queryTransitionHandled = false
        pendingStartMessage = checkpoint.snapshot.messageTemplate.orEmpty()
        pendingSafetyProbe = checkpoint.snapshot.executionMode == TaskExecutionMode.SAFE_BLANK_PROBE
        pausedPhase = null
        initialOcrAttempts = 0
        nestedCommentSurfaceOcrAttempts = 0
        commentLaunchHomeNavOcrAttempts = 0
        homeUnknownGeometryDumpSaved = false
        initialBlindBackAttempts = 0
        latestContext = null
        latestOcrContext = null
        userTabRevealAttempts = 0
        restrictedUserSkips = 0
        timeoutRecoveryAttempts = 0
        lastProcessedUserAnchorBottom = null
        processedUserIdentities.clear()
        processedUserIdentityRecords.clear()
        processedIdentityFingerprints.clear()
        legacyProcessedIdentityHashes.clear()
        processedIdentityFingerprints.addAll(checkpoint.processedIdentityFingerprints)
        legacyProcessedIdentityHashes.addAll(checkpoint.legacyProcessedIdentityHashes)
        processedCommentCandidateFingerprints.clear()
        processedCommentCandidateFingerprints.addAll(checkpoint.processedCommentIdentityFingerprints)
        currentUserIdentityFingerprint = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null
        cachedUserResultsViewportSignature = null
        cachedUserResultsOcrBlocks = emptyList()
        userResultsSignatureBeforeSwipe = null
        commentProfileHandoffObserved = false
        commentProfileHandoffNotBeforeMillis = 0L
        pendingCommentRuntimeSnapshot = null
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        profilePostconditionJob?.cancel()
        initialObservationJob?.cancel()
        phase = AutomationPhase.WAITING_FOR_HOME
        AutomationStore.publishPhase(phase)
        AutomationStore.resumeTask(checkpoint)

        // A search-entry comment task first uses the generic search/User-tab navigation. Keep
        // its immutable configuration across a rebind, but do not run the comment watchdog
        // until a verified source profile is actually available.
        restoredCommentConfig?.let { commentConfig ->
            val shouldStartRuntime = commentConfig.entryMode == CommentPrivateMessageEntryMode.CURRENT_PROFILE
            if (shouldStartRuntime) {
                commentRuntime.start(commentConfig)
            } else {
                pendingCommentRuntimeSnapshot = commentConfig
            }
            logger.info(
                "comment_runtime_resume_prepared_after_rebind",
                attributes = mapOf(
                    "entry_mode" to commentConfig.entryMode.name,
                    "runtime_started" to shouldStartRuntime,
                ),
            )
        }

        if (resumesCurrentProfileCommentTask) {
            phase = AutomationPhase.WAITING_FOR_PROFILE
            AutomationStore.publishPhase(phase)
            delay(TuningConstants.NavigationFlow.CURRENT_PROFILE_ENTRY_SETTLE_DELAY_MS)
            scheduleCurrentProfileCommentObservation()
            return
        }
        val existingContext = currentWindowContext()
        val existingDetection = existingContext?.let(pageDetector::detect)
        val inPlaceResumePhase = SavedTaskResumePolicy.phaseForInPlaceResume(
            forceInitialRestart = forceInitialRestart,
            visiblePage = existingDetection?.kind,
        )
        if (existingContext != null && existingDetection != null && inPlaceResumePhase != null) {
            phase = inPlaceResumePhase
            AutomationStore.publishPhase(phase)
            onScreenObserved(existingContext, existingDetection)
            return
        }
        gestures.awaitExternalActionSlot("target_app_launch")
        when (val result = TargetAppLauncher.launch(service)) {
            LaunchResult.Started -> {
                delay(TuningConstants.NavigationLifecycle.INITIAL_SCREEN_SETTLE_DELAY_MS)
                scheduleInitialObservation()
            }
            is LaunchResult.Failed -> pause("无法恢复抖音任务：${result.reason}")
        }
    }

    private suspend fun resume() {
        val localQueue = localTaskQueueSession ?: AutomationStore.getLocalTaskQueueSession()
        if (localQueue?.status == LocalTaskQueueStatus.PAUSED) {
            resumeLocalTaskQueue(localQueue)
            return
        }
        // AccessibilityService instances can be destroyed and rebound independently from the
        // overlay (observed on physical devices when switching foreground apps).  The overlay
        // still correctly shows “恢复”, but a new controller otherwise starts at IDLE and rejects
        // that explicit Resume command. Rehydrate only a deliberately suspended current-profile
        // task; do not auto-start it and do not apply this recovery to general paused runs.
        if (!taskActive && phase != AutomationPhase.SUSPENDED_BEFORE_START) {
            AutomationStore.getSuspendedCurrentProfileCheckpoint()?.let { checkpoint ->
                restoreSuspendedCurrentProfileTask(checkpoint)
            }
        }
        val resumesCurrentProfileCommentTask = activeTaskSnapshot
            ?.takeIf { it.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE }
            ?.commentConfig
            ?.entryMode == CommentPrivateMessageEntryMode.CURRENT_PROFILE
        if (phase == AutomationPhase.SUSPENDED_BEFORE_START) {
            if (!resumesCurrentProfileCommentTask) {
                AutomationStore.publishFailure("挂起任务缺少“当前用户主页”评论配置")
                logger.warn("resume_rejected", message = "Suspended task does not support current-profile resume")
                return
            }
            resumeCurrentProfileCommentTask(source = "suspended_before_start")
            return
        }
        if (phase != AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF ||
            (keyword.isNullOrBlank() && !resumesCurrentProfileCommentTask)
        ) {
            AutomationStore.publishFailure("There is no paused run ready to resume.")
            logger.warn("resume_rejected", message = "No paused run is available")
            return
        }

        if (resumesCurrentProfileCommentTask) {
            resumeCurrentProfileCommentTask(source = "manual_handoff")
            return
        }

        val context = latestContext ?: currentWindowContext()
        if (context == null) {
            AutomationStore.publishManualHandoff("No active Douyin window is available; review the screen before resuming")
            return
        }

        val detection = pageDetector.detect(context)
        AutomationStore.publishObservation(detection)
        val decision = CommentOverlayResumePolicy.resumeDecision(
            commentRuntimeFrozen = commentRuntime.isFrozen,
            detection = detection,
            pausedPhase = pausedPhase,
        )
        if (!decision.allowed || decision.phase == null) {
            AutomationStore.publishManualHandoff(decision.reason)
            logger.warn("resume_rejected", message = decision.reason)
            return
        }

        taskActive = true
        phase = decision.phase
        pausedPhase = null
        AutomationStore.publishPhase(phase)
        logger.info("poc_resumed", attributes = mapOf("phase" to phase.name))
        // Pause stops the comment runtime. Keep the immutable search-target config so a later
        // verified profile can hand off again instead of falling through to B-end messaging.
        if (isSearchTargetProfileCommentTask()) {
            pendingCommentRuntimeSnapshot =
                pendingCommentRuntimeSnapshot ?: activeTaskSnapshot?.commentConfig
        }
        // Resuming does not necessarily produce a new accessibility window event.  The old
        // implementation only changed the phase, which left a verified search/profile page
        // idle until Douyin happened to emit another event.  Continue from the observation that
        // was just validated so Resume has the same post-condition behavior as a fresh event.
        if (!confirmOcrBackedPage(context, detection)) return
        if (commentRuntime.unfreeze()) {
            logger.info(
                "comment_runtime_unfrozen_on_overlay_resume",
                attributes = mapOf("stage" to commentRuntime.stage.name, "page" to detection.kind.name),
            )
            commentRuntime.onObserved(context, detection)
            return
        }
        when (detection.kind) {
            PageKind.HOME -> openSearch(context)
            PageKind.SEARCH_ENTRY -> enterKeyword(context)
            PageKind.SEARCH_RESULTS -> selectUserTab(context)
            PageKind.USER_RESULTS -> selectVisibleUser(context)
            PageKind.USER_PROFILE -> {
                if (CommentOverlayResumePolicy.shouldHandoffToCommentRuntime(
                        isCommentPrivateMessageTask(),
                        detection.kind,
                    )
                ) {
                    if (!handoffCommentProfileObservation(
                            context,
                            detection,
                            source = "overlay_resume",
                        )
                    ) {
                        taskActive = false
                        phase = AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF
                        AutomationStore.publishManualHandoff("无法将当前主页交回评论私信流程")
                        logger.warn("comment_overlay_resume_handoff_rejected")
                    }
                } else {
                    openPrivateMessage(context)
                }
            }
            PageKind.DIRECT_MESSAGE -> completeAtMessagePage()
            else -> Unit
        }
    }

    /** Resumes a local queue from its frozen active item without dropping later queue items. */
    private suspend fun resumeLocalTaskQueue(session: LocalTaskQueueSession) {
        val checkpoint = AutomationStore.getSavedCheckpoint()
        if (checkpoint?.snapshot?.taskId != session.activeTask.taskId) {
            AutomationStore.publishManualHandoff("待办队列缺少当前任务检查点，已保留队列等待人工处理")
            logger.error(
                "task_queue_resume_rejected",
                attributes = mapOf("reason" to "active_checkpoint_missing"),
            )
            return
        }
        // The reader can intentionally find a visible Douyin window behind an overlay. It must
        // not be used to claim that Douyin is foreground after the operator has left the app.
        val targetInForeground = windowContextReader.isTargetAppInActiveWindow()
        val detection = currentWindowContext()
            ?.takeIf { targetInForeground }
            ?.let(pageDetector::detect)
        val visiblePage = detection?.kind ?: PageKind.OUTSIDE_TARGET
        val restartFromInitial = LocalTaskQueueResumePolicy.requiresInitialRestart(
            queueType = session.queueType,
            pausedPhase = session.pausedPhase,
            visiblePage = visiblePage,
        )
        val resumedSession = session.resume(System.currentTimeMillis())
        restoreLocalTaskQueue(resumedSession)
        AutomationStore.saveLocalTaskQueueSession(resumedSession)
        logger.info(
            "task_queue_resume_requested",
            attributes = mapOf(
                "queue_type" to session.queueType.name,
                "restart_from_initial" to restartFromInitial,
                "visible_page" to visiblePage.name,
            ),
        )
        resumeSavedTask(forceInitialRestart = restartFromInitial)
    }

    /** Rehydrates the in-memory FIFO from the persisted active index; it never starts work itself. */
    private fun restoreLocalTaskQueue(session: LocalTaskQueueSession) {
        localTaskQueueSession = session
        queuedTaskSnapshots.replace(session.tasks.drop(session.activeTaskIndex + 1))
    }

    /** Restores only enough durable state for an explicit suspended-task Resume to be safe. */
    private fun restoreSuspendedCurrentProfileTask(checkpoint: TaskCheckpoint) {
        val snapshot = checkpoint.snapshot
        val commentConfig = snapshot.commentConfig
        if (snapshot.taskType != AutomationTaskType.COMMENT_PRIVATE_MESSAGE ||
            commentConfig?.entryMode != CommentPrivateMessageEntryMode.CURRENT_PROFILE
        ) {
            return
        }
        taskActive = true
        keyword = snapshot.composedQueries.getOrNull(checkpoint.queryIndex).orEmpty()
        activeTaskSnapshot = snapshot
        taskQueryIndex = checkpoint.queryIndex
        remotePageNumber = 1
        remoteResumePending = false
        remoteResumeAnchor = null
        remoteResumeTargetPageNumber = null
        remoteResumeMaxSwipes = 0
        queryTransitionHandled = false
        pendingStartMessage = snapshot.messageTemplate.orEmpty()
        pendingSafetyProbe = snapshot.executionMode == TaskExecutionMode.SAFE_BLANK_PROBE
        pausedPhase = null
        latestContext = null
        latestOcrContext = null
        currentUserIdentityFingerprint = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null
        processedUserIdentities.clear()
        processedUserIdentityRecords.clear()
        processedIdentityFingerprints.clear()
        legacyProcessedIdentityHashes.clear()
        processedIdentityFingerprints.addAll(checkpoint.processedIdentityFingerprints)
        legacyProcessedIdentityHashes.addAll(checkpoint.legacyProcessedIdentityHashes)
        processedCommentCandidateFingerprints.clear()
        processedCommentCandidateFingerprints.addAll(checkpoint.processedCommentIdentityFingerprints)
        commentProfileHandoffObserved = false
        commentProfileHandoffNotBeforeMillis = 0L
        commentRuntime.stop()
        pendingCommentRuntimeSnapshot = null
        phase = AutomationPhase.SUSPENDED_BEFORE_START
        AutomationStore.restoreSuspendedTask(checkpoint)
        AutomationStore.publishPhase(phase)
        logger.info(
            "comment_suspended_task_rehydrated",
            attributes = mapOf("task_id_hash" to checkpoint.taskId.hashCode()),
        )
    }

    /**
     * Starts a CURRENT_PROFILE comment task only after the operator explicitly resumes it from a
     * verified Douyin profile or an already-open ordinary video. This intentionally does not call
     * TargetAppLauncher: the suspended flow exists to preserve the page the operator navigated to
     * by hand.
     */
    private suspend fun resumeCurrentProfileCommentTask(source: String) {
        val entry = resolveCurrentProfileCommentEntryForResume()
        if (entry == null) {
            keepCurrentProfileTaskSuspended("请先停留在抖音目标用户主页或普通视频（非直播、非广告）后再恢复")
            return
        }
        val (context, detection) = entry

        taskActive = true
        phase = AutomationPhase.WAITING_FOR_PROFILE
        pausedPhase = null
        latestContext = context
        if (context.ocrBlocks.isNotEmpty() || detection.reasons.any { it.contains("OCR", ignoreCase = true) }) {
            latestOcrContext = context
        }
        AutomationStore.publishPhase(phase)
        if (!handoffCommentProfileObservation(context, detection, source = source)) {
            failTaskWithoutManualHandoff("无法将当前页面交给评论私信流程")
            return
        }
        logger.info("comment_current_profile_resumed", attributes = mapOf("source" to source))
        scheduleCurrentProfileCommentObservation()
    }

    /**
     * The overlay's Resume button is itself an application window.  Let its removal settle and
     * take a few short, read-only snapshots before deciding that the hand-prepared target is
     * unavailable.  This eliminates the one-frame overlay/root race without weakening the
     * normal-video contract: every accepted candidate still has to expose the strict comment
     * action rail and pass the existing live/ad/risk exclusions.
     */
    private suspend fun resolveCurrentProfileCommentEntryForResume(): Pair<ScreenContext, PageDetection>? {
        val commentConfig = activeTaskSnapshot?.commentConfig ?: return null
        repeat(TuningConstants.NavigationFlow.CURRENT_PROFILE_RESUME_CONTEXT_ATTEMPTS) { attempt ->
            delay(
                if (attempt == 0) {
                    TuningConstants.NavigationFlow.CURRENT_PROFILE_RESUME_SETTLE_DELAY_MS
                } else {
                    TuningConstants.NavigationFlow.CURRENT_PROFILE_RESUME_CONTEXT_INTERVAL_MS
                },
            )
            val context = currentProfileObservationContext()
            if (context == null) {
                logger.info(
                    "comment_current_profile_resume_probe_waiting",
                    attributes = mapOf(
                        "attempt" to (attempt + 1),
                        "reason" to "douyin_window_unavailable",
                    ),
                )
                return@repeat
            }
            val detection = pageDetector.detect(context)
            AutomationStore.publishObservation(detection)
            val observation = CommentEntrySignalDetector.observe(
                context,
                skipPinnedVideos = commentConfig.skipPinnedVideos,
            )
            val accepted = isCurrentCommentEntrySurface(context, detection, commentConfig)
            logger.info(
                "comment_current_profile_resume_probe",
                attributes = mapOf(
                    "attempt" to (attempt + 1),
                    "page" to detection.kind.name,
                    "nodes" to context.nodes.size,
                    "video_surface" to observation.hasVideoSurface,
                    "comment_entry" to observation.hasCommentEntry,
                    "comment_surface" to observation.isCommentSurfaceReady,
                    "accepted" to accepted,
                ),
            )
            if (accepted) return context to detection
        }
        return null
    }

    /** Keeps the pre-start flow recoverable when the operator has not reached a usable profile. */
    private fun keepCurrentProfileTaskSuspended(reason: String) {
        if (phase == AutomationPhase.SUSPENDED_BEFORE_START) {
            AutomationStore.publishPhase(AutomationPhase.SUSPENDED_BEFORE_START)
        } else {
            AutomationStore.publishManualHandoff(reason)
        }
        logger.warn("comment_current_profile_resume_waiting", message = reason)
    }

    private suspend fun openSearch(context: ScreenContext) {
        phase = AutomationPhase.OPENING_SEARCH
        AutomationStore.publishPhase(phase)
        val semanticOutcome = clickSelector(context, DouyinSelectors.searchEntry)
        val structuralOutcome = if (!semanticOutcome.succeeded) {
            clickSelector(context, DouyinSelectors.searchEntryStructural)
        } else {
            null
        }
        val decision = SearchEntrySelectionPolicy.decide(semanticOutcome, structuralOutcome)
        if (decision.usedStructuralFallback) {
            logger.info(
                "search_entry_structural_fallback",
                message = "The unlabeled clickable search icon was selected by class and region",
                attributes = mapOf("route" to requireNotNull(decision.outcome).route),
            )
        }
        val outcome = decision.outcome ?: run {
            logger.warn(
                "search_entry_selector_fallback",
                message = "Semantic and structural search selectors were unavailable; using the constrained normalized fallback",
            )
            tapNormalizedGuarded(DouyinSelectors.searchEntryNormalizedFallback, "search_entry_fallback")
        }
        if (!outcome.succeeded) {
            pause("Could not find the Douyin search entry: ${outcome.reason}")
            return
        }
        logger.info("search_entry_opened", attributes = mapOf("route" to outcome.route))
        await(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, "Search input did not appear after opening search")

        // Some Douyin builds emit the window/content event before the gesture callback returns.
        // In that ordering the state machine has not entered WAITING_FOR_SEARCH_ENTRY yet, so the
        // event is intentionally ignored and no later event may arrive. Poll the live tree briefly
        // after the action and advance from the verified post-condition instead of timing out.
        for (attempt in 1..TuningConstants.NavigationFlow.SEARCH_ENTRY_POSTCONDITION_ATTEMPTS) {
            delay(if (attempt == 1) 250L else TuningConstants.NavigationFlow.SEARCH_ENTRY_POSTCONDITION_INTERVAL_MS)
            val postSearchContext = currentWindowContext() ?: continue
            val postSearchDetection = pageDetector.detect(postSearchContext)
            logger.info(
                "search_entry_postcondition",
                attributes = mapOf(
                    "attempt" to attempt,
                    "page" to postSearchDetection.kind.name,
                    "confidence" to postSearchDetection.confidence,
                    "nodes" to postSearchContext.nodes.size,
                ),
            )
            if (postSearchDetection.kind == PageKind.SEARCH_ENTRY) {
                latestContext = postSearchContext
                AutomationStore.publishObservation(postSearchDetection)
                enterKeyword(postSearchContext)
                return
            }
            if (postSearchDetection.kind == PageKind.SEARCH_RESULTS &&
                selector.select(postSearchContext, DouyinSelectors.searchInput).node != null
            ) {
                // The top bar in a restored result page is an editable search field (the node
                // dump captured during the failure showed text="国乒现状"). It is safe to replace
                // that text and press the same top-right Search control; no suggestion row is
                // clicked and the requested keyword is verified before submission.
                logger.warn(
                    "search_results_surface_reused",
                    message = "Douyin restored a previous result page; reusing its editable query field",
                    attributes = mapOf("source" to "open_search_postcondition"),
                )
                latestContext = postSearchContext
                AutomationStore.publishObservation(postSearchDetection)
                reuseSearchResultsQueryField(postSearchContext, "open_search_postcondition")
                return
            }
        }
    }

    /**
     * A result page can be a valid search surface: the query EditText remains at the top even
     * though the page detector intentionally classifies the page as SEARCH_RESULTS because its
     * tab strip is present. Re-enter the requested keyword only through that editable node.
     */
    private suspend fun reuseSearchResultsQueryField(context: ScreenContext, source: String) {
        val selection = selector.select(context, DouyinSelectors.searchInput)
        if (selection.node == null) {
            logger.warn(
                "search_results_query_field_missing",
                message = "A restored result page did not expose an editable query field",
                attributes = mapOf("source" to source),
            )
            recoverInitialSurface(context)
            return
        }
        logger.info(
            "search_results_query_field_ready",
            message = "Replacing the restored result-page query after node verification",
            attributes = mapOf("source" to source),
        )
        val queryNode = selection.node
        withLiveNode(queryNode) { liveNode ->
            gestures.click(liveNode, queryNode.bounds)
        }
        delay(TuningConstants.NavigationFlow.KEYWORD_POSTCONDITION_DELAY_MS)
        enterKeyword(currentWindowContext() ?: context)
    }

    /**
     * Bring a task back to a known Douyin surface when a launcher restores a profile, user list,
     * chat, or a transient feed/video page. This is a bounded back-navigation recovery; it never
     * taps an unrelated control or relies on a coordinate to guess the current page.
     */
    private suspend fun recoverInitialSurface(
        initialContext: ScreenContext?,
        requireHome: Boolean = false,
        suppressSearchUntilBack: Boolean = false,
    ) {
        var context: ScreenContext? = initialContext
        var pendingForcedBack = suppressSearchUntilBack
        repeat(TuningConstants.NavigationFlow.MAX_INITIAL_HOME_BACK_ACTIONS) { attempt ->
            val current = context ?: currentWindowContext() ?: recentInitialTargetContext()
            if (current == null) {
                // Accessibility callbacks can be absent for a short period when a restored
                // Douyin activity is covered by a system/OEM surface. A bounded blind BACK is
                // still safe here because the task was launched immediately before this loop;
                // it lets the app return to its home surface instead of waiting for an event
                // that may never arrive.
                if (initialBlindBackAttempts >= TuningConstants.NavigationFlow.MAX_INITIAL_BLIND_BACK_ACTIONS) {
                    failTaskWithoutManualHandoff("无法读取抖音页面，已尝试返回首页但无障碍服务未提供页面树")
                    return
                }
                initialBlindBackAttempts++
                val backSucceeded = gestures.globalBack().succeeded
                logger.warn(
                    "initial_context_missing_back",
                    message = "No Douyin node tree is available; issuing a bounded BACK to normalize the launch surface",
                    attributes = mapOf(
                        "attempt" to initialBlindBackAttempts,
                        "succeeded" to backSucceeded,
                    ),
                )
                if (!backSucceeded && initialBlindBackAttempts >= TuningConstants.NavigationFlow.MAX_INITIAL_BLIND_BACK_ACTIONS) {
                    failTaskWithoutManualHandoff("无法读取抖音页面，已尝试返回首页但无障碍服务未提供页面树")
                    return
                }
                delay(TuningConstants.NavigationFlow.INITIAL_SURFACE_RECOVERY_BACK_DELAY_MS)
                context = currentWindowContext() ?: recentInitialTargetContext()
                return@repeat
            }
            initialBlindBackAttempts = 0
            val commentSurface = CommentSurfaceDetector.detect(current)
            val forceBack = pendingForcedBack ||
                NestedLaunchSurfacePolicy.shouldSuppressHomeSearchAction(commentSurface.isCommentSurface)
            if (forceBack) {
                logger.info(
                    "initial_nested_comment_surface_back",
                    message = "The comment sheet is still open; issuing bounded BACK instead of tapping a search control above it",
                    attributes = mapOf(
                        "attempt" to attempt + 1,
                        "confidence" to commentSurface.confidence,
                        "forced" to pendingForcedBack,
                    ),
                )
                pendingForcedBack = false
            } else {
                val detection = normalizeInitialHomeDetection(current)
                logger.info(
                    "initial_surface_recovery_probe",
                    attributes = mapOf("attempt" to attempt + 1, "page" to detection.kind.name),
                )
                when (detection.kind) {
                    PageKind.HOME -> {
                        openSearch(current)
                        return
                    }
                    PageKind.SEARCH_ENTRY -> {
                        // A focused search-entry surface is already a clean, editable search page.
                        // enterKeyword overwrites any default/stale text and verifies the requested
                        // keyword before submitting, so it is safe even for comment tasks that
                        // demand normalization back to home. Pressing BACK from a focused search
                        // field only dismisses the keyboard and can exhaust the recovery budget
                        // without ever reaching HOME (observed on device: UNKNOWN→USER_PROFILE→
                        // USER_RESULTS→SEARCH_RESULTS→SEARCH_ENTRY, then BACK failed to leave it).
                        enterKeyword(current)
                        return
                    }
                    PageKind.SEARCH_RESULTS -> {
                        // A verified editable search field is already an initial-flow reset point:
                        // overwriting and resubmitting the frozen query cannot reuse a prior user,
                        // profile, video, or comment. Do not spend the remaining BACK budget merely
                        // to reach HOME when this safer semantic path is available.
                        if (selector.select(current, DouyinSelectors.searchInput).node != null) {
                            reuseSearchResultsQueryField(current, "initial_surface_recovery")
                            return
                        }
                    }
                    PageKind.OUTSIDE_TARGET -> {
                        pause("抖音未处于前台，无法回到搜索页面")
                        return
                    }
                    else -> Unit
                }
            }
            // Do not let the last profile/result snapshot masquerade as the post-BACK page if a
            // device drops one content-change callback during the transition.
            latestContext = null
            latestOcrContext = null
            if (!gestures.globalBack().succeeded) {
                pause("无法从抖音当前页面返回到可搜索页面")
                return
            }
            delay(TuningConstants.NavigationFlow.INITIAL_SURFACE_RECOVERY_BACK_DELAY_MS)
            context = currentWindowContext() ?: recentInitialTargetContext()
        }
        // The final bounded BACK can be the action that leaves a focused search field and
        // reveals HOME.  Check that resulting surface once before reporting a failed recovery;
        // otherwise the loop would reject a valid home page without ever observing it.
        val finalContext = currentWindowContext() ?: recentInitialTargetContext()
        if (finalContext != null &&
            !NestedLaunchSurfacePolicy.shouldSuppressHomeSearchAction(
                CommentSurfaceDetector.detect(finalContext).isCommentSurface,
            )
        ) {
            val finalDetection = normalizeInitialHomeDetection(finalContext)
            logger.info(
                "initial_surface_recovery_final_probe",
                attributes = mapOf("page" to finalDetection.kind.name),
            )
            when (finalDetection.kind) {
                PageKind.HOME -> {
                    openSearch(finalContext)
                    return
                }
                PageKind.SEARCH_ENTRY -> {
                    // See the matching loop branch: a search-entry surface is already a clean,
                    // editable search page, so enter the keyword directly instead of pressing
                    // BACK past a focused input that only dismisses the keyboard.
                    enterKeyword(finalContext)
                    return
                }
                PageKind.SEARCH_RESULTS -> if (selector.select(finalContext, DouyinSelectors.searchInput).node != null) {
                    reuseSearchResultsQueryField(finalContext, "initial_surface_recovery_final_probe")
                    return
                }
                else -> Unit
            }
        }
        val reason = "抖音未能在限定时间内回到可搜索页面"
        if (requireHome) {
            failTaskWithoutManualHandoff(reason)
        } else {
            pause(reason)
        }
    }

    private suspend fun enterKeyword(
        context: ScreenContext,
        preserveTimeoutRecoveryBudget: Boolean = false,
    ) {
        val activeKeyword = keyword
        if (activeKeyword.isNullOrBlank()) {
            pause("Search keyword was lost; stop and start the POC again")
            return
        }

        phase = AutomationPhase.ENTERING_KEYWORD
        AutomationStore.publishPhase(phase)
        val selection = selector.select(context, DouyinSelectors.searchInput)
        var selected = selection.node
        if (selected == null) {
            pause("Could not find an editable search field")
            return
        }

        var setTextResult = withLiveNode(selected) { liveNode ->
            gestures.setText(liveNode, activeKeyword)
        }
        if (!setTextResult.succeeded) {
            // The search surface can be rebuilt immediately after a restored-result transition.
            // Re-select the current editable node once before treating the action as failed; the
            // old immutable hierarchy path is not safe to reuse across that transition.
            val refreshedTarget = currentWindowContext()
                ?.let { selector.select(it, DouyinSelectors.searchInput).node }
            if (refreshedTarget != null) {
                logger.warn(
                    "search_input_stale_retry",
                    message = "The selected search field was replaced; reselecting the live field",
                )
                selected = refreshedTarget
                setTextResult = withLiveNode(selected) { liveNode ->
                    gestures.setText(liveNode, activeKeyword)
                }
            }
        }
        if (!setTextResult.succeeded) {
            pause("Could not enter the search keyword: ${setTextResult.reason}")
            return
        }

        // ACTION_SET_TEXT can return true while a transient Douyin search surface is still
        // showing its hint/default suggestion. Read the live field before submitting; a default
        // term such as "红木家具厂" must never be sent in place of the requested keyword.
        if (!ensureSearchKeyword(selected, activeKeyword)) {
            pause("The search field did not contain the requested keyword; review the screen and retry")
            return
        }

        // The search-entry page may contain a full friend/profile suggestion. It is deliberately
        // ignored: after the requested text is verified, submit only through the visible
        // top-right Search control (semantic bounds first, same-control normalized fallback).
        // Never tap a suggestion, move focus elsewhere, press BACK, or submit through IME_ENTER.
        delay(350L)
        val submitContext = currentWindowContext() ?: context
        // The top-right label is a non-clickable TextView on this Douyin build. Select it
        // semantically, then deliberately use its bounds so a no-op ACTION_CLICK cannot be
        // mistaken for a successful submit.
        val buttonResult = tapSelectorBounds(submitContext, searchSubmitSelector)
        val submitResult = if (buttonResult.succeeded) {
            buttonResult
        } else {
            val normalizedFallback = tapNormalizedGuarded(
                DouyinSelectors.searchSubmitNormalizedFallback,
                "search_submit_initial_fallback",
            )
            normalizedFallback
        }
        if (!submitResult.succeeded) {
            pause("Search was entered but could not be submitted: ${submitResult.reason}")
            return
        }

        // A dispatched gesture can complete while the IME is still animating and leave the
        // suggestion page visible. Confirm the post-condition; retry only on a still-classified
        // search-entry page, never on a login/risk/unknown state.
        //
        // Douyin may publish the result tree several seconds after the gesture (especially when
        // the first result page is network-backed). Do not hand this transition straight to the
        // generic event-driven watchdog: on some builds the result tree arrives without a second
        // accessibility event, so the watchdog can time out while the correct User page is
        // already visible. A bounded live poll is still node/page based and never taps a guessed
        // result.
        var postSubmitResultsContext: ScreenContext? = null
        delay(500L)
        val postSubmitContext = currentWindowContext()
        if (postSubmitContext != null && pageDetector.detect(postSubmitContext).kind == PageKind.SEARCH_ENTRY) {
            logger.warn("search_submit_postcondition_retry", message = "Search entry remained visible after the first submit gesture")
            val retry = retrySearchSubmit(postSubmitContext)
            if (!retry.succeeded) {
                pause("Search entry remained visible and the retry gesture was rejected")
                return
            }
            postSubmitResultsContext = currentWindowContext()?.takeIf { context ->
                pageDetector.detect(context).kind in setOf(PageKind.SEARCH_RESULTS, PageKind.USER_RESULTS)
            }
        }

        if (postSubmitResultsContext == null) {
            postSubmitResultsContext = searchResultsContextAfterSubmit("search_submit_initial")
        }

        if (postSubmitResultsContext != null) {
            val resultContext = requireNotNull(postSubmitResultsContext)
            val resultPage = pageDetector.detect(resultContext)
            when (resultPage.kind) {
                PageKind.USER_RESULTS -> {
                    phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                    AutomationStore.publishPhase(phase)
                    selectVisibleUser(resultContext)
                }
                PageKind.SEARCH_RESULTS -> {
                    phase = AutomationPhase.WAITING_FOR_SEARCH_RESULTS
                    AutomationStore.publishPhase(phase)
                    selectUserTab(resultContext)
                }
                else -> Unit
            }
            return
        }

        logger.info("search_submitted", attributes = mapOf("route" to submitResult.route))
        await(
            nextPhase = AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
            timeoutDescription = "Search results were not detected after submitting the keyword",
            resetRecoveryBudget = !preserveTimeoutRecoveryBudget,
        )
    }

    private suspend fun selectUserTab(context: ScreenContext) {
        phase = AutomationPhase.SELECTING_USER_TAB
        AutomationStore.publishPhase(phase)
        // The previous observation can be stale after the result tab strip animates. Resolve the
        // live tree immediately before selecting or clicking a category.
        val liveContext = currentWindowContext() ?: context
        val selection = selector.select(liveContext, DouyinSelectors.userTab)
        val candidate = selection.node
        val outcome = if (candidate == null) {
            ActionOutcome.failure(selection.reasons.joinToString())
        } else if (!UserTabCandidatePolicy.isSafeCandidate(candidate, selection, liveContext)) {
            logger.warn(
                "user_tab_candidate_rejected",
                message = "A user-like label was not an exact, fully visible category tab",
                attributes = mapOf("bounds" to candidate.bounds, "reasons" to selection.reasons.joinToString()),
            )
            ActionOutcome.failure("User label was not an exact fully visible tab")
        } else {
            logger.info(
                "user_tab_candidate",
                attributes = mapOf("text" to candidate.text.orEmpty(), "bounds" to candidate.bounds),
            )
            withLiveNode(candidate) { liveNode -> gestures.click(liveNode, candidate.bounds) }
        }
        if (!outcome.succeeded) {
            val reveal = revealUserTab(context)
            if (reveal.succeeded) {
                logger.info(
                    "user_tab_reveal_requested",
                    attributes = mapOf(
                        "route" to reveal.route,
                        "attempt" to userTabRevealAttempts,
                    ),
                )
                await(
                    nextPhase = AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
                    timeoutDescription = "The User tab did not appear after scrolling the result categories",
                )
                return
            }
            pause("Could not find the User tab in search results: ${outcome.reason}; ${reveal.reason}")
            return
        }
        logger.info("user_tab_opened", attributes = mapOf("route" to outcome.route))

        // Some Douyin builds switch the ViewPager without emitting a follow-up accessibility
        // content-change event, and the first tree after the click can still be the old results
        // page while the new tab is animating. Poll briefly for a verified USER_RESULTS
        // post-condition so the run does not wait until the step timeout. This does not bypass
        // verification or click a result blindly.
        for (attempt in 1..TuningConstants.NavigationFlow.USER_RESULTS_POSTCONDITION_ATTEMPTS) {
            delay(if (attempt == 1) 450L else TuningConstants.NavigationFlow.USER_RESULTS_POSTCONDITION_INTERVAL_MS)
            val postTabContext = currentWindowContext() ?: continue
            val postTabDetection = pageDetector.detect(postTabContext)
            logger.info(
                "user_tab_postcondition",
                attributes = mapOf(
                    "attempt" to attempt,
                    "page" to postTabDetection.kind.name,
                    "confidence" to postTabDetection.confidence,
                    "nodes" to postTabContext.nodes.size,
                ),
            )
            if (postTabDetection.kind == PageKind.USER_RESULTS) {
                latestContext = postTabContext
                AutomationStore.publishObservation(postTabDetection)
                selectVisibleUser(postTabContext)
                return
            }
        }
        await(
            nextPhase = AutomationPhase.WAITING_FOR_USER_RESULTS,
            timeoutDescription = "User search results were not detected after choosing the User tab",
        )
    }

    private suspend fun revealUserTab(context: ScreenContext): ActionOutcome {
        userTabRevealAttempts += 1
        val liveContext = currentWindowContext() ?: context
        val strip = selector.select(liveContext, DouyinSelectors.searchResultTabStrip).node

        // ACTION_SCROLL_FORWARD is intentionally not used here: Android exposes it as a
        // page-sized operation on some HorizontalScrollView implementations, which can jump
        // past 用户 to the end of the category list. Instead, use a bounded gesture inside the
        // semantic strip viewport. The travel is derived from the current viewport width and
        // clamped to 70..200 physical pixels, so it remains a small one-tab reveal on different
        // displays and never starts on the trailing grid/filter controls.
        val screenWidth = liveContext.screenSize.width.coerceAtLeast(1)
        val screenHeight = liveContext.screenSize.height.coerceAtLeast(1)
        val viewportLeft = strip?.bounds?.left?.coerceIn(0, screenWidth - 1)
            ?: (screenWidth * 0.04f).toInt()
        val viewportRight = strip?.bounds?.right?.coerceIn(viewportLeft + 1, screenWidth)
            ?: (screenWidth * 0.82f).toInt().coerceAtLeast(viewportLeft + 1)
        val viewportTop = strip?.bounds?.top?.coerceIn(0, screenHeight - 1)
            ?: (screenHeight * 0.10f).toInt()
        val viewportBottom = strip?.bounds?.bottom?.coerceIn(viewportTop + 1, screenHeight)
            ?: (screenHeight * 0.18f).toInt().coerceAtLeast(viewportTop + 1)
        val viewportWidth = (viewportRight - viewportLeft).coerceAtLeast(1)
        val travelPx = (viewportWidth * 0.20f).toInt().coerceIn(70, 200)
        val edgeInsetPx = (viewportWidth * 0.16f).toInt().coerceAtLeast(12)
        val startPx = (viewportRight - edgeInsetPx).coerceAtLeast(viewportLeft + travelPx + 1)
        val endPx = (startPx - travelPx).coerceAtLeast(viewportLeft + edgeInsetPx)
        val centerYPx = (viewportTop + viewportBottom) / 2
        val startX = startPx.toFloat() / screenWidth
        val endX = endPx.toFloat() / screenWidth
        val centerY = centerYPx.toFloat() / screenHeight

        logger.info(
            "user_tab_bounded_swipe",
            attributes = mapOf(
                "attempt" to userTabRevealAttempts,
                "travel_px" to travelPx,
                "viewport_left" to viewportLeft,
                "viewport_right" to viewportRight,
            ),
        )
        return swipeNormalizedGuarded(
            startX = startX,
            startY = centerY,
            endX = endX,
            endY = centerY,
            durationMs = 450L,
            tag = "user_tab_reveal",
        )
    }

    private suspend fun selectVisibleUser(
        context: ScreenContext,
        minimumAnchorTop: Float? = null,
    ) {
        queryTransitionHandled = false
        val maxUsers = activeTaskSnapshot?.maxUsers
        val userLimitReached = maxUsers != null && processedUserIdentityRecords.size >= maxUsers
        // Keep marker inspection after the existing higher-precedence terminal cases. This is a
        // screen-content read rather than a route effect, and the router remains pure.
        val accountHelpOnly = !userLimitReached &&
            !remoteResumePending &&
            minimumAnchorTop == null &&
            UserResultMarkers.accountHelpOnly(context)
        if (
            userSelectionFlow.onSelectionRequested(
                state = UserSelectionFlowState(
                    maxUsers = maxUsers,
                    processedUserCount = processedUserIdentityRecords.size,
                    remoteResumePending = remoteResumePending,
                    hasViewportAnchor = minimumAnchorTop != null,
                    accountHelpOnly = accountHelpOnly,
                ),
                context = context,
            )
        ) {
            return
        }
        phase = AutomationPhase.SELECTING_USER_RESULT
        AutomationStore.publishPhase(phase)
        // Prefer the structural row route whenever it is available. It deliberately targets the
        // name/profile content area, not the avatar: a live badge on an avatar can route to a live
        // room instead of the user's profile.
        var rowContext = context
        var rowMatch: StructuralUserRowMatch? = null
        // The P0 search route has already verified that the User tab is active. On the known
        // custom-rendered variant, eight full accessibility-tree reads can consume more than ten
        // seconds even though no structural row will ever appear. Probe that structure twice to
        // cover a settling frame, then enter the independently verified OCR fallback. Other
        // task types keep the longer bounded retry window.
        val useFastP0RowFallback = minimumAnchorTop == null && isSearchTargetProfileCommentTask()
        val rowProbeAttempts = if (useFastP0RowFallback) {
            TuningConstants.NavigationFlow.P0_USER_ROW_POSTCONDITION_ATTEMPTS
        } else {
            TuningConstants.NavigationFlow.USER_ROW_POSTCONDITION_ATTEMPTS
        }
        for (attempt in 1..rowProbeAttempts) {
            if (attempt > 1) {
                delay(
                    if (useFastP0RowFallback) {
                        TuningConstants.NavigationFlow.P0_USER_ROW_POSTCONDITION_INTERVAL_MS
                    } else {
                        TuningConstants.NavigationFlow.USER_ROW_POSTCONDITION_INTERVAL_MS
                    },
                )
            }
            val liveContext = currentWindowContext() ?: rowContext
            val liveDetection = pageDetector.detect(liveContext)
            if (liveDetection.kind != PageKind.USER_RESULTS) {
                logger.info(
                    "user_result_row_postcondition",
                    attributes = mapOf("attempt" to attempt, "page" to liveDetection.kind.name, "nodes" to liveContext.nodes.size),
                )
                break
            }
            rowContext = liveContext
            rowMatch = minimumAnchorTop?.let { top ->
                StructuralUserRowDetector.findAfter(liveContext, top)
            } ?: StructuralUserRowDetector.find(liveContext)
            logger.info(
                "user_result_row_postcondition",
                attributes = mapOf(
                    "attempt" to attempt,
                    "page" to liveDetection.kind.name,
                    "nodes" to liveContext.nodes.size,
                    "anchor_count" to StructuralUserRowDetector.anchorCount(liveContext),
                    "matched" to (rowMatch != null),
                ),
            )
            if (rowMatch != null) break
        }
        // A selected User tab can briefly expose a visible ViewPager whose child result tree is
        // entirely hidden. That is a transition observation, not evidence that the first card is
        // absent. Wait only for that state to settle, then reuse the same structural verifier.
        if (rowMatch == null && useFastP0RowFallback && UserResultsViewportTransitionDetector.isSettling(rowContext)) {
            for (attempt in 1..TuningConstants.NavigationFlow.P0_USER_RESULTS_VIEWPORT_SETTLE_ATTEMPTS) {
                delay(TuningConstants.NavigationFlow.P0_USER_RESULTS_VIEWPORT_SETTLE_INTERVAL_MS)
                val refreshed = currentWindowContext() ?: continue
                val refreshedDetection = pageDetector.detect(refreshed)
                if (refreshedDetection.kind != PageKind.USER_RESULTS) break
                rowContext = refreshed
                rowMatch = StructuralUserRowDetector.find(refreshed)
                logger.info(
                    "p0_user_results_viewport_settle",
                    attributes = mapOf(
                        "attempt" to attempt,
                        "still_settling" to UserResultsViewportTransitionDetector.isSettling(refreshed),
                        "anchor_count" to StructuralUserRowDetector.anchorCount(refreshed),
                        "matched" to (rowMatch != null),
                    ),
                )
                if (rowMatch != null || !UserResultsViewportTransitionDetector.isSettling(refreshed)) break
            }
        }
        // Some current Douyin builds leave the User tab visually selected but expose an
        // off-screen ViewPager subtree to accessibility. Search-target comment tasks and the
        // first B-end private-message row share one OCR-assisted geometry fallback: verify the
        // same first card twice and never tap a later result when that proof is unavailable.
        if (rowMatch == null && minimumAnchorTop == null && allowsFirstVisibleUserOcrFallback()) {
            resolveP0FirstUserOcrFallback(rowContext)?.let { resolution ->
                rowContext = resolution.context
                rowMatch = resolution.match
            }
        }
        if (rowMatch != null) {
            rowContext = enrichUserResultIdentityContext(rowContext, rowMatch!!)
            var identity = UserResultIdentityExtractor.extract(rowContext, rowMatch!!)
            // A custom-rendered row may be captured between two RecyclerView frames. Never tap a
            // row without a stable identity: retry the live snapshot/OCR briefly, then hand off
            // instead of risking a duplicate blank-message probe for an unknown account.
            repeat(TuningConstants.NavigationFlow.IDENTITY_RETRY_ATTEMPTS - 1) { retry ->
                if (identity != null) return@repeat
                delay(TuningConstants.NavigationFlow.IDENTITY_RETRY_INTERVAL_MS)
                val refreshed = currentWindowContext() ?: return@repeat
                val refreshedMatch = StructuralUserRowDetector.findAfter(
                    refreshed,
                    (minimumAnchorTop ?: (rowMatch!!.anchor.bounds.top - 1).toFloat()),
                ) ?: StructuralUserRowDetector.find(refreshed)
                if (refreshedMatch != null) {
                    rowContext = enrichUserResultIdentityContext(refreshed, refreshedMatch)
                    rowMatch = refreshedMatch
                    identity = UserResultIdentityExtractor.extract(rowContext, refreshedMatch)
                    logger.info(
                        "user_result_identity_retry",
                        attributes = mapOf("attempt" to retry + 2, "resolved" to (identity != null)),
                    )
                }
            }
            if (identity != null) {
                val duplicateReason = processedUserIdentityMatchReason(identity)
                val duplicate = duplicateReason != null
                val identityFingerprint = identity.fingerprint
                logger.info(
                    "user_result_identity",
                    attributes = mapOf(
                        "source" to identity.source.name,
                        "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
                        "duplicate" to duplicate,
                        "duplicate_reason" to (duplicateReason ?: "none"),
                        "visible_token_count" to identity.visibleTokens.size,
                    ),
                )
                if (duplicate) {
                    AutomationStore.recordUserTaskEvent(
                        identityFingerprint = identityFingerprint,
                        outcome = UserTaskRecord.Outcome.DUPLICATE_SKIPPED,
                        reason = "The overlapping viewport exposed an already processed user",
                        remoteUserKey = identity.key,
                        displayName = identity.displayName,
                    )
                    lastProcessedUserAnchorBottom = rowMatch!!.anchor.bounds.bottom.toFloat()
                    skipDuplicateUser(rowContext, rowMatch!!)
                    return
                }
                val blockedEvaluation = activeTaskSnapshot?.let { snapshot ->
                    BlockedKeywordEvaluator.evaluate(
                        text = UserResultText(
                            displayName = identity.displayName,
                            accountHandle = identity.accountHandle,
                            rowMetadata = identity.stableMetadata.toList(),
                            ocrText = identity.visibleTokens.toList(),
                        ),
                        blockedKeywords = snapshot.normalizedBlockedKeywords,
                    )
                }
                if (blockedEvaluation?.blocked == true) {
                    logger.info(
                        "user_result_blocked_keyword",
                        message = "The visible user matched a task block rule; skipping before opening the profile",
                        attributes = mapOf(
                            "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
                            "matched_count" to blockedEvaluation.matches.size,
                        ),
                    )
                    AutomationStore.recordUserTaskEvent(
                        identityFingerprint = identityFingerprint,
                        outcome = UserTaskRecord.Outcome.FILTERED_BY_KEYWORD,
                        reason = "Blocked keywords matched: ${blockedEvaluation.matchedKeywords.joinToString()}",
                        remoteUserKey = identity.key,
                        displayName = identity.displayName,
                    )
                    // A filtered row is handled too. Retaining its identity makes the next
                    // viewport anchor continue after it instead of exposing it again.
                    processedUserIdentities.add(identity.key)
                    processedUserIdentityRecords += identity
                    processedIdentityFingerprints.add(identityFingerprint)
                    persistTaskCheckpoint()
                    lastProcessedUserAnchorBottom = rowMatch!!.anchor.bounds.bottom.toFloat()
                    skipFilteredUser(rowContext, rowMatch!!)
                    return
                }
                val isCommentTask = activeTaskSnapshot?.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE
                if (!isCommentTask) {
                    when (
                        AutomationStore.recordUserTaskStarted(
                            identityFingerprint = identityFingerprint,
                            remoteUserKey = identity.key,
                            displayName = identity.displayName,
                            messageContent = currentTaskMessageContent(),
                        )
                    ) {
                        DailyUserAdmission.ADMITTED -> Unit
                        DailyUserAdmission.DAILY_UNIQUE_USER_LIMIT_REACHED -> {
                            pause("今日已处理 ${AutomationExecutionLimits.MAX_DAILY_UNIQUE_USERS} 名不同用户，任务已暂停，明日可从检查点继续")
                            return
                        }

                        DailyUserAdmission.NO_ACTIVE_TASK -> {
                            failTaskWithoutManualHandoff("任务记录不可用，已停止继续处理用户")
                            return
                        }
                    }
                    processedUserIdentities.add(identity.key)
                    processedUserIdentityRecords += identity
                    processedIdentityFingerprints.add(identityFingerprint)
                    persistTaskCheckpoint()
                } else {
                    // This identity is the single source profile for a comment task, not a
                    // private-message candidate. Its comment-candidate ledger is persisted by
                    // the comment runtime; retaining it here would make a safe initial restart
                    // skip the only source profile and try a different search result.
                    logger.info("comment_source_profile_identity_not_persisted")
                }
                currentUserIdentityFingerprint = identityFingerprint
                currentUserDisplayName = identity.displayName
                currentUserDisplayNameSource = identity.source
                if (isCommentTask) {
                    // P4-B reads the target profile's comment surface; it does not yet create a
                    // per-comment private-message record. Keep the profile runner's audit slot
                    // empty so an unfinished comment read cannot appear as a sent DM.
                    currentUserIdentityFingerprint = null
                    currentUserDisplayName = null
                    currentUserDisplayNameSource = null
                }
            } else {
                currentUserIdentityFingerprint = null
                currentUserDisplayName = null
                currentUserDisplayNameSource = null
                logger.warn(
                    "user_result_identity_unavailable",
                    message = "The row has no stable name or account identifier; bounded paging fallback will be used",
                    attributes = mapOf("anchor_top" to rowMatch!!.anchor.bounds.top),
                )
                AutomationStore.recordUserTaskEvent(
                    identityFingerprint = null,
                    outcome = UserTaskRecord.Outcome.IDENTITY_UNAVAILABLE,
                    reason = "The row had no stable account identity",
                )
                skipUnidentifiedUser(rowContext, rowMatch!!)
                return
            }
            val followState = StructuralUserRowDetector.followActionState(rowContext, rowMatch!!)
            logger.info(
                "user_result_follow_action",
                attributes = mapOf(
                    "state" to followState.name,
                    "anchor_bounds" to rowMatch!!.anchor.bounds,
                ),
            )
            if (followState == UserFollowActionState.FOLLOW_BACK) {
                AutomationStore.recordUserTaskFinished(
                    identityFingerprint = currentUserIdentityFingerprint,
                    outcome = UserTaskRecord.Outcome.FOLLOW_BACK_SKIPPED,
                    reason = "The result exposed a follow-back action",
                )
                currentUserIdentityFingerprint = null
                currentUserDisplayName = null
                currentUserDisplayNameSource = null
                skipFollowBackUser(rowContext, rowMatch!!)
                return
            }
            lastProcessedUserAnchorBottom = rowMatch!!.anchor.bounds.bottom.toFloat()
        } else {
            // A semantic fallback has no reliable row boundary. The next-user helper will use a
            // bounded scroll in this rare case rather than risking a duplicate tap.
            lastProcessedUserAnchorBottom = null
        }
        if (isSearchTargetProfileCommentTask()) {
            // Accessibility callbacks can be delivered late. Capture the click boundary before
            // dispatching the gesture so a prior run's profile tree cannot satisfy the new
            // task's WAITING_FOR_PROFILE phase.
            commentProfileHandoffNotBeforeMillis = System.currentTimeMillis()
        }
        val outcome = if (rowMatch != null) {
            logger.info(
                "user_result_row_match",
                attributes = mapOf("source" to rowMatch!!.source.name, "anchor_count" to StructuralUserRowDetector.anchorCount(rowContext)),
            )
            tapUserRowContent(rowMatch!!.row, rowContext)
        } else {
            if (UserResultMarkers.accountHelpOnly(rowContext)) {
                logger.info("user_results_account_help_marker_only")
                if (advanceToNextQueryIfAvailable("account_help_marker")) return
                completeTaskAtQueryEnd()
                return
            }
            // A search-target comment task selects exactly one profile, so it must never turn an
            // ambiguous first result into a list swipe. Preserve the live accessibility structure
            // for diagnosis, then end this bounded probe before any later result can be selected.
            if (isSearchTargetProfileCommentTask()) {
                saveNodeDump(rowContext, "comment_first_user_ambiguous")
                failTaskWithoutManualHandoff(
                    "目标用户搜索结果缺少可验证的行结构；任务已安全结束，未处理后续用户",
                )
                return
            }
            logger.warn(
                "user_result_row_match_failed",
                message = "No wide user-result row matched the follow-button anchor; semantic name selection will be attempted",
                attributes = mapOf("anchor_count" to StructuralUserRowDetector.anchorCount(rowContext), "nodes" to rowContext.nodes.size),
            )
            clickSelector(rowContext, DouyinSelectors.userResult)
        }
        if (!outcome.succeeded) {
            commentProfileHandoffNotBeforeMillis = 0L
            skipMessageSendFailure(
                "No unambiguous visible user result was found after bounded retries",
                failurePage = PageKind.USER_RESULTS,
            )
            return
        }
        logger.info("user_result_opened", attributes = mapOf("route" to outcome.route))
        await(
            nextPhase = AutomationPhase.WAITING_FOR_PROFILE,
            timeoutDescription = "A user profile was not detected after choosing the result",
        )
        scheduleProfilePostconditionCheck()
    }

    /**
     * OCR can vary punctuation or a single character between two screenshots of the same row.
     * Exact keys remain the first choice; account handles are authoritative, while name matching
     * requires stable metadata overlap to avoid collapsing two different accounts with the same
     * display name.
     */
    private fun processedUserIdentityMatchReason(identity: UserResultIdentity): String? =
        UserResultIdentityMatcher.processedMatchReason(
            identity = identity,
            processedIdentityFingerprints = processedIdentityFingerprints,
            legacyProcessedIdentityHashes = legacyProcessedIdentityHashes,
            processedIdentities = processedUserIdentityRecords,
        )

    private suspend fun handleAccountHelpOnlyEnd() {
        logger.info(
            "user_results_account_help_marker",
            message = "Douyin rendered the account-help row without another selectable result; treating it as a bounded end-of-results signal",
        )
        if (advanceToNextQueryIfAvailable("account_help_marker")) return
        completeTaskAtQueryEnd()
    }

    private fun persistTaskCheckpoint() {
        val snapshot = activeTaskSnapshot ?: return
        val queryIndex = TaskCheckpointQueryIndexPolicy.normalize(snapshot, taskQueryIndex) ?: return
        AutomationStore.saveTaskCheckpoint(
            TaskCheckpoint(
                taskId = AutomationStore.getCurrentTaskId() ?: snapshot.taskId,
                snapshot = snapshot,
                queryIndex = queryIndex,
                processedIdentityFingerprints = processedIdentityFingerprints.toList(),
                processedCommentIdentityFingerprints = processedCommentCandidateFingerprints.toList(),
                legacyProcessedIdentityHashes = legacyProcessedIdentityHashes.toList(),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Transfers a verified search-target profile, or a current ordinary video, to the isolated
     * comment runner.
     *
     * Search-target comment tasks intentionally share the resilient search/User-tab navigation
     * with B-end private-message tasks. From this point onward the flows must diverge completely:
     * the comment runner opens a work, reads one bounded comment viewport, and only then performs
     * its avatar-only private-message safety probe. Keeping the transfer in one function gives
     * the diagnostic log an unambiguous boundary and prevents the generic profile runner from
     * being selected accidentally.
     */
    private suspend fun handoffCommentProfileObservation(
        context: ScreenContext,
        detection: PageDetection,
        source: String,
    ): Boolean {
        val snapshot = activeTaskSnapshot ?: return false
        if (snapshot.taskType != AutomationTaskType.COMMENT_PRIVATE_MESSAGE) return false
        val commentConfig = snapshot.commentConfig
        if (commentConfig == null) {
            logger.error(
                "comment_profile_handoff_invalid",
                message = "Comment task has no comment configuration",
                attributes = mapOf("source" to source),
            )
            failTaskWithoutManualHandoff("评论私信任务配置缺失，无法进入评论区流程")
            return true
        }
        // The generic profile postcondition polls while the user-result animation is still
        // settling. A result page is never a valid handoff surface, even if the controller has
        // already advanced to WAITING_FOR_PROFILE. CURRENT_PROFILE additionally permits one
        // verified non-live, non-ad video already prepared by the operator.
        if (!isCurrentCommentEntrySurface(context, detection, commentConfig)) return false
        // Search-target tasks may be launched while Douyin still displays the profile from a
        // previous run. That is not the profile selected by this task. Accept a profile handoff
        // only after the user-result action has advanced the controller to WAITING_FOR_PROFILE;
        // CURRENT_PROFILE reaches the same phase explicitly when the operator taps Resume.
        if (phase != AutomationPhase.WAITING_FOR_PROFILE) {
            logger.info(
                "comment_profile_handoff_deferred",
                attributes = mapOf(
                    "source" to source,
                    "entry_mode" to commentConfig.entryMode.name,
                    "phase" to phase.name,
                ),
            )
            return false
        }
        if (commentConfig.entryMode == CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE &&
            context.capturedAtMillis < commentProfileHandoffNotBeforeMillis
        ) {
            logger.info(
                "comment_profile_handoff_stale_snapshot",
                attributes = mapOf(
                    "source" to source,
                    "captured_before_selection" to true,
                ),
            )
            return false
        }
        if (!commentRuntime.isRunning) {
            if (commentRuntime.unfreeze()) {
                logger.info(
                    "comment_runtime_unfrozen_at_handoff",
                    attributes = mapOf("source" to source, "page" to detection.kind.name),
                )
            } else {
                val snapshot = pendingCommentRuntimeSnapshot ?: commentConfig
                pendingCommentRuntimeSnapshot = null
                commentRuntime.start(snapshot)
                logger.info(
                    "comment_runtime_started_at_profile_handoff",
                    attributes = mapOf("source" to source, "page" to detection.kind.name),
                )
            }
        }

        timeoutJob?.cancel()
        if (!commentProfileHandoffObserved) {
            commentProfileHandoffObserved = true
            logger.info(
                "comment_profile_handoff",
                attributes = mapOf(
                    "source" to source,
                    "page" to detection.kind.name,
                    "confidence" to detection.confidence,
                    "nodes" to context.nodes.size,
                    "ocr_blocks" to context.ocrBlocks.size,
                ),
            )
        }
        commentRuntime.onObserved(context, detection)
        return true
    }

    private fun isCommentPrivateMessageTask(): Boolean =
        activeTaskSnapshot?.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE &&
            activeTaskSnapshot?.commentConfig != null

    private fun isCurrentProfileCommentTask(): Boolean =
        activeTaskSnapshot?.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE &&
            activeTaskSnapshot?.commentConfig?.entryMode == CommentPrivateMessageEntryMode.CURRENT_PROFILE

    /**
     * A direct entry is safe only on a verified profile or on a normal video that exposes the
     * strict comment affordance. Live/risk/login pages are classified before this point, while a
     * startup/download promotion is rejected by the existing ad detector instead of being treated
     * as a video merely because it has a right-side action rail.
     */
    private fun isCurrentCommentEntrySurface(
        context: ScreenContext,
        detection: PageDetection,
        commentConfig: CommentPrivateMessageSnapshot,
    ): Boolean {
        if (detection.kind == PageKind.USER_PROFILE) return true
        if (commentConfig.entryMode != CommentPrivateMessageEntryMode.CURRENT_PROFILE) return false
        if (detection.kind !in setOf(PageKind.HOME, PageKind.UNKNOWN)) return false
        if (TransientOverlayDetector.findStartupAd(context) != null) return false
        val observation = CommentEntrySignalDetector.observe(
            context,
            skipPinnedVideos = commentConfig.skipPinnedVideos,
        )
        return observation.hasVideoSurface && observation.hasCommentEntry
    }

    /**
     * Every SEARCH_TARGET_PROFILE comment task selects exactly one source profile during search
     * navigation before handing off to the comment runtime. The per-video/per-user bounds belong
     * to the comment runtime's loop, not to this navigation step, so the OCR-assisted first-card
     * fallback and fail-fast (never swipe a list) semantics apply to all such tasks.
     */
    private fun isSearchTargetProfileCommentTask(): Boolean {
        val snapshot = activeTaskSnapshot ?: return false
        return snapshot.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE &&
            snapshot.commentConfig?.entryMode == CommentPrivateMessageEntryMode.SEARCH_TARGET_PROFILE
    }

    private fun allowsFirstVisibleUserOcrFallback(): Boolean {
        val snapshot = activeTaskSnapshot ?: return false
        return snapshot.taskType == AutomationTaskType.PROFILE_PRIVATE_MESSAGE ||
            isSearchTargetProfileCommentTask()
    }

    /**
     * Resolves only P0's first source user when the live accessibility tree is the known
     * off-screen ViewPager variant. OCR is a verification aid, not a direct text click: two
     * screenshots must agree on the first card's account marker and geometry before a synthetic
     * row can authorise the existing avatar-excluding, bounded content-area gesture.
     */
    private suspend fun resolveP0FirstUserOcrFallback(
        context: ScreenContext,
    ): P0FirstUserOcrResolution? {
        val firstContext = captureContextWithOcr(
            base = context,
            tag = "p0_first_user_ocr_one",
            region = OcrRegion.USER_RESULTS,
        ) ?: return null
        val firstAnalysis = OcrUserResultRowDetector.analyzeFirstVisible(firstContext)
        val first = firstAnalysis.match
        logger.info(
            "p0_first_user_ocr_probe",
            attributes = mapOf(
                "sample" to 1,
                "ocr_blocks" to firstContext.ocrBlocks.size,
                "account_markers" to firstAnalysis.accountMarkerCount,
                "matched" to (first != null),
                "failure_reason" to (firstAnalysis.failureReason?.name ?: "none"),
                "follow_diagnostics" to firstAnalysis.followDiagnostics,
            ),
        )
        if (first == null) return null

        delay(TuningConstants.NavigationFlow.P0_FIRST_USER_OCR_STABILITY_DELAY_MS)
        val refreshedBase = currentWindowContext() ?: return null
        if (pageDetector.detect(refreshedBase).kind != PageKind.USER_RESULTS) {
            logger.warn(
                "p0_first_user_ocr_surface_changed",
                message = "The result surface changed before the OCR fallback could be confirmed",
            )
            return null
        }
        val secondContext = captureContextWithOcr(
            base = refreshedBase,
            tag = "p0_first_user_ocr_two",
            region = OcrRegion.USER_RESULTS,
        ) ?: return null
        val secondAnalysis = OcrUserResultRowDetector.analyzeFirstVisible(secondContext)
        val second = secondAnalysis.match
        val stable = second != null && first.agreesWith(second)
        logger.info(
            "p0_first_user_ocr_probe",
            attributes = mapOf(
                "sample" to 2,
                "ocr_blocks" to secondContext.ocrBlocks.size,
                "account_markers" to secondAnalysis.accountMarkerCount,
                "matched" to (second != null),
                "stable" to stable,
                "failure_reason" to (secondAnalysis.failureReason?.name ?: "none"),
                "follow_diagnostics" to secondAnalysis.followDiagnostics,
            ),
        )
        if (!stable) return null
        logger.warn(
            "p0_first_user_ocr_geometry_fallback",
            message = "The first custom-rendered user card was verified twice; using its bounded content area",
            attributes = mapOf("row_height" to second.rowBounds.height),
        )
        return P0FirstUserOcrResolution(
            context = secondContext,
            match = second.asStructuralMatch(),
        )
    }

    private data class P0FirstUserOcrResolution(
        val context: ScreenContext,
        val match: StructuralUserRowMatch,
    )


    /** Poll the profile transition independently of accessibility callbacks, which OEM builds may drop. */
    private fun scheduleProfilePostconditionCheck() {
        // An accessibility event may already have handed the verified profile to the isolated
        // comment runtime before this delayed fallback is scheduled. Do not start a second
        // poller that repeatedly re-reads the full target tree while the comment flow is moving.
        if (commentRuntime.isRunning) return
        profilePostconditionJob?.cancel()
        profilePostconditionJob = scope.launch {
            repeat(TuningConstants.NavigationFlow.PROFILE_POSTCONDITION_ATTEMPTS) { attempt ->
                delay(if (attempt == 0) TuningConstants.NavigationFlow.PROFILE_POSTCONDITION_INITIAL_DELAY_MS else TuningConstants.NavigationFlow.PROFILE_POSTCONDITION_INTERVAL_MS)
                var handedOff = false
                mutex.withLock {
                    if (!taskActive || phase != AutomationPhase.WAITING_FOR_PROFILE) return@withLock
                    val context = currentWindowContext() ?: return@withLock
                    val detection = pageDetector.detect(context)
                    logger.info(
                        "user_profile_postcondition",
                        attributes = mapOf(
                            "attempt" to (attempt + 1),
                            "page" to detection.kind.name,
                            "confidence" to detection.confidence,
                            "nodes" to context.nodes.size,
                        ),
                    )
                    // Comment tasks keep the profile-to-comment route in their isolated
                    // runtime. A stale snapshot is deliberately not a valid handoff, but it is
                    // also never permission to enter the legacy profile → private-message flow.
                    // Keep polling until a fresh verified profile arrives or the existing
                    // bounded profile watchdog resolves the route.
                    if (isCommentPrivateMessageTask()) {
                        when (detection.kind) {
                            PageKind.USER_PROFILE -> {
                                if (handoffCommentProfileObservation(context, detection, source = "profile_postcondition")) {
                                    handedOff = true
                                }
                            }

                            PageKind.HUMAN_INTERVENTION ->
                                pause("A verification or risk screen appeared before opening the comment workflow; manual handoff required")

                            PageKind.LOGIN ->
                                pause("Douyin login is required before opening the comment workflow")

                            else -> Unit
                        }
                        return@withLock
                    }
                    when (detection.kind) {
                        PageKind.USER_PROFILE -> openPrivateMessage(context)
                        PageKind.HUMAN_INTERVENTION -> pause("A verification or risk screen appeared before opening private messages; manual handoff required")
                        PageKind.LOGIN -> pause("Douyin login is required before opening private messages")
                        else -> Unit
                    }
                }
                if (handedOff) {
                    profilePostconditionJob = null
                    return@launch
                }
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_PROFILE) return@launch
            }
        }
    }

    /** Skip an already processed overlapping row, or page forward when it is the last row visible. */
    private suspend fun skipDuplicateUser(context: ScreenContext, match: StructuralUserRowMatch) {
        logger.warn(
            "user_result_duplicate_skipped",
            message = "The visible row was already processed; continuing from the last known user identity",
            attributes = mapOf("anchor_top" to match.anchor.bounds.top),
        )
        val nextVisible = StructuralUserRowDetector.findAfter(context, match.anchor.bounds.bottom.toFloat())
        if (nextVisible != null) {
            phase = AutomationPhase.WAITING_FOR_USER_RESULTS
            AutomationStore.publishPhase(phase)
            selectVisibleUser(context, minimumAnchorTop = match.anchor.bounds.bottom.toFloat())
            return
        }

        val scroll = swipeToNextUserPage("duplicate_user_skip")
        if (!scroll.succeeded) {
            failTaskWithoutManualHandoff("Could not advance after skipping an already processed user")
            return
        }
        val nextContext = awaitUserResultsAfterScroll("duplicate_user_skip")
        if (nextContext == null) {
            if (queryTransitionHandled) return
            failTaskWithoutManualHandoff("The next user result page was not detected after skipping an already processed user")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        selectAfterViewportAnchor(nextContext, "duplicate_user_skip")
    }

    /**
     * A row without a stable identity cannot be safely opened or audited. It is still a
     * recoverable row-level failure: advance using its structural anchor rather than stopping the
     * whole task for manual selection.
     */
    private suspend fun skipUnidentifiedUser(context: ScreenContext, match: StructuralUserRowMatch) {
        logger.warn(
            "user_result_identity_unavailable_skipped",
            message = "Skipping a row whose identity could not be resolved after bounded retries",
            attributes = mapOf("anchor_top" to match.anchor.bounds.top),
        )
        val nextVisible = StructuralUserRowDetector.findAfter(context, match.anchor.bounds.bottom.toFloat())
        if (nextVisible != null) {
            phase = AutomationPhase.WAITING_FOR_USER_RESULTS
            AutomationStore.publishPhase(phase)
            selectVisibleUser(context, minimumAnchorTop = match.anchor.bounds.bottom.toFloat())
            return
        }
        val scroll = swipeToNextUserPage("identity_unavailable_skip")
        if (!scroll.succeeded) {
            failTaskWithoutManualHandoff("Could not advance after skipping a user with unavailable identity")
            return
        }
        val nextContext = awaitUserResultsAfterScroll("identity_unavailable_skip")
        if (nextContext == null) {
            if (queryTransitionHandled) return
            failTaskWithoutManualHandoff("The next user result page was not detected after an identity failure")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        selectAfterViewportAnchor(nextContext, "identity_unavailable_skip")
    }

    /** Skip a row matched by the task's business filter without opening its profile. */
    private suspend fun skipFilteredUser(context: ScreenContext, match: StructuralUserRowMatch) {
        logger.info(
            "user_result_filtered_next_requested",
            attributes = mapOf("anchor_top" to match.anchor.bounds.top),
        )
        val nextVisible = StructuralUserRowDetector.findAfter(context, match.anchor.bounds.bottom.toFloat())
        if (nextVisible != null) {
            phase = AutomationPhase.WAITING_FOR_USER_RESULTS
            AutomationStore.publishPhase(phase)
            selectVisibleUser(context, minimumAnchorTop = match.anchor.bounds.bottom.toFloat())
            return
        }

        val scroll = swipeToNextUserPage("blocked_keyword_skip")
        if (!scroll.succeeded) {
            failTaskWithoutManualHandoff("Could not advance after skipping a blocked-keyword user")
            return
        }
        val nextContext = awaitUserResultsAfterScroll("blocked_keyword_skip")
        if (nextContext == null) {
            if (queryTransitionHandled) return
            failTaskWithoutManualHandoff("The next user result page was not detected after skipping a blocked-keyword user")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        selectAfterViewportAnchor(nextContext, "blocked_keyword_skip")
    }

    /**
     * A “回关” button means the account follows us but we do not follow it. Do not open that row;
     * first try the next visible row in the current viewport, then use a small bounded scroll only
     * when no subsequent row is exposed by the tree.
     */
    private suspend fun skipFollowBackUser(context: ScreenContext, match: StructuralUserRowMatch) {
        restrictedUserSkips += 1
        logger.warn(
            "user_result_follow_back_skipped",
            message = "The first visible user exposes a 回关 action; skipping without opening the profile",
            attributes = mapOf("skipped_users" to restrictedUserSkips, "anchor_bounds" to match.anchor.bounds),
        )
        val nextVisible = StructuralUserRowDetector.findAfter(context, match.anchor.bounds.bottom.toFloat())
        if (nextVisible != null) {
            phase = AutomationPhase.WAITING_FOR_USER_RESULTS
            AutomationStore.publishPhase(phase)
            selectVisibleUser(context, minimumAnchorTop = match.anchor.bounds.bottom.toFloat())
            return
        }

        val scroll = swipeToNextUserPage("follow_back_skip")
        logger.info(
            "user_result_follow_back_next_requested",
            attributes = mapOf("route" to scroll.route, "skipped_users" to restrictedUserSkips),
        )
        if (!scroll.succeeded) {
            failTaskWithoutManualHandoff("Could not advance after skipping a 回关 user")
            return
        }
        val nextContext = awaitUserResultsAfterScroll("follow_back_skip")
        if (nextContext == null) {
            if (queryTransitionHandled) return
            failTaskWithoutManualHandoff("The next user result page was not detected after skipping a 回关 user")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        selectAfterViewportAnchor(nextContext, "follow_back_skip")
    }

    /**
     * Continue a list after a vertical swipe by locating the last processed identity in the new
     * viewport first. A RecyclerView may move three or four old rows above the fold when its
     * network-backed append is only half complete; selecting the first visible row in that state
     * is therefore unsafe even when the viewport has already reported USER_RESULTS twice.
     *
     * The helper captures one OCR snapshot for the whole viewport, resolves all structural rows,
     * and only then chooses the row after the processed anchor. If the anchor is not yet exposed,
     * it waits for the row text to settle instead of opening an unverified row. A genuinely full
     * page (no overlap identity at all) is allowed to start at its first stable row.
     */
    private suspend fun resumeRemoteTaskFromAnchor(initialContext: ScreenContext) {
        val anchor = remoteResumeAnchor
        if (!remoteResumePending || anchor == null) {
            remoteResumePending = false
            return
        }

        var context = initialContext
        val maxSwipes = remoteResumeMaxSwipes.coerceAtLeast(1)
        repeat(maxSwipes + 1) { attempt ->
            context = currentWindowContext() ?: context
            if (pageDetector.detect(context).kind != PageKind.USER_RESULTS) {
                delay(TuningConstants.NavigationFlow.USER_ROW_POSTCONDITION_INTERVAL_MS)
                return@repeat
            }
            val rows = visibleStructuralUserRows(context)
            if (rows.isNotEmpty()) {
                val needsViewportOcr = rows.any { row ->
                    UserResultIdentityExtractor.extract(context, row) == null
                }
                val identityContext = enrichUserResultIdentityContext(
                    context = context,
                    match = rows.first(),
                    forceOcr = needsViewportOcr,
                    forceFreshOcr = needsViewportOcr && attempt > 0,
                )
                val identities = rows.map { row ->
                    UserResultIdentityExtractor.extract(identityContext, row)
                }
                val candidates = identities.mapIndexedNotNull { index, identity ->
                    identity?.let {
                        UserResultIdentityMatcher.viewportAnchorMatchReason(anchor, it)
                            ?.let { reason -> index to reason }
                    }
                }
                val strong = candidates.filterNot { it.second == "anchor_display_name" }
                val anchorIndex = when {
                    strong.size == 1 -> strong.single().first
                    strong.size > 1 -> -1
                    candidates.size == 1 -> candidates.single().first
                    else -> -1
                }
                logger.info(
                    "remote_resume_anchor_probe",
                    attributes = mapOf(
                        "attempt" to attempt + 1,
                        "row_count" to rows.size,
                        "identity_count" to identities.count { it != null },
                        "anchor_candidate_count" to candidates.size,
                        "anchor_index" to anchorIndex,
                        "target_page" to (remoteResumeTargetPageNumber ?: 1),
                    ),
                )
                if (identities.any { it == null }) {
                    logger.info(
                        "remote_resume_anchor_waiting",
                        message = "The visible rows do not yet have complete identities; waiting before paging",
                        attributes = mapOf("attempt" to attempt + 1),
                    )
                    delay(TuningConstants.NavigationFlow.USER_ROW_POSTCONDITION_INTERVAL_MS)
                    return@repeat
                }
                if (anchorIndex >= 0 && identities.all { it != null }) {
                    // The backend page number is authoritative once the physical anchor has been
                    // found. From this point onward normal overlap handling resumes.
                    remoteResumePending = false
                    remotePageNumber = remoteResumeTargetPageNumber ?: remotePageNumber
                    val anchorRow = rows[anchorIndex]
                    phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                    AutomationStore.publishPhase(phase)
                    selectVisibleUser(
                        identityContext,
                        minimumAnchorTop = anchorRow.anchor.bounds.bottom.toFloat(),
                    )
                    return
                }

                // The exact last anchor can be absent after a feed reorder or OCR truncation,
                // while one or more earlier terminal rows are still visible. The backend ledger
                // now lets us safely continue from the first row not already processed.
                val firstUnprocessedIndex = identities.indexOfFirst { identity ->
                    identity != null && processedUserIdentityMatchReason(identity) == null
                }
                val hasKnownProcessedRow = identities.any { identity ->
                    identity != null && processedUserIdentityMatchReason(identity) != null
                }
                if (identities.all { it != null } && hasKnownProcessedRow && firstUnprocessedIndex >= 0) {
                    val firstNewRow = rows[firstUnprocessedIndex]
                    logger.info(
                        "remote_resume_ledger_fallback",
                        message = "The exact remote anchor is absent; continuing after the visible processed ledger",
                        attributes = mapOf(
                            "attempt" to attempt + 1,
                            "processed_row_count" to identities.count { identity ->
                                identity != null && processedUserIdentityMatchReason(identity) != null
                            },
                            "first_unprocessed_index" to firstUnprocessedIndex,
                        ),
                    )
                    remoteResumePending = false
                    remotePageNumber = remoteResumeTargetPageNumber ?: remotePageNumber
                    phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                    AutomationStore.publishPhase(phase)
                    // findAfter is strict; subtract one pixel so the first new row itself is
                    // selected while still preserving the structural name-area click route.
                    selectVisibleUser(
                        identityContext,
                        minimumAnchorTop = firstNewRow.anchor.bounds.top.toFloat() - 1f,
                    )
                    return
                }
            } else {
                logger.info(
                    "remote_resume_anchor_waiting",
                    message = "The result rows are still loading; no resume swipe is issued",
                    attributes = mapOf("attempt" to attempt + 1),
                )
                delay(TuningConstants.NavigationFlow.USER_ROW_POSTCONDITION_INTERVAL_MS)
                return@repeat
            }

            if (attempt >= maxSwipes) return@repeat
            val scroll = swipeToNextUserPage("remote_resume_anchor")
            if (!scroll.succeeded) {
                pause("无法定位远程任务的上次用户，已暂停以避免重复处理")
                return
            }
            val nextContext = awaitUserResultsAfterScroll("remote_resume_anchor")
            if (nextContext == null) {
                if (!queryTransitionHandled) {
                    pause("远程任务断点用户未在结果中找到，已暂停等待人工确认")
                }
                return
            }
            context = nextContext
        }

        remoteResumePending = false
        pause("远程任务断点用户未在限定页数内找到，已暂停以避免重复处理")
    }

    private suspend fun selectAfterViewportAnchor(
        initialContext: ScreenContext,
        tag: String,
    ) {
        val previousIdentity = processedUserIdentityRecords.lastOrNull()
        if (previousIdentity == null) {
            phase = AutomationPhase.WAITING_FOR_USER_RESULTS
            AutomationStore.publishPhase(phase)
            selectVisibleUser(initialContext)
            return
        }

        var context = initialContext
        var lastIdentitySignature: String? = null
        var stableIdentityObservations = 0
        var lastResolvedAnchor: UserResultsAnchorObservation? = null
        var remoteCheckpointSubmitted = false
        repeat(TuningConstants.NavigationFlow.VIEWPORT_ANCHOR_PROBE_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(TuningConstants.NavigationFlow.VIEWPORT_ANCHOR_PROBE_INTERVAL_MS)
            context = currentWindowContext() ?: context
            if (pageDetector.detect(context).kind != PageKind.USER_RESULTS) return@repeat

            val rows = visibleStructuralUserRows(context)
            if (rows.isEmpty()) {
                logger.info(
                    "user_result_anchor_waiting",
                    message = "The next viewport has no complete result rows yet; waiting before selecting",
                    attributes = mapOf("tag" to tag, "attempt" to attempt + 1),
                )
                return@repeat
            }

            // One accessible row is not enough: custom-rendered lists may expose the first
            // account semantically while following rows exist only as pixels. Ensure one OCR pass
            // covers the whole stable viewport whenever any row lacks node identity.
            val needsViewportOcr = rows.any { row ->
                UserResultIdentityExtractor.extract(context, row) == null
            }
            val identityContext = enrichUserResultIdentityContext(
                context = context,
                match = rows.first(),
                forceOcr = needsViewportOcr,
                forceFreshOcr = needsViewportOcr && attempt > 0,
            )
            val identities = rows.map { row ->
                UserResultIdentityExtractor.extract(identityContext, row)
            }
            val identitySignature = viewportIdentitySignature(rows, identities)
            if (identitySignature == lastIdentitySignature) {
                stableIdentityObservations++
            } else {
                lastIdentitySignature = identitySignature
                stableIdentityObservations = 1
            }
            val completeIdentitiesStable = identities.all { it != null } &&
                stableIdentityObservations >= TuningConstants.NavigationFlow.VIEWPORT_IDENTITY_STABLE_OBSERVATIONS
            if (completeIdentitiesStable && !remoteCheckpointSubmitted) {
                val visibleKeys = identities.mapNotNull { it?.key }
                val fingerprint = (identitySignature + visibleKeys.joinToString("|")).hashCode().toString(16)
                AutomationStore.syncRemoteCheckpoint(
                    RemoteCheckpointRequest(
                        pageNumber = remotePageNumber,
                        pageFingerprint = fingerprint,
                        lastUserKey = previousIdentity.key,
                        lastUserName = previousIdentity.displayName,
                        visibleUserKeys = visibleKeys,
                    ),
                )
                remoteCheckpointSubmitted = true
            }
            val anchorCandidates = identities.mapIndexedNotNull { index, identity ->
                identity?.let { candidate ->
                    UserResultIdentityMatcher.viewportAnchorMatchReason(previousIdentity, candidate)?.let { reason ->
                        index to reason
                    }
                }
            }
            // A display-name-only anchor is useful for OCR punctuation drift, but it is not
            // allowed to choose between two same-name accounts. Prefer a single strong match;
            // otherwise leave the viewport unresolved and wait/manual-handoff rather than risk a
            // duplicate or the wrong account.
            val strongAnchorCandidates = anchorCandidates.filterNot { it.second == "anchor_display_name" }
            val anchorIndex = when {
                strongAnchorCandidates.size == 1 -> strongAnchorCandidates.single().first
                strongAnchorCandidates.size > 1 -> -1
                anchorCandidates.size == 1 -> anchorCandidates.single().first
                else -> -1
            }
            val firstUnprocessedIndex = identities.indexOfFirst { identity ->
                identity != null && processedUserIdentityMatchReason(identity) == null
            }
            lastResolvedAnchor = UserResultsAnchorContinuationPolicy.observe(
                previous = lastResolvedAnchor,
                anchorIndex = anchorIndex,
                anchorFingerprint = identities.getOrNull(anchorIndex)?.fingerprint,
            )
            val resolvedAnchorStable = UserResultsAnchorContinuationPolicy.canContinue(
                observation = lastResolvedAnchor,
                stableViewportObservations = stableIdentityObservations,
                requiredObservations = TuningConstants.NavigationFlow.VIEWPORT_IDENTITY_STABLE_OBSERVATIONS,
            )
            logger.info(
                "user_result_anchor_probe",
                attributes = mapOf(
                    "tag" to tag,
                    "attempt" to attempt + 1,
                    "row_count" to rows.size,
                    "identity_count" to identities.count { it != null },
                    "identities_stable" to completeIdentitiesStable,
                    "stable_identity_observations" to stableIdentityObservations,
                    "anchor_found" to (anchorIndex >= 0),
                    "anchor_index" to anchorIndex,
                    "resolved_anchor_stable" to resolvedAnchorStable,
                    "anchor_candidate_count" to anchorCandidates.size,
                    "first_unprocessed_index" to firstUnprocessedIndex,
                ),
            )

            if (anchorIndex >= 0 && resolvedAnchorStable) {
                val nextIndex = (anchorIndex + 1).takeIf { it < rows.size }
                if (nextIndex != null) {
                    logger.info(
                        "user_result_anchor_resolved",
                        message = "The last processed user was found; continuing strictly after its row",
                        attributes = mapOf(
                            "tag" to tag,
                            "anchor_index" to anchorIndex,
                            "next_index" to nextIndex,
                            "next_anchor_top" to rows[nextIndex].anchor.bounds.top,
                        ),
                    )
                    phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                    AutomationStore.publishPhase(phase)
                    selectVisibleUser(
                        identityContext,
                        minimumAnchorTop = rows[anchorIndex].anchor.bounds.bottom.toFloat(),
                    )
                    return
                }

                // The processed anchor is currently the last complete row. New rows may still
                // be arriving; let the bounded probe observe them before requesting another page.
                logger.info(
                    "user_result_anchor_at_viewport_end",
                    message = "The processed anchor is the last visible row; waiting for appended results",
                    attributes = mapOf("tag" to tag, "attempt" to attempt + 1),
                )
                return@repeat
            }

            // If every visible row has a stable identity, continue from the earliest identity not
            // present in the processed ledger. The previous anchor can be clipped out when a
            // network-backed list appends only half a page; in that case the first one or two
            // rows may still be duplicates even though the anchor itself is no longer visible.
            // Choosing the first unprocessed identity avoids guessing from scroll distance.
            val allIdentitiesStable = completeIdentitiesStable && identities.size == rows.size
            if (allIdentitiesStable && firstUnprocessedIndex >= 0) {
                val knownDuplicate = identities.any { identity ->
                    identity != null && processedUserIdentityMatchReason(identity) != null
                }
                val prefixIsProcessed = identities
                    .take(firstUnprocessedIndex)
                    .all { identity ->
                        identity != null && processedUserIdentityMatchReason(identity) != null
                    }
                if (prefixIsProcessed) {
                    val minimumTop = if (firstUnprocessedIndex == 0) {
                        null
                    } else {
                        rows[firstUnprocessedIndex - 1].anchor.bounds.bottom.toFloat()
                    }
                    logger.info(
                        "user_result_first_unprocessed_resolved",
                        message = "The previous anchor is clipped; continuing at the earliest stable identity not in the processed ledger",
                        attributes = mapOf(
                            "tag" to tag,
                            "first_unprocessed_index" to firstUnprocessedIndex,
                            "processed_prefix_count" to firstUnprocessedIndex,
                            "row_count" to rows.size,
                            "minimum_anchor_top" to minimumTop,
                            "known_duplicate" to knownDuplicate,
                        ),
                    )
                    phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                    AutomationStore.publishPhase(phase)
                    selectVisibleUser(identityContext, minimumAnchorTop = minimumTop)
                    return
                }
                if (!knownDuplicate) {
                    logger.info(
                        "user_result_full_page_started",
                        message = "No processed identity overlaps this stable viewport; starting from its first row",
                        attributes = mapOf("tag" to tag, "row_count" to rows.size),
                    )
                    phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                    AutomationStore.publishPhase(phase)
                    selectVisibleUser(identityContext)
                    return
                }
                logger.warn(
                    "user_result_anchor_identity_drift_waiting",
                    message = "An overlapping identity is visible but the last processed anchor is ambiguous; waiting instead of opening a row",
                    attributes = mapOf("tag" to tag, "first_unprocessed_index" to firstUnprocessedIndex),
                )
            }
        }

        logger.warn(
            "user_result_anchor_timeout",
            message = "The next viewport did not expose a stable continuation anchor; no row was opened",
            attributes = mapOf("tag" to tag, "attempts" to TuningConstants.NavigationFlow.VIEWPORT_ANCHOR_PROBE_ATTEMPTS),
        )
        failTaskWithoutManualHandoff("The next result page did not expose a stable continuation anchor before timeout")
    }

    private fun visibleStructuralUserRows(context: ScreenContext): List<StructuralUserRowMatch> {
        val rows = ArrayList<StructuralUserRowMatch>()
        var minimumAnchorTop = (context.screenSize.height * TuningConstants.NavigationFlow.USER_RESULTS_TOP_RATIO).toFloat() - 1f
        repeat(TuningConstants.NavigationFlow.MAX_VISIBLE_USER_ROWS) {
            val match = StructuralUserRowDetector.findAfter(context, minimumAnchorTop) ?: return@repeat
            rows += match
            minimumAnchorTop = match.anchor.bounds.bottom.toFloat()
        }
        return rows
    }

    private fun viewportIdentitySignature(
        rows: List<StructuralUserRowMatch>,
        identities: List<UserResultIdentity?>,
    ): String = buildString {
        rows.forEachIndexed { index, row ->
            append(row.anchor.bounds.top).append(':')
            append(row.anchor.bounds.bottom).append(':')
            // OCR can vary punctuation, spacing, or one clipped character between two captures.
            // Stability here means the same row geometry and identity availability, not byte-for-
            // byte identical OCR text. The actual identity matcher still runs before tapping.
            append(if (identities.getOrNull(index) != null) '1' else '0').append(';')
        }
    }

    private suspend fun tapUserRowContent(row: NodeSnapshot, context: ScreenContext): ActionOutcome {
        val screenWidth = context.screenSize.width.coerceAtLeast(1)
        val screenHeight = context.screenSize.height.coerceAtLeast(1)
        val rowWidth = row.bounds.width.coerceAtLeast(1)
        val rowHeight = row.bounds.height.coerceAtLeast(1)
        // Current Douyin rows place the avatar around the left fifth and the follow button on the
        // right quarter. Keep the tap in the name/metadata band between those areas. Ratios are
        // normalized to the row/display, so this remains usable across common screen sizes.
        val safeLeft = max(
            row.bounds.left,
            row.bounds.left + (rowWidth * TuningConstants.NavigationFlow.USER_ROW_CONTENT_LEFT_RATIO).toInt(),
        ).coerceIn(0, screenWidth - 1)
        val safeRight = min(
            row.bounds.right,
            (screenWidth * TuningConstants.NavigationFlow.USER_ROW_SAFE_TAP_RIGHT_RATIO).toInt(),
        ).coerceIn(safeLeft + 1, screenWidth)
        val safeTop = max(
            row.bounds.top,
            row.bounds.top + (rowHeight * TuningConstants.NavigationFlow.USER_ROW_CONTENT_TOP_RATIO).toInt(),
        ).coerceIn(0, screenHeight - 1)
        val safeBottom = min(
            row.bounds.bottom,
            row.bounds.top + (rowHeight * TuningConstants.NavigationFlow.USER_ROW_CONTENT_BOTTOM_RATIO).toInt(),
        ).coerceIn(safeTop + 1, screenHeight)
        val safeBounds = ScreenBounds(
            left = safeLeft,
            top = safeTop,
            right = safeRight,
            bottom = safeBottom,
        )
        logger.warn(
            "user_result_structural_fallback",
            message = "User row text is not exposed; tapping the name/profile content area (avatar excluded)",
            attributes = mapOf(
                "row_bounds" to row.bounds,
                "tap_bounds" to safeBounds,
                "avatar_excluded" to true,
            ),
        )
        if (waitForTargetWindow("user_result_name_area") == null) {
            return ActionOutcome.failure("Douyin window is temporarily covered or unavailable")
        }
        val gesture = gestures.tapBounds(safeBounds)
        return if (gesture.succeeded) {
            ActionOutcome.success("user_row_name_area_gesture")
        } else {
            gesture
        }
    }

    /**
     * The result list can expose a clipped name such as "佛山市南海楠荞红木..". Resolve the
     * profile header before opening private messages so the audit record keeps the full name.
     * Accessibility is the fast path; OCR is limited to the profile header and only runs when
     * the list identity is missing or visibly clipped.
     */
    private suspend fun enrichCurrentUserDisplayName(context: ScreenContext) {
        val identityFingerprint = currentUserIdentityFingerprint ?: return
        val listName = currentUserDisplayName
        val nodeCandidate = DisplayNameResolver.fromAccessibility(
            surface = DisplayNameResolver.Surface.PROFILE,
            context = context,
            previousName = listName,
        )
        val confirmedNodeName = nodeCandidate?.let { firstCandidate ->
            DisplayNameResolver.confirmProfileAccessibility(
                firstCandidate = firstCandidate,
                confirmationAttempts = TuningConstants.NavigationFlow.PROFILE_NAME_CONFIRM_ATTEMPTS,
                awaitNextConfirmation = {
                    delay(TuningConstants.NavigationFlow.PROFILE_NAME_CONFIRM_INTERVAL_MS)
                },
                nextCandidate = { previousCandidate ->
                    currentWindowContext()?.let { liveContext ->
                        DisplayNameResolver.fromAccessibility(
                            surface = DisplayNameResolver.Surface.PROFILE,
                            context = liveContext,
                            previousName = previousCandidate,
                        )
                    }
                },
            )
        }
        val nodeResolution = DisplayNameResolver.arbitrate(confirmedNodeName, null)
        if (nodeResolution != null) {
            currentUserDisplayName = nodeResolution.value
            currentUserDisplayNameSource = nodeResolution.source
            AutomationStore.updateCurrentUserDisplayName(identityFingerprint, nodeResolution.value)
            logger.info(
                "profile_display_name_resolved",
                attributes = mapOf(
                    "source" to "accessibility",
                    "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
                    "confirmed" to true,
                ),
            )
            return
        }

        if (nodeCandidate != null) {
            logger.warn(
                "profile_display_name_unstable",
                message = "The profile title changed between accessibility snapshots; OCR fallback will be considered",
                attributes = mapOf(
                    "had_previous_name" to !listName.isNullOrBlank(),
                    "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(currentUserIdentityFingerprint),
                ),
            )
        }

        // OCR is intentionally limited to uncertain rows. Accessibility-backed names are not
        // rescanned unless the profile node was unstable; OCR-backed rows always receive one
        // profile-header pass so a list-level glyph error cannot be persisted unchanged.
        val shouldUseProfileOcr = DisplayNameResolver.shouldUseOcr(
            surface = DisplayNameResolver.Surface.PROFILE,
            hasOcrEngine = ocr != null,
            currentSource = currentUserDisplayNameSource,
            listName = listName,
            hasAccessibilityCandidate = nodeCandidate != null,
        )
        if (!shouldUseProfileOcr) return

        val enriched = captureContextWithOcr(
            base = context,
            tag = "profile_header_identity",
            region = OcrRegion.PROFILE_HEADER,
        ) ?: return
        val ocrResolution = DisplayNameResolver.arbitrate(
            accessibilityCandidate = null,
            ocrCandidate = DisplayNameResolver.fromOcr(
                surface = DisplayNameResolver.Surface.PROFILE,
                context = enriched,
                previousName = currentUserDisplayName,
            ),
        ) ?: return
        currentUserDisplayName = ocrResolution.value
        currentUserDisplayNameSource = ocrResolution.source
        AutomationStore.updateCurrentUserDisplayName(identityFingerprint, ocrResolution.value)
        logger.info(
            "profile_display_name_resolved",
            attributes = mapOf(
                "source" to "ocr_profile_header",
                "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
            ),
        )
    }

    /**
     * The conversation itself exposes a second, independent identity anchor: the participant's
     * large circular avatar with the name rendered directly underneath. Use it before recording
     * the blank-message result so a transient profile/list candidate such as "视频" cannot remain
     * in the audit record. The node path is preferred; one local header OCR pass is only used if
     * the custom chat surface does not expose a usable name node.
     */
    private suspend fun enrichCurrentUserDisplayNameFromDirectMessage(context: ScreenContext?) {
        val identityFingerprint = currentUserIdentityFingerprint ?: return
        var directContext = context ?: currentWindowContext() ?: return
        val nodeName = DisplayNameResolver.fromAccessibility(
            surface = DisplayNameResolver.Surface.DIRECT_MESSAGE,
            context = directContext,
            previousName = currentUserDisplayName,
        )
        val nodeResolution = DisplayNameResolver.arbitrate(
            accessibilityCandidate = nodeName,
            ocrCandidate = null,
        )
        if (nodeResolution != null) {
            currentUserDisplayName = nodeResolution.value
            currentUserDisplayNameSource = nodeResolution.source
            AutomationStore.updateCurrentUserDisplayName(
                identityFingerprint,
                nodeResolution.value,
                PageKind.DIRECT_MESSAGE,
            )
            logger.info(
                "direct_message_display_name_resolved",
                attributes = mapOf(
                    "source" to "accessibility",
                    "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
                ),
            )
            return
        }

        if (!DisplayNameResolver.shouldUseOcr(
                surface = DisplayNameResolver.Surface.DIRECT_MESSAGE,
                hasOcrEngine = ocr != null,
                currentSource = currentUserDisplayNameSource,
                listName = currentUserDisplayName,
                hasAccessibilityCandidate = nodeName != null,
            )
        ) return
        directContext = captureContextWithOcr(
            base = directContext,
            tag = "direct_message_identity",
            region = OcrRegion.PROFILE_HEADER,
        ) ?: directContext
        val ocrResolution = DisplayNameResolver.arbitrate(
            accessibilityCandidate = null,
            ocrCandidate = DisplayNameResolver.fromOcr(
                surface = DisplayNameResolver.Surface.DIRECT_MESSAGE,
                context = directContext,
                previousName = currentUserDisplayName,
            ),
        ) ?: return
        currentUserDisplayName = ocrResolution.value
        currentUserDisplayNameSource = ocrResolution.source
        AutomationStore.updateCurrentUserDisplayName(
            identityFingerprint,
            ocrResolution.value,
            PageKind.DIRECT_MESSAGE,
        )
        logger.info(
            "direct_message_display_name_resolved",
            attributes = mapOf(
                "source" to "ocr_header",
                "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
            ),
        )
    }

    private suspend fun openPrivateMessage(context: ScreenContext) {
        enrichCurrentUserDisplayName(currentWindowContext() ?: context)
        phase = AutomationPhase.OPENING_MESSAGE_ENTRY
        AutomationStore.publishPhase(phase)
        var outcome = ActionOutcome.failure("No private-message entry route was available")
        for (attempt in 1..TuningConstants.NavigationFlow.PRIVATE_MESSAGE_ENTRY_ATTEMPTS) {
            val liveContext = currentWindowContext() ?: context
            val semanticSelection = selectSafePrivateMessageEntry(liveContext)
            val semanticOutcome = if (semanticSelection.node == null) {
                ActionOutcome.failure(semanticSelection.reasons.joinToString("; "))
            } else {
                clickSelection(liveContext, semanticSelection)
            }
            outcome = if (semanticOutcome.succeeded) {
                semanticOutcome
            } else {
                // OCR may confirm that a label exists, but it must never be used as a direct
                // click target. Prefer a live semantic node; the icon fallback is also accepted
                // only when its content description/view id identifies a paper-plane/message
                // control. If neither is available, retry and then skip safely.
                val iconNode = ProfileMessageEntryFallback.iconNode(liveContext)
                if (iconNode != null) {
                    logger.warn(
                        "private_message_icon_fallback",
                        message = "Profile exposes a semantic paper-plane/message icon; tapping the verified profile action",
                        attributes = mapOf(
                            "route" to "profile_action_icon_node",
                            "bounds" to iconNode.bounds,
                            "attempt" to attempt,
                        ),
                    )
                    withLiveNode(iconNode) { liveNode -> gestures.click(liveNode, iconNode.bounds) }
                } else {
                    ActionOutcome.failure("No semantic paper-plane private-message control")
                }
            }
            if (outcome.succeeded) {
                logger.info(
                    "private_message_entry_action_submitted",
                    attributes = mapOf("route" to outcome.route, "attempt" to attempt),
                )
                timeoutJob?.cancel()
                await(
                    nextPhase = AutomationPhase.WAITING_FOR_DIRECT_MESSAGE,
                    timeoutDescription = "The direct-message page was not detected after opening the entry",
                )
                when (awaitPrivateMessageEntryPostcondition(liveContext, attempt)) {
                    PageKind.DIRECT_MESSAGE,
                    PageKind.MESSAGE_EMPTY_REJECTED,
                    -> {
                        completeAtMessagePage()
                        return
                    }
                    PageKind.PRIVATE_MESSAGE_RESTRICTED -> {
                        skipRestrictedUser("Douyin requires following before private messaging")
                        return
                    }
                    PageKind.MESSAGE_SEND_FAILED -> {
                        skipMessageSendFailure(
                            "Douyin rejected the message because of the recipient's messaging settings",
                        )
                        return
                    }
                    PageKind.HUMAN_INTERVENTION -> {
                        pause("A verification or risk screen appeared while opening private messages; manual handoff required")
                        return
                    }
                    else -> {
                        logger.warn(
                            "private_message_entry_postcondition_retry",
                            message = "The profile remained visible after the entry action; retrying the same semantic/action-band route",
                            attributes = mapOf("attempt" to attempt, "page" to PageKind.USER_PROFILE.name),
                        )
                    }
                }
            }
            logger.warn(
                "private_message_entry_retry",
                message = "Private-message entry action was not accepted; refreshing the profile before retrying",
                attributes = mapOf("attempt" to attempt, "reason" to outcome.reason),
            )
            if (attempt < TuningConstants.NavigationFlow.PRIVATE_MESSAGE_ENTRY_ATTEMPTS) delay(TuningConstants.NavigationFlow.PRIVATE_MESSAGE_ENTRY_RETRY_INTERVAL_MS)
        }
        if (!outcome.succeeded) {
            val currentDetection = currentWindowContext()?.let(pageDetector::detect)
            if (currentDetection?.kind == PageKind.USER_PROFILE) {
                skipMessageSendFailure(
                    "The profile's private-message entry was unavailable after bounded retries",
                    failurePage = PageKind.USER_PROFILE,
                )
            } else {
                skipMessageSendFailure(
                    "Could not find the profile's private-message entry after bounded retries: ${outcome.reason}",
                    failurePage = currentDetection?.kind ?: PageKind.USER_PROFILE,
                )
            }
            return
        }
        logger.warn(
            "private_message_entry_exhausted",
            message = "The private-message entry did not reach a verified conversation after bounded retries",
            attributes = mapOf("attempts" to TuningConstants.NavigationFlow.PRIVATE_MESSAGE_ENTRY_ATTEMPTS),
        )
    }

    /** OCR is diagnostic-only for this action; direct OCR taps are intentionally disabled. */
    private suspend fun tapOcrPrivateMessageEntry(context: ScreenContext): ActionOutcome {
        logger.info("private_message_ocr_diagnostic_only")
        return ActionOutcome.failure("OCR cannot be used as a private-message click target")
    }

    private fun selectSafePrivateMessageEntry(context: ScreenContext): SelectionResult {
        val selection = selector.select(context, DouyinSelectors.privateMessageEntry)
        val node = selection.node ?: return selection
        val searchable = (node.searchableText() + selection.reasons).joinToString(" ").lowercase()
        val verdict = PrivateMessageEntryRuleStore.evaluate(
            route = PrivateMessageEntryRoute.SELECTOR,
            searchableText = searchable,
        )
        if (!verdict.isAllowed) {
            return selection.copy(
                node = null,
                score = 0f,
                reasons = listOf("Rejected unsafe or non-paper-plane private-message candidate"),
            )
        }
        return selection
    }

    private fun currentTaskMessageContent(): String =
        if (pendingSafetyProbe || activeTaskSnapshot?.executionMode == TaskExecutionMode.SAFE_BLANK_PROBE) {
            "空消息模拟（空格）"
        } else {
            pendingStartMessage.ifBlank { activeTaskSnapshot?.messageTemplate.orEmpty() }
                .ifBlank { "未设置" }
        }

    private fun confirmOcrBackedPage(context: ScreenContext, detection: PageDetection): Boolean {
        // A home feed is dynamic by design, so full-screen OCR text changes between frames even
        // when the actionable surface is stable.  The upper-right search node plus absence of a
        // startup overlay is a stronger post-condition than matching changing feed captions;
        // allow the node-first route immediately in that case.
        if (detection.kind == PageKind.HOME &&
            TransientOverlayDetector.find(context) == null &&
            hasInitialSearchSelectorCandidate(context)
        ) {
            lastOcrPageSignature = null
            ocrPageStableObservations = 0
            return true
        }
        val ocrBacked = detection.reasons.any { reason ->
            reason.contains("OCR", ignoreCase = true)
        }
        if (!ocrBacked) {
            lastOcrPageSignature = null
            ocrPageStableObservations = 0
            return true
        }
        if (!CommentTaskOcrPageStabilityPolicy.shouldRequireSecondOcrFrame(commentProfileHandoffObserved)) {
            lastOcrPageSignature = null
            ocrPageStableObservations = 0
            logger.info(
                "ocr_page_stable_skipped",
                attributes = mapOf("page" to detection.kind.name),
            )
            return true
        }
        val signature = "${detection.kind}|${detection.reasons.sorted().joinToString(";")}".hashCode().toString()
        if (signature == lastOcrPageSignature) {
            ocrPageStableObservations++
        } else {
            lastOcrPageSignature = signature
            ocrPageStableObservations = 1
        }
        if (ocrPageStableObservations < TuningConstants.NavigationLifecycle.OCR_PAGE_STABLE_OBSERVATIONS) {
            logger.info(
                "ocr_page_waiting_stable",
                message = "Waiting for a second matching OCR-backed page observation before acting",
                attributes = mapOf("page" to detection.kind.name, "stable_observations" to ocrPageStableObservations),
            )
            return false
        }
        return true
    }

    /**
     * Verify the result of a profile action independently of accessibility callbacks.  A chat
     * may first expose only the profile tree, so one OCR sample is taken before the action is
     * classified as unavailable.  Returning USER_PROFILE/UNKNOWN tells the caller to retry.
     */
    private suspend fun awaitPrivateMessageEntryPostcondition(
        initialContext: ScreenContext,
        actionAttempt: Int,
    ): PageKind {
        var lastKind = PageKind.UNKNOWN
        repeat(TuningConstants.NavigationFlow.PRIVATE_MESSAGE_ENTRY_POSTCONDITION_ATTEMPTS) { probeAttempt ->
            delay(
                if (probeAttempt == 0) {
                    TuningConstants.NavigationFlow.PRIVATE_MESSAGE_ENTRY_POSTCONDITION_INITIAL_DELAY_MS
                } else {
                    TuningConstants.NavigationFlow.PRIVATE_MESSAGE_ENTRY_POSTCONDITION_INTERVAL_MS
                },
            )
            var context = currentWindowContext() ?: initialContext
            var detection = pageDetector.detect(context)
            if (detection.kind in setOf(
                    PageKind.DIRECT_MESSAGE,
                    PageKind.MESSAGE_EMPTY_REJECTED,
                    PageKind.MESSAGE_SEND_FAILED,
                    PageKind.PRIVATE_MESSAGE_RESTRICTED,
                    PageKind.HUMAN_INTERVENTION,
                )
            ) {
                lastKind = detection.kind
                return lastKind
            }
            // The most common missing signal is the bottom composer. Let the service's
            // throttled OCR path enrich the next accessibility snapshot; use an explicit sample
            // here as a final bounded fallback so a real chat is not backed out of prematurely.
            if (probeAttempt == TuningConstants.NavigationFlow.PRIVATE_MESSAGE_ENTRY_OCR_PROBE_ATTEMPT && ocr != null) {
                context = captureContextWithOcr(context, "private_message_entry_probe") ?: context
                detection = pageDetector.detect(context)
                if (detection.kind != PageKind.UNKNOWN) lastKind = detection.kind
                if (detection.kind in setOf(
                        PageKind.DIRECT_MESSAGE,
                        PageKind.MESSAGE_EMPTY_REJECTED,
                        PageKind.MESSAGE_SEND_FAILED,
                        PageKind.PRIVATE_MESSAGE_RESTRICTED,
                        PageKind.HUMAN_INTERVENTION,
                    )
                ) {
                    return detection.kind
                }
            }
            logger.info(
                "private_message_entry_postcondition_probe",
                attributes = mapOf(
                    "action_attempt" to actionAttempt,
                    "probe_attempt" to probeAttempt + 1,
                    "page" to detection.kind.name,
                    "confidence" to detection.confidence,
                ),
            )
        }
        return lastKind
    }

    /**
     * Re-submit a still-visible search entry through the same top-right Search control only.
     *
     * This recovery is intentionally narrow. A suggestion row, IME_ENTER, or BACK can change
     * focus or navigate to an unrelated surface, so none of them is an acceptable substitute for
     * the Search button.
     */
    private suspend fun retrySearchSubmit(context: ScreenContext): ActionOutcome {
        var lastOutcome = ActionOutcome.failure("No Search button route was available")
        repeat(2) { attempt ->
            val buttonContext = currentWindowContext() ?: context
            lastOutcome = tapSelectorBounds(buttonContext, searchSubmitSelector)
            if (lastOutcome.succeeded && searchResultsPostconditionReached("search_button_bounds_retry_${attempt + 1}")) {
                return lastOutcome
            }
            if (lastOutcome.succeeded) {
                logger.warn(
                    "search_submit_route_noop",
                    message = "The top-right Search control was tapped but the search-entry page remained visible",
                    attributes = mapOf("route" to lastOutcome.route, "attempt" to attempt + 1),
                )
            }
            if (attempt == 0) delay(350L)
        }

        // Last resort is the same top-right control expressed as a normalized point. It is not a
        // suggestion or a focus-changing action and is therefore safe to use only while the
        // search-entry page is still present.
        lastOutcome = tapNormalizedGuarded(
            DouyinSelectors.searchSubmitNormalizedFallback,
            "search_submit_same_button_fallback",
        )
        if (lastOutcome.succeeded && searchResultsPostconditionReached("search_button_normalized_fallback")) {
            return lastOutcome
        }
        logger.warn(
            "search_submit_route_noop",
            message = "The top-right Search control did not leave the search-entry page after bounded retries",
            attributes = mapOf("route" to lastOutcome.route),
        )
        return ActionOutcome.failure("Top-right Search control did not open results")
    }

    /**
     * Polls the live tree until a results page is classified. The returned context is the same
     * snapshot that proved the post-condition, so callers can continue from it even when Douyin
     * emits no follow-up accessibility event.
     */
    private suspend fun searchResultsContextAfterSubmit(route: String): ScreenContext? {
        repeat(TuningConstants.NavigationFlow.SEARCH_SUBMIT_POSTCONDITION_ATTEMPTS) { attempt ->
            delay(
                if (attempt == 0) TuningConstants.NavigationFlow.SEARCH_SUBMIT_POSTCONDITION_DELAY_MS
                else TuningConstants.NavigationFlow.SEARCH_SUBMIT_POSTCONDITION_INTERVAL_MS,
            )
            // A non-focusable floating window or an OEM transition can make
            // rootInActiveWindow temporarily return our overlay (or null) even though the
            // accessibility callback has already delivered the target app's result tree. Keep
            // using that most-recent target snapshot for this short post-condition window; it
            // avoids declaring a correct result page timed out simply because the direct root
            // read lost focus for one frame.
            val liveContext = currentWindowContext()
            val recentContext = latestContext
                ?.takeIf { it.packageName == TargetAppLauncher.DOUYIN_PACKAGE }
                ?.takeIf { System.currentTimeMillis() - it.capturedAtMillis <= TuningConstants.NavigationFlow.SEARCH_SUBMIT_CONTEXT_MAX_AGE_MS }
            val candidates = listOfNotNull(liveContext, recentContext)
                .distinctBy { it.capturedAtMillis to it.nodes.size }
            if (candidates.isEmpty()) return@repeat
            val detectedCandidate = candidates
                .asSequence()
                .map { context -> context to pageDetector.detect(context) }
                .firstOrNull { (_, detection) ->
                    detection.kind == PageKind.SEARCH_RESULTS || detection.kind == PageKind.USER_RESULTS
                }
            val candidateContext = detectedCandidate?.first ?: candidates.first()
            val detection = detectedCandidate?.second ?: pageDetector.detect(candidateContext)
            logger.info(
                "search_submit_postcondition_probe",
                attributes = mapOf(
                    "route" to route,
                    "attempt" to attempt + 1,
                    "page" to detection.kind.name,
                    "confidence" to detection.confidence,
                    "root_context" to (liveContext != null),
                    "recent_context" to (recentContext != null),
                ),
            )
            if (detection.kind == PageKind.SEARCH_RESULTS || detection.kind == PageKind.USER_RESULTS) {
                latestContext = candidateContext
                AutomationStore.publishObservation(detection)
                return candidateContext
            }
        }
        return null
    }

    /** Returns true only after a live tree reports a results page, not merely a completed gesture. */
    private suspend fun searchResultsPostconditionReached(route: String): Boolean {
        return searchResultsContextAfterSubmit(route) != null
    }

    /** M2 submits one space and requires Douyin's blank-message notice; it never sends content. */
    private suspend fun probeEmptyMessageOnce() {
        if (phase != AutomationPhase.COMPLETED_AT_MESSAGE_PAGE) {
            AutomationStore.publishFailure("Open and verify a private-message page before running the safety probe")
            logger.warn("empty_message_probe_rejected", message = "No verified direct-message page is available")
            return
        }

        taskActive = true
        phase = AutomationPhase.VERIFYING_EMPTY_MESSAGE
        AutomationStore.publishPhase(phase)

        var context = currentWindowContext()
        if (context == null || pageDetector.detect(context).kind != PageKind.DIRECT_MESSAGE) {
            gestures.awaitExternalActionSlot("target_app_launch")
            when (val launch = TargetAppLauncher.launch(service)) {
                LaunchResult.Started -> {
                    delay(TuningConstants.NavigationFlow.MESSAGE_TARGET_RESTORE_DELAY_MS)
                    context = currentWindowContext()
                }
                is LaunchResult.Failed -> {
                    skipMessageSendFailure(
                        "Could not restore the verified private-message page for the safety probe: ${launch.reason}",
                        failurePage = PageKind.USER_PROFILE,
                    )
                    return
                }
            }
        }
        val directContext = context
        if (directContext == null || pageDetector.detect(directContext).kind != PageKind.DIRECT_MESSAGE) {
            skipMessageSendFailure(
                "The verified private-message page is no longer visible for the safety probe",
                failurePage = PageKind.USER_PROFILE,
            )
            return
        }

        var inputPlaced = false
        var inputFailure = "Could not find the private-message input for the safety probe"
        repeat(TuningConstants.NavigationFlow.MESSAGE_INPUT_ATTEMPTS) { attempt ->
            if (inputPlaced) return@repeat
            if (attempt > 0) delay(TuningConstants.NavigationFlow.MESSAGE_INPUT_RETRY_INTERVAL_MS)
            val liveContext = currentWindowContext() ?: directContext
            val input = selector.select(liveContext, DouyinSelectors.messageInput).node
            if (input == null) {
                inputFailure = "Could not find the private-message input for the safety probe"
                logger.warn(
                    "empty_message_input_retry",
                    message = inputFailure,
                    attributes = mapOf("attempt" to (attempt + 1)),
                )
                return@repeat
            }
            // Exactly one ASCII space is intentional. It renders as an empty composer while
            // still exercising the real send action; Douyin should reject it with a toast.
            val setText = withLiveNode(input) { liveNode -> gestures.setText(liveNode, " ") }
            if (setText.succeeded) {
                inputPlaced = true
            } else {
                inputFailure = "Could not place the blank probe in the private-message input"
                logger.warn(
                    "empty_message_input_retry",
                    message = inputFailure,
                    attributes = mapOf("attempt" to (attempt + 1), "reason" to setText.reason),
                )
            }
        }
        if (!inputPlaced) {
            skipMessageSendFailure(inputFailure, failurePage = PageKind.USER_PROFILE)
            return
        }

        delay(TuningConstants.NavigationFlow.MESSAGE_INPUT_SETTLE_DELAY_MS)
        val refreshedContext = currentWindowContext() ?: directContext
        var sendButtonOutcome = ActionOutcome.failure("No usable message send action")
        repeat(TuningConstants.NavigationFlow.MESSAGE_ACTION_ATTEMPTS) { attempt ->
            if (sendButtonOutcome.succeeded) return@repeat
            if (attempt > 0) delay(TuningConstants.NavigationFlow.MESSAGE_INPUT_RETRY_INTERVAL_MS)
            sendButtonOutcome = clickSelector(
                currentWindowContext() ?: refreshedContext,
                DouyinSelectors.messageSendAction,
            )
        }
        val sendOutcome = if (sendButtonOutcome.succeeded) {
            sendButtonOutcome
        } else {
            val refreshedInput = selector.select(refreshedContext, DouyinSelectors.messageInput).node
            if (refreshedInput == null) {
                ActionOutcome.failure("Message input disappeared before blank probe submit")
            } else {
                withLiveNode(refreshedInput) { liveNode -> gestures.submitText(liveNode) }
            }
        }
        if (!sendOutcome.succeeded) {
            skipMessageSendFailure(
                "Could not find a usable message send action for the safety probe",
                failurePage = PageKind.USER_PROFILE,
            )
            return
        }

        logger.info(
            "empty_message_probe_submitted",
            message = "Submitted one space; waiting for Douyin's blank-message notice",
            attributes = mapOf("route" to sendOutcome.route, "probe_length" to 1),
        )
        timeoutJob?.cancel()
        phase = AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT
        AutomationStore.publishPhase(phase)
        scheduleEmptyMessageProbeResultCheck()
    }

    private suspend fun completeEmptyMessageProbe() {
        if (!taskActive || phase != AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT) return
        val currentJob = coroutineContext[Job]
        timeoutJob?.takeUnless { it === currentJob }?.cancel()
        messageResultJob?.takeUnless { it === currentJob }?.cancel()
        messageResultJob = null
        restrictedUserSkips = 0
        enrichCurrentUserDisplayNameFromDirectMessage(currentWindowContext() ?: latestContext)
        phase = AutomationPhase.COMPLETED_EMPTY_MESSAGE_PROBE
        AutomationStore.publishPhase(phase)
        logger.info(
            "empty_message_probe_verified",
            message = "Douyin rejected the blank message; no real content was sent",
        )
        AutomationStore.recordUserTaskFinished(
            identityFingerprint = currentUserIdentityFingerprint,
            outcome = UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED,
            reason = "Douyin displayed the blank-message rejection",
            page = PageKind.MESSAGE_EMPTY_REJECTED,
        )
        currentUserIdentityFingerprint = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null
        advanceAfterEmptyMessageProbe()
    }

    private fun scheduleEmptyMessageProbeResultCheck() {
        messageResultJob?.cancel()
        messageResultJob = scope.launch {
            repeat(TuningConstants.NavigationFlow.EMPTY_MESSAGE_PROBE_ATTEMPTS) { attempt ->
                delay(if (attempt == 0) TuningConstants.NavigationFlow.EMPTY_MESSAGE_PROBE_INITIAL_DELAY_MS else TuningConstants.NavigationFlow.EMPTY_MESSAGE_PROBE_INTERVAL_MS)
                mutex.withLock {
                    if (!taskActive || phase != AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT) return@withLock
                    // Node inspection catches a toast exposed as text. On custom-rendered chat
                    // surfaces, take a bounded OCR probe at roughly 1.4s intervals as well. The
                    // screenshot helper rate-limits the actual capture, preventing a tight loop
                    // from flooding the device while still covering a short-lived toast.
                    val context = if (attempt % TuningConstants.NavigationFlow.EMPTY_MESSAGE_OCR_EVERY_ATTEMPTS == 0) {
                        captureEmptyMessageProbeContext() ?: currentWindowContext()
                    } else {
                        currentWindowContext()
                    } ?: return@withLock
                    val detection = pageDetector.detect(context)
                    logger.info(
                        "empty_message_probe_postcondition",
                        attributes = mapOf(
                            "attempt" to (attempt + 1),
                            "page" to detection.kind.name,
                            "confidence" to detection.confidence,
                            "nodes" to context.nodes.size,
                        ),
                    )
                    when (detection.kind) {
                        PageKind.MESSAGE_EMPTY_REJECTED -> completeEmptyMessageProbe()
                        PageKind.HUMAN_INTERVENTION -> pause("A verification or risk screen appeared during the blank-message probe; manual handoff required")
                        PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                            "The blank-message probe was rejected by the recipient's messaging settings",
                        )
                        else -> Unit
                    }
                }
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT) return@launch
            }
            mutex.withLock {
                if (taskActive && phase == AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT) {
                    skipMessageSendFailure(
                        "The blank-message notice was not detected before the safety-probe timeout",
                    )
                }
            }
        }
    }

    /** Capture one screenshot/OCR sample without exposing recognized text in logs or UI. */
    private suspend fun captureContextWithOcr(
        base: ScreenContext,
        tag: String,
        region: OcrRegion = OcrRegion.PROFILE_ACTION,
    ): ScreenContext? {
        val engine = ocr ?: return base
        return runCatching {
            val artifact = screenshotCapture.capture(tag)
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(artifact.path) }
                ?: return@runCatching base
            try {
                val matchVisualTemplates = tag == "comment_next_video_rail" &&
                    NextVideoTransitionProbePolicy.shouldMatchVisualTemplatesForRailOcr()
                val commentIconTemplateMatch = if (matchVisualTemplates) {
                    captureCommentIconTemplateMatch(bitmap, base.screenSize)
                        ?.takeUnless { match -> AppOwnedOverlayExclusion.excludes(match.bounds) }
                } else {
                    null
                }
                val actionRailAnchorTemplateMatch = if (matchVisualTemplates) {
                    captureActionRailAnchorTemplateMatch(bitmap, base.screenSize)
                        ?.takeUnless { anchors ->
                            AppOwnedOverlayExclusion.excludes(anchors.likeBounds) ||
                                AppOwnedOverlayExclusion.excludes(anchors.collectBounds)
                        }
                } else {
                    null
                }
                val result = engine.recognize(bitmap, region)
                val mappedOcrBlocks = AppOwnedOverlayExclusion.filterOcrBlocks(
                    OcrTextBlockMapper.map(
                        result = result,
                        // The P0 first-card detector must correlate a name line with the
                        // follower and account lines of that same card. The next-video action
                        // rail likewise needs independent count-line geometry to verify either a
                        // complete rail or the unique interior gap left by a zero count before
                        // deriving the comment bubble. Other OCR consumers retain their
                        // historical whole-block representation.
                        preserveLineGeometry = region == OcrRegion.USER_RESULTS ||
                            tag == "comment_next_video_rail",
                    ),
                )
                base.copy(
                    ocrBlocks = mappedOcrBlocks,
                    commentIconTemplateMatch = commentIconTemplateMatch,
                    actionRailAnchorTemplateMatch = actionRailAnchorTemplateMatch,
                    capturedAtMillis = System.currentTimeMillis(),
                ).also {
                    commentIconTemplateMatch?.let { match ->
                        val normalized = match.bounds.normalized(base.screenSize)
                        logger.info(
                            "comment_icon_template_candidate",
                            attributes = mapOf(
                                "confidence" to match.confidence,
                                "center_x" to normalized.centerX,
                                "center_y" to normalized.centerY,
                            ),
                        )
                    }
                    actionRailAnchorTemplateMatch?.let { anchors ->
                        val like = anchors.likeBounds.normalized(base.screenSize)
                        val collect = anchors.collectBounds.normalized(base.screenSize)
                        logger.info(
                            "action_rail_dual_anchor_candidate",
                            attributes = mapOf(
                                "like_confidence" to anchors.likeConfidence,
                                "collect_confidence" to anchors.collectConfidence,
                                "like_center_y" to like.centerY,
                                "collect_center_y" to collect.centerY,
                                "x_spread" to kotlin.math.abs(like.centerX - collect.centerX),
                            ),
                        )
                    }
                    latestContext = it
                    latestOcrContext = it
                }
            } finally {
                bitmap.recycle()
            }
        }.onFailure { error ->
            logger.warn(
                "profile_surface_ocr_failed",
                message = "Profile surface OCR probe failed; continuing with semantic checks",
                attributes = mapOf(
                    "cause" to (error::class.java.simpleName ?: "Throwable"),
                    "region" to region.name,
                ),
            )
        }.getOrNull()
    }

    /**
     * The matcher sees screenshot coordinates while accessibility reports screen coordinates.
     * Map through ratios so the visual candidate remains valid when their captured heights differ
     * by status-bar or cutout insets.
     */
    private suspend fun captureCommentIconTemplateMatch(
        bitmap: Bitmap,
        screenSize: ScreenSize,
    ): CommentIconTemplateMatch? {
        val searchRegion = Rect(
            (bitmap.width * COMMENT_ICON_TEMPLATE_SEARCH_LEFT_FRACTION).roundToInt(),
            (bitmap.height * COMMENT_ICON_TEMPLATE_SEARCH_TOP_FRACTION).roundToInt(),
            bitmap.width,
            (bitmap.height * COMMENT_ICON_TEMPLATE_SEARCH_BOTTOM_FRACTION).roundToInt(),
        )
        val match = commentIconTemplateMatcher.findMatch(
            bitmap = bitmap,
            templateId = AlphaMaskedCommentIconMatcher.TEMPLATE_ID,
            searchRegion = searchRegion,
        ) ?: return null
        val bounds = mapScreenshotBounds(match.bounds, bitmap, screenSize)
        return bounds.takeIf { it.width > 0 && it.height > 0 }
            ?.let { CommentIconTemplateMatch(bounds = it, confidence = match.confidence) }
    }

    /**
     * Finds the supplied like and collect templates independently in their expected vertical
     * bands. This returns evidence only; the runtime still needs two stable screenshots and the
     * detector must derive a valid intervening comment slot before a tap is possible.
     */
    private suspend fun captureActionRailAnchorTemplateMatch(
        bitmap: Bitmap,
        screenSize: ScreenSize,
    ): ActionRailAnchorTemplateMatch? {
        val likeMatch = actionRailAnchorTemplateMatcher.findMatch(
            bitmap = bitmap,
            templateId = AlphaMaskedActionIconMatcher.LIKE_TEMPLATE_ID,
            searchRegion = actionRailTemplateSearchRegion(
                bitmap = bitmap,
                topFraction = LIKE_ANCHOR_TEMPLATE_SEARCH_TOP_FRACTION,
                bottomFraction = LIKE_ANCHOR_TEMPLATE_SEARCH_BOTTOM_FRACTION,
            ),
        )
        if (likeMatch == null) {
            logger.info(
                "action_rail_dual_anchor_probe",
                attributes = mapOf("like_available" to false, "collect_attempted" to false),
            )
            return null
        }
        val collectMatch = actionRailAnchorTemplateMatcher.findMatch(
            bitmap = bitmap,
            templateId = AlphaMaskedActionIconMatcher.COLLECT_TEMPLATE_ID,
            searchRegion = actionRailTemplateSearchRegion(
                bitmap = bitmap,
                topFraction = COLLECT_ANCHOR_TEMPLATE_SEARCH_TOP_FRACTION,
                bottomFraction = COLLECT_ANCHOR_TEMPLATE_SEARCH_BOTTOM_FRACTION,
            ),
        )
        logger.info(
            "action_rail_dual_anchor_probe",
            attributes = mapOf(
                "like_available" to true,
                "collect_attempted" to true,
                "collect_available" to (collectMatch != null),
            ),
        )
        if (collectMatch == null) return null
        val likeBounds = mapScreenshotBounds(likeMatch.bounds, bitmap, screenSize)
        val collectBounds = mapScreenshotBounds(collectMatch.bounds, bitmap, screenSize)
        return ActionRailAnchorTemplateMatch(
            likeBounds = likeBounds,
            likeConfidence = likeMatch.confidence,
            collectBounds = collectBounds,
            collectConfidence = collectMatch.confidence,
        )
    }

    private fun actionRailTemplateSearchRegion(
        bitmap: Bitmap,
        topFraction: Float,
        bottomFraction: Float,
    ): Rect = Rect(
        (bitmap.width * ACTION_RAIL_ANCHOR_TEMPLATE_SEARCH_LEFT_FRACTION).roundToInt(),
        (bitmap.height * topFraction).roundToInt(),
        bitmap.width,
        (bitmap.height * bottomFraction).roundToInt(),
    )

    /** Converts screenshot-space visual evidence through display ratios, never fixed pixels. */
    private fun mapScreenshotBounds(
        screenshotBounds: Rect,
        bitmap: Bitmap,
        screenSize: ScreenSize,
    ): ScreenBounds = ScreenBounds(
        left = (screenshotBounds.left.toFloat() / bitmap.width * screenSize.width).roundToInt()
            .coerceIn(0, screenSize.width),
        top = (screenshotBounds.top.toFloat() / bitmap.height * screenSize.height).roundToInt()
            .coerceIn(0, screenSize.height),
        right = (screenshotBounds.right.toFloat() / bitmap.width * screenSize.width).roundToInt()
            .coerceIn(0, screenSize.width),
        bottom = (screenshotBounds.bottom.toFloat() / bitmap.height * screenSize.height).roundToInt()
            .coerceIn(0, screenSize.height),
    )

    private suspend fun captureEmptyMessageProbeContext(): ScreenContext? {
        val base = currentWindowContext() ?: latestContext ?: return null
        val engine = ocr ?: return base
        return runCatching {
            val artifact = screenshotCapture.capture("empty_message_probe")
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(artifact.path) }
                ?: return@runCatching base
            try {
                val result = engine.recognize(bitmap, OcrRegion.MESSAGE_COMPOSER)
                val enriched = base.copy(
                    ocrBlocks = AppOwnedOverlayExclusion.filterOcrBlocks(OcrTextBlockMapper.map(result)),
                    capturedAtMillis = System.currentTimeMillis(),
                )
                latestContext = enriched
                logger.info(
                    "empty_message_probe_ocr_ready",
                    attributes = mapOf("blocks" to result.blocks.size),
                )
                enriched
            } finally {
                bitmap.recycle()
            }
        }.onFailure { error ->
            logger.warn(
                "empty_message_probe_ocr_failed",
                message = "Blank-message OCR probe failed; accessibility events remain authoritative",
                attributes = mapOf("cause" to (error::class.java.simpleName ?: "Throwable")),
            )
        }.getOrDefault(base)
    }

    /** Returns to user results after a verified send or blank probe, then selects the next row. */
    private suspend fun advanceAfterEmptyMessageProbe() {
        if (!taskActive) return
        val maxUsers = activeTaskSnapshot?.maxUsers
        if (maxUsers != null && processedUserIdentityRecords.size >= maxUsers) {
            completeTaskAtUserLimit(maxUsers)
            return
        }
        val previousAnchorBottom = lastProcessedUserAnchorBottom
        val currentJob = coroutineContext[Job]
        messageEntryPostconditionJob?.takeUnless { it === currentJob }?.cancel()
        messageEntryPostconditionJob = null

        var resultsContext: ScreenContext? = null
        for (attempt in 0 until TuningConstants.NavigationFlow.MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE) {
            val currentContext = currentWindowContext()
            if (currentContext != null && pageDetector.detect(currentContext).kind == PageKind.USER_RESULTS) {
                resultsContext = currentContext
                break
            }
            if (!gestures.globalBack().succeeded) {
                failTaskWithoutManualHandoff("Could not return to user results after the blank-message probe")
                return
            }
            resultsContext = awaitPageAfterProfileBack(
                accepted = setOf(PageKind.USER_RESULTS),
                tag = "empty_message_probe",
            )
            if (resultsContext != null && pageDetector.detect(resultsContext!!).kind == PageKind.USER_RESULTS) {
                break
            }
        }
        resultsContext = resultsContext ?: currentWindowContext()
        if (resultsContext == null || pageDetector.detect(resultsContext!!).kind != PageKind.USER_RESULTS) {
            failTaskWithoutManualHandoff("User results did not return after the blank-message probe")
            return
        }

        // Keep processing the current viewport first. Only scroll when the row immediately after
        // the processed anchor is not exposed; this prevents M2 from skipping the first several
        // visible users after every successful blank-message probe.
        if (previousAnchorBottom != null) {
            val nextVisible = StructuralUserRowDetector.findAfter(
                resultsContext!!,
                previousAnchorBottom,
            )
            if (nextVisible != null) {
                logger.info(
                    "empty_message_probe_next_visible_requested",
                    attributes = mapOf(
                        "anchor_bottom" to previousAnchorBottom,
                        "next_anchor_top" to nextVisible.anchor.bounds.top,
                    ),
                )
                phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                AutomationStore.publishPhase(phase)
                selectVisibleUser(resultsContext!!, minimumAnchorTop = previousAnchorBottom)
                return
            }
        }

        val scroll = swipeToNextUserPage("empty_message_probe_next")
        logger.info(
            "empty_message_probe_next_requested",
            attributes = mapOf("route" to scroll.route),
        )
        if (!scroll.succeeded) {
            failTaskWithoutManualHandoff("Could not advance to the next user after the blank-message probe")
            return
        }
        val nextContext = awaitUserResultsAfterScroll("empty_message_probe")
        if (nextContext == null) {
            if (queryTransitionHandled) return
            failTaskWithoutManualHandoff("The next user result page was not detected after the blank-message probe")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        selectAfterViewportAnchor(nextContext, "empty_message_probe")
    }

    /**
     * RecyclerView/custom-rendered result pages can expose a transient incomplete tree immediately
     * after a swipe. Poll the live page for a bounded interval before treating it as a failure.
     */
    private suspend fun awaitUserResultsAfterScroll(tag: String): ScreenContext? {
        val beforeSwipe = userResultsSignatureBeforeSwipe
        var lastStableSignature: String? = null
        var stableObservations = 0
        repeat(TuningConstants.NavigationFlow.USER_NEXT_RESULT_POSTCONDITION_ATTEMPTS) { attempt ->
            delay(if (attempt == 0) TuningConstants.NavigationFlow.USER_NEXT_RESULT_DELAY_MS else TuningConstants.NavigationFlow.USER_NEXT_RESULT_POSTCONDITION_INTERVAL_MS)
            val context = currentWindowContext()
            if (context == null) {
                logger.info(
                    "user_result_next_waiting",
                    attributes = mapOf("tag" to tag, "attempt" to attempt + 1, "page" to "NO_CONTEXT"),
                )
                return@repeat
            }
            val detection = pageDetector.detect(context)
            val signature = if (detection.kind == PageKind.USER_RESULTS) {
                userResultViewportSignature(context)
            } else {
                null
            }
            val changed = beforeSwipe == null || signature == null || signature != beforeSwipe
            if (detection.kind == PageKind.USER_RESULTS && changed && signature != null) {
                if (signature == lastStableSignature) {
                    stableObservations += 1
                } else {
                    lastStableSignature = signature
                    stableObservations = 1
                }
            } else {
                lastStableSignature = null
                stableObservations = 0
            }
            logger.info(
                "user_result_next_postcondition",
                attributes = mapOf(
                    "tag" to tag,
                    "attempt" to (attempt + 1),
                    "page" to detection.kind.name,
                    "confidence" to detection.confidence,
                    "nodes" to context.nodes.size,
                    "viewport_changed" to changed,
                    "stable_observations" to stableObservations,
                ),
            )
            if (detection.kind == PageKind.USER_RESULTS && changed && stableObservations >= 2) {
                userResultsSignatureBeforeSwipe = null
                return context
            }
            if (detection.kind == PageKind.USER_RESULTS && !changed) {
                logger.info(
                    "user_result_next_waiting",
                    message = "The first result snapshot still matches the pre-swipe viewport",
                    attributes = mapOf("tag" to tag, "attempt" to attempt + 1),
                )
            }
        }
        logger.warn(
            "user_result_next_timeout",
            message = "The next user-result viewport was not available before the bounded wait expired",
            attributes = mapOf("tag" to tag, "attempts" to TuningConstants.NavigationFlow.USER_NEXT_RESULT_POSTCONDITION_ATTEMPTS),
        )
        userResultsSignatureBeforeSwipe = null
        if (advanceToNextQueryIfAvailable(tag)) return null
        return null
    }

    /**
     * A stable, unchanged viewport after the bounded network wait is the only condition that may
     * advance a multi-query task.  If another query exists, return to Douyin's verified search
     * entry and submit the frozen next query; otherwise finish the task without opening another
     * profile.  Unknown or covered windows remain a manual handoff.
     */
    private suspend fun advanceToNextQueryIfAvailable(tag: String): Boolean {
        val snapshot = activeTaskSnapshot ?: return false
        val cursor = TaskQueryCursor(snapshot.composedQueries, taskQueryIndex)
        val next = cursor.next()
        queryTransitionHandled = true
        if (next == null) {
            val endContext = currentWindowContext()
            if (endContext != null && hasConfirmedUserResultsEnd(endContext)) {
                completeTaskAtQueryEnd()
            } else {
                // A slow/half-loaded RecyclerView is not proof that the query is exhausted. Do
                // not publish COMPLETED here: the mobile client may still have queued records,
                // and the backend would then reject them with 409. Preserve the checkpoint and
                // pause with a visible reason so the operator can resume after the list settles.
                logger.warn(
                    "task_end_not_confirmed",
                    message = "The result list did not expose an explicit end marker after the bounded wait; pausing instead of completing",
                    attributes = mapOf("tag" to tag),
                )
                failTaskWithoutManualHandoff("用户结果分页等待超时，未确认已到末尾")
            }
            return true
        }

        taskQueryIndex = next.index
        keyword = next.current
        lastProcessedUserAnchorBottom = null
        cachedUserResultsViewportSignature = null
        cachedUserResultsOcrBlocks = emptyList()
        userResultsSignatureBeforeSwipe = null
        persistTaskCheckpoint()
        phase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY
        AutomationStore.publishPhase(phase)
        logger.info(
            "task_query_advanced",
            message = "The current result viewport was stable at its end; advancing to the next frozen query",
            attributes = mapOf("tag" to tag, "query_index" to taskQueryIndex, "query_count" to snapshot.composedQueries.size),
        )

        var context = currentWindowContext()
        repeat(TuningConstants.NavigationFlow.MAX_BACK_ACTIONS_TO_SEARCH_ENTRY) { attempt ->
            if (context != null && pageDetector.detect(context!!).kind == PageKind.SEARCH_ENTRY) return@repeat
            if (context != null && pageDetector.detect(context!!).kind == PageKind.HOME) {
                openSearch(context!!)
                return true
            }
            if (!gestures.globalBack().succeeded) {
                context = null
                return@repeat
            }
            context = awaitPageAfterProfileBack(
                accepted = setOf(PageKind.SEARCH_ENTRY, PageKind.HOME),
                tag = "query_switch",
            )
            logger.info(
                "task_query_search_entry_back_probe",
                attributes = mapOf("attempt" to attempt + 1, "page" to (context?.let { pageDetector.detect(it).kind.name } ?: "NO_CONTEXT")),
            )
        }
        context = currentWindowContext()
        val detection = context?.let(pageDetector::detect)
        when (detection?.kind) {
            PageKind.SEARCH_ENTRY -> enterKeyword(context!!, preserveTimeoutRecoveryBudget = true)
            PageKind.HOME -> openSearch(context!!)
            else -> {
                queryTransitionHandled = false
                pause("下一组搜索词切换时未回到可确认的搜索页面")
            }
        }
        return true
    }

    /** Only an explicit Douyin end-of-list label may complete the last frozen query. */
    private fun hasConfirmedUserResultsEnd(context: ScreenContext): Boolean {
        val endMarkers = listOf(
            "没有更多",
            "没有更多了",
            "已加载全部",
            "到底了",
            "no more",
            "end of results",
        )
        return (context.nodeText() + context.ocrText()).any { value ->
            val normalized = TextNormalizer.normalize(value)
            endMarkers.any { marker -> normalized.contains(TextNormalizer.normalize(marker)) }
        } || UserResultMarkers.accountHelpOnly(context)
    }

    /**
     * Legacy explicit operator command retained for later reviewed use. The M2 UI does not expose
     * this route, so normal runs always use [probeEmptyMessageOnce].
     */
    private suspend fun sendMessageOnce(rawMessage: String) {
        val message = rawMessage.trim()
        if (message.isEmpty()) {
            AutomationStore.publishFailure("Enter a message before sending")
            logger.warn("message_send_rejected", message = "The operator did not provide a message")
            return
        }
        if (message.length > TuningConstants.NavigationFlow.MAX_MESSAGE_LENGTH) {
            AutomationStore.publishFailure("Message is too long for the one-message POC")
            logger.warn("message_send_rejected", message = "The operator message exceeded the safe length limit")
            return
        }
        if (phase != AutomationPhase.COMPLETED_AT_MESSAGE_PAGE) {
            AutomationStore.publishFailure("Open and verify a private-message page before sending")
            logger.warn("message_send_rejected", message = "No verified direct-message page is available")
            return
        }

        taskActive = true
        phase = AutomationPhase.SENDING_MESSAGE
        AutomationStore.publishPhase(phase)

        var context = currentWindowContext()
        if (context == null || pageDetector.detect(context).kind != PageKind.DIRECT_MESSAGE) {
            gestures.awaitExternalActionSlot("target_app_launch")
            when (val launch = TargetAppLauncher.launch(service)) {
                LaunchResult.Started -> {
                    delay(TuningConstants.NavigationFlow.MESSAGE_TARGET_RESTORE_DELAY_MS)
                    context = currentWindowContext()
                }
                is LaunchResult.Failed -> {
                    skipMessageSendFailure(
                        "Could not restore the verified private-message page: ${launch.reason}",
                        failurePage = PageKind.USER_PROFILE,
                    )
                    return
                }
            }
        }
        val directContext = context
        if (directContext == null || pageDetector.detect(directContext).kind != PageKind.DIRECT_MESSAGE) {
            skipMessageSendFailure(
                "The verified private-message page is no longer visible",
                failurePage = PageKind.USER_PROFILE,
            )
            return
        }

        var inputPlaced = false
        var inputFailure = "Could not find the private-message input"
        repeat(TuningConstants.NavigationFlow.MESSAGE_INPUT_ATTEMPTS) { attempt ->
            if (inputPlaced) return@repeat
            if (attempt > 0) delay(TuningConstants.NavigationFlow.MESSAGE_INPUT_RETRY_INTERVAL_MS)
            val liveContext = currentWindowContext() ?: directContext
            val input = selector.select(liveContext, DouyinSelectors.messageInput).node
            if (input == null) {
                inputFailure = "Could not find the private-message input"
                return@repeat
            }
            val setText = withLiveNode(input) { liveNode -> gestures.setText(liveNode, message) }
            if (setText.succeeded) {
                inputPlaced = true
            } else {
                inputFailure = "Could not place the message in the private-message input"
                logger.warn(
                    "message_input_retry",
                    message = inputFailure,
                    attributes = mapOf("attempt" to (attempt + 1), "reason" to setText.reason),
                )
            }
        }
        if (!inputPlaced) {
            skipMessageSendFailure(inputFailure, failurePage = PageKind.DIRECT_MESSAGE)
            return
        }

        delay(TuningConstants.NavigationFlow.MESSAGE_INPUT_SETTLE_DELAY_MS)
        val refreshedContext = currentWindowContext() ?: directContext
        var sendButtonOutcome = ActionOutcome.failure("No usable message send action")
        repeat(TuningConstants.NavigationFlow.MESSAGE_ACTION_ATTEMPTS) { attempt ->
            if (sendButtonOutcome.succeeded) return@repeat
            if (attempt > 0) delay(TuningConstants.NavigationFlow.MESSAGE_INPUT_RETRY_INTERVAL_MS)
            sendButtonOutcome = clickSelector(
                currentWindowContext() ?: refreshedContext,
                DouyinSelectors.messageSendAction,
            )
        }
        val sendOutcome = if (sendButtonOutcome.succeeded) {
            sendButtonOutcome
        } else {
            val refreshedInput = selector.select(refreshedContext, DouyinSelectors.messageInput).node
            if (refreshedInput == null) {
                ActionOutcome.failure("Message input disappeared before submit")
            } else {
                withLiveNode(refreshedInput) { liveNode -> gestures.submitText(liveNode) }
            }
        }
        if (!sendOutcome.succeeded) {
            skipMessageSendFailure("Could not find a usable message send action", failurePage = PageKind.DIRECT_MESSAGE)
            return
        }

        logger.info("message_send_submitted", attributes = mapOf("route" to sendOutcome.route))
        timeoutJob?.cancel()
        phase = AutomationPhase.WAITING_FOR_MESSAGE_RESULT
        AutomationStore.publishPhase(phase)
        scheduleMessageResultCheck(message)
    }

    private suspend fun completeAtMessagePage() {
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        profilePostconditionJob?.cancel()
        messageResultJob?.cancel()
        enrichCurrentUserDisplayNameFromDirectMessage(currentWindowContext() ?: latestContext)
        phase = AutomationPhase.COMPLETED_AT_MESSAGE_PAGE
        AutomationStore.publishPhase(phase)
        val message = pendingStartMessage
        pendingStartMessage = ""
        val safetyProbe = pendingSafetyProbe
        pendingSafetyProbe = true
        if (safetyProbe) {
            logger.info(
                "empty_message_probe_requested",
                message = "M2 safety mode will submit one space and require the blank-message notice; no real message will be sent",
            )
            // Do not run the probe inline from onScreenObserved(). That callback is invoked while
            // the service holds its inspection lock; reading the active window again and
            // submitting text from inside that lock can stall the accessibility event loop on
            // some Douyin chat surfaces. Start it after the current observation returns and take
            // the controller mutex in the normal command path.
            scope.launch {
                mutex.withLock {
                    if (taskActive && phase == AutomationPhase.COMPLETED_AT_MESSAGE_PAGE) {
                        probeEmptyMessageOnce()
                    }
                }
            }
        } else if (message.isBlank()) {
            taskActive = false
            logger.info("poc_completed", message = "Direct-message page verified; no message was created or sent")
        } else {
            logger.info(
                "message_send_auto_requested",
                message = "A non-empty Start message was supplied; sending one message after page verification",
                attributes = mapOf("message_length" to message.length),
            )
            scope.launch {
                mutex.withLock {
                    if (taskActive && phase == AutomationPhase.COMPLETED_AT_MESSAGE_PAGE) {
                        sendMessageOnce(message)
                    }
                }
            }
        }
    }

    private suspend fun completeMessageSent() {
        if (!taskActive || phase != AutomationPhase.WAITING_FOR_MESSAGE_RESULT) return
        val currentJob = coroutineContext[Job]
        timeoutJob?.takeUnless { it === currentJob }?.cancel()
        messageResultJob?.takeUnless { it === currentJob }?.cancel()
        messageResultJob = null
        restrictedUserSkips = 0
        enrichCurrentUserDisplayNameFromDirectMessage(currentWindowContext() ?: latestContext)
        phase = AutomationPhase.COMPLETED_MESSAGE_SENT
        AutomationStore.publishPhase(phase)
        logger.info("message_send_completed", message = "One operator-requested message was verified in the conversation")
        AutomationStore.recordUserTaskFinished(
            identityFingerprint = currentUserIdentityFingerprint,
            outcome = UserTaskRecord.Outcome.MESSAGE_SENT,
            reason = "The operator-requested message was verified in the conversation",
            page = PageKind.DIRECT_MESSAGE,
            displayName = currentUserDisplayName,
        )
        currentUserIdentityFingerprint = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null
        advanceAfterEmptyMessageProbe()
    }

    private fun completeTaskAtUserLimit(maxUsers: Int) {
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        profilePostconditionJob?.cancel()
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        taskActive = false
        AutomationStore.clearTaskCheckpoint()
        logger.info(
            "task_completed_user_limit",
            message = "The configured user limit was reached; no additional profile was opened",
            attributes = mapOf("max_users" to maxUsers),
        )
        phase = AutomationPhase.COMPLETED_TASK
        publishTaskTerminal(phase)
    }

    private fun completeTaskAtQueryEnd() {
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        profilePostconditionJob?.cancel()
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        taskActive = false
        AutomationStore.clearTaskCheckpoint()
        logger.info(
            "task_completed_query_end",
            message = "All frozen search queries reached a stable end without opening another profile",
            attributes = mapOf("query_count" to (activeTaskSnapshot?.composedQueries?.size ?: 1)),
        )
        phase = AutomationPhase.COMPLETED_TASK
        publishTaskTerminal(phase)
    }

    private fun scheduleMessageResultCheck(expectedMessage: String) {
        messageResultJob?.cancel()
        messageResultJob = scope.launch {
            repeat(TuningConstants.NavigationFlow.MESSAGE_RESULT_ATTEMPTS) { attempt ->
                delay(TuningConstants.NavigationFlow.MESSAGE_RESULT_INTERVAL_MS)
                mutex.withLock {
                    if (!taskActive || phase != AutomationPhase.WAITING_FOR_MESSAGE_RESULT) return@withLock
                    val context = currentWindowContext() ?: return@withLock
                    val detection = pageDetector.detect(context)
                    logger.info(
                        "message_send_postcondition",
                        attributes = mapOf(
                            "attempt" to (attempt + 1),
                            "page" to detection.kind.name,
                            "confidence" to detection.confidence,
                            "nodes" to context.nodes.size,
                        ),
                    )
                    when (detection.kind) {
                        PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                            "Douyin rejected the message because of the recipient's messaging settings",
                        )
                        PageKind.DIRECT_MESSAGE -> if (MessageSendSuccessDetector.matches(context, expectedMessage)) {
                            completeMessageSent()
                        }
                        else -> Unit
                    }
                }
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_MESSAGE_RESULT) return@launch
            }
            mutex.withLock {
                if (taskActive && phase == AutomationPhase.WAITING_FOR_MESSAGE_RESULT) {
                    skipMessageSendFailure(
                        "The message result was not verified before the send timeout",
                    )
                }
            }
        }
    }

    private fun scheduleMessageEntryPostconditionCheck() {
        if (messageEntryPostconditionJob?.isActive == true) return
        messageEntryPostconditionJob = scope.launch {
            delay(TuningConstants.NavigationFlow.MESSAGE_ENTRY_POSTCONDITION_DELAY_MS)
            mutex.withLock {
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_DIRECT_MESSAGE) return@withLock
                val context = currentWindowContext() ?: return@withLock
                val detection = pageDetector.detect(context)
                logger.info(
                    "private_message_postcondition",
                    attributes = mapOf("page" to detection.kind.name, "confidence" to detection.confidence, "nodes" to context.nodes.size),
                )
                when (detection.kind) {
                    PageKind.DIRECT_MESSAGE -> completeAtMessagePage()
                    PageKind.PRIVATE_MESSAGE_RESTRICTED -> skipRestrictedUser("Douyin requires following before private messaging")
                    PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                        "Douyin rejected the message because of the recipient's messaging settings",
                    )
                    // A delayed profile callback does not prove that the tap failed. Re-enter the
                    // bounded action/post-condition loop once more before deciding that the
                    // account is unavailable.
                    PageKind.USER_PROFILE -> openPrivateMessage(context)
                    else -> Unit
                }
            }
        }
    }

    /**
     * Returns to the user results and advances the list by a bounded vertical gesture. This is
     * intentionally limited to follow-gated/unavailable profiles; it never follows an account or
     * attempts to bypass the restriction.
     */
    private suspend fun skipRestrictedUser(reason: String) {
        if (!taskActive) return
        val currentJob = coroutineContext[Job]
        timeoutJob?.takeUnless { it === currentJob }?.cancel()
        messageEntryPostconditionJob?.takeUnless { it === currentJob }?.cancel()
        messageEntryPostconditionJob = null
        restrictedUserSkips += 1
        logger.warn(
            "private_message_unavailable",
            message = reason,
            attributes = mapOf("skipped_users" to restrictedUserSkips),
        )
        enrichCurrentUserDisplayNameFromDirectMessage(currentWindowContext() ?: latestContext)
        AutomationStore.recordUserTaskFinished(
            identityFingerprint = currentUserIdentityFingerprint,
            outcome = UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
            reason = reason,
            page = PageKind.USER_PROFILE,
        )
        currentUserIdentityFingerprint = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null

        if (!gestures.globalBack().succeeded) {
            failTaskWithoutManualHandoff("Could not leave the unavailable user profile")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        val resultsContext = awaitPageAfterProfileBack(
            accepted = setOf(PageKind.USER_RESULTS),
            tag = "skip_restricted",
        )
        if (resultsContext == null || pageDetector.detect(resultsContext).kind != PageKind.USER_RESULTS) {
            failTaskWithoutManualHandoff("User results did not return after skipping an unavailable profile")
            return
        }

        // Move to the next visible row without paging through the entire list. The next
        // selection still uses StructuralUserRowDetector and the avatar-excluding tap band.
        val previousAnchorBottom = lastProcessedUserAnchorBottom
        if (previousAnchorBottom != null) {
            val nextVisible = StructuralUserRowDetector.findAfter(
                resultsContext!!,
                previousAnchorBottom,
            )
            if (nextVisible != null) {
                logger.info(
                    "private_message_unavailable_next_visible_requested",
                    attributes = mapOf(
                        "anchor_bottom" to previousAnchorBottom,
                        "next_anchor_top" to nextVisible.anchor.bounds.top,
                    ),
                )
                phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                AutomationStore.publishPhase(phase)
                selectVisibleUser(resultsContext!!, minimumAnchorTop = previousAnchorBottom)
                return
            }
        }

        val scroll = swipeToNextUserPage("restricted_user_skip")
        logger.info("user_result_next_requested", attributes = mapOf("route" to scroll.route, "skipped_users" to restrictedUserSkips))
        if (!scroll.succeeded) {
            failTaskWithoutManualHandoff("Could not advance to the next user result")
            return
        }
        val nextContext = awaitUserResultsAfterScroll("restricted_user_skip")
        if (nextContext == null) {
            if (queryTransitionHandled) return
            failTaskWithoutManualHandoff("The next user result page was not detected")
            return
        }
        selectAfterViewportAnchor(nextContext, "message_failure")
    }

    /**
     * Handles a delivery failure that appears after a message bubble is created. Unlike a
     * profile-level follow gate, the current screen is the conversation itself, so two bounded
     * back actions may be needed (conversation -> profile -> user results). The method never
     * retries the send or follows the account to bypass its privacy setting.
     *
     * The one-message M1 sender uses this branch after an explicit send command.
     */
    private suspend fun skipMessageSendFailure(
        reason: String,
        failurePage: PageKind = PageKind.MESSAGE_SEND_FAILED,
    ) {
        if (!taskActive) return
        timeoutJob?.cancel()
        val currentJob = coroutineContext[Job]
        messageEntryPostconditionJob?.takeUnless { it === currentJob }?.cancel()
        messageResultJob?.takeUnless { it === currentJob }?.cancel()
        messageEntryPostconditionJob = null
        messageResultJob = null
        restrictedUserSkips += 1
        logger.warn(
            "private_message_send_failed",
            message = reason,
            attributes = mapOf("skipped_users" to restrictedUserSkips),
        )
        enrichCurrentUserDisplayNameFromDirectMessage(currentWindowContext() ?: latestContext)
        AutomationStore.recordUserTaskFinished(
            identityFingerprint = currentUserIdentityFingerprint,
            outcome = UserTaskRecord.Outcome.MESSAGE_SEND_FAILED,
            reason = reason,
            page = failurePage,
        )
        currentUserIdentityFingerprint = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null

        var resultsContext: ScreenContext? = null
        for (attempt in 0 until TuningConstants.NavigationFlow.MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE) {
            val currentContext = currentWindowContext()
            if (currentContext != null && pageDetector.detect(currentContext).kind == PageKind.USER_RESULTS) {
                resultsContext = currentContext
                break
            }
            if (!gestures.globalBack().succeeded) {
                failTaskWithoutManualHandoff("Could not return to user results after a message-send failure")
                return
            }
            resultsContext = awaitPageAfterProfileBack(
                accepted = setOf(PageKind.USER_RESULTS),
                tag = "message_send_failure",
            )
            if (resultsContext != null && pageDetector.detect(resultsContext!!).kind == PageKind.USER_RESULTS) {
                break
            }
        }
        resultsContext = resultsContext ?: currentWindowContext()
        if (resultsContext == null || pageDetector.detect(resultsContext!!).kind != PageKind.USER_RESULTS) {
            failTaskWithoutManualHandoff("User results did not return after a message-send failure")
            return
        }

        val previousAnchorBottom = lastProcessedUserAnchorBottom
        if (previousAnchorBottom != null) {
            val nextVisible = StructuralUserRowDetector.findAfter(resultsContext!!, previousAnchorBottom)
            if (nextVisible != null) {
                logger.info(
                    "message_failure_next_visible_requested",
                    attributes = mapOf(
                        "anchor_bottom" to previousAnchorBottom,
                        "next_anchor_top" to nextVisible.anchor.bounds.top,
                    ),
                )
                phase = AutomationPhase.WAITING_FOR_USER_RESULTS
                AutomationStore.publishPhase(phase)
                selectVisibleUser(resultsContext!!, minimumAnchorTop = previousAnchorBottom)
                return
            }
        }

        val scroll = swipeToNextUserPage("message_failure_skip")
        logger.info(
            "user_result_next_requested",
            attributes = mapOf("route" to scroll.route, "skipped_users" to restrictedUserSkips),
        )
        if (!scroll.succeeded) {
            failTaskWithoutManualHandoff("Could not advance to the next user result after a message-send failure")
            return
        }
        val nextContext = awaitUserResultsAfterScroll("message_failure")
        if (nextContext == null) {
            if (queryTransitionHandled) return
            failTaskWithoutManualHandoff("The next user result page was not detected after a message-send failure")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        selectAfterViewportAnchor(nextContext, "message_failure")
    }

    /**
     * The current Douyin build often exposes only the right-side action anchor. Capture OCR once
     * per viewport in that case, then reuse the blocks for every overlapping row in the viewport.
     * This keeps identity continuity without taking a screenshot before every user.
     */
    private suspend fun enrichUserResultIdentityContext(
        context: ScreenContext,
        match: StructuralUserRowMatch,
        forceOcr: Boolean = false,
        forceFreshOcr: Boolean = false,
    ): ScreenContext {
        val blockedKeywordAuditRequired = activeTaskSnapshot?.normalizedBlockedKeywords?.isNotEmpty() == true
        if (!forceOcr &&
            !blockedKeywordAuditRequired &&
            UserResultIdentityExtractor.extract(context, match)?.source == UserResultIdentity.Source.ACCESSIBILITY
        ) {
            return context
        }
        if (context.ocrBlocks.isNotEmpty()) return context

        val viewportSignature = userResultViewportSignature(context)
        if (!forceFreshOcr &&
            viewportSignature == cachedUserResultsViewportSignature &&
            cachedUserResultsOcrBlocks.isNotEmpty()
        ) {
            return context.copy(ocrBlocks = cachedUserResultsOcrBlocks)
        }
        val ocrEngine = ocr ?: return context
        return try {
            val artifact = screenshotCapture.capture("user_results_identity")
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(artifact.path) }
                ?: return context
            try {
                val result = ocrEngine.recognize(bitmap, OcrRegion.USER_RESULTS)
                val blocks = AppOwnedOverlayExclusion.filterOcrBlocks(OcrTextBlockMapper.map(result))
                cachedUserResultsViewportSignature = viewportSignature
                cachedUserResultsOcrBlocks = blocks
                logger.info(
                    "user_result_identity_ocr_ready",
                    attributes = mapOf("blocks" to blocks.size, "viewport_hash" to viewportSignature.hashCode()),
                )
                context.copy(ocrBlocks = blocks)
            } finally {
                bitmap.recycle()
            }
        } catch (error: Throwable) {
            logger.warn(
                "user_result_identity_ocr_failed",
                message = "OCR identity fallback failed; continuing with bounded paging",
                attributes = mapOf("cause" to (error::class.java.simpleName ?: "Throwable")),
            )
            context
        }
    }

    private fun userResultViewportSignature(context: ScreenContext): String =
        UserResultsViewportFingerprint.create(context, topRatio = TuningConstants.NavigationFlow.USER_RESULTS_TOP_RATIO)

    private suspend fun clickSelector(context: ScreenContext, request: SelectorRequest): ActionOutcome {
        val liveContext = waitForTargetWindow("click_${request.name}") ?: context
        val selection = selector.select(liveContext, request)
        val target = selection.node ?: return ActionOutcome.failure(selection.reasons.joinToString())
        return withLiveNode(target) { liveNode -> gestures.click(liveNode, target.bounds) }
    }

    private suspend fun clickSelection(context: ScreenContext, selection: SelectionResult): ActionOutcome {
        val target = selection.node ?: return ActionOutcome.failure(selection.reasons.joinToString())
        return withLiveNode(target) { liveNode -> gestures.click(liveNode, target.bounds) }
    }

    private suspend fun tapSelectorBounds(context: ScreenContext, request: SelectorRequest): ActionOutcome {
        val liveContext = waitForTargetWindow("tap_${request.name}") ?: context
        val selection = selector.select(liveContext, request)
        val target = selection.node ?: return ActionOutcome.failure(selection.reasons.joinToString())
        logger.info(
            "selector_bounds_action",
            attributes = mapOf(
                "selector" to request.name,
                "bounds" to target.bounds,
                "score" to selection.score,
            ),
        )
        return gestures.tapBounds(target.bounds)
    }

    /**
     * A transient Android/OEM banner can become the active window and intercept the next gesture
     * (for example, the full-charge banner covering the upper search controls). Wait briefly for
     * the Douyin window to become active again instead of sending a gesture through the overlay.
     */
    private suspend fun waitForTargetWindow(tag: String): ScreenContext? {
        var overlayReported = false
        repeat(TuningConstants.NavigationFlow.SYSTEM_OVERLAY_WAIT_ATTEMPTS) { attempt ->
            val context = currentWindowContext()
            if (context != null) {
                val liveOverlay = TransientOverlayDetector.find(context)
                if (liveOverlay != null) {
                    if (!overlayReported) {
                        overlayReported = true
                        logger.warn(
                            "transient_live_overlay_detected",
                            message = "A live notification is covering the action surface; waiting before retrying",
                            attributes = mapOf(
                                "marker" to liveOverlay.marker,
                                "top" to liveOverlay.bounds.top,
                                "bottom" to liveOverlay.bounds.bottom,
                            ),
                        )
                    }
                    delay(TuningConstants.NavigationFlow.SYSTEM_OVERLAY_WAIT_INTERVAL_MS)
                    return@repeat
                }
                if (overlayReported) {
                    logger.info(
                        "system_overlay_cleared",
                        attributes = mapOf("tag" to tag, "attempt" to attempt + 1),
                    )
                }
                return context
            }
            if (!overlayReported) {
                overlayReported = true
                logger.warn(
                    "system_overlay_detected",
                    message = "The Douyin window is temporarily unavailable; waiting before retrying the action",
                    attributes = mapOf("tag" to tag),
                )
            }
            delay(TuningConstants.NavigationFlow.SYSTEM_OVERLAY_WAIT_INTERVAL_MS)
        }
        logger.warn(
            "target_window_unavailable",
            message = "The Douyin window did not return after a transient system overlay or window change",
            attributes = mapOf("tag" to tag),
        )
        return null
    }

    private suspend fun tapNormalizedGuarded(point: NormalizedPoint, tag: String): ActionOutcome {
        if (waitForTargetWindow(tag) == null) {
            return ActionOutcome.failure("Douyin window is temporarily covered or unavailable")
        }
        return gestures.tapNormalized(point)
    }

    private suspend fun tapBoundsGuarded(bounds: ScreenBounds, tag: String): ActionOutcome {
        if (waitForTargetWindow(tag) == null) {
            return ActionOutcome.failure("Douyin window is temporarily covered or unavailable")
        }
        return gestures.tapBounds(bounds)
    }

    /**
     * Advance the result viewport with a deliberate overlap. Identity tracking skips rows that
     * remain visible after this swipe, while the overlap prevents a short gesture from skipping
     * rows that sit between two accessibility snapshots.
     */
    private suspend fun swipeToNextUserPage(tag: String): ActionOutcome {
        userResultsSignatureBeforeSwipe = currentWindowContext()
            ?.takeIf { pageDetector.detect(it).kind == PageKind.USER_RESULTS }
            ?.let(::userResultViewportSignature)
        logger.info(
            "user_result_page_swipe_requested",
            attributes = mapOf(
                "tag" to tag,
                "start_y" to TuningConstants.NavigationFlow.USER_PAGE_SWIPE_START_Y,
                "end_y" to TuningConstants.NavigationFlow.USER_PAGE_SWIPE_END_Y,
                "travel" to (TuningConstants.NavigationFlow.USER_PAGE_SWIPE_START_Y - TuningConstants.NavigationFlow.USER_PAGE_SWIPE_END_Y),
            ),
        )
        val outcome = swipeNormalizedGuarded(
            startX = 0.50f,
            startY = TuningConstants.NavigationFlow.USER_PAGE_SWIPE_START_Y,
            endX = 0.50f,
            endY = TuningConstants.NavigationFlow.USER_PAGE_SWIPE_END_Y,
            durationMs = TuningConstants.NavigationFlow.USER_PAGE_SWIPE_DURATION_MS,
            tag = tag,
        )
        if (outcome.succeeded) remotePageNumber = (remotePageNumber + 1).coerceAtMost(10_000)
        return outcome
    }

    /** Exit an already-open live room before the underlying live item is swiped away. */
    private suspend fun exitLiveRoom(context: ScreenContext) {
        val closeButton = LiveRoomSurfaceDetector.findCloseButton(context)
        val outcome = if (closeButton != null) {
            withLiveNode(closeButton) { node -> gestures.click(node, closeButton.bounds) }
        } else {
            gestures.globalBack()
        }
        logger.info(
            "live_room_exited",
            attributes = mapOf("success" to outcome.succeeded, "route" to outcome.route),
        )
        if (!outcome.succeeded) {
            logger.warn("live_room_exit_failed", message = outcome.reason)
            DebugToast.showForEvent("live_room_exit_failed")
        } else {
            DebugToast.showForEvent("live_room_exited")
        }
    }

    /** Skip a live-feed item without ever clicking its “进入直播间” prompt. */
    private suspend fun swipeLiveRoomAway() {
        val outcome = swipeNormalizedGuarded(
            startX = 0.86f,
            startY = 0.84f,
            endX = 0.86f,
            endY = 0.22f,
            durationMs = TuningConstants.NavigationFlow.LIVE_ROOM_SWIPE_DURATION_MS,
            tag = "live_room_recovery",
        )
        logger.info(
            "live_room_swiped",
            attributes = mapOf("success" to outcome.succeeded, "route" to outcome.route),
        )
        if (!outcome.succeeded) {
            logger.warn("live_room_swipe_failed", message = outcome.reason)
            DebugToast.showForEvent("live_room_swipe_failed")
        } else {
            DebugToast.showForEvent("live_room_swiped")
            timeoutRecoveryAttempts = 0
        }
    }

    private suspend fun swipeNormalizedGuarded(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        tag: String,
    ): ActionOutcome {
        if (waitForTargetWindow(tag) == null) {
            return ActionOutcome.failure("Douyin window is temporarily covered or unavailable")
        }
        return gestures.swipeNormalized(startX, startY, endX, endY, durationMs)
    }

    private suspend fun ensureSearchKeyword(initialTarget: NodeSnapshot, expected: String): Boolean {
        delay(TuningConstants.NavigationFlow.KEYWORD_POSTCONDITION_DELAY_MS)
        val firstActual = readLiveNodeText(initialTarget)
        if (SearchKeywordVerifier.matches(expected, firstActual)) return true

        logger.warn(
            "search_keyword_postcondition_retry",
            message = "Search field did not expose the requested keyword after the first set-text action",
            attributes = mapOf(
                "expected_length" to expected.length,
                "actual_length" to (firstActual?.length ?: 0),
            ),
        )

        // The first snapshot may be stale while the search page is animating. Re-select the live
        // editable node and apply the same semantic action once more before giving up.
        val refreshedTarget = currentWindowContext()
            ?.let { selector.select(it, DouyinSelectors.searchInput).node }
            ?: return false
        val retryClick = withLiveNode(refreshedTarget) { liveNode ->
            gestures.click(liveNode, refreshedTarget.bounds)
        }
        if (!retryClick.succeeded) return false
        delay(TuningConstants.NavigationFlow.KEYWORD_POSTCONDITION_DELAY_MS)
        val retry = withLiveNode(refreshedTarget) { liveNode -> gestures.setText(liveNode, expected) }
        if (!retry.succeeded) return false
        delay(TuningConstants.NavigationFlow.KEYWORD_POSTCONDITION_DELAY_MS)
        val finalActual = readLiveNodeText(refreshedTarget)
        val verified = SearchKeywordVerifier.matches(expected, finalActual)
        if (!verified) {
            logger.error(
                "search_keyword_postcondition_failed",
                message = "Search field still did not contain the requested keyword; submission was blocked",
                attributes = mapOf(
                    "expected_length" to expected.length,
                    "actual_length" to (finalActual?.length ?: 0),
                ),
            )
        } else {
            logger.info("search_keyword_postcondition_verified")
        }
        return verified
    }

    @Suppress("DEPRECATION")
    private fun readLiveNodeText(target: NodeSnapshot): String? {
        val root = service.rootInActiveWindow ?: return null
        var liveNode: AccessibilityNodeInfo? = null
        return try {
            if (root.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) return null
            liveNode = selector.resolveLiveNode(root, target.hierarchyPath) ?: return null
            liveNode.text?.toString()
        } finally {
            if (liveNode != null && liveNode !== root) liveNode.recycle()
            root.recycle()
        }
    }

    /** Resolve an immutable snapshot path immediately before an action, then release the live node. */
    @Suppress("DEPRECATION")
    private suspend fun withLiveNode(
        target: NodeSnapshot,
        action: suspend (AccessibilityNodeInfo) -> ActionOutcome,
    ): ActionOutcome {
        if (waitForTargetWindow("accessibility_action") == null) {
            return ActionOutcome.failure("Douyin action surface is temporarily covered or unavailable")
        }
        val root = service.rootInActiveWindow ?: return ActionOutcome.failure("No active accessibility window")
        var liveNode: AccessibilityNodeInfo? = null
        try {
            if (root.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) {
                logger.warn(
                    "system_overlay_detected",
                    message = "The active window changed before the accessibility action could run",
                )
                return ActionOutcome.failure("Active window changed before the action")
            }
            liveNode = selector.resolveLiveNode(root, target.hierarchyPath)
                ?: return ActionOutcome.failure("Selected node no longer exists")
            return action(liveNode)
        } finally {
            if (liveNode != null && liveNode !== root) liveNode.recycle()
            root.recycle()
        }
    }

    private fun await(
        nextPhase: AutomationPhase,
        timeoutDescription: String,
        resetRecoveryBudget: Boolean = true,
    ) {
        timeoutJob?.cancel()
        if (resetRecoveryBudget) timeoutRecoveryAttempts = 0
        phase = nextPhase
        AutomationStore.publishPhase(nextPhase)
        timeoutJob = scope.launch {
            val timeoutMs = when (nextPhase) {
                // Cold-start ads, restored video pages, and the first ML Kit model load can all
                // consume more than one normal action interval. Give only the launch surface a
                // longer bounded window; user-row/profile/message steps retain the short guard.
                AutomationPhase.WAITING_FOR_HOME -> TuningConstants.NavigationLifecycle.STARTUP_STEP_TIMEOUT_MS
                AutomationPhase.WAITING_FOR_DIRECT_MESSAGE -> TuningConstants.NavigationFlow.MESSAGE_ENTRY_TIMEOUT_MS
                else -> TuningConstants.NavigationLifecycle.STEP_TIMEOUT_MS
            }
            delay(timeoutMs)
            mutex.withLock {
                if (taskActive && phase == nextPhase) {
                    logger.warn(
                        if (nextPhase == AutomationPhase.WAITING_FOR_DIRECT_MESSAGE) {
                            "private_message_entry_timeout"
                        } else {
                            "step_timeout"
                        },
                        attributes = mapOf("phase" to nextPhase.name, "timeout_ms" to timeoutMs),
                    )
                    captureDiagnosticsInternal("timeout_${nextPhase.name.lowercase()}")
                    if (!taskActive || phase != nextPhase) return@withLock

                    val currentTimeoutJob = coroutineContext[Job]
                    if (timeoutJob === currentTimeoutJob) timeoutJob = null
                    if (nextPhase == AutomationPhase.WAITING_FOR_DIRECT_MESSAGE) {
                        skipMessageSendFailure(
                            "The private-message page did not open within the allowed time",
                            failurePage = PageKind.USER_PROFILE,
                        )
                    } else if (nextPhase == AutomationPhase.WAITING_FOR_PROFILE) {
                        skipMessageSendFailure(
                            "The user profile did not open within the allowed time",
                            failurePage = PageKind.USER_RESULTS,
                        )
                    } else if (timeoutRecoveryAttempts < TuningConstants.NavigationFlow.MAX_TIMEOUT_RECOVERY_ATTEMPTS) {
                        timeoutRecoveryAttempts += 1
                        recoverAfterTimeout(nextPhase, timeoutDescription)
                    } else {
                        pause(timeoutDescription)
                    }
                }
            }
        }
    }

    /**
     * One bounded, phase-specific recovery is attempted after a watchdog timeout. A second
     * timeout is allowed to reach the existing manual-handoff path rather than looping forever.
     */
    private suspend fun recoverAfterTimeout(
        timedOutPhase: AutomationPhase,
        timeoutDescription: String,
    ) {
        val context = currentWindowContext()
        val detection = context?.let(pageDetector::detect)
        logger.warn(
            "step_timeout_recovery",
            message = "Attempting one bounded recovery from a timed-out phase",
            attributes = mapOf(
                "phase" to timedOutPhase.name,
                "current_page" to (detection?.kind?.name ?: "NO_CONTEXT"),
            ),
        )

        // A startup ad can leave the underlying HOME node tree visible. Detect it before routing
        // so a timeout can never turn into a tap through the ad banner.
        val startupAd = context
            ?.takeIf { timedOutPhase == AutomationPhase.WAITING_FOR_HOME }
            ?.let(TransientOverlayDetector::findStartupAd)
        recoveryFlow.onTimeout(
            timedOutPhase = timedOutPhase,
            context = context,
            page = detection?.kind,
            startupAdMarker = startupAd?.marker,
            timeoutDescription = timeoutDescription,
        )
    }

    private suspend fun waitForStartupAdTimeoutRecovery(
        marker: String,
        timeoutDescription: String,
    ) {
        logger.info(
            "startup_ad_timeout_recovery_wait",
            message = "The startup advertisement is still visible; extending the bounded wait",
            attributes = mapOf("marker" to marker),
        )
        await(
            nextPhase = AutomationPhase.WAITING_FOR_HOME,
            timeoutDescription = timeoutDescription,
            resetRecoveryBudget = false,
        )
        scheduleInitialObservation()
    }

    private suspend fun retryKeywordAfterSearchResultsTimeout(context: ScreenContext) {
        logger.warn(
            "search_results_timeout_retry",
            message = "Search entry is still visible; revalidating the keyword and resubmitting",
        )
        enterKeyword(context, preserveTimeoutRecoveryBudget = true)
    }

    private suspend fun skipProfileRecoveryFailure(failurePage: PageKind) {
        // The profile transition can be delivered without a follow-up accessibility event on
        // custom-rendered Douyin pages. If the watchdog does not see a verified profile or result
        // page, retain the existing per-user failure record instead of a task-level pause.
        skipMessageSendFailure(
            "The user profile did not become available after the bounded recovery window",
            failurePage = failurePage,
        )
    }

    private suspend fun dumpNodeTreeInternal(tag: String) {
        val context = currentWindowContext() ?: latestContext
        if (context == null) {
            logger.warn("node_dump_skipped", message = "No active window is available")
            return
        }

        saveNodeDump(context, tag)
    }

    private fun saveNodeDump(context: ScreenContext, tag: String) {
        val safeTag = tag.replace(Regex("[^a-zA-Z0-9_-]+"), "_").take(32).ifBlank { "window" }
        val directory = File(service.filesDir, TuningConstants.NavigationFlow.NODE_DUMP_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            logger.error("node_dump_failed", message = "Could not create private node-dump directory")
            return
        }
        val destination = File(directory, "nodes_${System.currentTimeMillis()}_$safeTag.txt")
        runCatching { destination.writeText(inspector.diagnosticDump(context)) }
            .onSuccess {
                AutomationStore.publishNodeDump(destination.absolutePath)
                logger.info("node_dump_saved", attributes = mapOf("file" to destination.name, "nodes" to context.nodes.size))
            }
            .onFailure { error ->
                logger.error("node_dump_failed", message = "Could not write private node dump", throwable = error)
            }
    }

    private suspend fun captureDiagnosticsInternal(tag: String) {
        runCatching {
            val ocrEngine = ocr ?: error("OCR engine is not available on this device")
            val artifact = screenshotCapture.capture(tag)
            AutomationStore.publishScreenshot(artifact.path)
            val freshContext = currentWindowContext()
            val diagnosticBase = freshContext ?: latestContext
            if (freshContext != null) latestContext = freshContext
            diagnosticBase?.let { context ->
                saveNodeDump(context, "${tag}_context")
            }
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(artifact.path) }
                ?: error("Could not decode private screenshot artifact")
            try {
                val result = ocrEngine.recognize(bitmap)
                AutomationStore.publishOcr(result.text)
                val base = freshContext ?: latestContext ?: currentWindowContext()
                if (base != null) {
                    val contextWithOcr = base.copy(
                        ocrBlocks = AppOwnedOverlayExclusion.filterOcrBlocks(OcrTextBlockMapper.map(result)),
                    )
                    val detection = pageDetector.detect(contextWithOcr)
                    latestContext = contextWithOcr
                    AutomationStore.publishObservation(detection)
                    if (taskActive && (detection.kind == PageKind.HUMAN_INTERVENTION || detection.kind == PageKind.LOGIN)) {
                        pause("OCR detected a verification, risk, or login screen; manual handoff required")
                    }
                }
                logger.info("diagnostics_capture_completed", attributes = mapOf("ocr_blocks" to result.blocks.size))
            } finally {
                bitmap.recycle()
            }
        }.onFailure { error ->
            logger.error("diagnostics_capture_failed", message = "Screenshot/OCR diagnostic capture failed", throwable = error)
        }
    }

    private fun currentWindowContext(): ScreenContext? = windowContextReader.read()

    /**
     * After a profile/DM BACK, classify as soon as the accepted page is present. Slow
     * transitions still spend the bounded poll budget instead of a fixed 700ms sleep.
     */
    private suspend fun awaitPageAfterProfileBack(
        accepted: Set<PageKind>,
        tag: String,
    ): ScreenContext? {
        var last: ScreenContext? = null
        repeat(TuningConstants.NavigationFlow.USER_PROFILE_BACK_POLL_ATTEMPTS) { attempt ->
            delay(
                if (attempt == 0) {
                    TuningConstants.NavigationFlow.USER_PROFILE_BACK_DELAY_MS
                } else {
                    TuningConstants.NavigationFlow.USER_PROFILE_BACK_POLL_INTERVAL_MS
                },
            )
            val context = currentWindowContext()
            last = context
            val kind = context?.let { pageDetector.detect(it).kind }
            logger.info(
                "profile_back_page_probe",
                attributes = mapOf(
                    "tag" to tag,
                    "attempt" to attempt + 1,
                    "page" to (kind?.name ?: "NO_CONTEXT"),
                ),
            )
            if (kind != null && kind in accepted) {
                return context
            }
        }
        return last
    }

    /**
     * Marks a task-level navigation failure without presenting it as a manual-handoff state.
     * This is used only after a per-user failure has already been recorded and bounded back/
     * paging recovery cannot restore the result list. Risk, captcha, and login paths continue to
     * use [pause] because they genuinely require an operator.
     */
    private fun failTaskWithoutManualHandoff(reason: String) {
        if (!taskActive && phase != AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF) return
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        profilePostconditionJob?.cancel()
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        commentRuntime.stop()
        taskActive = false
        phase = AutomationPhase.FAILED
        publishTaskTerminal(phase, error = reason)
        logger.error("task_failed_after_user_recovery", message = reason)
    }

    private fun pause(reason: String) {
        if (!taskActive && phase != AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF) return
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        profilePostconditionJob?.cancel()
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        commentRuntime.freeze()
        pausedPhase = phase.takeUnless {
            it == AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF || it == AutomationPhase.STOPPED
        }
        persistTaskCheckpoint()
        taskActive = false
        localTaskQueueSession?.let { session ->
            val pausedSession = session.pause(
                nowMillis = System.currentTimeMillis(),
                phase = pausedPhase,
            )
            localTaskQueueSession = pausedSession
            AutomationStore.saveLocalTaskQueueSession(pausedSession)
        }
        phase = AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF
        if (currentUserIdentityFingerprint != null) {
            AutomationStore.recordUserTaskFinished(
                identityFingerprint = currentUserIdentityFingerprint,
                outcome = UserTaskRecord.Outcome.PAUSED,
                reason = reason,
                page = latestContext?.let(pageDetector::detect)?.kind,
            )
            currentUserIdentityFingerprint = null
            currentUserDisplayName = null
            currentUserDisplayNameSource = null
        }
        AutomationStore.publishManualHandoff(reason)
        logger.warn("poc_paused_for_manual_handoff", message = reason)
    }

    private fun stop() {
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        profilePostconditionJob?.cancel()
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        commentRuntime.stop()
        taskActive = false
        queuedTaskSnapshots.clear()
        localTaskQueueSession?.let { session ->
            val stoppedSession = session.stop(System.currentTimeMillis())
            localTaskQueueSession = stoppedSession
            AutomationStore.saveLocalTaskQueueSession(stoppedSession)
        }
        if (currentUserIdentityFingerprint != null) {
            AutomationStore.recordUserTaskFinished(
                identityFingerprint = currentUserIdentityFingerprint,
                outcome = UserTaskRecord.Outcome.STOPPED,
                reason = "Stopped by the operator",
                page = latestContext?.let(pageDetector::detect)?.kind,
            )
            currentUserIdentityFingerprint = null
            currentUserDisplayName = null
            currentUserDisplayNameSource = null
        }
        keyword = null
        activeTaskSnapshot = null
        pendingStartMessage = ""
        pendingSafetyProbe = true
        lastProcessedUserAnchorBottom = null
        remotePageNumber = 1
        remoteResumePending = false
        remoteResumeAnchor = null
        remoteResumeTargetPageNumber = null
        remoteResumeMaxSwipes = 0
        processedUserIdentities.clear()
        latestOcrContext = null
        cachedUserResultsViewportSignature = null
        cachedUserResultsOcrBlocks = emptyList()
        userResultsSignatureBeforeSwipe = null
        pausedPhase = null
        phase = AutomationPhase.STOPPED
        AutomationStore.publishPhase(AutomationPhase.STOPPED)
        logger.info("poc_stopped")
    }

    private companion object {
        // Screen-ratio region for the player action rail. These are deliberately not absolute
        // screenshot pixels: the matcher owns no fallback point outside this bounded column.
        const val COMMENT_ICON_TEMPLATE_SEARCH_LEFT_FRACTION = 0.72f
        const val COMMENT_ICON_TEMPLATE_SEARCH_TOP_FRACTION = 0.42f
        const val COMMENT_ICON_TEMPLATE_SEARCH_BOTTOM_FRACTION = 0.82f
        const val COMMENT_ICON_TEMPLATE_MIN_CONFIDENCE = 0.86f
        const val ACTION_RAIL_ANCHOR_TEMPLATE_SEARCH_LEFT_FRACTION = 0.72f
        const val LIKE_ANCHOR_TEMPLATE_SEARCH_TOP_FRACTION = 0.40f
        const val LIKE_ANCHOR_TEMPLATE_SEARCH_BOTTOM_FRACTION = 0.65f
        const val COLLECT_ANCHOR_TEMPLATE_SEARCH_TOP_FRACTION = 0.56f
        const val COLLECT_ANCHOR_TEMPLATE_SEARCH_BOTTOM_FRACTION = 0.86f
        const val ACTION_RAIL_ANCHOR_TEMPLATE_MIN_CONFIDENCE = 0.88f

        val searchSubmitSelector = SelectorRequest(
            name = "search-submit",
            // The submit target is the visible, non-editable top-right label. Requiring a
            // Button/TextView class rejects camera/filter icons that may expose a generic search
            // content description, while requireEditable=false rejects the input itself.
            labels = DouyinLabels.search,
            contentDescriptionLabels = DouyinLabels.search,
            viewIdTokens = emptyList(),
            classNameTokens = listOf("Button", "TextView"),
            requireClassNameToken = true,
            requireClickable = null,
            // The search input itself also exposes the hint “搜索” and can otherwise outrank the
            // top-right submit label. Never treat an editable node as the submit control.
            requireEditable = false,
            preferredRegion = NormalizedRect(
                TuningConstants.NavigationFlow.SEARCH_SUBMIT_LEFT_RATIO,
                TuningConstants.NavigationFlow.SEARCH_SUBMIT_TOP_RATIO,
                TuningConstants.NavigationFlow.SEARCH_SUBMIT_RIGHT_RATIO,
                TuningConstants.NavigationFlow.SEARCH_SUBMIT_BOTTOM_RATIO,
            ),
            minimumScore = TuningConstants.NavigationFlow.SEARCH_SUBMIT_MINIMUM_SCORE,
        )
    }

    /**
     * Starting an already-running target app may not emit a new accessibility event. Poll the
     * current window briefly so the state machine can begin from the screen that is already shown.
     */
    private fun scheduleInitialObservation() {
        initialObservationJob?.cancel()
        initialObservationJob = scope.launch {
            var lastReadyKind: PageKind? = null
            var readyObservations = 0
            repeat(TuningConstants.NavigationLifecycle.INITIAL_OBSERVATION_ATTEMPTS) { observationAttempt ->
                delay(TuningConstants.NavigationLifecycle.INITIAL_OBSERVATION_INTERVAL_MS)
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_HOME) return@launch

                // A current-root lookup can be transiently shadowed by the optional progress
                // overlay on some OEM devices.  A short-lived callback snapshot is safe to use,
                // but a prior task's profile/result tree is never valid startup evidence.
                val context = currentWindowContext() ?: recentInitialTargetContext()
                if (context == null) {
                    if (
                        InitialHomeSurfacePolicy.shouldDeferMissingContextRecovery(
                            phase = phase,
                            initialClassificationPending = initialHomeClassificationPending,
                        )
                    ) {
                        logger.info(
                            "initial_context_missing_deferred",
                            message = "The launch surface has no node tree yet; continuing bounded observation without BACK",
                            attributes = mapOf("observation" to observationAttempt + 1),
                        )
                        logWaitingForHomeUnknownDiagnosis(
                            context = null,
                            detectedPage = null,
                            normalizedPage = null,
                            observationAttempt = observationAttempt + 1,
                            stableObservations = 0,
                            ocrSkippedReason = if (isCommentPrivateMessageTask()) "comment_task" else "none",
                        )
                        return@repeat
                    }
                    logger.warn(
                        "initial_context_missing",
                        message = "No Douyin node tree is available during startup; beginning bounded home recovery",
                        attributes = mapOf("observation" to observationAttempt + 1),
                    )
                    // The normal event path already owns this mutex. This polling path does not,
                    // so serialize the blind BACK recovery with every other navigation action.
                    mutex.withLock {
                        if (taskActive && phase == AutomationPhase.WAITING_FOR_HOME) {
                            recoverInitialSurface(initialContext = null, requireHome = true)
                        }
                    }
                    return@launch
                }
                val ocrSkippedReason = initialUnknownOcrSkipReason(context, observationAttempt)
                val augmentedContext = augmentInitialUnknownWithOcr(context, observationAttempt)
                val detected = pageDetector.detect(augmentedContext)
                // Check the launch overlay before trusting the page classifier. An ad can leave
                // the home navigation tree visible underneath it, so HOME alone is not proof that
                // the search icon is safe to tap.
                val startupAd = TransientOverlayDetector.findStartupAd(augmentedContext)
                if (startupAd != null) {
                    // Startup ads are wait-only. Never press a skip/download/ad control because
                    // it is not part of the Douyin automation contract and can launch an
                    // unrelated surface while the app is still starting.
                    logger.info(
                        "startup_ad_waiting",
                        message = "A possible Douyin startup advertisement is visible; waiting without a gesture",
                        attributes = mapOf("marker" to startupAd.marker, "attempt" to observationAttempt + 1),
                    )
                    lastReadyKind = null
                    readyObservations = 0
                    return@repeat
                }
                // Some Douyin home builds expose only the upper-right magnifying-glass node and
                // no pair of bottom-nav labels. Treat that semantic search candidate as a home
                // post-condition only after the ad/overlay has cleared; this avoids relying on a
                // fixed coordinate while still allowing the flow to start on sparse home trees.
                val detection = InitialHomeSurfacePolicy.normalize(
                    detected = detected,
                    hasTransientOverlay = TransientOverlayDetector.find(augmentedContext) != null,
                    hasSearchEntryCandidate = hasInitialSearchSelectorCandidate(augmentedContext),
                    reason = InitialHomeSurfacePolicy.OBSERVATION_REASON,
                )
                if (detection != detected) {
                    logger.info(
                        "initial_search_selector_candidate",
                        message = "The semantic search icon is visible after the startup settle window",
                    )
                }
                logger.info(
                    "initial_observation_snapshot",
                    attributes = mapOf(
                        "page" to detection.kind.name,
                        "confidence" to detection.confidence,
                        "nodes" to augmentedContext.nodes.size,
                        "ocr_blocks" to augmentedContext.ocrBlocks.size,
                    ),
                )
                if (detection.kind == PageKind.UNKNOWN) {
                    logWaitingForHomeUnknownDiagnosis(
                        context = augmentedContext,
                        detectedPage = detected.kind,
                        normalizedPage = detection.kind,
                        observationAttempt = observationAttempt + 1,
                        stableObservations = 0,
                        ocrSkippedReason = ocrSkippedReason,
                    )
                }
                if (detection.kind in TuningConstants.NavigationFlow.INITIAL_READY_PAGE_KINDS) {
                    if (detection.kind == lastReadyKind) {
                        readyObservations++
                    } else {
                        lastReadyKind = detection.kind
                        readyObservations = 1
                    }
                    if (readyObservations < TuningConstants.NavigationFlow.INITIAL_READY_STABLE_OBSERVATIONS) {
                        logger.info(
                            "initial_observation_waiting_stable",
                            message = "The first target page is visible; waiting for one more stable snapshot before acting",
                            attributes = mapOf(
                                "page" to detection.kind.name,
                                "stable_observations" to readyObservations,
                            ),
                        )
                        return@repeat
                    }
                } else {
                    lastReadyKind = null
                    readyObservations = 0
                }
                onScreenObserved(augmentedContext, detection)
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_HOME) return@launch
            }
        }
    }

    private suspend fun augmentInitialUnknownWithOcr(
        context: ScreenContext,
        observationAttempt: Int,
    ): ScreenContext {
        if (pageDetector.detect(context).kind != PageKind.UNKNOWN) return context
        // Comment P0 has a deterministic, node-first recovery path for an already-restored
        // profile/video: bounded Back navigation until HOME/search.  Screenshot OCR cannot
        // improve that decision and needlessly adds seconds to every regression start.
        if (isCommentPrivateMessageTask()) return context
        if (observationAttempt % TuningConstants.NavigationLifecycle.INITIAL_OCR_RETRY_EVERY_OBSERVATIONS != 0) return context
        if (initialOcrAttempts >= TuningConstants.NavigationLifecycle.INITIAL_OCR_MAX_ATTEMPTS) return context
        val engine = ocr ?: return context
        initialOcrAttempts++
        logger.info(
            "initial_ocr_probe_started",
            attributes = mapOf("attempt" to initialOcrAttempts, "observation" to observationAttempt + 1),
        )

        return runCatching {
            val artifact = screenshotCapture.capture("initial_page_probe")
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(artifact.path) }
                ?: return@runCatching context
            try {
                val result = engine.recognize(bitmap)
                if (result.isEmpty) {
                    context
                } else {
                    context.copy(
                        ocrBlocks = AppOwnedOverlayExclusion.filterOcrBlocks(OcrTextBlockMapper.map(result)),
                    )
                }
            } finally {
                bitmap.recycle()
            }
        }.onFailure { error ->
            logger.warn(
                "initial_ocr_probe_failed",
                message = "Initial OCR probe failed; continuing with accessibility nodes",
                attributes = mapOf("cause" to (error::class.java.simpleName ?: "Throwable")),
            )
        }.getOrDefault(context)
    }

    /** Returns only a recent target snapshot; startup recovery must never act on a stale run. */
    private fun recentInitialTargetContext(): ScreenContext? =
        latestContext
            ?.takeIf { it.packageName == TargetAppLauncher.DOUYIN_PACKAGE }
            ?.takeIf { System.currentTimeMillis() - it.capturedAtMillis <= TuningConstants.NavigationFlow.INITIAL_CONTEXT_MAX_AGE_MS }

    private fun initialUnknownOcrSkipReason(context: ScreenContext, observationAttempt: Int): String {
        if (pageDetector.detect(context).kind != PageKind.UNKNOWN) return "not_needed"
        if (isCommentPrivateMessageTask()) return "comment_task"
        if (observationAttempt % TuningConstants.NavigationLifecycle.INITIAL_OCR_RETRY_EVERY_OBSERVATIONS != 0) {
            return "not_due"
        }
        if (initialOcrAttempts >= TuningConstants.NavigationLifecycle.INITIAL_OCR_MAX_ATTEMPTS) return "max_attempts"
        if (ocr == null) return "engine_missing"
        return "none"
    }

    private fun logWaitingForHomeUnknownDiagnosis(
        context: ScreenContext?,
        detectedPage: PageKind?,
        normalizedPage: PageKind?,
        observationAttempt: Int,
        stableObservations: Int,
        ocrSkippedReason: String,
    ) {
        val report = WaitingForHomeUnknownDiagnostics.analyze(
            context = context,
            selector = selector,
            detectedPage = detectedPage,
            normalizedPage = normalizedPage,
            stableObservations = stableObservations,
            ocrSkippedReason = ocrSkippedReason,
        )
        logger.info(
            "waiting_for_home_unknown",
            message = "Startup UNKNOWN diagnosis; no click or BACK is taken from this record",
            attributes = report.toLogAttributes() + mapOf("observation" to observationAttempt),
        )
        if (!homeUnknownGeometryDumpSaved) {
            saveSanitizedHomeUnknownDump(report)
        }
    }

    private fun saveSanitizedHomeUnknownDump(report: WaitingForHomeUnknownDiagnostics.Report) {
        val directory = File(service.filesDir, TuningConstants.NavigationFlow.NODE_DUMP_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            logger.error("node_dump_failed", message = "Could not create private node-dump directory")
            return
        }
        val destination = File(directory, "nodes_${System.currentTimeMillis()}_waiting_for_home_unknown.txt")
        runCatching { destination.writeText(report.geometrySummary()) }
            .onSuccess {
                homeUnknownGeometryDumpSaved = true
                logger.info(
                    "waiting_for_home_unknown_dump_saved",
                    attributes = mapOf("file" to destination.name, "cause" to report.cause.name),
                )
            }
            .onFailure { error ->
                logger.error(
                    "waiting_for_home_unknown_dump_failed",
                    message = "Could not write sanitized home UNKNOWN summary",
                    throwable = error,
                )
            }
    }

    private fun hasInitialSearchSelectorCandidate(context: ScreenContext): Boolean =
        selector.select(context, DouyinSelectors.searchEntry).node != null ||
            selector.select(context, DouyinSelectors.searchEntryStructural).node != null

    /**
     * Startup recovery must classify a sparse HOME tree exactly like the observation loop does:
     * an UNKNOWN page that still exposes the semantic search entry (and no overlay) is HOME.
     * Without this normalization the recovery loop keeps pressing BACK past a home surface that
     * [PageDetector] reported as UNKNOWN, over-navigating into search/profile/results and failing
     * the otherwise-fine launch.
     */
    private fun normalizeInitialHomeDetection(context: ScreenContext): PageDetection {
        return InitialHomeSurfacePolicy.normalize(
            detected = pageDetector.detect(context),
            hasTransientOverlay = TransientOverlayDetector.find(context) != null,
            hasSearchEntryCandidate = hasInitialSearchSelectorCandidate(context),
            reason = InitialHomeSurfacePolicy.RECOVERY_REASON,
        )
    }

}
