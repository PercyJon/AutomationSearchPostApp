package com.example.douyinautomation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.BuildConfig
import com.example.douyinautomation.CommentRegressionPreset
import com.example.douyinautomation.automation.AuthStore
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationBlueLight
import com.example.douyinautomation.ui.theme.AutomationError
import com.example.douyinautomation.ui.theme.AutomationPage
import com.example.douyinautomation.ui.theme.AutomationSpacing
import kotlinx.coroutines.launch

/**
 * The app entry gate is intentionally driven by the encrypted local mobile-license session.
 * A login success updates [AuthStore.session], which atomically replaces this screen with the
 * existing application rather than duplicating navigation state in the login page.
 */
@Composable
internal fun AutomationAppSessionGate(
    initialKeyword: String = "",
    initialSection: String? = null,
    initialCommentTask: Boolean = false,
    autoStartCommentP0: Boolean = false,
    commentP0LaunchToken: Int = 0,
    commentRegressionPreset: CommentRegressionPreset? = null,
) {
    val session by AuthStore.session.collectAsState()

    if (session?.isUsable() == true) {
        AppHomeScreen(
            initialKeyword = initialKeyword,
            initialSection = initialSection,
            initialCommentTask = initialCommentTask,
            autoStartCommentP0 = autoStartCommentP0,
            commentP0LaunchToken = commentP0LaunchToken,
            commentRegressionPreset = commentRegressionPreset,
        )
    } else {
        LoginScreen(endpoint = BuildConfig.AUTOMATION_API_ENDPOINT)
    }
}

@Composable
private fun LoginScreen(endpoint: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val normalizedEndpoint = remember(endpoint) { endpoint.trim().trimEnd('/') }
    val endpointReady = normalizedEndpoint.startsWith("https://")
    var username by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately stay out of SavedState so process restore cannot retain one.
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        if (!endpointReady || submitting) return
        scope.launch {
            submitting = true
            message = null
            runCatching {
                AuthStore.login(
                    context = context,
                    endpoint = normalizedEndpoint,
                    username = username,
                    password = password,
                )
            }.onSuccess {
                password = ""
            }.onFailure { error ->
                message = error.message?.takeIf(String::isNotBlank)
                    ?: "登录失败，请检查账号或密码后重试"
            }
            submitting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AutomationPage)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AutomationSpacing.Page, vertical = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(AutomationBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(29.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("欢迎登录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "登录后即可使用已授权的自动化任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LoginTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            message = null
                        },
                        label = "账号",
                        leadingIcon = Icons.Default.Person,
                        keyboardType = KeyboardType.Text,
                    )
                    LoginTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            message = null
                        },
                        label = "密码",
                        leadingIcon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                )
                            }
                        },
                    )
                    if (!endpointReady) {
                        Text(
                            "当前安装包尚未配置服务地址，请联系管理员获取受管版本。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AutomationError,
                        )
                    }
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = AutomationError)
                    }
                    Button(
                        onClick = ::submit,
                        enabled = endpointReady && !submitting && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AutomationBlue),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (submitting) "登录中" else "登录")
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            color = AutomationBlueLight,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                "账号首次登录会绑定本设备。密码不会保存，本机仅加密保存设备绑定后的移动端授权。",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        label = { Text(label) },
        singleLine = true,
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AutomationBlue,
            focusedLabelColor = AutomationBlue,
        ),
        shape = RoundedCornerShape(8.dp),
    )
}
