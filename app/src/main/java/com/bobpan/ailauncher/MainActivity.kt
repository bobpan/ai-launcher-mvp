package com.bobpan.ailauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.bobpan.ailauncher.ui.home.HomeViewModel
import com.bobpan.ailauncher.ui.nav.LauncherNavHost
import com.bobpan.ailauncher.ui.theme.LauncherTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity Compose root.
 *
 * Minimal pattern (mirrors reference launcher's proven layout):
 *  - @AndroidEntryPoint only — NO class-level @Inject fields (they were triggering
 *    a crash before CrashLogger could initialize).
 *  - HomeViewModel is obtained inside setContent via hiltViewModel(), not at class
 *    construction time.
 *  - DevPanelSheet + ProfileDebugSheet internals get their own hiltViewModel()s,
 *    so no DebugActions / UserProfileDao dependencies need to flow through here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        com.bobpan.ailauncher.util.CrashLogger.crumb("MainActivity.onCreate begin")
        super.onCreate(savedInstanceState)
        com.bobpan.ailauncher.util.CrashLogger.crumb("MainActivity post-super")

        // NFR-11 / Decision #19 — edge-to-edge, light-content icons.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        com.bobpan.ailauncher.util.CrashLogger.crumb("MainActivity window config done")

        setContent {
            com.bobpan.ailauncher.util.CrashLogger.crumb("MainActivity setContent composing")
            LauncherTheme {
                val homeViewModel: HomeViewModel = hiltViewModel()
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                LauncherNavHost(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    snackbarHostState = snackbarHostState,
                    onSetDefaultLauncher = { openHomeSettings() }
                )
            }
        }
        com.bobpan.ailauncher.util.CrashLogger.crumb("MainActivity.onCreate end")
    }

    // NOTE: onResume/onNewIntent behaviors (default-launcher banner refresh + home-button
    // scroll-to-top) are deferred for v0.1. They relied on a class-level HomeViewModel
    // reference; re-add once we introduce a shared state holder. Not critical for MVP.

    private fun openHomeSettings() {
        val home = Intent(Settings.ACTION_HOME_SETTINGS)
        val fallback = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        runCatching { startActivity(home) }
            .recoverCatching { startActivity(fallback) }
    }
}
