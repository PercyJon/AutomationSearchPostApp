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
    val hierarchyPath: List<Int> = emptyList(),
)

/**
 * A comment whose author can be opened from the comment-row avatar. The avatar is preferred over
 * text because the name and action controls can be exposed as neighboring accessibility nodes;
 * text remains metadata for identity/deduplication. OCR-only fixtures may retain the author bounds
 * for deterministic unit tests, but the real runtime refuses to tap without a verified avatar.
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
    /** Live-node path for the author/name; empty when the candidate came from OCR only. */
    val interactionHierarchyPath: List<Int> = emptyList(),
    /** The circular avatar that Douyin uses to open the commenter profile. */
    val avatarBounds: ScreenBounds? = null,
    /** Live-node path for [avatarBounds], when Accessibility exposes the avatar node. */
    val avatarHierarchyPath: List<Int> = emptyList(),
)

data class CommentCandidateExtraction(
    val candidates: List<CommentUserCandidate>,
    val fragments: List<CommentTextFragment>,
    /** Number of visible comment-body fragments that passed basic body/chrome validation. */
    val commentBodiesRead: Int = 0,
    /** Number of [commentBodiesRead] fragments that matched the frozen keyword rule. */
    val matchedCommentBodies: Int = 0,
    /**
     * The first left-side avatar below the comments-count header.  This is deliberately exposed
     * to the runtime so it can refuse a later candidate when the first visible comment row was
     * only partially exposed by Douyin's accessibility tree.
     */
    val firstVisibleCommentAvatar: ScreenBounds? = null,
) {
    val matchedCount: Int get() = candidates.size
    /** Matching comment rows that also formed a safe author/avatar candidate. */
    val actionableCandidateCount: Int get() = candidates.size
}

/**
 * Extracts comment/user candidates from a frozen screen snapshot. This is deliberately framework
 * free so fixtures can be replayed without opening Douyin. It does not click or infer a page
 * transition; the controller must perform those actions only after a verified candidate.
 */
object CommentCandidateExtractor {
    // A Douyin display name may legitimately be one character (for example "1").  Do not
    // discard that row before it can be paired with its avatar and comment body: the avatar +
    // two-line geometry checks below are the safety boundary, not a nickname-length heuristic.
    private const val MIN_TEXT_LENGTH = 1
    private const val MAX_AUTHOR_LENGTH = 40
    private const val HEADER_EXCLUSION_RATIO = 0.14f
    private const val MAX_AUTHOR_GAP_MULTIPLIER = 3f

