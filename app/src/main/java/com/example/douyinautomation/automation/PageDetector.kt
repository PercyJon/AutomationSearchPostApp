package com.example.douyinautomation.automation

/**
 * Stateless V0 page classifier. It intentionally works only from [ScreenContext], never live
 * accessibility objects, so a captured node tree and OCR response can be replayed in tests.
 *
 * Captcha and risk signals are evaluated first. Any such signal wins over a seemingly valid page
 * and returns [PageKind.HUMAN_INTERVENTION]; callers must pause rather than retry around it.
 */
class PageDetector {

    fun detect(context: ScreenContext): PageDetection {
        if (!belongsToDouyin(context.packageName)) {
            return PageDetection(
                kind = PageKind.OUTSIDE_TARGET,
                confidence = 0.99f,
                reasons = listOf("Foreground package is ${context.packageName}"),
            )
        }

        val riskSignals = matchingSignals(context, DouyinLabels.captchaOrRisk)
        if (riskSignals.isNotEmpty()) {
            return PageDetection(
                kind = PageKind.HUMAN_INTERVENTION,
                confidence = if (riskSignals.any { it.fromAccessibility }) 0.99f else 0.93f,
                reasons = riskSignals.take(MAX_REASONS).map {
                    "Risk/captcha label \"${it.term}\" from ${it.sourceName}"
                },
            )
        }

        val loginSignals = matchingSignals(context, DouyinLabels.login)
        if (loginSignals.isNotEmpty()) {
            return PageDetection(
                kind = PageKind.LOGIN,
                confidence = confidence(loginSignals, base = 0.82f),
                reasons = reasonsFor("Login label", loginSignals),
            )
        }

        // An opened live room exposes a close control in the upper-right and a share/forward
        // control in the lower-right. It must be exited before the underlying live item is
        // swiped away; never treat the room as a normal home or profile page.
        if (LiveRoomSurfaceDetector.isOpenedRoom(context)) {
            return PageDetection(
                kind = PageKind.LIVE_ROOM_SESSION,
                confidence = 0.95f,
                reasons = listOf("Opened live room close/share control pair"),
            )
        }

        // A live-feed item can expose a large “点击进入直播间” prompt. The action layer handles
        // this page with a vertical swipe and never clicks the prompt.
        val liveRoomSignals = matchingLiveRoomSignals(context)
        if (liveRoomSignals.isNotEmpty() || LiveRoomSurfaceDetector.hasEntryPrompt(context)) {
            val signals = liveRoomSignals.ifEmpty {
                listOf(Signal("进入直播间", "live-room-structure", fromAccessibility = false))
            }
            return PageDetection(
                kind = PageKind.LIVE_ROOM,
                confidence = confidence(signals, base = 0.93f),
                reasons = reasonsFor("Live-room entry prompt", signals),
            )
        }

        // A direct-message page requires composer evidence. "发私信" on a profile is an entry
        // button and must not be confused with an already-open chat.
        val editableNodes = context.nodes.filter { it.isEditable && it.isVisibleToUser }
        val messageHeader = matchingSignals(context, DIRECT_MESSAGE_HEADERS)
        val sendButton = matchingSignals(context, DouyinLabels.send)
        val composerNodes = editableNodes.filter { nodeMatches(it, DouyinLabels.messageInput) }
        // Some profiles use a custom-rendered composer with no hint, text, or content
        // description. Its stable semantic shape is an editable field in the bottom band next
        // to a right-side “发送” action. This is distinct from the top search field and avoids
        // treating an otherwise valid fourth user chat as UNKNOWN.
        val bottomComposerNodes = editableNodes.filter { node ->
            node.bounds.height > 0 &&
                node.bounds.top >= (context.screenSize.height * BOTTOM_COMPOSER_TOP_RATIO).toInt()
        }
        val hasBottomSendAction = context.nodes.any { node ->
            node.bounds.height > 0 &&
                node.bounds.top >= (context.screenSize.height * BOTTOM_COMPOSER_TOP_RATIO).toInt() &&
                nodeMatches(node, DouyinLabels.send)
        }
        val hasStructuralComposer = bottomComposerNodes.isNotEmpty() && hasBottomSendAction
        // A few Douyin profile chats render the composer entirely in a custom surface: no
        // editable node and no labelled send node are exposed, but OCR still sees the quick
        // question/composer text in the lower band. This is strong chat evidence when it is
        // constrained to the bottom of the screen and avoids treating a profile's “发私信”
        // action as an open conversation.
        val ocrComposerSignals = context.ocrBlocks.filter { block ->
            val normalized = TextNormalizer.normalize(block.text)
            val inBottomBand = block.bounds == ScreenBounds.EMPTY ||
                block.bounds.top >= (context.screenSize.height * OCR_COMPOSER_TOP_RATIO).toInt()
            inBottomBand && OCR_CONVERSATION_COMPOSER_MARKERS.any(normalized::contains)
        }
        val hasOcrComposer = ocrComposerSignals.isNotEmpty()
        // A search field can coexist with incidental "私信"/"发送" text in a result card or OCR
        // overlay. Require an actual message composer, or both the chat header and send action,
        // before classifying the page as an open conversation.
        val isDirectMessage = composerNodes.isNotEmpty() || hasStructuralComposer || hasOcrComposer ||
            (editableNodes.isNotEmpty() && messageHeader.isNotEmpty() && sendButton.isNotEmpty())

        // A profile can expose a follow gate after the paper-plane action is pressed. This is a
        // normal per-account restriction, not a captcha or platform-risk screen. If the same
        // wording appears beside a real conversation composer, however, it is the post-send
        // delivery failure and must be handled as such.
        val privateMessageRestrictionSignals = matchingSignals(context, DouyinLabels.privateMessageRestriction)
        val emptyMessageRejectionSignals = matchingEmptyMessageRejectionSignals(context)
        val messageSendFailureSignals = matchingMessageSendFailureSignals(context)
        // The safety probe intentionally submits one space.  Douyin keeps the conversation page
        // visible while showing a transient “不能发送空白消息” notice, so this result must win
        // over the ordinary DIRECT_MESSAGE classification.
        if (emptyMessageRejectionSignals.isNotEmpty()) {
            return PageDetection(
                kind = PageKind.MESSAGE_EMPTY_REJECTED,
                confidence = confidence(emptyMessageRejectionSignals, base = 0.96f),
                reasons = reasonsFor("Blank-message probe rejection", emptyMessageRejectionSignals),
            )
        }
        if (isDirectMessage && (privateMessageRestrictionSignals.isNotEmpty() || messageSendFailureSignals.isNotEmpty())) {
            val signals = privateMessageRestrictionSignals + messageSendFailureSignals
            return PageDetection(
                kind = PageKind.MESSAGE_SEND_FAILED,
                confidence = confidence(signals, base = 0.90f),
                reasons = reasonsFor("Message-send failure", signals),
            )
        }
        if (!isDirectMessage && privateMessageRestrictionSignals.isNotEmpty()) {
            return PageDetection(
                kind = PageKind.PRIVATE_MESSAGE_RESTRICTED,
                confidence = confidence(privateMessageRestrictionSignals, base = 0.88f),
                reasons = reasonsFor("Private-message restriction", privateMessageRestrictionSignals),
            )
        }
        // A conversation can open successfully even when the recipient will reject the first
        // message. Douyin renders a red exclamation marker beside the bubble and a textual reason
        // such as “对方设置了仅他关注的人可发消息，需要对方修改权限后可发消息”. The marker itself
        // is not reliably exposed by Accessibility, so the fuzzy text signal is authoritative;
        // OCR is allowed through matchingMessageSendFailureSignals when the custom chat surface
        // has no node text.
        if (messageSendFailureSignals.isNotEmpty()) {
            return PageDetection(
                kind = PageKind.MESSAGE_SEND_FAILED,
                confidence = confidence(messageSendFailureSignals, base = 0.90f),
                reasons = reasonsFor("Message-send failure", messageSendFailureSignals),
            )
        }
        if (isDirectMessage) {
            val composerReason = composerNodes.firstOrNull()?.let { "Editable message composer at ${it.stableId}" }
            val structuralComposerReason = if (hasStructuralComposer) {
                "Bottom composer structure with send action"
            } else {
                null
            }
            return PageDetection(
                kind = PageKind.DIRECT_MESSAGE,
                confidence = when {
                    composerNodes.isNotEmpty() && sendButton.isNotEmpty() -> 0.96f
                    hasStructuralComposer -> 0.93f
                    hasOcrComposer -> 0.90f
                    composerNodes.isNotEmpty() || (messageHeader.isNotEmpty() && sendButton.isNotEmpty()) -> 0.89f
                    else -> 0.78f
                },
                reasons = listOfNotNull(composerReason, structuralComposerReason) +
                    (if (hasOcrComposer) listOf("OCR conversation composer in bottom band") else emptyList()) +
                    reasonsFor("Message page label", messageHeader + sendButton),
            )
        }

        // Search-entry must win over profile-like text rendered in the suggestion dropdown. A
        // friend suggestion can include a full “抖音号: …” line, which otherwise looks exactly
        // like a profile identity and would make the controller stop while the keyboard remains
        // open. The editable search field is the stronger page-level postcondition.
        val editableSearch = context.nodes.any { node ->
            node.isEditable && node.isVisibleToUser && nodeMatches(node, DouyinLabels.search)
        }
        val searchSignals = matchingSignals(context, DouyinLabels.search)
        // Results pages keep the same editable query field at the top. Their tab strip is the
        // stronger post-condition, so do not classify such a page as SEARCH_ENTRY merely because
        // the query field remains editable. This prevents the submit watchdog from tapping the
        // Search button repeatedly after results have already opened.
        val earlyResultTabSignals = matchingSignals(context, SEARCH_RESULT_TABS)
        val hasResultTabs = earlyResultTabSignals.distinctBy(Signal::term).size >= 2
        if (editableSearch && !hasResultTabs) {
            return PageDetection(
                kind = PageKind.SEARCH_ENTRY,
                confidence = 0.91f,
                reasons = listOf("Editable search field found") + reasonsFor("Search label", searchSignals),
            )
        }

        // OCR may read incidental "用户" text from a result card. A user-results page requires a
        // semantic accessibility node for the actual category tab; OCR remains useful for rows
        // and other weak signals but cannot authorize the tab transition by itself.
        val userTabSignals = matchingVisibleUserTabSignals(context)
        val selectedUserTabSignals = matchingSelectedUserTabSignals(context)
        val resultTabSignals = matchingSignals(context, SEARCH_RESULT_TABS)
        val userRowSignals = matchingSignals(context, USER_ROW_HINTS)
        val structuralUserRowSignals = if (StructuralUserRowDetector.find(context) != null) {
            listOf(Signal(term = "关注按钮", sourceName = "accessibility-structure", fromAccessibility = true))
        } else {
            emptyList()
        }
        val accountHelpSignals = if (UserResultMarkers.hasAccountHelp(context)) {
            listOf(Signal(term = "找不到想找的账号", sourceName = "account-help-marker", fromAccessibility = false))
        } else {
            emptyList()
        }
        // Seeing the word “用户” in the tab strip is not enough: on the 综合 page that tab is
        // often already visible but not selected. A user-results page must either expose an
        // explicitly selected User tab, semantic row hints, or the stable row-level follow-button
        // anchors used by current custom-rendered Douyin builds.
        val hasUserResultsPostcondition = selectedUserTabSignals.isNotEmpty() ||
            userRowSignals.isNotEmpty() ||
            structuralUserRowSignals.isNotEmpty() ||
            accountHelpSignals.isNotEmpty()
        if (userTabSignals.isNotEmpty() && hasUserResultsPostcondition) {
            return PageDetection(
                kind = PageKind.USER_RESULTS,
                confidence = confidence(
                    userTabSignals + selectedUserTabSignals + userRowSignals + structuralUserRowSignals + accountHelpSignals,
                    base = 0.82f,
                ),
                reasons = reasonsFor(
                    "User-result label",
                    userTabSignals + selectedUserTabSignals + userRowSignals + structuralUserRowSignals + accountHelpSignals,
                ),
            )
        }

        // Feed captions and overlays frequently contain the single word "视频". Require at least
        // two distinct result-tab signals before classifying a page as search results; a genuine
        // result page normally exposes a tab pair/trio such as 综合/视频/用户.
        if (resultTabSignals.distinctBy(Signal::term).size >= 2) {
            return PageDetection(
                kind = PageKind.SEARCH_RESULTS,
                confidence = confidence(resultTabSignals, base = 0.80f),
                reasons = reasonsFor("Search-result tab", resultTabSignals),
            )
        }

        // A standalone follower count is common in a user-result row, so it is not enough to
        // classify a profile. User-results and search-results were checked above because their
        // rows also contain profile-like text such as “抖音号” and “粉丝”. Only classify a profile
        // after those stronger result-page postconditions have been ruled out.
        val accessibilityProfileSignals = matchingAccessibilitySignals(context, PROFILE_IDENTITY_LABELS)
        val accessibilityProfileEntry = matchingAccessibilitySignals(context, PROFILE_MESSAGE_ENTRY)
        val accessibilityFollowSignals = matchingAccessibilitySignals(context, FOLLOW_ACTIONS)
        val profileSignals = matchingSignals(context, PROFILE_IDENTITY_LABELS)
        val profileMessageEntry = matchingSignals(context, PROFILE_MESSAGE_ENTRY)
        val profileFollowSignals = matchingSignals(context, FOLLOW_ACTIONS)
        // Feed captions and OCR overlays often contain isolated words such as “获赞” or “粉丝”.
        // Those weak OCR-only signals must not turn the home feed into a profile and leave the
        // controller waiting for a profile action that is not actually present. Accessibility
        // identity/action structure is authoritative; OCR is accepted only when it provides the
        // paired message-entry and follow controls.
        val hasAccessibilityProfileStructure = accessibilityProfileSignals.size >= 2 ||
            (accessibilityProfileSignals.isNotEmpty() && accessibilityProfileEntry.isNotEmpty())
        val hasOcrProfileStructure = profileMessageEntry.isNotEmpty() && profileFollowSignals.isNotEmpty()
        if (hasAccessibilityProfileStructure || hasOcrProfileStructure) {
            val reasons = if (hasAccessibilityProfileStructure) {
                accessibilityProfileSignals + accessibilityProfileEntry
            } else {
                profileSignals + profileMessageEntry + accessibilityFollowSignals
            }
            return PageDetection(
                kind = PageKind.USER_PROFILE,
                confidence = confidence(reasons, base = 0.75f),
                reasons = reasonsFor("Profile label", reasons),
            )
        }

        val cancelSignals = matchingSignals(context, DouyinLabels.cancel)
        if (editableSearch || (context.nodes.any { it.isEditable && it.isVisibleToUser } && cancelSignals.isNotEmpty())) {
            return PageDetection(
                kind = PageKind.SEARCH_ENTRY,
                confidence = if (editableSearch) 0.91f else 0.78f,
                reasons = listOfNotNull(
                    if (editableSearch) "Editable search field found" else null,
                ) + reasonsFor("Search label", searchSignals + cancelSignals),
            )
        }

        val homeSignals = matchingSignals(context, DouyinLabels.home)
        if (homeSignals.size >= 2) {
            return PageDetection(
                kind = PageKind.HOME,
                confidence = confidence(homeSignals, base = 0.74f),
                reasons = reasonsFor("Home navigation label", homeSignals),
            )
        }

        return PageDetection(
            kind = PageKind.UNKNOWN,
            confidence = 0.1f,
            reasons = listOf("No V0 page signature matched"),
        )
    }

