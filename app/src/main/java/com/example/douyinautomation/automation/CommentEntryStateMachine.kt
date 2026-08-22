package com.example.douyinautomation.automation

/** Surface-level actions that the future comment runner may dispatch after a verified state. */
enum class CommentEntryAction {
    NONE,
    /** The opened room must be exited before the live item can be swiped away. */
    EXIT_LIVE_ROOM,
    /** The visible live item is skipped without opening the room. */
    SWIPE_LIVE_ROOM,
    /** Select the profile's Works tab before looking for a video thumbnail. */
    OPEN_WORKS_TAB,
    /** Confirm the newest sort option after tapping the Works tab opened its sort menu. */
    DISMISS_WORKS_SORT,
    /** The profile has no accessible works (including a private account); return to the list. */
    SKIP_PROFILE,
    OPEN_FIRST_VIDEO,
    OPEN_COMMENTS,
    READ_COMMENTS,
    PAUSE_FOR_MANUAL_HANDOFF,
    STOP,
}

/** P4-A state for the bounded profile → first video → comment panel route. */
enum class CommentEntryStage {
    WAITING_FOR_PROFILE,
    WAITING_FOR_VIDEO,
    WAITING_FOR_COMMENTS,
    READY_TO_READ,
    PAUSED_FOR_MANUAL_HANDOFF,
    FAILED,
}

data class CommentEntryObservation(
    val page: PageKind,
    val hasFirstVideoTarget: Boolean = false,
    /** The semantically selected first-video node; its bounds are only a final gesture fallback. */
    val firstVideoTarget: NodeSnapshot? = null,
    /** Profile tab that exposes the user's video works. */
    val worksTabTarget: NodeSnapshot? = null,
    /** The transient sort-menu option shown after the Works tab is tapped. */
    val worksSortLatestTarget: NodeSnapshot? = null,
    val worksTabSelected: Boolean = false,
    /** True for private accounts or profiles that explicitly show an empty works state. */
    val profileHasNoWorks: Boolean = false,
    /** Applied only when selecting a profile thumbnail. */
    val skipPinnedVideos: Boolean = false,
    val hasVideoSurface: Boolean = false,
    val hasCommentEntry: Boolean = false,
    val commentButton: CommentButtonTarget? = null,
    val commentSurface: CommentSurfaceDetection? = null,
    val riskReason: String? = null,
) {
    val isCommentSurfaceReady: Boolean
        get() = commentSurface?.isCommentSurface == true
}

data class CommentEntryDecision(
    val stage: CommentEntryStage,
    val action: CommentEntryAction,
    val reason: String,
)

/**
 * Pure P4-A transition policy. Actions are emitted only after the preceding page/post-condition
 * is observed; callers must execute the action and inspect a fresh ScreenContext before asking
 * for the next decision. Risk, login, and unknown screens always pause for a person.
 */
class CommentEntryStateMachine {
    var stage: CommentEntryStage = CommentEntryStage.WAITING_FOR_PROFILE
        private set
    private var worksTabVisited = false
    /** Prevent a delayed pre-click profile snapshot from opening the same first video twice. */
    private var firstVideoOpenRequested = false

