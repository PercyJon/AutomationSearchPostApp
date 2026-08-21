package com.example.douyinautomation.automation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Last-resort structural proof for the first visible user-search result.
 *
 * A few Douyin builds draw the selected User tab in a custom surface while reporting the
 * accessibility subtree of an off-screen ViewPager page. In that state there is no trustworthy
 * live row node or action label to use. This detector does not turn arbitrary OCR text into a
 * click target: it accepts only the first visible, repeated user-card layout containing a name,
 * follower metadata, Douyin account marker, and a right-side Follow label. The controller then
 * uses the derived row geometry solely as the final bounded gesture fallback for the single-user
 * P0 probe, after two matching OCR samples agree.
 */
data class OcrUserResultRowMatch(
    val rowBounds: ScreenBounds,
    val followBounds: ScreenBounds,
    val accountBounds: ScreenBounds,
    /** Kept in memory only to ensure two OCR observations refer to the same first result. */
    internal val accountProof: String,
) {
    fun asStructuralMatch(): StructuralUserRowMatch = StructuralUserRowMatch(
        // No live node corresponds to this custom-rendered row. The synthetic snapshots carry
        // only verified geometry; gestures never try to resolve or click them as live nodes.
        row = NodeSnapshot(
            bounds = rowBounds,
            isVisibleToUser = true,
        ),
        anchor = NodeSnapshot(
            contentDescription = "关注",
            bounds = followBounds,
            isVisibleToUser = true,
        ),
        source = StructuralUserRowMatch.Source.OCR_ASSISTED_STABLE,
    )

    fun agreesWith(other: OcrUserResultRowMatch): Boolean =
        accountProof == other.accountProof &&
            abs(rowBounds.top - other.rowBounds.top) <= MAX_STABLE_ROW_DRIFT_PX &&
            abs(rowBounds.bottom - other.rowBounds.bottom) <= MAX_STABLE_ROW_DRIFT_PX &&
            abs(followBounds.centerY - other.followBounds.centerY) <= MAX_STABLE_ROW_DRIFT_PX

    private companion object {
        const val MAX_STABLE_ROW_DRIFT_PX = 36
    }
}

data class OcrUserResultRowAnalysis(
    val match: OcrUserResultRowMatch?,
    /** Count only; safe to emit in diagnostics without exposing recognised account text. */
    val accountMarkerCount: Int,
)

object OcrUserResultRowDetector {
    fun analyzeFirstVisible(context: ScreenContext): OcrUserResultRowAnalysis {
        val screenWidth = context.screenSize.width.coerceAtLeast(1)
        val screenHeight = context.screenSize.height.coerceAtLeast(1)
        val contentTop = (screenHeight * CONTENT_TOP_RATIO).toInt()
        val contentBottom = (screenHeight * CONTENT_BOTTOM_RATIO).toInt()
        val textLeft = (screenWidth * TEXT_LEFT_RATIO).toInt()
        val textRight = (screenWidth * TEXT_RIGHT_RATIO).toInt()

        val accountMarkers = context.ocrBlocks.asSequence()
            .filter { block -> isUsable(block.bounds) }
            .filter { block -> block.bounds.top >= contentTop && block.bounds.bottom <= contentBottom }
            .filter { block -> block.bounds.left in textLeft..textRight }
            .filter { block -> isAccountMarker(block.text) }
            .sortedBy { block -> block.bounds.top }
            .toList()
        val firstAccount = accountMarkers.firstOrNull()
            ?: return OcrUserResultRowAnalysis(match = null, accountMarkerCount = 0)

        // The earliest account line anchors the first visible result. Never walk forward to a
        // later card if this one is incomplete: P0 is explicitly limited to the first user.
        val title = context.ocrBlocks.asSequence()
            .filter { block -> isUsable(block.bounds) }
            .filter { block -> block.bounds.left in textLeft..textRight }
            .filter { block -> block.bounds.bottom <= firstAccount.bounds.top + TITLE_BOTTOM_TOLERANCE_PX }
            .filter { block -> block.bounds.top >= firstAccount.bounds.top - max(MIN_TITLE_DISTANCE_PX, screenHeight / 10) }
            .filter { block -> isLikelyDisplayName(block.text) }
            .filter { block -> abs(block.bounds.left - firstAccount.bounds.left) <= screenWidth * MAX_TEXT_COLUMN_DRIFT_RATIO }
            .sortedBy { block -> block.bounds.top }
            .firstOrNull()
            // ML Kit may group a card's name, follower line, and account line into one text
            // block. Its individual line bounds are intentionally not retained in
            // [OcrTextBlock], so accept that block only when a non-metadata display-name line is
            // present inside the same tightly bounded card.
            ?: firstAccount.takeIf { block -> containsEmbeddedDisplayName(block.text) }
            ?: return OcrUserResultRowAnalysis(match = null, accountMarkerCount = accountMarkers.size)

        val hasFollowerMetadata = context.ocrBlocks.any { block ->
            isUsable(block.bounds) &&
                block.bounds.left in textLeft..textRight &&
                block.bounds.centerY in title.bounds.top.toFloat()..firstAccount.bounds.bottom.toFloat() &&
                abs(block.bounds.left - firstAccount.bounds.left) <= screenWidth * MAX_TEXT_COLUMN_DRIFT_RATIO &&
                TextNormalizer.normalize(block.text).contains("粉丝")
        }
        if (!hasFollowerMetadata) {
            return OcrUserResultRowAnalysis(match = null, accountMarkerCount = accountMarkers.size)
        }

        val rowTop = max(contentTop, title.bounds.top - max(TITLE_TOP_PADDING_PX, title.bounds.height))
        val provisionalRowBottom = min(
            contentBottom,
            firstAccount.bounds.bottom + max(ROW_BOTTOM_PADDING_PX, title.bounds.height),
        )
        val follow = context.ocrBlocks.asSequence()
            .filter { block -> isUsable(block.bounds) }
            .filter { block -> block.bounds.left >= screenWidth * FOLLOW_LEFT_RATIO }
            .filter { block -> block.bounds.width in MIN_FOLLOW_WIDTH_PX..(screenWidth * MAX_FOLLOW_WIDTH_RATIO).toInt() }
            .filter { block -> block.bounds.height <= screenHeight * MAX_FOLLOW_HEIGHT_RATIO }
            .filter { block -> isFollowLabel(block.text) }
            .filter { block -> block.bounds.centerY in rowTop.toFloat()..provisionalRowBottom.toFloat() }
            .sortedBy { block -> abs(block.bounds.centerY - firstAccount.bounds.centerY) }
            .firstOrNull()
            ?: return OcrUserResultRowAnalysis(match = null, accountMarkerCount = accountMarkers.size)

        val rowBottom = min(
            contentBottom,
            max(provisionalRowBottom, follow.bounds.bottom + ROW_BOTTOM_PADDING_PX),
        )
        val rowBounds = ScreenBounds(
            left = 0,
            top = rowTop,
            right = screenWidth,
            bottom = rowBottom,
        )
        if (rowBounds.height !in minRowHeight(screenHeight)..maxRowHeight(screenHeight)) {
            return OcrUserResultRowAnalysis(match = null, accountMarkerCount = accountMarkers.size)
        }
        val proof = accountProof(firstAccount.text) ?: return OcrUserResultRowAnalysis(
            match = null,
            accountMarkerCount = accountMarkers.size,
        )
        return OcrUserResultRowAnalysis(
            match = OcrUserResultRowMatch(
                rowBounds = rowBounds,
                followBounds = follow.bounds,
                accountBounds = firstAccount.bounds,
                accountProof = proof,
            ),
            accountMarkerCount = accountMarkers.size,
        )
    }

