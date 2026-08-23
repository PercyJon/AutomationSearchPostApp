package com.example.douyinautomation.automation

/**
 * Keeps startup observation and bounded recovery aligned when a sparse Douyin HOME tree exposes
 * only a verified semantic search entry. This is a classification policy only; it never selects
 * a node or performs an action.
 */
object InitialHomeSurfacePolicy {

    fun normalize(
        detected: PageDetection,
        hasTransientOverlay: Boolean,
        hasSearchEntryCandidate: Boolean,
        reason: String,
    ): PageDetection {
        if (detected.kind != PageKind.UNKNOWN) return detected
        if (hasTransientOverlay || !hasSearchEntryCandidate) return detected
        return PageDetection(
            kind = PageKind.HOME,
            confidence = SEMANTIC_SEARCH_HOME_CONFIDENCE,
            reasons = listOf(reason),
        )
    }

    const val OBSERVATION_REASON = "Semantic home search-entry candidate found"
    const val RECOVERY_REASON = "Semantic home search-entry candidate found during recovery"

    private const val SEMANTIC_SEARCH_HOME_CONFIDENCE = 0.78f
}
