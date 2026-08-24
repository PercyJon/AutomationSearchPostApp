package com.example.douyinautomation.automation

/** A comment-entry target verified from accessibility, a confirmed visual template, or OCR geometry. */
sealed interface CommentButtonTarget {
    data class AccessibilityNode(val node: NodeSnapshot) : CommentButtonTarget

    /**
     * Only emitted after the next-video probe confirmed the same bounded template candidate in
     * two screenshots. The caller still requires a video-page state and a comment-sheet
     * postcondition before considering the coordinate gesture successful.
     */
    data class TemplateFallback(
        val bounds: ScreenBounds,
        val confidence: Float,
    ) : CommentButtonTarget

    /**
     * Only emitted when the video action rail has unusable accessibility bounds. The caller must
     * issue a coordinate gesture directly; an OCR block is never resolved as an accessibility node.
     */
    data class OcrFallback(val bounds: ScreenBounds) : CommentButtonTarget

    /**
     * Derived only from two stable, independently matched like and collect icons that prove the
     * comment slot between them. This stays behind all existing selector, bubble-template, and
     * OCR-geometry routes.
     */
    data class DualAnchorFallback(
        val bounds: ScreenBounds,
        val likeConfidence: Float,
        val collectConfidence: Float,
    ) : CommentButtonTarget
}

/**
 * Finds the comment speech-bubble control on a video page from the ordered right-side action
 * icons (like, comment, favorite, share). Current Douyin players do not expose a textual comment
 * label there: accessibility text and OCR “评论” are deliberately not entrance evidence.
 *
 * When the icon nodes have unusable bounds, a supplied alpha-aware bubble template may assist
 * only after two stable next-video screenshots. OCR can otherwise recover the target from the
 * geometry of the numeric labels below the icons. If both of those routes fail, two independently
 * stable visual anchors (like above, collect below) can prove the intervening comment slot. A
 * zero count may omit its label, so the OCR fallback accepts a complete four-slot number rail, or
 * a rail whose fixed trailing share label uniquely proves the numeric-slot order. OCR text never
 * directly authorizes a tap.
 */
object VideoCommentButtonDetector {
    private val imageClasses = listOf("imageview", "imagebutton", "button")

    fun find(context: ScreenContext): CommentButtonTarget? {
        structuralActionRail(context)?.getOrNull(1)?.let { button ->
            return CommentButtonTarget.AccessibilityNode(button)
        }
        templateIconFallback(context)?.let { return it }
        ocrActionCountRailFallback(context)?.let { return it }
        return dualAnchorFallback(context)
    }

    private fun templateIconFallback(context: ScreenContext): CommentButtonTarget.TemplateFallback? {
        val match = context.commentIconTemplateMatch?.takeIf(CommentIconTemplateMatch::isConfirmed)
            ?: return null
        if (!hasOnScreenBounds(match.bounds, context.screenSize)) return null
        val normalized = match.bounds.normalized(context.screenSize)
        return CommentButtonTarget.TemplateFallback(match.bounds, match.confidence).takeIf {
            match.confidence >= TEMPLATE_MIN_CONFIDENCE &&
                normalized.left >= TEMPLATE_RAIL_LEFT &&
                normalized.centerY in TEMPLATE_RAIL_TOP..TEMPLATE_RAIL_BOTTOM &&
                normalized.width in TEMPLATE_ICON_MIN_WIDTH..TEMPLATE_ICON_MAX_WIDTH &&
                normalized.height in TEMPLATE_ICON_MIN_HEIGHT..TEMPLATE_ICON_MAX_HEIGHT
        }
    }

    private fun dualAnchorFallback(context: ScreenContext): CommentButtonTarget.DualAnchorFallback? {
        val anchors = context.actionRailAnchorTemplateMatch
            ?.takeIf(ActionRailAnchorTemplateMatch::isConfirmed)
            ?: return null
        val bounds = deriveCommentBoundsFromDualAnchors(anchors, context.screenSize) ?: return null
        return CommentButtonTarget.DualAnchorFallback(
            bounds = bounds,
            likeConfidence = anchors.likeConfidence,
            collectConfidence = anchors.collectConfidence,
        )
    }