    fun observe(observation: CommentEntryObservation): CommentEntryDecision {
        observation.riskReason?.let { return pause("检测到风险或验证码：$it") }
        when (observation.page) {
            PageKind.LIVE_ROOM_SESSION -> return decision(CommentEntryAction.EXIT_LIVE_ROOM, "已进入直播间，准备退出")
            PageKind.LIVE_ROOM -> return decision(CommentEntryAction.SWIPE_LIVE_ROOM, "检测到直播入口，准备划走")
            PageKind.HUMAN_INTERVENTION -> return pause("检测到风险或验证码页面")
            PageKind.LOGIN -> return pause("抖音需要登录后才能继续")
            PageKind.OUTSIDE_TARGET -> return pause("当前前台不是抖音")
            // PageDetector V0 intentionally has no video/comment page kind yet. UNKNOWN is
            // accepted only when the dedicated structural detector has already supplied a
            // video surface or a verified comment panel; incidental unknown pages still pause.
            PageKind.UNKNOWN -> if (!observation.hasVideoSurface &&
                observation.commentSurface == null &&
                observation.worksSortLatestTarget == null
            ) {
                return pause("当前页面无法确认，暂不执行点击")
            }
            else -> Unit
        }

        return when (stage) {
            CommentEntryStage.WAITING_FOR_PROFILE -> {
                if (observation.page != PageKind.USER_PROFILE) {
                    decision(CommentEntryAction.NONE, "等待确认用户主页")
                } else if (observation.profileHasNoWorks) {
                    decision(CommentEntryAction.SKIP_PROFILE, "用户没有可处理的作品或账号为私密账号")
                } else if (
                    observation.worksTabTarget != null &&
                        !observation.worksTabSelected &&
                        !worksTabVisited
                ) {
                    worksTabVisited = true
                    stage = CommentEntryStage.WAITING_FOR_VIDEO
                    decision(CommentEntryAction.OPEN_WORKS_TAB, "先切换到作品标签")
                } else if (observation.hasFirstVideoTarget) {
                    stage = CommentEntryStage.WAITING_FOR_VIDEO
                    firstVideoOpenRequested = true
                    decision(CommentEntryAction.OPEN_FIRST_VIDEO, "已确认主页，准备打开第一个视频")
                } else {
                    decision(CommentEntryAction.NONE, "已在用户主页，但暂未确认第一个视频入口")
                }
            }

            CommentEntryStage.WAITING_FOR_VIDEO -> {
                if (observation.worksSortLatestTarget != null) {
                    decision(CommentEntryAction.DISMISS_WORKS_SORT, "作品标签已打开排序菜单，确认最新作品")
                } else if (observation.profileHasNoWorks) {
                    decision(CommentEntryAction.SKIP_PROFILE, "作品标签没有可处理的视频")
                } else if (firstVideoOpenRequested && observation.page == PageKind.USER_PROFILE) {
                    // The click is already in flight. Accessibility frequently delivers one or
                    // more old profile snapshots after it succeeds; tapping their thumbnail a
                    // second time can reopen/close a detail surface and strand the watchdog.
                    decision(CommentEntryAction.NONE, "首个视频已请求打开，等待视频页面切换")
                } else if (observation.hasFirstVideoTarget) {
                    firstVideoOpenRequested = true
                    decision(CommentEntryAction.OPEN_FIRST_VIDEO, "已确认作品标签，准备打开第一个视频")
                } else if (observation.hasVideoSurface && observation.hasCommentEntry) {
                    // 切换视频后，上一个视频的评论面板可能尚未关闭（或抖音在新视频上自动弹回
                    // 面板）。此时评论按钮是 toggle：再点一次会把已打开的面板关掉，让 12 秒
                    // 看门狗在“等待评论区”上超时。面板已就绪就直接读取，绝不再点评论按钮。
                    if (observation.isCommentSurfaceReady) {
                        stage = CommentEntryStage.READY_TO_READ
                        decision(CommentEntryAction.READ_COMMENTS, "评论区已打开，直接读取评论")
                    } else {
                        stage = CommentEntryStage.WAITING_FOR_COMMENTS
                        decision(CommentEntryAction.OPEN_COMMENTS, "已确认视频页面，准备打开评论区")
                    }
                } else {
                    decision(CommentEntryAction.NONE, "等待视频页面和评论入口稳定")
                }
            }

            CommentEntryStage.WAITING_FOR_COMMENTS -> {
                if (observation.isCommentSurfaceReady) {
                    stage = CommentEntryStage.READY_TO_READ
                    decision(CommentEntryAction.READ_COMMENTS, "已确认评论区，可读取评论")
                } else {
                    decision(CommentEntryAction.NONE, "等待评论区加载完成")
                }
            }

            CommentEntryStage.READY_TO_READ ->
                decision(CommentEntryAction.NONE, "评论区已就绪")

            CommentEntryStage.PAUSED_FOR_MANUAL_HANDOFF ->
                decision(CommentEntryAction.PAUSE_FOR_MANUAL_HANDOFF, "仍需人工处理后才能继续")

            CommentEntryStage.FAILED ->
                decision(CommentEntryAction.STOP, "评论入口状态机已失败")
        }
    }

    fun timeout(reason: String): CommentEntryDecision {
        stage = CommentEntryStage.FAILED
        return decision(CommentEntryAction.STOP, "评论流程超时：$reason")
    }

    fun reset() {
        stage = CommentEntryStage.WAITING_FOR_PROFILE
        worksTabVisited = false
        firstVideoOpenRequested = false
    }

