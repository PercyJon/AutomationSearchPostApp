package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
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
    private var lastSignature: String? = null
    private val inspectionInFlight = AtomicBoolean(false)
    private val inspectionMutex = Mutex()
    private val ocrProbeInFlight = AtomicBoolean(false)
    private val lastOcrProbeAtMillis = AtomicLong(0L)

    override fun onServiceConnected() {
        super.onServiceConnected()
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
        AutomationStore.markServiceConnected()
        logger.info("accessibility_service_connected", attributes = mapOf("target" to TargetAppLauncher.DOUYIN_PACKAGE))
        commandJob = serviceScope.launch {
            AutomationStore.commands.collect { command ->
                controller.handle(command)
            }
        }
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
        if (!event.isWindowOrContentChange()) return

        // Accessibility callbacks run on the service main thread. A Douyin transition can expose
        // a very large node tree, so never walk it synchronously here: doing so can trigger an OEM
        // background-ANR and Android will kill the service. Keep at most one snapshot in flight;
        // the next event will provide a fresher tree once this one completes.
        if (!inspectionInFlight.compareAndSet(false, true)) return

        val root = rootInActiveWindow
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
        if (detector.detect(context).kind != PageKind.UNKNOWN) return context

        val engine = ocr ?: return context
        val now = SystemClock.uptimeMillis()
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
                val result = engine.recognize(bitmap)
                if (result.isEmpty) context else context.copy(ocrBlocks = result.toOcrTextBlocks())
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

    private companion object {
        const val OCR_PROBE_INTERVAL_MS = 1_500L
    }
}
