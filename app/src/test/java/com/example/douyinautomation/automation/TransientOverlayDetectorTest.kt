package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransientOverlayDetectorTest {
    @Test
    fun `strong live notification in top banner is blocking`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "某用户正在直播 进入直播间",
                    isVisibleToUser = true,
                    isClickable = true,
                    bounds = ScreenBounds(0, 96, 1080, 260),
                ),
            ),
        )

        val match = TransientOverlayDetector.find(context)

        assertNotNull(match)
        assertTrue(match.marker.contains("正在直播"))
    }

    @Test
    fun `ordinary live tab does not block`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "直播",
                    isVisibleToUser = true,
                    isClickable = true,
                    bounds = ScreenBounds(600, 240, 760, 330),
                ),
            ),
        )

        assertFalse(TransientOverlayDetector.isBlocking(context))
    }

    @Test
    fun `live phrase outside top controls does not block`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "正在直播",
                    isVisibleToUser = true,
                    bounds = ScreenBounds(60, 1200, 500, 1280),
                ),
            ),
        )

        assertNull(TransientOverlayDetector.find(context))
    }

    @Test
    fun `profile live badge attached to avatar does not block`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "直播中",
                    isVisibleToUser = true,
                    isClickable = true,
                    bounds = ScreenBounds(112, 540, 296, 598),
                ),
            ),
        )

        assertFalse(TransientOverlayDetector.isBlocking(context))
    }

    @Test
    fun `full-screen live stream card is not a transient overlay`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    contentDescription = "点击进入直播间按钮",
                    isVisibleToUser = true,
                    isClickable = true,
                    bounds = ScreenBounds(0, 0, 1080, 2265),
                ),
            ),
        )

        assertFalse(TransientOverlayDetector.isBlocking(context))
    }

    @Test
    fun `live badge on a two column content card does not block`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "直播中",
                    isVisibleToUser = true,
                    isClickable = true,
                    bounds = ScreenBounds(12, 531, 534, 1227),
                ),
            ),
        )

        assertFalse(TransientOverlayDetector.isBlocking(context))
    }

    @Test
    fun `startup skip marker is wait only and does not require a wide banner`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "跳过",
                    isVisibleToUser = true,
                    isClickable = true,
                    bounds = ScreenBounds(930, 80, 1040, 150),
                ),
            ),
        )

        val match = TransientOverlayDetector.findStartupAd(context)

        assertNotNull(match)
        assertEquals("跳过", match.marker)
    }

    @Test
    fun `ordinary feed ad label alone is not treated as startup ad`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "广告",
                    isVisibleToUser = true,
                    bounds = ScreenBounds(800, 300, 860, 340),
                ),
            ),
        )

        assertNull(TransientOverlayDetector.findStartupAd(context))
    }

    @Test
    fun `top im banner with reply is wait only and blocking`() {
        val context = screenshotImBanner(includeReply = true)

        val match = TransientOverlayDetector.findImBanner(context)

        assertNotNull(match)
        assertEquals("回复", match?.marker)
        assertTrue(TransientOverlayDetector.isBlocking(context))
        assertEquals("回复", TransientOverlayDetector.findWaitOnly(context)?.marker)
    }

    @Test
    fun `wide clickable top banner covering search is wait only without reply`() {
        val context = screenshotImBanner(includeReply = false)

        val match = TransientOverlayDetector.findImBanner(context)

        assertNotNull(match)
        assertEquals(TransientOverlayDetector.IM_BANNER_MARKER, match?.marker)
        assertEquals(TransientOverlayDetector.IM_BANNER_MARKER, TransientOverlayDetector.findWaitOnly(context)?.marker)
    }

    @Test
    fun `bottom messages tab is not an im banner`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(text = "首页", bounds = ScreenBounds(40, 2280, 200, 2380)),
                NodeSnapshot(text = "消息", isClickable = true, bounds = ScreenBounds(620, 2280, 780, 2380)),
            ),
        )

        assertNull(TransientOverlayDetector.findImBanner(context))
        assertFalse(TransientOverlayDetector.isBlocking(context))
    }

    @Test
    fun `comment reply outside the top band is not an im banner`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(text = "回复", isClickable = true, bounds = ScreenBounds(470, 1320, 570, 1380)),
                NodeSnapshot(text = "回复", isClickable = true, bounds = ScreenBounds(470, 1710, 570, 1770)),
            ),
        )

        assertNull(TransientOverlayDetector.findImBanner(context))
    }

    @Test
    fun `home tabs and small search icon are not an im banner`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(text = "直播", isClickable = true, bounds = ScreenBounds(80, 90, 180, 160)),
                NodeSnapshot(text = "关注", isClickable = true, bounds = ScreenBounds(420, 90, 520, 160)),
                NodeSnapshot(text = "推荐", isClickable = true, bounds = ScreenBounds(700, 90, 820, 160)),
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "搜索",
                    isClickable = true,
                    bounds = ScreenBounds(960, 80, 1048, 168),
                ),
            ),
        )

        assertNull(TransientOverlayDetector.findImBanner(context))
        assertFalse(TransientOverlayDetector.isBlocking(context))
    }

    @Test
    fun `existing ocr reply on a wide banner is used without extra capture`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "岛屿",
                    isVisibleToUser = true,
                    isClickable = true,
                    bounds = ScreenBounds(24, 88, 1056, 268),
                ),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("岛屿", ScreenBounds(160, 110, 280, 160)),
                OcrTextBlock("回复", ScreenBounds(860, 120, 1000, 200)),
            ),
        )

        val match = TransientOverlayDetector.findImBanner(context)

        assertNotNull(match)
        assertEquals("回复", match?.marker)
    }

    @Test
    fun `ocr reply in the top band without a banner node is not an im banner`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            ocrBlocks = listOf(
                OcrTextBlock("回复", ScreenBounds(860, 120, 1000, 200)),
            ),
        )

        assertNull(TransientOverlayDetector.findImBanner(context))
    }

    @Test
    fun `search keyword field is not an im banner`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.EditText",
                    text = "红木沙发",
                    isEditable = true,
                    isClickable = true,
                    bounds = ScreenBounds(24, 88, 1056, 200),
                ),
                NodeSnapshot(text = "搜索", isClickable = true, bounds = ScreenBounds(920, 96, 1048, 192)),
            ),
        )

        assertNull(TransientOverlayDetector.findImBanner(context))
        assertNull(TransientOverlayDetector.findLiveNotification(context))
        assertFalse(TransientOverlayDetector.isBlocking(context))
    }

    @Test
    fun `chat thread reply in the top band is not an im banner`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(text = "回复", isClickable = true, bounds = ScreenBounds(860, 180, 1000, 250)),
                NodeSnapshot(
                    className = "android.widget.EditText",
                    hintText = "发送消息",
                    isEditable = true,
                    isClickable = true,
                    bounds = ScreenBounds(40, 2200, 880, 2320),
                ),
            ),
        )

        assertNull(TransientOverlayDetector.findImBanner(context))
        assertNull(TransientOverlayDetector.findLiveNotification(context))
        assertFalse(TransientOverlayDetector.isBlocking(context))
    }

    private fun screenshotImBanner(includeReply: Boolean): ScreenContext {
        val nodes = mutableListOf(
            NodeSnapshot(
                text = "岛屿",
                isVisibleToUser = true,
                isClickable = true,
                bounds = ScreenBounds(24, 88, 1056, 268),
            ),
            NodeSnapshot(text = "首页", bounds = ScreenBounds(40, 2280, 200, 2380)),
            NodeSnapshot(text = "消息", isClickable = true, bounds = ScreenBounds(620, 2280, 780, 2380)),
            NodeSnapshot(
                className = "android.widget.ImageView",
                contentDescription = "搜索",
                isClickable = true,
                bounds = ScreenBounds(960, 80, 1048, 168),
            ),
        )
        if (includeReply) {
            nodes += NodeSnapshot(
                text = "回复",
                isVisibleToUser = true,
                isClickable = true,
                bounds = ScreenBounds(860, 128, 1020, 228),
            )
        }
        return ScreenContext(screenSize = ScreenSize(1080, 2412), nodes = nodes)
    }
}
