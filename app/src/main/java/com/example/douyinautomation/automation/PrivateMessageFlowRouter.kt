package com.example.douyinautomation.automation

/** Pure phase/page routing for the controller's existing private-message safety flow. */
object PrivateMessageFlowRouter {
    fun route(
        phase: AutomationPhase,
        page: PageKind,
    ): PrivateMessageFlowRoute? = when (phase) {
        AutomationPhase.WAITING_FOR_DIRECT_MESSAGE -> when (page) {
            PageKind.DIRECT_MESSAGE -> PrivateMessageFlowRoute.COMPLETE_AT_MESSAGE_PAGE
            PageKind.MESSAGE_EMPTY_REJECTED -> PrivateMessageFlowRoute.COMPLETE_EMPTY_PROBE
            PageKind.PRIVATE_MESSAGE_RESTRICTED -> PrivateMessageFlowRoute.SKIP_RESTRICTED
            PageKind.MESSAGE_SEND_FAILED -> PrivateMessageFlowRoute.SKIP_DIRECT_MESSAGE_FAILURE
            PageKind.USER_PROFILE -> PrivateMessageFlowRoute.SCHEDULE_ENTRY_POSTCONDITION
            else -> null
        }

        AutomationPhase.WAITING_FOR_MESSAGE_RESULT -> when (page) {
            PageKind.MESSAGE_SEND_FAILED -> PrivateMessageFlowRoute.SKIP_MESSAGE_RESULT_FAILURE
            PageKind.HUMAN_INTERVENTION -> PrivateMessageFlowRoute.PAUSE_MESSAGE_RESULT_RISK
            else -> null
        }

        AutomationPhase.WAITING_FOR_EMPTY_MESSAGE_RESULT -> when (page) {
            PageKind.MESSAGE_EMPTY_REJECTED -> PrivateMessageFlowRoute.COMPLETE_EMPTY_PROBE
            PageKind.HUMAN_INTERVENTION -> PrivateMessageFlowRoute.PAUSE_EMPTY_PROBE_RISK
            PageKind.MESSAGE_SEND_FAILED -> PrivateMessageFlowRoute.SKIP_EMPTY_PROBE_FAILURE
            else -> null
        }

        else -> null
    }
}

enum class PrivateMessageFlowRoute {
    COMPLETE_AT_MESSAGE_PAGE,
    COMPLETE_EMPTY_PROBE,
    SKIP_RESTRICTED,
    SKIP_DIRECT_MESSAGE_FAILURE,
    SCHEDULE_ENTRY_POSTCONDITION,
    SKIP_MESSAGE_RESULT_FAILURE,
    PAUSE_MESSAGE_RESULT_RISK,
    SKIP_EMPTY_PROBE_FAILURE,
    PAUSE_EMPTY_PROBE_RISK,
}
