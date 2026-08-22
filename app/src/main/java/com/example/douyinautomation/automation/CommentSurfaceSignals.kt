package com.example.douyinautomation.automation

/** A comment-entry target verified from accessibility, or the narrow OCR-only final fallback. */
sealed interface CommentButtonTarget {
    data class AccessibilityNode(val node: NodeSnapshot) : CommentButtonTarget

    /**
     * Only emitted when the video action rail has unusable accessibility bounds. The caller must
     * issue a coordinate gesture directly; an OCR block is never resolved as an accessibility node.
     */
    data class OcrFallback(val bounds: ScreenBounds) : CommentButtonTarget
}

/**
 * Finds the comment speech-bubble control on a video page. Semantic labels are authoritative;
 * the structural fallback uses the ordered right-side action rail (like, comment, favorite,
 * share). OCR is permitted only as a final coordinate-tap fallback for malformed rail bounds.
 */
object VideoCommentButtonDetector {
    private val labels = listOf("评论", "comment", "comments")
    private val imageClasses = listOf("imageview", "imagebutton", "button")

    /** Maximum vertical centre delta for two rail layers to be treated as the same position. */
    private const val RAIL_CLUSTER_TOLERANCE = 30

    fun find(context: ScreenContext): CommentButtonTarget? {
        // Douyin's custom-rendered video page can report isVisibleToUser=false for an actionable
        // right-rail speech-bubble even though the node is on-screen and clickable: the uiautomator
        // tree proves the node exists with clickable=true/enabled=true while the live accessibility
        // flag is transiently false. A verified semantic match plus on-screen bounds is
        // authoritative, so do not discard it on that transient visibility flag; visibility only
        // ranks a candidate lower. The structural rail fallback below keeps its stricter
        // visibility check because it has no semantic label to fall back on.
        val semantic = context.nodes.asSequence()
            .filter { it.isEnabled && it.isClickable && it.bounds != ScreenBounds.EMPTY }
            .filter { node ->
                val text = node.searchableText().joinToString(" ")
                TextNormalizer.matchingTerms(text, labels).isNotEmpty()
            }
            .filter { it.normalizedBounds(context.screenSize).left >= 0.68f }
            .filter { it.normalizedBounds(context.screenSize).top in 0.20f..0.92f }
            .maxByOrNull { node ->
                val text = node.searchableText().joinToString(" ")
                TextNormalizer.matchingTerms(text, labels).size * 10 +
                    if (node.normalizedBounds(context.screenSize).left >= 0.78f) 1 else 0 +
                    if (node.isVisibleToUser) 2 else 0
            }
        if (semantic != null) return CommentButtonTarget.AccessibilityNode(semantic)

        // The post-swipe video can render its action rail as non-clickable ImageView children
        // (for example id=gmn/c8q click=false) whose parent container owns the click handler.
        // A coordinate tap at the bubble's centre still opens the panel even when ACTION_CLICK is
        // unavailable, so a geometrically verified rail item must not be discarded solely for the
        // clickable flag. When both clickable and non-clickable layers exist, the clickable item
        // still ranks first for the same position.
        val railCandidates = context.nodes.asSequence()
            .filter { node ->
                if (!node.isVisibleToUser || !node.isEnabled) return@filter false
                val normalized = node.normalizedBounds(context.screenSize)
                val className = TextNormalizer.normalize(node.className)
                val imageLike = imageClasses.any(className::contains)
                imageLike && normalized.left >= 0.76f && normalized.top in 0.28f..0.90f &&
                    node.bounds.width in 24..220 && node.bounds.height in 24..220
            }
            .sortedBy { it.bounds.centerY }
            .toList()

        // Cluster rail positions top-to-bottom, keeping the clickable node whenever the same
        // position is also rendered as a non-clickable duplicate layer. The post-swipe video can
        // expose both `id=gmn` (clickable) and `id=c8q` (non-clickable) ImageViews for the same
        // bubble; preferring the clickable one preserves ACTION_CLICK while still resolving the
        // position when only the non-clickable layer is present.
        val rail = mutableListOf<NodeSnapshot>()
        for (candidate in railCandidates) {
            val samePosition = rail.lastOrNull {
                kotlin.math.abs(it.bounds.centerY - candidate.bounds.centerY) <= RAIL_CLUSTER_TOLERANCE
            }
            if (samePosition == null) {
                rail.add(candidate)
            } else if (candidate.isClickable && !samePosition.isClickable) {
                rail[rail.lastIndex] = candidate
            }
        }

        // A real action rail contains at least the heart, comment and one additional action. The
        // second item is the speech bubble according to the supplied Douyin layouts. Requiring a
        // populated rail prevents an arbitrary right-side button from becoming a comment tap.
        rail.takeIf { it.size >= 3 }?.getOrNull(1)?.let { button ->
            return CommentButtonTarget.AccessibilityNode(button)
        }

        // Some Douyin builds expose the entire right rail with a negative Y or zero-height
        // accessibility bounds while the pixels remain visible. At this point both semantic and
        // structural accessibility selectors have rejected the rail, so use the on-screen OCR
        // "评论" label as the narrowly-scoped final fallback. Requiring a compact right-side
        // block prevents captions or an already-open comment panel from becoming a tap target.
        val ocrFallback = context.ocrBlocks.asSequence()
            .filter { block -> block.bounds.width > 0 && block.bounds.height > 0 }
            .filter { block ->
                val normalized = block.bounds.normalized(context.screenSize)
                normalized.centerX >= OCR_RAIL_LEFT && normalized.centerY in OCR_RAIL_TOP..OCR_RAIL_BOTTOM
            }
            .filter { block -> TextNormalizer.matchingTerms(block.text, labels).isNotEmpty() }
            .maxByOrNull { block ->
                val normalized = block.bounds.normalized(context.screenSize)
                (block.confidence ?: 0f) + normalized.centerX
            }
        return ocrFallback?.let { CommentButtonTarget.OcrFallback(it.bounds) }
    }

