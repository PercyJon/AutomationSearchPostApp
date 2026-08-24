package com.example.douyinautomation.automation

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextBlockMapperTest {

    @Test
    fun mapsNullBoundsAndPreservesBlockText() {
        val result = OcrResult(
            text = "first",
            blocks = listOf(
                OcrBlock(text = "first", bounds = null, lines = emptyList()),
            ),
        )

        val mapped = OcrTextBlockMapper.map(result)

        assertEquals(ScreenBounds.EMPTY, mapped[0].bounds)
        assertEquals(listOf("first"), mapped.map(OcrTextBlock::text))
    }

    @Test
    fun ordersReversedBoundsWithoutAndroidRuntimeDependencies() {
        val bounds = OcrTextBlockMapper.orderedBounds(80, 90, 20, 30)

        assertEquals(ScreenBounds(20, 30, 80, 90), bounds)
    }

    @Test
    fun `preserves line bounds only when a result-card caller requests them`() {
        val result = OcrResult(
            text = "Demo Designer\\n粉丝：6534\\n抖音号：first_card_01",
            blocks = listOf(
                OcrBlock(
                    text = "Demo Designer\\n粉丝：6534\\n抖音号：first_card_01",
                    bounds = Rect(276, 406, 700, 564),
                    lines = listOf(
                        OcrLine("Demo Designer", Rect(276, 406, 590, 450)),
                        OcrLine("粉丝：6534", Rect(276, 470, 520, 510)),
                        OcrLine("抖音号：first_card_01", Rect(276, 526, 700, 564)),
                    ),
                ),
                OcrBlock(
                    text = "关注",
                    bounds = Rect(816, 458, 972, 522),
                    lines = emptyList(),
                ),
            ),
        )

        val defaultBlocks = OcrTextBlockMapper.map(result)
        val lineBlocks = OcrTextBlockMapper.map(result, preserveLineGeometry = true)

        assertEquals(2, defaultBlocks.size)
        assertEquals(4, lineBlocks.size)
        assertEquals(
            listOf("Demo Designer", "粉丝：6534", "抖音号：first_card_01", "关注"),
            lineBlocks.map(OcrTextBlock::text),
        )
    }
}
