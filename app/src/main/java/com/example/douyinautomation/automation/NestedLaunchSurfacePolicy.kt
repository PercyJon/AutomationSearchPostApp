package com.example.douyinautomation.automation

/**
 * Startup recovery for a leftover nested Douyin surface: the comment sheet, or the
 * group/private-message sheet opened from a home IM heads-up.
 *
 * This is a classification policy only. It never issues BACK, taps search, or widens
 * [PageDetector]. The controller still owns the existing bounded BACK budget and the
 * HOME / SEARCH_ENTRY / SEARCH_RESULTS action order after the sheet has closed.
 *
 * A right-rail video on the home feed is intentionally not nested evidence: BACK from
 * that surface can leave Douyin. Only comment-sheet or group-chat-sheet chrome is accepted.
 */
object NestedLaunchSurfacePolicy {

    /**
     * During [AutomationPhase.WAITING_FOR_HOME], a verified comment sheet is not an
     * unclassified home. The existing bounded BACK recovery may run instead of waiting
     * out the startup watchdog.
     */
    fun shouldRecoverByBoundedBack(
        phase: AutomationPhase,
        isCommentSurface: Boolean,
        isGroupChatOverlay: Boolean = false,
    ): Boolean {
        val nested = isCommentSurface || isGroupChatOverlay
        if (!nested) return false
        if (phase == AutomationPhase.WAITING_FOR_HOME) return true
        // The IM heads-up opens a group sheet on top of HOME. Search-entry wait must BACK
        // instead of treating the dimmed feed as a missing search field.
        return isGroupChatOverlay && phase == AutomationPhase.WAITING_FOR_SEARCH_ENTRY
    }

    /**
     * Search icons can remain visible above an open comment sheet or group-chat sheet.
     * Tapping them would act through the overlay. Keep using BACK until the sheet is gone,
     * then follow the existing HOME / search-entry routing (including a still-visible
     * search field).
     */
    fun shouldSuppressHomeSearchAction(
        isCommentSurface: Boolean,
        isGroupChatOverlay: Boolean = false,
    ): Boolean = isCommentSurface || isGroupChatOverlay

    /**
     * Node-first comment-sheet detection can miss a visually open panel when the accessibility
     * tree is depth-truncated. OCR may then run only to feed [CommentSurfaceDetector]; the
     * blocks must not be reused as PageDetector HOME/search evidence.
     */
    fun shouldProbeCommentSurfaceOcr(
        phase: AutomationPhase,
        pageIsUnknown: Boolean,
        nodeDetectedSheet: Boolean,
        hasOcrBlocks: Boolean,
        attempts: Int,
        maxAttempts: Int,
        isCommentTask: Boolean = true,
    ): Boolean = isCommentTask &&
        phase == AutomationPhase.WAITING_FOR_HOME &&
        pageIsUnknown &&
        !nodeDetectedSheet &&
        !hasOcrBlocks &&
        attempts < maxAttempts

    /**
     * After OCR (not the node tree) confirmed the sheet, the first recovery step must BACK.
     * A truncated tree can still expose the search icon above the sheet.
     */
    fun shouldForceInitialBack(ocrConfirmedSheet: Boolean, nodeDetectedSheet: Boolean): Boolean =
        ocrConfirmedSheet && !nodeDetectedSheet
}
