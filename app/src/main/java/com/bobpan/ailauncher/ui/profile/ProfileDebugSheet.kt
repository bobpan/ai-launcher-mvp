package com.bobpan.ailauncher.ui.profile

import com.bobpan.ailauncher.ui.components.ProfileDebugRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.Space
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDebugSheet(
    state: ProfileSheetState,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showResetDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LauncherPalette.BgElevated,
        scrimColor = Color(0xB80A0F1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = LauncherPalette.GlassOutlineSoft) },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl, vertical = Space.sm)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.titleLarge
                )
                val counter = when (state) {
                    is ProfileSheetState.Loaded -> state.counter
                    else -> 0
                }
                Text(
                    text = stringResource(R.string.profile_counter, counter),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = LauncherPalette.GlassOutlineSoft)

            when (state) {
                is ProfileSheetState.Loading -> {
                    repeat(6) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        )
                    }
                }
                is ProfileSheetState.Empty -> {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Space.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        Text("🌱", fontSize = 32.sp)
                        Text(
                            text = stringResource(R.string.profile_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is ProfileSheetState.Loaded -> {
                    val maxAbs = state.topTags.maxOfOrNull { abs(it.weight) }?.coerceAtLeast(0.01f) ?: 1f
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        state.topTags.forEach { entry ->
                            ProfileDebugRow(
                                tag = entry.tag,
                                weight = entry.weight,
                                normalizedFraction = abs(entry.weight) / maxAbs
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Button(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LauncherPalette.DangerRed.copy(alpha = 0.22f),
                            contentColor   = LauncherPalette.DangerRed
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("🗑  ${stringResource(R.string.profile_reset_cta)}")
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.profile_reset_dialog_title)) },
            text  = { Text(stringResource(R.string.profile_reset_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onReset()
                }) {
                    Text(stringResource(R.string.profile_reset_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.profile_reset_dialog_cancel))
                }
            },
            containerColor = LauncherPalette.BgElevated
        )
    }
}
