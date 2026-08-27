package com.example.douyinautomation.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.FloatingOverlayService
import com.example.douyinautomation.automation.LicenseStatus
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationCard
import com.example.douyinautomation.ui.theme.AutomationDivider
import com.example.douyinautomation.ui.theme.AutomationError
import com.example.douyinautomation.ui.theme.AutomationPage
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationSuccess
import com.example.douyinautomation.ui.theme.AutomationSuccessSurface
import com.example.douyinautomation.ui.theme.AutomationWarning
import com.example.douyinautomation.ui.theme.AutomationWarningSurface
import kotlinx.coroutines.launch

@Composable
internal fun MyPage(
    padding: PaddingValues,
    onOpenRecords: () -> Unit,
    onOpenMarketing: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSignOut: suspend () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val licenseState by AuthStore.uiState.collectAsState()
    val automationState by AutomationStore.uiState.collectAsState()
    var overlayAllowed by remember { mutableStateOf(FloatingOverlayService.canDrawOverlays(context)) }
    val account = licenseState.accountName
        ?: AuthStore.currentConfig()?.accountUsername
        ?: "未登录"
    val signedIn = AuthStore.currentConfig() != null
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }
    var signOutError by rememberSaveable { mutableStateOf<String?>(null) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val openAccessibilitySettings = remember(context) {
        {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    val openOverlaySettings = remember(context) {
        { FloatingOverlayService.openPermissionSettings(context) }
    }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayAllowed = FloatingOverlayService.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("退出登录") },
            text = {
                Text(
                    signOutError
                        ?: "将先通知服务端解除本机设备绑定，再清除本机已加密保存的授权信息。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        signingOut = true
                        signOutError = null
                        scope.launch {
                            runCatching { onSignOut() }
                                .onSuccess { showSignOutConfirmation = false }
                                .onFailure { error ->
                                    signOutError = "退出未同步：${error.message ?: "请检查网络后重试"}"
                                }
                            signingOut = false
                        }
                    },
                    enabled = !signingOut,
                ) {
                    Text(if (signingOut) "退出中…" else "退出", color = AutomationError)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !signingOut,
                    onClick = {
                        signOutError = null
                        showSignOutConfirmation = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AutomationSpacing.Page, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountHero(
            account = account,
            status = licenseState.status,
        )
        AutomationServiceNotice(
            connected = automationState.serviceConnected,
            onOpenAccessibilitySettings = openAccessibilitySettings,
        )
        OverlayFeatureNotice(
            enabled = overlayAllowed,
            onOpenOverlaySettings = openOverlaySettings,
        )

        MyMenuSection {
            MyMenuItem(
                icon = Icons.Default.Devices,
                title = "账号与设备",
                value = myStatusLabel(licenseState.status),
                onClick = onOpenSettings,
                showDivider = true,
            )
            MyMenuItem(
                icon = Icons.Default.Edit,
                title = "营销内容编辑",
                onClick = onOpenMarketing,
                showDivider = true,
            )
            MyMenuItem(
                icon = Icons.Default.History,
                title = "任务记录",
                onClick = onOpenRecords,
                showDivider = false,
            )
        }
        MyMenuSection {
            MyMenuItem(
                icon = Icons.Default.Settings,
                title = "自动化设置",
                onClick = onOpenSettings,
                showDivider = true,
            )
            MyMenuItem(
                icon = Icons.Default.Accessibility,
                title = "服务与诊断",
                onClick = onOpenDiagnostics,
                showDivider = true,
            )
            MyMenuItem(
                icon = Icons.Default.Cloud,
                title = "远程任务设置",
                onClick = onOpenSettings,
                showDivider = false,
            )
        }
        if (signedIn) {
            MyMenuSection {
                MyMenuItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出登录",
                    titleColor = AutomationError,
                    trailingIcon = false,
                    showDivider = false,
                    onClick = { showSignOutConfirmation = true },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AutomationServiceNotice(
    connected: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val tone = if (connected) AutomationSuccess else AutomationWarning
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (connected) AutomationSuccessSurface else AutomationWarningSurface,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Accessibility,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tone,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = "自动化服务",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ServiceEnableAction(
                enabled = connected,
                enabledLabel = "已开启",
                onEnable = onOpenAccessibilitySettings,
            )
        }
    }
}

@Composable
private fun OverlayFeatureNotice(
    enabled: Boolean,
    onOpenOverlaySettings: () -> Unit,
) {
    val tone = if (enabled) AutomationSuccess else AutomationWarning
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (enabled) AutomationSuccessSurface else AutomationWarningSurface,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tone,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = "悬浮窗功能",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ServiceEnableAction(
                enabled = enabled,
                enabledLabel = "已开启",
                onEnable = onOpenOverlaySettings,
            )
        }
    }
}

@Composable
private fun ServiceEnableAction(
    enabled: Boolean,
    enabledLabel: String,
    onEnable: () -> Unit,
) {
    if (enabled) {
        Text(
            text = enabledLabel,
            style = MaterialTheme.typography.labelLarge,
            color = AutomationSuccess,
            fontWeight = FontWeight.Medium,
        )
    } else {
        Box(
            modifier = Modifier
                .clickable(onClick = onEnable)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "点击开启",
                style = MaterialTheme.typography.labelLarge,
                color = AutomationBlue,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AccountHero(account: String, status: LicenseStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = AutomationBlue,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("自动化获客助手", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
            }
            AccountStatusPill(status = status)
        }
    }
}

@Composable
private fun AccountStatusPill(status: LicenseStatus) {
    Surface(
        color = Color.White.copy(alpha = 0.18f),
        contentColor = Color.White,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = myStatusLabel(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MyMenuSection(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AutomationCard,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun MyMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingIcon: Boolean = true,
    showDivider: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (titleColor == AutomationError) AutomationError else AutomationBlue,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (trailingIcon) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = AutomationDivider)
        }
    }
}

private fun myStatusLabel(status: LicenseStatus): String = when (status) {
    LicenseStatus.NOT_CONFIGURED -> "未登录"
    LicenseStatus.VERIFIED -> "已授权"
    LicenseStatus.REJECTED -> "需重新登录"
    LicenseStatus.TEMPORARILY_UNAVAILABLE -> "服务暂不可用"
}
