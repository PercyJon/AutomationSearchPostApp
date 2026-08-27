package com.example.douyinautomation.automation

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.roundToInt
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Coroutine-friendly wrapper around ML Kit text recognition.
 *
 * OCR text is returned only to the caller and is deliberately never included in diagnostic logs.
 * M0 uses ML Kit's on-device Chinese recognizer because the target client is normally displayed in
 * Chinese. A different [TextRecognizer] can still be injected for a localized test device.
 */
class MlKitOcrEngine(
    private val logger: DiagnosticLogger = DiagnosticLogger(),
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build(),
    ),
) : Closeable {
    private val isClosed = AtomicBoolean(false)

    suspend fun recognize(bitmap: Bitmap, region: OcrRegion = OcrRegion.FULL): OcrResult {
        check(!isClosed.get()) { "MlKitOcrEngine is already closed" }
        check(!bitmap.isRecycled) { "Cannot recognize a recycled bitmap" }

        val crop = region.boundsFor(bitmap.width, bitmap.height)
        val source = if (crop == null || crop.width() <= 0 || crop.height() <= 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
        }
        val scale = if (source.width < OCR_MIN_SIDE || source.height < OCR_MIN_SIDE) OCR_SCALE else 1f
        val input = if (scale == 1f) source else Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        return try {
            val recognizedText = recognizer.process(InputImage.fromBitmap(input, ROTATION_DEGREES))
                .awaitResult()
            val result = recognizedText.toOcrResult(
                sourceOffset = crop?.let { it.left to it.top } ?: (0 to 0),
                scale = scale,
            )
            logger.info(
                "ocr_completed",
                attributes = mapOf(
                    "blocks" to result.blocks.size,
                    "characters" to result.text.length,
                    "size" to "${bitmap.width}x${bitmap.height}",
                    "region" to region.name,
                ),
            )
            result
        } catch (error: Throwable) {
            logger.error(
                "ocr_failed",
                message = "ML Kit recognition failed",
                throwable = error,
            )
            throw error
        } finally {
            if (input !== source) input.recycle()
            if (source !== bitmap) source.recycle()
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            recognizer.close()
            logger.info("ocr_closed")
        }
    }

    private fun Text.toOcrResult(sourceOffset: Pair<Int, Int>, scale: Float): OcrResult = OcrResult(
        text = text,
        blocks = textBlocks.map { block ->
            OcrBlock(
                text = block.text,
                bounds = block.boundingBox?.let { it.toSourceRect(sourceOffset, scale) },
                lines = block.lines.map { line ->
                    OcrLine(
                        text = line.text,
                        bounds = line.boundingBox?.let { it.toSourceRect(sourceOffset, scale) },
                    )
                },
            )
        },
    )

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { error ->
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }

    private companion object {
        const val ROTATION_DEGREES = 0
        const val OCR_SCALE = 1.5f
        const val OCR_MIN_SIDE = 720
    }
}

enum class OcrRegion {
    FULL,
    USER_RESULTS,
    /**
     * Narrow player-side crop for right-rail count geometry after a verified video swipe.
     *
     * The bounds deliberately use screen ratios: they include the complete action/count rail
     * with a margin, but exclude caption and most changing video content. This both keeps OCR
     * bounded and avoids treating unrelated video text as a count-rail candidate.
     */
    VIDEO_ACTION_RAIL,
    /** Small top profile crop used only when the accessibility tree clips the profile name. */
    PROFILE_HEADER,
    PROFILE_ACTION,
    MESSAGE_COMPOSER,
    TOAST,
    /**
     * Top-right home chrome (tabs + magnifying glass). Screen ratios only; the crop is evidence
     * for HOME, not a click rectangle.
     */
    HOME_SEARCH_CHROME,
    ;

    fun boundsFor(width: Int, height: Int): Rect? = when (this) {
        FULL -> null
        USER_RESULTS -> Rect(0, (height * 0.12f).roundToInt(), width, (height * 0.96f).roundToInt())
        VIDEO_ACTION_RAIL -> OcrRegionGeometry.videoActionRailBounds(width, height).toRect()
        PROFILE_HEADER -> Rect(0, (height * 0.08f).roundToInt(), width, (height * 0.35f).roundToInt())
        PROFILE_ACTION -> Rect(0, (height * 0.28f).roundToInt(), width, (height * 0.66f).roundToInt())
        MESSAGE_COMPOSER -> Rect(0, (height * 0.62f).roundToInt(), width, height)
        TOAST -> Rect(0, (height * 0.35f).roundToInt(), width, (height * 0.78f).roundToInt())
        HOME_SEARCH_CHROME -> OcrRegionGeometry.homeSearchChromeBounds(width, height).toRect()
    }
}

/** Pure ratio geometry, separated from Android [Rect] so it remains unit-testable on the JVM. */
internal object OcrRegionGeometry {
    fun videoActionRailBounds(width: Int, height: Int): OcrCropBounds = OcrCropBounds(
        left = (width * 0.68f).roundToInt(),
        top = (height * 0.36f).roundToInt(),
        right = width,
        bottom = (height * 0.96f).roundToInt(),
    )

    fun homeSearchChromeBounds(width: Int, height: Int): OcrCropBounds = OcrCropBounds(
        left = (width * EmptyTreeHomeSearchChromePolicy.LEFT).roundToInt(),
        top = (height * EmptyTreeHomeSearchChromePolicy.TOP).roundToInt(),
        right = (width * EmptyTreeHomeSearchChromePolicy.RIGHT).roundToInt(),
        bottom = (height * EmptyTreeHomeSearchChromePolicy.BOTTOM).roundToInt(),
    )
}

internal data class OcrCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun toRect(): Rect = Rect(left, top, right, bottom)
}

private fun Rect.toSourceRect(offset: Pair<Int, Int>, scale: Float): Rect {
    val left = (left / scale).roundToInt() + offset.first
    val top = (top / scale).roundToInt() + offset.second
    val right = (right / scale).roundToInt() + offset.first
    val bottom = (bottom / scale).roundToInt() + offset.second
    return Rect(left, top, right, bottom)
}

data class OcrResult(
    val text: String,
    val blocks: List<OcrBlock>,
) {
    val isEmpty: Boolean
        get() = text.isBlank()
}

data class OcrBlock(
    val text: String,
    val bounds: Rect?,
    val lines: List<OcrLine>,
)

data class OcrLine(
    val text: String,
    val bounds: Rect?,
)
