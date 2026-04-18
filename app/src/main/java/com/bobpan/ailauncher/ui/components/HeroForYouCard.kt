package com.bobpan.ailauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.CardType
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherShapes
import com.bobpan.ailauncher.ui.theme.LauncherTheme
import com.bobpan.ailauncher.ui.theme.Motion
import com.bobpan.ailauncher.ui.theme.Space
import com.bobpan.ailauncher.ui.theme.glassSurface
import kotlinx.coroutines.delay

enum class HeroState { Loaded, Loading, Pulsing }

/**
 * Hero "FOR YOU" card (DESIGN §5.1).
 *
 * @param pulseToken increment to trigger a 260ms feedback-confirm pulse; no-op on 0.
 */
@Composable
fun HeroForYouCard(
    card: Card,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onAction: () -> Unit,
    fraction: Float,
    modifier: Modifier = Modifier,
    state: HeroState = HeroState.Loaded,
    pulseToken: Int = 0,
    storageHealthy: Boolean = true
) {
    val isLoading = state == HeroState.Loading
    var pulsing by remember { mutableStateOf(false) }
    LaunchedEffect(pulseToken) {
        if (pulseToken > 0) {
            pulsing = true
            delay(260)
            pulsing = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pulsing) 1.04f else 1f,
        animationSpec = tween(260),
        label = "hero_scale"
    )
    val borderAccent by animateColorAsState(
        targetValue = if (pulsing) LauncherPalette.PositiveGreen else LauncherPalette.AccentBlue,
        animationSpec = tween(200),
        label = "hero_border"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .defaultMinSize(minHeight = 208.dp)
            .glassSurface(
                shape = LauncherShapes.large,
                elevated = true,
                accent = borderAccent
            )
            .padding(Space.lg)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon
            Box(
                Modifier
                    .size(44.dp)
                    .clip(LauncherShapes.small)
                    .background(LauncherPalette.GlassFillSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(text = card.icon, fontSize = 28.sp)
            }

            Text(
                text = card.title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = card.description,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "✧ ${card.whyLabel}",
                color = LauncherPalette.AccentBlue.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAction,
                    shape = LauncherShapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(end = Space.sm)
                        .semantics { contentDescription = card.actionLabel }
                ) {
                    Text(
                        text = card.actionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                FeedbackButtonRow(
                    onThumbUp = onThumbUp,
                    onThumbDown = onThumbDown,
                    enabled = !isLoading && storageHealthy
                )
            }

            Spacer(Modifier.height(4.dp))
            ConfidenceBar(fraction = fraction)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E, widthDp = 360, heightDp = 320)
@Composable
private fun HeroForYouCardPreview() {
    LauncherTheme {
        Box(Modifier.padding(16.dp)) {
            HeroForYouCard(
                card = Card(
                    id = "work_01", intent = Intent.WORK, type = CardType.CONTINUE,
                    icon = "🎨", title = "继续 Figma 稿件",
                    description = "示例文件 \"Launcher v2 - Home\"",
                    actionLabel = "打开 Figma", whyLabel = "示例：最近编辑的设计稿",
                    tags = listOf("design", "figma", "work", "focus")
                ),
                onThumbUp = {}, onThumbDown = {}, onAction = {},
                fraction = 0.72f
            )
        }
    }
}
