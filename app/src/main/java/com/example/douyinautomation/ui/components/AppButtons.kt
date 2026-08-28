package com.example.douyinautomation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationField
import com.example.douyinautomation.ui.theme.AutomationFieldShape
import com.example.douyinautomation.ui.theme.AutomationPill
import com.example.douyinautomation.ui.theme.AutomationSizing
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationText

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = AutomationPill,
) {
    CompactControl {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.heightIn(min = AutomationSizing.Control),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AutomationBlue,
                contentColor = Color.White,
                disabledContainerColor = AutomationBlue.copy(alpha = 0.4f),
                disabledContentColor = Color.White.copy(alpha = 0.9f),
            ),
            contentPadding = PaddingValues(horizontal = AutomationSpacing.Card, vertical = 8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(AutomationSpacing.Compact))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CompactControl {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = AutomationSizing.Control),
            shape = AutomationFieldShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AutomationField,
                contentColor = AutomationText,
            ),
            contentPadding = PaddingValues(horizontal = AutomationSpacing.Card, vertical = 8.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AppButtonRow(
    secondaryText: String,
    onSecondary: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryEnabled: Boolean = true,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Item),
    ) {
        AppSecondaryButton(
            text = secondaryText,
            onClick = onSecondary,
            modifier = Modifier.weight(1f),
            enabled = secondaryEnabled,
        )
        AppPrimaryButton(
            text = primaryText,
            onClick = onPrimary,
            modifier = Modifier.weight(1f),
            enabled = primaryEnabled,
            loading = primaryLoading,
            shape = AutomationFieldShape,
        )
    }
}

@Composable
internal fun CompactControl(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified, content = content)
}
