package com.example.douyinautomation.automation

/**
 * Resolves the full display name from a verified profile header.
 *
 * Accessibility text is intentionally tried first because it is much faster and more stable
 * than starting OCR. OCR candidates are accepted only from the small profile-header crop and are
 * used when the profile tree is custom-rendered or exposes the same clipped list-row label.
 */
object ProfileDisplayNameResolver {

    fun fromAccessibility(
        context: ScreenContext,
        previousName: String?,
    ): String? = resolve(
        candidates = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { it.bounds.top <= context.screenSize.height * PROFILE_HEADER_BOTTOM_RATIO }
            .filter { it.bounds.bottom >= context.screenSize.height * PROFILE_HEADER_TOP_RATIO }
            .flatMap { node ->
                profileNodeText(node).asSequence().map { value ->
                    Candidate(value = value, bounds = node.bounds)
                }
            },
        previousName = previousName,
        anchor = findAnchor(context),
        screenSize = context.screenSize,
    )

    fun fromOcr(
        context: ScreenContext,
        previousName: String?,
    ): String? = resolve(
        candidates = context.ocrBlocks.asSequence()
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { it.bounds.top <= context.screenSize.height * PROFILE_HEADER_BOTTOM_RATIO }
            .filter { it.bounds.bottom >= context.screenSize.height * PROFILE_HEADER_TOP_RATIO }
            .map { block ->
                Candidate(value = block.text, bounds = block.bounds)
            },
        previousName = previousName,
        anchor = findAnchor(context),
        screenSize = context.screenSize,
    )

    private fun resolve(
        candidates: Sequence<Candidate>,
        previousName: String?,
        anchor: ProfileNameAnchor?,
        screenSize: ScreenSize,
    ): String? {
        val filtered = candidates
            .mapNotNull { candidate ->
                sanitizeCandidate(candidate.value)?.let { value -> candidate.copy(value = value) }
            }
            .filter { it.value.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH }
            .filterNot { isNoise(it.value) }
            .filterNot { isTruncated(it.value) }
            .distinctBy { normalize(it.value) }
            .toList()
        if (filtered.isEmpty()) return null

        // Douyin places a prominent top-right “求更新”/refresh pill in the same broad header
        // region as the profile. Prefer the candidate geometrically tied to the avatar or to the
        // account-label line; this prevents a toolbar action from becoming the saved user name.
        val anchored = anchor?.let { profileNameCandidates(filtered, it) }.orEmpty()
        // If neither semantic anchor is available, use the largest readable text on the avatar's
        // right side as a conservative fallback. This handles profiles whose second line is a
        // company name rather than “店铺账号/抖音号”, while explicitly excluding toolbar labels.
        val preferred = anchored.ifEmpty {
            largestProfileNameCandidate(filtered, screenSize)?.let(::listOf) ?: filtered
        }

        val previous = previousName?.takeIf(String::isNotBlank)?.let(::normalize)
        val matching = previous?.let { value ->
            preferred.filter { candidate ->
                val normalized = normalize(candidate.value)
                normalized.startsWith(value) || value.startsWith(normalized)
            }
        }.orEmpty()
        val pool = matching.ifEmpty { preferred }

        // The profile title is normally the first matching text in the header. When two nodes
        // share the same top edge, prefer the longer value because the list row may have been
        // clipped while the profile header contains the complete name.
        return pool.sortedWith(
            compareBy<Candidate> { it.bounds.top }
                .thenByDescending { it.bounds.height }
                .thenByDescending { normalize(it.value).length },
        ).firstOrNull()?.value
    }

    private fun largestProfileNameCandidate(
        candidates: List<Candidate>,
        screenSize: ScreenSize,
    ): Candidate? = candidates
        .filter { candidate ->
            candidate.bounds.centerY >= screenSize.height * FALLBACK_MIN_CENTER_Y_RATIO &&
                candidate.bounds.centerX >= screenSize.width * FALLBACK_MIN_CENTER_X_RATIO &&
                candidate.bounds.centerX <= screenSize.width * FALLBACK_MAX_CENTER_X_RATIO
        }
        .maxWithOrNull(
            compareBy<Candidate> { it.bounds.height }
                .thenBy { it.bounds.width }
                .thenByDescending { it.bounds.centerY },
        )

