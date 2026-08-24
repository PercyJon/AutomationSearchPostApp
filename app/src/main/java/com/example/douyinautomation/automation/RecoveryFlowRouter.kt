package com.example.douyinautomation.automation

/**
 * Pure routing boundary for the existing bounded watchdog-timeout recovery.
 *
 * The controller continues to inspect the live window and startup-ad marker, then owns every
 * effect: retry budget, delays, global BACK, node/OCR/geometry checks, task completion and pause.
 */
object RecoveryFlowRouter {
    fun route(
        timedOutPhase: AutomationPhase,
        page: PageKind?,
        startupAdVisible: Boolean,
    ): RecoveryFlowRoute {
        if (timedOutPhase == AutomationPhase.WAITING_FOR_HOME && startupAdVisible) {
            return RecoveryFlowRoute.WAIT_FOR_STARTUP_AD
        }

        return when (timedOutPhase) {
            AutomationPhase.WAITING_FOR_SEARCH_RESULTS ->
                RecoveryFlowRoute.RETRY_KEYWORD_PRESERVING_BUDGET.takeIf {
                    page == PageKind.SEARCH_ENTRY
                } ?: RecoveryFlowRoute.PAUSE_TIMEOUT

            AutomationPhase.WAITING_FOR_SEARCH_ENTRY -> when (page) {
                PageKind.SEARCH_ENTRY -> RecoveryFlowRoute.ENTER_KEYWORD
                PageKind.SEARCH_RESULTS -> RecoveryFlowRoute.REUSE_SEARCH_ENTRY_QUERY
                PageKind.HOME -> RecoveryFlowRoute.OPEN_SEARCH
                else -> RecoveryFlowRoute.PAUSE_TIMEOUT
            }

            AutomationPhase.WAITING_FOR_HOME -> when (page) {
                PageKind.HOME -> RecoveryFlowRoute.OPEN_SEARCH
                PageKind.SEARCH_ENTRY -> RecoveryFlowRoute.ENTER_KEYWORD
                PageKind.SEARCH_RESULTS -> RecoveryFlowRoute.REUSE_HOME_QUERY
                PageKind.USER_RESULTS,
                PageKind.USER_PROFILE,
                PageKind.DIRECT_MESSAGE,
                -> RecoveryFlowRoute.RECOVER_INITIAL_SURFACE

                else -> RecoveryFlowRoute.PAUSE_TIMEOUT
            }

            AutomationPhase.WAITING_FOR_USER_RESULTS -> when (page) {
                PageKind.USER_RESULTS -> RecoveryFlowRoute.SELECT_VISIBLE_USER
                PageKind.SEARCH_RESULTS -> RecoveryFlowRoute.SELECT_USER_TAB
                else -> RecoveryFlowRoute.PAUSE_TIMEOUT
            }

            AutomationPhase.WAITING_FOR_PROFILE -> when (page) {
                PageKind.USER_PROFILE -> RecoveryFlowRoute.OPEN_PRIVATE_MESSAGE
                PageKind.USER_RESULTS -> RecoveryFlowRoute.SELECT_VISIBLE_USER
                else -> RecoveryFlowRoute.SKIP_PROFILE_RECOVERY_FAILURE
            }

            else -> RecoveryFlowRoute.PAUSE_TIMEOUT
        }
    }
}

enum class RecoveryFlowRoute {
    WAIT_FOR_STARTUP_AD,
    RETRY_KEYWORD_PRESERVING_BUDGET,
    ENTER_KEYWORD,
    REUSE_SEARCH_ENTRY_QUERY,
    OPEN_SEARCH,
    REUSE_HOME_QUERY,
    RECOVER_INITIAL_SURFACE,
    SELECT_VISIBLE_USER,
    SELECT_USER_TAB,
    OPEN_PRIVATE_MESSAGE,
    SKIP_PROFILE_RECOVERY_FAILURE,
    PAUSE_TIMEOUT,
}
