# DESIGN v1 — AI Launcher MVP (v0.1)

**Status:** Approved for build · **Owner:** UI/UX · **Last updated:** 2026-04-18
**Companion docs:** `AGENTS.md`, `docs/PRD-v2.md`, `docs/ARCHITECTURE-v1.md`
**Scope:** Implementation-grade spec. The Phase 3 dev agent should be able to transcribe each Compose signature and Modifier chain in this document directly into `ui/` sources without further design consultation. Where this doc and PRD-v2 appear to disagree, PRD-v2 wins on *behavior*, this doc wins on *visual/interaction*.

> **Reading rule.** Section 4 (Design System tokens) is the single source of truth for colors, typography, shapes, spacing, glass, and motion. Every other section *references* tokens rather than redefining them. Do not inline hex literals in component code — pull from `MaterialTheme.colorScheme.*` or `LauncherTheme.*` extensions.

---

## 1. Information Architecture

### 1.1 Screen map

```
AI Launcher (single Activity: MainActivity)
│
├── HomeScreen                        [root, startDestination]
│   ├── Header row                    (greeting + profile badge)
│   ├── DefaultLauncherBanner?        (conditional, FR-17)
│   ├── HeroForYouCard                (top ranked card for active intent)
│   ├── IntentChipStrip               (4 chips · 通勤/工作/午餐/休息)
│   ├── DiscoverFeed                  (LazyColumn of DiscoverCard)
│   ├── AiDock                        (bottom bar · drawer btn + AI pill)
│   └── ProfileDebugSheet             (ModalBottomSheet overlay · FR-12)
│
├── AppDrawerScreen                   (nav destination · FR-13)
│   └── AppsGrid                      (4-col alphabetical grid)
│
└── (System overlays)
    ├── Snackbar host                 (dark glass · NFR-11)
    └── ResetProfileDialog            (Material 3 AlertDialog, dark)
```

The app is single-activity + two Compose destinations. The profile debug sheet is an *overlay on Home* (ModalBottomSheet), not a nav destination, so back gesture closes it before popping to Home.

### 1.2 Nav graph (Compose Navigation)

```
┌────────────────────┐    drawer btn in AiDock    ┌────────────────────┐
│                    │ ──────────────────────────▶│                    │
│   HomeScreen       │                            │   AppDrawerScreen  │
│   route="home"     │◀────────────────────────── │   route="drawer"   │
│                    │   back gesture / Home btn  │                    │
└─────────┬──────────┘                            └────────────────────┘
          │ profile badge tap (overlay, not nav)
          ▼
   ProfileDebugSheet
   (ModalBottomSheet
    attached to Home
    composition)
```

`NavHost(startDestination = "home")`. Exactly two composable destinations. No nested graphs. `popUpTo("home") { inclusive = false }` on every navigation return.

### 1.3 Z-order on HomeScreen

From back to front:

| Layer | Content | Elevation |
|---|---|---|
| L0 | `BackgroundGradient` (radial ambient blobs + vertical gradient) | 0dp |
| L1 | `Column` content (header, hero, chips, feed) | 0dp |
| L2 | `AiDock` (sticky bottom, inside a `Box` bottom alignment) | 4dp |
| L3 | Snackbar host | 6dp |
| L4 | `ProfileDebugSheet` (ModalBottomSheet scrim + sheet) | 16dp |
| L5 | `ResetProfileDialog` | 24dp |

Elevation is *visual* (shadow/tonal), not `Modifier.zIndex` — Compose natural composition order handles stacking.

---

## 2. User Flows

Six ASCII flow diagrams covering the MVP golden path. Each arrow labeled with trigger; each box labeled with the screen region that changes.

### 2.1 Cold boot → first meaningful paint

```
  [ App launcher icon tapped / Home btn (default) ]
                  │
                  ▼
  ┌───────────────────────────────────┐
  │ MainActivity.onCreate              │
  │  - install splash (themed gradient)│
  │  - setContent { LauncherTheme {} } │
  └───────────────┬───────────────────┘
                  │  HomeViewModel.init()
                  ▼
  ┌───────────────────────────────────┐
  │ ProfileRepository.snapshot()       │ ← empty on first run
  │ CardRepository.getAll()            │ ← 16 seeds from SeedCards.kt
  │ RecommendationEngine.recommend(    │
  │   intent=WORK, count=4)            │
  └───────────────┬───────────────────┘
                  │  StateFlow<HomeUiState>
                  ▼
  ┌───────────────────────────────────┐
  │ HomeScreen composes                │
  │   Header ("工作模式 · 14:22")       │
  │   Banner? (if not default launcher)│
  │   HeroForYouCard = work_01         │
  │   IntentChipStrip (WORK selected)  │
  │   DiscoverFeed [work_02, work_03,  │
  │                  work_04]          │  ← exploration slot = work_04
  │   AiDock                           │
  └───────────────────────────────────┘
                  │  NFR-01: ≤500ms to Hero committed
                  ▼
         [ user sees home screen ]
```

### 2.2 Feedback on Hero (👍 path)

```
  [ user taps 👍 on HeroForYouCard ]
                  │
         coalesce? (same cardId <400ms? → drop)
                  │ no
                  ▼
  ┌───────────────────────────────────┐
  │ FeedbackButtonRow emits onThumbUp  │
  │ HomeViewModel.onFeedback(          │
  │   card, THUMB_UP, weight=+3)       │
  └───────────────┬───────────────────┘
                  │ same Room txn
                  ▼
  ┌───────────────────────────────────┐
  │ FeedbackDao.insert(event)          │
  │ UserProfile tag weights += 3/N     │
  └───────────────┬───────────────────┘
                  │
                  ▼
        engine.recommend(WORK, 4)
                  │
         top card changed?
           ┌──────┴──────┐
         yes             no
           │              │
           ▼              ▼
  crossfade-in      pulse confirm
  new Hero (300ms)  current Hero
  (see §9.1)        (see §9.3)
                  │
                  ▼
       [ Hero updated / pulsed ]
```

### 2.3 Intent switch (WORK → LUNCH)

```
  [ user taps "午餐" IntentChip ]
                  │
         rapid-switch guard
         (last switch <500ms? mark feed signals poisoned)
                  │
                  ▼
  ┌───────────────────────────────────┐
  │ HomeViewModel.onIntentSelected(    │
  │   LUNCH)                           │
  │  - activeIntent = LUNCH            │
  │  - dismissList[LUNCH] cleared IF   │
  │    re-entering (FR-06 (b))         │
  │  - scroll feed to top              │
  └───────────────┬───────────────────┘
                  │
                  ▼
  engine.recommend(LUNCH, 4)
                  │
                  ▼
  ┌───────────────────────────────────┐
  │ HomeScreen re-renders (≤200ms)     │
  │   Header → "午餐时间 · HH:mm"       │
  │   HeroForYouCard → lunch_01        │
  │   Chip strip: ✓LUNCH filled accent │
  │   DiscoverFeed: [lunch_02,         │
  │     lunch_03, lunch_04]            │
  └───────────────────────────────────┘
                  │  cross-fade (§9.2)
                  ▼
       [ feed swapped, visual held ]
```

### 2.4 Dismiss a Discover card

```
  [ user taps ✕ on discover card[i] ]
                  │
                  ▼
  ┌───────────────────────────────────┐
  │ Card animates OUT                  │
  │  - translateX += 40dp (200ms)      │
  │  - alpha 1→0 (200ms, FastOutLinear)│
  │ simultaneously:                    │
  │  FeedbackEvent(DISMISS, -2) saved  │
  │  dismissList[intent] += cardId     │
  └───────────────┬───────────────────┘
                  │
        any cards remaining?
           ┌──────┴──────┐
         yes             no (all 4 dismissed)
           │              │
           ▼              ▼
  LazyColumn reflows   Empty state
  (animateItem-        🌱 "已看完本
   Placement, 250ms)    模式推荐" + 恢复
                  │
                  ▼
         [ feed updated ]
```

### 2.5 Open Profile Debug Sheet

```
  [ user taps profile badge in Header ]
                  │
                  ▼
  ┌───────────────────────────────────┐
  │ HomeViewModel.openProfileSheet()   │
  │  - dwell timers PAUSED (FR-07.5)   │
  │  - profileUiState = Loading        │
  └───────────────┬───────────────────┘
                  │
                  ▼
  ┌───────────────────────────────────┐
  │ ProfileRepository.top10Tags()      │
  │ FeedbackDao.count()                │
  └───────────────┬───────────────────┘
                  │ <50ms
                  ▼
  ┌───────────────────────────────────┐
  │ ModalBottomSheet slides up         │
  │  scrim rgba(10,15,30,0.72)         │
  │  - Title "Profile v47"             │
  │  - Counter "累计反馈: N"           │
  │  - Top 10 ProfileDebugRow          │
  │  - 重置画像 button (danger style)  │
  └───────────────┬───────────────────┘
                  │ tap 重置画像
                  ▼
  AlertDialog "确定清空…?"
                  │ Confirm
                  ▼
  clear tables → sheet dismisses → Hero resets
```

