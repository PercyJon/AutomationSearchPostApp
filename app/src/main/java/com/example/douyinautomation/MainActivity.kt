package com.example.douyinautomation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.CommentKeywordMatchMode
import com.example.douyinautomation.automation.TaskDraft
import com.example.douyinautomation.automation.TaskExecutionMode
import com.example.douyinautomation.ui.AutomationAppSessionGate
import com.example.douyinautomation.ui.theme.AutomationTheme

/**
 * Parameterized M3 device-regression preset delivered through the debug OPEN_COMMENT_P0 intent.
 * When present it overrides the fixed "comment-p0-designer-1-1" seed so bounded 5/10/20, keyword
 * filter, skip-pinned and multi-video regressions can be driven entirely from ADB extras.
 */
data class CommentRegressionPreset(
    val targetUser: String = "designer",
    val matchKeywords: String = "",
    val matchMode: CommentKeywordMatchMode = CommentKeywordMatchMode.ANY,
    val maxVideos: Int = 1,
    val maxUsersPerVideo: Int = 1,
    val skipPinnedVideos: Boolean = false,
    /** Debug-only: inspect the first safe candidate and stop before any profile interaction. */
    val dryRun: Boolean = false,
)

class MainActivity : ComponentActivity() {
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresh = Runnable { AutomationStore.refreshServiceStatus(this) }
    private var openRecordsTab: Boolean = false
    private var openCommentP0: Boolean = false
    private var commentRegressionPreset: CommentRegressionPreset? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRecordsTab = intent.getBooleanExtra(EXTRA_OPEN_RECORDS, false)
        openCommentP0 = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_OPEN_COMMENT_P0, false)
        commentRegressionPreset = if (openCommentP0) readCommentRegressionPreset(intent) else null
        // The launch nonce lives in the companion object so it survives recreate().  A process-cold
        // start assigns the initial token exactly once; re-delivered intents advance it in onNewIntent.
        if (openCommentP0 && commentP0LaunchNonce == 0) commentP0LaunchNonce = 1
        AuthStore.initialize(this)
        AutomationStore.initialize(this)
        AutomationStore.logger.info(
            "p0_launch_oncreate",
            attributes = mapOf("nonce" to commentP0LaunchNonce, "open" to openCommentP0),
        )
        seedMultiTaskFixtureIfRequested(intent)

        setContent {
            AutomationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AutomationAppSessionGate(
                        initialKeyword = intent.getStringExtra(EXTRA_PREFILL_KEYWORD).orEmpty(),
                        initialSection = if (openRecordsTab) "RECORDS" else null,
                        initialCommentTask = openCommentP0,
                        autoStartCommentP0 = openCommentP0,
                        commentP0LaunchToken = commentP0LaunchNonce,
                        commentRegressionPreset = commentRegressionPreset,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SEED_MULTI_TASK_TESTS, false)) {
            seedMultiTaskFixtureIfRequested(intent)
            recreate()
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_RECORDS, false)) {
            openRecordsTab = true
            recreate()
            return
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_OPEN_COMMENT_P0, false)) {
            openCommentP0 = true
            commentP0LaunchNonce += 1
            AutomationStore.logger.info(
                "p0_launch_new_intent",
                attributes = mapOf("nonce" to commentP0LaunchNonce),
            )
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        AutomationStore.refreshServiceStatus(this)
        statusHandler.postDelayed(statusRefresh, 300L)
        statusHandler.postDelayed(statusRefresh, 1_200L)
        statusHandler.postDelayed(statusRefresh, 2_500L)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(statusRefresh)
        super.onPause()
    }

    /** Installs the requested three-task fixture only for a debug build on the development phone. */
    private fun seedMultiTaskFixtureIfRequested(source: android.content.Intent) {
        if (!BuildConfig.DEBUG || !source.getBooleanExtra(EXTRA_SEED_MULTI_TASK_TESTS, false)) return
        AutomationStore.replaceSavedTasks(
            listOf(
                TaskDraft(
                    id = "m2-5-foshan-redwood",
                    name = "佛山红木家具",
                    customKeywords = listOf("佛山红木家具"),
                    maxUsers = 5,
                    executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
                ),
                TaskDraft(
                    id = "m2-5-foshan-sofa",
                    name = "佛山沙发家具",
                    customKeywords = listOf("佛山沙发家具"),
                    maxUsers = 5,
                    executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
                ),
                TaskDraft(
                    id = "m2-5-redwood",
                    name = "红木家具",
                    customKeywords = listOf("红木家具"),
                    maxUsers = 10,
                    executionMode = TaskExecutionMode.SAFE_BLANK_PROBE,
                ),
            ),
        )
    }

    /** Reads the M3 parameterized comment regression preset from a debug intent. */
    private fun readCommentRegressionPreset(source: android.content.Intent): CommentRegressionPreset =
        CommentRegressionPreset(
            targetUser = source.getStringExtra(EXTRA_COMMENT_TARGET_USER)?.takeIf { it.isNotBlank() } ?: "designer",
            matchKeywords = source.getStringExtra(EXTRA_COMMENT_MATCH_KEYWORDS).orEmpty(),
            matchMode = runCatching {
                CommentKeywordMatchMode.valueOf(
                    source.getStringExtra(EXTRA_COMMENT_MATCH_MODE)
                        ?.trim()
                        ?.uppercase()
                        ?: CommentKeywordMatchMode.ANY.name,
                )
            }.getOrDefault(CommentKeywordMatchMode.ANY),
            maxVideos = source.getIntExtra(EXTRA_COMMENT_MAX_VIDEOS, 1).coerceAtLeast(1),
            maxUsersPerVideo = source.getIntExtra(EXTRA_COMMENT_MAX_USERS, 1).coerceAtLeast(1),
            skipPinnedVideos = source.getBooleanExtra(EXTRA_COMMENT_SKIP_PINNED, false),
            dryRun = source.getBooleanExtra(EXTRA_COMMENT_DRY_RUN, false),
        )

    companion object {
        /** Monotonic token identifying each distinct OPEN_COMMENT_P0 debug launch. Static so it
         * survives recreate() (an instance field resets to 0 and re-arms the latch incorrectly). */
        @Volatile
        private var commentP0LaunchNonce: Int = 0

        /** Debug-device convenience for Unicode test data; production flow remains operator-driven. */
        const val EXTRA_PREFILL_KEYWORD = "com.example.douyinautomation.PREFILL_KEYWORD"
        const val EXTRA_OPEN_RECORDS = "com.example.douyinautomation.OPEN_RECORDS"
        const val EXTRA_SEED_MULTI_TASK_TESTS = "com.example.douyinautomation.SEED_MULTI_TASK_TESTS"
        /** Debug-only P0 regression shortcut; it reuses the visible “立即开始” task path. */
        const val EXTRA_OPEN_COMMENT_P0 = "com.example.douyinautomation.OPEN_COMMENT_P0"
        /** M3 parameterized regression extras; only read when OPEN_COMMENT_P0 is set. */
        const val EXTRA_COMMENT_TARGET_USER = "com.example.douyinautomation.COMMENT_TARGET_USER"
        const val EXTRA_COMMENT_MATCH_KEYWORDS = "com.example.douyinautomation.COMMENT_MATCH_KEYWORDS"
        const val EXTRA_COMMENT_MATCH_MODE = "com.example.douyinautomation.COMMENT_MATCH_MODE"
        const val EXTRA_COMMENT_MAX_VIDEOS = "com.example.douyinautomation.COMMENT_MAX_VIDEOS"
        const val EXTRA_COMMENT_MAX_USERS = "com.example.douyinautomation.COMMENT_MAX_USERS"
        const val EXTRA_COMMENT_SKIP_PINNED = "com.example.douyinautomation.COMMENT_SKIP_PINNED"
        /** Debug-only candidate inspection; it never taps a commenter or opens private messages. */
        const val EXTRA_COMMENT_DRY_RUN = "com.example.douyinautomation.COMMENT_DRY_RUN"
    }
}