    private const val OCR_RAIL_LEFT = 0.76f
    private const val OCR_RAIL_TOP = 0.28f
    private const val OCR_RAIL_BOTTOM = 0.90f
}

data class CommentPanelEndDetection(
    val reached: Boolean,
    val confidence: Float,
    val marker: String? = null,
)

/** Detects the terminal marker shown after the comment list has finished loading. */
object CommentPanelEndDetector {
    private val markers = listOf(
        "暂时没有更多了",
        "暂无更多了",
        "暂无更多内容",
        "没有更多内容",
        "没有更多",
    )
    private val emptyPanelMarkers = listOf(
        "期待你的评论",
        "发条评论表达你的想法",
        "暂无评论",
    )

    fun detect(context: ScreenContext): CommentPanelEndDetection {
        val values = (context.nodeText() + context.ocrText())
            .map(TextNormalizer::normalize)
            .filter(String::isNotBlank)
        val marker = values.firstNotNullOfOrNull { value ->
            markers.firstOrNull(value::contains)
        }
        if (marker != null) {
            return CommentPanelEndDetection(reached = true, confidence = 0.96f, marker = marker)
        }

        // A video with no comments does not show the normal "no more" footer. Douyin instead
        // renders “期待你的评论” together with a “去评论” button below the author activity
        // row. Some builds omit the button from the accessibility tree, but retain the “评论 0”
        // tab and the author's “发布了作品” activity row.  Keep that alternate combination
        // equally strict so a caption or an unrelated action labelled “去评论” never completes
        // a normal comment list by itself.
        val emptyPanelMarker = values.firstOrNull { value ->
            emptyPanelMarkers.any(value::contains)
        }
        val hasGoCommentAction = values.any { value -> value.contains("去评论") }
        val hasZeroCommentTab = values.any { value -> value.matches(Regex("评论\\s*0")) }
        val hasAuthorActivity = values.any { value -> value.contains("发布了作品") }
        if (emptyPanelMarker != null && (hasGoCommentAction || (hasZeroCommentTab && hasAuthorActivity))) {
            val proof = if (hasGoCommentAction) {
                "去评论"
            } else {
                "评论0/发布了作品"
            }
            return CommentPanelEndDetection(
                reached = true,
                confidence = 0.94f,
                marker = "${emptyPanelMarker}/$proof",
            )
        }
        return CommentPanelEndDetection(
            reached = false,
            confidence = 0.05f,
            marker = null,
        )
    }
}
