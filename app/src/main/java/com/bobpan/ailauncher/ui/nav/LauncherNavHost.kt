package com.bobpan.ailauncher.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bobpan.ailauncher.ui.drawer.AppDrawerScreen
import com.bobpan.ailauncher.ui.drawer.AppDrawerViewModel
import com.bobpan.ailauncher.ui.home.HomeScreen
import com.bobpan.ailauncher.ui.home.HomeViewModel
import com.bobpan.ailauncher.ui.profile.ProfileDebugSheet
import com.bobpan.ailauncher.ui.profile.ProfileDebugViewModel

object Routes {
    const val HOME   = "home"
    const val DRAWER = "drawer"
}

/**
 * Root NavHost.
 *
 * For v0.1 MVP:
 *  - ProfileDebugSheet uses its own hiltViewModel() — no profileDao param needed.
 *  - DevPanelSheet is stubbed out (FR-20 debug-only, non-blocking); re-enable once
 *    we have a clean way to wire DebugActions in without class-level @Inject.
 */
@Composable
fun LauncherNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    onSetDefaultLauncher: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            val profileVm: ProfileDebugViewModel = hiltViewModel()
            val sheetOpen by homeViewModel.profileSheetOpen.collectAsStateWithLifecycle()

            HomeScreen(
                viewModel = homeViewModel,
                onOpenDrawer = { navController.navigate(Routes.DRAWER) },
                onSetDefaultLauncher = onSetDefaultLauncher,
                snackbarHostState = snackbarHostState
            )

            if (sheetOpen) {
                val profileState by profileVm.uiState.collectAsStateWithLifecycle()
                ProfileDebugSheet(
                    state = profileState,
                    onReset = {
                        profileVm.resetProfile { homeViewModel.refresh() }
                    },
                    onDismiss = { homeViewModel.closeProfileSheet() }
                )
            }

            // DevPanelSheet (FR-20 debug sheet) — stubbed for v0.1. Re-enable after
            // we introduce a sub-VM that exposes DebugActions + ProfileDao cleanly.
        }
        composable(Routes.DRAWER) {
            val drawerVm: AppDrawerViewModel = hiltViewModel()
            AppDrawerScreen(
                viewModel = drawerVm,
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) }
            )
        }
    }
}
