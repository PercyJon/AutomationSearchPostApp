package com.example.douyinautomation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationCard
import com.example.douyinautomation.ui.theme.AutomationCardShape
import com.example.douyinautomation.ui.theme.AutomationDivider
import com.example.douyinautomation.ui.theme.AutomationFieldBorder
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationText
import com.example.douyinautomation.ui.theme.AutomationTextSecondary

data class AppStatColumn(
    val value: String,
    val label: String,
)

@Composable
fun AppHeroStats(
    headline: String,
    columns: List<AppStatColumn>,
    modifier: Modifier = Modifier,
    headlineIcon: ImageVector? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val showHeadline = headline.isNotBlank() || headlineIcon != null
        if (showHeadline) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (headlineIcon != null) {
                    Icon(
                        imageVector = headlineIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = AutomationBlue,
                    )
                    Spacer(modifier = Modifier.width(AutomationSpacing.Compact))
                }
                if (headline.isNotBlank()) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AutomationText,
                    )
                }
            }
        }
        if (columns.isNotEmpty()) {
            if (showHeadline) {
                Spacer(modifier = Modifier.height(AutomationSpacing.Item))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                columns.forEachIndexed { index, column ->
                    if (index > 0) {
                        VerticalDivider(
                            modifier = Modifier.height(22.dp),
                            color = AutomationDivider,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = column.value,
                            style = MaterialTheme.typography.titleMedium,
                            color = AutomationText,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = column.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = AutomationTextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppHistoryRow(
    title: String,
    subtitle: String,
    timestamp: String,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeTone: AppStatusTone = AppStatusTone.Neutral,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AutomationCard,
        shape = AutomationCardShape,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AutomationFieldBorder),
    ) {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = AutomationSpacing.Card, vertical = 8.dp)
                .heightIn(min = 56.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Compact),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Compact),
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AutomationText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!badgeText.isNullOrBlank()) {
                            AppStatusBadge(text = badgeText, tone = badgeTone)
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AutomationTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = AutomationTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (trailing != null) {
                    trailing()
                }
            }
        }
    }
}

@Composable
fun AppSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactControl {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.82f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AutomationBlue,
                ),
            )
        }
        Spacer(modifier = Modifier.width(AutomationSpacing.Compact))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AutomationText,
            )
            if (!caption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = AutomationTextSecondary,
                )
            }
        }
    }
}
