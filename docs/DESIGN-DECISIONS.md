# DESIGN-DECISIONS — PM Arbitration for Phase 3

**Status:** Locked for Phase 3 dev · **Owner:** PM · **Date:** 2026-04-18
**Inputs reviewed:** `AGENTS.md`, `docs/PRD-v2.md`, `docs/DESIGN-v1.md`, `docs/ARCHITECTURE-v1.md`
**Purpose:** Single consolidated arbitration document. The Phase 3 dev agent should consult this file **before** PRD/DESIGN/ARCH when the three conflict, because this file states which one wins on which axis and logs every micro-decision resolving a cross-doc gap.

**Arbitration rule (reiterated, locked):**
- **PRD-v2 wins on behavior.**
- **DESIGN-v1 wins on visual / interaction.**
- **ARCHITECTURE-v1 wins on technical wiring.**
- **This file wins on conflicts between any two of the above.**

---

## Part 1 — Design → Architecture cross-check

Going through DESIGN-v1 §5's eight components and verifying the architecture supplies every piece of state/data each one needs.

### 1.1 HeroForYouCard (DESIGN §5.1)

Needs: `card: Card`, `onThumbUp/Down/Action` callbacks, `HeroState` (Loaded / Loading / Pulsing), ConfidenceBar `fraction`.

Architecture provides:
- `Card` (§5.3) ✅
- `HomeUiState.Content.heroCard` (§8.1) ✅
- `HomeUiState.Content.scores` + `maxScore` → ConfidenceBar fraction ✅
- `HomeViewModel.submitFeedback(card, signal)` ✅

**D→A conflict 1 · No `HeroState` in `HomeUiState`.** DESIGN defines `Loaded / Loading / Pulsing` but ARCH's `HomeUiState` has only top-level `Loading / Content / Error` and Content has no `heroPulsing: Boolean`. The "Pulsing" state (the 600ms post-tap confirmation pulse when Hero didn't change after 👍) has no state field.

**D→A conflict 2 · No "hero unchanged after feedback" signal.** PRD FR-02 says a pulse confirmation plays when top-ranked card does not change after 👍. `HomeViewModel.uiState` is a pure `combine` — it cannot tell the UI "profile changed but hero didn't" without an extra one-shot event.

### 1.2 IntentChip / IntentChipStrip (DESIGN §5.2)

Needs: `selected: Intent`, `onSelect: (Intent) -> Unit`.

Architecture provides:
- `HomeUiState.Content.selectedIntent` ✅
- `HomeViewModel.selectIntent(intent)` ✅

No gap.

### 1.3 DiscoverCard (DESIGN §5.3)

Needs: `card: Card`, `onAction`, `onDismiss`, `onVisibilityChange(fraction)`.

