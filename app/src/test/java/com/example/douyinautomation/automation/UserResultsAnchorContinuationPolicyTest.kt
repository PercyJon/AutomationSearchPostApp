package com.example.douyinautomation.automation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserResultsAnchorContinuationPolicyTest {
    @Test
    fun `continues after the same stable anchor when another visible row lacks identity`() {
        val first = UserResultsAnchorContinuationPolicy.observe(
            previous = null,
            anchorIndex = 1,
            anchorFingerprint = "previous-user",
        )
        val second = UserResultsAnchorContinuationPolicy.observe(
            previous = first,
            anchorIndex = 1,
            anchorFingerprint = "previous-user",
        )

        assertFalse(
            UserResultsAnchorContinuationPolicy.canContinue(
                observation = first,
                stableViewportObservations = 1,
                requiredObservations = 2,
            ),
        )
        assertTrue(
            UserResultsAnchorContinuationPolicy.canContinue(
                observation = second,
                stableViewportObservations = 2,
                requiredObservations = 2,
            ),
        )
    }

    @Test
    fun `does not continue when the resolved anchor changes or disappears`() {
        val first = UserResultsAnchorContinuationPolicy.observe(
            previous = null,
            anchorIndex = 1,
            anchorFingerprint = "previous-user",
        )
        val changed = UserResultsAnchorContinuationPolicy.observe(
            previous = first,
            anchorIndex = 2,
            anchorFingerprint = "previous-user",
        )
        val missing = UserResultsAnchorContinuationPolicy.observe(
            previous = changed,
            anchorIndex = -1,
            anchorFingerprint = null,
        )

        assertFalse(
            UserResultsAnchorContinuationPolicy.canContinue(
                observation = changed,
                stableViewportObservations = 2,
                requiredObservations = 2,
            ),
        )
        assertFalse(
            UserResultsAnchorContinuationPolicy.canContinue(
                observation = missing,
                stableViewportObservations = 3,
                requiredObservations = 2,
            ),
        )
    }
}
