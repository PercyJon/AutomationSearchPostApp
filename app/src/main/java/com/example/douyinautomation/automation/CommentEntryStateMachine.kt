package com.example.douyinautomation.automation

/** Surface-level actions that the future comment runner may dispatch after a verified state. */
enum class CommentEntryAction {
    NONE,
    OPEN_FIRST_VIDEO,
    OPEN_COMMENTS,
    READ_COMMENTS,
    PAUSE_FOR_MANUAL_HANDOFF,
    STOP,
}

/** P4-A state for the bounded profile → first video → comment panel route. */
enum class CommentEntryStage {
    WAITING_FOR_PROFILE,
    WAITING_FOR_VIDEO,
    WAITING_FOR_COMMENTS,
    READY_TO_READ,
    PAUSED_FOR_MANUAL_HANDOFF,
    FAILED,
}

data class CommentEntryObservation(
    val page: PageKind,
    val hasFirstVideoTarget: Boolean = false,
    val hasVideoSurface: Boolean = false,
    val hasCommentEntry: Boolean = false,
    val commentButton: NodeSnapshot? = null,
    val commentSurface: CommentSurfaceDetection? = null,
    val riskReason: String? = null,
) {
    val isCommentSurfaceReady: Boolean
        get() = commentSurface?.isCommentSurface == true
}

data class CommentEntryDecision(
    val stage: CommentEntryStage,
    val action: CommentEntryAction,
    val reason: String,
)

/**
 * Pure P4-A transition policy. Actions are emitted only after the preceding page/post-condition
 * is observed; callers must execute the action and inspect a fresh ScreenContext before asking
 * for the next decision. Risk, login, and unknown screens always pause for a person.
 */
class CommentEntryStateMachine {
    var stage: CommentEntryStage = CommentEntryStage.WAITING_FOR_PROFILE
        private set

    fun observe(observation: CommentEntryObservation): CommentEntryDecision {
        observation.riskReason?.let { return pause("检测到风险或验证码：$it") }
        when (observation.page) {
            PageKind.HUMAN_INTERVENTION -> return pause("检测到风险或验证码页面")
            PageKind.LOGIN -> return pause("抖音需要登录后才能继续")
            PageKind.OUTSIDE_TARGET -> return pause("当前前台不是抖音")
            // PageDetector V0 intentionally has no video/comment page kind yet. UNKNOWN is
            // accepted only when the dedicated structural detector has already supplied a
            // video surface or a verified comment panel; incidental unknown pages still pause.
            PageKind.UNKNOWN -> if (!observation.hasVideoSurface && observation.commentSurface == null) {
                return pause("当前页面无法确认，暂不执行点击")
            }
            else -> Unit
        }

        return when (stage) {
            CommentEntryStage.WAITING_FOR_PROFILE -> {
                if (observation.page != PageKind.USER_PROFILE) {
                    decision(CommentEntryAction.NONE, "等待确认用户主页")
                } else if (observation.hasFirstVideoTarget) {
                    stage = CommentEntryStage.WAITING_FOR_VIDEO
                    decision(CommentEntryAction.OPEN_FIRST_VIDEO, "已确认主页，准备打开第一个视频")
                } else {
                    decision(CommentEntryAction.NONE, "已在用户主页，但暂未确认第一个视频入口")
                }
            }

            CommentEntryStage.WAITING_FOR_VIDEO -> {
                if (observation.hasVideoSurface && observation.hasCommentEntry) {
                    stage = CommentEntryStage.WAITING_FOR_COMMENTS
                    decision(CommentEntryAction.OPEN_COMMENTS, "已确认视频页面，准备打开评论区")
                } else {
                    decision(CommentEntryAction.NONE, "等待视频页面和评论入口稳定")
                }
            }

            CommentEntryStage.WAITING_FOR_COMMENTS -> {
                if (observation.isCommentSurfaceReady) {
                    stage = CommentEntryStage.READY_TO_READ
                    decision(CommentEntryAction.READ_COMMENTS, "已确认评论区，可读取评论")
                } else {
                    decision(CommentEntryAction.NONE, "等待评论区加载完成")
                }
            }

            CommentEntryStage.READY_TO_READ ->
                decision(CommentEntryAction.NONE, "评论区已就绪")

            CommentEntryStage.PAUSED_FOR_MANUAL_HANDOFF ->
                decision(CommentEntryAction.PAUSE_FOR_MANUAL_HANDOFF, "仍需人工处理后才能继续")

            CommentEntryStage.FAILED ->
                decision(CommentEntryAction.STOP, "评论入口状态机已失败")
        }
    }

    fun timeout(reason: String): CommentEntryDecision {
        stage = CommentEntryStage.FAILED
        return decision(CommentEntryAction.STOP, "评论流程超时：$reason")
    }

    fun reset() {
        stage = CommentEntryStage.WAITING_FOR_PROFILE
    }

    private fun pause(reason: String): CommentEntryDecision {
        stage = CommentEntryStage.PAUSED_FOR_MANUAL_HANDOFF
        return decision(CommentEntryAction.PAUSE_FOR_MANUAL_HANDOFF, reason)
    }

    private fun decision(action: CommentEntryAction, reason: String) = CommentEntryDecision(
        stage = stage,
        action = action,
        reason = reason,
    )
}

/**
 * Detects only the structural signals needed by P4-A. It does not choose a fixed pixel or click
 * an avatar. A clickable image-like node in the lower profile region is accepted as the first
 * video target only when the profile exposes a “作品/视频” anchor as well.
 */
object CommentEntrySignalDetector {
    private val videoMarkers = listOf("视频", "播放", "暂停", "作品")

    fun observe(context: ScreenContext): CommentEntryObservation {
        val page = PageDetector().detect(context).kind
        val normalizedTexts = (context.nodeText() + context.ocrText()).map(TextNormalizer::normalize)
        val hasVideoMarker = normalizedTexts.any { text -> videoMarkers.any(text::contains) }
        val commentButton = VideoCommentButtonDetector.find(context)
        // Comment text in a caption is not permission to click. The state machine is only told
        // that a comment entry exists when the semantic/structural speech-bubble selector found
        // a clickable node in the video action rail.
        val hasCommentEntry = commentButton != null
        val hasFirstVideoTarget = page == PageKind.USER_PROFILE && hasVideoMarker &&
            context.nodes.any { node ->
                val normalizedClass = TextNormalizer.normalize(node.className)
                val imageLike = normalizedClass.contains("imageview") ||
                    normalizedClass.contains("surfaceview") ||
                    normalizedClass.contains("textureview")
                val inContentRegion = node.normalizedBounds(context.screenSize).top >= 0.24f
                node.isVisibleToUser && node.isClickable && imageLike &&
                    node.bounds.width >= MIN_VIDEO_EDGE && node.bounds.height >= MIN_VIDEO_EDGE &&
                    inContentRegion
            }
        val hasVideoSurface = page == PageKind.UNKNOWN && hasVideoMarker &&
            context.nodes.any { it.isVisibleToUser && it.bounds.width >= MIN_VIDEO_EDGE }
        return CommentEntryObservation(
            page = page,
            hasFirstVideoTarget = hasFirstVideoTarget,
            hasVideoSurface = hasVideoSurface,
            hasCommentEntry = hasCommentEntry,
            commentButton = commentButton,
            commentSurface = CommentSurfaceDetector.detect(context),
        )
    }

    private const val MIN_VIDEO_EDGE = 80
}