    /**
     * Like, comment and collect are consecutive player actions. A valid like/collect pair must
     * therefore share a rail, have comparable rendered sizes, and span exactly two ordinary icon
     * slots. The resulting coordinate is the midpoint; it never relies on screen pixels.
     */
    private fun deriveCommentBoundsFromDualAnchors(
        anchors: ActionRailAnchorTemplateMatch,
        screenSize: ScreenSize,
    ): ScreenBounds? {
        if (!hasOnScreenBounds(anchors.likeBounds, screenSize) ||
            !hasOnScreenBounds(anchors.collectBounds, screenSize)
        ) {
            return null
        }
        val like = anchors.likeBounds.normalized(screenSize)
        val collect = anchors.collectBounds.normalized(screenSize)
        if (!isDualAnchorIcon(like) || !isDualAnchorIcon(collect) ||
            collect.centerY <= like.centerY
        ) {
            return null
        }
        val xSpread = kotlin.math.abs(like.centerX - collect.centerX)
        val widthRatio = minOf(like.width, collect.width) / maxOf(like.width, collect.width)
        val heightRatio = minOf(like.height, collect.height) / maxOf(like.height, collect.height)
        val slotGap = (collect.centerY - like.centerY) / DUAL_ANCHOR_SPANNED_SLOT_COUNT
        if (xSpread > ACTION_RAIL_MAX_X_SPREAD_FRACTION ||
            widthRatio < DUAL_ANCHOR_MIN_SIZE_RATIO ||
            heightRatio < DUAL_ANCHOR_MIN_SIZE_RATIO ||
            slotGap !in ACTION_RAIL_MIN_SLOT_GAP_FRACTION..ACTION_RAIL_MAX_SLOT_GAP_FRACTION
        ) {
            return null
        }

        val halfWidth = ((anchors.likeBounds.width + anchors.collectBounds.width) /
            (2 * DUAL_ANCHOR_HALF_SIZE_DIVISOR)).coerceAtLeast(1)
        val halfHeight = ((anchors.likeBounds.height + anchors.collectBounds.height) /
            (2 * DUAL_ANCHOR_HALF_SIZE_DIVISOR)).coerceAtLeast(1)
        val centerX = ((anchors.likeBounds.centerX + anchors.collectBounds.centerX) / 2f).toInt()
        val centerY = ((anchors.likeBounds.centerY + anchors.collectBounds.centerY) / 2f).toInt()
        val bounds = ScreenBounds(
            left = (centerX - halfWidth).coerceAtLeast(0),
            top = (centerY - halfHeight).coerceAtLeast(0),
            right = (centerX + halfWidth).coerceAtMost(screenSize.width),
            bottom = (centerY + halfHeight).coerceAtMost(screenSize.height),
        )
        val normalized = bounds.normalized(screenSize)
        return bounds.takeIf {
            it.width > 0 &&
                it.height > 0 &&
                normalized.left >= TEMPLATE_RAIL_LEFT &&
                normalized.centerY in TEMPLATE_RAIL_TOP..TEMPLATE_RAIL_BOTTOM &&
                normalized.width in TEMPLATE_ICON_MIN_WIDTH..TEMPLATE_ICON_MAX_WIDTH &&
                normalized.height in TEMPLATE_ICON_MIN_HEIGHT..TEMPLATE_ICON_MAX_HEIGHT
        }
    }

    private fun isDualAnchorIcon(bounds: NormalizedRect): Boolean =
        bounds.left >= TEMPLATE_RAIL_LEFT &&
            bounds.centerY in ACTION_RAIL_TOP..ACTION_RAIL_BOTTOM &&
            bounds.width in TEMPLATE_ICON_MIN_WIDTH..TEMPLATE_ICON_MAX_WIDTH &&
            bounds.height in TEMPLATE_ICON_MIN_HEIGHT..TEMPLATE_ICON_MAX_HEIGHT

