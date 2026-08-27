package com.example.douyinautomation.automation

/**
 * Versioned, read-only tuning values for bounded automation behavior.
 *
 * This object is deliberately code-owned in M7: remote configuration must not be allowed to
 * relax page confirmation, node/OCR evidence, gesture bounds, or message-safety gates. Values
 * move here without changing their defaults before any future configuration mechanism is added.
 */
object TuningConstants {
    const val VERSION = 7

    /** Startup polling and phase watchdog values used by [DouyinNavigationController]. */
    object NavigationLifecycle {
        /** Normal bounded phase watchdog, excluding explicitly slower startup. */
        const val STEP_TIMEOUT_MS = 12_000L

        /** Startup gets a longer window because the target app may restore an existing surface. */
        const val STARTUP_STEP_TIMEOUT_MS = 30_000L

        /** Delay after bringing the target app forward before its first reliable tree read. */
        const val INITIAL_SCREEN_SETTLE_DELAY_MS = 1_500L

        /** Bounded sampling window used when no initial callback is delivered. */
        const val INITIAL_OBSERVATION_ATTEMPTS = 24
        const val INITIAL_OBSERVATION_INTERVAL_MS = 350L

        /** Unknown initial pages may use OCR only at this bounded cadence. */
        const val INITIAL_OCR_RETRY_EVERY_OBSERVATIONS = 2
        const val INITIAL_OCR_MAX_ATTEMPTS = 6

        /**
         * Comment tasks skip launch-page OCR so a leftover comment sheet is not promoted to HOME.
         * A separate, smaller budget may OCR only for [CommentSurfaceDetector] confirmation.
         */
        const val NESTED_COMMENT_SURFACE_OCR_MAX_ATTEMPTS = 2

        /**
         * Comment-task WAITING_FOR_HOME UNKNOWN may OCR top/bottom nav bands to classify HOME.
         * These blocks are not given to [PageDetector]; search still uses node → structural → fallback.
         */
        const val COMMENT_LAUNCH_HOME_NAV_OCR_MAX_ATTEMPTS = 2

        /**
         * B-end WAITING_FOR_HOME may OCR the top-right search chrome while the tree is missing
         * or truncated. Search still uses node → structural → the existing normalized fallback.
         */
        const val EMPTY_TREE_HOME_SEARCH_CHROME_OCR_MAX_ATTEMPTS = 3

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
        const val PAGE_POLL_INTERVAL_MS = 250L
        const val BLANK_PROBE_SETTLE_MS = 250L

        /** Bounds retries for independently rendered profile shells and message actions. */
        const val PRIVATE_MESSAGE_ENTRY_ATTEMPTS = 4
        const val PRIVATE_MESSAGE_ENTRY_RETRY_DELAY_MS = 500L
        const val PRIVATE_MESSAGE_ENTRY_POSTCONDITION_TIMEOUT_MS = 3_200L
        /**
         * Between the two BACKs that return from a commenter's DM/profile to the comment sheet.
         * Keep this short; do not reuse [NavigationFlow.INITIAL_SURFACE_RECOVERY_BACK_DELAY_MS].
         */
        const val RETURN_TO_COMMENT_DELAY_MS = 120L
        const val WORKS_SORT_SETTLE_MS = 140L

        /**
         * A profile thumbnail can acknowledge its click before the detail player mounts. This
         * device exposed the media surface about four seconds later, so keep reading the same
         * live tree within the existing 18-second video-page watchdog.
         */
        const val FIRST_VIDEO_TRANSITION_PROBE_ATTEMPTS = 24
        const val FIRST_VIDEO_TRANSITION_INITIAL_DELAY_MS = 200L
        const val FIRST_VIDEO_TRANSITION_PROBE_INTERVAL_MS = 300L
        const val NEXT_VIDEO_TRANSITION_PROBE_ATTEMPTS = 24
        const val NEXT_VIDEO_TRANSITION_INITIAL_GRACE_MS = 100L
        const val NEXT_VIDEO_TRANSITION_PROBE_INTERVAL_MS = 300L