    private fun belongsToDouyin(packageName: String?): Boolean {
        // Tests and offline diagnostic replays may not carry package metadata; preserve detection.
        if (packageName.isNullOrBlank()) return true
        val normalized = TextNormalizer.normalize(packageName)
        return TARGET_PACKAGE_MARKERS.any(normalized::contains)
    }

    private fun matchingSignals(context: ScreenContext, terms: Iterable<String>): List<Signal> {
        val accessibilitySignals = context.nodeText().flatMap { value ->
            TextNormalizer.matchingTerms(value, terms).map { term ->
                Signal(term = term, sourceName = "accessibility", fromAccessibility = true)
            }
        }
        // OCR is a fallback rather than a replacement for nodes. If nodes expose only one weak
        // signal (common on custom-rendered Douyin pages), retain it and supplement it with OCR;
        // once two or more node signals exist, the semantic tree is sufficiently authoritative.
        if (accessibilitySignals.size >= 2) return accessibilitySignals.distinct()
        val ocrSignals = context.ocrText().flatMap { value ->
            TextNormalizer.matchingTerms(value, terms).map { term ->
                Signal(term = term, sourceName = "OCR", fromAccessibility = false)
            }
        }.distinct()
        return (accessibilitySignals + ocrSignals).distinct()
    }

