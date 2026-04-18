# DESIGN v1 — AI Launcher MVP (v0.1)

**Status:** Implementation-grade · **Owner:** UI/UX · **Last updated:** 2026-04-18
**Companion docs:** `AGENTS.md` (invariants), `docs/PRD-v2.md` (requirements)
**Visual direction:** 方案 D · Dark glassmorphic · Accent blue `#7CB4FF` + pink `#FF7EC8`

> **Reading rule for the dev agent.** This spec, PRD-v2, and AGENTS.md form the complete implementation brief. Every color, spacing, radius, duration, and Compose signature below is intended to be transcribable without creative decisions. If a value you need is missing, treat it as a bug in this doc and file an issue rather than inventing one.

---

## 1. Information Architecture

### 1.1 Screen map

```
AI Launcher (single Activity: MainActivity)
│
├── HomeScreen            — NavHost start destination
│   ├── Header                         (greeting + intent string + profile badge)
│   ├── DefaultLauncherBanner (opt.)   (FR-17, resume-check conditional)
│   ├── HeroForYouCard                 (FR-01, FR-02)
│   ├── IntentChipsStrip               (FR-03, FR-04)
│   ├── DiscoverFeed (LazyColumn)      (FR-05, FR-06, FR-07)
│   └── AiDock                         (FR-15)
│
├── AppDrawerScreen       — navigated from AiDock drawer button
│   ├── TopBar (back glyph + title "应用")
│   └── AppGrid (4-column LazyVerticalGrid, alphabetical)
│
└── Overlays (not screens — ModalBottomSheet / Dialog / Snackbar)
    ├── ProfileDebugSheet  — opened from Header profile badge   (FR-12)
    ├── ResetConfirmDialog — opened from ProfileDebugSheet      (FR-12)
    ├── Snackbar host      — at root, anchored above AiDock     (FR-08, FR-19)
    └── DevPanelSheet      — debug only, long-press profile badge (FR-20)
```

### 1.2 Navigation graph

Compose Navigation (`androidx.navigation:navigation-compose`). Two routes only:

```
startDestination = "home"

"home"   ──[click AiDock drawer button]──►  "drawer"
"drawer" ──[back gesture / system back]──►  "home"   (preserves intent, dismiss list, LazyListState)
```

Overlays are composed inside `"home"` and controlled by boolean state in `HomeViewModel`. They are **not** navigation destinations — this keeps back-stack semantics for FR-14 trivial (pop drawer → home canonical state).

Home-button canonical state handled in `MainActivity.onNewIntent` per FR-14 (dismiss sheets → pop to "home" → scroll feed to top → preserve intent + dismiss list).

---

## 2. User Flows

### 2.1 Cold boot → Home

```
[ APK install / launcher chooser ]
            │
            ▼
 MainActivity.onCreate
            │
            ▼
 HomeViewModel.init {
     intent      = WORK                (FR-03 cold-start default)
     dismissList = emptySet()
     profile     = load()              (empty on first run)
     feed        = engine.recommend(WORK, 4)
 }
            │ ≤ 500 ms (NFR-01)
            ▼
  HomeScreen renders
  ┌──────────────────────────────────┐
  │ Header: "工作模式 · 14:22"  [v47]│
  │ (optional) FR-17 default banner  │
  │ HeroForYouCard (work_01)         │ ← cold-start = first seed of intent
  │ IntentChips [通勤 ✓工作 午餐 休息]│   (exact: cold-start default is WORK with ✓)
  │ Discover: work_02, work_03, work_04 (last = exploration slot) │
  │ AiDock: [📱 drawer]   ✦ AI · 即将上线  │
  └──────────────────────────────────┘
```

### 2.2 Feedback on Hero card (👍/👎) — optimistic UI + revert on error

```
User taps 👍 on Hero
     │
     ▼
FeedbackButtonRow onThumbUp()
     │   ┌── same-card 400ms debounce gate (FR-02) ──┐
     ▼   ▼                                            │
HomeViewModel.onThumbUp(cardId)                       │
     │                                                │
     ├─► [OPTIMISTIC] button scales 0.92× (120ms)    │
     │   → fills with #7CB4FF (success pulse 240ms) │
     │   → heroState.pendingSignal = THUMB_UP       │
     │                                                │
     ├─► launch coroutine:                            │
     │       FeedbackRepository.insert(event)         │
     │       ProfileRepository.applyDelta(card.tags,  │
     │                                   +3/N)        │
     │       engine.recommend(intent, 4) → newFeed    │
     │       ◄─ success ─►                            │
     │                                                │
     │   ┌─── if top card changed ───┐                │
     │   │ Hero crossfade 300ms      │                │
     │   │ newHero.enter (reveal)    │                │
     │   └───────────────────────────┘                │
     │   ┌─── if top card unchanged ─┐                │
     │   │ Hero ambient-pulse 2000ms │                │
     │   └───────────────────────────┘                │
     │                                                │
     └─► [ERROR PATH] repo returns failure (FR-19     │
         Room-unavailable) or coroutine throws:       │
         • revert heroState.pendingSignal = null      │
         • button un-fills (240ms ease)               │
         • Snackbar "存储暂时不可用，反馈未保存"    ─┘
```

👎 follows the identical flow, with +3 replaced by −3 and the success fill color `#FF5A7A`. Debounce window is per `(cardId, signal)` — a 👍 followed by 👎 within 400ms is **not** debounced (they are distinct signals, per FR-02 accumulate rule).

### 2.3 Intent chip switch

```
User taps an unselected chip (e.g. 午餐)
     │
     ▼
IntentChipsStrip onSelect(LUNCH)
     │
     ▼
HomeViewModel.setIntent(LUNCH)
     │
     ├─► update header string "午餐时间 · HH:mm"
     ├─► chip ✓ glyph + fill animates from old→new (240ms crossfade, staggered 40ms)
     ├─► dwell timers on old feed CANCELLED (any <500ms appearances discarded — FR-04)
     │
     ├─► feed = engine.recommend(LUNCH, 4)
     │
     ▼
Discover feed cross-fades (240ms)
     • old cards fade-out 0→160ms (alpha 1→0)
     • new cards fade-in 80→240ms (alpha 0→1, translateY +8dp → 0)
     • LazyListState.scrollToItem(0)
     │
     ▼
Hero re-renders (always swap — FR-04) with 300ms reveal
```

### 2.4 Dismiss a Discover card (✕)

```
User taps ✕ on Discover card (≥48dp hitbox)
     │
     ▼ onDismiss(cardId)
     │
     ├─► [OPTIMISTIC] card starts dismiss animation immediately
     │      • translateX 0 → +48dp (120ms)
     │      • alpha 1 → 0 (200ms total)
     │      • height collapses (200ms, FastOutLinearIn)
     │
     ├─► HomeViewModel:
     │      dismissList[intent] += cardId
     │      FeedbackRepository.insert(DISMISS, -2)
     │      feed = engine.recommend(intent, 4) filtered
     │
     ├─► LazyColumn re-lays out remaining cards (animateItemPlacement, 240ms)
     │
     └─► if dismissList[intent].size == 4 → FR-19 empty state
              "🌱  已看完本模式推荐" + [恢复本模式] button
```

On Room failure: revert — card re-appears at its slot (reverse animation 200ms) + Snackbar `"存储暂时不可用，反馈未保存"`. The in-memory session hide is also rolled back so the user can try again.

### 2.5 Open Profile v47 sheet

