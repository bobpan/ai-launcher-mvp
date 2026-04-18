package com.bobpan.ailauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.Space
import kotlin.math.abs

/**
 * One tag row in the profile sheet. Bar is left-anchored for positive weights, right-anchored for negative.
 */
@Composable
fun ProfileDebugRow(
    tag: String,
    weight: Float,
    normalizedFraction: Float,
    modifier: Modifier = Modifier
) {
    val color: Color = if (weight >= 0f) LauncherPalette.AccentBlue else LauncherPalette.AccentPink
    val cd = "$tag，权重 ${"%+.2f".format(weight)}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.width(96.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(LauncherPalette.GlassFillSoft)
                .padding(horizontal = 0.dp)
        ) {
            // Center zero tick.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(LauncherPalette.GlassOutlineSoft)
            )
            // Fill — left-anchored positive, right-anchored negative.
            val fraction = abs(normalizedFraction).coerceIn(0f, 1f) * 0.5f
            Box(
                modifier = Modifier
                    .align(if (weight >= 0f) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Text(
            text = "%+.2f".format(weight),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(56.dp)
                .padding(start = Space.sm)
        )
    }
}
