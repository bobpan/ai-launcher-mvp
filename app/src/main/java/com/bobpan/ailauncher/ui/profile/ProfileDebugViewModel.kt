package com.bobpan.ailauncher.ui.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobpan.ailauncher.data.db.entity.UserProfileEntry
import com.bobpan.ailauncher.data.repo.FeedbackRepository
import com.bobpan.ailauncher.data.repo.ProfileRepository
import com.bobpan.ailauncher.util.AppDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ProfileSheetState {
    data object Loading : ProfileSheetState

    @Immutable
    data class Loaded(
        val topTags: List<UserProfileEntry>,
        val counter: Int
    ) : ProfileSheetState

    data object Empty : ProfileSheetState
}

@HiltViewModel
class ProfileDebugViewModel @Inject constructor(
    private val profileRepository:  ProfileRepository,
    private val feedbackRepository: FeedbackRepository,
    private val dispatchers:        AppDispatchers
) : ViewModel() {

    val uiState: StateFlow<ProfileSheetState> = combine(
        profileRepository.observeTopTags(10),
        feedbackRepository.observeCount()
    ) { tags, count ->
        if (count == 0 && tags.isEmpty()) ProfileSheetState.Empty
        else ProfileSheetState.Loaded(tags, count)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileSheetState.Loading
    )

    fun resetProfile(onDone: () -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            feedbackRepository.clearAll()
            withContext(dispatchers.main) { onDone() }
        }
    }
}
