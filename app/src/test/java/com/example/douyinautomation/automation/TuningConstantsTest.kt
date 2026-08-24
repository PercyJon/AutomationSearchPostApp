package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class TuningConstantsTest {

    @Test
    fun navigationLifecycleKeepsTheEstablishedBoundedStartupBudget() {
        val lifecycle = TuningConstants.NavigationLifecycle

        assertEquals(1, TuningConstants.VERSION)
        assertEquals(12_000L, lifecycle.STEP_TIMEOUT_MS)
        assertEquals(30_000L, lifecycle.STARTUP_STEP_TIMEOUT_MS)
        assertEquals(5_000L, lifecycle.INITIAL_SCREEN_SETTLE_DELAY_MS)
        assertEquals(24, lifecycle.INITIAL_OBSERVATION_ATTEMPTS)
        assertEquals(350L, lifecycle.INITIAL_OBSERVATION_INTERVAL_MS)
        assertEquals(2, lifecycle.INITIAL_OCR_RETRY_EVERY_OBSERVATIONS)
        assertEquals(6, lifecycle.INITIAL_OCR_MAX_ATTEMPTS)
        assertEquals(2, lifecycle.OCR_PAGE_STABLE_OBSERVATIONS)
    }
}
