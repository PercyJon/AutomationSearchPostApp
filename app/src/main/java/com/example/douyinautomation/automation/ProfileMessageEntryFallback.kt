package com.example.douyinautomation.automation

/**
 * Semantic fallback for profile layouts that render the private-message action as a paper-plane
 * icon. Geometry alone is never sufficient: a “咨询客服” action can occupy the same region and
 * may navigate to commerce/cart pages, so candidates must expose a message/paper-plane token.
 */
object ProfileMessageEntryFallback {
    private const val ACTION_BAND_TOP = 0.28f
    private const val ACTION_BAND_BOTTOM = 0.60f
    private const val RIGHT_SIDE_START = 0.80f
    private const val MAX_BUTTON_WIDTH = 0.26f
    private const val MAX_BUTTON_HEIGHT = 0.13f

    fun iconNode(context: ScreenContext): NodeSnapshot? {
        return context.nodes.asSequence()
            .filter { it.isClickable && it.isEnabled && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { node -> isPaperPlaneSemantic(node) }
            .filter { node ->
                val bounds = node.normalizedBounds(context.screenSize)
                bounds.top >= ACTION_BAND_TOP &&
                    bounds.bottom <= ACTION_BAND_BOTTOM &&
                    bounds.right >= RIGHT_SIDE_START &&
                    bounds.width <= MAX_BUTTON_WIDTH &&
                    bounds.height <= MAX_BUTTON_HEIGHT
            }
            // The paper-plane action is the rightmost compact control in this band. Prefer the
            // narrowest candidate first to avoid accidentally choosing a wide follow container.
            .sortedWith(
                compareByDescending<NodeSnapshot> { it.bounds.right }
                    .thenBy { it.bounds.width * it.bounds.height },
            )
            .firstOrNull()
    }

    private fun isPaperPlaneSemantic(node: NodeSnapshot): Boolean {
        val text = node.searchableText().joinToString(" ").lowercase()
        return PrivateMessageEntryRuleStore.evaluate(
            route = PrivateMessageEntryRoute.ICON_FALLBACK,
            searchableText = text,
        ).isAllowed
    }

    fun normalizedPoint(screenSize: ScreenSize, followBounds: ScreenBounds?): NormalizedPoint {
        val width = screenSize.width.coerceAtLeast(1)
        val height = screenSize.height.coerceAtLeast(1)
        val y = followBounds?.centerY?.div(height.toFloat()) ?: 0.43f
        val x = followBounds?.let {
            ((it.right.toFloat() / width) + 0.12f).coerceIn(0.84f, 0.96f)
        } ?: 0.90f
        return NormalizedPoint(x = x, y = y.coerceIn(ACTION_BAND_TOP, ACTION_BAND_BOTTOM))
    }
}
