package com.example.douyinautomation.automation

/**
 * Identifies the shell of a Douyin user-result row from the stable right-side action anchor.
 *
 * Current Douyin builds often expose the row name and metadata only as custom-rendered pixels,
 * while exposing the right-side "关注按钮" or "发私信" action as an accessibility node.  The detector therefore
 * returns the wide row shell, never the avatar or follow button itself.  The controller can then
 * tap a bounded name/content area inside that row.
 */
data class StructuralUserRowMatch(
    val row: NodeSnapshot,
    val anchor: NodeSnapshot,
    val source: Source,
) {
    enum class Source {
        STRICT_ANCESTOR,
        VERTICAL_OVERLAP,
        /** Two OCR samples verified the first custom-rendered user card; P0-only fallback. */
        OCR_ASSISTED_STABLE,
    }
}

/** The row action tells us whether this account is already followed or only follows us back. */
enum class UserFollowActionState {
    FOLLOW,
    FOLLOW_BACK,
    FOLLOWING,
    UNKNOWN,
}

object StructuralUserRowDetector {
    private const val TOP_RATIO = 0.14f
    // Derived from the verified 1080×2412 reference surface. Row eligibility must scale with
    // the actual display height instead of preserving physical-pixel thresholds across devices.
    private const val MIN_ROW_HEIGHT_RATIO = 0.075f
    private const val MAX_ROW_HEIGHT_RATIO = 0.166f
    private const val MIN_ROW_WIDTH_RATIO = 0.90f
    private const val MAX_ROW_LEFT_RATIO = 0.05f

    fun find(context: ScreenContext): StructuralUserRowMatch? = findFromAnchorTop(context, null)

    /** Finds the first row after a previously inspected row without scrolling past visible rows. */
    fun findAfter(context: ScreenContext, anchorBottom: Float): StructuralUserRowMatch? =
        findFromAnchorTop(context, anchorBottom)

    private fun findFromAnchorTop(context: ScreenContext, minimumAnchorTop: Float?): StructuralUserRowMatch? {
        val screenWidth = context.screenSize.width.coerceAtLeast(1)
        val screenHeight = context.screenSize.height.coerceAtLeast(1)
        val anchors = context.nodes.asSequence()
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { node ->
                isFollowActionAnchor(node, screenWidth) &&
                    node.bounds.top >= (screenHeight * TOP_RATIO).toInt() &&
                    (minimumAnchorTop == null || node.bounds.top > minimumAnchorTop)
            }
            .sortedBy { it.bounds.top }
            .toList()

        for (anchor in anchors) {
            val strict = context.nodes.asSequence()
                .filter { candidate -> isWideRow(candidate, screenWidth, screenHeight) }
                .filter { candidate -> isStrictAncestor(candidate.hierarchyPath, anchor.hierarchyPath) }
                .sortedWith(compareBy<NodeSnapshot> { it.bounds.height }.thenByDescending { it.hierarchyPath.size })
                .firstOrNull()
            if (strict != null) {
                return StructuralUserRowMatch(strict, anchor, StructuralUserRowMatch.Source.STRICT_ANCESTOR)
            }

            // Accessibility snapshots can omit a wrapper or briefly report a different child
            // index during a RecyclerView transition.  A wide row whose vertical span contains
            // the follow anchor is still a safe structural match; choose the smallest/nearest
            // such shell so a parent ViewPager is never selected.
            val overlap = context.nodes.asSequence()
                .filter { candidate -> isWideRow(candidate, screenWidth, screenHeight) }
                .filter { candidate -> candidate.bounds.top <= anchor.bounds.centerY.toInt() }
                .filter { candidate -> candidate.bounds.bottom >= anchor.bounds.centerY.toInt() }
                .sortedWith(
                    compareBy<NodeSnapshot> { it.bounds.height }
                        .thenBy { kotlin.math.abs(it.bounds.centerY - anchor.bounds.centerY) },
                )
                .firstOrNull()
            if (overlap != null) {
                return StructuralUserRowMatch(overlap, anchor, StructuralUserRowMatch.Source.VERTICAL_OVERLAP)
            }
        }
        return null
    }

    /**
     * Returns the visible state of the right-side action for a matched row. Douyin may expose the
     * state as a button content description, button text, or a short child label depending on the
     * build; inspect the anchor and the nearby right-side nodes before falling back to UNKNOWN.
     */
    fun followActionState(context: ScreenContext, match: StructuralUserRowMatch): UserFollowActionState {
        val row = match.row.bounds
        val rightSideValues = context.nodes
            .asSequence()
            .filter { node ->
                node.bounds.width > 0 &&
                    node.bounds.centerY in (row.top.toFloat()..row.bottom.toFloat()) &&
                    node.bounds.centerX >= context.screenSize.width * 0.62f &&
                    node.bounds.centerY in (match.anchor.bounds.top.toFloat() - match.anchor.bounds.height)..(match.anchor.bounds.bottom.toFloat() + match.anchor.bounds.height)
            }
            .flatMap { node -> node.searchableText().asSequence() }
            .toList() + match.anchor.searchableText()

        val normalized = rightSideValues.map(TextNormalizer::normalize)
        return when {
            normalized.any { value -> value.contains("回关") || value.contains("follow back") } -> UserFollowActionState.FOLLOW_BACK
            normalized.any { value -> value.contains("已关注") || value.contains("互相关注") || value == "following" } -> UserFollowActionState.FOLLOWING
            normalized.any { value ->
                value == "关注" || value == "关注按钮" || value.contains("发私信") || value == "follow"
            } -> UserFollowActionState.FOLLOW
            else -> UserFollowActionState.UNKNOWN
        }
    }

    fun anchorCount(context: ScreenContext): Int {
        val minTop = (context.screenSize.height.coerceAtLeast(1) * TOP_RATIO).toInt()
        return context.nodes.count { node ->
            node.bounds.width > 0 &&
                node.bounds.height > 0 &&
                isFollowActionAnchor(node, context.screenSize.width) &&
                node.bounds.top >= minTop
        }
    }

    private fun isFollowActionAnchor(node: NodeSnapshot, screenWidth: Int): Boolean {
        if (node.bounds.right < screenWidth * 0.62f) return false
        val values = node.searchableText().map(TextNormalizer::normalize)
        return values.any { value ->
                value.contains("关注按钮") ||
                value.contains("发私信") ||
                value.contains("回关") ||
                value == "关注" ||
                value == "已关注" ||
                value == "互相关注" ||
                value == "follow" ||
                value == "following" ||
                value == "follow back"
        }
    }

    private fun isWideRow(node: NodeSnapshot, screenWidth: Int, screenHeight: Int): Boolean =
        node.bounds.left <= (screenWidth * MAX_ROW_LEFT_RATIO).toInt() &&
            node.bounds.right >= (screenWidth * MIN_ROW_WIDTH_RATIO).toInt() &&
            node.bounds.top >= (screenHeight * TOP_RATIO).toInt() &&
            node.bounds.height in rowHeightRange(screenHeight)

    internal fun rowHeightRange(screenHeight: Int): IntRange {
        val height = screenHeight.coerceAtLeast(1)
        return (height * MIN_ROW_HEIGHT_RATIO).toInt()..(height * MAX_ROW_HEIGHT_RATIO).toInt()
    }

    private fun isStrictAncestor(ancestor: List<Int>, descendant: List<Int>): Boolean =
        ancestor.size < descendant.size && ancestor.indices.all { index -> ancestor[index] == descendant[index] }
}
