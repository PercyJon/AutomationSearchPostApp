package com.example.douyinautomation.automation

/**
 * Safety policy for returning from a commenter profile to the original video comments.
 *
 * OCR is useful to recognize a page, but never sufficiently specific to tap a comment-panel tab.
 * Reopening is permitted only through a compact accessibility node located in the video action
 * rail.  In particular, this excludes “评论 / AI解析” panel tabs.
 */
internal object CommentPanelRecoveryPolicy {
    fun reopenTarget(
        surface: CommentSurfaceDetection,
        target: CommentButtonTarget?,
        screenSize: ScreenSize,
    ): CommentButtonTarget.AccessibilityNode? {
        if (surface.isCommentSurface) return null
        val nodeTarget = target as? CommentButtonTarget.AccessibilityNode ?: return null
        val node = nodeTarget.node
        val bounds = node.normalizedBounds(screenSize)
        val semantic = node.searchableText().joinToString(" ").let(TextNormalizer::normalize)
        if (semantic.contains("ai解析") || semantic.contains("ai分析") || semantic.contains("智能解析")) {
            return null
        }
        return nodeTarget.takeIf {
            bounds.left >= MIN_RAIL_LEFT &&
                bounds.top in MIN_RAIL_TOP..MAX_RAIL_TOP &&
                bounds.width <= MAX_RAIL_WIDTH &&
                bounds.height <= MAX_RAIL_HEIGHT
        }
    }

    private const val MIN_RAIL_LEFT = 0.76f
    private const val MIN_RAIL_TOP = 0.28f
    private const val MAX_RAIL_TOP = 0.90f
    private const val MAX_RAIL_WIDTH = 0.24f
    private const val MAX_RAIL_HEIGHT = 0.18f
}
