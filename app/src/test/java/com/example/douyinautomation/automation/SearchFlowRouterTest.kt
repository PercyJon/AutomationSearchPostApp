package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFlowRouterTest {

    @Test
    fun waitingForHomeKeepsTheExistingBoundedRecoveryRoutes() {
        assertEquals(
            SearchFlowRoute.OPEN_SEARCH,
            SearchFlowRouter.route(AutomationPhase.WAITING_FOR_HOME, PageKind.HOME),
        )
        listOf(
            PageKind.SEARCH_ENTRY,
            PageKind.SEARCH_RESULTS,
            PageKind.USER_RESULTS,
            PageKind.USER_PROFILE,
            PageKind.DIRECT_MESSAGE,
            PageKind.UNKNOWN,
        ).forEach { page ->
            assertEquals(
                SearchFlowRoute.RECOVER_INITIAL_SURFACE,
                SearchFlowRouter.route(AutomationPhase.WAITING_FOR_HOME, page),
            )
        }
        assertNull(SearchFlowRouter.route(AutomationPhase.WAITING_FOR_HOME, PageKind.PRIVATE_MESSAGE_RESTRICTED))
    }

    @Test
    fun searchAndUserResultStagesKeepTheirExistingDispatches() {
        assertEquals(
            SearchFlowRoute.ENTER_KEYWORD,
            SearchFlowRouter.route(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, PageKind.SEARCH_ENTRY),
        )
        assertEquals(
            SearchFlowRoute.REUSE_RESULTS_QUERY,
            SearchFlowRouter.route(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, PageKind.SEARCH_RESULTS),
        )
        assertEquals(
            SearchFlowRoute.SELECT_USER_TAB,
            SearchFlowRouter.route(AutomationPhase.WAITING_FOR_SEARCH_RESULTS, PageKind.SEARCH_RESULTS),
        )
        assertEquals(
            SearchFlowRoute.SELECT_VISIBLE_USER,
            SearchFlowRouter.route(AutomationPhase.WAITING_FOR_SEARCH_RESULTS, PageKind.USER_RESULTS),
        )
        assertEquals(
            SearchFlowRoute.SELECT_VISIBLE_USER,
            SearchFlowRouter.route(AutomationPhase.WAITING_FOR_USER_RESULTS, PageKind.USER_RESULTS),
        )
        assertNull(SearchFlowRouter.route(AutomationPhase.WAITING_FOR_SEARCH_RESULTS, PageKind.USER_PROFILE))
        assertNull(SearchFlowRouter.route(AutomationPhase.WAITING_FOR_PROFILE, PageKind.USER_PROFILE))
    }
}
