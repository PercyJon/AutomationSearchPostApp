package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Executes an accessibility-node action first and uses the selected node's bounds only as a
 * semantic fallback. There are no hard-coded device pixels in this class.
 */
class GestureEngine(
    private val service: AccessibilityService,
    private val logger: DiagnosticLogger,
) {
    suspend fun click(node: AccessibilityNodeInfo, fallbackBounds: ScreenBounds): ActionOutcome {
        if (node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return ActionOutcome.success("node_click")
        }

        logger.warn("node_click_fallback", attributes = mapOf("route" to "bounds_gesture"))
        return tapBounds(fallbackBounds)
    }

    fun setText(node: AccessibilityNodeInfo, value: String): ActionOutcome {
        if (!node.isEnabled || !node.isEditable) {
            return ActionOutcome.failure("Target is not an enabled editable node")
        }

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            ActionOutcome.success("node_set_text")
        } else {
            ActionOutcome.failure("ACTION_SET_TEXT was rejected")
        }
    }

    fun submitText(node: AccessibilityNodeInfo): ActionOutcome =
        if (
            node.isEnabled && node.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
            )
        ) {
            ActionOutcome.success("ime_enter")
        } else {
            ActionOutcome.failure("ACTION_IME_ENTER was rejected")
        }

    fun scrollForward(node: AccessibilityNodeInfo): ActionOutcome =
        if (node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            ActionOutcome.success("node_scroll_forward")
        } else {
            ActionOutcome.failure("ACTION_SCROLL_FORWARD was rejected")
        }

    suspend fun tapBounds(bounds: ScreenBounds): ActionOutcome {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return ActionOutcome.failure("Target bounds are empty")
        }
        return dispatchTap(bounds.centerX, bounds.centerY)
    }

    /** Explicit, profile-supplied coordinate fallback for a future version; never used by M0. */
    suspend fun tapNormalized(x: Float, y: Float): ActionOutcome {
        if (x !in 0f..1f || y !in 0f..1f) return ActionOutcome.failure("Normalized point is out of range")
        val metrics = service.resources.displayMetrics
        return dispatchTap(x * metrics.widthPixels, y * metrics.heightPixels)
    }

    suspend fun tapNormalized(point: NormalizedPoint): ActionOutcome =
        tapNormalized(point.x, point.y)

    /** Normalized horizontal gesture used only to reveal an off-screen result category. */
    suspend fun swipeNormalized(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = SWIPE_DURATION_MS,
    ): ActionOutcome {
        if (startX !in 0f..1f || startY !in 0f..1f || endX !in 0f..1f || endY !in 0f..1f) {
            return ActionOutcome.failure("Normalized swipe point is out of range")
        }
        val metrics = service.resources.displayMetrics
        return dispatchSwipe(
            startX = startX * metrics.widthPixels,
            startY = startY * metrics.heightPixels,
            endX = endX * metrics.widthPixels,
            endY = endY * metrics.heightPixels,
            durationMs = durationMs,
        )
    }

    private suspend fun dispatchTap(x: Float, y: Float): ActionOutcome = suspendCancellableCoroutine { continuation ->
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, TAP_START_DELAY_MS, TAP_DURATION_MS))
            .build()
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(ActionOutcome.success("bounds_gesture"))
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(ActionOutcome.failure("Gesture was cancelled by the system"))
                    }
                }
            },
            null,
        )
        if (!dispatched && continuation.isActive) {
            continuation.resume(ActionOutcome.failure("System rejected gesture dispatch"))
        }
    }

    private suspend fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
    ): ActionOutcome = suspendCancellableCoroutine { continuation ->
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, TAP_START_DELAY_MS, durationMs))
            .build()
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(ActionOutcome.success("normalized_swipe"))
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(ActionOutcome.failure("Swipe was cancelled by the system"))
                    }
                }
            },
            null,
        )
        if (!dispatched && continuation.isActive) {
            continuation.resume(ActionOutcome.failure("System rejected swipe dispatch"))
        }
    }

    private companion object {
        const val TAP_START_DELAY_MS = 0L
        const val TAP_DURATION_MS = 60L
        const val SWIPE_DURATION_MS = 260L
    }
}

data class ActionOutcome(
    val succeeded: Boolean,
    val route: String,
    val reason: String? = null,
) {
    companion object {
        fun success(route: String) = ActionOutcome(succeeded = true, route = route)
        fun failure(reason: String) = ActionOutcome(succeeded = false, route = "none", reason = reason)
    }
}
