package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupChatOverlayDetectorTest {
    private val screen = ScreenSize(1080, 2412)

    @Test
    fun screenshotGroupSheetIsOverlay() {
        val detection = GroupChatOverlayDetector.detect(screenshotGroupSheet())

        assertTrue(detection.isGroupChatOverlay)
    }

    @Test
    fun homeFeedWithMessagesTabIsNotOverlay() {
        val detection = GroupChatOverlayDetector.detect(
            ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(text = "推荐", bounds = ScreenBounds(700, 90, 820, 160)),
                    NodeSnapshot(text = "首页", bounds = ScreenBounds(40, 2280, 200, 2380)),
                    NodeSnapshot(text = "消息", isClickable = true, bounds = ScreenBounds(620, 2280, 780, 2380)),
                ),
            ),
        )

        assertFalse(detection.isGroupChatOverlay)
    }

    @Test
    fun commentSheetComposerIsNotGroupOverlay() {
        val detection = GroupChatOverlayDetector.detect(
            ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(text = "447条评论", bounds = ScreenBounds(40, 980, 280, 1050)),
                    NodeSnapshot(
                        text = "期待你的评论",
                        className = "android.widget.EditText",
                        bounds = ScreenBounds(42, 2268, 678, 2388),
                        isEditable = true,
                    ),
                    NodeSnapshot(text = "回复", bounds = ScreenBounds(470, 1320, 570, 1380)),
                ),
            ),
        )

        assertFalse(detection.isGroupChatOverlay)
    }

    private fun screenshotGroupSheet(): ScreenContext = ScreenContext(
        screenSize = screen,
        nodes = listOf(
            NodeSnapshot(text = "直播", bounds = ScreenBounds(80, 70, 180, 140)),
            NodeSnapshot(text = "推荐", bounds = ScreenBounds(700, 70, 820, 140)),
            NodeSnapshot(
                className = "android.widget.ImageView",
                contentDescription = "搜索",
                isClickable = true,
                bounds = ScreenBounds(960, 60, 1048, 148),
            ),
            NodeSnapshot(text = "岛屿 (3)", bounds = ScreenBounds(360, 220, 720, 290)),
            NodeSnapshot(text = "打招呼", isClickable = true, bounds = ScreenBounds(40, 1980, 180, 2060)),
            NodeSnapshot(text = "比心", isClickable = true, bounds = ScreenBounds(200, 1980, 300, 2060)),
            NodeSnapshot(text = "@群聊AI", isClickable = true, bounds = ScreenBounds(320, 1980, 500, 2060)),
            NodeSnapshot(
                text = "发送消息",
                className = "android.widget.EditText",
                bounds = ScreenBounds(40, 2140, 780, 2260),
                isEditable = true,
            ),
        ),
    )
}
