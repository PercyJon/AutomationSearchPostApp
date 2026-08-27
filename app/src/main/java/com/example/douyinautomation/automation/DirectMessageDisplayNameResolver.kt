package com.example.douyinautomation.automation

import kotlin.math.abs
import kotlin.math.max

/**
 * Resolves the conversation participant name from a verified Douyin direct-message page.
 *
 * The profile page is not the only reliable source of a name.  In a conversation Douyin renders
 * a larger circular avatar in the message header and places the participant name directly below
 * it.  The avatar can be different sizes and may have a red follow-plus badge, so the resolver
 * anchors on the largest square image in the centered conversation header instead of fixed
 * coordinates.  The compact title in the top app bar and follow-prompt text are deliberately
 * rejected.
 */
object DirectMessageDisplayNameResolver {

    fun fromAccessibility(context: ScreenContext, previousName: String?): String? = resolve(
        context = context,
        candidates = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .flatMap { node ->
                listOfNotNull(
                    node.text,
                    node.contentDescription,
                    node.hintText,
                    node.stateDescription,
                    node.paneTitle,
                ).asSequence().map { Candidate(it, node.bounds) }
            },
        previousName = previousName,
    )

    fun fromOcr(context: ScreenContext, previousName: String?): String? = resolve(
        context = context,
        candidates = context.ocrBlocks.asSequence()
            .filter { it.bounds.width > 0 && it.bounds.height > 0 }
            .map { Candidate(it.text, it.bounds) },
        previousName = previousName,
    )

    private fun resolve(
        context: ScreenContext,
        candidates: Sequence<Candidate>,
        previousName: String?,
    ): String? {
        val filtered = candidates
            .mapNotNull { candidate ->
                ProfileDisplayNameResolver.sanitizeCandidate(candidate.value)?.let { value ->
                    candidate.copy(value = value)
                }
            }
            .filter { it.value.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH }
            .filterNot { ProfileDisplayNameResolver.isNoiseCandidate(it.value) }
            .filterNot { isConversationNoise(it.value) }
            .filterNot { it.value.all(Char::isDigit) }
            .filterNot { it.value.contains("…") || it.value.contains("..") }
            .distinctBy { "${canonical(it.value)}:${it.bounds.left}:${it.bounds.top}" }
            .toList()
        if (filtered.isEmpty()) return null

        val avatar = findConversationAvatar(context)
        val anchored = avatar?.let { bounds ->
            filtered.filter { candidate -> isBelowAvatar(candidate.bounds, bounds, context.screenSize) }
        }.orEmpty()
        val preferred = anchored.ifEmpty {
            // If the image is custom-rendered and absent from the accessibility tree, keep the
            // fallback inside the centered header band. This is still content/geometry based and
            // excludes the app-bar title and lower message text.
            filtered.filter { candidate ->
                val center = candidate.bounds.centerY / context.screenSize.height.toFloat()
                val horizontal = candidate.bounds.centerX / context.screenSize.width.toFloat()
                center in FALLBACK_MIN_CENTER_Y..FALLBACK_MAX_CENTER_Y &&
                    horizontal in FALLBACK_MIN_CENTER_X..FALLBACK_MAX_CENTER_X
            }.maxWithOrNull(
                compareBy<Candidate> { it.bounds.height }
                    .thenBy { it.bounds.width }
                    .thenBy { it.bounds.top },
            )?.let(::listOf).orEmpty()
        }
        if (preferred.isEmpty()) return null

        val previous = previousName
            ?.takeIf(String::isNotBlank)
            ?.let(::canonical)
        val matching = previous?.let { expected ->
            preferred.filter { candidate ->
                val actual = canonical(candidate.value)
                actual.startsWith(expected) || expected.startsWith(actual)
            }
        }.orEmpty()
        return (matching.ifEmpty { preferred })
            .sortedWith(
                compareBy<Candidate> { it.bounds.top }
                    .thenByDescending { it.bounds.height }
                    .thenByDescending { canonical(it.value).length },
            )
            .firstOrNull()
            ?.value
    }