    fun extract(
        context: ScreenContext,
        matchKeywords: Iterable<String>,
        matchMode: CommentKeywordMatchMode = CommentKeywordMatchMode.ANY,
    ): CommentCandidateExtraction {
        val fragments = collectFragments(context)
        val terms = CommentKeywordMatcher.normalizeKeywords(matchKeywords)
        // The location/check-in header and the centered “270条评论” count are part of the
        // comment sheet chrome, not user comments.  When the count exposes bounds, use its
        // bottom edge as a hard content boundary so a split/atypical location label cannot be
        // promoted to an author/comment pair by geometry alone.
        val commentContentTop = commentCountBottom(context)
        val locationCardBottom = locationCardBottom(context, commentContentTop)
        val headerAnchoredFirstAvatar = firstCommentAvatar(context, commentContentTop)
        // Keep the entire author/body pairing inside the content region.  Filtering only the
        // prospective body is insufficient: an upper-sheet location label can otherwise be
        // reused as the “author” of a nearby chrome tab (for example “AI 解析”), then paired
        // with the location pin as if it were an avatar.
        val contentFragments = fragments.filter { fragment ->
            fragment.bounds != ScreenBounds.EMPTY &&
                fragment.bounds.top >= (context.screenSize.height * HEADER_EXCLUSION_RATIO).toInt() &&
                (commentContentTop == null || fragment.bounds.top >= commentContentTop) &&
                (locationCardBottom == null || fragment.bounds.top >= locationCardBottom)
        }
        // Current Douyin comment rows expose their body through a stable `:id/content` node.
        // Prefer those bodies whenever they are available.  The generic text fallback below is
        // still needed for older/custom-rendered builds, but it cannot reliably distinguish a
        // standalone region label in the metadata line from a short comment.  In particular, a
        // metadata fragment immediately below row N used to make row N's body look like that
        // fragment's “author”, which removed the first three real rows and left only a later one.
        // This structural preference is intentionally scoped to body nodes that remain in the
        // comment-content region; it does not inspect location text or any action control.
        val structuredCommentBodies = context.nodes.asSequence()
            .filter { node ->
                node.isVisibleToUser &&
                    node.bounds != ScreenBounds.EMPTY &&
                    !node.text.isNullOrBlank() &&
                    node.bounds.top >= (context.screenSize.height * HEADER_EXCLUSION_RATIO).toInt() &&
                    (commentContentTop == null || node.bounds.top >= commentContentTop) &&
                    (locationCardBottom == null || node.bounds.top >= locationCardBottom) &&
                    isExplicitCommentBodyNode(node)
            }
            .toList()
        val commentBodies = contentFragments.filter { fragment ->
                (structuredCommentBodies.isEmpty() ||
                    structuredCommentBodies.any { body ->
                        fragment.source == CommentTextSource.ACCESSIBILITY &&
                            body.hierarchyPath == fragment.hierarchyPath &&
                            body.bounds == fragment.bounds
                    }) &&
                isLikelyCommentText(fragment.text) &&
                // A bare number at the far right belongs to the like/dislike action rail. It
                // must not participate in author pairing: otherwise a punctuation-free comment
                // immediately to its left can be mistaken for that number's author and be
                // removed from the candidate list (including a valid first commenter named “1”).
                !isRightActionCount(fragment, context.screenSize)
        }
        val commentFragments = commentBodies.filter { fragment ->
            CommentKeywordMatcher.matches(fragment.text, terms, matchMode)
        }
        // With no match keywords every visible text row is eligible initially. Remove fragments
        // that are themselves the author line for a nearby comment; otherwise an author name is
        // treated as a second comment and P4-C may visit the same person twice.
        val authorFragments = commentFragments.asSequence()
            .mapNotNull { comment -> findAuthor(comment, contentFragments, context.screenSize.width) }
            .toSet()
        val avatarSignalsAvailable = context.nodes.any { node ->
            isCommentCandidateAvatar(node, context, commentContentTop)
        }
        val candidates = commentFragments.mapNotNull { comment ->
            if (comment in authorFragments) return@mapNotNull null
            val author = findAuthor(comment, contentFragments, context.screenSize.width)
            val authorText = author?.text?.trim()?.takeIf(::isLikelyAuthorText)
            // When the accessibility tree exposes avatar images, require the avatar to be to the
            // left of the author/comment pair. This rejects the top location row even if OCR or a
            // custom text node made it look like a comment. If no image nodes are exposed (pure
            // OCR fixture), retain the author/comment geometry fallback.
            val avatar = findNearbyAvatar(author, comment, context, commentContentTop)
            if (avatarSignalsAvailable && avatar == null) {
                return@mapNotNull null
            }
            // Some Douyin versions expose the first row's avatar and comment body but virtualize
            // its nickname into an unlabeled custom node. That is still a verified comment row:
            // the left avatar + comment-body geometry is a stronger interaction contract than
            // nickname text. Keep it as an anonymous candidate rather than silently selecting a
            // later commenter. Without a real avatar (OCR-only data), retain the old requirement
            // for an identifiable author because the runtime may not tap OCR coordinates.
            if (authorText == null && avatar == null) return@mapNotNull null
            val identitySeed = authorText ?: "avatar-comment:${IdentityTextCanonicalizer.normalize(comment.text)}"
            val key = "comment-user:${IdentityTextCanonicalizer.normalize(identitySeed)}"
            CommentUserCandidate(
                authorText = authorText,
                commentText = comment.text.trim(),
                authorBounds = author?.bounds,
                commentBounds = comment.bounds,
                // The avatar is the explicit, stable profile-entry affordance. Keep the author
                // bounds only for OCR-only fixtures; the runtime requires a real avatar node.
                interactionBounds = avatar?.bounds ?: author?.bounds ?: return@mapNotNull null,
                matchedKeywords = terms.filter { term ->
                    IdentityTextCanonicalizer.normalize(comment.text).contains(term)
                },
                identityKey = key,
                source = comment.source,
                interactionHierarchyPath = avatar?.hierarchyPath ?: author?.hierarchyPath.orEmpty(),
                avatarBounds = avatar?.bounds,
                avatarHierarchyPath = avatar?.hierarchyPath.orEmpty(),
            )
        }
            // Accessibility traversal order reflects view hierarchy construction, not visual row
            // order.  On several Douyin builds that order is bottom-to-top, which made a bounded
            // one-user run start from the last visible comment.  The task contract is always the
            // first complete comment row below the sheet header, so order candidates explicitly
            // by their visual row before the runtime applies its configured limit.
            .sortedWith(
                compareBy<CommentUserCandidate> { candidate ->
                    minOf(
                        candidate.avatarBounds?.top ?: Int.MAX_VALUE,
                        candidate.authorBounds?.top ?: candidate.commentBounds.top,
                        candidate.commentBounds.top,
                    )
                }.thenBy { candidate ->
                    candidate.avatarBounds?.left ?: candidate.authorBounds?.left ?: candidate.commentBounds.left
                }.thenBy { candidate ->
                    if (candidate.source == CommentTextSource.ACCESSIBILITY) 0 else 1
                },
            )
            .distinctBy(CommentUserCandidate::identityKey)
        return CommentCandidateExtraction(
            candidates = candidates,
            fragments = fragments,
            commentBodiesRead = commentBodies.size,
            matchedCommentBodies = commentFragments.size,
            // The count header is sometimes virtualized. Once a candidate has passed the strict
            // avatar-row checks, its top-most avatar is the only safe fallback anchor; never
            // scan arbitrary images in the sheet because POI/location cards can contain their
            // own left-side image controls.
            firstVisibleCommentAvatar = headerAnchoredFirstAvatar ?: candidates.firstOrNull()?.avatarBounds,
        )
    }

    /**
     * The resource name is a structural body marker, not the displayed text.  It is deliberately
     * strict: if a future build removes it, extraction falls back to the existing avatar/text
     * geometry instead of guessing from an unrelated generic “content” label.
     */
    private fun isExplicitCommentBodyNode(node: NodeSnapshot): Boolean {
        val id = TextNormalizer.normalize(node.viewIdResourceName)
        return id.endsWith(":id/content") || id.endsWith("/content")
    }

    private fun isExplicitCommentTitleNode(node: NodeSnapshot): Boolean {
        val id = TextNormalizer.normalize(node.viewIdResourceName)
        return id.endsWith(":id/title") || id.endsWith("/title")
    }

