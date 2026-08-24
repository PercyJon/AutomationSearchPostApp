package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateMessageFlowRouterTest {

    @Test
    fun directMessageEntryKeepsTheExistingTerminalAndRetryRoutes() {
        assertEquals(
            PrivateMessageFlowRoute.COMPLETE_AT_MESSAGE_PAGE,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, PageKind.DIRECT_MESSAGE),
        )
        assertEquals(
            PrivateMessageFlowRoute.COMPLETE_EMPTY_PROBE,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, PageKind.MESSAGE_EMPTY_REJECTED),
        )
        assertEquals(
            PrivateMessageFlowRoute.SKIP_RESTRICTED,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, PageKind.PRIVATE_MESSAGE_RESTRICTED),
        )
        assertEquals(
            PrivateMessageFlowRoute.SKIP_DIRECT_MESSAGE_FAILURE,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, PageKind.MESSAGE_SEND_FAILED),
        )
        assertEquals(
            PrivateMessageFlowRoute.SCHEDULE_ENTRY_POSTCONDITION,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_DIRECT_MESSAGE, PageKind.USER_PROFILE),
        )
    }

    @Test
    fun messageResultStagesKeepTheExistingFailureAndSafetyRoutes() {
        assertEquals(
            PrivateMessageFlowRoute.SKIP_MESSAGE_RESULT_FAILURE,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_MESSAGE_RESULT, PageKind.MESSAGE_SEND_FAILED),
        )
        assertEquals(
            PrivateMessageFlowRoute.PAUSE_MESSAGE_RESULT_RISK,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_MESSAGE_RESULT, PageKind.HUMAN_INTERVENTION),
        )
        assertEquals(
            PrivateMessageFlowRoute.COMPLETE_EMPTY_PROBE,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT, PageKind.MESSAGE_EMPTY_REJECTED),
        )
        assertEquals(
            PrivateMessageFlowRoute.SKIP_EMPTY_PROBE_FAILURE,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT, PageKind.MESSAGE_SEND_FAILED),
        )
        assertEquals(
            PrivateMessageFlowRoute.PAUSE_EMPTY_PROBE_RISK,
            PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT, PageKind.HUMAN_INTERVENTION),
        )
        assertNull(PrivateMessageFlowRouter.route(AutomationPhase.WAITING_FOR_PROFILE, PageKind.DIRECT_MESSAGE))
    }
}
