package com.bobpan.ailauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherTheme
import com.bobpan.ailauncher.ui.theme.Motion

/**
 * Thin gradient bar conveying Hero confidence. DESIGN §5.5.
 * `fraction` is clamped to [0.08, 1]. Value-agnostic animation over [Motion.heroCrossfade].
 */
@Composable
fun ConfidenceBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val clamped = fraction.coerceIn(0.08f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = Motion.DUR_MEDIUM, easing = Motion.Standard),
        label = "confidence_fraction"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LauncherPalette.GlassFillSoft)
            .clearAndSetSemantics {}
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction = animated)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(LauncherPalette.AccentBlue, LauncherPalette.AccentPink)
                    )
                )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1E, widthDp = 280)
@Composable
private fun ConfidenceBarPreview() {
    LauncherTheme {
        ConfidenceBar(fraction = 0.72f)
    }
}
