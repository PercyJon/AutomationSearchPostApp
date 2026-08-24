package com.example.douyinautomation.automation

/**
 * Executes existing search-stage routing while concrete node/OCR/geometry/gesture effects remain
 * controller-owned callbacks. This keeps page observation free of search action dispatch details.
 */
class SearchFlow(
    private val openSearch: suspend (ScreenContext) -> Unit,
    private val recoverInitialSurface: suspend (ScreenContext) -> Unit,
    private val enterKeyword: suspend (ScreenContext) -> Unit,
    private val reuseResultsQuery: suspend (ScreenContext) -> Unit,
    private val selectUserTab: suspend (ScreenContext) -> Unit,
    private val selectVisibleUser: suspend (ScreenContext) -> Unit,
) {
    /** Returns true only when an existing search-stage route consumed this observation. */
    suspend fun onPageObserved(
        phase: AutomationPhase,
        context: ScreenContext,
        page: PageKind,
    ): Boolean = when (SearchFlowRouter.route(phase, page)) {
        SearchFlowRoute.OPEN_SEARCH -> {
            openSearch(context)
            true
        }

        SearchFlowRoute.RECOVER_INITIAL_SURFACE -> {
            recoverInitialSurface(context)
            true
        }

        SearchFlowRoute.ENTER_KEYWORD -> {
            enterKeyword(context)
            true
        }

        SearchFlowRoute.REUSE_RESULTS_QUERY -> {
            reuseResultsQuery(context)
            true
        }

        SearchFlowRoute.SELECT_USER_TAB -> {
            selectUserTab(context)
            true
        }

        SearchFlowRoute.SELECT_VISIBLE_USER -> {
            selectVisibleUser(context)
            true
        }

        null -> false
    }
}
