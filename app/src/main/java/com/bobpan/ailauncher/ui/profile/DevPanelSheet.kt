package com.bobpan.ailauncher.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevPanelSheet(
    onSeedDemo: () -> Unit,
    onDumpProfile: () -> Unit,
    onClearDismiss: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text(
                text = stringResource(R.string.dev_panel_title),
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = onSeedDemo,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LauncherPalette.AccentBlue.copy(alpha = 0.18f),
                    contentColor   = LauncherPalette.AccentBlue
                )
            ) { Text(stringResource(R.string.dev_seed_demo)) }

            Button(
                onClick = onDumpProfile,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LauncherPalette.GlassFillStrong,
                    contentColor   = LauncherPalette.TextPrimary
                )
            ) { Text(stringResource(R.string.dev_dump_profile)) }

            Button(
                onClick = onClearDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LauncherPalette.GlassFillStrong,
                    contentColor   = LauncherPalette.TextPrimary
                )
            ) { Text(stringResource(R.string.dev_clear_dismiss)) }
        }
    }
}