```
User taps profile badge in Header (40dp visible, 48dp hit-slop)
     │
     ▼
HomeViewModel.openProfileSheet()
     │
     ▼
ModalBottomSheet (Material 3) rises
     • scrim fades to rgba(10,15,30,0.72) over 240ms
     • sheet translateY from 100% → 0 (300ms, FastOutSlowIn)
     • dwell timers PAUSE (FR-07 rule 5)
     │
     ▼
Sheet content:
  ┌────────────────────────────────┐
  │ Handle bar (32×4dp, 40% white) │
  │ "Profile v47"                   │
  │ "累计反馈: 47"                  │
  │ ─────────────────────────────── │
  │ Top 10 tags (sorted |weight| ↓) │
  │  coffee       +12.25            │
  │  design        +8.50            │
  │  …                              │
  │  lunch         +3.00            │
  │  healthy       −1.50            │
  │ ─────────────────────────────── │
  │ [ 重置画像 ] (danger-outline)   │
  └────────────────────────────────┘

Close paths:
  • swipe down                → sheet slides out (240ms), dwell resumes
  • tap scrim                 → same
  • back gesture              → same
  • tap 重置画像 → AlertDialog → Confirm → 4-step reset (FR-12) → auto-close
```

### 2.6 Swipe up → App Drawer → Return

> **Note for dev agent.** Swipe-up gesture is explicitly cut in v0.1 per PRD §7. The only path to the drawer is the **AiDock drawer button**. The flow title below refers to the user intent ("I want apps"), not the gesture.

```
User taps AiDock drawer button (48dp)
     │
     ▼
NavController.navigate("drawer")
     • shared-element-lite: drawer button scales up and morphs into
       drawer grid container (240ms reveal; actual impl = Crossfade
       between Home and Drawer composables, no transform required)
     • Home Composable leaves composition → dwell timers END (FR-07 rule 6)
     │
     ▼
AppDrawerScreen
  ┌─────────────────────────────────────┐
  │  ◄  应用                            │  ← TopBar (56dp)
  │                                     │
  │  [A] 支付宝   [B] 百度   [C] 抖音  │  ← 4-col grid
  │  [D] 高德地图 …                     │     each tile 72dp icon + label
  │                                     │
  │                                     │
  └─────────────────────────────────────┘

User taps back (gesture or system) OR presses Home:
     │
     ▼
NavController.popBackStack() → "home"
     • HomeScreen re-enters composition
     • intent, dismissList, LazyListState RESTORED from ViewModel
     • dwell timers re-arm when cards become visible again
```

---

## 3. Screen Wireframes

### 3.1 HomeScreen

#### Layout (top→bottom, all values in dp)

```
┌─ Box (fillMaxSize) ──────────────────────────────────────────┐
│  Background: BgGradient (radial ambient, see §4.5)           │
│                                                              │
│  Column(Modifier.safeDrawingPadding().padding(horiz=16)) {   │
│                                                              │
│    Spacer(12)                                                │
│    Header                                    height 56       │
│    Spacer(16)                                                │
│                                                              │
│    DefaultLauncherBanner (if visible)        height 52       │
│    Spacer(12) // only when banner shown                      │
│                                                              │
│    HeroForYouCard                            height ≈200     │
│    Spacer(20)                                                │
│                                                              │
│    IntentChipsStrip                          height 48       │
│    Spacer(16)                                                │
│                                                              │
│    DiscoverFeed (LazyColumn, weight=1f)      flex            │
│       itemSpacing 12                                         │
│       contentPadding bottom=96 (dock clearance)              │
│                                                              │
│  } // Column                                                 │
│                                                              │
│  AiDock (Align.BottomCenter)                 height 64       │
│    bottomPadding 16 + navBar insets                          │
│                                                              │
│  SnackbarHost (BottomCenter, above dock)     bottomPad 88    │
└──────────────────────────────────────────────────────────────┘
```

#### Component hierarchy tree

```
HomeScreen
├── Box                                 (Modifier.fillMaxSize().background(BgGradient))
│   ├── AmbientGradientLayer            (two radial lights, z-index 0)
│   ├── Column                          (main content)
│   │   ├── Header
│   │   │   ├── Column                  (greeting + intent string)
│   │   │   │   ├── Text "你好, Kai"     (TitleSmall)
│   │   │   │   └── Text "工作模式 · 14:22" (BodyLarge, primary accent)
│   │   │   └── ProfileBadge            (40dp circle, 48dp hit)
│   │   ├── DefaultLauncherBanner?      (if !isDefault)
│   │   ├── HeroForYouCard
│   │   │   ├── HeroGlassSurface        (Shape.lg, 22dp radius)
│   │   │   ├── Column(padding 20)
│   │   │   │   ├── Row  [icon · whyLabel · ConfidenceBar]
│   │   │   │   ├── Text title          (HeadlineSmall)
│   │   │   │   ├── Text description    (BodyMedium, secondary)
│   │   │   │   └── Row [PrimaryAction · FeedbackButtonRow]
│   │   ├── IntentChipsStrip
│   │   │   └── Row(spacedBy 8) { IntentChip × 4 }
│   │   └── DiscoverFeed (LazyColumn)
│   │       └── items(cards) { DiscoverCard }
│   │           or EmptyIntentCard (FR-19)
│   ├── AiDock                          (Align.BottomCenter)
│   └── SnackbarHost
└── Overlays
    ├── ProfileDebugSheet?
    ├── ResetConfirmDialog?
    └── DevPanelSheet? (DEBUG)
```

#### States

| State | Trigger | Visual |
|---|---|---|
| **Loaded** | Normal; ≥1 card available for intent | All modules render, Hero pulsing ambient once on cold start |
| **Loading** | First 0–500ms of cold start OR intent-switch gap | Hero/feed shows `ShimmerPlaceholder` (see §5.1 loading state) for at most 300ms; if data ready sooner, skip |
| **Empty (all dismissed)** | `dismissList[intent].size == 4` | Hero slot = `EmptyIntentCard` (🌱 + recover button); Discover area renders nothing |
| **Room-unavailable** | `repo.isHealthy == false` | Feed renders from in-memory `SEED_CARDS`; any feedback tap triggers Snackbar `"存储暂时不可用，反馈未保存"`; no visual degradation of cards themselves |
| **First-run** | Empty profile | Identical to Loaded — cold-start rule renders `work_01` as Hero; optional FR-17 banner |

#### Interaction targets (all ≥48dp hit per FR-18)

| Target | Visible size | Hit slop | Action |
|---|---|---|---|
| ProfileBadge | 40dp | 48dp | tap: open ProfileDebugSheet · long-press (DEBUG): DevPanelSheet |
| IntentChip | h 40dp | 48dp (via `minimumInteractiveComponentSize`) | tap: setIntent |
| Hero 👍 button | 44dp circle | 48dp | tap: THUMB_UP |
| Hero 👎 button | 44dp circle | 48dp | tap: THUMB_DOWN |
| Hero primary action | h 40dp | 48dp | tap: ACTION_TAP + Snackbar |
| Discover ✕ | 24dp glyph | 48dp | tap: dismiss |
| Discover card body | full | — | tap: ACTION_TAP |
| AiDock drawer | 40dp icon | 48dp | tap: navigate("drawer") |
| AiDock pill | h 40dp | none | non-interactive (FR-15) |
| Banner tap zone | full row | ≥48dp | tap: open HOME_SETTINGS |
| Banner ✕ | 24dp | 48dp | tap: dismiss banner this session |

Vertical scroll: LazyColumn only. No horizontal swipe on cards (swipe-to-dismiss deferred to v0.2; ✕ button is the path).

### 3.2 AppDrawerScreen

#### Layout

