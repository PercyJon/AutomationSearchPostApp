package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommentPanelRecoveryPolicyTest {
    private val screen = ScreenSize(1080, 2400)
    private val closedSurface = CommentSurfaceDetection(false, 0.1f, listOf("No comment-surface signature matched"))

    @Test
    fun `recovery accepts only an accessibility-backed compact right rail node`() {
        val railNode = NodeSnapshot(
            className = "android.widget.ImageView",
            bounds = ScreenBounds(900, 1450, 1010, 1560),
            isClickable = true,
        )
        val target = CommentButtonTarget.AccessibilityNode(railNode)

        assertEquals(target, CommentPanelRecoveryPolicy.reopenTarget(closedSurface, target, screen))
    }

    @Test
    fun `recovery never uses an OCR coordinate fallback`() {
        val target = CommentButtonTarget.OcrFallback(ScreenBounds(900, 1450, 1010, 1560))

        assertNull(CommentPanelRecoveryPolicy.reopenTarget(closedSurface, target, screen))
    }

    @Test
    fun `recovery never uses a visual template coordinate fallback`() {
        val target = CommentButtonTarget.TemplateFallback(
            bounds = ScreenBounds(900, 1450, 1010, 1560),
            confidence = 0.93f,
        )

        assertNull(CommentPanelRecoveryPolicy.reopenTarget(closedSurface, target, screen))
    }

    @Test
    fun `recovery rejects an ai analysis tab even when it is clickable`() {
        val target = CommentButtonTarget.AccessibilityNode(
            NodeSnapshot(
                text = "AI解析",
                className = "android.widget.TextView",
                bounds = ScreenBounds(820, 1200, 1020, 1280),
                isClickable = true,
            ),
        )

        assertNull(CommentPanelRecoveryPolicy.reopenTarget(closedSurface, target, screen))
    }

    @Test
    fun `recovery leaves an already open comment surface untouched`() {
        val target = CommentButtonTarget.AccessibilityNode(
            NodeSnapshot(
                className = "android.widget.ImageView",
                bounds = ScreenBounds(900, 1450, 1010, 1560),
                isClickable = true,
            ),
        )
        val openSurface = CommentSurfaceDetection(true, 0.95f, listOf("comment count header"))

        assertNull(CommentPanelRecoveryPolicy.reopenTarget(openSurface, target, screen))
    }
}
