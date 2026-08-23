package com.example.douyinautomation.automation

import kotlin.math.max
import kotlin.math.min

/** Converts OCR engine output into the framework-free screen model used by selectors. */
object OcrTextBlockMapper {

    fun map(result: OcrResult): List<OcrTextBlock> = result.blocks.map { block ->
        val bounds = block.bounds
        OcrTextBlock(
            text = block.text,
            bounds = if (bounds == null) {
                ScreenBounds.EMPTY
            } else {
                orderedBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
            },
        )
    }

    fun orderedBounds(left: Int, top: Int, right: Int, bottom: Int): ScreenBounds = ScreenBounds(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom),
    )
}
