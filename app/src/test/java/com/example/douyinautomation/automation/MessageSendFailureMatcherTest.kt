package com.example.douyinautomation.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSendFailureMatcherTest {
    @Test
    fun `matches recipient-specific permission wording variants`() {
        val variants = listOf(
            "对方设置了仅他关注的人可以发送消息",
            "仅关注他的人可发私信",
            "需要互相关注后才能发送消息",
            "该用户暂不接收陌生人私信",
            "由于对方权限设置，无法发送消息",
            "Message failed: recipient settings do not allow this message",
        )

        variants.forEach { value ->
            assertTrue("Expected fuzzy match for: $value", MessageSendFailureMatcher.matches(value))
        }
    }

    @Test
    fun `does not match normal follow reminder or safety notice`() {
        val nonFailures = listOf(
            "点关注，方便以后找到他",
            "为保障用户沟通安全，未互相关注的陌生人违规消息可能会被处理",
            "关注后才能发送私信",
        )

        nonFailures.forEach { value ->
            assertFalse("Unexpected fuzzy match for: $value", MessageSendFailureMatcher.matches(value))
        }
    }
}
