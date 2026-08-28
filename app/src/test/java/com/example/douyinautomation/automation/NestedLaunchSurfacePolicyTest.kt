package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedLaunchSurfacePolicyTest {

    private val screen = ScreenSize(1080, 2412)

    @Test
    fun waitingForHomeRecoversOnlyWhenCommentSheetIsConfirmed() {
        assertTrue(
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = AutomationPhase.WAITING_FOR_HOME,
                isCommentSurface = true,
            ),
        )
        assertFalse(
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = AutomationPhase.WAITING_FOR_HOME,
                isCommentSurface = false,
            ),
        )
        assertFalse(
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
                isCommentSurface = true,
            ),
        )
    }

    @Test
    fun searchEntryWaitRecoversGroupChatOverlay() {
        assertTrue(
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
                isCommentSurface = false,
                isGroupChatOverlay = true,
            ),
        )
        assertTrue(
            NestedLaunchSurfacePolicy.shouldSuppressHomeSearchAction(
                isCommentSurface = false,
                isGroupChatOverlay = true,
            ),
        )
        assertFalse(
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = AutomationPhase.WAITING_FOR_USER_RESULTS,
                isCommentSurface = false,
                isGroupChatOverlay = true,
            ),
        )
    }

    @Test
    fun openCommentSheetSuppressesHomeSearchAction() {
        assertTrue(NestedLaunchSurfacePolicy.shouldSuppressHomeSearchAction(isCommentSurface = true))
        assertFalse(NestedLaunchSurfacePolicy.shouldSuppressHomeSearchAction(isCommentSurface = false))
    }

    @Test
    fun leftoverCountHeaderAndComposerIsNestedLaunchSurface() {
        val detection = CommentSurfaceDetector.detect(leftoverCommentSheet())

        assertTrue(detection.isCommentSurface)
        assertTrue(
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = AutomationPhase.WAITING_FOR_HOME,
                isCommentSurface = detection.isCommentSurface,
            ),
        )
        assertTrue(
            NestedLaunchSurfacePolicy.shouldSuppressHomeSearchAction(detection.isCommentSurface),
        )
    }

    @Test
    fun homeFeedCommentLabelIsNotNestedLaunchSurface() {
        val detection = CommentSurfaceDetector.detect(
            ScreenContext(
                screenSize = screen,
                nodes = listOf(
                    NodeSnapshot(text = "首页", bounds = ScreenBounds(40, 2280, 200, 2380)),
                    NodeSnapshot(text = "评论", bounds = ScreenBounds(900, 1260, 1040, 1320)),
                    NodeSnapshot(
                        className = "android.widget.ImageView",
                        bounds = ScreenBounds(960, 80, 1048, 168),
                        isClickable = true,
                    ),
                ),
            ),
        )

        assertFalse(detection.isCommentSurface)
        assertFalse(
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = AutomationPhase.WAITING_FOR_HOME,
                isCommentSurface = detection.isCommentSurface,
            ),
        )
    }

    @Test
    fun truncatedTreeOcrSheetChromeIsNestedLaunchSurface() {
        val detection = CommentSurfaceDetector.detect(ocrOnlyFeedCommentSheet())

        assertTrue(detection.isCommentSurface)
        assertTrue(
            NestedLaunchSurfacePolicy.shouldRecoverByBoundedBack(
                phase = AutomationPhase.WAITING_FOR_HOME,
                isCommentSurface = detection.isCommentSurface,
            ),
        )
        assertTrue(
            NestedLaunchSurfacePolicy.shouldForceInitialBack(
                ocrConfirmedSheet = detection.isCommentSurface,
                nodeDetectedSheet = false,
            ),
        )
    }

    @Test
    fun commentSurfaceOcrProbeRunsOnlyForUnknownHomeWithoutNodeSheet() {
        assertTrue(
            NestedLaunchSurfacePolicy.shouldProbeCommentSurfaceOcr(
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                nodeDetectedSheet = false,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 2,
            ),
        )
        assertFalse(
            NestedLaunchSurfacePolicy.shouldProbeCommentSurfaceOcr(
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                nodeDetectedSheet = true,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 2,
            ),
        )
        assertFalse(
            NestedLaunchSurfacePolicy.shouldProbeCommentSurfaceOcr(
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = false,
                nodeDetectedSheet = false,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 2,
            ),
        )
        assertFalse(
            NestedLaunchSurfacePolicy.shouldProbeCommentSurfaceOcr(
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                nodeDetectedSheet = false,
                hasOcrBlocks = true,
                attempts = 0,
                maxAttempts = 2,
            ),
        )
        assertFalse(
            NestedLaunchSurfacePolicy.shouldProbeCommentSurfaceOcr(
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                nodeDetectedSheet = false,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 2,
                isCommentTask = false,
            ),
        )
    }

    @Test
    fun homeFeedOcrCommentLabelIsNotNestedLaunchSurface() {
        val detection = CommentSurfaceDetector.detect(
            ScreenContext(
                screenSize = screen,
                ocrBlocks = listOf(
                    OcrTextBlock("评论", ScreenBounds(920, 1260, 1040, 1320)),
                    OcrTextBlock("1772", ScreenBounds(920, 1100, 1040, 1160)),
                ),
            ),
        )

        assertFalse(detection.isCommentSurface)
        assertFalse(
            NestedLaunchSurfacePolicy.shouldProbeCommentSurfaceOcr(
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                nodeDetectedSheet = detection.isCommentSurface,
                hasOcrBlocks = true,
                attempts = 0,
                maxAttempts = 2,
            ),
        )
    }

    private fun leftoverCommentSheet(): ScreenContext = ScreenContext(
        screenSize = screen,
        nodes = listOf(
            NodeSnapshot(text = "447条评论", bounds = ScreenBounds(40, 980, 280, 1050)),
            NodeSnapshot(
                text = "期待你的评论",
                className = "android.widget.EditText",
                bounds = ScreenBounds(42, 2268, 678, 2388),
                isEditable = true,
            ),
            NodeSnapshot(text = "回复", bounds = ScreenBounds(470, 1320, 570, 1380)),
            NodeSnapshot(text = "回复", bounds = ScreenBounds(470, 1710, 570, 1770)),
            NodeSnapshot(
                className = "android.widget.ImageView",
                bounds = ScreenBounds(960, 80, 1048, 168),
                isClickable = true,
            ),
        ),
    )

    /** 17:13 feed comment sheet: truncated nodes, OCR still sees tab chrome and reply rows. */
    private fun ocrOnlyFeedCommentSheet(): ScreenContext = ScreenContext(
        screenSize = screen,
        ocrBlocks = listOf(
            OcrTextBlock("评论 58", ScreenBounds(40, 980, 280, 1050)),
            OcrTextBlock("AI解析", ScreenBounds(700, 980, 900, 1050)),
            OcrTextBlock("回复", ScreenBounds(470, 1320, 570, 1380)),
            OcrTextBlock("回复", ScreenBounds(470, 1710, 570, 1770)),
        ),
    )
}