### 2.6 App drawer open + return via Home button

```
  [ user taps drawer icon in AiDock ]
                  │
                  ▼
  navController.navigate("drawer")
                  │  slide-up 250ms (§9)
                  ▼
  ┌───────────────────────────────────┐
  │ AppDrawerScreen                    │
  │  header "应用 · N"                  │
  │  4-col grid of AppIconTile         │
  │  (alphabetical, loaded async)      │
  └───────────────┬───────────────────┘
                  │ user taps an app tile
                  ▼
       startActivity(launchIntent)
                  │
                  ▼
       [ third-party app opens ]
                  │
                  │ user presses Home button
                  ▼
  MainActivity (singleTask) resumes
                  │
                  ▼
  FR-14 canonical state enforcement:
    1. dismiss Profile sheet
    2. popBackStack to "home"
    3. LazyListState.scrollToItem(0)
    4. PRESERVE activeIntent
    5. PRESERVE dismissList
                  │
                  ▼
       [ HomeScreen, same intent, fresh top ]
```

---

## 3. Screen Wireframes

Wireframes are described surface-by-surface with exact dp values, state matrix, and interactions. The dev agent should take the dp numbers literally — no "roughly" interpretation.

### 3.1 HomeScreen

```
┌────────────────────────────────────────────────┐  ← status bar (transparent,
│                                                │     light icons)
│   ░░░  Ambient blob #1 (blue, radial blur)     │
│                                                │
│  ┌──────────────────────────────────────────┐  │  padding: 20dp H, 16dp T
│  │  早安，Kai                               │  │  Header row (56dp tall)
│  │  工作模式 · 14:22           ●  Profile ▸ │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│  [ DefaultLauncherBanner (optional, 52dp) ]    │  FR-17, 8dp top margin
│                                                │
│  ┌──────────────────────────────────────────┐  │  16dp top margin
│  │                                          │  │
│  │    🎨                                     │  │
│  │    继续 Figma 稿件                         │  │  HeroForYouCard
│  │    示例文件 "Launcher v2 - Home"           │  │  (≥ 208dp tall,
│  │                                          │  │   fills width − 40dp)
│  │    ✧ 示例：最近编辑的设计稿                 │  │
│  │                                          │  │
│  │    ┌──────────┐       ┌─────┐  ┌─────┐   │  │
│  │    │ 打开 Figma│       │ 👍  │  │ 👎  │   │  │
│  │    └──────────┘       └─────┘  └─────┘   │  │
│  │    ──────────── ConfidenceBar ──         │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│   [通勤]  [✓ 工作]  [午餐]  [休息]              │  IntentChipStrip
│                                                │  (48dp tall · 12dp gap)
│   ░░░  Ambient blob #2 (pink, radial blur)     │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │ 🎯 开启专注模式 45 min          · NEW      │  │  DiscoverCard
│  │    屏蔽通知、白噪音、番茄钟          ✕     │  │  (160dp tall)
│  │    ┌──────────┐                          │  │
│  │    │ 开始专注  │                           │  │
│  │    └──────────┘                          │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │ 📅 今日日程概览                      ✕     │  │
│  │    示例：3 个会议、2 个待办              │  │
│  │    ┌──────────┐                          │  │
│  │    │ 查看日程  │                           │  │
│  │    └──────────┘                          │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│  ┌──────────────────────────────────────────┐  │
│  │ 📋  试试 Linear 建单  [NEW]          ✕     │  │
│  │    轻量级项目管理，给你看看                │  │
│  │    ┌──────────┐                          │  │
│  │    │ 了解一下  │                           │  │
│  │    └──────────┘                          │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│  ──── 72dp bottom spacer (dock clearance) ──── │
│                                                │
│  ┌──────────────────────────────────────────┐  │  AiDock (sticky bottom)
│  │  ▦         ✦ AI · 即将上线           · · │  │  72dp tall, 16dp inset
│  └──────────────────────────────────────────┘  │
│                                                │
└────────────────────────────────────────────────┘  ← nav bar (transparent)
```

#### 3.1.1 Composition outline

```kotlin
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntentSelected: (Intent) -> Unit,
    onHeroFeedback: (Card, Signal) -> Unit,
    onCardAction: (Card) -> Unit,
    onCardDismiss: (Card) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSetDefault: () -> Unit,
    onDismissBanner: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherTheme.gradients.home)
            .ambientBlobs()         // L0 radial blurs
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 96.dp  // 72dp dock + 24dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderRow(state, onOpenProfile) }
            if (state.showDefaultBanner) {
                item { DefaultLauncherBanner(onSetDefault, onDismissBanner) }
            }
            item { HeroForYouCard(state.hero, onHeroFeedback, onCardAction) }
            item { IntentChipStrip(state.intent, onIntentSelected) }
            items(state.feed, key = { it.id }) { card ->
                DiscoverCard(
                    card = card,
                    onAction = { onCardAction(card) },
                    onDismiss = { onCardDismiss(card) },
                    modifier = Modifier.animateItemPlacement(
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    )
                )
            }
            if (state.feed.isEmpty()) {
                item { EmptyIntentState(onRestore = state.onRestoreDismissed) }
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
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
```

#### 3.1.2 Spacing

| Surface | Pad / Size |
|---|---|
| Outer horizontal | `20.dp` |
| Header row height | `56.dp` (min), content-hug max |
| Banner top margin (when shown) | `8.dp` from header |
| Gap between composition items | `16.dp` (`Arrangement.spacedBy`) |
| Hero card vertical padding inside | `20.dp` |
| Hero card internal element gaps | title→desc `8.dp`, desc→whyLabel `12.dp`, whyLabel→actions `20.dp` |
| ChipStrip inter-chip gap | `12.dp` |
| Discover card vertical padding inside | `16.dp` |
| Dock height | `72.dp` |
| Dock horizontal inset | `16.dp` |
| Dock from nav bar | `navigationBarsPadding()` + `12.dp` |

#### 3.1.3 State matrix

| State | Trigger | Hero | Feed | Banner | Notes |
|---|---|---|---|---|---|
| **Cold / empty profile** | First launch or post-reset | `work_01` (seed order) | `[work_02, work_03, work_04]` | Visible if not default | FR-11 cold-start; NFR-01 ≤500ms |
| **Loaded (steady)** | Post-snapshot, profile has entries | Engine top-1 | Engine 2..4 in score order, explore at tail | Visible if not default | Normal |
| **Loading** | `HomeUiState.isLoading` (rare; first compose before flow emits) | HeroForYouCard in `Loading` variant (shimmer) | 3 `DiscoverCard` shimmer placeholders | Hidden | Shimmer spec §4.6 |
| **Error — Room unhealthy** | `FeedbackRepository.isHealthy == false` | Rendered from in-memory `SEED_CARDS` | Same | Hidden | FR-19: feedback taps Snackbar `"存储暂时不可用，反馈未保存"` |
| **All-cards-dismissed** | `dismissList.size == 4` for active intent | `EmptyIntentGlassCard` 🌱 with 恢复本模式 btn | Empty | Hidden | FR-19 row 4 |
| **Reduce motion on** | `TRANSITION_ANIMATION_SCALE == 0f` | Crossfade → instant | Dismiss → instant hide | unchanged | FR-18 |

#### 3.1.4 Header row

```
┌───────────────────────────────────────────────┐
│  早安，Kai                        ┌──────────┐│
│  工作模式 · 14:22                 │● Profile ▸││
│                                   └──────────┘│
└───────────────────────────────────────────────┘
```

- Left block:
  - Line 1 `headlineSmall` — fixed `"早安，Kai"` (v0.1 — not time-of-day-variant; critique-compliant "mock-not-fabricated").
  - Line 2 `labelLarge` `colorScheme.onSurfaceVariant` — one of four fixed strings from FR-04 + `HH:mm` computed via `LocalDateTime.now().format("HH:mm")` at render.
- Right block: `ProfileBadge` — 40dp circle, glass surface + 1dp gradient border, inner 2-char label `P·` in `labelMedium`. Full component has 48dp touch target via `Modifier.minimumInteractiveComponentSize()`. `contentDescription = "查看画像 Profile v47"`.

#### 3.1.5 Interactions (Home)

