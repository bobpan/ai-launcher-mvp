package com.bobpan.ailauncher.ui.drawer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobpan.ailauncher.data.model.AppInfo
import com.bobpan.ailauncher.data.repo.PackageAppsRepository
import com.bobpan.ailauncher.util.AppDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DrawerUiState {
    data object Loading : DrawerUiState
    @Immutable data class Content(val apps: List<AppInfo>) : DrawerUiState
    data object Empty : DrawerUiState
    data class Error(val message: String) : DrawerUiState
}

@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    private val packageApps: PackageAppsRepository,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrawerUiState>(DrawerUiState.Loading)
    val uiState: StateFlow<DrawerUiState> = _uiState.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch(dispatchers.io) {
            _uiState.value = DrawerUiState.Loading
            _uiState.value = runCatching { packageApps.launchableApps() }
                .map { apps ->
                    if (apps.isEmpty()) DrawerUiState.Empty else DrawerUiState.Content(apps)
                }
                .getOrElse { t -> DrawerUiState.Error(t.message ?: "unknown") }
        }
    }

    suspend fun launchIntentFor(pkg: String) = packageApps.launchIntentFor(pkg)

    suspend fun iconFor(pkg: String) = packageApps.loadIcon(pkg)
}