    /**
     * Re-arms the route after the current video's comment sheet has been closed and the next
     * video has been selected. The next observation must still confirm both the video surface
     * and its comment entry before opening the panel again.
     */
    fun prepareNextVideo() {
        stage = CommentEntryStage.WAITING_FOR_VIDEO
        firstVideoOpenRequested = false
    }

    private fun pause(reason: String): CommentEntryDecision {
        stage = CommentEntryStage.PAUSED_FOR_MANUAL_HANDOFF
        return decision(CommentEntryAction.PAUSE_FOR_MANUAL_HANDOFF, reason)
    }

    private fun decision(action: CommentEntryAction, reason: String) = CommentEntryDecision(
        stage = stage,
        action = action,
        reason = reason,
    )
}

/**
 * Detects only the structural signals needed by P4-A. It does not choose a fixed pixel or click
 * an avatar. A clickable image-like node in the lower profile region is accepted as the first
 * video target only when the profile exposes a “作品/视频” anchor as well. Some Douyin builds
 * expose the thumbnail ImageView as non-clickable while its own bounds still receive the tap;
 * those nodes are accepted and GestureEngine will try the node action before its node bounds.
 */
object CommentEntrySignalDetector {
    private val videoMarkers = listOf("视频", "播放", "暂停", "作品")

    fun observe(
        context: ScreenContext,
        skipPinnedVideos: Boolean = false,
    ): CommentEntryObservation {
        val page = PageDetector().detect(context).kind
        val normalizedTexts = (context.nodeText() + context.ocrText()).map(TextNormalizer::normalize)
        val hasVideoMarker = normalizedTexts.any { text -> videoMarkers.any(text::contains) }
        val profileHasNoWorks = page == PageKind.USER_PROFILE && hasNoWorksSignal(normalizedTexts)
        val worksTabTarget = worksTabTarget(context)
        val worksSortLatestTarget = worksSortLatestTarget(context)
        val worksTabSelected = worksTabTarget?.isSelected ?: true
        val commentButton = VideoCommentButtonDetector.find(context)
        // Comment text in a caption is not permission to click. The state machine is only told
        // that a comment entry exists when the semantic/structural speech-bubble selector found
        // a verified action-rail node, or when its malformed bounds require the detector's
        // narrowly-scoped right-rail OCR fallback.
        val hasCommentEntry = commentButton != null
        val firstVideoTarget = firstVideoTarget(
            context,
            page,
            hasVideoMarker,
            worksTabSelected,
            skipPinnedVideos,
        )
        val hasFirstVideoTarget = firstVideoTarget != null
        // The action rail itself is stronger evidence than incidental caption/OCR text: the
        // detector only returns a semantic “评论” control on the right rail, or the second item
        // of a verified three-or-more-icon action rail. Some custom-rendered video pages expose
        // neither “播放” nor a full-width video node, so requiring those weaker visual markers
        // made a genuine video wait until its watchdog expired even though its comment button
        // was already safely actionable.
        //
        // Douyin renders the immersive video player opened from a profile's first video tile with
        // the same bottom-navigation labels as the home feed, so PageDetector can classify that
        // genuine video surface as HOME instead of UNKNOWN. The verified right-rail comment button
        // is authoritative in both cases: a home-classified screen carrying a semantic/structural
        // speech-bubble action is the opened video, and proceeding lets the WAITING_FOR_VIDEO →
        // OPEN_COMMENTS transition fire instead of idling into the “等待视频页面” watchdog. The
        // weaker visual marker (a wide node plus “播放/暂停/作品” text) is still restricted to
        // UNKNOWN, since a live home feed can also contain incidental wide nodes.
        val hasCommentRail = commentButton != null
        val hasWideVideoNode = hasVideoMarker &&
            context.nodes.any { it.isVisibleToUser && it.bounds.width >= MIN_VIDEO_EDGE }
        val hasVideoSurface = hasCommentRail && (page == PageKind.UNKNOWN || page == PageKind.HOME) ||
            (page == PageKind.UNKNOWN && hasWideVideoNode)
        return CommentEntryObservation(
            page = page,
            hasFirstVideoTarget = hasFirstVideoTarget,
            firstVideoTarget = firstVideoTarget,
            worksTabTarget = worksTabTarget,
            worksSortLatestTarget = worksSortLatestTarget,
            worksTabSelected = worksTabSelected,
            profileHasNoWorks = profileHasNoWorks,
            skipPinnedVideos = skipPinnedVideos,
            hasVideoSurface = hasVideoSurface,
            hasCommentEntry = hasCommentEntry,
            commentButton = commentButton,
            commentSurface = CommentSurfaceDetector.detect(context),
        )
    }

