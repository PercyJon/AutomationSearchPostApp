package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class TuningConstantsTest {

    @Test
    fun navigationLifecycleKeepsTheEstablishedBoundedStartupBudget() {
        val lifecycle = TuningConstants.NavigationLifecycle

        assertEquals(5, TuningConstants.VERSION)
        assertEquals(12_000L, lifecycle.STEP_TIMEOUT_MS)
        assertEquals(30_000L, lifecycle.STARTUP_STEP_TIMEOUT_MS)
        assertEquals(1_500L, lifecycle.INITIAL_SCREEN_SETTLE_DELAY_MS)
        assertEquals(24, lifecycle.INITIAL_OBSERVATION_ATTEMPTS)
        assertEquals(350L, lifecycle.INITIAL_OBSERVATION_INTERVAL_MS)
        assertEquals(2, lifecycle.INITIAL_OCR_RETRY_EVERY_OBSERVATIONS)
        assertEquals(6, lifecycle.INITIAL_OCR_MAX_ATTEMPTS)
        assertEquals(2, lifecycle.NESTED_COMMENT_SURFACE_OCR_MAX_ATTEMPTS)
        assertEquals(2, lifecycle.COMMENT_LAUNCH_HOME_NAV_OCR_MAX_ATTEMPTS)
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
        assertEquals(120L, runtime.RETURN_TO_COMMENT_DELAY_MS)
        assertEquals(3, runtime.MAX_RETURN_TO_COMMENT_BACKS)
        assertEquals(20, runtime.MAX_COMMENT_SCROLLS)
        assertEquals(2, runtime.MAX_STALE_SCROLLS)
        assertEquals(3, runtime.MAX_EMPTY_SCROLLS)
        assertEquals(3, runtime.MAX_LIVE_ROOM_EXITS)
        assertEquals(3, runtime.MAX_LIVE_ROOM_SWIPES)
        assertEquals(250L, runtime.PAGE_POLL_INTERVAL_MS)
        assertEquals(24, runtime.FIRST_VIDEO_TRANSITION_PROBE_ATTEMPTS)
        assertEquals(200L, runtime.FIRST_VIDEO_TRANSITION_INITIAL_DELAY_MS)
        assertEquals(300L, runtime.FIRST_VIDEO_TRANSITION_PROBE_INTERVAL_MS)
        assertEquals(100L, runtime.NEXT_VIDEO_TRANSITION_INITIAL_GRACE_MS)
        assertEquals(300L, runtime.NEXT_VIDEO_TRANSITION_PROBE_INTERVAL_MS)
        assertEquals(180L, runtime.COMMENT_PANEL_PROBE_INITIAL_DELAY_MS)
        assertEquals(300L, runtime.COMMENT_PANEL_PROBE_INTERVAL_MS)
        assertEquals(300L, runtime.POST_SCROLL_POLL_INTERVAL_MS)
        assertEquals(700L, runtime.NEXT_VIDEO_SETTLE_MS)
        assertEquals(8, runtime.COMMENT_SURFACE_POLL_ATTEMPTS)
        assertEquals(12, runtime.NEXT_VIDEO_SHEET_CLOSE_POLL_ATTEMPTS)
        assertEquals(150L, runtime.NEXT_VIDEO_SHEET_CLOSE_POLL_INTERVAL_MS)
        assertEquals(3, runtime.NEXT_VIDEO_CLOSED_PLAYER_STABLE_SAMPLES)
        assertEquals(2, runtime.NEXT_VIDEO_SWIPE_MAX_ATTEMPTS)
        assertEquals(2, runtime.NEXT_VIDEO_SHEET_OPEN_RETRY_THRESHOLD)
        assertEquals(520L, runtime.NEXT_VIDEO_SWIPE_DURATION_MS)
        assertEquals(0.84f, runtime.NEXT_VIDEO_SWIPE_START_Y)
        assertEquals(0.28f, runtime.NEXT_VIDEO_SWIPE_END_Y)
    }

    @Test
    fun navigationFlowKeepsTheEstablishedRecoveryAndSafetyBounds() {
        val flow = TuningConstants.NavigationFlow

        assertEquals(120, flow.CURRENT_PROFILE_OBSERVATION_ATTEMPTS)
        assertEquals(30, flow.MAX_REMOTE_RESUME_SWIPES)
        assertEquals(20, flow.MAX_VISIBLE_USER_ROWS)
        assertEquals(5, flow.MAX_INITIAL_HOME_BACK_ACTIONS)
        assertEquals(200L, flow.USER_PROFILE_BACK_DELAY_MS)
        assertEquals(150L, flow.USER_PROFILE_BACK_POLL_INTERVAL_MS)
        assertEquals(4, flow.USER_PROFILE_BACK_POLL_ATTEMPTS)
        assertEquals(2_000L, flow.INITIAL_SURFACE_RECOVERY_BACK_DELAY_MS)
        assertEquals(4, flow.MAX_INITIAL_BLIND_BACK_ACTIONS)
        assertEquals(2, flow.MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE)
        assertEquals(500, flow.MAX_MESSAGE_LENGTH)
        assertEquals(18, flow.EMPTY_MESSAGE_PROBE_ATTEMPTS)
        assertEquals(1, flow.MAX_TIMEOUT_RECOVERY_ATTEMPTS)
        assertEquals(30, flow.SYSTEM_OVERLAY_WAIT_ATTEMPTS)
        assertEquals(3, flow.P0_USER_RESULTS_VIEWPORT_SETTLE_ATTEMPTS)
        assertEquals(350L, flow.P0_USER_RESULTS_VIEWPORT_SETTLE_INTERVAL_MS)
        assertEquals(0.76f, flow.USER_PAGE_SWIPE_START_Y)
        assertEquals(0.38f, flow.USER_PAGE_SWIPE_END_Y)
        assertEquals(0.05f, flow.USER_ROW_CONTENT_TOP_RATIO)
        assertEquals(0.32f, flow.USER_ROW_CONTENT_BOTTOM_RATIO)
    }

    @Test
    fun accessibilityLifecycleKeepsOcrAndRebindBounds() {
        val lifecycle = TuningConstants.AccessibilityLifecycle

        assertEquals(1_500L, lifecycle.OCR_PROBE_INTERVAL_MS)
        assertEquals(4_000L, lifecycle.OCR_CACHE_TTL_MS)
        assertEquals(700L, lifecycle.REBIND_RESUME_DELAY_MS)
        assertEquals(15_000L, lifecycle.REBIND_RESUME_THROTTLE_MS)
    }
}
