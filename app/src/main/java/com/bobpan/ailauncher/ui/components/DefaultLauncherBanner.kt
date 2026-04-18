package com.bobpan.ailauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.unit.sp
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherShapes
import com.bobpan.ailauncher.ui.theme.LauncherTheme
import com.bobpan.ailauncher.ui.theme.Space
import com.bobpan.ailauncher.ui.theme.glassSurface

@Composable
fun DefaultLauncherBanner(
    onSetDefault: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desc = stringResource(R.string.banner_default)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .glassSurface(
                shape = LauncherShapes.medium,
                elevated = false,
                accent = LauncherPalette.AccentBlue
            )
            .clickable(onClick = onSetDefault)
            .padding(horizontal = Space.md, vertical = Space.sm)
            .semantics { contentDescription = "设为默认启动器，点击前往设置" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🏠", fontSize = 20.sp, modifier = Modifier.padding(end = Space.sm))
        Text(
            text = desc,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
            color = LauncherPalette.TextPrimary
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_dismiss_banner),
                tint = LauncherPalette.TextMuted
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E, widthDp = 380, heightDp = 80)
@Composable
private fun DefaultLauncherBannerPreview() {
    LauncherTheme {
        Box(Modifier.padding(16.dp)) {
            DefaultLauncherBanner(onSetDefault = {}, onDismiss = {})
        }
    }
}
