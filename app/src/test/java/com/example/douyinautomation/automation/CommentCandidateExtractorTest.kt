package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentCandidateExtractorTest {
    private val screen = ScreenSize(1080, 2400)

    @Test
    fun `node text is preferred and author bounds are returned for safe name tap`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("店主小王", 120, 520, 420, 575),
                    node("价格和尺寸怎么说", 120, 590, 700, 655),
                    node("写评论", 80, 2200, 400, 2260, isEditable = true),
                ),
            ),
            matchKeywords = listOf("价格|优惠"),
        )

        assertEquals(1, extraction.candidates.size)
        val candidate = extraction.candidates.single()
        assertEquals("店主小王", candidate.authorText)
        assertEquals(ScreenBounds(120, 520, 420, 575), candidate.authorBounds)
        assertEquals(candidate.authorBounds, candidate.interactionBounds)
        assertEquals(listOf("价格"), candidate.matchedKeywords)
        assertEquals(CommentTextSource.ACCESSIBILITY, candidate.source)
    }

    @Test
    fun `ocr is used only when semantic node text is absent`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                ocrBlocks = listOf(
                    OcrTextBlock("家具达人", ScreenBounds(100, 500, 400, 550)),
                    OcrTextBlock("有没有优惠活动", ScreenBounds(100, 565, 700, 625)),
                ),
            ),
            matchKeywords = listOf("优惠"),
        )

        assertEquals(1, extraction.candidates.size)
        assertEquals(CommentTextSource.OCR, extraction.candidates.single().source)
        assertEquals("家具达人", extraction.candidates.single().authorText)
    }

    @Test
    fun `empty keywords keep all non-ui comment text but ignore controls`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("全部评论", 40, 300, 300, 360),
                    node("红木很好看", 100, 580, 500, 640),
                    node("店主", 100, 510, 300, 555),
                    node("分享", 800, 2200, 950, 2260),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertTrue(extraction.candidates.any { it.commentText == "红木很好看" })
        assertFalse(extraction.candidates.any { it.commentText == "分享" })
    }

    @Test
    fun `comment surface requires more than incidental comment label`() {
        val video = CommentSurfaceDetector.detect(
            ScreenContext(
                screenSize = screen,
                nodes = listOf(node("评论", 100, 300, 200, 350)),
            ),
        )
        assertFalse(video.isCommentSurface)

        val comments = CommentSurfaceDetector.detect(
            ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("全部评论", 100, 300, 300, 350),
                    node("回复 1", 100, 600, 300, 650),
                    node("回复 2", 100, 700, 300, 750),
                    node("写评论", 80, 2200, 500, 2260, isEditable = true),
                ),
            ),
        )
        assertTrue(comments.isCommentSurface)
        assertTrue(comments.confidence >= 0.9f)
    }

    private fun node(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        isEditable: Boolean = false,
    ) = NodeSnapshot(
        text = text,
        bounds = ScreenBounds(left, top, right, bottom),
        isEditable = isEditable,
        isVisibleToUser = true,
    )
}
