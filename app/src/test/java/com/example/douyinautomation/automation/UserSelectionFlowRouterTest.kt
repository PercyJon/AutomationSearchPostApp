package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSelectionFlowRouterTest {

    @Test
    fun userLimitKeepsItsExistingHighestPrecedence() {
        assertEquals(
            UserSelectionFlowRoute.COMPLETE_AT_USER_LIMIT,
            route(
                maxUsers = 10,
                processedUserCount = 10,
                remoteResumePending = true,
                hasViewportAnchor = false,
                accountHelpOnly = true,
            ),
        )
    }

    @Test
    fun remoteResumePrecedesAccountHelpOnlyWithoutAnAnchor() {
        assertEquals(
            UserSelectionFlowRoute.RESUME_REMOTE_ANCHOR,
            route(
                maxUsers = 10,
                processedUserCount = 3,
                remoteResumePending = true,
                hasViewportAnchor = false,
                accountHelpOnly = true,
            ),
        )
    }

    @Test
    fun accountHelpOnlyEndsTheBoundedResultsStageWithoutAnAnchor() {
        assertEquals(
            UserSelectionFlowRoute.HANDLE_ACCOUNT_HELP_END,
            route(
                maxUsers = null,
                processedUserCount = 3,
                remoteResumePending = false,
                hasViewportAnchor = false,
                accountHelpOnly = true,
            ),
        )
    }

    @Test
    fun aViewportAnchorKeepsExistingContinuationOnTheVisibleResult() {
        assertEquals(
            UserSelectionFlowRoute.SELECT_VISIBLE_RESULT,
            route(
                maxUsers = 10,
                processedUserCount = 3,
                remoteResumePending = true,
                hasViewportAnchor = true,
                accountHelpOnly = true,
            ),
        )
    }

    @Test
    fun ordinaryResultSelectionKeepsTheExistingDefaultRoute() {
        assertEquals(
            UserSelectionFlowRoute.SELECT_VISIBLE_RESULT,
            route(
                maxUsers = null,
                processedUserCount = 0,
                remoteResumePending = false,
                hasViewportAnchor = false,
                accountHelpOnly = false,
            ),
        )
    }

    @Test
    fun unsuccessfulRowsDoNotFillTheConfiguredUserCount() {
        assertEquals(
            UserSelectionFlowRoute.SELECT_VISIBLE_RESULT,
            route(
                maxUsers = 5,
                processedUserCount = 4,
                remoteResumePending = false,
                hasViewportAnchor = false,
                accountHelpOnly = false,
            ),
        )
    }

    private fun route(
        maxUsers: Int?,
        processedUserCount: Int,
        remoteResumePending: Boolean,
        hasViewportAnchor: Boolean,
        accountHelpOnly: Boolean,
    ): UserSelectionFlowRoute = UserSelectionFlowRouter.route(
        UserSelectionFlowState(
            maxUsers = maxUsers,
            processedUserCount = processedUserCount,
            remoteResumePending = remoteResumePending,
            hasViewportAnchor = hasViewportAnchor,
            accountHelpOnly = accountHelpOnly,
        ),
    )
}