        /** Strict negative OCR evidence before safely skipping an unverifiable next-video entry. */
        const val NEXT_VIDEO_HIDDEN_ENTRY_LIMIT = 2
        const val NEXT_VIDEO_OCR_PROBE_LIMIT = 2
        const val VIDEO_CONTROLS_REVEAL_SETTLE_MS = 250L
        const val PROFILE_CONTENT_TIMEOUT_MS = 12_000L
        const val PROFILE_SURFACE_PROBE_ATTEMPTS = 20
        const val PROFILE_SURFACE_PROBE_INITIAL_DELAY_MS = 150L
        const val PROFILE_SURFACE_PROBE_INTERVAL_MS = 300L
        const val COMMENT_PANEL_PROBE_ATTEMPTS = 6
        const val COMMENT_PANEL_PROBE_INITIAL_DELAY_MS = 180L
        const val COMMENT_PANEL_PROBE_INTERVAL_MS = 300L

        /** One-based retry index for the existing safety-checked coordinate retry. */
        const val COMMENT_PANEL_NODE_BOUNDS_RETRY_ATTEMPT = 3
        const val INITIAL_COMMENT_CANDIDATE_READ_RETRIES = 3
        const val INITIAL_COMMENT_CANDIDATE_READ_RETRY_DELAY_MS = 300L
        const val MAX_RETURN_TO_COMMENT_BACKS = 3
        /** Window can be null for a beat after BACK; do not spend the back budget on empty trees. */
        const val RETURN_MISSING_CONTEXT_RETRIES = 8
        const val COMMENT_SURFACE_POLL_ATTEMPTS = 8

        /** Hard bounds that prevent unbounded comment-list or live-room navigation. */
        const val MAX_COMMENT_SCROLLS = 20
        const val MAX_STALE_SCROLLS = 2
        const val MAX_EMPTY_SCROLLS = 3
        const val POST_SCROLL_POLL_ATTEMPTS = 3
        const val POST_SCROLL_POLL_INTERVAL_MS = 300L
        const val AVATAR_RESOLVE_RETRIES = 8
        const val AVATAR_RESOLVE_RETRY_DELAY_MS = 300L
        const val AVATAR_PROFILE_RETRY_DELAY_MS = 250L
        const val MAX_LIVE_ROOM_EXITS = 3
        const val MAX_LIVE_ROOM_SWIPES = 3
        const val LIVE_ROOM_SWIPE_DURATION_MS = 460L
        /**
         * Profile-player next-work swipe. Restore the 0.84→0.28 travel that completed a 2-video
         * batch; the upper-canvas 0.38→0.12 path still reopened a continuation list (~0.57).
         */
        const val NEXT_VIDEO_SWIPE_START_Y = 0.84f
        const val NEXT_VIDEO_SWIPE_END_Y = 0.28f
        const val NEXT_VIDEO_SWIPE_DURATION_MS = 520L
        const val NEXT_VIDEO_SETTLE_MS = 700L
        /**
         * Close the comment sheet before the next-video swipe. BACK only when the sheet is open;
         * then poll until it is gone. Do not reuse [RETURN_TO_COMMENT_DELAY_MS].
         */
        const val NEXT_VIDEO_SHEET_CLOSE_POLL_ATTEMPTS = 12
        const val NEXT_VIDEO_SHEET_CLOSE_POLL_INTERVAL_MS = 150L
        /** Consecutive closed-player frames required before the next-video swipe. */
        const val NEXT_VIDEO_CLOSED_PLAYER_STABLE_SAMPLES = 3
        /** First swipe plus one retry only if the comment sheet is still open. */
        const val NEXT_VIDEO_SWIPE_MAX_ATTEMPTS = 2
        const val NEXT_VIDEO_SHEET_OPEN_RETRY_THRESHOLD = 2
        const val BLANK_PROBE_NODE_DUMP_DIRECTORY = "diagnostics/nodes"
    }

    /** Bounded navigation, recovery and normalized-gesture values used by the controller. */
    object NavigationFlow {
        const val NODE_DUMP_DIRECTORY = "diagnostics/nodes"
        const val INITIAL_CONTEXT_MAX_AGE_MS = 4_000L
        const val CURRENT_PROFILE_ENTRY_SETTLE_DELAY_MS = 700L
        const val CURRENT_PROFILE_OBSERVATION_ATTEMPTS = 120
        const val CURRENT_PROFILE_OBSERVATION_INTERVAL_MS = 500L
        const val CURRENT_PROFILE_RESUME_SETTLE_DELAY_MS = 150L
        const val CURRENT_PROFILE_RESUME_CONTEXT_ATTEMPTS = 6
        const val CURRENT_PROFILE_RESUME_CONTEXT_INTERVAL_MS = 250L
        const val CURRENT_PROFILE_CONTEXT_MAX_AGE_MS = 4_000L
        const val NEXT_TASK_SETTLE_DELAY_MS = 900L
        const val INITIAL_READY_STABLE_OBSERVATIONS = 2
        val INITIAL_READY_PAGE_KINDS = setOf(
            PageKind.HOME,
            PageKind.SEARCH_ENTRY,
            PageKind.SEARCH_RESULTS,
            PageKind.USER_RESULTS,
        )

