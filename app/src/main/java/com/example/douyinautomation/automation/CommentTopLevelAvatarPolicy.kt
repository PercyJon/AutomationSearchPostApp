package com.example.douyinautomation.automation

/**
 * Top-level comment avatars share one left column. Nested replies are indented to the right
 * of that column and must never become private-message targets.
 *
 * The column is frozen from the first comment viewport of a video ([columnLeftPx] = minimum
 * avatar.left). Later frames keep that value so a reply-only slice cannot redefine the rail.
 */
internal object CommentTopLevelAvatarPolicy {
    /**
     * Horizontal jitter around the frozen column, in dp.
     *
     * Douyin reply indent is typically ~40dp. 16dp covers same-row layout jitter without
     * accepting the nested-reply gutter.
     */
    const val COLUMN_TOLERANCE_DP = 16f

    data class Partition(
        val topLevel: List<CommentUserCandidate>,
        val replies: List<CommentUserCandidate>,
    )

    fun tolerancePx(density: Float): Int =
        (COLUMN_TOLERANCE_DP * density.coerceAtLeast(0.5f)).toInt().coerceAtLeast(1)

    fun columnLeftPx(avatarLefts: Iterable<Int>): Int? = avatarLefts.minOrNull()

    fun isTopLevelAvatar(avatarLeft: Int, columnLeft: Int, density: Float): Boolean =
        kotlin.math.abs(avatarLeft - columnLeft) <= tolerancePx(density)

    fun partition(
        candidates: List<CommentUserCandidate>,
        columnLeft: Int,
        density: Float,
    ): Partition {
        val topLevel = ArrayList<CommentUserCandidate>()
        val replies = ArrayList<CommentUserCandidate>()
        for (candidate in candidates) {
            val left = candidate.avatarBounds?.left
            if (left != null && isTopLevelAvatar(left, columnLeft, density)) {
                topLevel += candidate
            } else {
                replies += candidate
            }
        }
        return Partition(topLevel = topLevel, replies = replies)
    }

    /**
     * Leading-row safety must key off the first *top-level* avatar, not the first-by-top
     * node. A nested reply can sit above the next parent comment once the parent has
     * scrolled away or the thread is expanded.
     */
    fun leadingTopLevelAvatar(
        firstVisible: ScreenBounds?,
        topLevelCandidates: List<CommentUserCandidate>,
        columnLeft: Int,
        density: Float,
    ): ScreenBounds? {
        if (firstVisible != null && isTopLevelAvatar(firstVisible.left, columnLeft, density)) {
            return firstVisible
        }
        return topLevelCandidates
            .mapNotNull { it.avatarBounds }
            .filter { isTopLevelAvatar(it.left, columnLeft, density) }
            .minByOrNull { it.top }
    }
}
