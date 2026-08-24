package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotNodeOnlyFallbackPolicyTest {

    @Test
    fun selectsPureNodeTreeOnlyForProtectedWindows() {
        assertEquals(
            ScreenshotNodeOnlyFallbackReason.SECURE_WINDOW,
            ScreenshotNodeOnlyFallbackPolicy.reasonFor(ScreenshotCaptureFailureKind.SECURE_WINDOW),
        )
        assertNull(ScreenshotNodeOnlyFallbackPolicy.reasonFor(ScreenshotCaptureFailureKind.INTERVAL_TOO_SHORT))
        assertNull(ScreenshotNodeOnlyFallbackPolicy.reasonFor(ScreenshotCaptureFailureKind.UNKNOWN))
        assertNull(ScreenshotNodeOnlyFallbackPolicy.reasonFor(null))
    }

    @Test
    fun reportsTheStartOfAProtectedWindowFallbackOnlyOnceUntilCaptureRecovers() {
        val secure = ScreenshotNodeOnlyFallbackReason.SECURE_WINDOW

        assertTrue(ScreenshotNodeOnlyFallbackPolicy.shouldReport(previousReason = null, currentReason = secure))
        assertFalse(ScreenshotNodeOnlyFallbackPolicy.shouldReport(previousReason = secure, currentReason = secure))
        assertFalse(ScreenshotNodeOnlyFallbackPolicy.shouldReport(previousReason = secure, currentReason = null))
        assertTrue(ScreenshotNodeOnlyFallbackPolicy.shouldReport(previousReason = null, currentReason = secure))
    }
}
