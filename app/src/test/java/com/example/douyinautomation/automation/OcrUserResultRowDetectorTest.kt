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

    @Test
    fun `accepts a truncated follow pill rendered as a single guan glyph`() {
        // Real device observation: the first result card's right-side follow pill is rendered
        // small enough that OCR reports only the "关" glyph at 42px wide, below the historical
        // 48px minimum and missing the full "关注" label.
        val context = userResultsContext(
            block("Designer", 276, 430, 590, 479),
            block("粉丝：2.0万", 276, 492, 490, 536),
            block("抖音号：donganchuang", 276, 550, 696, 596),
            block("关", 848, 483, 890, 528),
        )

        val analysis = OcrUserResultRowDetector.analyzeFirstVisible(context)
        val match = analysis.match

        assertEquals(1, analysis.accountMarkerCount)
        assertNotNull(match)
        assertEquals(
            StructuralUserRowMatch.Source.OCR_ASSISTED_STABLE,
            match!!.asStructuralMatch().source,
        )
        val identity = UserResultIdentityExtractor.extract(context, match.asStructuralMatch())
        assertEquals("donganchuang", identity?.accountHandle)
    }

    @Test
    fun `accepts a wide follow-column block that embeds a follow glyph`() {
        // ML Kit may report the right-side follow pill merged with its padding as a single wider
        // block. The strict width cap rejects it, so the detector must fall back to a broader
        // right-column block that still carries a follow signal inside the card's vertical band.
        val context = userResultsContext(
            block("Designer", 276, 430, 590, 479),
            block("粉丝：2.0万", 276, 492, 490, 536),
            block("抖音号：donganchuang", 276, 550, 696, 596),
            block("关注", 830, 478, 980, 533),
        )

        val analysis = OcrUserResultRowDetector.analyzeFirstVisible(context)
        val match = analysis.match

        assertEquals(1, analysis.accountMarkerCount)
        assertNotNull(match)
        assertEquals(
            StructuralUserRowMatch.Source.OCR_ASSISTED_STABLE,
            match!!.asStructuralMatch().source,
        )
        val identity = UserResultIdentityExtractor.extract(context, match.asStructuralMatch())
        assertEquals("donganchuang", identity?.accountHandle)
    }

    private fun userResultsContext(vararg blocks: OcrTextBlock): ScreenContext = ScreenContext(
        screenSize = ScreenSize(1080, 2412),
        packageName = "com.ss.android.ugc.aweme",
        ocrBlocks = blocks.toList(),
    )

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int): OcrTextBlock =
        OcrTextBlock(text = text, bounds = ScreenBounds(left, top, right, bottom))
}
