package com.example.douyinautomation.automation

/**
 * Dynamic exclusion for the app-owned progress window.
 *
 * The overlay may change size, style, or position. Its actual rendered bounds are published by
 * [FloatingOverlayService] so target-app OCR and visual matching can ignore it without encoding
 * any overlay appearance or fixed coordinates.
 */
internal object AppOwnedOverlayExclusion {
    @Volatile
    private var activeBounds: ScreenBounds? = null

    fun update(bounds: ScreenBounds) {
        activeBounds = bounds.takeIf { it.width > 0 && it.height > 0 }
    }

    fun clear() {
        activeBounds = null
    }

    fun excludes(bounds: ScreenBounds): Boolean {
        val overlay = activeBounds ?: return false
        return bounds.left < overlay.right &&
            bounds.right > overlay.left &&
            bounds.top < overlay.bottom &&
            bounds.bottom > overlay.top
    }

    fun filterOcrBlocks(blocks: List<OcrTextBlock>): List<OcrTextBlock> =
        blocks.filterNot { block -> excludes(block.bounds) }
}

/** The overlay remains visible, but must not intercept any active automation touch. */
internal object FloatingOverlayTouchPolicy {
    fun shouldDisableTouches(phase: AutomationPhase): Boolean = phase !in setOf(
        AutomationPhase.IDLE,
        AutomationPhase.SERVICE_READY,
        AutomationPhase.SUSPENDED_BEFORE_START,
        AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
        AutomationPhase.COMPLETED_TASK,
        AutomationPhase.FAILED,
        AutomationPhase.STOPPED,
    )
}
