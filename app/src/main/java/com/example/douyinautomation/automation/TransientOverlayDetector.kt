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

    private val startupAdMarkers = listOf(
        "跳过",
        "广告",
        "开屏广告",
        "广告剩余",
        "立即下载",
        "立即打开",
        "打开应用",
        "下载并打开",
        "sponsored",
        "skip",
    )

    private val startupAdStrongMarkers = setOf(
        "跳过",
        "开屏广告",
        "广告剩余",
        "立即下载",
        "立即打开",
        "打开应用",
        "下载并打开",
        "sponsored",
        "skip",
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

    /**
     * Detects common startup-ad labels. This is a wait-only signal: the controller must not
     * click a skip/download/ad control while the launch surface is changing.
     */
    fun findStartupAd(context: ScreenContext): OverlayMatch? {
        val topLimit = (context.screenSize.height * STARTUP_AD_TOP_REGION_RATIO).toInt()
        val nodeMatches = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { it.bounds.top <= topLimit }
            .flatMap { node ->
                node.searchableText().asSequence().flatMap { value ->
                    TextNormalizer.matchingTerms(value, startupAdMarkers).asSequence()
                        .map { marker -> OverlayMatch(marker, node.bounds) }
                }
            }
        val ocrMatches = context.ocrBlocks.asSequence()
            .filter { it.bounds == ScreenBounds.EMPTY || it.bounds.top <= topLimit }
            .flatMap { block ->
                TextNormalizer.matchingTerms(block.text, startupAdMarkers).asSequence()
                    .map { marker -> OverlayMatch(marker, block.bounds) }
            }
        return (nodeMatches + ocrMatches).firstOrNull { match ->
            // “广告” alone can occur in a normal feed card. Require a stronger skip/download
            // marker for a small node; a wide OCR block is enough for a full-screen ad.
            val strongMarker = match.marker in startupAdStrongMarkers
            val wide = match.bounds == ScreenBounds.EMPTY ||
                match.bounds.width >= (context.screenSize.width * STARTUP_AD_MIN_WIDTH_RATIO).toInt()
            strongMarker || wide
        }
    }

    data class OverlayMatch(
        val marker: String,
        val bounds: ScreenBounds,
    )

    private const val TOP_REGION_RATIO = 0.38f
    // A two-column content card can be wider than 45% of the display. A blocking notification
    // banner, by contrast, spans almost the whole viewport (possibly with a small side margin).
    private const val MIN_OVERLAY_WIDTH_RATIO = 0.75f
    private const val MAX_OVERLAY_SIDE_MARGIN_RATIO = 0.12f
    private const val STARTUP_AD_TOP_REGION_RATIO = 0.55f
    private const val STARTUP_AD_MIN_WIDTH_RATIO = 0.60f
}
