package com.example.douyinautomation.automation

import java.util.Locale

/**
 * A framework-free representation of the screen.  Keeping this model free of Android classes
 * makes page classification and selector ranking deterministic and easy to exercise in JVM tests.
 */
data class ScreenSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "Screen width must be positive." }
        require(height > 0) { "Screen height must be positive." }
    }
}

/** Raw pixel bounds reported by accessibility or OCR. */
data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right must be greater than or equal to left." }
        require(bottom >= top) { "bottom must be greater than or equal to top." }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun normalized(screenSize: ScreenSize): NormalizedRect = NormalizedRect(
        left = left.toFloat() / screenSize.width,
        top = top.toFloat() / screenSize.height,
        right = right.toFloat() / screenSize.width,
        bottom = bottom.toFloat() / screenSize.height,
    ).clamped()

    companion object {
        val EMPTY = ScreenBounds(0, 0, 0, 0)
    }
}

/**
 * Geometry expressed as a fraction of the current display.  This is deliberately independent of
 * resolution, display density, and cutout size so selectors can use location as a weak hint.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "Normalized bounds must be finite."
        }
    }

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    fun contains(rect: NormalizedRect): Boolean =
        contains(rect.centerX, rect.centerY)

    fun clamped(): NormalizedRect = NormalizedRect(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f),
    ).ordered()

    fun ordered(): NormalizedRect = NormalizedRect(
        left = minOf(left, right),
        top = minOf(top, bottom),
        right = maxOf(left, right),
        bottom = maxOf(top, bottom),
    )

    companion object {
        val FULL_SCREEN = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

/** A normalized tap point used only by the final, profile-supplied gesture fallback. */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) {
            "Normalized point must be finite and within the display."
        }
    }
}

/** A serializable snapshot of one accessibility node; [hierarchyPath] starts at the root. */
data class NodeSnapshot(
    val hierarchyPath: List<Int> = emptyList(),
    val text: String? = null,
    val contentDescription: String? = null,
    val hintText: String? = null,
    val stateDescription: String? = null,
    val paneTitle: String? = null,
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val bounds: ScreenBounds = ScreenBounds.EMPTY,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisibleToUser: Boolean = true,
    val isScrollable: Boolean = false,
    val isSelected: Boolean = false,
    val childCount: Int = 0,
    val depth: Int = hierarchyPath.size,
) {
    val stableId: String
        get() = if (hierarchyPath.isEmpty()) "root" else hierarchyPath.joinToString(separator = ".")

    fun normalizedBounds(screenSize: ScreenSize): NormalizedRect = bounds.normalized(screenSize)

    fun searchableText(): List<String> = listOfNotNull(
        text,
        contentDescription,
        hintText,
        stateDescription,
        paneTitle,
        viewIdResourceName,
        className,
    ).filter { it.isNotBlank() }
}

/** OCR is optional and is treated as a fallback when semantic accessibility data is missing. */
data class OcrTextBlock(
    val text: String,
    val bounds: ScreenBounds = ScreenBounds.EMPTY,
    val confidence: Float? = null,
)

data class ScreenContext(
    val screenSize: ScreenSize = ScreenSize(1080, 1920),
    val packageName: String? = null,
    val nodes: List<NodeSnapshot> = emptyList(),
    val ocrBlocks: List<OcrTextBlock> = emptyList(),
    val capturedAtMillis: Long = 0L,
) {
    fun normalizedBounds(node: NodeSnapshot): NormalizedRect = node.normalizedBounds(screenSize)

    /** Text sourced from accessibility nodes, before OCR fallback text. */
    fun nodeText(): List<String> = nodes.flatMap(NodeSnapshot::searchableText)

    fun ocrText(): List<String> = ocrBlocks.map(OcrTextBlock::text).filter(String::isNotBlank)
}