| Gesture | Result |
|---|---|
| Tap ProfileBadge | opens `ProfileDebugSheet` |
| Tap Hero 👍 | FR-02 path (§2.2) |
| Tap Hero 👎 | FR-02 path inverse |
| Tap Hero action btn | FR-08 Snackbar + ACTION_TAP signal |
| Tap chip | FR-04 (§2.3) |
| Tap Discover action btn | FR-08 Snackbar |
| Tap Discover ✕ | FR-06 (§2.4) |
| Tap dock drawer btn | navigate `drawer` |
| Tap AI pill | no-op (disabled) |
| Vertical scroll on LazyColumn | feed scrolls; Hero can scroll off-screen |
| Long-press ProfileBadge (DEBUG only) | dev panel sheet (FR-20) |

### 3.2 AppDrawerScreen

```
┌────────────────────────────────────────────────┐
│                                                │  Same gradient bg as Home
│  ┌──────────────────────────────────────────┐  │  header bar, 56dp
│  │  应用 · 142                         ←     │  │
│  └──────────────────────────────────────────┘  │
│                                                │
│  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐            │  4-column grid
│  │ 📷 │  │ 🎵 │  │ 🎨 │  │ 📅 │            │  rowSpacing 20dp
│  │Cam  │  │Music│  │Figma│  │Cal  │           │  columnSpacing 12dp
│  └─────┘  └─────┘  └─────┘  └─────┘            │
│                                                │
│  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐            │
│  │ 🗓 │  │ 🧭 │  │ 🎮 │  │ 🛒 │            │
│  │Cal..│  │Maps │  │Game │  │Shop │           │
│  └─────┘  └─────┘  └─────┘  └─────┘            │
│                                                │
│  ... (lazy rows)                               │
│                                                │
└────────────────────────────────────────────────┘
```

```kotlin
@Composable
fun AppDrawerScreen(
    apps: List<AppInfo>,
    onBack: () -> Unit,
    onAppLaunch: (AppInfo) -> Unit
)
```

#### Spacing & grid

| Surface | Value |
|---|---|
| Outer horizontal padding | `16.dp` |
| Top content pad (below header) | `12.dp` |
| Bottom pad | `navigationBarsPadding()` |
| Grid columns | `4` fixed (Compose `GridCells.Fixed(4)`) |
| Row vertical arrangement | `Arrangement.spacedBy(20.dp)` |
| Column horizontal arrangement | `Arrangement.spacedBy(12.dp)` |
| AppIconTile total size | `72.dp × 96.dp` (icon 56dp + label under) |

#### State matrix

| State | Trigger | UI |
|---|---|---|
| **Loaded** | `apps.size >= 1` | Full grid |
| **Loading** | Activity resolution pending | 8 shimmering `AppIconTile` placeholders (dummy 8-item list) |
| **Empty** | `apps.isEmpty()` | Single glass card centered: `"没有可启动的应用"` (FR-19 row 6). 180dp tall, icon 🫙 |
| **Error** | PackageManager throws (defensive) | Same empty glass card with text `"应用列表暂时不可用"`. Logged to logcat, no crash. |

#### Interactions

| Gesture | Result |
|---|---|
| Tap tile | launch intent; if null → Snackbar `"无法打开此应用"` |
| Tap back arrow | `onBack()` → `navController.popBackStack()` |
| System back gesture | same as tap back |
| Home button | MainActivity singleTask → FR-14 canonical state |
| Vertical scroll | LazyVerticalGrid scrolls |

### 3.3 ProfileDebugSheet

```
       ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  ← scrim rgba(10,15,30,0.72)
  ╔══════════════════════════════════════════════╗  ← sheet top
  ║  ═══                                         ║   drag handle 32×4
  ║                                              ║
  ║   Profile v47                     累计反馈: 47║  title row
  ║                                              ║
  ║   ── Top Tags ────────────────────────────── ║
  ║                                              ║
  ║   coffee          ████████████░░░░░  +4.50   ║  ProfileDebugRow
  ║   design          ██████████░░░░░░░  +3.75   ║
  ║   figma           █████████░░░░░░░░  +3.25   ║
  ║   work            █████████░░░░░░░░  +3.00   ║
  ║   focus           ███████░░░░░░░░░░  +2.25   ║
  ║   lunch           ██████░░░░░░░░░░░  +1.75   ║
  ║   habit           █████░░░░░░░░░░░░  +1.50   ║
  ║   commute         ███░░░░░░░░░░░░░░  +0.75   ║
  ║   explore         ░░░░░█████░░░░░░░  −1.25   ║  (negative shown right-side
  ║   pm              ░░░░████░░░░░░░░░  −1.00   ║   of center, pink tint)
  ║                                              ║
  ║                                              ║
  ║   ┌───────────────────────────────────┐      ║
  ║   │       🗑  重置画像                  │     ║  danger button
  ║   └───────────────────────────────────┘      ║
  ║                                              ║  nav bar inset
  ╚══════════════════════════════════════════════╝
```

```kotlin
@Composable
fun ProfileDebugSheet(
    state: ProfileSheetState,
    onReset: () -> Unit,
    onDismiss: () -> Unit
)
```

Uses Material 3 `ModalBottomSheet` with:

```kotlin
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = LauncherTheme.glass.surfaceElevated,   // #161a2e @ ~92% + blur
    scrimColor = Color(0xB80A0F1E),                          // 72% alpha of bg
    dragHandle = { BottomSheetDefaults.DragHandle(color = LauncherTheme.glass.outlineSoft) },
    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
)
```

#### Spacing

| Surface | Value |
|---|---|
| Outer content padding | `24.dp` horizontal, `12.dp` top, `24.dp + navBars` bottom |
| Title row height | `48.dp`, baseline-centered |
| Section divider `── Top Tags ──` | 1dp `LauncherTheme.glass.outlineSoft` + 16dp vertical gap |
| Row vertical spacing | `12.dp` between ProfileDebugRow |
| Reset button top margin | `24.dp` |
| Reset button height | `56.dp` |

#### State matrix

| State | Trigger | UI |
|---|---|---|
| **Loaded** | `tags.isNotEmpty()` | Rows + counter + reset btn |
| **Empty** | `tags.isEmpty() && counter == 0` | No rows section; single glass placeholder `"还没有画像数据 · 给卡片一点反馈吧"` (icon 🌱), reset btn hidden |
| **Loading** | Initial sheet open | 10 shimmer rows, counter `—`, reset btn disabled |
| **Reset confirm** | Tap reset | Overlay `AlertDialog`; sheet stays mounted behind |

#### Interactions

| Gesture | Result |
|---|---|
| Swipe down on sheet | `onDismiss` |
| Tap on scrim | `onDismiss` |
| Back gesture | `onDismiss` |
| Tap 重置画像 | show `ResetProfileDialog` |
| Confirm dialog | execute FR-12 4-step reset, dismiss sheet |

---

## 4. Design System Tokens (Compose-ready)

All tokens exposed via two mechanisms:

1. Standard Material 3 `MaterialTheme.colorScheme.*` / `.typography.*` / `.shapes.*` — use these in all application code.
2. Extension object `LauncherTheme` — exposes the non-M3 pieces (glass, gradients, motion).

### 4.1 Colors (exact hex — single source of truth)

```kotlin
// ui/theme/Color.kt
object LauncherPalette {
    // Background
    val BgDeep          = Color(0xFF0A0F1E)   // gradient start
    val BgElevated      = Color(0xFF161A2E)   // gradient end, also dialog surface

    // Accents
    val AccentBlue      = Color(0xFF7CB4FF)   // primary
    val AccentBlueDim   = Color(0xCC7CB4FF)   // 80% — pressed state
    val AccentPink      = Color(0xFFFF7EC8)   // secondary
    val AccentPinkDim   = Color(0xCCFF7EC8)
    val PositiveGreen   = Color(0xFF64E6A0)   // tertiary / 👍 success pulse
    val DangerRed       = Color(0xFFFF6B7A)   // 👎 active, destructive

    // Neutrals
    val TextPrimary     = Color(0xFFF5F7FF)   // 98% white-ish on dark
    val TextSecondary   = Color(0xB3F5F7FF)   // 70%
    val TextMuted       = Color(0x80F5F7FF)   // 50%
    val TextDisabled    = Color(0x52F5F7FF)   // 32%

    // Glass
    val GlassFillSoft   = Color(0x0DFFFFFF)   // 5% white  (cards resting)
    val GlassFillStrong = Color(0x14FFFFFF)   // 8% white  (hero, sheet)
    val GlassOutline    = Color(0x29FFFFFF)   // 16% white (hairline borders)
    val GlassOutlineSoft= Color(0x14FFFFFF)   // 8% white  (dividers)

    // Ambient blobs
    val BlobBlue        = Color(0x667CB4FF)   // 40% of AccentBlue
    val BlobPink        = Color(0x4DFF7EC8)   // 30% of AccentPink
}
```

