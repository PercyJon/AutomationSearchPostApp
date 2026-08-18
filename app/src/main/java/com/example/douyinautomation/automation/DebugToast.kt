package com.example.douyinautomation.automation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Temporary development-only feedback for abnormal POC states.
 *
 * The mapping intentionally uses static, non-sensitive Chinese messages. Raw node text,
 * keywords, OCR output, account identifiers, and exception details are never shown in a Toast.
 * This helper will be removed after M0 acceptance; diagnostics/logcat remain the durable record.
 */
object DebugToast {
    // Lazy construction keeps the pure message mapping usable in JVM unit tests, where Android's
    // main Looper is not prepared. On a device this is initialized on first Toast delivery.
    private val mainHandler: Handler? by lazy {
        runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
    }

    @Volatile
    private var applicationContext: Context? = null

    fun install(context: Context) {
        applicationContext = context.applicationContext
    }

    fun clear() {
        applicationContext = null
    }

    fun showForEvent(event: String) {
        val text = messageFor(event) ?: return
        val context = applicationContext ?: return
        mainHandler?.post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    internal fun messageFor(event: String): String? = when (event) {
        "poc_paused_for_manual_handoff" -> "调试提示：流程已暂停，请检查当前页面"
        "step_timeout" -> "调试提示：步骤超时，流程已暂停"
        "step_timeout_recovery" -> "调试提示：步骤超时，正在执行兜底"
        "search_results_timeout_retry",
        "search_submit_postcondition_retry",
        "search_submit_route_noop",
        "search_submit_keyboard_dismiss",
        -> "调试提示：搜索结果未出现，正在重新提交"
        "system_overlay_detected" -> "调试提示：系统提示暂时遮挡，正在等待后重试"
        "transient_live_overlay_detected" -> "调试提示：直播提示暂时遮挡，等待消失后重试"
        "target_window_unavailable" -> "调试提示：抖音窗口暂不可用，正在等待恢复"
        "node_inspection_failed" -> "调试提示：无障碍节点读取异常"
        "ocr_page_probe_failed",
        "initial_ocr_probe_failed",
        "ocr_initialization_failed",
        -> "调试提示：OCR 识别异常"
        "search_keyword_postcondition_failed" -> "调试提示：关键词校验失败"
        "user_result_row_match_failed" -> "调试提示：用户结果未识别，流程将暂停"
        "user_result_follow_back_skipped" -> "调试提示：检测到回关用户，已跳过"
        "user_tab_candidate_rejected" -> "调试提示：用户标签候选不可用"
        "private_message_unavailable" -> "调试提示：该用户暂不可私信，正在尝试下一位"
        "private_message_send_failed" -> "调试提示：消息发送失败，正在尝试下一位"
        "empty_message_probe_submitted" -> "调试提示：已提交空格，正在等待空白消息提示"
        "empty_message_probe_verified" -> "调试提示：空白消息提示已确认，正在处理下一位"
        "empty_message_probe_next_requested" -> "调试提示：正在进入下一位用户"
        "message_send_rejected" -> "调试提示：消息发送未执行，请检查私信输入和发送按钮"
        "private_message_entry_timeout" -> "调试提示：私信页面打开超时，正在尝试下一位"
        "private_message_entry_retry" -> "调试提示：私信入口未响应，正在刷新后重试"
        "user_result_duplicate_skipped" -> "调试提示：检测到重复用户，已跳过并继续"
        "user_result_identity_unavailable" -> "调试提示：用户身份暂不可识别，使用翻页兜底"
        "user_result_next_timeout" -> "调试提示：下一页用户加载超时，流程已暂停"
        "user_result_next_waiting" -> "调试提示：正在等待下一页用户加载"
        "user_result_next_recovery" -> "调试提示：下一页未稳定，正在执行一次兜底滚动"
        "diagnostics_capture_failed" -> "调试提示：截图或 OCR 诊断失败"
        "node_dump_failed" -> "调试提示：节点树导出失败"
        "command_rejected",
        "command_dropped",
        "resume_rejected",
        -> "调试提示：操作未执行，请查看诊断日志"
        "accessibility_service_interrupted",
        "accessibility_service_destroyed",
        -> "调试提示：无障碍服务发生中断"
        else -> null
    }
}
