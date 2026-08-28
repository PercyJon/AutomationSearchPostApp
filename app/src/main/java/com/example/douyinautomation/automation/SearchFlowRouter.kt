package com.example.douyinautomation.automation

/**
 * Pure routing boundary for the search portion of [DouyinNavigationController].
 *
 * It expresses only the existing phase/page dispatch. The controller retains every side effect:
 * node selection, OCR, geometry validation, gestures, bounded recovery and safety pauses.
 */
object SearchFlowRouter {
    fun route(
        phase: AutomationPhase,
        page: PageKind,
    ): SearchFlowRoute? = when (phase) {
        AutomationPhase.WAITING_FOR_HOME -> when (page) {
            PageKind.HOME -> SearchFlowRoute.OPEN_SEARCH
            PageKind.SEARCH_ENTRY,
            PageKind.SEARCH_RESULTS,
            PageKind.USER_RESULTS,
            PageKind.USER_PROFILE,
            PageKind.DIRECT_MESSAGE,
            PageKind.UNKNOWN,
            -> SearchFlowRoute.RECOVER_INITIAL_SURFACE

            else -> null
        }

        AutomationPhase.WAITING_FOR_SEARCH_ENTRY -> when (page) {
            PageKind.SEARCH_ENTRY -> SearchFlowRoute.ENTER_KEYWORD
            PageKind.SEARCH_RESULTS -> SearchFlowRoute.REUSE_RESULTS_QUERY
            PageKind.DIRECT_MESSAGE -> SearchFlowRoute.RECOVER_INITIAL_SURFACE
            else -> null
        }

        AutomationPhase.WAITING_FOR_SEARCH_RESULTS -> when (page) {
            PageKind.SEARCH_RESULTS -> SearchFlowRoute.SELECT_USER_TAB
            PageKind.USER_RESULTS -> SearchFlowRoute.SELECT_VISIBLE_USER
            else -> null
        }

        AutomationPhase.WAITING_FOR_USER_RESULTS ->
            SearchFlowRoute.SELECT_VISIBLE_USER.takeIf { page == PageKind.USER_RESULTS }

        else -> null
    }
}

enum class SearchFlowRoute {
    OPEN_SEARCH,
    RECOVER_INITIAL_SURFACE,
    ENTER_KEYWORD,
    REUSE_RESULTS_QUERY,
    SELECT_USER_TAB,
    SELECT_VISIBLE_USER,
}
