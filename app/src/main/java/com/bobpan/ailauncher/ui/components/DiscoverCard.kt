package com.bobpan.ailauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.CardType
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherShapes
import com.bobpan.ailauncher.ui.theme.LauncherTheme
import com.bobpan.ailauncher.ui.theme.Space
import com.bobpan.ailauncher.ui.theme.glassSurface

/** Visual accent stripe for NEW cards. Non-color redundancy (FR-18). */
@Composable
private fun NewAccentStripe(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .width(2.dp)
            .background(LauncherPalette.AccentPink)
    )
}

@Composable
private fun NewPill() {
    Box(
        Modifier
            .clip(LauncherShapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "新推荐" }
    ) {
        Text(
            text = "NEW",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun DiscoverCard(
    card: Card,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onVisibilityChange: (fraction: Float) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 160.dp)
            .glassSurface(shape = LauncherShapes.medium, elevated = false)
            .onGloballyPositioned { coords ->
                val h = coords.size.height.toFloat().coerceAtLeast(1f)
                val root = coords.findRootCoordinates()
                val windowTop = coords.positionInRoot().y
                val windowBottom = windowTop + h
                val visibleTop = maxOf(windowTop, 0f)
                val visibleBottom = minOf(windowBottom, root.size.height.toFloat())
                val visible = (visibleBottom - visibleTop).coerceAtLeast(0f)
                onVisibilityChange(visible / h)
            }
            .semantics(mergeDescendants = true) {}
    ) {
        Row(Modifier.fillMaxWidth()) {
            if (card.type == CardType.NEW) {
                NewAccentStripe(Modifier.padding(start = 8.dp))
            }
            Column(
                modifier = Modifier
                    .padding(Space.md)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = card.icon, fontSize = 26.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (card.type == CardType.NEW) {
                        NewPill()
                    }
                }
                Text(
                    text = card.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(
                    onClick = onAction,
                    shape = LauncherShapes.small,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        LauncherPalette.AccentBlue.copy(alpha = 0.6f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LauncherPalette.AccentBlue
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .wrapContentWidth()
                        .semantics { contentDescription = card.actionLabel }
                ) {
                    Text(
                        text = card.actionLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 4.dp, top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_dismiss_card),
                    tint = LauncherPalette.TextMuted
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E, widthDp = 360, heightDp = 220)
@Composable
private fun DiscoverCardPreview() {
    LauncherTheme {
        Box(Modifier.padding(16.dp)) {
            DiscoverCard(
                card = Card(
                    id = "work_04", intent = Intent.WORK, type = CardType.NEW,
                    icon = "📋", title = "试试 Linear 建单",
                    description = "轻量级项目管理，给你看看",
                    actionLabel = "了解一下",
                    whyLabel = "ε-greedy 探索 · 项目管理类",
                    tags = listOf("productivity", "pm", "work", "explore")
                ),
                onAction = {}, onDismiss = {}
            )
        }
    }
}