    private fun structuralActionRail(context: ScreenContext): List<NodeSnapshot>? {
        // The player can transiently report isVisibleToUser=false for a live image node. The
        // complete, on-screen and evenly spaced icon rail is the proof; visibility only ranks
        // duplicate layers at the same position and is never the sole reason to reject it.
        val candidates = context.nodes.asSequence()
            .filter { node ->
                if (!node.isEnabled || !hasOnScreenBounds(node.bounds, context.screenSize)) {
                    return@filter false
                }
                val normalized = node.normalizedBounds(context.screenSize)
                val className = TextNormalizer.normalize(node.className)
                imageClasses.any(className::contains) &&
                    normalized.left >= ACTION_RAIL_LEFT &&
                    normalized.top in ACTION_RAIL_TOP..ACTION_RAIL_BOTTOM &&
                    normalized.width in ACTION_ICON_MIN_WIDTH..ACTION_ICON_MAX_WIDTH &&
                    normalized.height in ACTION_ICON_MIN_HEIGHT..ACTION_ICON_MAX_HEIGHT
            }
            .sortedBy { it.bounds.centerY }
            .toList()

        val rail = mutableListOf<NodeSnapshot>()
        candidates.forEach { candidate ->
            val existing = rail.lastOrNull {
                kotlin.math.abs(
                    it.normalizedBounds(context.screenSize).centerY -
                        candidate.normalizedBounds(context.screenSize).centerY,
                ) <= RAIL_LAYER_CLUSTER_TOLERANCE_FRACTION
            }
            when {
                existing == null -> rail.add(candidate)
                candidate.isClickable && !existing.isClickable -> rail[rail.lastIndex] = candidate
                candidate.isVisibleToUser && !existing.isVisibleToUser -> rail[rail.lastIndex] = candidate
            }
        }

        return rail.windowed(
            size = MIN_ACTION_RAIL_SLOTS,
            step = 1,
            partialWindows = false,
        ).firstOrNull { isVerifiedActionIconRail(it, context.screenSize) }
    }

    private fun hasOnScreenBounds(bounds: ScreenBounds, screenSize: ScreenSize): Boolean =
        bounds.width > 0 &&
            bounds.height > 0 &&
            bounds.left >= 0 &&
            bounds.top >= 0 &&
            bounds.right <= screenSize.width &&
            bounds.bottom <= screenSize.height

    private fun isVerifiedActionIconRail(
        rail: List<NodeSnapshot>,
        screenSize: ScreenSize,
    ): Boolean {
        if (rail.size != MIN_ACTION_RAIL_SLOTS) return false
        val normalized = rail.map { it.normalizedBounds(screenSize) }
        val gaps = normalized.zipWithNext { upper, lower -> lower.centerY - upper.centerY }
        val minGap = gaps.minOrNull() ?: return false
        val maxGap = gaps.maxOrNull() ?: return false
        val xCenters = normalized.map(NormalizedRect::centerX)
        val xSpread = (xCenters.maxOrNull() ?: 0f) - (xCenters.minOrNull() ?: 0f)
        return minGap >= ACTION_RAIL_MIN_SLOT_GAP_FRACTION &&
            maxGap <= ACTION_RAIL_MAX_SLOT_GAP_FRACTION &&
            maxGap / minGap <= ACTION_RAIL_MAX_GAP_RATIO &&
            xSpread <= ACTION_RAIL_MAX_X_SPREAD_FRACTION
    }

