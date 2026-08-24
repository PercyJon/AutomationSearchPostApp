package com.example.douyinautomation.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSelectionFlowTest {

    @Test
    fun terminalAndContinuationRoutesInvokeTheirExistingActions() = runBlocking {
        val events = mutableListOf<String>()
        val flow = flow(events)
        val context = ScreenContext()

        assertTrue(flow.onSelectionRequested(state(maxUsers = 10, processedUserCount = 10), context))
        assertTrue(
            flow.onSelectionRequested(
                state(remoteResumePending = true, accountHelpOnly = true),
                context,
            ),
        )
        assertTrue(flow.onSelectionRequested(state(accountHelpOnly = true), context))

        assertEquals(listOf("complete_limit:10", "resume_remote_anchor", "account_help_end"), events)
    }

    @Test
    fun visibleCandidateRouteRemainsInTheController() = runBlocking {
        val events = mutableListOf<String>()
        assertFalse(flow(events).onSelectionRequested(state(), ScreenContext()))
        assertTrue(events.isEmpty())
    }

    private fun flow(events: MutableList<String>) = UserSelectionFlow(
        completeAtUserLimit = { maxUsers -> events += "complete_limit:$maxUsers" },
        resumeRemoteAnchor = { events += "resume_remote_anchor" },
        handleAccountHelpEnd = { events += "account_help_end" },
    )

    private fun state(
        maxUsers: Int? = null,
        processedUserCount: Int = 0,
        remoteResumePending: Boolean = false,
        hasViewportAnchor: Boolean = false,
        accountHelpOnly: Boolean = false,
    ) = UserSelectionFlowState(
        maxUsers = maxUsers,
        processedUserCount = processedUserCount,
        remoteResumePending = remoteResumePending,
        hasViewportAnchor = hasViewportAnchor,
        accountHelpOnly = accountHelpOnly,
    )
}