    private fun matchingLiveRoomSignals(context: ScreenContext): List<Signal> {
        val nodeSignals = context.nodes.asSequence()
            .filter { it.isVisibleToUser && it.bounds.height > 0 }
            .filter { node ->
                val bounds = node.normalizedBounds(context.screenSize)
                node.bounds == ScreenBounds.EMPTY || (bounds.top >= 0.18f && bounds.bottom <= 0.92f)
            }
            .flatMap { node ->
                node.searchableText().asSequence().flatMap { value ->
                    TextNormalizer.matchingTerms(value, DouyinLabels.liveRoomEntry).map { term ->
                        Signal(term = term, sourceName = "accessibility", fromAccessibility = true)
                    }
                }
            }
            .distinct()
            .toList()
        val ocrSignals = context.ocrBlocks.asSequence()
            .filter { block ->
                block.bounds == ScreenBounds.EMPTY ||
                    (block.bounds.top >= (context.screenSize.height * 0.18f).toInt() &&
                        block.bounds.bottom <= (context.screenSize.height * 0.92f).toInt())
            }
            .flatMap { block ->
                TextNormalizer.matchingTerms(block.text, DouyinLabels.liveRoomEntry).map { term ->
                    Signal(term = term, sourceName = "OCR", fromAccessibility = false)
                }
            }
            .distinct()
            .toList()
        val combinedOcr = context.ocrText().joinToString(separator = "")
        val combinedSignal = if (TextNormalizer.matchesAny(combinedOcr, DouyinLabels.liveRoomEntry)) {
            listOf(Signal(term = "进入直播间", sourceName = "OCR-combined", fromAccessibility = false))
        } else {
            emptyList()
        }
        return (nodeSignals + ocrSignals + combinedSignal).distinct()
    }

