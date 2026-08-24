package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentSurfaceSignalsTest {
    private val screen = ScreenSize(1080, 2400)

    @Test
    fun `structural action rail survives transient false visibility flags`() {
        val rail = (0..2).map { index ->
            node(
                text = null,
                className = "android.widget.ImageButton",
                left = 900,
                top = 950 + index * 180,
                right = 1000,
                bottom = 1050 + index * 180,
            ).copy(isVisibleToUser = false)
        }

        assertEquals(
            CommentButtonTarget.AccessibilityNode(rail[1]),
            VideoCommentButtonDetector.find(ScreenContext(screen, nodes = rail)),
        )
    }

    @Test
    fun `right rail selection ignores a misleading comment text label`() {
        val rail = (0..2).map { index ->
            node(
                text = if (index == 0) "评论" else null,
                className = "android.widget.ImageView",
                left = 900,
                top = 950 + index * 180,
                right = 1000,
                bottom = 1050 + index * 180,
            )
        }

        assertEquals(
            CommentButtonTarget.AccessibilityNode(rail[1]),
            VideoCommentButtonDetector.find(ScreenContext(screen, nodes = rail)),
        )
    }

    @Test
    fun `wide text parent is ignored while the compact action rail selects the second icon`() {
        val wideParent = node(
            text = "评论 435",
            className = "android.widget.FrameLayout",
            left = 740,
            top = 1180,
            right = 1080,
            bottom = 1580,
        )
        val rail = (0..3).map { index ->
            node(
                text = null,
                className = "android.widget.ImageView",
                left = 900,
                top = 1000 + index * 180,
                right = 1000,
                bottom = 1100 + index * 180,
            )
        }

        assertEquals(
            CommentButtonTarget.AccessibilityNode(rail[1]),
            VideoCommentButtonDetector.find(ScreenContext(screen, nodes = listOf(wideParent) + rail)),
        )
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
    fun `OCR comment text never creates an entry target`() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(OcrTextBlock("评论", ScreenBounds(900, 1560, 1010, 1610), confidence = 0.96f)),
        )

        assertNull(VideoCommentButtonDetector.find(context))
    }

    @Test
    fun `unconfirmed comment template never creates an entry target`() {
        val context = ScreenContext(
            screenSize = screen,
            commentIconTemplateMatch = CommentIconTemplateMatch(
                bounds = ScreenBounds(900, 1320, 1010, 1420),
                confidence = 0.93f,
            ),
        )

        assertNull(VideoCommentButtonDetector.find(context))
    }

    @Test
    fun `confirmed right rail template becomes a bounded fallback`() {
        val bounds = ScreenBounds(900, 1320, 1010, 1420)
        val context = ScreenContext(
            screenSize = screen,
            commentIconTemplateMatch = CommentIconTemplateMatch(
                bounds = bounds,
                confidence = 0.93f,
                isConfirmed = true,
            ),
        )

        assertEquals(
            CommentButtonTarget.TemplateFallback(bounds, 0.93f),
            VideoCommentButtonDetector.find(context),
        )
    }

    @Test
    fun `template candidate needs a same-position second screenshot`() {
        val first = CommentIconTemplateMatch(ScreenBounds(900, 1320, 1010, 1420), confidence = 0.93f)
        val stableSecond = CommentIconTemplateMatch(ScreenBounds(905, 1325, 1015, 1425), confidence = 0.92f)
        val differentIcon = CommentIconTemplateMatch(ScreenBounds(900, 1640, 1010, 1740), confidence = 0.94f)

        assertTrue(CommentIconTemplateStabilityPolicy.confirms(first, stableSecond, screen))
        assertFalse(CommentIconTemplateStabilityPolicy.confirms(first, differentIcon, screen))
        assertFalse(CommentIconTemplateStabilityPolicy.confirms(first, null, screen))
    }

    @Test
    fun `unconfirmed dual anchors never create an entry target`() {
        val context = ScreenContext(
            screenSize = screen,
            actionRailAnchorTemplateMatch = dualAnchorMatch(),
        )

        assertNull(VideoCommentButtonDetector.find(context))
    }

    @Test
    fun `confirmed like and collect anchors derive the intervening comment position`() {
        val context = ScreenContext(
            screenSize = screen,
            actionRailAnchorTemplateMatch = dualAnchorMatch(isConfirmed = true),
        )

        assertEquals(
            CommentButtonTarget.DualAnchorFallback(
                bounds = ScreenBounds(900, 1300, 1000, 1400),
                likeConfidence = 0.94f,
                collectConfidence = 0.93f,
            ),
            VideoCommentButtonDetector.find(context),
        )
    }

    @Test
    fun `dual anchors reject a reversed or mismatched action rail`() {
        val reversed = dualAnchorMatch(
            likeBounds = ScreenBounds(900, 1500, 1000, 1600),
            collectBounds = ScreenBounds(900, 1100, 1000, 1200),
            isConfirmed = true,
        )
        val differentScale = dualAnchorMatch(
            likeBounds = ScreenBounds(900, 1100, 1000, 1200),
            collectBounds = ScreenBounds(890, 1500, 1060, 1670),
            isConfirmed = true,
        )

        assertNull(
            VideoCommentButtonDetector.find(
                ScreenContext(screenSize = screen, actionRailAnchorTemplateMatch = reversed),
            ),
        )
        assertNull(
            VideoCommentButtonDetector.find(
                ScreenContext(screenSize = screen, actionRailAnchorTemplateMatch = differentScale),
            ),
        )
    }

    @Test
    fun `dual anchors need a stable same-rail second screenshot`() {
        val first = dualAnchorMatch()
        val stableSecond = dualAnchorMatch(
            likeBounds = ScreenBounds(905, 1105, 1005, 1205),
            collectBounds = ScreenBounds(905, 1505, 1005, 1605),
        )
        val shiftedRail = dualAnchorMatch(
            likeBounds = ScreenBounds(900, 1420, 1000, 1520),
            collectBounds = ScreenBounds(900, 1820, 1000, 1920),
        )

        assertTrue(ActionRailAnchorTemplateStabilityPolicy.confirms(first, stableSecond, screen))
        assertFalse(ActionRailAnchorTemplateStabilityPolicy.confirms(first, shiftedRail, screen))
        assertFalse(ActionRailAnchorTemplateStabilityPolicy.confirms(first, null, screen))
    }

    @Test
    fun `existing template and OCR routes keep priority over dual anchors`() {
        val dualAnchors = dualAnchorMatch(isConfirmed = true)
        val templateBounds = ScreenBounds(900, 1320, 1010, 1420)
        val templateContext = ScreenContext(
            screenSize = screen,
            commentIconTemplateMatch = CommentIconTemplateMatch(
                bounds = templateBounds,
                confidence = 0.93f,
                isConfirmed = true,
            ),
            actionRailAnchorTemplateMatch = dualAnchors,
        )
        val ocrContext = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("465", ScreenBounds(930, 1420, 1030, 1470), confidence = 0.97f),
                OcrTextBlock("59", ScreenBounds(930, 1620, 1030, 1670), confidence = 0.96f),
                OcrTextBlock("1", ScreenBounds(930, 1820, 1030, 1870), confidence = 0.98f),
                OcrTextBlock("1278", ScreenBounds(930, 2020, 1030, 2070), confidence = 0.95f),
            ),
            actionRailAnchorTemplateMatch = dualAnchors,
        )

        assertEquals(
            CommentButtonTarget.TemplateFallback(templateBounds, 0.93f),
            VideoCommentButtonDetector.find(templateContext),
        )
        assertEquals(
            CommentButtonTarget.OcrFallback(ScreenBounds(920, 1505, 1040, 1625)),
            VideoCommentButtonDetector.find(ocrContext),
        )
    }

    @Test
    fun `ambiguous three-number OCR rail is rejected`() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("465", ScreenBounds(930, 1420, 1030, 1470), confidence = 0.97f),
                OcrTextBlock("187", ScreenBounds(930, 1620, 1030, 1670), confidence = 0.96f),
                OcrTextBlock("76", ScreenBounds(930, 1820, 1030, 1870), confidence = 0.95f),
            ),
        )

        assertNull(VideoCommentButtonDetector.find(context))
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
    fun `trailing share label proves that three numeric slots start with like`() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("2474", ScreenBounds(930, 1420, 1030, 1470), confidence = 0.97f),
                OcrTextBlock("19", ScreenBounds(930, 1620, 1030, 1670), confidence = 0.96f),
                OcrTextBlock("2", ScreenBounds(930, 1820, 1030, 1870), confidence = 0.98f),
                OcrTextBlock("分享", ScreenBounds(930, 2020, 1030, 2070), confidence = 0.95f),
            ),
        )

        assertEquals(
            CommentButtonTarget.OcrFallback(ScreenBounds(920, 1505, 1040, 1625)),
            VideoCommentButtonDetector.find(context),
        )
    }

    @Test
    fun `OCR action-count rail infers the comment bubble when its zero-count slot is absent`() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                // Like and favorite have values while comment is zero. The fixed final share
                // label proves the 0,2,3 slot mapping without using comment text.
                OcrTextBlock("3.2万", ScreenBounds(930, 1420, 1030, 1470), confidence = 0.97f),
                OcrTextBlock("1", ScreenBounds(930, 1820, 1030, 1870), confidence = 0.98f),
                OcrTextBlock("分享", ScreenBounds(930, 2020, 1030, 2070), confidence = 0.95f),
            ),
        )

        assertEquals(
            CommentButtonTarget.OcrFallback(ScreenBounds(920, 1505, 1040, 1625)),
            VideoCommentButtonDetector.find(context),
        )
    }

    @Test
    fun `trailing share label keeps the first visible count as comment when like is zero`() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("19", ScreenBounds(930, 1620, 1030, 1670), confidence = 0.96f),
                OcrTextBlock("2", ScreenBounds(930, 1820, 1030, 1870), confidence = 0.98f),
                OcrTextBlock("分享", ScreenBounds(930, 2020, 1030, 2070), confidence = 0.95f),
            ),
        )

        assertEquals(
            CommentButtonTarget.OcrFallback(ScreenBounds(920, 1505, 1040, 1625)),
            VideoCommentButtonDetector.find(context),
        )
    }

    @Test
    fun `OCR action-count rail accepts compact labels left of the icon centers`() {
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
            // Verified player screenshots place these count labels around 78% of screen width,
            // while the icon itself remains farther right. The complete four-slot rail remains
            // the safety proof for the OCR-only coordinate fallback.
            ocrBlocks = listOf(
                OcrTextBlock("935", ScreenBounds(800, 1420, 890, 1470), confidence = 0.97f),
                OcrTextBlock("43", ScreenBounds(800, 1620, 890, 1670), confidence = 0.96f),
                OcrTextBlock("194", ScreenBounds(800, 1820, 890, 1870), confidence = 0.98f),
                OcrTextBlock("58", ScreenBounds(800, 2020, 890, 2070), confidence = 0.95f),
            ),
        )

        assertEquals(
            CommentButtonTarget.OcrFallback(ScreenBounds(785, 1505, 905, 1625)),
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
    fun `OCR sheet heading with two reply rows confirms a sparse restored comment sheet`() {
        val detection = CommentSurfaceDetector.detect(
            ScreenContext(
                screenSize = screen,
                ocrBlocks = listOf(
                    OcrTextBlock("评论", ScreenBounds(48, 1040, 210, 1110)),
                    OcrTextBlock("回复", ScreenBounds(470, 1320, 570, 1380)),
                    OcrTextBlock("回复", ScreenBounds(470, 1710, 570, 1770)),
                ),
            ),
        )

        assertTrue(detection.isCommentSurface)
        assertTrue(detection.reasons.any { it.contains("OCR sheet header with reply rows") })
    }

    @Test
    fun `right rail OCR comment label with reply-like caption is not a comment sheet`() {
        val detection = CommentSurfaceDetector.detect(
            ScreenContext(
                screenSize = screen,
                ocrBlocks = listOf(
                    OcrTextBlock("评论", ScreenBounds(920, 1260, 1040, 1320)),
                    OcrTextBlock("回复", ScreenBounds(120, 1540, 240, 1600)),
                    OcrTextBlock("回复", ScreenBounds(120, 1640, 240, 1700)),
                ),
            ),
        )

        assertFalse(detection.isCommentSurface)
    }

    @Test
    fun `AI analysis tab plus comment composer is recognized as an already open comment sheet`() {
        val detection = CommentSurfaceDetector.detect(
            ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(text = "AI解析", bounds = ScreenBounds(700, 900, 900, 970)),
                    NodeSnapshot(
                        text = "有什么想法，展开说说",
                        className = "android.widget.EditText",
                        bounds = ScreenBounds(42, 2268, 678, 2388),
                        isEditable = true,
                    ),
                ),
            ),
        )

        assertTrue(detection.isCommentSurface)
        assertTrue(detection.reasons.any { it.contains("AI analysis tab") })
    }

    @Test
    fun `comment entry observation carries the verified button`() {
        val rail = (0..2).map { index ->
            node(
                text = null,
                className = "android.widget.ImageButton",
                left = 900,
                top = 1070 + index * 180,
                right = 1010,
                bottom = 1170 + index * 180,
            )
        }
        val button = rail[1]
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "视频", bounds = ScreenBounds(40, 400, 200, 480)),
            ) + rail,
        )

        val observation = CommentEntrySignalDetector.observe(context)

        assertNotNull(observation.commentButton)
        assertTrue(observation.hasCommentEntry)
    }

    @Test
    fun `HOME classified next video accepts a complete OCR action rail`() {
        val context = ScreenContext(
            screenSize = screen,
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(text = "首页"),
                NodeSnapshot(text = "推荐"),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("465", ScreenBounds(930, 1240, 1030, 1290), confidence = 0.97f),
                OcrTextBlock("5", ScreenBounds(930, 1440, 1030, 1490), confidence = 0.96f),
                OcrTextBlock("187", ScreenBounds(930, 1640, 1030, 1690), confidence = 0.98f),
                OcrTextBlock("76", ScreenBounds(930, 1840, 1030, 1890), confidence = 0.95f),
            ),
        )

        val observation = CommentEntrySignalDetector.observe(context)

        assertEquals(PageKind.HOME, observation.page)
        assertTrue(observation.hasVideoSurface)
        assertTrue(observation.hasCommentEntry)
        assertNotNull(observation.commentButton)
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

    private fun dualAnchorMatch(
        likeBounds: ScreenBounds = ScreenBounds(900, 1100, 1000, 1200),
        collectBounds: ScreenBounds = ScreenBounds(900, 1500, 1000, 1600),
        isConfirmed: Boolean = false,
    ) = ActionRailAnchorTemplateMatch(
        likeBounds = likeBounds,
        likeConfidence = 0.94f,
        collectBounds = collectBounds,
        collectConfidence = 0.93f,
        isConfirmed = isConfirmed,
    )
}
