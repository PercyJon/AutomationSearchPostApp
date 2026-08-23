package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEntrySelectionPolicyTest {

    @Test
    fun semanticOutcomeAlwaysWins() {
        val decision = SearchEntrySelectionPolicy.decide(
            semanticOutcome = ActionOutcome.success("node_click"),
            structuralOutcome = ActionOutcome.success("structural_click"),
        )

        assertEquals("node_click", decision.outcome?.route)
        assertFalse(decision.usedStructuralFallback)
        assertFalse(decision.requiresNormalizedFallback)
    }

    @Test
    fun structuralOutcomeIsUsedOnlyAfterSemanticFailure() {
        val decision = SearchEntrySelectionPolicy.decide(
            semanticOutcome = ActionOutcome.failure("semantic missing"),
            structuralOutcome = ActionOutcome.success("structural_click"),
        )

        assertEquals("structural_click", decision.outcome?.route)
        assertTrue(decision.usedStructuralFallback)
        assertFalse(decision.requiresNormalizedFallback)
    }

    @Test
    fun normalizedFallbackIsReservedForTwoSelectorFailures() {
        val decision = SearchEntrySelectionPolicy.decide(
            semanticOutcome = ActionOutcome.failure("semantic missing"),
            structuralOutcome = ActionOutcome.failure("structural missing"),
        )

        assertNull(decision.outcome)
        assertFalse(decision.usedStructuralFallback)
        assertTrue(decision.requiresNormalizedFallback)
    }
}