    private fun isUsable(bounds: ScreenBounds): Boolean = bounds.width > 0 && bounds.height > 0

    private fun isAccountMarker(value: String): Boolean {
        val normalized = TextNormalizer.normalize(value)
        return normalized.contains("抖音号") ||
            normalized.contains("douyin id") ||
            normalized.contains("douyinid")
    }

    private fun accountProof(value: String): String? {
        val normalized = IdentityTextCanonicalizer.normalize(value)
        val suffix = when {
            "抖音号" in normalized -> normalized.substringAfter("抖音号")
            "douyinid" in normalized -> normalized.substringAfter("douyinid")
            else -> normalized.substringAfter("douyin id", missingDelimiterValue = "")
        }.trimStart(':', '：')
            .filter { it.isLetterOrDigit() || it in "_-@." }
        return suffix.takeIf { it.length >= MIN_ACCOUNT_PROOF_LENGTH }
    }

    private fun isLikelyDisplayName(value: String): Boolean {
        val normalized = TextNormalizer.normalize(value)
        if (normalized.length !in 2..48 || normalized.all(Char::isDigit)) return false
        if (DISPLAY_NAME_EXCLUDED_TERMS.any(normalized::contains)) return false
        return normalized.any { it.isLetterOrDigit() }
    }

    private fun containsEmbeddedDisplayName(value: String): Boolean = value
        .lineSequence()
        .map(TextNormalizer::normalize)
        .any(::isLikelyDisplayName)

    private fun isFollowLabel(value: String): Boolean = TextNormalizer.normalize(value) in FOLLOW_LABELS

    private fun minRowHeight(screenHeight: Int): Int = max(MIN_ROW_HEIGHT_PX, (screenHeight * MIN_ROW_HEIGHT_RATIO).toInt())

    private fun maxRowHeight(screenHeight: Int): Int = max(MAX_ROW_HEIGHT_PX, (screenHeight * MAX_ROW_HEIGHT_RATIO).toInt())

    private const val CONTENT_TOP_RATIO = 0.15f
    private const val CONTENT_BOTTOM_RATIO = 0.90f
    private const val TEXT_LEFT_RATIO = 0.18f
    private const val TEXT_RIGHT_RATIO = 0.72f
    private const val FOLLOW_LEFT_RATIO = 0.64f
    private const val MAX_FOLLOW_WIDTH_RATIO = 0.30f
    private const val MAX_FOLLOW_HEIGHT_RATIO = 0.09f
    private const val MAX_TEXT_COLUMN_DRIFT_RATIO = 0.16f
    private const val MIN_FOLLOW_WIDTH_PX = 48
    private const val TITLE_BOTTOM_TOLERANCE_PX = 16
    private const val MIN_TITLE_DISTANCE_PX = 120
    private const val TITLE_TOP_PADDING_PX = 28
    private const val ROW_BOTTOM_PADDING_PX = 32
    private const val MIN_ROW_HEIGHT_PX = 160
    private const val MAX_ROW_HEIGHT_PX = 440
    private const val MIN_ROW_HEIGHT_RATIO = 0.07f
    private const val MAX_ROW_HEIGHT_RATIO = 0.20f
    private const val MIN_ACCOUNT_PROOF_LENGTH = 2

    private val FOLLOW_LABELS = setOf("关注", "follow")
    private val DISPLAY_NAME_EXCLUDED_TERMS = listOf(
        "粉丝",
        "抖音号",
        "douyin",
        "关注",
        "综合",
        "用户",
        "视频",
        "搜索",
    )
}
