package com.example.douyinautomation.automation

import kotlin.math.max
import kotlin.math.min

/** Converts OCR engine output into the framework-free screen model used by selectors. */
object OcrTextBlockMapper {

    /**
     * Preserves ML Kit's per-line geometry only for a caller that needs to correlate separate
     * lines inside one visual card. The normal page-classification path intentionally retains
     * whole text blocks, so existing OCR selectors keep their current signal shape.
     */
    fun map(
        result: OcrResult,
        preserveLineGeometry: Boolean = false,
    ): List<OcrTextBlock> = result.blocks.flatMap { block ->
        val linesWithBounds = block.lines.filter { line ->
            line.text.isNotBlank() && line.bounds != null
        }
        if (preserveLineGeometry && linesWithBounds.isNotEmpty()) {
            linesWithBounds.map { line ->
                mappedBlock(text = line.text, bounds = line.bounds)
            }
        } else {
            listOf(mappedBlock(text = block.text, bounds = block.bounds))
        }
    }

    private fun mappedBlock(text: String, bounds: android.graphics.Rect?): OcrTextBlock =
        OcrTextBlock(
            text = text,
            bounds = if (bounds == null) {
                ScreenBounds.EMPTY
            } else {
                orderedBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
            },
        )

    fun orderedBounds(left: Int, top: Int, right: Int, bottom: Int): ScreenBounds = ScreenBounds(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom),
    )
}
