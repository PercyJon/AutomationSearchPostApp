package com.example.douyinautomation.automation

import android.view.accessibility.AccessibilityNodeInfo

/** A query over immutable node snapshots; labels can be Chinese, English, or a mixture of both. */
data class SelectorRequest(
    val name: String,
    val labels: List<String> = emptyList(),
    val contentDescriptionLabels: List<String> = emptyList(),
    val viewIdTokens: List<String> = emptyList(),
    val classNameTokens: List<String> = emptyList(),
    /** When true, at least one class token must match; tokens otherwise remain a ranking hint. */
    val requireClassNameToken: Boolean = false,
    val requireClickable: Boolean? = null,
    val requireEditable: Boolean? = null,
    val requireScrollable: Boolean? = null,
    val requireEnabled: Boolean = true,
    val requireVisible: Boolean = true,
    val preferredRegion: NormalizedRect? = null,
    val minimumScore: Float = 0.45f,
)

data class SelectionResult(
    val request: SelectorRequest,
    val node: NodeSnapshot?,
    val score: Float,
    val reasons: List<String>,
) {
    val found: Boolean get() = node != null
}

/**
 * Selector V0 ranks semantic matches first and uses normalised geometry only as a tie-breaker.
 * Coordinates are intentionally absent from the primary path; callers can use a selected node's
 * normalised bounds for a gesture fallback only after semantic selection succeeds.
 */
class SelectorEngine {

    fun select(context: ScreenContext, request: SelectorRequest): SelectionResult {
        val candidates = context.nodes.flatMap { node ->
            listOfNotNull(
                rank(node, context.screenSize, request),
                clickableAncestorCandidate(context, node, request),
            )
        }.sortedWith(
            compareByDescending<Candidate> { it.score }
                .thenByDescending { it.node.isClickable }
                .thenByDescending { it.node.isVisibleToUser }
                .thenBy { it.node.depth },
        )

        val best = candidates.firstOrNull { it.score >= request.minimumScore }
        return SelectionResult(
            request = request,
            node = best?.node,
            score = best?.score ?: 0f,
            reasons = best?.reasons ?: listOf("No node met ${request.name} minimum score ${request.minimumScore}"),
        )
    }

    /**
     * Some Android views expose their visible label as a non-clickable child of a clickable row or
     * Material button. Preserve semantic selection by choosing only that label's nearest clickable
     * ancestor; this is not a generic coordinate fallback.
     */
    private fun clickableAncestorCandidate(
        context: ScreenContext,
        labeledNode: NodeSnapshot,
        request: SelectorRequest,
    ): Candidate? {
        if (request.requireClickable != true || request.requireEditable == true) return null
        if (labeledNode.isClickable) return null

        val semanticMatch = rank(
            node = labeledNode,
            screenSize = context.screenSize,
            request = request.copy(requireClickable = null),
        ) ?: return null
        val ancestor = context.nodes
            .asSequence()
            .filter { candidate ->
                candidate.isClickable &&
                    (!request.requireVisible || candidate.isVisibleToUser) &&
                    (!request.requireEnabled || candidate.isEnabled) &&
                    candidate.hierarchyPath.isStrictAncestorOf(labeledNode.hierarchyPath)
            }
            .maxByOrNull { it.hierarchyPath.size }
            ?: return null

        return Candidate(
            node = ancestor,
            score = (semanticMatch.score * ANCESTOR_SCORE_FACTOR).coerceIn(0f, 1f),
            reasons = semanticMatch.reasons + "nearest clickable ancestor of ${labeledNode.stableId}",
        )
    }

