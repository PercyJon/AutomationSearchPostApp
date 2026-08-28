package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryFlowRouterTest {

    @Test
    fun startupAdKeepsItsExistingHighestHomeRecoveryPrecedence() {
        assertEquals(
            RecoveryFlowRoute.WAIT_FOR_STARTUP_AD,
            RecoveryFlowRouter.route(
                timedOutPhase = AutomationPhase.WAITING_FOR_HOME,
                page = PageKind.HOME,
                startupAdVisible = true,
            ),
        )
    }

    @Test
    fun searchResultsTimeoutOnlyResubmitsFromTheSearchEntry() {
        assertEquals(
            RecoveryFlowRoute.RETRY_KEYWORD_PRESERVING_BUDGET,
            route(AutomationPhase.WAITING_FOR_SEARCH_RESULTS, PageKind.SEARCH_ENTRY),
        )
        assertEquals(
            RecoveryFlowRoute.PAUSE_TIMEOUT,
            route(AutomationPhase.WAITING_FOR_SEARCH_RESULTS, PageKind.USER_RESULTS),
        )
    }

    @Test
    fun searchEntryTimeoutKeepsExistingPageRoutes() {
        assertEquals(
            RecoveryFlowRoute.ENTER_KEYWORD,
            route(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, PageKind.SEARCH_ENTRY),
        )
        assertEquals(
            RecoveryFlowRoute.REUSE_SEARCH_ENTRY_QUERY,
            route(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, PageKind.SEARCH_RESULTS),
        )
        assertEquals(
            RecoveryFlowRoute.OPEN_SEARCH,
            route(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, PageKind.HOME),
        )
        assertEquals(
            RecoveryFlowRoute.RECOVER_INITIAL_SURFACE,
            route(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, PageKind.DIRECT_MESSAGE),
        )
    }

    @Test
    fun homeTimeoutKeepsExistingSearchAndBoundedBackRoutes() {
        assertEquals(RecoveryFlowRoute.OPEN_SEARCH, route(AutomationPhase.WAITING_FOR_HOME, PageKind.HOME))
        assertEquals(RecoveryFlowRoute.ENTER_KEYWORD, route(AutomationPhase.WAITING_FOR_HOME, PageKind.SEARCH_ENTRY))
        assertEquals(RecoveryFlowRoute.REUSE_HOME_QUERY, route(AutomationPhase.WAITING_FOR_HOME, PageKind.SEARCH_RESULTS))
        listOf(PageKind.USER_RESULTS, PageKind.USER_PROFILE, PageKind.DIRECT_MESSAGE).forEach { page ->
            assertEquals(RecoveryFlowRoute.RECOVER_INITIAL_SURFACE, route(AutomationPhase.WAITING_FOR_HOME, page))
        }
        assertEquals(RecoveryFlowRoute.PAUSE_TIMEOUT, route(AutomationPhase.WAITING_FOR_HOME, PageKind.UNKNOWN))
    }

    @Test
    fun userResultsTimeoutKeepsExistingSelectionRecovery() {
        assertEquals(
            RecoveryFlowRoute.SELECT_VISIBLE_USER,
            route(AutomationPhase.WAITING_FOR_USER_RESULTS, PageKind.USER_RESULTS),
        )
        assertEquals(
            RecoveryFlowRoute.SELECT_USER_TAB,
            route(AutomationPhase.WAITING_FOR_USER_RESULTS, PageKind.SEARCH_RESULTS),
        )
    }

    @Test
    fun profileTimeoutKeepsExistingMessageRetryAndFailureBoundary() {
        assertEquals(
            RecoveryFlowRoute.OPEN_PRIVATE_MESSAGE,
            route(AutomationPhase.WAITING_FOR_PROFILE, PageKind.USER_PROFILE),
        )
        assertEquals(
            RecoveryFlowRoute.SELECT_VISIBLE_USER,
            route(AutomationPhase.WAITING_FOR_PROFILE, PageKind.USER_RESULTS),
        )
        assertEquals(
            RecoveryFlowRoute.SKIP_PROFILE_RECOVERY_FAILURE,
            route(AutomationPhase.WAITING_FOR_PROFILE, PageKind.UNKNOWN),
        )
    }

    @Test
    fun unrelatedTimedOutStagesKeepTheExistingManualPause() {
        assertEquals(
            RecoveryFlowRoute.PAUSE_TIMEOUT,
            route(AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT, PageKind.MESSAGE_EMPTY_REJECTED),
        )
        assertEquals(
            RecoveryFlowRoute.PAUSE_TIMEOUT,
            RecoveryFlowRouter.route(AutomationPhase.WAITING_FOR_HOME, null, startupAdVisible = false),
        )
    }

    private fun route(
        timedOutPhase: AutomationPhase,
        page: PageKind,
    ): RecoveryFlowRoute = RecoveryFlowRouter.route(
        timedOutPhase = timedOutPhase,
        page = page,
        startupAdVisible = false,
    )
}
