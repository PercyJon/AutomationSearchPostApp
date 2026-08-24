package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserResultsViewportTransitionDetectorTest {
    @Test
    fun `recognises a selected user pager with only hidden result content`() {
        val context = ScreenContext(
            nodes = listOf(
                node(path = listOf(1), className = "androidx.viewpager.widget.ViewPager", visible = true),
                node(path = listOf(1, 0), visible = false),
            ),
        )

        assertTrue(UserResultsViewportTransitionDetector.isSettling(context))
    }

    @Test
    fun `does not wait once the user pager exposes visible content`() {
        val context = ScreenContext(
            nodes = listOf(
                node(path = listOf(1), className = "androidx.viewpager.widget.ViewPager", visible = true),
                node(path = listOf(1, 0), visible = true),
            ),
        )

        assertFalse(UserResultsViewportTransitionDetector.isSettling(context))
    }

    private fun node(
        path: List<Int>,
        className: String? = null,
        visible: Boolean,
    ) = NodeSnapshot(
        hierarchyPath = path,
        className = className,
        bounds = ScreenBounds(0, 0, 1, 1),
        isVisibleToUser = visible,
    )
}
