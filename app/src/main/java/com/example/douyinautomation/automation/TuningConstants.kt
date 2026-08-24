package com.example.douyinautomation.automation

/**
 * Versioned, read-only tuning values for bounded automation behavior.
 *
 * This object is deliberately code-owned in M7: remote configuration must not be allowed to
 * relax page confirmation, node/OCR evidence, gesture bounds, or message-safety gates. Values
 * move here without changing their defaults before any future configuration mechanism is added.
 */
object TuningConstants {
    const val VERSION = 1

    /** Startup polling and phase watchdog values used by [DouyinNavigationController]. */
    object NavigationLifecycle {
        /** Normal bounded phase watchdog, excluding explicitly slower startup. */
        const val STEP_TIMEOUT_MS = 12_000L

        /** Startup gets a longer window because the target app may restore an existing surface. */
        const val STARTUP_STEP_TIMEOUT_MS = 30_000L

        /** Delay after bringing the target app forward before its first reliable tree read. */
        const val INITIAL_SCREEN_SETTLE_DELAY_MS = 5_000L

        /** Bounded sampling window used when no initial callback is delivered. */
        const val INITIAL_OBSERVATION_ATTEMPTS = 24
        const val INITIAL_OBSERVATION_INTERVAL_MS = 350L

        /** Unknown initial pages may use OCR only at this bounded cadence. */
        const val INITIAL_OCR_RETRY_EVERY_OBSERVATIONS = 2
        const val INITIAL_OCR_MAX_ATTEMPTS = 6

        /** A detected OCR-backed message page needs this many stable observations. */
        const val OCR_PAGE_STABLE_OBSERVATIONS = 2
    }
}
