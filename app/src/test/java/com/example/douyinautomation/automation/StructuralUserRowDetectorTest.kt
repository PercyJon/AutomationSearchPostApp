package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuralUserRowDetectorTest {

    @Test
    fun `matches current douyin row through strict ancestor and excludes avatar`() {
        val rowPath = listOf(0, 1, 2)
        val anchorPath = rowPath + 1
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            packageName = "com.ss.android.ugc.aweme",
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = rowPath,
                    className = "android.widget.FrameLayout",
                    bounds = ScreenBounds(0, 867, 1056, 1107),
                ),
                NodeSnapshot(
                    hierarchyPath = anchorPath,
                    contentDescription = "关注按钮",
                    bounds = ScreenBounds(768, 945, 1008, 1029),
                ),
                NodeSnapshot(
                    hierarchyPath = rowPath + 0,
                    className = "android.view.ViewGroup",
                    bounds = ScreenBounds(48, 891, 240, 1083),
                ),
            ),
        )

        val match = StructuralUserRowDetector.find(context)

        assertNotNull(match)
        assertEquals(StructuralUserRowMatch.Source.STRICT_ANCESTOR, match?.source)
        assertTrue(match!!.row.bounds.left <= 48)
        assertTrue(match.row.bounds.right < 1080)
    }

    @Test
    fun `uses vertical overlap when wrapper path is unavailable`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = listOf(9),
                    className = "android.view.ViewGroup",
                    bounds = ScreenBounds(0, 867, 1056, 1107),
                ),
                NodeSnapshot(
                    hierarchyPath = listOf(1, 2, 3),
                    contentDescription = "关注按钮",
                    bounds = ScreenBounds(768, 945, 1008, 1029),
                ),
            ),
        )

        val match = StructuralUserRowDetector.find(context)

        assertNotNull(match)
        assertEquals(StructuralUserRowMatch.Source.VERTICAL_OVERLAP, match?.source)
    }

    @Test
    fun `recognizes follow back action and can find the next visible row`() {
        val firstRowPath = listOf(0, 1)
        val firstAnchorPath = firstRowPath + 0
        val secondRowPath = listOf(0, 2)
        val secondAnchorPath = secondRowPath + 0
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = firstRowPath,
                    bounds = ScreenBounds(0, 400, 1056, 640),
                ),
                NodeSnapshot(
                    hierarchyPath = firstAnchorPath,
                    text = "回关",
                    contentDescription = "回关按钮",
                    bounds = ScreenBounds(768, 465, 1008, 549),
                ),
                NodeSnapshot(
                    hierarchyPath = secondRowPath,
                    bounds = ScreenBounds(0, 660, 1056, 900),
                ),
                NodeSnapshot(
                    hierarchyPath = secondAnchorPath,
                    text = "关注",
                    contentDescription = "关注按钮",
                    bounds = ScreenBounds(768, 725, 1008, 809),
                ),
            ),
        )

        val first = StructuralUserRowDetector.find(context)
        val second = first?.let { StructuralUserRowDetector.findAfter(context, it.anchor.bounds.bottom.toFloat()) }

        assertNotNull(first)
        assertEquals(UserFollowActionState.FOLLOW_BACK, StructuralUserRowDetector.followActionState(context, first!!))
        assertNotNull(second)
        assertEquals(UserFollowActionState.FOLLOW, StructuralUserRowDetector.followActionState(context, second!!))
    }

    @Test
    fun `recognizes a private message action as a user row anchor`() {
        val row = NodeSnapshot(
            hierarchyPath = listOf(4),
            bounds = ScreenBounds(0, 360, 1080, 680),
        )
        val messageAction = NodeSnapshot(
            hierarchyPath = listOf(4, 1),
            text = "发私信",
            bounds = ScreenBounds(760, 455, 1008, 565),
        )
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(row, messageAction),
        )

        val match = StructuralUserRowDetector.find(context)

        assertNotNull(match)
        assertEquals(messageAction.hierarchyPath, match?.anchor?.hierarchyPath)
        assertEquals(UserFollowActionState.FOLLOW, StructuralUserRowDetector.followActionState(context, match!!))
    }

    @Test
    fun `scales row height eligibility with the actual display height`() {
        val shortScreen = ScreenSize(720, 1608)

        val accepted = StructuralUserRowDetector.find(rowContext(shortScreen, rowHeight = 160))
        val rejected = StructuralUserRowDetector.find(rowContext(shortScreen, rowHeight = 280))

        assertNotNull(accepted)
        assertEquals(120..266, StructuralUserRowDetector.rowHeightRange(shortScreen.height))
        assertEquals(null, rejected)
    }

    private fun rowContext(screenSize: ScreenSize, rowHeight: Int): ScreenContext {
        val rowTop = (screenSize.height * 0.20f).toInt()
        val rowPath = listOf(1)
        return ScreenContext(
            screenSize = screenSize,
            nodes = listOf(
                NodeSnapshot(
                    hierarchyPath = rowPath,
                    bounds = ScreenBounds(0, rowTop, (screenSize.width * 0.98f).toInt(), rowTop + rowHeight),
                ),
                NodeSnapshot(
                    hierarchyPath = rowPath + 0,
                    contentDescription = "关注按钮",
                    bounds = ScreenBounds(
                        (screenSize.width * 0.71f).toInt(),
                        rowTop + rowHeight / 3,
                        (screenSize.width * 0.93f).toInt(),
                        rowTop + rowHeight / 2,
                    ),
                ),
            ),
        )
    }
}
