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
 * Bounded alpha-mask matcher for the supplied Douyin like and collect icons.
 *
 * These assets are translucent white overlays, so matching a fixed white RGB value would fail
 * when the video behind them changes. The matcher samples the template's opaque silhouette and
 * transparent surrounding points, then verifies the alpha silhouette against the candidate's
 * relative luminance. It only returns visual evidence in a caller-bounded action-rail region; it
 * never chooses a tap.
 */
class AlphaMaskedActionIconMatcher(
    private val assets: AssetManager,
) : ImageMatcher {
    private val signatures: Map<String, AlphaTemplateSignature> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        templateAssets.mapNotNull { (templateId, assetName) ->
            runCatching {
                assets.open(assetName).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { template ->
                        try {
                            AlphaTemplateSignature.from(template)
                        } finally {
                            template.recycle()
                        }
                    }
                }
            }.getOrNull()?.let { templateId to it }
        }.toMap()
    }

    override suspend fun findMatch(
        bitmap: Bitmap,
        templateId: String,
        searchRegion: Rect?,
    ): ImageMatch? {
        if (bitmap.isRecycled || searchRegion == null) return null
        return withContext(Dispatchers.Default) {
            val template = signatures[templateId] ?: return@withContext null
            val region = searchRegion.clampedTo(bitmap.width, bitmap.height) ?: return@withContext null
            findBestMatch(
                frame = LuminanceFrame(bitmap),
                templateId = templateId,
                template = template,
                region = region,
            )
        }
    }

    private fun findBestMatch(
        frame: LuminanceFrame,
        templateId: String,
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

            // The cadence is derived from each candidate's current scale rather than an absolute
            // device-pixel spacing. Nearby candidates and sizes make the match resilient to
            // density, screenshot scale, and the icon's animated antialiasing.
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
                            templateId = templateId,
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
        var silhouetteCoverageCount = 0
        template.foregroundSamples.forEach { point ->
            foregroundTotal += frame.sample(left, top, width, height, point)
        }
        val foregroundLuminance = foregroundTotal / template.foregroundSamples.size
        if (foregroundLuminance < MIN_FOREGROUND_LUMINANCE) return 0f

        var backgroundTotal = 0f
        template.backgroundSamples.forEach { point ->
            backgroundTotal += frame.sample(left, top, width, height, point)
        }
        val backgroundLuminance = backgroundTotal / template.backgroundSamples.size
        val relativeContrast = foregroundLuminance - backgroundLuminance
        if (relativeContrast < MIN_RELATIVE_CONTRAST) return 0f

        template.foregroundSamples.forEach { point ->
            if (frame.sample(left, top, width, height, point) >=
                backgroundLuminance + MIN_FOREGROUND_SAMPLE_CONTRAST
            ) {
                silhouetteCoverageCount += 1
            }
        }
        val silhouetteCoverage = silhouetteCoverageCount.toFloat() / template.foregroundSamples.size
        if (silhouetteCoverage < MIN_SILHOUETTE_COVERAGE) return 0f

        val shapeCorrelation = template.shapeCorrelation(
            frame = frame,
            left = left,
            top = top,
            width = width,
            height = height,
        )
        if (!shapeCorrelation.isFinite() || shapeCorrelation < MIN_SHAPE_CORRELATION) return 0f

        // Every signal is relative to the candidate's own background. This keeps a translucent
        // white icon usable over different videos without claiming that any globally bright area
        // is an icon.
        val foregroundScore = normalize(
            value = foregroundLuminance,
            lower = MIN_FOREGROUND_LUMINANCE,
            upper = FULL_FOREGROUND_LUMINANCE,
        )
        val contrastScore = normalize(
            value = relativeContrast,
            lower = MIN_RELATIVE_CONTRAST,
            upper = FULL_RELATIVE_CONTRAST,
        )
        val coverageScore = normalize(
            value = silhouetteCoverage,
            lower = MIN_SILHOUETTE_COVERAGE,
            upper = FULL_SILHOUETTE_COVERAGE,
        )
        val shapeScore = normalize(
            value = shapeCorrelation,
            lower = MIN_SHAPE_CORRELATION,
            upper = FULL_SHAPE_CORRELATION,
        )
        return (
            foregroundScore * FOREGROUND_WEIGHT +
                contrastScore * CONTRAST_WEIGHT +
                coverageScore * COVERAGE_WEIGHT +
                shapeScore * SHAPE_WEIGHT
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

    private data class RelativeAlphaSample(
        val x: Float,
        val y: Float,
        val alpha: Float,
    )

    private data class AlphaTemplateSignature(
        val aspectRatio: Float,
        val foregroundSamples: List<RelativeSample>,
        val backgroundSamples: List<RelativeSample>,
        val shapeSamples: List<RelativeAlphaSample>,
    ) {
        companion object {
            fun from(bitmap: Bitmap): AlphaTemplateSignature? {
                if (bitmap.width <= 1 || bitmap.height <= 1) return null
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val alpha = IntArray(pixels.size) { index -> pixels[index] ushr 24 }
                val foreground = samplesFor(
                    alpha = alpha,
                    width = width,
                    height = height,
                    targetSamples = TARGET_FOREGROUND_SAMPLE_COUNT,
                    accepts = { value -> value >= OPAQUE_ALPHA_THRESHOLD },
                )
                val background = samplesFor(
                    alpha = alpha,
                    width = width,
                    height = height,
                    targetSamples = TARGET_BACKGROUND_SAMPLE_COUNT,
                    accepts = { value -> value <= TRANSPARENT_ALPHA_THRESHOLD },
                )
                val shape = alphaSamples(alpha, width, height)
                return AlphaTemplateSignature(
                    aspectRatio = width.toFloat() / height,
                    foregroundSamples = foreground,
                    backgroundSamples = background,
                    shapeSamples = shape,
                ).takeIf { signature ->
                    signature.foregroundSamples.isNotEmpty() &&
                        signature.backgroundSamples.isNotEmpty() &&
                        signature.shapeSamples.isNotEmpty()
                }
            }

            private fun samplesFor(
                alpha: IntArray,
                width: Int,
                height: Int,
                targetSamples: Int,
                accepts: (Int) -> Boolean,
            ): List<RelativeSample> {
                val stride = samplingStride(width * height, targetSamples)
                return buildList {
                    var y = 0
                    while (y < height) {
                        var x = 0
                        while (x < width) {
                            if (accepts(alpha[y * width + x])) {
                                add(
                                    RelativeSample(
                                        x = x.toFloat() / (width - 1),
                                        y = y.toFloat() / (height - 1),
                                    ),
                                )
                            }
                            x += stride
                        }
                        y += stride
                    }
                }
            }

            private fun alphaSamples(
                alpha: IntArray,
                width: Int,
                height: Int,
            ): List<RelativeAlphaSample> {
                val stride = samplingStride(width * height, TARGET_SHAPE_SAMPLE_COUNT)
                return buildList {
                    var y = 0
                    while (y < height) {
                        var x = 0
                        while (x < width) {
                            add(
                                RelativeAlphaSample(
                                    x = x.toFloat() / (width - 1),
                                    y = y.toFloat() / (height - 1),
                                    alpha = alpha[y * width + x] / ALPHA_CHANNEL_MAX,
                                ),
                            )
                            x += stride
                        }
                        y += stride
                    }
                }
            }

            private fun samplingStride(size: Int, targetSamples: Int): Int =
                ceil(sqrt(size.toDouble() / targetSamples)).toInt().coerceAtLeast(1)
        }

        fun shapeCorrelation(
            frame: LuminanceFrame,
            left: Int,
            top: Int,
            width: Int,
            height: Int,
        ): Float {
            var alphaTotal = 0f
            var luminanceTotal = 0f
            var alphaSquareTotal = 0f
            var luminanceSquareTotal = 0f
            var crossTotal = 0f
            shapeSamples.forEach { point ->
                val luminance = frame.sample(left, top, width, height, point)
                alphaTotal += point.alpha
                luminanceTotal += luminance
                alphaSquareTotal += point.alpha * point.alpha
                luminanceSquareTotal += luminance * luminance
                crossTotal += point.alpha * luminance
            }
            val sampleCount = shapeSamples.size.toFloat()
            val covariance = crossTotal - alphaTotal * luminanceTotal / sampleCount
            val alphaVariance = alphaSquareTotal - alphaTotal * alphaTotal / sampleCount
            val luminanceVariance = luminanceSquareTotal - luminanceTotal * luminanceTotal / sampleCount
            val denominator = sqrt(alphaVariance * luminanceVariance)
            return if (denominator > 0f) covariance / denominator else Float.NaN
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
        ): Float = sample(left, top, candidateWidth, candidateHeight, point.x, point.y)

        fun sample(
            left: Int,
            top: Int,
            candidateWidth: Int,
            candidateHeight: Int,
            point: RelativeAlphaSample,
        ): Float = sample(left, top, candidateWidth, candidateHeight, point.x, point.y)

        private fun sample(
            left: Int,
            top: Int,
            candidateWidth: Int,
            candidateHeight: Int,
            relativeX: Float,
            relativeY: Float,
        ): Float {
            val x = (left + relativeX * (candidateWidth - 1)).roundToInt().coerceIn(0, width - 1)
            val y = (top + relativeY * (candidateHeight - 1)).roundToInt().coerceIn(0, height - 1)
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
        const val LIKE_TEMPLATE_ID = "douyin_like_icon"
        const val COLLECT_TEMPLATE_ID = "douyin_collect_icon"

        private val templateAssets = mapOf(
            LIKE_TEMPLATE_ID to "like_icon_template.png",
            COLLECT_TEMPLATE_ID to "collect_icon_template.png",
        )
        private val CANDIDATE_WIDTH_FRACTIONS = floatArrayOf(
            0.06f,
            0.07f,
            0.08f,
            0.09f,
            0.10f,
            0.11f,
            0.12f,
            0.13f,
        )

        private const val SEARCH_STEP_FRACTION = 0.10f
        private const val RETAINED_CANDIDATE_CONFIDENCE = 0.72f
        private const val MIN_DISTINCT_CONFIDENCE_MARGIN = 0.08f
        private const val SAME_ICON_CENTER_TOLERANCE_FRACTION = 0.75f

        private const val OPAQUE_ALPHA_THRESHOLD = 180
        private const val TRANSPARENT_ALPHA_THRESHOLD = 32
        private const val TARGET_FOREGROUND_SAMPLE_COUNT = 100
        private const val TARGET_BACKGROUND_SAMPLE_COUNT = 120
        private const val TARGET_SHAPE_SAMPLE_COUNT = 160
        private const val ALPHA_CHANNEL_MAX = 255f

        private const val MIN_FOREGROUND_LUMINANCE = 0.32f
        private const val FULL_FOREGROUND_LUMINANCE = 0.72f
        private const val MIN_RELATIVE_CONTRAST = 0.14f
        private const val FULL_RELATIVE_CONTRAST = 0.45f
        private const val MIN_FOREGROUND_SAMPLE_CONTRAST = 0.06f
        private const val MIN_SILHOUETTE_COVERAGE = 0.68f
        private const val FULL_SILHOUETTE_COVERAGE = 0.92f
        private const val MIN_SHAPE_CORRELATION = 0.52f
        private const val FULL_SHAPE_CORRELATION = 0.80f
        private const val FOREGROUND_WEIGHT = 0.15f
        private const val CONTRAST_WEIGHT = 0.20f
        private const val COVERAGE_WEIGHT = 0.15f
        private const val SHAPE_WEIGHT = 0.50f

        private const val RED_LUMINANCE_WEIGHT = 0.2126f
        private const val GREEN_LUMINANCE_WEIGHT = 0.7152f
        private const val BLUE_LUMINANCE_WEIGHT = 0.0722f
    }
}