Material 3 mapping:

```kotlin
val LauncherColorScheme = darkColorScheme(
    primary            = LauncherPalette.AccentBlue,
    onPrimary          = LauncherPalette.BgDeep,
    primaryContainer   = Color(0xFF1E2C4B),
    onPrimaryContainer = LauncherPalette.TextPrimary,

    secondary          = LauncherPalette.AccentPink,
    onSecondary        = LauncherPalette.BgDeep,
    secondaryContainer = Color(0xFF3A1E33),
    onSecondaryContainer = LauncherPalette.TextPrimary,

    tertiary           = LauncherPalette.PositiveGreen,
    onTertiary         = LauncherPalette.BgDeep,

    error              = LauncherPalette.DangerRed,
    onError            = LauncherPalette.TextPrimary,

    background         = LauncherPalette.BgDeep,
    onBackground       = LauncherPalette.TextPrimary,

    surface            = LauncherPalette.BgElevated,
    onSurface          = LauncherPalette.TextPrimary,
    surfaceVariant     = Color(0xFF1F2440),
    onSurfaceVariant   = LauncherPalette.TextSecondary,

    outline            = LauncherPalette.GlassOutline,
    outlineVariant     = LauncherPalette.GlassOutlineSoft,

    scrim              = Color(0xB80A0F1E)   // 72% of BgDeep
)
```

**Contrast check (WCAG AA):**

| fg / bg | ratio | target | pass |
|---|---|---|---|
| TextPrimary #F5F7FF / BgDeep #0A0F1E | 18.1 : 1 | 4.5 | ✅ |
| TextSecondary 70% on BgDeep | 12.7 : 1 | 4.5 | ✅ |
| AccentBlue #7CB4FF on BgDeep | 8.1 : 1 | 3 | ✅ |
| AccentPink #FF7EC8 on BgDeep | 7.9 : 1 | 3 | ✅ |
| BgDeep on AccentBlue (btn label) | 8.1 : 1 | 4.5 | ✅ |
| TextPrimary on GlassFillStrong over bg | 13.4 : 1 (effective) | 4.5 | ✅ |

### 4.2 Typography

```kotlin
// ui/theme/Type.kt
val LauncherTypography = Typography(
    // DISPLAY — reserved, unused in v0.1
    displayLarge   = default.displayLarge,

    // HEADLINE — Hero title
    headlineLarge  = TextStyle(
        fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp, color = LauncherPalette.TextPrimary
    ),
    headlineMedium = TextStyle(                      // Discover card title
        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp, color = LauncherPalette.TextPrimary
    ),
    headlineSmall  = TextStyle(                      // Header line 1 "早安，Kai"
        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
        color = LauncherPalette.TextPrimary
    ),

    // TITLE — Sheet title, dialog title, section headers
    titleLarge     = TextStyle(
        fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium,
        color = LauncherPalette.TextPrimary
    ),
    titleMedium    = TextStyle(                      // IntentChip label
        fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp, color = LauncherPalette.TextPrimary
    ),

    // BODY — card descriptions
    bodyLarge      = TextStyle(                      // Hero description
        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal,
        color = LauncherPalette.TextSecondary
    ),
    bodyMedium     = TextStyle(                      // Discover description
        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal,
        color = LauncherPalette.TextSecondary
    ),

    // LABEL — small micro copy
    labelLarge     = TextStyle(                      // Header line 2; action btns
        fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp, color = LauncherPalette.TextPrimary
    ),
    labelMedium    = TextStyle(                      // whyLabel, badges, counters
        fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp, color = LauncherPalette.TextMuted
    ),
    labelSmall     = TextStyle(                      // NEW pill, version "v47"
        fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp, color = LauncherPalette.TextMuted
    )
)
```

Font family: system default (`FontFamily.Default`) in v0.1 — no custom font shipped (keeps APK under NFR-10 15MB).

**Font scaling policy (FR-18):** all sizes above are sp, scale automatically. The Hero card switches to a 2-line-clamp on title and 3-line-clamp on description when `LocalDensity.current.fontScale > 1.3f`.

### 4.3 Shape scale

```kotlin
// ui/theme/Shape.kt
val LauncherShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // chips pressed, counters
    small      = RoundedCornerShape(10.dp),  // IntentChip, small buttons, badges
    medium     = RoundedCornerShape(16.dp),  // DiscoverCard, AiDock, Banner
    large      = RoundedCornerShape(22.dp),  // HeroForYouCard, BottomSheet top
    extraLarge = RoundedCornerShape(28.dp)   // future / reserved
)
```

**Named alias (required from brief):**

| Name | dp | Compose |
|---|---|---|
| `sm` | 10 | `Shapes.small` |
| `md` | 16 | `Shapes.medium` |
| `lg` | 22 | `Shapes.large` |

### 4.4 Spacing scale

Use **only** these values anywhere a `.dp` literal is tempting:

```kotlin
object Space {
    val xxs = 4.dp
    val xs  = 8.dp
    val sm  = 12.dp
    val md  = 16.dp
    val lg  = 20.dp
    val xl  = 24.dp
    val xxl = 32.dp
}
```

Any value outside `{4, 8, 12, 16, 20, 24, 32}` requires a design sign-off (and should probably be replaced with nearest token).

### 4.5 Glassmorphism

```kotlin
// ui/theme/Glass.kt
data class GlassTokens(
    val surfaceSoft:       Color = LauncherPalette.GlassFillSoft,     // 5% white
    val surfaceElevated:   Color = LauncherPalette.GlassFillStrong,   // 8% white
    val outline:           Color = LauncherPalette.GlassOutline,      // 16% white
    val outlineSoft:       Color = LauncherPalette.GlassOutlineSoft,  // 8% white
    val blurRadius:        Dp    = 24.dp,                             // for RenderEffect
    val ambientBlobBlur:   Dp    = 55.dp                              // hero ambient
)
```

**Glass surface recipe (composable Modifier):**

```kotlin
fun Modifier.glassSurface(
    shape: Shape = LauncherShapes.medium,
    elevated: Boolean = false,
    accent: Color? = null
): Modifier = this
    .clip(shape)
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                if (elevated) LauncherPalette.GlassFillStrong
                else LauncherPalette.GlassFillSoft,
                LauncherPalette.GlassFillSoft.copy(alpha = 0.02f)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOfNotNull(
                accent?.copy(alpha = 0.45f) ?: LauncherPalette.GlassOutline,
                LauncherPalette.GlassOutlineSoft
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        ),
        shape = shape
    )
```

**Blur:** on Android 12+ (`Build.VERSION.SDK_INT >= 31`), apply `Modifier.graphicsLayer { renderEffect = BlurEffect(24f, 24f, TileMode.Clamp) }` to the ambient blob layer only (not to the glass cards themselves — blurring behind cards is deferred to v0.2 to avoid perf regression on older hardware). The "glass" look on cards in v0.1 is achieved via overlay alpha + gradient border, not real backdrop blur.

**Ambient blobs (L0):**

```kotlin
fun Modifier.ambientBlobs(): Modifier = drawBehind {
    drawCircle(
        color = LauncherPalette.BlobBlue,
        radius = 220.dp.toPx(),
        center = Offset(x = size.width * 0.15f, y = size.height * 0.12f),
        blendMode = BlendMode.Plus
    )
    drawCircle(
        color = LauncherPalette.BlobPink,
        radius = 180.dp.toPx(),
        center = Offset(x = size.width * 0.85f, y = size.height * 0.55f),
        blendMode = BlendMode.Plus
    )
}
```

(On API ≥31 the blob layer additionally gets `BlurEffect(55.dp, 55.dp)` for a real soft glow. On API <31 the raw alpha does the job — the blobs are already soft via low alpha.)

### 4.6 Motion

```kotlin
// ui/theme/Motion.kt
object Motion {
    // Durations (ms)
    const val DUR_INSTANT   = 0       // reduce-motion collapse
    const val DUR_MICRO     = 120     // button press, tick
    const val DUR_SHORT     = 200     // dismiss, scale
    const val DUR_MEDIUM    = 300     // hero crossfade, nav transition
    const val DUR_LONG      = 450     // feed reflow emphasis

    // Easings
    val Standard      : Easing = FastOutSlowInEasing   // default
    val Decelerate    : Easing = LinearOutSlowInEasing // enter / appear
    val Accelerate    : Easing = FastOutLinearInEasing // exit / disappear
    val Emphasized    : Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedIn  : Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedOut : Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // Named specs
    val CardEnter       = tween<Float>(DUR_MEDIUM, easing = EmphasizedIn)
    val CardExit        = tween<Float>(DUR_SHORT,  easing = EmphasizedOut)
    val HeroCrossfade   = tween<Float>(DUR_MEDIUM, easing = Standard)
    val ChipSelect      = spring<Float>(dampingRatio = 0.75f, stiffness = 400f)
    val FeedbackPulse   = keyframes<Float> {
        durationMillis = 260
        1.0f at 0
        1.08f at 90 with Decelerate
        0.98f at 180
        1.0f at 260
    }
    val DismissSwipe    = tween<Float>(DUR_SHORT, easing = Accelerate)
}
```

