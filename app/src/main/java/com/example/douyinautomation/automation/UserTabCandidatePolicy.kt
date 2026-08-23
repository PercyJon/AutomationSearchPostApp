package com.example.douyinautomation.automation

/** Safety gate for selecting the “用户” category tab from a verified search-result surface. */
object UserTabCandidatePolicy {

    fun isSafeCandidate(
        candidate: NodeSnapshot,
        selection: SelectionResult,
        context: ScreenContext,
    ): Boolean {
        val directLabel = listOfNotNull(candidate.text, candidate.contentDescription, candidate.hintText)
            .any { raw -> DouyinLabels.users.any { label -> TextNormalizer.normalize(raw) == TextNormalizer.normalize(label) } }
        val ancestorLabel = selection.reasons.any { reason ->
            DouyinLabels.users.any { label -> reason == "label=$label" }
        }
        val bounds = candidate.bounds
        val tabStrip = context.nodes.firstOrNull { node ->
            node.className?.contains("HorizontalScrollView", ignoreCase = true) == true &&
                node.bounds.top <= bounds.top &&
                node.bounds.bottom >= bounds.bottom
        }
        val viewportLeft = tabStrip?.bounds?.left ?: 0
        val viewportRight = tabStrip?.bounds?.right
            ?: (context.screenSize.width * VIEWPORT_FALLBACK_RIGHT_RATIO).toInt()
        val fullyOnScreen = bounds.left >= 0 &&
            bounds.top >= 0 &&
            bounds.right <= context.screenSize.width &&
            bounds.bottom <= context.screenSize.height &&
            bounds.width > 0 &&
            bounds.height > 0
        val insideVisibleTabStrip = bounds.left >= viewportLeft && bounds.right <= viewportRight
        val topBandBottom = (context.screenSize.height * TOP_BAND_BOTTOM_RATIO).toInt()
        val compactTabHeight = bounds.height <= (context.screenSize.height * MAX_TAB_HEIGHT_RATIO).toInt()
        val inTopTabBand = bounds.top <= topBandBottom && bounds.bottom <= topBandBottom
        return (directLabel || ancestorLabel) &&
            fullyOnScreen &&
            insideVisibleTabStrip &&
            compactTabHeight &&
            inTopTabBand
    }

    private const val VIEWPORT_FALLBACK_RIGHT_RATIO = 0.84f
    private const val TOP_BAND_BOTTOM_RATIO = 0.36f
    private const val MAX_TAB_HEIGHT_RATIO = 0.14f
}