    private fun ocrActionCountRailFallback(context: ScreenContext): CommentButtonTarget.OcrFallback? {
        val rawCounts = context.ocrBlocks.asSequence()
            .filter { block -> hasOnScreenBounds(block.bounds, context.screenSize) }
            .filter { block ->
                val normalized = block.bounds.normalized(context.screenSize)
                normalized.centerX >= OCR_COUNT_RAIL_LEFT &&
                    normalized.centerY in OCR_COUNT_RAIL_TOP..OCR_COUNT_RAIL_BOTTOM
            }
            .filter { block -> engagementCountPattern.matches(TextNormalizer.normalize(block.text)) }
            .sortedBy { it.bounds.centerY }
            .toList()
        val trailingShareMarkers = context.ocrBlocks.asSequence()
            .filter { block -> hasOnScreenBounds(block.bounds, context.screenSize) }
            .filter { block ->
                val normalized = block.bounds.normalized(context.screenSize)
                normalized.centerX >= OCR_COUNT_RAIL_LEFT &&
                    normalized.centerY in OCR_COUNT_RAIL_TOP..OCR_COUNT_RAIL_BOTTOM
            }
            .filter { block ->
                TextNormalizer.matchingTerms(block.text, trailingShareMarkerTerms).isNotEmpty()
            }
            .sortedBy { it.bounds.centerY }
            .toList()

        // OCR can emit overlapping duplicates for one small label. Keep the most confident
        // line before proving the action-stack geometry.
        val counts = mutableListOf<OcrTextBlock>()
        rawCounts.forEach { candidate ->
            val existing = counts.lastOrNull {
                kotlin.math.abs(
                    it.bounds.normalized(context.screenSize).centerY -
                        candidate.bounds.normalized(context.screenSize).centerY,
                ) <= OCR_COUNT_CLUSTER_TOLERANCE_FRACTION
            }
            when {
                existing == null -> counts.add(candidate)
                (candidate.confidence ?: 0f) > (existing.confidence ?: 0f) -> {
                    counts[counts.lastIndex] = candidate
                }
            }
        }

        val layout = findVerifiedCountRailLayout(
            counts = counts,
            trailingShareMarkers = trailingShareMarkers,
            screenSize = context.screenSize,
        ) ?: return null
        return deriveCommentIconBounds(layout, context.screenSize)
            ?.let(CommentButtonTarget::OcrFallback)
    }

    private fun findVerifiedCountRailLayout(
        counts: List<OcrTextBlock>,
        trailingShareMarkers: List<OcrTextBlock>,
        screenSize: ScreenSize,
    ): OcrCountRailLayout? {
        counts.windowed(size = COMPLETE_ACTION_RAIL_SLOTS, step = 1, partialWindows = false)
            .firstNotNullOfOrNull { completeCountRailLayout(it, screenSize) }
            ?.let { return it }

        countRailLayoutWithTrailingShareMarker(counts, trailingShareMarkers, screenSize)
            ?.let { return it }

        // When a count is zero, its label is absent. With exactly one interior gap the remaining
        // three labels have a unique slot mapping: 0,2,3 means comment is zero; 0,1,3 means a
        // later action is zero and the observed second label remains the comment anchor. Equal
        // gaps can also mean an omitted edge slot, so they stay rejected rather than guessed.
        return counts.windowed(size = MISSING_ONE_COUNT_RAIL_SLOTS, step = 1, partialWindows = false)
            .firstNotNullOfOrNull { singleInteriorMissingCountRailLayout(it, screenSize) }
    }

    private fun completeCountRailLayout(
        rail: List<OcrTextBlock>,
        screenSize: ScreenSize,
        countCenterBlocks: List<OcrTextBlock> = rail,
    ): OcrCountRailLayout? {
        val spacing = evenlySpacedCountColumn(rail, screenSize) ?: return null
        return OcrCountRailLayout(
            firstCountCenterY = rail.first().bounds.centerY,
            commentCountCenterY = rail[1].bounds.centerY,
            slotSpacing = spacing,
            countCenterX = countCenterBlocks.map { it.bounds.centerX }.average().toFloat(),
        )
    }

    /**
     * The share action is the fixed fourth slot on the player rail. Its visible label can prove
     * the index of the preceding numeric labels, but is never itself a target or click reason.
     */
    private fun countRailLayoutWithTrailingShareMarker(
        counts: List<OcrTextBlock>,
        trailingShareMarkers: List<OcrTextBlock>,
        screenSize: ScreenSize,
    ): OcrCountRailLayout? {
        for (shareMarker in trailingShareMarkers) {
            val countsBeforeShare = counts.filter { it.bounds.centerY < shareMarker.bounds.centerY }
            countsBeforeShare.windowed(
                size = MISSING_ONE_COUNT_RAIL_SLOTS,
                step = 1,
                partialWindows = false,
            ).firstNotNullOfOrNull { countRail ->
                completeCountRailLayout(
                    rail = countRail + shareMarker,
                    screenSize = screenSize,
                    countCenterBlocks = countRail,
                )
            }?.let { return it }

            countsBeforeShare.windowed(size = 2, step = 1, partialWindows = false)
                .firstNotNullOfOrNull { countRail ->
                    twoCountRailWithTrailingShareMarker(countRail, shareMarker, screenSize)
                }?.let { return it }
        }
        return null
    }

