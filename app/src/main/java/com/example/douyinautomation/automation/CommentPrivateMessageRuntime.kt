package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Terminal outcome emitted to the owning navigation controller. */
data class CommentRuntimeTerminal(
    val outcome: Outcome,
    val reason: String,
) {
    enum class Outcome {
        COMPLETED,
        FAILED,
        PAUSED,
    }
}

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
    private val onTerminal: (CommentRuntimeTerminal) -> Unit,
) {
    private val stateMachine = CommentEntryStateMachine()
    private val ledger = CommentCandidateLedger()
    private var config: CommentPrivateMessageSnapshot? = null
    private var timeoutJob: Job? = null
    private var running = false
    private var scrollPending = false
    private var lastViewportFingerprint: Int? = null
    private var scrollCount = 0
    private var videoIndex = 0
    private var liveRoomExitCount = 0
    private var liveRoomSwipeCount = 0
    private val processedCandidateKeys = LinkedHashSet<String>()
    /** Task-level identity ledger; prevents the same commenter being probed again on another video. */
    private val processedTaskCandidateKeys = LinkedHashSet<String>()
    private var activeCandidate: CommentUserCandidate? = null

    val isRunning: Boolean get() = running
    val stage: CommentEntryStage get() = stateMachine.stage
    val candidateCount: Int get() = ledger.size

    fun start(snapshot: CommentPrivateMessageSnapshot) {
        timeoutJob?.cancel()
        config = snapshot
        stateMachine.reset()
        ledger.clear()
        running = true
        scrollPending = false
        lastViewportFingerprint = null
        scrollCount = 0
        videoIndex = 0
        liveRoomExitCount = 0
        liveRoomSwipeCount = 0
        processedCandidateKeys.clear()
        processedTaskCandidateKeys.clear()
        activeCandidate = null
        // Search-target mode still traverses the existing launch/search/user-tab flow before the
        // runtime receives a profile observation. Give that bounded entry route a longer guard;
        // once the profile/video/comment actions begin, each step returns to the short watchdog.
        armTimeout("等待用户主页", timeoutMs = INITIAL_ENTRY_TIMEOUT_MS)
        logger.info(
            "comment_runtime_started",
            attributes = mapOf(
                "entry_mode" to snapshot.entryMode.name,
                "keyword_count" to snapshot.matchKeywords.size,
                "max_videos" to snapshot.maxVideos,
                "max_users_per_video" to snapshot.maxUsersPerVideo,
            ),
        )
    }

    fun stop() {
        timeoutJob?.cancel()
        timeoutJob = null
        running = false
        config = null
        ledger.clear()
        processedCandidateKeys.clear()
        processedTaskCandidateKeys.clear()
        activeCandidate = null
    }

    suspend fun onObserved(context: ScreenContext, detection: PageDetection) {
        if (!running || context.packageName != TargetAppLauncher.DOUYIN_PACKAGE) return

        val observation = CommentEntrySignalDetector.observe(context)
        val decision = stateMachine.observe(observation)
        when (decision.action) {
            CommentEntryAction.EXIT_LIVE_ROOM -> {
                if (liveRoomExitCount >= MAX_LIVE_ROOM_EXITS) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "连续进入直播间且无法安全退出")
                    return
                }
                liveRoomExitCount += 1
                val closeButton = LiveRoomSurfaceDetector.findCloseButton(context)
                val outcome = if (closeButton != null) {
                    clickSnapshot(closeButton, "live_room_close")
                } else if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                    ActionOutcome.success("global_back")
                } else {
                    ActionOutcome.failure("无法执行返回操作")
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
                if (liveRoomSwipeCount >= MAX_LIVE_ROOM_SWIPES) {
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
                    durationMs = LIVE_ROOM_SWIPE_DURATION_MS,
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

            CommentEntryAction.OPEN_FIRST_VIDEO -> {
                val target = observation.firstVideoTarget
                    ?: return terminal(CommentRuntimeTerminal.Outcome.FAILED, "未找到第一个视频入口")
                val outcome = clickSnapshot(target, "comment_first_video")
                if (!outcome.succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "打开第一个视频失败：${outcome.reason}")
                    return
                }
                armTimeout("等待视频页面")
                return
            }

            CommentEntryAction.OPEN_COMMENTS -> {
                val target = observation.commentButton
                    ?: return terminal(CommentRuntimeTerminal.Outcome.FAILED, "未找到评论按钮")
                val outcome = clickSnapshot(target, "comment_open_panel")
                if (!outcome.succeeded) {
                    terminal(CommentRuntimeTerminal.Outcome.FAILED, "打开评论区失败：${outcome.reason}")
                    return
                }
                armTimeout("等待评论区")
                return
            }

            CommentEntryAction.READ_COMMENTS -> {
                processCommentViewport(context)
                return
            }

            CommentEntryAction.NONE -> Unit
        }

        // Once the state machine has crossed the comment-surface post-condition, subsequent
        // content-change events are pagination observations rather than another click request.
        if (stateMachine.stage == CommentEntryStage.READY_TO_READ && observation.isCommentSurfaceReady) {
            val fingerprint = viewportFingerprint(context)
            if (scrollPending && fingerprint == lastViewportFingerprint) return
            scrollPending = false
            processCommentViewport(context)
        } else if (detection.kind == PageKind.HUMAN_INTERVENTION || detection.kind == PageKind.LOGIN) {
            terminal(CommentRuntimeTerminal.Outcome.PAUSED, "抖音出现需要人工处理的页面")
        }
    }

    private suspend fun processCommentViewport(context: ScreenContext) {
        timeoutJob?.cancel()
        val end = CommentPanelEndDetector.detect(context)
        if (end.reached) {
            advanceAfterVideo("评论区已读取到底部：${end.marker.orEmpty()}")
            return
        }

        val terms = config?.matchKeywords.orEmpty()
        val extraction = CommentCandidateExtractor.extract(context, terms)
        val newCandidates = extraction.candidates
            .filterNot { candidate ->
                processedCandidateKeys.contains(candidate.identityKey) ||
                    processedTaskCandidateKeys.contains(candidate.identityKey)
            }
        val update = ledger.add(extraction)
        logger.info(
            "comment_viewport_read",
            attributes = mapOf(
                "candidate_count" to extraction.candidates.size,
                "new_candidate_count" to update.added,
                "duplicate_candidate_count" to update.duplicates,
                "ledger_count" to update.total,
                "scroll_count" to scrollCount,
                "viewport_fingerprint" to viewportFingerprint(context),
            ),
        )

        val maxUsers = config?.maxUsersPerVideo ?: CommentPrivateMessageConfig.DEFAULT_MAX_USERS_PER_VIDEO
        val remaining = (maxUsers - processedCandidateKeys.size).coerceAtLeast(0)
        for (candidate in newCandidates.take(remaining)) {
            processedCandidateKeys += candidate.identityKey
            processedTaskCandidateKeys += candidate.identityKey
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
        if (scrollCount >= MAX_COMMENT_SCROLLS) {
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
        armTimeout("等待评论区下一页")
    }

    /**
     * Finishes one video and moves to the next one without returning to the profile grid. The
     * comment sheet is closed first, then a single bounded feed swipe selects the next video.
     * The state machine is re-armed only after the previous video has been fully accounted for.
     */
    private suspend fun advanceAfterVideo(reason: String) {
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
        val closed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (!closed) {
            terminal(CommentRuntimeTerminal.Outcome.FAILED, "关闭当前视频评论区失败，无法继续下一个视频")
            return
        }
        delay(RETURN_TO_COMMENT_DELAY_MS)

        videoIndex = completedVideo
        ledger.clear()
        processedCandidateKeys.clear()
        lastViewportFingerprint = null
        scrollPending = false
        scrollCount = 0
        activeCandidate = null
        stateMachine.prepareNextVideo()

        val swipe = gestures.swipeNormalized(
            startX = 0.50f,
            startY = 0.84f,
            endX = 0.50f,
            endY = 0.28f,
            durationMs = NEXT_VIDEO_SWIPE_DURATION_MS,
        )
        logger.info(
            "comment_next_video_swiped",
            attributes = mapOf(
                "video_index" to videoIndex,
                "video_total" to snapshot.maxVideos,
                "success" to swipe.succeeded,
                "reason" to reason,
            ),
        )
        if (!swipe.succeeded) {
            terminal(CommentRuntimeTerminal.Outcome.FAILED, "切换下一个视频失败：${swipe.reason.orEmpty()}")
            return
        }
        armTimeout("等待下一个视频")
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
        val identityHash = candidate.identityKey.hashCode()
        val displayName = candidate.authorText?.trim()?.takeIf(String::isNotBlank)
            ?: candidate.identityKey.removePrefix("comment-user:").ifBlank { "评论用户" }
        val messageContent = "空消息模拟（空格）"
        AutomationStore.recordUserTaskStarted(
            identityHash = identityHash,
            page = PageKind.UNKNOWN,
            remoteUserKey = candidate.identityKey,
            displayName = displayName,
            messageContent = messageContent,
        )
        logger.info(
            "comment_candidate_processing_started",
            attributes = mapOf("identity_hash" to identityHash, "source" to candidate.source.name),
        )

        // The comment sheet may have refreshed while the candidate was queued. Resolve the
        // author against the latest node tree before falling back to the captured hierarchy path.
        val candidateContext = currentContext() ?: initialContext
        val click = clickCommentCandidate(candidateContext, candidate)
        if (!click.succeeded) {
            finishCandidate(
                identityHash,
                displayName,
                UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
                "未能点击评论用户名称：${click.reason.orEmpty()}",
                PageKind.UNKNOWN,
            )
            return returnToCommentSurface()
        }
        val profile = awaitPage(setOf(PageKind.USER_PROFILE), "等待评论用户主页")
        when (profile.kind) {
            PageKind.HUMAN_INTERVENTION,
            PageKind.LOGIN,
            -> {
                finishCandidate(
                    identityHash,
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
                    identityHash,
                    displayName,
                    UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
                    "评论用户主页未在限定时间内确认",
                    profile.kind,
                )
                return returnToCommentSurface()
            }
        }

        val profileContext = profile.context ?: currentContext()
        if (profileContext == null) {
            finishCandidate(
                identityHash,
                displayName,
                UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
                "评论用户主页内容不可用",
                PageKind.USER_PROFILE,
            )
            return returnToCommentSurface()
        }
        val privateMessageEntry = selectSafePrivateMessageEntry(profileContext)
        if (privateMessageEntry == null) {
            finishCandidate(
                identityHash,
                displayName,
                UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
                "主页没有可确认的纸飞机私信入口",
                PageKind.USER_PROFILE,
            )
            return returnToCommentSurface()
        }
        val entryClick = clickSnapshot(privateMessageEntry, "comment_private_message_entry")
        if (!entryClick.succeeded) {
            finishCandidate(
                identityHash,
                displayName,
                UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
                "纸飞机私信入口点击失败：${entryClick.reason.orEmpty()}",
                PageKind.USER_PROFILE,
            )
            return returnToCommentSurface()
        }
        val directMessage = awaitPage(
            setOf(
                PageKind.DIRECT_MESSAGE,
                PageKind.MESSAGE_EMPTY_REJECTED,
                PageKind.MESSAGE_SEND_FAILED,
                PageKind.PRIVATE_MESSAGE_RESTRICTED,
            ),
            "等待评论用户私信页",
        )
        when (directMessage.kind) {
            PageKind.DIRECT_MESSAGE -> Unit
            PageKind.HUMAN_INTERVENTION,
            PageKind.LOGIN,
            -> {
                finishCandidate(
                    identityHash,
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
                    identityHash,
                    displayName,
                    UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
                    "对方的私信权限不允许当前账号发送",
                    directMessage.kind,
                )
                return returnToCommentSurface()
            }
            else -> {
                finishCandidate(
                    identityHash,
                    displayName,
                    UserTaskRecord.Outcome.MESSAGE_SEND_FAILED,
                    "私信页未在限定时间内确认",
                    directMessage.kind,
                )
                return returnToCommentSurface()
            }
        }

        val probe = probeBlankMessage()
        if (probe.kind == PageKind.MESSAGE_EMPTY_REJECTED) {
            finishCandidate(
                identityHash,
                displayName,
                UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED,
                "抖音提示不能发送空白消息，安全探测完成，未发送真实内容",
                probe.kind,
            )
        } else {
            finishCandidate(
                identityHash,
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

    private suspend fun clickCommentCandidate(
        context: ScreenContext,
        candidate: CommentUserCandidate,
    ): ActionOutcome {
        val author = candidate.authorText?.trim()?.takeIf(String::isNotBlank)
            ?: return ActionOutcome.failure("评论作者名称不可用，拒绝点击未知区域")
        val bounds = candidate.interactionBounds
        val screen = context.screenSize
        val region = NormalizedRect(
            left = ((bounds.left - 48).toFloat() / screen.width).coerceIn(0f, 1f),
            top = ((bounds.top - 48).toFloat() / screen.height).coerceIn(0f, 1f),
            right = ((bounds.right + 48).toFloat() / screen.width).coerceIn(0f, 1f),
            bottom = ((bounds.bottom + 48).toFloat() / screen.height).coerceIn(0f, 1f),
        ).ordered()
        // Resolve the author name in the current node tree first. SelectorEngine can promote a
        // non-clickable text child to its nearest clickable row ancestor, avoiding avatar taps.
        val semantic = selector.select(
            context,
            SelectorRequest(
                name = "comment-author",
                labels = listOf(author),
                requireClickable = true,
                preferredRegion = region,
                minimumScore = 0.45f,
            ),
        ).node
        // OCR can identify a matching author but has no live node path. Never turn that into a
        // root-node click or a coordinate guess; the candidate is safely recorded as skipped.
        if (semantic == null && candidate.interactionHierarchyPath.isEmpty()) {
            return ActionOutcome.failure("评论作者仅由 OCR 识别，缺少可安全定位的节点")
        }
        val target = semantic ?: candidate.interactionHierarchyPath
            .takeIf { it.isNotEmpty() }
            ?.let {
                NodeSnapshot(
                    hierarchyPath = it,
                    text = author,
                    bounds = bounds,
                    isClickable = true,
                    isVisibleToUser = true,
                )
            }
        if (target == null) return ActionOutcome.failure("评论作者名称节点不可重新定位")
        return clickSnapshot(target, "comment_author")
    }

    private fun selectSafePrivateMessageEntry(context: ScreenContext): NodeSnapshot? {
        val selection = selector.select(context, DouyinSelectors.privateMessageEntry)
        val node = selection.node ?: return null
        val searchable = (node.searchableText() + selection.reasons).joinToString(" ").lowercase()
        val unsafe = listOf("客服", "咨询", "购物车", "商品").any(searchable::contains)
        val semanticMessage = listOf("发私信", "私信", "message", "direct message", "paper", "plane")
            .any(searchable::contains)
        return node.takeUnless { unsafe || !semanticMessage }
    }

    private suspend fun awaitPage(
        expected: Set<PageKind>,
        description: String,
        timeoutMs: Long = CANDIDATE_STEP_TIMEOUT_MS,
    ): ObservedPage {
        val attempts = (timeoutMs / PAGE_POLL_INTERVAL_MS).toInt().coerceAtLeast(1)
        var lastKind = PageKind.UNKNOWN
        var lastContext: ScreenContext? = null
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(PAGE_POLL_INTERVAL_MS)
            val context = currentContext() ?: return@repeat
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

    private suspend fun probeBlankMessage(): ObservedPage {
        val context = currentContext()
            ?: return ObservedPage(PageKind.UNKNOWN, null, "私信页不可用")
        val input = selector.select(context, DouyinSelectors.messageInput).node
            ?: return ObservedPage(PageKind.MESSAGE_SEND_FAILED, context, "未找到私信输入框")
        val setText = withLiveNode(input) { node -> gestures.setText(node, " ") }
        if (!setText.succeeded) {
            return ObservedPage(PageKind.MESSAGE_SEND_FAILED, context, "无法在私信输入框放置空格探测")
        }
        delay(BLANK_PROBE_SETTLE_MS)
        val refreshed = currentContext() ?: context
        val send = selector.select(refreshed, DouyinSelectors.messageSendAction).node
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
        return awaitPage(
            expected = setOf(PageKind.MESSAGE_EMPTY_REJECTED, PageKind.MESSAGE_SEND_FAILED),
            description = "空消息探测结果",
            timeoutMs = BLANK_PROBE_TIMEOUT_MS,
        )
    }

    private suspend fun returnToCommentSurface(): Boolean {
        for (attempt in 0..MAX_RETURN_TO_COMMENT_BACKS) {
            val context = currentContext()
            if (context != null) {
                if (CommentSurfaceDetector.detect(context).isCommentSurface) return true
                val detection = pageDetector.detect(context)
                if (detection.kind == PageKind.HUMAN_INTERVENTION || detection.kind == PageKind.LOGIN) {
                    terminal(CommentRuntimeTerminal.Outcome.PAUSED, "返回评论区时出现需要人工处理的页面")
                    return false
                }
                // If the sheet closed but the video remains visible, reopen it via the verified
                // speech-bubble node; OCR text and fixed coordinates are never clicked.
                val commentButton = VideoCommentButtonDetector.find(context)
                if (commentButton != null && detection.kind == PageKind.UNKNOWN) {
                    val click = clickSnapshot(commentButton, "comment_reopen_panel")
                    if (click.succeeded) {
                        val reopened = awaitCommentSurface()
                        if (reopened) return true
                    }
                }
            }
            if (attempt == MAX_RETURN_TO_COMMENT_BACKS) break
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) break
            delay(RETURN_TO_COMMENT_DELAY_MS)
        }
        terminal(CommentRuntimeTerminal.Outcome.FAILED, "无法在限定次数内返回评论区")
        return false
    }

    private suspend fun awaitCommentSurface(): Boolean {
        repeat(COMMENT_SURFACE_POLL_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(PAGE_POLL_INTERVAL_MS)
            val context = currentContext() ?: return@repeat
            if (CommentSurfaceDetector.detect(context).isCommentSurface) return true
        }
        return false
    }

    private fun finishCandidate(
        identityHash: Int,
        displayName: String,
        outcome: UserTaskRecord.Outcome,
        reason: String,
        page: PageKind,
    ) {
        AutomationStore.recordUserTaskFinished(
            identityHash = identityHash,
            outcome = outcome,
            reason = reason,
            page = page,
            remoteUserKey = activeCandidate?.identityKey,
            displayName = displayName,
        )
        logger.info(
            "comment_candidate_processing_finished",
            attributes = mapOf("identity_hash" to identityHash, "outcome" to outcome.name),
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

    private suspend fun scrollCommentPanel(context: ScreenContext): ActionOutcome {
        val scrollable = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.isEnabled && it.isScrollable }
            .filter { it.normalizedBounds(context.screenSize).top >= 0.20f }
            .maxByOrNull { it.bounds.height }
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
    private suspend fun clickSnapshot(target: NodeSnapshot, action: String): ActionOutcome {
        return withLiveNode(target) { node -> gestures.click(node, target.bounds) }
            .also { outcome ->
                logger.info(
                    "comment_action",
                    attributes = mapOf("action" to action, "success" to outcome.succeeded, "route" to outcome.route),
                )
            }
    }

    @Suppress("DEPRECATION")
    private suspend fun withLiveNode(
        target: NodeSnapshot,
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
                    ?: return ActionOutcome.failure("节点已变化，无法重新定位")
                action(liveNode)
            }
        } finally {
            if (liveNode != null && liveNode !== root) liveNode.recycle()
            root.recycle()
        }
    }

    private fun viewportFingerprint(context: ScreenContext): Int = buildString {
        context.nodes.filter { it.isVisibleToUser && it.bounds != ScreenBounds.EMPTY }.forEach { node ->
            append(node.text.orEmpty()).append('|')
                .append(node.bounds.left).append(',').append(node.bounds.top).append(',')
                .append(node.bounds.right).append(',').append(node.bounds.bottom).append(';')
        }
        context.ocrBlocks.forEach { block -> append(block.text).append('|').append(block.bounds).append(';') }
    }.hashCode()

    private fun armTimeout(description: String, timeoutMs: Long = STEP_TIMEOUT_MS) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (running) {
                terminal(CommentRuntimeTerminal.Outcome.FAILED, "评论流程超时：$description")
            }
        }
    }

    private fun terminal(outcome: CommentRuntimeTerminal.Outcome, reason: String) {
        if (!running) return
        running = false
        timeoutJob?.cancel()
        timeoutJob = null
        logger.info(
            "comment_runtime_terminal",
            attributes = mapOf("outcome" to outcome.name, "candidate_count" to ledger.size),
        )
        onTerminal(CommentRuntimeTerminal(outcome, reason))
    }

    private companion object {
        const val STEP_TIMEOUT_MS = 12_000L
        const val INITIAL_ENTRY_TIMEOUT_MS = 60_000L
        const val CANDIDATE_STEP_TIMEOUT_MS = 12_000L
        const val BLANK_PROBE_TIMEOUT_MS = 8_000L
        const val PAGE_POLL_INTERVAL_MS = 350L
        const val BLANK_PROBE_SETTLE_MS = 250L
        const val RETURN_TO_COMMENT_DELAY_MS = 450L
        const val MAX_RETURN_TO_COMMENT_BACKS = 3
        const val COMMENT_SURFACE_POLL_ATTEMPTS = 8
        /** Bounded regression scope; never scroll an unbounded long comment list. */
        const val MAX_COMMENT_SCROLLS = 20
        const val MAX_LIVE_ROOM_EXITS = 3
        const val MAX_LIVE_ROOM_SWIPES = 3
        const val LIVE_ROOM_SWIPE_DURATION_MS = 460L
        const val NEXT_VIDEO_SWIPE_DURATION_MS = 520L
    }
}
