package com.bobpan.ailauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.bobpan.ailauncher.data.model.AppInfo
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherShapes

@Composable
fun AppIconTile(
    app: AppInfo,
    onClick: () -> Unit,
    loadIcon: suspend (String) -> android.graphics.drawable.Drawable?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        val drawable = loadIcon(app.packageName)
        bitmap = drawable?.let { runCatching { it.toBitmap().asImageBitmap() }.getOrNull() }
    }

    Column(
        modifier = modifier
            .width(72.dp)
            .clip(LauncherShapes.medium)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(LauncherShapes.medium)
                .background(LauncherPalette.GlassFillSoft),
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let { bm ->
                Image(bitmap = bm, contentDescription = null, modifier = Modifier.size(44.dp))
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = LauncherPalette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
        Spacer(Modifier.height(2.dp))
    }
}