        const val KEYWORD_POSTCONDITION_DELAY_MS = 280L
        const val MAX_NODE_SCROLL_ATTEMPTS = 2
        const val USER_RESULTS_POSTCONDITION_ATTEMPTS = 8
        const val USER_RESULTS_POSTCONDITION_INTERVAL_MS = 500L
        const val USER_ROW_POSTCONDITION_ATTEMPTS = 8
        const val USER_ROW_POSTCONDITION_INTERVAL_MS = 350L
        /**
         * After BACK to USER_RESULTS the first snapshot can predate follow-button anchors.
         * Poll this many times before the existing OCR identity continuation.
         */
        const val EMPTY_MESSAGE_NEXT_ROW_POLL_ATTEMPTS = 5
        const val P0_USER_ROW_POSTCONDITION_ATTEMPTS = 2
        const val P0_USER_ROW_POSTCONDITION_INTERVAL_MS = 120L
        const val P0_USER_RESULTS_VIEWPORT_SETTLE_ATTEMPTS = 3
        const val P0_USER_RESULTS_VIEWPORT_SETTLE_INTERVAL_MS = 350L
        const val IDENTITY_RETRY_ATTEMPTS = 3
        const val IDENTITY_RETRY_INTERVAL_MS = 450L

        /** Two exception-only OCR samples must settle before P0 geometry fallback. */
        const val P0_FIRST_USER_OCR_STABILITY_DELAY_MS = 350L
        const val VIEWPORT_ANCHOR_PROBE_ATTEMPTS = 8
        const val VIEWPORT_ANCHOR_PROBE_INTERVAL_MS = 650L
        const val VIEWPORT_IDENTITY_STABLE_OBSERVATIONS = 2
        const val REMOTE_RESUME_EXTRA_SWIPES = 8
        const val MAX_REMOTE_RESUME_SWIPES = 30
        const val MAX_VISIBLE_USER_ROWS = 20

        const val MESSAGE_ENTRY_POSTCONDITION_DELAY_MS = 250L
        const val PRIVATE_MESSAGE_ENTRY_ATTEMPTS = 3
        const val PRIVATE_MESSAGE_ENTRY_RETRY_INTERVAL_MS = 450L
        const val PRIVATE_MESSAGE_ENTRY_POSTCONDITION_ATTEMPTS = 7
        const val PRIVATE_MESSAGE_ENTRY_POSTCONDITION_INITIAL_DELAY_MS = 500L
        const val PRIVATE_MESSAGE_ENTRY_POSTCONDITION_INTERVAL_MS = 450L
        const val PRIVATE_MESSAGE_ENTRY_OCR_PROBE_ATTEMPT = 2
        /**
         * First sample after leaving a profile/DM. Callers poll until the accepted page is
         * classified or [USER_PROFILE_BACK_POLL_ATTEMPTS] is spent. Do not reuse for startup
         * leftover-sheet recovery.
         */
        const val USER_PROFILE_BACK_DELAY_MS = 200L
        const val USER_PROFILE_BACK_POLL_INTERVAL_MS = 150L
        const val USER_PROFILE_BACK_POLL_ATTEMPTS = 4
        /**
         * Startup-only. Used exclusively by [DouyinNavigationController.recoverInitialSurface]
         * when closing a leftover comment sheet. Never reuse this for comment-runtime
         * return-to-sheet (two BACKs after a blank probe) or B-end profile/results BACK.
         */
        const val INITIAL_SURFACE_RECOVERY_BACK_DELAY_MS = 2_000L
        const val MAX_BACK_ACTIONS_TO_SEARCH_ENTRY = 3
        const val MAX_INITIAL_HOME_BACK_ACTIONS = 5
        const val MAX_INITIAL_BLIND_BACK_ACTIONS = 4
        const val PROFILE_POSTCONDITION_ATTEMPTS = 16
        const val PROFILE_POSTCONDITION_INITIAL_DELAY_MS = 180L
        const val PROFILE_POSTCONDITION_INTERVAL_MS = 250L
        const val PROFILE_NAME_CONFIRM_ATTEMPTS = 2
        const val PROFILE_NAME_CONFIRM_INTERVAL_MS = 110L
        const val USER_NEXT_RESULT_DELAY_MS = 700L
        const val USER_NEXT_RESULT_POSTCONDITION_ATTEMPTS = 36
        const val USER_NEXT_RESULT_POSTCONDITION_INTERVAL_MS = 400L
        const val MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE = 2

