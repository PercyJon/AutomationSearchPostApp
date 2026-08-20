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
 * P4-B runtime for the verified profile → first video → comments surface route.
 *
 * This class deliberately stops after reading and deduplicating the comment candidates. It does
 * not open a commenter profile or send a message yet; that is the P4-C action boundary. Every
 * click is resolved from a fresh AccessibilityNodeInfo tree, and the normalized gesture is only
 * used after a semantic/structural node selection has succeeded.
 */
class CommentPrivateMessageRuntime(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val logger: DiagnosticLogger,
    private val inspector: NodeTreeInspector,
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
    }

    suspend fun onObserved(context: ScreenContext, detection: PageDetection) {
        if (!running || context.packageName != TargetAppLauncher.DOUYIN_PACKAGE) return

        val observation = CommentEntrySignalDetector.observe(context)
        val decision = stateMachine.observe(observation)
        when (decision.action) {
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
        val end = CommentPanelEndDetector.detect(context)
        if (end.reached) {
            terminal(CommentRuntimeTerminal.Outcome.COMPLETED, "评论区已读取到底部：${end.marker.orEmpty()}")
            return
        }

        val terms = config?.matchKeywords.orEmpty()
        val extraction = CommentCandidateExtractor.extract(context, terms)
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
        if (ledger.size >= maxUsers) {
            terminal(CommentRuntimeTerminal.Outcome.COMPLETED, "已达到每个视频的评论用户上限")
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
        /** Bounded regression scope; never scroll an unbounded long comment list. */
        const val MAX_COMMENT_SCROLLS = 20
    }
}
