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
    ): ScreenContext = inspectWithMetadata(
        root = root,
        screenSize = screenSize,
        packageName = packageName,
        capturedAtMillis = capturedAtMillis,
    ).context

    /**
     * Returns the same immutable context as [inspect], plus bounded-traversal metadata for
     * diagnostics. The metadata must never be used to relax a page or click safety decision.
     */
    fun inspectWithMetadata(
        root: AccessibilityNodeInfo?,
        screenSize: ScreenSize? = null,
        packageName: String? = root?.packageName?.toString(),
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): NodeTreeInspection {
        if (root == null) {
            return NodeTreeInspection(
                context = ScreenContext(
                    screenSize = screenSize ?: ScreenSize(1, 1),
                    packageName = packageName,
                    capturedAtMillis = capturedAtMillis,
                ),
            )
        }

        val resolvedScreenSize = screenSize ?: estimateScreenSize(root)
        val snapshots = mutableListOf<NodeSnapshot>()
        val deadlineNanos = System.nanoTime() + MAX_INSPECTION_NANOS
        val traversal = TraversalState()
        walk(
            node = root,
            path = emptyList(),
            depth = 0,
            destination = snapshots,
            deadlineNanos = deadlineNanos,
            traversal = traversal,
        )
        return NodeTreeInspection(
            context = ScreenContext(
                screenSize = resolvedScreenSize,
                packageName = packageName,
                nodes = snapshots,
                capturedAtMillis = capturedAtMillis,
            ),
            truncation = traversal.reason?.let { reason ->
                NodeTreeTruncation(reason = reason, capturedNodeCount = snapshots.size)
            },
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
        traversal: TraversalState,
    ) {
        // Douyin can expose hundreds of transient/custom-rendered nodes. A bounded snapshot keeps
        // the accessibility callback responsive; OCR remains the fallback for labels outside the
        // bounded tree. The traversal still includes the shallow navigation nodes first.
        NodeTreeTraversalBudgetPolicy.stopReason(
            depth = depth,
            capturedNodeCount = destination.size,
            maximumDepth = MAX_DEPTH,
            maximumNodeCount = MAX_NODES,
            nowNanos = System.nanoTime(),
            deadlineNanos = deadlineNanos,
        )?.let { reason ->
            traversal.record(reason)
            return
        }
        destination += node.toSnapshot(path = path, depth = depth)
        var childIndex = 0
        while (childIndex < node.childCount) {
            if (traversal.reason != null) return
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
                NodeTreeTraversalBudgetPolicy.stopReason(
                    depth = depth + 1,
                    capturedNodeCount = destination.size,
                    maximumDepth = MAX_DEPTH,
                    maximumNodeCount = MAX_NODES,
                    nowNanos = System.nanoTime(),
                    deadlineNanos = deadlineNanos,
                )?.let { reason ->
                    traversal.record(reason)
                    return
                }
                walk(
                    node = child,
                    path = path + childIndex,
                    depth = depth + 1,
                    destination = destination,
                    deadlineNanos = deadlineNanos,
                    traversal = traversal,
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

    private class TraversalState {
        var reason: NodeTreeTruncationReason? = null
            private set

        fun record(reason: NodeTreeTruncationReason) {
            if (this.reason == null) this.reason = reason
        }
    }
}

data class NodeTreeInspection(
    val context: ScreenContext,
    val truncation: NodeTreeTruncation? = null,
)

data class NodeTreeTruncation(
    val reason: NodeTreeTruncationReason,
    val capturedNodeCount: Int,
)

enum class NodeTreeTruncationReason {
    MAXIMUM_DEPTH,
    MAXIMUM_NODE_COUNT,
    INSPECTION_DEADLINE,
}

internal object NodeTreeTraversalBudgetPolicy {
    fun stopReason(
        depth: Int,
        capturedNodeCount: Int,
        maximumDepth: Int,
        maximumNodeCount: Int,
        nowNanos: Long,
        deadlineNanos: Long,
    ): NodeTreeTruncationReason? = when {
        depth > maximumDepth -> NodeTreeTruncationReason.MAXIMUM_DEPTH
        capturedNodeCount >= maximumNodeCount -> NodeTreeTruncationReason.MAXIMUM_NODE_COUNT
        nowNanos >= deadlineNanos -> NodeTreeTruncationReason.INSPECTION_DEADLINE
        else -> null
    }
}

/** Reports only the start of a continuous truncation interval, avoiding diagnostic log floods. */
internal object NodeTreeTruncationWarningPolicy {
    fun shouldWarn(
        previousReason: NodeTreeTruncationReason?,
        currentReason: NodeTreeTruncationReason?,
    ): Boolean = previousReason == null && currentReason != null
}