        const val MESSAGE_ENTRY_TIMEOUT_MS = 12_000L
        const val MAX_MESSAGE_LENGTH = 500
        const val MESSAGE_TARGET_RESTORE_DELAY_MS = 700L
        const val MESSAGE_INPUT_SETTLE_DELAY_MS = 250L
        const val MESSAGE_INPUT_ATTEMPTS = 3
        const val MESSAGE_ACTION_ATTEMPTS = 3
        const val MESSAGE_INPUT_RETRY_INTERVAL_MS = 350L
        const val MESSAGE_RESULT_ATTEMPTS = 8
        const val MESSAGE_RESULT_INTERVAL_MS = 600L

        /** Bounded empty-message rejection observation; never a real-message delivery loop. */
        const val EMPTY_MESSAGE_PROBE_ATTEMPTS = 18
        const val EMPTY_MESSAGE_PROBE_INITIAL_DELAY_MS = 120L
        const val EMPTY_MESSAGE_PROBE_INTERVAL_MS = 350L
        const val EMPTY_MESSAGE_OCR_EVERY_ATTEMPTS = 4
        const val MAX_TIMEOUT_RECOVERY_ATTEMPTS = 1
        const val SEARCH_ENTRY_POSTCONDITION_ATTEMPTS = 8
        const val SEARCH_ENTRY_POSTCONDITION_INTERVAL_MS = 350L
        const val SEARCH_SUBMIT_POSTCONDITION_ATTEMPTS = 12
        const val SEARCH_SUBMIT_POSTCONDITION_DELAY_MS = 450L
        const val SEARCH_SUBMIT_POSTCONDITION_INTERVAL_MS = 500L
        const val SEARCH_SUBMIT_CONTEXT_MAX_AGE_MS = 8_000L
        const val SYSTEM_OVERLAY_WAIT_ATTEMPTS = 30
        const val SYSTEM_OVERLAY_WAIT_INTERVAL_MS = 350L

        /** Existing normalized gesture coordinates; they are ratios, not absolute pixels. */
        const val USER_PAGE_SWIPE_START_Y = 0.76f
        const val USER_PAGE_SWIPE_END_Y = 0.38f
        const val USER_PAGE_SWIPE_DURATION_MS = 480L
        const val LIVE_ROOM_SWIPE_DURATION_MS = 460L
        const val USER_RESULTS_TOP_RATIO = 0.14f
        const val USER_ROW_CONTENT_LEFT_RATIO = 0.24f
        // Custom-rendered result cards expose the profile entry at their top. Keep the gesture
        // centred on that bounded name band rather than lower follower/account metadata.
        const val USER_ROW_CONTENT_TOP_RATIO = 0.05f
        const val USER_ROW_CONTENT_BOTTOM_RATIO = 0.32f
        const val USER_ROW_SAFE_TAP_RIGHT_RATIO = 0.70f

        const val SEARCH_SUBMIT_LEFT_RATIO = 0.68f
        const val SEARCH_SUBMIT_TOP_RATIO = 0f
        const val SEARCH_SUBMIT_RIGHT_RATIO = 1f
        const val SEARCH_SUBMIT_BOTTOM_RATIO = 0.30f
        const val SEARCH_SUBMIT_MINIMUM_SCORE = 0.30f
    }

    /** OCR probe throttling and saved-checkpoint rebind timing used by the accessibility service. */
    object AccessibilityLifecycle {
        const val OCR_PROBE_INTERVAL_MS = 1_500L
        const val OCR_CACHE_TTL_MS = 4_000L
        const val REBIND_RESUME_DELAY_MS = 700L
        const val REBIND_RESUME_THROTTLE_MS = 15_000L
    }
}