    private fun profileNameCandidates(
        candidates: List<Candidate>,
        anchor: ProfileNameAnchor,
    ): List<Candidate> = candidates.filter { candidate ->
        val bounds = candidate.bounds
        val avatar = anchor.avatarBounds
        val avatarMatch = avatar != null &&
            bounds.left >= avatar.right - (avatar.width * AVATAR_RIGHT_OVERLAP_RATIO).toInt() &&
            bounds.centerY >= avatar.top + (avatar.height * AVATAR_VERTICAL_TOP_RATIO) &&
            bounds.centerY <= avatar.bottom - (avatar.height * AVATAR_VERTICAL_BOTTOM_RATIO)
        val account = anchor.accountBounds
        val accountMatch = account != null &&
            bounds.bottom <= account.top + ACCOUNT_LINE_MAX_OVERLAP_PX &&
            bounds.bottom >= account.top - (account.height * ACCOUNT_LINE_MAX_GAP_RATIO).toInt() &&
            bounds.right >= account.left - (account.width * ACCOUNT_HORIZONTAL_OVERLAP_RATIO).toInt() &&
            bounds.left <= account.right + (account.width * ACCOUNT_HORIZONTAL_OVERLAP_RATIO).toInt()
        avatarMatch || accountMatch
    }

    /** Finds the two stable profile-header anchors available in the accessibility/OCR context. */
    private fun findAnchor(context: ScreenContext): ProfileNameAnchor? {
        val avatarBounds = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { it.bounds.left <= context.screenSize.width * AVATAR_MAX_LEFT_RATIO }
            .filter { it.bounds.top <= context.screenSize.height * PROFILE_HEADER_BOTTOM_RATIO }
            .filter { it.bounds.width >= it.bounds.height * 0.75f && it.bounds.height >= it.bounds.width * 0.75f }
            .filter { node ->
                node.contentDescription.orEmpty().contains("头像") ||
                    node.contentDescription.orEmpty().contains("用户图片")
            }
            .map { it.bounds }
            .minByOrNull { it.top }

        val accountBounds = (context.nodes.asSequence()
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .mapNotNull { node ->
                val value = profileNodeText(node).firstOrNull(::isAccountLabel) ?: return@mapNotNull null
                node.bounds to value
            } + context.ocrBlocks.asSequence()
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .mapNotNull { block ->
                block.bounds to block.text
            })
            .filter { (_, value) -> isAccountLabel(value) }
            .map { (bounds, _) -> bounds }
            .minByOrNull { it.top }

        return if (avatarBounds != null || accountBounds != null) {
            ProfileNameAnchor(avatarBounds, accountBounds)
        } else {
            null
        }
    }

    private fun isAccountLabel(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.contains("店铺账号") ||
            normalized.contains("商家认证账号") ||
            normalized.startsWith("抖音号")
    }

    /**
     * Removes UI text accidentally concatenated to a profile title by Douyin's custom view.
     * This is deliberately conservative: only known action/menu suffixes are stripped; ordinary
     * punctuation and words that may be part of a real account name are retained.
     */
    fun sanitizeCandidate(raw: String): String? {
        var value = raw.trim().replace(WHITESPACE, " ")
        if (value.isBlank()) return null
        UI_SUFFIX_MARKERS.forEach { marker ->
            val index = value.indexOf(marker, startIndex = 1, ignoreCase = true)
            if (index > 1) {
                value = value.substring(0, index).trimEnd(' ', '，', ',', '|', '·')
            }
        }
        // OCR can merge the title and the account-label line into one block. Keep the title part
        // and let the account label serve only as a geometric anchor.
        ACCOUNT_LABEL_MARKERS.forEach { marker ->
            val index = value.indexOf(marker, startIndex = 1, ignoreCase = true)
            if (index > 1) {
                value = value.substring(0, index).trimEnd(' ', '，', ',', '|', '·', '\n')
            }
        }
        value = value
            .replace(Regex("\\s*[（(]?[vV][)）]?\\s*(店铺账号|商家认证账号)\\s*$"), "")
            .trim()
        return value.takeIf(String::isNotBlank)?.let(ProfileNameCorrection::correct)
    }

