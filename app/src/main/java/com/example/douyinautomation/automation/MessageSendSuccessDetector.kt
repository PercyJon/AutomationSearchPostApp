package com.example.douyinautomation.automation

/**
 * Conservative post-condition for a one-message send.
 *
 * A successful accessibility action alone is not delivery proof. We require the operator's
 * message to appear as a non-editable conversation node or OCR block. Failure text is handled by
 * [PageDetector] before this detector is consulted.
 */
object MessageSendSuccessDetector {
    fun matches(context: ScreenContext, expectedMessage: String): Boolean {
        val expected = TextNormalizer.normalize(expectedMessage).trim()
        if (expected.isEmpty()) return false

        val nodeMatch = context.nodes.asSequence()
            .filter { it.isVisibleToUser && !it.isEditable }
            .filter { it.bounds.height > 0 && it.bounds.bottom >= (context.screenSize.height * 0.25f) }
            .flatMap { it.searchableText().asSequence() }
            .any { TextNormalizer.normalize(it).contains(expected) }
        if (nodeMatch) return true

        return context.ocrBlocks.asSequence()
            .map(OcrTextBlock::text)
            .any { TextNormalizer.normalize(it).contains(expected) }
    }
}
