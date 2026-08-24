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

/**
 * Reserved-and-disabled implementation: no image matching is attempted and no screenshot data is
 * retained. It is the only implementation that may be wired into production until the separate
 * multi-device, dp-normalized template-matching rollout described in M8-E1 is approved.
 */
object NoOpImageMatcher : ImageMatcher {
    override suspend fun findMatch(
        bitmap: Bitmap,
        templateId: String,
        searchRegion: Rect?,
    ): ImageMatch? = null
}

/**
 * Safety gate for a future OpenCV-backed matcher. A template match may only become actionable
 * after the provider reports a high-confidence result inside the caller's region; a missing
 * template never falls back to an arbitrary coordinate. The M3-K controller still requires a
 * semantic node for critical actions, so this adapter is diagnostic/verification-only for now.
 * It is not wired into any automation action in the current build.
 */
class VerifiedTemplateMatcher(
    private val provider: ImageMatcher,
    private val minimumConfidence: Float = 0.86f,
) : ImageMatcher {
    init {
        require(minimumConfidence in 0f..1f) { "minimumConfidence must be between 0 and 1" }
    }

    override suspend fun findMatch(
        bitmap: Bitmap,
        templateId: String,
        searchRegion: Rect?,
    ): ImageMatch? = provider.findMatch(bitmap, templateId, searchRegion)
        ?.takeIf { it.confidence >= minimumConfidence }
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
