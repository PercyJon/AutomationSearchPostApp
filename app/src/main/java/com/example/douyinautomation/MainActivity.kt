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
import com.example.douyinautomation.automation.TaskDraft
import com.example.douyinautomation.automation.TaskExecutionMode
import com.example.douyinautomation.ui.AppHomeScreen
import com.example.douyinautomation.ui.theme.AutomationTheme

class MainActivity : ComponentActivity() {
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresh = Runnable { AutomationStore.refreshServiceStatus(this) }
    private var openRecordsTab: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRecordsTab = intent.getBooleanExtra(EXTRA_OPEN_RECORDS, false)
        AuthStore.initialize(this)
        AutomationStore.initialize(this)
        seedMultiTaskFixtureIfRequested(intent)

        setContent {
            AutomationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppHomeScreen(
                        initialKeyword = intent.getStringExtra(EXTRA_PREFILL_KEYWORD).orEmpty(),
                        initialSection = if (openRecordsTab) "RECORDS" else null,
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

    companion object {
        /** Debug-device convenience for Unicode test data; production flow remains operator-driven. */
        const val EXTRA_PREFILL_KEYWORD = "com.example.douyinautomation.PREFILL_KEYWORD"
        const val EXTRA_OPEN_RECORDS = "com.example.douyinautomation.OPEN_RECORDS"
        const val EXTRA_SEED_MULTI_TASK_TESTS = "com.example.douyinautomation.SEED_MULTI_TASK_TESTS"
    }
}
