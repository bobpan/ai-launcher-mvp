package com.bobpan.ailauncher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobpan.ailauncher.ui.components.AiDock
import com.bobpan.ailauncher.ui.components.DefaultLauncherBanner
import com.bobpan.ailauncher.ui.components.DiscoverCard
import com.bobpan.ailauncher.ui.components.EmptyIntentState
import com.bobpan.ailauncher.ui.components.HeroForYouCard
import com.bobpan.ailauncher.ui.components.IntentChipStrip
import com.bobpan.ailauncher.ui.theme.ambientBlobs
import com.bobpan.ailauncher.ui.theme.homeGradient
import com.bobpan.ailauncher.util.Clock
import com.bobpan.ailauncher.util.DwellController

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDrawer: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    snackbarHostState: SnackbarHostState,
    clock: Clock = Clock.System,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetOpen by viewModel.profileSheetOpen.collectAsStateWithLifecycle()
    val devSheetOpen by viewModel.devSheetOpen.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()

    var heroPulseToken by remember { mutableIntStateOf(0) }

    // --- Event collection: snackbars + hero pulses + scroll-to-top ---
    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(ev.message)
                UiEvent.HeroPulse       -> heroPulseToken += 1
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.scrollToTopTrigger.collect {
            lazyListState.animateScrollToItem(0)
        }
    }

    // --- Dwell controller wiring (Locked Decision #10) ---
    val dwell = remember(viewModel) {
        DwellController(
            clock = clock,
            thresholdMs = 2_000L,
            onDwell = { cardId, durationMs ->
                val content = state as? HomeUiState.Content
                val card = content?.discoverCards?.firstOrNull { it.id == cardId }
                    ?: content?.heroCard?.takeIf { it.id == cardId }
                card?.let { viewModel.trackDwell(it, durationMs) }
            }
        )
    }
    val content = state as? HomeUiState.Content
    // Pause dwell while sheet/dev-sheet open.
    LaunchedEffect(sheetOpen, devSheetOpen) {
        dwell.setPaused(sheetOpen || devSheetOpen)
    }
    // Pause dwell on lifecycle pause.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> dwell.setPaused(true)
                Lifecycle.Event.ON_RESUME -> dwell.setPaused(sheetOpen || devSheetOpen)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Intent change ends all appearances.
    LaunchedEffect(content?.selectedIntent) { dwell.endAll() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .homeGradient()
    ) {
        // Decorative blob layer drawn below the list (same Box, behind).
        Box(Modifier.fillMaxSize().ambientBlobs())

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("header") {
                content?.let {
                    HeaderRow(
                        state = it,
                        onOpenProfile = { viewModel.openProfileSheet() },
                        onLongPressProfile = { viewModel.openDevSheet() }
                    )
                }
            }

            if (content?.showDefaultBanner == true) {
                item("banner") {
                    DefaultLauncherBanner(
                        onSetDefault = onSetDefaultLauncher,
                        onDismiss    = { viewModel.dismissBanner() }
                    )
                }
            }

            item("hero") {
                content?.let { c ->
                    val hero = c.heroCard
                    if (c.allDismissed || hero == null) {
                        EmptyIntentState(onRestore = { viewModel.restoreDismissedForActiveIntent() })
                    } else {
                        val fraction = if (c.maxScore > 0f) {
                            (c.scores[hero.id] ?: 0f) / c.maxScore
                        } else 0.08f
                        HeroForYouCard(
                            card = hero,
                            onThumbUp    = { viewModel.submitFeedback(hero, com.bobpan.ailauncher.data.model.Signal.Like(hero.id, clock.nowMs())) },
                            onThumbDown  = { viewModel.submitFeedback(hero, com.bobpan.ailauncher.data.model.Signal.Dislike(hero.id, clock.nowMs())) },
                            onAction     = { viewModel.onCardAction(hero) },
                            fraction     = fraction,
                            pulseToken   = heroPulseToken,
                            storageHealthy = c.storageHealthy
                        )
                    }
                }
            }

            item("chips") {
                content?.let {
                    IntentChipStrip(
                        selected = it.selectedIntent,
                        onSelect = { intent -> viewModel.selectIntent(intent) }
                    )
                }
            }

            if (content != null && !content.allDismissed) {
                items(content.discoverCards, key = { it.id }) { card ->
                    DiscoverCard(
                        card = card,
                        onAction = { viewModel.onCardAction(card) },
                        onDismiss = { viewModel.dismissCard(card) },
                        onVisibilityChange = { f -> dwell.onVisibilityChange(card.id, f) }
                    )
                }
            }
        }

        AiDock(
            onOpenDrawer = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        )
    }
}
