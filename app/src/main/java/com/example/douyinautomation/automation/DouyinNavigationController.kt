package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.BitmapFactory
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
    private var initialOcrAttempts = 0
    private var latestContext: ScreenContext? = null
    private var userTabRevealAttempts = 0
    private var restrictedUserSkips = 0
    private var timeoutRecoveryAttempts = 0
    /** Bottom edge of the row currently being processed, used to pick the next visible row. */
    private var lastProcessedUserAnchorBottom: Float? = null
    /** Row identities already handled in this run; this survives small overlapping page swipes. */
    private val processedUserIdentities = LinkedHashSet<String>()
    /** Opaque hashes restored from a durable checkpoint after the process is recreated. */
    private val processedIdentityHashes = LinkedHashSet<Int>()
    /** Rich identity aliases are retained so OCR punctuation/spacing drift cannot reopen a row. */
    private val processedUserIdentityRecords = ArrayList<UserResultIdentity>()
    /** Hash of the currently selected row's identity, used to finish its task audit record. */
    private var currentUserIdentityHash: Int? = null
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

    fun installOcrEngine(engine: MlKitOcrEngine?) {
        ocr = engine
    }

    /** True only while an explicitly started POC run is waiting for or performing a step. */
    fun shouldUseOcrFallback(): Boolean = taskActive && ocr != null

    /** True while the M2 blank-message result is still being awaited, regardless of OCR state. */
    fun shouldProbeEmptyMessageResult(): Boolean =
        taskActive && phase == AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT

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
    suspend fun onTransientAccessibilityText(values: List<String>) = mutex.withLock {
        if (!taskActive || phase != AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT || values.isEmpty()) return@withLock
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

    suspend fun handle(command: AutomationCommand) = mutex.withLock {
        when (command) {
            is AutomationCommand.Start -> {
                queuedTaskSnapshots.clear()
                start(
                    searchKeyword = command.keyword,
                    startMessage = command.message,
                    safetyProbe = command.safetyProbe,
                    taskSnapshot = command.taskSnapshot,
                    remoteResume = command.remoteResume,
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

        if (!taskActive || detection.kind == PageKind.OUTSIDE_TARGET) return
        // Do not replace the last Douyin context when the diagnostics activity briefly becomes
        // the active window. This keeps operator-requested node dumps useful while the target app
        // remains underneath the translucent diagnostics surface.
        latestContext = context
        if (detection.kind == PageKind.HUMAN_INTERVENTION) {
            pause("Verification or risk screen detected; manual handoff required")
            return
        }
        if (detection.kind == PageKind.LOGIN) {
            pause("Douyin login is required; complete it manually before retrying")
            return
        }
        if (!confirmOcrBackedPage(context, detection)) return

        when (phase) {
            AutomationPhase.WAITING_FOR_HOME -> when (detection.kind) {
                PageKind.HOME -> openSearch(context)
                PageKind.SEARCH_ENTRY -> enterKeyword(context)
                // Starting Douyin does not always create a fresh activity. If the previous
                // operator run left the app on a result page, the launch intent can restore that
                // page (including its old query) instead of showing the home feed. The result
                // page still exposes the real editable query field, so reuse that verified field
                // rather than submitting the stale query or waiting for a search-entry event that
                // will never arrive.
                PageKind.SEARCH_RESULTS -> reuseSearchResultsQueryField(context, "initial_observation")
                PageKind.USER_RESULTS,
                PageKind.USER_PROFILE,
                PageKind.DIRECT_MESSAGE
                -> recoverInitialSurface(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_SEARCH_ENTRY -> when (detection.kind) {
                PageKind.SEARCH_ENTRY -> enterKeyword(context)
                // Some Douyin builds transition straight from the restored search surface to
                // results while keeping the editable query field at the top. Treat this as an
                // actionable search surface only when that node is present; never accept a
                // visually similar result label as proof that the requested query was entered.
                PageKind.SEARCH_RESULTS -> reuseSearchResultsQueryField(context, "search_entry_wait")
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_SEARCH_RESULTS -> when (detection.kind) {
                PageKind.SEARCH_RESULTS -> selectUserTab(context)
                PageKind.USER_RESULTS -> selectVisibleUser(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_USER_RESULTS -> when (detection.kind) {
                PageKind.USER_RESULTS -> selectVisibleUser(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_PROFILE -> when (detection.kind) {
                PageKind.USER_PROFILE -> openPrivateMessage(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_DIRECT_MESSAGE -> when (detection.kind) {
                PageKind.DIRECT_MESSAGE -> completeAtMessagePage()
                PageKind.MESSAGE_EMPTY_REJECTED -> completeEmptyMessageProbe()
                PageKind.PRIVATE_MESSAGE_RESTRICTED -> skipRestrictedUser("Douyin requires following before private messaging")
                PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                    "Douyin rejected the message because of the recipient's messaging settings",
                )
                PageKind.USER_PROFILE -> scheduleMessageEntryPostconditionCheck()
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_MESSAGE_RESULT -> when (detection.kind) {
                PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                    "Douyin rejected the message because of the recipient's messaging settings",
                )
                PageKind.HUMAN_INTERVENTION -> pause("A verification or risk screen appeared after sending; manual handoff required")
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT -> when (detection.kind) {
                PageKind.MESSAGE_EMPTY_REJECTED -> completeEmptyMessageProbe()
                PageKind.HUMAN_INTERVENTION -> pause("A verification or risk screen appeared during the blank-message probe; manual handoff required")
                PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                    "The blank-message probe was rejected by the recipient's messaging settings",
                )
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
    ) {
        // The comment task contract is intentionally introduced before its runtime controller.
        // Never let a future saved/remote comment task fall through to the profile runner and
        // click an unrelated search result. It will become an explicit P4 dispatch branch once
        // the comment surface and overlay have passed their own regression tests.
        if (taskSnapshot?.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE) {
            val reason = "评论私信流程尚未启用，任务未执行"
            logger.warn("comment_task_not_enabled", message = reason)
            AutomationStore.publishFailure(reason)
            return
        }
        val sanitizedKeyword = taskSnapshot?.composedQueries?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: searchKeyword.trim()
        if (sanitizedKeyword.isEmpty()) {
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
            ?.let { key -> remoteAnchorIdentity(key, remoteResume.progress.lastUserName) }
        remoteResumeTargetPageNumber = remoteResume?.progress?.lastPageNumber?.coerceAtLeast(1)
        remoteResumeMaxSwipes = (remoteResumeTargetPageNumber ?: 1)
            .plus(REMOTE_RESUME_EXTRA_SWIPES)
            .coerceAtMost(MAX_REMOTE_RESUME_SWIPES)
        queryTransitionHandled = false
        pendingStartMessage = startMessage.trim()
        pendingSafetyProbe = safetyProbe
        pausedPhase = null
        initialOcrAttempts = 0
        userTabRevealAttempts = 0
        restrictedUserSkips = 0
        timeoutRecoveryAttempts = 0
        lastProcessedUserAnchorBottom = null
        processedUserIdentities.clear()
        processedUserIdentityRecords.clear()
        processedIdentityHashes.clear()
        // Rehydrate the complete terminal identity ledger when the backend provides it. The last
        // user anchor alone is not enough when the feed reorders or clips that row; known earlier
        // identities let the controller choose the first genuinely new visible row instead of
        // swiping through several pages waiting for an OCR key that may no longer be present.
        remoteResume?.progress?.let { progress ->
            val knownKeys = (progress.processedUserKeys + listOfNotNull(progress.lastUserKey)).distinct()
            knownKeys.forEach { key ->
                val savedName = key.takeIf { it == progress.lastUserKey }?.let { progress.lastUserName }
                val identity = remoteAnchorIdentity(key, savedName)
                if (processedUserIdentities.add(identity.key)) {
                    processedUserIdentityRecords += identity
                }
                processedIdentityHashes += identity.key.hashCode()
            }
        }
        currentUserIdentityHash = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null
        cachedUserResultsViewportSignature = null
        cachedUserResultsOcrBlocks = emptyList()
        userResultsSignatureBeforeSwipe = null
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        profilePostconditionJob?.cancel()
        initialObservationJob?.cancel()
        // Create/switch the persisted task history entry before publishing the first phase.
        // In a queued batch, publishing LAUNCHING_TARGET while currentTaskId still points to
        // the previous task would regress its terminal COMPLETED status back to RUNNING.
        AutomationStore.beginTask(sanitizedKeyword, taskSnapshot)
        phase = AutomationPhase.LAUNCHING_TARGET
        AutomationStore.publishPhase(phase)
        logger.info("poc_started", attributes = mapOf("target" to TargetAppLauncher.DOUYIN_PACKAGE))

        when (val result = TargetAppLauncher.launch(service)) {
            LaunchResult.Started -> {
                // Douyin may show a full-screen promotion immediately after cold start. Do not
                // inspect or click through that surface: give it a bounded five-second settle
                // window before the first page observation and search action.
                logger.info(
                    "initial_screen_settle_started",
                    attributes = mapOf("wait_ms" to INITIAL_SCREEN_SETTLE_DELAY_MS),
                )
                delay(INITIAL_SCREEN_SETTLE_DELAY_MS)
                logger.info("initial_screen_settle_completed")
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
        if (tasks.any { it.taskType == AutomationTaskType.COMMENT_PRIVATE_MESSAGE }) {
            val reason = "批量任务中包含尚未启用的评论私信任务，已停止本批次以避免误操作"
            logger.warn("comment_task_batch_not_enabled", message = reason)
            AutomationStore.publishFailure(reason)
            return
        }
        val validTasks = tasks.filter { snapshot ->
            snapshot.composedQueries.any { it.isNotBlank() }
        }
        if (validTasks.isEmpty()) {
            AutomationStore.publishFailure("没有可执行的待办任务")
            return
        }
        queuedTaskSnapshots.replace(validTasks.drop(1))
        logger.info(
            "task_batch_started",
            attributes = mapOf("task_count" to validTasks.size),
        )
        val first = validTasks.first()
        start(
            searchKeyword = first.composedQueries.first(),
            startMessage = first.messageTemplate.orEmpty(),
            safetyProbe = first.executionMode == TaskExecutionMode.SAFE_BLANK_PROBE,
            taskSnapshot = first,
            remoteResume = null,
        )
    }

    /**
     * Continue a local batch after a task has finished.  A short settle delay gives Douyin time to
     * return to its stable surface before the next launch, while the same controller mutex keeps
     * two task starts from overlapping.
     */
    private fun scheduleNextQueuedTask(): Boolean {
        val next = queuedTaskSnapshots.poll() ?: return false
        scope.launch {
            delay(NEXT_TASK_SETTLE_DELAY_MS)
            mutex.withLock {
                if (taskActive) {
                    queuedTaskSnapshots.replace(listOf(next) + queuedTaskSnapshots.asList())
                    return@withLock
                }
                start(
                    searchKeyword = next.composedQueries.firstOrNull().orEmpty(),
                    startMessage = next.messageTemplate.orEmpty(),
                    safetyProbe = next.executionMode == TaskExecutionMode.SAFE_BLANK_PROBE,
                    taskSnapshot = next,
                    remoteResume = null,
                )
            }
        }
        return true
    }

    private fun publishTaskTerminal(
        phase: AutomationPhase,
        error: String? = null,
    ) {
        val hasNext = !queuedTaskSnapshots.isEmpty
        if (phase == AutomationPhase.FAILED) {
            AutomationStore.publishFailure(error ?: "任务执行失败", openRecords = !hasNext)
        } else {
            AutomationStore.publishPhase(phase, error = error, openRecords = !hasNext)
        }
        if (hasNext) {
            scheduleNextQueuedTask()
        }
    }

    private suspend fun resumeSavedTask() {
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
        val query = checkpoint.snapshot.composedQueries.getOrNull(checkpoint.queryIndex)
        if (query.isNullOrBlank()) {
            AutomationStore.publishFailure("任务检查点中的搜索词无效")
            AutomationStore.clearTaskCheckpoint()
            return
        }
        logger.info(
            "saved_task_resume_requested",
            attributes = mapOf("query_index" to checkpoint.queryIndex, "query_count" to checkpoint.snapshot.composedQueries.size),
        )
        taskActive = true
        keyword = query
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
        userTabRevealAttempts = 0
        restrictedUserSkips = 0
        timeoutRecoveryAttempts = 0
        lastProcessedUserAnchorBottom = null
        processedUserIdentities.clear()
        processedUserIdentityRecords.clear()
        processedIdentityHashes.clear()
        processedIdentityHashes.addAll(checkpoint.processedIdentityHashes)
        currentUserIdentityHash = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null
        cachedUserResultsViewportSignature = null
        cachedUserResultsOcrBlocks = emptyList()
        userResultsSignatureBeforeSwipe = null
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        profilePostconditionJob?.cancel()
        initialObservationJob?.cancel()
        phase = AutomationPhase.WAITING_FOR_HOME
        AutomationStore.publishPhase(phase)
        AutomationStore.resumeTask(checkpoint)
        val existingContext = currentWindowContext()
        val existingDetection = existingContext?.let(pageDetector::detect)
        if (existingContext != null && existingDetection != null && existingDetection.kind in setOf(
                PageKind.HOME,
                PageKind.SEARCH_ENTRY,
                PageKind.SEARCH_RESULTS,
                PageKind.USER_RESULTS,
            )
        ) {
            phase = when (existingDetection.kind) {
                PageKind.USER_RESULTS -> AutomationPhase.WAITING_FOR_USER_RESULTS
                PageKind.SEARCH_ENTRY -> AutomationPhase.WAITING_FOR_SEARCH_ENTRY
                PageKind.SEARCH_RESULTS -> AutomationPhase.WAITING_FOR_SEARCH_RESULTS
                else -> AutomationPhase.WAITING_FOR_HOME
            }
            AutomationStore.publishPhase(phase)
            onScreenObserved(existingContext, existingDetection)
            return
        }
        when (val result = TargetAppLauncher.launch(service)) {
            LaunchResult.Started -> {
                delay(INITIAL_SCREEN_SETTLE_DELAY_MS)
                scheduleInitialObservation()
            }
            is LaunchResult.Failed -> pause("无法恢复抖音任务：${result.reason}")
        }
    }

    private suspend fun resume() {
        if (phase != AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF || keyword.isNullOrBlank()) {
            AutomationStore.publishFailure("There is no paused run ready to resume.")
            logger.warn("resume_rejected", message = "No paused run is available")
            return
        }

        val context = latestContext ?: currentWindowContext()
        if (context == null) {
            AutomationStore.publishManualHandoff("No active Douyin window is available; review the screen before resuming")
            return
        }

        val detection = pageDetector.detect(context)
        AutomationStore.publishObservation(detection)
        val decision = AutomationResumePolicy.decide(detection)
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
        // Resuming does not necessarily produce a new accessibility window event.  The old
        // implementation only changed the phase, which left a verified search/profile page
        // idle until Douyin happened to emit another event.  Continue from the observation that
        // was just validated so Resume has the same post-condition behavior as a fresh event.
        if (!confirmOcrBackedPage(context, detection)) return
        when (detection.kind) {
            PageKind.HOME -> openSearch(context)
            PageKind.SEARCH_ENTRY -> enterKeyword(context)
            PageKind.SEARCH_RESULTS -> selectUserTab(context)
            PageKind.USER_RESULTS -> selectVisibleUser(context)
            PageKind.USER_PROFILE -> openPrivateMessage(context)
            PageKind.DIRECT_MESSAGE -> completeAtMessagePage()
            else -> Unit
        }
    }

    private suspend fun openSearch(context: ScreenContext) {
        phase = AutomationPhase.OPENING_SEARCH
        AutomationStore.publishPhase(phase)
        val semanticOutcome = clickSelector(context, DouyinSelectors.searchEntry)
        val structuralOutcome = if (!semanticOutcome.succeeded) {
            clickSelector(context, DouyinSelectors.searchEntryStructural)
        } else {
            ActionOutcome.failure("semantic search selector already succeeded")
        }
        val outcome = if (semanticOutcome.succeeded) {
            semanticOutcome
        } else if (structuralOutcome.succeeded) {
            logger.info(
                "search_entry_structural_fallback",
                message = "The unlabeled clickable search icon was selected by class and region",
                attributes = mapOf("route" to structuralOutcome.route),
            )
            structuralOutcome
        } else {
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
        for (attempt in 1..SEARCH_ENTRY_POSTCONDITION_ATTEMPTS) {
            delay(if (attempt == 1) 250L else SEARCH_ENTRY_POSTCONDITION_INTERVAL_MS)
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
        enterKeyword(context)
    }

    /**
     * Bring a task back to a known Douyin surface when a launcher restores a profile, user list,
     * chat, or a transient feed/video page. This is a bounded back-navigation recovery; it never
     * taps an unrelated control or relies on a coordinate to guess the current page.
     */
    private suspend fun recoverInitialSurface(initialContext: ScreenContext) {
        var context: ScreenContext? = initialContext
        repeat(MAX_BACK_ACTIONS_TO_SEARCH_ENTRY + 2) { attempt ->
            val current = context ?: currentWindowContext()
            if (current == null) {
                delay(USER_PROFILE_BACK_DELAY_MS)
                context = currentWindowContext()
                return@repeat
            }
            val detection = pageDetector.detect(current)
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
                    enterKeyword(current)
                    return
                }
                PageKind.SEARCH_RESULTS -> {
                    if (selector.select(current, DouyinSelectors.searchInput).node != null) {
                        reuseSearchResultsQueryField(current, "initial_surface_recovery")
                        return
                    }
                }
                else -> Unit
            }
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                pause("无法从抖音当前页面返回到可搜索页面")
                return
            }
            delay(USER_PROFILE_BACK_DELAY_MS)
            context = currentWindowContext()
        }
        pause("抖音未能在限定时间内回到可搜索页面")
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
        delay(500L)
        val postSubmitContext = currentWindowContext()
        if (postSubmitContext != null && pageDetector.detect(postSubmitContext).kind == PageKind.SEARCH_ENTRY) {
            logger.warn("search_submit_postcondition_retry", message = "Search entry remained visible after the first submit gesture")
            val retry = retrySearchSubmit(postSubmitContext)
            if (!retry.succeeded) {
                pause("Search entry remained visible and the retry gesture was rejected")
                return
            }
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
        } else if (!isVisibleUserTabCandidate(candidate, selection, liveContext)) {
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
        for (attempt in 1..USER_RESULTS_POSTCONDITION_ATTEMPTS) {
            delay(if (attempt == 1) 450L else USER_RESULTS_POSTCONDITION_INTERVAL_MS)
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

    private fun isVisibleUserTabCandidate(
        candidate: NodeSnapshot,
        selection: SelectionResult,
        context: ScreenContext,
    ): Boolean {
        val directLabel = listOfNotNull(candidate.text, candidate.contentDescription, candidate.hintText)
            .any { raw -> DouyinLabels.users.any { label -> TextNormalizer.normalize(raw) == TextNormalizer.normalize(label) } }
        val ancestorLabel = selection.reasons.any { reason ->
            DouyinLabels.users.any { label -> reason == "label=$label" }
        }
        val bounds = candidate.bounds
        val tabStrip = context.nodes.firstOrNull { node ->
            node.className?.contains("HorizontalScrollView", ignoreCase = true) == true &&
                node.bounds.top <= bounds.top &&
                node.bounds.bottom >= bounds.bottom
        }
        val viewportLeft = tabStrip?.bounds?.left ?: 0
        val viewportRight = tabStrip?.bounds?.right
            ?: (context.screenSize.width * USER_TAB_VIEWPORT_FALLBACK_RIGHT_RATIO).toInt()
        val fullyOnScreen = bounds.left >= 0 &&
            bounds.top >= 0 &&
            bounds.right <= context.screenSize.width &&
            bounds.bottom <= context.screenSize.height &&
            bounds.width > 0 &&
            bounds.height > 0
        val insideVisibleTabStrip = bounds.left >= viewportLeft && bounds.right <= viewportRight
        // A transient result/video surface can expose a descendant labelled “用户” inside a
        // large clickable content container. It may satisfy the semantic label match but is not a
        // category tab; accepting it can open a video detail page. Category tabs are always in
        // the upper strip, have a compact height, and never occupy a substantial portion of the
        // screen. Keep the geometry check as a guard in addition to semantic matching.
        val topBandBottom = (context.screenSize.height * 0.36f).toInt()
        val compactTabHeight = bounds.height <= (context.screenSize.height * 0.14f).toInt()
        val inTopTabBand = bounds.top <= topBandBottom && bounds.bottom <= topBandBottom
        return (directLabel || ancestorLabel) &&
            fullyOnScreen &&
            insideVisibleTabStrip &&
            compactTabHeight &&
            inTopTabBand
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
        if (maxUsers != null && processedUserIdentityRecords.size >= maxUsers) {
            completeTaskAtUserLimit(maxUsers)
            return
        }
        // A remote task with existing progress must first locate the backend's last-user anchor.
        // Starting at the first visible row would silently reprocess users when the process was
        // recreated on a fresh Douyin result page, so no row is selected until the anchor is found.
        if (remoteResumePending && minimumAnchorTop == null) {
            resumeRemoteTaskFromAnchor(context)
            return
        }
        if (minimumAnchorTop == null && UserResultMarkers.accountHelpOnly(context)) {
            logger.info(
                "user_results_account_help_marker",
                message = "Douyin rendered the account-help row without another selectable result; treating it as a bounded end-of-results signal",
            )
            if (advanceToNextQueryIfAvailable("account_help_marker")) return
            completeTaskAtQueryEnd()
            return
        }
        phase = AutomationPhase.SELECTING_USER_RESULT
        AutomationStore.publishPhase(phase)
        // Prefer the structural row route whenever it is available. It deliberately targets the
        // name/profile content area, not the avatar: a live badge on an avatar can route to a live
        // room instead of the user's profile.
        var rowContext = context
        var rowMatch: StructuralUserRowMatch? = null
        for (attempt in 1..USER_ROW_POSTCONDITION_ATTEMPTS) {
            if (attempt > 1) delay(USER_ROW_POSTCONDITION_INTERVAL_MS)
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
        if (rowMatch != null) {
            rowContext = enrichUserResultIdentityContext(rowContext, rowMatch!!)
            var identity = UserResultIdentityExtractor.extract(rowContext, rowMatch!!)
            // A custom-rendered row may be captured between two RecyclerView frames. Never tap a
            // row without a stable identity: retry the live snapshot/OCR briefly, then hand off
            // instead of risking a duplicate blank-message probe for an unknown account.
            repeat(IDENTITY_RETRY_ATTEMPTS - 1) { retry ->
                if (identity != null) return@repeat
                delay(IDENTITY_RETRY_INTERVAL_MS)
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
                val identityHash = identity.key.hashCode()
                logger.info(
                    "user_result_identity",
                    attributes = mapOf(
                        "source" to identity.source.name,
                        "key_hash" to identityHash,
                        "duplicate" to duplicate,
                        "duplicate_reason" to (duplicateReason ?: "none"),
                        "visible_token_count" to identity.visibleTokens.size,
                    ),
                )
                if (duplicate) {
                    AutomationStore.recordUserTaskEvent(
                        identityHash = identityHash,
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
                            "identity_hash" to identityHash,
                            "matched_count" to blockedEvaluation.matches.size,
                        ),
                    )
                    AutomationStore.recordUserTaskEvent(
                        identityHash = identityHash,
                        outcome = UserTaskRecord.Outcome.FILTERED_BY_KEYWORD,
                        reason = "Blocked keywords matched: ${blockedEvaluation.matchedKeywords.joinToString()}",
                        remoteUserKey = identity.key,
                        displayName = identity.displayName,
                    )
                    // A filtered row is handled too. Retaining its identity makes the next
                    // viewport anchor continue after it instead of exposing it again.
                    processedUserIdentities.add(identity.key)
                    processedUserIdentityRecords += identity
                    processedIdentityHashes.add(identityHash)
                    persistTaskCheckpoint()
                    lastProcessedUserAnchorBottom = rowMatch!!.anchor.bounds.bottom.toFloat()
                    skipFilteredUser(rowContext, rowMatch!!)
                    return
                }
                processedUserIdentities.add(identity.key)
                processedUserIdentityRecords += identity
                processedIdentityHashes.add(identityHash)
                persistTaskCheckpoint()
                currentUserIdentityHash = identityHash
                currentUserDisplayName = identity.displayName
                currentUserDisplayNameSource = identity.source
                AutomationStore.recordUserTaskStarted(
                    identityHash = identityHash,
                    remoteUserKey = identity.key,
                    displayName = identity.displayName,
                    messageContent = currentTaskMessageContent(),
                )
            } else {
                currentUserIdentityHash = null
                currentUserDisplayName = null
                currentUserDisplayNameSource = null
                logger.warn(
                    "user_result_identity_unavailable",
                    message = "The row has no stable name or account identifier; bounded paging fallback will be used",
                    attributes = mapOf("anchor_top" to rowMatch!!.anchor.bounds.top),
                )
                AutomationStore.recordUserTaskEvent(
                    identityHash = null,
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
                    identityHash = currentUserIdentityHash,
                    outcome = UserTaskRecord.Outcome.FOLLOW_BACK_SKIPPED,
                    reason = "The result exposed a follow-back action",
                )
                currentUserIdentityHash = null
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
            logger.warn(
                "user_result_row_match_failed",
                message = "No wide user-result row matched the follow-button anchor; semantic name selection will be attempted",
                attributes = mapOf("anchor_count" to StructuralUserRowDetector.anchorCount(rowContext), "nodes" to rowContext.nodes.size),
            )
            clickSelector(rowContext, DouyinSelectors.userResult)
        }
        if (!outcome.succeeded) {
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
        "checkpoint_hash".takeIf { identity.key.hashCode() in processedIdentityHashes } ?: processedUserIdentityRecords.firstNotNullOfOrNull { previous ->
            identityMatchReason(previous, identity)
        }

    /**
     * Rebuild a comparable identity from the backend checkpoint. Older mobile records may have
     * been written with a fallback key such as "|佛山市南海正明堂家具店" when the OCR row name
     * was clipped. Treat each key segment as searchable metadata instead of treating the leading
     * pipe value as the literal display name. This lets the current row match through its company
     * line even when its visible display name is "佛山正明堂中高档二手...".
     */
    private fun remoteAnchorIdentity(key: String, savedName: String?): UserResultIdentity {
        val segments = key.split('|')
            .map { it.trim() }
            .filter(String::isNotBlank)
            .map(::normalizeIdentityText)
            .filter(String::isNotBlank)
            .distinct()
        val normalizedSavedName = savedName
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.trimStart().startsWith('|') }
            ?.let(::normalizeIdentityText)
        val handle = segments.firstOrNull { it.startsWith("handle:") }
            ?.removePrefix("handle:")
            ?.takeIf(String::isNotBlank)
        val metadata = segments
            .filterNot { normalizedSavedName != null && it == normalizedSavedName }
            .filterNot { it.startsWith("handle:") }
            .toSet()
        val visibleTokens = (segments + listOfNotNull(normalizedSavedName)).toSet()
        return UserResultIdentity(
            key = key,
            source = UserResultIdentity.Source.ACCESSIBILITY,
            displayName = normalizedSavedName,
            accountHandle = handle,
            stableMetadata = metadata,
            visibleTokens = visibleTokens,
        )
    }

    private fun identityMatchReason(
        previous: UserResultIdentity,
        identity: UserResultIdentity,
    ): String? = when {
        previous.key == identity.key -> "exact_key"
        !previous.accountHandle.isNullOrBlank() &&
            previous.accountHandle == identity.accountHandle -> "account_handle"
        // The remote progress endpoint supplies stable user keys but older records do not always
        // carry a separate display name. In that case compare the non-volatile company/metadata
        // segments directly; requiring one shared stable segment still avoids name-only matches.
        previous.displayName.isNullOrBlank() &&
            metadataOverlap(previous.stableMetadata, identity.stableMetadata) -> "remote_metadata"
        sameDisplayName(previous.displayName, identity.displayName) &&
            metadataOverlap(previous.stableMetadata, identity.stableMetadata) -> "name_metadata"
        sameDisplayName(previous.displayName, identity.displayName) &&
            visibleMetadataOverlap(previous, identity) -> "name_visible_tokens"
        else -> null
    }

    /**
     * Anchor matching is intentionally a little more tolerant than ordinary duplicate skipping.
     * A partial swipe can lose the handle/metadata OCR block while leaving the display name
     * readable. This relaxed rule is used only to locate the continuation anchor and is rejected
     * when multiple same-name candidates are visible.
     */
    private fun viewportAnchorMatchReason(
        previous: UserResultIdentity,
        current: UserResultIdentity,
    ): String? = identityMatchReason(previous, current) ?:
        if (sameDisplayName(previous.displayName, current.displayName)) {
            "anchor_display_name"
        } else {
            null
        }

    private fun sameDisplayName(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false
        val left = normalizeIdentityText(first)
        val right = normalizeIdentityText(second)
        if (left == right) return true
        // OCR often clips the end of a long name at the viewport edge and replaces it with an
        // ellipsis. Treat a sufficiently long common prefix as the same display name. Ordinary
        // duplicate skipping still requires metadata/tokens; only the isolated viewport-anchor
        // rule may use this tolerant name comparison when it has a single visible candidate.
        val shorter = minOf(left.length, right.length)
        if (shorter >= 4 && (left.startsWith(right) || right.startsWith(left))) return true
        return normalizedNameDistance(left, right) <= 1
    }

    private fun normalizedNameDistance(first: String, second: String): Int {
        val left = normalizeIdentityText(first)
        val right = normalizeIdentityText(second)
        if (kotlin.math.abs(left.length - right.length) > 3) return 4
        if (left == right) return 0
        // A real edit distance handles OCR dropping one character in the middle of a company
        // name (e.g. "家具有公司" vs "家具有限公司"); positional mismatch counting would report
        // several errors and miss the duplicate.
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun metadataOverlap(first: Set<String>, second: Set<String>): Boolean =
        first.any { left ->
            second.any { right ->
                left == right || normalizedNameDistance(left, right) <= 1
            }
        }

    /**
     * Stable metadata can disappear when a row is clipped at the top/bottom of a new viewport.
     * Compare the remaining row-local OCR tokens as a second signal, excluding generic labels
     * and the display name itself so two distinct accounts with the same name are not collapsed.
     */
    private fun visibleMetadataOverlap(
        previous: UserResultIdentity,
        current: UserResultIdentity,
    ): Boolean {
        val previousTokens = identityMetadataTokens(previous)
        val currentTokens = identityMetadataTokens(current)
        if (previousTokens.isEmpty() || currentTokens.isEmpty()) return false
        return previousTokens.any { left ->
            currentTokens.any { right ->
                left == right ||
                    left.startsWith(right) ||
                    right.startsWith(left) ||
                    normalizedNameDistance(left, right) <= 1
            }
        }
    }

    private fun identityMetadataTokens(identity: UserResultIdentity): Set<String> {
        val name = identity.displayName?.let(::normalizeIdentityText)
        return identity.visibleTokens
            .map(::normalizeIdentityText)
            .filter { token ->
                token.length >= 2 &&
                    token != name &&
                    token !in IDENTITY_GENERIC_TOKENS &&
                    !token.all(Char::isDigit) &&
                    !token.contains("粉丝") &&
                    !token.contains("获赞")
            }
            .toSet()
    }

    private fun normalizeIdentityText(value: String): String =
        IdentityTextCanonicalizer.normalize(value)

    private fun persistTaskCheckpoint() {
        val snapshot = activeTaskSnapshot ?: return
        AutomationStore.saveTaskCheckpoint(
            TaskCheckpoint(
                taskId = AutomationStore.getCurrentTaskId() ?: snapshot.taskId,
                snapshot = snapshot,
                queryIndex = taskQueryIndex.coerceIn(0, snapshot.composedQueries.lastIndex),
                processedIdentityHashes = processedIdentityHashes.toList(),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    /** Poll the profile transition independently of accessibility callbacks, which OEM builds may drop. */
    private fun scheduleProfilePostconditionCheck() {
        profilePostconditionJob?.cancel()
        profilePostconditionJob = scope.launch {
            repeat(PROFILE_POSTCONDITION_ATTEMPTS) { attempt ->
                delay(if (attempt == 0) PROFILE_POSTCONDITION_INITIAL_DELAY_MS else PROFILE_POSTCONDITION_INTERVAL_MS)
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
                    when (detection.kind) {
                        PageKind.USER_PROFILE -> openPrivateMessage(context)
                        PageKind.HUMAN_INTERVENTION -> pause("A verification or risk screen appeared before opening private messages; manual handoff required")
                        PageKind.LOGIN -> pause("Douyin login is required before opening private messages")
                        else -> Unit
                    }
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
                delay(USER_ROW_POSTCONDITION_INTERVAL_MS)
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
                    identity?.let { viewportAnchorMatchReason(anchor, it)?.let { reason -> index to reason } }
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
                    delay(USER_ROW_POSTCONDITION_INTERVAL_MS)
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
                delay(USER_ROW_POSTCONDITION_INTERVAL_MS)
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
        var remoteCheckpointSubmitted = false
        repeat(VIEWPORT_ANCHOR_PROBE_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(VIEWPORT_ANCHOR_PROBE_INTERVAL_MS)
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
            val identitiesStable = identities.all { it != null } &&
                stableIdentityObservations >= VIEWPORT_IDENTITY_STABLE_OBSERVATIONS
            if (identitiesStable && !remoteCheckpointSubmitted) {
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
                    viewportAnchorMatchReason(previousIdentity, candidate)?.let { reason ->
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
            logger.info(
                "user_result_anchor_probe",
                attributes = mapOf(
                    "tag" to tag,
                    "attempt" to attempt + 1,
                    "row_count" to rows.size,
                    "identity_count" to identities.count { it != null },
                    "identities_stable" to identitiesStable,
                    "stable_identity_observations" to stableIdentityObservations,
                    "anchor_found" to (anchorIndex >= 0),
                    "anchor_index" to anchorIndex,
                    "anchor_candidate_count" to anchorCandidates.size,
                    "first_unprocessed_index" to firstUnprocessedIndex,
                ),
            )

            if (anchorIndex >= 0 && identitiesStable) {
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
            val allIdentitiesStable = identitiesStable && identities.size == rows.size
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
            attributes = mapOf("tag" to tag, "attempts" to VIEWPORT_ANCHOR_PROBE_ATTEMPTS),
        )
        failTaskWithoutManualHandoff("The next result page did not expose a stable continuation anchor before timeout")
    }

    private fun visibleStructuralUserRows(context: ScreenContext): List<StructuralUserRowMatch> {
        val rows = ArrayList<StructuralUserRowMatch>()
        var minimumAnchorTop = (context.screenSize.height * USER_RESULTS_TOP_RATIO).toFloat() - 1f
        repeat(MAX_VISIBLE_USER_ROWS) {
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
            row.bounds.left + (rowWidth * USER_ROW_CONTENT_LEFT_RATIO).toInt(),
        ).coerceIn(0, screenWidth - 1)
        val safeRight = min(
            row.bounds.right,
            (screenWidth * USER_ROW_SAFE_TAP_RIGHT_RATIO).toInt(),
        ).coerceIn(safeLeft + 1, screenWidth)
        val safeTop = max(
            row.bounds.top,
            row.bounds.top + (rowHeight * USER_ROW_CONTENT_TOP_RATIO).toInt(),
        ).coerceIn(0, screenHeight - 1)
        val safeBottom = min(
            row.bounds.bottom,
            row.bounds.top + (rowHeight * USER_ROW_CONTENT_BOTTOM_RATIO).toInt(),
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
        val identityHash = currentUserIdentityHash ?: return
        val listName = currentUserDisplayName
        val nodeCandidate = ProfileDisplayNameResolver.fromAccessibility(context, listName)
        val confirmedNodeName = nodeCandidate?.let {
            confirmProfileNodeName(it, listName)
        }
        if (confirmedNodeName != null) {
            currentUserDisplayName = confirmedNodeName
            currentUserDisplayNameSource = UserResultIdentity.Source.ACCESSIBILITY
            AutomationStore.updateCurrentUserDisplayName(identityHash, confirmedNodeName)
            logger.info(
                "profile_display_name_resolved",
                attributes = mapOf(
                    "source" to "accessibility",
                    "identity_hash" to identityHash,
                    "confirmed" to true,
                ),
            )
            return
        }

        val clippedListName = listName.orEmpty().let { value ->
            value.isBlank() || value.contains("…") || value.contains("..") || value.trimEnd().endsWith('.')
        }
        // OCR is intentionally limited to uncertain rows. Accessibility-backed names are not
        // rescanned unless the profile node was unstable; OCR-backed rows always receive one
        // profile-header pass so a list-level glyph error cannot be persisted unchanged.
        val shouldUseProfileOcr = ocr != null && (
            currentUserDisplayNameSource == UserResultIdentity.Source.OCR ||
                clippedListName ||
                nodeCandidate != null
            )
        if (!shouldUseProfileOcr) return

        val enriched = captureContextWithOcr(
            base = context,
            tag = "profile_header_identity",
            region = OcrRegion.PROFILE_HEADER,
        ) ?: return
        val ocrName = ProfileDisplayNameResolver.fromOcr(enriched, currentUserDisplayName) ?: return
        currentUserDisplayName = ocrName
        currentUserDisplayNameSource = UserResultIdentity.Source.OCR
        AutomationStore.updateCurrentUserDisplayName(identityHash, ocrName)
        logger.info(
            "profile_display_name_resolved",
            attributes = mapOf("source" to "ocr_profile_header", "identity_hash" to identityHash),
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
        val identityHash = currentUserIdentityHash ?: return
        var directContext = context ?: currentWindowContext() ?: return
        val nodeName = DirectMessageDisplayNameResolver.fromAccessibility(
            directContext,
            currentUserDisplayName,
        )
        if (nodeName != null) {
            currentUserDisplayName = nodeName
            currentUserDisplayNameSource = UserResultIdentity.Source.ACCESSIBILITY
            AutomationStore.updateCurrentUserDisplayName(identityHash, nodeName, PageKind.DIRECT_MESSAGE)
            logger.info(
                "direct_message_display_name_resolved",
                attributes = mapOf("source" to "accessibility", "identity_hash" to identityHash),
            )
            return
        }

        if (ocr == null) return
        directContext = captureContextWithOcr(
            base = directContext,
            tag = "direct_message_identity",
            region = OcrRegion.PROFILE_HEADER,
        ) ?: directContext
        val ocrName = DirectMessageDisplayNameResolver.fromOcr(
            directContext,
            currentUserDisplayName,
        ) ?: return
        currentUserDisplayName = ocrName
        currentUserDisplayNameSource = UserResultIdentity.Source.OCR
        AutomationStore.updateCurrentUserDisplayName(identityHash, ocrName, PageKind.DIRECT_MESSAGE)
        logger.info(
            "direct_message_display_name_resolved",
            attributes = mapOf("source" to "ocr_header", "identity_hash" to identityHash),
        )
    }

    /**
     * A profile header can briefly expose the previous account while the page animation settles.
     * Confirm the same semantic title in two consecutive trees before persisting it.
     */
    private suspend fun confirmProfileNodeName(
        firstCandidate: String,
        previousName: String?,
    ): String? {
        var candidate = firstCandidate
        repeat(PROFILE_NAME_CONFIRM_ATTEMPTS - 1) {
            delay(PROFILE_NAME_CONFIRM_INTERVAL_MS)
            // A second inspection must come from a fresh root; reusing the initial snapshot
            // would make the confirmation meaningless during a transient profile animation.
            val liveContext = currentWindowContext() ?: return null
            val next = ProfileDisplayNameResolver.fromAccessibility(liveContext, candidate)
                ?: return null
            if (ProfileDisplayNameResolver.equivalent(candidate, next)) {
                return next
            }
            candidate = next
        }
        logger.warn(
            "profile_display_name_unstable",
            message = "The profile title changed between accessibility snapshots; OCR fallback will be considered",
            attributes = mapOf(
                "had_previous_name" to !previousName.isNullOrBlank(),
                "identity_hash" to (currentUserIdentityHash ?: 0),
            ),
        )
        return null
    }

    private suspend fun openPrivateMessage(context: ScreenContext) {
        enrichCurrentUserDisplayName(currentWindowContext() ?: context)
        phase = AutomationPhase.OPENING_MESSAGE_ENTRY
        AutomationStore.publishPhase(phase)
        var outcome = ActionOutcome.failure("No private-message entry route was available")
        for (attempt in 1..PRIVATE_MESSAGE_ENTRY_ATTEMPTS) {
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
            if (attempt < PRIVATE_MESSAGE_ENTRY_ATTEMPTS) delay(PRIVATE_MESSAGE_ENTRY_RETRY_INTERVAL_MS)
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
            attributes = mapOf("attempts" to PRIVATE_MESSAGE_ENTRY_ATTEMPTS),
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
        val unsafe = listOf("客服", "咨询", "购物车", "商品").any(searchable::contains)
        val semanticMessage = listOf("发私信", "私信", "message", "direct message", "paper", "plane")
            .any(searchable::contains)
        if (unsafe || !semanticMessage) {
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
        val signature = "${detection.kind}|${detection.reasons.sorted().joinToString(";")}".hashCode().toString()
        if (signature == lastOcrPageSignature) {
            ocrPageStableObservations++
        } else {
            lastOcrPageSignature = signature
            ocrPageStableObservations = 1
        }
        if (ocrPageStableObservations < OCR_PAGE_STABLE_OBSERVATIONS) {
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
        repeat(PRIVATE_MESSAGE_ENTRY_POSTCONDITION_ATTEMPTS) { probeAttempt ->
            delay(
                if (probeAttempt == 0) {
                    PRIVATE_MESSAGE_ENTRY_POSTCONDITION_INITIAL_DELAY_MS
                } else {
                    PRIVATE_MESSAGE_ENTRY_POSTCONDITION_INTERVAL_MS
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
            if (probeAttempt == PRIVATE_MESSAGE_ENTRY_OCR_PROBE_ATTEMPT && ocr != null) {
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

    /** Returns true only after a live tree reports a results page, not merely a completed gesture. */
    private suspend fun searchResultsPostconditionReached(route: String): Boolean {
        repeat(SEARCH_SUBMIT_POSTCONDITION_ATTEMPTS) { attempt ->
            delay(if (attempt == 0) SEARCH_SUBMIT_POSTCONDITION_DELAY_MS else SEARCH_SUBMIT_POSTCONDITION_INTERVAL_MS)
            val liveContext = currentWindowContext() ?: return@repeat
            val detection = pageDetector.detect(liveContext)
            logger.info(
                "search_submit_postcondition_probe",
                attributes = mapOf(
                    "route" to route,
                    "attempt" to attempt + 1,
                    "page" to detection.kind.name,
                    "confidence" to detection.confidence,
                ),
            )
            if (detection.kind == PageKind.SEARCH_RESULTS || detection.kind == PageKind.USER_RESULTS) {
                latestContext = liveContext
                AutomationStore.publishObservation(detection)
                return true
            }
        }
        return false
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
            when (val launch = TargetAppLauncher.launch(service)) {
                LaunchResult.Started -> {
                    delay(MESSAGE_TARGET_RESTORE_DELAY_MS)
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
        repeat(MESSAGE_INPUT_ATTEMPTS) { attempt ->
            if (inputPlaced) return@repeat
            if (attempt > 0) delay(MESSAGE_INPUT_RETRY_INTERVAL_MS)
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

        delay(MESSAGE_INPUT_SETTLE_DELAY_MS)
        val refreshedContext = currentWindowContext() ?: directContext
        var sendButtonOutcome = ActionOutcome.failure("No usable message send action")
        repeat(MESSAGE_ACTION_ATTEMPTS) { attempt ->
            if (sendButtonOutcome.succeeded) return@repeat
            if (attempt > 0) delay(MESSAGE_INPUT_RETRY_INTERVAL_MS)
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
            identityHash = currentUserIdentityHash,
            outcome = UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED,
            reason = "Douyin displayed the blank-message rejection",
            page = PageKind.MESSAGE_EMPTY_REJECTED,
        )
        currentUserIdentityHash = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null
        advanceAfterEmptyMessageProbe()
    }

    private fun scheduleEmptyMessageProbeResultCheck() {
        messageResultJob?.cancel()
        messageResultJob = scope.launch {
            repeat(EMPTY_MESSAGE_PROBE_ATTEMPTS) { attempt ->
                delay(if (attempt == 0) EMPTY_MESSAGE_PROBE_INITIAL_DELAY_MS else EMPTY_MESSAGE_PROBE_INTERVAL_MS)
                mutex.withLock {
                    if (!taskActive || phase != AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT) return@withLock
                    // Node inspection catches a toast exposed as text. On custom-rendered chat
                    // surfaces, take a bounded OCR probe at roughly 1.4s intervals as well. The
                    // screenshot helper rate-limits the actual capture, preventing a tight loop
                    // from flooding the device while still covering a short-lived toast.
                    val context = if (attempt % EMPTY_MESSAGE_OCR_EVERY_ATTEMPTS == 0) {
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
                val result = engine.recognize(bitmap, region)
                base.copy(
                    ocrBlocks = result.toOcrTextBlocks(),
                    capturedAtMillis = System.currentTimeMillis(),
                ).also { latestContext = it }
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
                    ocrBlocks = result.toOcrTextBlocks(),
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

    /** Returns to user results after a verified blank-message rejection, then selects the next row. */
    private suspend fun advanceAfterEmptyMessageProbe() {
        if (!taskActive) return
        val previousAnchorBottom = lastProcessedUserAnchorBottom
        val currentJob = coroutineContext[Job]
        messageEntryPostconditionJob?.takeUnless { it === currentJob }?.cancel()
        messageEntryPostconditionJob = null

        var resultsContext: ScreenContext? = null
        for (attempt in 0 until MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE) {
            val currentContext = currentWindowContext()
            if (currentContext != null && pageDetector.detect(currentContext).kind == PageKind.USER_RESULTS) {
                resultsContext = currentContext
                break
            }
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                failTaskWithoutManualHandoff("Could not return to user results after the blank-message probe")
                return
            }
            delay(USER_PROFILE_BACK_DELAY_MS)
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
        repeat(USER_NEXT_RESULT_POSTCONDITION_ATTEMPTS) { attempt ->
            delay(if (attempt == 0) USER_NEXT_RESULT_DELAY_MS else USER_NEXT_RESULT_POSTCONDITION_INTERVAL_MS)
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
            attributes = mapOf("tag" to tag, "attempts" to USER_NEXT_RESULT_POSTCONDITION_ATTEMPTS),
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
        repeat(MAX_BACK_ACTIONS_TO_SEARCH_ENTRY) { attempt ->
            if (context != null && pageDetector.detect(context!!).kind == PageKind.SEARCH_ENTRY) return@repeat
            if (context != null && pageDetector.detect(context!!).kind == PageKind.HOME) {
                openSearch(context!!)
                return true
            }
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                context = null
                return@repeat
            }
            delay(USER_PROFILE_BACK_DELAY_MS)
            context = currentWindowContext()
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
        if (message.length > MAX_MESSAGE_LENGTH) {
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
            when (val launch = TargetAppLauncher.launch(service)) {
                LaunchResult.Started -> {
                    delay(MESSAGE_TARGET_RESTORE_DELAY_MS)
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
        repeat(MESSAGE_INPUT_ATTEMPTS) { attempt ->
            if (inputPlaced) return@repeat
            if (attempt > 0) delay(MESSAGE_INPUT_RETRY_INTERVAL_MS)
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

        delay(MESSAGE_INPUT_SETTLE_DELAY_MS)
        val refreshedContext = currentWindowContext() ?: directContext
        var sendButtonOutcome = ActionOutcome.failure("No usable message send action")
        repeat(MESSAGE_ACTION_ATTEMPTS) { attempt ->
            if (sendButtonOutcome.succeeded) return@repeat
            if (attempt > 0) delay(MESSAGE_INPUT_RETRY_INTERVAL_MS)
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

    private fun completeMessageSent() {
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        messageResultJob?.cancel()
        taskActive = false
        keyword = null
        activeTaskSnapshot = null
        phase = AutomationPhase.COMPLETED_MESSAGE_SENT
        AutomationStore.publishPhase(phase)
        logger.info("message_send_completed", message = "One operator-requested message was verified in the conversation")
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
            repeat(MESSAGE_RESULT_ATTEMPTS) { attempt ->
                delay(MESSAGE_RESULT_INTERVAL_MS)
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
            delay(MESSAGE_ENTRY_POSTCONDITION_DELAY_MS)
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
            identityHash = currentUserIdentityHash,
            outcome = UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
            reason = reason,
            page = PageKind.USER_PROFILE,
        )
        currentUserIdentityHash = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null

        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            failTaskWithoutManualHandoff("Could not leave the unavailable user profile")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        delay(USER_PROFILE_BACK_DELAY_MS)
        val resultsContext = currentWindowContext()
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
            identityHash = currentUserIdentityHash,
            outcome = UserTaskRecord.Outcome.MESSAGE_SEND_FAILED,
            reason = reason,
            page = failurePage,
        )
        currentUserIdentityHash = null
        currentUserDisplayName = null
        currentUserDisplayNameSource = null

        var resultsContext: ScreenContext? = null
        for (attempt in 0 until MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE) {
            val currentContext = currentWindowContext()
            if (currentContext != null && pageDetector.detect(currentContext).kind == PageKind.USER_RESULTS) {
                resultsContext = currentContext
                break
            }
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                failTaskWithoutManualHandoff("Could not return to user results after a message-send failure")
                return
            }
            delay(USER_PROFILE_BACK_DELAY_MS)
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
                val blocks = result.toOcrTextBlocks()
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

    private fun userResultViewportSignature(context: ScreenContext): String = buildString {
        // Node counts fluctuate when a profile is reopened, while the action-anchor geometry stays
        // stable for the same viewport. Do not include the count or identity OCR will run again
        // before every visible row.
        append("viewport|")
        context.nodes.asSequence()
            .filter { node -> node.bounds.width > 0 && node.bounds.height > 0 }
            .filter { node -> node.bounds.right >= context.screenSize.width * 0.62f }
            .filter { node -> node.bounds.top >= context.screenSize.height * USER_RESULTS_TOP_RATIO }
            .sortedBy { it.bounds.top }
            .forEach { node ->
                append(node.bounds.left).append(',')
                    .append(node.bounds.top).append(',')
                    .append(node.bounds.right).append(',')
                    .append(node.bounds.bottom).append(';')
            }
    }

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
        repeat(SYSTEM_OVERLAY_WAIT_ATTEMPTS) { attempt ->
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
                    delay(SYSTEM_OVERLAY_WAIT_INTERVAL_MS)
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
            delay(SYSTEM_OVERLAY_WAIT_INTERVAL_MS)
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
                "start_y" to USER_PAGE_SWIPE_START_Y,
                "end_y" to USER_PAGE_SWIPE_END_Y,
                "travel" to (USER_PAGE_SWIPE_START_Y - USER_PAGE_SWIPE_END_Y),
            ),
        )
        val outcome = swipeNormalizedGuarded(
            startX = 0.50f,
            startY = USER_PAGE_SWIPE_START_Y,
            endX = 0.50f,
            endY = USER_PAGE_SWIPE_END_Y,
            durationMs = USER_PAGE_SWIPE_DURATION_MS,
            tag = tag,
        )
        if (outcome.succeeded) remotePageNumber = (remotePageNumber + 1).coerceAtMost(10_000)
        return outcome
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
        delay(KEYWORD_POSTCONDITION_DELAY_MS)
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
        val retry = withLiveNode(refreshedTarget) { liveNode -> gestures.setText(liveNode, expected) }
        if (!retry.succeeded) return false
        delay(KEYWORD_POSTCONDITION_DELAY_MS)
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
                AutomationPhase.WAITING_FOR_HOME -> STARTUP_STEP_TIMEOUT_MS
                AutomationPhase.WAITING_FOR_DIRECT_MESSAGE -> MESSAGE_ENTRY_TIMEOUT_MS
                else -> STEP_TIMEOUT_MS
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
                    } else if (timeoutRecoveryAttempts < MAX_TIMEOUT_RECOVERY_ATTEMPTS) {
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

        // A startup ad can leave the underlying HOME node tree visible. Re-check the overlay
        // before the phase-specific HOME recovery so a timeout can never turn into a tap through
        // the ad banner.
        if (timedOutPhase == AutomationPhase.WAITING_FOR_HOME &&
            context != null &&
            TransientOverlayDetector.findStartupAd(context) != null
        ) {
            val startupAd = TransientOverlayDetector.findStartupAd(requireNotNull(context))
            logger.info(
                "startup_ad_timeout_recovery_wait",
                message = "The startup advertisement is still visible; extending the bounded wait",
                attributes = mapOf("marker" to (startupAd?.marker ?: "unknown")),
            )
            await(
                nextPhase = AutomationPhase.WAITING_FOR_HOME,
                timeoutDescription = timeoutDescription,
                resetRecoveryBudget = false,
            )
            scheduleInitialObservation()
            return
        }

        when (timedOutPhase) {
            AutomationPhase.WAITING_FOR_SEARCH_RESULTS -> when {
                context != null && detection?.kind == PageKind.SEARCH_ENTRY -> {
                    logger.warn(
                        "search_results_timeout_retry",
                        message = "Search entry is still visible; revalidating the keyword and resubmitting",
                    )
                    enterKeyword(context, preserveTimeoutRecoveryBudget = true)
                }
                else -> pause(timeoutDescription)
            }

            AutomationPhase.WAITING_FOR_SEARCH_ENTRY -> when (detection?.kind) {
                PageKind.SEARCH_ENTRY -> enterKeyword(context!!)
                PageKind.SEARCH_RESULTS -> reuseSearchResultsQueryField(requireNotNull(context), "search_entry_timeout_recovery")
                PageKind.HOME -> openSearch(context!!)
                else -> pause(timeoutDescription)
            }

            AutomationPhase.WAITING_FOR_HOME -> when (detection?.kind) {
                PageKind.HOME -> openSearch(requireNotNull(context))
                PageKind.SEARCH_ENTRY -> enterKeyword(requireNotNull(context))
                PageKind.SEARCH_RESULTS -> reuseSearchResultsQueryField(requireNotNull(context), "home_timeout_recovery")
                PageKind.USER_RESULTS,
                PageKind.USER_PROFILE,
                PageKind.DIRECT_MESSAGE -> recoverInitialSurface(requireNotNull(context))
                PageKind.UNKNOWN -> {
                    val startupAd = context?.let(TransientOverlayDetector::findStartupAd)
                    if (startupAd != null) {
                        logger.info(
                            "startup_ad_timeout_recovery_wait",
                            message = "The startup advertisement is still visible; extending the bounded wait",
                            attributes = mapOf("marker" to startupAd.marker),
                        )
                        await(
                            nextPhase = AutomationPhase.WAITING_FOR_HOME,
                            timeoutDescription = timeoutDescription,
                            resetRecoveryBudget = false,
                        )
                        scheduleInitialObservation()
                    } else {
                        pause(timeoutDescription)
                    }
                }
                else -> pause(timeoutDescription)
            }

            AutomationPhase.WAITING_FOR_USER_RESULTS -> when (detection?.kind) {
                PageKind.USER_RESULTS -> selectVisibleUser(context!!)
                PageKind.SEARCH_RESULTS -> selectUserTab(context!!)
                else -> pause(timeoutDescription)
            }

            AutomationPhase.WAITING_FOR_PROFILE -> when (detection?.kind) {
                // The profile transition can be delivered without a follow-up accessibility
                // event on custom-rendered Douyin pages. If the watchdog sees a verified profile,
                // retry the private-message entry instead of pausing on a screen that is already
                // ready for the next action.
                PageKind.USER_PROFILE -> openPrivateMessage(requireNotNull(context))
                PageKind.USER_RESULTS -> selectVisibleUser(requireNotNull(context))
                else -> skipMessageSendFailure(
                    "The user profile did not become available after the bounded recovery window",
                    failurePage = detection?.kind ?: PageKind.USER_RESULTS,
                )
            }

            else -> pause(timeoutDescription)
        }
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
        val directory = File(service.filesDir, NODE_DUMP_DIRECTORY)
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
                    val contextWithOcr = base.copy(ocrBlocks = result.toOcrTextBlocks())
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

    @Suppress("DEPRECATION")
    private fun currentWindowContext(): ScreenContext? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            if (root.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) return null
            inspector.inspect(root)
        } finally {
            root.recycle()
        }
    }

    private fun OcrResult.toOcrTextBlocks(): List<OcrTextBlock> = blocks.map { block ->
        val bounds = block.bounds
        OcrTextBlock(
            text = block.text,
            bounds = if (bounds == null) {
                ScreenBounds.EMPTY
            } else {
                ScreenBounds(
                    left = min(bounds.left, bounds.right),
                    top = min(bounds.top, bounds.bottom),
                    right = max(bounds.left, bounds.right),
                    bottom = max(bounds.top, bounds.bottom),
                )
            },
        )
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
        pausedPhase = phase.takeUnless {
            it == AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF || it == AutomationPhase.STOPPED
        }
        taskActive = false
        queuedTaskSnapshots.clear()
        phase = AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF
        if (currentUserIdentityHash != null) {
            AutomationStore.recordUserTaskFinished(
                identityHash = currentUserIdentityHash,
                outcome = UserTaskRecord.Outcome.PAUSED,
                reason = reason,
                page = latestContext?.let(pageDetector::detect)?.kind,
            )
            currentUserIdentityHash = null
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
        taskActive = false
        queuedTaskSnapshots.clear()
        if (currentUserIdentityHash != null) {
            AutomationStore.recordUserTaskFinished(
                identityHash = currentUserIdentityHash,
                outcome = UserTaskRecord.Outcome.STOPPED,
                reason = "Stopped by the operator",
                page = latestContext?.let(pageDetector::detect)?.kind,
            )
            currentUserIdentityHash = null
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
        cachedUserResultsViewportSignature = null
        cachedUserResultsOcrBlocks = emptyList()
        userResultsSignatureBeforeSwipe = null
        pausedPhase = null
        phase = AutomationPhase.STOPPED
        AutomationStore.publishPhase(AutomationPhase.STOPPED)
        logger.info("poc_stopped")
    }

    private companion object {
        val IDENTITY_GENERIC_TOKENS = setOf(
            "关注",
            "回关",
            "已关注",
            "互相关注",
            "发私信",
            "背景图片",
            "背景图",
            "用户头像",
            "头像",
            "头像图片",
            "图片",
            "图片背景",
            "背景",
            "默认头像",
            "用户图片",
            "封面",
            "封面图片",
            "视频封面",
            "视频",
            "照片",
            "筛选",
            "按钮",
            "店铺账号",
            "商家认证账号",
            "朋友",
        )
        const val STEP_TIMEOUT_MS = 12_000L
        const val STARTUP_STEP_TIMEOUT_MS = 30_000L
        const val NODE_DUMP_DIRECTORY = "diagnostics/nodes"
        const val INITIAL_OBSERVATION_ATTEMPTS = 24
        const val INITIAL_OBSERVATION_INTERVAL_MS = 350L
        const val INITIAL_OCR_RETRY_EVERY_OBSERVATIONS = 2
        const val INITIAL_OCR_MAX_ATTEMPTS = 6
        const val OCR_PAGE_STABLE_OBSERVATIONS = 2
        const val INITIAL_SCREEN_SETTLE_DELAY_MS = 5_000L
        const val NEXT_TASK_SETTLE_DELAY_MS = 900L
        const val INITIAL_READY_STABLE_OBSERVATIONS = 2
        val INITIAL_READY_PAGE_KINDS = setOf(
            PageKind.HOME,
            PageKind.SEARCH_ENTRY,
            PageKind.SEARCH_RESULTS,
            PageKind.USER_RESULTS,
        )
        const val KEYWORD_POSTCONDITION_DELAY_MS = 280L
        const val MAX_NODE_SCROLL_ATTEMPTS = 2
        const val USER_RESULTS_POSTCONDITION_ATTEMPTS = 8
        const val USER_RESULTS_POSTCONDITION_INTERVAL_MS = 500L
        const val USER_ROW_POSTCONDITION_ATTEMPTS = 8
        const val USER_ROW_POSTCONDITION_INTERVAL_MS = 350L
        const val IDENTITY_RETRY_ATTEMPTS = 3
        const val IDENTITY_RETRY_INTERVAL_MS = 450L
        const val VIEWPORT_ANCHOR_PROBE_ATTEMPTS = 8
        const val VIEWPORT_ANCHOR_PROBE_INTERVAL_MS = 650L
        const val VIEWPORT_IDENTITY_STABLE_OBSERVATIONS = 2
        const val REMOTE_RESUME_EXTRA_SWIPES = 8
        const val MAX_REMOTE_RESUME_SWIPES = 30
        const val MAX_VISIBLE_USER_ROWS = 20
        const val MESSAGE_ENTRY_POSTCONDITION_DELAY_MS = 900L
        const val PRIVATE_MESSAGE_ENTRY_ATTEMPTS = 3
        const val PRIVATE_MESSAGE_ENTRY_RETRY_INTERVAL_MS = 450L
        const val PRIVATE_MESSAGE_ENTRY_POSTCONDITION_ATTEMPTS = 7
        const val PRIVATE_MESSAGE_ENTRY_POSTCONDITION_INITIAL_DELAY_MS = 500L
        const val PRIVATE_MESSAGE_ENTRY_POSTCONDITION_INTERVAL_MS = 450L
        const val PRIVATE_MESSAGE_ENTRY_OCR_PROBE_ATTEMPT = 2
        const val USER_PROFILE_BACK_DELAY_MS = 700L
        const val MAX_BACK_ACTIONS_TO_SEARCH_ENTRY = 3
        const val PROFILE_POSTCONDITION_ATTEMPTS = 16
        const val PROFILE_POSTCONDITION_INITIAL_DELAY_MS = 450L
        const val PROFILE_POSTCONDITION_INTERVAL_MS = 400L
        const val PROFILE_NAME_CONFIRM_ATTEMPTS = 2
        const val PROFILE_NAME_CONFIRM_INTERVAL_MS = 110L
        const val USER_NEXT_RESULT_DELAY_MS = 700L
        // Network-backed result pages can expose a half-moved RecyclerView for several seconds.
        // Keep the wait bounded but long enough to cover a slow page append before handing off.
        const val USER_NEXT_RESULT_POSTCONDITION_ATTEMPTS = 36
        const val USER_NEXT_RESULT_POSTCONDITION_INTERVAL_MS = 400L
        const val MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE = 2
        const val MESSAGE_ENTRY_TIMEOUT_MS = 12_000L
        const val MAX_MESSAGE_LENGTH = 500
        const val MESSAGE_TARGET_RESTORE_DELAY_MS = 700L
        const val MESSAGE_INPUT_SETTLE_DELAY_MS = 250L
        const val MESSAGE_INPUT_ATTEMPTS = 3
        const val MESSAGE_ACTION_ATTEMPTS = 3
        const val MESSAGE_INPUT_RETRY_INTERVAL_MS = 350L
        const val MESSAGE_RESULT_ATTEMPTS = 8
        const val MESSAGE_RESULT_INTERVAL_MS = 600L
        // The rejection toast is transient. Start quickly, then sample for roughly six seconds
        // while also listening for Accessibility notification events. This replaces the old
        // 4.8-second/600ms cadence that routinely missed the toast on the real device.
        const val EMPTY_MESSAGE_PROBE_ATTEMPTS = 18
        const val EMPTY_MESSAGE_PROBE_INITIAL_DELAY_MS = 120L
        const val EMPTY_MESSAGE_PROBE_INTERVAL_MS = 350L
        const val EMPTY_MESSAGE_OCR_EVERY_ATTEMPTS = 4
        const val MAX_TIMEOUT_RECOVERY_ATTEMPTS = 1
        const val SEARCH_ENTRY_POSTCONDITION_ATTEMPTS = 8
        const val SEARCH_ENTRY_POSTCONDITION_INTERVAL_MS = 350L
        const val SEARCH_SUBMIT_POSTCONDITION_ATTEMPTS = 3
        const val SEARCH_SUBMIT_POSTCONDITION_DELAY_MS = 450L
        const val SEARCH_SUBMIT_POSTCONDITION_INTERVAL_MS = 350L
        // OEM/Douyin live banners can remain above the target for several seconds. Wait long
        // enough for a normal transient notification to clear, but keep a bounded manual-handoff
        // path when the active window never returns.
        const val SYSTEM_OVERLAY_WAIT_ATTEMPTS = 30
        const val SYSTEM_OVERLAY_WAIT_INTERVAL_MS = 350L
        const val USER_PAGE_SWIPE_START_Y = 0.76f
        const val USER_PAGE_SWIPE_END_Y = 0.38f
        const val USER_PAGE_SWIPE_DURATION_MS = 480L
        const val USER_RESULTS_TOP_RATIO = 0.14f
        const val USER_ROW_MIN_HEIGHT = 180
        const val USER_ROW_MAX_HEIGHT = 400
        const val USER_ROW_CONTENT_LEFT_RATIO = 0.24f
        const val USER_ROW_CONTENT_TOP_RATIO = 0.12f
        const val USER_ROW_CONTENT_BOTTOM_RATIO = 0.58f
        const val USER_ROW_SAFE_TAP_RIGHT_RATIO = 0.70f
        const val USER_TAB_VIEWPORT_FALLBACK_RIGHT_RATIO = 0.84f

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
            preferredRegion = NormalizedRect(0.68f, 0f, 1f, 0.30f),
            minimumScore = 0.30f,
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
            repeat(INITIAL_OBSERVATION_ATTEMPTS) { observationAttempt ->
                delay(INITIAL_OBSERVATION_INTERVAL_MS)
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_HOME) return@launch

                val context = currentWindowContext() ?: return@repeat
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
                val searchCandidateReady = detected.kind == PageKind.UNKNOWN &&
                    TransientOverlayDetector.find(augmentedContext) == null &&
                    hasInitialSearchSelectorCandidate(augmentedContext)
                val detection = if (searchCandidateReady) {
                    logger.info(
                        "initial_search_selector_candidate",
                        message = "The semantic search icon is visible after the startup settle window",
                    )
                    PageDetection(
                        kind = PageKind.HOME,
                        confidence = 0.78f,
                        reasons = listOf("Semantic home search-entry candidate found"),
                    )
                } else {
                    detected
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
                if (detection.kind in INITIAL_READY_PAGE_KINDS) {
                    if (detection.kind == lastReadyKind) {
                        readyObservations++
                    } else {
                        lastReadyKind = detection.kind
                        readyObservations = 1
                    }
                    if (readyObservations < INITIAL_READY_STABLE_OBSERVATIONS) {
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
        if (observationAttempt % INITIAL_OCR_RETRY_EVERY_OBSERVATIONS != 0) return context
        if (initialOcrAttempts >= INITIAL_OCR_MAX_ATTEMPTS) return context
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
                if (result.isEmpty) context else context.copy(ocrBlocks = result.toOcrTextBlocks())
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

    private fun hasInitialSearchSelectorCandidate(context: ScreenContext): Boolean =
        selector.select(context, DouyinSelectors.searchEntry).node != null ||
            selector.select(context, DouyinSelectors.searchEntryStructural).node != null

}
