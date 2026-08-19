package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class DirectMessageDisplayNameResolverTest {
    @Test
    fun `uses the name below the largest centered avatar instead of app bar and follow prompt`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(442, 325, 638, 521),
                ),
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "头像",
                    bounds = ScreenBounds(142, 108, 238, 204),
                ),
                NodeSnapshot(
                    text = "佛山红木家具",
                    bounds = ScreenBounds(276, 116, 610, 180),
                ),
                NodeSnapshot(
                    text = "关注，方便以后找到他",
                    bounds = ScreenBounds(118, 710, 910, 790),
                ),
                NodeSnapshot(
                    text = "佛山红木家具",
                    bounds = ScreenBounds(300, 580, 780, 656),
                ),
                NodeSnapshot(
                    contentDescription = "关注",
                    bounds = ScreenBounds(500, 475, 580, 555),
                ),
            ),
        )

        assertEquals(
            "佛山红木家具",
            DirectMessageDisplayNameResolver.fromAccessibility(context, "视频"),
        )
    }

    @Test
    fun `OCR fallback resolves the name below an avatar with a red follow badge`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(360, 300, 720, 660),
                ),
                NodeSnapshot(
                    className = "android.widget.Button",
                    contentDescription = "关注",
                    bounds = ScreenBounds(500, 600, 580, 680),
                ),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("视频", ScreenBounds(700, 160, 820, 220)),
                OcrTextBlock("佛山市南海区鸿福兴家具厂", ScreenBounds(120, 735, 960, 820)),
                OcrTextBlock("你已进入咨询会话", ScreenBounds(140, 850, 940, 930)),
            ),
        )

        assertEquals(
            "佛山市南海区鸿福兴家具厂",
            DirectMessageDisplayNameResolver.fromOcr(context, "视频"),
        )
    }
}
