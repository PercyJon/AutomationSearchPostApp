package com.example.douyinautomation.automation

/**
 * B-end WAITING_FOR_HOME classification when the accessibility tree is missing or has no search
 * node. Evidence is taken only from the top-right home chrome band (screen ratios, aligned with
 * [DouyinSelectors.searchEntry] vertically). This policy never chooses a click box; search still
 * uses node → structural → [DouyinSelectors.searchEntryNormalizedFallback].
 */
object EmptyTreeHomeSearchChromePolicy {

    /**
     * Left edge is wider than [DouyinSelectors.searchEntry.preferredRegion] so OCR can read the
     * adjacent 推荐 / 关注 tabs. The tap target stays the existing normalized fallback.
     */
    const val LEFT = 0.50f
    const val TOP = 0f
    const val RIGHT = 1f
    /** Same bottom as [DouyinSelectors.searchEntry] preferredRegion. */
    const val BOTTOM = 0.20f

    const val HOME_REASON = "OCR top-right home search chrome"
    private const val HOME_CONFIDENCE = 0.74f

    /**
     * Top-tab / search labels that can appear in the chrome band. 直播 is omitted because a live
     * room can show that word without a safe home search icon.
     */
    private val CHROME_TERMS = listOf("推荐", "关注") + DouyinLabels.search

    fun shouldProbe(
        isCommentTask: Boolean,
        phase: AutomationPhase,
        pageIsUnknown: Boolean,
        hasSearchEntryCandidate: Boolean,
        hasOcrBlocks: Boolean,
        attempts: Int,
        maxAttempts: Int,
    ): Boolean = !isCommentTask &&
        phase == AutomationPhase.WAITING_FOR_HOME &&
        pageIsUnknown &&
        !hasSearchEntryCandidate &&
        !hasOcrBlocks &&
        attempts < maxAttempts

    fun classify(context: ScreenContext): PageDetection? {
        if (chromeHits(context) < 1) return null
        return PageDetection(
            kind = PageKind.HOME,
            confidence = HOME_CONFIDENCE,
            reasons = listOf(HOME_REASON),
        )
    }

    fun chromeHits(context: ScreenContext): Int {
        val width = context.screenSize.width
        val height = context.screenSize.height
        if (width <= 0 || height <= 0) return 0
        val terms = linkedSetOf<String>()
        for (block in context.ocrBlocks) {
            if (block.bounds.width <= 0 || block.bounds.height <= 0) continue
            val centerX = block.bounds.centerX / width.toFloat()
            val centerY = block.bounds.centerY / height.toFloat()
            if (centerX < LEFT || centerX > RIGHT || centerY < TOP || centerY > BOTTOM) continue
            terms += TextNormalizer.matchingTerms(block.text, CHROME_TERMS)
        }
        return terms.size
    }
}
