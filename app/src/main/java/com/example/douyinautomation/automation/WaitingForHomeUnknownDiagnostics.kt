package com.example.douyinautomation.automation

import java.util.Locale

/**
 * Read-only diagnosis for a WAITING_FOR_HOME frame that stays UNKNOWN.
 * It never selects a click target or changes page classification.
 */
object WaitingForHomeUnknownDiagnostics {

    enum class Cause {
        MISSING_NODES,
        MISSING_SEMANTICS,
        OVERLAY_REJECTED,
        OCR_NOT_TRIGGERED,
        STABLE_GATE_NOT_PASSED,
    }

    data class Report(
        val cause: Cause,
        val nodeCount: Int,
        val visibleNodeCount: Int,
        val clickableNodeCount: Int,
        val classCounts: String,
        val homeHitCount: Int,
        val homeTerms: String,
        val homeSource: String,
        val searchEntryFound: Boolean,
        val searchEntryReasons: String,
        val searchEntryRegion: String,
        val searchEntryStructuralFound: Boolean,
        val searchEntryStructuralReasons: String,
        val searchEntryStructuralRegion: String,
        val overlayKind: String,
        val overlayMarker: String,
        val overlayRegion: String,
        val ocrBlockCount: Int,
        val ocrSkippedReason: String,
        val stableObservations: Int,
        val detectedPage: String,
        val normalizedPage: String,
    ) {
        fun toLogAttributes(): Map<String, Any?> = mapOf(
            "cause" to cause.name,
            "nodes" to nodeCount,
            "visible_nodes" to visibleNodeCount,
            "clickable_nodes" to clickableNodeCount,
            "class_counts" to classCounts,
            "home_hits" to homeHitCount,
            "home_terms" to homeTerms,
            "home_source" to homeSource,
            "search_entry_found" to searchEntryFound,
            "search_entry_reason" to searchEntryReasons,
            "search_entry_region" to searchEntryRegion,
            "search_structural_found" to searchEntryStructuralFound,
            "search_structural_reason" to searchEntryStructuralReasons,
            "search_structural_region" to searchEntryStructuralRegion,
            "overlay_kind" to overlayKind,
            "overlay_marker" to overlayMarker,
            "overlay_region" to overlayRegion,
            "ocr_blocks" to ocrBlockCount,
            "ocr_skipped" to ocrSkippedReason,
            "stable_observations" to stableObservations,
            "detected_page" to detectedPage,
            "normalized_page" to normalizedPage,
        )

        fun geometrySummary(): String = buildString {
            append("cause=").append(cause.name)
            append(" nodes=").append(nodeCount)
            append(" visible=").append(visibleNodeCount)
            append(" clickable=").append(clickableNodeCount)
            append(" classes=").append(classCounts)
            append(" home_hits=").append(homeHitCount)
            append(" overlay=").append(overlayKind)
            append(" ocr_blocks=").append(ocrBlockCount)
            append(" ocr_skipped=").append(ocrSkippedReason)
        }
    }

    fun analyze(
        context: ScreenContext?,
        selector: SelectorEngine,
        detectedPage: PageKind?,
        normalizedPage: PageKind?,
        stableObservations: Int,
        ocrSkippedReason: String,
        requiredStableObservations: Int = TuningConstants.NavigationFlow.INITIAL_READY_STABLE_OBSERVATIONS,
        readyPageKinds: Set<PageKind> = TuningConstants.NavigationFlow.INITIAL_READY_PAGE_KINDS,
    ): Report {
        if (context == null || context.nodes.isEmpty()) {
            return missingNodes(
                detectedPage = detectedPage,
                normalizedPage = normalizedPage,
                stableObservations = stableObservations,
                ocrSkippedReason = ocrSkippedReason,
                ocrBlockCount = context?.ocrBlocks?.size ?: 0,
            )
        }

        val visible = context.nodes.filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
        val clickable = visible.count { it.isClickable }
        val searchEntry = selector.select(context, DouyinSelectors.searchEntry)
        val searchStructural = selector.select(context, DouyinSelectors.searchEntryStructural)
        val liveOverlay = TransientOverlayDetector.find(context)
        val startupAd = TransientOverlayDetector.findStartupAd(context)
        val overlayMatch = liveOverlay ?: startupAd
        val overlayKind = when {
            liveOverlay != null -> "live_banner"
            startupAd != null -> "startup_ad"
            else -> "none"
        }
        val nodeHomeTerms = matchingHomeTerms(context.nodes.flatMap(NodeSnapshot::searchableText))
        val ocrHomeTerms = matchingHomeTerms(context.ocrBlocks.map(OcrTextBlock::text))
        val effectiveHomeTerms = if (nodeHomeTerms.size >= 2) {
            nodeHomeTerms
        } else {
            (nodeHomeTerms + ocrHomeTerms).distinct()
        }
        val homeSource = when {
            nodeHomeTerms.isNotEmpty() && ocrHomeTerms.isNotEmpty() && nodeHomeTerms.size < 2 -> "mixed"
            nodeHomeTerms.isNotEmpty() -> "accessibility"
            ocrHomeTerms.isNotEmpty() -> "ocr"
            else -> "none"
        }
        val normalizedKind = normalizedPage ?: PageKind.UNKNOWN
        val cause = decideCause(
            overlayKind = overlayKind,
            normalizedPage = normalizedKind,
            stableObservations = stableObservations,
            requiredStableObservations = requiredStableObservations,
            readyPageKinds = readyPageKinds,
            homeHitCount = effectiveHomeTerms.size,
            searchEntryFound = searchEntry.found,
            searchStructuralFound = searchStructural.found,
            ocrBlockCount = context.ocrBlocks.size,
            ocrSkippedReason = ocrSkippedReason,
        )

        return Report(
            cause = cause,
            nodeCount = context.nodes.size,
            visibleNodeCount = visible.size,
            clickableNodeCount = clickable,
            classCounts = classCounts(visible),
            homeHitCount = effectiveHomeTerms.size,
            homeTerms = effectiveHomeTerms.joinToString(separator = ",").ifBlank { "none" },
            homeSource = homeSource,
            searchEntryFound = searchEntry.found,
            searchEntryReasons = compactReasons(searchEntry.reasons),
            searchEntryRegion = regionOf(context, searchEntry.node),
            searchEntryStructuralFound = searchStructural.found,
            searchEntryStructuralReasons = compactReasons(searchStructural.reasons),
            searchEntryStructuralRegion = regionOf(context, searchStructural.node),
            overlayKind = overlayKind,
            overlayMarker = overlayMatch?.marker ?: "none",
            overlayRegion = overlayMatch?.let { formatRect(it.bounds.normalized(context.screenSize)) } ?: "none",
            ocrBlockCount = context.ocrBlocks.size,
            ocrSkippedReason = ocrSkippedReason,
            stableObservations = stableObservations,
            detectedPage = detectedPage?.name ?: "none",
            normalizedPage = normalizedKind.name,
        )
    }

