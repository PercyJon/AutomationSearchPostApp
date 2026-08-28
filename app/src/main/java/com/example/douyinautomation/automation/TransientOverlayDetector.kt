package com.example.douyinautomation.automation

/**
 * Detects in-app overlays that can intercept a tap while they are drawn over Douyin's top
 * controls. A plain "直播" tab is deliberately not enough for the live banner: the detector
 * requires a stronger live-notification phrase and a visible top-region node.
 *
 * IM/group heads-up banners are a separate wait-only class. They cover the search icon and
 * home tabs; tapping them opens chat. Detection is node-first so startup does not wait on OCR.
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

    /**
     * Strong IM-banner action. Optional: some heads-up cards omit it. Constrained to the top
     * band so comment-row "回复" and the bottom "消息" tab cannot match.
     */
    private val imReplyMarkers = listOf("回复")

    fun find(context: ScreenContext): OverlayMatch? =
        findLiveNotification(context) ?: findImBanner(context)

    fun isBlocking(context: ScreenContext): Boolean = find(context) != null

    /**
     * Overlays that must be waited out without a gesture: startup ads and IM/group heads-up
     * banners. Live-feed cards are not included; they are swiped as [PageKind.LIVE_ROOM].
     */
    fun findWaitOnly(context: ScreenContext): OverlayMatch? =
        findStartupAd(context) ?: findImBanner(context)

    /**
     * Top IM/group heads-up that covers search and home tabs. Node-first; existing OCR blocks
     * are used only when already present. Never clicks 回复 or the banner body.
     */
    fun findImBanner(context: ScreenContext): OverlayMatch? {
        val reply = findTopBandReply(context)
        if (reply != null) return reply
        return findGeometryImBanner(context)
    }

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

    private fun findLiveNotification(context: ScreenContext): OverlayMatch? {
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
            val screenHeight = context.screenSize.height
            val wideEnough = match.bounds.width >= (screenWidth * MIN_OVERLAY_WIDTH_RATIO).toInt()
            val spansViewport = match.bounds.left <= (screenWidth * MAX_OVERLAY_SIDE_MARGIN_RATIO).toInt() &&
                match.bounds.right >= (screenWidth * (1f - MAX_OVERLAY_SIDE_MARGIN_RATIO)).toInt()
            // A full-screen live-stream card also spans the viewport and carries an entry label,
            // but it is the feed content itself rather than a banner drawn over the top controls.
            // It must be swiped away (LIVE_ROOM) instead of being waited on as a transient
            // overlay, so exclude nodes that occupy most of the screen height.
            val notFullScreenCard = match.bounds.height < (screenHeight * MAX_OVERLAY_HEIGHT_RATIO).toInt()
            wideEnough && spansViewport && notFullScreenCard
        }
    }

    private fun findTopBandReply(context: ScreenContext): OverlayMatch? {
        val nodeMatch = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { isImTopBand(it.bounds, context.screenSize) }
            .flatMap { node ->
                node.searchableText().asSequence().flatMap { value ->
                    TextNormalizer.matchingTerms(value, imReplyMarkers).asSequence()
                        .map { marker -> OverlayMatch(marker, node.bounds) }
                }
            }
            .firstOrNull()
        if (nodeMatch != null) return nodeMatch
        return context.ocrBlocks.asSequence()
            .filter { it.bounds == ScreenBounds.EMPTY || isImTopBand(it.bounds, context.screenSize) }
            .flatMap { block ->
                TextNormalizer.matchingTerms(block.text, imReplyMarkers).asSequence()
                    .map { marker -> OverlayMatch(marker, block.bounds) }
            }
            .firstOrNull()
    }

    private fun findGeometryImBanner(context: ScreenContext): OverlayMatch? {
        return context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.isClickable && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { isImBannerGeometry(it.bounds, context.screenSize) }
            .filter { node ->
                TextNormalizer.matchingTerms(
                    node.searchableText().joinToString(" "),
                    HOME_CHROME_MARKERS,
                ).isEmpty()
            }
            .map { OverlayMatch(IM_BANNER_MARKER, it.bounds) }
            .firstOrNull()
    }

    private fun isImTopBand(bounds: ScreenBounds, screenSize: ScreenSize): Boolean {
        val topLimit = (screenSize.height * IM_TOP_REGION_RATIO).toInt()
        val maxHeight = (screenSize.height * IM_MAX_HEIGHT_RATIO).toInt()
        return bounds.top <= topLimit && bounds.height < maxHeight
    }

    private fun isImBannerGeometry(bounds: ScreenBounds, screenSize: ScreenSize): Boolean {
        if (!isImTopBand(bounds, screenSize)) return false
        val minHeight = (screenSize.height * IM_MIN_HEIGHT_RATIO).toInt()
        if (bounds.height < minHeight) return false
        val wideEnough = bounds.width >= (screenSize.width * MIN_OVERLAY_WIDTH_RATIO).toInt()
        val spansViewport = bounds.left <= (screenSize.width * MAX_OVERLAY_SIDE_MARGIN_RATIO).toInt() &&
            bounds.right >= (screenSize.width * (1f - MAX_OVERLAY_SIDE_MARGIN_RATIO)).toInt()
        return wideEnough && spansViewport && overlapsSearchChrome(bounds, screenSize)
    }

    /**
     * The banner must occupy the same top-right band as the home search icon. Ordinary home
     * tabs usually stop short of that corner; a small search ImageView is not wide enough to
     * pass [MIN_OVERLAY_WIDTH_RATIO].
     */
    private fun overlapsSearchChrome(bounds: ScreenBounds, screenSize: ScreenSize): Boolean {
        val banner = bounds.normalized(screenSize)
        return banner.right > SEARCH_CHROME_LEFT &&
            banner.left < SEARCH_CHROME_RIGHT &&
            banner.bottom > SEARCH_CHROME_TOP &&
            banner.top < SEARCH_CHROME_BOTTOM
    }

    const val IM_BANNER_MARKER = "im_banner"

    private const val TOP_REGION_RATIO = 0.38f
    // A two-column content card can be wider than 45% of the display. A blocking notification
    // banner, by contrast, spans almost the whole viewport (possibly with a small side margin).
    private const val MIN_OVERLAY_WIDTH_RATIO = 0.75f
    private const val MAX_OVERLAY_SIDE_MARGIN_RATIO = 0.12f
    // A transient banner sits in the top region; a full-screen live feed card occupies the whole
    // display and must be excluded from the overlay definition.
    private const val MAX_OVERLAY_HEIGHT_RATIO = 0.60f
    private const val STARTUP_AD_TOP_REGION_RATIO = 0.55f
    private const val STARTUP_AD_MIN_WIDTH_RATIO = 0.60f
    // IM heads-up sits just under the status bar and covers search/tabs (~10% of the screenshot).
    private const val IM_TOP_REGION_RATIO = 0.18f
    private const val IM_MAX_HEIGHT_RATIO = 0.22f
    private const val IM_MIN_HEIGHT_RATIO = 0.03f
    // Aligned with DouyinSelectors.searchEntry.preferredRegion; detection only, never a tap.
    private const val SEARCH_CHROME_LEFT = 0.76f
    private const val SEARCH_CHROME_TOP = 0f
    private const val SEARCH_CHROME_RIGHT = 1f
    private const val SEARCH_CHROME_BOTTOM = 0.20f
    private val HOME_CHROME_MARKERS = listOf("推荐", "关注", "直播", "同城", "商城", "团购")
}
