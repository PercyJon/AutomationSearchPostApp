package com.example.douyinautomation.automation

/**
 * Matches the short, transient notice Douyin shows when a blank composer is submitted.
 *
 * M2 deliberately submits one ASCII space instead of a real message.  A matching notice is the
 * only success signal for that probe; a completed accessibility click is not treated as proof.
 * The wording varies slightly across Douyin builds and may arrive split across OCR blocks, so the
 * matcher accepts the stable blank-message vocabulary while avoiding generic send failures.
 */
object EmptyMessageRejectionMatcher {
    fun matches(value: CharSequence?): Boolean {
        val normalized = compact(value)
        if (normalized.isEmpty()) return false
        if (DIRECT_MARKERS.any(normalized::contains)) return true

        val hasBlank = BLANK_MARKERS.any(normalized::contains)
        val hasMessage = MESSAGE_MARKERS.any(normalized::contains)
        val hasCannotSend = CANNOT_SEND_MARKERS.any(normalized::contains)
        return hasBlank && (hasMessage || hasCannotSend)
    }

    private fun compact(value: CharSequence?): String = TextNormalizer
        .normalize(value)
        .replace(Regex("[\\s\\p{Punct}，。！？、：；“”‘’（）【】《》…]"), "")

    private val DIRECT_MARKERS = listOf(
        "不能发送空白消息",
        "无法发送空白消息",
        "不允许发送空白消息",
        "cannot send blank message",
        "can't send a blank message",
    )
    private val BLANK_MARKERS = listOf("空白", "空消息", "blank", "empty")
    private val MESSAGE_MARKERS = listOf("消息", "私信", "message")
    private val CANNOT_SEND_MARKERS = listOf("不能发送", "无法发送", "不允许发送", "cannot send", "can't send")
}
