package com.example.douyinautomation.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.automation.AutomationCommand
import com.example.douyinautomation.automation.AutomationPhase
import com.example.douyinautomation.automation.AutomationStore

/**
 * An on-device control surface for the M0 proof of concept.
 *
 * It intentionally exposes diagnostics rather than trying to conceal failures: an operator can
 * inspect the current page, node dump, screenshot and OCR result before accepting the next step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    initialKeyword: String = "",
) {
    val state by AutomationStore.uiState.collectAsState()
    val context = LocalContext.current
    var keyword by rememberSaveable(initialKeyword) { mutableStateOf(initialKeyword) }
    var message by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TopAppBar(
                title = {
                    Column {
                        Text("Douyin Automation POC")
                        Text(
                            text = "M1 diagnostics and controlled message test",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        item {
            ServiceStatusCard(
                connected = state.serviceConnected,
                statusKnown = state.serviceStatusKnown,
                onOpenSettings = { openAccessibilitySettings(context) },
            )
        }

        if (state.awaitingManualHandoff) {
            item {
                ManualHandoffCard()
            }
        }

        item {
            TaskControlCard(
                keyword = keyword,
                onKeywordChange = { keyword = it },
                message = message,
                onMessageChange = { message = it },
                keywordPresets = DEFAULT_TEST_KEYWORDS,
                onStart = {
                    val normalizedKeyword = keyword.trim()
                    if (normalizedKeyword.isNotEmpty()) {
                        AutomationStore.send(
                            AutomationCommand.Start(
                                keyword = normalizedKeyword,
                                message = message.trim(),
                            ),
                        )
                    }
                },
                onPause = { AutomationStore.send(AutomationCommand.Pause) },
                onResume = { AutomationStore.send(AutomationCommand.Resume) },
                onStop = { AutomationStore.send(AutomationCommand.Stop) },
                canSendMessage = state.phase == AutomationPhase.COMPLETED_AT_MESSAGE_PAGE,
                onSendMessage = {
                    val normalizedMessage = message.trim()
                    if (normalizedMessage.isNotEmpty()) {
                        AutomationStore.send(AutomationCommand.SendMessage(normalizedMessage))
                    }
                },
            )
        }

        item {
            DiagnosticsActionsCard(
                onCapture = { AutomationStore.send(AutomationCommand.CaptureDiagnostics) },
                onDumpNodeTree = { AutomationStore.send(AutomationCommand.DumpNodeTree) },
            )
        }

        item {
            StateCard(
                phase = state.phase,
                lastPage = state.lastPage,
                lastNodeDumpPath = state.lastNodeDumpPath,
                lastScreenshotPath = state.lastScreenshotPath,
                lastOcrText = state.lastOcrText,
                lastError = state.lastError,
            )
        }

        item {
            Text(
                text = "Diagnostic log",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (state.diagnosticEntries.isEmpty()) {
            item {
                Text(
                    text = "No diagnostic events yet. Enable the accessibility service, then capture diagnostics.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.diagnosticEntries.takeLast(MAX_VISIBLE_LOGS).asReversed()) { entry ->
                LogEntryCard(
                    text = entry.toString(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ServiceStatusCard(
    connected: Boolean,
    statusKnown: Boolean,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    !statusKnown -> "Checking accessibility service…"
                    connected -> "Accessibility service connected"
                    else -> "Accessibility service not connected"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    !statusKnown -> MaterialTheme.colorScheme.onSurface
                    connected -> SuccessGreen
                    else -> MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    !statusKnown -> "Checking the Android accessibility service status."
                    connected -> {
                    "The POC can inspect the active window and run explicitly requested diagnostics."
                    }
                    else -> {
                    "Enable “Douyin automation diagnostics” in Android Accessibility settings before testing."
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenSettings) {
                Text("Open accessibility settings")
            }
        }
    }
}

@Composable
private fun ManualHandoffCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Manual action required", fontWeight = FontWeight.Bold)
            Text(
                "The automation has paused for a verification, risk notice, or other ambiguous state. Complete or dismiss it manually, then review diagnostics before resuming.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TaskControlCard(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    keywordPresets: List<String>,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    canSendMessage: Boolean,
    onSendMessage: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Guided POC flow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Leave the message blank to verify the private-message page only. If a message is supplied, one send is attempted automatically after the page is verified.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search keyword") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message to send once (optional)") },
                placeholder = { Text("Leave blank to verify the private-message page only") },
                minLines = 2,
                maxLines = 4,
                supportingText = { Text("The send button is enabled only after a private-message page is verified.") },
            )
            Text(
                text = "Test keyword presets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                keywordPresets.forEach { preset ->
                    AssistChip(
                        onClick = { onKeywordChange(preset) },
                        label = { Text(preset) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStart,
                    enabled = keyword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start test")
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSendMessage,
                    enabled = canSendMessage && message.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Send once on verified chat")
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text("Pause")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text("Resume")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsActionsCard(
    onCapture: () -> Unit,
    onDumpNodeTree: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Capture diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Capture a service screenshot with OCR and export the current accessibility node tree for selector review.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onCapture, modifier = Modifier.weight(1f)) {
                    Text("Screenshot + OCR")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onDumpNodeTree, modifier = Modifier.weight(1f)) {
                    Text("Dump node tree")
                }
            }
        }
    }
}

@Composable
private fun StateCard(
    phase: Any?,
    lastPage: Any?,
    lastNodeDumpPath: Any?,
    lastScreenshotPath: Any?,
    lastOcrText: Any?,
    lastError: Any?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Latest state", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DetailRow("Phase", phase)
            DetailRow("Detected page", lastPage)
            DetailRow("Node dump", lastNodeDumpPath)
            DetailRow("Screenshot", lastScreenshotPath)
            DetailRow("OCR", lastOcrText)
            DetailRow("Error", lastError)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: Any?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.width(104.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value?.toString().orEmpty().ifBlank { "—" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LogEntryCard(text: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private const val MAX_VISIBLE_LOGS = 100
private val DEFAULT_TEST_KEYWORDS = listOf(
    "红木沙发",
    "是小瑜瑜呀~",
    "实木餐桌",
    "茶桌",
)
private val SuccessGreen = Color(0xFF166534)
