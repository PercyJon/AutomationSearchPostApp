package com.example.douyinautomation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationBlueSoft
import com.example.douyinautomation.ui.theme.AutomationDivider
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationText
import com.example.douyinautomation.ui.theme.AutomationTextSecondary

data class AppKeyValue(
    val label: String,
    val value: String,
    val valueColor: Color = AutomationText,
)

/** Centered icon + title + value for a task detail hero. Layout from app4. */
@Composable
fun AppDetailHero(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconBackground: Color = AutomationBlueSoft,
    iconTint: Color = AutomationBlue,
    valueColor: Color = AutomationText,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.height(AutomationSpacing.Item))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = AutomationText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(AutomationSpacing.Tight))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = valueColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AppKeyValueTable(
    rows: List<AppKeyValue>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { row ->
            AppKeyValueRow(row)
        }
    }
}

@Composable
fun AppKeyValueGroups(
    groups: List<List<AppKeyValue>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        groups.forEachIndexed { index, group ->
            AppKeyValueTable(group)
            if (index != groups.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = AutomationSpacing.Tight),
                    color = AutomationDivider,
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
fun AppKeyValueRow(
    row: AppKeyValue,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AutomationSpacing.Tight),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Card),
    ) {
        Text(
            text = row.label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = AutomationTextSecondary,
            textAlign = TextAlign.Start,
        )
        Text(
            text = row.value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = row.valueColor,
        )
    }
}
