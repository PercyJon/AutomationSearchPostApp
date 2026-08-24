package com.example.douyinautomation.automation

/**
 * Pure precedence rule for the start of the existing user-result selection stage.
 *
 * The controller still owns result-marker inspection and every effect: remote-anchor lookup,
 * query transition, node/OCR/geometry verification, tapping and checkpoint persistence.
 */
object UserSelectionFlowRouter {
    fun route(state: UserSelectionFlowState): UserSelectionFlowRoute = when {
        state.maxUsers != null && state.processedUserCount >= state.maxUsers ->
            UserSelectionFlowRoute.COMPLETE_AT_USER_LIMIT

        !state.hasViewportAnchor && state.remoteResumePending ->
            UserSelectionFlowRoute.RESUME_REMOTE_ANCHOR

        !state.hasViewportAnchor && state.accountHelpOnly ->
            UserSelectionFlowRoute.HANDLE_ACCOUNT_HELP_END

        else -> UserSelectionFlowRoute.SELECT_VISIBLE_RESULT
    }
}

data class UserSelectionFlowState(
    val maxUsers: Int?,
    val processedUserCount: Int,
    val remoteResumePending: Boolean,
    val hasViewportAnchor: Boolean,
    val accountHelpOnly: Boolean,
)

enum class UserSelectionFlowRoute {
    COMPLETE_AT_USER_LIMIT,
    RESUME_REMOTE_ANCHOR,
    HANDLE_ACCOUNT_HELP_END,
    SELECT_VISIBLE_RESULT,
}
