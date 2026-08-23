package com.example.douyinautomation.automation

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
}
