package com.bobpan.ailauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherShapes
import com.bobpan.ailauncher.ui.theme.LauncherTheme
import com.bobpan.ailauncher.ui.theme.Space
import com.bobpan.ailauncher.ui.theme.glassSurface

@Composable
fun AiDock(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPressProfile: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .glassSurface(shape = LauncherShapes.medium, elevated = true)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = stringResource(R.string.cd_open_drawer),
                tint = LauncherPalette.TextPrimary
            )
        }

        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 40.dp)
                .glassSurface(shape = LauncherShapes.small, elevated = false)
                .padding(horizontal = Space.md, vertical = Space.xs)
                .semantics { contentDescription = "AI 助理即将上线" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.ai_pill),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 48dp spacer for visual balance.
        Box(Modifier.size(48.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E, widthDp = 380, heightDp = 120)
@Composable
private fun AiDockPreview() {
    LauncherTheme {
        Box(Modifier.padding(16.dp)) {
            AiDock(onOpenDrawer = {})
        }
    }
}
