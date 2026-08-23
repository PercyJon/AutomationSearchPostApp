package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentPanelScrollTargetSelectorTest {
    private val screen = ScreenSize(1080, 2400)

    @Test
    fun `comment recycler is chosen instead of the taller feed pager`() {
        val feedPager = scrollable(
            path = listOf(0),
            className = "androidx.viewpager.widget.ViewPager",
            bounds = ScreenBounds(0, 0, 1080, 2400),
        )
        val sheetPager = scrollable(
            path = listOf(1),
            className = "androidx.viewpager.widget.ViewPager",
            bounds = ScreenBounds(0, 1104, 1080, 2400),
        )
        val commentRecycler = scrollable(
            path = listOf(2),
            className = "androidx.recyclerview.widget.RecyclerView",
            bounds = ScreenBounds(0, 1104, 1080, 2243),
        )

        val selected = CommentPanelScrollTargetSelector.select(
            ScreenContext(screenSize = screen, nodes = listOf(feedPager, sheetPager, commentRecycler)),
        )

        assertEquals(commentRecycler, selected)
    }

    @Test
    fun `lower custom sheet container is used when no list widget is exposed`() {
        val feedPager = scrollable(
            path = listOf(0),
            className = "androidx.viewpager.widget.ViewPager",
            bounds = ScreenBounds(0, 0, 1080, 2400),
        )
        val sheetContainer = scrollable(
            path = listOf(1),
            className = "android.view.ViewGroup",
            bounds = ScreenBounds(0, 1160, 1080, 2240),
        )

        val selected = CommentPanelScrollTargetSelector.select(
            ScreenContext(screenSize = screen, nodes = listOf(feedPager, sheetContainer)),
        )

        assertEquals(sheetContainer, selected)
    }

    private fun scrollable(
        path: List<Int>,
        className: String,
        bounds: ScreenBounds,
    ) = NodeSnapshot(
        hierarchyPath = path,
        className = className,
        bounds = bounds,
        isScrollable = true,
    )
}