    private fun collectFragments(context: ScreenContext): List<CommentTextFragment> {
        val nodeFragments = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds != ScreenBounds.EMPTY }
            .mapNotNull { node ->
                // Only visible text nodes are comment fragments. Content descriptions belong to
                // image/buttons (for example “赞0，未选中”, “踩，已选中”, and the location pin) and
                // must never be promoted to a user name or comment body.
                val value = node.text?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                CommentTextFragment(
                    text = value,
                    bounds = node.bounds,
                    source = CommentTextSource.ACCESSIBILITY,
                    hierarchyPath = node.hierarchyPath,
                )
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
        // A nickname and its body are adjacent within one row. The earlier unbounded
        // height-based allowance could stretch across the previous row's body to the next
        // commenter's nickname, then mark that previous body as an "author" and drop it. Cap
        // the gap to the actual two-line row spacing; this preserves long comment bodies while
        // preventing cross-row pairing.
        val maxGap = (comment.bounds.height * MAX_AUTHOR_GAP_MULTIPLIER).coerceIn(32f, 96f)
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
            !isCommentMetadata(value) &&
            !TextNormalizer.normalize(value).endsWith("作者")

    private fun isLikelyAuthorText(value: String): Boolean {
        val normalized = TextNormalizer.normalize(value)
        if (normalized.length !in MIN_TEXT_LENGTH..MAX_AUTHOR_LENGTH) return false
        if (isUiNoise(normalized)) return false
        if (isCommentMetadata(normalized)) return false
        if (normalized.contains("回复") || normalized.contains("点赞") ||
            normalized.contains("分钟前") || normalized.contains("小时前") ||
            normalized.contains("昨天") || normalized.contains("刚刚")
        ) return false
        if (normalized.endsWith("作者")) return false
        if (normalized.endsWith("评论") || normalized.endsWith("条回复")) return false
        // A comment is often a sentence; short names may contain punctuation, but not a full
        // sentence ending in common Chinese/ASCII punctuation.
        return normalized.none { it in "。！？!?；;" }
    }

    /**
     * Douyin exposes the number beside the heart as a standalone TextView (for example “2” or
     * “709”). A real comment body is always in the left text column, so position is the decisive
     * signal here rather than the number's value; numeric nicknames and comment bodies on the
     * normal text rail remain valid.
     */
    private fun isRightActionCount(fragment: CommentTextFragment, screenSize: ScreenSize): Boolean {
        val compact = TextNormalizer.normalize(fragment.text).replace(" ", "")
        return fragment.bounds.centerX >= (screenSize.width * 0.70f).toInt() &&
            compact.matches(Regex("\\d+(?:\\.\\d+)?(?:w|万)?"))
    }

    private fun isUiNoise(value: String): Boolean {
        val normalized = TextNormalizer.normalize(value)
        if (normalized.isBlank()) return true
        val compact = normalized.replace(" ", "")
        return normalized in UI_NOISE ||
            compact in COMMENT_SHEET_TAB_NOISE ||
            normalized.contains("写评论") ||
            normalized.contains("展开") ||
            normalized.contains("收起") ||
            normalized.contains("发私信") ||
            normalized.contains("已选中") ||
            normalized.contains("未选中") ||
            normalized == "赞" || normalized.startsWith("赞0") ||
            normalized == "踩" || normalized.startsWith("踩,") ||
            // Do not reject real comments merely because they mention “视频/作品/关注”.
            // These are UI-noise labels only when the whole short fragment is the label.
            normalized == "作品" ||
            normalized == "视频" ||
            normalized == "关注" ||
            normalized == "分享" ||
            normalized == "收藏"
    }

    private fun isCommentMetadata(value: String): Boolean {
        val normalized = TextNormalizer.normalize(value)
        if (normalized.contains("小时前") || normalized.contains("分钟前") ||
            normalized.contains("昨天") || normalized == "刚刚" ||
            normalized == "打卡" || normalized.endsWith("人打卡") ||
            // This is Douyin's own top-of-sheet discovery shortcut, not a commenter name or
            // body. Its suggested phrase is dynamic, so only the stable official label is used.
            normalized.startsWith("大家都在搜") ||
            normalized == "免费开放" || normalized.endsWith("人浏览") ||
            normalized.endsWith("人参与") ||
            normalized.matches(Regex("\\d+条评论")) ||
            normalized.contains("已选中") || normalized.contains("未选中") ||
            normalized == "赞" || normalized.startsWith("赞0") ||
            normalized == "踩" || normalized.startsWith("踩,") ||
            normalized == "点赞" || normalized == "不喜欢" ||
            normalized == "喜欢" || normalized == "回复" ||
            normalized.matches(Regex("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}.*"))
        ) return true
        // Douyin's location line is rendered as time · region. A normal comment may contain a
        // middle dot, but not together with the time metadata marker.
        return normalized.contains("·") &&
            (normalized.contains("前") || normalized.matches(Regex(".*\\d{1,2}:\\d{2}.*")))
    }

    private fun commentCountBottom(context: ScreenContext): Int? {
        fun markerBottom(text: String, bounds: ScreenBounds): Int? {
            if (bounds == ScreenBounds.EMPTY ||
                bounds.top > (context.screenSize.height * COMMENT_HEADER_MAX_TOP_RATIO).toInt()
            ) return null
            val compact = TextNormalizer.normalize(text).replace(" ", "")
            // Older sheets center “270条评论”; newer panels show the left tab as “评论 10”.
            // Both are stable chrome boundaries, but their geometry differs. Treating only the
            // former as a header left the location pin above the latter eligible for a false
            // avatar/text pairing after a list scroll.
            return when {
                COMMENT_COUNT_HEADER_PATTERN.matches(compact) &&
                    bounds.centerX in (context.screenSize.width * 0.18f)..(context.screenSize.width * 0.82f) -> {
                    bounds.bottom
                }
                COMMENT_TAB_COUNT_PATTERN.matches(compact) &&
                    bounds.left <= (context.screenSize.width * COMMENT_TAB_MAX_RIGHT_RATIO).toInt() -> {
                    bounds.bottom
                }
                else -> null
            }
        }

        val nodeBottoms = context.nodes.asSequence()
            .filter { it.isVisibleToUser }
            .flatMap { node ->
                node.searchableText().asSequence().mapNotNull { text -> markerBottom(text, node.bounds) }
            }
        val ocrBottoms = context.ocrBlocks.asSequence()
            .mapNotNull { block -> markerBottom(block.text, block.bounds) }
        return (nodeBottoms + ocrBottoms).maxOrNull()
    }

    /**
     * Detects the top location card from its leading location icon, never from a city, street or
     * venue name.  Place names are unbounded and can legitimately appear in real comments.  A
     * location icon is instead paired with text immediately to its right, then its short visual
     * card is excluded before candidate extraction.  The centered comment-count header, when
     * present, remains the stronger lower boundary for the whole sheet.
     */
    private fun locationCardBottom(
        context: ScreenContext,
        commentContentTop: Int?,
    ): Int? {
        // The centered count header is a verified hard boundary. Do not try to extend an icon
        // card across it: compact card rows can otherwise chain through the count into the first
        // real commenter. The icon-based branch below is specifically for builds that virtualize
        // that header.
        if (commentContentTop != null) return commentContentTop
        val visibleTextBounds = buildList {
            context.nodes.asSequence()
                .filter { node ->
                    node.isVisibleToUser && node.bounds != ScreenBounds.EMPTY && !node.text.isNullOrBlank()
                }
                .forEach { node -> add(node.bounds) }
            context.ocrBlocks.asSequence()
                .filter { block -> block.bounds != ScreenBounds.EMPTY && block.text.isNotBlank() }
                .forEach { block -> add(block.bounds) }
        }
        return context.nodes.asSequence()
            .filter { node -> isLocationIcon(node, context.screenSize, commentContentTop) }
            .mapNotNull { icon ->
                val nearbyText = visibleTextBounds.filter { bounds ->
                    isTextBesideLocationIcon(bounds, icon.bounds, context.screenSize)
                }
                // An opaque ImageView is treated as a location icon only when it forms the
                // icon-plus-text row promised by the UI contract. This prevents arbitrary small
                // decorative images from creating an exclusion region.
                if (nearbyText.isEmpty()) return@mapNotNull null
                locationCardBottomFrom(icon.bounds, nearbyText, visibleTextBounds, context.screenSize)
            }
            .maxOrNull()
    }

    private fun isLocationIcon(
        node: NodeSnapshot,
        screenSize: ScreenSize,
        commentContentTop: Int?,
    ): Boolean {
        if (!node.isVisibleToUser || node.bounds == ScreenBounds.EMPTY) return false
        if (commentContentTop != null && node.bounds.bottom > commentContentTop) return false
        // With a virtualized count header, only the upper sheet can host the location card. This
        // conservative cutoff prevents a small image inside a later comment from becoming an
        // icon anchor; it is preferable to leave an uncertain row untouched.
        if (commentContentTop == null && node.bounds.top > (screenSize.height * 0.55f).toInt()) return false
        val semantic = node.searchableText().joinToString(" ").let(TextNormalizer::normalize)
        val viewId = TextNormalizer.normalize(node.viewIdResourceName)
        val explicitLocationIcon = semantic.contains("位置") || semantic.contains("定位") ||
            semantic.contains("地址") || semantic.contains("location") || semantic.contains("place") ||
            semantic.contains("poi") || semantic.contains("map") ||
            viewId.contains("location") || viewId.contains("place") ||
            viewId.contains("poi") || viewId.contains("map")
        val className = TextNormalizer.normalize(node.className)
        val imageLike = className.contains("imageview") || className.contains("image") ||
            viewId.contains("icon")
        if (!imageLike && !explicitLocationIcon) return false
        // A generic small ImageView next to text is indistinguishable from a compact avatar. An
        // opaque but present resource id (as exposed by Douyin's location pin) or an explicit
        // location semantic is required before applying the conservative icon-card exclusion.
        if (!explicitLocationIcon && node.viewIdResourceName.isNullOrBlank()) return false
        val width = node.bounds.width
        val height = node.bounds.height
        if (width !in 20..96 || height !in 20..96) return false
        val ratio = width.toFloat() / height.toFloat()
        if (ratio !in 0.55f..1.45f) return false
        // The pin belongs to the same fixed left rail as avatars, but is smaller. In particular,
        // do not inspect right-side action icons at all.
        return node.bounds.centerX <= (screenSize.width * 0.16f).toInt()
    }

    private fun isTextBesideLocationIcon(
        text: ScreenBounds,
        icon: ScreenBounds,
        screenSize: ScreenSize,
    ): Boolean {
        val lineAllowance = icon.height.coerceAtLeast(24)
        return text.left >= icon.right - 8 &&
            text.left <= (screenSize.width * 0.94f).toInt() &&
            text.bottom >= icon.top - lineAllowance / 2 &&
            text.top <= icon.bottom + (lineAllowance * 3) / 4
    }

    private fun locationCardBottomFrom(
        icon: ScreenBounds,
        nearbyText: List<ScreenBounds>,
        allText: List<ScreenBounds>,
        screenSize: ScreenSize,
    ): Int {
        val lineHeight = maxOf(
            icon.height,
            nearbyText.maxOfOrNull(ScreenBounds::height) ?: 0,
        ).coerceAtLeast(24)
        var bottom = maxOf(icon.bottom, nearbyText.maxOf(ScreenBounds::bottom))
        // POI cards can add a short second/third line (rank, opening time, ticket information).
        // Grow only across immediate lines to the right of the icon and cap the expansion, so a
        // later real comment can never be swallowed merely because its rows happen to be dense.
        repeat(3) {
            val nextBottom = allText.asSequence()
                .filter { bounds ->
                    bounds.left >= icon.right - 8 &&
                        bounds.left <= (screenSize.width * 0.94f).toInt() &&
                        bounds.top >= icon.top - lineHeight / 2 &&
                        // Do not bridge a full row-height gap. The first real commenter can be
                        // close to a compact location header, and preserving that first row is
                        // more important than absorbing a distant optional POI field.
                        bounds.top <= bottom + (lineHeight * 3) / 4
                }
                .map(ScreenBounds::bottom)
                .maxOrNull()
                ?: return@repeat
            if (nextBottom <= bottom) return@repeat
            bottom = nextBottom
        }
        return bottom
    }

    private fun findNearbyAvatar(
        author: CommentTextFragment?,
        comment: CommentTextFragment,
        context: ScreenContext,
        commentContentTop: Int?,
    ): NodeSnapshot? {
        val textLeft = minOf(author?.bounds?.left ?: comment.bounds.left, comment.bounds.left)
        val rowTop = minOf(author?.bounds?.top ?: comment.bounds.top, comment.bounds.top)
        val rowBottom = maxOf(author?.bounds?.bottom ?: comment.bounds.bottom, comment.bounds.bottom)
        // A row avatar ends just before the text column.  The earlier 8%-of-screen allowance
        // was large enough to admit a right-side action image (or an image embedded in the
        // comment body) when it happened to be closer to the author text than the real avatar.
        // Keep this overlap deliberately tiny; a genuine avatar is always wholly on the left.
        val horizontalAllowance = (context.screenSize.width * 0.025f).toInt().coerceIn(12, 28)
        return context.nodes
            .asSequence()
            .filter { node -> isCommentCandidateAvatar(node, context, commentContentTop) }
            .filter { avatar ->
                isRowAvatar(
                    avatar,
                    textLeft,
                    rowTop,
                    rowBottom,
                    horizontalAllowance,
                    context.screenSize.width,
                )
            }
            .minByOrNull { avatar ->
                kotlin.math.abs(avatar.bounds.centerX - textLeft) +
                    kotlin.math.abs(avatar.bounds.centerY - (rowTop + rowBottom) / 2f)
            }
    }

    /** Finds the first avatar-shaped node below the verified centered “N条评论” header. */
    private fun firstCommentAvatar(context: ScreenContext, commentContentTop: Int?): ScreenBounds? {
        val contentTop = commentContentTop ?: return null
        val leftLimit = (context.screenSize.width * 0.30f).toInt()
        return context.nodes.asSequence()
            .filter(::isAvatarLikeNode)
            .filter { avatar ->
                avatar.bounds.top >= contentTop &&
                    avatar.bounds.centerX <= leftLimit
            }
            .sortedWith(
                compareBy<NodeSnapshot> { it.bounds.top }
                    .thenBy { it.bounds.left },
            )
            .map(NodeSnapshot::bounds)
            .firstOrNull()
    }

    /** True only when the candidate is attached to the same visual row as [anchor]. */
    fun belongsToAvatarRow(candidate: CommentUserCandidate, anchor: ScreenBounds): Boolean {
        val avatar = candidate.avatarBounds ?: return false
        val verticalTolerance = maxOf(anchor.height, avatar.height) * 0.75f
        val horizontalTolerance = maxOf(anchor.width, avatar.width) * 0.50f
        return kotlin.math.abs(avatar.centerY - anchor.centerY) <= verticalTolerance &&
            kotlin.math.abs(avatar.centerX - anchor.centerX) <= horizontalTolerance
    }

    /**
     * Proves that the leading visible row is structurally complete but its body does not match.
     *
     * Both title and body must be live Accessibility nodes on the avatar's row. This permits a
     * later matching commenter only when the leading row is safely known to be non-matching;
     * OCR-only text and incomplete/virtualized rows remain a hard stop.
     */
    fun hasVerifiedNonMatchingLeadingRow(
        context: ScreenContext,
        anchor: ScreenBounds,
        matchKeywords: Iterable<String>,
        matchMode: CommentKeywordMatchMode,
    ): Boolean {
        val terms = CommentKeywordMatcher.normalizeKeywords(matchKeywords)
        if (terms.isEmpty()) return false
        val rowHeight = anchor.height.coerceAtLeast(1)
        val rowLeft = anchor.right - rowHeight / 8
        val rowTop = anchor.top - rowHeight / 4
        val rowBottom = anchor.bottom + rowHeight
        fun belongsToLeadingRow(node: NodeSnapshot): Boolean =
            node.isVisibleToUser &&
                node.bounds != ScreenBounds.EMPTY &&
                node.bounds.left >= rowLeft &&
                node.bounds.top >= rowTop &&
                node.bounds.bottom <= rowBottom

        val hasTitle = context.nodes.any { node ->
            belongsToLeadingRow(node) &&
                isExplicitCommentTitleNode(node) &&
                !node.text.isNullOrBlank()
        }
        if (!hasTitle) return false
        val body = context.nodes.firstOrNull { node ->
            belongsToLeadingRow(node) &&
                isExplicitCommentBodyNode(node) &&
                !node.text.isNullOrBlank()
        } ?: return false
        return !CommentKeywordMatcher.matches(body.text.orEmpty(), terms, matchMode)
    }

    /** True when [anchor]'s visual row still carries visible text. Diagnostic-only. */
    fun hasRowText(context: ScreenContext, anchor: ScreenBounds): Boolean {
        val rowHeight = anchor.height.coerceAtLeast(1)
        val bandTop = anchor.top - rowHeight / 2
        val bandBottom = anchor.bottom + rowHeight / 2
        fun overlaps(bounds: ScreenBounds): Boolean =
            bounds.centerY >= bandTop && bounds.centerY <= bandBottom
        val hasNodeText = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds != ScreenBounds.EMPTY }
            .any { node -> !node.text.isNullOrBlank() && overlaps(node.bounds) }
        if (hasNodeText) return true
        return context.ocrBlocks.asSequence()
            .filter { it.bounds != ScreenBounds.EMPTY && it.text.isNotBlank() }
            .any { block -> overlaps(block.bounds) }
    }

