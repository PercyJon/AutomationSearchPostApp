package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrUserResultRowGeometryTest {

    @Test
    fun preservesReferenceMetricsAndScalesThemOnShorterScreens() {
        val reference = OcrUserResultRowGeometry.forScreen(ScreenSize(1080, 2412))
        val shorter = OcrUserResultRowGeometry.forScreen(ScreenSize(720, 1608))

        assertEquals(
            OcrUserResultRowGeometry.Metrics(
                minFollowWidth = 32,
                titleBottomTolerance = 16,
                minTitleDistance = 120,
                titleTopPadding = 28,
                rowBottomPadding = 32,
                maxStableRowDrift = 36,
            ),
            reference,
        )
        assertEquals(
            OcrUserResultRowGeometry.Metrics(
                minFollowWidth = 21,
                titleBottomTolerance = 10,
                minTitleDistance = 80,
                titleTopPadding = 18,
                rowBottomPadding = 21,
                maxStableRowDrift = 24,
            ),
            shorter,
        )
    }
}