    private fun twoCountRailWithTrailingShareMarker(
        counts: List<OcrTextBlock>,
        shareMarker: OcrTextBlock,
        screenSize: ScreenSize,
    ): OcrCountRailLayout? {
        if (counts.size != 2) return null
        val rail = counts + shareMarker
        if (!isAlignedCountColumn(rail, screenSize)) return null

        val firstGap = counts[1].bounds.centerY - counts[0].bounds.centerY
        val secondGap = shareMarker.bounds.centerY - counts[1].bounds.centerY
        val spacing = minOf(firstGap, secondGap)
        if (!isAllowedCountSlotSpacing(spacing, screenSize)) return null

        val firstToSecondRatio = firstGap / secondGap
        val secondToFirstRatio = secondGap / firstGap
        val firstCountCenterY = counts.first().bounds.centerY
        val commentCountCenterY: Float
        val precedingCountCenterY: Float
        when {
            // Slots 0,2,3: comment count is zero and its label is absent.
            firstToSecondRatio in MISSING_SLOT_DOUBLE_GAP_MIN_RATIO..MISSING_SLOT_DOUBLE_GAP_MAX_RATIO -> {
                precedingCountCenterY = firstCountCenterY
                commentCountCenterY = firstCountCenterY + spacing
            }
            // Slots 0,1,3: a later count is zero; the second visible number is comment.
            secondToFirstRatio in MISSING_SLOT_DOUBLE_GAP_MIN_RATIO..MISSING_SLOT_DOUBLE_GAP_MAX_RATIO -> {
                precedingCountCenterY = firstCountCenterY
                commentCountCenterY = counts[1].bounds.centerY
            }
            // Slots 1,2,3: like count is zero; the first visible number is comment.
            firstToSecondRatio <= OCR_COUNT_MAX_GAP_RATIO &&
                secondToFirstRatio <= OCR_COUNT_MAX_GAP_RATIO -> {
                precedingCountCenterY = firstCountCenterY - spacing
                commentCountCenterY = firstCountCenterY
            }
            else -> return null
        }
        return OcrCountRailLayout(
            firstCountCenterY = precedingCountCenterY,
            commentCountCenterY = commentCountCenterY,
            slotSpacing = spacing,
            countCenterX = counts.map { it.bounds.centerX }.average().toFloat(),
        )
    }

    private fun singleInteriorMissingCountRailLayout(
        rail: List<OcrTextBlock>,
        screenSize: ScreenSize,
    ): OcrCountRailLayout? {
        if (rail.size != MISSING_ONE_COUNT_RAIL_SLOTS || !isAlignedCountColumn(rail, screenSize)) {
            return null
        }
        val firstGap = rail[1].bounds.centerY - rail[0].bounds.centerY
        val secondGap = rail[2].bounds.centerY - rail[1].bounds.centerY
        val spacing = minOf(firstGap, secondGap)
        if (!isAllowedCountSlotSpacing(spacing, screenSize)) return null

        val firstToSecondRatio = firstGap / secondGap
        val secondToFirstRatio = secondGap / firstGap
        val firstCountCenterY = rail.first().bounds.centerY
        val commentCountCenterY = when {
            // Slots 0,2,3: comment count is zero and its label is absent.
            firstToSecondRatio in MISSING_SLOT_DOUBLE_GAP_MIN_RATIO..MISSING_SLOT_DOUBLE_GAP_MAX_RATIO ->
                firstCountCenterY + spacing
            // Slots 0,1,3: a later count is zero; the visible second label is comment.
            secondToFirstRatio in MISSING_SLOT_DOUBLE_GAP_MIN_RATIO..MISSING_SLOT_DOUBLE_GAP_MAX_RATIO ->
                rail[1].bounds.centerY
            else -> return null
        }
        return OcrCountRailLayout(
            firstCountCenterY = firstCountCenterY,
            commentCountCenterY = commentCountCenterY,
            slotSpacing = spacing,
            countCenterX = rail.map { it.bounds.centerX }.average().toFloat(),
        )
    }

