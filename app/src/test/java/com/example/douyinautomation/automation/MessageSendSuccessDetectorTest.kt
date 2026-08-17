package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSendSuccessDetectorTest {
    @Test
    fun `non editable outgoing node verifies message delivery`() {
        val context = contextOf(
            NodeSnapshot(text = "您好，想了解一下产品。", bounds = ScreenBounds(500, 1200, 1000, 1320)),
            NodeSnapshot(text = "输入你的问题", isEditable = true, bounds = ScreenBounds(100, 2200, 900, 2350)),
        )

        assertTrue(MessageSendSuccessDetector.matches(context, "您好，想了解一下产品。"))
    }

    @Test
    fun `editable composer alone is not delivery proof`() {
        val context = contextOf(
            NodeSnapshot(text = "您好，想了解一下产品。", isEditable = true, bounds = ScreenBounds(100, 2200, 900, 2350)),
        )

        assertFalse(MessageSendSuccessDetector.matches(context, "您好，想了解一下产品。"))
    }

    @Test
    fun `ocr can verify a custom rendered outgoing bubble`() {
        val context = ScreenContext(
            packageName = "com.ss.android.ugc.aweme",
            ocrBlocks = listOf(OcrTextBlock("您好，想了解一下产品。")),
        )

        assertTrue(MessageSendSuccessDetector.matches(context, "您好，想了解一下产品。"))
    }

    private fun contextOf(vararg nodes: NodeSnapshot): ScreenContext = ScreenContext(
        packageName = "com.ss.android.ugc.aweme",
        screenSize = ScreenSize(1080, 2400),
        nodes = nodes.toList(),
    )
}