    /**
     * Resolves an immutable snapshot path back to a live node. If the returned node is not [root],
     * the caller owns it and must recycle it after performing the Android action.
     */
    @Suppress("DEPRECATION")
    fun resolveLiveNode(
        root: AccessibilityNodeInfo?,
        hierarchyPath: List<Int>,
    ): AccessibilityNodeInfo? {
        val nonNullRoot = root ?: return null
        if (hierarchyPath.isEmpty()) return nonNullRoot

        var current: AccessibilityNodeInfo = nonNullRoot
        var currentIsOwned = false
        hierarchyPath.forEach { childIndex ->
            val child = current.getChild(childIndex)
            if (currentIsOwned) current.recycle()
            if (child == null) return null
            current = child
            currentIsOwned = true
        }
        return current
    }

    private fun rank(
        node: NodeSnapshot,
        screenSize: ScreenSize,
        request: SelectorRequest,
    ): Candidate? {
        if (request.requireVisible && !node.isVisibleToUser) return null
        if (request.requireEnabled && !node.isEnabled) return null
        if (request.requireClickable != null && node.isClickable != request.requireClickable) return null
        if (request.requireEditable != null && node.isEditable != request.requireEditable) return null
        if (request.requireScrollable != null && node.isScrollable != request.requireScrollable) return null
        if (request.requireClassNameToken &&
            (request.classNameTokens.isEmpty() ||
                TextNormalizer.matchingTerms(node.className, request.classNameTokens).isEmpty())
        ) {
            return null
        }

        var score = 0f
        val reasons = mutableListOf<String>()
        val textTerms = node.searchableText().flatMap { TextNormalizer.matchingTerms(it, request.labels) }.distinct()
        if (textTerms.isNotEmpty()) {
            score += 0.68f
            reasons += "label=${textTerms.joinToString()}"
        }

        val descriptionTerms = TextNormalizer.matchingTerms(node.contentDescription, request.contentDescriptionLabels)
        if (descriptionTerms.isNotEmpty()) {
            score += 0.62f
            reasons += "contentDescription=${descriptionTerms.joinToString()}"
        }

        val idTerms = TextNormalizer.matchingTerms(node.viewIdResourceName, request.viewIdTokens)
        if (idTerms.isNotEmpty()) {
            score += 0.42f
            reasons += "viewId=${idTerms.joinToString()}"
        }

        val classTerms = TextNormalizer.matchingTerms(node.className, request.classNameTokens)
        if (classTerms.isNotEmpty()) {
            score += 0.18f
            reasons += "class=${classTerms.joinToString()}"
        }

        // A constraint-only selector is valid, but deliberately carries a lower base score.
        if (score == 0f && (request.requireClickable != null || request.requireEditable != null)) {
            score = 0.30f
            reasons += "structural constraint"
        }

        request.preferredRegion?.let { region ->
            val nodeBounds = node.normalizedBounds(screenSize)
            if (region.contains(nodeBounds)) {
                score += 0.16f
                reasons += "preferred normalised region"
            } else {
                score -= 0.08f
                reasons += "outside preferred normalised region"
            }
        }

        if (node.isClickable) score += 0.04f
        return Candidate(
            node = node,
            score = score.coerceIn(0f, 1f),
            reasons = reasons,
        )
    }

    private data class Candidate(
        val node: NodeSnapshot,
        val score: Float,
        val reasons: List<String>,
    )

    private fun List<Int>.isStrictAncestorOf(descendant: List<Int>): Boolean =
        size < descendant.size && indices.all { index -> this[index] == descendant[index] }

    private companion object {
        const val ANCESTOR_SCORE_FACTOR = 0.94f
    }
}

/** Common V0 selectors for the M0 navigation chain. */
object DouyinSelectors {
    // Operator-confirmed location of the home-page magnifying glass, expressed independently of
    // resolution. It is used only after semantic/node selection cannot find the icon.
    val searchEntryNormalizedFallback = NormalizedPoint(0.92f, 0.07f)
    // Search-entry pages expose the top-right "搜索" label as a non-clickable TextView on some
    // builds; its bounds are still a safe semantic-region fallback for the button action.
    val searchSubmitNormalizedFallback = NormalizedPoint(0.88f, 0.07f)

