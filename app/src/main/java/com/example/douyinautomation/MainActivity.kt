package com.example.douyinautomation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.ui.AppHomeScreen

class MainActivity : ComponentActivity() {
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRefresh = Runnable { AutomationStore.refreshServiceStatus(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AuthStore.initialize(this)

        setContent {
            DouyinAutomationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppHomeScreen(
                        initialKeyword = intent.getStringExtra(EXTRA_PREFILL_KEYWORD).orEmpty(),
                    )
                }
            }
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

    companion object {
        /** Debug-device convenience for Unicode test data; production flow remains operator-driven. */
        const val EXTRA_PREFILL_KEYWORD = "com.example.douyinautomation.PREFILL_KEYWORD"
    }
}

private val PocColorScheme = lightColorScheme()

@androidx.compose.runtime.Composable
private fun DouyinAutomationTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocColorScheme,
        content = content,
    )
}