    /**
     * Selects the first video card from the profile using structure, not a fixed screen point.
     * Larger cards rank first and the lower profile content region excludes the avatar/header.
     */
    fun firstVideoTarget(context: ScreenContext): NodeSnapshot? {
        val page = PageDetector().detect(context).kind
        val normalizedTexts = (context.nodeText() + context.ocrText()).map(TextNormalizer::normalize)
        val hasVideoMarker = normalizedTexts.any { text -> videoMarkers.any(text::contains) }
        val worksTabSelected = worksTabTarget(context)?.isSelected ?: true
        return firstVideoTarget(context, page, hasVideoMarker, worksTabSelected, false)
    }

    private fun firstVideoTarget(
        context: ScreenContext,
        page: PageKind,
        hasVideoMarker: Boolean,
        worksTabSelected: Boolean,
        skipPinnedVideos: Boolean,
    ): NodeSnapshot? {
        if (page != PageKind.USER_PROFILE || !hasVideoMarker || !worksTabSelected) return null
        return context.nodes.asSequence()
            .filter { node ->
                val normalizedClass = TextNormalizer.normalize(node.className)
                val normalizedId = TextNormalizer.normalize(node.viewIdResourceName)
                val imageLike = normalizedClass.contains("imageview") ||
                    normalizedClass.contains("surfaceview") ||
                    normalizedClass.contains("textureview")
                // Current Douyin exposes a video thumbnail as a non-clickable ImageView below a
                // clickable `qb-` tile container.  During transitions the child can also report
                // isVisibleToUser=false even though its container is actionable.  Keep the
                // semantic/structural checks, but do not discard that valid pair merely because
                // the custom-rendered child has a transient visibility flag.
                val videoResource = normalizedId.contains(":id/cover") ||
                    normalizedId.endsWith("/cover") ||
                    normalizedId.contains("cover") ||
                    normalizedId.contains("qb-")
                val hasVisibleClickableContainer = context.nodes.any { container ->
                    container.isClickable &&
                        container.isVisibleToUser &&
                        isStrictAncestor(container.hierarchyPath, node.hierarchyPath) &&
                        container.normalizedBounds(context.screenSize).contains(
                            node.normalizedBounds(context.screenSize),
                        )
                }
                val normalizedBounds = node.normalizedBounds(context.screenSize)
                (imageLike || videoResource) &&
                    (node.isVisibleToUser || hasVisibleClickableContainer || videoResource) &&
                    node.bounds.width >= MIN_VIDEO_EDGE && node.bounds.height >= MIN_VIDEO_EDGE &&
                    normalizedBounds.top >= 0.24f
            }
            .map { node ->
                // Prefer the clickable tile container when the thumbnail itself is only a
                // visual child.  The live-node resolver can then perform a semantic click before
                // falling back to the container's current bounds.
                if (node.isClickable) {
                    node
                } else {
                    context.nodes.asSequence()
                        .filter { container ->
                            container.isClickable &&
                                isStrictAncestor(container.hierarchyPath, node.hierarchyPath) &&
                                container.normalizedBounds(context.screenSize).contains(
                                    node.normalizedBounds(context.screenSize),
                                )
                        }
                        .minByOrNull { it.bounds.width.toLong() * it.bounds.height.toLong() }
                        ?: node
                }
            }
            .distinctBy(NodeSnapshot::hierarchyPath)
            .filterNot { node -> skipPinnedVideos && isPinnedTile(node, context) }
            .sortedWith(
                compareBy<NodeSnapshot> { it.normalizedBounds(context.screenSize).top }
                    .thenByDescending { it.bounds.width.toLong() * it.bounds.height.toLong() },
            )
            .firstOrNull()
    }

