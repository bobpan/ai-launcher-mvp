package com.bobpan.ailauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherShapes
import com.bobpan.ailauncher.ui.theme.LauncherTheme
import com.bobpan.ailauncher.ui.theme.Space
import com.bobpan.ailauncher.ui.theme.glassSurface

@Composable
fun IntentChip(
    intent: Intent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = intentLabel(intent)
    val selBgColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val bg by animateColorAsState(selBgColor, tween(180), label = "chip_bg")
    val labelColor = if (selected) MaterialTheme.colorScheme.onPrimary else LauncherPalette.TextPrimary

    val cdSel = stringResource(R.string.cd_chip_selected, label)
    val cdNot = stringResource(R.string.cd_chip_unselected, label)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .wrapContentHeight()
            .clip(LauncherShapes.small)
            .then(
                if (selected) Modifier.background(bg)
                else Modifier.glassSurface(shape = LauncherShapes.small, elevated = false)
            )
            .border(
                width = 1.dp,
                color = if (selected) LauncherPalette.AccentBlue.copy(alpha = 0.6f)
                        else LauncherPalette.GlassOutline,
                shape = LauncherShapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 12.dp else 16.dp, vertical = 12.dp)
            .semantics {
                this.role = Role.Tab
                this.selected = selected
                this.contentDescription = if (selected) cdSel else cdNot
            }
    ) {
        if (selected) {
            Text(
                text = "✓",
                color = labelColor,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun IntentChipStrip(
    selected: Intent,
    onSelect: (Intent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(Intent.COMMUTE, Intent.WORK, Intent.LUNCH, Intent.REST).forEach { intent ->
            IntentChip(
                intent = intent,
                selected = intent == selected,
                onClick = { onSelect(intent) }
            )
        }
    }
}

@Composable
private fun intentLabel(intent: Intent): String = when (intent) {
    Intent.COMMUTE -> stringResource(R.string.chip_commute)
    Intent.WORK    -> stringResource(R.string.chip_work)
    Intent.LUNCH   -> stringResource(R.string.chip_lunch)
    Intent.REST    -> stringResource(R.string.chip_rest)
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E, widthDp = 380)
@Composable
private fun IntentChipStripPreview() {
    LauncherTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            IntentChipStrip(selected = Intent.WORK, onSelect = {})
        }
    }
}
