package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommentSurfaceSignalsTest {
    private val screen = ScreenSize(1080, 2400)

    @Test
    fun `semantic comment control wins over caption text`() {
        val commentButton = node(
            text = "评论",
            className = "android.widget.ImageButton",
            left = 900,
            top = 1250,
            right = 1010,
            bottom = 1360,
        )
        val context = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(text = "评论区欢迎留言", bounds = ScreenBounds(40, 1700, 600, 1780)),
                commentButton,
            ),
        )

        val found = VideoCommentButtonDetector.find(context)

        assertEquals(commentButton, found)
    }

    @Test
    fun `structural action rail selects second button as comment`() {
        val buttons = (0..3).map { index ->
            node(
                text = null,
                className = "android.widget.ImageView",
                left = 900,
                top = 950 + index * 180,
                right = 1000,
                bottom = 1050 + index * 180,
            )
        }

        assertEquals(buttons[1], VideoCommentButtonDetector.find(ScreenContext(screen, nodes = buttons)))
    }

    @Test
    fun `comment end marker is recognized from nodes or OCR`() {
        val nodeResult = CommentPanelEndDetector.detect(
            ScreenContext(screen, nodes = listOf(NodeSnapshot(text = "暂时没有更多了"))),
        )
        assertTrue(nodeResult.reached)
        assertEquals("暂时没有更多了", nodeResult.marker)

        val ocrResult = CommentPanelEndDetector.detect(
            ScreenContext(screen, ocrBlocks = listOf(OcrTextBlock("暂无更多内容"))),
        )
        assertTrue(ocrResult.reached)
        assertTrue(ocrResult.confidence > 0.9f)

        val notEnd = CommentPanelEndDetector.detect(
            ScreenContext(screen, nodes = listOf(NodeSnapshot(text = "更多回复"))),
        )
        assertFalse(notEnd.reached)
    }

    @Test
    fun `comment entry observation carries the verified button`() {
        val button = node(
            text = "评论",
            className = "android.widget.ImageButton",
            left = 900,
            top = 1250,
            right = 1010,
            bottom = 1360,
        )
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "视频", bounds = ScreenBounds(40, 400, 200, 480)),
                button,
            ),
        )

        val observation = CommentEntrySignalDetector.observe(context)

        assertNotNull(observation.commentButton)
        assertTrue(observation.hasCommentEntry)
    }

    private fun node(
        text: String?,
        className: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = NodeSnapshot(
        text = text,
        className = className,
        bounds = ScreenBounds(left, top, right, bottom),
        isClickable = true,
        isEnabled = true,
        isVisibleToUser = true,
    )
}
