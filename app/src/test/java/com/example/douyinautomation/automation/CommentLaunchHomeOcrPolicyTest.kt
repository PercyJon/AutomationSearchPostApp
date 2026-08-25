package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentLaunchHomeOcrPolicyTest {

    private val screen = ScreenSize(1080, 2412)

    @Test
    fun classifiesOnlyCommentTaskUnknownHomeWithoutSheet() {
        assertTrue(
            CommentLaunchHomeOcrPolicy.shouldClassify(
                isCommentTask = true,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                isCommentSurface = false,
            ),
        )
        assertFalse(
            CommentLaunchHomeOcrPolicy.shouldClassify(
                isCommentTask = false,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                isCommentSurface = false,
            ),
        )
        assertFalse(
            CommentLaunchHomeOcrPolicy.shouldClassify(
                isCommentTask = true,
                phase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
                pageIsUnknown = true,
                isCommentSurface = false,
            ),
        )
        assertFalse(
            CommentLaunchHomeOcrPolicy.shouldClassify(
                isCommentTask = true,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = false,
                isCommentSurface = false,
            ),
        )
        assertFalse(
            CommentLaunchHomeOcrPolicy.shouldClassify(
                isCommentTask = true,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                isCommentSurface = true,
            ),
        )
    }

    @Test
    fun probeRunsOnlyUntilBudgetWithEmptyBlocks() {
        assertTrue(
            CommentLaunchHomeOcrPolicy.shouldProbe(hasOcrBlocks = false, attempts = 0, maxAttempts = 2),
        )
        assertFalse(
            CommentLaunchHomeOcrPolicy.shouldProbe(hasOcrBlocks = true, attempts = 0, maxAttempts = 2),
        )
        assertFalse(
            CommentLaunchHomeOcrPolicy.shouldProbe(hasOcrBlocks = false, attempts = 2, maxAttempts = 2),
        )
    }

    @Test
    fun topAndBottomNavChromeBecomesHome() {
        val detection = CommentLaunchHomeOcrPolicy.classify(visibleHomeNav())

        assertNotNull(detection)
        assertEquals(PageKind.HOME, detection!!.kind)
        assertEquals(listOf(CommentLaunchHomeOcrPolicy.HOME_REASON), detection.reasons)
        assertEquals(BandHits(top = 2, bottom = 1), CommentLaunchHomeOcrPolicy.bandHits(visibleHomeNav()))
    }

    @Test
    fun midScreenCaptionIsNotHome() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("推荐关注", ScreenBounds(80, 1100, 400, 1180)),
                OcrTextBlock("首页装修", ScreenBounds(80, 1200, 400, 1280)),
            ),
        )

        assertNull(CommentLaunchHomeOcrPolicy.classify(context))
        assertEquals(BandHits(0, 0), CommentLaunchHomeOcrPolicy.bandHits(context))
    }

    @Test
    fun topTabsWithoutBottomBarAreNotHome() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("关注", ScreenBounds(520, 120, 640, 180)),
                OcrTextBlock("推荐", ScreenBounds(780, 120, 900, 180)),
            ),
        )

        assertNull(CommentLaunchHomeOcrPolicy.classify(context))
        assertEquals(BandHits(2, 0), CommentLaunchHomeOcrPolicy.bandHits(context))
    }

    @Test
    fun commentSheetChromeIsNotHomeEvenWithTopTabs() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("推荐", ScreenBounds(780, 120, 900, 180)),
                OcrTextBlock("评论 58", ScreenBounds(40, 980, 280, 1050)),
                OcrTextBlock("回复", ScreenBounds(470, 1320, 570, 1380)),
            ),
        )

        assertNull(CommentLaunchHomeOcrPolicy.classify(context))
        assertFalse(
            CommentLaunchHomeOcrPolicy.shouldClassify(
                isCommentTask = true,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                isCommentSurface = true,
            ),
        )
    }

    @Test
    fun classifyNeverReturnsSearchOrProfile() {
        val detection = CommentLaunchHomeOcrPolicy.classify(visibleHomeNav())

        assertEquals(PageKind.HOME, detection?.kind)
    }

    private fun visibleHomeNav(): ScreenContext = ScreenContext(
        screenSize = screen,
        ocrBlocks = listOf(
            OcrTextBlock("关注", ScreenBounds(520, 120, 640, 180)),
            OcrTextBlock("推荐", ScreenBounds(780, 120, 900, 180)),
            OcrTextBlock("首页", ScreenBounds(40, 2280, 200, 2380)),
            OcrTextBlock("3472", ScreenBounds(920, 1100, 1040, 1160)),
        ),
    )

    private fun BandHits(top: Int, bottom: Int) = CommentLaunchHomeOcrPolicy.BandHits(top, bottom)
}
