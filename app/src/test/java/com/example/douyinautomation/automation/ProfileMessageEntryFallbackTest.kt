package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileMessageEntryFallbackTest {
    @Test
    fun `selects compact right side profile action but not wide follow button`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.Button",
                    isClickable = true,
                    bounds = ScreenBounds(48, 975, 881, 1082),
                ),
                NodeSnapshot(
                    className = "android.widget.Button",
                    isClickable = true,
                    contentDescription = "发私信",
                    bounds = ScreenBounds(900, 975, 1014, 1082),
                ),
            ),
        )

        val node = ProfileMessageEntryFallback.iconNode(context)

        assertNotNull(node)
        assertEquals(ScreenBounds(900, 975, 1014, 1082), node?.bounds)
    }

    @Test
    fun `does not select unlabeled compact action that could be customer service`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.Button",
                    isClickable = true,
                    bounds = ScreenBounds(900, 975, 1014, 1082),
                ),
            ),
        )
        assertNull(ProfileMessageEntryFallback.iconNode(context))
    }

    @Test
    fun `does not select top navigation icon as profile message action`() {
        val context = ScreenContext(
            screenSize = ScreenSize(1080, 2412),
            nodes = listOf(
                NodeSnapshot(
                    className = "android.widget.ImageView",
                    isClickable = true,
                    bounds = ScreenBounds(900, 120, 1020, 240),
                ),
            ),
        )

        assertNull(ProfileMessageEntryFallback.iconNode(context))
    }

    @Test
    fun `derives action point just to the right of follow bounds`() {
        val point = ProfileMessageEntryFallback.normalizedPoint(
            screenSize = ScreenSize(1080, 2412),
            followBounds = ScreenBounds(48, 975, 881, 1082),
        )

        assertEquals(0.936f, point.x, 0.001f)
        assertEquals(0.426f, point.y, 0.002f)
    }
}
