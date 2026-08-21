package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers.IO
import kotlin.math.max
import kotlin.math.min

/**
 * Android's opt-in accessibility entry point for M0. The service is explicitly scoped in its XML
 * metadata to Douyin's package; it does not observe unrelated apps. Android Settings controls
 * enabling/disabling it—this app never attempts to change secure accessibility settings itself.
 */
class DouyinAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inspector = NodeTreeInspector()
    private val detector = PageDetector()
    private val selector = SelectorEngine()
    private lateinit var screenshotCapture: ScreenshotCapture

    private var ocr: MlKitOcrEngine? = null
    private lateinit var controller: DouyinNavigationController
    private var commandJob: Job? = null
    private var ocrInitializationJob: Job? = null
    private var rebindResumeJob: Job? = null
    private var lastSignature: String? = null
    private val inspectionInFlight = AtomicBoolean(false)
    private val inspectionMutex = Mutex()
    private val ocrProbeInFlight = AtomicBoolean(false)
    private val lastOcrProbeAtMillis = AtomicLong(0L)
    private var cachedOcrContextSignature: String? = null
    private var cachedOcrBlocks: List<OcrTextBlock> = emptyList()
    private var cachedOcrAtMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutomationStore.initialize(this)
        val logger = AutomationStore.logger
        DebugToast.install(this)
        // Keep service binding independent from optional ML Kit model availability. Some OEMs
        // block or delay recognizer initialization, so OCR is created after the service has
        // announced readiness on a background dispatcher.
        ocr = null
        screenshotCapture = ScreenshotCapture(this, logger)
        controller = DouyinNavigationController(
            service = this,
            scope = serviceScope,
            logger = logger,
            inspector = inspector,
            pageDetector = detector,
            selector = selector,
            gestures = GestureEngine(this, logger),
            screenshotCapture = screenshotCapture,
            ocr = null
        )
        // Subscribe before reporting the service as command-ready. A debug intent can recreate
        // Compose at the same time Android binds this service; publishing readiness first would
        // let a SharedFlow command be emitted before there is a collector and silently lose the
        // P0 start request. UNDISPATCHED installs the subscription synchronously on this callback.
        commandJob = serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            AutomationStore.commands.collect { command ->
                controller.handle(command)
            }
        }
        AutomationStore.markServiceConnected()
        logger.info("accessibility_service_connected", attributes = mapOf("target" to TargetAppLauncher.DOUYIN_PACKAGE))
        scheduleCheckpointResumeAfterRebind()
        ocrInitializationJob?.cancel()
        ocrInitializationJob = serviceScope.launch(Dispatchers.IO) {
            logger.info("ocr_initialization_started")
            val ocrEngine = runCatching { MlKitOcrEngine(logger) }
                .onFailure { error ->
                    logger.error(
                        "ocr_initialization_failed",
                        message = "OCR will be unavailable for this run",
                        throwable = error,
                    )
                }
                .getOrNull()
            if (ocrEngine == null) return@launch
            if (::controller.isInitialized) {
                ocr = ocrEngine
                controller.installOcrEngine(ocrEngine)
                logger.info("ocr_initialization_ready")
            } else {
                ocrEngine.close()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::controller.isInitialized || event == null) return
        if (event.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) return

        // Douyin normally exposes the blank-message rejection as a transient toast.  That toast
        // is delivered as TYPE_NOTIFICATION_STATE_CHANGED rather than as a content-change event,
        // so the old filter discarded the only reliable signal before OCR had a chance to run.
        // Copy the text while the event is live and hand it to the state machine without walking
        // the (usually huge) active window tree.
        if (controller.shouldProbeEmptyMessageResult()) {
            val transientText = buildList {
                event.text?.forEach { value -> value?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::add) }
                event.contentDescription?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            }.distinct()
            if (transientText.isNotEmpty()) {
                serviceScope.launch {
                    controller.onTransientAccessibilityText(transientText)
                }
            }
        }
        if (!event.isWindowOrContentChange()) return

        // Accessibility callbacks run on the service main thread. A Douyin transition can expose
        // a very large node tree, so never walk it synchronously here: doing so can trigger an OEM
        // background-ANR and Android will kill the service. Keep at most one snapshot in flight;
        // the next event will provide a fresher tree once this one completes.
        if (!inspectionInFlight.compareAndSet(false, true)) return

        val root = targetRootFor(event)
        if (root == null) {
            inspectionInFlight.set(false)
            return
        }
        serviceScope.launch(Dispatchers.Default) {
            inspectionMutex.withLock {
                try {
                    val context = inspector.inspect(root)
                    val augmentedContext = augmentUnknownPageWithOcr(context)
                    val detection = detector.detect(augmentedContext)
                    // Include OCR in the deduplication key. A node-only UNKNOWN snapshot may be
                    // followed by the same tree plus a successful OCR result; suppressing that
                    // second observation would prevent HOME/SEARCH detection from advancing.
                    val ocrSignature = augmentedContext.ocrText().joinToString("|").hashCode()
                    // Node count alone is not enough to deduplicate a Douyin tab transition: the
                    // selected tab and visible result rows can change while the tree retains the
                    // same size. Include a compact semantic/geometry signature so a post-click
                    // USER_RESULTS observation is not suppressed as a duplicate SEARCH_RESULTS
                    // snapshot, while identical content-change events still remain cheap to drop.
                    val nodeSignature = context.nodes.joinToString("|") { node ->
                        buildString {
                            append(node.text.orEmpty())
                            append('\u0001').append(node.contentDescription.orEmpty())
                            append('\u0001').append(node.bounds.left).append(',').append(node.bounds.top)
                            append(',').append(node.bounds.right).append(',').append(node.bounds.bottom)
                            append('\u0001').append(node.isSelected)
                            append('\u0001').append(node.isVisibleToUser)
                        }
                    }.hashCode()
                    val signature = "${detection.kind}:${detection.confidence}:${context.nodes.size}:${nodeSignature}:${augmentedContext.ocrBlocks.size}:$ocrSignature"
                    if (signature == lastSignature && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                        return@withLock
                    }
                    lastSignature = signature
                    controller.onScreenObserved(augmentedContext, detection)
                } catch (error: Throwable) {
                    // A stale OEM node or a transient binder failure must not terminate the
                    // accessibility service. The next event can recover with a fresh root.
                    AutomationStore.logger.error(
                        "node_inspection_failed",
                        message = "Accessibility tree inspection failed; waiting for the next event",
                        throwable = error,
                    )
                } finally {
                    @Suppress("DEPRECATION")
                    root.recycle()
                    inspectionInFlight.set(false)
                }
            }
        }
    }

    /**
     * Douyin often renders its home feed in a custom view with little or no useful node text.
     * When a running task remains UNKNOWN, take a throttled service screenshot and use ML Kit as
     * the next recognition level. OCR is never used to bypass a risk screen; those signals still
     * route the controller to manual handoff.
     */
    private suspend fun augmentUnknownPageWithOcr(context: ScreenContext): ScreenContext {
        if (!::controller.isInitialized || !controller.shouldUseOcrFallback()) return context
        val detectedKind = detector.detect(context).kind
        val probeToastMayBeVisible = controller.shouldProbeEmptyMessageWithOcr() &&
            detectedKind == PageKind.DIRECT_MESSAGE
        val privateMessageEntryMayBeIncomplete = controller.shouldProbePrivateMessageEntryWithOcr()
        if (detectedKind != PageKind.UNKNOWN &&
            !probeToastMayBeVisible &&
            !privateMessageEntryMayBeIncomplete
        ) return context

        val engine = ocr ?: return context
        val now = SystemClock.uptimeMillis()
        val contextSignature = buildString {
            append(detectedKind.name).append('|')
            context.nodes.asSequence()
                .filter { it.bounds.width > 0 && it.bounds.height > 0 }
                .take(80)
                .forEach { node ->
                    append(node.bounds.left).append(',').append(node.bounds.top).append(',')
                        .append(node.bounds.right).append(',').append(node.bounds.bottom).append('|')
                        .append(node.text.orEmpty()).append('|').append(node.contentDescription.orEmpty()).append(';')
                }
        }.hashCode().toString()
        if (contextSignature == cachedOcrContextSignature && now - cachedOcrAtMillis < OCR_CACHE_TTL_MS) {
            return if (cachedOcrBlocks.isEmpty()) context else context.copy(ocrBlocks = cachedOcrBlocks)
        }
        val previous = lastOcrProbeAtMillis.get()
        if (now - previous < OCR_PROBE_INTERVAL_MS ||
            !lastOcrProbeAtMillis.compareAndSet(previous, now) ||
            !ocrProbeInFlight.compareAndSet(false, true)
        ) {
            return context
        }

        return try {
            val artifact = screenshotCapture.capture("page_probe")
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(artifact.path) }
                ?: return context
            try {
                val region = when {
                    probeToastMayBeVisible -> OcrRegion.TOAST
                    privateMessageEntryMayBeIncomplete -> OcrRegion.PROFILE_ACTION
                    detectedKind == PageKind.USER_RESULTS -> OcrRegion.USER_RESULTS
                    else -> OcrRegion.FULL
                }
                val result = engine.recognize(bitmap, region)
                val blocks = result.toOcrTextBlocks()
                cachedOcrContextSignature = contextSignature
                cachedOcrBlocks = blocks
                cachedOcrAtMillis = now
                if (result.isEmpty) context else context.copy(ocrBlocks = blocks)
            } finally {
                bitmap.recycle()
            }
        } catch (error: Throwable) {
            AutomationStore.logger.warn(
                "ocr_page_probe_failed",
                message = "OCR fallback was unavailable for this page; continuing with node detection",
                attributes = mapOf("cause" to (error::class.java.simpleName ?: "Throwable")),
            )
            context
        } finally {
            ocrProbeInFlight.set(false)
        }
    }

    private fun OcrResult.toOcrTextBlocks(): List<OcrTextBlock> = blocks.map { block ->
        val bounds = block.bounds
        OcrTextBlock(
            text = block.text,
            bounds = if (bounds == null) {
                ScreenBounds.EMPTY
            } else {
                ScreenBounds(
                    left = min(bounds.left, bounds.right),
                    top = min(bounds.top, bounds.bottom),
                    right = max(bounds.left, bounds.right),
                    bottom = max(bounds.top, bounds.bottom),
                )
            },
        )
    }

    override fun onInterrupt() {
        AutomationStore.logger.warn("accessibility_service_interrupted")
        if (::controller.isInitialized) {
            serviceScope.launch { controller.handle(AutomationCommand.Pause) }
        } else {
            AutomationStore.publishManualHandoff(
                "Android interrupted the accessibility service; inspect the screen before continuing.",
            )
        }
    }

    override fun onDestroy() {
        commandJob?.cancel()
        rebindResumeJob?.cancel()
        ocrInitializationJob?.cancel()
        ocr?.close()
        AutomationStore.markServiceDisconnected()
        AutomationStore.logger.info("accessibility_service_destroyed")
        DebugToast.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun AccessibilityEvent.isWindowOrContentChange(): Boolean = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        AccessibilityEvent.TYPE_VIEW_CLICKED,
        AccessibilityEvent.TYPE_VIEW_FOCUSED,
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        -> true

        else -> false
    }

    /**
     * Prefer the active root, but do not drop a legitimate Douyin event merely because the
     * optional floating progress panel temporarily becomes the OEM's active accessibility
     * window.  The event source is already scoped to Douyin by the service configuration; walk
     * it to its root so inspection still receives the complete target hierarchy.
     */
    @Suppress("DEPRECATION")
    private fun targetRootFor(event: AccessibilityEvent): android.view.accessibility.AccessibilityNodeInfo? {
        rootInActiveWindow?.let { activeRoot ->
            if (activeRoot.packageName?.toString() == TargetAppLauncher.DOUYIN_PACKAGE) {
                return activeRoot
            }
            activeRoot.recycle()
        }

        var node = event.source ?: return null
        while (true) {
            val parent = node.parent ?: break
            node.recycle()
            node = parent
        }
        return if (node.packageName?.toString() == TargetAppLauncher.DOUYIN_PACKAGE) {
            node
        } else {
            node.recycle()
            null
        }
    }

    /**
     * Recreate the controller's in-memory state after a transient service rebind.  This is kept
     * separate from the normal operator Resume command: a running task was already authorized,
     * and the durable checkpoint contains the frozen query/filter contract.  The short delay lets
     * Android finish binding the new service and lets Douyin settle before we inspect its window.
     */
    private fun scheduleCheckpointResumeAfterRebind() {
        val checkpoint = AutomationStore.getCheckpointForServiceRebind() ?: return
        val now = SystemClock.uptimeMillis()
        if (checkpoint.taskId == lastRebindResumeTaskId &&
            now - lastRebindResumeAtMillis < REBIND_RESUME_THROTTLE_MS
        ) {
            AutomationStore.logger.info(
                "service_rebind_resume_throttled",
                attributes = mapOf("task_id_hash" to checkpoint.taskId.hashCode()),
            )
            return
        }
        lastRebindResumeTaskId = checkpoint.taskId
        lastRebindResumeAtMillis = now
        rebindResumeJob?.cancel()
        rebindResumeJob = serviceScope.launch {
            kotlinx.coroutines.delay(REBIND_RESUME_DELAY_MS)
            if (!::controller.isInitialized) return@launch
            val stillRunning = AutomationStore.getCheckpointForServiceRebind()
            if (stillRunning?.taskId != checkpoint.taskId) return@launch
            AutomationStore.logger.info(
                "service_rebind_resume_started",
                attributes = mapOf("task_id_hash" to checkpoint.taskId.hashCode()),
            )
            controller.handle(AutomationCommand.ResumeSavedTask)
        }
    }

    private companion object {
        const val OCR_PROBE_INTERVAL_MS = 1_500L
        const val OCR_CACHE_TTL_MS = 4_000L
        const val REBIND_RESUME_DELAY_MS = 700L
        const val REBIND_RESUME_THROTTLE_MS = 15_000L
        var lastRebindResumeTaskId: String? = null
        var lastRebindResumeAtMillis: Long = 0L
    }
}
