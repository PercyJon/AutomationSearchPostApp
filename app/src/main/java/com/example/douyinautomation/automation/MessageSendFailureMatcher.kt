package com.example.douyinautomation.automation

/**
 * Fuzzy, privacy-safe matching for Douyin delivery failures.
 *
 * The exact sentence varies with the recipient's settings and may be split across OCR lines.
 * Matching therefore combines message/send vocabulary with either an explicit failure marker or
 * a recipient restriction marker. It deliberately does not match a generic follow reminder such
 * as “点关注，方便以后找到他”.
 */
object MessageSendFailureMatcher {
    fun matches(value: CharSequence?): Boolean {
        val normalized = compact(value)
        if (normalized.isEmpty()) return false

        if (containsAny(normalized, DIRECT_FAILURE_MARKERS)) return true

        val hasMessage = containsAny(normalized, MESSAGE_MARKERS)
        if (!hasMessage) return false

        // A profile-level “关注后才能发送私信” gate is not a delivery failure. The page detector
        // handles the canonical gate separately; keep fuzzy matching focused on an already-open
        // conversation or a sentence that names the recipient's settings.
        val profileGateOnly = normalized.startsWith("关注后") &&
            !containsAny(normalized, RECIPIENT_MARKERS) &&
            !containsAny(normalized, PERMISSION_MARKERS)
        if (profileGateOnly) return false

        val hasSendOrReceive = containsAny(normalized, SEND_OR_RECEIVE_MARKERS)
        val hasFailure = containsAny(normalized, FAILURE_MARKERS)
        val hasRestriction = containsAny(normalized, RESTRICTION_MARKERS)

        return hasMessage && (
            hasFailure ||
                (hasRestriction && hasSendOrReceive)
            )
    }

    private fun compact(value: CharSequence?): String = TextNormalizer
        .normalize(value)
        .replace(Regex("[\\s\\p{Punct}，。！？、：；“”‘’（）【】《》…]"), "")

    private fun containsAny(value: String, terms: Iterable<String>): Boolean =
        terms.any { value.contains(it) }

    private val DIRECT_FAILURE_MARKERS = listOf(
        "消息发送失败",
        "发送失败",
        "消息未发送",
        "发送不成功",
        "无法发送",
        "不能发送",
        "不可以发送",
        "不可发送",
        "拒绝发送",
        "failedtosend",
        "messagefailed",
        "couldntsend",
    )

    private val MESSAGE_MARKERS = listOf("消息", "私信", "message")
    private val SEND_OR_RECEIVE_MARKERS = listOf(
        "发送",
        "发消息",
        "发私信",
        "接收",
        "接受",
        "联系",
        "send",
        "receive",
    )
    private val FAILURE_MARKERS = listOf(
        "失败",
        "未发送",
        "无法",
        "不能",
        "不可以",
        "不可",
        "拒绝",
        "未能",
        "暂不",
        "fail",
        "cannot",
        "couldnt",
    )
    private val RESTRICTION_MARKERS = listOf(
        "关注",
        "互相关注",
        "权限",
        "设置",
        "限制",
        "仅",
        "只能",
        "允许",
        "陌生人",
        "permission",
        "restrict",
    )
    private val RECIPIENT_MARKERS = listOf("对方", "该用户", "他", "她", "recipient")
    private val PERMISSION_MARKERS = listOf("权限", "设置", "限制", "permission", "restrict")
}
