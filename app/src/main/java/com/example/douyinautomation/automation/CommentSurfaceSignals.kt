package com.example.douyinautomation.automation

/**
 * Finds the comment speech-bubble control on a video page. Semantic labels are authoritative;
 * the structural fallback uses the ordered right-side action rail (like, comment, favorite,
 * share) and never taps an OCR text block.
 */
object VideoCommentButtonDetector {
    private val labels = listOf("评论", "comment", "comments")
    private val imageClasses = listOf("imageview", "imagebutton", "button")

    fun find(context: ScreenContext): NodeSnapshot? {
        val semantic = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.isEnabled && it.isClickable }
            .filter { node ->
                val text = node.searchableText().joinToString(" ")
                TextNormalizer.matchingTerms(text, labels).isNotEmpty()
            }
            .filter { it.normalizedBounds(context.screenSize).left >= 0.68f }
            .filter { it.normalizedBounds(context.screenSize).top in 0.20f..0.92f }
            .maxByOrNull { node ->
                val text = node.searchableText().joinToString(" ")
                TextNormalizer.matchingTerms(text, labels).size * 10 +
                    if (node.normalizedBounds(context.screenSize).left >= 0.78f) 1 else 0
            }
        if (semantic != null) return semantic

        val rail = context.nodes.asSequence()
            .filter { node ->
                if (!node.isVisibleToUser || !node.isEnabled || !node.isClickable) return@filter false
                val normalized = node.normalizedBounds(context.screenSize)
                val className = TextNormalizer.normalize(node.className)
                val imageLike = imageClasses.any(className::contains)
                imageLike && normalized.left >= 0.76f && normalized.top in 0.28f..0.90f &&
                    node.bounds.width in 24..220 && node.bounds.height in 24..220
            }
            .sortedBy { it.bounds.centerY }
            .toList()

        // A real action rail contains at least the heart, comment and one additional action. The
        // second item is the speech bubble according to the supplied Douyin layouts. Requiring a
        // populated rail prevents an arbitrary right-side button from becoming a comment tap.
        return rail.takeIf { it.size >= 3 }?.getOrNull(1)
    }
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
        // row. Require both signals so a caption or an unrelated action labelled “去评论” does
        // not complete a normal comment list by itself.
        val emptyPanelMarker = values.firstOrNull { value ->
            emptyPanelMarkers.any(value::contains)
        }
        val hasGoCommentAction = values.any { value -> value.contains("去评论") }
        if (emptyPanelMarker != null && hasGoCommentAction) {
            return CommentPanelEndDetection(
                reached = true,
                confidence = 0.94f,
                marker = "${emptyPanelMarker}/去评论",
            )
        }
        return CommentPanelEndDetection(
            reached = false,
            confidence = 0.05f,
            marker = null,
        )
    }
}