enum class PageKind {
    OUTSIDE_TARGET,
    HOME,
    /** A Douyin live-feed/live-room surface with an entry prompt; never click it. */
    LIVE_ROOM,
    /** An opened live room with close/share controls; exit first, then swipe the live item away. */
    LIVE_ROOM_SESSION,
    SEARCH_ENTRY,
    SEARCH_RESULTS,
    USER_RESULTS,
    USER_PROFILE,
    /** The profile explicitly says messaging is gated until the account is followed. */
    PRIVATE_MESSAGE_RESTRICTED,
    DIRECT_MESSAGE,
    /** The M2 safety probe was rejected as blank; no real message was delivered. */
    MESSAGE_EMPTY_REJECTED,
    /** A message was attempted in an open conversation but Douyin rejected delivery. */
    MESSAGE_SEND_FAILED,
    /** Captcha, risk controls, or another state where the task must stop for a person. */
    HUMAN_INTERVENTION,
    LOGIN,
    UNKNOWN,
}

data class PageDetection(
    val kind: PageKind,
    val confidence: Float,
    val reasons: List<String>,
) {
    init {
        require(confidence in 0f..1f) { "Confidence must be between 0 and 1." }
    }
}

/**
 * Safe resume decisions for a paused run. A resume is allowed only after the current screen is
 * positively classified; risk, login, and unknown pages remain manual-only.
 */
data class ResumeDecision(
    val phase: AutomationPhase?,
    val allowed: Boolean,
    val reason: String,
)

object AutomationResumePolicy {
    fun decide(detection: PageDetection): ResumeDecision = when (detection.kind) {
        PageKind.HOME -> ResumeDecision(
            phase = AutomationPhase.WAITING_FOR_HOME,
            allowed = true,
            reason = "Home page is ready",
        )

        PageKind.SEARCH_ENTRY -> ResumeDecision(
            phase = AutomationPhase.WAITING_FOR_SEARCH_ENTRY,
            allowed = true,
            reason = "Search entry page is ready",
        )

        PageKind.SEARCH_RESULTS -> ResumeDecision(
            phase = AutomationPhase.WAITING_FOR_SEARCH_RESULTS,
            allowed = true,
            reason = "Search results page is ready",
        )

        PageKind.USER_RESULTS -> ResumeDecision(
            phase = AutomationPhase.WAITING_FOR_USER_RESULTS,
            allowed = true,
            reason = "User results page is ready",
        )

        PageKind.USER_PROFILE -> ResumeDecision(
            phase = AutomationPhase.WAITING_FOR_PROFILE,
            allowed = true,
            reason = "User profile page is ready",
        )

        PageKind.DIRECT_MESSAGE -> ResumeDecision(
            phase = AutomationPhase.WAITING_FOR_DIRECT_MESSAGE,
            allowed = true,
            reason = "Direct-message page is ready",
        )

        PageKind.MESSAGE_SEND_FAILED -> ResumeDecision(
            phase = null,
            allowed = false,
            reason = "The last message was rejected by the recipient's messaging settings",
        )

        PageKind.MESSAGE_EMPTY_REJECTED -> ResumeDecision(
            phase = null,
            allowed = false,
            reason = "The blank-message safety probe has completed; start a new task to continue",
        )

        PageKind.PRIVATE_MESSAGE_RESTRICTED -> ResumeDecision(
            phase = null,
            allowed = false,
            reason = "This profile requires following before private messaging",
        )

        PageKind.HUMAN_INTERVENTION -> ResumeDecision(
            phase = null,
            allowed = false,
            reason = "Verification or risk screen still requires manual handling",
        )

        PageKind.LOGIN -> ResumeDecision(
            phase = null,
            allowed = false,
            reason = "Login is still required",
        )

        PageKind.OUTSIDE_TARGET,
        PageKind.LIVE_ROOM,
        PageKind.LIVE_ROOM_SESSION,
        PageKind.UNKNOWN,
        -> ResumeDecision(
            phase = null,
            allowed = false,
            reason = "The current page is not a verified safe resume point",
        )
    }
}

