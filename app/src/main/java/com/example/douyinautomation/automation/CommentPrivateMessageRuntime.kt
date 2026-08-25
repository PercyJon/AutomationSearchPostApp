package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Terminal outcome emitted to the owning navigation controller. */
data class CommentRuntimeTerminal(
    val outcome: Outcome,
    val reason: String,
) {
    enum class Outcome {
        COMPLETED,
        FAILED,
        PAUSED,
        /** The current source profile cannot provide a video; the controller may select next. */
        SKIPPED_PROFILE,
    }
}

/** Dump a missing comment rail only while the sheet is not already the active surface. */
internal object CommentButtonMissDumpPolicy {
    fun shouldDump(
        hasVideoSurface: Boolean,
        hasCommentEntry: Boolean,
        commentSurfaceReady: Boolean,
    ): Boolean = hasVideoSurface && !hasCommentEntry && !commentSurfaceReady
}

/** Pure gate for the bounded post-swipe action-rail probe. It never authorizes a click. */
internal object NextVideoTransitionProbePolicy {
    fun shouldProbeActionRail(
        page: PageKind,
        hasVideoSurface: Boolean,
        hasCommentEntry: Boolean,
        changedFromPreviousVideo: Boolean,
        ocrProbeCount: Int,
        ocrProbeLimit: Int,
        sheetOpen: Boolean = false,
    ): Boolean =
        !sheetOpen &&
            !hasCommentEntry &&
            ocrProbeCount < ocrProbeLimit &&
            (
                hasVideoSurface ||
                    page == PageKind.HOME ||
                    (page == PageKind.UNKNOWN && changedFromPreviousVideo)
                )

    fun shouldReplaceSwipeWatchdog(
        actionRailProbeWillRun: Boolean,
        watchdogAlreadyReplaced: Boolean,
    ): Boolean = actionRailProbeWillRun && !watchdogAlreadyReplaced
}

private val NEXT_VIDEO_INTERVENTION_PAGES = setOf(
    PageKind.HUMAN_INTERVENTION,
    PageKind.LOGIN,
    PageKind.LIVE_ROOM,
    PageKind.LIVE_ROOM_SESSION,
)

/**
 * P4-C runtime for the verified profile → first video → comments → commenter profile route.
 *
 * Candidates are read and deduplicated before any profile action. Every click is resolved from a
 * fresh AccessibilityNodeInfo tree, and the normalized gesture is only used after a
 * semantic/structural node selection has succeeded. The only submitted value is a single space
 * used to verify Douyin's blank-message rejection; real message content is never sent.
 */