    /**
     * Returns true only for Douyin's explicit video-author badge on [anchor]'s own row. Visible
     * text alone is deliberately insufficient: the comment sheet can place an official search
     * shortcut, a location header, or a partially virtualized first row near the same left rail.
     */
    fun hasVideoAuthorBadge(context: ScreenContext, anchor: ScreenBounds): Boolean {
        val rowHeight = anchor.height.coerceAtLeast(1)
        val bandTop = anchor.top - rowHeight / 2
        val bandBottom = anchor.bottom + rowHeight
        fun overlaps(bounds: ScreenBounds): Boolean =
            bounds.centerY >= bandTop && bounds.centerY <= bandBottom
        fun isAuthorBadge(text: String): Boolean {
            val normalized = TextNormalizer.normalize(text)
            return normalized == "作者" || normalized.endsWith("作者")
        }
        val hasNodeBadge = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds != ScreenBounds.EMPTY && overlaps(it.bounds) }
            .flatMap { it.searchableText().asSequence() }
            .any(::isAuthorBadge)
        if (hasNodeBadge) return true
        return context.ocrBlocks.asSequence()
            .filter { it.bounds != ScreenBounds.EMPTY && overlaps(it.bounds) }
            .any { block -> isAuthorBadge(block.text) }
    }

    /** Re-resolves the avatar from a fresh tree before an interaction. */
    fun resolveAvatarTarget(context: ScreenContext, candidate: CommentUserCandidate): NodeSnapshot? {
        val path = candidate.avatarHierarchyPath
        val expected = candidate.avatarBounds ?: return null
        val commentContentTop = commentCountBottom(context)
        val textLeft = minOf(
            candidate.authorBounds?.left ?: Int.MAX_VALUE,
            candidate.commentBounds.left,
        )
        val rowTop = minOf(
            candidate.authorBounds?.top ?: candidate.commentBounds.top,
            candidate.commentBounds.top,
        )
        val rowBottom = maxOf(
            candidate.authorBounds?.bottom ?: candidate.commentBounds.bottom,
            candidate.commentBounds.bottom,
        )
        if (path.isNotEmpty()) {
            context.nodes.firstOrNull { node ->
                node.hierarchyPath == path &&
                    isCommentCandidateAvatar(node, context, commentContentTop) &&
                    isRowAvatar(
                        node,
                        textLeft,
                        rowTop,
                        rowBottom,
                        avatarTextOverlap(expected),
                        context.screenSize.width,
                    )
            }?.let { return it }
        }
        return context.nodes.asSequence()
            .filter { node -> isCommentCandidateAvatar(node, context, commentContentTop) }
            .filter { node ->
                isRowAvatar(
                    node,
                    textLeft,
                    rowTop,
                    rowBottom,
                    avatarTextOverlap(expected),
                    context.screenSize.width,
                ) &&
                    kotlin.math.abs(node.bounds.centerX - expected.centerX) <= expected.width * 1.5f &&
                    kotlin.math.abs(node.bounds.centerY - expected.centerY) <= expected.height * 1.5f
            }
            .minByOrNull { node ->
                kotlin.math.abs(node.bounds.centerX - expected.centerX) +
                    kotlin.math.abs(node.bounds.centerY - expected.centerY)
            }
    }

    /**
     * Diagnostic snapshot of why an avatar re-resolution may fail. The runtime logs this on the
     * failure path so a stale-tree case (avatar was present at extraction but vanished from the
     * fresh accessibility tree) can be told apart from a missing-avatar case (avatarBounds null,
     * which is only possible on pure-OCR fixtures) on the device.
     */
    fun avatarTargetDiagnostics(context: ScreenContext, candidate: CommentUserCandidate): Map<String, Any?> {
        val expected = candidate.avatarBounds
        val pathNodes = if (candidate.avatarHierarchyPath.isNotEmpty()) {
            context.nodes.filter { it.hierarchyPath == candidate.avatarHierarchyPath }
        } else {
            emptyList()
        }
        val commentContentTop = commentCountBottom(context)
        val avatarLikeCount = context.nodes.count { node ->
            isCommentCandidateAvatar(node, context, commentContentTop)
        }
        val avatarLikeInRow = if (expected != null) {
            val textLeft = minOf(
                candidate.authorBounds?.left ?: Int.MAX_VALUE,
                candidate.commentBounds.left,
            )
            val rowTop = minOf(
                candidate.authorBounds?.top ?: candidate.commentBounds.top,
                candidate.commentBounds.top,
            )
            val rowBottom = maxOf(
                candidate.authorBounds?.bottom ?: candidate.commentBounds.bottom,
                candidate.commentBounds.bottom,
            )
            context.nodes.asSequence()
                .filter { node -> isCommentCandidateAvatar(node, context, commentContentTop) }
                .count { node ->
                    isRowAvatar(
                        node,
                        textLeft,
                        rowTop,
                        rowBottom,
                        avatarTextOverlap(expected),
                        context.screenSize.width,
                    )
                }
        } else {
            0
        }
        return mapOf(
            "avatar_bounds" to (expected?.toString() ?: "null"),
            "avatar_path" to candidate.avatarHierarchyPath.joinToString("/"),
            "avatar_path_len" to candidate.avatarHierarchyPath.size,
            "avatar_like_count" to avatarLikeCount,
            "path_node_present" to pathNodes.isNotEmpty(),
            "path_node_bounds" to (pathNodes.firstOrNull()?.bounds?.toString() ?: "null"),
            "avatar_like_in_row" to avatarLikeInRow,
            "author_bounds" to (candidate.authorBounds?.toString() ?: "null"),
            "comment_bounds" to candidate.commentBounds.toString(),
            "source" to candidate.source.name,
        )
    }

    /**
     * Counts distinct left-side avatar rows visible below the sheet header. The comment sheet
     * exposes one circular avatar per comment row, while a profile exposes only its single header
     * avatar and a video page exposes at most the author avatar. This is therefore a stable,
     * text-independent signature that the panel is still open even after its header and “回复”
     * markers have scrolled out of the accessibility tree.
     */
    fun commentAvatarRowCount(context: ScreenContext): Int {
        val leftLimit = (context.screenSize.width * 0.34f).toInt()
        val commentContentTop = commentCountBottom(context)
        val contentTop = commentContentTop ?: (context.screenSize.height * 0.14f).toInt()
        val centers = context.nodes.asSequence()
            .filter { node -> isCommentCandidateAvatar(node, context, commentContentTop) }
            .filter { node ->
                node.bounds.centerX <= leftLimit && node.bounds.top >= contentTop
            }
            .map { it.bounds.centerY }
            .toList()
            .sorted()
        if (centers.isEmpty()) return 0
        var rows = 1
        for (index in 1 until centers.size) {
            if (centers[index] - centers[index - 1] > AVATAR_ROW_BAND) rows += 1
        }
        return rows
    }

    /**
     * A comment-row avatar is the only safe click target for the comment flow.  Keep this
     * geometry deliberately strict: it must be a near-square image on the left of the author and
     * comment, aligned with that row.  This excludes the location pin, like/dislike controls,
     * reply controls, and the comment sheet's empty-state artwork.
     */
    private fun isRowAvatar(
        node: NodeSnapshot,
        textLeft: Int,
        rowTop: Int,
        rowBottom: Int,
        horizontalAllowance: Int,
        screenWidth: Int,
    ): Boolean {
        val bounds = node.bounds
        val rowHeight = (rowBottom - rowTop).coerceAtLeast(1)
        val verticalAllowance = maxOf(bounds.height * 0.55f, rowHeight * 0.9f)
        // An avatar is aligned with the nickname at the start of a comment row. It may extend
        // below the body, but it must never *start* after that text. This rejects a left-side
        // location/decorative image from a lower row being paired with a header-like text row
        // above it.
        val latestAvatarTop = rowTop + maxOf(bounds.height * 0.55f, rowHeight * 0.35f)
        // A commenter avatar always sits in the fixed left rail of the sheet. A right-side
        // near-square action node (such as the heart/like control) can be left of a separately
        // exposed count label, so relative-to-text geometry alone is insufficient.
        val leftAvatarRail = (screenWidth * 0.30f).toInt()
        return bounds.centerX <= leftAvatarRail &&
            bounds.left < textLeft &&
            bounds.right <= textLeft + horizontalAllowance &&
            bounds.top <= latestAvatarTop &&
            bounds.centerY >= rowTop - verticalAllowance &&
            bounds.centerY <= rowBottom + verticalAllowance &&
            bounds.width <= rowHeight * 2.1f &&
            bounds.height <= rowHeight * 2.1f
    }

    /** Fresh-tree relocation must retain the same narrow left-of-text geometry as extraction. */
    private fun avatarTextOverlap(expected: ScreenBounds): Int =
        (expected.width / 4).coerceIn(8, 28)

    private fun isAvatarLikeNode(node: NodeSnapshot): Boolean {
        if (!node.isVisibleToUser || node.bounds == ScreenBounds.EMPTY) return false
        val normalizedClass = TextNormalizer.normalize(node.className)
        val normalizedId = TextNormalizer.normalize(node.viewIdResourceName)
        val semanticLabels = node.searchableText()
            .joinToString(" ")
            .let(TextNormalizer::normalize)
        // The location pin in the sheet header is also an ImageView.  It must never satisfy
        // the avatar signal used to validate a comment row, even when the surrounding text is
        // split into nodes and cannot be recognized by the metadata text rules alone.
        if (semanticLabels.contains("位置") || semanticLabels.contains("定位") ||
            semanticLabels.contains("地址") || semanticLabels.contains("打卡") ||
            semanticLabels.contains("location") || semanticLabels.contains("place") ||
            semanticLabels.contains("poi") || semanticLabels.contains("map") ||
            normalizedId.contains("location") || normalizedId.contains("place") ||
            normalizedId.contains("poi") || normalizedId.contains("map")
        ) return false
        val imageLike = normalizedClass.contains("imageview") ||
            normalizedClass.contains("avatar") ||
            normalizedId.contains("avatar") ||
            normalizedId.contains("head")
        if (!imageLike) return false
        val width = node.bounds.width
        val height = node.bounds.height
        if (width !in 24..180 || height !in 24..180) return false
        val ratio = width.toFloat() / height.toFloat()
        return ratio in 0.65f..1.35f
    }

    /**
     * A location/search-card icon can look exactly like a compact avatar to accessibility. Once
     * the sheet header boundary is known, any such left-rail image that ends above it is chrome,
     * never a commenter. This is intentionally structural: no city, street, or venue wording is
     * used to make the decision.
     */
    private fun isCommentCandidateAvatar(
        node: NodeSnapshot,
        context: ScreenContext,
        commentContentTop: Int?,
    ): Boolean {
        if (!isAvatarLikeNode(node)) return false
        val contentTop = commentContentTop ?: return true
        return node.bounds.bottom > contentTop ||
            node.bounds.centerX > (context.screenSize.width * HEADER_ICON_LEFT_LIMIT_RATIO).toInt()
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
    private const val AVATAR_ROW_BAND = 60
    private const val COMMENT_HEADER_MAX_TOP_RATIO = 0.65f
    private const val COMMENT_TAB_MAX_RIGHT_RATIO = 0.56f
    private const val HEADER_ICON_LEFT_LIMIT_RATIO = 0.20f
    private val COMMENT_COUNT_HEADER_PATTERN = Regex("\\d+条评论")
    private val COMMENT_TAB_COUNT_PATTERN = Regex("评论\\d+")
    private val COMMENT_SHEET_TAB_NOISE = setOf("ai解析", "ai分析", "智能解析")

}

