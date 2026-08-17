package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenBoundsTest {

    @Test
    fun invertedCoordinatesCanBeNormalizedBeforeSnapshotCreation() {
        val bounds = ScreenBounds(
            left = minOf(240, 120),
            top = minOf(480, 320),
            right = maxOf(240, 120),
            bottom = maxOf(480, 320),
        )

        assertEquals(120, bounds.left)
        assertEquals(320, bounds.top)
        assertEquals(240, bounds.right)
        assertEquals(480, bounds.bottom)
    }
}
