package com.bobpan.ailauncher.ui.home

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.data.model.Signal
import com.bobpan.ailauncher.data.model.SignalType
import com.bobpan.ailauncher.data.model.UserProfile
import com.bobpan.ailauncher.data.repo.CardRepository
import com.bobpan.ailauncher.data.repo.FeedbackRepository
import com.bobpan.ailauncher.data.repo.ProfileRepository
import com.bobpan.ailauncher.domain.recommend.RecommendationEngine
import com.bobpan.ailauncher.util.AppDispatchers
import com.bobpan.ailauncher.util.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cardRepository:     CardRepository,
    private val feedbackRepository: FeedbackRepository,
    private val profileRepository:  ProfileRepository,
    private val engine:             RecommendationEngine,
    private val clock:              Clock,
    private val dispatchers:        AppDispatchers,
    @Suppress("UNUSED_PARAMETER") savedState: SavedStateHandle
) : ViewModel() {

    private val _selectedIntent    = MutableStateFlow(Intent.WORK)
    private val _dismissedByIntent = MutableStateFlow<Map<Intent, Set<String>>>(emptyMap())
    private val _bannerDismissed   = MutableStateFlow(false)
    private val _showDefaultBanner = MutableStateFlow(false)
    val profileSheetOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val devSheetOpen:     MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _scrollToTop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopTrigger: SharedFlow<Unit> = _scrollToTop.asSharedFlow()

    // Debounce state (FR-02 400ms, Locked Decision #3).
    private val lastSubmit: MutableMap<Pair<String, SignalType>, Long> = mutableMapOf()

    // Trace for HERO_UNCHANGED_AFTER_DOWNVOTE logging + pulse.
    private var lastHeroId: String? = null
    @Volatile private var lastDislikeCardId: String? = null

    // FR-04 rapid-switch suppression window.
    @Volatile private var lastIntentSwitchMs: Long = 0L

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedIntent,
        _dismissedByIntent,
        cardRepository.observeAll(),
        profileRepository.observeProfile(),
        feedbackRepository.observeCount(),
        feedbackRepository.isHealthy,
        _showDefaultBanner,
        _bannerDismissed
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val intent     = values[0] as Intent
        @Suppress("UNCHECKED_CAST")
        val dismissMap = values[1] as Map<Intent, Set<String>>
        @Suppress("UNCHECKED_CAST")
        val catalog    = values[2] as List<Card>
        val profile    = values[3] as UserProfile
        val count      = values[4] as Int
        val healthy    = values[5] as Boolean
        val show       = values[6] as Boolean
        val bannerDn   = values[7] as Boolean

        val dismissed = dismissMap[intent].orEmpty()
        val result    = engine.recommend(intent, profile, catalog, dismissed, count = 4)

        val hero = result.ordered.firstOrNull()
        val prevHeroId = lastHeroId
        val prevDislikeTarget = lastDislikeCardId
        lastHeroId = hero?.id

        // Detect hero-unchanged after a 👎 and notify UI (Decision #4).
        if (
            prevDislikeTarget != null &&
            prevHeroId == prevDislikeTarget &&
            hero?.id == prevHeroId
        ) {
            Log.w("FLYWHEEL", "HERO_UNCHANGED_AFTER_DOWNVOTE cardId=$prevHeroId")
            _events.tryEmit(UiEvent.HeroPulse)
        }
        // Clear the trace on each emission so it's single-shot.
        lastDislikeCardId = null

        val hasAnyForIntent = catalog.any { it.intent == intent }
        HomeUiState.Content(
            selectedIntent    = intent,
            heroCard          = hero,
            discoverCards     = result.ordered.drop(1),
            scores            = result.scores,
            maxScore          = result.maxScore,
            showDefaultBanner = show && !bannerDn,
            storageHealthy    = healthy,
            allDismissed      = hasAnyForIntent && dismissed.size >= 4,
            feedbackCounter   = count
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState.Loading
    )

    fun selectIntent(intent: Intent) {
        if (_selectedIntent.value == intent) return
        lastIntentSwitchMs = clock.nowMs()
        _dismissedByIntent.update { it - intent }
        _selectedIntent.value = intent
    }

    fun submitFeedback(card: Card, signal: Signal) {
        val type = signal.persistedType
        // FR-02 debounce applies to thumbs only.
        if (type == SignalType.THUMB_UP || type == SignalType.THUMB_DOWN) {
            val key = card.id to type
            val now = clock.nowMs()
            val last = lastSubmit[key] ?: 0L
            if (now - last < DEBOUNCE_MS) return
            lastSubmit[key] = now
        }
        if (signal is Signal.Dislike) lastDislikeCardId = card.id
        viewModelScope.launch(dispatchers.io) {
            feedbackRepository.record(signal, card)
            if (!feedbackRepository.isHealthy.value) {
                _events.emit(UiEvent.ShowSnackbar("存储暂时不可用，反馈未保存"))
            }
        }
    }

    fun dismissCard(card: Card) {
        val now = clock.nowMs()
        submitFeedback(card, Signal.Dismiss(card.id, now))
        _dismissedByIntent.update { map ->
            val current = map[card.intent].orEmpty()
            map + (card.intent to (current + card.id))
        }
    }

    fun trackDwell(card: Card, durationMs: Long) {
        if (durationMs < DWELL_MIN_MS) return
        val now = clock.nowMs()
        // Second safeguard against rapid-switch (Decision #9).
        if (now - lastIntentSwitchMs < SUPPRESS_WINDOW_MS) return
        submitFeedback(card, Signal.Dwell(card.id, now, durationMs))
    }

    fun onCardAction(card: Card) {
        submitFeedback(card, Signal.ActionTapped(card.id, clock.nowMs()))
        viewModelScope.launch {
            _events.emit(UiEvent.ShowSnackbar("${card.actionLabel} — 即将上线"))
        }
    }

    fun restoreDismissedForActiveIntent() {
        _dismissedByIntent.update { it - _selectedIntent.value }
    }

    fun setBannerVisibility(show: Boolean) { _showDefaultBanner.value = show }
    fun dismissBanner() { _bannerDismissed.value = true }

    fun onHomeButtonPressed() {
        profileSheetOpen.value = false
        devSheetOpen.value = false
        _scrollToTop.tryEmit(Unit)
    }

    fun openProfileSheet()  { profileSheetOpen.value = true }
    fun closeProfileSheet() { profileSheetOpen.value = false }
    fun openDevSheet()      { devSheetOpen.value = true }
    fun closeDevSheet()     { devSheetOpen.value = false }

    /** FR-12 reset (called after ProfileDebugViewModel.clearAll succeeds). */
    fun refresh() {
        _selectedIntent.value      = Intent.WORK
        _dismissedByIntent.value   = emptyMap()
        profileSheetOpen.value     = false
        devSheetOpen.value         = false
        lastSubmit.clear()
        lastHeroId                 = null
        lastDislikeCardId          = null
        lastIntentSwitchMs         = clock.nowMs()
        _scrollToTop.tryEmit(Unit)
    }

    fun isDwellSuppressed(appearanceStartMs: Long): Boolean =
        appearanceStartMs - lastIntentSwitchMs < SUPPRESS_WINDOW_MS

    private companion object {
        const val DEBOUNCE_MS        = 400L
        const val DWELL_MIN_MS       = 2_000L
        const val SUPPRESS_WINDOW_MS = 500L
    }
}