data class CommentSurfaceDetection(
    val isCommentSurface: Boolean,
    val confidence: Float,
    val reasons: List<String>,
)

/** Conservative comment-surface detector used by the future controller before reading rows. */
object CommentSurfaceDetector {
    private val markers = listOf("评论", "全部评论", "写评论", "条评论", "回复")
    private val countHeaderPattern = Regex("^\\d+(?:[.,]\\d+)?(?:万|亿)?\\s*条评论$")
    private val commentTabPattern = Regex("^评论\\s*\\d+$")

    fun detect(context: ScreenContext): CommentSurfaceDetection {
        val nodeTexts = context.nodeText().map(TextNormalizer::normalize)
        val ocrTexts = context.ocrText().map(TextNormalizer::normalize)
        val allTexts = nodeTexts + ocrTexts
        val nodeMarkers = nodeTexts.flatMap { text -> markers.filter(text::contains) }.distinct()
        val ocrMarkers = ocrTexts.flatMap { text -> markers.filter(text::contains) }.distinct()
        val bottomComposer = context.nodes.any { node ->
            node.isEditable && node.bounds.top >= context.screenSize.height * 0.68f
        } || ocrTexts.any { it.contains("写评论") }
        val repeatedReply = allTexts.count { it.contains("回复") } >= 2
        // A full-screen video can expose an inline “期待你的评论” prompt, a location card, and
        // several decorative left-side images. None of those proves that the bottom sheet is
        // open. A count/tab header is the stable sheet chrome; when the header has scrolled
        // away, require both several structured comment rows and their repeated reply affordance.
        val explicitCountHeader = allTexts.any(countHeaderPattern::matches)
        val commentsTabHeader = allTexts.any(commentTabPattern::matches)
        val fullCommentsHeader = allTexts.any { it == "全部评论" }
        // Some current panels render a second “AI解析” tab beside the comments tab. It is not an
        // action target for this runtime, but its presence together with composer/row evidence is
        // useful proof that the comment sheet is already open. Without this signal a recovery
        // pass can mistake the panel for a video and attempt to reopen it.
        val aiAnalysisTab = allTexts.any { text ->
            text.contains("ai解析") || text.contains("ai分析") || text.contains("智能解析")
        }
        // On this device, returning from a commenter profile can restore a fully visible sheet
        // whose accessibility tree temporarily omits the header, composer and avatar rows. OCR
        // still retains the sheet's left-side “评论” heading and several per-row “回复” labels.
        // Treat that geometry as sheet proof only when the heading is in the left sheet chrome
        // and at least two distinct reply rows lie beneath it; a video rail's right-side comment
        // label or caption text cannot satisfy this layout.
        val ocrSheetHeader = context.ocrBlocks.firstOrNull { block ->
            val text = TextNormalizer.normalize(block.text)
            val bounds = block.bounds.normalized(context.screenSize)
            text.startsWith("评论") &&
                bounds.left <= OCR_SHEET_HEADER_MAX_LEFT &&
                bounds.right <= OCR_SHEET_HEADER_MAX_RIGHT &&
                bounds.top in OCR_SHEET_HEADER_MIN_TOP..OCR_SHEET_HEADER_MAX_TOP
        }?.bounds?.normalized(context.screenSize)
        val ocrReplyRowCenters = ocrSheetHeader?.let { header ->
            context.ocrBlocks.asSequence()
                .filter { block -> TextNormalizer.normalize(block.text).contains("回复") }
                .map { block -> block.bounds.normalized(context.screenSize) }
                .filter { bounds ->
                    bounds.left in OCR_SHEET_REPLY_MIN_LEFT..OCR_SHEET_REPLY_MAX_LEFT &&
                        bounds.top >= header.bottom + OCR_SHEET_REPLY_MIN_HEADER_GAP &&
                        bounds.bottom <= OCR_SHEET_REPLY_MAX_BOTTOM
                }
                .map { bounds -> bounds.centerY }
                .sorted()
                .fold(mutableListOf<Float>()) { centers, centerY ->
                    val previous = centers.lastOrNull()
                    if (previous == null || centerY - previous >= OCR_SHEET_REPLY_ROW_MIN_GAP) {
                        centers += centerY
                    }
                    centers
                }
        }.orEmpty()
        val ocrSheetHeaderWithReplyRows = ocrSheetHeader != null &&
            ocrReplyRowCenters.size >= OCR_SHEET_MIN_REPLY_ROWS
        val structuredCommentRows = CommentCandidateExtractor.extract(context, emptyList()).candidates.size
        // A profile exposes a single header avatar and a video page at most the author avatar,
        // so three or more left-side avatar rows paired with real author/comment text are strong
        // evidence that the comment sheet is open even after its header has scrolled out of the
        // accessibility tree. Raw image counts alone are intentionally insufficient.
        val avatarRowCount = CommentCandidateExtractor.commentAvatarRowCount(context)
        val avatarRowEvidence = avatarRowCount >= 3 && structuredCommentRows >= 2
        val reasons = buildList {
            if (nodeMarkers.isNotEmpty()) add("node markers: ${nodeMarkers.joinToString()}")
            if (ocrMarkers.isNotEmpty()) add("OCR markers: ${ocrMarkers.joinToString()}")
            if (explicitCountHeader) add("comment count header")
            if (commentsTabHeader) add("comment tab header")
            if (fullCommentsHeader) add("full comments header")
            if (aiAnalysisTab) add("AI analysis tab")
            if (bottomComposer) add("bottom comment composer")
            if (repeatedReply) add("repeated reply markers")
            if (ocrSheetHeaderWithReplyRows) add("OCR sheet header with reply rows: ${ocrReplyRowCenters.size}")
            if (avatarRowEvidence) add("structured comment avatar rows: $avatarRowCount/$structuredCommentRows")
        }
        val isSurface = (explicitCountHeader || fullCommentsHeader) &&
            (bottomComposer || repeatedReply || avatarRowEvidence) ||
            commentsTabHeader && bottomComposer ||
            aiAnalysisTab && (bottomComposer || repeatedReply || avatarRowEvidence) ||
            ocrSheetHeaderWithReplyRows ||
            avatarRowEvidence && repeatedReply
        return CommentSurfaceDetection(
            isCommentSurface = isSurface,
            confidence = when {
                !isSurface -> 0.1f
                (explicitCountHeader || fullCommentsHeader) && bottomComposer -> 0.95f
                commentsTabHeader && bottomComposer -> 0.94f
                explicitCountHeader || fullCommentsHeader -> 0.88f
                ocrSheetHeaderWithReplyRows -> 0.84f
                else -> 0.84f
            },
            reasons = reasons.ifEmpty { listOf("No comment-surface signature matched") },
        )
    }

    private const val OCR_SHEET_HEADER_MAX_LEFT = 0.48f
    private const val OCR_SHEET_HEADER_MAX_RIGHT = 0.62f
    private const val OCR_SHEET_HEADER_MIN_TOP = 0.32f
    private const val OCR_SHEET_HEADER_MAX_TOP = 0.70f
    private const val OCR_SHEET_REPLY_MIN_LEFT = 0.14f
    private const val OCR_SHEET_REPLY_MAX_LEFT = 0.68f
    private const val OCR_SHEET_REPLY_MIN_HEADER_GAP = 0.04f
    private const val OCR_SHEET_REPLY_MAX_BOTTOM = 0.94f
    private const val OCR_SHEET_REPLY_ROW_MIN_GAP = 0.06f
    private const val OCR_SHEET_MIN_REPLY_ROWS = 2
}
