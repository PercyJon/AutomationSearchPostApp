package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentReturnBackPolicyTest {

    @Test
    fun skipProbeDoesNotSecondBackUntilProfileLeavePollFinishes() {
        assertFalse(
            CommentReturnBackPolicy.shouldDispatchAnotherReturnBack(
                stillNested = true,
                backsDispatched = 1,
                requiredBacks = 1,
                stillOnUserProfile = true,
                profileLeavePollCompleted = false,
            ),
        )
        assertTrue(
            CommentReturnBackPolicy.shouldPollForProfileLeave(
                stillOnUserProfile = true,
                backsDispatched = 1,
                requiredBacks = 1,
            ),
        )
    }

    @Test
    fun skipProbeMaySecondBackOnlyIfStillOnProfileAfterPoll() {
        assertTrue(
            CommentReturnBackPolicy.shouldDispatchAnotherReturnBack(
                stillNested = true,
                backsDispatched = 1,
                requiredBacks = 1,
                stillOnUserProfile = true,
                profileLeavePollCompleted = true,
            ),
        )
        assertFalse(
            CommentReturnBackPolicy.shouldDispatchAnotherReturnBack(
                stillNested = false,
                backsDispatched = 1,
                requiredBacks = 1,
                stillOnUserProfile = false,
                profileLeavePollCompleted = true,
            ),
        )
    }

    @Test
    fun returnSkipsActionRailOcrWhenNodesAlreadyProveTheCommentSheet() {
        assertFalse(
            CommentReturnBackPolicy.shouldEnrichReturnWithActionRailOcr(
                stillNested = false,
                nodeCommentSurfaceReady = true,
                ocrAlreadyAttempted = false,
            ),
        )
        assertTrue(
            CommentReturnBackPolicy.shouldEnrichReturnWithActionRailOcr(
                stillNested = false,
                nodeCommentSurfaceReady = false,
                ocrAlreadyAttempted = false,
            ),
        )
        assertFalse(
            CommentReturnBackPolicy.shouldEnrichReturnWithActionRailOcr(
                stillNested = true,
                nodeCommentSurfaceReady = false,
                ocrAlreadyAttempted = false,
            ),
        )
        assertFalse(
            CommentReturnBackPolicy.shouldEnrichReturnWithActionRailOcr(
                stillNested = false,
                nodeCommentSurfaceReady = false,
                ocrAlreadyAttempted = true,
            ),
        )
    }

    @Test
    fun missingReturnWindowWaitsInsteadOfConsumingBackBudget() {
        assertTrue(
            CommentReturnBackPolicy.shouldWaitForReturnContext(
                contextMissing = true,
                missingRetries = 0,
                missingRetryLimit = 8,
            ),
        )
        assertFalse(
            CommentReturnBackPolicy.shouldWaitForReturnContext(
                contextMissing = false,
                missingRetries = 0,
                missingRetryLimit = 8,
            ),
        )
        assertFalse(
            CommentReturnBackPolicy.shouldWaitForReturnContext(
                contextMissing = true,
                missingRetries = 8,
                missingRetryLimit = 8,
            ),
        )
    }

    @Test
    fun returnSkipsUnknownPageOcrWhileWaitingForTheCommentSheet() {
        assertTrue(
            CommentReturnBackPolicy.shouldBypassUnknownPageOcrDuringReturn(
                awaitingCommentSurfaceReturn = true,
            ),
        )
        assertFalse(
            CommentReturnBackPolicy.shouldBypassUnknownPageOcrDuringReturn(
                awaitingCommentSurfaceReturn = false,
            ),
        )
    }

    @Test
    fun dmPathTakesTheSecondBackWithoutAProfileLeavePoll() {
        assertTrue(
            CommentReturnBackPolicy.shouldDispatchAnotherReturnBack(
                stillNested = true,
                backsDispatched = 1,
                requiredBacks = 2,
                stillOnUserProfile = true,
                profileLeavePollCompleted = false,
            ),
        )
        assertFalse(
            CommentReturnBackPolicy.shouldPollForProfileLeave(
                stillOnUserProfile = true,
                backsDispatched = 1,
                requiredBacks = 2,
            ),
        )
    }
}
