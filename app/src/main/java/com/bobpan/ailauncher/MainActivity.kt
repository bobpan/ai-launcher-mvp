package com.bobpan.ailauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.bobpan.ailauncher.data.db.dao.UserProfileDao
import com.bobpan.ailauncher.ui.home.HomeViewModel
import com.bobpan.ailauncher.ui.nav.LauncherNavHost
import com.bobpan.ailauncher.ui.nav.Routes
import com.bobpan.ailauncher.ui.theme.LauncherTheme
import com.bobpan.ailauncher.util.DebugActions
import com.bobpan.ailauncher.util.DefaultLauncherCheck
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    @Inject lateinit var debugActions: DebugActions
    @Inject lateinit var profileDao: UserProfileDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NFR-11 / Decision #19 — edge-to-edge, light-content icons.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContent {
            LauncherTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                LauncherNavHost(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    snackbarHostState = snackbarHostState,
                    onSetDefaultLauncher = { openHomeSettings() },
                    debugActions = debugActions,
                    profileDao = profileDao
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // FR-17: show banner only when we are NOT the default home.
        homeViewModel.setBannerVisibility(!DefaultLauncherCheck.isDefault(this))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // FR-14 canonical state on Home button while foreground.
        homeViewModel.onHomeButtonPressed()
    }

    private fun openHomeSettings() {
        val home = Intent(Settings.ACTION_HOME_SETTINGS)
        val fallback = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        runCatching { startActivity(home) }
            .recoverCatching { startActivity(fallback) }
    }
}
