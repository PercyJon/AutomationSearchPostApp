package com.example.douyinautomation.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugToastTest {
    @Test
    fun `unexpected development events have static toast messages`() {
        assertEquals(
            "调试提示：步骤超时，流程已暂停",
            DebugToast.messageFor("step_timeout"),
        )
        assertEquals(
            "调试提示：步骤超时，正在执行兜底",
            DebugToast.messageFor("step_timeout_recovery"),
        )
        assertEquals(
            "调试提示：搜索结果未出现，正在重新提交",
            DebugToast.messageFor("search_results_timeout_retry"),
        )
        assertEquals(
            "调试提示：搜索结果未出现，正在重新提交",
            DebugToast.messageFor("search_submit_route_noop"),
        )
        assertEquals(
            "调试提示：搜索结果未出现，正在重新提交",
            DebugToast.messageFor("search_submit_keyboard_dismiss"),
        )
        assertEquals(
            "调试提示：关键词校验失败",
            DebugToast.messageFor("search_keyword_postcondition_failed"),
        )
        assertEquals(
            "调试提示：用户结果未识别，流程将暂停",
            DebugToast.messageFor("user_result_row_match_failed"),
        )
        assertEquals(
            "调试提示：该用户暂不可私信，正在尝试下一位",
            DebugToast.messageFor("private_message_unavailable"),
        )
        assertEquals(
            "调试提示：检测到回关用户，已跳过",
            DebugToast.messageFor("user_result_follow_back_skipped"),
        )
        assertEquals(
            "调试提示：系统提示暂时遮挡，正在等待后重试",
            DebugToast.messageFor("system_overlay_detected"),
        )
        assertEquals(
            "调试提示：消息发送失败，正在尝试下一位",
            DebugToast.messageFor("private_message_send_failed"),
        )
        assertEquals(
            "调试提示：私信页面打开超时，正在尝试下一位",
            DebugToast.messageFor("private_message_entry_timeout"),
        )
    }

    @Test
    fun `normal fallback events do not create a toast`() {
        assertNull(DebugToast.messageFor("search_entry_selector_fallback"))
        assertNull(DebugToast.messageFor("user_result_structural_fallback"))
    }
}
