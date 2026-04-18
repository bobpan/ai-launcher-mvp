package com.bobpan.ailauncher.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object LauncherPalette {
    // Background
    val BgDeep           = Color(0xFF0A0F1E)
    val BgElevated       = Color(0xFF161A2E)

    // Accents
    val AccentBlue       = Color(0xFF7CB4FF)
    val AccentBlueDim    = Color(0xCC7CB4FF)
    val AccentPink       = Color(0xFFFF7EC8)
    val AccentPinkDim    = Color(0xCCFF7EC8)
    val PositiveGreen    = Color(0xFF64E6A0)
    val DangerRed        = Color(0xFFFF6B7A)

    // Neutrals
    val TextPrimary      = Color(0xFFF5F7FF)
    val TextSecondary    = Color(0xB3F5F7FF)
    val TextMuted        = Color(0x80F5F7FF)
    val TextDisabled     = Color(0x52F5F7FF)

    // Glass
    val GlassFillSoft    = Color(0x0DFFFFFF)
    val GlassFillStrong  = Color(0x14FFFFFF)
    val GlassOutline     = Color(0x29FFFFFF)
    val GlassOutlineSoft = Color(0x14FFFFFF)

    // Ambient blobs
    val BlobBlue         = Color(0x667CB4FF)
    val BlobPink         = Color(0x4DFF7EC8)
}

val LauncherColorScheme = darkColorScheme(
    primary               = LauncherPalette.AccentBlue,
    onPrimary             = LauncherPalette.BgDeep,
    primaryContainer      = Color(0xFF1E2C4B),
    onPrimaryContainer    = LauncherPalette.TextPrimary,

    secondary             = LauncherPalette.AccentPink,
    onSecondary           = LauncherPalette.BgDeep,
    secondaryContainer    = Color(0xFF3A1E33),
    onSecondaryContainer  = LauncherPalette.TextPrimary,

    tertiary              = LauncherPalette.PositiveGreen,
    onTertiary            = LauncherPalette.BgDeep,

    error                 = LauncherPalette.DangerRed,
    onError               = LauncherPalette.TextPrimary,

    background            = LauncherPalette.BgDeep,
    onBackground          = LauncherPalette.TextPrimary,

    surface               = LauncherPalette.BgElevated,
    onSurface             = LauncherPalette.TextPrimary,
    surfaceVariant        = Color(0xFF1F2440),
    onSurfaceVariant      = LauncherPalette.TextSecondary,

    outline               = LauncherPalette.GlassOutline,
    outlineVariant        = LauncherPalette.GlassOutlineSoft,

    scrim                 = Color(0xB80A0F1E)
)
