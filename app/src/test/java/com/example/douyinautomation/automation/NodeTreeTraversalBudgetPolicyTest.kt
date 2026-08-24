package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeTreeTraversalBudgetPolicyTest {

    @Test
    fun preservesTheExistingDepthThenNodeThenDeadlineStopPriority() {
        assertEquals(
            NodeTreeTruncationReason.MAXIMUM_DEPTH,
            stopReason(depth = 33, capturedNodeCount = 400, nowNanos = 100L, deadlineNanos = 100L),
        )
        assertEquals(
            NodeTreeTruncationReason.MAXIMUM_NODE_COUNT,
            stopReason(depth = 32, capturedNodeCount = 400, nowNanos = 100L, deadlineNanos = 100L),
        )
        assertEquals(
            NodeTreeTruncationReason.INSPECTION_DEADLINE,
            stopReason(depth = 32, capturedNodeCount = 399, nowNanos = 100L, deadlineNanos = 100L),
        )
    }

    @Test
    fun permitsTraversalInsideTheConfiguredBudget() {
        assertNull(stopReason(depth = 32, capturedNodeCount = 399, nowNanos = 99L, deadlineNanos = 100L))
    }

    @Test
    fun warnsOnceForAContinuousTruncationAndRearmsAfterACompleteSnapshot() {
        assertTrue(
            NodeTreeTruncationWarningPolicy.shouldWarn(
                previousReason = null,
                currentReason = NodeTreeTruncationReason.MAXIMUM_NODE_COUNT,
            ),
        )
        assertFalse(
            NodeTreeTruncationWarningPolicy.shouldWarn(
                previousReason = NodeTreeTruncationReason.MAXIMUM_NODE_COUNT,
                currentReason = NodeTreeTruncationReason.MAXIMUM_NODE_COUNT,
            ),
        )
        assertFalse(
            NodeTreeTruncationWarningPolicy.shouldWarn(
                previousReason = NodeTreeTruncationReason.MAXIMUM_NODE_COUNT,
                currentReason = null,
            ),
        )
        assertTrue(
            NodeTreeTruncationWarningPolicy.shouldWarn(
                previousReason = null,
                currentReason = NodeTreeTruncationReason.INSPECTION_DEADLINE,
            ),
        )
    }

    private fun stopReason(
        depth: Int,
        capturedNodeCount: Int,
        nowNanos: Long,
        deadlineNanos: Long,
    ) = NodeTreeTraversalBudgetPolicy.stopReason(
        depth = depth,
        capturedNodeCount = capturedNodeCount,
        maximumDepth = 32,
        maximumNodeCount = 400,
        nowNanos = nowNanos,
        deadlineNanos = deadlineNanos,
    )
}