    private fun evenlySpacedCountColumn(
        rail: List<OcrTextBlock>,
        screenSize: ScreenSize,
    ): Float? {
        if (rail.size != COMPLETE_ACTION_RAIL_SLOTS || !isAlignedCountColumn(rail, screenSize)) {
            return null
        }
        val gaps = rail.zipWithNext { upper, lower -> lower.bounds.centerY - upper.bounds.centerY }
        val minGap = gaps.minOrNull() ?: return null
        val maxGap = gaps.maxOrNull() ?: return null
        if (!isAllowedCountSlotSpacing(minGap, screenSize) ||
            !isAllowedCountSlotSpacing(maxGap, screenSize) ||
            maxGap / minGap > OCR_COUNT_MAX_GAP_RATIO
        ) {
            return null
        }
        return gaps.sorted()[gaps.size / 2].toFloat()
    }

    private fun isAlignedCountColumn(
        rail: List<OcrTextBlock>,
        screenSize: ScreenSize,
    ): Boolean {
        val xCenters = rail.map { it.bounds.normalized(screenSize).centerX }
        val xSpread = (xCenters.maxOrNull() ?: 0f) - (xCenters.minOrNull() ?: 0f)
        return xSpread <= OCR_COUNT_MAX_X_SPREAD_FRACTION
    }

    private fun isAllowedCountSlotSpacing(
        spacing: Float,
        screenSize: ScreenSize,
    ): Boolean {
        val normalizedSpacing = spacing / screenSize.height
        return normalizedSpacing in OCR_COUNT_MIN_GAP_FRACTION..OCR_COUNT_MAX_GAP_FRACTION
    }

    private fun deriveCommentIconBounds(
        layout: OcrCountRailLayout,
        screenSize: ScreenSize,
    ): ScreenBounds? {
        val targetCenterY = layout.commentCountCenterY -
            layout.slotSpacing * OCR_COMMENT_ICON_OFFSET_FRACTION
        if (targetCenterY <= layout.firstCountCenterY + layout.slotSpacing * OCR_MIN_ICON_GAP_FRACTION ||
            targetCenterY >= layout.commentCountCenterY - layout.slotSpacing * OCR_MIN_ICON_GAP_FRACTION
        ) {
            return null
        }

        val halfSide = (layout.slotSpacing * OCR_COMMENT_ICON_SIZE_FRACTION).toInt().coerceAtLeast(1)
        val centerX = layout.countCenterX.toInt()
        val centerY = targetCenterY.toInt()
        val bounds = ScreenBounds(
            left = (centerX - halfSide).coerceAtLeast(0),
            top = (centerY - halfSide).coerceAtLeast(0),
            right = (centerX + halfSide).coerceAtMost(screenSize.width),
            bottom = (centerY + halfSide).coerceAtMost(screenSize.height),
        )
        val normalized = bounds.normalized(screenSize)
        return bounds.takeIf {
            it.width > 0 &&
                it.height > 0 &&
                normalized.centerX >= OCR_COUNT_RAIL_LEFT &&
                normalized.centerY in ACTION_RAIL_TOP..ACTION_RAIL_BOTTOM &&
                normalized.width in OCR_COMMENT_ICON_MIN_WIDTH..OCR_COMMENT_ICON_MAX_WIDTH &&
                normalized.height in OCR_COMMENT_ICON_MIN_HEIGHT..OCR_COMMENT_ICON_MAX_HEIGHT
        }
    }

    private data class OcrCountRailLayout(
        val firstCountCenterY: Float,
        val commentCountCenterY: Float,
        val slotSpacing: Float,
        val countCenterX: Float,
    )

