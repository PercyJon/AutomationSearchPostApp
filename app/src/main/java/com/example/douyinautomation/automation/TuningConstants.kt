package com.example.douyinautomation.automation

/**
 * Versioned, read-only tuning values for bounded automation behavior.
 *
 * This object is deliberately code-owned in M7: remote configuration must not be allowed to
 * relax page confirmation, node/OCR evidence, gesture bounds, or message-safety gates. Values
 * move here without changing their defaults before any future configuration mechanism is added.
 */
object TuningConstants {
    const val VERSION = 2

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

    /** Bounded waits, retries and stop limits used by [CommentPrivateMessageRuntime]. */
    object CommentRuntime {
        const val STEP_TIMEOUT_MS = 12_000L
        const val VIDEO_PAGE_TIMEOUT_MS = 18_000L
        const val INITIAL_ENTRY_TIMEOUT_MS = 60_000L
        const val CANDIDATE_STEP_TIMEOUT_MS = 12_000L
        const val BLANK_PROBE_TIMEOUT_MS = 8_000L
        const val PAGE_POLL_INTERVAL_MS = 350L
        const val BLANK_PROBE_SETTLE_MS = 250L

        /** Bounds retries for independently rendered profile shells and message actions. */
        const val PRIVATE_MESSAGE_ENTRY_ATTEMPTS = 4
        const val PRIVATE_MESSAGE_ENTRY_RETRY_DELAY_MS = 500L
        const val PRIVATE_MESSAGE_ENTRY_POSTCONDITION_TIMEOUT_MS = 3_200L
        const val RETURN_TO_COMMENT_DELAY_MS = 450L
        const val WORKS_SORT_SETTLE_MS = 140L

        const val FIRST_VIDEO_TRANSITION_PROBE_ATTEMPTS = 3
        const val FIRST_VIDEO_TRANSITION_INITIAL_DELAY_MS = 450L
        const val FIRST_VIDEO_TRANSITION_PROBE_INTERVAL_MS = 550L
        const val NEXT_VIDEO_TRANSITION_PROBE_ATTEMPTS = 24
        const val NEXT_VIDEO_TRANSITION_INITIAL_GRACE_MS = 180L
        const val NEXT_VIDEO_TRANSITION_PROBE_INTERVAL_MS = 500L

        /** Strict negative OCR evidence before safely skipping an unverifiable next-video entry. */
        const val NEXT_VIDEO_HIDDEN_ENTRY_LIMIT = 2
        const val NEXT_VIDEO_OCR_PROBE_LIMIT = 2
        const val VIDEO_CONTROLS_REVEAL_SETTLE_MS = 450L
        const val PROFILE_CONTENT_TIMEOUT_MS = 12_000L
        const val PROFILE_SURFACE_PROBE_ATTEMPTS = 20
        const val PROFILE_SURFACE_PROBE_INITIAL_DELAY_MS = 250L
        const val PROFILE_SURFACE_PROBE_INTERVAL_MS = 500L
        const val COMMENT_PANEL_PROBE_ATTEMPTS = 6
        const val COMMENT_PANEL_PROBE_INITIAL_DELAY_MS = 350L
        const val COMMENT_PANEL_PROBE_INTERVAL_MS = 500L

        /** One-based retry index for the existing safety-checked coordinate retry. */
        const val COMMENT_PANEL_NODE_BOUNDS_RETRY_ATTEMPT = 3
        const val INITIAL_COMMENT_CANDIDATE_READ_RETRIES = 3
        const val INITIAL_COMMENT_CANDIDATE_READ_RETRY_DELAY_MS = 450L
        const val MAX_RETURN_TO_COMMENT_BACKS = 3
        const val COMMENT_SURFACE_POLL_ATTEMPTS = 8

        /** Hard bounds that prevent unbounded comment-list or live-room navigation. */
        const val MAX_COMMENT_SCROLLS = 20
        const val MAX_STALE_SCROLLS = 2
        const val MAX_EMPTY_SCROLLS = 3
        const val POST_SCROLL_POLL_ATTEMPTS = 3
        const val POST_SCROLL_POLL_INTERVAL_MS = 600L
        const val AVATAR_RESOLVE_RETRIES = 8
        const val AVATAR_RESOLVE_RETRY_DELAY_MS = 400L
        const val AVATAR_PROFILE_RETRY_DELAY_MS = 450L
        const val MAX_LIVE_ROOM_EXITS = 3
        const val MAX_LIVE_ROOM_SWIPES = 3
        const val LIVE_ROOM_SWIPE_DURATION_MS = 460L
        const val NEXT_VIDEO_SWIPE_DURATION_MS = 520L
        const val NEXT_VIDEO_SETTLE_MS = 1_600L
        const val BLANK_PROBE_NODE_DUMP_DIRECTORY = "diagnostics/nodes"
    }
}
