package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.view.Display
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Captures the default display through an enabled [AccessibilityService] and writes a PNG to
 * app-private storage. The temporary hardware-backed bitmap never leaves this class; it is copied
 * into ARGB_8888 before encoding and its [HardwareBuffer] is always closed.
 */
class ScreenshotCapture(
    private val service: AccessibilityService,
    private val logger: DiagnosticLogger = DiagnosticLogger(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val callbackExecutor: Executor = Dispatchers.IO.asExecutor(),
) {
    private val sequence = AtomicLong()
    private val captureMutex = Mutex()
    private var lastRequestAtMillis = 0L
    private var activeNodeOnlyFallbackReason: ScreenshotNodeOnlyFallbackReason? = null

    suspend fun capture(tag: String): ScreenshotArtifact = captureMutex.withLock {
        val now = android.os.SystemClock.uptimeMillis()
        val waitMillis = MIN_REQUEST_INTERVAL_MILLIS - (now - lastRequestAtMillis)
        if (waitMillis > 0L) delay(waitMillis)
        lastRequestAtMillis = android.os.SystemClock.uptimeMillis()
        captureInternal(tag)
    }

    private suspend fun captureInternal(tag: String): ScreenshotArtifact {
        val safeTag = normalizeTag(tag)
        logger.info("screenshot_requested", attributes = mapOf("tag" to safeTag))

        return suspendCancellableCoroutine { continuation ->
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    callbackExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            try {
                                if (!continuation.isActive) return
                                activeNodeOnlyFallbackReason = null

                                val artifact = writeArtifact(
                                    hardwareBuffer = hardwareBuffer,
                                    colorSpace = screenshot.colorSpace,
                                    tag = safeTag,
                                )
                                logger.info(
                                    "screenshot_saved",
                                    attributes = mapOf(
                                        "tag" to safeTag,
                                        "size" to "${artifact.width}x${artifact.height}",
                                        "file" to artifact.file.name,
                                    ),
                                )
                                if (continuation.isActive) {
                                    continuation.resume(artifact)
                                }
                            } catch (error: Throwable) {
                                resumeFailure(
                                    continuation = continuation,
                                    exception = ScreenshotCaptureException("Unable to save screenshot", error),
                                )
                            } finally {
                                hardwareBuffer.close()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            val failureKind = ScreenshotCaptureFailureKind.fromErrorCode(errorCode)
                            resumeFailure(
                                continuation = continuation,
                                exception = ScreenshotCaptureException(
                                    message = "Accessibility screenshot failed: ${failureKind.diagnosticName}",
                                    errorCode = errorCode,
                                    failureKind = failureKind,
                                ),
                            )
                        }
                    },
                )
            } catch (error: Throwable) {
                resumeFailure(
                    continuation = continuation,
                    exception = ScreenshotCaptureException("Unable to request screenshot", error),
                )
            }
        }
    }

    private fun writeArtifact(
        hardwareBuffer: HardwareBuffer,
        colorSpace: ColorSpace?,
        tag: String,
    ): ScreenshotArtifact {
        val sourceBitmap = Bitmap.wrapHardwareBuffer(
            hardwareBuffer,
            colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB),
        ) ?: throw IOException("Could not wrap screenshot hardware buffer")

        val argbBitmap = try {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IOException("Could not copy screenshot into ARGB_8888")
        } finally {
            sourceBitmap.recycle()
        }

        try {
            val timestamp = nowMillis()
            val directory = artifactDirectory()
            val baseName = "screenshot_${timestamp}_${sequence.incrementAndGet()}_$tag"
            val destination = File(directory, "$baseName.png")
            val temporary = File(directory, "$baseName.tmp")

            try {
                FileOutputStream(temporary).use { output ->
                    if (!argbBitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) {
                        throw IOException("Could not encode screenshot PNG")
                    }
                    output.fd.sync()
                }
                if (!temporary.renameTo(destination)) {
                    throw IOException("Could not finalize screenshot artifact")
                }
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }

            return ScreenshotArtifact(
                file = destination,
                tag = tag,
                capturedAtMillis = timestamp,
                width = argbBitmap.width,
                height = argbBitmap.height,
            )
        } finally {
            argbBitmap.recycle()
        }
    }

    private fun artifactDirectory(): File {
        val directory = File(service.filesDir, ARTIFACT_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create private screenshot directory")
        }
        if (!directory.isDirectory) {
            throw IOException("Private screenshot path is not a directory")
        }
        return directory
    }

    private fun resumeFailure(
        continuation: kotlinx.coroutines.CancellableContinuation<ScreenshotArtifact>,
        exception: ScreenshotCaptureException,
    ) {
        val nodeOnlyFallbackReason = ScreenshotNodeOnlyFallbackPolicy.reasonFor(exception.failureKind)
        if (
            ScreenshotNodeOnlyFallbackPolicy.shouldReport(
                previousReason = activeNodeOnlyFallbackReason,
                currentReason = nodeOnlyFallbackReason,
            )
        ) {
            logger.warn(
                "screenshot_pure_node_tree_fallback",
                message = "Screenshot is unavailable for a protected window; continuing only with accessibility nodes",
                attributes = mapOf("reason" to nodeOnlyFallbackReason?.name),
            )
        }
        activeNodeOnlyFallbackReason = nodeOnlyFallbackReason
        logger.error(
            "screenshot_failed",
            message = exception.message,
            attributes = exception.errorCode?.let {
                mapOf("code" to it, "reason" to exception.failureKind?.diagnosticName)
            }
                ?: emptyMap(),
            throwable = exception.cause,
        )
        if (continuation.isActive) {
            continuation.resumeWithException(exception)
        }
    }

    private fun normalizeTag(tag: String): String =
        tag.trim()
            .lowercase(Locale.US)
            .replace(NON_TAG_CHARACTER, "_")
            .trim('_')
            .take(MAX_TAG_LENGTH)
            .ifBlank { "screen" }

    companion object {
        private const val ARTIFACT_DIRECTORY = "diagnostics/screenshots"
        private const val MAX_TAG_LENGTH = 32
        private const val PNG_QUALITY = 100
        // Android may reject back-to-back AccessibilityService.takeScreenshot calls even when
        // they originate from different coroutines. Keep the shared capture path serialized.
        private const val MIN_REQUEST_INTERVAL_MILLIS = 1_100L
        private val NON_TAG_CHARACTER = Regex("[^a-z0-9_-]+")
    }
}

