package com.example.douyinautomation.automation

/** Source priority used by the comment reader: semantic nodes win over OCR for the same region. */
enum class CommentTextSource {
    ACCESSIBILITY,
    OCR,
}

data class CommentTextFragment(
    val text: String,
    val bounds: ScreenBounds,
    val source: CommentTextSource,
)

/**
 * A comment whose author can be opened from a text region. The extractor intentionally returns
 * the author/name bounds, not an avatar bounds, so a live avatar cannot send the runner into a
 * live room. If the author is not exposed, [interactionBounds] falls back to the comment text
 * region and the future controller must verify the resulting profile before messaging.
 */
data class CommentUserCandidate(
    val authorText: String?,
    val commentText: String,
    val authorBounds: ScreenBounds?,
    val commentBounds: ScreenBounds,
    val interactionBounds: ScreenBounds,
    val matchedKeywords: List<String>,
    val identityKey: String,
    val source: CommentTextSource,
)

data class CommentCandidateExtraction(
    val candidates: List<CommentUserCandidate>,
    val fragments: List<CommentTextFragment>,
) {
    val matchedCount: Int get() = candidates.size
}

/**
 * Extracts comment/user candidates from a frozen screen snapshot. This is deliberately framework
 * free so fixtures can be replayed without opening Douyin. It does not click or infer a page
 * transition; the controller must perform those actions only after a verified candidate.
 */
object CommentCandidateExtractor {
    private const val MIN_TEXT_LENGTH = 2
    private const val MAX_AUTHOR_LENGTH = 40
    private const val HEADER_EXCLUSION_RATIO = 0.14f
    private const val MAX_AUTHOR_GAP_MULTIPLIER = 3f

    fun extract(
        context: ScreenContext,
        matchKeywords: Iterable<String>,
    ): CommentCandidateExtraction {
        val fragments = collectFragments(context)
        val terms = CommentKeywordMatcher.normalizeKeywords(matchKeywords)
        val commentFragments = fragments.filter { fragment ->
            fragment.bounds != ScreenBounds.EMPTY &&
                fragment.bounds.top >= (context.screenSize.height * HEADER_EXCLUSION_RATIO).toInt() &&
                isLikelyCommentText(fragment.text) &&
                CommentKeywordMatcher.matches(fragment.text, terms)
        }
        val candidates = commentFragments.mapNotNull { comment ->
            val author = findAuthor(comment, fragments, context.screenSize.width)
            val authorText = author?.text?.trim()?.takeIf(::isLikelyAuthorText)
            val identitySeed = authorText ?: "${comment.text}:${comment.bounds.centerX}:${comment.bounds.centerY}"
            val key = "comment-user:${IdentityTextCanonicalizer.normalize(identitySeed)}"
            CommentUserCandidate(
                authorText = authorText,
                commentText = comment.text.trim(),
                authorBounds = authorText?.let { author?.bounds },
                commentBounds = comment.bounds,
                interactionBounds = authorText?.let { author?.bounds } ?: comment.bounds,
                matchedKeywords = terms.filter { term ->
                    IdentityTextCanonicalizer.normalize(comment.text).contains(term)
                },
                identityKey = key,
                source = comment.source,
            )
        }.distinctBy(CommentUserCandidate::identityKey)
        return CommentCandidateExtraction(candidates = candidates, fragments = fragments)
    }

