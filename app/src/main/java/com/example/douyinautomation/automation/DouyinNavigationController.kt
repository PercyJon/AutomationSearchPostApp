package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.BitmapFactory
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Deliberately bounded state machine. It inspects and navigates to a verified direct-message
 * page; when the operator supplies a non-empty message with Start, it performs one explicit
 * message send after that page is verified. It pauses on any risk, verification, login,
 * missing-selector, or timeout condition.
 */
class DouyinNavigationController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val logger: DiagnosticLogger,
    private val inspector: NodeTreeInspector,
    private val pageDetector: PageDetector,
    private val selector: SelectorEngine,
    private val gestures: GestureEngine,
    private val screenshotCapture: ScreenshotCapture,
    @Volatile private var ocr: MlKitOcrEngine?,
) {
    private val mutex = Mutex()
    private var phase = AutomationPhase.IDLE
    @Volatile private var taskActive = false
    private var keyword: String? = null
    private var pendingStartMessage: String = ""
    private var pausedPhase: AutomationPhase? = null
    private var timeoutJob: Job? = null
    private var initialObservationJob: Job? = null
    private var messageEntryPostconditionJob: Job? = null
    private var messageResultJob: Job? = null
    private var initialOcrAttempted = false
    private var latestContext: ScreenContext? = null
    private var userTabRevealAttempts = 0
    private var restrictedUserSkips = 0
    private var timeoutRecoveryAttempts = 0

    fun installOcrEngine(engine: MlKitOcrEngine?) {
        ocr = engine
    }

    /** True only while an explicitly started POC run is waiting for or performing a step. */
    fun shouldUseOcrFallback(): Boolean = taskActive && ocr != null

    suspend fun handle(command: AutomationCommand) = mutex.withLock {
        when (command) {
            is AutomationCommand.Start -> start(command.keyword, command.message)
            is AutomationCommand.SendMessage -> sendMessageOnce(command.message)
            AutomationCommand.Pause -> pause("Paused by the operator")
            AutomationCommand.Resume -> resume()
            AutomationCommand.Stop -> stop()
            AutomationCommand.CaptureDiagnostics -> captureDiagnosticsInternal("operator_request")
            AutomationCommand.DumpNodeTree -> dumpNodeTreeInternal("operator_request")
        }
    }

    suspend fun onScreenObserved(context: ScreenContext, detection: PageDetection) = mutex.withLock {
        AutomationStore.publishObservation(detection)

        if (!taskActive || detection.kind == PageKind.OUTSIDE_TARGET) return
        // Do not replace the last Douyin context when the diagnostics activity briefly becomes
        // the active window. This keeps operator-requested node dumps useful while the target app
        // remains underneath the translucent diagnostics surface.
        latestContext = context
        if (detection.kind == PageKind.HUMAN_INTERVENTION) {
            pause("Verification or risk screen detected; manual handoff required")
            return
        }
        if (detection.kind == PageKind.LOGIN) {
            pause("Douyin login is required; complete it manually before retrying")
            return
        }

        when (phase) {
            AutomationPhase.WAITING_FOR_HOME -> when (detection.kind) {
                PageKind.HOME -> openSearch(context)
                PageKind.SEARCH_ENTRY -> enterKeyword(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_SEARCH_ENTRY -> when (detection.kind) {
                PageKind.SEARCH_ENTRY -> enterKeyword(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_SEARCH_RESULTS -> when (detection.kind) {
                PageKind.SEARCH_RESULTS -> selectUserTab(context)
                PageKind.USER_RESULTS -> selectVisibleUser(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_USER_RESULTS -> when (detection.kind) {
                PageKind.USER_RESULTS -> selectVisibleUser(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_PROFILE -> when (detection.kind) {
                PageKind.USER_PROFILE -> openPrivateMessage(context)
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_DIRECT_MESSAGE -> when (detection.kind) {
                PageKind.DIRECT_MESSAGE -> completeAtMessagePage()
                PageKind.PRIVATE_MESSAGE_RESTRICTED -> skipRestrictedUser("Douyin requires following before private messaging")
                PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                    "Douyin rejected the message because of the recipient's messaging settings",
                )
                PageKind.USER_PROFILE -> scheduleMessageEntryPostconditionCheck()
                else -> Unit
            }

            AutomationPhase.WAITING_FOR_MESSAGE_RESULT -> when (detection.kind) {
                PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                    "Douyin rejected the message because of the recipient's messaging settings",
                )
                PageKind.HUMAN_INTERVENTION -> pause("A verification or risk screen appeared after sending; manual handoff required")
                else -> Unit
            }

            else -> Unit
        }
    }

    private suspend fun start(searchKeyword: String, startMessage: String) {
        val sanitizedKeyword = searchKeyword.trim()
        if (sanitizedKeyword.isEmpty()) {
            AutomationStore.publishFailure("Enter a search keyword before starting the POC.")
            return
        }
        if (!TargetAppLauncher.isInstalled(service)) {
            AutomationStore.publishFailure("Douyin is not installed on this device.")
            logger.error("target_not_installed")
            return
        }

        if (taskActive) {
            logger.warn("start_ignored_active")
            return
        }

        taskActive = true
        keyword = sanitizedKeyword
        pendingStartMessage = startMessage.trim()
        pausedPhase = null
        initialOcrAttempted = false
        userTabRevealAttempts = 0
        restrictedUserSkips = 0
        timeoutRecoveryAttempts = 0
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        initialObservationJob?.cancel()
        phase = AutomationPhase.LAUNCHING_TARGET
        AutomationStore.publishPhase(phase)
        logger.info("poc_started", attributes = mapOf("target" to TargetAppLauncher.DOUYIN_PACKAGE))

        when (val result = TargetAppLauncher.launch(service)) {
            LaunchResult.Started -> await(
                nextPhase = AutomationPhase.WAITING_FOR_HOME,
                timeoutDescription = "Douyin home or search page was not detected",
            ).also {
                logger.info("initial_observation_scheduled")
                scheduleInitialObservation()
            }

            is LaunchResult.Failed -> pause("Could not open Douyin: ${result.reason}")
        }
    }

    private suspend fun resume() {
        if (phase != AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF || keyword.isNullOrBlank()) {
            AutomationStore.publishFailure("There is no paused run ready to resume.")
            logger.warn("resume_rejected", message = "No paused run is available")
            return
        }

        val context = latestContext ?: currentWindowContext()
        if (context == null) {
            AutomationStore.publishManualHandoff("No active Douyin window is available; review the screen before resuming")
            return
        }

        val detection = pageDetector.detect(context)
        AutomationStore.publishObservation(detection)
        val decision = AutomationResumePolicy.decide(detection)
        if (!decision.allowed || decision.phase == null) {
            AutomationStore.publishManualHandoff(decision.reason)
            logger.warn("resume_rejected", message = decision.reason)
            return
        }

        taskActive = true
        phase = decision.phase
        pausedPhase = null
        AutomationStore.publishPhase(phase)
        logger.info("poc_resumed", attributes = mapOf("phase" to phase.name))
        if (phase == AutomationPhase.WAITING_FOR_DIRECT_MESSAGE && detection.kind == PageKind.DIRECT_MESSAGE) {
            completeAtMessagePage()
        }
    }

    private suspend fun openSearch(context: ScreenContext) {
        phase = AutomationPhase.OPENING_SEARCH
        AutomationStore.publishPhase(phase)
        val semanticOutcome = clickSelector(context, DouyinSelectors.searchEntry)
        val outcome = if (semanticOutcome.succeeded) {
            semanticOutcome
        } else {
            logger.warn(
                "search_entry_selector_fallback",
                message = "Semantic search selector was unavailable; using the operator-confirmed normalized home icon position",
            )
            tapNormalizedGuarded(DouyinSelectors.searchEntryNormalizedFallback, "search_entry_fallback")
        }
        if (!outcome.succeeded) {
            pause("Could not find the Douyin search entry: ${outcome.reason}")
            return
        }
        logger.info("search_entry_opened", attributes = mapOf("route" to outcome.route))
        await(AutomationPhase.WAITING_FOR_SEARCH_ENTRY, "Search input did not appear after opening search")

        // Some Douyin builds emit the window/content event before the gesture callback returns.
        // In that ordering the state machine has not entered WAITING_FOR_SEARCH_ENTRY yet, so the
        // event is intentionally ignored and no later event may arrive. Poll the live tree briefly
        // after the action and advance from the verified post-condition instead of timing out.
        for (attempt in 1..SEARCH_ENTRY_POSTCONDITION_ATTEMPTS) {
            delay(if (attempt == 1) 250L else SEARCH_ENTRY_POSTCONDITION_INTERVAL_MS)
            val postSearchContext = currentWindowContext() ?: continue
            val postSearchDetection = pageDetector.detect(postSearchContext)
            logger.info(
                "search_entry_postcondition",
                attributes = mapOf(
                    "attempt" to attempt,
                    "page" to postSearchDetection.kind.name,
                    "confidence" to postSearchDetection.confidence,
                    "nodes" to postSearchContext.nodes.size,
                ),
            )
            if (postSearchDetection.kind == PageKind.SEARCH_ENTRY) {
                latestContext = postSearchContext
                AutomationStore.publishObservation(postSearchDetection)
                enterKeyword(postSearchContext)
                return
            }
        }
    }

    private suspend fun enterKeyword(
        context: ScreenContext,
        preserveTimeoutRecoveryBudget: Boolean = false,
    ) {
        val activeKeyword = keyword
        if (activeKeyword.isNullOrBlank()) {
            pause("Search keyword was lost; stop and start the POC again")
            return
        }

        phase = AutomationPhase.ENTERING_KEYWORD
        AutomationStore.publishPhase(phase)
        val selection = selector.select(context, DouyinSelectors.searchInput)
        val selected = selection.node
        if (selected == null) {
            pause("Could not find an editable search field")
            return
        }

        val setTextResult = withLiveNode(selected) { liveNode ->
            gestures.setText(liveNode, activeKeyword)
        }
        if (!setTextResult.succeeded) {
            pause("Could not enter the search keyword: ${setTextResult.reason}")
            return
        }

        // ACTION_SET_TEXT can return true while a transient Douyin search surface is still
        // showing its hint/default suggestion. Read the live field before submitting; a default
        // term such as "红木家具厂" must never be sent in place of the requested keyword.
        if (!ensureSearchKeyword(selected, activeKeyword)) {
            pause("The search field did not contain the requested keyword; review the screen and retry")
            return
        }

        // The search-entry page may contain a full friend/profile suggestion. It is deliberately
        // ignored: after the requested text is verified, submit only through the visible
        // top-right Search control (semantic bounds first, same-control normalized fallback).
        // Never tap a suggestion, move focus elsewhere, press BACK, or submit through IME_ENTER.
        delay(350L)
        val submitContext = currentWindowContext() ?: context
        // The top-right label is a non-clickable TextView on this Douyin build. Select it
        // semantically, then deliberately use its bounds so a no-op ACTION_CLICK cannot be
        // mistaken for a successful submit.
        val buttonResult = tapSelectorBounds(submitContext, searchSubmitSelector)
        val submitResult = if (buttonResult.succeeded) {
            buttonResult
        } else {
            val normalizedFallback = tapNormalizedGuarded(
                DouyinSelectors.searchSubmitNormalizedFallback,
                "search_submit_initial_fallback",
            )
            normalizedFallback
        }
        if (!submitResult.succeeded) {
            pause("Search was entered but could not be submitted: ${submitResult.reason}")
            return
        }

        // A dispatched gesture can complete while the IME is still animating and leave the
        // suggestion page visible. Confirm the post-condition; retry only on a still-classified
        // search-entry page, never on a login/risk/unknown state.
        delay(500L)
        val postSubmitContext = currentWindowContext()
        if (postSubmitContext != null && pageDetector.detect(postSubmitContext).kind == PageKind.SEARCH_ENTRY) {
            logger.warn("search_submit_postcondition_retry", message = "Search entry remained visible after the first submit gesture")
            val retry = retrySearchSubmit(postSubmitContext)
            if (!retry.succeeded) {
                pause("Search entry remained visible and the retry gesture was rejected")
                return
            }
        }

        logger.info("search_submitted", attributes = mapOf("route" to submitResult.route))
        await(
            nextPhase = AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
            timeoutDescription = "Search results were not detected after submitting the keyword",
            resetRecoveryBudget = !preserveTimeoutRecoveryBudget,
        )
    }

    private suspend fun selectUserTab(context: ScreenContext) {
        phase = AutomationPhase.SELECTING_USER_TAB
        AutomationStore.publishPhase(phase)
        // The previous observation can be stale after the result tab strip animates. Resolve the
        // live tree immediately before selecting or clicking a category.
        val liveContext = currentWindowContext() ?: context
        val selection = selector.select(liveContext, DouyinSelectors.userTab)
        val candidate = selection.node
        val outcome = if (candidate == null) {
            ActionOutcome.failure(selection.reasons.joinToString())
        } else if (!isVisibleUserTabCandidate(candidate, selection, liveContext)) {
            logger.warn(
                "user_tab_candidate_rejected",
                message = "A user-like label was not an exact, fully visible category tab",
                attributes = mapOf("bounds" to candidate.bounds, "reasons" to selection.reasons.joinToString()),
            )
            ActionOutcome.failure("User label was not an exact fully visible tab")
        } else {
            logger.info(
                "user_tab_candidate",
                attributes = mapOf("text" to candidate.text.orEmpty(), "bounds" to candidate.bounds),
            )
            withLiveNode(candidate) { liveNode -> gestures.click(liveNode, candidate.bounds) }
        }
        if (!outcome.succeeded) {
            val reveal = revealUserTab(context)
            if (reveal.succeeded) {
                logger.info(
                    "user_tab_reveal_requested",
                    attributes = mapOf(
                        "route" to reveal.route,
                        "attempt" to userTabRevealAttempts,
                    ),
                )
                await(
                    nextPhase = AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
                    timeoutDescription = "The User tab did not appear after scrolling the result categories",
                )
                return
            }
            pause("Could not find the User tab in search results: ${outcome.reason}; ${reveal.reason}")
            return
        }
        logger.info("user_tab_opened", attributes = mapOf("route" to outcome.route))

        // Some Douyin builds switch the ViewPager without emitting a follow-up accessibility
        // content-change event, and the first tree after the click can still be the old results
        // page while the new tab is animating. Poll briefly for a verified USER_RESULTS
        // post-condition so the run does not wait until the step timeout. This does not bypass
        // verification or click a result blindly.
        for (attempt in 1..USER_RESULTS_POSTCONDITION_ATTEMPTS) {
            delay(if (attempt == 1) 450L else USER_RESULTS_POSTCONDITION_INTERVAL_MS)
            val postTabContext = currentWindowContext() ?: continue
            val postTabDetection = pageDetector.detect(postTabContext)
            logger.info(
                "user_tab_postcondition",
                attributes = mapOf(
                    "attempt" to attempt,
                    "page" to postTabDetection.kind.name,
                    "confidence" to postTabDetection.confidence,
                    "nodes" to postTabContext.nodes.size,
                ),
            )
            if (postTabDetection.kind == PageKind.USER_RESULTS) {
                latestContext = postTabContext
                AutomationStore.publishObservation(postTabDetection)
                selectVisibleUser(postTabContext)
                return
            }
        }
        await(
            nextPhase = AutomationPhase.WAITING_FOR_USER_RESULTS,
            timeoutDescription = "User search results were not detected after choosing the User tab",
        )
    }

    private fun isVisibleUserTabCandidate(
        candidate: NodeSnapshot,
        selection: SelectionResult,
        context: ScreenContext,
    ): Boolean {
        val directLabel = listOfNotNull(candidate.text, candidate.contentDescription, candidate.hintText)
            .any { raw -> DouyinLabels.users.any { label -> TextNormalizer.normalize(raw) == TextNormalizer.normalize(label) } }
        val ancestorLabel = selection.reasons.any { reason ->
            DouyinLabels.users.any { label -> reason == "label=$label" }
        }
        val bounds = candidate.bounds
        val tabStrip = context.nodes.firstOrNull { node ->
            node.className?.contains("HorizontalScrollView", ignoreCase = true) == true &&
                node.bounds.top <= bounds.top &&
                node.bounds.bottom >= bounds.bottom
        }
        val viewportLeft = tabStrip?.bounds?.left ?: 0
        val viewportRight = tabStrip?.bounds?.right
            ?: (context.screenSize.width * USER_TAB_VIEWPORT_FALLBACK_RIGHT_RATIO).toInt()
        val fullyOnScreen = bounds.left >= 0 &&
            bounds.top >= 0 &&
            bounds.right <= context.screenSize.width &&
            bounds.bottom <= context.screenSize.height &&
            bounds.width > 0 &&
            bounds.height > 0
        val insideVisibleTabStrip = bounds.left >= viewportLeft && bounds.right <= viewportRight
        return (directLabel || ancestorLabel) && fullyOnScreen && insideVisibleTabStrip
    }

    private suspend fun revealUserTab(context: ScreenContext): ActionOutcome {
        userTabRevealAttempts += 1
        val liveContext = currentWindowContext() ?: context
        val strip = selector.select(liveContext, DouyinSelectors.searchResultTabStrip).node

        // ACTION_SCROLL_FORWARD is intentionally not used here: Android exposes it as a
        // page-sized operation on some HorizontalScrollView implementations, which can jump
        // past 用户 to the end of the category list. Instead, use a bounded gesture inside the
        // semantic strip viewport. The travel is derived from the current viewport width and
        // clamped to 70..200 physical pixels, so it remains a small one-tab reveal on different
        // displays and never starts on the trailing grid/filter controls.
        val screenWidth = liveContext.screenSize.width.coerceAtLeast(1)
        val screenHeight = liveContext.screenSize.height.coerceAtLeast(1)
        val viewportLeft = strip?.bounds?.left?.coerceIn(0, screenWidth - 1)
            ?: (screenWidth * 0.04f).toInt()
        val viewportRight = strip?.bounds?.right?.coerceIn(viewportLeft + 1, screenWidth)
            ?: (screenWidth * 0.82f).toInt().coerceAtLeast(viewportLeft + 1)
        val viewportTop = strip?.bounds?.top?.coerceIn(0, screenHeight - 1)
            ?: (screenHeight * 0.10f).toInt()
        val viewportBottom = strip?.bounds?.bottom?.coerceIn(viewportTop + 1, screenHeight)
            ?: (screenHeight * 0.18f).toInt().coerceAtLeast(viewportTop + 1)
        val viewportWidth = (viewportRight - viewportLeft).coerceAtLeast(1)
        val travelPx = (viewportWidth * 0.20f).toInt().coerceIn(70, 200)
        val edgeInsetPx = (viewportWidth * 0.16f).toInt().coerceAtLeast(12)
        val startPx = (viewportRight - edgeInsetPx).coerceAtLeast(viewportLeft + travelPx + 1)
        val endPx = (startPx - travelPx).coerceAtLeast(viewportLeft + edgeInsetPx)
        val centerYPx = (viewportTop + viewportBottom) / 2
        val startX = startPx.toFloat() / screenWidth
        val endX = endPx.toFloat() / screenWidth
        val centerY = centerYPx.toFloat() / screenHeight

        logger.info(
            "user_tab_bounded_swipe",
            attributes = mapOf(
                "attempt" to userTabRevealAttempts,
                "travel_px" to travelPx,
                "viewport_left" to viewportLeft,
                "viewport_right" to viewportRight,
            ),
        )
        return swipeNormalizedGuarded(
            startX = startX,
            startY = centerY,
            endX = endX,
            endY = centerY,
            durationMs = 450L,
            tag = "user_tab_reveal",
        )
    }

    private suspend fun selectVisibleUser(
        context: ScreenContext,
        minimumAnchorTop: Float? = null,
    ) {
        phase = AutomationPhase.SELECTING_USER_RESULT
        AutomationStore.publishPhase(phase)
        // Prefer the structural row route whenever it is available. It deliberately targets the
        // name/profile content area, not the avatar: a live badge on an avatar can route to a live
        // room instead of the user's profile.
        var rowContext = context
        var rowMatch: StructuralUserRowMatch? = null
        for (attempt in 1..USER_ROW_POSTCONDITION_ATTEMPTS) {
            if (attempt > 1) delay(USER_ROW_POSTCONDITION_INTERVAL_MS)
            val liveContext = currentWindowContext() ?: rowContext
            val liveDetection = pageDetector.detect(liveContext)
            if (liveDetection.kind != PageKind.USER_RESULTS) {
                logger.info(
                    "user_result_row_postcondition",
                    attributes = mapOf("attempt" to attempt, "page" to liveDetection.kind.name, "nodes" to liveContext.nodes.size),
                )
                break
            }
            rowContext = liveContext
            rowMatch = minimumAnchorTop?.let { top ->
                StructuralUserRowDetector.findAfter(liveContext, top)
            } ?: StructuralUserRowDetector.find(liveContext)
            logger.info(
                "user_result_row_postcondition",
                attributes = mapOf(
                    "attempt" to attempt,
                    "page" to liveDetection.kind.name,
                    "nodes" to liveContext.nodes.size,
                    "anchor_count" to StructuralUserRowDetector.anchorCount(liveContext),
                    "matched" to (rowMatch != null),
                ),
            )
            if (rowMatch != null) break
        }
        if (rowMatch != null) {
            val followState = StructuralUserRowDetector.followActionState(rowContext, rowMatch!!)
            logger.info(
                "user_result_follow_action",
                attributes = mapOf(
                    "state" to followState.name,
                    "anchor_bounds" to rowMatch!!.anchor.bounds,
                ),
            )
            if (followState == UserFollowActionState.FOLLOW_BACK) {
                skipFollowBackUser(rowContext, rowMatch!!)
                return
            }
        }
        val outcome = if (rowMatch != null) {
            logger.info(
                "user_result_row_match",
                attributes = mapOf("source" to rowMatch!!.source.name, "anchor_count" to StructuralUserRowDetector.anchorCount(rowContext)),
            )
            tapUserRowContent(rowMatch!!.row, rowContext)
        } else {
            logger.warn(
                "user_result_row_match_failed",
                message = "No wide user-result row matched the follow-button anchor; semantic name selection will be attempted",
                attributes = mapOf("anchor_count" to StructuralUserRowDetector.anchorCount(rowContext), "nodes" to rowContext.nodes.size),
            )
            clickSelector(rowContext, DouyinSelectors.userResult)
        }
        if (!outcome.succeeded) {
            pause(
                "No unambiguous visible user result was found. Select a result manually, then review diagnostics.",
            )
            return
        }
        logger.info("user_result_opened", attributes = mapOf("route" to outcome.route))
        await(
            nextPhase = AutomationPhase.WAITING_FOR_PROFILE,
            timeoutDescription = "A user profile was not detected after choosing the result",
        )
    }

    /**
     * A “回关” button means the account follows us but we do not follow it. Do not open that row;
     * first try the next visible row in the current viewport, then use a small bounded scroll only
     * when no subsequent row is exposed by the tree.
     */
    private suspend fun skipFollowBackUser(context: ScreenContext, match: StructuralUserRowMatch) {
        restrictedUserSkips += 1
        logger.warn(
            "user_result_follow_back_skipped",
            message = "The first visible user exposes a 回关 action; skipping without opening the profile",
            attributes = mapOf("skipped_users" to restrictedUserSkips, "anchor_bounds" to match.anchor.bounds),
        )
        if (restrictedUserSkips > MAX_RESTRICTED_USER_SKIPS) {
            pause("Too many consecutive 回关 users were skipped; review the results manually")
            return
        }

        val nextVisible = StructuralUserRowDetector.findAfter(context, match.anchor.bounds.bottom.toFloat())
        if (nextVisible != null) {
            phase = AutomationPhase.WAITING_FOR_USER_RESULTS
            AutomationStore.publishPhase(phase)
            selectVisibleUser(context, minimumAnchorTop = match.anchor.bounds.bottom.toFloat())
            return
        }

        val scroll = swipeNormalizedGuarded(
            startX = 0.50f,
            startY = 0.68f,
            endX = 0.50f,
            endY = 0.52f,
            durationMs = 360L,
            tag = "follow_back_skip",
        )
        logger.info(
            "user_result_follow_back_next_requested",
            attributes = mapOf("route" to scroll.route, "skipped_users" to restrictedUserSkips),
        )
        if (!scroll.succeeded) {
            pause("Could not advance after skipping a 回关 user")
            return
        }
        delay(USER_NEXT_RESULT_DELAY_MS)
        val nextContext = currentWindowContext()
        if (nextContext == null || pageDetector.detect(nextContext).kind != PageKind.USER_RESULTS) {
            pause("The next user result page was not detected after skipping a 回关 user")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        selectVisibleUser(nextContext)
    }

    private suspend fun tapUserRowContent(row: NodeSnapshot, context: ScreenContext): ActionOutcome {
        val screenWidth = context.screenSize.width.coerceAtLeast(1)
        val screenHeight = context.screenSize.height.coerceAtLeast(1)
        val rowWidth = row.bounds.width.coerceAtLeast(1)
        val rowHeight = row.bounds.height.coerceAtLeast(1)
        // Current Douyin rows place the avatar around the left fifth and the follow button on the
        // right quarter. Keep the tap in the name/metadata band between those areas. Ratios are
        // normalized to the row/display, so this remains usable across common screen sizes.
        val safeLeft = max(
            row.bounds.left,
            row.bounds.left + (rowWidth * USER_ROW_CONTENT_LEFT_RATIO).toInt(),
        ).coerceIn(0, screenWidth - 1)
        val safeRight = min(
            row.bounds.right,
            (screenWidth * USER_ROW_SAFE_TAP_RIGHT_RATIO).toInt(),
        ).coerceIn(safeLeft + 1, screenWidth)
        val safeTop = max(
            row.bounds.top,
            row.bounds.top + (rowHeight * USER_ROW_CONTENT_TOP_RATIO).toInt(),
        ).coerceIn(0, screenHeight - 1)
        val safeBottom = min(
            row.bounds.bottom,
            row.bounds.top + (rowHeight * USER_ROW_CONTENT_BOTTOM_RATIO).toInt(),
        ).coerceIn(safeTop + 1, screenHeight)
        val safeBounds = ScreenBounds(
            left = safeLeft,
            top = safeTop,
            right = safeRight,
            bottom = safeBottom,
        )
        logger.warn(
            "user_result_structural_fallback",
            message = "User row text is not exposed; tapping the name/profile content area (avatar excluded)",
            attributes = mapOf(
                "row_bounds" to row.bounds,
                "tap_bounds" to safeBounds,
                "avatar_excluded" to true,
            ),
        )
        if (waitForTargetWindow("user_result_name_area") == null) {
            return ActionOutcome.failure("Douyin window is temporarily covered or unavailable")
        }
        val gesture = gestures.tapBounds(safeBounds)
        return if (gesture.succeeded) {
            ActionOutcome.success("user_row_name_area_gesture")
        } else {
            gesture
        }
    }

    private suspend fun openPrivateMessage(context: ScreenContext) {
        phase = AutomationPhase.OPENING_MESSAGE_ENTRY
        AutomationStore.publishPhase(phase)
        val semanticOutcome = clickSelector(context, DouyinSelectors.privateMessageEntry)
        val outcome = if (semanticOutcome.succeeded) {
            semanticOutcome
        } else {
            val iconNode = ProfileMessageEntryFallback.iconNode(context)
            if (iconNode != null) {
                logger.warn(
                    "private_message_icon_fallback",
                    message = "Profile exposes a compact unlabeled message icon; tapping the right-side profile action",
                    attributes = mapOf("route" to "profile_action_icon_node", "bounds" to iconNode.bounds),
                )
                withLiveNode(iconNode) { liveNode -> gestures.click(liveNode, iconNode.bounds) }
            } else {
                val follow = selector.select(context, DouyinSelectors.profileFollowAction).node
                val point = ProfileMessageEntryFallback.normalizedPoint(context.screenSize, follow?.bounds)
                logger.warn(
                    "private_message_normalized_fallback",
                    message = "Profile message label and icon node are unavailable; using the constrained profile action region",
                    attributes = mapOf("x" to point.x, "y" to point.y, "follow_present" to (follow != null)),
                )
                tapNormalizedGuarded(point, "private_message_fallback")
            }
        }
        if (!outcome.succeeded) {
            pause("Could not find the profile's private-message entry: ${outcome.reason}")
            return
        }
        logger.info("private_message_entry_opened", attributes = mapOf("route" to outcome.route))
        await(
            nextPhase = AutomationPhase.WAITING_FOR_DIRECT_MESSAGE,
            timeoutDescription = "The direct-message page was not detected after opening the entry",
        )
        // If the tap opens a follow-gate toast/dialog without emitting a semantic page-change
        // event, inspect the profile again shortly after the action. Any still-visible profile is
        // treated as unavailable and skipped rather than blocking the whole user pipeline.
        scheduleMessageEntryPostconditionCheck()
    }

    /**
     * Re-submit a still-visible search entry through the same top-right Search control only.
     *
     * This recovery is intentionally narrow. A suggestion row, IME_ENTER, or BACK can change
     * focus or navigate to an unrelated surface, so none of them is an acceptable substitute for
     * the Search button.
     */
    private suspend fun retrySearchSubmit(context: ScreenContext): ActionOutcome {
        var lastOutcome = ActionOutcome.failure("No Search button route was available")
        repeat(2) { attempt ->
            val buttonContext = currentWindowContext() ?: context
            lastOutcome = tapSelectorBounds(buttonContext, searchSubmitSelector)
            if (lastOutcome.succeeded && searchResultsPostconditionReached("search_button_bounds_retry_${attempt + 1}")) {
                return lastOutcome
            }
            if (lastOutcome.succeeded) {
                logger.warn(
                    "search_submit_route_noop",
                    message = "The top-right Search control was tapped but the search-entry page remained visible",
                    attributes = mapOf("route" to lastOutcome.route, "attempt" to attempt + 1),
                )
            }
            if (attempt == 0) delay(350L)
        }

        // Last resort is the same top-right control expressed as a normalized point. It is not a
        // suggestion or a focus-changing action and is therefore safe to use only while the
        // search-entry page is still present.
        lastOutcome = tapNormalizedGuarded(
            DouyinSelectors.searchSubmitNormalizedFallback,
            "search_submit_same_button_fallback",
        )
        if (lastOutcome.succeeded && searchResultsPostconditionReached("search_button_normalized_fallback")) {
            return lastOutcome
        }
        logger.warn(
            "search_submit_route_noop",
            message = "The top-right Search control did not leave the search-entry page after bounded retries",
            attributes = mapOf("route" to lastOutcome.route),
        )
        return ActionOutcome.failure("Top-right Search control did not open results")
    }

    /** Returns true only after a live tree reports a results page, not merely a completed gesture. */
    private suspend fun searchResultsPostconditionReached(route: String): Boolean {
        repeat(SEARCH_SUBMIT_POSTCONDITION_ATTEMPTS) { attempt ->
            delay(if (attempt == 0) SEARCH_SUBMIT_POSTCONDITION_DELAY_MS else SEARCH_SUBMIT_POSTCONDITION_INTERVAL_MS)
            val liveContext = currentWindowContext() ?: return@repeat
            val detection = pageDetector.detect(liveContext)
            logger.info(
                "search_submit_postcondition_probe",
                attributes = mapOf(
                    "route" to route,
                    "attempt" to attempt + 1,
                    "page" to detection.kind.name,
                    "confidence" to detection.confidence,
                ),
            )
            if (detection.kind == PageKind.SEARCH_RESULTS || detection.kind == PageKind.USER_RESULTS) {
                latestContext = liveContext
                AutomationStore.publishObservation(detection)
                return true
            }
        }
        return false
    }

    /**
     * Sends one operator-provided message from the verified conversation. The command is separate
     * from the M0 navigation run, so an empty message never results in an accidental send.
     */
    private suspend fun sendMessageOnce(rawMessage: String) {
        val message = rawMessage.trim()
        if (message.isEmpty()) {
            AutomationStore.publishFailure("Enter a message before sending")
            logger.warn("message_send_rejected", message = "The operator did not provide a message")
            return
        }
        if (message.length > MAX_MESSAGE_LENGTH) {
            AutomationStore.publishFailure("Message is too long for the one-message POC")
            logger.warn("message_send_rejected", message = "The operator message exceeded the safe length limit")
            return
        }
        if (phase != AutomationPhase.COMPLETED_AT_MESSAGE_PAGE) {
            AutomationStore.publishFailure("Open and verify a private-message page before sending")
            logger.warn("message_send_rejected", message = "No verified direct-message page is available")
            return
        }

        taskActive = true
        phase = AutomationPhase.SENDING_MESSAGE
        AutomationStore.publishPhase(phase)

        var context = currentWindowContext()
        if (context == null || pageDetector.detect(context).kind != PageKind.DIRECT_MESSAGE) {
            when (val launch = TargetAppLauncher.launch(service)) {
                LaunchResult.Started -> {
                    delay(MESSAGE_TARGET_RESTORE_DELAY_MS)
                    context = currentWindowContext()
                }
                is LaunchResult.Failed -> {
                    pause("Could not restore the verified private-message page: ${launch.reason}")
                    return
                }
            }
        }
        val directContext = context
        if (directContext == null || pageDetector.detect(directContext).kind != PageKind.DIRECT_MESSAGE) {
            pause("The verified private-message page is no longer visible")
            return
        }

        val input = selector.select(directContext, DouyinSelectors.messageInput).node
        if (input == null) {
            pause("Could not find the private-message input")
            return
        }
        val setText = withLiveNode(input) { liveNode -> gestures.setText(liveNode, message) }
        if (!setText.succeeded) {
            pause("Could not place the message in the private-message input")
            return
        }

        delay(MESSAGE_INPUT_SETTLE_DELAY_MS)
        val refreshedContext = currentWindowContext() ?: directContext
        val sendButtonOutcome = clickSelector(refreshedContext, DouyinSelectors.messageSendAction)
        val sendOutcome = if (sendButtonOutcome.succeeded) {
            sendButtonOutcome
        } else {
            val refreshedInput = selector.select(refreshedContext, DouyinSelectors.messageInput).node
            if (refreshedInput == null) {
                ActionOutcome.failure("Message input disappeared before submit")
            } else {
                withLiveNode(refreshedInput) { liveNode -> gestures.submitText(liveNode) }
            }
        }
        if (!sendOutcome.succeeded) {
            pause("Could not find a usable message send action")
            return
        }

        logger.info("message_send_submitted", attributes = mapOf("route" to sendOutcome.route))
        timeoutJob?.cancel()
        phase = AutomationPhase.WAITING_FOR_MESSAGE_RESULT
        AutomationStore.publishPhase(phase)
        scheduleMessageResultCheck(message)
    }

    private suspend fun completeAtMessagePage() {
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        messageResultJob?.cancel()
        keyword = null
        phase = AutomationPhase.COMPLETED_AT_MESSAGE_PAGE
        AutomationStore.publishPhase(phase)
        val message = pendingStartMessage
        pendingStartMessage = ""
        if (message.isBlank()) {
            taskActive = false
            logger.info("poc_completed", message = "Direct-message page verified; no message was created or sent")
        } else {
            logger.info(
                "message_send_auto_requested",
                message = "A non-empty Start message was supplied; sending one message after page verification",
                attributes = mapOf("message_length" to message.length),
            )
            sendMessageOnce(message)
        }
    }

    private fun completeMessageSent() {
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        messageResultJob?.cancel()
        taskActive = false
        keyword = null
        phase = AutomationPhase.COMPLETED_MESSAGE_SENT
        AutomationStore.publishPhase(phase)
        logger.info("message_send_completed", message = "One operator-requested message was verified in the conversation")
    }

    private fun scheduleMessageResultCheck(expectedMessage: String) {
        messageResultJob?.cancel()
        messageResultJob = scope.launch {
            repeat(MESSAGE_RESULT_ATTEMPTS) { attempt ->
                delay(MESSAGE_RESULT_INTERVAL_MS)
                mutex.withLock {
                    if (!taskActive || phase != AutomationPhase.WAITING_FOR_MESSAGE_RESULT) return@withLock
                    val context = currentWindowContext() ?: return@withLock
                    val detection = pageDetector.detect(context)
                    logger.info(
                        "message_send_postcondition",
                        attributes = mapOf(
                            "attempt" to (attempt + 1),
                            "page" to detection.kind.name,
                            "confidence" to detection.confidence,
                            "nodes" to context.nodes.size,
                        ),
                    )
                    when (detection.kind) {
                        PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                            "Douyin rejected the message because of the recipient's messaging settings",
                        )
                        PageKind.DIRECT_MESSAGE -> if (MessageSendSuccessDetector.matches(context, expectedMessage)) {
                            completeMessageSent()
                        }
                        else -> Unit
                    }
                }
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_MESSAGE_RESULT) return@launch
            }
            mutex.withLock {
                if (taskActive && phase == AutomationPhase.WAITING_FOR_MESSAGE_RESULT) {
                    pause("The message result was not verified; review the conversation manually")
                }
            }
        }
    }

    private fun scheduleMessageEntryPostconditionCheck() {
        if (messageEntryPostconditionJob?.isActive == true) return
        messageEntryPostconditionJob = scope.launch {
            delay(MESSAGE_ENTRY_POSTCONDITION_DELAY_MS)
            mutex.withLock {
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_DIRECT_MESSAGE) return@withLock
                val context = currentWindowContext() ?: return@withLock
                val detection = pageDetector.detect(context)
                logger.info(
                    "private_message_postcondition",
                    attributes = mapOf("page" to detection.kind.name, "confidence" to detection.confidence, "nodes" to context.nodes.size),
                )
                when (detection.kind) {
                    PageKind.DIRECT_MESSAGE -> completeAtMessagePage()
                    PageKind.PRIVATE_MESSAGE_RESTRICTED -> skipRestrictedUser("Douyin requires following before private messaging")
                    PageKind.MESSAGE_SEND_FAILED -> skipMessageSendFailure(
                        "Douyin rejected the message because of the recipient's messaging settings",
                    )
                    PageKind.USER_PROFILE -> skipRestrictedUser("Profile remained open after the message action; treating this user as unavailable")
                    else -> Unit
                }
            }
        }
    }

    /**
     * Returns to the user results and advances the list by a bounded vertical gesture. This is
     * intentionally limited to follow-gated/unavailable profiles; it never follows an account or
     * attempts to bypass the restriction.
     */
    private suspend fun skipRestrictedUser(reason: String) {
        if (!taskActive) return
        val currentJob = coroutineContext[Job]
        timeoutJob?.takeUnless { it === currentJob }?.cancel()
        messageEntryPostconditionJob?.takeUnless { it === currentJob }?.cancel()
        messageEntryPostconditionJob = null
        restrictedUserSkips += 1
        logger.warn(
            "private_message_unavailable",
            message = reason,
            attributes = mapOf("skipped_users" to restrictedUserSkips),
        )
        if (restrictedUserSkips > MAX_RESTRICTED_USER_SKIPS) {
            pause("Too many consecutive profiles cannot receive private messages; review the results manually")
            return
        }

        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            pause("Could not leave the unavailable user profile")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        delay(USER_PROFILE_BACK_DELAY_MS)
        val resultsContext = currentWindowContext()
        if (resultsContext == null || pageDetector.detect(resultsContext).kind != PageKind.USER_RESULTS) {
            pause("User results did not return after skipping an unavailable profile")
            return
        }

        // Move to the next visible row without paging through the entire list. The next
        // selection still uses StructuralUserRowDetector and the avatar-excluding tap band.
        val scroll = swipeNormalizedGuarded(
            startX = 0.50f,
            startY = 0.76f,
            endX = 0.50f,
            endY = 0.48f,
            durationMs = 420L,
            tag = "restricted_user_skip",
        )
        logger.info("user_result_next_requested", attributes = mapOf("route" to scroll.route, "skipped_users" to restrictedUserSkips))
        if (!scroll.succeeded) {
            pause("Could not advance to the next user result")
            return
        }
        delay(USER_NEXT_RESULT_DELAY_MS)
        val nextContext = currentWindowContext()
        if (nextContext == null || pageDetector.detect(nextContext).kind != PageKind.USER_RESULTS) {
            pause("The next user result page was not detected")
            return
        }
        selectVisibleUser(nextContext)
    }

    /**
     * Handles a delivery failure that appears after a message bubble is created. Unlike a
     * profile-level follow gate, the current screen is the conversation itself, so two bounded
     * back actions may be needed (conversation -> profile -> user results). The method never
     * retries the send or follows the account to bypass its privacy setting.
     *
     * The one-message M1 sender uses this branch after an explicit send command.
     */
    private suspend fun skipMessageSendFailure(reason: String) {
        if (!taskActive) return
        timeoutJob?.cancel()
        val currentJob = coroutineContext[Job]
        messageEntryPostconditionJob?.takeUnless { it === currentJob }?.cancel()
        messageResultJob?.takeUnless { it === currentJob }?.cancel()
        messageEntryPostconditionJob = null
        messageResultJob = null
        restrictedUserSkips += 1
        logger.warn(
            "private_message_send_failed",
            message = reason,
            attributes = mapOf("skipped_users" to restrictedUserSkips),
        )
        if (restrictedUserSkips > MAX_RESTRICTED_USER_SKIPS) {
            pause("Too many message deliveries failed; review the results manually")
            return
        }

        var resultsContext: ScreenContext? = null
        for (attempt in 0 until MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE) {
            val currentContext = currentWindowContext()
            if (currentContext != null && pageDetector.detect(currentContext).kind == PageKind.USER_RESULTS) {
                resultsContext = currentContext
                break
            }
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                pause("Could not return to user results after a message-send failure")
                return
            }
            delay(USER_PROFILE_BACK_DELAY_MS)
        }
        resultsContext = resultsContext ?: currentWindowContext()
        if (resultsContext == null || pageDetector.detect(resultsContext!!).kind != PageKind.USER_RESULTS) {
            pause("User results did not return after a message-send failure")
            return
        }

        val scroll = swipeNormalizedGuarded(
            startX = 0.50f,
            startY = 0.76f,
            endX = 0.50f,
            endY = 0.48f,
            durationMs = 420L,
            tag = "message_failure_skip",
        )
        logger.info(
            "user_result_next_requested",
            attributes = mapOf("route" to scroll.route, "skipped_users" to restrictedUserSkips),
        )
        if (!scroll.succeeded) {
            pause("Could not advance to the next user result after a message-send failure")
            return
        }
        delay(USER_NEXT_RESULT_DELAY_MS)
        val nextContext = currentWindowContext()
        if (nextContext == null || pageDetector.detect(nextContext).kind != PageKind.USER_RESULTS) {
            pause("The next user result page was not detected after a message-send failure")
            return
        }
        phase = AutomationPhase.WAITING_FOR_USER_RESULTS
        AutomationStore.publishPhase(phase)
        selectVisibleUser(nextContext)
    }

    private suspend fun clickSelector(context: ScreenContext, request: SelectorRequest): ActionOutcome {
        val liveContext = waitForTargetWindow("click_${request.name}") ?: context
        val selection = selector.select(liveContext, request)
        val target = selection.node ?: return ActionOutcome.failure(selection.reasons.joinToString())
        return withLiveNode(target) { liveNode -> gestures.click(liveNode, target.bounds) }
    }

    private suspend fun tapSelectorBounds(context: ScreenContext, request: SelectorRequest): ActionOutcome {
        val liveContext = waitForTargetWindow("tap_${request.name}") ?: context
        val selection = selector.select(liveContext, request)
        val target = selection.node ?: return ActionOutcome.failure(selection.reasons.joinToString())
        logger.info(
            "selector_bounds_action",
            attributes = mapOf(
                "selector" to request.name,
                "bounds" to target.bounds,
                "score" to selection.score,
            ),
        )
        return gestures.tapBounds(target.bounds)
    }

    /**
     * A transient Android/OEM banner can become the active window and intercept the next gesture
     * (for example, the full-charge banner covering the upper search controls). Wait briefly for
     * the Douyin window to become active again instead of sending a gesture through the overlay.
     */
    private suspend fun waitForTargetWindow(tag: String): ScreenContext? {
        var overlayReported = false
        repeat(SYSTEM_OVERLAY_WAIT_ATTEMPTS) { attempt ->
            val context = currentWindowContext()
            if (context != null) {
                if (overlayReported) {
                    logger.info(
                        "system_overlay_cleared",
                        attributes = mapOf("tag" to tag, "attempt" to attempt + 1),
                    )
                }
                return context
            }
            if (!overlayReported) {
                overlayReported = true
                logger.warn(
                    "system_overlay_detected",
                    message = "The Douyin window is temporarily unavailable; waiting before retrying the action",
                    attributes = mapOf("tag" to tag),
                )
            }
            delay(SYSTEM_OVERLAY_WAIT_INTERVAL_MS)
        }
        logger.warn(
            "target_window_unavailable",
            message = "The Douyin window did not return after a transient system overlay or window change",
            attributes = mapOf("tag" to tag),
        )
        return null
    }

    private suspend fun tapNormalizedGuarded(point: NormalizedPoint, tag: String): ActionOutcome {
        if (waitForTargetWindow(tag) == null) {
            return ActionOutcome.failure("Douyin window is temporarily covered or unavailable")
        }
        return gestures.tapNormalized(point)
    }

    private suspend fun swipeNormalizedGuarded(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        tag: String,
    ): ActionOutcome {
        if (waitForTargetWindow(tag) == null) {
            return ActionOutcome.failure("Douyin window is temporarily covered or unavailable")
        }
        return gestures.swipeNormalized(startX, startY, endX, endY, durationMs)
    }

    private suspend fun ensureSearchKeyword(initialTarget: NodeSnapshot, expected: String): Boolean {
        delay(KEYWORD_POSTCONDITION_DELAY_MS)
        val firstActual = readLiveNodeText(initialTarget)
        if (SearchKeywordVerifier.matches(expected, firstActual)) return true

        logger.warn(
            "search_keyword_postcondition_retry",
            message = "Search field did not expose the requested keyword after the first set-text action",
            attributes = mapOf(
                "expected_length" to expected.length,
                "actual_length" to (firstActual?.length ?: 0),
            ),
        )

        // The first snapshot may be stale while the search page is animating. Re-select the live
        // editable node and apply the same semantic action once more before giving up.
        val refreshedTarget = currentWindowContext()
            ?.let { selector.select(it, DouyinSelectors.searchInput).node }
            ?: return false
        val retry = withLiveNode(refreshedTarget) { liveNode -> gestures.setText(liveNode, expected) }
        if (!retry.succeeded) return false
        delay(KEYWORD_POSTCONDITION_DELAY_MS)
        val finalActual = readLiveNodeText(refreshedTarget)
        val verified = SearchKeywordVerifier.matches(expected, finalActual)
        if (!verified) {
            logger.error(
                "search_keyword_postcondition_failed",
                message = "Search field still did not contain the requested keyword; submission was blocked",
                attributes = mapOf(
                    "expected_length" to expected.length,
                    "actual_length" to (finalActual?.length ?: 0),
                ),
            )
        } else {
            logger.info("search_keyword_postcondition_verified")
        }
        return verified
    }

    @Suppress("DEPRECATION")
    private fun readLiveNodeText(target: NodeSnapshot): String? {
        val root = service.rootInActiveWindow ?: return null
        var liveNode: AccessibilityNodeInfo? = null
        return try {
            if (root.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) return null
            liveNode = selector.resolveLiveNode(root, target.hierarchyPath) ?: return null
            liveNode.text?.toString()
        } finally {
            if (liveNode != null && liveNode !== root) liveNode.recycle()
            root.recycle()
        }
    }

    /** Resolve an immutable snapshot path immediately before an action, then release the live node. */
    @Suppress("DEPRECATION")
    private suspend fun withLiveNode(
        target: NodeSnapshot,
        action: suspend (AccessibilityNodeInfo) -> ActionOutcome,
    ): ActionOutcome {
        val root = service.rootInActiveWindow ?: return ActionOutcome.failure("No active accessibility window")
        var liveNode: AccessibilityNodeInfo? = null
        try {
            if (root.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) {
                logger.warn(
                    "system_overlay_detected",
                    message = "The active window changed before the accessibility action could run",
                )
                return ActionOutcome.failure("Active window changed before the action")
            }
            liveNode = selector.resolveLiveNode(root, target.hierarchyPath)
                ?: return ActionOutcome.failure("Selected node no longer exists")
            return action(liveNode)
        } finally {
            if (liveNode != null && liveNode !== root) liveNode.recycle()
            root.recycle()
        }
    }

    private fun await(
        nextPhase: AutomationPhase,
        timeoutDescription: String,
        resetRecoveryBudget: Boolean = true,
    ) {
        timeoutJob?.cancel()
        if (resetRecoveryBudget) timeoutRecoveryAttempts = 0
        phase = nextPhase
        AutomationStore.publishPhase(nextPhase)
        timeoutJob = scope.launch {
            val timeoutMs = if (nextPhase == AutomationPhase.WAITING_FOR_DIRECT_MESSAGE) {
                MESSAGE_ENTRY_TIMEOUT_MS
            } else {
                STEP_TIMEOUT_MS
            }
            delay(timeoutMs)
            mutex.withLock {
                if (taskActive && phase == nextPhase) {
                    logger.warn(
                        if (nextPhase == AutomationPhase.WAITING_FOR_DIRECT_MESSAGE) {
                            "private_message_entry_timeout"
                        } else {
                            "step_timeout"
                        },
                        attributes = mapOf("phase" to nextPhase.name, "timeout_ms" to timeoutMs),
                    )
                    captureDiagnosticsInternal("timeout_${nextPhase.name.lowercase()}")
                    if (!taskActive || phase != nextPhase) return@withLock

                    val currentTimeoutJob = coroutineContext[Job]
                    if (timeoutJob === currentTimeoutJob) timeoutJob = null
                    if (nextPhase == AutomationPhase.WAITING_FOR_DIRECT_MESSAGE) {
                        skipRestrictedUser(
                            "The private-message page did not open within the allowed time; treating this user as unavailable",
                        )
                    } else if (timeoutRecoveryAttempts < MAX_TIMEOUT_RECOVERY_ATTEMPTS) {
                        timeoutRecoveryAttempts += 1
                        recoverAfterTimeout(nextPhase, timeoutDescription)
                    } else {
                        pause(timeoutDescription)
                    }
                }
            }
        }
    }

    /**
     * One bounded, phase-specific recovery is attempted after a watchdog timeout. A second
     * timeout is allowed to reach the existing manual-handoff path rather than looping forever.
     */
    private suspend fun recoverAfterTimeout(
        timedOutPhase: AutomationPhase,
        timeoutDescription: String,
    ) {
        val context = currentWindowContext()
        val detection = context?.let(pageDetector::detect)
        logger.warn(
            "step_timeout_recovery",
            message = "Attempting one bounded recovery from a timed-out phase",
            attributes = mapOf(
                "phase" to timedOutPhase.name,
                "current_page" to (detection?.kind?.name ?: "NO_CONTEXT"),
            ),
        )

        when (timedOutPhase) {
            AutomationPhase.WAITING_FOR_SEARCH_RESULTS -> when {
                context != null && detection?.kind == PageKind.SEARCH_ENTRY -> {
                    logger.warn(
                        "search_results_timeout_retry",
                        message = "Search entry is still visible; revalidating the keyword and resubmitting",
                    )
                    enterKeyword(context, preserveTimeoutRecoveryBudget = true)
                }
                else -> pause(timeoutDescription)
            }

            AutomationPhase.WAITING_FOR_SEARCH_ENTRY -> when (detection?.kind) {
                PageKind.SEARCH_ENTRY -> enterKeyword(context!!)
                PageKind.HOME -> openSearch(context!!)
                else -> pause(timeoutDescription)
            }

            AutomationPhase.WAITING_FOR_USER_RESULTS -> when (detection?.kind) {
                PageKind.USER_RESULTS -> selectVisibleUser(context!!)
                PageKind.SEARCH_RESULTS -> selectUserTab(context!!)
                else -> pause(timeoutDescription)
            }

            AutomationPhase.WAITING_FOR_PROFILE -> if (detection?.kind == PageKind.USER_RESULTS) {
                selectVisibleUser(requireNotNull(context))
            } else {
                pause(timeoutDescription)
            }

            else -> pause(timeoutDescription)
        }
    }

    private suspend fun dumpNodeTreeInternal(tag: String) {
        val context = currentWindowContext() ?: latestContext
        if (context == null) {
            logger.warn("node_dump_skipped", message = "No active window is available")
            return
        }

        saveNodeDump(context, tag)
    }

    private fun saveNodeDump(context: ScreenContext, tag: String) {
        val safeTag = tag.replace(Regex("[^a-zA-Z0-9_-]+"), "_").take(32).ifBlank { "window" }
        val directory = File(service.filesDir, NODE_DUMP_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            logger.error("node_dump_failed", message = "Could not create private node-dump directory")
            return
        }
        val destination = File(directory, "nodes_${System.currentTimeMillis()}_$safeTag.txt")
        runCatching { destination.writeText(inspector.diagnosticDump(context)) }
            .onSuccess {
                AutomationStore.publishNodeDump(destination.absolutePath)
                logger.info("node_dump_saved", attributes = mapOf("file" to destination.name, "nodes" to context.nodes.size))
            }
            .onFailure { error ->
                logger.error("node_dump_failed", message = "Could not write private node dump", throwable = error)
            }
    }

    private suspend fun captureDiagnosticsInternal(tag: String) {
        runCatching {
            val ocrEngine = ocr ?: error("OCR engine is not available on this device")
            val artifact = screenshotCapture.capture(tag)
            AutomationStore.publishScreenshot(artifact.path)
            val freshContext = currentWindowContext()
            val diagnosticBase = freshContext ?: latestContext
            if (freshContext != null) latestContext = freshContext
            diagnosticBase?.let { context ->
                saveNodeDump(context, "${tag}_context")
            }
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(artifact.path) }
                ?: error("Could not decode private screenshot artifact")
            try {
                val result = ocrEngine.recognize(bitmap)
                AutomationStore.publishOcr(result.text)
                val base = freshContext ?: latestContext ?: currentWindowContext()
                if (base != null) {
                    val contextWithOcr = base.copy(ocrBlocks = result.toOcrTextBlocks())
                    val detection = pageDetector.detect(contextWithOcr)
                    latestContext = contextWithOcr
                    AutomationStore.publishObservation(detection)
                    if (taskActive && (detection.kind == PageKind.HUMAN_INTERVENTION || detection.kind == PageKind.LOGIN)) {
                        pause("OCR detected a verification, risk, or login screen; manual handoff required")
                    }
                }
                logger.info("diagnostics_capture_completed", attributes = mapOf("ocr_blocks" to result.blocks.size))
            } finally {
                bitmap.recycle()
            }
        }.onFailure { error ->
            logger.error("diagnostics_capture_failed", message = "Screenshot/OCR diagnostic capture failed", throwable = error)
        }
    }

    @Suppress("DEPRECATION")
    private fun currentWindowContext(): ScreenContext? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            if (root.packageName?.toString() != TargetAppLauncher.DOUYIN_PACKAGE) return null
            inspector.inspect(root)
        } finally {
            root.recycle()
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

    private fun pause(reason: String) {
        if (!taskActive && phase != AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF) return
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        pausedPhase = phase.takeUnless {
            it == AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF || it == AutomationPhase.STOPPED
        }
        taskActive = false
        phase = AutomationPhase.PAUSED_FOR_MANUAL_HANDOFF
        AutomationStore.publishManualHandoff(reason)
        logger.warn("poc_paused_for_manual_handoff", message = reason)
    }

    private fun stop() {
        timeoutJob?.cancel()
        initialObservationJob?.cancel()
        messageEntryPostconditionJob?.cancel()
        messageResultJob?.cancel()
        taskActive = false
        keyword = null
        pendingStartMessage = ""
        pausedPhase = null
        phase = AutomationPhase.STOPPED
        AutomationStore.publishPhase(AutomationPhase.STOPPED)
        logger.info("poc_stopped")
    }

    private companion object {
        const val STEP_TIMEOUT_MS = 12_000L
        const val NODE_DUMP_DIRECTORY = "diagnostics/nodes"
        const val INITIAL_OBSERVATION_ATTEMPTS = 12
        const val INITIAL_OBSERVATION_INTERVAL_MS = 350L
        const val KEYWORD_POSTCONDITION_DELAY_MS = 280L
        const val MAX_NODE_SCROLL_ATTEMPTS = 2
        const val USER_RESULTS_POSTCONDITION_ATTEMPTS = 8
        const val USER_RESULTS_POSTCONDITION_INTERVAL_MS = 500L
        const val USER_ROW_POSTCONDITION_ATTEMPTS = 8
        const val USER_ROW_POSTCONDITION_INTERVAL_MS = 350L
        const val MESSAGE_ENTRY_POSTCONDITION_DELAY_MS = 900L
        const val USER_PROFILE_BACK_DELAY_MS = 700L
        const val USER_NEXT_RESULT_DELAY_MS = 700L
        const val MAX_RESTRICTED_USER_SKIPS = 10
        const val MAX_BACK_ACTIONS_FROM_MESSAGE_FAILURE = 2
        const val MESSAGE_ENTRY_TIMEOUT_MS = 12_000L
        const val MAX_MESSAGE_LENGTH = 500
        const val MESSAGE_TARGET_RESTORE_DELAY_MS = 700L
        const val MESSAGE_INPUT_SETTLE_DELAY_MS = 250L
        const val MESSAGE_RESULT_ATTEMPTS = 8
        const val MESSAGE_RESULT_INTERVAL_MS = 600L
        const val MAX_TIMEOUT_RECOVERY_ATTEMPTS = 1
        const val SEARCH_ENTRY_POSTCONDITION_ATTEMPTS = 8
        const val SEARCH_ENTRY_POSTCONDITION_INTERVAL_MS = 350L
        const val SEARCH_SUBMIT_POSTCONDITION_ATTEMPTS = 3
        const val SEARCH_SUBMIT_POSTCONDITION_DELAY_MS = 450L
        const val SEARCH_SUBMIT_POSTCONDITION_INTERVAL_MS = 350L
        const val SYSTEM_OVERLAY_WAIT_ATTEMPTS = 5
        const val SYSTEM_OVERLAY_WAIT_INTERVAL_MS = 300L
        const val USER_RESULTS_TOP_RATIO = 0.14f
        const val USER_ROW_MIN_HEIGHT = 180
        const val USER_ROW_MAX_HEIGHT = 400
        const val USER_ROW_CONTENT_LEFT_RATIO = 0.24f
        const val USER_ROW_CONTENT_TOP_RATIO = 0.12f
        const val USER_ROW_CONTENT_BOTTOM_RATIO = 0.58f
        const val USER_ROW_SAFE_TAP_RIGHT_RATIO = 0.70f
        const val USER_TAB_VIEWPORT_FALLBACK_RIGHT_RATIO = 0.84f

        val searchSubmitSelector = SelectorRequest(
            name = "search-submit",
            // The submit target is the visible, non-editable top-right label. Requiring a
            // Button/TextView class rejects camera/filter icons that may expose a generic search
            // content description, while requireEditable=false rejects the input itself.
            labels = DouyinLabels.search,
            contentDescriptionLabels = DouyinLabels.search,
            viewIdTokens = emptyList(),
            classNameTokens = listOf("Button", "TextView"),
            requireClassNameToken = true,
            requireClickable = null,
            // The search input itself also exposes the hint “搜索” and can otherwise outrank the
            // top-right submit label. Never treat an editable node as the submit control.
            requireEditable = false,
            preferredRegion = NormalizedRect(0.68f, 0f, 1f, 0.30f),
            minimumScore = 0.30f,
        )
    }

    /**
     * Starting an already-running target app may not emit a new accessibility event. Poll the
     * current window briefly so the state machine can begin from the screen that is already shown.
     */
    private fun scheduleInitialObservation() {
        initialObservationJob?.cancel()
        initialObservationJob = scope.launch {
            repeat(INITIAL_OBSERVATION_ATTEMPTS) {
                delay(INITIAL_OBSERVATION_INTERVAL_MS)
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_HOME) return@launch

                val context = currentWindowContext() ?: return@repeat
                val augmentedContext = augmentInitialUnknownWithOcr(context)
                val detection = pageDetector.detect(augmentedContext)
                logger.info(
                    "initial_observation_snapshot",
                    attributes = mapOf(
                        "page" to detection.kind.name,
                        "confidence" to detection.confidence,
                        "nodes" to augmentedContext.nodes.size,
                        "ocr_blocks" to augmentedContext.ocrBlocks.size,
                    ),
                )
                onScreenObserved(augmentedContext, detection)
                if (!taskActive || phase != AutomationPhase.WAITING_FOR_HOME) return@launch
            }
        }
    }

    private suspend fun augmentInitialUnknownWithOcr(context: ScreenContext): ScreenContext {
        if (initialOcrAttempted || pageDetector.detect(context).kind != PageKind.UNKNOWN) return context
        val engine = ocr ?: return context
        initialOcrAttempted = true

        return runCatching {
            val artifact = screenshotCapture.capture("initial_page_probe")
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(artifact.path) }
                ?: return@runCatching context
            try {
                val result = engine.recognize(bitmap)
                if (result.isEmpty) context else context.copy(ocrBlocks = result.toOcrTextBlocks())
            } finally {
                bitmap.recycle()
            }
        }.onFailure { error ->
            logger.warn(
                "initial_ocr_probe_failed",
                message = "Initial OCR probe failed; continuing with accessibility nodes",
                attributes = mapOf("cause" to (error::class.java.simpleName ?: "Throwable")),
            )
        }.getOrDefault(context)
    }

}
