package com.bobpan.ailauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherShapes
import com.bobpan.ailauncher.ui.theme.Space
import com.bobpan.ailauncher.ui.theme.glassSurface

@Composable
fun EmptyIntentState(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 208.dp)
            .glassSurface(
                shape = LauncherShapes.large,
                elevated = true,
                accent = LauncherPalette.AccentPink
            )
            .padding(Space.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text("🌱", fontSize = 36.sp)
            Text(
                text = stringResource(R.string.empty_intent_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_intent_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onRestore,
                shape = LauncherShapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LauncherPalette.AccentPink
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    LauncherPalette.AccentPink.copy(alpha = 0.6f)
                )
            ) {
                Text(stringResource(R.string.empty_intent_cta))
            }
        }
    }
}