    fun equivalent(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false
        return normalize(first) == normalize(second)
    }

    /** Shared control-noise predicate used by the direct-message name fallback. */
    fun isNoiseCandidate(value: String): Boolean = isNoise(value)

    private fun isNoise(value: String): Boolean {
        val normalized = normalize(value).lowercase()
        if (
            normalized.startsWith("android.") ||
            normalized.startsWith("com.") ||
            normalized.contains("textview") ||
            normalized.contains(":id/") ||
            normalized.contains("resource") ||
            normalized.contains('/')
        ) return true
        return normalized == "视频" ||
            NOISE_MARKERS.any(normalized::contains) ||
            value.all(Char::isDigit) ||
            value.all { it in "-_./:：· " }
    }

    /** Profile names must never be sourced from resource IDs or class names. */
    private fun profileNodeText(node: NodeSnapshot): List<String> = listOfNotNull(
        node.text,
        node.contentDescription,
        node.hintText,
        node.stateDescription,
        node.paneTitle,
    ).filter(String::isNotBlank)

    private fun isTruncated(value: String): Boolean =
        value.contains("…") || value.contains("..") || value.trimEnd().endsWith('.')

    private fun normalize(value: String): String = value
        .filterNot(Char::isWhitespace)
        .replace("…", "")
        .replace(".", "")
        .replace("·", "")
        .replace("。", "")

    private data class Candidate(
        val value: String,
        val bounds: ScreenBounds,
    )

    private data class ProfileNameAnchor(
        val avatarBounds: ScreenBounds?,
        val accountBounds: ScreenBounds?,
    )

    private val WHITESPACE = Regex("\\s+")
    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 40
    private const val PROFILE_HEADER_TOP_RATIO = 0.08f
    private const val PROFILE_HEADER_BOTTOM_RATIO = 0.35f
    private const val AVATAR_MAX_LEFT_RATIO = 0.42f
    private const val AVATAR_RIGHT_OVERLAP_RATIO = 0.12f
    private const val AVATAR_VERTICAL_TOP_RATIO = 0.08f
    private const val AVATAR_VERTICAL_BOTTOM_RATIO = 0.08f
    private const val ACCOUNT_LINE_MAX_GAP_RATIO = 2.5f
    private const val ACCOUNT_HORIZONTAL_OVERLAP_RATIO = 0.45f
    private const val ACCOUNT_LINE_MAX_OVERLAP_PX = 18
    private const val FALLBACK_MIN_CENTER_Y_RATIO = 0.11f
    private const val FALLBACK_MIN_CENTER_X_RATIO = 0.20f
    private const val FALLBACK_MAX_CENTER_X_RATIO = 0.96f
    private val NOISE_MARKERS = listOf(
        "头像",
        "背景图片",
        "背景图",
        "封面图片",
        "视频封面",
        "用户图片",
        "粉丝",
        "获赞",
        "作品",
        "关注",
        "私信",
        "发消息",
        "抖音号",
        "商家认证",
        "店铺账号",
        "店铺",
        "ip属地",
        "直播",
        "分享",
        "收藏",
        "设置",
        "求更新",
        "搜索",
        "返回",
        "更多",
        "菜单",
        "关闭",
        "筛选",
        "按钮",
    )

    private val UI_SUFFIX_MARKERS = listOf(
        "复制名字和修改备注",
        "复制名称和修改备注",
        "复制名字",
        "复制名称",
        "修改备注",
        "加入黑名单",
        "取消关注",
        "发私信",
    )

    private val ACCOUNT_LABEL_MARKERS = listOf(
        "抖音号",
        "店铺账号",
        "商家认证账号",
    )
}
