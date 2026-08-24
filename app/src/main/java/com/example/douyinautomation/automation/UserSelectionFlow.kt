package com.example.douyinautomation.automation

/**
 * Executes the existing terminal/continuation branches at the start of user-result selection.
 * Row identity, OCR, geometry, filters, tapping and checkpoints remain controller-owned.
 */
class UserSelectionFlow(
    private val completeAtUserLimit: suspend (Int) -> Unit,
    private val resumeRemoteAnchor: suspend (ScreenContext) -> Unit,
    private val handleAccountHelpEnd: suspend () -> Unit,
) {
    /** Returns true when an existing terminal or remote-resume route consumed the selection. */
    suspend fun onSelectionRequested(
        state: UserSelectionFlowState,
        context: ScreenContext,
    ): Boolean = when (UserSelectionFlowRouter.route(state)) {
        UserSelectionFlowRoute.COMPLETE_AT_USER_LIMIT -> {
            completeAtUserLimit(requireNotNull(state.maxUsers))
            true
        }

        UserSelectionFlowRoute.RESUME_REMOTE_ANCHOR -> {
            resumeRemoteAnchor(context)
            true
        }

        UserSelectionFlowRoute.HANDLE_ACCOUNT_HELP_END -> {
            handleAccountHelpEnd()
            true
        }

        UserSelectionFlowRoute.SELECT_VISIBLE_RESULT -> false
    }
}
