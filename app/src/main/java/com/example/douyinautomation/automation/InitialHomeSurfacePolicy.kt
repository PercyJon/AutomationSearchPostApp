package com.example.douyinautomation.automation

/**
 * Keeps startup observation and bounded recovery aligned when a sparse Douyin HOME tree exposes
 * only a verified semantic search entry. This is a classification policy only; it never selects
 * a node or performs an action.
 */
object InitialHomeSurfacePolicy {

    /**
     * A custom-rendered launch surface can be UNKNOWN for several frames while its HOME evidence
     * settles. That state is not evidence that global BACK is safe; the bounded startup observer
     * must collect a confirmed page or let its watchdog take the existing safe-pause path.
     */
    fun shouldDeferUnknownRecovery(
        phase: AutomationPhase,
        detectedPage: PageKind,
        initialClassificationPending: Boolean,
    ): Boolean = initialClassificationPending &&
        phase == AutomationPhase.WAITING_FOR_HOME &&
        detectedPage == PageKind.UNKNOWN

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
