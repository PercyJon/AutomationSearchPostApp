package com.example.douyinautomation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationBlueSoft
import com.example.douyinautomation.ui.theme.AutomationCard
import com.example.douyinautomation.ui.theme.AutomationChipShape
import com.example.douyinautomation.ui.theme.AutomationField
import com.example.douyinautomation.ui.theme.AutomationFieldBorder
import com.example.douyinautomation.ui.theme.AutomationPill
import com.example.douyinautomation.ui.theme.AutomationSizing
import com.example.douyinautomation.ui.theme.AutomationSpacing
import com.example.douyinautomation.ui.theme.AutomationText
import com.example.douyinautomation.ui.theme.AutomationTextSecondary

@Composable
fun AppSegmentedTab(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .shadow(4.dp, AutomationPill, clip = false)
            .clip(AutomationPill)
            .background(AutomationCard)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(AutomationSizing.ControlCompact)
                    .clip(AutomationPill)
                    .background(if (selected) AutomationBlue else AutomationCard)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else AutomationText,
                )
            }
        }
    }
}

@Composable
fun AppRadio(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(AutomationSizing.ControlCompact)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AutomationSizing.Icon)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) AutomationBlue else AutomationFieldBorder,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(AutomationSpacing.Compact)
                        .clip(CircleShape)
                        .background(AutomationBlue),
                )
            }
        }
    }
}

@Composable
fun AppChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    dropdown: Boolean = false,
) {
    val background = if (selected) AutomationBlueSoft else AutomationField
    val content = if (selected) AutomationBlue else AutomationText
    Row(
        modifier = modifier
            .clip(AutomationChipShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(AutomationSizing.Icon),
                tint = content,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
        if (dropdown) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = AutomationTextSecondary,
            )
        }
    }
}

@Composable
fun AppFilterPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AutomationSpacing.Compact),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(AutomationPill)
                    .background(if (selected) AutomationBlueSoft else AutomationField)
                    .border(
                        width = 1.dp,
                        color = if (selected) AutomationBlue.copy(alpha = 0.35f) else AutomationFieldBorder,
                        shape = AutomationPill,
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) AutomationBlue else AutomationTextSecondary,
                )
            }
        }
    }
}
