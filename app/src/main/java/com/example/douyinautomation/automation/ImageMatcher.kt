package com.example.douyinautomation.automation

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Extension seam for a future on-device OpenCV/template matching implementation.
 *
 * It is intentionally a no-op in M0. It must not be used to solve CAPTCHAs, defeat platform
 * safeguards, or continue a task after a risk/manual-handoff screen has been detected.
 */
interface ImageMatcher {
    suspend fun findMatch(
        bitmap: Bitmap,
        templateId: String,
        searchRegion: Rect? = null,
    ): ImageMatch?
}

/** M0 implementation: no image matching is attempted and no screenshot data is retained. */
object NoOpImageMatcher : ImageMatcher {
    override suspend fun findMatch(
        bitmap: Bitmap,
        templateId: String,
        searchRegion: Rect?,
    ): ImageMatch? = null
}

data class ImageMatch(
    val templateId: String,
    val bounds: Rect,
    val confidence: Float,
) {
    init {
        require(confidence in 0f..1f) { "confidence must be between 0 and 1" }
    }
}