data class ScreenshotArtifact(
    val file: File,
    val tag: String,
    val capturedAtMillis: Long,
    val width: Int,
    val height: Int,
) {
    /** Absolute app-private path, useful in in-app diagnostics and debug run-as workflows. */
    val path: String
        get() = file.absolutePath
}

class ScreenshotCaptureException(
    message: String,
    cause: Throwable? = null,
    val errorCode: Int? = null,
    val failureKind: ScreenshotCaptureFailureKind? = null,
) : IOException(message, cause)

enum class ScreenshotCaptureFailureKind(
    val diagnosticName: String,
) {
    INTERNAL_ERROR("internal_error"),
    INTERVAL_TOO_SHORT("interval_too_short"),
    INVALID_DISPLAY("invalid_display"),
    INVALID_WINDOW("invalid_window"),
    ACCESSIBILITY_ACCESS_UNAVAILABLE("accessibility_access_unavailable"),
    SECURE_WINDOW("secure_window"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromErrorCode(errorCode: Int): ScreenshotCaptureFailureKind = when (errorCode) {
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> INTERNAL_ERROR
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> INTERVAL_TOO_SHORT
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> INVALID_DISPLAY
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW -> INVALID_WINDOW
            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> ACCESSIBILITY_ACCESS_UNAVAILABLE
            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> SECURE_WINDOW
            else -> UNKNOWN
        }
    }
}

enum class ScreenshotNodeOnlyFallbackReason {
    SECURE_WINDOW,
}

internal object ScreenshotNodeOnlyFallbackPolicy {
    fun reasonFor(failureKind: ScreenshotCaptureFailureKind?): ScreenshotNodeOnlyFallbackReason? =
        when (failureKind) {
            ScreenshotCaptureFailureKind.SECURE_WINDOW -> ScreenshotNodeOnlyFallbackReason.SECURE_WINDOW
            else -> null
        }

    fun shouldReport(
        previousReason: ScreenshotNodeOnlyFallbackReason?,
        currentReason: ScreenshotNodeOnlyFallbackReason?,
    ): Boolean = previousReason == null && currentReason != null
}
