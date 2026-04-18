package com.bobpan.ailauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassTokens(
    val surfaceSoft:     Color = LauncherPalette.GlassFillSoft,
    val surfaceElevated: Color = LauncherPalette.GlassFillStrong,
    val outline:         Color = LauncherPalette.GlassOutline,
    val outlineSoft:     Color = LauncherPalette.GlassOutlineSoft,
    val blurRadius:      Dp    = 24.dp,
    val ambientBlobBlur: Dp    = 55.dp
)

/**
 * Glassmorphic card surface (DESIGN §4.5). No real backdrop blur — overlay alpha + gradient border.
 */
fun Modifier.glassSurface(
    shape: Shape,
    elevated: Boolean = false,
    accent: Color? = null
): Modifier = this
    .clip(shape)
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                if (elevated) LauncherPalette.GlassFillStrong else LauncherPalette.GlassFillSoft,
                LauncherPalette.GlassFillSoft.copy(alpha = 0.02f)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                accent?.copy(alpha = 0.45f) ?: LauncherPalette.GlassOutline,
                LauncherPalette.GlassOutlineSoft
            ),
            start = Offset.Zero,
            end   = Offset.Infinite
        ),
        shape = shape
    )

/** Background gradient used on HomeScreen / AppDrawerScreen (DESIGN §1.3 L0). */
fun Modifier.homeGradient(): Modifier = this.background(
    Brush.verticalGradient(
        colors = listOf(LauncherPalette.BgDeep, LauncherPalette.BgElevated)
    )
)

/** Ambient radial blobs (DESIGN §4.5). */
fun Modifier.ambientBlobs(): Modifier = this
    .drawBehind {
        drawCircle(
            color = LauncherPalette.BlobBlue,
            radius = 220.dp.toPx(),
            center = Offset(x = size.width * 0.15f, y = size.height * 0.12f),
            blendMode = BlendMode.Plus
        )
        drawCircle(
            color = LauncherPalette.BlobPink,
            radius = 180.dp.toPx(),
            center = Offset(x = size.width * 0.85f, y = size.height * 0.55f),
            blendMode = BlendMode.Plus
        )
    }
    .then(
        if (Build.VERSION.SDK_INT >= 31) {
            Modifier.graphicsLayer {
                renderEffect = BlurEffect(55f, 55f, TileMode.Clamp)
            }
        } else Modifier
    )