    private fun missingNodes(
        detectedPage: PageKind?,
        normalizedPage: PageKind?,
        stableObservations: Int,
        ocrSkippedReason: String,
        ocrBlockCount: Int,
    ) = Report(
        cause = Cause.MISSING_NODES,
        nodeCount = 0,
        visibleNodeCount = 0,
        clickableNodeCount = 0,
        classCounts = "none",
        homeHitCount = 0,
        homeTerms = "none",
        homeSource = "none",
        searchEntryFound = false,
        searchEntryReasons = "no_node_tree",
        searchEntryRegion = "none",
        searchEntryStructuralFound = false,
        searchEntryStructuralReasons = "no_node_tree",
        searchEntryStructuralRegion = "none",
        overlayKind = "none",
        overlayMarker = "none",
        overlayRegion = "none",
        ocrBlockCount = ocrBlockCount,
        ocrSkippedReason = ocrSkippedReason,
        stableObservations = stableObservations,
        detectedPage = detectedPage?.name ?: "none",
        normalizedPage = normalizedPage?.name ?: "none",
    )

    private fun decideCause(
        overlayKind: String,
        normalizedPage: PageKind,
        stableObservations: Int,
        requiredStableObservations: Int,
        readyPageKinds: Set<PageKind>,
        homeHitCount: Int,
        searchEntryFound: Boolean,
        searchStructuralFound: Boolean,
        ocrBlockCount: Int,
        ocrSkippedReason: String,
    ): Cause {
        if (overlayKind != "none") return Cause.OVERLAY_REJECTED
        if (normalizedPage in readyPageKinds && stableObservations < requiredStableObservations) {
            return Cause.STABLE_GATE_NOT_PASSED
        }
        val ocrSkipped = ocrSkippedReason.isNotBlank() &&
            ocrSkippedReason != "none" &&
            ocrSkippedReason != "not_needed"
        if (homeHitCount < 2 &&
            !searchEntryFound &&
            !searchStructuralFound &&
            ocrBlockCount == 0 &&
            ocrSkipped
        ) {
            return Cause.OCR_NOT_TRIGGERED
        }
        return Cause.MISSING_SEMANTICS
    }

    private fun matchingHomeTerms(values: Iterable<String>): List<String> =
        values.flatMap { TextNormalizer.matchingTerms(it, DouyinLabels.home) }.distinct()

    private fun classCounts(nodes: List<NodeSnapshot>): String {
        if (nodes.isEmpty()) return "none"
        return nodes.asSequence()
            .map { simpleClassName(it.className) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(8)
            .joinToString(separator = ",") { "${it.key}:${it.value}" }
    }

    private fun simpleClassName(className: String?): String {
        val value = className?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
        return value ?: "unknown"
    }

    private fun compactReasons(reasons: List<String>): String =
        reasons.joinToString(separator = ";").ifBlank { "none" }

    private fun regionOf(context: ScreenContext, node: NodeSnapshot?): String =
        node?.let { formatRect(it.normalizedBounds(context.screenSize)) } ?: "none"

    private fun formatRect(rect: NormalizedRect): String =
        String.format(
            Locale.US,
            "%.2f,%.2f-%.2f,%.2f",
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
        )
}
