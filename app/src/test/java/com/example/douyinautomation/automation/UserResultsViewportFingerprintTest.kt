package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class UserResultsViewportFingerprintTest {

    @Test
    fun includesOnlyVisibleActionRailGeometryInTopOrder() {
        val context = ScreenContext(
            screenSize = ScreenSize(width = 100, height = 1_000),
            nodes = listOf(
                node(65, 300, 90, 340),
                node(64, 200, 92, 250),
                node(10, 180, 55, 220),
                node(70, 100, 95, 130),
                node(70, 400, 70, 450),
            ),
        )

        val fingerprint = UserResultsViewportFingerprint.create(context, topRatio = 0.14f)

        assertEquals("viewport|64,200,92,250;65,300,90,340;", fingerprint)
    }

    private fun node(left: Int, top: Int, right: Int, bottom: Int) = NodeSnapshot(
        bounds = ScreenBounds(left, top, right, bottom),
    )
}
