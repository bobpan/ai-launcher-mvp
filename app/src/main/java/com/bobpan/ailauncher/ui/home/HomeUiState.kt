package com.bobpan.ailauncher.ui.home

import androidx.compose.runtime.Immutable
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.Intent

sealed interface HomeUiState {
    data object Loading : HomeUiState

    @Immutable
    data class Content(
        val selectedIntent:    Intent,
        val heroCard:          Card?,
        val discoverCards:     List<Card>,
        val scores:            Map<String, Float>,
        val maxScore:          Float,
        val showDefaultBanner: Boolean,
        val storageHealthy:    Boolean,
        val allDismissed:      Boolean,
        val feedbackCounter:   Int
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

/** One-shot UI events (Snackbars, Hero pulse). Locked Decision #2. */
sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
    data object HeroPulse : UiEvent
}
