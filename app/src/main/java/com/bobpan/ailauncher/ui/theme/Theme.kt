package com.bobpan.ailauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Extension tokens not expressible via M3's ColorScheme/Typography/Shapes. */
object LauncherTokens {
    val glass: GlassTokens = GlassTokens()
}

val LocalLauncherTokens = staticCompositionLocalOf { LauncherTokens }

/** Convenience accessor: `LauncherTheme.glass` from any composable. */
object LauncherTheme {
    val glass: GlassTokens
        @Composable get() = LocalLauncherTokens.current.glass
}

@Composable
fun LauncherTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLauncherTokens provides LauncherTokens) {
        MaterialTheme(
            colorScheme = LauncherColorScheme,
            typography  = LauncherTypography,
            shapes      = LauncherShapes,
            content     = content
        )
    }
}
