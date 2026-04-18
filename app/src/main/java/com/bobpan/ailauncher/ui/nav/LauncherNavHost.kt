package com.bobpan.ailauncher.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bobpan.ailauncher.BuildConfig
import com.bobpan.ailauncher.data.db.dao.UserProfileDao
import com.bobpan.ailauncher.ui.drawer.AppDrawerScreen
import com.bobpan.ailauncher.ui.drawer.AppDrawerViewModel
import com.bobpan.ailauncher.ui.home.HomeScreen
import com.bobpan.ailauncher.ui.home.HomeViewModel
import com.bobpan.ailauncher.ui.profile.DevPanelSheet
import com.bobpan.ailauncher.ui.profile.ProfileDebugSheet
import com.bobpan.ailauncher.ui.profile.ProfileDebugViewModel
import com.bobpan.ailauncher.util.DebugActions
import kotlinx.coroutines.launch

object Routes {
    const val HOME   = "home"
    const val DRAWER = "drawer"
}

@Composable
fun LauncherNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    onSetDefaultLauncher: () -> Unit,
    debugActions: DebugActions,
    profileDao: UserProfileDao,
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
            val devOpen   by homeViewModel.devSheetOpen.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

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

            if (BuildConfig.DEBUG && devOpen) {
                DevPanelSheet(
                    onSeedDemo = {
                        scope.launch { debugActions.seedDemoProfile() }
                    },
                    onDumpProfile = {
                        scope.launch { debugActions.dumpProfileToLogcat(profileDao) }
                    },
                    onClearDismiss = { homeViewModel.restoreDismissedForActiveIntent() },
                    onDismiss = { homeViewModel.closeDevSheet() }
                )
            }
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
