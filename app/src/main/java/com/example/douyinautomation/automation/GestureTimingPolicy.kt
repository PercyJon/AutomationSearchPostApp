package com.example.douyinautomation.automation

import kotlin.math.roundToLong

/**
 * Derives the default accessibility gesture timings from the active display cadence.
 *
 * The baseline is the 120 Hz device on which the existing 60 ms tap and 260 ms swipe
 * behaviour was verified. Lower-refresh displays receive more frame intervals, while the
 * lower bounds keep a faster display from receiving a shorter, less stable gesture.
 */
data class GestureTimingProfile(
    val tapDurationMs: Long,
    val defaultSwipeDurationMs: Long,
)

object GestureTimingPolicy {
    private const val BASELINE_REFRESH_RATE_HZ = 120f
    private const val BASELINE_TAP_DURATION_MS = 60L
    private const val BASELINE_SWIPE_DURATION_MS = 260L
    private const val MAX_TAP_DURATION_MS = 120L
    private const val MAX_SWIPE_DURATION_MS = 500L

    fun forRefreshRate(refreshRateHz: Float?): GestureTimingProfile {
        val validRefreshRate = refreshRateHz?.takeIf { it.isFinite() && it > 0f }
            ?: return baselineProfile()
        val cadenceScale = BASELINE_REFRESH_RATE_HZ / validRefreshRate

        return GestureTimingProfile(
            tapDurationMs = scaledDuration(
                baselineMs = BASELINE_TAP_DURATION_MS,
                cadenceScale = cadenceScale,
                maximumMs = MAX_TAP_DURATION_MS,
            ),
            defaultSwipeDurationMs = scaledDuration(
                baselineMs = BASELINE_SWIPE_DURATION_MS,
                cadenceScale = cadenceScale,
                maximumMs = MAX_SWIPE_DURATION_MS,
            ),
        )
    }

    private fun baselineProfile() = GestureTimingProfile(
        tapDurationMs = BASELINE_TAP_DURATION_MS,
        defaultSwipeDurationMs = BASELINE_SWIPE_DURATION_MS,
    )

    private fun scaledDuration(
        baselineMs: Long,
        cadenceScale: Float,
        maximumMs: Long,
    ): Long = (baselineMs * cadenceScale)
        .roundToLong()
        .coerceIn(baselineMs, maximumMs)
}