    private fun matchingAccessibilitySignals(context: ScreenContext, terms: Iterable<String>): List<Signal> =
        context.nodeText().flatMap { value ->
            TextNormalizer.matchingTerms(value, terms).map { term ->
                Signal(term = term, sourceName = "accessibility", fromAccessibility = true)
            }
        }.distinct()

    private fun matchingMessageSendFailureSignals(context: ScreenContext): List<Signal> {
        val accessibilitySignals = context.nodeText().flatMap { value ->
            if (MessageSendFailureMatcher.matches(value)) {
                listOf(Signal(term = "message-delivery-restriction", sourceName = "accessibility", fromAccessibility = true))
            } else {
                emptyList()
            }
        }
        val ocrSignals = context.ocrText().flatMap { value ->
            if (MessageSendFailureMatcher.matches(value)) {
                listOf(Signal(term = "message-delivery-restriction", sourceName = "OCR", fromAccessibility = false))
            } else {
                emptyList()
            }
        }
        return (accessibilitySignals + ocrSignals).distinct()
    }

    private fun matchingEmptyMessageRejectionSignals(context: ScreenContext): List<Signal> {
        val accessibilitySignals = context.nodeText().flatMap { value ->
            if (EmptyMessageRejectionMatcher.matches(value)) {
                listOf(Signal(term = "blank-message-rejection", sourceName = "accessibility", fromAccessibility = true))
            } else {
                emptyList()
            }
        }
        val ocrSignals = context.ocrText().flatMap { value ->
            if (EmptyMessageRejectionMatcher.matches(value)) {
                listOf(Signal(term = "blank-message-rejection", sourceName = "OCR", fromAccessibility = false))
            } else {
                emptyList()
            }
        }
        val splitOcrText = context.ocrText().joinToString(separator = "")
        val combinedOcrSignal = if (splitOcrText.isNotBlank() &&
            EmptyMessageRejectionMatcher.matches(splitOcrText)
        ) {
            listOf(Signal(term = "blank-message-rejection", sourceName = "OCR-combined", fromAccessibility = false))
        } else {
            emptyList()
        }
        return (accessibilitySignals + ocrSignals + combinedOcrSignal).distinct()
    }

