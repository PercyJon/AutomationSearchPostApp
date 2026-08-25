package com.example.douyinautomation.automation

/**
 * Comment-task startup classification for a visually stable home feed whose accessibility tree
 * does not expose HOME labels or a clickable search node.
 *
 * This is a classification policy only. It never taps search, never issues BACK, and never
 * feeds OCR blocks into [PageDetector]. OCR evidence is accepted only from the top and bottom
 * navigation bands, and the only page it may return is [PageKind.HOME].
 */
object CommentLaunchHomeOcrPolicy {

    /** Screen-ratio ceiling for the top tab strip (直播 / 关注 / 推荐). */
    const val TOP_NAV_MAX_Y = 0.16f

    /** Screen-ratio floor for the bottom tab bar (首页 / 朋友 / 消息 / 我). */
    const val BOTTOM_NAV_MIN_Y = 0.86f

    const val HOME_REASON = "OCR top/bottom home navigation chrome"
    private const val HOME_CONFIDENCE = 0.76f

    fun shouldClassify(
        isCommentTask: Boolean,
        phase: AutomationPhase,
        pageIsUnknown: Boolean,
        isCommentSurface: Boolean,
    ): Boolean = isCommentTask &&
        phase == AutomationPhase.WAITING_FOR_HOME &&
        pageIsUnknown &&
        !isCommentSurface

    fun shouldProbe(
        hasOcrBlocks: Boolean,
        attempts: Int,
        maxAttempts: Int,
    ): Boolean = !hasOcrBlocks && attempts < maxAttempts

    fun classify(context: ScreenContext): PageDetection? {
        val hits = bandHits(context)
        if (hits.top < 1 || hits.bottom < 1) return null
        return PageDetection(
            kind = PageKind.HOME,
            confidence = HOME_CONFIDENCE,
            reasons = listOf(HOME_REASON),
        )
    }

    fun bandHits(context: ScreenContext): BandHits {
        val height = context.screenSize.height
        if (height <= 0) return BandHits(0, 0)
        val topTerms = linkedSetOf<String>()
        val bottomTerms = linkedSetOf<String>()
        for (block in context.ocrBlocks) {
            if (block.bounds.width <= 0 || block.bounds.height <= 0) continue
            val centerY = block.bounds.centerY / height
            val terms = TextNormalizer.matchingTerms(block.text, DouyinLabels.home)
            if (terms.isEmpty()) continue
            when {
                centerY <= TOP_NAV_MAX_Y -> topTerms += terms
                centerY >= BOTTOM_NAV_MIN_Y -> bottomTerms += terms
            }
        }
        return BandHits(top = topTerms.size, bottom = bottomTerms.size)
    }

    data class BandHits(val top: Int, val bottom: Int)
}
