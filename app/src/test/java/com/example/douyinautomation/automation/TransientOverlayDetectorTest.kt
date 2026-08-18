package com.example.douyinautomation.automation

import kotlin.test.Test
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
}