```
┌─ Box (fillMaxSize, background = BgGradient) ─────────────────┐
│                                                              │
│  TopAppBar (Material3, CenterAligned)         height 56      │
│    title: Text "应用" (TitleLarge)                           │
│    navigationIcon: IconButton(ArrowBack, cd="返回")          │
│    colors: transparent container, onSurface text             │
│                                                              │
│  LazyVerticalGrid(columns = Fixed(4))                        │
│    contentPadding(horizontal=16, top=8, bottom=24+navBar)    │
│    verticalArrangement spacedBy 16                           │
│    horizontalArrangement spacedBy 8                          │
│                                                              │
│    items(apps) { AppIconTile(app) }                          │
│                                                              │
│    OR if apps.isEmpty():                                     │
│      EmptyDrawerCard "没有可启动的应用" (glass, 22dp radius)│
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

#### Component hierarchy

```
AppDrawerScreen
├── Scaffold
│   ├── topBar = TopAppBar(title, navigationIcon, transparent)
│   └── content
│       └── LazyVerticalGrid
│           └── items(sortedApps) { AppIconTile }
```

#### States

| State | Visual |
|---|---|
| **Loaded** | Grid of AppIconTile, 4 columns |
| **Loading** | `CircularProgressIndicator` centered (only if PackageManager query >100ms; usually instant — skip) |
| **Empty** | Single glass card center: icon 📭 + text `"没有可启动的应用"` |
| **Error** | Same as empty — query never throws in practice; fallback defensive |

#### Interactions

| Target | Size | Action |
|---|---|---|
| AppIconTile | 72dp (icon 56 + label 16) | tap: launch app · long-press: (v0.2 — no-op v0.1) |
| Back nav icon | 40dp visible, 48dp hit | tap: popBackStack |

### 3.3 ProfileDebugSheet

#### Layout

`ModalBottomSheet` (Material 3), `SheetValue.Expanded` by default (not partially expanded — 60% height), scrim `rgba(10,15,30,0.72)` per NFR-11.

```
┌─ Sheet (Shape top-corners 22dp, surface = #161a2e) ──────────┐
│  DragHandle (32×4dp pill, color=onSurface 40%, top=12)       │
│                                                              │
│  Column(padding horizontal=24, top=8, bottom=32) {           │
│                                                              │
│    Text "Profile v47"                TitleLarge              │
│    Spacer(4)                                                 │
│    Text "累计反馈: N"               BodySmall, secondary    │
│    Spacer(20)                                                │
│                                                              │
│    // Top-tags list                                          │
│    Column(spacedBy 8) {                                      │
│        ProfileDebugRow × up to 10                            │
│    }                                                         │
│    Spacer(24)                                                │
│                                                              │
│    OutlinedButton "重置画像"  (danger tint, fullWidth 48dp) │
│  }                                                           │
└──────────────────────────────────────────────────────────────┘
```

#### States

| State | Visual |
|---|---|
| **Loaded** | Top 10 tags rendered |
| **Empty** | Tags list replaced by glass strip: `"还没有学到你的偏好，多用用就好～"` (BodyMedium, secondary) |
| **Loading** | Show 3 shimmer rows (shouldn't happen in practice; Room query <50ms) |
| **Reset-in-flight** | Reset button disabled + inline CircularProgressIndicator (20dp) replacing the label |

#### Interactions

| Target | Size | Action |
|---|---|---|
| DragHandle / scrim / back | — | close sheet |
| 重置画像 | 48dp h | tap: show ResetConfirmDialog |
| Each ProfileDebugRow | — | non-interactive in v0.1 |

---

## 4. Design System — Compose tokens

All tokens live in `ui/theme/`. The dev agent creates: `Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Motion.kt`, `Theme.kt` (which wires them into a custom `MaterialTheme` + a `LocalAppTokens` CompositionLocal for the non-Material3 extras: ambient gradient, spacing, motion).

### 4.1 Colors (exact hex values)

```kotlin
// ui/theme/Color.kt

// — Backgrounds —
val BgDeep              = Color(0xFF0A0F1E)   // gradient start (top-left)
val BgMid               = Color(0xFF10152A)
val BgShallow           = Color(0xFF161A2E)   // gradient end (bottom-right), also sheet/dialog surface

// — Ambient radial light tints (drawn at ~10% alpha) —
val AmbientBlue         = Color(0xFF3A6BFF)   // top-right glow
val AmbientPink         = Color(0xFFFF5CB8)   // bottom-left glow

// — Accents —
val AccentBlue          = Color(0xFF7CB4FF)   // primary
val AccentBlueSoft      = Color(0xFF5C8FE6)   // pressed/darker variant
val AccentPink          = Color(0xFFFF7EC8)   // secondary
val AccentPinkSoft      = Color(0xFFE06AAE)

// — Semantic —
val Positive            = Color(0xFF64E6A0)   // THUMB_UP fill + success
val PositiveSoft        = Color(0xFF3FB87A)
val Danger              = Color(0xFFFF5A7A)   // THUMB_DOWN fill + destructive
val DangerSoft          = Color(0xFFD94560)
val Warning             = Color(0xFFFFB84C)   // reserved (not used in v0.1 UI but defined)

// — Text —
val TextPrimary         = Color(0xFFF2F5FF)   // 95% pseudo-white, tinted cool
val TextSecondary       = Color(0xB3F2F5FF)   // 70% alpha of primary
val TextTertiary        = Color(0x80F2F5FF)   // 50% alpha — for meta labels
val TextDisabled        = Color(0x4DF2F5FF)   // 30% alpha

// — Glass surfaces (overlay alphas over the gradient bg) —
val GlassSurface        = Color(0x14FFFFFF)   //  8% white (card bg)
val GlassSurfaceHigh    = Color(0x1FFFFFFF)   // 12% white (Hero card, slightly lifted)
val GlassSurfaceLow     = Color(0x0AFFFFFF)   //  4% white (chip unselected)
val GlassBorder         = Color(0x29FFFFFF)   // 16% white (gradient border base)
val GlassBorderAccent   = Color(0x80FFFFFF)   // 50% white (hero gradient border top)

// — Scrim / overlays —
val ScrimSheet          = Color(0xB80A0F1E)   // rgba(10,15,30,0.72) — FR-12
val ScrimDialog         = Color(0x99000000)   // 60% black

// — Feedback button surfaces —
val ThumbUpBg           = Color(0x1A64E6A0)   // 10% Positive, default
val ThumbUpBgActive     = Color(0xFF64E6A0)   // full Positive, after tap
val ThumbDownBg         = Color(0x1AFF5A7A)
val ThumbDownBgActive   = Color(0xFFFF5A7A)
val DismissBg           = Color(0x1AFFFFFF)
val DismissFg           = TextSecondary
```

### 4.1.1 Material 3 ColorScheme mapping

```kotlin
darkColorScheme(
    primary            = AccentBlue,
    onPrimary          = BgDeep,
    primaryContainer   = Color(0x337CB4FF),    // 20% AccentBlue for selected chip fill
    onPrimaryContainer = TextPrimary,
    secondary          = AccentPink,
    onSecondary        = BgDeep,
    tertiary           = Positive,
    onTertiary         = BgDeep,
    error              = Danger,
    onError            = TextPrimary,
    background         = BgDeep,
    onBackground       = TextPrimary,
    surface            = BgShallow,            // sheets/dialogs
    onSurface          = TextPrimary,
    surfaceVariant     = GlassSurface,         // glass cards
    onSurfaceVariant   = TextSecondary,
    outline            = GlassBorder,
    outlineVariant     = Color(0x14FFFFFF),
    scrim              = ScrimSheet,
)
```

Non-scheme tokens (`Ambient*`, `Glass*`, `ThumbUp*`, etc.) are exposed via `LocalAppTokens` so components can reference them with `MaterialTheme` semantics.

### 4.1.2 WCAG contrast verification (dark bg)

| Pair | Ratio | Passes |
|---|---|---|
| TextPrimary on BgDeep            | 17.4 : 1 | AAA |
| TextPrimary on GlassSurface/BgDeep composite | 15.8 : 1 | AAA |
| TextSecondary on BgDeep          | 10.4 : 1 | AAA |
| TextTertiary on BgDeep           | 6.6 : 1  | AA (body), AAA (large) |
| AccentBlue on BgDeep             | 8.9 : 1  | AAA (large), AA (body) |
| Positive on BgDeep               | 11.5 : 1 | AAA |
| Danger on BgDeep                 | 5.2 : 1  | AA |
| TextPrimary on AccentBlue fill   | 1.95 : 1 | FAIL — **never place TextPrimary on AccentBlue fill**; use `BgDeep` (onPrimary) instead. Ratio BgDeep-on-AccentBlue = 8.9:1 ✓ |
| TextPrimary on Positive fill     | 2.1 : 1  | FAIL — use `BgDeep` (onTertiary) on Positive. ✓ |

Rule of thumb codified: **onPrimary / onTertiary = `BgDeep`**, never the other way around.

### 4.2 Typography

System font stack (no custom font in MVP): `FontFamily.Default` which on Android resolves to Roboto + Noto CJK for Chinese glyphs.

```kotlin
// ui/theme/Type.kt

val AppTypography = Typography(
    // display — reserved, unused in v0.1 home
    displayLarge   = TextStyle(fontSize = 40.sp, fontWeight = W600, lineHeight = 48.sp, letterSpacing = (-0.5).sp),

    // headline — Hero title
    headlineSmall  = TextStyle(fontSize = 22.sp, fontWeight = W600, lineHeight = 28.sp, letterSpacing = 0.sp),

    // title — Header greeting, Sheet title, Drawer top bar, Discover card title
    titleLarge     = TextStyle(fontSize = 20.sp, fontWeight = W600, lineHeight = 26.sp),
    titleMedium    = TextStyle(fontSize = 17.sp, fontWeight = W600, lineHeight = 22.sp),
    titleSmall     = TextStyle(fontSize = 15.sp, fontWeight = W500, lineHeight = 20.sp),

    // body — descriptions, tag rows, button labels
    bodyLarge      = TextStyle(fontSize = 15.sp, fontWeight = W400, lineHeight = 22.sp),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = W400, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontSize = 12.sp, fontWeight = W400, lineHeight = 16.sp),

    // label — chips, action buttons, feedback counters
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = W600, lineHeight = 18.sp),
    labelMedium    = TextStyle(fontSize = 12.sp, fontWeight = W600, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall     = TextStyle(fontSize = 11.sp, fontWeight = W500, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)
```

Named roles used in this doc:
- **display** → not used in v0.1
- **title** → `titleLarge`
- **headline** → `headlineSmall` (Hero only)
- **body** → `bodyMedium`
- **caption** → `bodySmall`
- **overline** → `labelSmall` (uppercase via `.toUpperCase()` in composition, e.g. "NEW" badge)

**Font scaling behavior (FR-18):** `TextStyle.lineHeight` is specified in `sp` so it scales with system font size. All text composables use default (unrestricted) `TextUnit` sizing. Hero card uses `Modifier.weight` in its row layout so the title can wrap to 3 lines at 200% scale without pushing the action offscreen. Action button uses `Modifier.widthIn(min = 88.dp)` so the label always has room.

### 4.3 Shape

```kotlin
// ui/theme/Shape.kt
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),      // badges, tiny chips
    small      = RoundedCornerShape(10.dp),     // buttons, small surfaces → "sm"
    medium     = RoundedCornerShape(16.dp),     // Discover card, chip, banner → "md"
    large      = RoundedCornerShape(22.dp),     // Hero card, bottom-sheet top corners → "lg"
    extraLarge = RoundedCornerShape(50.dp),     // phone-mockup outer → "xl" (reserved; not applied to any in-app surface v0.1)
)

