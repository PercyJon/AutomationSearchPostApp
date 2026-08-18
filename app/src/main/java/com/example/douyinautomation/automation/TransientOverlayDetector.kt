package com.example.douyinautomation.automation

/**
 * Detects an in-app live notification that can intercept a tap while it is drawn over Douyin's
 * top controls. A plain "直播" tab is deliberately not enough: the detector requires a stronger
 * live-notification phrase and a visible top-region node, so ordinary search tabs remain usable.
 */
object TransientOverlayDetector {
    private val strongLiveMarkers = listOf(
        "正在直播",
        "直播中",
        "直播通知",
        "直播提醒",
        "进入直播间",
        "观看直播",
        "立即观看",
        "点击进入直播间",
    )

    fun find(context: ScreenContext): OverlayMatch? {
        val topLimit = (context.screenSize.height * TOP_REGION_RATIO).toInt()
        val candidates = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { it.bounds.top <= topLimit }
            .flatMap { node ->
                node.searchableText().asSequence()
                    .flatMap { value ->
                        TextNormalizer.matchingTerms(value, strongLiveMarkers).asSequence()
                            .map { marker -> OverlayMatch(marker, node.bounds) }
                    }
            }
            .toList()

        // A profile's own “直播中” badge is usually attached to the avatar and reports a tall
        // avatar-sized bounds rectangle.  Height alone therefore produces a false positive and
        // can block the real profile “发私信” button forever.  A transient notification banner
        // must span a substantial portion of the display; small live badges are not overlays.
        return candidates.firstOrNull { match ->
            val screenWidth = context.screenSize.width
            val wideEnough = match.bounds.width >= (screenWidth * MIN_OVERLAY_WIDTH_RATIO).toInt()
            val spansViewport = match.bounds.left <= (screenWidth * MAX_OVERLAY_SIDE_MARGIN_RATIO).toInt() &&
                match.bounds.right >= (screenWidth * (1f - MAX_OVERLAY_SIDE_MARGIN_RATIO)).toInt()
            wideEnough && spansViewport
        }
    }

    fun isBlocking(context: ScreenContext): Boolean = find(context) != null

    data class OverlayMatch(
        val marker: String,
        val bounds: ScreenBounds,
    )

    private const val TOP_REGION_RATIO = 0.38f
    // A two-column content card can be wider than 45% of the display. A blocking notification
    // banner, by contrast, spans almost the whole viewport (possibly with a small side margin).
    private const val MIN_OVERLAY_WIDTH_RATIO = 0.75f
    private const val MAX_OVERLAY_SIDE_MARGIN_RATIO = 0.12f
}
