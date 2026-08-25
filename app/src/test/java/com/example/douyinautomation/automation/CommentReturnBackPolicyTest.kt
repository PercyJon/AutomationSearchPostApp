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