    private const val ACTION_RAIL_LEFT = 0.76f
    private const val ACTION_RAIL_TOP = 0.28f
    private const val ACTION_RAIL_BOTTOM = 0.90f
    private const val ACTION_ICON_MIN_WIDTH = 0.02f
    private const val ACTION_ICON_MAX_WIDTH = 0.22f
    private const val ACTION_ICON_MIN_HEIGHT = 0.01f
    private const val ACTION_ICON_MAX_HEIGHT = 0.10f
    private const val MIN_ACTION_RAIL_SLOTS = 3
    private const val RAIL_LAYER_CLUSTER_TOLERANCE_FRACTION = 0.0125f
    private const val ACTION_RAIL_MIN_SLOT_GAP_FRACTION = 0.035f
    private const val ACTION_RAIL_MAX_SLOT_GAP_FRACTION = 0.16f
    private const val ACTION_RAIL_MAX_GAP_RATIO = 1.45f
    private const val ACTION_RAIL_MAX_X_SPREAD_FRACTION = 0.08f
    // Count labels can sit slightly left of the icon centres. They are only geometric anchors.
    private const val OCR_COUNT_RAIL_LEFT = 0.74f
    private const val OCR_COUNT_RAIL_TOP = 0.42f
    private const val OCR_COUNT_RAIL_BOTTOM = 0.93f
    private const val OCR_COUNT_CLUSTER_TOLERANCE_FRACTION = 0.012f
    private const val COMPLETE_ACTION_RAIL_SLOTS = 4
    private const val MISSING_ONE_COUNT_RAIL_SLOTS = 3
    private const val OCR_COUNT_MIN_GAP_FRACTION = 0.035f
    private const val OCR_COUNT_MAX_GAP_FRACTION = 0.16f
    private const val OCR_COUNT_MAX_GAP_RATIO = 1.45f
    private const val OCR_COUNT_MAX_X_SPREAD_FRACTION = 0.08f
    private const val MISSING_SLOT_DOUBLE_GAP_MIN_RATIO = 1.65f
    private const val MISSING_SLOT_DOUBLE_GAP_MAX_RATIO = 2.35f
    private const val OCR_COMMENT_ICON_OFFSET_FRACTION = 0.40f
    private const val OCR_COMMENT_ICON_SIZE_FRACTION = 0.30f
    private const val OCR_MIN_ICON_GAP_FRACTION = 0.16f
    private const val OCR_COMMENT_ICON_MIN_WIDTH = 0.03f
    private const val OCR_COMMENT_ICON_MAX_WIDTH = 0.20f
    private const val OCR_COMMENT_ICON_MIN_HEIGHT = 0.015f
    private const val OCR_COMMENT_ICON_MAX_HEIGHT = 0.12f
    private const val TEMPLATE_MIN_CONFIDENCE = 0.86f
    private const val TEMPLATE_RAIL_LEFT = 0.72f
    private const val TEMPLATE_RAIL_TOP = 0.42f
    private const val TEMPLATE_RAIL_BOTTOM = 0.82f
    private const val TEMPLATE_ICON_MIN_WIDTH = 0.05f
    private const val TEMPLATE_ICON_MAX_WIDTH = 0.15f
    private const val TEMPLATE_ICON_MIN_HEIGHT = 0.02f
    private const val TEMPLATE_ICON_MAX_HEIGHT = 0.11f
    private const val DUAL_ANCHOR_SPANNED_SLOT_COUNT = 2f
    private const val DUAL_ANCHOR_HALF_SIZE_DIVISOR = 2
    private const val DUAL_ANCHOR_MIN_SIZE_RATIO = 0.70f
    private val engagementCountPattern = Regex("^\\d+(?:[.,]\\d+)?(?:万|亿|w|k|m)?$")
    private val trailingShareMarkerTerms = listOf("分享", "share")
}

/**
 * A visual candidate may only become actionable when two screenshots agree on its normalized
 * centre and size. This is intentionally separate from the matcher so a prior video's position
 * can never make a new screenshot confirmed by itself.
 */
