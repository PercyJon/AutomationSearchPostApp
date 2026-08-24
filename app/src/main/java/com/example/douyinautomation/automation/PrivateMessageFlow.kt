package com.example.douyinautomation.automation

/**
 * Executes the existing private-message phase dispatch while leaving all concrete effects owned
 * by the controller. This isolates completion, failure and risk handling from screen observation.
 */
class PrivateMessageFlow(
    private val completeAtMessagePage: suspend () -> Unit,
    private val completeEmptyProbe: suspend () -> Unit,
    private val skipRestricted: suspend () -> Unit,
    private val skipDirectMessageFailure: suspend () -> Unit,
    private val scheduleEntryPostcondition: suspend () -> Unit,
    private val skipMessageResultFailure: suspend () -> Unit,
    private val pauseMessageResultRisk: suspend () -> Unit,
    private val skipEmptyProbeFailure: suspend () -> Unit,
    private val pauseEmptyProbeRisk: suspend () -> Unit,
) {
    /** Returns true only when an existing private-message route consumed this observation. */
    suspend fun onPageObserved(
        phase: AutomationPhase,
        page: PageKind,
    ): Boolean = when (PrivateMessageFlowRouter.route(phase, page)) {
        PrivateMessageFlowRoute.COMPLETE_AT_MESSAGE_PAGE -> {
            completeAtMessagePage()
            true
        }

        PrivateMessageFlowRoute.COMPLETE_EMPTY_PROBE -> {
            completeEmptyProbe()
            true
        }

        PrivateMessageFlowRoute.SKIP_RESTRICTED -> {
            skipRestricted()
            true
        }

        PrivateMessageFlowRoute.SKIP_DIRECT_MESSAGE_FAILURE -> {
            skipDirectMessageFailure()
            true
        }

        PrivateMessageFlowRoute.SCHEDULE_ENTRY_POSTCONDITION -> {
            scheduleEntryPostcondition()
            true
        }

        PrivateMessageFlowRoute.SKIP_MESSAGE_RESULT_FAILURE -> {
            skipMessageResultFailure()
            true
        }

        PrivateMessageFlowRoute.PAUSE_MESSAGE_RESULT_RISK -> {
            pauseMessageResultRisk()
            true
        }

        PrivateMessageFlowRoute.SKIP_EMPTY_PROBE_FAILURE -> {
            skipEmptyProbeFailure()
            true
        }

        PrivateMessageFlowRoute.PAUSE_EMPTY_PROBE_RISK -> {
            pauseEmptyProbeRisk()
            true
        }

        null -> false
    }
}
