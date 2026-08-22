package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommentSurfaceSignalsTest {
    private val screen = ScreenSize(1080, 2400)

    @Test
    fun `semantic comment control is found even with a transient visibility flag`() {
        val commentButton = node(
            text = "评论",
            className = "android.widget.ImageButton",
            left = 900,
            top = 1250,
            right = 1010,
            bottom = 1360,
        ).copy(isVisibleToUser = false)
        val context = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(text = "视频", bounds = ScreenBounds(40, 400, 200, 480)),
                commentButton,
            ),
        )

        val found = VideoCommentButtonDetector.find(context)

        assertEquals(CommentButtonTarget.AccessibilityNode(commentButton), found)
    }

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

        assertEquals(CommentButtonTarget.AccessibilityNode(commentButton), found)
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

        assertEquals(
            CommentButtonTarget.AccessibilityNode(buttons[1]),
            VideoCommentButtonDetector.find(ScreenContext(screen, nodes = buttons)),
        )
    }

    @Test
    fun `structural action rail resolves the second button when it reports clickable=false`() {
        val buttons = (0..3).map { index ->
            node(
                text = null,
                className = "android.widget.ImageView",
                left = 900,
                top = 950 + index * 180,
                right = 1000,
                bottom = 1050 + index * 180,
            ).copy(isClickable = false)
        }

        assertEquals(
            CommentButtonTarget.AccessibilityNode(buttons[1]),
            VideoCommentButtonDetector.find(ScreenContext(screen, nodes = buttons)),
        )
    }

    @Test
    fun `a clickable rail item wins over a non-clickable duplicate at the same position`() {
        val nonClickable = (0..3).map { index ->
            node(
                text = null,
                className = "android.widget.ImageView",
                left = 900,
                top = 950 + index * 180,
                right = 1000,
                bottom = 1050 + index * 180,
            ).copy(isClickable = false)
        }
        val clickableComment = node(
            text = null,
            className = "android.widget.ImageView",
            left = 900,
            top = 950 + 180,
            right = 1000,
            bottom = 1050 + 180,
        ).copy(isClickable = true)
        val context = ScreenContext(screen, nodes = nonClickable + clickableComment)

        assertEquals(CommentButtonTarget.AccessibilityNode(clickableComment), VideoCommentButtonDetector.find(context))
    }

    @Test
    fun `OCR comment label is used only after malformed accessibility rail bounds are rejected`() {
        val malformedRail = (0..3).map { index ->
            node(
                text = null,
                className = "android.widget.ImageView",
                left = 900,
                top = -900 + index * 180,
                right = 1000,
                bottom = -900 + index * 180,
            )
        }
        val ocrBounds = ScreenBounds(900, 1560, 1010, 1610)
        val context = ScreenContext(
            screenSize = screen,
            nodes = malformedRail,
            ocrBlocks = listOf(OcrTextBlock("评论", ocrBounds, confidence = 0.96f)),
        )

        assertEquals(
            CommentButtonTarget.OcrFallback(ocrBounds),
            VideoCommentButtonDetector.find(context),
        )
    }

    @Test
    fun `OCR action-count rail infers only the second comment bubble`() {
        val malformedRail = (0..3).map { index ->
            node(
                text = null,
                className = "android.widget.ImageView",
                left = 900,
                top = -900 + index * 180,
                right = 1000,
                bottom = -800 + index * 180,
            )
        }
        val context = ScreenContext(
            screenSize = screen,
            nodes = malformedRail,
            ocrBlocks = listOf(
                OcrTextBlock("3.2万", ScreenBounds(930, 1420, 1030, 1470), confidence = 0.97f),
                OcrTextBlock("59", ScreenBounds(930, 1620, 1030, 1670), confidence = 0.96f),
                OcrTextBlock("1", ScreenBounds(930, 1820, 1030, 1870), confidence = 0.98f),
                OcrTextBlock("1278", ScreenBounds(930, 2020, 1030, 2070), confidence = 0.95f),
            ),
        )

        assertEquals(
            CommentButtonTarget.OcrFallback(ScreenBounds(920, 1505, 1040, 1625)),
            VideoCommentButtonDetector.find(context),
        )
    }

    @Test
    fun `OCR caption text outside the action rail is not a comment fallback`() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(OcrTextBlock("评论区欢迎留言", ScreenBounds(80, 1560, 460, 1610))),
        )

        assertEquals(null, VideoCommentButtonDetector.find(context))
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
    fun `zero comment panel is terminal and its author activity row is not a candidate`() {
        val context = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(text = "评论 0", bounds = ScreenBounds(30, 680, 140, 740)),
                NodeSnapshot(text = "赞 6", bounds = ScreenBounds(180, 680, 270, 740)),
                NodeSnapshot(text = "收藏 0", bounds = ScreenBounds(320, 680, 430, 740)),
                NodeSnapshot(text = "Horizon G. 作者", bounds = ScreenBounds(120, 820, 430, 870)),
                NodeSnapshot(text = "发布了作品 2020-5-21", bounds = ScreenBounds(120, 880, 620, 940)),
                NodeSnapshot(text = "期待你的评论", bounds = ScreenBounds(300, 1450, 700, 1520)),
                NodeSnapshot(text = "发条评论表达你的想法", bounds = ScreenBounds(280, 1530, 760, 1590)),
                NodeSnapshot(text = "去评论", bounds = ScreenBounds(320, 1630, 540, 1700), isClickable = true),
                NodeSnapshot(text = "写评论", bounds = ScreenBounds(40, 2200, 500, 2260), isEditable = true),
            ),
        )

        val end = CommentPanelEndDetector.detect(context)
        assertTrue(end.reached)
        assertTrue(end.marker.orEmpty().contains("期待你的评论"))
        assertTrue(end.marker.orEmpty().contains("去评论"))
        assertTrue(CommentCandidateExtractor.extract(context, emptyList()).candidates.isEmpty())
    }

    @Test
    fun `zero comment tab plus empty state skips when go-comment button is absent from accessibility`() {
        val context = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(text = "评论 0", bounds = ScreenBounds(30, 680, 140, 740)),
                NodeSnapshot(text = "发布了作品 2020-5-21", bounds = ScreenBounds(120, 880, 620, 940)),
                NodeSnapshot(text = "期待你的评论", bounds = ScreenBounds(300, 1450, 700, 1520)),
                NodeSnapshot(text = "发条评论表达你的想法吧", bounds = ScreenBounds(280, 1530, 760, 1590)),
            ),
        )

        val end = CommentPanelEndDetector.detect(context)

        assertTrue(end.reached)
        assertEquals("期待你的评论/评论0/发布了作品", end.marker)
    }

    @Test
    fun `empty comment prompt without go-comment action is not terminal`() {
        val context = ScreenContext(
            screenSize = screen,
            nodes = listOf(NodeSnapshot(text = "期待你的评论", bounds = ScreenBounds(300, 1450, 700, 1520))),
        )
        assertFalse(CommentPanelEndDetector.detect(context).reached)
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
