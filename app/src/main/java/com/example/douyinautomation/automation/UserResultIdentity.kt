package com.example.douyinautomation.automation

/**
 * A short-lived identity for one search-result row. It is kept in memory only for the current
 * run; raw names and account identifiers are never written to diagnostics.
 */
data class UserResultIdentity(
    val key: String,
    val source: Source,
    val displayName: String? = null,
    val accountHandle: String? = null,
    val stableMetadata: Set<String> = emptySet(),
    /** All row-local tokens retained in memory for overlap comparison when OCR clips a line. */
    val visibleTokens: Set<String> = emptySet(),
) {
    enum class Source {
        ACCESSIBILITY,
        OCR,
    }
}

/** Extracts a row identity from semantic text first, then OCR text in the same row bounds. */
object UserResultIdentityExtractor {
    private val actionLabels = setOf(
        "关注",
        "回关",
        "已关注",
        "互相关注",
        "发私信",
        "关注按钮",
        "回关按钮",
        "following",
        "follow",
        "follow back",
    )

    fun extract(context: ScreenContext, match: StructuralUserRowMatch): UserResultIdentity? {
        val row = match.row.bounds
        val nodeValues = context.nodes.asSequence()
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { node -> isInsideRow(node.bounds, row) }
            .flatMap { node -> searchableNodeValues(node).asSequence() }
            .map(::normalizeCandidate)
            .filter(::isUsefulCandidate)
            .toList()
        if (nodeValues.isNotEmpty()) return buildIdentity(nodeValues, UserResultIdentity.Source.ACCESSIBILITY)

        val ocrValues = context.ocrBlocks.asSequence()
            .filter { block -> block.bounds.width > 0 && block.bounds.height > 0 }
            .filter { block -> isInsideRow(block.bounds, row) }
            .map { it.text }
            .map(::normalizeCandidate)
            .filter(::isUsefulCandidate)
            .toList()
        if (ocrValues.isEmpty()) return null
        return buildIdentity(ocrValues, UserResultIdentity.Source.OCR)
    }

    /**
     * Prefer the account handle when Douyin exposes one. Follower counts and labels can change
     * between two overlapping RecyclerView snapshots, while the handle remains stable. When no
     * handle is available, retain the useful name/metadata values but drop volatile counters.
     */
    private fun buildIdentity(values: List<String>, source: UserResultIdentity.Source): UserResultIdentity {
        val distinct = values.distinct()
        val handle = distinct.firstNotNullOfOrNull(::extractAccountHandle)
        val stableValues = distinct.filterNot(::isVolatileMetadata)
        val displayName = stableValues.firstOrNull(::isLikelyDisplayName)
        val metadata = stableValues
            .filter { it != displayName }
            .take(MAX_FALLBACK_VALUES)
            .toSet()
        val key = if (!handle.isNullOrBlank()) {
            "handle:$handle"
        } else {
            (stableValues.ifEmpty { distinct }).take(MAX_FALLBACK_VALUES).joinToString("|")
        }
        return UserResultIdentity(
            key = key,
            source = source,
            displayName = displayName,
            accountHandle = handle,
            stableMetadata = metadata,
            visibleTokens = distinct
                .filterNot { it in actionLabels }
                .filterNot { it.length < 2 }
                .take(MAX_FALLBACK_VALUES + 2)
                .toSet(),
        )
    }

    private fun extractAccountHandle(value: String): String? {
        val marker = value.indexOfFirst { it == ':' || it == '：' }
        val prefix = if (marker >= 0) value.substring(0, marker) else value
        val hasMarker = prefix.contains("抖音号") || prefix.contains("douyin") || prefix == "id"
        if (!hasMarker) return null
        val suffix = if (marker >= 0) value.substring(marker + 1) else value
        return suffix
            .replace(Regex("[^a-z0-9_@.-]"), "")
            .takeIf { it.length >= 2 }
    }

    private fun isVolatileMetadata(value: String): Boolean =
        value.contains("粉丝") ||
            value.contains("获赞") ||
            value.contains("作品") ||
            value.all(Char::isDigit)

    private fun isLikelyDisplayName(value: String): Boolean {
        if (value.length !in 2..40) return false
        if (value in setOf("朋友", "商家认证账号", "店铺账号", "发过相关视频")) return false
        if (value.contains("粉丝") || value.contains("抖音号") || value.contains("获赞")) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun searchableNodeValues(node: NodeSnapshot): List<String> = listOfNotNull(
        node.text,
        node.contentDescription,
        node.hintText,
        node.stateDescription,
        node.paneTitle,
    )

    private fun normalizeCandidate(value: String): String = TextNormalizer.normalize(value)
        .replace(Regex("\\s+"), "")

    private fun isUsefulCandidate(value: String): Boolean {
        if (value.length < 2 || value in actionLabels) return false
        if (value.contains("关注按钮") || value.contains("回关按钮")) return false
        // Do not use generic class/id metadata or a lone numeric counter as an identity.
        if (value.all(Char::isDigit)) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun isInsideRow(bounds: ScreenBounds, row: ScreenBounds): Boolean =
        bounds.centerY in row.top.toFloat()..row.bottom.toFloat() &&
            bounds.centerX >= row.left.toFloat() &&
            bounds.centerX <= row.right.toFloat()

    private const val MAX_FALLBACK_VALUES = 4
}
