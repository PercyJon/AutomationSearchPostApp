package com.example.douyinautomation.automation

/**
 * Geometry-only fallback for profile layouts that render the private-message action as a paper
 * plane icon without exposing a text/content-description label. It is deliberately constrained to
 * the profile action band and the narrow right-side button, so it cannot select the avatar, follow
 * button, grid, or top navigation controls.
 */
object ProfileMessageEntryFallback {
    private const val ACTION_BAND_TOP = 0.28f
    private const val ACTION_BAND_BOTTOM = 0.60f
    private const val RIGHT_SIDE_START = 0.80f
    private const val MAX_BUTTON_WIDTH = 0.26f
    private const val MAX_BUTTON_HEIGHT = 0.13f

    fun iconNode(context: ScreenContext): NodeSnapshot? {
        val width = context.screenSize.width.coerceAtLeast(1)
        val height = context.screenSize.height.coerceAtLeast(1)
        return context.nodes.asSequence()
            .filter { it.isClickable && it.isEnabled && it.bounds.width > 0 && it.bounds.height > 0 }
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
