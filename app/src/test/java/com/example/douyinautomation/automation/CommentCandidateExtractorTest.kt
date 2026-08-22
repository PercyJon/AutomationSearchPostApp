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
                    node("店主小王", 120, 520, 420, 575, hierarchyPath = listOf(3, 2, 0)),
                    node("价格和尺寸怎么说", 120, 590, 700, 655, hierarchyPath = listOf(3, 2, 1)),
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
        assertEquals(listOf(3, 2, 0), candidate.interactionHierarchyPath)
        assertEquals(listOf("价格"), candidate.matchedKeywords)
        assertEquals(CommentTextSource.ACCESSIBILITY, candidate.source)
    }

    @Test
    fun `one character nickname remains the first eligible commenter`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("270条评论", 380, 850, 650, 900),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                        isVisibleToUser = true,
                    ),
                    node("1", 180, 980, 240, 1038),
                    node("这个评论匹配测试词", 180, 1040, 800, 1110),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 1230, 156, 1338),
                        isVisibleToUser = true,
                    ),
                    node("后续用户", 180, 1240, 420, 1298),
                    node("这个评论同样匹配测试词", 180, 1300, 820, 1370),
                ),
            ),
            matchKeywords = listOf("匹配测试词"),
        )

        assertEquals(listOf("1", "后续用户"), extraction.candidates.map { it.authorText })
        assertEquals(ScreenBounds(48, 970, 156, 1078), extraction.candidates.first().avatarBounds)
    }

    @Test
    fun `right-side like count cannot consume a punctuation-free first comment`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("270条评论", 380, 850, 650, 900),
                    NodeSnapshot(
                        className = "按钮",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/avatar",
                        contentDescription = "1的头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                        isClickable = true,
                    ),
                    node("1", 180, 970, 240, 1028),
                    // No sentence-ending punctuation: this is the case that was previously
                    // swallowed when the heart's standalone number was treated as a comment.
                    node("设计得很有创意", 180, 1040, 800, 1110),
                    node("2", 884, 1120, 906, 1173),
                    NodeSnapshot(
                        className = "按钮",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/avatar",
                        contentDescription = "后续用户的头像",
                        bounds = ScreenBounds(48, 1230, 156, 1338),
                        isClickable = true,
                    ),
                    node("后续用户", 180, 1240, 420, 1298),
                    node("这是一条后续评论！", 180, 1300, 820, 1370),
                    node("0", 884, 1380, 906, 1433),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(listOf("1", "后续用户"), extraction.candidates.map { it.authorText })
        assertEquals("设计得很有创意", extraction.candidates.first().commentText)
        assertEquals(ScreenBounds(48, 970, 156, 1078), extraction.candidates.first().avatarBounds)
    }

    @Test
    fun `first avatar comment remains eligible when nickname is virtualized`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("270条评论", 380, 850, 650, 900),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                        isVisibleToUser = true,
                    ),
                    // The first row's name is virtualized by Douyin, but its body and avatar
                    // still form a complete, tappable comment row.
                    node("首条评论正文", 180, 1040, 800, 1110),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 1570, 156, 1678),
                        isVisibleToUser = true,
                    ),
                    node("后续用户", 180, 1580, 420, 1638),
                    node("第二条评论", 180, 1640, 820, 1710),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(2, extraction.candidates.size)
        val first = extraction.candidates.first()
        assertEquals(null, first.authorText)
        assertEquals("首条评论正文", first.commentText)
        assertEquals(ScreenBounds(48, 970, 156, 1078), first.avatarBounds)
        assertTrue(CommentCandidateExtractor.belongsToAvatarRow(first, extraction.firstVisibleCommentAvatar!!))
    }

    @Test
    fun `first visible avatar remains anchored when comment count header is virtualized`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                        isVisibleToUser = true,
                    ),
                    node("首位用户", 180, 980, 360, 1038),
                    node("首条评论正文", 180, 1040, 800, 1110),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(ScreenBounds(48, 970, 156, 1078), extraction.firstVisibleCommentAvatar)
        assertTrue(CommentCandidateExtractor.belongsToAvatarRow(
            extraction.candidates.single(),
            extraction.firstVisibleCommentAvatar!!,
        ))
    }

    @Test
    fun `opaque location icon excludes its adjacent card without reading place text`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    // This opaque resource id intentionally carries no location text. The rule
                    // must use the small left-side icon plus its adjacent layout, not a city or
                    // street-name heuristic.
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/ery",
                        bounds = ScreenBounds(48, 800, 112, 864),
                        isVisibleToUser = true,
                    ),
                    node("任意地点名称", 156, 790, 600, 840),
                    node("任意地点说明", 156, 850, 850, 910),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 1200, 156, 1308),
                        isVisibleToUser = true,
                    ),
                    node("首位评论用户", 180, 1210, 480, 1268),
                    node("这是首条真实评论", 180, 1270, 860, 1340),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(1, extraction.candidates.size)
        assertEquals("首位评论用户", extraction.candidates.single().authorText)
    }

    @Test
    fun `official everyone is searching shortcut is never a comment row`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("大家都在搜：生命数字1到9对照表", 156, 760, 900, 824),
                    // The shortcut may expose a compact leading ImageView. It is not an avatar
                    // and its dynamic search phrase must never enter commenter matching.
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/search_icon",
                        bounds = ScreenBounds(48, 760, 112, 824),
                        isVisibleToUser = true,
                    ),
                    node("4480条评论", 380, 850, 650, 900),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                        isVisibleToUser = true,
                    ),
                    node("首位评论用户", 180, 980, 480, 1038),
                    node("这是首条真实评论", 180, 1040, 860, 1110),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(listOf("首位评论用户"), extraction.candidates.map { it.authorText })
    }

    @Test
    fun `location display is ignored by pin structure without reading country or venue`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "位置",
                        bounds = ScreenBounds(48, 760, 112, 824),
                        isVisibleToUser = true,
                    ),
                    // The wording deliberately mirrors a common foreign-location presentation.
                    // No country, city, street or venue text is part of the detection rule.
                    node("澳大利亚 | Wright Park", 156, 760, 780, 824),
                    node("791条评论", 380, 850, 650, 900),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                        isVisibleToUser = true,
                    ),
                    node("首位评论用户", 180, 980, 480, 1038),
                    node("这是首条真实评论", 180, 1040, 860, 1110),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(listOf("首位评论用户"), extraction.candidates.map { it.authorText })
    }

    @Test
    fun `right side heart image can never become a commenter avatar`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("270条评论", 380, 850, 650, 900),
                    node("首位评论用户", 760, 980, 980, 1038),
                    node("评论正文", 760, 1040, 1000, 1110),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/gmn",
                        bounds = ScreenBounds(831, 1040, 885, 1094),
                        isVisibleToUser = true,
                    ),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertTrue(extraction.candidates.isEmpty())
    }

    @Test
    fun `left side image below comment text can never be paired as its avatar`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("270条评论", 380, 850, 650, 900),
                    node("首位评论用户", 180, 980, 480, 1038),
                    node("评论正文", 180, 1040, 860, 1110),
                    // This is in the same left rail but starts after the text block. It could be
                    // a location/decorative image from a lower card, never this commenter's
                    // avatar.
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/ery",
                        bounds = ScreenBounds(48, 1160, 112, 1224),
                        isVisibleToUser = true,
                    ),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertTrue(extraction.candidates.isEmpty())
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
        val candidate = extraction.candidates.single()
        assertEquals(CommentTextSource.OCR, candidate.source)
        assertEquals("家具达人", candidate.authorText)
        assertTrue(candidate.interactionHierarchyPath.isEmpty())
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
        assertFalse(extraction.candidates.any { it.commentText == "店主" })
    }

    @Test
    fun `location metadata row without avatar and author is never a comment candidate`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("5小时前 · 广东", 120, 500, 460, 550),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        bounds = ScreenBounds(40, 600, 104, 664),
                        isVisibleToUser = true,
                    ),
                    node("家具达人", 140, 602, 420, 645),
                    node("价格多少", 140, 662, 520, 710),
                    node("写评论", 80, 2200, 400, 2260, isEditable = true),
                ),
            ),
            matchKeywords = emptyList(),
        )
        assertEquals(1, extraction.candidates.size)
        assertEquals("家具达人", extraction.candidates.single().authorText)
        assertEquals("价格多少", extraction.candidates.single().commentText)
    }

    @Test
    fun `location card with check in count is never treated as a comment`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "位置",
                        bounds = ScreenBounds(48, 760, 112, 824),
                    ),
                    node("天津市 | 棉3创意街区", 156, 780, 720, 840),
                    node("8076人打卡", 156, 846, 420, 900),
                    node("清水", 180, 980, 300, 1038),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                    ),
                    node("这个梦想可能会实现", 180, 1040, 800, 1110),
                    node("写评论", 80, 2200, 400, 2260, isEditable = true),
                ),
            ),
            matchKeywords = emptyList(),
        )
        assertEquals(1, extraction.candidates.size)
        assertEquals("清水", extraction.candidates.single().authorText)
        assertEquals("这个梦想可能会实现", extraction.candidates.single().commentText)
    }

    @Test
    fun `comment count boundary ignores split location header even without metadata words`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "地址",
                        bounds = ScreenBounds(48, 760, 112, 824),
                    ),
                    // Some builds split the location into separate text nodes and omit the
                    // vertical bar. The count row is the reliable boundary in that case.
                    node("天津市", 156, 780, 300, 840),
                    node("棉3创意街区", 305, 780, 620, 840),
                    node("270条评论", 380, 850, 650, 900),
                    node("清水", 140, 980, 240, 1038),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                    ),
                    node("这个梦想可能会实现", 180, 1040, 800, 1110),
                    node("2020-10-17 · 广东 · 回复", 180, 1120, 560, 1170),
                    node("写评论", 80, 2200, 400, 2260, isEditable = true),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(1, extraction.candidates.size)
        assertEquals("清水", extraction.candidates.single().authorText)
    }

    @Test
    fun `a lower comment mentioning comment count does not move the header boundary`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("270条评论", 380, 850, 650, 900),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 970, 156, 1078),
                    ),
                    node("清水", 180, 980, 300, 1038),
                    node("这个视频有270条评论", 180, 1040, 800, 1110),
                    node("2020-10-17", 180, 1120, 360, 1170),
                    // A later comment mentioning the same phrase must not be treated as a new
                    // count/header boundary.
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 1390, 156, 1498),
                    ),
                    node("梅花鹿", 180, 1400, 320, 1450),
                    node("这里有270条评论值得看", 180, 1460, 800, 1530),
                    node("写评论", 80, 2200, 400, 2260, isEditable = true),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(2, extraction.candidates.size)
        assertTrue(extraction.candidates.any { it.authorText == "清水" })
        assertTrue(extraction.candidates.any { it.authorText == "梅花鹿" })
    }

    @Test
    fun `content descriptions for action icons never become comment fragments`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "位置",
                        bounds = ScreenBounds(48, 700, 112, 764),
                    ),
                    node("270条评论", 380, 780, 650, 830),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "赞0，未选中",
                        bounds = ScreenBounds(820, 1000, 900, 1080),
                    ),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "踩，已选中",
                        bounds = ScreenBounds(910, 1000, 990, 1080),
                    ),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 900, 156, 1008),
                    ),
                    node("梅花鹿", 180, 900, 320, 950),
                    node("视频拍得很好", 180, 960, 700, 1030),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(listOf("梅花鹿"), extraction.candidates.mapNotNull(CommentUserCandidate::authorText))
        assertFalse(extraction.candidates.any { it.authorText?.contains("赞") == true })
        assertFalse(extraction.candidates.any { it.authorText?.contains("踩") == true })
    }

    @Test
    fun `location pin without a semantic label is still excluded by the count boundary`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    // This mirrors Douyin builds where the location ImageView has no
                    // contentDescription and only exposes an opaque resource id.
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/ery",
                        bounds = ScreenBounds(48, 899, 108, 959),
                    ),
                    node("天津市", 156, 874, 291, 937),
                    node("棉3创意街区", 318, 874, 569, 937),
                    node("8076人打卡", 156, 940, 333, 985),
                    node("270条评论", 454, 1021, 625, 1112),
                    NodeSnapshot(
                        className = "按钮",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/avatar",
                        contentDescription = "清水的头像",
                        bounds = ScreenBounds(48, 1132, 156, 1240),
                    ),
                    node("清水", 180, 1132, 258, 1185),
                    node("这个梦想可能会实现。但是不一定能有效果。", 180, 1197, 1032, 1322),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(listOf("清水"), extraction.candidates.mapNotNull(CommentUserCandidate::authorText))
        assertEquals(ScreenBounds(48, 1132, 156, 1240), extraction.candidates.single().interactionBounds)
    }

    @Test
    fun `ordinary comment containing like wording is not mistaken for the like control`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        viewIdResourceName = "com.ss.android.ugc.aweme:id/avatar",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 900, 156, 1008),
                    ),
                    node("家具爱好者", 180, 900, 360, 950),
                    node("很喜欢这个视频，价格也合适", 180, 960, 760, 1030),
                ),
            ),
            matchKeywords = listOf("喜欢"),
        )

        assertEquals(1, extraction.candidates.size)
        assertEquals("很喜欢这个视频，价格也合适", extraction.candidates.single().commentText)
    }

    @Test
    fun `like and dislike accessibility labels never become comment names`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 900, 156, 1008),
                        hierarchyPath = listOf(2, 0),
                    ),
                    node("梅花鹿", 180, 900, 320, 950),
                    node("视频拍得很好，这个楼顶设计的真是太美啦！", 180, 960, 820, 1030),
                    node("2020-10-17", 180, 1040, 360, 1080),
                    node("回复", 370, 1040, 450, 1080),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        text = "赞0，未选中",
                        bounds = ScreenBounds(820, 950, 900, 1020),
                    ),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        text = "踩，已选中",
                        bounds = ScreenBounds(910, 950, 990, 1020),
                    ),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(1, extraction.candidates.size)
        assertEquals("梅花鹿", extraction.candidates.single().authorText)
        assertEquals("视频拍得很好，这个楼顶设计的真是太美啦！", extraction.candidates.single().commentText)
        assertEquals(ScreenBounds(48, 900, 156, 1008), extraction.candidates.single().interactionBounds)
    }

    @Test
    fun `avatar target rejects a stale path that now points to a reply or text node`() {
        val candidate = CommentUserCandidate(
            authorText = "梅花鹿",
            commentText = "视频拍得很好",
            authorBounds = ScreenBounds(180, 900, 320, 950),
            commentBounds = ScreenBounds(180, 960, 700, 1030),
            interactionBounds = ScreenBounds(48, 900, 156, 1008),
            matchedKeywords = emptyList(),
            identityKey = "comment-user:梅花鹿",
            source = CommentTextSource.ACCESSIBILITY,
            avatarBounds = ScreenBounds(48, 900, 156, 1008),
            avatarHierarchyPath = listOf(2, 0),
        )
        val context = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = listOf(2, 0),
                    className = "android.widget.TextView",
                    text = "回复",
                    bounds = ScreenBounds(360, 1040, 450, 1080),
                ),
            ),
        )

        assertEquals(null, CommentCandidateExtractor.resolveAvatarTarget(context, candidate))
    }

    @Test
    fun `comment candidate interaction target is the left avatar not the author label`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 900, 156, 1008),
                        hierarchyPath = listOf(1, 0),
                    ),
                    node("梅花鹿", 180, 900, 320, 950),
                    node("视频拍得很好", 180, 960, 700, 1030),
                ),
            ),
            matchKeywords = emptyList(),
        )

        val candidate = extraction.candidates.single()
        assertEquals(ScreenBounds(48, 900, 156, 1008), candidate.interactionBounds)
        assertEquals(listOf(1, 0), candidate.interactionHierarchyPath)
        assertEquals(listOf(1, 0), candidate.avatarHierarchyPath)
    }

    @Test
    fun `right side image next to author is never mistaken for the row avatar`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 900, 156, 1008),
                        hierarchyPath = listOf(1, 0),
                    ),
                    // This simulates a nearby visual/action image that old, loose horizontal
                    // matching preferred because it was closer to the author text.
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        bounds = ScreenBounds(192, 900, 248, 956),
                        hierarchyPath = listOf(1, 3),
                    ),
                    node("梅花鹿", 180, 900, 320, 950),
                    node("视频拍得很好", 180, 960, 700, 1030),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(1, extraction.candidates.size)
        assertEquals(ScreenBounds(48, 900, 156, 1008), extraction.candidates.single().avatarBounds)
        assertEquals(listOf(1, 0), extraction.candidates.single().avatarHierarchyPath)
    }

    @Test
    fun `first visible comment avatar anchors the first candidate despite reverse tree order`() {
        val extraction = CommentCandidateExtractor.extract(
            context = ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    node("270条评论", 400, 780, 650, 840),
                    // RecyclerView traversal can report the lower row first.
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 1380, 156, 1488),
                    ),
                    node("第二位评论者", 180, 1388, 420, 1440),
                    node("第二条评论", 180, 1450, 700, 1520),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        contentDescription = "用户头像",
                        bounds = ScreenBounds(48, 940, 156, 1048),
                    ),
                    node("第一位评论者", 180, 948, 420, 1000),
                    node("第一条评论", 180, 1010, 700, 1080),
                ),
            ),
            matchKeywords = emptyList(),
        )

        assertEquals(ScreenBounds(48, 940, 156, 1048), extraction.firstVisibleCommentAvatar)
        val first = extraction.candidates.first()
        assertEquals("第一位评论者", first.authorText)
        assertTrue(CommentCandidateExtractor.belongsToAvatarRow(first, extraction.firstVisibleCommentAvatar!!))
    }

    @Test
    fun `only an explicit author badge permits skipping a leading row`() {
        // The video author's own comment: the “作者” label excludes the row from candidates, but
        // the row still exposes visible text beside the avatar and may safely be skipped.
        val authorContext = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(48, 940, 156, 1048),
                ),
                node("店主小王·作者", 180, 948, 420, 1000),
                node("作者的评论内容", 180, 1010, 700, 1080),
            ),
        )
        assertTrue(
            CommentCandidateExtractor.hasRowText(
                authorContext,
                ScreenBounds(48, 940, 156, 1048),
            ),
        )
        assertTrue(
            CommentCandidateExtractor.hasVideoAuthorBadge(
                authorContext,
                ScreenBounds(48, 940, 156, 1048),
            ),
        )

        // Text near the leading left-side image is not an author badge. It could belong to a
        // location card or to a first row whose content is still incomplete.
        val nonAuthorContext = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    bounds = ScreenBounds(48, 940, 156, 1048),
                ),
                node("大家都在搜：任意快捷词", 180, 948, 680, 1000),
            ),
        )
        assertFalse(
            CommentCandidateExtractor.hasVideoAuthorBadge(
                nonAuthorContext,
                ScreenBounds(48, 940, 156, 1048),
            ),
        )

        // A partially virtualized first row exposes the avatar but no text; it must still be
        // treated as unresolved rather than skipped.
        val virtualizedContext = ScreenContext(
            screenSize = screen,
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "用户头像",
                    bounds = ScreenBounds(48, 940, 156, 1048),
                ),
            ),
        )
        assertFalse(
            CommentCandidateExtractor.hasRowText(
                virtualizedContext,
                ScreenBounds(48, 940, 156, 1048),
            ),
        )
        assertFalse(
            CommentCandidateExtractor.hasVideoAuthorBadge(
                virtualizedContext,
                ScreenBounds(48, 940, 156, 1048),
            ),
        )
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
        hierarchyPath: List<Int> = emptyList(),
    ) = NodeSnapshot(
        hierarchyPath = hierarchyPath,
        text = text,
        bounds = ScreenBounds(left, top, right, bottom),
        isEditable = isEditable,
        isVisibleToUser = true,
    )
}
