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
