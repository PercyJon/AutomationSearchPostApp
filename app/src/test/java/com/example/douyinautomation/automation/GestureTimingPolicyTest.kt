package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureTimingPolicyTest {

    @Test
    fun preservesTheValidatedDefaultsAtTheReferenceCadence() {
        assertEquals(
            GestureTimingProfile(tapDurationMs = 60L, defaultSwipeDurationMs = 260L),
            GestureTimingPolicy.forRefreshRate(120.00001f),
        )
    }

    @Test
    fun providesMoreFramesOnLowerRefreshRateDisplaysWithinBoundedDurations() {
        assertEquals(
            GestureTimingProfile(tapDurationMs = 80L, defaultSwipeDurationMs = 347L),
            GestureTimingPolicy.forRefreshRate(90f),
        )
        assertEquals(
            GestureTimingProfile(tapDurationMs = 120L, defaultSwipeDurationMs = 500L),
            GestureTimingPolicy.forRefreshRate(60f),
        )
    }

    @Test
    fun neverShortensTheVerifiedBaselineOnFasterDisplaysOrInvalidReadings() {
        val baseline = GestureTimingProfile(tapDurationMs = 60L, defaultSwipeDurationMs = 260L)

        assertEquals(baseline, GestureTimingPolicy.forRefreshRate(240f))
        assertEquals(baseline, GestureTimingPolicy.forRefreshRate(null))
        assertEquals(baseline, GestureTimingPolicy.forRefreshRate(0f))
        assertEquals(baseline, GestureTimingPolicy.forRefreshRate(Float.NaN))
    }

    @Test
    fun postedTapIsSuccessWithoutWaitingForTheOemCallback() {
        assertEquals(
            "bounds_gesture_posted",
            GestureDispatchCallbackPolicy.postedTapOutcome().route,
        )
    }
}
