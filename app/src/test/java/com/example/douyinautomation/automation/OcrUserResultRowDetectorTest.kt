package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrUserResultRowDetectorTest {
    @Test
    fun `accepts a first user card only when all four OCR structure signals align`() {
        val context = userResultsContext(
            block("Demo Designer", 276, 406, 590, 450),
            block("粉丝：6534", 276, 470, 520, 510),
            block("抖音号：p0_first_01", 276, 526, 700, 564),
            block("关注", 816, 458, 972, 522),
        )

        val analysis = OcrUserResultRowDetector.analyzeFirstVisible(context)
        val match = analysis.match

        assertEquals(1, analysis.accountMarkerCount)
        assertNotNull(match)
        assertTrue(match!!.rowBounds.top < 406)
        assertTrue(match.rowBounds.bottom > 564)
        assertEquals(
            StructuralUserRowMatch.Source.OCR_ASSISTED_STABLE,
            match.asStructuralMatch().source,
        )
        val identity = UserResultIdentityExtractor.extract(context, match.asStructuralMatch())
        assertEquals("p0_first_01", identity?.accountHandle)
    }

    @Test
    fun `does not skip an incomplete first card to use a later OCR card`() {
        val context = userResultsContext(
            block("First user", 276, 406, 590, 450),
            block("粉丝：10", 276, 470, 520, 510),
            block("抖音号：first_missing_follow", 276, 526, 700, 564),
            // A complete second card must not become a substitute for P0's first result.
            block("Second user", 276, 650, 590, 694),
            block("粉丝：20", 276, 714, 520, 754),
            block("抖音号：second_valid", 276, 770, 700, 808),
            block("关注", 816, 702, 972, 766),
        )

        val analysis = OcrUserResultRowDetector.analyzeFirstVisible(context)

        assertEquals(2, analysis.accountMarkerCount)
        assertNull(analysis.match)
    }

    @Test
    fun `requires the repeated OCR sample to preserve the same first account and geometry`() {
        val first = OcrUserResultRowDetector.analyzeFirstVisible(
            userResultsContext(
                block("Demo Designer", 276, 406, 590, 450),
                block("粉丝：6534", 276, 470, 520, 510),
                block("抖音号：p0_first_01", 276, 526, 700, 564),
                block("关注", 816, 458, 972, 522),
            ),
        ).match
        val stable = OcrUserResultRowDetector.analyzeFirstVisible(
            userResultsContext(
                block("Demo Designer", 276, 410, 590, 454),
                block("粉丝：6534", 276, 474, 520, 514),
                block("抖音号：p0_first_01", 276, 530, 700, 568),
                block("关注", 816, 462, 972, 526),
            ),
        ).match
        val changedAccount = OcrUserResultRowDetector.analyzeFirstVisible(
            userResultsContext(
                block("Demo Designer", 276, 410, 590, 454),
                block("粉丝：6534", 276, 474, 520, 514),
                block("抖音号：other_user_02", 276, 530, 700, 568),
                block("关注", 816, 462, 972, 526),
            ),
        ).match

        assertNotNull(first)
        assertNotNull(stable)
        assertNotNull(changedAccount)
        assertTrue(first!!.agreesWith(stable!!))
        assertFalse(first.agreesWith(changedAccount!!))
    }

    private fun userResultsContext(vararg blocks: OcrTextBlock): ScreenContext = ScreenContext(
        screenSize = ScreenSize(1080, 2412),
        packageName = "com.ss.android.ugc.aweme",
        ocrBlocks = blocks.toList(),
    )

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int): OcrTextBlock =
        OcrTextBlock(text = text, bounds = ScreenBounds(left, top, right, bottom))
}
