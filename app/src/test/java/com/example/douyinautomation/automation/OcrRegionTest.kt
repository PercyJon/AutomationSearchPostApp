package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrRegionTest {
    @Test
    fun `video action rail preserves a ratio based right-side margin`() {
        val bounds = OcrRegionGeometry.videoActionRailBounds(width = 1000, height = 2000)

        assertEquals(680, bounds.left)
        assertEquals(720, bounds.top)
        assertEquals(1000, bounds.right)
        assertEquals(1920, bounds.bottom)
    }

    @Test
    fun `home search chrome uses the top-right screen-ratio band`() {
        val bounds = OcrRegionGeometry.homeSearchChromeBounds(width = 1000, height = 2000)

        assertEquals(500, bounds.left)
        assertEquals(0, bounds.top)
        assertEquals(1000, bounds.right)
        assertEquals(400, bounds.bottom)
    }
}
