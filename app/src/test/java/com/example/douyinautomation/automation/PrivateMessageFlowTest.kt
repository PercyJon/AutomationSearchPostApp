package com.example.douyinautomation.automation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateMessageFlowTest {

    @Test
    fun directMessageAndFailurePagesInvokeTheirExistingActions() = runBlocking {
        val events = mutableListOf<String>()
        val flow = flow(events)

        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, PageKind.DIRECT_MESSAGE))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, PageKind.PRIVATE_MESSAGE_RESTRICTED))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_MESSAGE_RESULT, PageKind.MESSAGE_SEND_FAILED))

        assertEquals(listOf("complete_at_message_page", "skip_restricted", "skip_message_result_failure"), events)
    }

    @Test
    fun emptyProbeAndRiskPagesInvokeTheirExistingActions() = runBlocking {
        val events = mutableListOf<String>()
        val flow = flow(events)

        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT, PageKind.MESSAGE_EMPTY_REJECTED))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT, PageKind.HUMAN_INTERVENTION))
        assertTrue(flow.onPageObserved(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, PageKind.USER_PROFILE))

        assertEquals(listOf("complete_empty_probe", "pause_empty_probe_risk", "schedule_entry_postcondition"), events)
    }

    @Test
    fun unrelatedObservationIsNotConsumed() = runBlocking {
        val events = mutableListOf<String>()
        assertFalse(flow(events).onPageObserved(AutomationPhase.WAITING_FOR_PROFILE, PageKind.USER_PROFILE))
        assertTrue(events.isEmpty())
    }

    private fun flow(events: MutableList<String>) = PrivateMessageFlow(
        completeAtMessagePage = { events += "complete_at_message_page" },
        completeEmptyProbe = { events += "complete_empty_probe" },
        skipRestricted = { events += "skip_restricted" },
        skipDirectMessageFailure = { events += "skip_direct_message_failure" },
        scheduleEntryPostcondition = { events += "schedule_entry_postcondition" },
        skipMessageResultFailure = { events += "skip_message_result_failure" },
        pauseMessageResultRisk = { events += "pause_message_result_risk" },
        skipEmptyProbeFailure = { events += "skip_empty_probe_failure" },
        pauseEmptyProbeRisk = { events += "pause_empty_probe_risk" },
    )
}
