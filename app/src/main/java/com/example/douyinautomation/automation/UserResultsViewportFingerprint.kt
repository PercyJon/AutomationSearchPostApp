package com.example.douyinautomation.automation

/**
 * Stable, geometry-only cache key for one visible user-results viewport. It intentionally omits
 * node count and text so OCR is reused for overlapping rows as the accessibility tree changes.
 */
object UserResultsViewportFingerprint {

    fun create(context: ScreenContext, topRatio: Float): String = buildString {
        append("viewport|")
        context.nodes.asSequence()
            .filter { node -> node.bounds.width > 0 && node.bounds.height > 0 }
            .filter { node -> node.bounds.right >= context.screenSize.width * ACTION_RAIL_LEFT_RATIO }
            .filter { node -> node.bounds.top >= context.screenSize.height * topRatio }
            .sortedBy { it.bounds.top }
            .forEach { node ->
                append(node.bounds.left).append(',')
                    .append(node.bounds.top).append(',')
                    .append(node.bounds.right).append(',')
                    .append(node.bounds.bottom).append(';')
            }
    }

    private const val ACTION_RAIL_LEFT_RATIO = 0.62f
}
