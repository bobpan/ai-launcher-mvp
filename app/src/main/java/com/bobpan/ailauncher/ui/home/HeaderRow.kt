package com.bobpan.ailauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.Space
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HeaderRow(
    state: HomeUiState.Content,
    onOpenProfile: () -> Unit,
    onLongPressProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val time = remember_now()
    val headerText = when (state.selectedIntent) {
        Intent.COMMUTE -> stringResource(R.string.header_commute, time)
        Intent.WORK    -> stringResource(R.string.header_work,    time)
        Intent.LUNCH   -> stringResource(R.string.header_lunch,   time)
        Intent.REST    -> stringResource(R.string.header_rest,    time)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.greeting_kai),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = headerText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
            if (!state.storageHealthy) {
                Text(
                    text = "⚠",
                    fontSize = 16.sp,
                    color = LauncherPalette.DangerRed.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .semantics { contentDescription = "存储不可用，部分功能受限" }
                )
            }
            ProfileBadge(
                onClick = onOpenProfile,
                onLongPress = onLongPressProfile
            )
        }
    }
}

@Composable
private fun ProfileBadge(
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val cd = stringResource(R.string.cd_profile_badge)
    Box(
        modifier = Modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LauncherPalette.GlassFillStrong)
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            LauncherPalette.AccentBlue.copy(alpha = 0.6f),
                            LauncherPalette.GlassOutlineSoft
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "P·",
                style = MaterialTheme.typography.labelMedium,
                color = LauncherPalette.TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun remember_now(): String {
    // Formatted once per recomposition — fine for v0.1 static-clock (FR-04).
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
}