    private fun collectFragments(context: ScreenContext): List<CommentTextFragment> {
        val nodeFragments = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds != ScreenBounds.EMPTY }
            .mapNotNull { node ->
                // Text is authoritative. Content descriptions are accepted only when no text is
                // present, preventing image/button accessibility labels from becoming authors.
                val value = node.text?.trim()?.takeIf(String::isNotEmpty)
                    ?: node.contentDescription?.trim()?.takeIf { node.text.isNullOrBlank() && it.isNotEmpty() }
                    ?: return@mapNotNull null
                CommentTextFragment(value, node.bounds, CommentTextSource.ACCESSIBILITY)
            }
            .toList()
        val nodeRegions = nodeFragments.map { it.bounds to IdentityTextCanonicalizer.normalize(it.text) }.toSet()
        val ocrFragments = context.ocrBlocks.asSequence()
            .filter { it.text.isNotBlank() && it.bounds != ScreenBounds.EMPTY }
            .map { CommentTextFragment(it.text.trim(), it.bounds, CommentTextSource.OCR) }
            .filterNot { fragment ->
                (fragment.bounds to IdentityTextCanonicalizer.normalize(fragment.text)) in nodeRegions
            }
            .toList()
        return (nodeFragments + ocrFragments)
            .filter { isUsefulFragment(it.text) }
            .distinctBy { "${it.source}:${IdentityTextCanonicalizer.normalize(it.text)}:${it.bounds}" }
    }

    private fun findAuthor(
        comment: CommentTextFragment,
        fragments: List<CommentTextFragment>,
        screenWidth: Int,
    ): CommentTextFragment? {
        val maxGap = (comment.bounds.height * MAX_AUTHOR_GAP_MULTIPLIER).coerceAtLeast(96f)
        val maxColumnOffset = screenWidth * MAX_AUTHOR_COLUMN_OFFSET_RATIO
        return fragments.asSequence()
            .filter { it !== comment }
            .filter { it.bounds.bottom <= comment.bounds.top }
            .filter { (comment.bounds.top - it.bounds.bottom) <= maxGap }
            .filter { kotlin.math.abs(it.bounds.left - comment.bounds.left) <= maxColumnOffset }
            .filter { isLikelyAuthorText(it.text) }
            .sortedWith(
                compareBy< CommentTextFragment> { comment.bounds.top - it.bounds.bottom }
                    .thenBy { kotlin.math.abs(it.bounds.centerX - comment.bounds.centerX) },
            )
            .firstOrNull()
    }

    private fun isUsefulFragment(value: String): Boolean =
        value.trim().length >= MIN_TEXT_LENGTH && !isUiNoise(value)

    private fun isLikelyCommentText(value: String): Boolean =
        // Short comments such as “价格” or “多少钱” are valid matches, so do not discard them
        // merely because they could also look like a short display name. The future controller
        // will still require a comment-container/post-condition before opening a profile.
        isUsefulFragment(value) && value.trim().length <= 500 &&
            !TextNormalizer.normalize(value).contains("发布了作品") &&
            !TextNormalizer.normalize(value).contains("发条评论表达你的想法") &&
            !TextNormalizer.normalize(value).contains("期待你的评论") &&
            !TextNormalizer.normalize(value).contains("去评论") &&
            !TextNormalizer.normalize(value).endsWith("作者")

    private fun isLikelyAuthorText(value: String): Boolean {
        val normalized = TextNormalizer.normalize(value)
        if (normalized.length !in MIN_TEXT_LENGTH..MAX_AUTHOR_LENGTH) return false
        if (isUiNoise(normalized)) return false
        if (normalized.contains("回复") || normalized.contains("点赞") || normalized.contains("分钟前")) return false
        if (normalized.endsWith("作者")) return false
        if (normalized.endsWith("评论") || normalized.endsWith("条回复")) return false
        // A comment is often a sentence; short names may contain punctuation, but not a full
        // sentence ending in common Chinese/ASCII punctuation.
        return normalized.none { it in "。！？!?；;" }
    }

    private fun isUiNoise(value: String): Boolean {
        val normalized = TextNormalizer.normalize(value)
        if (normalized.isBlank()) return true
        return normalized in UI_NOISE ||
            normalized.contains("写评论") ||
            normalized.contains("展开") ||
            normalized.contains("收起") ||
            normalized.contains("发私信") ||
            normalized.contains("关注") ||
            normalized.contains("分享") ||
            normalized.contains("收藏") ||
            normalized.contains("作品") ||
            normalized.contains("视频")
    }

    private val UI_NOISE = setOf(
        "评论",
        "回复",
        "全部评论",
        "发送",
        "取消",
        "搜索",
        "用户",
        "综合",
    )

    private const val MAX_AUTHOR_COLUMN_OFFSET_RATIO = 0.22f
}

data class CommentSurfaceDetection(
    val isCommentSurface: Boolean,
    val confidence: Float,
    val reasons: List<String>,
)

/** Conservative comment-surface detector used by the future controller before reading rows. */
object CommentSurfaceDetector {
    private val markers = listOf("评论", "全部评论", "写评论", "条评论", "回复")

    fun detect(context: ScreenContext): CommentSurfaceDetection {
        val nodeTexts = context.nodeText().map(TextNormalizer::normalize)
        val ocrTexts = context.ocrText().map(TextNormalizer::normalize)
        val nodeMarkers = nodeTexts.flatMap { text -> markers.filter(text::contains) }.distinct()
        val ocrMarkers = ocrTexts.flatMap { text -> markers.filter(text::contains) }.distinct()
        val bottomComposer = context.nodes.any { node ->
            node.isEditable && node.bounds.top >= context.screenSize.height * 0.68f
        } || ocrTexts.any { it.contains("写评论") }
        val repeatedReply = (nodeTexts + ocrTexts).count { it.contains("回复") } >= 2
        val reasons = buildList {
            if (nodeMarkers.isNotEmpty()) add("node markers: ${nodeMarkers.joinToString()}")
            if (ocrMarkers.isNotEmpty()) add("OCR markers: ${ocrMarkers.joinToString()}")
            if (bottomComposer) add("bottom comment composer")
            if (repeatedReply) add("repeated reply markers")
        }
        val isSurface = nodeMarkers.isNotEmpty() && (bottomComposer || repeatedReply || nodeMarkers.size >= 2) ||
            ocrMarkers.isNotEmpty() && bottomComposer
        return CommentSurfaceDetection(
            isCommentSurface = isSurface,
            confidence = when {
                !isSurface -> 0.1f
                nodeMarkers.isNotEmpty() && bottomComposer -> 0.92f
                nodeMarkers.size >= 2 -> 0.84f
                else -> 0.72f
            },
            reasons = reasons.ifEmpty { listOf("No comment-surface signature matched") },
        )
    }
}
