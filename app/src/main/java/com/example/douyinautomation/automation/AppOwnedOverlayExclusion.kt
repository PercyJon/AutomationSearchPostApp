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

/**
 * Overlay expand / pause / resume / stop must stay tappable during a running task.
 * Automation clicks are kept off the window by published-bounds OCR exclusion, not by
 * FLAG_NOT_TOUCHABLE (which also blocked the operator).
 */
internal object FloatingOverlayTouchPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldDisableTouches(phase: AutomationPhase): Boolean = false
}

internal enum class FloatingOverlayPrimaryAction { PAUSE, RESUME, NONE }

internal object FloatingOverlayControlPolicy {
    fun primaryAction(phase: AutomationPhase): FloatingOverlayPrimaryAction = when (phase) {
        AutomationPhase.SUSPENDED_BEFORE_START,
        AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF,
        -> FloatingOverlayPrimaryAction.RESUME
        AutomationPhase.COMPLETED_TASK,
        AutomationPhase.FAILED,
        AutomationPhase.STOPPED,
        -> FloatingOverlayPrimaryAction.NONE
        else -> FloatingOverlayPrimaryAction.PAUSE
    }

    fun progressLabel(handled: Int, total: Int): String =
        if (total > 0) "$handled/$total" else "$handled"

    fun detailLabel(messaged: Int): String = "已私信 $messaged"

    fun stageLabel(phaseLabel: String, queueLabel: String?): String =
        listOfNotNull(queueLabel, phaseLabel).joinToString(" · ")
}

/**
 * Pause from the overlay is a handoff on Douyin, not a finished run.
 * Opening Records would steal the target app and make Resume/Stop operate on our UI.
 */
internal object TaskRecordsOpenPolicy {
    fun shouldOpenRecordsTab(phase: AutomationPhase): Boolean = when (phase) {
        AutomationPhase.COMPLETED_TASK,
        AutomationPhase.FAILED,
        AutomationPhase.STOPPED,
        -> true
        else -> false
    }
}

/**
 * Overlay resume of a comment task must re-enter [CommentPrivateMessageRuntime], not the
 * B-end profile → private-message click. Pause stops that runtime; the generic resume
 * map would otherwise call openPrivateMessage on USER_PROFILE.
 */
internal object CommentOverlayResumePolicy {
    fun shouldHandoffToCommentRuntime(
        isCommentPrivateMessageTask: Boolean,
        page: PageKind,
    ): Boolean = isCommentPrivateMessageTask && page == PageKind.USER_PROFILE

    /**
     * Douyin video and comment surfaces are often [PageKind.UNKNOWN]. B-end resume still
     * rejects that page, but a frozen comment runtime must continue from its last stage.
     */
    fun resumeDecision(
        commentRuntimeFrozen: Boolean,
        detection: PageDetection,
        pausedPhase: AutomationPhase?,
    ): ResumeDecision {
        val baseline = AutomationResumePolicy.decide(detection)
        if (baseline.allowed || !commentRuntimeFrozen || detection.kind != PageKind.UNKNOWN) {
            return baseline
        }
        val phase = pausedPhase?.takeUnless {
            it == AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF || it == AutomationPhase.STOPPED
        } ?: AutomationPhase.WAITING_FOR_PROFILE
        return ResumeDecision(
            phase = phase,
            allowed = true,
            reason = "Frozen comment runtime may resume on an unclassified Douyin surface",
        )
    }
}
