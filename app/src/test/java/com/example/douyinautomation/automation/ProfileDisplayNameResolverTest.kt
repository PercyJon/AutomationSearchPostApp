package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileDisplayNameResolverTest {
    @Test
    fun `profile OCR prefers the name beside avatar over the top refresh action`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(48, 270, 360, 582),
                ),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("求更新", ScreenBounds(510, 120, 760, 205)),
                OcrTextBlock("佛山市顺德区MIFU家具厂的小店", ScreenBounds(408, 355, 920, 443)),
                OcrTextBlock("店铺账号", ScreenBounds(408, 449, 620, 497)),
            ),
        )

        assertEquals(
            "佛山市顺德区MIFU家具厂的小店",
            ProfileDisplayNameResolver.fromOcr(context, "求更新"),
        )
    }

    @Test
    fun `profile OCR can use avatar geometry when account label is absent`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(48, 270, 360, 582),
                ),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("求更新", ScreenBounds(510, 120, 760, 205)),
                OcrTextBlock("佛山嘉美实木家具", ScreenBounds(408, 355, 870, 443)),
            ),
        )

        assertEquals(
            "佛山嘉美实木家具",
            ProfileDisplayNameResolver.fromOcr(context, "求更新"),
        )
    }

    @Test
    fun `profile accessibility prefers name node tied to avatar`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "求更新",
                    bounds = ScreenBounds(510, 120, 760, 205),
                ),
                NodeSnapshot(
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(48, 270, 360, 582),
                ),
                NodeSnapshot(
                    text = "佛山市原木古风家具",
                    bounds = ScreenBounds(408, 355, 920, 443),
                ),
                NodeSnapshot(
                    text = "抖音号：62303106982",
                    bounds = ScreenBounds(408, 449, 780, 497),
                ),
            ),
        )

        assertEquals(
            "佛山市原木古风家具",
            ProfileDisplayNameResolver.fromAccessibility(context, "求更新"),
        )
    }

    @Test
    fun `profile OCR strips account label merged into the same text block`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(48, 270, 360, 582),
                ),
            ),
            ocrBlocks = listOf(
                OcrTextBlock(
                    "佛山市餐桌椅源头厂\n抖音号：165130268",
                    ScreenBounds(408, 355, 920, 497),
                ),
            ),
        )

        assertEquals(
            "佛山市餐桌椅源头厂",
            ProfileDisplayNameResolver.fromOcr(context, null),
        )
    }

    @Test
    fun `profile OCR falls back to the largest right-side title without known account label`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            ocrBlocks = listOf(
                OcrTextBlock("搜索", ScreenBounds(770, 150, 960, 220)),
                OcrTextBlock("佛山市盛益家具有限公司", ScreenBounds(408, 410, 950, 465)),
                OcrTextBlock("盛益红木家具", ScreenBounds(408, 335, 800, 430)),
            ),
        )

        assertEquals(
            "盛益红木家具",
            ProfileDisplayNameResolver.fromOcr(context, null),
        )
    }

    @Test
    fun `profile OCR ignores filter button when profile anchors are transient`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            ocrBlocks = listOf(
                OcrTextBlock("筛选，按钮", ScreenBounds(820, 155, 1060, 225)),
                OcrTextBlock("佛山茶宴红木家具", ScreenBounds(408, 355, 900, 443)),
                OcrTextBlock("商家认证账号", ScreenBounds(408, 449, 700, 497)),
            ),
        )

        assertEquals(
            "佛山茶宴红木家具",
            ProfileDisplayNameResolver.fromOcr(context, "筛选，按钮"),
        )
    }

    @Test
    fun `prefers complete profile header name over clipped list label`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "佛山市南海楠荞红木..",
                    bounds = ScreenBounds(180, 360, 820, 430),
                ),
                NodeSnapshot(
                    text = "佛山市南海楠荞红木家具厂",
                    bounds = ScreenBounds(180, 350, 900, 430),
                ),
            ),
        )

        assertEquals(
            "佛山市南海楠荞红木家具厂",
            ProfileDisplayNameResolver.fromAccessibility(context, "佛山市南海楠荞红木.."),
        )
    }

    @Test
    fun `does not treat a clipped profile label as a complete name`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "佛山市南海楠荞红木..",
                    bounds = ScreenBounds(180, 360, 820, 430),
                ),
            ),
        )

        assertNull(ProfileDisplayNameResolver.fromAccessibility(context, "佛山市南海楠荞红木.."))
    }

    @Test
    fun `ignores resource ids exposed beside profile text`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    viewIdResourceName = "com.ss.android.ugc.aweme:id/6dy",
                    bounds = ScreenBounds(40, 300, 1000, 380),
                ),
                NodeSnapshot(
                    text = "玲丽红木家具",
                    bounds = ScreenBounds(180, 360, 900, 430),
                ),
            ),
        )

        assertEquals("玲丽红木家具", ProfileDisplayNameResolver.fromAccessibility(context, null))
    }

    @Test
    fun `removes profile action menu text accidentally joined to the name`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "花园君尚.红木家具，复制名字和修改备注",
                    bounds = ScreenBounds(160, 340, 920, 430),
                ),
            ),
        )

        assertEquals(
            "花园君尚.红木家具",
            ProfileDisplayNameResolver.fromAccessibility(context, null),
        )
    }

    @Test
    fun `uses only the profile header crop for OCR fallback`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            ocrBlocks = listOf(
                OcrTextBlock("佛山市南海楠荞红木家具厂", ScreenBounds(180, 360, 900, 430)),
                OcrTextBlock("佛山市南海楠荞红木家具厂介绍", ScreenBounds(100, 1200, 900, 1300)),
            ),
        )

        assertEquals(
            "佛山市南海楠荞红木家具厂",
            ProfileDisplayNameResolver.fromOcr(context, "佛山市南海楠荞红木.."),
        )
    }

    @Test
    fun `corrects a one-character city prefix OCR error conservatively`() {
        assertEquals(
            "杭州世成红木沙发坐垫",
            ProfileNameCorrection.correct("抗州世成红木沙发坐垫"),
        )
        assertEquals(
            "抗州木作",
            ProfileNameCorrection.correct("抗州木作"),
        )
    }
}
