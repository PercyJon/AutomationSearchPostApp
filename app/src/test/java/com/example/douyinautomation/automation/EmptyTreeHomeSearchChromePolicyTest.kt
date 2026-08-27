package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyTreeHomeSearchChromePolicyTest {

    private val screen = ScreenSize(1080, 2412)

    @Test
    fun probesOnlyBEndUnknownHomeWithoutSearchNode() {
        assertTrue(
            EmptyTreeHomeSearchChromePolicy.shouldProbe(
                isCommentTask = false,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                hasSearchEntryCandidate = false,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 3,
            ),
        )
        assertFalse(
            EmptyTreeHomeSearchChromePolicy.shouldProbe(
                isCommentTask = true,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                hasSearchEntryCandidate = false,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 3,
            ),
        )
        assertFalse(
            EmptyTreeHomeSearchChromePolicy.shouldProbe(
                isCommentTask = false,
                phase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
                pageIsUnknown = true,
                hasSearchEntryCandidate = false,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 3,
            ),
        )
        assertFalse(
            EmptyTreeHomeSearchChromePolicy.shouldProbe(
                isCommentTask = false,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = false,
                hasSearchEntryCandidate = false,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 3,
            ),
        )
        assertFalse(
            EmptyTreeHomeSearchChromePolicy.shouldProbe(
                isCommentTask = false,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                hasSearchEntryCandidate = true,
                hasOcrBlocks = false,
                attempts = 0,
                maxAttempts = 3,
            ),
        )
        assertFalse(
            EmptyTreeHomeSearchChromePolicy.shouldProbe(
                isCommentTask = false,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                hasSearchEntryCandidate = false,
                hasOcrBlocks = true,
                attempts = 0,
                maxAttempts = 3,
            ),
        )
        assertFalse(
            EmptyTreeHomeSearchChromePolicy.shouldProbe(
                isCommentTask = false,
                phase = AutomationPhase.WAITING_FOR_HOME,
                pageIsUnknown = true,
                hasSearchEntryCandidate = false,
                hasOcrBlocks = false,
                attempts = 3,
                maxAttempts = 3,
            ),
        )
    }

    @Test
    fun recommendTabInChromeBandBecomesHome() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("推荐", ScreenBounds(780, 120, 900, 180)),
            ),
        )

        val detection = EmptyTreeHomeSearchChromePolicy.classify(context)

        assertNotNull(detection)
        assertEquals(PageKind.HOME, detection!!.kind)
        assertEquals(listOf(EmptyTreeHomeSearchChromePolicy.HOME_REASON), detection.reasons)
        assertEquals(1, EmptyTreeHomeSearchChromePolicy.chromeHits(context))
    }

    @Test
    fun searchLabelInChromeBandBecomesHome() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("搜索", ScreenBounds(960, 80, 1060, 150)),
            ),
        )

        val detection = EmptyTreeHomeSearchChromePolicy.classify(context)

        assertEquals(PageKind.HOME, detection?.kind)
    }

    @Test
    fun midScreenCaptionIsNotHome() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("推荐关注", ScreenBounds(80, 1100, 400, 1180)),
            ),
        )

        assertNull(EmptyTreeHomeSearchChromePolicy.classify(context))
        assertEquals(0, EmptyTreeHomeSearchChromePolicy.chromeHits(context))
    }

    @Test
    fun leftTopTabOutsideChromeBandIsNotHome() {
        val context = ScreenContext(
            screenSize = screen,
            ocrBlocks = listOf(
                OcrTextBlock("直播", ScreenBounds(40, 120, 160, 180)),
            ),
        )

        assertNull(EmptyTreeHomeSearchChromePolicy.classify(context))
    }

    @Test
    fun chromeBandAlignsWithExistingSearchFallback() {
        assertEquals(0.20f, DouyinSelectors.searchEntry.preferredRegion?.bottom)
        assertEquals(0.92f, DouyinSelectors.searchEntryNormalizedFallback.x)
        assertEquals(0.07f, DouyinSelectors.searchEntryNormalizedFallback.y)
        assertEquals(DouyinSelectors.searchEntry.preferredRegion?.bottom, EmptyTreeHomeSearchChromePolicy.BOTTOM)
    }

    @Test
    fun classifyNeverReturnsSearchOrProfile() {
        val detection = EmptyTreeHomeSearchChromePolicy.classify(
            ScreenContext(
                screenSize = screen,
                ocrBlocks = listOf(
                    OcrTextBlock("关注", ScreenBounds(520, 120, 640, 180)),
                    OcrTextBlock("推荐", ScreenBounds(780, 120, 900, 180)),
                ),
            ),
        )

        assertEquals(PageKind.HOME, detection?.kind)
    }
}