Architecture provides:
- `HomeUiState.Content.discoverCards: List<Card>` ✅
- `HomeViewModel.dismissCard(card)` ✅
- `HomeViewModel.trackDwell(card, durationMs)` ✅
- `util/Dwell.kt` helper (§2 tree, Appendix #8) for accrual

**D→A conflict 3 · Action-tap signal not surfaced on VM.** DESIGN says tapping the card action fires `FeedbackEvent(signal=ACTION_TAP, weight=+2)` (FR-08), but `HomeViewModel` only exposes `submitFeedback(card, signal)` and `dismissCard` / `trackDwell` convenience methods. There is no `onCardAction(card)` convenience method and the Snackbar host is owned by `HomeScreen`. Dev needs a single entry point that (a) persists the signal, (b) shows the `"${actionLabel} — 即将上线"` snackbar.

### 1.4 AiDock (DESIGN §5.4)

Needs: `onOpenDrawer: () -> Unit`. No data inputs.

Architecture: `LauncherNavHost` routes `drawer` on click. ✅

No gap.

### 1.5 ConfidenceBar (DESIGN §5.5)

Needs: `fraction: Float` (0..1, normalized).

Architecture provides:
- `HomeUiState.Content.scores` and `maxScore` (§8.1) — consumer computes `scores[hero.id] / maxScore`. ✅

No gap (but see Locked Decision #5 — normalization lives in the Compose layer, not VM).

### 1.6 FeedbackButtonRow (DESIGN §5.6)

Needs: `onThumbUp`, `onThumbDown`, transient `thumbUpActive/thumbDownActive` flags (600ms glow).

Architecture provides:
- `HomeViewModel.submitFeedback` ✅
- No `thumbUpActive / thumbDownActive` state — DESIGN §9.3 says this is computed via a `LaunchedEffect(pressCount) { delay(600); active = false }` **inside the composable**, not in the VM. This is a composable-local pattern — **not a gap**. ✅

### 1.7 AppIconTile (DESIGN §5.7)

Needs: `app: AppInfo`, `onClick`.

Architecture provides:
- `AppDrawerViewModel.uiState: StateFlow<DrawerUiState>` with `DrawerUiState.Content(apps)` ✅
- `AppInfo` domain model (§5.7) — **but `AppInfo.iconKey: String`** (not `Drawable`).

**D→A conflict 4 · Icon rendering pipeline.** DESIGN §5.7 says `AppIconTile` takes `AppInfo.icon: Drawable → ImageBitmap` via `rememberAsyncImagePainter` (Coil-style). ARCH's `AppInfo.iconKey: String` and `PackageAppsRepository.loadIcon(packageName): Drawable?` are separate. There is no Coil dep in Gradle (§3.4). The composable has to synchronously request a Drawable per tile and keep it out of recomposition.

### 1.8 ProfileDebugRow (DESIGN §5.8)

Needs: `tag: String`, `weight: Float`, `normalizedFraction: Float`.

Architecture provides:
- `ProfileSheetState.Loaded.topTags: List<UserProfileEntry>` (has `tag` + `weight`) ✅
- `normalizedFraction` is not in the state — the composable must compute `|weight| / maxAbsWeight` over the list.

**D→A conflict 5 (minor) · `normalizedFraction` not exposed.** Same character as 1.5: caller-computed in Compose. Not actually a gap once documented — see Locked Decision #5.

### 1.9 Additional DESIGN components the architecture must also support

DESIGN also mandates components not in §5 but referenced in §1.3 / §3 / §7:

- **DefaultLauncherBanner** → needs `showDefaultBanner`, `onSetDefault`, `onDismissBanner`. ARCH has `showDefaultBanner` + `dismissBanner()` ✅ but **lacks `onSetDefault`** — the intent firing logic lives in `MainActivity` per FR-17, not in VM. DESIGN §3.1.1 shows `onSetDefault` as a lambda passed to `HomeScreen`. **Not a gap** — `MainActivity` wires it.
- **EmptyIntentState** ("已看完本模式推荐") → needs `onRestore`. ARCH has `restoreDismissedForActiveIntent()` ✅.
- **ResetProfileDialog** → shown on tap of 重置画像. ARCH has `ProfileDebugViewModel.resetProfile(onDone)` ✅.
- **Snackbar host** → ARCH doesn't define a `SnackbarHostState` owner. DESIGN §3.1.1 shows `snackbarHostState` passed in. **D→A conflict 6:** no `Snackbar` channel between VM and UI. Feedback actions happen on VM side but Snackbars must show on UI side.

### Summary of D→A conflicts

| # | Conflict | Severity |
|---|---|---|
| 1 | `HeroState` (Loaded/Loading/Pulsing) not in `HomeUiState` | Minor |
| 2 | No "hero unchanged after feedback" one-shot event channel | Medium |
| 3 | No `onCardAction(card)` convenience method on `HomeViewModel` | Minor |
| 4 | Icon rendering pipeline — `iconKey` vs `Drawable`, no Coil | Medium |
| 5 | `normalizedFraction` for ConfidenceBar/ProfileDebugRow not in state (compose-local compute) | Trivial |
| 6 | No Snackbar channel (`SharedFlow<UiEvent>`) between VM and UI | Medium |

---

## Part 2 — Architecture → Design cross-check

Going through ARCH §8's three ViewModels and verifying DESIGN has screens/components that consume every field each VM exposes.

### 2.1 `HomeUiState.Content` (ARCH §8.1) — 9 fields

| Field | Consumed by DESIGN? |
|---|---|
| `selectedIntent: Intent` | IntentChipStrip (§5.2), Header greeting (§3.1.4) ✅ |
| `heroCard: Card?` | HeroForYouCard (§5.1) ✅ |
| `discoverCards: List<Card>` | LazyColumn items (§3.1.1) ✅ |
| `scores: Map<String, Float>` | ConfidenceBar (§5.5) ✅ |
| `maxScore: Float` | ConfidenceBar normalization ✅ |
| `showDefaultBanner: Boolean` | DefaultLauncherBanner (§3.1.1, §7.7) ✅ |
| `storageHealthy: Boolean` | §7.2 subtle ⚠ glyph in header; Snackbar on feedback tap ✅ |
| `allDismissed: Boolean` | EmptyIntentState (§7.4) ✅ |
| `feedbackCounter: Int` | ProfileDebugSheet header ✅ |

**A→D conflict 1 (trivial) · `storageHealthy: false` ⚠ glyph position.** DESIGN §7.2 says "a subtle ⚠ glyph in the header near the profile badge". ARCH exposes `storageHealthy` on `HomeUiState` but the DESIGN header (§3.1.4) composition doesn't show a slot for the warning glyph — it has the greeting block on the left and the profile badge on the right, nothing between.

**A→D conflict 2 · `heroCard: Card?` nullable with no explicit DESIGN UI for the `null` case.** When `allDismissed == true`, DESIGN §7.4 renders an `EmptyIntentGlassCard` **replacing** the Hero slot. Good. But `heroCard` could also be `null` if the engine returns empty list for an intent with zero seeds (FR-19 row 5, "defensive"). DESIGN §7.5 says "Same UI as §7.4." ✅

### 2.2 `DrawerUiState` (ARCH §8.3)

| Variant | DESIGN home |
|---|---|
| `Loading` | §3.2 state matrix row "Loading" — 8 shimmer tiles ✅ |
| `Content(apps)` | §3.2 grid ✅ |
| `Empty` | §3.2 + §7.6 — "没有可启动的应用" glass card ✅ |
| `Error(message)` | §3.2 state matrix row "Error" — same card with `"应用列表暂时不可用"` ✅ |

No gap.

### 2.3 `ProfileSheetState` (ARCH §8.4)

| Variant | DESIGN home |
|---|---|
| `Loading` | §3.3 state matrix "Loading" — 10 shimmer rows ✅ |
| `Loaded(topTags, counter)` | §3.3 rows + counter ✅ |
| `Empty` | §3.3 / §7.3 — 🌱 empty state ✅ |

**A→D conflict 3 (minor) · No `Error` variant on `ProfileSheetState`.** If `feedbackRepository.isHealthy == false` and the sheet is opened, ARCH's `ProfileDebugViewModel` has no `Error` variant and the state will be `Empty` (count=0, tags=empty) or indefinitely `Loading`. DESIGN §7.2 says the sheet "shows empty state §7.3" when Room is unhealthy — fine, but this shortcut means we never distinguish "no data yet" from "storage broken" in the sheet.

### 2.4 `HomeViewModel.profileSheetOpen: MutableStateFlow<Boolean>`

Consumed by HomeScreen to show/hide `ProfileDebugSheet` (DESIGN §3.1.1, §2.5). ✅

### 2.5 `HomeViewModel` action methods

| Method | DESIGN trigger |
|---|---|
| `selectIntent(intent)` | Chip tap (§2.3, §5.2) ✅ |
| `submitFeedback(card, signal)` | 👍 / 👎 / action-tap (§2.2, §5.1, §5.6) ✅ |
| `dismissCard(card)` | ✕ tap (§2.4, §5.3) ✅ |
| `trackDwell(card, durationMs)` | Dwell helper (§5.3) ✅ |
| `restoreDismissedForActiveIntent()` | "恢复本模式" button (§7.4) ✅ |
| `setBannerVisibility(show)` | Called from `MainActivity.onResume` (FR-17) ✅ |
| `dismissBanner()` | Banner ✕ (§3.1.1, §7.7) ✅ |
| `onHomeButtonPressed()` | FR-14 from `MainActivity.onNewIntent` ✅ |
| `refresh()` | Profile reset hook ✅ |

No method without a DESIGN consumer. Good.

### Summary of A→D conflicts

| # | Conflict | Severity |
|---|---|---|
| 1 | `storageHealthy=false` ⚠ glyph has no slot in DESIGN header layout | Trivial |
| 2 | `heroCard: Card?` fully covered by `allDismissed` empty state (not really a gap) | None |
| 3 | `ProfileSheetState` has no `Error` variant → merges with `Empty` | Minor |

---

## Part 3 — PRD → (Design + Architecture) coverage matrix

Every FR in PRD-v2 mapped to its DESIGN section and ARCH class/file. Gaps flagged.

| FR | Design home | Architecture home | Gap? |
|---|---|---|---|
| **FR-01** Hero render | §3.1, §5.1 | `HeroForYouCard`, `HomeViewModel.uiState.heroCard` | — |
| **FR-02** Hero 👍/👎 + debounce + re-evaluate + crossfade/pulse | §2.2, §5.1, §5.6, §9.1, §9.3 | `HomeViewModel.submitFeedback`, `FeedbackRepositoryImpl.record` (txn) | **Gap-1:** 400ms debounce location undefined — PRD says "debounce same-card 👍/👎 within 400ms" but neither DESIGN nor ARCH says whether it lives in `FeedbackButtonRow` (Compose-side `LaunchedEffect`) or `HomeViewModel` (last-tap timestamp per cardId). **Gap-2:** "hero unchanged after 👎" logcat WARN `HERO_UNCHANGED_AFTER_DOWNVOTE cardId=$id` has no stated owner. |
| **FR-03** Intent chip strip, cold default = WORK, ✓ glyph | §3.1, §5.2 | `HomeViewModel._selectedIntent = MutableStateFlow(Intent.WORK)` | — |
| **FR-04** Chip switch + header strings + rapid-switch suppression | §2.3, §3.1.4, §5.2, §9.2 | `HomeViewModel.selectIntent`, `combine` re-emit | **Gap-3:** Rapid-switch suppression (<500ms since feed rendered → poison in-flight dwells) has **no owner**. `HomeViewModel` doesn't track "last intent switch at timestamp X" and `util/Dwell.kt` is described in ARCH Appendix but its interface is unspecified. |
| **FR-05** Discover feed 3 cards (positions 2..4) | §3.1, §5.3 | `HomeUiState.Content.discoverCards` = `result.ordered.drop(1)` | — |
| **FR-06** Dismiss ✕ + dismiss session per `(intent, process_lifetime)` + session-hide-plus-persist | §2.4, §5.3, §9.4 | `HomeViewModel._dismissedByIntent`, `dismissCard` | — |
| **FR-07** Dwell: Discover-only, ≥50% visible, 100ms throttle, 2000ms threshold, one-per-Appearance, pause on bg/sheet/drawer, intent-change ends Appearance, rapid-switch exemption | §5.3 "Dwell tracking hook" | `util/Dwell.kt` (stubbed) + `HomeViewModel.trackDwell` | **Gap-4:** Pause conditions (sheet open, drawer foreground, lifecycle <RESUMED) need a signal source. Compose has `LocalLifecycleOwner`; sheet has `profileSheetOpen`; drawer is a separate nav destination. Architecture has not said how these three "pause" signals reach `util/Dwell.kt`. |
| **FR-08** Action-tap snackbar "${actionLabel} — 即将上线" | §5.1, §5.3, §8.1 copy table (`action_snackbar`) | no `onCardAction` helper on VM (see D→A-3) | **Gap-5:** Action-tap persistence + Snackbar emission — see Locked Decision #8. |
| **FR-09** Feedback event persistence + `isHealthy` + destructive migration | — (infra) | `FeedbackEventEntity`, `FeedbackDao`, `FeedbackRepository.isHealthy`, `Room.fallbackToDestructiveMigration()` | — |
| **FR-10** Profile tag-weight update formula | — (infra) | `UserProfileDao.upsertDelta` + `FeedbackRepositoryImpl.record(... withTransaction { ... N× upsertDelta ... })` | — |
| **FR-11** ε-greedy engine | — (infra) | `domain/recommend/EpsilonGreedyEngine` | — |
| **FR-12** Profile v47 sheet, top 10, counter, reset | §3.3, §5.8, §7.3 | `ProfileDebugViewModel`, `UserProfileDao.observeTopByAbs(10)`, `FeedbackRepository.clearAll()` | **Gap-6:** Reset flow's step 2 ("reset active intent to 工作") is in PRD FR-12 but no method on `HomeViewModel` does that. `refresh()` only closes the sheet. |
| **FR-13** App drawer, 4-col grid, A-Z, launch via `getLaunchIntentForPackage` | §3.2, §5.7 | `AppDrawerScreen`, `AppDrawerViewModel`, `PackageAppsRepository.launchableApps/launchIntentFor` | — |
| **FR-14** Launcher intent-filter + Home-button canonical state | (infra) | `AndroidManifest.xml` (§11), `MainActivity.onNewIntent` (§10) + `HomeViewModel.onHomeButtonPressed()` | **Gap-7:** LazyListState.scrollToItem(0) hook — ARCH §10 says "Scroll-to-top is handled in HomeScreen via a LaunchedEffect keyed off a trip-counter StateFlow." — but no such StateFlow is declared on `HomeViewModel`. |
| **FR-15** AiDock placeholder | §3.1, §5.4 | `AiDock` composable | — |
| **FR-16** Seed load | — | `SeedCards.kt`, `SeedInstaller`, `LauncherApp.onCreate` | — |
| **FR-17** Default-launcher banner | §3.1.1, §7.7 | `DefaultLauncherBanner`, `HomeViewModel.setBannerVisibility/dismissBanner`, `util/DefaultLauncherCheck.kt` | **Gap-8:** `MainActivity.onResume` check logic has no owner spelled out — ARCH §2 declares `util/DefaultLauncherCheck.kt` exists but not its API. |
| **FR-18** Accessibility | §6 | enforced via Compose modifiers + `minimumInteractiveComponentSize` | — |
| **FR-19** Error/empty states | §7 (rows 7.1–7.7) | `HomeUiState.Content.allDismissed`, `storageHealthy`; `DrawerUiState.Empty/Error`; `ProfileSheetState.Empty` | **Gap-9 (mild):** "Default-launcher revoked" row relies on `MainActivity` calling `setBannerVisibility(true)` each resume — fine, but not explicitly traced. |
| **FR-20** Dev instrumentation (logcat `FLYWHEEL`, long-press badge dev panel, seed demo profile, dump profile, clear dismiss) | — (DESIGN is silent; §3.1.5 mentions "Long-press ProfileBadge (DEBUG only) → dev panel sheet") | partial — ARCH §7.2 spec'd `setDeterministicMode`. **No dev-panel sheet composable defined.** **No `FLYWHEEL` logcat hook in `FeedbackRepositoryImpl`.** **No "Seed demo profile" implementation.** | **Gap-10:** FR-20 is the largest coverage gap — it has a mention in DESIGN §3.1.5 and a partial API in ARCH (`setDeterministicMode`) but **nothing else is designed or specified**. Three of its four features (logcat, dev panel, seed demo profile) are unspecified. |

### PRD coverage gap summary

| # | FR | Gap |
|---|---|---|
| 1 | FR-02 | 400ms debounce location undefined |
| 2 | FR-02 | `HERO_UNCHANGED_AFTER_DOWNVOTE` logcat WARN unowned |
| 3 | FR-04 | Rapid-switch suppression owner undefined |
| 4 | FR-07 | Pause signals → Dwell helper channel undefined |
| 5 | FR-08 | ACTION_TAP signal plumbing + Snackbar emission |
| 6 | FR-12 | Reset flow's "reset intent to 工作" step unowned |
| 7 | FR-14 | Scroll-to-top trip-counter StateFlow missing |
| 8 | FR-17 | `DefaultLauncherCheck.kt` API unspecified |
| 9 | FR-19 | Default-launcher revoked — trace implicit (mild) |
| 10 | FR-20 | Dev instrumentation largely unspecified (biggest gap) |

---

## Part 4 — Resolutions

For each conflict / gap above, a decision. Format: **who wins / compromise / how**.

### D→A conflicts

**D→A-1 (`HeroState` pulsing).** Accept **compose-local state**, not VM state.
Rationale: the 600ms pulse is pure animation; putting it on `HomeUiState` bloats the state class and forces unnecessary recompositions. `HeroForYouCard` internally holds `var pulseCount by remember { mutableStateOf(0) }` and a `LaunchedEffect(pulseCount) { ... }`. `HomeViewModel` emits a `SharedFlow<UiEvent>` containing a `HeroPulse` event when it detects "feedback recorded but hero.id didn't change". See Locked Decision #1 and #2.

**D→A-2 (no hero-unchanged channel).** Accept **add `UiEvent` SharedFlow**. See Locked Decision #2.

**D→A-3 (no `onCardAction`).** Accept **add one method**. See Locked Decision #8.

**D→A-4 (icon pipeline).** Accept **architect's position, no Coil**. The `AppIconTile` composable uses a `produceState<ImageBitmap?>` keyed by `app.packageName` that calls `packageApps.loadIcon(pkg)?.toBitmap()?.asImageBitmap()` on `Dispatchers.IO`. Coil is explicitly **not** added — NFR-10 APK size + NFR-06 zero-telemetry argue against. See Locked Decision #6.

**D→A-5 (`normalizedFraction`).** Not a gap. Compose-local compute. See Locked Decision #5.

**D→A-6 (Snackbar channel).** Accept **add `UiEvent` SharedFlow on `HomeViewModel`**. See Locked Decision #2.

### A→D conflicts

**A→D-1 (⚠ glyph slot).** Accept **DESIGN wins — add the slot**. Render a 16sp ⚠ glyph at the start of the header's right block, immediately to the left of the profile badge, with 4dp gap, only when `storageHealthy == false`. See Locked Decision #13.

**A→D-2.** Not a real gap.

**A→D-3 (ProfileSheetState Error).** Accept **compromise — defer the Error variant to v0.2**. The Sheet re-uses `Empty` when Room is unhealthy; the ⚠ glyph in the header (A→D-1) + the Snackbar on the feedback tap is the user-facing signal. See Locked Decision #14.

### PRD coverage gaps

**Gap-1 (FR-02 debounce).** Decide: **ViewModel-level.** Keep the visual "active" 600ms flag in Compose (DESIGN §9.3 already says so), but the 400ms signal-coalescing lives in `HomeViewModel.submitFeedback`: a `Map<Pair<cardId, SignalType>, Long>` of last-submit timestamps; incoming feedback with `now - last < 400` is dropped before the repo call. See Locked Decision #3.

**Gap-2 (hero-unchanged WARN).** Decide: **emit from `HomeViewModel`** when the `combine` produces a new `Content` whose `heroCard.id == previousHero.id` AND the trigger was a feedback submission. Attach `android.util.Log.w("FLYWHEEL", "HERO_UNCHANGED_AFTER_DOWNVOTE cardId=$id")` call conditional on signal being `Dislike`. See Locked Decision #4.

**Gap-3 (rapid-switch suppression).** Decide: **`HomeViewModel` owns `lastIntentSwitchMs: Long`**. `util/Dwell.kt` receives a Clock + a `suspend fun shouldSuppress(): Boolean` via VM callback, or simpler: VM exposes `val suppressUntilMs: StateFlow<Long>` = lastIntentSwitchMs + 500; the dwell helper ignores Appearances begun before that timestamp. See Locked Decision #9.

**Gap-4 (FR-07 pause signals).** Decide: **centralize in `HomeScreen`**. The composable owns a `DwellController` (instance of `util/Dwell.kt`) which gets boolean inputs `isForeground`, `isSheetOpen`, `isDrawerOpen`, derived from `LocalLifecycleOwner` + `profileSheetOpen` + `navBackStackEntry.destination.route`. The controller calls `viewModel.trackDwell(card, ms)` only when the ms threshold is crossed. See Locked Decision #10.

**Gap-5 (FR-08 ACTION_TAP).** Decide: add `HomeViewModel.onCardAction(card)` that (a) `submitFeedback(card, Signal.ActionTapped(...))`, (b) emits `UiEvent.ShowSnackbar("${card.actionLabel} — 即将上线")`. See Locked Decision #8.

**Gap-6 (FR-12 reset-to-WORK).** Decide: `ProfileDebugViewModel.resetProfile(onDone)` stays as-is. `HomeViewModel.refresh()` is extended: set `_selectedIntent.value = Intent.WORK`, clear `_dismissedByIntent`, close sheet. The sheet's reset button calls `profileDebugVm.resetProfile { homeVm.refresh() }`. See Locked Decision #11.

**Gap-7 (FR-14 scroll-to-top).** Decide: add `val scrollToTopTrigger: SharedFlow<Unit>` on `HomeViewModel`, emitted by `onHomeButtonPressed()`. `HomeScreen` uses `LaunchedEffect(Unit) { viewModel.scrollToTopTrigger.collect { lazyListState.animateScrollToItem(0) } }`. See Locked Decision #12.

**Gap-8 (FR-17 `DefaultLauncherCheck`).** Decide: specify API now.
```kotlin
// util/DefaultLauncherCheck.kt
object DefaultLauncherCheck {
    fun isDefault(ctx: Context): Boolean = /* PackageManager.resolveActivity(MAIN+HOME).activityInfo.packageName == ctx.packageName */
}
```
`MainActivity.onResume { homeVm.setBannerVisibility(!DefaultLauncherCheck.isDefault(this)) }`. See Locked Decision #15.

**Gap-9 (FR-19 revoked-default trace).** Covered by Locked Decision #15's `onResume` hook. ✅

**Gap-10 (FR-20 dev instrumentation).** Decide: **scope down** — ship the minimum viable dev panel in Phase 3.
- `FLYWHEEL` logcat: add inside `FeedbackRepositoryImpl.record()` — one `Log.d` line per event: `"FLYWHEEL event=$signalType card=$cardId w=$weight top3=${topTags(3)}"`. Trivial.
- Dev panel sheet: a second `ModalBottomSheet` opened on long-press of profile badge, only rendered when `BuildConfig.DEBUG`. Three buttons: **Seed demo profile**, **Dump profile to logcat**, **Clear dismiss list**. Use an injected `DebugActions` class.
- Seed demo profile: hardcoded list of 20 `FeedbackEvent(cardId, signalType, weight, now)` tuples biased toward `coffee/design/music`. Inserted via `FeedbackRepository` one-shot call. Implementation in `util/DebugActions.kt`.
- `setDeterministicMode(seed)` already speced in ARCH §7.2.
See Locked Decision #16.

---

## Part 5 — Locked Decisions Log

Every numbered decision is a direct instruction to the dev agent. No further design consultation required.

1. **Hero pulse state is composable-local.** `HeroForYouCard` holds `var pulseToken by remember { mutableStateOf(0) }` and increments it when the VM emits `UiEvent.HeroPulse`. A `LaunchedEffect(pulseToken)` drives the 260ms `Motion.FeedbackPulse`. Do **not** add `heroPulsing: Boolean` to `HomeUiState`.

2. **Add `UiEvent` SharedFlow on `HomeViewModel`.** Interface:
   ```kotlin
   sealed interface UiEvent {
       data class ShowSnackbar(val message: String) : UiEvent
       data object HeroPulse : UiEvent
   }
   private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
   val events: SharedFlow<UiEvent> = _events.asSharedFlow()
   ```
   `HomeScreen` collects it via `LaunchedEffect(Unit) { viewModel.events.collect { … } }` and drives snackbars + hero pulse from it.

3. **Feedback 400ms debounce lives in `HomeViewModel.submitFeedback`.** Maintain `private val lastSubmit = mutableMapOf<Pair<String, SignalType>, Long>()`. Before the repo call: compute `key = cardId to signal.persistedType`; if `clock.nowMs() - (lastSubmit[key] ?: 0) < 400` return early; else update the map and proceed. Applies to THUMB_UP and THUMB_DOWN only; DISMISS/DWELL/ACTION_TAP bypass the debounce.

4. **`HERO_UNCHANGED_AFTER_DOWNVOTE` log.** In `HomeViewModel`, keep `private var lastHeroId: String? = null` and after each `combine` emission: if the trigger was a Dislike on `card.id == lastHeroId` **and** the new `heroCard.id == lastHeroId`, call `android.util.Log.w("FLYWHEEL", "HERO_UNCHANGED_AFTER_DOWNVOTE cardId=$lastHeroId")`. Also emit `UiEvent.HeroPulse` so the UI plays the confirmation pulse.

5. **ConfidenceBar fraction is computed in `HomeScreen`, not VM.** In `HomeScreen`: `val fraction = if (state.maxScore > 0) (state.scores[state.heroCard?.id] ?: 0f) / state.maxScore else 0.08f`. Same for `ProfileDebugRow.normalizedFraction = abs(weight) / maxAbsWeight` computed by `ProfileDebugSheet` over the `topTags` list.

6. **App icon pipeline — no Coil.** `AppIconTile` uses `produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) { value = withContext(Dispatchers.IO) { packageApps.loadIcon(app.packageName)?.toBitmap()?.asImageBitmap() } }`. `packageApps` obtained via `hiltViewModel<AppDrawerViewModel>()` exposing `suspend fun iconFor(pkg: String)` — add this method to `AppDrawerViewModel`. Do **not** add Coil or Glide.

7. **`AppInfo.iconKey` retained but unused in v0.1.** The field exists per ARCH §5.7; Phase 3 treats it as `iconKey = packageName` (trivially) and reads the icon via `PackageAppsRepository.loadIcon`.

8. **`HomeViewModel.onCardAction(card: Card)` is the single entry point for action taps.** Implementation:
   ```kotlin
   fun onCardAction(card: Card) {
       submitFeedback(card, Signal.ActionTapped(card.id, clock.nowMs()))
       viewModelScope.launch { _events.emit(UiEvent.ShowSnackbar("${card.actionLabel} — 即将上线")) }
   }
   ```
   Wire this from both `HeroForYouCard.onAction` and `DiscoverCard.onAction`.

9. **Rapid-switch suppression — `HomeViewModel` owns the timestamp.** Add `private var lastIntentSwitchMs: Long = 0L`. `selectIntent` sets `lastIntentSwitchMs = clock.nowMs()`. Expose `fun isDwellSuppressed(appearanceStartMs: Long): Boolean = appearanceStartMs - lastIntentSwitchMs < 500L`. `DwellController` calls this before emitting `trackDwell`. `trackDwell` in the VM has a second safeguard (same check) in case the controller misses it.

10. **`util/Dwell.kt` is the `DwellController` class.** Signature:
    ```kotlin
    class DwellController(
        private val clock: Clock,
        private val thresholdMs: Long = 2_000L,
        private val onDwell: (cardId: String, durationMs: Long) -> Unit
    ) {
        fun onVisibilityChange(cardId: String, fraction: Float) { /* ≥0.5 begins Appearance */ }
        fun setPaused(paused: Boolean) { /* pause/resume all active Appearances */ }
        fun endAll() { /* on intent change / profile reset */ }
    }
    ```
    Owned by `HomeScreen` as `remember { DwellController(clock) { cid, ms -> viewModel.trackDwell(card = feed.first { it.id == cid }, durationMs = ms) } }`. Lifecycle: paused when `lifecycle.currentState < RESUMED` OR `profileSheetOpen` OR `currentRoute != HOME`. Call `endAll()` in `LaunchedEffect(state.selectedIntent)`.

11. **`HomeViewModel.refresh()` is extended** to: `_selectedIntent.value = Intent.WORK`; `_dismissedByIntent.value = emptyMap()`; `profileSheetOpen.value = false`; `_events.tryEmit(UiEvent.HeroPulse)` is NOT fired here (this is a reset, not a feedback). Sheet's reset button calls `profileDebugVm.resetProfile { homeVm.refresh() }`.

12. **Add `scrollToTopTrigger` SharedFlow on `HomeViewModel`.**
    ```kotlin
    private val _scrollToTop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopTrigger: SharedFlow<Unit> = _scrollToTop.asSharedFlow()
    fun onHomeButtonPressed() {
        profileSheetOpen.value = false
        _scrollToTop.tryEmit(Unit)
    }
    ```
    `HomeScreen`: `LaunchedEffect(Unit) { viewModel.scrollToTopTrigger.collect { lazyListState.animateScrollToItem(0) } }`.

13. **Storage-unhealthy ⚠ glyph slot in header.** Modify `HeaderRow` to render, only when `state.storageHealthy == false`: a 16sp `⚠` glyph, color `LauncherPalette.DangerRed` at 70% alpha, with `contentDescription = "存储不可用，部分功能受限"` and `Modifier.minimumInteractiveComponentSize()` even though it's non-interactive (to avoid cramping the 48dp profile badge). Placed with 4dp end-margin before the profile badge.

14. **`ProfileSheetState` keeps 3 variants (Loading/Loaded/Empty) — no Error.** When Room is unhealthy, the sheet displays `Empty`. The ⚠ glyph (Decision #13) and the Snackbar on feedback taps are the user-facing error channels. Revisit in v0.2.

15. **`util/DefaultLauncherCheck.kt` API.**
    ```kotlin
    object DefaultLauncherCheck {
        fun isDefault(ctx: Context): Boolean {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return resolved?.activityInfo?.packageName == ctx.packageName
        }
    }
    ```
    `MainActivity.onResume` calls `homeViewModel.setBannerVisibility(!DefaultLauncherCheck.isDefault(this))`.

16. **FR-20 dev instrumentation — Phase 3 scope.** Ship all four sub-features but minimally:
    - **Logcat:** `FeedbackRepositoryImpl.record` ends with `if (BuildConfig.DEBUG) Log.d("FLYWHEEL", "evt=${signal.persistedType} card=${card.id} w=${signal.weight} top3=${profileDao.topPositive(3).joinToString { it.tag+"="+it.weight }}")`.
    - **Long-press dev panel:** a second `ModalBottomSheet` in `HomeScreen`, opened by `detectTapGestures(onLongPress = { ... })` on the profile badge. Rendered only when `BuildConfig.DEBUG`. Contains three `Button`s wired to `DebugActions` methods. Use a new `@HiltViewModel class DebugPanelViewModel` or just pass actions as lambdas from `HomeScreen`.
    - **`util/DebugActions.kt`:**
      ```kotlin
      class DebugActions @Inject constructor(
          private val feedbackRepository: FeedbackRepository,
          private val cardRepository: CardRepository,
          private val clock: Clock
      ) {
          suspend fun seedDemoProfile() { /* 20 synthetic signals biased coffee/design/music */ }
          suspend fun dumpProfileToLogcat(profileDao: UserProfileDao) { ... }
          // clearDismissList handled by HomeViewModel.restoreDismissedForActiveIntent()
      }
      ```
    - **`setDeterministicMode(seed)`:** already in `EpsilonGreedyEngine` (ARCH §7.2). Wire a fourth button **only if time permits**; otherwise leave it for a v0.2 demo script.

17. **Naming: use `@Named("recEngineRng")` (not `@RecEngineRng`).** ARCH Appendix A.4 allows either; locking in the `@Named` form because PRD-v2 FR-11 says `@Named` verbatim.

18. **No Coil, no Glide, no accompanist-systemuicontroller, no kotlinx-datetime.** Every dependency must be in the ARCH §3.1 `libs.versions.toml` block. Adding any new dep requires a PM sign-off commit message `deps(scope): add <dep> for <reason>` and must not touch `libs.versions.toml` until that commit.

19. **Theming surfaces (NFR-11) are `MainActivity.onCreate` work, not Compose work.** Status bar / nav bar transparency + light-content icons are set via `WindowCompat.setDecorFitsSystemWindows(window, false)` + `WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false` in `MainActivity.onCreate` **before** `setContent { ... }`.

20. **Room schema export is committed.** After first successful build, `app/schemas/com.bobpan.ailauncher.data.db.LauncherDatabase/1.json` must be `git add`ed in the same commit as the Room entities.

---

## Part 6 — Phase 3 Kickoff Checklist

### 6.1 First actions (must run before any Kotlin is compiled)

1. **Generate Gradle wrapper** (ARCH §15.2):
   ```bash
   cd /home/jadetreer/hermes-projects/ai-launcher-mvp
   gradle wrapper --gradle-version 8.11.1 --distribution-type bin
   chmod +x gradlew
   git add gradlew gradlew.bat gradle/wrapper/
   git commit -m "build: add gradle wrapper 8.11.1"
   git push origin main
   ```
   Verify `gradle/wrapper/gradle-wrapper.properties` pins `distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip`.

2. **Create `gradle/libs.versions.toml`** verbatim from ARCH §3.1.

3. **Create `settings.gradle.kts`** verbatim from ARCH §3.2.

4. **Create `build.gradle.kts` (project-level)** verbatim from ARCH §3.3.

5. **Create `app/build.gradle.kts`** verbatim from ARCH §3.4.

6. **Create `gradle.properties`** verbatim from ARCH §3.5.

7. **`./gradlew :app:help`** — confirm wrapper downloads Gradle 8.11.1 and resolves plugins.

### 6.2 Build order (in recommended sequence)

| Step | Scope | Files | Smoke-test after |
|---|---|---|---|
| 1 | **Gradle + skeleton** | (above) + `AndroidManifest.xml` minimal + `LauncherApp.kt` + empty `MainActivity.kt` | `./gradlew :app:assembleDebug` compiles, no Hilt yet |
| 2 | **Hilt bootstrap** | `@HiltAndroidApp LauncherApp`, `AppModule`, `DatabaseModule`, `RepositoryModule`, `DomainModule` | `./gradlew :app:assembleDebug` still compiles with Hilt KSP |
| 3 | **Domain models** | `Intent`, `CardType`, `Card`, `Signal`, `SignalType`, `UserProfile`, `ConfidenceLevel`, `AppInfo` | `./gradlew :app:testDebugUnitTest` — trivial test `Intent.values().size == 4` |
| 4 | **Room layer** | entities, DAOs, `Converters`, `LauncherDatabase` | `LauncherDatabaseSmokeTest` passes on emulator |
| 5 | **Seed + repositories** | `SeedCards.kt` (16 cards), `SeedInstaller`, `FeedbackRepositoryImpl`, `CardRepositoryImpl`, `ProfileRepositoryImpl`, `PackageAppsRepositoryImpl` | `FeedbackRepositoryImplTest` passes (FR-10 formula unit test) |
| 6 | **Engine** | `RecommendationEngine` interface + `EpsilonGreedyEngine` | `EpsilonGreedyEngineTest` passes SM-07 1000-iter test |
| 7 | **Theme + Design tokens** | `Color.kt`, `Type.kt`, `Shape.kt`, `Motion.kt`, `Glass.kt`, `Theme.kt` | Preview composable renders with gradient bg |
| 8 | **Stateless components** | `HeroForYouCard`, `IntentChip`, `DiscoverCard`, `AiDock`, `ConfidenceBar`, `FeedbackButtonRow`, `AppIconTile`, `ProfileDebugRow`, `DefaultLauncherBanner`, `EmptyIntentState` | Each has a `@Preview` function in the same file |
| 9 | **Home VM + Screen** | `HomeUiState`, `HomeViewModel`, `UiEvent`, `HomeScreen`, `HeaderRow`, `DwellController` | `HomeViewModelTest` passes; APK launches, shows cold-start Hero `work_01` |
| 10 | **Profile sheet** | `ProfileSheetState`, `ProfileDebugViewModel`, `ProfileDebugSheet` | Tap badge → sheet opens → reset button clears tables |
| 11 | **Drawer** | `DrawerUiState`, `AppDrawerViewModel`, `AppDrawerScreen` | Dock drawer button opens drawer; tapping an app launches it |
| 12 | **Navigation + MainActivity wiring** | `LauncherNavHost`, `MainActivity` with `onNewIntent`, `setDecorFitsSystemWindows`, `DefaultLauncherCheck` hook | FR-14 golden path works |
| 13 | **FR-20 dev instrumentation** | `DebugActions`, long-press handler on badge, logcat `FLYWHEEL` tag | Long-press badge shows dev sheet; Seed demo profile populates top tags |

Commit after each step with `feat(scope): ...` messages.

### 6.3 Smoke-test criteria (go / no-go before Phase 3 is done)

Each of these must pass on a Pixel 6 (or Pixel 6 emulator, API 34) before Phase 3 ships:

- **S1** App installs, cold-starts, and shows home screen with Hero `work_01`, 4 intent chips (工作 selected with ✓ glyph), 3 Discover cards (positions 2..4 of engine output), AI Dock. No crash.
- **S2** Tap each intent chip in turn → Hero and Discover feed swap within 200ms; header string updates to one of the 4 fixed strings.
- **S3** Tap 👍 on Hero → Snackbar/pulse plays; `FeedbackDao.count()` increments by 1; repeat tap within 400ms does **not** double-increment (FR-02 debounce).
- **S4** Tap 👎 on Hero 2× → Hero cardId changes (SM-04).
- **S5** Tap ✕ on a Discover card → card animates out, re-flow plays, `feedback_events` has 1 DISMISS row.
- **S6** Scroll a Discover card into view, wait 2500ms, scroll off → exactly 1 DWELL event for that card in `feedback_events` (SM-10).
- **S7** Open profile badge → sheet shows top tags + counter. Tap 重置画像 → confirm → tables empty, Hero reverts to `work_01`, intent resets to 工作 (SM-09).
- **S8** Open drawer → alphabetical grid of launchable apps. Tap an app → it launches. Press Home → return to HomeScreen, same intent, scroll at top (FR-14, SM-01).
- **S9** Force-stop and re-launch → `FeedbackDao.count()` preserves prior count (SM-03).
- **S10** When not default launcher: banner visible; tap banner → system home-app settings opens.
- **S11** `./gradlew :app:testDebugUnitTest` → green. Minimum: `EpsilonGreedyEngineTest`, `HomeViewModelTest`, `FeedbackRepositoryImplTest`, `ProfileDebugViewModelTest`, `AppDrawerViewModelTest` all pass.
- **S12** `./gradlew :app:connectedDebugAndroidTest` → `LauncherDatabaseSmokeTest` green.

### 6.4 Definition of Done — Phase 3

Phase 3 is **done** when **all** of the following are true:

1. A debug APK can be built with `./gradlew :app:assembleDebug` with zero warnings at AGP-default lint level.
2. All 20 FRs have implementation in the package tree, traceable to the ARCH §2 file names.
3. All 12 smoke tests (§6.3 S1–S12) pass on Pixel 6 emulator API 34.
4. Unit test suite runs green on JVM in <60s.
5. `./gradlew :app:dependencies | grep -iE 'okhttp|retrofit|ktor|firebase|crashlytics|sentry|coil|glide'` returns empty (NFR-06).
6. No `<uses-permission android:name="android.permission.INTERNET" />` in merged manifest.
7. Release APK size ≤ 15MB (NFR-10) — even though release is debug-signed and minify-off, raw size must be under budget.
8. `app/schemas/com.bobpan.ailauncher.data.db.LauncherDatabase/1.json` committed.
9. All 20 locked decisions in Part 5 implemented.
10. GitHub Actions `build-apk.yml` and `test.yml` both pass on `main`.

When all 10 are checked, tag the commit `v0.1.0-phase3-done` and hand off for QA golden-path (NFR-07 script).

---

## Part 7 — PM Sign-Off Notes

- **No show-stoppers.** The four source docs are tightly consistent on every load-bearing behavior (engine, feedback persistence, feedback flow, drawer, launcher integration). All conflicts found are either (a) minor state-plumbing gaps resolved by the SharedFlow pattern in Locked Decision #2, or (b) under-specified support surfaces (dev panel, default-launcher check, scroll-to-top) resolved by 2–5-line additions.
- **Biggest gap is FR-20** (dev instrumentation). It's not a Phase-3 blocker — the flywheel works without it — but it *is* a demo blocker. Locked Decision #16 mandates it ship anyway; deviating (shipping FR-20 partial) is allowed only if the Smoke-tests (§6.3) are all green and time is tight.
- **No scope expansion requested.** This arbitration preserves every out-of-scope decision in PRD-v2 §7.
- **No critical contradictions requiring rework of any of the three upstream docs.** They can remain at v1 / v2 as-is. Future authoritative changes go into this file as Part 5 additions (#21, #22, ...), or into a new `DESIGN-DECISIONS-v2.md` if the list exceeds ~40 entries.

*End of DESIGN-DECISIONS.*
