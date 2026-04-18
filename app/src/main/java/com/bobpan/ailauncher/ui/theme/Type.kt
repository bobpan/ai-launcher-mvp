package com.bobpan.ailauncher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LauncherTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp, color = LauncherPalette.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp, color = LauncherPalette.TextPrimary
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
        color = LauncherPalette.TextPrimary
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium,
        color = LauncherPalette.TextPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp, color = LauncherPalette.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal,
        color = LauncherPalette.TextSecondary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
        color = LauncherPalette.TextSecondary
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp, color = LauncherPalette.TextPrimary
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp, color = LauncherPalette.TextMuted
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp, color = LauncherPalette.TextMuted
    )
)