val PillShape      = RoundedCornerShape(percent = 50)   // intent chip, AiDock pill, feedback button
val TopSheetShape  = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
```

The `xl = 50dp` token exists per the brief ("phone mockup feel"); dev agent should apply it to the outermost `Box` only when running in a preview-mockup configuration, not on the live device surface.

### 4.4 Spacing

```kotlin
// ui/theme/Spacing.kt
@Immutable data class Spacing(
    val xxs: Dp = 4.dp,
    val xs:  Dp = 8.dp,
    val sm:  Dp = 12.dp,
    val md:  Dp = 16.dp,
    val lg:  Dp = 20.dp,
    val xl:  Dp = 24.dp,
    val xxl: Dp = 32.dp,
)
val LocalSpacing = staticCompositionLocalOf { Spacing() }
```

Usage convention: screen-edge inset = `md (16)`; module-to-module vertical = `lg (20)`; internal card padding = `lg (20)`; card-to-card in feed = `sm (12)`.

### 4.5 Elevation & blur (glassmorphism spec)

Android Compose does not expose a cheap runtime backdrop-blur on SDK 26. We fake it with a two-layer composite. **No `Modifier.blur` on the glass surface itself** (poor perf SDK 26–30 and broken on some OEMs).

```kotlin
// Home screen background (once, in HomeScreen root Box):
Modifier
  .drawBehind {
      // Layer 1: linear gradient (bg base)
      drawRect(
          Brush.linearGradient(
              colors = listOf(BgDeep, BgMid, BgShallow),
              start = Offset.Zero,
              end = Offset(size.width, size.height)
          )
      )
      // Layer 2: two radial ambient lights at ~10% alpha
      drawCircle(
          brush = Brush.radialGradient(
              colors = listOf(AmbientBlue.copy(alpha = 0.22f), Color.Transparent),
              center = Offset(size.width * 0.85f, size.height * 0.12f),
              radius = size.minDimension * 0.55f
          ),
          blendMode = BlendMode.Plus
      )
      drawCircle(
          brush = Brush.radialGradient(
              colors = listOf(AmbientPink.copy(alpha = 0.18f), Color.Transparent),
              center = Offset(size.width * 0.10f, size.height * 0.88f),
              radius = size.minDimension * 0.50f
          ),
          blendMode = BlendMode.Plus
      )
  }
```

Glass cards layer over this with the following recipe (applied via helper `Modifier.glassSurface(variant: GlassVariant)`):

```kotlin
fun Modifier.glassSurface(
    shape: Shape = MaterialTheme.shapes.medium,
    variant: GlassVariant = GlassVariant.Standard,  // Standard | Hero | Low
    borderBrush: Brush? = defaultGlassBorderBrush,
): Modifier = this
    .clip(shape)
    .background(
        when (variant) {
            GlassVariant.Hero     -> GlassSurfaceHigh    // 12%
            GlassVariant.Standard -> GlassSurface        // 8%
            GlassVariant.Low      -> GlassSurfaceLow     // 4%
        }
    )
    .then(
        if (borderBrush != null)
            Modifier.border(width = 1.dp, brush = borderBrush, shape = shape)
        else Modifier
    )