class CommentPrivateMessageRuntime(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val logger: DiagnosticLogger,
    private val inspector: NodeTreeInspector,
    private val pageDetector: PageDetector,
    private val selector: SelectorEngine,
    private val gestures: GestureEngine,
    /** Owner-supplied OCR capture used only for a malformed next-video comment rail. */
    private val enrichWithOcr: suspend (ScreenContext) -> ScreenContext = { it },
    /** Restores opaque task-level candidate references after a saved-task resume. */
    private val restoredCommentCandidateFingerprints: () -> Set<String> = { emptySet() },
    /** Persists one newly attempted comment candidate before its profile flow begins. */
    private val onCommentCandidateProcessed: (String) -> Unit = {},
    private val onTerminal: (CommentRuntimeTerminal) -> Unit,
) {
    private val stateMachine = CommentEntryStateMachine()
    private val ledger = CommentCandidateLedger()
    private var config: CommentPrivateMessageSnapshot? = null
    private var timeoutJob: Job? = null
    /** Rejects an old watchdog that wakes while a newer action has already re-armed it. */
    private val timeoutGeneration = AtomicLong(0L)
    @Volatile private var running = false
    private var scrollPending = false
    private var lastViewportFingerprint: Int? = null
    private var scrollCount = 0
    /** Consecutive scrolls whose viewport fingerprint did not change; signals end-of-list. */
    private var staleScrollCount = 0
    /** Consecutive scrolls that added zero new candidates; catches RecyclerView recycling at the bottom. */
    private var emptyScrollCount = 0
    /** Bounded settling reads for the first comment viewport; never scroll before it resolves. */
    private var initialCandidateReadRetryCount = 0
    /** Viewports already included in the task's aggregate match statistics. */
    private val reportedMatchStatisticsFingerprints = LinkedHashSet<Int>()
    private var videoIndex = 0
    private var liveRoomExitCount = 0
    private var liveRoomSwipeCount = 0
    private val processedCandidateKeys = LinkedHashSet<String>()
    /** Task-level opaque ledger; survives restart and prevents a commenter being probed twice. */
    private val processedTaskCandidateLedger = CommentCandidateResumeLedger()
    private var activeCandidate: CommentUserCandidate? = null
    /** Absolute uptime until which post-swipe observations are ignored while the next video settles. */
    private var nextVideoSettleUntilMs = 0L
    /** True while the blank-message safety probe is awaiting its result. */
    @Volatile private var probingBlankMessage = false
    /** Set when a transient toast carried the blank-message rejection text. */
    @Volatile private var blankRejectionObserved = false
    /** Latest target-app observation, used to avoid a redundant full-tree read after a tap. */
    @Volatile private var latestObservedContext: ScreenContext? = null
    /** Monotonically increases for every target-app observation. */
    private val observedContextGeneration = AtomicLong(0L)
    /** True only while a commenter flow has started returning from profile/DM to its comment sheet. */
    @Volatile private var awaitingCommentSurfaceReturn = false
    /** A comment sheet confirmed by an event during [awaitingCommentSurfaceReturn]. */
    @Volatile private var confirmedReturnCommentSurface: ScreenContext? = null
    /** Number of successful BACK actions dispatched during the current comment-sheet return. */
    @Volatile private var returnCommentSurfaceBackActions = 0
    /** The return BACK count at which [confirmedReturnCommentSurface] was observed. */
    @Volatile private var confirmedReturnCommentSurfaceBackActions = -1
    /**
     * Accessibility events can arrive while a commenter profile is still being processed. A
     * second viewport pass must not run concurrently with the first one, otherwise both passes
     * consume the same ledger and can exceed the configured per-video user limit.
     */
    private val viewportMutex = Mutex()
    /** A4: newest unprocessed viewport context. Events arriving while the lock is held are merged
     * into this slot instead of being dropped, so a burst of accessibility events never loses the
     * viewport that actually carried the next loaded comment. */
    @Volatile private var pendingViewportContext: ScreenContext? = null
    /** A4: single consumer coroutine that drains [pendingViewportContext] while holding the lock. */
    private var viewportMergeJob: Job? = null
    /** Bounded direct verification for a first-video click when Douyin drops its page event. */
    private var firstVideoTransitionProbeJob: Job? = null
    /** Bounded direct verification for an opened comment sheet when its event is dropped. */
    private var commentPanelProbeJob: Job? = null
    /** Bounded direct verification after the feed swipe selects the next video. */
    private var nextVideoTransitionProbeJob: Job? = null
    /**
     * A search-result → profile transition can expose a verified profile shell before its Works
     * grid has populated. The initial shell has no safe first-video target yet, and some Douyin
     * builds do not emit a second accessibility event when the grid appears. Re-sample that
     * already-verified profile for a short, fixed window instead of idling until the entry guard.
     */
    private var profileSurfaceProbeJob: Job? = null
    /** One profile OCR sample used only to locate the Works-grid anchor when nodes omit it. */
    private var profileWorksAnchorOcrAttempted = false
    /** Lightweight semantic fingerprint taken after the old comment sheet has closed. */
    private var nextVideoSurfaceSignatureBeforeSwipe: Int? = null
    /** True once a stable changed closed-player fingerprint proves the swipe changed works. */
    private var nextVideoPlayerFingerprintConfirmed = false
    /** True until the changed player or comment viewport proves the swipe landed on a different video. */
    @Volatile private var awaitingNextVideoConfirmation = false
    private var nextVideoCommitted = false
    private var nextVideoSwipeAttempt = 0
    private var nextVideoAdvanceReason = ""
    /** First video's first visible comment-row top as a screen-height ratio; not pixels. */
    private var firstScreenCommentRowTopRatio: Float? = null
    /** Probe-only: allow OPEN_COMMENTS while [awaitingNextVideoConfirmation] is still true. */
    @Volatile private var nextVideoCommentsOpenAuthorized = false

    val isRunning: Boolean get() = running
    val stage: CommentEntryStage get() = stateMachine.stage
    val candidateCount: Int get() = ledger.size
    val isProbingBlankMessage: Boolean get() = running && probingBlankMessage

    fun start(snapshot: CommentPrivateMessageSnapshot) {
        timeoutJob?.cancel()
        config = snapshot
        stateMachine.reset()
        ledger.clear()
        running = true
        scrollPending = false
        lastViewportFingerprint = null
        scrollCount = 0
        staleScrollCount = 0
        emptyScrollCount = 0
        initialCandidateReadRetryCount = 0
        reportedMatchStatisticsFingerprints.clear()
        videoIndex = 0
        liveRoomExitCount = 0
        liveRoomSwipeCount = 0
        processedCandidateKeys.clear()
        processedTaskCandidateLedger.restore(restoredCommentCandidateFingerprints())
        activeCandidate = null
        latestObservedContext = null
        observedContextGeneration.incrementAndGet()
        awaitingCommentSurfaceReturn = false
        clearReturnCommentSurfaceConfirmation()
        pendingViewportContext = null
        viewportMergeJob?.cancel()
        viewportMergeJob = null
        firstVideoTransitionProbeJob?.cancel()
        firstVideoTransitionProbeJob = null
        commentPanelProbeJob?.cancel()
        commentPanelProbeJob = null
        nextVideoTransitionProbeJob?.cancel()
        nextVideoTransitionProbeJob = null
        profileSurfaceProbeJob?.cancel()
        profileSurfaceProbeJob = null
        profileWorksAnchorOcrAttempted = false
        nextVideoSurfaceSignatureBeforeSwipe = null
        nextVideoPlayerFingerprintConfirmed = false
        awaitingNextVideoConfirmation = false
        nextVideoCommitted = false
        nextVideoSwipeAttempt = 0
        nextVideoAdvanceReason = ""
        firstScreenCommentRowTopRatio = null
        nextVideoCommentsOpenAuthorized = false
        // Search-target mode still traverses the existing launch/search/user-tab flow before the
        // runtime receives a profile observation. Give that bounded entry route a longer guard;
        // once the profile/video/comment actions begin, each step returns to the short watchdog.
        armTimeout("等待用户主页", timeoutMs = TuningConstants.CommentRuntime.INITIAL_ENTRY_TIMEOUT_MS)
        logger.info(
            "comment_runtime_started",
            attributes = mapOf(
                "entry_mode" to snapshot.entryMode.name,
                // Numeric bounds are deliberately keyed without the "user"/"keyword" substrings so
                // the DiagnosticLogger's defensive redaction does not hide the M3 5/10/20 evidence.
                "terms_count" to snapshot.matchKeywords.size,
                "max_videos" to snapshot.maxVideos,
                "per_video_cap" to snapshot.maxUsersPerVideo,
                "skip_pinned" to snapshot.skipPinnedVideos,
                "dry_run" to snapshot.dryRun,
                "skip_blank_probe" to snapshot.skipBlankProbe,
            ),
        )
    }

    fun stop() {
        timeoutJob?.cancel()
        timeoutJob = null
        viewportMergeJob?.cancel()
        viewportMergeJob = null
        firstVideoTransitionProbeJob?.cancel()
        firstVideoTransitionProbeJob = null
        commentPanelProbeJob?.cancel()
        commentPanelProbeJob = null
        nextVideoTransitionProbeJob?.cancel()
        nextVideoTransitionProbeJob = null
        profileSurfaceProbeJob?.cancel()
        profileSurfaceProbeJob = null
        profileWorksAnchorOcrAttempted = false
        nextVideoSurfaceSignatureBeforeSwipe = null
        nextVideoPlayerFingerprintConfirmed = false
        awaitingNextVideoConfirmation = false
        nextVideoCommitted = false
        nextVideoSwipeAttempt = 0
        nextVideoAdvanceReason = ""
        firstScreenCommentRowTopRatio = null
        nextVideoCommentsOpenAuthorized = false
        pendingViewportContext = null
        running = false
        config = null
        ledger.clear()
        processedCandidateKeys.clear()
        processedTaskCandidateLedger.clear()
        activeCandidate = null
        latestObservedContext = null
        observedContextGeneration.incrementAndGet()
        awaitingCommentSurfaceReturn = false
        clearReturnCommentSurfaceConfirmation()
    }

    /** Restarts profile observation after a search result was skipped without losing the task. */
    fun rearmAfterSkippedProfile() {
        if (config == null) return
        stateMachine.reset()
        ledger.clear()
        scrollPending = false
        lastViewportFingerprint = null
        scrollCount = 0
        staleScrollCount = 0
        emptyScrollCount = 0
        initialCandidateReadRetryCount = 0
        activeCandidate = null
        latestObservedContext = null
        observedContextGeneration.incrementAndGet()
        awaitingCommentSurfaceReturn = false
        clearReturnCommentSurfaceConfirmation()
        pendingViewportContext = null
        viewportMergeJob?.cancel()
        viewportMergeJob = null
        firstVideoTransitionProbeJob?.cancel()
        firstVideoTransitionProbeJob = null
        commentPanelProbeJob?.cancel()
        commentPanelProbeJob = null
        nextVideoTransitionProbeJob?.cancel()
        nextVideoTransitionProbeJob = null
        profileSurfaceProbeJob?.cancel()
        profileSurfaceProbeJob = null
        profileWorksAnchorOcrAttempted = false
        nextVideoSurfaceSignatureBeforeSwipe = null
        nextVideoPlayerFingerprintConfirmed = false
        awaitingNextVideoConfirmation = false
        nextVideoCommitted = false
        nextVideoSwipeAttempt = 0
        nextVideoAdvanceReason = ""
        firstScreenCommentRowTopRatio = null
        nextVideoCommentsOpenAuthorized = false
        running = true
        armTimeout("等待下一个用户主页")
    }

    suspend fun onObserved(context: ScreenContext, detection: PageDetection) {
        if (!running || context.packageName != TargetAppLauncher.DOUYIN_PACKAGE) return
        latestObservedContext = context
        observedContextGeneration.incrementAndGet()

        // After advancing to the next video the closing panel still emits accessibility events
        // that look like a valid video surface + comment rail. Ignore observations until the
        // previous panel has closed and the new video's own surface has settled, otherwise the
        // state machine opens comments on the stale rail and fails with "节点已变化".
        if (stateMachine.stage == CommentEntryStage.WAITING_FOR_VIDEO &&
            SystemClock.uptimeMillis() < nextVideoSettleUntilMs
        ) {
            logger.info(
                "comment_next_video_settling",
                attributes = mapOf("remaining_ms" to (nextVideoSettleUntilMs - SystemClock.uptimeMillis())),
            )
            return
        }

        var observation = CommentEntrySignalDetector.observe(
            context,
            skipPinnedVideos = config?.skipPinnedVideos == true,
        )
        if (
            stateMachine.stage == CommentEntryStage.WAITING_FOR_PROFILE &&
            observation.page == PageKind.USER_PROFILE &&
            !observation.profileHasNoWorks &&
            !observation.hasFirstVideoTarget &&
            !profileWorksAnchorOcrAttempted
        ) {
            profileWorksAnchorOcrAttempted = true
            val ocrContext = enrichWithOcr(context)
            observation = CommentEntrySignalDetector.observe(
                ocrContext,
                skipPinnedVideos = config?.skipPinnedVideos == true,
            )
            logger.info(
                "comment_profile_works_anchor_ocr",
                attributes = mapOf(
                    "ocr_blocks" to ocrContext.ocrBlocks.size,
                    "works_anchor" to CommentEntrySignalDetector.hasWorksGridAnchor(ocrContext),
                    "first_video" to observation.hasFirstVideoTarget,
                ),
            )
        }
        if (awaitingNextVideoConfirmation &&
            stateMachine.stage == CommentEntryStage.WAITING_FOR_VIDEO &&
            !nextVideoCommentsOpenAuthorized &&
            observation.page !in NEXT_VIDEO_INTERVENTION_PAGES
        ) {
            logger.info(
                "comment_next_video_observation_held",
                attributes = mapOf(
                    "page" to observation.page.name,
                    "video_surface" to observation.hasVideoSurface,
                    "comment_entry" to observation.hasCommentEntry,
                    "sheet" to observation.isCommentSurfaceReady,
                ),
            )
            return
        }
        if (awaitingCommentSurfaceReturn && observation.isCommentSurfaceReady) {
            confirmedReturnCommentSurface = context
            confirmedReturnCommentSurfaceBackActions = returnCommentSurfaceBackActions
            logger.info(
                "comment_return_surface_event_confirmed",
                attributes = mapOf(
                    "nodes" to context.nodes.size,
                    "ocr_blocks" to context.ocrBlocks.size,
                    "back_actions" to returnCommentSurfaceBackActions,
                ),
            )
        }
        val decision = stateMachine.observe(observation)
        val waitingForProfileContent =
            stateMachine.stage == CommentEntryStage.WAITING_FOR_PROFILE &&
                observation.page == PageKind.USER_PROFILE &&
                decision.action == CommentEntryAction.NONE
        // A genuine video surface without a resolved comment button means the detector's filters
        // dropped an actionable speech-bubble. Dump every semantic/rail candidate so the failing
        // filter (visibility flag, clickable/enabled flag, bounds, or rail cardinality) is visible
        // in the log instead of a silent timeout on the second video after swipe.
        if (
            CommentButtonMissDumpPolicy.shouldDump(
                hasVideoSurface = observation.hasVideoSurface,
                hasCommentEntry = observation.hasCommentEntry,
                commentSurfaceReady = observation.isCommentSurfaceReady,
            )
        ) {
            logCommentButtonMissDiagnostics(context)
        }
        logger.info(
            "comment_entry_observation",
            attributes = mapOf(
                "page" to observation.page.name,
                "stage" to stateMachine.stage.name,
                "works_tab" to (observation.worksTabTarget != null),
                "works_selected" to observation.worksTabSelected,
                "first_video" to observation.hasFirstVideoTarget,
                "no_works" to observation.profileHasNoWorks,
                "video_surface" to observation.hasVideoSurface,
                "comment_entry" to observation.hasCommentEntry,
                "surface_ready" to observation.isCommentSurfaceReady,
                "scroll_pending" to scrollPending,
                "action" to decision.action.name,
            ),
        )
        when (decision.action) {
            CommentEntryAction.EXIT_LIVE_ROOM -> {
                if (liveRoomExitCount >= TuningConstants.CommentRuntime.MAX_LIVE_ROOM_EXITS) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "连续进入直播间且无法安全退出")
                    return
                }
                liveRoomExitCount += 1
                val closeButton = LiveRoomSurfaceDetector.findCloseButton(context)
                val outcome = if (closeButton != null) {
                    clickSnapshot(closeButton, "live_room_close")
                } else {
                    gestures.globalBack()
                }
                logger.info(
                    "live_room_exited",
                    attributes = mapOf("success" to outcome.succeeded, "attempt" to liveRoomExitCount, "route" to outcome.route),
                )
                if (!outcome.succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "退出直播间失败：${outcome.reason}")
                } else {
                    armTimeout("等待直播页面退出")
                }
                return
            }
            CommentEntryAction.SWIPE_LIVE_ROOM -> {
                if (liveRoomSwipeCount >= TuningConstants.CommentRuntime.MAX_LIVE_ROOM_SWIPES) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "连续遇到直播内容，已达到安全划走上限")
                    return
                }
                liveRoomSwipeCount += 1
                val outcome = gestures.swipeNormalized(
                    // Stay outside the expanded floating overlay and away from the right-side
                    // live controls while crossing the feed surface.
                    startX = 0.86f,
                    startY = 0.84f,
                    endX = 0.86f,
                    endY = 0.22f,
                    durationMs = TuningConstants.CommentRuntime.LIVE_ROOM_SWIPE_DURATION_MS,
                )
                logger.info(
                    "live_room_swiped",
                    attributes = mapOf("success" to outcome.succeeded, "attempt" to liveRoomSwipeCount),
                )
                if (!outcome.succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "划走直播内容失败：${outcome.reason}")
                } else {
                    armTimeout("等待直播内容划走")
                }
                return
            }
            CommentEntryAction.PAUSE_FOR_MANUAL_HANDOFF -> {
                terminal(CommentRuntimeTerminal.Outcome.PAUSED, decision.reason)
                return
            }

            CommentEntryAction.STOP -> {
                terminal(CommentRuntimeTerminal.Outcome.FAILED, decision.reason)
                return
            }

            CommentEntryAction.SKIP_PROFILE -> {
                if (config?.entryMode == CommentPrivateMessageEntryMode.CURRENT_PROFILE) {
                    terminal(CommentRuntimeTerminal.Outcome.COMPLETED, decision.reason)
                    return
                }
                val backedOut = gestures.globalBack().succeeded
                if (!backedOut) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "跳过无作品或私密账号时无法返回用户列表")
                    return
                }
                delay(TuningConstants.CommentRuntime.RETURN_TO_COMMENT_DELAY_MS)
                terminal(CommentRuntimeTerminal.Outcome.SKIPPED_PROFILE, decision.reason)
                return
            }

            CommentEntryAction.OPEN_WORKS_TAB -> {
                val target = observation.worksTabTarget
                    ?: return terminal(CommentRuntimeTerminal.Outcome.FAILED, "未找到作品标签")
                val outcome = clickSnapshot(target, "comment_open_works_tab")
                if (!outcome.succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "切换作品标签失败：${outcome.reason}")
                    return
                }
                armTimeout("等待作品标签内容")
                return
            }

            CommentEntryAction.DISMISS_WORKS_SORT -> {
                val target = observation.worksSortLatestTarget
                    ?: return terminal(CommentRuntimeTerminal.Outcome.FAILED, "未找到作品排序菜单的最新选项")
                val outcome = clickSnapshot(target, "comment_select_latest_works")
                if (!outcome.succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "确认最新作品失败：${outcome.reason}")
                    return
                }
                // On some builds “最新” is already selected. The row reports a successful
                // accessibility click but the transient popup stays focused; re-sample it and
                // close only that popup, never the underlying profile.
                delay(TuningConstants.CommentRuntime.WORKS_SORT_SETTLE_MS)
                val popupStillVisible = currentContext()?.let { latestContext ->
                    CommentEntrySignalDetector.observe(
                        latestContext,
                        skipPinnedVideos = config?.skipPinnedVideos == true,
                    ).worksSortLatestTarget != null
                } == true
                if (popupStillVisible && !gestures.globalBack().succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "作品排序菜单无法关闭")
                    return
                }
                armTimeout("等待最新作品列表")
                return
            }

            CommentEntryAction.OPEN_FIRST_VIDEO -> {
                var target = observation.firstVideoTarget
                    ?: return terminal(CommentRuntimeTerminal.Outcome.FAILED, "未找到第一个视频入口")
                var targetContext = context
                // A pinned badge is commonly custom-rendered and absent from the profile tree.
                // When the operator asked to skip pinned works, take one bounded OCR sample
                // before the first work click and feed it only into the existing pinned-tile
                // geometry filter. The selected action remains an accessibility node.
                if (config?.skipPinnedVideos == true && context.ocrBlocks.isEmpty()) {
                    val ocrContext = enrichWithOcr(context)
                    val ocrObservation = CommentEntrySignalDetector.observe(
                        ocrContext,
                        skipPinnedVideos = true,
                    )
                    val ocrTarget = ocrObservation.firstVideoTarget
                    logger.info(
                        "comment_first_video_pinned_ocr_reselect",
                        attributes = mapOf(
                            "before_top" to target.normalizedBounds(context.screenSize).top,
                            "after_top" to (ocrTarget?.normalizedBounds(ocrContext.screenSize)?.top ?: -1f),
                            "ocr_blocks" to ocrContext.ocrBlocks.size,
                        ),
                    )
                    if (ocrTarget == null) {
                        return terminal(
                            CommentRuntimeTerminal.Outcome.FAILED,
                            "置顶视频过滤后未找到可安全打开的首个作品",
                        )
                    }
                    target = ocrTarget
                    targetContext = ocrContext
                }
                val tile = target.normalizedBounds(targetContext.screenSize)
                logger.info(
                    "comment_first_video_selected",
                    attributes = mapOf(
                        "skip_pinned" to (config?.skipPinnedVideos == true),
                        "top" to tile.top,
                        "left" to tile.left,
                    ),
                )
                val outcome = clickSnapshot(target, "comment_first_video")
                if (!outcome.succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "打开第一个视频失败：${outcome.reason}")
                    return
                }
                // A custom-rendered video surface can expose its safe right-side comment rail
                // only after its first media/layout pass. Keep the wait bounded, but give that
                // real transition longer than an ordinary button click.
                armTimeout("等待视频页面", timeoutMs = TuningConstants.CommentRuntime.VIDEO_PAGE_TIMEOUT_MS)
                scheduleFirstVideoTransitionProbe()
                return
            }

            CommentEntryAction.OPEN_COMMENTS -> {
                val target = observation.commentButton
                    ?: return terminal(CommentRuntimeTerminal.Outcome.FAILED, "未找到评论按钮")
                val outcome = clickCommentButton(target, "comment_open_panel")
                if (!outcome.succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "打开评论区失败：${outcome.reason}")
                    return
                }
                armTimeout("等待评论区")
                scheduleCommentPanelProbe()
                return
            }

            CommentEntryAction.READ_COMMENTS -> {
                processCommentViewportOnce(context)
                return
            }

            CommentEntryAction.NONE -> Unit
        }

        // The profile shell is verified but has not exposed a thumbnail or an explicit empty
        // state yet. Do not click from the incomplete tree. A bounded direct probe is enough to
        // catch the Works grid's late layout pass on current Douyin builds, including when no
        // follow-up accessibility callback is emitted.
        if (waitingForProfileContent) {
            scheduleProfileSurfaceProbe()
        }

        // Once the state machine has crossed the comment-surface post-condition, subsequent
        // content-change events are pagination observations rather than another click request.
        // The marker-based surface detector can degrade after a scroll: the panel header and the
        // “回复” labels leave the accessibility tree while the comment list itself stays open. A
        // scroll this runtime issued is therefore accepted as equivalent evidence that the panel
        // is still the active surface, so the bounded read loop does not idle into the pagination
        // watchdog merely because the header markers scrolled away.
        if (stateMachine.stage == CommentEntryStage.READY_TO_READ &&
            (observation.isCommentSurfaceReady || scrollPending)
        ) {
            val fingerprint = viewportFingerprint(context)
            if (scrollPending && fingerprint == lastViewportFingerprint) {
                // The scroll completed but the viewport fingerprint is unchanged: the list has
                // ended, or the footer needs one more settle beat. Detect the end marker directly
                // instead of letting the pagination watchdog expire into a spurious FAILED, and
                // retry the scroll a bounded number of times before concluding the list ended.
                val end = CommentPanelEndDetector.detect(context)
                if (end.reached) {
                    advanceAfterVideo("评论区已读取到底部：${end.marker.orEmpty()}")
                } else if (staleScrollCount >= TuningConstants.CommentRuntime.MAX_STALE_SCROLLS) {
                    advanceAfterVideo("评论区连续滚动后内容未更新")
                } else {
                    staleScrollCount += 1
                    logger.info(
                        "comment_scroll_stale",
                        attributes = mapOf("stale_scrolls" to staleScrollCount, "scroll_count" to scrollCount),
                    )
                    val outcome = scrollCommentPanel(context)
                    if (!outcome.succeeded) {
                        terminal(CommentRuntimeTerminal.Outcome.FAILED, "滚动评论区失败：${outcome.reason}")
                    } else {
                        lastViewportFingerprint = fingerprint
                        scrollCount += 1
                        armTimeout("等待评论区下一页")
                    }
                }
                return
            }
            scrollPending = false
            staleScrollCount = 0
            processCommentViewportOnce(context)
        } else if (detection.kind == PageKind.HUMAN_INTERVENTION || detection.kind == PageKind.LOGIN) {
            terminal(CommentRuntimeTerminal.Outcome.PAUSED, "抖音出现需要人工处理的页面")
        }
    }

    private suspend fun processCommentViewportOnce(context: ScreenContext) {
        // A4: instead of tryLock()'s drop-on-contention, the newest viewport is parked and drained
        // by a single serial consumer. A viewport that actually carried the next loaded comment is
        // therefore never lost to a mid-pass accessibility-event burst.
        if (viewportMergeJob?.isActive == true) {
            pendingViewportContext = context
            logger.info(
                "comment_viewport_merged",
                attributes = mapOf("nodes" to context.nodes.size, "ocr_blocks" to context.ocrBlocks.size),
            )
            return
        }
        viewportMergeJob = scope.launch {
            var viewport: ScreenContext? = context
            try {
                viewportMutex.withLock {
                    while (running && viewport != null) {
                        processCommentViewport(viewport)
                        viewport = pendingViewportContext
                        pendingViewportContext = null
                    }
                }
            } finally {
                pendingViewportContext = null
                viewportMergeJob = null
            }
        }
    }

    /**
     * Some Douyin profile grids acknowledge the thumbnail click but omit the activity/content
     * event for the detail page. Verify the live tree directly so the runner can continue
     * without idling until its watchdog. A retry is permitted only after every bounded sample
     * still proves that the same profile grid remains open.
     */
    private fun scheduleFirstVideoTransitionProbe() {
        firstVideoTransitionProbeJob?.cancel()
        firstVideoTransitionProbeJob = scope.launch {
            var ocrProbeCount = 0
            var actionRailWatchdogReplaced = false
            repeat(TuningConstants.CommentRuntime.FIRST_VIDEO_TRANSITION_PROBE_ATTEMPTS) { attempt ->
                delay(
                    if (attempt == 0) TuningConstants.CommentRuntime.FIRST_VIDEO_TRANSITION_INITIAL_DELAY_MS
                    else TuningConstants.CommentRuntime.FIRST_VIDEO_TRANSITION_PROBE_INTERVAL_MS,
                )
                if (!running || stateMachine.stage != CommentEntryStage.WAITING_FOR_VIDEO) {
                    return@launch
                }
                var context = currentContext() ?: return@repeat
                var observation = CommentEntrySignalDetector.observe(
                    context,
                    skipPinnedVideos = config?.skipPinnedVideos == true,
                )
                val actionRailProbe = NextVideoTransitionProbePolicy.shouldProbeActionRail(
                    page = observation.page,
                    hasVideoSurface = observation.hasVideoSurface,
                    hasCommentEntry = observation.hasCommentEntry,
                    changedFromPreviousVideo = false,
                    ocrProbeCount = ocrProbeCount,
                    ocrProbeLimit = TuningConstants.CommentRuntime.NEXT_VIDEO_OCR_PROBE_LIMIT,
                    sheetOpen = CommentSurfaceDetector.detect(context).isCommentSurface,
                )
                if (actionRailProbe) {
                    ocrProbeCount += 1
                    if (!actionRailWatchdogReplaced) {
                        actionRailWatchdogReplaced = true
                        armTimeout(
                            "等待首视频动作栏验证",
                            timeoutMs = TuningConstants.CommentRuntime.VIDEO_PAGE_TIMEOUT_MS,
                        )
                    }
                    context = enrichWithOcr(context)
                    observation = CommentEntrySignalDetector.observe(
                        context,
                        skipPinnedVideos = config?.skipPinnedVideos == true,
                    )
                }
                logger.info(
                    "comment_first_video_postcondition",
                    attributes = mapOf(
                        "attempt" to attempt + 1,
                        "page" to observation.page.name,
                        "video_surface" to observation.hasVideoSurface,
                        "comment_entry" to observation.hasCommentEntry,
                        "action_rail_ocr" to actionRailProbe,
                        "ocr_blocks" to context.ocrBlocks.size,
                    ),
                )
                if (observation.hasVideoSurface && observation.hasCommentEntry) {
                    onObserved(context, pageDetector.detect(context))
                    return@launch
                }
                if (attempt == TuningConstants.CommentRuntime.FIRST_VIDEO_TRANSITION_PROBE_ATTEMPTS - 1 &&
                    observation.page == PageKind.USER_PROFILE &&
                    observation.firstVideoTarget != null
                ) {
                    val retry = clickSnapshot(observation.firstVideoTarget, "comment_first_video_retry")
                    logger.info(
                        "comment_first_video_retry",
                        attributes = mapOf("success" to retry.succeeded, "route" to retry.route),
                    )
                    if (!retry.succeeded) {
                        terminal(CommentRuntimeTerminal.Outcome.FAILED, "首个视频点击后仍停留主页且重试失败：${retry.reason.orEmpty()}")
                    } else {
                        armTimeout("等待视频页面", timeoutMs = TuningConstants.CommentRuntime.VIDEO_PAGE_TIMEOUT_MS)
                    }
                }
            }
        }
    }

    /**
     * A feed swipe can move to a fully rendered video without dispatching a content-change event.
     * Probe the target window after the old comment sheet's settle window. The baseline check
     * prevents the just-closed video's action rail from being treated as a new item, so this
     * never reopens comments merely because the panel animation is late.
     */
    private fun scheduleNextVideoTransitionProbe() {
        nextVideoTransitionProbeJob?.cancel()
        nextVideoTransitionProbeJob = scope.launch {
            var hiddenEntryObservations = 0
            var controlsRevealAttempted = false
            var ocrProbeCount = 0
            var actionRailWatchdogReplaced = false
            // Kept inside one post-swipe probe so an old video's matching coordinates cannot
            // promote a new screenshot by themselves.
            var previousCommentIconTemplateMatch: CommentIconTemplateMatch? = null
            var previousActionRailAnchorTemplateMatch: ActionRailAnchorTemplateMatch? = null
            var consecutiveSheetOpen = 0
            var changedPlayerSamples = NextVideoAdvancePolicy.ChangedPlayerSignatureSamples(
                signature = null,
                count = 0,
            )
            repeat(TuningConstants.CommentRuntime.NEXT_VIDEO_TRANSITION_PROBE_ATTEMPTS) { attempt ->
                val settleRemaining = (nextVideoSettleUntilMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
                delay(
                    when {
                        settleRemaining > 0L ->
                            settleRemaining +
                                if (attempt == 0) {
                                    TuningConstants.CommentRuntime.NEXT_VIDEO_TRANSITION_INITIAL_GRACE_MS
                                } else {
                                    0L
                                }
                        attempt == 0 -> TuningConstants.CommentRuntime.NEXT_VIDEO_TRANSITION_INITIAL_GRACE_MS
                        else -> TuningConstants.CommentRuntime.NEXT_VIDEO_TRANSITION_PROBE_INTERVAL_MS
                    },
                )
                if (!running || stateMachine.stage != CommentEntryStage.WAITING_FOR_VIDEO) {
                    return@launch
                }
                var context = currentContext() ?: return@repeat
                var observation = CommentEntrySignalDetector.observe(
                    context,
                    skipPinnedVideos = config?.skipPinnedVideos == true,
                )
                val sheetOpen = CommentSurfaceDetector.detect(context).isCommentSurface
                // Prove the page changed from the raw post-swipe tree. OCR adds blocks to the
                // context and must not manufacture the fingerprint difference that authorizes
                // the UNKNOWN-only diagnostic probe.
                val signature = videoSurfaceFingerprint(context)
                changedPlayerSamples = NextVideoAdvancePolicy.nextChangedPlayerSignatureSamples(
                    beforeSwipeSignature = nextVideoSurfaceSignatureBeforeSwipe,
                    currentSignature = signature,
                    sheetOpen = sheetOpen,
                    previous = changedPlayerSamples,
                )
                val fingerprintChanged = changedPlayerSamples.signature != null
                val fingerprintChangedStable =
                    NextVideoAdvancePolicy.isChangedPlayerSignatureStable(changedPlayerSamples)
                // Direct tree reads are intentionally cheap, but they contain no OCR blocks.
                // A profile-detail player can temporarily be classified as HOME after the
                // verified next-video swipe, even while its right rail is visibly rendered. A
                // changed UNKNOWN detail surface has the same failure mode. Capture at most two
                // bounded action-rail samples, then re-run the same strict detector against the
                // enriched snapshot. This gate does not classify the page or authorize a tap.
                val actionRailProbeWillRun = NextVideoTransitionProbePolicy.shouldProbeActionRail(
                    page = observation.page,
                    hasVideoSurface = observation.hasVideoSurface,
                    hasCommentEntry = observation.hasCommentEntry,
                    changedFromPreviousVideo = fingerprintChanged,
                    ocrProbeCount = ocrProbeCount,
                    ocrProbeLimit = TuningConstants.CommentRuntime.NEXT_VIDEO_OCR_PROBE_LIMIT,
                    sheetOpen = sheetOpen,
                )
                if (NextVideoTransitionProbePolicy.shouldReplaceSwipeWatchdog(
                        actionRailProbeWillRun = actionRailProbeWillRun,
                        watchdogAlreadyReplaced = actionRailWatchdogReplaced,
                    )
                ) {
                    actionRailWatchdogReplaced = true
                    // The old timeout starts at swipe dispatch. Replace it before the first
                    // potentially expensive OCR/template pass so both bounded frames can finish.
                    armTimeout(
                        "等待下一视频动作栏验证",
                        timeoutMs = TuningConstants.CommentRuntime.VIDEO_PAGE_TIMEOUT_MS,
                    )
                    logger.info(
                        "comment_next_video_action_rail_watchdog_replaced",
                        attributes = mapOf(
                            "attempt" to (attempt + 1),
                            "page" to observation.page.name,
                            "changed" to fingerprintChanged,
                            "sheet_open" to sheetOpen,
                        ),
                    )
                }
                if (actionRailProbeWillRun) {
                    ocrProbeCount += 1
                    context = enrichWithOcr(context)
                    val currentTemplateMatch = context.commentIconTemplateMatch
                    val templateConfirmed = CommentIconTemplateStabilityPolicy.confirms(
                        previous = previousCommentIconTemplateMatch,
                        current = currentTemplateMatch,
                        screenSize = context.screenSize,
                    )
                    val currentActionRailAnchors = context.actionRailAnchorTemplateMatch
                    val dualAnchorConfirmed = ActionRailAnchorTemplateStabilityPolicy.confirms(
                        previous = previousActionRailAnchorTemplateMatch,
                        current = currentActionRailAnchors,
                        screenSize = context.screenSize,
                    )
                    if (currentTemplateMatch != null) {
                        logger.info(
                            "comment_icon_template_confirmation",
                            attributes = mapOf(
                                "attempt" to (attempt + 1),
                                "confirmed" to templateConfirmed,
                                "confidence" to currentTemplateMatch.confidence,
                            ),
                        )
                        previousCommentIconTemplateMatch = currentTemplateMatch
                        context = context.copy(
                            commentIconTemplateMatch = currentTemplateMatch.copy(
                                isConfirmed = templateConfirmed,
                            ),
                        )
                    } else {
                        previousCommentIconTemplateMatch = null
                    }
                    if (currentActionRailAnchors != null) {
                        logger.info(
                            "action_rail_dual_anchor_confirmation",
                            attributes = mapOf(
                                "attempt" to (attempt + 1),
                                "confirmed" to dualAnchorConfirmed,
                                "like_confidence" to currentActionRailAnchors.likeConfidence,
                                "collect_confidence" to currentActionRailAnchors.collectConfidence,
                            ),
                        )
                        previousActionRailAnchorTemplateMatch = currentActionRailAnchors
                        context = context.copy(
                            actionRailAnchorTemplateMatch = currentActionRailAnchors.copy(
                                isConfirmed = dualAnchorConfirmed,
                            ),
                        )
                    } else {
                        previousActionRailAnchorTemplateMatch = null
                    }
                    observation = CommentEntrySignalDetector.observe(
                        context,
                        skipPinnedVideos = config?.skipPinnedVideos == true,
                    )
                }
                val sheetOpenNow = CommentSurfaceDetector.detect(context).isCommentSurface
                if (sheetOpenNow) {
                    consecutiveSheetOpen += 1
                } else {
                    consecutiveSheetOpen = 0
                }
                val readyToOpen = NextVideoAdvancePolicy.shouldOpenCommentsAfterSwipe(
                    sheetOpen = sheetOpenNow,
                    hasVideoSurface = observation.hasVideoSurface,
                    hasCommentEntry = observation.hasCommentEntry,
                )
                if (fingerprintChangedStable) {
                    nextVideoPlayerFingerprintConfirmed = true
                }
                val confirmedReadyToOpen = NextVideoAdvancePolicy.shouldOpenCommentsAfterChangedPlayer(
                    playerFingerprintChanged = fingerprintChanged,
                    sheetOpen = sheetOpenNow,
                    hasVideoSurface = observation.hasVideoSurface,
                    hasCommentEntry = observation.hasCommentEntry,
                )
                logger.info(
                    "comment_next_video_postcondition",
                    attributes = mapOf(
                        "attempt" to (attempt + 1),
                        "page" to observation.page.name,
                        "video_surface" to observation.hasVideoSurface,
                        "comment_entry" to observation.hasCommentEntry,
                        "changed" to fingerprintChanged,
                        "changed_stable" to fingerprintChangedStable,
                        "changed_samples" to changedPlayerSamples.count,
                        "sheet_open" to sheetOpenNow,
                        "ready_to_open" to readyToOpen,
                        "confirmed_ready_to_open" to confirmedReadyToOpen,
                        "swipe_attempt" to nextVideoSwipeAttempt,
                        "ocr_blocks" to context.ocrBlocks.size,
                        "template_confirmed" to (context.commentIconTemplateMatch?.isConfirmed == true),
                        "dual_anchor_confirmed" to (context.actionRailAnchorTemplateMatch?.isConfirmed == true),
                    ),
                )
                if (observation.page in NEXT_VIDEO_INTERVENTION_PAGES) {
                    onObserved(context, pageDetector.detect(context))
                    return@launch
                }
                if (NextVideoAdvancePolicy.shouldRetrySwipe(
                        confirmed = readyToOpen,
                        consecutiveSheetOpen = consecutiveSheetOpen,
                        sheetOpenRetryThreshold = TuningConstants.CommentRuntime.NEXT_VIDEO_SHEET_OPEN_RETRY_THRESHOLD,
                        swipeAttempt = nextVideoSwipeAttempt,
                        maxSwipeAttempts = TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_MAX_ATTEMPTS,
                    )
                ) {
                    if (!retryNextVideoSwipe()) {
                        terminal(CommentRuntimeTerminal.Outcome.FAILED, "未能确认切换到下一条视频")
                        return@launch
                    }
                    consecutiveSheetOpen = 0
                    hiddenEntryObservations = 0
                    controlsRevealAttempted = false
                    ocrProbeCount = 0
                    previousCommentIconTemplateMatch = null
                    previousActionRailAnchorTemplateMatch = null
                    return@repeat
                }
                if (readyToOpen) {
                    // The rail can auto-hide before a second fingerprint frame arrives. A raw
                    // changed closed-player tree plus this fresh safe entry authorizes opening;
                    // accounting remains deferred until the sheet itself is confirmed.
                    if (!confirmedReadyToOpen) return@repeat
                    nextVideoPlayerFingerprintConfirmed = true
                    nextVideoCommentsOpenAuthorized = true
                    armTimeout("等待下一视频评论入口处理")
                    onObserved(context, pageDetector.detect(context))
                    return@launch
                }
                if (
                    NextVideoAdvancePolicy.shouldRevealHiddenEntryAfterConfirmedPlayer(
                        playerChangedConfirmed = nextVideoPlayerFingerprintConfirmed,
                        sheetOpen = sheetOpenNow,
                        hasCommentEntry = observation.hasCommentEntry,
                    )
                ) {
                    hiddenEntryObservations += 1
                    // Persisting a full node dump is intentionally expensive. Capture it once
                    // per inaccessible video so it remains useful for triage without consuming
                    // most of the bounded next-video watchdog.
                    if (hiddenEntryObservations == 1) {
                        logCommentButtonMissDiagnostics(context)
                    }
                    // The profile-player can auto-hide its right action rail after a feed
                    // swipe. Its visible SurfaceView is a neutral media canvas, not a social
                    // action: touch its centre once only to reveal the existing controls. This
                    // never targets the heart, comment text, location card, or any user row.
                    if (!controlsRevealAttempted) {
                        controlsRevealAttempted = true
                        val surface = neutralVideoSurface(context)
                        if (surface != null) {
                            val reveal = gestures.tapBounds(surface.bounds)
                            logger.info(
                                "comment_video_controls_reveal",
                                attributes = mapOf("success" to reveal.succeeded, "route" to reveal.route),
                            )
                            if (reveal.succeeded) {
                                // The action rail can be absent until this neutral media-canvas
                                // tap. The previous watchdog was spent on bounded OCR evidence;
                                // give exactly one fresh action-rail verification window after
                                // the reveal, without adding another swipe or control tap.
                                armTimeout(
                                    "等待下一视频动作栏显示",
                                    timeoutMs = TuningConstants.CommentRuntime.VIDEO_PAGE_TIMEOUT_MS,
                                )
                            }
                            delay(TuningConstants.CommentRuntime.VIDEO_CONTROLS_REVEAL_SETTLE_MS)
                        }
                    }
                    // A genuine video that still does not expose a safe comment entry after
                    // the one neutral reveal is not actionable. Treat it like the documented
                    // no-comment case and move forward; opening arbitrary invisible controls
                    // would be riskier than skipping this video.
                    if (hiddenEntryObservations >= TuningConstants.CommentRuntime.NEXT_VIDEO_HIDDEN_ENTRY_LIMIT) {
                        logger.info(
                            "comment_next_video_skipped_no_safe_entry",
                            attributes = mapOf("video_index" to videoIndex, "observations" to hiddenEntryObservations),
                        )
                        advanceAfterVideo("当前视频未提供可验证评论入口，已跳过", closeCommentSheet = false)
                        return@launch
                    }
                }
            }
            if (running && awaitingNextVideoConfirmation && !nextVideoCommitted) {
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "未能确认切换到下一条视频")
            }
        }
    }

    /**
     * Reads the current verified profile a few times after its shell appeared without a Works
     * tile. This is intentionally read-only until the ordinary state machine sees a structural
     * first-video target; it never turns an unrecognized row, label, or right-side action into a
     * click target.
     */
    private fun scheduleProfileSurfaceProbe() {
        if (profileSurfaceProbeJob?.isActive == true) return
        armTimeout("等待用户主页内容稳定", timeoutMs = TuningConstants.CommentRuntime.PROFILE_CONTENT_TIMEOUT_MS)
        profileSurfaceProbeJob = scope.launch {
            repeat(TuningConstants.CommentRuntime.PROFILE_SURFACE_PROBE_ATTEMPTS) { attempt ->
                delay(
                    if (attempt == 0) TuningConstants.CommentRuntime.PROFILE_SURFACE_PROBE_INITIAL_DELAY_MS
                    else TuningConstants.CommentRuntime.PROFILE_SURFACE_PROBE_INTERVAL_MS,
                )
                if (!running || stateMachine.stage != CommentEntryStage.WAITING_FOR_PROFILE) {
                    return@launch
                }
                val context = currentContext() ?: return@repeat
                val detection = pageDetector.detect(context)
                val observation = CommentEntrySignalDetector.observe(
                    context,
                    skipPinnedVideos = config?.skipPinnedVideos == true,
                )
                logger.info(
                    "comment_profile_surface_probe",
                    attributes = mapOf(
                        "attempt" to (attempt + 1),
                        "page" to detection.kind.name,
                        "first_video" to observation.hasFirstVideoTarget,
                        "no_works" to observation.profileHasNoWorks,
                    ),
                )
                when (detection.kind) {
                    PageKind.USER_PROFILE,
                    PageKind.HUMAN_INTERVENTION,
                    PageKind.LOGIN,
                    -> onObserved(context, detection)

                    // A transient custom-rendered tree is not evidence that the profile has
                    // changed. Keep sampling it rather than pausing or guessing a click target.
                    else -> Unit
                }
                if (!running || stateMachine.stage != CommentEntryStage.WAITING_FOR_PROFILE) {
                    return@launch
                }
            }
            if (running && stateMachine.stage == CommentEntryStage.WAITING_FOR_PROFILE) {
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "用户主页未在限定时间内出现可处理的视频入口")
            }
        }
    }

    /**
     * The comment sheet is custom-rendered on current Douyin builds and commonly omits the
     * content-change callback after a successful comment-rail click. Poll the same live tree a
     * few times and feed a confirmed panel back through the ordinary state machine.
     */
    private fun scheduleCommentPanelProbe() {
        commentPanelProbeJob?.cancel()
        commentPanelProbeJob = scope.launch {
            var coordinateRetryDecided = false
            repeat(TuningConstants.CommentRuntime.COMMENT_PANEL_PROBE_ATTEMPTS) { attempt ->
                delay(
                    if (attempt == 0) TuningConstants.CommentRuntime.COMMENT_PANEL_PROBE_INITIAL_DELAY_MS
                    else TuningConstants.CommentRuntime.COMMENT_PANEL_PROBE_INTERVAL_MS,
                )
                if (!running || stateMachine.stage != CommentEntryStage.WAITING_FOR_COMMENTS) {
                    return@launch
                }
                val context = currentContext() ?: return@repeat
                val surface = CommentSurfaceDetector.detect(context)
                logger.info(
                    "comment_panel_postcondition",
                    attributes = mapOf(
                        "attempt" to attempt + 1,
                        "ready" to surface.isCommentSurface,
                        "confidence" to surface.confidence,
                    ),
                )
                if (surface.isCommentSurface) {
                    onObserved(context, pageDetector.detect(context))
                    return@launch
                }
                // Some current Douyin player renderers acknowledge ACTION_CLICK on the compact
                // speech-bubble node without dispatching it to the visual player.  After two
                // ordinary post-condition reads, retry exactly once through the bounds of a
                // freshly resolved, safety-checked right-rail node.  Reusing the recovery policy
                // keeps this out of the comment sheet's “评论 / AI解析” tabs, location rows,
                // reaction controls, and comment content.
                if (!coordinateRetryDecided && attempt + 1 == TuningConstants.CommentRuntime.COMMENT_PANEL_NODE_BOUNDS_RETRY_ATTEMPT) {
                    coordinateRetryDecided = true
                    val currentTarget = CommentEntrySignalDetector.observe(
                        context,
                        skipPinnedVideos = config?.skipPinnedVideos == true,
                    ).commentButton
                    val retryTarget = CommentPanelRecoveryPolicy.reopenTarget(
                        surface = surface,
                        target = currentTarget,
                        screenSize = context.screenSize,
                    )
                    if (retryTarget != null) {
                        val outcome = gestures.tapBounds(retryTarget.node.bounds)
                        logger.info(
                            "comment_panel_bounds_retry",
                            attributes = mapOf(
                                "success" to outcome.succeeded,
                                "route" to outcome.route,
                                "bounds" to retryTarget.node.bounds.toString(),
                            ),
                        )
                    } else {
                        logger.info("comment_panel_bounds_retry_suppressed")
                    }
                }
            }
        }
    }

    private suspend fun processCommentViewport(context: ScreenContext) {
        // A viewport parked by the merge consumer can still drain after advanceAfterVideo re-arms
        // the route to WAITING_FOR_VIDEO. Only a confirmed comment panel (READY_TO_READ) may feed
        // candidates, so a stale viewport from the closing old panel is never re-processed.
        if (stateMachine.stage != CommentEntryStage.READY_TO_READ) {
            logger.info(
                "comment_viewport_skipped",
                attributes = mapOf("stage" to stateMachine.stage.name),
            )
            return
        }
        timeoutJob?.cancel()
        val end = CommentPanelEndDetector.detect(context)
        if (end.reached) {
            if (awaitingNextVideoConfirmation) {
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "未能确认切换到下一条视频")
                return
            }
            advanceAfterVideo("评论区已读取到底部：${end.marker.orEmpty()}")
            return
        }

        val terms = config?.matchKeywords.orEmpty()
        val matchMode = CommentKeywordMatchMode.ANY
        val extraction = CommentCandidateExtractor.extract(context, terms, matchMode)
        reportMatchStatistics(context, extraction)
        if (
            NextVideoAdvancePolicy.shouldEvaluateNextVideoViewportGate(
                awaitingConfirmation = awaitingNextVideoConfirmation,
                commentListScrollCount = scrollCount,
            )
        ) {
            if (nextVideoPlayerFingerprintConfirmed) {
                logger.info(
                    "comment_next_video_viewport_gate",
                    attributes = mapOf(
                        "first_screen" to true,
                        "source" to "changed_player_with_safe_entry",
                    ),
                )
                commitNextVideoAccounting()
            } else {
            val height = context.screenSize.height
            val unprocessed = extraction.candidates.filterNot { candidate ->
                processedCandidateKeys.contains(candidate.identityKey) ||
                    processedTaskCandidateLedger.contains(candidate.identityKey)
            }
            val minTop = unprocessed.minOfOrNull(::candidateTop)?.takeIf { it < Int.MAX_VALUE }
            if (minTop == null || height <= 0) {
                logger.info(
                    "comment_next_video_viewport_gate",
                    attributes = mapOf("first_screen" to false, "reason" to "no_new_row"),
                )
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "未能确认切换到下一条视频")
                return
            }
            val minRowTopRatio = minTop.toFloat() / height.toFloat()
            val firstScreen = NextVideoAdvancePolicy.isFirstScreenCommentViewport(
                minRowTopRatio = minRowTopRatio,
                baselineRowTopRatio = firstScreenCommentRowTopRatio,
            )
            logger.info(
                "comment_next_video_viewport_gate",
                attributes = mapOf(
                    "min_row_ratio" to minRowTopRatio,
                    "baseline_ratio" to (firstScreenCommentRowTopRatio ?: -1f),
                    "first_screen" to firstScreen,
                    "unprocessed_count" to unprocessed.size,
                ),
            )
            if (!firstScreen) {
                if (
                    NextVideoAdvancePolicy.shouldRetryAfterContinuationViewport(
                        firstScreen = false,
                        swipeAttempt = nextVideoSwipeAttempt,
                        maxSwipeAttempts = TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_MAX_ATTEMPTS,
                    )
                ) {
                    logger.info(
                        "comment_next_video_continuation_retry",
                        attributes = mapOf(
                            "min_row_ratio" to minRowTopRatio,
                            "swipe_attempt" to nextVideoSwipeAttempt,
                        ),
                    )
                    stateMachine.prepareNextVideo()
                    pendingViewportContext = null
                    if (!retryNextVideoSwipe()) {
                        terminal(CommentRuntimeTerminal.Outcome.FAILED, "未能确认切换到下一条视频")
                        return
                    }
                    armTimeout(
                        "等待下一个视频",
                        timeoutMs = TuningConstants.CommentRuntime.VIDEO_PAGE_TIMEOUT_MS,
                    )
                    scheduleNextVideoTransitionProbe()
                    return
                }
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "未能确认切换到下一条视频")
                return
            }
            commitNextVideoAccounting()
            }
        }
        val firstAvatar = extraction.firstVisibleCommentAvatar
        val firstCandidate = extraction.candidates.firstOrNull()
        val leadingRowVerifiedNonMatching = firstAvatar?.let { anchor ->
            CommentCandidateExtractor.hasVerifiedNonMatchingLeadingRow(
                context = context,
                anchor = anchor,
                matchKeywords = terms,
                matchMode = matchMode,
            )
        } == true
        // The first visible row is a hard safety boundary.  A partial/virtualized first row can
        // leave its avatar in the tree while hiding the paired name or comment text; in that
        // case a simple visual sort would incorrectly choose the next complete row.  Stop
        // instead of skipping the first commenter.  Once that first candidate has already been
        // processed, later candidates remain eligible on the next viewport pass.
        if (firstAvatar != null && firstCandidate != null &&
            !CommentCandidateExtractor.belongsToAvatarRow(firstCandidate, firstAvatar)
        ) {
            // The leading row may be the video author's own comment, but text beside an avatar is
            // not enough proof: it can also belong to a location/search header or an incomplete
            // first comment. Only a visible “作者” badge permits moving on to a later commenter.
            if (CommentCandidateExtractor.hasVideoAuthorBadge(context, firstAvatar)) {
                logger.warn(
                    "comment_leading_author_row_skipped",
                    message = "首条为带“作者”标记的作者本人评论，跳过并处理下一条有效评论",
                    attributes = mapOf(
                        "first_avatar_top" to firstAvatar.top,
                        "first_candidate_top" to candidateTop(firstCandidate),
                        "candidate_count" to extraction.candidates.size,
                    ),
                )
            } else if (leadingRowVerifiedNonMatching) {
                // The leading row is not a candidate because its explicit body does not match
                // the frozen task rule. Its avatar, title and body were nevertheless proven in
                // one Accessibility row, so the next matching row may be handled normally.
                logger.info(
                    "comment_leading_nonmatching_row_verified",
                    attributes = mapOf(
                        "first_avatar_top" to firstAvatar.top,
                        "first_candidate_top" to candidateTop(firstCandidate),
                        "candidate_count" to extraction.candidates.size,
                    ),
                )
            } else {
                // Preserve the exact hierarchy privately before stopping.  This is only reached
                // on the strict no-click safety path and gives us evidence to improve a partial
                // first-row matcher without touching another commenter or a like control.
                saveNodeDiagnostic(context, "comment_first_row_unresolved")
                logger.warn(
                    "comment_first_row_unresolved",
                    message = "首条评论或顶部信息未形成可验证结构；未点击后续评论用户",
                    attributes = mapOf(
                        "first_avatar_top" to firstAvatar.top,
                        "first_candidate_top" to candidateTop(firstCandidate),
                        "candidate_count" to extraction.candidates.size,
                    ),
                )
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "首条评论信息不完整，为避免误点已停止本次安全探测")
                return
            }
        }
        val avatarCandidates = extraction.candidates
            // A real comment candidate must retain a live avatar target. OCR-only text or a
            // location-card text pair is never actionable and must not enter the profile flow.
            .filter { it.avatarBounds != null }
        val currentVideoDuplicateCount = avatarCandidates.count { candidate ->
            processedCandidateKeys.contains(candidate.identityKey)
        }
        val taskLedgerDuplicateCount = avatarCandidates.count { candidate ->
            !processedCandidateKeys.contains(candidate.identityKey) &&
                processedTaskCandidateLedger.contains(candidate.identityKey)
        }
        val newCandidates = avatarCandidates.filterNot { candidate ->
            processedCandidateKeys.contains(candidate.identityKey) ||
                processedTaskCandidateLedger.contains(candidate.identityKey)
        }
        val update = ledger.add(extraction)
        logger.info(
            "comment_viewport_read",
            attributes = mapOf(
                "candidate_count" to extraction.candidates.size,
                "actionable_candidate_count" to avatarCandidates.size,
                "eligible_candidate_count" to newCandidates.size,
                "current_video_duplicate_count" to currentVideoDuplicateCount,
                "task_ledger_duplicate_count" to taskLedgerDuplicateCount,
                // `new_candidate_count` is retained for diagnostic compatibility: it is the
                // viewport ledger delta, before task-level dedupe, not the number that can be
                // safely queued for a private-message attempt.
                "new_candidate_count" to update.added,
                "duplicate_candidate_count" to update.duplicates,
                "ledger_count" to update.total,
                "scroll_count" to scrollCount,
                "first_avatar_top" to (firstAvatar?.top ?: -1),
                "first_candidate_top" to (firstCandidate?.let(::candidateTop) ?: -1),
                "viewport_fingerprint" to viewportFingerprint(context),
            ),
        )

        // Never use a down-scroll as a way to discover the first commenter.  A just-opened
        // RecyclerView can briefly expose the sheet chrome before it exposes any avatar/body
        // rows; scrolling in that gap was the source of apparently random later-row selections.
        // Re-read the same top viewport a few times, then skip this video rather than silently
        // moving past its first commenter.
        if (scrollCount == 0 && processedCandidateKeys.isEmpty() && newCandidates.isEmpty()) {
            if (initialCandidateReadRetryCount < TuningConstants.CommentRuntime.INITIAL_COMMENT_CANDIDATE_READ_RETRIES) {
                initialCandidateReadRetryCount += 1
                logger.info(
                    "comment_initial_viewport_waiting",
                    attributes = mapOf(
                        "attempt" to initialCandidateReadRetryCount,
                        "max_attempts" to TuningConstants.CommentRuntime.INITIAL_COMMENT_CANDIDATE_READ_RETRIES,
                    ),
                )
                delay(TuningConstants.CommentRuntime.INITIAL_COMMENT_CANDIDATE_READ_RETRY_DELAY_MS)
                // A just-confirmed empty or late-rendering comment sheet can temporarily omit
                // the header/composer signatures from its next accessibility tree. That is not
                // evidence that the sheet closed: this coroutine is still reading the same
                // verified top viewport and has not dispatched any navigation action. Prefer a
                // fresh tree when available, but retain the confirmed snapshot for the remaining
                // bounded top-viewport reads. Otherwise a single sparse tree waits for a future
                // callback that some Douyin builds never emit and turns a safely skippable video
                // into the task-level "等待评论区首屏候选" timeout.
                processCommentViewport(currentContext() ?: context)
                // Do not fall through to pagination: the bounded top-viewport reads own this
                // panel until they either find a verified commenter or skip the video safely.
                return
            }
            advanceAfterVideo("评论区首屏没有可验证评论用户，未向下滚动")
            return
        } else if (newCandidates.isNotEmpty()) {
            initialCandidateReadRetryCount = 0
        }

        // Debug-device regression verification may inspect the candidate chosen by the same
        // production extractor without making any external UI action. In particular, this lets
        // us prove that a top location card was ignored before approving a real blank probe.
        if (config?.dryRun == true && newCandidates.isNotEmpty()) {
            val candidate = newCandidates.first()
            logger.info(
                "comment_dry_run_candidate_selected",
                attributes = mapOf(
                    "row_top" to candidateTop(candidate),
                    "avatar_left" to (candidate.avatarBounds?.left ?: -1),
                    "avatar_top" to (candidate.avatarBounds?.top ?: -1),
                    "has_author_text" to (candidate.authorText != null),
                ),
            )
            terminal(CommentRuntimeTerminal.Outcome.COMPLETED, "干跑已验证首位可操作评论候选，未执行点击")
            return
        }

        val maxUsers = config?.maxUsersPerVideo ?: CommentPrivateMessageConfig.DEFAULT_MAX_USERS_PER_VIDEO
        val remaining = (maxUsers - processedCandidateKeys.size).coerceAtLeast(0)
        // Freeze the current viewport into a visual top-to-bottom work queue before entering any
        // profile.  Returning from a profile can emit RecyclerView updates, but those events are
        // merged until this queue drains; consequently the first available commenter is always
        // handled first and no later row can jump the queue.
        val orderedCandidates = newCandidates
            .sortedWith(
                compareBy<CommentUserCandidate> { candidateTop(it) }
                    .thenBy { it.avatarBounds?.left ?: it.authorBounds?.left ?: it.commentBounds.left }
                    .thenBy { it.identityKey },
            )
            .take(remaining)
        if (orderedCandidates.isNotEmpty()) {
            logger.info(
                "comment_candidate_queue_frozen",
                attributes = mapOf(
                    "count" to orderedCandidates.size,
                    "scroll_count" to scrollCount,
                    "row_tops" to orderedCandidates.map(::candidateTop).joinToString(","),
                ),
            )
            rememberFirstScreenCommentRow(context, orderedCandidates)
        }
        for ((queuePosition, candidate) in orderedCandidates.withIndex()) {
            logger.info(
                "comment_candidate_queue_position",
                attributes = mapOf(
                    "position" to queuePosition + 1,
                    "row_top" to candidateTop(candidate),
                    "scroll_count" to scrollCount,
                ),
            )
            processedCandidateKeys += candidate.identityKey
            processedTaskCandidateLedger.markProcessed(candidate.identityKey)?.let(onCommentCandidateProcessed)
            activeCandidate = candidate
            val result = processCommentCandidate(candidate, context)
            activeCandidate = null
            if (!running) return
            if (!result) return
        }
        if (processedCandidateKeys.size >= maxUsers) {
            advanceAfterVideo("已完成当前视频的评论用户上限探测")
            return
        }
        // A RecyclerView-backed comment list keeps recycling rows near the bottom, so the viewport
        // fingerprint can change on every scroll even when no new commenter has appeared, and
        // Douyin sometimes omits the "暂时没有更多了" footer from the accessibility tree. Detect
        // that exhaustion case by counting consecutive scrolls that added zero new candidates,
        // instead of relying only on the fingerprint or the footer marker, so the bounded 5/10/20
        // regression terminates cleanly instead of idling into the pagination watchdog.
        if (update.added > 0) {
            emptyScrollCount = 0
        } else if (scrollCount > 0) {
            emptyScrollCount += 1
            if (emptyScrollCount >= TuningConstants.CommentRuntime.MAX_EMPTY_SCROLLS) {
                advanceAfterVideo("评论区连续滚动后无新增评论用户")
                return
            }
        }
        if (scrollCount >= TuningConstants.CommentRuntime.MAX_COMMENT_SCROLLS) {
            terminal(CommentRuntimeTerminal.Outcome.FAILED, "评论区连续滚动后仍未出现到底提示")
            return
        }

        val outcome = scrollCommentPanel(context)
        if (!outcome.succeeded) {
            terminal(CommentRuntimeTerminal.Outcome.FAILED, "滚动评论区失败：${outcome.reason}")
            return
        }
        lastViewportFingerprint = viewportFingerprint(context)
        scrollPending = true
        scrollCount += 1
        // A scroll that reaches the bottom of the list may never produce another accessibility
        // event: Douyin stops emitting content-change events once the comment list is exhausted,
        // while RecyclerView recycling keeps rewriting the viewport. Waiting for the next
        // observation would therefore idle into the pagination watchdog. Instead, poll the active
        // window directly (bounded) and treat either the end footer or consecutive zero-new-
        // candidate viewports as end-of-list, so the 5/10/20 regression always terminates cleanly.
        var emptyPolls = 0
        while (running && emptyPolls < TuningConstants.CommentRuntime.POST_SCROLL_POLL_ATTEMPTS) {
            delay(TuningConstants.CommentRuntime.POST_SCROLL_POLL_INTERVAL_MS)
            val fresh = currentContext()
            if (fresh == null) break
            val end = CommentPanelEndDetector.detect(fresh)
            if (end.reached) {
                advanceAfterVideo("评论区已读取到底部：${end.marker.orEmpty()}")
                return
            }
            val postScrollExtraction = CommentCandidateExtractor.extract(fresh, terms, matchMode)
            reportMatchStatistics(fresh, postScrollExtraction)
            val added = postScrollExtraction.candidates.count { candidate ->
                !processedCandidateKeys.contains(candidate.identityKey) &&
                    !processedTaskCandidateLedger.contains(candidate.identityKey)
            }
            if (added > 0) {
                logger.info(
                    "comment_post_scroll_ready",
                    attributes = mapOf("added" to added, "scroll_count" to scrollCount),
                )
                scrollPending = false
                staleScrollCount = 0
                processCommentViewport(fresh)
                return
            }
            emptyPolls += 1
            logger.info(
                "comment_post_scroll_empty",
                attributes = mapOf("poll" to emptyPolls, "scroll_count" to scrollCount),
            )
        }
        if (!running) return
        if (emptyPolls >= TuningConstants.CommentRuntime.POST_SCROLL_POLL_ATTEMPTS) {
            advanceAfterVideo("评论区连续滚动后无新增评论用户")
            return
        }
        armTimeout("等待评论区下一页")
    }

    /**
     * Finishes one video and moves to the next one without returning to the profile grid. The
     * comment sheet is closed first, then a single bounded feed swipe selects the next video.
     * The state machine is re-armed only after the previous video has been fully accounted for.
     * Video index and the per-video cap stay unchanged until a closed player fingerprint proves Null-context polls before treating the sheet as closed.
     * the swipe landed on a different video.
     */
    private suspend fun advanceAfterVideo(
        reason: String,
        closeCommentSheet: Boolean = true,
    ) {
        val snapshot = config
            ?: return terminal(CommentRuntimeTerminal.Outcome.FAILED, "评论任务配置已丢失")
        val completedVideo = videoIndex + 1
        if (completedVideo >= snapshot.maxVideos) {
            logger.info(
                "comment_video_batch_completed",
                attributes = mapOf("video_count" to completedVideo, "reason" to reason),
            )
            terminal(CommentRuntimeTerminal.Outcome.COMPLETED, reason)
            return
        }

        timeoutJob?.cancel()
        logger.info(
            "comment_next_video_advance_begin",
            attributes = mapOf(
                "video_index" to videoIndex,
                "close_sheet" to closeCommentSheet,
                "reason" to reason,
                "previous_scroll_count" to scrollCount,
            ),
        )
        // The confirmation gate only inspects the first sheet after the swipe. A leftover
        // comment-list scroll from the video we just finished would skip that gate and
        // immediately re-advance because the old per-video cap is still full.
        val previousScrollCount = scrollCount
        scrollPending = false
        scrollCount = NextVideoAdvancePolicy.commentListScrollCountAfterLeavingVideo()
        staleScrollCount = 0
        emptyScrollCount = 0
        if (previousScrollCount != 0) {
            logger.info(
                "comment_next_video_scroll_reset",
                attributes = mapOf("previous_scroll_count" to previousScrollCount),
            )
        }
        nextVideoCommitted = false
        nextVideoPlayerFingerprintConfirmed = false
        awaitingNextVideoConfirmation = true
        nextVideoCommentsOpenAuthorized = false
        nextVideoSwipeAttempt = 1
        nextVideoAdvanceReason = reason
        if (NextVideoAdvancePolicy.shouldArmWaitingForVideoBeforeSheetClose()) {
            stateMachine.prepareNextVideo()
            pendingViewportContext = null
            logger.info("comment_next_video_close_route_armed")
        }
        if (!prepareClosedPlayerForNextVideoSwipe()) {
            terminal(CommentRuntimeTerminal.Outcome.FAILED, "关闭当前视频评论区失败，无法继续下一个视频")
            return
        }
        val fingerprint = nextVideoSurfaceSignatureBeforeSwipe
        if (fingerprint == null) {
            terminal(CommentRuntimeTerminal.Outcome.FAILED, "关闭评论区后未能确认视频播放器，无法切换下一个视频")
            return
        }

        if (!dispatchNextVideoSwipe(reason)) {
            awaitingNextVideoConfirmation = false
            terminal(CommentRuntimeTerminal.Outcome.FAILED, "切换下一个视频失败")
            return
        }
        armTimeout("等待下一个视频", timeoutMs = TuningConstants.CommentRuntime.VIDEO_PAGE_TIMEOUT_MS)
        scheduleNextVideoTransitionProbe()
    }

    /**
     * Close the comment sheet with at most one BACK, then wait until the player is stably
     * closed (consecutive frames: sheet closed, video surface, comment entry). Do not BACK
     * again from a one-frame sheet_open flicker.
     */
    private suspend fun prepareClosedPlayerForNextVideoSwipe(): Boolean {
        var backDispatched = false
        var consecutiveClosed = 0
        repeat(TuningConstants.CommentRuntime.NEXT_VIDEO_SHEET_CLOSE_POLL_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                delay(TuningConstants.CommentRuntime.NEXT_VIDEO_SHEET_CLOSE_POLL_INTERVAL_MS)
            }
            val context = currentContext()
            if (context == null) {
                consecutiveClosed = 0
                logger.info(
                    "comment_next_video_sheet_close_poll",
                    attributes = mapOf(
                        "attempt" to (attempt + 1),
                        "open" to "null_context",
                        "closed_samples" to consecutiveClosed,
                    ),
                )
                return@repeat
            }
            val sheetOpen = CommentSurfaceDetector.detect(context).isCommentSurface
            consecutiveClosed = NextVideoAdvancePolicy.nextClosedSampleCount(sheetOpen, consecutiveClosed)
            val observation = CommentEntrySignalDetector.observe(
                context,
                skipPinnedVideos = config?.skipPinnedVideos == true,
            )
            logger.info(
                "comment_next_video_sheet_close_poll",
                attributes = mapOf(
                    "attempt" to (attempt + 1),
                    "open" to sheetOpen,
                    "closed_samples" to consecutiveClosed,
                    "video_surface" to observation.hasVideoSurface,
                    "comment_entry" to observation.hasCommentEntry,
                    "back_dispatched" to backDispatched,
                ),
            )
            if (
                NextVideoAdvancePolicy.isStableClosedPlayer(
                    consecutiveClosedSamples = consecutiveClosed,
                    requiredSamples = TuningConstants.CommentRuntime.NEXT_VIDEO_CLOSED_PLAYER_STABLE_SAMPLES,
                    sheetOpen = sheetOpen,
                    hasVideoSurface = observation.hasVideoSurface,
                    hasCommentEntry = observation.hasCommentEntry,
                )
            ) {
                val fingerprint = videoSurfaceFingerprint(context)
                if (fingerprint != null) {
                    nextVideoSurfaceSignatureBeforeSwipe = fingerprint
                    logger.info(
                        "comment_next_video_closed_player_stable",
                        attributes = mapOf(
                            "attempt" to (attempt + 1),
                            "closed_samples" to consecutiveClosed,
                            "video_index" to videoIndex,
                        ),
                    )
                    return true
                }
            }
            if (sheetOpen &&
                NextVideoAdvancePolicy.shouldDispatchFingerprintCloseBack(alreadyDispatchedCloseBack = backDispatched)
            ) {
                val closed = gestures.globalBack().succeeded
                backDispatched = true
                consecutiveClosed = 0
                logger.info(
                    "comment_next_video_close_sheet_back",
                    attributes = mapOf("success" to closed, "video_index" to videoIndex),
                )
                if (!closed) return false
            }
        }
        return false
    }

    private suspend fun dispatchNextVideoSwipe(reason: String): Boolean {
        nextVideoSettleUntilMs = SystemClock.uptimeMillis() + TuningConstants.CommentRuntime.NEXT_VIDEO_SETTLE_MS
        val swipe = gestures.swipeNormalized(
            startX = 0.50f,
            startY = TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_START_Y,
            endX = 0.50f,
            endY = TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_END_Y,
            durationMs = TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_DURATION_MS,
        )
        logger.info(
            "comment_next_video_swiped",
            attributes = mapOf(
                "video_index" to videoIndex,
                "video_total" to (config?.maxVideos ?: 0),
                "swipe_attempt" to nextVideoSwipeAttempt,
                "start_y" to TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_START_Y,
                "end_y" to TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_END_Y,
                "duration_ms" to TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_DURATION_MS,
                "success" to swipe.succeeded,
                "reason" to reason,
            ),
        )
        return swipe.succeeded
    }

    private suspend fun retryNextVideoSwipe(): Boolean {
        if (nextVideoSwipeAttempt >= TuningConstants.CommentRuntime.NEXT_VIDEO_SWIPE_MAX_ATTEMPTS) {
            return false
        }
        nextVideoSwipeAttempt += 1
        logger.info(
            "comment_next_video_swipe_retry",
            attributes = mapOf(
                "swipe_attempt" to nextVideoSwipeAttempt,
                "video_index" to videoIndex,
            ),
        )
        nextVideoPlayerFingerprintConfirmed = false
        if (!prepareClosedPlayerForNextVideoSwipe()) return false
        return dispatchNextVideoSwipe(nextVideoAdvanceReason)
    }

    private fun commitNextVideoAccounting() {
        if (nextVideoCommitted) return
        nextVideoCommitted = true
        awaitingNextVideoConfirmation = false
        videoIndex += 1
        ledger.clear()
        processedCandidateKeys.clear()
        lastViewportFingerprint = null
        scrollPending = false
        scrollCount = 0
        staleScrollCount = 0
        emptyScrollCount = 0
        initialCandidateReadRetryCount = 0
        reportedMatchStatisticsFingerprints.clear()
        activeCandidate = null
        logger.info(
            "comment_next_video_confirmed",
            attributes = mapOf(
                "video_index" to videoIndex,
                "video_total" to (config?.maxVideos ?: 0),
            ),
        )
    }

    private fun rememberFirstScreenCommentRow(
        context: ScreenContext,
        orderedCandidates: List<CommentUserCandidate>,
    ) {
        if (scrollCount != 0 || firstScreenCommentRowTopRatio != null) return
        val height = context.screenSize.height
        val minTop = orderedCandidates.minOfOrNull(::candidateTop)?.takeIf { it < Int.MAX_VALUE } ?: return
        if (height <= 0) return
        firstScreenCommentRowTopRatio = minTop.toFloat() / height.toFloat()
        logger.info(
            "comment_first_screen_row_baseline",
            attributes = mapOf("ratio" to firstScreenCommentRowTopRatio),
        )
    }

    /**
     * Executes P4-C for one matched comment author. Every transition is verified from a fresh
     * page snapshot, and the only submitted value is one ASCII space for the blank-message
     * safety probe. A candidate-level failure is recorded and skipped; only an unsafe page or a
     * failure to return to the comment sheet stops the whole task.
     */
    private suspend fun processCommentCandidate(
        candidate: CommentUserCandidate,
        initialContext: ScreenContext,
    ): Boolean {
        val identityFingerprint = UserIdentityFingerprint.fromStableKey(candidate.identityKey)
        val displayName = candidate.authorText?.trim()?.takeIf(String::isNotBlank)
            ?: candidate.identityKey.removePrefix("comment-user:").ifBlank { "评论用户" }
        val messageContent = "空消息模拟（空格）"
        when (
            AutomationStore.recordUserTaskStarted(
                identityFingerprint = identityFingerprint,
                page = PageKind.UNKNOWN,
                remoteUserKey = candidate.identityKey,
                displayName = displayName,
                messageContent = messageContent,
            )
        ) {
            DailyUserAdmission.ADMITTED -> Unit
            DailyUserAdmission.DAILY_UNIQUE_USER_LIMIT_REACHED -> {
                terminal(
                    CommentRuntimeTerminal.Outcome.PAUSED,
                    "今日已处理 ${AutomationExecutionLimits.MAX_DAILY_UNIQUE_USERS} 名不同用户，任务已暂停，明日可从检查点继续",
                )
                return false
            }

            DailyUserAdmission.NO_ACTIVE_TASK -> {
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "任务记录不可用，已停止继续处理评论用户")
                return false
            }
        }
        logger.info(
            "comment_candidate_processing_started",
            attributes = mapOf(
                "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
                "source" to candidate.source.name,
                "row_top" to candidateTop(candidate),
            ),
        )

        // The comment sheet's custom-rendered avatar rows can briefly vanish from the
        // accessibility tree while the RecyclerView settles after opening. Resolve the avatar
        // against fresh trees a bounded number of times before recording an identity failure.
        val profileObservationGeneration = observedContextGeneration.get()
        var click: ActionOutcome = ActionOutcome.failure("评论头像节点不可重新定位，拒绝点击名称或其他控件")
        var avatarAttempt = 0
        while (avatarAttempt < TuningConstants.CommentRuntime.AVATAR_RESOLVE_RETRIES && !click.succeeded) {
            if (avatarAttempt > 0) {
                delay(TuningConstants.CommentRuntime.AVATAR_RESOLVE_RETRY_DELAY_MS)
                logger.info(
                    "comment_avatar_resolve_retry",
                    attributes = mapOf("attempt" to avatarAttempt, "last_reason" to click.reason.orEmpty()),
                )
            }
            val candidateContext = currentContext() ?: initialContext
            click = clickCommentCandidate(candidateContext, candidate)
            avatarAttempt += 1
        }
        if (!click.succeeded) {
            finishCandidate(
                identityFingerprint,
                displayName,
                UserTaskRecord.Outcome.IDENTITY_UNAVAILABLE,
                "未能打开评论用户主页（评论头像不可用）：${click.reason.orEmpty()}",
                PageKind.UNKNOWN,
            )
            return returnToCommentSurface()
        }
        var profile = awaitPage(
            expected = setOf(PageKind.USER_PROFILE),
            description = "等待评论用户主页",
            afterObservedGeneration = profileObservationGeneration,
        )
        // ACTION_CLICK (or its bounded coordinate fallback) can be acknowledged by a recycled
        // RecyclerView row without immediately navigating. Retry exactly once, and only after a
        // fresh snapshot still proves that the comment panel is open. This preserves the strict
        // avatar-only contract: we never retry against an unknown/profile/DM surface, nor do we
        // substitute a username, comment body, heart, or other nearby control.
        val retryContext = profile.context ?: currentContext()
        if (
            profile.kind !in setOf(PageKind.USER_PROFILE, PageKind.HUMAN_INTERVENTION, PageKind.LOGIN) &&
            retryContext != null &&
            CommentSurfaceDetector.detect(retryContext).isCommentSurface
        ) {
            logger.warn(
                "comment_profile_open_retry",
                message = "头像点击未确认跳转且评论面板仍在，使用同一头像进行一次受限重试",
                attributes = mapOf("first_result" to profile.kind.name, "row_top" to candidateTop(candidate)),
            )
            delay(TuningConstants.CommentRuntime.AVATAR_PROFILE_RETRY_DELAY_MS)
            val retryObservationGeneration = observedContextGeneration.get()
            val retryClick = clickCommentCandidate(currentContext() ?: retryContext, candidate)
            if (retryClick.succeeded) {
                profile = awaitPage(
                    expected = setOf(PageKind.USER_PROFILE),
                    description = "等待评论用户主页（头像重试）",
                    afterObservedGeneration = retryObservationGeneration,
                )
            }
        }
        when (profile.kind) {
            PageKind.HUMAN_INTERVENTION,
            PageKind.LOGIN,
            -> {
                finishCandidate(
                    identityFingerprint,
                    displayName,
                    UserTaskRecord.Outcome.PAUSED,
                    "评论用户主页出现需要人工处理的页面",
                    profile.kind,
                )
                terminal(CommentRuntimeTerminal.Outcome.PAUSED, "评论用户主页需要人工处理")
                return false
            }
            PageKind.USER_PROFILE -> Unit
            else -> {
                finishCandidate(
                    identityFingerprint,
                    displayName,
                    UserTaskRecord.Outcome.IDENTITY_UNAVAILABLE,
                    "评论用户主页未在限定时间内确认",
                    profile.kind,
                )
                return returnToCommentSurface()
            }
        }

        val profileContext = profile.context ?: currentContext()
        if (profileContext == null) {
            finishCandidate(
                identityFingerprint,
                displayName,
                UserTaskRecord.Outcome.IDENTITY_UNAVAILABLE,
                "评论用户主页内容不可用",
                PageKind.USER_PROFILE,
            )
            return returnToCommentSurface()
        }
        if (config?.skipBlankProbe == true) {
            logger.info(
                "comment_profile_return_skip_probe",
                attributes = mapOf(
                    "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
                    "row_top" to candidateTop(candidate),
                ),
            )
            finishCandidate(
                identityFingerprint,
                displayName,
                UserTaskRecord.Outcome.PROFILE_OPENED,
                "调试：已进入评论用户主页，跳过私信入口与空白探测",
                PageKind.USER_PROFILE,
            )
            return returnToCommentSurface()
        }
        // A commenter profile first appears as a lightweight shell on some Douyin builds.  The
        // compact paper-plane action is mounted a little later, so a single profile snapshot can
        // falsely look like a recipient has disabled private messages.  Re-read the live profile
        // and retry the same semantic action in a bounded window before reporting it unavailable.
        val opening = openCommentPrivateMessage(profileContext)
        val directMessage = opening.page
        when (directMessage.kind) {
            PageKind.DIRECT_MESSAGE -> Unit
            PageKind.HUMAN_INTERVENTION,
            PageKind.LOGIN,
            -> {
                finishCandidate(
                    identityFingerprint,
                    displayName,
                    UserTaskRecord.Outcome.PAUSED,
                    "私信入口后出现需要人工处理的页面",
                    directMessage.kind,
                )
                terminal(CommentRuntimeTerminal.Outcome.PAUSED, "评论用户私信流程需要人工处理")
                return false
            }
            PageKind.PRIVATE_MESSAGE_RESTRICTED -> {
                finishCandidate(
                    identityFingerprint,
                    displayName,
                    UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
                    "对方的私信权限不允许当前账号发送",
                    directMessage.kind,
                )
                return returnToCommentSurface()
            }
            else -> {
                finishCandidate(
                    identityFingerprint,
                    displayName,
                    if (opening.entryActionSubmitted) {
                        UserTaskRecord.Outcome.MESSAGE_SEND_FAILED
                    } else {
                        UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE
                    },
                    opening.reason ?: if (opening.entryActionSubmitted) {
                        "私信入口已尝试，但私信页未在限定时间内确认"
                    } else {
                        "主页在稳定等待后仍没有可确认的纸飞机私信入口"
                    },
                    directMessage.kind,
                )
                return returnToCommentSurface()
            }
        }

        val probe = probeBlankMessage(directMessage.context)
        if (probe.kind == PageKind.MESSAGE_EMPTY_REJECTED) {
            finishCandidate(
                identityFingerprint,
                displayName,
                UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED,
                "抖音提示不能发送空白消息，安全探测完成，未发送真实内容",
                probe.kind,
            )
        } else {
            finishCandidate(
                identityFingerprint,
                displayName,
                UserTaskRecord.Outcome.MESSAGE_SEND_FAILED,
                probe.reason ?: "空消息安全探测未得到确认",
                probe.kind,
            )
        }
        return returnToCommentSurface()
    }

    private data class ObservedPage(
        val kind: PageKind,
        val context: ScreenContext?,
        val reason: String? = null,
    )

    /** Result of the bounded profile-action route used only by the comment-private-message flow. */
    private data class PrivateMessageOpening(
        val page: ObservedPage,
        val entryActionSubmitted: Boolean,
        val reason: String? = null,
    )

    private suspend fun clickCommentCandidate(
        context: ScreenContext,
        candidate: CommentUserCandidate,
    ): ActionOutcome {
        val target = CommentCandidateExtractor.resolveAvatarTarget(context, candidate)
        if (target == null) {
            saveNodeDiagnostic(context, "avatar_resolution_failed")
            logger.warn(
                "comment_avatar_resolution_failed",
                message = "评论头像节点不可重新定位，拒绝点击名称或其他控件",
                attributes = CommentCandidateExtractor.avatarTargetDiagnostics(context, candidate),
            )
            return ActionOutcome.failure("评论头像节点不可重新定位，拒绝点击名称或其他控件")
        }
        logger.info(
            "comment_avatar_target_verified",
            attributes = mapOf(
                "target_id" to target.stableId,
                "view_id" to (target.viewIdResourceName ?: ""),
                "bounds" to target.bounds.toString(),
            ),
        )
        // The comment contract is avatar -> profile -> paper-plane entry. Do not resolve by the
        // visible author label: accessibility may expose nearby “赞0，未选中/踩，已选中” labels
        // as clickable nodes, which previously produced the false “私信入口不可用” records.
        return clickSnapshot(target, "comment_avatar") { liveNode ->
            isLiveCommentAvatar(liveNode, target.bounds, candidate)
        }
    }

    /**
     * Opens a commenter private-message page through the profile's semantic paper-plane action.
     *
     * The profile transition and its action row are separate asynchronous renders.  In the old
     * path, a valid USER_PROFILE shell without its action row immediately became
     * PRIVATE_MESSAGE_UNAVAILABLE, even though the action appeared moments later.  This method
     * only taps a node with an explicit private-message semantic and always refreshes the active
     * window between attempts; it never falls back to a raw coordinate or to a follow/like action.
     */
    private suspend fun openCommentPrivateMessage(
        initialProfileContext: ScreenContext,
    ): PrivateMessageOpening {
        var lastContext = initialProfileContext
        var lastReason = "主页私信入口尚未稳定"
        var entryActionSubmitted = false

        repeat(TuningConstants.CommentRuntime.PRIVATE_MESSAGE_ENTRY_ATTEMPTS) { zeroBasedAttempt ->
            val attempt = zeroBasedAttempt + 1
            if (zeroBasedAttempt > 0) {
                delay(TuningConstants.CommentRuntime.PRIVATE_MESSAGE_ENTRY_RETRY_DELAY_MS)
            }

            val liveContext = currentContext() ?: lastContext
            lastContext = liveContext
            val detection = pageDetector.detect(liveContext)
            when (detection.kind) {
                PageKind.DIRECT_MESSAGE,
                PageKind.MESSAGE_EMPTY_REJECTED,
                PageKind.MESSAGE_SEND_FAILED,
                PageKind.PRIVATE_MESSAGE_RESTRICTED,
                PageKind.HUMAN_INTERVENTION,
                PageKind.LOGIN,
                -> {
                    return PrivateMessageOpening(
                        page = ObservedPage(detection.kind, liveContext),
                        entryActionSubmitted = entryActionSubmitted,
                    )
                }
                else -> Unit
            }

            // A transient UNKNOWN classification is common while the profile grid animates.  Do
            // not require that classification to be USER_PROFILE before inspecting the strictly
            // semantic action; the action itself is the safety boundary.  A returned comment
            // surface, however, is never allowed to receive a private-message click.
            if (CommentSurfaceDetector.detect(liveContext).isCommentSurface) {
                lastReason = "等待私信入口时已返回评论区"
                logger.warn(
                    "comment_private_message_entry_waiting",
                    message = "Comment surface appeared while waiting for the profile action; no private-message action was attempted",
                    attributes = mapOf("attempt" to attempt, "page" to detection.kind.name),
                )
                return@repeat
            }

            val selection = selectSafePrivateMessageEntry(liveContext)
            val entry = selection.node ?: ProfileMessageEntryFallback.iconNode(liveContext)
            if (entry == null) {
                lastReason = selection.reasons.joinToString("; ")
                    .ifBlank { "主页还没有暴露语义私信入口" }
                logger.info(
                    "comment_private_message_entry_waiting",
                    attributes = mapOf(
                        "attempt" to attempt,
                        "page" to detection.kind.name,
                        "nodes" to liveContext.nodes.size,
                        "selector_score" to selection.score,
                        "selector_reason_count" to selection.reasons.size,
                    ),
                )
                if (attempt == TuningConstants.CommentRuntime.PRIVATE_MESSAGE_ENTRY_ATTEMPTS) {
                    saveNodeDiagnostic(liveContext, "comment_private_message_entry_unavailable")
                }
                return@repeat
            }

            val directMessageObservationGeneration = observedContextGeneration.get()
            val entryClick = clickSnapshot(entry, "comment_private_message_entry")
            if (!entryClick.succeeded) {
                lastReason = "纸飞机私信入口点击失败：${entryClick.reason.orEmpty()}"
                logger.warn(
                    "comment_private_message_entry_retry",
                    message = "The verified private-message action could not be activated; refreshing the same profile action",
                    attributes = mapOf("attempt" to attempt, "route" to entryClick.route),
                )
                return@repeat
            }

            entryActionSubmitted = true
            logger.info(
                "comment_private_message_entry_submitted",
                attributes = mapOf(
                    "attempt" to attempt,
                    "source" to if (selection.node != null) "selector" else "profile_icon_fallback",
                ),
            )
            val observed = awaitPage(
                expected = setOf(
                    PageKind.DIRECT_MESSAGE,
                    PageKind.MESSAGE_EMPTY_REJECTED,
                    PageKind.MESSAGE_SEND_FAILED,
                    PageKind.PRIVATE_MESSAGE_RESTRICTED,
                ),
                description = "等待评论用户私信页",
                timeoutMs = TuningConstants.CommentRuntime.PRIVATE_MESSAGE_ENTRY_POSTCONDITION_TIMEOUT_MS,
                afterObservedGeneration = directMessageObservationGeneration,
            )
            lastContext = observed.context ?: lastContext
            when (observed.kind) {
                PageKind.DIRECT_MESSAGE,
                PageKind.MESSAGE_EMPTY_REJECTED,
                PageKind.MESSAGE_SEND_FAILED,
                PageKind.PRIVATE_MESSAGE_RESTRICTED,
                PageKind.HUMAN_INTERVENTION,
                PageKind.LOGIN,
                -> {
                    return PrivateMessageOpening(
                        page = observed,
                        entryActionSubmitted = true,
                        reason = observed.reason,
                    )
                }
                else -> {
                    lastReason = observed.reason ?: "私信入口后页面仍未稳定"
                    logger.warn(
                        "comment_private_message_entry_postcondition_retry",
                        message = "The profile remained visible after the private-message action; retrying the same semantic action",
                        attributes = mapOf("attempt" to attempt, "page" to observed.kind.name),
                    )
                }
            }
        }

        val finalDetection = pageDetector.detect(lastContext)
        return PrivateMessageOpening(
            page = ObservedPage(finalDetection.kind, lastContext, lastReason),
            entryActionSubmitted = entryActionSubmitted,
            reason = lastReason,
        )
    }

    private fun selectSafePrivateMessageEntry(context: ScreenContext): SelectionResult {
        val selection = selector.select(context, DouyinSelectors.privateMessageEntry)
        val node = selection.node ?: return selection
        val searchable = (node.searchableText() + selection.reasons).joinToString(" ").lowercase()
        val verdict = PrivateMessageEntryRuleStore.evaluate(
            route = PrivateMessageEntryRoute.SELECTOR,
            searchableText = searchable,
        )
        return if (!verdict.isAllowed) {
            selection.copy(
                node = null,
                score = 0f,
                reasons = listOf("Rejected unsafe or non-paper-plane private-message candidate"),
            )
        } else {
            selection
        }
    }

    private suspend fun awaitPage(
        expected: Set<PageKind>,
        description: String,
        timeoutMs: Long = TuningConstants.CommentRuntime.CANDIDATE_STEP_TIMEOUT_MS,
        afterObservedGeneration: Long? = null,
    ): ObservedPage {
        val attempts = (timeoutMs / TuningConstants.CommentRuntime.PAGE_POLL_INTERVAL_MS).toInt().coerceAtLeast(1)
        var lastKind = PageKind.UNKNOWN
        var lastContext: ScreenContext? = null
        var consumedObservedGeneration = afterObservedGeneration ?: Long.MIN_VALUE
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(TuningConstants.CommentRuntime.PAGE_POLL_INTERVAL_MS)
            val observedGeneration = observedContextGeneration.get()
            val observedContext = latestObservedContext?.takeIf {
                observedGeneration > consumedObservedGeneration
            }
            if (observedContext != null) consumedObservedGeneration = observedGeneration
            // A new accessibility observation is the fastest reliable postcondition after a
            // profile or paper-plane tap. Fall back to a live tree read only when no new event
            // arrived, preserving the bounded polling path on OEM builds that drop callbacks.
            val context = observedContext ?: currentContext() ?: return@repeat
            lastContext = context
            val detection = pageDetector.detect(context)
            lastKind = detection.kind
            if (detection.kind == PageKind.HUMAN_INTERVENTION || detection.kind == PageKind.LOGIN) {
                return ObservedPage(detection.kind, context, "检测到需要人工处理的页面")
            }
            if (detection.kind in expected) return ObservedPage(detection.kind, context)
        }
        return ObservedPage(lastKind, lastContext, "${description}超时")
    }

    private suspend fun probeBlankMessage(initialContext: ScreenContext? = null): ObservedPage {
        val context = initialContext
            ?: currentContext()
            ?: return ObservedPage(PageKind.UNKNOWN, null, "私信页不可用")
        val input = selector.select(context, DouyinSelectors.messageInput).node
            ?: return ObservedPage(PageKind.MESSAGE_SEND_FAILED, context, "未找到私信输入框")
        val setText = withLiveNode(input) { node -> gestures.setText(node, " ") }
        if (!setText.succeeded) {
            return ObservedPage(PageKind.MESSAGE_SEND_FAILED, context, "无法在私信输入框放置空格探测")
        }
        delay(TuningConstants.CommentRuntime.BLANK_PROBE_SETTLE_MS)
        val refreshed = currentContext() ?: context
        val send = selector.select(refreshed, DouyinSelectors.messageSendAction).node
        // Start collecting the native rejection *before* the submit gesture. On some Douyin
        // versions the “不能发送空白消息” toast is emitted in the same event turn as the click;
        // arming after it races the toast and falsely records a probe failure.
        probingBlankMessage = true
        blankRejectionObserved = false
        try {
            val submit = if (send != null) {
                withLiveNode(send) { node -> gestures.click(node, send.bounds) }
            } else {
                val refreshedInput = selector.select(refreshed, DouyinSelectors.messageInput).node
                if (refreshedInput == null) {
                    ActionOutcome.failure("发送按钮和输入框均不可用")
                } else {
                    withLiveNode(refreshedInput) { node -> gestures.submitText(node) }
                }
            }
            if (!submit.succeeded) {
                return ObservedPage(PageKind.MESSAGE_SEND_FAILED, refreshed, "无法提交空消息探测")
            }
            logger.info("comment_blank_probe_submitted")
            // Douyin delivers the “不能发送空白消息” notice as a transient
            // TYPE_NOTIFICATION_STATE_CHANGED event that never appears in the node tree.
            val attempts = (TuningConstants.CommentRuntime.BLANK_PROBE_TIMEOUT_MS / TuningConstants.CommentRuntime.PAGE_POLL_INTERVAL_MS).toInt().coerceAtLeast(1)
            var lastKind = PageKind.UNKNOWN
            var lastContext: ScreenContext? = null
            repeat(attempts) { attempt ->
                if (attempt > 0) delay(TuningConstants.CommentRuntime.PAGE_POLL_INTERVAL_MS)
                if (blankRejectionObserved) {
                    return ObservedPage(PageKind.MESSAGE_EMPTY_REJECTED, lastContext, null)
                }
                val sampled = currentContext() ?: return@repeat
                lastContext = sampled
                val detection = pageDetector.detect(sampled)
                lastKind = detection.kind
                if (detection.kind == PageKind.MESSAGE_EMPTY_REJECTED ||
                    detection.kind == PageKind.MESSAGE_SEND_FAILED
                ) {
                    // Capture the exact node tree that produced the detection. Douyin's blank-message
                    // rejection can arrive as an inline node (e.g. "发送失败") rather than a transient
                    // toast; the dump is written to the private diagnostics directory, never to logcat.
                    saveNodeDiagnostic(
                        sampled,
                        if (detection.kind == PageKind.MESSAGE_EMPTY_REJECTED) {
                            "blank_probe_empty_rejected"
                        } else {
                            "blank_probe_send_failed"
                        },
                    )
                    logger.info(
                        "comment_blank_probe_node_detection",
                        attributes = mapOf(
                            "attempt" to attempt,
                            "kind" to detection.kind.name,
                            "confidence" to detection.confidence,
                            "reason_count" to detection.reasons.size,
                        ),
                    )
                    return ObservedPage(detection.kind, sampled, null)
                }
            }
            return ObservedPage(lastKind, lastContext, "空消息探测结果超时")
        } finally {
            probingBlankMessage = false
            blankRejectionObserved = false
        }
    }

    /**
     * Receives transient Accessibility notification text (usually a toast) while the blank-message
     * probe is active. The “不能发送空白消息” notice never enters the node tree, so this is the only
     * reliable confirmation that the safety probe was rejected as blank and no real content moved.
     */
    fun onTransientAccessibilityText(values: List<String>) {
        if (!running || !probingBlankMessage) return
        val matched = values.any { EmptyMessageRejectionMatcher.matches(it) }
        // Log every transient delivery while the probe is armed so we can tell whether the toast
        // ever arrives at all, independent of whether its text matched the rejection vocabulary.
        logger.info(
            "comment_blank_probe_transient_received",
            attributes = mapOf("signals" to values.size, "matched" to matched),
        )
        if (matched) {
            blankRejectionObserved = true
            logger.info("comment_blank_probe_rejection_transient", attributes = mapOf("signals" to values.size))
        }
    }

    /**
     * Logs why the comment-button selector failed while the page is otherwise a video surface.
     * Only metadata is logged (class, view-id tail, flags, normalized bounds, depth); no user
     * text enters logcat. This is the triage signal for the "second video after swipe" miss.
     */
    private fun logCommentButtonMissDiagnostics(context: ScreenContext) {
        val imageClasses = listOf("imageview", "imagebutton", "button")

        fun NodeSnapshot.describe(): String = buildString {
            append("class=").append(className ?: "?")
            viewIdResourceName?.takeIf { it.isNotBlank() }?.let {
                append(" id=").append(it.substringAfterLast('/'))
            }
            append(" click=").append(isClickable)
            append(" enabled=").append(isEnabled)
            append(" visible=").append(isVisibleToUser)
            append(" bounds=").append(bounds.left).append(',').append(bounds.top)
                .append(',').append(bounds.right).append(',').append(bounds.bottom)
            val n = normalizedBounds(context.screenSize)
            append(" n=[")
            append(n.left).append(',').append(n.top).append(',').append(n.right).append(',').append(n.bottom)
            append("] depth=").append(depth)
            contentDescription?.takeIf { it.isNotBlank() }?.let {
                append(" desc=").append(it.take(12))
            }
        }

        val railCandidates = context.nodes.filter { node ->
            if (!node.isEnabled ||
                node.bounds.width <= 0 ||
                node.bounds.height <= 0 ||
                node.bounds.left < 0 ||
                node.bounds.top < 0 ||
                node.bounds.right > context.screenSize.width ||
                node.bounds.bottom > context.screenSize.height
            ) {
                return@filter false
            }
            val normalized = node.normalizedBounds(context.screenSize)
            val className = TextNormalizer.normalize(node.className)
            val imageLike = imageClasses.any(className::contains)
            imageLike && normalized.left >= 0.76f && normalized.top in 0.28f..0.90f &&
                normalized.width in 0.02f..0.22f && normalized.height in 0.01f..0.10f
        }

        logger.info(
            "comment_button_miss_diagnostic",
            attributes = mapOf(
                "page" to PageDetector().detect(context).kind.name,
                "nodes" to context.nodes.size,
                "max_depth" to (context.nodes.maxOfOrNull { it.depth } ?: 0),
                "rail_candidates" to railCandidates.size,
                "video_index" to videoIndex,
            ),
        )
        railCandidates.sortedBy { it.bounds.centerY }.take(8).forEachIndexed { index, node ->
            logger.info(
                "comment_miss_rail",
                attributes = mapOf("index" to index, "node" to node.describe()),
            )
        }
        // Persist the full tree privately for offline inspection if the log metadata is inconclusive.
        saveNodeDiagnostic(context, "comment_button_miss")
    }

    /** Writes a privacy-scoped node dump into the private diagnostics directory, never to logcat. */
    private fun saveNodeDiagnostic(context: ScreenContext, tag: String) {
        val safeTag = tag.replace(Regex("[^a-zA-Z0-9_-]+"), "_").take(32).ifBlank { "blank_probe" }
        val directory = File(service.filesDir, TuningConstants.CommentRuntime.BLANK_PROBE_NODE_DUMP_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            logger.error("blank_probe_node_dump_failed", message = "Could not create private node-dump directory")
            return
        }
        val destination = File(directory, "nodes_${System.currentTimeMillis()}_$safeTag.txt")
        runCatching { destination.writeText(inspector.diagnosticDump(context)) }
            .onSuccess {
                logger.info(
                    "blank_probe_node_dump_saved",
                    attributes = mapOf("file" to destination.name, "nodes" to context.nodes.size),
                )
            }
            .onFailure { error ->
                logger.error("blank_probe_node_dump_failed", message = "Could not write private node dump", throwable = error)
            }
    }

    private suspend fun returnToCommentSurface(): Boolean {
        awaitingCommentSurfaceReturn = true
        clearReturnCommentSurfaceConfirmation()
        try {
            var ocrRecoveryAttempted = false
            var requiredBackActions: Int? = null
            var profileLeavePollCompleted = false
            for (attempt in 0..TuningConstants.CommentRuntime.MAX_RETURN_TO_COMMENT_BACKS) {
                var context = currentContext()
                if (context != null) {
                    var detection = pageDetector.detect(context)
                    if (detection.kind == PageKind.HUMAN_INTERVENTION || detection.kind == PageKind.LOGIN) {
                        terminal(CommentRuntimeTerminal.Outcome.PAUSED, "返回评论区时出现需要人工处理的页面")
                        return false
                    }
                    // A direct-message composer can contain reply-like labels and a bottom input,
                    // while a profile can contain “回复” in its visible content. Do not treat either
                    // surface as the comment sheet before performing the required back navigation.
                    var stillInsideNestedSurface = detection.kind in setOf(
                        PageKind.USER_PROFILE,
                        PageKind.DIRECT_MESSAGE,
                        PageKind.MESSAGE_EMPTY_REJECTED,
                        PageKind.MESSAGE_SEND_FAILED,
                        PageKind.PRIVATE_MESSAGE_RESTRICTED,
                    )

                    if (requiredBackActions == null && stillInsideNestedSurface) {
                        requiredBackActions = requiredBackActionsFor(detection.kind)
                    }
                    val minimumBackActions = requiredBackActions ?: 0
                    val stillOnUserProfile = detection.kind == PageKind.USER_PROFILE
                    val nodeCommentSurfaceReady = CommentSurfaceDetector.detect(context).isCommentSurface

                    // A commenter profile can return directly to a video whose full right rail
                    // is visually present but sparse in accessibility. Take one bounded OCR
                    // sample only when nodes have not already proved the comment sheet.
                    if (
                        CommentReturnBackPolicy.shouldEnrichReturnWithActionRailOcr(
                            stillNested = stillInsideNestedSurface,
                            nodeCommentSurfaceReady = nodeCommentSurfaceReady,
                            ocrAlreadyAttempted = ocrRecoveryAttempted,
                        )
                    ) {
                        ocrRecoveryAttempted = true
                        context = enrichWithOcr(context)
                        detection = pageDetector.detect(context)
                        if (detection.kind == PageKind.HUMAN_INTERVENTION || detection.kind == PageKind.LOGIN) {
                            terminal(CommentRuntimeTerminal.Outcome.PAUSED, "返回评论区时出现需要人工处理的页面")
                            return false
                        }
                        stillInsideNestedSurface = detection.kind in setOf(
                            PageKind.USER_PROFILE,
                            PageKind.DIRECT_MESSAGE,
                            PageKind.MESSAGE_EMPTY_REJECTED,
                            PageKind.MESSAGE_SEND_FAILED,
                            PageKind.PRIVATE_MESSAGE_RESTRICTED,
                        )
                    } else if (!stillInsideNestedSurface && nodeCommentSurfaceReady) {
                        logger.info(
                            "comment_return_ocr_skipped",
                            attributes = mapOf(
                                "attempt" to attempt,
                                "back_actions" to returnCommentSurfaceBackActions,
                                "required_back_actions" to minimumBackActions,
                            ),
                        )
                    }

                    val surfaceDetection = CommentSurfaceDetector.detect(context)
                    val commentButton = VideoCommentButtonDetector.find(context)
                    logger.info(
                        "comment_return_to_surface_attempt",
                        attributes = mapOf(
                            "attempt" to attempt,
                            "page" to detection.kind.name,
                            "nested" to stillInsideNestedSurface,
                            "surface" to surfaceDetection.isCommentSurface,
                            "surface_reasons" to surfaceDetection.reasons.joinToString("|"),
                            "comment_button" to (commentButton != null),
                            "ocr_blocks" to context.ocrBlocks.size,
                            "back_actions" to returnCommentSurfaceBackActions,
                            "required_back_actions" to minimumBackActions,
                        ),
                    )
                    if (!stillInsideNestedSurface && surfaceDetection.isCommentSurface) {
                        logger.info(
                            "comment_surface_restored",
                            attributes = mapOf("back_attempts" to attempt, "source" to "current_context"),
                        )
                        return true
                    }

                    // Never consume a surface event until the current context is outside the
                    // nested DM/profile route and this return has completed the expected number
                    // of BACK actions. An old comment event can otherwise arrive after the first
                    // DM -> profile BACK and incorrectly advance the next candidate.
                    if (!stillInsideNestedSurface && hasConfirmedReturnCommentSurface(minimumBackActions)) {
                        logger.info(
                            "comment_surface_restored",
                            attributes = mapOf(
                                "back_attempts" to attempt,
                                "source" to "accessibility_event_after_final_back",
                                "back_actions" to returnCommentSurfaceBackActions,
                            ),
                        )
                        return true
                    }

                    if (!stillInsideNestedSurface) {
                        // A comment sheet is a toggle surface. If this final video view is still
                        // ambiguous, do not reopen it automatically: the detector can temporarily
                        // see comment-row controls as a right rail and GestureEngine may otherwise
                        // downgrade a failed node action to a coordinate gesture. Waiting for a
                        // direct confirmation is safe; failing it is preferable to touching a
                        // heart, address, tab, or other non-requirement control.
                        if (awaitCommentSurface(minimumBackActions)) {
                            logger.info(
                                "comment_surface_restored",
                                attributes = mapOf("back_attempts" to attempt, "source" to "bounded_wait"),
                            )
                            return true
                        }
                        logger.warn(
                            "comment_reopen_suppressed",
                            message = "Final video surface is unconfirmed; automatic reopen is disabled for return safety",
                            attributes = mapOf("attempt" to attempt, "back_actions" to returnCommentSurfaceBackActions),
                        )
                        break
                    }
                    if (attempt == TuningConstants.CommentRuntime.MAX_RETURN_TO_COMMENT_BACKS) break
                    val dispatchBack = CommentReturnBackPolicy.shouldDispatchAnotherReturnBack(
                        stillNested = stillInsideNestedSurface,
                        backsDispatched = returnCommentSurfaceBackActions,
                        requiredBacks = minimumBackActions,
                        stillOnUserProfile = stillOnUserProfile,
                        profileLeavePollCompleted = profileLeavePollCompleted,
                    )
                    if (!dispatchBack) {
                        if (
                            CommentReturnBackPolicy.shouldPollForProfileLeave(
                                stillOnUserProfile = stillOnUserProfile,
                                backsDispatched = returnCommentSurfaceBackActions,
                                requiredBacks = minimumBackActions,
                            )
                        ) {
                            when (awaitLeaveUserProfileAfterReturnBack()) {
                                ProfileLeavePoll.COMMENT_SURFACE -> {
                                    logger.info(
                                        "comment_surface_restored",
                                        attributes = mapOf(
                                            "back_attempts" to attempt,
                                            "source" to "profile_leave_poll",
                                        ),
                                    )
                                    return true
                                }
                                ProfileLeavePoll.LEFT_PROFILE,
                                ProfileLeavePoll.STILL_PROFILE,
                                -> profileLeavePollCompleted = true
                            }
                        }
                        continue
                    }
                    val nextBackActionCount = returnCommentSurfaceBackActions + 1
                    confirmedReturnCommentSurface = null
                    confirmedReturnCommentSurfaceBackActions = -1
                    returnCommentSurfaceBackActions = nextBackActionCount
                    if (!gestures.globalBack().succeeded) {
                        returnCommentSurfaceBackActions -= 1
                        break
                    }
                    logger.info(
                        "comment_return_back_dispatched",
                        attributes = mapOf("attempt" to attempt, "back_actions" to nextBackActionCount),
                    )
                    profileLeavePollCompleted = false
                    if (stillOnUserProfile) {
                        when (awaitLeaveUserProfileAfterReturnBack()) {
                            ProfileLeavePoll.COMMENT_SURFACE -> {
                                logger.info(
                                    "comment_surface_restored",
                                    attributes = mapOf(
                                        "back_attempts" to attempt,
                                        "source" to "profile_leave_after_back",
                                    ),
                                )
                                return true
                            }
                            ProfileLeavePoll.LEFT_PROFILE,
                            ProfileLeavePoll.STILL_PROFILE,
                            -> profileLeavePollCompleted = true
                        }
                    } else {
                        delay(TuningConstants.CommentRuntime.RETURN_TO_COMMENT_DELAY_MS)
                    }
                }
            }
            terminal(CommentRuntimeTerminal.Outcome.FAILED, "无法在限定次数内返回评论区")
            return false
        } finally {
            awaitingCommentSurfaceReturn = false
            clearReturnCommentSurfaceConfirmation()
        }
    }

    private enum class ProfileLeavePoll {
        COMMENT_SURFACE,
        LEFT_PROFILE,
        STILL_PROFILE,
    }

    /**
     * After BACK from a commenter profile, wait like the B-end profile return:
     * 200ms first sample, then 150ms × 4. Do not issue another BACK during this window.
     */
    private suspend fun awaitLeaveUserProfileAfterReturnBack(): ProfileLeavePoll {
        var last = ProfileLeavePoll.STILL_PROFILE
        repeat(TuningConstants.NavigationFlow.USER_PROFILE_BACK_POLL_ATTEMPTS) { attempt ->
            delay(
                if (attempt == 0) {
                    TuningConstants.NavigationFlow.USER_PROFILE_BACK_DELAY_MS
                } else {
                    TuningConstants.NavigationFlow.USER_PROFILE_BACK_POLL_INTERVAL_MS
                },
            )
            val context = currentContext()
            val kind = context?.let { pageDetector.detect(it).kind }
            val surface = context?.let { CommentSurfaceDetector.detect(it).isCommentSurface } == true
            logger.info(
                "comment_return_profile_leave_poll",
                attributes = mapOf(
                    "attempt" to (attempt + 1),
                    "page" to (kind?.name ?: "NO_CONTEXT"),
                    "surface" to surface,
                ),
            )
            if (surface && kind != PageKind.USER_PROFILE && kind != PageKind.DIRECT_MESSAGE) {
                return ProfileLeavePoll.COMMENT_SURFACE
            }
            if (kind != null && kind != PageKind.USER_PROFILE) {
                last = if (surface) ProfileLeavePoll.COMMENT_SURFACE else ProfileLeavePoll.LEFT_PROFILE
                if (last == ProfileLeavePoll.COMMENT_SURFACE) return last
                return ProfileLeavePoll.LEFT_PROFILE
            }
        }
        return last
    }

    private suspend fun awaitCommentSurface(minimumBackActions: Int): Boolean {
        repeat(TuningConstants.CommentRuntime.COMMENT_SURFACE_POLL_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(TuningConstants.CommentRuntime.PAGE_POLL_INTERVAL_MS)
            if (hasConfirmedReturnCommentSurface(minimumBackActions)) return true
            val context = currentContext() ?: return@repeat
            if (CommentSurfaceDetector.detect(context).isCommentSurface) return true
        }
        return false
    }

    private fun hasConfirmedReturnCommentSurface(minimumBackActions: Int): Boolean =
        minimumBackActions > 0 &&
            returnCommentSurfaceBackActions >= minimumBackActions &&
            confirmedReturnCommentSurfaceBackActions >= minimumBackActions &&
            confirmedReturnCommentSurface != null

    private fun requiredBackActionsFor(kind: PageKind): Int = when (kind) {
        PageKind.USER_PROFILE -> 1
        PageKind.DIRECT_MESSAGE,
        PageKind.MESSAGE_EMPTY_REJECTED,
        PageKind.MESSAGE_SEND_FAILED,
        PageKind.PRIVATE_MESSAGE_RESTRICTED,
        -> 2
        else -> 0
    }

    private fun clearReturnCommentSurfaceConfirmation() {
        confirmedReturnCommentSurface = null
        confirmedReturnCommentSurfaceBackActions = -1
        returnCommentSurfaceBackActions = 0
    }

    private fun finishCandidate(
        identityFingerprint: String,
        displayName: String,
        outcome: UserTaskRecord.Outcome,
        reason: String,
        page: PageKind,
    ) {
        AutomationStore.recordUserTaskFinished(
            identityFingerprint = identityFingerprint,
            outcome = outcome,
            reason = reason,
            page = page,
            remoteUserKey = activeCandidate?.identityKey,
            displayName = displayName,
        )
        logger.info(
            "comment_candidate_processing_finished",
            attributes = mapOf(
                "identity_fingerprint_prefix" to UserIdentityFingerprint.logPrefix(identityFingerprint),
                "outcome" to outcome.name,
            ),
        )
    }

    private fun currentContext(): ScreenContext? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            if (root.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) return null
            inspector.inspect(root)
        } finally {
            root.recycle()
        }
    }

    private fun candidateTop(candidate: CommentUserCandidate): Int = minOf(
        candidate.avatarBounds?.top ?: Int.MAX_VALUE,
        candidate.authorBounds?.top ?: candidate.commentBounds.top,
        candidate.commentBounds.top,
    )

    private suspend fun scrollCommentPanel(context: ScreenContext): ActionOutcome {
        // A comment sheet is layered over Douyin's full-screen feed ViewPager.  Picking the
        // tallest scrollable node selects that underlying feed on current builds, which means a
        // request for the fifth commenter can silently scroll the video instead of the comment
        // list.  Prefer a lower-sheet RecyclerView/ListView and only then fall back to another
        // lower-region scroll container.
        val scrollable = CommentPanelScrollTargetSelector.select(context)
        if (scrollable != null) {
            val outcome = withLiveNode(scrollable) { node ->
                gestures.scrollForward(node)
            }
            if (outcome.succeeded) return outcome
            logger.warn("comment_node_scroll_fallback", message = "评论列表节点滚动失败，使用归一化手势兜底")
        }
        return gestures.swipeNormalized(
            startX = 0.50f,
            startY = 0.82f,
            endX = 0.50f,
            endY = 0.46f,
            durationMs = 420L,
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun clickSnapshot(
        target: NodeSnapshot,
        action: String,
        liveNodeGuard: ((AccessibilityNodeInfo) -> Boolean)? = null,
    ): ActionOutcome {
        return withLiveNode(target, liveNodeGuard) { node -> gestures.click(node, target.bounds) }
            .also { outcome ->
                logger.info(
                    "comment_action",
                    attributes = mapOf("action" to action, "success" to outcome.succeeded, "route" to outcome.route),
                )
            }
    }

    /**
     * Uses accessibility-node activation whenever the rail exposes a usable node. A coordinate
     * route can only come from the existing OCR geometry proof, a direct template candidate, or
     * a like/collect pair already confirmed in two post-swipe screenshots. Every coordinate route
     * still requires the page-state and comment-panel postconditions owned by the state machine.
     */
    private suspend fun clickCommentButton(
        target: CommentButtonTarget,
        action: String,
    ): ActionOutcome = when (target) {
        is CommentButtonTarget.AccessibilityNode -> clickSnapshot(target.node, action)
        is CommentButtonTarget.TemplateFallback -> gestures.tapBounds(target.bounds).also { outcome ->
            logger.info(
                "comment_action",
                attributes = mapOf(
                    "action" to action,
                    "success" to outcome.succeeded,
                    "route" to outcome.route,
                    "source" to "template_fallback",
                    "confidence" to target.confidence,
                ),
            )
        }
        is CommentButtonTarget.OcrFallback -> gestures.tapBounds(target.bounds).also { outcome ->
            logger.info(
                "comment_action",
                attributes = mapOf(
                    "action" to action,
                    "success" to outcome.succeeded,
                    "route" to outcome.route,
                    "source" to "ocr_fallback",
                ),
            )
        }
        is CommentButtonTarget.DualAnchorFallback -> gestures.tapBounds(target.bounds).also { outcome ->
            logger.info(
                "comment_action",
                attributes = mapOf(
                    "action" to action,
                    "success" to outcome.succeeded,
                    "route" to outcome.route,
                    "source" to "dual_anchor_fallback",
                    "like_confidence" to target.likeConfidence,
                    "collect_confidence" to target.collectConfidence,
                ),
            )
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun withLiveNode(
        target: NodeSnapshot,
        liveNodeGuard: ((AccessibilityNodeInfo) -> Boolean)? = null,
        action: suspend (AccessibilityNodeInfo) -> ActionOutcome,
    ): ActionOutcome {
        val root = service.rootInActiveWindow
            ?: return ActionOutcome.failure("当前没有可用的抖音窗口")
        var liveNode: AccessibilityNodeInfo? = null
        return try {
            if (root.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) {
                ActionOutcome.failure("当前窗口不是抖音")
            } else {
                liveNode = selector.resolveLiveNode(root, target.hierarchyPath)
                if (liveNode != null && liveNodeGuard != null && !liveNodeGuard(liveNode!!)) {
                    liveNode?.recycle()
                    liveNode = null
                }
                if (liveNode == null) {
                    // Prefer Android's semantic id index before walking a rebuilt tree. This is
                    // especially reliable for Douyin's `cover`/`qb-` video tiles, whose child
                    // indexes change while the Works grid is settling.
                    val indexed = findLiveNodeByViewId(root, target)
                    if (indexed != null) {
                        if (liveNodeGuard == null || liveNodeGuard(indexed)) {
                            liveNode = indexed
                            logger.info(
                                "comment_node_relocated",
                                attributes = mapOf(
                                    "from" to target.stableId,
                                    "route" to "view_id_index",
                                    "view_id" to (target.viewIdResourceName ?: ""),
                                ),
                            )
                        } else {
                            indexed.recycle()
                        }
                    }
                }
                if (liveNode == null) {
                    // The Works grid is custom-rendered and can be rebuilt between the
                    // observation callback and this action. Re-inspect the current tree and
                    // relocate by stable semantic identity (view id/class + relative bounds)
                    // instead of failing the whole task on a stale hierarchy path.
                    val freshContext = inspector.inspect(root)
                    val relocated = selector.relocateSnapshot(freshContext, target)
                    if (relocated != null) {
                        val relocatedNode = selector.resolveLiveNode(root, relocated.hierarchyPath)
                        if (relocatedNode != null && (liveNodeGuard == null || liveNodeGuard(relocatedNode))) {
                            liveNode = relocatedNode
                            logger.info(
                                "comment_node_relocated",
                                attributes = mapOf(
                                    "from" to target.stableId,
                                    "to" to relocated.stableId,
                                    "view_id" to (relocated.viewIdResourceName ?: ""),
                                ),
                            )
                        } else {
                            relocatedNode?.recycle()
                        }
                    }
                }
                val node = liveNode
                if (node == null) {
                    ActionOutcome.failure("节点已变化，无法重新定位")
                } else {
                    action(node)
                }
            }
        } finally {
            if (liveNode != null && liveNode !== root) liveNode.recycle()
            root.recycle()
        }
    }

    /**
     * Validates the mutable node at the last possible moment before the comment-user click.
     * Douyin can rebuild a RecyclerView between inspection and dispatchGesture; a stale path
     * must never be allowed to land on the author text, reply action, or the comment body.
     */
    @Suppress("DEPRECATION")
    private fun isLiveCommentAvatar(
        node: AccessibilityNodeInfo,
        expectedBounds: ScreenBounds,
        candidate: CommentUserCandidate,
    ): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        val resourceId = node.viewIdResourceName?.toString()?.lowercase().orEmpty()
        val className = node.className?.toString()?.lowercase().orEmpty()
        val description = node.contentDescription?.toString()?.lowercase().orEmpty()
        val avatarSemantic = resourceId.contains("avatar") || resourceId.contains("head") ||
            description.contains("头像") || description.contains("avatar") ||
            className.contains("imageview") || className.contains("avatar")
        if (!avatarSemantic) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val actual = ScreenBounds(rect.left, rect.top, rect.right, rect.bottom)
        if (actual == ScreenBounds.EMPTY || actual.width !in 24..220 || actual.height !in 24..220) {
            return false
        }
        val ratio = actual.width.toFloat() / actual.height.toFloat()
        if (ratio !in 0.65f..1.35f) return false

        // The extractor already restricts candidates to this left rail. Repeat that invariant
        // against the mutable live node immediately before dispatching a gesture so a recycled
        // path cannot turn into the right-side heart/like action.
        if (actual.centerX > service.resources.displayMetrics.widthPixels * 0.30f) return false

        val authorLeft = candidate.authorBounds?.left ?: candidate.commentBounds.left
        val rowTop = minOf(candidate.authorBounds?.top ?: candidate.commentBounds.top, candidate.commentBounds.top)
        val rowBottom = maxOf(candidate.authorBounds?.bottom ?: candidate.commentBounds.bottom, candidate.commentBounds.bottom)
        val rowHeight = (rowBottom - rowTop).coerceAtLeast(1)
        // The live node must remain the small circular target to the *left* of the comment
        // text.  Do not permit the wide overlap used by old builds: it could turn a nearby
        // action/metadata ImageView into a click target after the RecyclerView refreshed.
        val horizontalAllowance = (expectedBounds.width / 4).coerceIn(8, 28).toFloat()
        val verticalAllowance = maxOf(actual.height * 0.75f, rowHeight * 0.9f)
        if (actual.left >= authorLeft || actual.right > authorLeft + horizontalAllowance) return false
        if (actual.centerY !in (rowTop - verticalAllowance)..(rowBottom + verticalAllowance)) return false

        val centerTolerance = maxOf(expectedBounds.width * 1.5f, 96f)
        return kotlin.math.abs(actual.centerX - expectedBounds.centerX) <= centerTolerance &&
            kotlin.math.abs(actual.centerY - expectedBounds.centerY) <= centerTolerance
    }

    @Suppress("DEPRECATION")
    private fun findLiveNodeByViewId(
        root: AccessibilityNodeInfo,
        target: NodeSnapshot,
    ): AccessibilityNodeInfo? {
        val viewId = target.viewIdResourceName?.takeIf { it.isNotBlank() } ?: return null
        val candidates = runCatching {
            root.findAccessibilityNodeInfosByViewId(viewId)
        }.getOrNull().orEmpty()
            .filter { node ->
                node.isEnabled &&
                    (!target.isVisibleToUser || node.isVisibleToUser) &&
                    (!target.isClickable || node.isClickable)
            }
        if (candidates.isEmpty()) return null

        val chosen = candidates.minByOrNull { node ->
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            kotlin.math.abs(bounds.centerX() - target.bounds.centerX) +
                kotlin.math.abs(bounds.centerY() - target.bounds.centerY)
        }
        candidates.filter { it !== chosen }.forEach { it.recycle() }
        return chosen
    }

    private fun viewportFingerprint(context: ScreenContext): Int = buildString {
        context.nodes.filter { it.isVisibleToUser && it.bounds != ScreenBounds.EMPTY }.forEach { node ->
            append(node.text.orEmpty()).append('|')
                .append(node.bounds.left).append(',').append(node.bounds.top).append(',')
                .append(node.bounds.right).append(',').append(node.bounds.bottom).append(';')
        }
        context.ocrBlocks.forEach { block -> append(block.text).append('|').append(block.bounds).append(';') }
    }.hashCode()

    /** Writes only aggregate counts; comment text and OCR content never leave this runtime. */
    private fun reportMatchStatistics(
        context: ScreenContext,
        extraction: CommentCandidateExtraction,
    ) {
        val fingerprint = viewportFingerprint(context)
        if (!reportedMatchStatisticsFingerprints.add(fingerprint)) return
        AutomationStore.recordCommentMatchStatistics(
            commentBodiesRead = extraction.commentBodiesRead,
            matchedCommentBodies = extraction.matchedCommentBodies,
            actionableCandidates = extraction.actionableCandidateCount,
        )
    }

    /**
     * Captures only the visible semantic/video-action shell after the old comment panel has
     * closed. It is not an identity key and is never used to choose a person; it only stops a
     * late panel animation from being mistaken for the next feed item after a swipe.
     */
    private fun videoSurfaceFingerprint(context: ScreenContext): Int? = buildString {
        context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds != ScreenBounds.EMPTY }
            .filter { it.normalizedBounds(context.screenSize).top < 0.94f }
            .forEach { node ->
                append(node.viewIdResourceName.orEmpty()).append('|')
                append(node.className.orEmpty()).append('|')
                node.searchableText().forEach { append(it).append('|') }
                val bounds = node.normalizedBounds(context.screenSize)
                append(bounds.left).append(',').append(bounds.top).append(',')
                    .append(bounds.right).append(',').append(bounds.bottom).append(';')
            }
        context.ocrBlocks
            .filter { it.bounds.centerY < context.screenSize.height * 0.94f }
            .forEach { block -> append(block.text).append('|').append(block.bounds).append(';') }
    }.takeIf(String::isNotBlank)?.hashCode()

    /** Returns only the central media canvas used to reveal an auto-hidden player action rail. */
    private fun neutralVideoSurface(context: ScreenContext): NodeSnapshot? = context.nodes
        .asSequence()
        .filter { it.bounds != ScreenBounds.EMPTY }
        .filter { TextNormalizer.normalize(it.className).contains("surfaceview") }
        .filter {
            // Some Douyin player surfaces are drawn correctly but carry a stale false
            // isVisibleToUser flag after the feed pager settles. Raw in-screen geometry keeps
            // this fallback limited to the currently rendered media canvas and rules out the
            // off-screen/stale surfaces that use negative coordinates.
            it.bounds.left >= 0 && it.bounds.top >= 0 &&
                it.bounds.right <= context.screenSize.width && it.bounds.bottom <= context.screenSize.height
        }
        .filter {
            val bounds = it.normalizedBounds(context.screenSize)
            bounds.left <= 0.10f && bounds.right >= 0.90f &&
                bounds.width >= 0.70f && bounds.height >= 0.16f &&
                bounds.centerY in 0.22f..0.72f
        }
        .maxByOrNull { it.bounds.width * it.bounds.height }

    private fun armTimeout(description: String, timeoutMs: Long = TuningConstants.CommentRuntime.STEP_TIMEOUT_MS) {
        timeoutJob?.cancel()
        val generation = timeoutGeneration.incrementAndGet()
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (running && timeoutGeneration.get() == generation) {
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "评论流程超时：$description")
            }
        }
    }

    private fun terminal(outcome: CommentRuntimeTerminal.Outcome, reason: String) {
        if (!running) return
        timeoutGeneration.incrementAndGet()
        running = false
        timeoutJob?.cancel()
        timeoutJob = null
        firstVideoTransitionProbeJob?.cancel()
        firstVideoTransitionProbeJob = null
        commentPanelProbeJob?.cancel()
        commentPanelProbeJob = null
        nextVideoTransitionProbeJob?.cancel()
        nextVideoTransitionProbeJob = null
        profileSurfaceProbeJob?.cancel()
        profileSurfaceProbeJob = null
        nextVideoSurfaceSignatureBeforeSwipe = null
        logger.info(
            "comment_runtime_terminal",
            attributes = mapOf("outcome" to outcome.name, "candidate_count" to ledger.size),
        )
        onTerminal(CommentRuntimeTerminal(outcome, reason))
    }

}
