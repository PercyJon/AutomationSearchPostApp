package com.example.douyinautomation.automation

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extension seam for reviewed on-device visual evidence providers.
 *
 * Providers must not solve CAPTCHAs, defeat platform safeguards, or continue a task after a
 * risk/manual-handoff screen has been detected. The default remains [NoOpImageMatcher]; the
 * comment-bubble implementation is deliberately limited to its P0 caller.
 */
interface ImageMatcher {
    suspend fun findMatch(
        bitmap: Bitmap,
        templateId: String,
        searchRegion: Rect? = null,
    ): ImageMatch?
}

/** Safe default for every action that does not opt into a separately reviewed visual fallback. */
object NoOpImageMatcher : ImageMatcher {
    override suspend fun findMatch(
        bitmap: Bitmap,
        templateId: String,
        searchRegion: Rect?,
    ): ImageMatch? = null
}

/**
 * A deliberately narrow matcher for the supplied Douyin comment-bubble asset.
 *
 * The asset has an alpha mask: the three dark points are transparent holes rather than stable
 * black pixels. Raw RGB matching would therefore vary with the playing video's background. This
 * matcher instead verifies that a bright bubble-shaped foreground contains three relatively dark
 * holes at the template's normalized positions. It requires a caller-supplied bounded region and
 * searches candidate sizes as fractions of the current screenshot width; it never owns a tap.
 */
