package com.example.douyinautomation.automation

import android.graphics.Bitmap
import android.graphics.Rect
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

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        check(!isClosed.get()) { "MlKitOcrEngine is already closed" }
        check(!bitmap.isRecycled) { "Cannot recognize a recycled bitmap" }

        return try {
            val recognizedText = recognizer.process(InputImage.fromBitmap(bitmap, ROTATION_DEGREES))
                .awaitResult()
            val result = recognizedText.toOcrResult()
            logger.info(
                "ocr_completed",
                attributes = mapOf(
                    "blocks" to result.blocks.size,
                    "characters" to result.text.length,
                    "size" to "${bitmap.width}x${bitmap.height}",
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
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            recognizer.close()
            logger.info("ocr_closed")
        }
    }

    private fun Text.toOcrResult(): OcrResult = OcrResult(
        text = text,
        blocks = textBlocks.map { block ->
            OcrBlock(
                text = block.text,
                bounds = block.boundingBox?.let(::Rect),
                lines = block.lines.map { line ->
                    OcrLine(
                        text = line.text,
                        bounds = line.boundingBox?.let(::Rect),
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
    }
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
