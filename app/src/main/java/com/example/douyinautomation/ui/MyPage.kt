package com.example.douyinautomation.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.automation.AutomationStore
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.automation.LicenseStatus
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationBlueLight
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
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSignOut: suspend () -> Unit,
) {
    val licenseState by AuthStore.uiState.collectAsState()
    val automationState by AutomationStore.uiState.collectAsState()
    val account = licenseState.accountName
        ?: AuthStore.currentConfig()?.accountUsername
        ?: "未登录"
    val signedIn = AuthStore.currentConfig() != null
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }
    var signOutError by rememberSaveable { mutableStateOf<String?>(null) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
        AuthorizationNotice(status = licenseState.status, message = licenseState.message)
        AutomationServiceNotice(connected = automationState.serviceConnected)

        MyMenuSection {
            MyMenuItem(
                icon = Icons.Default.Devices,
                title = "账号与设备",
                value = myStatusLabel(licenseState.status),
                onClick = onOpenSettings,
            )
            MyMenuItem(
                icon = Icons.Default.History,
                title = "任务记录",
                onClick = onOpenRecords,
            )
        }
        MyMenuSection {
            MyMenuItem(
                icon = Icons.Default.Settings,
                title = "自动化设置",
                onClick = onOpenSettings,
            )
            MyMenuItem(
                icon = Icons.Default.Accessibility,
                title = "服务与诊断",
                onClick = onOpenDiagnostics,
            )
            MyMenuItem(
                icon = Icons.Default.Cloud,
                title = "远程任务设置",
                onClick = onOpenSettings,
            )
        }
        if (signedIn) {
            MyMenuSection {
                MyMenuItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出登录",
                    titleColor = AutomationError,
                    trailingIcon = false,
                    onClick = { showSignOutConfirmation = true },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun AutomationServiceNotice(connected: Boolean) {
    val tone = if (connected) AutomationSuccess else AutomationWarning
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (connected) AutomationSuccessSurface else AutomationWarningSurface,
        shape = RoundedCornerShape(10.dp),
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
            Text(
                text = if (connected) "正常" else "未开启",
                style = MaterialTheme.typography.labelLarge,
                color = tone,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AccountHero(account: String, status: LicenseStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
        shape = RoundedCornerShape(14.dp),
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
private fun AuthorizationNotice(status: LicenseStatus, message: String) {
    val color = when (status) {
        LicenseStatus.VERIFIED -> AutomationSuccess
        LicenseStatus.REJECTED -> AutomationError
        LicenseStatus.TEMPORARILY_UNAVAILABLE -> AutomationWarning
        LicenseStatus.NOT_CONFIGURED -> AutomationBlue
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AutomationBlueLight,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MyMenuSection(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AutomationCard,
        shape = RoundedCornerShape(12.dp),
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
        HorizontalDivider(color = AutomationDivider)
    }
}

private fun myStatusLabel(status: LicenseStatus): String = when (status) {
    LicenseStatus.NOT_CONFIGURED -> "未登录"
    LicenseStatus.VERIFIED -> "已授权"
    LicenseStatus.REJECTED -> "需重新登录"
    LicenseStatus.TEMPORARILY_UNAVAILABLE -> "服务暂不可用"
}
