package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyMessageNextRowPolicyTest {

    @Test
    fun pollsOnlyWhileUserResultsHaveNoNextStructuralRow() {
        assertTrue(
            EmptyMessageNextRowPolicy.shouldPollForNextStructuralRow(
                pageKind = PageKind.USER_RESULTS,
                nextVisible = null,
                attempt = 0,
                maxAttempts = 5,
            ),
        )
        assertFalse(
            EmptyMessageNextRowPolicy.shouldPollForNextStructuralRow(
                pageKind = PageKind.USER_RESULTS,
                nextVisible = sampleRow(),
                attempt = 0,
                maxAttempts = 5,
            ),
        )
        assertFalse(
            EmptyMessageNextRowPolicy.shouldPollForNextStructuralRow(
                pageKind = PageKind.USER_PROFILE,
                nextVisible = null,
                attempt = 0,
                maxAttempts = 5,
            ),
        )
        assertFalse(
            EmptyMessageNextRowPolicy.shouldPollForNextStructuralRow(
                pageKind = PageKind.USER_RESULTS,
                nextVisible = null,
                attempt = 5,
                maxAttempts = 5,
            ),
        )
    }

    private fun sampleRow() = StructuralUserRowMatch(
        row = NodeSnapshot(bounds = ScreenBounds(0, 627, 1056, 867)),
        anchor = NodeSnapshot(
            contentDescription = "关注按钮",
            bounds = ScreenBounds(768, 705, 1008, 789),
        ),
        source = StructuralUserRowMatch.Source.STRICT_ANCESTOR,
    )
}
