package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageDetectorTest {
    private val detector = PageDetector()

    @Test
    fun `live feed entry prompt is classified before home heuristics`() {
        val context = contextOf(
            NodeSnapshot(
                text = "点击进入直播间",
                bounds = ScreenBounds(260, 1200, 820, 1340),
                isClickable = true,
            ),
            NodeSnapshot(text = "推荐", bounds = ScreenBounds(700, 120, 900, 220)),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.LIVE_ROOM, detection.kind)
        assertTrue(detection.reasons.any { it.contains("Live-room") })
    }

    @Test
    fun `opened live room requires close and lower-right share controls`() {
        val context = contextOf(
            NodeSnapshot(
                text = "关闭",
                bounds = ScreenBounds(960, 90, 1050, 180),
                isClickable = true,
            ),
            NodeSnapshot(
                contentDescription = "分享",
                bounds = ScreenBounds(920, 1900, 1040, 2040),
                isClickable = true,
            ),
            NodeSnapshot(text = "直播间"),
        )

        assertEquals(PageKind.LIVE_ROOM_SESSION, detector.detect(context).kind)
    }

    @Test
    fun `opened live room falls back to unlabelled close and share geometry`() {
        val context = contextOf(
            NodeSnapshot(
                bounds = ScreenBounds(930, 80, 1008, 158),
                isClickable = true,
            ),
            NodeSnapshot(
                bounds = ScreenBounds(900, 1640, 980, 1720),
                isClickable = true,
            ),
            NodeSnapshot(text = "说点什么..."),
        )

        assertEquals(PageKind.LIVE_ROOM_SESSION, detector.detect(context).kind)
    }

    @Test
    fun `live feed controls without room composer are not treated as opened room`() {
        val context = contextOf(
            NodeSnapshot(
                bounds = ScreenBounds(930, 80, 1008, 158),
                isClickable = true,
            ),
            NodeSnapshot(
                bounds = ScreenBounds(900, 1640, 980, 1720),
                isClickable = true,
            ),
            NodeSnapshot(text = "直播中"),
        )

        assertFalse(detector.detect(context).kind == PageKind.LIVE_ROOM_SESSION)
    }

    @Test
    fun `captcha signal takes precedence over profile and direct message labels`() {
        val context = contextOf(
            NodeSnapshot(text = "抖音号：abc123"),
            NodeSnapshot(text = "发私信", isClickable = true),
            NodeSnapshot(text = "安全验证，请完成验证码", isVisibleToUser = true),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.HUMAN_INTERVENTION, detection.kind)
        assertTrue(detection.confidence >= 0.95f)
        assertTrue(detection.reasons.any { it.contains("Risk/captcha") })
    }

    @Test
    fun `english user tab is classified as user search results`() {
        val context = contextOf(
            NodeSnapshot(text = "All", isClickable = true),
            NodeSnapshot(
                text = "Users",
                bounds = ScreenBounds(400, 180, 600, 260),
                isClickable = true,
                isSelected = true,
            ),
            NodeSnapshot(text = "12.4K followers", isClickable = true),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.USER_RESULTS, detection.kind)
        assertTrue(detection.reasons.any { it.contains("User-result") })
    }

    @Test
    fun `non clickable user tab label is classified through its clickable parent`() {
        val context = contextOf(
            NodeSnapshot(text = "综合", bounds = ScreenBounds(70, 180, 170, 260), isClickable = true),
            NodeSnapshot(text = "用户", bounds = ScreenBounds(400, 180, 600, 260), isSelected = true),
            NodeSnapshot(text = "关注", bounds = ScreenBounds(700, 500, 900, 580)),
        )

        assertEquals(PageKind.USER_RESULTS, detector.detect(context).kind)
    }

    @Test
    fun `visible but unselected user tab remains search results`() {
        val context = contextOf(
            NodeSnapshot(text = "综合", bounds = ScreenBounds(70, 180, 170, 260), isClickable = true),
            NodeSnapshot(text = "视频", bounds = ScreenBounds(700, 180, 800, 260), isClickable = true),
            NodeSnapshot(text = "用户", bounds = ScreenBounds(820, 180, 920, 260), isClickable = true),
        )

        assertEquals(PageKind.SEARCH_RESULTS, detector.detect(context).kind)
    }

    @Test
    fun `structural follow anchors verify custom rendered user results`() {
        val rowPath = listOf(0, 1)
        val anchorPath = listOf(0, 1, 0)
        val context = contextOf(
            NodeSnapshot(text = "用户", bounds = ScreenBounds(400, 180, 600, 260), isClickable = true),
            NodeSnapshot(
                hierarchyPath = rowPath,
                className = "android.widget.FrameLayout",
                bounds = ScreenBounds(0, 400, 1080, 640),
            ),
            NodeSnapshot(
                hierarchyPath = anchorPath,
                contentDescription = "关注按钮",
                bounds = ScreenBounds(768, 465, 1008, 549),
            ),
        )

        assertEquals(PageKind.USER_RESULTS, detector.detect(context).kind)
    }

    @Test
    fun `account help separator remains a user results page when rows are present`() {
        val context = contextOf(
            NodeSnapshot(text = "用户", isSelected = true, bounds = ScreenBounds(300, 180, 460, 260)),
            NodeSnapshot(text = "关注", isClickable = true, bounds = ScreenBounds(780, 500, 1000, 580)),
            NodeSnapshot(text = "找不到想要的账号？告诉我们"),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.USER_RESULTS, detection.kind)
        assertTrue(detection.reasons.any { it.contains("找不到想找的账号") })
    }

    @Test
    fun `user results win over profile-like row metadata`() {
        val rowPath = listOf(0, 1)
        val context = contextOf(
            NodeSnapshot(text = "用户", bounds = ScreenBounds(200, 180, 360, 260), isSelected = true),
            NodeSnapshot(
                hierarchyPath = rowPath,
                bounds = ScreenBounds(0, 400, 1080, 640),
            ),
            NodeSnapshot(
                hierarchyPath = rowPath + 0,
                contentDescription = "发私信按钮",
                bounds = ScreenBounds(768, 465, 1008, 549),
            ),
            NodeSnapshot(text = "抖音号：46821855471"),
            NodeSnapshot(text = "粉丝：1139"),
        )

        assertEquals(PageKind.USER_RESULTS, detector.detect(context).kind)
    }

    @Test
    fun `user word in a result body does not create user results page`() {
        val context = contextOf(
            NodeSnapshot(text = "综合", bounds = ScreenBounds(70, 180, 170, 260), isClickable = true),
            NodeSnapshot(text = "视频", bounds = ScreenBounds(700, 180, 800, 260), isClickable = true),
            NodeSnapshot(text = "用户评价很高", bounds = ScreenBounds(80, 900, 500, 980), isClickable = true),
        )

        assertEquals(PageKind.SEARCH_RESULTS, detector.detect(context).kind)
    }

    @Test
    fun `profile button is not mistaken for an open direct message conversation`() {
        val context = contextOf(
            NodeSnapshot(text = "抖音号：douyin_demo"),
            NodeSnapshot(text = "粉丝 100", isClickable = true),
            NodeSnapshot(text = "发私信", isClickable = true),
        )

        assertEquals(PageKind.USER_PROFILE, detector.detect(context).kind)
    }

    @Test
    fun `merchant profile with customer service action is still a user profile`() {
        val context = contextOf(
            NodeSnapshot(text = "优质红木家具", contentDescription = "优质红木家具，复制名字和修改备注"),
            NodeSnapshot(text = "店铺账号", contentDescription = "抖音组织认证：店铺账号"),
            NodeSnapshot(text = "获赞"),
            NodeSnapshot(text = "粉丝"),
            NodeSnapshot(text = "关注", isClickable = true),
            NodeSnapshot(text = "联系客服", contentDescription = "私信", isClickable = true),
        )

        assertEquals(PageKind.USER_PROFILE, detector.detect(context).kind)
    }

    @Test
    fun `incidental message label beside a search field is not direct message page`() {
        val context = contextOf(
            NodeSnapshot(text = "红木沙发", hintText = "搜索", isEditable = true),
            NodeSnapshot(text = "私信", isClickable = true),
        )

        assertEquals(PageKind.SEARCH_ENTRY, detector.detect(context).kind)
    }

    @Test
    fun `search entry wins over friend suggestion profile text`() {
        val context = contextOf(
            NodeSnapshot(
                text = "是小瑜瑜呀~",
                hintText = "搜索",
                isEditable = true,
                bounds = ScreenBounds(120, 100, 760, 230),
            ),
            NodeSnapshot(text = "朋友"),
            NodeSnapshot(text = "抖音号:46821855471"),
        )

        assertEquals(PageKind.SEARCH_ENTRY, detector.detect(context).kind)
    }

    @Test
    fun `results tabs win over the editable query field that remains on results page`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    text = "是小瑜瑜呀~",
                    hintText = "搜索",
                    className = "android.widget.EditText",
                    isEditable = true,
                    bounds = ScreenBounds(132, 96, 754, 240),
                ),
                NodeSnapshot(text = "综合", bounds = ScreenBounds(40, 270, 180, 360)),
                NodeSnapshot(text = "用户", bounds = ScreenBounds(220, 270, 360, 360)),
                NodeSnapshot(text = "视频", bounds = ScreenBounds(400, 270, 540, 360)),
            ),
        )

        assertEquals(PageKind.SEARCH_RESULTS, detector.detect(context).kind)
    }

    @Test
    fun `message composer is sufficient evidence for direct message page`() {
        val context = contextOf(
            NodeSnapshot(hintText = "说点什么", isEditable = true),
            NodeSnapshot(text = "私信"),
        )

        assertEquals(PageKind.DIRECT_MESSAGE, detector.detect(context).kind)
    }

    @Test
    fun `douyin assistant question composer is direct message page`() {
        val context = contextOf(
            NodeSnapshot(text = " 输入你的问题..", isEditable = true),
        )

        assertEquals(PageKind.DIRECT_MESSAGE, detector.detect(context).kind)
    }

    @Test
    fun `unlabeled bottom composer with send action is direct message page`() {
        val context = ScreenContext(
            packageName = "com.ss.android.ugc.aweme",
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.EditText",
                    isEditable = true,
                    bounds = ScreenBounds(143, 2263, 766, 2384),
                ),
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    contentDescription = "发送",
                    isClickable = true,
                    bounds = ScreenBounds(887, 2263, 1058, 2384),
                ),
            ),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.DIRECT_MESSAGE, detection.kind)
        assertTrue(detection.reasons.any { it.contains("Bottom composer structure") })
    }

    @Test
    fun `ocr quick-question composer identifies custom chat without editable nodes`() {
        val context = ScreenContext(
            packageName = "com.ss.android.ugc.aweme",
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(text = "抖音号：designer_demo"),
                NodeSnapshot(text = "发私信", isClickable = true),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("点击发送以下常见问题", ScreenBounds(150, 1780, 900, 1880)),
            ),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.DIRECT_MESSAGE, detection.kind)
        assertTrue(detection.reasons.any { it.contains("OCR conversation composer") })
    }

    @Test
    fun `isolated profile words in feed OCR do not become a user profile`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(text = "推荐", bounds = ScreenBounds(0, 90, 160, 180)),
                NodeSnapshot(text = "首页", bounds = ScreenBounds(0, 2260, 160, 2380)),
            ),
            ocrBlocks = listOf(
                OcrTextBlock("获赞 4758"),
                OcrTextBlock("粉丝互动"),
            ),
        )

        assertTrue(detector.detect(context).kind != PageKind.USER_PROFILE)
    }

    @Test
    fun `follow gate is a restricted profile rather than a risk screen`() {
        val context = contextOf(
            NodeSnapshot(text = "关注后才能发送私信", isVisibleToUser = true),
            NodeSnapshot(text = "抖音号：designer_demo"),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.PRIVATE_MESSAGE_RESTRICTED, detection.kind)
        assertTrue(detection.reasons.any { it.contains("Private-message restriction") })
    }

    @Test
    fun `douyin voice composer with follow reminder is still direct message page`() {
        val context = contextOf(
            NodeSnapshot(text = "点关注，方便以后找到他"),
            NodeSnapshot(text = "发消息或按住说话...", isEditable = true),
        )

        assertEquals(PageKind.DIRECT_MESSAGE, detector.detect(context).kind)
    }

    @Test
    fun `message privacy failure takes precedence over the open composer`() {
        val context = contextOf(
            NodeSnapshot(text = "发消息或按住说话...", isEditable = true),
            NodeSnapshot(
                text = "对方设置了仅他关注的人可发消息，需要对方修改权限后可发消息",
                isVisibleToUser = true,
            ),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.MESSAGE_SEND_FAILED, detection.kind)
        assertTrue(detection.reasons.any { it.contains("Message-send failure") })
    }

    @Test
    fun `blank-message rejection takes precedence over the open composer`() {
        val context = contextOf(
            NodeSnapshot(text = "发消息或按住说话...", isEditable = true),
            NodeSnapshot(text = "不能发送空白消息", isVisibleToUser = true),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.MESSAGE_EMPTY_REJECTED, detection.kind)
        assertTrue(detection.reasons.any { it.contains("Blank-message probe rejection") })
    }

    @Test
    fun `ocr blank-message rejection is recognized while chat remains open`() {
        val context = ScreenContext(
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(NodeSnapshot(text = "输入你的问题", isEditable = true)),
            ocrBlocks = listOf(OcrTextBlock("不能发送"), OcrTextBlock("空白消息")),
        )

        assertEquals(PageKind.MESSAGE_EMPTY_REJECTED, detector.detect(context).kind)
    }

    @Test
    fun `ocr split privacy failure is still recognized`() {
        val context = ScreenContext(
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(NodeSnapshot(text = "发消息或按住说话...", isEditable = true)),
            ocrBlocks = listOf(
                OcrTextBlock("对方设置了仅他关注的人可发消息"),
                OcrTextBlock("需要对方修改权限后可发消息"),
            ),
        )

        assertEquals(PageKind.MESSAGE_SEND_FAILED, detector.detect(context).kind)
    }

    @Test
    fun `ocr supplies fallback when nodes have no semantic labels`() {
        val context = ScreenContext(
            packageName = "com.ss.android.ugc.aweme",
            ocrBlocks = listOf(OcrTextBlock("Captcha: verify you are human")),
        )

        val detection = detector.detect(context)

        assertEquals(PageKind.HUMAN_INTERVENTION, detection.kind)
        assertTrue(detection.reasons.any { it.contains("OCR") })
    }

    @Test
    fun `unrelated foreground package is outside target`() {
        val detection = detector.detect(
            ScreenContext(
                packageName = "com.android.settings",
                nodes = listOf(NodeSnapshot(text = "首页")),
            ),
        )

        assertEquals(PageKind.OUTSIDE_TARGET, detection.kind)
    }

    @Test
    fun `single video word in feed does not masquerade as search results`() {
        val context = contextOf(
            NodeSnapshot(text = "首页", isClickable = true),
            NodeSnapshot(text = "朋友", isClickable = true),
            NodeSnapshot(text = "视频教程和精彩内容"),
        )

        assertEquals(PageKind.HOME, detector.detect(context).kind)
    }

    @Test
    fun `live recommendation shell is recognized as home`() {
        val context = contextOf(
            NodeSnapshot(text = "关注", isClickable = true),
            NodeSnapshot(text = "商城", isClickable = true),
            NodeSnapshot(text = "推荐", isClickable = true),
        )

        assertEquals(PageKind.HOME, detector.detect(context).kind)
    }

    @Test
    fun `search keyword verifier rejects a hint or default suggestion`() {
        assertTrue(SearchKeywordVerifier.matches("红木沙发", "红木沙发"))
        assertTrue(SearchKeywordVerifier.matches(" Red  Wood ", "red wood"))
        assertFalse(SearchKeywordVerifier.matches("红木沙发", "红木家具厂"))
        assertFalse(SearchKeywordVerifier.matches("红木沙发", null))
    }

    private fun contextOf(vararg nodes: NodeSnapshot): ScreenContext = ScreenContext(
        packageName = "com.ss.android.ugc.aweme",
        nodes = nodes.toList(),
    )
}