    private fun matchingVisibleUserTabSignals(context: ScreenContext): List<Signal> =
        context.nodes.asSequence()
            // Douyin exposes the tab label as a non-clickable Button child while its clickable
            // ActionBar$Tab parent carries no text. The selector resolves that parent separately;
            // detection should therefore accept an exact, visible label in the top strip without
            // requiring the text-bearing node itself to be clickable.
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { node ->
                val bounds = node.normalizedBounds(context.screenSize)
                bounds.top <= 0.25f && bounds.bottom <= 0.30f
            }
            .flatMap { node ->
                node.searchableText().asSequence().flatMap { value ->
                    DouyinLabels.users.asSequence()
                        .filter { label -> TextNormalizer.normalize(value) == TextNormalizer.normalize(label) }
                        .map { label -> Signal(term = label, sourceName = "accessibility", fromAccessibility = true) }
                }
            }
            .distinct()
            .toList()

    private fun matchingSelectedUserTabSignals(context: ScreenContext): List<Signal> =
        context.nodes.asSequence()
            .filter { it.isSelected && it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .filter { node ->
                val bounds = node.normalizedBounds(context.screenSize)
                bounds.top <= 0.25f && bounds.bottom <= 0.30f
            }
            .flatMap { node ->
                node.searchableText().asSequence().flatMap { value ->
                    DouyinLabels.users.asSequence()
                        .filter { label -> TextNormalizer.normalize(value) == TextNormalizer.normalize(label) }
                        .map { label -> Signal(term = label, sourceName = "accessibility-selected", fromAccessibility = true) }
                }
            }
            .distinct()
            .toList()

    private fun nodeMatches(node: NodeSnapshot, terms: Iterable<String>): Boolean =
        node.searchableText().any { TextNormalizer.matchesAny(it, terms) }

    private fun confidence(signals: List<Signal>, base: Float): Float =
        (base + (signals.distinctBy(Signal::term).size - 1).coerceAtLeast(0) * 0.04f)
            .coerceAtMost(if (signals.any(Signal::fromAccessibility)) 0.97f else 0.88f)

    private fun reasonsFor(prefix: String, signals: List<Signal>): List<String> =
        signals.take(MAX_REASONS).map { "$prefix \"${it.term}\" from ${it.sourceName}" }

    private data class Signal(
        val term: String,
        val sourceName: String,
        val fromAccessibility: Boolean,
    )

    private companion object {
        const val MAX_REASONS = 4
        const val BOTTOM_COMPOSER_TOP_RATIO = 0.72f
        const val OCR_COMPOSER_TOP_RATIO = 0.65f
        val TARGET_PACKAGE_MARKERS = listOf("com.ss.android.ugc.aweme", "douyin", "aweme")
        val DIRECT_MESSAGE_HEADERS = listOf("私信", "聊天", "messages", "direct message", "chat")
        val OCR_CONVERSATION_COMPOSER_MARKERS = listOf(
            "点击发送",
            "常见问题",
            "输入消息",
            "输入你的问题",
            "说点什么",
            "发消息或按住说话",
            "type a message",
        )
        // Merchant profiles may expose only “联系客服” (with a content description of “私信”)
        // instead of the normal “发私信” button. They are still user profiles, but the
        // navigation controller must treat the missing direct-message route as unavailable and
        // return to the result list rather than classifying the screen as HOME.
        val PROFILE_MESSAGE_ENTRY = listOf("发私信", "私信", "联系客服", "message")
        val PROFILE_IDENTITY_LABELS = listOf(
            "抖音号",
            "ip属地",
            "获赞",
            "粉丝",
            "店铺账号",
            "商家认证账号",
            "douyin id",
            "likes",
        )
        val FOLLOW_ACTIONS = listOf("关注", "follow", "已关注", "following")
        val SEARCH_RESULT_TABS = listOf("综合", "视频", "用户", "all", "videos", "users", "accounts")
        val USER_ROW_HINTS = listOf("粉丝", "followers", "共同关注", "followed by")
    }
}