Reduce-motion check (FR-18):

```kotlin
@Composable
fun motionScale(): Float =
    Settings.Global.getFloat(
        LocalContext.current.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
    )
// If == 0f, substitute DUR_INSTANT for DUR_MEDIUM/LONG on hero crossfade and dismiss.
```

---

## 5. Component Specs

Components listed in home-screen order; each includes purpose, signature, visual spec tied to §4 tokens, state behavior, and `contentDescription` strings.

### 5.1 HeroForYouCard

**Purpose.** The single highest-confidence recommendation. Must feel like the centerpiece of the home screen. PRD FR-01/02.

```kotlin
@Composable
fun HeroForYouCard(
    card: Card,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    state: HeroState = HeroState.Loaded      // Loaded | Loading | Pulsing
)
```

**Visual spec.**

- Outer shape `LauncherShapes.large` (22dp)
- Surface: `Modifier.glassSurface(shape = LauncherShapes.large, elevated = true, accent = LauncherPalette.AccentBlue)`
- Min height `208.dp`; width `fillMaxWidth()`
- Internal padding `20.dp`
- Layout (`Column`, `spacedBy(12.dp)`):
  - Row 1: icon (44dp emoji in `Box` with 8dp corner glass fill)
  - Row 2: title (`typography.headlineLarge`)
  - Row 3: description (`bodyLarge`, `maxLines = 2`, `TextOverflow.Ellipsis`)
  - Row 4: whyLabel (`labelMedium`, prefixed `✧ `, color `AccentBlue` at 80% alpha)
  - Row 5: action row (`Row`, `SpaceBetween`):
    - Left: primary action `Button` (pill, `shape = sm`, `contentPadding 12dp × 20dp`, height 44dp, `colors.primary` bg, `labelLarge` on primary)
    - Right: `FeedbackButtonRow` (see 5.6)
  - Row 6: `ConfidenceBar` (see 5.5), bottom hairline

**States.**

| State | Visual delta |
|---|---|
| `Loaded` | As above |
| `Loading` | All text rows replaced by shimmer bars (§4.6 shimmer); action + feedback row disabled at 40% alpha |
| `Pulsing` | After 👍 where Hero did not change: apply `Motion.FeedbackPulse` to scale + 200ms tint of accent border → PositiveGreen → accent (see §9.3) |

**Content descriptions.**

| Element | cd |
|---|---|
| icon | `null` (decorative) |
| title | `null` (read as text) |
| thumbUp | `"喜欢这个推荐"` |
| thumbDown | `"不喜欢这个推荐"` |
| action button | actionLabel text itself |
| whyLabel | read as text (no extra cd); prefix "✧" is decorative |
| card root | merged semantics: `mergeDescendants = true`, `traversalIndex` on children per FR-18 |

### 5.2 IntentChip

**Purpose.** Declare active context (FR-03/04). Exactly 4 shown as a strip, 1 always selected.

```kotlin
@Composable
fun IntentChip(
    intent: Intent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun IntentChipStrip(
    selected: Intent,
    onSelect: (Intent) -> Unit,
    modifier: Modifier = Modifier
)
```

**Visual spec (IntentChip).**

- Shape: `LauncherShapes.small` (10dp)
- Height: `48.dp` (≥48 per FR-18) — visual height 40dp + `minimumInteractiveComponentSize` pad
- Width: content-hug; `horizontalPadding = 16.dp` when unselected, `12.dp` + 4dp check glyph when selected
- Unselected: `glassSurface(shape = sm, elevated = false)`, label `typography.titleMedium` in `TextPrimary`
- Selected: `background(colorScheme.primary)`, `border(1.dp, AccentBlue.copy(alpha=0.6f))`, leading `✓` glyph 16sp + 4dp gap + label in `colorScheme.onPrimary`
- Strip: `Row(horizontalArrangement = Arrangement.spacedBy(Space.sm))`; fills width; chips size-to-content, strip left-aligned. If sum exceeds width (at 200% font scale), wraps into `FlowRow`.

**States.**

| State | Visual |
|---|---|
| Unselected resting | Glass, no glyph |
| Unselected pressed | Fill 10% white flash (120ms) |
| Selected resting | AccentBlue fill + ✓ |
| Selected animate-in | Width grows 150ms (spring) to accommodate ✓ + crossfade text color |
| Disabled | N/A in v0.1 — always one is selected |

**contentDescription:** `"$label 模式${if (selected) "，已选中" else ""}"`. Uses `Modifier.semantics { selected = selected; role = Role.Tab }`.

### 5.3 DiscoverCard (+ NEW variant)

**Purpose.** Secondary recommendations under the Hero, always 3 in v0.1 (positions 2..4 of `recommend`). FR-05/06/07/08.

```kotlin
@Composable
fun DiscoverCard(
    card: Card,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onVisibilityChange: (fraction: Float) -> Unit = {}   // for dwell tracking
)
```

**Visual spec.**

- Shape `LauncherShapes.medium` (16dp)
- Surface `glassSurface(shape = md, elevated = false, accent = null)`
- Min height `160.dp`; width `fillMaxWidth()`
- Internal padding `16.dp`
- Layout (`Row` top-aligned):
  - Left column (`weight = 1f`):
    - icon (32dp emoji)
    - title `headlineMedium` with optional trailing `NewPill` if `card.type == NEW`
    - description `bodyMedium`, `maxLines = 2`, ellipsis
    - action button: pill, 40dp tall, `shape = sm`, outlined with AccentBlue 60%, label in AccentBlue
  - Right column (48.dp wide, top aligned):
    - ✕ `IconButton`, 48dp touch target, 20sp glyph, color `TextMuted`

**NEW variant.** Applied when `card.type == CardType.NEW`:

- A small `NewPill` to the right of title: `"NEW"` `labelSmall`, letter-spacing 0.6sp, `colorScheme.secondary` (AccentPink) foreground on `secondaryContainer`, `shape = sm`, height 18dp, horizontal pad 6dp.
- Left edge accent strip: 2dp wide `AccentPink` bar inside the card, full height, 8dp from left edge. This doubles the "explore" signal for non-color cues.

**Dwell tracking hook.** Card wires `Modifier.onGloballyPositioned { coords -> ... }` throttled to 100ms (FR-07.1) and reports a visibility fraction 0..1 to the parent, which owns the accrual timer. The card itself holds no dwell state (FR-11 purity principle extended).

**States.**

| State | Visual |
|---|---|
| Resting | As above |
| Pressed (action) | 98% scale 120ms (spring) |
| Dismiss-in-progress | 200ms translateX +40dp + alpha 1→0 (see §9.4) |
| Reflowing | `Modifier.animateItemPlacement(tween(250, Standard))` on LazyColumn item |
| Loading placeholder | Skeleton: icon=16dp circle, title=60% width bar, desc=90% width bar, action=80×32 bar. Shimmer 1200ms loop using `LinearGradient` sweep. |

**Content descriptions.**

| Element | cd |
|---|---|
| card root | `"${title}，${description}"` merged; children mergeDescendants |
| action btn | actionLabel |
| dismiss btn | `"关闭此卡片"` |
| NEW pill | `"新推荐"` |

### 5.4 AiDock

**Purpose.** Persistent bottom affordance. v0.1 = drawer launcher + single "coming soon" pill (FR-15).