    /**
     * Finds the Works tab by semantic text/content-description and promotes its nearest
     * clickable ancestor when the visible label is a non-clickable child. No tab coordinates are
     * stored; bounds are retained only on the selected live node for the action fallback.
     */
    private fun worksTabTarget(context: ScreenContext): NodeSnapshot? {
        // A profile with only one content tab is already on Works. Clicking it would open the
        // “最新/最热” sort popup on current Douyin builds, so only expose a switch target when a
        // second tab (商品/橱窗/直播/…) is present.
        if (!hasMultipleProfileTabs(context)) return null
        val labels = context.nodes.filter { node ->
            node.isVisibleToUser &&
                node.searchableText().any { TextNormalizer.normalize(it).contains("作品") }
        }
        val target = labels.asSequence()
            .mapNotNull { labeled ->
                if (labeled.isClickable) labeled
                else context.nodes
                    .asSequence()
                    .filter { candidate ->
                        candidate.isClickable &&
                            candidate.isVisibleToUser &&
                            isStrictAncestor(candidate.hierarchyPath, labeled.hierarchyPath)
                    }
                    .maxByOrNull { it.hierarchyPath.size }
            }
            .filter { it.normalizedBounds(context.screenSize).top in 0.35f..0.90f }
            .maxWithOrNull(
                compareBy<NodeSnapshot> { it.isSelected }
                    .thenByDescending { it.bounds.width.toLong() * it.bounds.height.toLong() },
            )
        return target?.let { selectedTarget ->
            // Accessibility exposes the selected state on current Douyin builds. Some ROM/app
            // combinations put the state only in the tab's spoken description (for example
            // “当前作品按最新发布排序”) or on a child label, so preserve those semantic signals
            // as an equivalent to the visible black label/underline + down-triangle state.
            selectedTarget.copy(
                isSelected = selectedTarget.isSelected ||
                    hasSelectedWorksSignal(selectedTarget, context),
            )
        }
    }

    private fun hasSelectedWorksSignal(
        tab: NodeSnapshot,
        context: ScreenContext,
    ): Boolean {
        fun selectedMarker(value: String): Boolean {
            val normalized = TextNormalizer.normalize(value)
            return normalized.contains("当前作品") ||
                normalized.contains("已选") ||
                normalized.contains("选中") ||
                normalized.contains("按最新发布排序") ||
                normalized.contains("按最热发布排序") ||
                normalized.contains("展开排序") ||
                normalized.contains("▼") ||
                normalized.contains("▾")
        }
        return tab.searchableText().any(::selectedMarker) || context.nodes.any { child ->
            isStrictAncestor(tab.hierarchyPath, child.hierarchyPath) &&
                child.searchableText().any(::selectedMarker)
        }
    }

    private fun hasMultipleProfileTabs(context: ScreenContext): Boolean = profileTabLabels(context)
        // ActionBar$Tab exposes the same label again through its android:id/text1 child. The
        // child is not a second tab; counting both was the reason a one-tab “作品 41” profile
        // incorrectly emitted OPEN_WORKS_TAB and opened Douyin's 最新/最热 menu.
        .distinctBy { profileTabIdentity(it, context) }
        .size >= 2

    /**
     * Returns one snapshot per real profile tab, not every descendant carrying the tab label.
     * Current Douyin builds expose an [androidx.appcompat.app.ActionBar$Tab] root and an
     * [android:id/text1] child with identical text/content-description. Prefer the tab root so
     * the eventual action has a clickable semantic target; collapse the child when a root is
     * present. The fallback still works with reduced fixture trees that only contain text nodes.
     */
    private fun profileTabLabels(context: ScreenContext): List<NodeSnapshot> {
        val candidates = context.nodes.filter { node ->
            if (!node.isVisibleToUser) return@filter false
            val bounds = node.normalizedBounds(context.screenSize)
            if (bounds.top !in 0.35f..0.90f) return@filter false
            node.searchableText().any(::isProfileTabText)
        }
        if (candidates.isEmpty()) return emptyList()

        val roots = candidates.filter(::isProfileTabRoot)
        val source = if (roots.isNotEmpty()) roots else candidates
        return source
            .filterNot { candidate ->
                // If a tab root and its label child are both candidates, keep the root only.
                candidates.any { possibleRoot ->
                    possibleRoot !== candidate &&
                        isProfileTabRoot(possibleRoot) &&
                        isStrictAncestor(possibleRoot.hierarchyPath, candidate.hierarchyPath) &&
                        sameProfileTabLabel(possibleRoot, candidate)
                }
            }
            .distinctBy { profileTabIdentity(it, context) }
    }

    private fun isProfileTabRoot(node: NodeSnapshot): Boolean {
        val normalizedClass = TextNormalizer.normalize(node.className)
        val normalizedId = TextNormalizer.normalize(node.viewIdResourceName)
        return normalizedClass.contains("actionbar\$tab") ||
            normalizedClass.contains("tablayout\$tab") ||
            (node.isClickable && node.childCount > 0 && normalizedId != "android:id/text1")
    }