    val searchEntry = SelectorRequest(
        name = "search-entry",
        labels = DouyinLabels.search,
        contentDescriptionLabels = DouyinLabels.search,
        viewIdTokens = listOf("search"),
        // Douyin's home-page magnifying glass is in the upper-right corner. This is only a
        // normalized-region tie-breaker/fallback when the icon exposes no semantic label.
        classNameTokens = listOf("ImageView", "Button"),
        requireClickable = true,
        preferredRegion = NormalizedRect(0.76f, 0f, 1f, 0.20f),
        minimumScore = 0.30f,
    )

    val searchInput = SelectorRequest(
        name = "search-input",
        labels = DouyinLabels.search,
        contentDescriptionLabels = DouyinLabels.search,
        viewIdTokens = listOf("search", "input", "edit"),
        requireEditable = true,
        preferredRegion = NormalizedRect(0f, 0f, 1f, 0.32f),
        minimumScore = 0.30f,
    )

    val userTab = SelectorRequest(
        name = "user-tab",
        labels = DouyinLabels.users,
        requireClickable = true,
        // Douyin's text child can report isVisibleToUser=false even when its clickable tab parent
        // is fully inside the HorizontalScrollView viewport. The controller applies the stricter
        // viewport-bound check after selection, so do not discard that semantic parent here.
        requireVisible = false,
        classNameTokens = listOf("TextView", "Button"),
        // The visible tab strip ends before the trailing grid/filter controls on current builds.
        // Keeping the right edge below that control area prevents an off-screen tab snapshot from
        // being mistaken for the User category.
        preferredRegion = NormalizedRect(0f, 0.05f, 0.84f, 0.25f),
        // A structural clickable node without a user label must never satisfy this selector.
        minimumScore = 0.70f,
    )

    /**
     * Some Douyin builds place the User category outside the initially visible horizontal tab
     * strip. This selector lets the controller ask the accessibility tree to reveal it before
     * falling back to a normalized horizontal swipe.
     */
    val searchResultTabStrip = SelectorRequest(
        name = "search-result-tab-strip",
        classNameTokens = listOf("HorizontalScrollView", "RecyclerView", "ViewPager", "ScrollView"),
        requireScrollable = true,
        preferredRegion = NormalizedRect(0f, 0.04f, 1f, 0.28f),
        minimumScore = 0.30f,
    )

    val userResult = SelectorRequest(
        name = "user-result",
        labels = listOf("粉丝", "followers"),
        requireClickable = true,
        preferredRegion = NormalizedRect(0f, 0.15f, 1f, 0.95f),
        minimumScore = 0.40f,
    )

    val privateMessageEntry = SelectorRequest(
        name = "private-message-entry",
        labels = listOf("发私信", "private message", "message"),
        contentDescriptionLabels = DouyinLabels.privateMessage,
        requireClickable = true,
        preferredRegion = NormalizedRect(0f, 0.10f, 1f, 0.82f),
    )

    val messageInput = SelectorRequest(
        name = "message-input",
        labels = DouyinLabels.messageInput,
        requireEditable = true,
        preferredRegion = NormalizedRect(0f, 0.72f, 1f, 1f),
        minimumScore = 0.35f,
    )

    val messageSendAction = SelectorRequest(
        name = "message-send-action",
        labels = DouyinLabels.send,
        contentDescriptionLabels = DouyinLabels.send,
        requireClickable = true,
        preferredRegion = NormalizedRect(0.60f, 0.70f, 1f, 1f),
        minimumScore = 0.35f,
    )

    val profileFollowAction = SelectorRequest(
        name = "profile-follow-action",
        labels = DouyinLabels.follow,
        contentDescriptionLabels = DouyinLabels.follow,
        requireClickable = true,
        preferredRegion = NormalizedRect(0f, 0.28f, 0.90f, 0.60f),
        minimumScore = 0.45f,
    )
}