    private fun findConversationAvatar(context: ScreenContext): ScreenBounds? {
        val width = context.screenSize.width.coerceAtLeast(1)
        val height = context.screenSize.height.coerceAtLeast(1)
        return context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { node ->
                val bounds = node.bounds
                val ratio = bounds.width.toFloat() / bounds.height.toFloat()
                val centerX = bounds.centerX / width.toFloat()
                val centerY = bounds.centerY / height.toFloat()
                ratio in AVATAR_MIN_RATIO..AVATAR_MAX_RATIO &&
                    bounds.width >= MIN_AVATAR_SIZE &&
                    centerX in AVATAR_MIN_CENTER_X..AVATAR_MAX_CENTER_X &&
                    centerY in AVATAR_MIN_CENTER_Y..AVATAR_MAX_CENTER_Y
            }
            .maxWithOrNull(
                compareBy<NodeSnapshot> { isAvatarSemantic(it) }
                    .thenBy { it.bounds.width * it.bounds.height }
                    .thenBy { -abs(it.bounds.centerX - width / 2) },
            )
            ?.bounds
    }

    private fun isBelowAvatar(
        candidate: ScreenBounds,
        avatar: ScreenBounds,
        screenSize: ScreenSize,
    ): Boolean {
        val verticalGap = candidate.top - avatar.bottom
        val horizontalDistance = abs(candidate.centerX - avatar.centerX)
        val maxHorizontalDistance = max(
            avatar.width * MAX_HORIZONTAL_AVATAR_DISTANCE_RATIO,
            screenSize.width * MAX_HORIZONTAL_SCREEN_DISTANCE_RATIO,
        )
        return verticalGap >= -avatar.height * AVATAR_BOTTOM_OVERLAP_RATIO &&
            verticalGap <= avatar.height * MAX_NAME_GAP_RATIO &&
            horizontalDistance <= maxHorizontalDistance &&
            candidate.centerY / screenSize.height.toFloat() <= NAME_MAX_CENTER_Y
    }

    private fun isAvatarSemantic(node: NodeSnapshot): Boolean {
        val text = node.searchableText().joinToString(" ").lowercase()
        return text.contains("头像") ||
            text.contains("用户图片") ||
            node.className.orEmpty().contains("image", ignoreCase = true)
    }

    private fun isConversationNoise(value: String): Boolean {
        val normalized = canonical(value)
        return normalized == "视频" ||
            normalized == "搜索" ||
            normalized == "筛选" ||
            normalized == "按钮" ||
            IdentityCountToken.matches(value) ||
            normalized.contains("关注") ||
            normalized.contains("方便以后找到") ||
            normalized.contains("你已进入咨询会话") ||
            normalized.contains("会话可能会被记录") ||
            normalized.contains("了解更多") ||
            normalized.contains("昨天在线") ||
            normalized.contains("在线") ||
            normalized.contains("人工客服") ||
            normalized.contains("服务评价") ||
            normalized.contains("案例资料") ||
            normalized.contains("常见问题")
    }

    private fun canonical(value: String): String = value
        .filterNot(Char::isWhitespace)
        .replace("…", "")
        .replace(".", "")
        .replace("·", "")
        .replace("。", "")

    private data class Candidate(
        val value: String,
        val bounds: ScreenBounds,
    )

    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 40
    private const val MIN_AVATAR_SIZE = 56
    private const val AVATAR_MIN_RATIO = 0.72f
    private const val AVATAR_MAX_RATIO = 1.38f
    private const val AVATAR_MIN_CENTER_X = 0.22f
    private const val AVATAR_MAX_CENTER_X = 0.78f
    private const val AVATAR_MIN_CENTER_Y = 0.10f
    private const val AVATAR_MAX_CENTER_Y = 0.42f
    private const val AVATAR_BOTTOM_OVERLAP_RATIO = 0.28f
    private const val MAX_NAME_GAP_RATIO = 1.65f
    private const val MAX_HORIZONTAL_AVATAR_DISTANCE_RATIO = 1.7f
    private const val MAX_HORIZONTAL_SCREEN_DISTANCE_RATIO = 0.30f
    private const val NAME_MAX_CENTER_Y = 0.48f
    private const val FALLBACK_MIN_CENTER_X = 0.16f
    private const val FALLBACK_MAX_CENTER_X = 0.84f
    private const val FALLBACK_MIN_CENTER_Y = 0.18f
    private const val FALLBACK_MAX_CENTER_Y = 0.42f
}
