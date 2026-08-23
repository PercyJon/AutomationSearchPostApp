package com.example.douyinautomation.automation

/** Preserves the safe search-entry selector order before any constrained gesture fallback. */
object SearchEntrySelectionPolicy {

    fun decide(
        semanticOutcome: ActionOutcome,
        structuralOutcome: ActionOutcome?,
    ): Decision = when {
        semanticOutcome.succeeded -> Decision(
            outcome = semanticOutcome,
            usedStructuralFallback = false,
            requiresNormalizedFallback = false,
        )

        structuralOutcome?.succeeded == true -> Decision(
            outcome = structuralOutcome,
            usedStructuralFallback = true,
            requiresNormalizedFallback = false,
        )

        else -> Decision(
            outcome = null,
            usedStructuralFallback = false,
            requiresNormalizedFallback = true,
        )
    }

    data class Decision(
        val outcome: ActionOutcome?,
        val usedStructuralFallback: Boolean,
        val requiresNormalizedFallback: Boolean,
    )
}