```kotlin
@Composable
fun AiDock(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Visual spec.**

- Shape `LauncherShapes.medium` (16dp), capsule feel via 16dp radius on a 72dp-tall bar
- `glassSurface(shape = md, elevated = true)` + `graphicsLayer { shadowElevation = 8.dp.toPx() }`
- Internal layout: `Row(verticalAlignment = CenterVertically, horizontalArrangement = SpaceBetween)`, padding `12.dp × 16.dp`
  - Left: drawer `IconButton`, 48dp, icon "dots/grid" (`Icons.Rounded.Apps`), color `TextPrimary`
  - Center: pill `"✦ AI · 即将上线"`, `labelLarge`, `colorScheme.onSurfaceVariant`, inside a `Box` with `glassSurface(shape = sm)`, vertical padding 8dp, horizontal 16dp, **no ripple, no click handler** — purely `Text` inside a non-clickable `Box`
  - Right: 48dp spacer (for visual balance with drawer button), `Modifier.size(48.dp)`, not clickable

**States:** single state only. Never disabled — the drawer button is always tappable.

**Content descriptions.**

| Element | cd |
|---|---|
| drawer btn | `"打开应用抽屉"` |
| pill | `"AI 助理即将上线"` (informational, no role) |

### 5.5 ConfidenceBar

**Purpose.** Visual tie between Hero and the flywheel; conveys "AI is X% sure." A presentation-only component — the numeric value is the *score* normalized against the top score in the current intent, not a real confidence.

```kotlin
@Composable
fun ConfidenceBar(
    fraction: Float,    // 0f..1f, already normalized by ViewModel
    modifier: Modifier = Modifier
)
```

**Visual spec.**

- Height `4.dp`
- Shape `RoundedCornerShape(2.dp)`
- Track: `LauncherPalette.GlassFillSoft`
- Filled: linear gradient `AccentBlue → AccentPink`, width = `fraction * parentWidth`
- Animates fraction changes via `animateFloatAsState(targetValue = fraction, animationSpec = Motion.HeroCrossfade)`
- `fraction.coerceIn(0.08f, 1f)` so it never looks empty

**States.**

| State | Visual |
|---|---|
| Loaded | Animated to target |
| Loading | Indeterminate: 30%-width gradient segment sweeps left→right in 1400ms loop |
| Reduce-motion | Solid fill to target, no animation |

**contentDescription:** `null` — purely decorative; value duplicated by card copy.

### 5.6 FeedbackButtonRow

**Purpose.** Dedicated 👍 / 👎 control pair used on the Hero card. FR-02.

```kotlin
@Composable
fun FeedbackButtonRow(
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    modifier: Modifier = Modifier,
    thumbUpActive: Boolean = false,   // transient visual after tap
    thumbDownActive: Boolean = false
)
```

**Visual spec.**

- `Row(horizontalArrangement = Arrangement.spacedBy(Space.sm))`
- Each button: `IconButton` inside a `Box` with:
  - Size 48dp × 48dp (touch), inner icon 22sp
  - Shape `LauncherShapes.small`
  - Resting bg `GlassFillSoft`, border 1dp `GlassOutline`
  - ThumbUp pressed: bg → PositiveGreen 24% alpha, icon → PositiveGreen; scale 1.08 then 1.0 (Motion.FeedbackPulse)
  - ThumbDown pressed: bg → DangerRed 24% alpha, icon → DangerRed; same scale curve
- Debounce handled by ViewModel (FR-02), but the visual "active" flag flashes for 600ms after tap regardless of whether the event was coalesced.

**States.** resting / pressed / active (post-tap 600ms glow) / disabled-by-Room-unhealthy (40% alpha, no ripple, tap shows Snackbar per FR-09).

**Content descriptions.** `"喜欢"` / `"不喜欢"`.

### 5.7 AppIconTile

**Purpose.** Single app entry in the drawer grid. FR-13.

```kotlin
@Composable
fun AppIconTile(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Visual spec.**

- Column, width `72.dp`, total height `96.dp`
- Icon: 56dp `Image` in a `Box(Modifier.size(56.dp).clip(LauncherShapes.medium))`
  - Sourced from `app.icon` (Drawable → `rememberAsyncImagePainter` or converted to `ImageBitmap`)
- Spacer 6dp
- Label: `labelMedium` / `TextPrimary`, `maxLines = 1`, `ellipsis`, `textAlign = Center`, full-width
- Touch target: whole tile; `Modifier.clip(LauncherShapes.medium).clickable { onClick() }`
- Pressed: `scale 0.96` spring (stiffness 500)

**States.** resting / pressed / disabled (40% alpha — tile visible but launch intent null would lead to Snackbar; we still render the tile).

**contentDescription:** `"${app.label}"` (the label itself is sufficient).

### 5.8 ProfileDebugRow

**Purpose.** Display one tag + its weight inside ProfileDebugSheet. FR-12.

```kotlin
@Composable
fun ProfileDebugRow(
    tag: String,
    weight: Float,
    normalizedFraction: Float,   // |weight| / maxAbsWeight in list
    modifier: Modifier = Modifier
)
```

**Visual spec.**

- Full-width `Row`, height 32dp
- Left: tag label `labelLarge`, `width = 96.dp`, max 1 line ellipsis
- Center: weight bar, `height = 8.dp`, `weight(1f)` in row, shape `RoundedCornerShape(4.dp)`:
  - Track: `GlassFillSoft`
  - Fill: `fraction * trackWidth` wide, color **AccentBlue** if weight ≥ 0, **AccentPink** if weight < 0.
  - When weight < 0: fill is right-anchored (`Alignment.End`) so the "cross zero" visual reads like a mirrored bar around the track's center; a 1dp zero tick line is drawn at 50% for clarity.
- Right: weight value `labelMedium`, `width = 56.dp`, `textAlign = End`, formatted `"%+.2f".format(weight)` — sign always visible.
- Between-row vertical gap 12dp (owned by parent).

**States.**

| State | Visual |
|---|---|
| Loaded | As above |
| Loading | Shimmer on label + bar, value `—` |

**contentDescription:** `"${tag}，权重 ${"%+.2f".format(weight)}"`.

---

## 6. Accessibility

Binding requirements from FR-18:

### 6.1 Touch targets

- All interactive elements ≥ `48.dp × 48.dp`. For visually smaller controls (e.g. 32dp dismiss ✕ glyph), wrap in `IconButton` (default 48dp) or apply `Modifier.minimumInteractiveComponentSize()` explicitly.
- Chip strip horizontal gap ≥ 8dp (we use 12dp).

### 6.2 Content descriptions

Every icon-only interactive element has a non-null `contentDescription`; every decorative icon has `contentDescription = null` **explicitly** (never by default).

| Component | element | cd |
|---|---|---|
| HeroForYouCard | 👍 | `"喜欢这个推荐"` |
| HeroForYouCard | 👎 | `"不喜欢这个推荐"` |
| HeroForYouCard | emoji icon | `null` |
| DiscoverCard | ✕ | `"关闭此卡片"` |
| DiscoverCard | emoji | `null` |
| DiscoverCard | NEW pill | `"新推荐"` |
| IntentChip | whole chip | `"$label 模式${selected?"，已选中":""}"` |
| AiDock | drawer btn | `"打开应用抽屉"` |
| AiDock | AI pill | `"AI 助理即将上线"` |
| Header | profile badge | `"查看画像 Profile v47"` |
| Header | greeting text | read as text (no cd) |
| Banner | ✕ | `"关闭此提示"` |
| Banner | row | `"设为默认启动器，点击前往设置"` |
| ProfileSheet | drag handle | `null` (decorative; sheet semantics handle dismiss) |
| ProfileSheet | 重置画像 | `"重置画像"` |
| AppIconTile | root | label text suffices |

### 6.3 WCAG AA contrast

All combinations in §4.1 table pass AA (≥4.5:1 body, ≥3:1 large). On glassmorphic surfaces, text always sits above either the `GlassFillStrong` (8% white) layer on Hero/Sheet or is backed by the card's internal gradient — we do not rely on the ambient blobs behind for contrast.

### 6.4 TalkBack traversal

Hero card order (FR-18): `title → description → whyLabel → 👍 → 👎`. Implemented by composable declaration order and `Modifier.semantics { traversalIndex = N }` on action buttons (index 4, 5 respectively), the action button skipped in traversal by `clearAndSetSemantics {}` **disabled** — we want it reachable but after feedback; give it `traversalIndex = 6`.

Discover card order: `title → description → action btn → ✕`.

Home screen outer order: `header → banner? → hero → chip strip (one tab-group) → feed cards (top to bottom) → dock (drawer btn, then pill)`.

### 6.5 Font scaling

- All text uses `sp`.
- Usable at 130% scale: manual QA.
- At 200% scale: Hero title becomes 2-line; description 3-line; action button wraps below feedback row (column instead of row) — implement via `BoxWithConstraints` inside Hero: if `LocalDensity.fontScale > 1.7f`, switch action row to column.

### 6.6 Reduce motion

```kotlin
val animationsEnabled = Settings.Global
    .getFloat(ctx.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) != 0f

val heroCrossfadeSpec = if (animationsEnabled) Motion.HeroCrossfade else snap()
val dismissSpec       = if (animationsEnabled) Motion.DismissSwipe   else snap()
```

All other animations (chip-select spring, feedback pulse, press scale) still run — they are not the *only* feedback signal (FR-18).

### 6.7 Non-color indicators

- Active IntentChip: color fill **+** leading `✓` glyph (FR-03).
- Active intent in header text string (FR-04): `工作模式 · 14:22`.
- NEW card: accent pink edge bar **+** `"NEW"` pill text.
- ConfidenceBar direction: always left-to-right fill; polarity conveyed by `ProfileDebugRow` anchoring + explicit `+ / −` sign in weight value.

---

## 7. Empty & Error States

Maps PRD FR-19 rows to concrete components. Every string in `strings.xml`.

### 7.1 Cold-start, empty profile

Handled by normal render path — no special UI. Hero = `work_01`, feed = `[work_02, work_03, work_04]`. The FR-17 default-launcher banner may be visible. No error surfaces.

### 7.2 Room unhealthy

- Same composition as steady state but sourced from in-memory `SEED_CARDS`.
- Feedback tap → Snackbar: `"存储暂时不可用，反馈未保存"` (zh) / `"Storage unavailable — feedback not saved"` (en, for strings.xml).
- Visual hint: a subtle `⚠` glyph in the header near the profile badge (`16sp`, `DangerRed` at 70%), content description `"存储不可用，部分功能受限"`.
- Profile badge still tappable but sheet shows empty state §7.3.

### 7.3 Profile sheet, no data

```
┌──────────────────────────────┐
│   Profile v47                │
│   累计反馈: 0                 │
│   ─────────────────────────  │
│                              │
│         🌱                   │
│   还没有画像数据              │
│   给卡片一点反馈吧            │
│                              │
└──────────────────────────────┘
```

Reset button hidden when count == 0.

### 7.4 All cards dismissed for intent

Glass card replacing Hero slot, feed area empty:

```
┌──────────────────────────────────────────┐
│                                          │
│                🌱                         │
│                                          │
│       已看完本模式推荐                      │
│   试试其他模式，或点击下方恢复               │
│                                          │
│       ┌────────────────┐                  │
│       │  恢复本模式    │                    │
│       └────────────────┘                  │
│                                          │
└──────────────────────────────────────────┘
```

- Size: fills Hero slot (`min 208dp`).
- Tag: `LauncherShapes.large`, `glassSurface(elevated = true, accent = AccentPink)`.
- Button: outlined pill, tap → `HomeViewModel.restoreDismissedForActiveIntent()` → feed re-renders.

### 7.5 Intent has zero cards (defensive, should not occur in v0.1)

Same UI as §7.4, defensive branch — if seed parser ever produces fewer than 1 card for an intent, same empty state renders with the text `"该模式暂无推荐"` and the 恢复本模式 button hidden.

### 7.6 App drawer: 0 launchable apps

```
┌──────────────────────────────┐
│                              │
│           🫙                  │
│    没有可启动的应用            │
│                              │
└──────────────────────────────┘
```

Centered in AppDrawerScreen content area; glass card 240dp tall, width `wrapContentWidth() + 32dp horizontal pad`.

### 7.7 Default launcher revoked

`DefaultLauncherBanner` re-appears on next `onResume`. Component:

```kotlin
@Composable
fun DefaultLauncherBanner(
    onSetDefault: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
)
```

Visual: full-width glass row, 52dp tall, `shape = md`, leading `🏠` emoji 20sp, text `"设为默认启动器"` `labelLarge`, trailing `✕` 48dp IconButton. Tap on row body → `onSetDefault`.

---

## 8. Copywriting Table (zh + en)

Seed strings from PRD-v2 §9 used **verbatim** as specified by FR-16 (only `id`, `intent`, `type`, `tags` are strictly load-bearing; text is here for parity and i18n prep — strings.xml keys are authoritative). English column is **reference only** for v0.2 i18n; v0.1 ships zh only.

### 8.1 UI chrome strings

| Key | zh (ship) | en (reference) |
|---|---|---|
| `app_name` | AI 启动器 | AI Launcher |
| `greeting_kai` | 早安，Kai | Good morning, Kai |
| `header_commute` | 通勤模式 · %1$s | Commute · %1$s |
| `header_work` | 工作模式 · %1$s | Work · %1$s |
| `header_lunch` | 午餐时间 · %1$s | Lunch · %1$s |
| `header_rest` | 休息时间 · %1$s | Rest · %1$s |
| `chip_commute` | 通勤 | Commute |
| `chip_work` | 工作 | Work |
| `chip_lunch` | 午餐 | Lunch |
| `chip_rest` | 休息 | Rest |
| `ai_pill` | ✦ AI · 即将上线 | ✦ AI · Coming soon |
| `banner_default` | 设为默认启动器 | Set as default launcher |
| `profile_title` | Profile v47 | Profile v47 |
| `profile_counter` | 累计反馈: %1$d | Total feedback: %1$d |
| `profile_section_top_tags` | Top Tags | Top Tags |
| `profile_reset_cta` | 重置画像 | Reset profile |
| `profile_reset_dialog_title` | 确定清空所有画像数据？ | Clear all profile data? |
| `profile_reset_dialog_body` | 此操作无法撤销。 | This cannot be undone. |
| `profile_reset_dialog_confirm` | 清空 | Clear |
| `profile_reset_dialog_cancel` | 取消 | Cancel |
| `profile_empty` | 还没有画像数据 · 给卡片一点反馈吧 | No profile data yet — try some feedback |
| `drawer_header` | 应用 · %1$d | Apps · %1$d |
| `drawer_empty` | 没有可启动的应用 | No launchable apps |
| `drawer_cannot_open` | 无法打开此应用 | Cannot open this app |
| `action_snackbar` | %1$s — 即将上线 | %1$s — coming soon |
| `feedback_snackbar_offline` | 存储暂时不可用，反馈未保存 | Storage unavailable — feedback not saved |
| `empty_intent_title` | 已看完本模式推荐 | All caught up for this mode |
| `empty_intent_body` | 试试其他模式，或点击下方恢复 | Try another mode, or restore below |
| `empty_intent_cta` | 恢复本模式 | Restore this mode |

### 8.2 Accessibility strings

| Key | zh | en |
|---|---|---|
| `cd_thumb_up` | 喜欢这个推荐 | Like this recommendation |
| `cd_thumb_down` | 不喜欢这个推荐 | Dislike this recommendation |
| `cd_dismiss_card` | 关闭此卡片 | Dismiss this card |
| `cd_dismiss_banner` | 关闭此提示 | Dismiss this prompt |
| `cd_open_drawer` | 打开应用抽屉 | Open app drawer |
| `cd_profile_badge` | 查看画像 Profile v47 | View profile |
| `cd_ai_pill` | AI 助理即将上线 | AI assistant coming soon |
| `cd_new_pill` | 新推荐 | New recommendation |
| `cd_chip_selected` | %1$s 模式，已选中 | %1$s mode, selected |
| `cd_chip_unselected` | %1$s 模式 | %1$s mode |

### 8.3 Seed-card strings (verbatim from PRD-v2 §9)

| id | title (zh) | description (zh) | whyLabel (zh) | actionLabel (zh) |
|---|---|---|---|---|
| `commute_01` | 继续听《三体》 | 上次停在第 14 章，还剩 23 分钟 | 示例：你在听的有声书 | 继续播放 |
| `commute_02` | 常用通勤路线 | 示例路线预计 18 分钟 | 示例：你的常用路线 | 查看路线 |
| `commute_03` | 新闻早报 · 3 分钟 | 科技、财经、本地头条速览 | 示例：早间高频选择 | 开始播报 |
| `commute_04` | 试听《硅谷早知道》 | 科技播客，看看你是否感兴趣 | ε-greedy 探索 · 匹配科技偏好 | 试听 |
| `work_01` | 继续 Figma 稿件 | 示例文件 "Launcher v2 - Home" | 示例：最近编辑的设计稿 | 打开 Figma |
| `work_02` | 开启专注模式 45 min | 屏蔽通知、白噪音、番茄钟 | 示例：工作日常用 | 开始专注 |
| `work_03` | 今日日程概览 | 示例：3 个会议、2 个待办 | 工作模式常看 | 查看日程 |
| `work_04` | 试试 Linear 建单 | 轻量级项目管理，给你看看 | ε-greedy 探索 · 项目管理类 | 了解一下 |
| `lunch_01` | 瑞幸生椰拿铁 | 15 元券可用 | 示例：高频选择 | 一键下单 |
| `lunch_02` | 附近餐厅 · 20% off | 步行约 4 分钟，人均 58 元 | 午餐时间 · 附近优惠 | 查看菜单 |
| `lunch_03` | 叫个外卖 | 示例：常点的 3 家都在营业 | 午餐时间高频选择 | 打开美团 |
| `lunch_04` | 今日轻食套餐 | 鸡胸肉 + 藜麦碗 | ε-greedy 探索 · 健康选项 | 看看 |
| `rest_01` | 继续《漫长的季节》E08 | 上次停在 18:23，剩 42 分钟 | 示例：最近在追的剧 | 继续看 |
| `rest_02` | 放首《夜的第七章》 | 周杰伦 | 示例：休息时常听 | 播放 |
| `rest_03` | 10 分钟身体扫描冥想 | 睡前放松 | 示例：冥想类高频选择 | 开始 |
| `rest_04` | 试试《塞尔达》王国之泪 | 开放世界解谜游戏 | ε-greedy 探索 · 解谜偏好 | 了解 |

---

## 9. Animation Choreography

All timings pull from `Motion` tokens (§4.6). Reduce-motion collapses DUR_MEDIUM/LONG to 0 per §6.6.

### 9.1 Hero card enter / crossfade on intent switch or feedback flip

**Trigger:** `HomeUiState.hero.id` changes.

**Spec:** crossfade with subtle Y-translate for "pull to top" feel.

```kotlin
AnimatedContent(
    targetState = hero,
    transitionSpec = {
        (fadeIn(Motion.HeroCrossfade) +
         slideInVertically(Motion.HeroCrossfade) { it / 12 }
        ) togetherWith (
         fadeOut(tween(Motion.DUR_SHORT, easing = Motion.Accelerate)) +
         slideOutVertically(tween(Motion.DUR_SHORT)) { -it / 12 }
        ) using SizeTransform(clip = false)
    },
    label = "hero"
) { card -> HeroForYouCard(card, ...) }
```

Durations: exit 200ms accelerate, enter 300ms emphasized-in, overlap 100ms. ConfidenceBar `fraction` animates separately via `animateFloatAsState(spec = Motion.HeroCrossfade)`.

### 9.2 Intent chip select + feed cross-fade

**Trigger:** `selected: Intent` changes in `IntentChipStrip`.

**Chip:**

- Old selected chip: fill color → `GlassFillSoft` via `animateColorAsState(tween(180, Standard))`; ✓ glyph fades + slides left 8dp over 140ms then removed.
- New selected chip: fill → `primary` via `animateColorAsState(Motion.ChipSelect)` (spring); ✓ glyph fades in + slides in from left 8dp over 160ms (Decelerate).
- Width: `animateDpAsState` on content width (spring stiffness 400) to absorb the 4dp + glyph width addition.

**Feed:**

- LazyColumn contents crossfade: wrap `items(...)` body with `AnimatedContent(targetState = feed, transitionSpec = { fadeIn(tween(DUR_MEDIUM)) togetherWith fadeOut(tween(DUR_SHORT)) })` at the column level, or better, key the LazyColumn by `intent` to force recomposition and use `Modifier.animateItemPlacement(tween(300, Standard))`.
- Hero participates via §9.1 concurrently.
- Total observable swap duration: 300ms.

### 9.3 Feedback tap (👍 / 👎) scale + color

**Trigger:** tap on FeedbackButtonRow button.

**Spec:**

```kotlin
val scale by animateFloatAsState(
    targetValue = if (pressed) 1.08f else 1.0f,
    animationSpec = Motion.FeedbackPulse,
    label = "feedback_scale"
)
val tint by animateColorAsState(
    targetValue = when {
        active && signal == THUMB_UP   -> LauncherPalette.PositiveGreen
        active && signal == THUMB_DOWN -> LauncherPalette.DangerRed
        else                            -> LauncherPalette.TextPrimary
    },
    animationSpec = tween(180, easing = Motion.Standard),
    label = "feedback_tint"
)
val bgTint by animateColorAsState(
    targetValue = when {
        active && signal == THUMB_UP   -> LauncherPalette.PositiveGreen.copy(alpha = 0.24f)
        active && signal == THUMB_DOWN -> LauncherPalette.DangerRed.copy(alpha = 0.24f)
        else                            -> LauncherPalette.GlassFillSoft
    },
    animationSpec = tween(180),
    label = "feedback_bg"
)
```

`active` auto-clears 600ms after last tap (via `LaunchedEffect(pressCount) { delay(600); active = false }`). Pulse keyframes (260ms total) are one-shot.

**Hero-unchanged pulse (§2.2 "confirm" branch):** same pulse applied to the Hero card's border via `animateColorAsState` target = `PositiveGreen` for 200ms then back to accent.

### 9.4 Dismiss swipe / tap (✕)

**Trigger:** tap ✕ on DiscoverCard. (v0.1 is tap-only; true swipe is v0.2.)

**Spec:**

```kotlin
val dismissed by remember(card.id) { mutableStateOf(false) }
val translateX by animateDpAsState(
    targetValue = if (dismissed) 40.dp else 0.dp,
    animationSpec = Motion.DismissSwipe,
    label = "dismiss_tx"
)
val alpha by animateFloatAsState(
    targetValue = if (dismissed) 0f else 1f,
    animationSpec = Motion.DismissSwipe,
    label = "dismiss_alpha"
)
// Modifier
Modifier
    .graphicsLayer {
        translationX = translateX.toPx()
        this.alpha = alpha
    }
```

On animation end (`alpha == 0f`), the ViewModel removes the card from `feed`; LazyColumn `animateItemPlacement(tween(250))` handles reflow.

Reduce-motion: `dismissed` triggers instant state (alpha = 0f, translate = 0dp via `snap()` spec); ViewModel removes immediately; no reflow animation.

### 9.5 Bonus — ProfileDebugSheet open/close

M3 default `ModalBottomSheet` slide-in handles this. We add:

- scrim fade 250ms via default.
- `ProfileDebugRow` entries stagger-fade-in on open: each row delayed `30ms * index`, 180ms fade+translateY 8dp → 0dp.

---

## Appendix — Token → component map

Quick lookup for the dev agent.

| Component | Shape | Typography | Colors touched | Motion |
|---|---|---|---|---|
| HeroForYouCard | lg (22) | headlineLarge, bodyLarge, labelLarge, labelMedium | primary, GlassFillStrong, GlassOutline, PositiveGreen, DangerRed | HeroCrossfade, FeedbackPulse |
| IntentChip | sm (10) | titleMedium | primary, onPrimary, GlassFillSoft | ChipSelect |
| DiscoverCard | md (16) | headlineMedium, bodyMedium, labelSmall | GlassFillSoft, GlassOutline, AccentPink (NEW), primary | DismissSwipe, animateItemPlacement |
| AiDock | md (16) | labelLarge | GlassFillStrong, onSurfaceVariant | — |
| ConfidenceBar | 2dp corner | — | AccentBlue, AccentPink, GlassFillSoft | HeroCrossfade |
| FeedbackButtonRow | sm (10) | — | PositiveGreen, DangerRed, GlassFillSoft, GlassOutline | FeedbackPulse |
| AppIconTile | md (16) | labelMedium | TextPrimary, GlassFillSoft | spring scale |
| ProfileDebugRow | 4dp corner (bar) | labelLarge, labelMedium | AccentBlue, AccentPink, GlassFillSoft | stagger fade |
| DefaultLauncherBanner | md (16) | labelLarge | GlassFillSoft, AccentBlue | — |

---

## Appendix — Decisions taken in this doc not explicitly in PRD-v2

These are implementation choices made by Design to fill gaps between "behavior spec" (PRD) and "code spec" (this doc). Listed for PM sign-off:

1. **ConfidenceBar existence.** PRD does not mandate a confidence indicator; Design adds a 4dp gradient bar at the bottom of the Hero card as a passive visual cue tying the card to the flywheel. Value is engine-score-normalized, never shown as text.
2. **NEW-card left edge accent stripe.** PRD says NEW cards are tagged — Design adds a 2dp pink edge bar *in addition to* the textual `NEW` pill, for non-color redundancy (FR-18 non-color indicators principle applied to card type, not just chip selection).
3. **Header greeting literal "早安，Kai".** PRD does not specify greeting copy; Design picks a fixed string (not time-of-day variant, not user-input) to satisfy PRD-v2 §9 copy-voice rule ("never time-dependent / fabricated-behavioral").
4. **ProfileBadge visual (40dp circle with `P·` glyph).** PRD only names "profile badge" — Design fully specifies the glyph, size, and content description.
5. **AiDock right-side 48dp spacer.** Added purely for visual centering of the coming-soon pill; not functional.
6. **Reduce-motion substitution uses `snap()`** (not `tween(0)`) to guarantee a frame-level instant transition rather than a 1-frame tween.
7. **Ambient blob real blur only on API ≥ 31.** PRD says "blur" without API floor; Design gates the `BlurEffect` to avoid perf risk on older devices (minSdk 26).
8. **Dwell tracking ownership.** PRD leaves "where the timer lives" open; Design locates the *accrual timer* in `HomeViewModel` and the *visibility fraction emitter* in `DiscoverCard` — card reports, VM accumulates. Matches FR-11's purity principle.

*End of DESIGN v1.*
