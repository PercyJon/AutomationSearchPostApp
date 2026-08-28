package com.example.douyinautomation.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.example.douyinautomation.ui.theme.AutomationBlue
import com.example.douyinautomation.ui.theme.AutomationHeroShape
import com.example.douyinautomation.ui.theme.AutomationLoginOnBlue
import com.example.douyinautomation.ui.theme.AutomationSpacing

/** Blue page header with rounded bottom corners and an overlapping stats card. */
@Composable
fun AppPageHero(
    title: String,
    subtitle: String,
    columns: List<AppStatColumn>,
    modifier: Modifier = Modifier,
    edgeToStatusBar: Boolean = true,
) {
    if (edgeToStatusBar) {
        UseOnBlueStatusBars()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AutomationHeroShape)
                    .background(AutomationBlue)
                    .then(if (edgeToStatusBar) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = AutomationSpacing.Page)
                    .padding(
                        top = AutomationSpacing.Hero,
                        bottom = AutomationSpacing.Hero + AutomationSpacing.HeroOverlap,
                    ),
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AutomationLoginOnBlue.copy(alpha = 0.88f),
                )
                Spacer(modifier = Modifier.height(AutomationSpacing.Tight))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AutomationLoginOnBlue,
                )
            }
            AppCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = AutomationSpacing.Page)
                    .offset(y = AutomationSpacing.HeroOverlap),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = AutomationSpacing.Card,
                        vertical = AutomationSpacing.Item,
                    ),
                ) {
                    AppHeroStats(headline = "", columns = columns)
                }
            }
        }
        Spacer(modifier = Modifier.height(AutomationSpacing.HeroOverlap + AutomationSpacing.Hero))
    }
}

@Composable
internal fun UseOnBlueStatusBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            return@DisposableEffect onDispose {}
        }
        val controller = WindowInsetsControllerCompat(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
