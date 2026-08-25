package com.example.douyinautomation.automation

/**
 * Startup recovery for a leftover nested Douyin surface, currently the comment sheet.
 *
 * This is a classification policy only. It never issues BACK, taps search, or widens
 * [PageDetector]. The controller still owns the existing bounded BACK budget and the
 * HOME / SEARCH_ENTRY / SEARCH_RESULTS action order after the sheet has closed.
 *
 * A right-rail video on the home feed is intentionally not nested evidence: BACK from
 * that surface can leave Douyin. Only [CommentSurfaceDetector] sheet chrome is accepted.
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
    ): Boolean = phase == AutomationPhase.WAITING_FOR_HOME && isCommentSurface

    /**
     * Search icons can remain visible above an open comment sheet. Tapping them would
     * act through the overlay. Keep using BACK until the sheet is gone, then follow
     * the existing HOME / search-entry routing (including a still-visible search field).
     */
    fun shouldSuppressHomeSearchAction(isCommentSurface: Boolean): Boolean = isCommentSurface

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
    ): Boolean = phase == AutomationPhase.WAITING_FOR_HOME &&
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