class AlphaMaskedCommentIconMatcher(
    private val assets: AssetManager,
) : ImageMatcher {
    private val signature: AlphaTemplateSignature? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            assets.open(TEMPLATE_ASSET).use { stream ->
                BitmapFactory.decodeStream(stream)?.let { template ->
                    try {
                        AlphaTemplateSignature.from(template)
                    } finally {
                        template.recycle()
                    }
                }
            }
        }.getOrNull()
    }

    override suspend fun findMatch(
        bitmap: Bitmap,
        templateId: String,
        searchRegion: Rect?,
    ): ImageMatch? {
        if (templateId != TEMPLATE_ID || bitmap.isRecycled || searchRegion == null) return null
        return withContext(Dispatchers.Default) {
            val template = signature ?: return@withContext null
            val region = searchRegion.clampedTo(bitmap.width, bitmap.height) ?: return@withContext null
            findBestMatch(
                frame = LuminanceFrame(bitmap),
                template = template,
                region = region,
            )
        }
    }

    private fun findBestMatch(
        frame: LuminanceFrame,
        template: AlphaTemplateSignature,
        region: Rect,
    ): ImageMatch? {
        val candidates = mutableListOf<ImageMatch>()
        for (widthFraction in CANDIDATE_WIDTH_FRACTIONS) {
            val candidateWidth = (frame.width * widthFraction).roundToInt().coerceAtLeast(1)
            val candidateHeight = (candidateWidth / template.aspectRatio).roundToInt().coerceAtLeast(1)
            val maximumLeft = region.right - candidateWidth
            val maximumTop = region.bottom - candidateHeight
            if (maximumLeft < region.left || maximumTop < region.top) continue

            // This is a sampling cadence derived from the candidate itself, not a screen-pixel
            // threshold. A true icon is evaluated at several neighboring positions/scales.
            val horizontalStep = (candidateWidth * SEARCH_STEP_FRACTION).roundToInt().coerceAtLeast(1)
            val verticalStep = (candidateHeight * SEARCH_STEP_FRACTION).roundToInt().coerceAtLeast(1)
            var top = region.top
            while (top <= maximumTop) {
                var left = region.left
                while (left <= maximumLeft) {
                    val confidence = candidateConfidence(
                        frame = frame,
                        template = template,
                        left = left,
                        top = top,
                        width = candidateWidth,
                        height = candidateHeight,
                    )
                    if (confidence >= RETAINED_CANDIDATE_CONFIDENCE) {
                        candidates += ImageMatch(
                            templateId = TEMPLATE_ID,
                            bounds = Rect(left, top, left + candidateWidth, top + candidateHeight),
                            confidence = confidence,
                        )
                    }
                    left += horizontalStep
                }
                top += verticalStep
            }
        }

        val best = candidates.maxByOrNull(ImageMatch::confidence) ?: return null
        val distinctCompetitor = candidates.asSequence()
            .filterNot { candidate -> isSameVisualCandidate(best, candidate, frame) }
            .maxByOrNull(ImageMatch::confidence)
        if (distinctCompetitor != null &&
            best.confidence - distinctCompetitor.confidence < MIN_DISTINCT_CONFIDENCE_MARGIN
        ) {
            return null
        }
        return best
    }

    private fun candidateConfidence(
        frame: LuminanceFrame,
        template: AlphaTemplateSignature,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): Float {
        var foregroundTotal = 0f
        template.foregroundSamples.forEach { point ->
            foregroundTotal += frame.sample(left, top, width, height, point)
        }
        val foregroundLuminance = foregroundTotal / template.foregroundSamples.size
        if (foregroundLuminance < MIN_FOREGROUND_LUMINANCE) return 0f

        var minimumDotContrast = Float.MAX_VALUE
        var totalDotContrast = 0f
        template.dotSamples.forEach { dot ->
            var dotTotal = 0f
            dot.forEach { point ->
                dotTotal += frame.sample(left, top, width, height, point)
            }
            val contrast = foregroundLuminance - dotTotal / dot.size
            minimumDotContrast = minOf(minimumDotContrast, contrast)
            totalDotContrast += contrast
        }
        if (minimumDotContrast < MIN_DOT_CONTRAST) return 0f
        val averageDotContrast = totalDotContrast / template.dotSamples.size

        // Contrast is relative to the same candidate's bubble, so alpha blending with a video
        // background does not require the source asset to be a fixed white RGB value.
        val minimumDotScore = normalize(
            value = minimumDotContrast,
            lower = MIN_DOT_CONTRAST,
            upper = FULL_DOT_CONTRAST,
        )
        val averageDotScore = normalize(
            value = averageDotContrast,
            lower = MIN_DOT_CONTRAST,
            upper = FULL_DOT_CONTRAST,
        )
        val foregroundScore = normalize(
            value = foregroundLuminance,
            lower = MIN_FOREGROUND_LUMINANCE,
            upper = FULL_FOREGROUND_LUMINANCE,
        )
        return (
            minimumDotScore * MINIMUM_DOT_WEIGHT +
                averageDotScore * AVERAGE_DOT_WEIGHT +
                foregroundScore * FOREGROUND_WEIGHT
            ).coerceIn(0f, 1f)
    }

    private fun isSameVisualCandidate(
        first: ImageMatch,
        second: ImageMatch,
        frame: LuminanceFrame,
    ): Boolean {
        val firstCenterX = (first.bounds.left + first.bounds.right) / (2f * frame.width)
        val firstCenterY = (first.bounds.top + first.bounds.bottom) / (2f * frame.height)
        val secondCenterX = (second.bounds.left + second.bounds.right) / (2f * frame.width)
        val secondCenterY = (second.bounds.top + second.bounds.bottom) / (2f * frame.height)
        val sharedWidth = max(first.bounds.width(), second.bounds.width()).toFloat() / frame.width
        val sharedHeight = max(first.bounds.height(), second.bounds.height()).toFloat() / frame.height
        return abs(firstCenterX - secondCenterX) <= sharedWidth * SAME_ICON_CENTER_TOLERANCE_FRACTION &&
            abs(firstCenterY - secondCenterY) <= sharedHeight * SAME_ICON_CENTER_TOLERANCE_FRACTION
    }

    private fun normalize(value: Float, lower: Float, upper: Float): Float =
        ((value - lower) / (upper - lower)).coerceIn(0f, 1f)

    private data class RelativeSample(
        val x: Float,
        val y: Float,
    )

    private data class AlphaTemplateSignature(
        val aspectRatio: Float,
        val foregroundSamples: List<RelativeSample>,
        val dotSamples: List<List<RelativeSample>>,
    ) {
        companion object {
            fun from(bitmap: Bitmap): AlphaTemplateSignature? {
                if (bitmap.width <= 1 || bitmap.height <= 1) return null
                val width = bitmap.width
                val height = bitmap.height
                val pixelCount = width * height
                val pixels = IntArray(pixelCount)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val alpha = IntArray(pixelCount) { index -> pixels[index] ushr 24 }
                val foregroundSamples = foregroundSamples(alpha, width, height)
                val dots = transparentInteriorComponents(alpha, width, height)
                    .sortedBy { component -> component.map { it % width }.average() }
                    .map { component -> componentSamples(component, width, height) }
                return AlphaTemplateSignature(
                    aspectRatio = width.toFloat() / height,
                    foregroundSamples = foregroundSamples,
                    dotSamples = dots,
                ).takeIf { signature ->
                    signature.foregroundSamples.isNotEmpty() && signature.dotSamples.size == EXPECTED_DOT_COUNT
                }
            }

            private fun foregroundSamples(
                alpha: IntArray,
                width: Int,
                height: Int,
            ): List<RelativeSample> {
                val stride = samplingStride(width * height, TARGET_FOREGROUND_SAMPLE_COUNT)
                return buildList {
                    var y = 0
                    while (y < height) {
                        var x = 0
                        while (x < width) {
                            if (alpha[y * width + x] >= OPAQUE_ALPHA_THRESHOLD) {
                                add(relativeSample(x, y, width, height))
                            }
                            x += stride
                        }
                        y += stride
                    }
                }
            }

            private fun transparentInteriorComponents(
                alpha: IntArray,
                width: Int,
                height: Int,
            ): List<List<Int>> {
                val visited = BooleanArray(alpha.size)
                val components = mutableListOf<List<Int>>()
                alpha.indices.forEach { start ->
                    if (visited[start] || alpha[start] >= TRANSPARENT_ALPHA_THRESHOLD) return@forEach
                    val queue = IntArray(alpha.size)
                    var read = 0
                    var write = 0
                    queue[write++] = start
                    visited[start] = true
                    val component = mutableListOf<Int>()
                    var touchesEdge = false
                    while (read < write) {
                        val current = queue[read++]
                        component += current
                        val x = current % width
                        val y = current / width
                        touchesEdge = touchesEdge || x == 0 || y == 0 || x == width - 1 || y == height - 1
                        visitTransparentNeighbor(current - 1, x > 0, alpha, visited, queue, write).also { next ->
                            write = next
                        }
                        visitTransparentNeighbor(current + 1, x < width - 1, alpha, visited, queue, write).also { next ->
                            write = next
                        }
                        visitTransparentNeighbor(current - width, y > 0, alpha, visited, queue, write).also { next ->
                            write = next
                        }
                        visitTransparentNeighbor(current + width, y < height - 1, alpha, visited, queue, write).also { next ->
                            write = next
                        }
                    }
                    val areaFraction = component.size.toFloat() / alpha.size
                    if (!touchesEdge && areaFraction in MIN_DOT_AREA_FRACTION..MAX_DOT_AREA_FRACTION) {
                        components += component
                    }
                }
                return components
            }

            private fun visitTransparentNeighbor(
                index: Int,
                isInsideTemplate: Boolean,
                alpha: IntArray,
                visited: BooleanArray,
                queue: IntArray,
                write: Int,
            ): Int {
                if (!isInsideTemplate || visited[index] || alpha[index] >= TRANSPARENT_ALPHA_THRESHOLD) {
                    return write
                }
                visited[index] = true
                queue[write] = index
                return write + 1
            }

            private fun componentSamples(
                component: List<Int>,
                width: Int,
                height: Int,
            ): List<RelativeSample> {
                val stride = samplingStride(component.size, TARGET_DOT_SAMPLE_COUNT)
                return component.filterIndexed { index, _ -> index % stride == 0 }
                    .map { index -> relativeSample(index % width, index / width, width, height) }
            }

            private fun relativeSample(x: Int, y: Int, width: Int, height: Int): RelativeSample =
                RelativeSample(
                    x = x.toFloat() / (width - 1),
                    y = y.toFloat() / (height - 1),
                )

            private fun samplingStride(size: Int, targetSamples: Int): Int =
                ceil(sqrt(size.toDouble() / targetSamples)).toInt().coerceAtLeast(1)
        }
    }

    private class LuminanceFrame(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        private val pixels = IntArray(width * height).also { destination ->
            bitmap.getPixels(destination, 0, width, 0, 0, width, height)
        }

        fun sample(
            left: Int,
            top: Int,
            candidateWidth: Int,
            candidateHeight: Int,
            point: RelativeSample,
        ): Float {
            val x = (left + point.x * (candidateWidth - 1)).roundToInt().coerceIn(0, width - 1)
            val y = (top + point.y * (candidateHeight - 1)).roundToInt().coerceIn(0, height - 1)
            val color = pixels[y * width + x]
            return (
                ((color shr 16) and 0xff) * RED_LUMINANCE_WEIGHT +
                    ((color shr 8) and 0xff) * GREEN_LUMINANCE_WEIGHT +
                    (color and 0xff) * BLUE_LUMINANCE_WEIGHT
                ) / 255f
        }
    }

    private fun Rect.clampedTo(width: Int, height: Int): Rect? {
        val left = this.left.coerceIn(0, width)
        val top = this.top.coerceIn(0, height)
        val right = this.right.coerceIn(0, width)
        val bottom = this.bottom.coerceIn(0, height)
        return Rect(left, top, right, bottom).takeIf { it.width() > 0 && it.height() > 0 }
    }

    companion object {
        const val TEMPLATE_ID = "douyin_comment_bubble"
        private const val TEMPLATE_ASSET = "comment_icon_template.png"
        private val CANDIDATE_WIDTH_FRACTIONS = floatArrayOf(0.06f, 0.07f, 0.08f, 0.09f, 0.10f, 0.11f, 0.12f, 0.13f)

        private const val SEARCH_STEP_FRACTION = 0.07f
        private const val RETAINED_CANDIDATE_CONFIDENCE = 0.70f
        private const val MIN_DISTINCT_CONFIDENCE_MARGIN = 0.08f
        private const val SAME_ICON_CENTER_TOLERANCE_FRACTION = 0.75f

        private const val TRANSPARENT_ALPHA_THRESHOLD = 32
        private const val OPAQUE_ALPHA_THRESHOLD = 180
        private const val EXPECTED_DOT_COUNT = 3
        private const val MIN_DOT_AREA_FRACTION = 0.003f
        private const val MAX_DOT_AREA_FRACTION = 0.03f
        private const val TARGET_FOREGROUND_SAMPLE_COUNT = 360
        private const val TARGET_DOT_SAMPLE_COUNT = 24

        private const val MIN_FOREGROUND_LUMINANCE = 0.30f
        private const val FULL_FOREGROUND_LUMINANCE = 0.70f
        private const val MIN_DOT_CONTRAST = 0.20f
        private const val FULL_DOT_CONTRAST = 0.45f
        private const val MINIMUM_DOT_WEIGHT = 0.65f
        private const val AVERAGE_DOT_WEIGHT = 0.25f
        private const val FOREGROUND_WEIGHT = 0.10f

        private const val RED_LUMINANCE_WEIGHT = 0.2126f
        private const val GREEN_LUMINANCE_WEIGHT = 0.7152f
        private const val BLUE_LUMINANCE_WEIGHT = 0.0722f
    }
}

/**
 * Confidence gate shared by reviewed template providers. A missing/weak template never produces
 * a coordinate; the caller must still bind any accepted match to its own geometry, page-state,
 * temporal-stability and post-action confirmation gates.
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
