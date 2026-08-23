package com.example.douyinautomation.automation

/**
 * Chooses the scroll container inside a visible Douyin comment sheet.
 *
 * The feed itself exposes several full-screen ViewPagers as scrollable accessibility nodes.  A
 * comment panel can also expose a sheet ViewPager around its list, so rank the concrete list
 * widgets first and keep the fallback restricted to the lower half of the screen.
 */
internal object CommentPanelScrollTargetSelector {
    fun select(context: ScreenContext): NodeSnapshot? = context.nodes.asSequence()
        .filter { it.isVisibleToUser && it.isEnabled && it.isScrollable }
        .filter { candidate ->
            val bounds = candidate.normalizedBounds(context.screenSize)
            bounds.top >= MIN_COMMENT_SHEET_TOP &&
                bounds.bottom >= MIN_COMMENT_SHEET_BOTTOM &&
                bounds.width >= MIN_COMMENT_SHEET_WIDTH
        }
        .sortedWith(
            compareByDescending<NodeSnapshot>(::containerPriority)
                .thenByDescending { it.bounds.top }
                .thenByDescending { it.bounds.height },
        )
        .firstOrNull()

    private fun containerPriority(node: NodeSnapshot): Int {
        val className = node.className.orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        return when {
            className.contains("RecyclerView", ignoreCase = true) -> 4
            className.contains("ListView", ignoreCase = true) -> 3
            viewId.contains("comment", ignoreCase = true) || viewId.contains("recycler", ignoreCase = true) -> 2
            className.contains("ViewPager", ignoreCase = true) -> 0
            else -> 1
        }
    }

    private const val MIN_COMMENT_SHEET_TOP = 0.20f
    private const val MIN_COMMENT_SHEET_BOTTOM = 0.55f
    private const val MIN_COMMENT_SHEET_WIDTH = 0.60f
}
