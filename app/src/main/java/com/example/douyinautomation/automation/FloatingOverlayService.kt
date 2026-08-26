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
    private var overlayView: View? = null
    private var expandedView: LinearLayout? = null
    private var collapsedView: TextView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var expandedX = 0
    private var expandedY = 0
    private var isCollapsed = false
    private var detailText: TextView? = null
    private var progressText: TextView? = null
    private var stageText: TextView? = null
    private var actionButton: TextView? = null
    private var nonTouchableForAutomation = false

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
        // Start collapsed so the overlay never intercepts the first automation tap. The
        // operator can expand it from the edge when progress or controls are needed.
        val view = buildCollapsedOverlay()
        val params = WindowManager.LayoutParams(
            dp(COLLAPSED_SIZE_DP),
            dp(COLLAPSED_SIZE_DP),
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
            x = collapsedSlotX()
            y = collapsedSlotY()
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
        collapsedView = view
        expandedX = dp(12)
        expandedY = dp(88)
        isCollapsed = true
        windowParams = params
        publishOverlayBounds(view)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startIfAllowed is also called when a new task begins while this service is already
        // alive. Re-collapse an expanded panel so a previous inspection cannot block taps.
        if (intent?.action == ACTION_TASK_STARTED && !isCollapsed) {
            collapseOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        AppOwnedOverlayExclusion.clear()
        overlayView = null
        expandedView = null
        collapsedView = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun compactText(
        sizeSp: Float,
        color: Int,
        extraTopDp: Int = 0,
        extraBottomDp: Int = 0,
    ): TextView = TextView(this).apply {
        textSize = sizeSp
        setTextColor(color)
        includeFontPadding = false
        setPadding(0, dp(extraTopDp), 0, dp(extraBottomDp))
    }

    private fun compactButton(
        label: String,
        backgroundColor: Int,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        setTextColor(Color.WHITE)
        background = roundedBackground(backgroundColor, dp(7))
        setOnClickListener { onClick() }
    }

    private fun buildOverlay(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                setColor(COLOR_PANEL)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), COLOR_PANEL_STROKE)
            }
            elevation = dp(3).toFloat()
        }
        val collapseButton = TextView(this).apply {
            text = "收起"
            contentDescription = "收起进度"
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.rgb(45, 111, 226))
            textSize = 10f
            background = roundedBackground(COLOR_COLLAPSE_BUTTON, dp(8))
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { collapseOverlay() }
        }
        stageText = compactText(11f, Color.rgb(94, 104, 122))
        progressText = compactText(12f, Color.rgb(45, 111, 226)).apply {
            setPadding(dp(6), 0, 0, 0)
        }
        detailText = compactText(10f, Color.rgb(105, 112, 126), extraTopDp = 1, extraBottomDp = 2)
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnTouchListener(DragTouchListener())
            addView(stageText, LinearLayout.LayoutParams(-2, dp(18)))
            addView(progressText, LinearLayout.LayoutParams(-2, dp(18)))
            addView(View(this@FloatingOverlayService), LinearLayout.LayoutParams(0, 1, 1f))
            addView(collapseButton, LinearLayout.LayoutParams(-2, dp(20)))
        }
        actionButton = compactButton("暂停", COLOR_PAUSE) {
            val phase = AutomationStore.uiState.value.phase
            val action = FloatingOverlayControlPolicy.primaryAction(phase)
            val canResume = action == FloatingOverlayPrimaryAction.RESUME
            // Resume must inspect Douyin immediately.  Keeping this expanded application
            // overlay above the player can temporarily make it the active accessibility
            // window on some OEM builds, so remove it before queuing the explicit Resume.
            // The controller performs its own bounded, read-only target revalidation after
            // this handoff; collapsing here never starts a task by itself.
            if (canResume) collapseOverlay()
            AutomationStore.logger.info(
                "floating_overlay_command",
                attributes = mapOf("command" to if (canResume) "resume" else "pause"),
            )
            AutomationStore.send(
                if (canResume) AutomationCommand.Resume else AutomationCommand.Pause,
            )
        }
        val stopButton = compactButton("停止", COLOR_STOP) {
            AutomationStore.logger.info(
                "floating_overlay_command",
                attributes = mapOf("command" to "stop"),
            )
            AutomationStore.send(AutomationCommand.Stop)
        }
        val pauseParams = LinearLayout.LayoutParams(0, dp(24), 1f).apply { marginEnd = dp(4) }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(actionButton, pauseParams)
            addView(stopButton, LinearLayout.LayoutParams(0, dp(24), 1f))
        }
        card.addView(statusRow, LinearLayout.LayoutParams(-1, dp(20)))
        card.addView(detailText, LinearLayout.LayoutParams(-1, -2))
        card.addView(actions, LinearLayout.LayoutParams(-1, dp(24)))
        return card
    }

    private fun buildCollapsedOverlay(): TextView = TextView(this).apply {
        text = "↗"
        contentDescription = "展开进度"
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 21f
        // Sky-blue, semi-transparent, and half the prior side length. Recognition still uses
        // dynamically published actual bounds rather than this visual style.
        background = roundedBackground(COLOR_COLLAPSED_CHIP, dp(10))
        elevation = dp(4).toFloat()
        setOnClickListener { expandOverlay() }
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun collapseOverlay() {
        if (isCollapsed) return
        val manager = windowManager ?: return
        val params = windowParams ?: return
        val expanded = expandedView ?: return
        expandedX = params.x
        expandedY = params.y
        val oldWidth = params.width
        val oldHeight = params.height
        val collapsed = buildCollapsedOverlay()
        val collapsedSize = dp(COLLAPSED_SIZE_DP)
        params.width = collapsedSize
        params.height = collapsedSize
        params.x = collapsedSlotX()
        params.y = collapsedSlotY()
        runCatching {
            manager.removeView(expanded)
            manager.addView(collapsed, params)
            overlayView = collapsed
            collapsedView = collapsed
            isCollapsed = true
            publishOverlayBounds(collapsed)
        }.onFailure { error ->
            AutomationStore.logger.error(
                "floating_overlay_collapse_failed",
                message = "悬浮窗收起失败，保持展开状态",
                throwable = error,
            )
            params.width = oldWidth
            params.height = oldHeight
            params.x = expandedX
            params.y = expandedY
            runCatching { manager.addView(expanded, params) }
        }
    }

    private fun expandOverlay() {
        if (!isCollapsed) return
        val manager = windowManager ?: return
        val params = windowParams ?: return
        val collapsed = collapsedView ?: return
        val expanded = expandedView ?: buildOverlay().also { expandedView = it }
        val oldWidth = params.width
        val oldHeight = params.height
        val oldX = params.x
        val oldY = params.y
        params.width = dp(EXPANDED_WIDTH_DP)
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.x = expandedX.coerceAtLeast(0)
        params.y = expandedY.coerceAtLeast(dp(12))
        runCatching {
            manager.removeView(collapsed)
            manager.addView(expanded, params)
            overlayView = expanded
            collapsedView = null
            isCollapsed = false
            publishOverlayBounds(expanded)
            updateOverlay(AutomationStore.uiState.value)
            AutomationStore.logger.info(
                "floating_overlay_expanded",
                attributes = mapOf("x" to params.x, "y" to params.y),
            )
        }.onFailure { error ->
            AutomationStore.logger.error(
                "floating_overlay_expand_failed",
                message = "悬浮窗展开失败",
                throwable = error,
            )
            params.width = oldWidth
            params.height = oldHeight
            params.x = oldX
            params.y = oldY
            runCatching { manager.addView(collapsed, params) }
        }
    }

    private fun updateOverlay(state: AutomationUiState) {
        updateAutomationTouchability(state.phase)
        val total = state.taskMaxUsers?.coerceAtLeast(0) ?: 0
        val handled = state.taskHandledUserCount.coerceAtLeast(0)
        val records = state.recordEntries.filter { it.taskId == state.taskId }
        val messaged = records.count {
            it.outcome == UserTaskRecord.Outcome.BLANK_PROBE_VERIFIED ||
                it.outcome == UserTaskRecord.Outcome.PROFILE_OPENED
        }
        val queueLabel = AutomationStore.getLocalTaskQueueSession()
            ?.takeIf { session -> session.status in setOf(LocalTaskQueueStatus.RUNNING, LocalTaskQueueStatus.PAUSED) }
            ?.let { session -> "队列 ${session.activeTaskIndex + 1}/${session.tasks.size}" }
        stageText?.text = FloatingOverlayControlPolicy.stageLabel(phaseLabel(state.phase), queueLabel)
        progressText?.text = FloatingOverlayControlPolicy.progressLabel(handled, total)
        detailText?.text = FloatingOverlayControlPolicy.detailLabel(messaged)
        val action = FloatingOverlayControlPolicy.primaryAction(state.phase)
        actionButton?.apply {
            text = when (action) {
                FloatingOverlayPrimaryAction.RESUME -> "恢复"
                FloatingOverlayPrimaryAction.PAUSE,
                FloatingOverlayPrimaryAction.NONE,
                -> "暂停"
            }
            background = roundedBackground(
                if (action == FloatingOverlayPrimaryAction.RESUME) COLOR_RESUME else COLOR_PAUSE,
                dp(8),
            )
            isEnabled = action != FloatingOverlayPrimaryAction.NONE
        }
    }

    /** Overlay stays tappable; automation avoids it via published OCR exclusion bounds. */
    private fun updateAutomationTouchability(phase: AutomationPhase) {
        val shouldDisableTouches = FloatingOverlayTouchPolicy.shouldDisableTouches(phase)
        if (nonTouchableForAutomation == shouldDisableTouches) return
        val view = overlayView ?: return
        val params = windowParams ?: return
        val previousFlags = params.flags
        nonTouchableForAutomation = shouldDisableTouches
        if (shouldDisableTouches) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { windowManager?.updateViewLayout(view, params) }
            .onFailure { error ->
                params.flags = previousFlags
                nonTouchableForAutomation = !shouldDisableTouches
                AutomationStore.logger.error(
                    "floating_overlay_touchability_update_failed",
                    message = "无法更新自动化期间的悬浮窗触摸穿透",
                    throwable = error,
                )
            }
    }

    private fun publishOverlayBounds(view: View) {
        view.post {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            AppOwnedOverlayExclusion.update(
                ScreenBounds(
                    left = location[0],
                    top = location[1],
                    right = location[0] + view.width,
                    bottom = location[1] + view.height,
                ),
            )
        }
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
                        runCatching {
                            windowManager?.updateViewLayout(view.rootView, params)
                            publishOverlayBounds(view.rootView)
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    private fun phaseLabel(phase: AutomationPhase): String = when (phase) {
        AutomationPhase.SUSPENDED_BEFORE_START -> "已挂起"
        AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF -> "已暂停"
        AutomationPhase.COMPLETED_TASK -> "已完成"
        AutomationPhase.FAILED -> "执行失败"
        AutomationPhase.STOPPED -> "已停止"
        else -> "进行中"
    }

    private fun collapsedSlotX(): Int = dp(COLLAPSED_EDGE_DP)

    /** Left side, below status/search and above the Douyin tab bar and comment avatar column. */
    private fun collapsedSlotY(): Int = dp(168)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXPANDED_WIDTH_DP = 196
        private const val COLLAPSED_SIZE_DP = 29
        private const val COLLAPSED_EDGE_DP = 8
        private val COLOR_PAUSE = Color.argb(210, 217, 119, 6)
        private val COLOR_RESUME = Color.argb(210, 22, 163, 74)
        private val COLOR_STOP = Color.argb(210, 220, 38, 38)
        private val COLOR_PANEL = Color.argb(150, 255, 255, 255)
        private val COLOR_PANEL_STROKE = Color.argb(110, 220, 226, 236)
        private val COLOR_COLLAPSE_BUTTON = Color.argb(140, 238, 244, 255)
        private val COLOR_COLLAPSED_CHIP = Color.argb(130, 56, 189, 248)

        fun startIfAllowed(context: Context) {
            if (!canDrawOverlays(context)) {
                AutomationStore.logger.info("floating_overlay_waiting_for_permission")
                return
            }
            // beginTask is reached from the foreground app/service command path. A regular service
            // avoids introducing a notification channel just for this compact operator window.
            runCatching {
                context.startService(
                    Intent(context, FloatingOverlayService::class.java).apply {
                        action = ACTION_TASK_STARTED
                    },
                )
            }
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

        private const val ACTION_TASK_STARTED =
            "com.example.douyinautomation.action.FLOATING_TASK_STARTED"
    }
}