/** A small set of labels used by PageDetector and SelectorEngine. */
object DouyinLabels {
    val search = listOf("搜索", "search")
    val cancel = listOf("取消", "cancel")
    val allResults = listOf("综合", "all")
    val videos = listOf("视频", "videos")
    val users = listOf("用户", "users", "账号", "accounts")
    val privateMessage = listOf("发私信", "私信", "message", "direct message")
    val privateMessageRestriction = listOf(
        "关注后才能发送私信",
        "关注后才可以发送私信",
        "关注后发送私信",
        "关注后才能私信",
        "follow before messaging",
        "follow to message",
    )
    /**
     * Delivery-failure text shown after a message bubble receives the red exclamation marker.
     * Keep both the full sentence from current Douyin builds and shorter stable fragments because
     * OCR may split the two-line explanation into separate blocks.
     */
    val messageSendFailure = listOf(
        "对方设置了仅他关注的人可发消息",
        "仅他关注的人可发消息",
        "仅关注的人可发消息",
        "需要对方修改权限后可发消息",
        "对方修改权限后可发消息",
        "消息发送失败",
        "发送失败",
        "无法发送消息",
        "message failed",
        "failed to send",
        "couldn't send",
    )
    val follow = listOf("关注", "已关注", "follow", "following")
    val send = listOf("发送", "send")
    // Douyin 39.x uses a contextual assistant composer such as “输入你的问题..” on a
    // newly-opened profile chat. Keep the semantic composer match broad enough for that label,
    // while still requiring an actual editable node in PageDetector.
    val messageInput = listOf(
        "输入消息",
        "输入你的问题",
        "说点什么",
        "发送消息",
        "发消息或按住说话",
        "type a message",
        "message",
    )
    val profile = listOf(
        "抖音号",
        "ip属地",
        "获赞",
        "粉丝",
        "关注",
        "douyin id",
        "followers",
        "following",
        "likes",
    )
    // "me" is intentionally omitted: substring matching would confuse it with words such as
    // "message". The other navigation labels are sufficient for a V0 home signature.
    // Douyin 39.x can expose the live/recommendation shell instead of the ordinary feed. The
    // current top navigation includes 关注/商城/同城/团购/直播 in addition to the bottom 首页
    // navigation; these are still weak home signals and are only accepted in pairs.
    val home = listOf(
        "首页",
        "推荐",
        "朋友",
        "消息",
        "我",
        "关注",
        "商城",
        "同城",
        "团购",
        "直播",
        "home",
        "following",
        "friends",
        "inbox",
    )
    val login = listOf("登录", "手机号登录", "log in", "sign in")
    /** Strong live-feed markers; the generic “直播中” label alone is intentionally excluded. */
    val liveRoomEntry = listOf("点击进入直播间", "进入直播间", "点击进入直播", "join live room")
    val captchaOrRisk = listOf(
        "验证码",
        "安全验证",
        "人机验证",
        "滑块验证",
        "请完成验证",
        "操作频繁",
        "访问频繁",
        "异常行为",
        "账号存在风险",
        "captcha",
        "security verification",
        "verify you are human",
        "drag the slider",
        "too many requests",
        "unusual activity",
        "suspicious activity",
        "account at risk",
    )
}

object TextNormalizer {
    fun normalize(value: CharSequence?): String = value
        ?.toString()
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()

    fun matchingTerms(value: CharSequence?, terms: Iterable<String>): List<String> {
        val normalizedValue = normalize(value)
        if (normalizedValue.isEmpty()) return emptyList()
        return terms.mapNotNull { term ->
            val normalizedTerm = normalize(term)
            normalizedTerm.takeIf { it.isNotEmpty() && normalizedValue.contains(it) }
        }
    }

    fun matchesAny(value: CharSequence?, terms: Iterable<String>): Boolean =
        matchingTerms(value, terms).isNotEmpty()
}

/** Strict post-condition for the search field; hints and default suggestions never count. */
object SearchKeywordVerifier {
    fun matches(expected: String, actual: CharSequence?): Boolean {
        val normalizedExpected = TextNormalizer.normalize(expected)
        val normalizedActual = TextNormalizer.normalize(actual)
        return normalizedExpected.isNotEmpty() && normalizedExpected == normalizedActual
    }
}
