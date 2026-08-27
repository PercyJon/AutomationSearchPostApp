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
    /** Opaque durable reference for checkpoints and record correlation. */
    val fingerprint: String get() = UserIdentityFingerprint.fromStableKey(key)

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
        val nodeCandidates = context.nodes.asSequence()
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { node -> isInsideRow(node.bounds, row) }
            .flatMap { node -> searchableNodeCandidates(node).asSequence() }
            .map { it.copy(value = normalizeCandidate(it.value)) }
            .filter { isUsefulCandidate(it.value) }
            .toList()
        val ocrCandidates = context.ocrBlocks.asSequence()
            .filter { block -> block.bounds.width > 0 && block.bounds.height > 0 }
            .filter { block -> isInsideRow(block.bounds, row) }
            .map { candidate -> IdentityCandidate(candidate.text, CandidateSource.OCR, candidate.bounds) }
            .map { it.copy(value = normalizeCandidate(it.value)) }
            .filter { isUsefulCandidate(it.value) }
            .toList()
        // Keep semantic values first, but merge row-local OCR when it is available.  A custom
        // Douyin row can expose the display name through Accessibility while rendering the
        // company/metadata line only as pixels; filtering only nodeValues would then miss a
        // blocked keyword such as “厂”.  The row bounds keep this merge local to the selected
        // account and prevent neighboring rows from affecting the identity.
        val mergedCandidates = (nodeCandidates + ocrCandidates)
            .map { candidate ->
                candidate.copy(value = ProfileNameCorrection.correct(candidate.value))
            }
            .filterNot { isIdentityNoise(it.value) }
            .distinctBy { "${it.source}:${it.value}:${it.bounds.left}:${it.bounds.top}" }
        if (mergedCandidates.isEmpty()) return null
        return buildIdentity(
            candidates = mergedCandidates,
            source = if (nodeCandidates.isNotEmpty()) {
                UserResultIdentity.Source.ACCESSIBILITY
            } else {
                UserResultIdentity.Source.OCR
            },
        )
    }

    /**
     * Prefer the account handle when Douyin exposes one. Follower counts and labels can change
     * between two overlapping RecyclerView snapshots, while the handle remains stable. When no
     * handle is available, retain the useful name/metadata values but drop volatile counters.
     */
    private fun buildIdentity(
        candidates: List<IdentityCandidate>,
        source: UserResultIdentity.Source,
    ): UserResultIdentity? {
        val distinct = candidates.distinctBy { it.value }
        val values = distinct.map(IdentityCandidate::value)
        val handle = values.firstNotNullOfOrNull(::extractAccountHandle)
        val stableValues = distinct.filterNot { isVolatileMetadata(it.value) || isChromeLabel(it.value) }
        // The list can expose avatar alt text before the actual name.  Never let a generic image
        // description become the durable account title, even when OCR is unavailable. If there is
        // no handle or meaningful text left, return null so the controller skips the row safely.
        val displayNameCandidate = stableValues
            .filter { isLikelyDisplayName(it.value) }
            .sortedWith(
                compareByDescending<IdentityCandidate> { candidateScore(it) }
                    .thenByDescending { it.value.length },
            )
            .firstOrNull()
        if (displayNameCandidate == null && handle == null) return null
        val displayName = displayNameCandidate?.value
        val metadata = stableValues
            .filter { it.value != displayName }
            .map(IdentityCandidate::value)
            .take(MAX_FALLBACK_VALUES)
            .toSet()
        val key = if (!handle.isNullOrBlank()) {
            "handle:$handle"
        } else {
            (stableValues.ifEmpty { distinct }).take(MAX_FALLBACK_VALUES)
                .joinToString("|", transform = IdentityCandidate::value)
        }
        return UserResultIdentity(
            key = key,
            source = source,
            displayName = displayName,
            accountHandle = handle,
            stableMetadata = metadata,
            visibleTokens = values
                .filterNot { it in actionLabels }
                .filterNot { it.length < 2 }
                .take(MAX_FALLBACK_VALUES + 2)
                .toSet(),
        )
    }

    private fun candidateScore(candidate: IdentityCandidate): Int {
        var score = when (candidate.source) {
            CandidateSource.ACCESSIBILITY -> 40
            CandidateSource.OCR -> 20
        }
        if (candidate.field == CandidateField.TEXT) score += 50
        if (candidate.field == CandidateField.CONTENT_DESCRIPTION) score -= 15
        return score
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
            value.all(Char::isDigit) ||
            IdentityCountToken.matches(value)

    private fun isLikelyDisplayName(value: String): Boolean {
        if (value.length !in 2..40) return false
        if (isIdentityNoise(value)) return false
        if (IdentityCountToken.matches(value)) return false
        if (isChromeLabel(value)) return false
        if (value.contains("粉丝") || value.contains("抖音号") || value.contains("获赞")) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun isChromeLabel(value: String): Boolean {
        val compact = value.trim().trimEnd('：', ':')
        if (compact in CHROME_LABELS) return true
        if (value.endsWith("：") || value.endsWith(":")) return true
        return compact.contains("组织认证") || compact.contains("商家认证")
    }

    private fun searchableNodeCandidates(node: NodeSnapshot): List<IdentityCandidate> = listOfNotNull(
        node.text?.let { IdentityCandidate(it, CandidateSource.ACCESSIBILITY, node.bounds, CandidateField.TEXT) },
        node.contentDescription?.let {
            IdentityCandidate(it, CandidateSource.ACCESSIBILITY, node.bounds, CandidateField.CONTENT_DESCRIPTION)
        },
        node.hintText?.let { IdentityCandidate(it, CandidateSource.ACCESSIBILITY, node.bounds, CandidateField.HINT) },
        node.stateDescription?.let {
            IdentityCandidate(it, CandidateSource.ACCESSIBILITY, node.bounds, CandidateField.STATE_DESCRIPTION)
        },
        node.paneTitle?.let { IdentityCandidate(it, CandidateSource.ACCESSIBILITY, node.bounds, CandidateField.PANE_TITLE) },
    )

    private fun normalizeCandidate(value: String): String = TextNormalizer.normalize(value)
        .replace(Regex("\\s+"), "")

    private fun isUsefulCandidate(value: String): Boolean {
        if (value.length < 2 || value in actionLabels) return false
        if (value.contains("关注按钮") || value.contains("回关按钮")) return false
        if (isIdentityNoise(value)) return false
        // Do not use generic class/id metadata or a lone numeric counter as an identity.
        if (value.all(Char::isDigit)) return false
        return value.any { it.isLetterOrDigit() }
    }

    /**
     * Custom Douyin rows commonly expose avatar/image alt text as accessibility content
     * descriptions. These labels are UI chrome, not account identities, and must never be saved
     * as a user's display name. Keep this list conservative so a real account named with ordinary
     * words is not discarded.
     */
    private fun isIdentityNoise(value: String): Boolean {
        val normalized = value.trim()
        if (normalized in IMAGE_IDENTITY_NOISE) return true
        return normalized.contains("头像") ||
            normalized.contains("背景图片") ||
            normalized.contains("封面图片") ||
            normalized.contains("视频封面") ||
            normalized == "视频" ||
            normalized.contains("筛选") ||
            normalized.contains("按钮")
    }

    private fun isInsideRow(bounds: ScreenBounds, row: ScreenBounds): Boolean =
        bounds.centerY in row.top.toFloat()..row.bottom.toFloat() &&
            bounds.centerX >= row.left.toFloat() &&
            bounds.centerX <= row.right.toFloat()

    private const val MAX_FALLBACK_VALUES = 4

    private enum class CandidateSource {
        ACCESSIBILITY,
        OCR,
    }

    private enum class CandidateField {
        TEXT,
        CONTENT_DESCRIPTION,
        HINT,
        STATE_DESCRIPTION,
        PANE_TITLE,
    }

    private data class IdentityCandidate(
        val value: String,
        val source: CandidateSource,
        val bounds: ScreenBounds,
        val field: CandidateField = CandidateField.TEXT,
    )

    private val IMAGE_IDENTITY_NOISE = setOf(
        "背景图片",
        "背景图",
        "用户头像",
        "头像",
        "头像图片",
        "图片",
        "图片背景",
        "背景",
        "默认头像",
        "用户图片",
        "封面",
        "封面图片",
        "视频封面",
        "照片",
        "加载中",
    )

    private val CHROME_LABELS = setOf(
        "朋友",
        "商家认证账号",
        "店铺账号",
        "发过相关视频",
        "抖音组织认证",
    )
}

/**
 * Follower/like counters rendered as “16.8万” or OCR-split “8万”. They are not account names.
 */
internal object IdentityCountToken {
    private val COUNT = Regex("""^\d+(?:\.\d+)?万$""")

    fun matches(value: String): Boolean {
        val compact = value.filterNot(Char::isWhitespace)
        return compact.length in 2..8 && COUNT.matches(compact)
    }
}
