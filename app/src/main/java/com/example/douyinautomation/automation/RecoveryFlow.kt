package com.example.douyinautomation.automation

/**
 * Executes existing watchdog-timeout recovery dispatch. The controller provides all effects,
 * including startup-ad wait-only behavior, retry budget handling, navigation and manual pause.
 */
class RecoveryFlow(
    private val waitForStartupAd: suspend (marker: String, timeoutDescription: String) -> Unit,
    private val retryKeywordPreservingBudget: suspend (ScreenContext) -> Unit,
    private val enterKeyword: suspend (ScreenContext) -> Unit,
    private val reuseSearchEntryQuery: suspend (ScreenContext) -> Unit,
    private val openSearch: suspend (ScreenContext) -> Unit,
    private val reuseHomeQuery: suspend (ScreenContext) -> Unit,
    private val recoverInitialSurface: suspend (ScreenContext) -> Unit,
    private val selectVisibleUser: suspend (ScreenContext) -> Unit,
    private val selectUserTab: suspend (ScreenContext) -> Unit,
    private val openPrivateMessage: suspend (ScreenContext) -> Unit,
    private val skipProfileRecoveryFailure: suspend (PageKind) -> Unit,
    private val pauseTimeout: suspend (String) -> Unit,
) {
    suspend fun onTimeout(
        timedOutPhase: AutomationPhase,
        context: ScreenContext?,
        page: PageKind?,
        startupAdMarker: String?,
        timeoutDescription: String,
    ) {
        when (
            RecoveryFlowRouter.route(
                timedOutPhase = timedOutPhase,
                page = page,
                startupAdVisible = startupAdMarker != null,
            )
        ) {
            RecoveryFlowRoute.WAIT_FOR_STARTUP_AD ->
                waitForStartupAd(requireNotNull(startupAdMarker), timeoutDescription)

            RecoveryFlowRoute.RETRY_KEYWORD_PRESERVING_BUDGET ->
                retryKeywordPreservingBudget(requireNotNull(context))

            RecoveryFlowRoute.ENTER_KEYWORD -> enterKeyword(requireNotNull(context))
            RecoveryFlowRoute.REUSE_SEARCH_ENTRY_QUERY -> reuseSearchEntryQuery(requireNotNull(context))
            RecoveryFlowRoute.OPEN_SEARCH -> openSearch(requireNotNull(context))
            RecoveryFlowRoute.REUSE_HOME_QUERY -> reuseHomeQuery(requireNotNull(context))
            RecoveryFlowRoute.RECOVER_INITIAL_SURFACE -> recoverInitialSurface(requireNotNull(context))
            RecoveryFlowRoute.SELECT_VISIBLE_USER -> selectVisibleUser(requireNotNull(context))
            RecoveryFlowRoute.SELECT_USER_TAB -> selectUserTab(requireNotNull(context))
            RecoveryFlowRoute.OPEN_PRIVATE_MESSAGE -> openPrivateMessage(requireNotNull(context))
            RecoveryFlowRoute.SKIP_PROFILE_RECOVERY_FAILURE ->
                skipProfileRecoveryFailure(page ?: PageKind.USER_RESULTS)

            RecoveryFlowRoute.PAUSE_TIMEOUT -> pauseTimeout(timeoutDescription)
        }
    }
}