private val defaultGlassBorderBrush: Brush
    @Composable get() = Brush.linearGradient(
        colors = listOf(
            GlassBorderAccent,       // 50% white top-left
            GlassBorder,             // 16% white mid
            Color.Transparent        // fade bottom-right
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )
```

Elevation (Material 3 tonal) is **set to 0dp on all glass surfaces** — the visual depth comes from the gradient border + ambient radial glow, not from a shadow. Sheets/dialogs use Material default tonal elevation (`level2 = 3dp`) for their surface but still over our themed surface color.

### 4.6 Motion

```kotlin
// ui/theme/Motion.kt
object Motion {
    // durations (ms)
    val Tap          = 120      // button scale on press
    val ChipSwitch   = 240      // intent crossfade
    val CardReveal   = 300      // hero enter / replace
    val DismissSlide = 200      // discover card dismiss
    val SheetRise    = 300      // bottom sheet
    val AmbientPulse = 2000     // hero "confirmed but unchanged" pulse
    val FeedXfade    = 240      // Discover feed swap on intent change

    // easings (use Compose AndroidX)
    val StandardEase     = FastOutSlowInEasing   // most UI
    val AccelEase        = FastOutLinearInEasing // exits (dismiss collapse)
    val DecelEase        = LinearOutSlowInEasing // enters
    val EmphasizedEase   = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) // hero reveal
}
```

Reduce-motion override (FR-18): when `LocalReduceMotion.current == true` (observes `Settings.Global.TRANSITION_ANIMATION_SCALE == 0f`):
- `CardReveal`, `DismissSlide`, `FeedXfade` → **0ms** (instant)
- `Tap`, `SheetRise` → kept (<=300ms, acceptable) but no scale/translate, alpha only

---

## 5. Component Specs

All components live in `ui/components/` unless noted. Every composable signature below is copy-paste ready.

### 5.1 HeroForYouCard

**Purpose.** The single highest-ranked card per FR-01/FR-02. Always present (even in empty/dismissed state, via `EmptyIntentCard` variant — separate composable).

**API**
```kotlin
@Composable
fun HeroForYouCard(
    card: Card,
    confidence: Float,               // 0f..1f, derived from score normalization (see §5.5)
    isPendingUp: Boolean = false,    // optimistic UI flag
    isPendingDown: Boolean = false,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual spec**
- Size: `fillMaxWidth().heightIn(min = 188.dp)` — auto-grows with content / font scale
- Shape: `MaterialTheme.shapes.large` (22dp)
- Surface: `glassSurface(variant = Hero)` → 12% white fill + gradient border
- Inner padding: `20.dp` all sides
- Layout (Column, verticalArrangement spacedBy 12):
  - Row 1 (h 20, horizontalArrangement SpaceBetween):
    - Row [Text(icon, emoji 24sp) · Spacer(8) · Text(whyLabel, labelSmall, TextTertiary, uppercase OFF — Chinese)]
    - ConfidenceBar(confidence, width = 56dp)
  - Text(card.title, headlineSmall, TextPrimary, maxLines = 2)
  - Text(card.description, bodyMedium, TextSecondary, maxLines = 2)
  - Row 2 (Spacer(weight=1f) pushes it to bottom on tall screens) [PrimaryActionButton · Spacer(weight=1f) · FeedbackButtonRow]

**States**
| State | Visual |
|---|---|
| default | glass 12%, border gradient visible, no glow |
| pressed (anywhere on action) | scale 0.98, 120ms FastOutSlowIn |
| pendingUp | 👍 button filled Positive, card emits ambient pulse (border alpha 0.5 → 0.9 → 0.5 over 2000ms once) |
| pendingDown | 👎 button filled Danger, card pulses once |
| revealing (intent-switch or crossfade swap) | enter with alpha 0→1 + translateY 12dp→0 over 300ms EmphasizedEase |
| loading (cold start shimmer, max 300ms) | same shape, surface = GlassSurfaceLow, shimmer gradient sweep left→right 1200ms loop |

**a11y**
- contentDescription on container: `"推荐卡片：${card.title}"`
- Traversal order (FR-18): title → description → whyLabel → 👍 → 👎 → primary action
- `semantics { heading() }` on title Text

### 5.2 IntentChip

**Purpose.** FR-03/FR-04 — 4 selectable chips.

**API**
```kotlin
@Composable
fun IntentChip(
    intent: Intent,
    label: String,           // "通勤"|"工作"|"午餐"|"休息"
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual spec**
- Size: `heightIn(min = 40.dp).widthIn(min = 72.dp)`, hit padding to 48dp via `Modifier.minimumInteractiveComponentSize()`
- Shape: `PillShape` (50%)
- Selected state:
  - fill = `MaterialTheme.colorScheme.primary` (AccentBlue)
  - text color = `onPrimary` (BgDeep)
  - leading ✓ glyph (Icons.Filled.Check, 16dp, onPrimary) with 4dp end spacing — **mandatory non-color cue per FR-03**
  - elevation 0dp; no shadow
- Unselected:
  - `glassSurface(variant = Low)` (4% white + gradient border)
  - text color = `TextSecondary`
  - no leading glyph
- Text: `labelLarge` (14sp W600)
- Horizontal padding: `16.dp` when unselected, `14.dp` when selected (accommodate ✓)
- Inter-chip spacing: `8.dp` (per FR-03)

**States**
| State | Visual |
|---|---|
| default (unselected) | glass low, border gradient |
| selected | AccentBlue fill + ✓ glyph |
| pressed | scale 0.96, 120ms |
| disabled | reserved — not used in v0.1 |

**a11y**
- `Modifier.semantics { selected = selected; role = Role.Tab }` — enables TalkBack "selected" announcement
- contentDescription: null (text label is self-describing; don't double-announce)

### 5.3 DiscoverCard (with NEW variant)

**Purpose.** FR-05/FR-06. Renders in Discover feed LazyColumn. Participates in dwell tracking (FR-07).

**API**
```kotlin
@Composable
fun DiscoverCard(
    card: Card,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
    onVisibilityChanged: (visibleFraction: Float) -> Unit = {},
    modifier: Modifier = Modifier,
)
```

The `onVisibilityChanged` callback is invoked from `Modifier.onGloballyPositioned` (throttled 100ms) with the visible-fraction of the card inside the parent LazyColumn viewport. The `HomeViewModel` bridges this to the FR-07 dwell timer using the injected `Clock`.

**Visual spec**
- Size: `fillMaxWidth().heightIn(min = 128.dp)`
- Shape: `MaterialTheme.shapes.medium` (16dp)
- Surface: `glassSurface(variant = Standard)` (8% white + gradient border)
- Inner padding: `16.dp`
- Layout (Row, spacedBy 12):
  - Column (icon block, width 56dp): Box(56×56) with `GlassSurfaceLow`, emoji centered (24sp), if `card.type == NEW` stack an overline badge at bottom-right: "NEW" pill (10sp W600, AccentPink fill, BgDeep text, 6dp radius, 4dp inset)
  - Column (weight=1f, spacedBy 4):
    - Row [Text(title, titleMedium) · Spacer(weight=1f) · DismissButton(onDismiss)]
    - Text(description, bodySmall, TextSecondary, maxLines = 2)
    - Spacer(8)
    - Row [Text(whyLabel, labelSmall, TextTertiary) · Spacer(weight=1f) · ActionTextButton(card.actionLabel, onPrimaryAction)]
- NEW variant override: border brush switches from default white-gradient to pink-tinted: `Brush.linearGradient(listOf(AccentPink.copy(alpha=0.6f), GlassBorder, Transparent))`
- CONTINUE variant: leading icon gets a subtle 1dp ring in `AccentBlue.copy(alpha=0.4f)` to telegraph "resume"
- DISCOVER variant: no additional treatment

**Dismiss button (nested component, same file)**
- 24dp glyph (Icons.Outlined.Close), 48dp hit via `Modifier.size(48.dp).clickable(...)` with a 12dp padding so the visible glyph centers
- Tint: `TextSecondary`, on press → `TextPrimary`
- contentDescription: `"关闭卡片"`

**States**
| State | Visual |
|---|---|
| default | glass standard |
| pressed (card body) | scale 0.98, 120ms |
| dismissing | translateX 0→+48dp + alpha 1→0 (200ms) + height → 0 (200ms AccelEase); LazyColumn animateItemPlacement shifts peers up |
| entering (intent switch) | alpha 0→1 + translateY +8dp → 0 over 240ms, staggered 40ms per card index |
| loading | skeleton: title bar 140×14 shimmer, desc bar 200×12 shimmer |

**a11y**
- Container `semantics { contentDescription = "${cardTypeLabel}：${card.title}. ${card.description}" }`
- Traversal: title → description → whyLabel → action → dismiss

### 5.4 AiDock

**Purpose.** FR-15 simplified dock. Drawer button + non-interactive pill.

**API**
```kotlin
@Composable
fun AiDock(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual spec**
- Size: `fillMaxWidth().height(64.dp)`, bottom padding = 16dp + navBar inset
- Shape: `MaterialTheme.shapes.large` (22dp), horizontal margin 16dp
- Surface: `glassSurface(variant = Hero)` (12% white + gradient border) — slightly heavier than cards to anchor the screen
- Inner padding: horizontal 12, vertical 12
- Layout (Row, verticalAlign = CenterVertically, spacedBy 12):
  - IconButton (drawer) — 40dp visible, 48dp hit, icon `Icons.Outlined.Apps` in `TextPrimary`
  - AiComingSoonPill (weight=1f) — non-interactive
  - Spacer(40dp) — visual symmetry balancer matching drawer icon

**AiComingSoonPill (nested)**
- Height 40dp, PillShape, bg `GlassSurfaceLow`, border `GlassBorder` 1dp
- Center row: Text("✦", 16sp, AccentBlue) · Spacer(6) · Text("AI · 即将上线", bodyMedium, TextSecondary)
- **No Modifier.clickable** — truly inert, no ripple, per FR-15

**States**
- default only; drawer IconButton gets standard ripple on press, pill has none.

**a11y**
- drawer: contentDescription = `"打开应用抽屉"` (FR-15 exact)
- pill: `semantics { contentDescription = "AI 功能即将上线"; disabled() }`

### 5.5 ConfidenceBar

**Purpose.** Visual hint that the Hero is the "highest-ranked" card. Decorative — ties to engine score, but purely informational.

**API**
```kotlin
@Composable
fun ConfidenceBar(
    confidence: Float,    // 0f..1f, clamped internally
    modifier: Modifier = Modifier,
)
```

Confidence derivation (computed in `HomeViewModel`, not in the composable):
```kotlin
val scores = engine.scoresFor(intent)   // list of card scores
val top = scores.max() ?: 0f
val min = scores.min() ?: 0f
val range = (top - min).takeIf { it > 0.01f } ?: 1f
val confidence = 0.35f + 0.65f * ((heroScore - min) / range)  // floor 0.35 so bar never looks empty
```

**Visual spec**
- Size: width configurable (default 56dp in Hero), height 4dp
- Shape: `RoundedCornerShape(2.dp)`
- Track bg: `GlassSurfaceLow`
- Fill: horizontal gradient `AccentBlue → AccentPink`, width = `width * confidence`
- A subtle 1px top highlight at `Color.White.copy(alpha=0.2f)` for a "glass rod" look (optional, via Canvas drawLine)

**States**
- default; no interactive states.

**a11y**
- `semantics { contentDescription = "推荐置信度 ${(confidence*100).toInt()}%" }`

### 5.6 FeedbackButtonRow

**Purpose.** 👍 / 👎 pair on the Hero.

**API**
```kotlin
@Composable
fun FeedbackButtonRow(
    isPendingUp: Boolean,
    isPendingDown: Boolean,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual spec**
- Row with `horizontalArrangement = spacedBy(12.dp)`
- Each FeedbackButton:
  - Size: 44dp visible circle, 48dp hit
  - Shape: `CircleShape`
  - Default bg: `ThumbUpBg` (10% Positive) for 👍, `ThumbDownBg` for 👎
  - Active bg (post-tap, pending): full `ThumbUpBgActive` / `ThumbDownBgActive`
  - Border: 1dp `GlassBorder` default → transparent when active
  - Icon: Unicode glyph "👍" / "👎" at 20sp, OR Material icon `Icons.Rounded.ThumbUp` / `ThumbDown` at 22dp — dev's choice, spec allows either (emoji preferred for the glass aesthetic; if ColorEmoji rendering is inconsistent on target devices, fall back to vector icon tinted `Positive`/`Danger`)

**States**
| State | Visual |
|---|---|
| default | 10% tint bg, 1dp border, icon at 100% color |
| pressed | scale 0.92 (120ms FastOutSlowIn), bg brightens by +4% alpha |
| pending (active) | full-color fill, icon tints `BgDeep`, scale 1.06 held for 240ms then back to 1.0 |
| disabled | N/A v0.1 |

**a11y**
- 👍 contentDescription: `"赞，更多这类推荐"`
- 👎 contentDescription: `"踩，少推荐这类"`
- `semantics { role = Role.Button }` on each

### 5.7 AppIconTile

**Purpose.** Grid tile in AppDrawerScreen (FR-13).

**API**
```kotlin
@Composable
fun AppIconTile(
    app: AppInfo,           // { packageName, label, icon: Drawable }
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Visual spec**
- Total tile: width = (screenWidth - 16*2 - 8*3) / 4, height ≈ 88dp
- Layout (Column, spacedBy 6, horizontalAlign CenterHorizontally):
  - Box(56.dp, `glassSurface(variant = Low)`, shape = `RoundedCornerShape(14.dp)`): icon rendered via `Image(rememberDrawablePainter(app.icon))`, 40dp, center
  - Text(app.label, labelSmall, TextSecondary, maxLines = 1, ellipsis = End, textAlign = Center)
- Tile itself clickable with ripple bounded by the 56dp icon box

**States**
| State | Visual |
|---|---|
| default | glass low tile |
| pressed | icon box scales 0.94 (120ms) |
| focused (TalkBack / keyboard) | 2dp border in `AccentBlue` |

**a11y**
- contentDescription: `"打开 ${app.label}"` on the clickable container; the inner `Image` sets `contentDescription = null`

### 5.8 ProfileDebugRow

**Purpose.** Single row in ProfileDebugSheet showing a tag weight.

**API**
```kotlin
@Composable
fun ProfileDebugRow(
    tag: String,
    weight: Float,
    rank: Int,                      // 1..10 for left gutter
    modifier: Modifier = Modifier,
)
```

**Visual spec**
- Height 44dp, horizontal padding 12, `glassSurface(variant = Low)`, shape medium
- Row layout (spacedBy 12):
  - Text("${rank}", labelMedium, TextTertiary, width 20dp)
  - Text(tag, bodyMedium, TextPrimary, weight = 1f)
  - WeightPill (small pill with signed value)

**WeightPill (nested)**
- Height 24dp, horizontal padding 10, `PillShape`
- bg: if weight > 0 → `Positive.copy(alpha = min(0.35f, abs(weight)/10f))`; if < 0 → `Danger.copy(alpha = ...)`; if 0 → `GlassSurfaceLow`
- Text: `labelMedium`, color `TextPrimary`, format `"%+.2f".format(weight)` — always shows sign (e.g. `+0.75`, `−1.50`, `+0.00`)

**States**
- default only (non-interactive v0.1)

**a11y**
- `semantics { contentDescription = "第 ${rank} 位：${tag}，权重 ${"%+.2f".format(weight)}" }`

---

## 6. Accessibility (FR-18 expansion)

1. **Touch targets.** Every interactive element uses `Modifier.minimumInteractiveComponentSize()` (default 48dp) or explicit size ≥48dp. Visible glyph may be smaller (e.g. 24dp ✕) with padding bringing hit area to 48dp.
2. **contentDescription rules.**
   - Icon-only buttons: MUST set a non-null, user-readable Chinese string (see §8 copy table).
   - Decorative icons inside labeled containers: set `contentDescription = null` explicitly to avoid TalkBack double-read.
   - Emoji used as icons are *in addition to* the contentDescription, not a substitute.
3. **Color contrast.** Verified in §4.1.2. All body text on BgDeep ≥10:1; all body text on glass composite ≥7:1. Accent fills never carry light text — `onPrimary = BgDeep` is the governing rule.
4. **Font scaling.** Verified manually at 100% / 130% / 200% per FR-18. Hero Title `maxLines=2` + action button `widthIn(min=88.dp)` keeps critical actions visible at 200%.
5. **Reduce motion.** `LocalReduceMotion` flag (provided by `ReduceMotionProvider` in MainActivity reading `Settings.Global.TRANSITION_ANIMATION_SCALE`) collapses CardReveal, DismissSlide, FeedXfade to 0ms.
6. **TalkBack traversal.** Hero card custom order: title → description → whyLabel → 👍 → 👎 → action — implemented via composable source order (which matches) **plus** `Modifier.semantics { traversalIndex = n.toFloat() }` defensively on 👍 (3), 👎 (4), action (5).
7. **Selected state.** Intent chips use `Modifier.semantics { selected = true/false; role = Role.Tab }` AND have a non-color (✓ glyph) cue per FR-03.
8. **Focus visuals.** For physical keyboard / switch access (defensive, not release-gating): 2dp AccentBlue border on any focusable surface via `Modifier.focusable()` + `Modifier.onFocusChanged`. Default Compose focus ring is acceptable where applied.

---

## 7. Empty / Error States

| State | Composable | Visual Spec |
|---|---|---|
| **First-run (no signals)** | HomeScreen default | No special UI — FR-11 cold-start renders `work_01` as Hero. Optional FR-17 banner above Hero if not default launcher. |
| **Room unavailable** | HomeScreen, degraded mode | Cards render from in-memory `SEED_CARDS`. Tapping 👍/👎/✕/action triggers `Snackbar("存储暂时不可用，反馈未保存")`. No error banner or modal — silent-but-helpful. |
| **Discover feed empty (all dismissed)** | `EmptyIntentCard` (replaces Hero area) | Glass card, Shape.large, padding 24dp, centered Column: emoji `🌱` at 40sp → Spacer(12) → Text `"已看完本模式推荐"` (titleMedium, TextPrimary) → Spacer(8) → Text `"试试其他模式，或点击下方恢复"` (bodyMedium, TextSecondary) → Spacer(16) → Button "恢复本模式" (Filled, AccentBlue, height 48dp, full width). Discover feed area below is not rendered. |
| **App drawer empty** | `EmptyDrawerCard` | Centered in drawer content. Glass card (Shape.medium, padding 24dp): emoji `📭` → Text `"没有可启动的应用"` (titleMedium). No action button. |
| **Profile sheet empty (no tags)** | Sheet, tags list area | Replace `Column { ProfileDebugRow × N }` with a `glassSurface(Low)` strip: Text `"还没有学到你的偏好，多用用就好～"` (bodyMedium, TextSecondary, padding 20dp). 重置画像 button stays enabled. |
| **Banner** (FR-17) | `DefaultLauncherBanner` | Full-width glass card height 52dp, Shape.medium. Row: Icon(Home, AccentBlue, 20dp) · Text `"设为默认启动器"` (titleSmall) · Spacer(weight=1f) · IconButton(Close, TextTertiary, contentDescription `"关闭"`). Whole row clickable. |

All empty-state strings are sourced from `strings.xml` (FR-19 mandate) even though localization is Chinese-only for v0.1.

---

## 8. Copywriting (strings table)

String IDs and values. These go into `app/src/main/res/values/strings.xml` (Chinese, the default locale for v0.1) and `app/src/main/res/values-en/strings.xml` (English, for future use + dev-agent preview clarity). Only Chinese ships in v0.1.

| id | zh-Hans (ships) | en (reference) | Used in |
|---|---|---|---|
| `app_name` | AI 启动器 | AI Launcher | launcher label |
| `home_greeting` | 你好, Kai | Hello, Kai | Header — name is seeded constant in v0.1 |
| `header_commute` | 通勤模式 · %1$s | Commute · %1$s | Header intent string (FR-04) |
| `header_work` | 工作模式 · %1$s | Work · %1$s | — |
| `header_lunch` | 午餐时间 · %1$s | Lunch · %1$s | — |
| `header_rest` | 休息时间 · %1$s | Rest · %1$s | — |
| `intent_commute` | 通勤 | Commute | IntentChip |
| `intent_work` | 工作 | Work | — |
| `intent_lunch` | 午餐 | Lunch | — |
| `intent_rest` | 休息 | Rest | — |
| `dock_drawer_cd` | 打开应用抽屉 | Open app drawer | AiDock drawer IconButton cd (FR-15) |
| `dock_pill` | ✦ AI · 即将上线 | ✦ AI · coming soon | AiDock pill (FR-15) |
| `dock_pill_cd` | AI 功能即将上线 | AI features coming soon | Pill semantics |
| `feedback_up_cd` | 赞，更多这类推荐 | Thumbs up — more like this | 👍 cd |
| `feedback_down_cd` | 踩，少推荐这类 | Thumbs down — less like this | 👎 cd |
| `card_dismiss_cd` | 关闭卡片 | Dismiss card | ✕ cd |
| `card_new_badge` | NEW | NEW | NEW variant overline |
| `hero_card_cd` | 推荐卡片：%1$s | Recommended: %1$s | Hero container cd |
| `discover_card_cd` | %1$s：%2$s。%3$s | %1$s: %2$s. %3$s | Discover container cd (type : title . description) |
| `card_type_continue` | 继续 | Continue | card type label |
| `card_type_discover` | 推荐 | Discover | — |
| `card_type_new` | 新发现 | New | — |
| `action_snackbar` | %1$s — 即将上线 | %1$s — coming soon | FR-08 Snackbar literal |
| `error_storage_snackbar` | 存储暂时不可用，反馈未保存 | Storage unavailable — feedback not saved | FR-19 Room-unavailable Snackbar |
| `error_cannot_open_app` | 无法打开此应用 | Can't open this app | FR-13 Snackbar |
| `banner_default_launcher` | 设为默认启动器 | Set as default launcher | FR-17 banner |
| `banner_dismiss_cd` | 关闭 | Dismiss | banner ✕ cd |
| `drawer_title` | 应用 | Apps | AppDrawerScreen top bar |
| `drawer_back_cd` | 返回 | Back | back icon cd |
| `drawer_empty` | 没有可启动的应用 | No launchable apps | FR-19 |
| `app_tile_cd` | 打开 %1$s | Open %1$s | AppIconTile cd |
| `profile_title` | Profile v47 | Profile v47 | ProfileDebugSheet title (literal per FR-12) |
| `profile_counter` | 累计反馈: %1$d | Total feedback: %1$d | counter |
| `profile_empty` | 还没有学到你的偏好，多用用就好～ | Haven't learned your preferences yet — keep using the app :) | empty tags state |
| `profile_reset_button` | 重置画像 | Reset profile | button label (FR-12) |
| `profile_reset_title` | 确定清空所有画像数据？ | Clear all profile data? | AlertDialog title |
| `profile_reset_body` | 此操作无法撤销。 | This cannot be undone. | AlertDialog body |
| `profile_reset_confirm` | 确定 | Confirm | dialog confirm |
| `profile_reset_cancel` | 取消 | Cancel | dialog cancel |
| `profile_row_cd` | 第 %1$d 位：%2$s，权重 %3$s | Rank %1$d: %2$s, weight %3$s | ProfileDebugRow cd |
| `empty_intent_title` | 已看完本模式推荐 | All caught up for this mode | FR-19 empty intent (LUNCH dismiss-all) |
| `empty_intent_body` | 试试其他模式，或点击下方恢复 | Try another mode, or tap below to restore | — |
| `empty_intent_restore` | 恢复本模式 | Restore this mode | button — clears dismiss list |
| `dev_panel_title` | 开发工具 | Dev tools | DevPanelSheet (DEBUG only) |
| `dev_seed_profile` | 灌入演示画像 | Seed demo profile | dev button (FR-20) |
| `dev_dump_profile` | 导出画像到 logcat | Dump profile to logcat | — |
| `dev_clear_dismiss` | 清空本模式隐藏 | Clear dismiss list | — |

**Seed card copy** — ships verbatim from PRD-v2 §9. The dev agent should NOT duplicate the full table here; `SeedCards.kt` is the source of truth for `title`, `description`, `actionLabel`, `icon`, `whyLabel`. Line-level edits allowed per FR-16.

**Confidence bar a11y string:** `"推荐置信度 %1$d%%"` (not in table above since it's formatted inline; add when implementing strings.xml).

---

## 9. Animation choreography

### 9.1 Card enter (Hero cold-start, and intent-switch Hero replace)

```
t=0       alpha=0.0, translateY=+12dp
t=60ms    — still 0, start curve
t=120ms   alpha=0.4, translateY=+7dp
t=200ms   alpha=0.8, translateY=+2dp
t=300ms   alpha=1.0, translateY=0     ← settled
```
Easing: `EmphasizedEase` (`CubicBezier(0.2, 0.0, 0.0, 1.0)`). Duration: `Motion.CardReveal = 300ms`.

Compose impl sketch:
```kotlin
AnimatedContent(
    targetState = heroState,
    transitionSpec = {
        (fadeIn(tween(Motion.CardReveal, easing = Motion.EmphasizedEase)) +
         slideInVertically(tween(Motion.CardReveal, easing = Motion.EmphasizedEase)) { it / 4 })
            .togetherWith(fadeOut(tween(160)))
    }, label = "hero"
) { state -> HeroForYouCard(state.card, ...) }
```

### 9.2 Intent switch (cross-fade feed)

```
Header string    : fade swap         0→140ms (alpha only)
Chip fill        : AnimateColorAsState AccentBlue↔glass, 240ms, StandardEase
Chip ✓ glyph     : scale 0→1 over 240ms DecelEase (new), scale 1→0 over 160ms AccelEase (old)
Hero card        : AnimatedContent swap, 300ms (§9.1)
Discover feed    : Crossfade wrapper OR each item key-ed to (intent,cardId)
                   with LazyColumn.animateItemPlacement(240ms) and
                   enter = fadeIn+slideInVertically(12dp)
                   exit  = fadeOut(160ms)
Stagger          : +40ms per Discover card index so the feed "ripples"
Scroll           : LazyListState.scrollToItem(0) immediately (no animated scroll —
                   the crossfade hides the jump)
```

Total wall-clock: ~360ms from tap to settled feed.

### 9.3 Feedback tap (scale + color)

```
t=0      touchDown: button.scale 1.0 → 0.92 (120ms, FastOutSlowIn)
t=120    touchUp  : scale 0.92 → 1.06 (120ms DecelEase, spring-like overshoot)
t=240    scale 1.06 → 1.0 (120ms StandardEase)
                   AND bg color animates default → active
                   via animateColorAsState(240ms)
t=240+   if card stays same: ambient border pulse 2000ms once
         if card changes  : hero crossfade per §9.1 starts at t=240
```

Reduce-motion: scale anim skipped entirely; color snap at t=0; ambient pulse skipped.

### 9.4 Dismiss swipe (tap-driven; no actual swipe gesture in v0.1)

```
Tap ✕:
t=0       ✕ icon scale 1.0 → 0.85 (80ms)
t=0       card begins exit:
            translateX 0 → +48dp  (200ms AccelEase)
            alpha      1 → 0      (200ms linear)
            height     measured → 0 (200ms AccelEase, via animateItemPlacement +
                                     Modifier.animateContentSize)
t=120     LazyColumn peers start sliding up (animateItemPlacement, 240ms StandardEase)
t=200     card fully gone
t=200+    if dismissList.size == 4 → EmptyIntentCard reveals (§9.1 card-enter curve)
```

Error revert (Room unavailable after optimistic dismiss):
```
t=0   optimistic dismiss started (as above)
t=~50 repo reports failure
t=50  cancel exit animation, reverse:
        translateX +48dp → 0 (200ms DecelEase)
        alpha         0 → 1 (160ms)
        height      0 → measured (200ms)
      show Snackbar "存储暂时不可用，反馈未保存"
```

### 9.5 Ambient pulse (Hero, "confirmed but unchanged")

Used when 👍/👎 was recorded but the engine's top pick didn't change.

```
Border alpha: 0.5 → 0.9 → 0.5 over 2000ms (infinite-repeatable but fired ONCE per feedback)
             CubicBezier(0.4, 0.0, 0.6, 1.0)  — symmetric, "breathing"
Glass fill : unchanged
Text       : unchanged
```

Impl: `rememberInfiniteTransition` gated by a `LaunchedEffect(feedbackId) { delay(2000); active=false }`.

### 9.6 Sheet rise (ProfileDebugSheet)

```
Scrim alpha : 0 → 0.72 over 240ms (linear)
Sheet Y     : 100% → 0 over 300ms (FastOutSlowIn)
Sheet alpha : 0 → 1 over 160ms
Dwell timers: PAUSED at t=0 (not at t=300)
```

Close: reverse with `240ms` total, dwell resumes at t=240.

---

## Appendix A · Design decisions I had to make (not explicit in PRD-v2)

The PRD fully specified copy, flow, and FR-level behavior, but left visual/component-level details open. The following choices were made in this doc:

1. **ConfidenceBar exists.** Not mentioned in PRD-v2 but the brief lists it as a required component. Derivation formula chosen (§5.5) to keep it non-hallucinatory: it shows the Hero's position within the engine's scored range, not a fabricated "% confidence."
2. **Glass surface recipe without real blur.** SDK 26 can't cheaply run `Modifier.blur` so the glassmorphism is faked with stacked alpha + gradient border + ambient radials. PRD mentions "blur" but does not require a kernel size; this spec commits to no-blur for perf parity.
3. **Feedback button = emoji with Material-icon fallback.** PRD says "👍/👎"; emoji rendering on some OEMs (Xiaomi, pre-Noto builds) is inconsistent. Spec authorizes either emoji or `Icons.Rounded.ThumbUp` tinted to accent.
4. **Corner radius scale.** PRD says glassmorphic cards but didn't fix radii. Set 10/16/22/50dp per the brief. The `xl = 50dp` "phone mockup" token is present but not applied to in-app surfaces.
5. **Confidence/ambient-pulse colors.** Chose `AccentBlue → AccentPink` gradient for the ConfidenceBar fill to reinforce the dual-accent identity without inventing a new color.
6. **Header greeting string.** "你好, Kai" — PRD persona is named Kai but doesn't specify a greeting. Seeded as a constant; trivial to parameterize later.
7. **Banner height 52dp.** PRD says "minimal, dismissible banner"; chose the single-line 52dp treatment over a two-line 72dp to minimize vertical competition with Hero.
8. **Snackbar anchoring at `bottomPad 88dp`** so it never collides with the 64dp dock + 16dp margin.
9. **Sheet expands to 60%/full.** PRD did not specify partial-vs-full; chose fully expanded for accessibility (no drag-to-read behavior needed).

None of these contradict PRD-v2; they fill in visual/spatial gaps that the PRD intentionally left to design.

---

*End of DESIGN v1.*