    private fun isProfileTabText(raw: String): Boolean {
        val text = TextNormalizer.normalize(raw)
        return PROFILE_TAB_LABELS.any { label ->
            text == label ||
                text.startsWith("$label ") ||
                text.startsWith("$label,") ||
                text.startsWith("$label，")
        }
    }

    private fun sameProfileTabLabel(first: NodeSnapshot, second: NodeSnapshot): Boolean {
        val firstText = first.searchableText().firstOrNull(::isProfileTabText)
        val secondText = second.searchableText().firstOrNull(::isProfileTabText)
        if (firstText == null || secondText == null) return false
        val firstNormalized = TextNormalizer.normalize(firstText)
        val secondNormalized = TextNormalizer.normalize(secondText)
        return PROFILE_TAB_LABELS.any { label ->
            firstNormalized.startsWith(label) && secondNormalized.startsWith(label)
        }
    }

    private fun profileTabIdentity(node: NodeSnapshot, context: ScreenContext): String {
        val label = node.searchableText().firstOrNull(::isProfileTabText)
            ?.let(TextNormalizer::normalize)
            ?.substringBefore(',')
            ?.substringBefore('，')
            .orEmpty()
        val rect = node.normalizedBounds(context.screenSize)
        // Label + horizontal slot keeps two real tabs distinct even when a fixture omits paths.
        return "$label:${(rect.centerX * 100).toInt()}"
    }

    /**
     * Tapping the Works tab on current Douyin builds can open a small “最新/最热” menu even when
     * the tab is already selected. Treat the exact “最新” option as a semantic transient target;
     * this prevents the state machine from mistaking the menu's UNKNOWN page for a broken profile.
     */
    private fun worksSortLatestTarget(context: ScreenContext): NodeSnapshot? {
        val latestLabels = context.nodes.filter { node ->
            node.isVisibleToUser && node.searchableText().any { TextNormalizer.normalize(it) == "最新" }
        }
        return latestLabels.asSequence()
            .mapNotNull { labeled ->
                if (labeled.isClickable) labeled
                else context.nodes
                    .asSequence()
                    .filter { candidate ->
                        candidate.isClickable && candidate.isVisibleToUser &&
                            isStrictAncestor(candidate.hierarchyPath, labeled.hierarchyPath)
                    }
                    .maxByOrNull { it.hierarchyPath.size }
            }
            .filter { it.bounds != ScreenBounds.EMPTY }
            .maxByOrNull { it.bounds.top }
    }

    private fun hasNoWorksSignal(normalizedTexts: List<String>): Boolean = normalizedTexts.any { text ->
        text.contains("私密账号") ||
            text.contains("关注账号即可查看内容") ||
            text.contains("暂无作品") ||
            text.contains("暂无视频") ||
            text.contains("作品会展示在这里") ||
            Regex("作品\\s*0(?:$|[^0-9])").containsMatchIn(text)
    }

    private fun isPinnedTile(tile: NodeSnapshot, context: ScreenContext): Boolean {
        val marginX = (tile.bounds.width * 0.12f).toInt()
        val marginY = (tile.bounds.height * 0.18f).toInt()
        fun isWithinTile(bounds: ScreenBounds): Boolean =
            bounds.centerX.toInt() in (tile.bounds.left - marginX)..(tile.bounds.right + marginX) &&
                bounds.centerY.toInt() in (tile.bounds.top - marginY)..(tile.bounds.bottom + marginY)
        val nodeMarker = context.nodes.any { marker ->
            val text = marker.searchableText().joinToString(" ").let(TextNormalizer::normalize)
            marker.isVisibleToUser && text.contains("置顶") && isWithinTile(marker.bounds)
        }
        val ocrMarker = context.ocrBlocks.any { marker ->
            TextNormalizer.normalize(marker.text).contains("置顶") && isWithinTile(marker.bounds)
        }
        return nodeMarker || ocrMarker
    }

    private fun isStrictAncestor(ancestor: List<Int>, descendant: List<Int>): Boolean =
        ancestor.size < descendant.size && descendant.subList(0, ancestor.size) == ancestor

    private const val MIN_VIDEO_EDGE = 80
    private val PROFILE_TAB_LABELS = setOf("作品", "橱窗", "商品", "直播", "视频", "合集", "收藏", "喜欢")
}