internal object CommentIconTemplateStabilityPolicy {
    fun confirms(
        previous: CommentIconTemplateMatch?,
        current: CommentIconTemplateMatch?,
        screenSize: ScreenSize,
    ): Boolean {
        if (previous == null || current == null) return false
        val first = previous.bounds.normalized(screenSize)
        val second = current.bounds.normalized(screenSize)
        if (first.width == 0f || first.height == 0f || second.width == 0f || second.height == 0f) {
            return false
        }
        val centreToleranceX = maxOf(first.width, second.width) * MAX_CENTER_SHIFT_BY_ICON_SIZE
        val centreToleranceY = maxOf(first.height, second.height) * MAX_CENTER_SHIFT_BY_ICON_SIZE
        val widthRatio = minOf(first.width, second.width) / maxOf(first.width, second.width)
        val heightRatio = minOf(first.height, second.height) / maxOf(first.height, second.height)
        return kotlin.math.abs(first.centerX - second.centerX) <= centreToleranceX &&
            kotlin.math.abs(first.centerY - second.centerY) <= centreToleranceY &&
            widthRatio >= MIN_SIZE_RATIO &&
            heightRatio >= MIN_SIZE_RATIO
    }

    private const val MAX_CENTER_SHIFT_BY_ICON_SIZE = 0.60f
    private const val MIN_SIZE_RATIO = 0.70f
}

/** The two anchors must independently remain at the same normalized positions and scale. */
internal object ActionRailAnchorTemplateStabilityPolicy {
    fun confirms(
        previous: ActionRailAnchorTemplateMatch?,
        current: ActionRailAnchorTemplateMatch?,
        screenSize: ScreenSize,
    ): Boolean {
        if (previous == null || current == null) return false
        return isStable(previous.likeBounds, current.likeBounds, screenSize) &&
            isStable(previous.collectBounds, current.collectBounds, screenSize) &&
            hasStableInterAnchorGap(previous, current, screenSize)
    }

    private fun isStable(
        previous: ScreenBounds,
        current: ScreenBounds,
        screenSize: ScreenSize,
    ): Boolean {
        val first = previous.normalized(screenSize)
        val second = current.normalized(screenSize)
        if (first.width == 0f || first.height == 0f || second.width == 0f || second.height == 0f) {
            return false
        }
        val centerToleranceX = maxOf(first.width, second.width) * MAX_CENTER_SHIFT_BY_ICON_SIZE
        val centerToleranceY = maxOf(first.height, second.height) * MAX_CENTER_SHIFT_BY_ICON_SIZE
        val widthRatio = minOf(first.width, second.width) / maxOf(first.width, second.width)
        val heightRatio = minOf(first.height, second.height) / maxOf(first.height, second.height)
        return kotlin.math.abs(first.centerX - second.centerX) <= centerToleranceX &&
            kotlin.math.abs(first.centerY - second.centerY) <= centerToleranceY &&
            widthRatio >= MIN_SIZE_RATIO &&
            heightRatio >= MIN_SIZE_RATIO
    }

    private fun hasStableInterAnchorGap(
        previous: ActionRailAnchorTemplateMatch,
        current: ActionRailAnchorTemplateMatch,
        screenSize: ScreenSize,
    ): Boolean {
        val previousLike = previous.likeBounds.normalized(screenSize)
        val previousCollect = previous.collectBounds.normalized(screenSize)
        val currentLike = current.likeBounds.normalized(screenSize)
        val currentCollect = current.collectBounds.normalized(screenSize)
        val previousGap = previousCollect.centerY - previousLike.centerY
        val currentGap = currentCollect.centerY - currentLike.centerY
        if (previousGap <= 0f || currentGap <= 0f) return false
        return minOf(previousGap, currentGap) / maxOf(previousGap, currentGap) >= MIN_GAP_RATIO
    }

    private const val MAX_CENTER_SHIFT_BY_ICON_SIZE = 0.60f
    private const val MIN_SIZE_RATIO = 0.70f
    private const val MIN_GAP_RATIO = 0.70f
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
