package com.example.douyinautomation.automation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Small app-owned progress window shown above Douyin while an automation task is running.
 *
 * It deliberately contains only task counters and lifecycle controls. It never exposes comment
 * text, OCR output, account names or message content. The overlay is opt-in through Android's
 * “display over other apps” permission and silently declines to start until that permission is
 * granted.
 */
class FloatingOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var detailText: TextView? = null
    private var progressText: TextView? = null
    private var stageText: TextView? = null
    private var actionButton: Button? = null

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOverlays(this)) {
            AutomationStore.logger.warn(
                "floating_overlay_permission_missing",
                message = "悬浮窗权限未开启，任务仍会继续执行",
            )
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)
        val view = buildOverlay()
        val params = WindowManager.LayoutParams(
            dp(250),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(88)
        }
        runCatching { windowManager?.addView(view, params) }
            .onFailure { error ->
                AutomationStore.logger.error(
                    "floating_overlay_add_failed",
                    message = "悬浮窗无法显示，任务仍会继续执行",
                    throwable = error,
                )
                stopSelf()
                return
            }
        overlayView = view
        windowParams = params
        serviceScope.launch {
            AutomationStore.uiState.collectLatest { state ->
                updateOverlay(state)
                if (state.phase in setOf(
                        AutomationPhase.COMPLETED_TASK,
                        AutomationPhase.FAILED,
                        AutomationPhase.STOPPED,
                    )
                ) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlayView = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildOverlay(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(220, 226, 236))
            }
            elevation = dp(8).toFloat()
        }
        val header = TextView(this).apply {
            text = "自动化进度"
            setTextColor(Color.rgb(26, 35, 52))
            textSize = 15f
            setPadding(0, 0, 0, dp(4))
            setOnTouchListener(DragTouchListener())
        }
        stageText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(94, 104, 122))
        }
        progressText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(45, 111, 226))
            setPadding(0, dp(4), 0, 0)
        }
        detailText = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(105, 112, 126))
            setPadding(0, dp(2), 0, dp(6))
        }
        actionButton = Button(this).apply {
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                val paused = AutomationStore.uiState.value.phase == AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF
                AutomationStore.send(if (paused) AutomationCommand.Resume else AutomationCommand.Pause)
            }
        }
        val stopButton = Button(this).apply {
            text = "停止"
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { AutomationStore.send(AutomationCommand.Stop) }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(actionButton, LinearLayout.LayoutParams(0, dp(36), 1f))
            addView(stopButton, LinearLayout.LayoutParams(0, dp(36), 1f))
        }
        card.addView(header, LinearLayout.LayoutParams(-1, dp(28)))
        card.addView(stageText, LinearLayout.LayoutParams(-1, dp(22)))
        card.addView(progressText, LinearLayout.LayoutParams(-1, dp(26)))
        card.addView(detailText, LinearLayout.LayoutParams(-1, dp(24)))
        card.addView(actions, LinearLayout.LayoutParams(-1, dp(40)))
        return card
    }

    private fun updateOverlay(state: AutomationUiState) {
        val taskName = state.taskName?.takeIf(String::isNotBlank) ?: "当前任务"
        val total = state.taskMaxUsers?.coerceAtLeast(0) ?: 0
        val handled = state.taskHandledUserCount.coerceAtLeast(0)
        val records = state.recordEntries.filter { it.taskId == state.taskId }
        val success = records.count { it.outcome == UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED }
        val failed = records.count {
            it.outcome in setOf(
                UserTaskRecord.Outcome.MESSAGE_SEND_FAILED,
                UserTaskRecord.Outcome.PRIVATE_MESSAGE_UNAVAILABLE,
                UserTaskRecord.Outcome.IDENTITY_UNAVAILABLE,
                UserTaskRecord.Outcome.PAUSED,
                UserTaskRecord.Outcome.STOPPED,
            )
        }
        stageText?.text = "${taskName.take(20)} · ${phaseLabel(state.phase)}"
        progressText?.text = "处理 $handled / ${if (total > 0) total else "—"}"
        detailText?.text = "成功 $success · 失败 $failed · 总数 ${records.size}"
        actionButton?.text = if (state.phase == AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF) "继续" else "暂停"
        actionButton?.isEnabled = state.phase !in setOf(
            AutomationPhase.COMPLETED_TASK,
            AutomationPhase.FAILED,
            AutomationPhase.STOPPED,
        )
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = windowParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { windowManager?.updateViewLayout(view.rootView, params) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    private fun phaseLabel(phase: AutomationPhase): String = when (phase) {
        AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF -> "已暂停"
        AutomationPhase.COMPLETED_TASK -> "已完成"
        AutomationPhase.FAILED -> "执行失败"
        AutomationPhase.STOPPED -> "已停止"
        else -> "执行中"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun startIfAllowed(context: Context) {
            if (!canDrawOverlays(context)) {
                AutomationStore.logger.info("floating_overlay_waiting_for_permission")
                return
            }
            // beginTask is reached from the foreground app/service command path. A regular service
            // avoids introducing a notification channel just for this compact operator window.
            runCatching { context.startService(Intent(context, FloatingOverlayService::class.java)) }
                .onFailure { error ->
                    AutomationStore.logger.error("floating_overlay_start_failed", throwable = error)
                }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FloatingOverlayService::class.java)) }
        }

        fun openPermissionSettings(context: Context) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}
