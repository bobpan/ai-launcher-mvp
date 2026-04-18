package com.bobpan.ailauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay

@Composable
fun FeedbackButtonRow(
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var upPressCount   by remember { mutableIntStateOf(0) }
    var downPressCount by remember { mutableIntStateOf(0) }
    var upActive   by remember { mutableStateOf(false) }
    var downActive by remember { mutableStateOf(false) }

    LaunchedEffect(upPressCount) {
        if (upPressCount > 0) { upActive = true; delay(600); upActive = false }
    }
    LaunchedEffect(downPressCount) {
        if (downPressCount > 0) { downActive = true; delay(600); downActive = false }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        FeedbackButton(
            contentDescription = stringResource(R.string.cd_thumb_up),
            label = "👍",
            activeColor = LauncherPalette.PositiveGreen,
            active = upActive,
            enabled = enabled,
            onClick = { upPressCount += 1; onThumbUp() }
        )
        FeedbackButton(
            contentDescription = stringResource(R.string.cd_thumb_down),
            label = "👎",
            activeColor = LauncherPalette.DangerRed,
            active = downActive,
            enabled = enabled,
            onClick = { downPressCount += 1; onThumbDown() }
        )
    }
}

@Composable
private fun FeedbackButton(
    contentDescription: String,
    label: String,
    activeColor: Color,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val targetBg = if (active) activeColor.copy(alpha = 0.24f) else LauncherPalette.GlassFillSoft
    val bg by animateColorAsState(targetBg, tween(180), label = "feedback_bg")
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = tween(180),
        label = "feedback_scale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .clip(LauncherShapes.small)
            .background(bg)
            .border(1.dp, LauncherPalette.GlassOutline, LauncherShapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            color = LauncherPalette.TextPrimary
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E)
@Composable
private fun FeedbackButtonRowPreview() {
    LauncherTheme {
        Box(
            Modifier
                .size(200.dp, 80.dp)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            FeedbackButtonRow(onThumbUp = {}, onThumbDown = {})
        }
    }
}
