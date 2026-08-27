package com.example.douyinautomation.automation

/**
 * Source-order and OCR-gating policy shared by profile and direct-message display-name flows.
 * Candidate extraction remains surface-specific and outside this policy.
 */
object DisplayNameResolutionPolicy {

    fun shouldUseProfileOcr(
        hasOcrEngine: Boolean,
        currentSource: UserResultIdentity.Source?,
        listName: String?,
        hasAccessibilityCandidate: Boolean,
    ): Boolean = hasOcrEngine && (
        currentSource == UserResultIdentity.Source.OCR ||
            isClipped(listName) ||
            isChromeListName(listName) ||
            hasAccessibilityCandidate
        )

    fun shouldUseDirectMessageOcr(
        hasOcrEngine: Boolean,
        hasAccessibilityCandidate: Boolean,
    ): Boolean = hasOcrEngine && !hasAccessibilityCandidate

    fun isClipped(value: String?): Boolean = value.orEmpty().let { candidate ->
        candidate.isBlank() ||
            candidate.contains("…") ||
            candidate.contains("..") ||
            candidate.trimEnd().endsWith('.')
    }

    /** Certification pills and trailing-colon labels are chrome, not a usable list identity. */
    fun isChromeListName(value: String?): Boolean {
        val name = value?.trim().orEmpty()
        return name.isNotEmpty() && ProfileDisplayNameResolver.isNoiseCandidate(name)
    }
}
