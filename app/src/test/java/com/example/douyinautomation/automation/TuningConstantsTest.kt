package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class TuningConstantsTest {

    @Test
    fun navigationLifecycleKeepsTheEstablishedBoundedStartupBudget() {
        val lifecycle = TuningConstants.NavigationLifecycle

        assertEquals(2, TuningConstants.VERSION)
        assertEquals(12_000L, lifecycle.STEP_TIMEOUT_MS)
        assertEquals(30_000L, lifecycle.STARTUP_STEP_TIMEOUT_MS)
        assertEquals(5_000L, lifecycle.INITIAL_SCREEN_SETTLE_DELAY_MS)
        assertEquals(24, lifecycle.INITIAL_OBSERVATION_ATTEMPTS)
        assertEquals(350L, lifecycle.INITIAL_OBSERVATION_INTERVAL_MS)
        assertEquals(2, lifecycle.INITIAL_OCR_RETRY_EVERY_OBSERVATIONS)
        assertEquals(6, lifecycle.INITIAL_OCR_MAX_ATTEMPTS)
        assertEquals(2, lifecycle.OCR_PAGE_STABLE_OBSERVATIONS)
    }

    @Test
    fun commentRuntimeKeepsTheEstablishedSafetyBounds() {
        val runtime = TuningConstants.CommentRuntime

        assertEquals(60_000L, runtime.INITIAL_ENTRY_TIMEOUT_MS)
        assertEquals(4, runtime.PRIVATE_MESSAGE_ENTRY_ATTEMPTS)
        assertEquals(3_200L, runtime.PRIVATE_MESSAGE_ENTRY_POSTCONDITION_TIMEOUT_MS)
        assertEquals(2, runtime.NEXT_VIDEO_HIDDEN_ENTRY_LIMIT)
        assertEquals(2, runtime.NEXT_VIDEO_OCR_PROBE_LIMIT)
        assertEquals(3, runtime.MAX_RETURN_TO_COMMENT_BACKS)
        assertEquals(20, runtime.MAX_COMMENT_SCROLLS)
        assertEquals(2, runtime.MAX_STALE_SCROLLS)
        assertEquals(3, runtime.MAX_EMPTY_SCROLLS)
        assertEquals(3, runtime.MAX_LIVE_ROOM_EXITS)
        assertEquals(3, runtime.MAX_LIVE_ROOM_SWIPES)
    }
}
