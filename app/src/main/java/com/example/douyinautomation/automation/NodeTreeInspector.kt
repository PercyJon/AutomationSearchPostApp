package com.example.douyinautomation.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max
import kotlin.math.min

/**
 * The only component that converts Android's mutable AccessibilityNodeInfo tree into immutable
 * [NodeSnapshot] values.  Downstream page detection deliberately consumes only those snapshots.
 */
class NodeTreeInspector {

    fun inspect(
        root: AccessibilityNodeInfo?,
        screenSize: ScreenSize? = null,
        packageName: String? = root?.packageName?.toString(),
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): ScreenContext {
        if (root == null) {
            return ScreenContext(
                screenSize = screenSize ?: ScreenSize(1, 1),
                packageName = packageName,
                capturedAtMillis = capturedAtMillis,
            )
        }

        val resolvedScreenSize = screenSize ?: estimateScreenSize(root)
        val snapshots = mutableListOf<NodeSnapshot>()
        val deadlineNanos = System.nanoTime() + MAX_INSPECTION_NANOS
        walk(
            node = root,
            path = emptyList(),
            depth = 0,
            destination = snapshots,
            deadlineNanos = deadlineNanos,
        )
        return ScreenContext(
            screenSize = resolvedScreenSize,
            packageName = packageName,
            nodes = snapshots,
            capturedAtMillis = capturedAtMillis,
        )
    }

    fun diagnosticDump(context: ScreenContext): String = buildString {
        append("package=").append(context.packageName ?: "<unknown>")
        append(" screen=").append(context.screenSize.width).append('x').append(context.screenSize.height)
        context.nodes.forEach { node ->
            append('\n')
            append("  ".repeat(node.depth))
            append('[').append(node.stableId).append("] ")
            append(node.className ?: "<class?>")
            // Resource ids and interaction flags are retained only in the app-private
            // diagnostic dump. They let a no-click safety stop distinguish two visually
            // identical comment avatars whose accessibility roles differ between rows.
            node.viewIdResourceName?.takeIf(String::isNotBlank)?.let {
                append(" id=\"").append(it).append('"')
            }
            node.text?.takeIf(String::isNotBlank)?.let { append(" text=\"").append(it).append('"') }
            node.contentDescription?.takeIf(String::isNotBlank)?.let {
                append(" desc=\"").append(it).append('"')
            }
            append(" bounds=").append(node.bounds)
            if (node.isClickable) append(" clickable")
            if (node.isEditable) append(" editable")
            if (!node.isVisibleToUser) append(" hidden")
        }
    }

    private fun estimateScreenSize(root: AccessibilityNodeInfo): ScreenSize {
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        return ScreenSize(
            width = max(1, bounds.right),
            height = max(1, bounds.bottom),
        )
    }

    @Suppress("DEPRECATION")
    private fun walk(
        node: AccessibilityNodeInfo,
        path: List<Int>,
        depth: Int,
        destination: MutableList<NodeSnapshot>,
        deadlineNanos: Long,
    ) {
        // Douyin can expose hundreds of transient/custom-rendered nodes. A bounded snapshot keeps
        // the accessibility callback responsive; OCR remains the fallback for labels outside the
        // bounded tree. The traversal still includes the shallow navigation nodes first.
        if (
            depth > MAX_DEPTH ||
            destination.size >= MAX_NODES ||
            System.nanoTime() >= deadlineNanos
        ) return
        destination += node.toSnapshot(path = path, depth = depth)
        var childIndex = 0
        while (
            childIndex < node.childCount &&
            destination.size < MAX_NODES &&
            System.nanoTime() < deadlineNanos
        ) {
            // OEM/custom-rendered views can report a childCount that includes a transient null
            // slot.  Breaking here silently discarded all following siblings (in Douyin this
            // can be the entire RecyclerView of user rows), leaving the page detector with only
            // the selected tab and no result anchors.  Skip the hole and keep walking the rest
            // of the bounded tree instead.
            val child = node.getChild(childIndex)
            if (child == null) {
                childIndex += 1
                continue
            }
            try {
                walk(
                    node = child,
                    path = path + childIndex,
                    depth = depth + 1,
                    destination = destination,
                    deadlineNanos = deadlineNanos,
                )
            } finally {
                // Child instances are owned by this traversal. The root remains caller-owned.
                child.recycle()
            }
            childIndex += 1
        }
    }

    private fun AccessibilityNodeInfo.toSnapshot(
        path: List<Int>,
        depth: Int,
    ): NodeSnapshot {
        val rect = Rect()
        getBoundsInScreen(rect)
        return NodeSnapshot(
            hierarchyPath = path,
            text = text?.toString(),
            contentDescription = contentDescription?.toString(),
            hintText = hintText?.toString(),
            stateDescription = stateDescription?.toString(),
            paneTitle = paneTitle?.toString(),
            viewIdResourceName = viewIdResourceName,
            className = className?.toString(),
            packageName = packageName?.toString(),
            // Android/OEM accessibility trees can briefly expose an empty or inverted Rect while
            // a page is transitioning. Normalize it instead of allowing one malformed node to
            // terminate the entire accessibility service.
            bounds = ScreenBounds(
                left = min(rect.left, rect.right),
                top = min(rect.top, rect.bottom),
                right = max(rect.left, rect.right),
                bottom = max(rect.top, rect.bottom),
            ),
            isClickable = isClickable,
            isEditable = isEditable,
            isEnabled = isEnabled,
            isVisibleToUser = isVisibleToUser,
            isScrollable = isScrollable,
            isSelected = isSelected,
            childCount = childCount,
            depth = depth,
        )
    }

    private companion object {
        const val MAX_NODES = 400
        const val MAX_DEPTH = 32
        const val MAX_INSPECTION_NANOS = 500_000_000L
    }
}
