package com.example.douyinautomation.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryFlowTest {

    @Test
    fun startupAdAndSearchRecoveryInvokeTheirExistingActions() = runBlocking {
        val events = mutableListOf<String>()
        val flow = flow(events)
        val context = ScreenContext()

        flow.onTimeout(
            timedOutPhase = AutomationPhase.WAITING_FOR_HOME,
            context = context,
            page = PageKind.HOME,
            startupAdMarker = "跳过",
            timeoutDescription = "timeout",
        )
        flow.onTimeout(
            timedOutPhase = AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
            context = context,
            page = PageKind.SEARCH_ENTRY,
            startupAdMarker = null,
            timeoutDescription = "timeout",
        )
        flow.onTimeout(
            timedOutPhase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
            context = context,
            page = PageKind.SEARCH_RESULTS,
            startupAdMarker = null,
            timeoutDescription = "timeout",
        )

        assertEquals(
            listOf("wait_startup_ad:跳过:timeout", "retry_keyword", "reuse_search_entry_query"),
            events,
        )
    }

    @Test
    fun userAndProfileRecoveryInvokeTheirExistingActions() = runBlocking {
        val events = mutableListOf<String>()
        val flow = flow(events)
        val context = ScreenContext()

        flow.onTimeout(
            AutomationPhase.WAITING_FOR_USER_RESULTS,
            context,
            PageKind.USER_RESULTS,
            null,
            "timeout",
        )
        flow.onTimeout(
            AutomationPhase.WAITING_FOR_PROFILE,
            context,
            PageKind.USER_PROFILE,
            null,
            "timeout",
        )
        flow.onTimeout(
            AutomationPhase.WAITING_FOR_PROFILE,
            context,
            PageKind.UNKNOWN,
            null,
            "timeout",
        )
        flow.onTimeout(
            AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT,
            context,
            PageKind.MESSAGE_EMPTY_REJECTED,
            null,
            "timeout",
        )

        assertEquals(
            listOf("select_visible_user", "open_private_message", "skip_profile:UNKNOWN", "pause:timeout"),
            events,
        )
    }

    private fun flow(events: MutableList<String>) = RecoveryFlow(
        waitForStartupAd = { marker, timeout -> events += "wait_startup_ad:$marker:$timeout" },
        retryKeywordPreservingBudget = { events += "retry_keyword" },
        enterKeyword = { events += "enter_keyword" },
        reuseSearchEntryQuery = { events += "reuse_search_entry_query" },
        openSearch = { events += "open_search" },
        reuseHomeQuery = { events += "reuse_home_query" },
        recoverInitialSurface = { events += "recover_initial_surface" },
        selectVisibleUser = { events += "select_visible_user" },
        selectUserTab = { events += "select_user_tab" },
        openPrivateMessage = { events += "open_private_message" },
        skipProfileRecoveryFailure = { page -> events += "skip_profile:${page.name}" },
        pauseTimeout = { reason -> events += "pause:$reason" },
    )
}
