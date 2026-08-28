package com.example.douyinautomation.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFlowTest {

    @Test
    fun searchStagesInvokeTheirExistingActions() = runBlocking {
        val events = mutableListOf<String>()
        val flow = flow(events)
        val context = ScreenContext()

        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_HOME, context, PageKind.HOME))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_HOME, context, PageKind.USER_PROFILE))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, context, PageKind.SEARCH_ENTRY))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, context, PageKind.SEARCH_RESULTS))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, context, PageKind.DIRECT_MESSAGE))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_SEARCH_RESULTS, context, PageKind.SEARCH_RESULTS))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_USER_RESULTS, context, PageKind.USER_RESULTS))

        assertEquals(
            listOf(
                "open_search",
                "recover_initial_surface",
                "enter_keyword",
                "reuse_results_query",
                "recover_initial_surface",
                "select_user_tab",
                "select_visible_user",
            ),
            events,
        )
    }

    @Test
    fun unrelatedObservationIsNotConsumed() = runBlocking {
        val events = mutableListOf<String>()
        assertFalse(
            flow(events).onPageObserved(
                AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
                ScreenContext(),
                PageKind.USER_PROFILE,
            ),
        )
        assertTrue(events.isEmpty())
    }

    private fun flow(events: MutableList<String>) = SearchFlow(
        openSearch = { events += "open_search" },
        recoverInitialSurface = { events += "recover_initial_surface" },
        enterKeyword = { events += "enter_keyword" },
        reuseResultsQuery = { events += "reuse_results_query" },
        selectUserTab = { events += "select_user_tab" },
        selectVisibleUser = { events += "select_visible_user" },
    )
}
