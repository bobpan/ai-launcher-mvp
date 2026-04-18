# PRD v2 — AI Launcher MVP (v0.1)

**Status:** Approved for build · **Owner:** PM · **Last updated:** 2026-04-18
**Target release:** v0.1 (MVP, single build)
**Supersedes:** `docs/PRD-v1.md` (v1 + `docs/PRD-v1-critique.md` merged into this document)
**Companion docs:** `AGENTS.md` (invariants), `docs/DESIGN-v1.md` (visual spec), `docs/ARCHITECTURE-v1.md` (tech decisions)

> **Reading rule for the Phase 3 dev agent:** this document + `AGENTS.md` + `docs/DESIGN-v1.md` are the *complete* implementation spec. Do not consult PRD-v1 or the v1 critique — they are historical. Every requirement below is intended to be unambiguously implementable; if you find an ambiguity, treat it as a bug in this PRD and file an issue rather than guessing.

---

## 1. Product Overview

**Elevator pitch.** AI Launcher is an Android home screen that replaces the grid-of-icons metaphor with a **personalized AI feed**. Instead of hunting through app drawers, the user lands on a "FOR YOU" hero card — the one thing the AI believes they want to do *right now* — followed by a TikTok-style Discover feed tuned to their current intent (通勤 / 工作 / 午餐 / 休息). Every 👍, 👎, ✕, tap, and dwell feeds a local user profile that sharpens subsequent recommendations. The launcher gets smarter the more you use it, and — critically — it shows you *exactly* what it has learned, editable in a transparent "Profile v47" sheet.

**Who is this for (persona).**
> **"Kai" — 28, product designer in Shanghai. Android power user. Has 140+ apps installed, uses ~12 regularly. Already tried Niagara, Nothing Launcher, and Smart Launcher but abandoned them because "they're still just grids with extra steps." Cares about aesthetics (glassmorphic, dark mode), cares about privacy (doesn't want another cloud account), and is AI-curious but skeptical of black-box recommendations. Wants a phone that feels like a *concierge*, not a *filing cabinet*.**

Secondary persona: Android developers and prosumers who want to dogfood an AI-native launcher and are comfortable side-loading an APK.

**Scope-down from v1.** The v1 promise "noticeably better within a week" overstated what a 16-card catalog can demonstrate. v2 reframes the pitch as **"visibly different within a single 10-minute demo session"** and backs it with a deterministic test hook (see FR-11, FR-20, SM-07). The week-over-week learning story returns in v0.2 with an expanded catalog.

---

## 2. Problem Statement

From Kai's point of view, traditional launchers fail in four ways:

1. **Grid-of-icons is a filing system, not a decision system.** The launcher knows nothing about context. At 8:47 AM it shows the same 5×6 grid as at 11:47 PM. The user does all the cognitive work of deciding "what do I want to do?"
2. **Shortcuts don't scale.** Folders, hotseats, and "smart suggestions" rows in Pixel Launcher top out at ~6 slots and require manual curation. After 30 apps, the grid wins.
3. **Existing "AI" launchers are opaque.** Pixel's suggestion row is a black box — no explanation, no correction mechanism. If it suggests the wrong app, the user has no way to tell it "no, never suggest this at 9 AM."
4. **No feedback loop.** Tapping an app tells the launcher nothing actionable. The user cannot thumb-up, thumb-down, or dismiss a suggestion. The launcher never improves beyond day one.

The user's unmet need: **"Show me the one thing I probably want to do right now, let me correct you when you're wrong, and let me *see* that correction land."**

---

## 3. Core Concept: The Feedback Flywheel

The flywheel is the **single differentiator** of this product. Every other launcher feature (drawer, search, widgets) is table-stakes — the flywheel is why Kai switches.

```
        ┌─────────────────────┐
        │  ① AI 推荐            │
        │  (Hero + Feed cards) │
        └──────────┬──────────┘
                   │ user sees
                   ▼
        ┌─────────────────────┐
        │  ② 用户交互信号       │
        │  👍 👎 ✕ tap dwell   │
        └──────────┬──────────┘
                   │ persisted to Room
                   ▼
        ┌─────────────────────┐
        │  ③ 画像更新 (Profile) │
        │  tag weights ± δ     │
        └──────────┬──────────┘
                   │ consumed by
                   ▼
        ┌─────────────────────┐
        │  ④ 推荐更精准         │
        │  ε-greedy re-rank    │
        └──────────┬──────────┘
                   │ loops to ①
                   ▼
              (next render)
```

**Four loop rules, all enforced in MVP:**

1. **Every card is tagged** with a set of intent tags (e.g. `[coffee, morning, commute]`) and a card type (`continue` / `discover` / `new`).
2. **Every signal has a weight:** 👍 = +3, tap-through = +2, long dwell (>2s) = +1, ✕ dismiss = −2, 👎 = −3. *(SCROLL_PAST removed from v2 — see Revision Log §R-CUT-1.)*
3. **Exploration is mandatory.** The recommendation engine runs ε-greedy with a fixed exploration *slot* policy tuned for the 16-card catalog (see FR-11). Exploration prevents filter-bubble collapse.
4. **Transparency is mandatory.** The user can open Profile v47 at any time and see the top-weighted tags, the most recent signals, and a "reset" button. If we cannot explain a recommendation, we cannot ship it.

MVP note: the "AI" in step ① is a deterministic ε-greedy stub over the 16 seed cards. No LLM, no cloud. The flywheel mechanics are real; the intelligence is faked. This is intentional — we ship the loop first, plug in a model later.

---

## 4. User Stories

### Home screen discovery
1. As Kai, I want to see a single "FOR YOU" hero card the moment I unlock my phone, so that I don't have to decide what to do next.
2. As Kai, I want the hero card to show a title, one-line description, an icon/emoji, and a clear primary action button, so that I can act in one tap.
3. As Kai, I want to see a "why recommended" micro-label on the hero card (e.g. "基于你最近的咖啡习惯"), so that I trust the recommendation. *(v0.1: the `whyLabel` is the card's seeded string, verbatim — we do not dynamically generate it. See FR-01.)*
4. As Kai, I want a horizontally scrollable strip of 4 intent chips (通勤 / 工作 / 午餐 / 休息) under the hero, so that I can declare my current context.
5. As Kai, I want a vertically scrolling Discover feed of 3-4 cards below the chips, so that I can browse alternatives if the hero misses.
6. As Kai, I want the Discover feed cards to feel TikTok-ish — tall, edge-to-edge, rich imagery/gradient — so that browsing feels like entertainment, not a settings menu.

### Feedback interactions
7. As Kai, I want to tap 👍 on the hero card to tell the AI "more like this," so that future recommendations lean this direction.
8. As Kai, I want to tap 👎 on the hero card to tell the AI "never this again," so that it stops surfacing similar cards.
9. As Kai, I want to tap a small ✕ on any Discover card to dismiss it without the friction of a thumbs-down, so that I can quickly clear clutter.
10. As Kai, I want the launcher to silently track how long I dwell on each card, so that I don't have to manually rate everything.
11. As Kai, I want tapping a card's primary action to count as the strongest positive signal, so that my actual behavior matters more than my stated preferences.
12. As Kai, I want feedback animations (card fades/slides on dismiss) to feel instant and physical, so that the act of teaching the AI feels satisfying.

### Profile transparency
13. As Kai, I want to open a "Profile v47" debug bottom sheet from the header badge, so that I can see what the AI thinks of me.
14. As Kai, I want to see my top 10 weighted tags with numeric scores, so that I understand why I'm getting these recommendations.
15. As Kai, I want a "Reset profile" button in the sheet, so that I can start fresh if the AI gets stuck in a bad loop. *(v1 feedback-event timeline cut — see Revision Log §R-CUT-3.)*

### Intent switching
16. As Kai, I want tapping an intent chip to immediately replace the Discover feed with cards tagged for that intent, so that I can switch context in one tap.
17. As Kai, I want the currently active intent chip to be visually distinct (filled accent color **and** a leading check glyph), so that I always know what mode I'm in — even if I'm colorblind.
18. As Kai, I want the header greeting to reflect the active intent (e.g. "工作模式 · 14:22"), so that the context is always reinforced.

### App access
19. As Kai, I want to tap a drawer button in the AI Dock to open a full-screen app drawer, so that I retain traditional launcher access. *(v1 swipe-up gesture moved to v0.2 — see Revision Log §R-CUT-2.)*
20. As Kai, I want the app drawer to be an alphabetical grid of all installed apps with icons and labels, so that I can find any app without the AI's help.
21. As Kai, I want the AI Dock to show a single clearly-labeled "coming soon" affordance for AI input, so that the product's voice/text direction is visible without baiting me into dead-end interactions.

### Launcher integration
22. As Kai, I want to set AI Launcher as my default Android launcher via the system chooser, so that pressing Home opens it.
23. As Kai, I want a small in-app banner prompting me to set as default when I am not, so that I can complete setup in two taps. *(New in v2 — replaces onboarding contradiction.)*
24. As Kai, I want the launcher to respond correctly to the Home button (re-entering the home screen in a defined, predictable state), so that it behaves like a real launcher.
25. As Kai, I want my feedback history to survive a phone reboot, so that the AI doesn't forget me overnight.
26. As Kai, I want the launcher to cold-start with a usable Hero card in under half a second, so that it feels faster than stock.

---

## 5. Functional Requirements

Each FR is independently testable. Acceptance criteria use "Given / When / Then." Requirements new or materially changed in v2 are marked **[v2]**.

### FR-01 · Hero "FOR YOU" card rendering
**Description.** A single large glassmorphic card at the top of the home screen rendering the currently highest-ranked card for the active intent.
**Acceptance.**
- Given the app has launched and an intent is active, when the home screen renders, then a HeroCard displays the top-ranked card's `title`, `description`, `icon`, `actionLabel`, `whyLabel`.
- The `whyLabel` string is the seed card's `whyLabel` field **verbatim**. v0.1 does **not** dynamically generate it from profile tags.
- The card has two visible buttons: 👍 and 👎, each ≥48dp touch target.
- The card uses the glassmorphic style (blur, 4–8% white overlay, gradient border) per DESIGN-v1.md.
- On cold start with an empty profile, the Hero is the first card in seed-declaration order for the active intent (see FR-11 cold-start rule).

### FR-02 · Hero card explicit feedback **[v2]**
**Description.** 👍 and 👎 buttons on the hero card persist a feedback event, update the profile, and refresh the Hero.
**Acceptance.**
- Given the hero card is visible, when the user taps 👍, then a `FeedbackEvent(cardId, intent, signal=THUMB_UP, weight=+3, timestamp)` is written to Room within 50ms, the profile's tag weights are updated (see FR-10), and the Hero is re-evaluated via FR-11; if the top-ranked card changes, the new Hero crossfades in within 300ms. If it does not change, the Hero stays and a brief confirmation pulse animation plays.
- 👎 behaves identically with `signal=THUMB_DOWN, weight=-3`. Because −3 typically pushes the card below the top rank, the Hero is expected to change; if the engine still ranks the same card first (e.g. all other cards are equally or more negative), the Hero stays and we log a WARN-level logcat entry `"HERO_UNCHANGED_AFTER_DOWNVOTE cardId=$id"` for debuggability.
- **Debounce:** consecutive 👍 or 👎 on the same `cardId` within 400ms are coalesced into a single event. This prevents spam-tap inflation. (Addresses critique §3.2 spam-👍.)
- **Toggle rule:** 👍 and 👎 do **not** cancel each other; both accumulate. The toggle-vs-accumulate ambiguity in v1 is resolved as *accumulate*.

### FR-03 · Intent chip strip **[v2]**
**Description.** A horizontal row of exactly 4 chips labeled 通勤 / 工作 / 午餐 / 休息, below the hero card.
**Acceptance.**
- Given the home screen is rendered, when the user views the chip strip, then 4 chips appear in the order: 通勤, 工作, 午餐, 休息.
- Exactly one chip is always selected. **Cold-start default = 工作 (WORK).** The selected intent is **not** persisted across process death in v0.1 — every cold start resets to 工作. (Resolves v1 ambiguity row 1; persistence is a v0.2 feature.)
- The selected chip has filled accent (#7CB4FF) background **and** a leading ✓ glyph; unselected chips have a glass outline with no glyph. (Addresses critique §3.4 color-only signaling.)
- All chips are ≥48dp tall with ≥8dp horizontal spacing between tap targets.

### FR-04 · Intent chip switching **[v2]**
**Description.** Tapping a chip changes the active intent, refreshes both the hero card and the Discover feed, and updates the header greeting.
**Acceptance.**
- Given the user is on the home screen, when they tap a non-selected chip, then within 200ms the Hero card and Discover feed both re-render using cards filtered to that intent (Hero is always recomputed — v1 ambiguity row 2 resolved as *always swap*), and the header greeting updates to one of the four fixed strings below.
- **Fixed header strings** (v1 ambiguity row 13 resolved):
  - COMMUTE → `通勤模式 · HH:mm`
  - WORK → `工作模式 · HH:mm`
  - LUNCH → `午餐时间 · HH:mm`
  - REST → `休息时间 · HH:mm`
  - The `HH:mm` is device local time at render; it is static (not a live clock) — re-rendered on intent switch and app resume only.
- **Rapid-switch suppression:** when the user changes intent, cards that were visible for <500ms on the *previous* intent do **not** emit any implicit signal (see FR-07). This prevents the "tap-all-chips-in-2-seconds pollutes profile" pathology from the critique.

### FR-05 · Discover feed rendering **[v2]**
**Description.** A vertically scrolling LazyColumn of Discover cards below the chip strip, populated from the seed data filtered by active intent, composed via FR-11.
**Acceptance.**
- Given an intent is active, when the feed renders, then exactly `min(4, availableForIntent)` cards appear, composed via the FR-11 ε-greedy slot allocation. (v1 said "3–6"; with 4 cards per intent this is clamped to 4 deterministically — v1 ambiguity row 3 resolved.)
- The Hero card occupies the top slot and is **excluded** from the Discover feed list. The feed shows positions 2–4 (i.e. 3 Discover cards when 4 cards are available for the intent).
- Each Discover card shows: `title`, `description`, card-type badge (continue / discover / new), `icon`, `actionLabel`, and a ✕ dismiss button (≥48dp touch target, visible at all times).
- If `availableForIntent == 0` (every card for the intent has been dismissed in this session — see FR-06), the feed shows the empty state defined in FR-19.

### FR-06 · Discover card dismiss (✕) **[v2]**
**Description.** Tapping ✕ removes the card from the current intent session and records a dismiss signal.
**Acceptance.**
- When the user taps ✕ on a Discover card, a `FeedbackEvent(signal=DISMISS, weight=-2)` is persisted, the card animates out (200ms slide+fade), and the feed re-flows without that card.
- **Session definition (v2):** a *dismiss session* is scoped to `(intent, process_lifetime)`. The dismiss list is held in-memory in the `HomeViewModel` and is cleared on:
  (a) process death / cold start,
  (b) intent change away from and back to the same intent (switching 工作 → 午餐 → 工作 clears 工作's dismiss list),
  (c) Profile v47 reset (see FR-12).
  It is **not** cleared by opening the drawer, opening the profile sheet, or the Home button.
- Dismiss is a *signal* (persisted to Room as a feedback event) **and** a *session hide* (in-memory set). The persisted signal still affects profile weights across restarts; the visible hide is session-only. This is documented copy-in-app via a tooltip-free spec note; users perceive "dismiss hides it now and teaches the AI forever."
- Dismissing the last remaining card triggers the FR-19 empty state.

### FR-07 · Implicit dwell tracking **[v2 — REWRITTEN]**
**Description.** The system measures how long each Discover card is substantially visible in the viewport and emits at most one DWELL signal per card per appearance.

**Definitions:**
- **Visible:** the card's composable has ≥50% of its laid-out height intersecting the LazyColumn viewport rectangle, measured via `LayoutCoordinates` in an `onGloballyPositioned` modifier with a 100ms throttle.
- **Appearance:** a contiguous time span during which the card remains Visible. An Appearance begins when the card becomes Visible and ends when it becomes Not-Visible (scrolled off, covered, or the owning screen pauses).
- **Dwell timer:** a per-card millisecond counter that starts at 0 on the beginning of an Appearance, accrues while Visible, and resets to 0 when the Appearance ends.

**Rules (unambiguous):**
1. **Discover cards only.** The Hero card does **not** participate in dwell tracking. Rationale: the Hero is always ≥50% visible by construction; auto-emitting DWELL on it floods the feedback table with noise. (Resolves critique C-1 Hero-dwell pathology.)
2. **One DWELL event per Appearance.** When an Appearance's dwell timer crosses 2000ms, exactly one `FeedbackEvent(signal=DWELL, weight=+1)` is emitted. Further time within the same Appearance emits nothing.
3. **Scrolling off stops the timer.** When a card ceases to be Visible before the timer reaches 2000ms, no event is emitted and the timer resets. (No SCROLL_PAST signal — critique §4 item 4 accepted; v1's −0.5 band removed.)
4. **Re-entering re-arms.** Scrolling a card back into view starts a **new** Appearance with a fresh 0-counter. A card can therefore emit multiple DWELL events per session if the user repeatedly returns to it (intended — repeated returns are a real positive signal).
5. **Pause conditions (timer is suspended, Appearance is *not* ended):**
   - The app is in the background (`Lifecycle.State < RESUMED`), including screen off and recent-apps.
   - The Profile v47 bottom sheet is open (covers cards).
   - The app drawer is open (not part of the home screen's LazyColumn).
   - A system dialog or IME has taken focus.
   On pause, the accrued dwell is preserved. On resume, if the card is still Visible, the timer continues accruing toward 2000ms.
6. **End conditions (Appearance ends, timer resets, DWELL not emitted if <2000ms accrued):**
   - The card leaves the viewport via scroll.
   - The active intent changes.
   - The card is dismissed (✕) or rendered invalid (e.g. profile reset rebuilds the feed).
7. **Rapid-switch exemption:** per FR-04, if the user changes intent within 500ms of the feed rendering, any Appearances that started in that window are discarded without emitting signals.
8. **Test hook:** `RecommendationEngine` / `FeedbackRepository` expose an injectable `Clock` (via Hilt) so instrumented tests can drive virtual time. Dwell logic must use the injected clock, not `System.currentTimeMillis()` directly. (Satisfies critique §3.6 determinism.)

### FR-08 · Card action tap-through **[v2]**
**Description.** Tapping the action button on any card records the strongest positive signal.
**Acceptance.**
- When the user taps a card's action button, a `FeedbackEvent(signal=ACTION_TAP, weight=+2)` is persisted.
- The action itself is a no-op in v0.1 and shows a **Snackbar** (not Toast — critique §3.5 R-18) with the literal text `"${actionLabel} — 即将上线"`. (v1 ambiguity row 6 resolved.)
- The Snackbar uses the app's dark glassmorphic theme per DESIGN-v1.md.
- ACTION_TAP on the Hero card also triggers the same Hero re-evaluation flow as FR-02.

### FR-09 · Feedback event persistence (Room) **[v2]**
**Description.** All feedback events persist to a Room database with full crash/reboot durability.
**Acceptance.**
- Every feedback event is written via `FeedbackRepository` to a Room table `feedback_events` with columns: `id` (PK autogen Long), `card_id` (String), `intent` (String enum name), `signal` (String enum name), `weight` (Float), `timestamp_ms` (Long).
- Writes complete in <50ms on a Pixel 6 (median of 100 inserts).
- After killing the process and relaunching, all previously recorded events are queryable via `FeedbackDao.getAll()` and `FeedbackDao.count()`.
- **Room version = 1.** Schema-change policy for v0.1: `fallbackToDestructiveMigration()` is enabled. Beta testers' data may be wiped across builds that change schema. This is documented in the APK release notes. (Addresses critique C-8.)
- **Room build failure handling:** if the Room database cannot be opened on launch (disk full, corruption), `FeedbackRepository` exposes an `isHealthy: Boolean` flag; the UI falls back to the FR-19 cold-start empty state and feedback button taps show the FR-19 Snackbar. The app does not crash. (Addresses critique §3.1.)

### FR-10 · User profile derivation **[v2 — CLARIFIED]**
**Description.** A `UserProfile` table stores the running sum of weights per tag, updated transactionally with every feedback event.
**Acceptance.**
- Table `user_profile`: columns `tag` (String PK), `weight` (Float), `updated_ms` (Long).
- **Formula (unambiguous — v1 ambiguity row 7 resolved):** for each feedback event with `event.weight` and `card.tags.size == N`, the profile update is:
  ```
  for each tag in card.tags:
      user_profile[tag].weight += event.weight / N
  ```
  Here `N` is the **card's tag count**, not a user-total. Worked example: a 👍 (+3) on `lunch_01` (tags = `[coffee, luckin, lunch, habit]`, N=4) increments each of those 4 tags by +0.75.
- The increment is executed in the same Room transaction as the `feedback_events` insert. Either both writes succeed or neither does.
- A query of the top 10 tags by weight (`ORDER BY weight DESC LIMIT 10`) returns in <50ms at the 95th percentile.
- Tags never read before are inserted with `weight = (event.weight / N)`.

### FR-11 · ε-greedy recommendation engine **[v2 — REWRITTEN]**
**Description.** A pure-Kotlin `RecommendationEngine` ranks seed cards for an intent using profile weights, with bounded exploration tuned for the 16-card catalog.

**Scoring.**
- For a card `c` and profile `P`, the exploitation score is:
  ```
  score(c, P) = Σ (1.0 × P.weight[tag])  for tag in c.tags
  ```
  That is, **every tag on a card contributes with an implicit card-side weight of 1.0** (v1 ambiguity row 8 resolved; critique C-2). Card type (`CONTINUE` / `DISCOVER` / `NEW`) does **not** modify the exploitation score directly.
- If a tag is not present in `user_profile`, its profile weight is treated as 0.0.
- Ties are broken by seed-declaration order (stable, deterministic).

**Slot allocation (tuned for 4-cards-per-intent).**
- `recommend(intent, count)` returns a list of up to `count` cards. In v0.1 always called with `count=4`.
- Let `available = seedCards.filter { it.intent == intent && it !in dismissedThisSession }`.
- If `available.size <= 1`: return `available` as-is (no exploration room).
- If `available.size >= 2`: reserve **exactly 1 slot for exploration**, remainder for exploitation. (A fixed-slot rule is unambiguous and guarantees ≥1 explore pick even on a 4-card catalog — resolves "20% of 4 = 0.8" rounding trap, critique C-2.)
  - Exploitation slots = `available.sortedByDescending { score(it, P) }.take(available.size - 1)`.
  - Exploration slot = one card chosen uniformly at random from `available - exploitationSlots`.
- The returned list places the top-scoring exploitation card at position 0 (this is the **Hero source**) followed by remaining exploitation picks in score order, with the exploration slot inserted at position `available.size - 1` (the *bottom* of the list). This deterministic ordering makes the feed visually stable while still exposing exploration.

**Cold-start rule (unambiguous — resolves v1 contradiction with parsing note 4).**
- When `user_profile` is empty (no signals yet): every `score(c, P)` evaluates to 0.0, ties break by seed-declaration order → Hero is the first seed card for the intent (which happens to be the `CONTINUE` card in the spec). The exploration slot still fires and picks uniformly at random from the non-exploitation set.
- The v1 parsing note ("NEW cards act as the random 20% even before profile data") is **rejected**: it contradicts the deterministic cold-start order and forced a special-case in the engine. Instead, NEW-type cards are simply seeded last in §9, so on cold start they naturally occupy the exploration pool. (Rejection logged in Revision Log §R-REJ-1.)

**Determinism & test hook (new in v2).**
- The engine accepts an injected `kotlin.random.Random` instance (provided by Hilt as `@Singleton @Named("recEngineRng") Random`).
- In production the binding is `Random.Default`.
- For instrumented/unit tests, the binding is replaced with `Random(seed = 42L)`, making exploration-slot selection fully reproducible.
- Additional debug-only hook (gated by `BuildConfig.DEBUG`): `RecommendationEngine.setDeterministicMode(seed: Long)` re-seeds the RNG at runtime so demo scripts and QA can reproduce exact sequences.

**Purity constraints.**
- Synchronous, in-memory, zero network calls.
- Stateless across calls except for the injected profile snapshot and RNG — the engine itself holds no mutable state. The dismiss-list is passed in as a parameter by the caller (`HomeViewModel`), never cached inside the engine.

### FR-12 · Profile v47 debug sheet **[v2 — SCOPED DOWN]**
**Description.** A Material 3 ModalBottomSheet, launched from the header profile badge, showing the user's learned profile in a human-readable form.
**Acceptance.**
- Tapping the profile badge in the header opens a bottom sheet titled "Profile v47".
- The sheet displays:
  - (a) the **top 10 tags by weight**, with numeric scores rounded to 2 decimal places, sorted descending by absolute weight (so strongly-negative tags are visible at the bottom half of the list);
  - (b) an aggregate counter `"累计反馈: N"` where N = `feedback_events` row count;
  - (c) a **"重置画像"** (Reset profile) button in the sheet's footer.
- **v1's "timeline of 20 most recent events" is removed from v0.1** (critique §4 item 5 accepted; logged in Revision Log §R-CUT-3). Implementing it correctly (non-interactive rows, relative timestamps, dark theming) is time the flywheel needs more.
- **Reset flow:** tapping 重置画像 shows a Material 3 AlertDialog "确定清空所有画像数据？此操作无法撤销。" with Confirm / Cancel. On confirm, in a single Room transaction: `DELETE FROM feedback_events`; `DELETE FROM user_profile`. The UI then:
  1. clears the in-memory dismiss list,
  2. resets the active intent to 工作 (default),
  3. closes the bottom sheet,
  4. re-renders the home screen via `HomeViewModel.refresh()`.
  This prevents the "stale cards still on screen after reset" race (critique §3.1 & R-13).
- The sheet respects the dark glassmorphic theme. Scrim color is `rgba(10, 15, 30, 0.72)` to match the gradient background.
- Sheet dismissal: swipe-down or tap-outside-scrim both close the sheet. Back gesture also closes.
- Timeline items (now removed) had implied interactivity in v1 — this question is now moot.

### FR-13 · App drawer **[v2]**
**Description.** A full-screen alphabetical grid of all launchable installed apps, reachable via a dedicated button in the AI Dock. *(Swipe-up gesture deferred to v0.2 to avoid Android-13+ system-gesture collision — critique §3.3 / §4.)*
**Acceptance.**
- Tapping the drawer-icon button in the AI Dock navigates to `AppDrawerScreen`.
- The drawer lists every app returned by `PackageManager.queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER), 0)`, sorted A–Z by display label (`ApplicationInfo.loadLabel`), as a 4-column grid with icon + label.
- Tapping an app launches it via `packageManager.getLaunchIntentForPackage(packageName)`; if that returns null, show a Snackbar `"无法打开此应用"`.
- A back gesture/button returns to the home screen, **preserving** intent selection, dismiss list, and scroll position. "State" for FR-13 back-nav explicitly includes all three. (v1 ambiguity row 10 resolved.)
- Work-profile apps: v0.1 shows them alongside primary-profile apps without badging. (Accepts critique §3.3 work-profile item as low-risk; Revision Log §R-ACCEPT-1.)
- Empty drawer (fewer than 4 apps, e.g. a fresh emulator): grid renders the apps it has; no empty-state illustration required.

### FR-14 · Launcher intent-filter + default-launcher eligibility **[v2 — TIGHTENED]**
**Description.** The app's `MainActivity` registers as an Android launcher so the user can select it from Settings → Default apps → Home app, with predictable Home-button re-entry behavior.
**Acceptance.**
- `AndroidManifest.xml` declares on `MainActivity` an intent filter with:
  - `<action android:name="android.intent.action.MAIN" />`
  - `<category android:name="android.intent.category.HOME" />`
  - `<category android:name="android.intent.category.DEFAULT" />`
- `MainActivity` uses `android:launchMode="singleTask"` and `android:exported="true"` (required for Android 12+, critique §3.3).
- After install on a Pixel 6 (Android 14), the user can select "AI Launcher" in the system home-app picker, and pressing Home subsequently opens our `MainActivity`.

**Home-button re-entry semantics (unambiguous — v2 picks a single behavior):**
When Home is pressed while our app is already foreground, the launcher returns to the **home screen in its canonical state**, defined as all of the following, executed in order:
1. Dismiss any open bottom sheet (Profile v47).
2. Pop the navigation back stack to `HomeScreen` (closes `AppDrawerScreen` if open; closes any future sub-screens).
3. Reset the Discover feed scroll position to the top (`LazyListState.scrollToItem(0)`).
4. **Preserve** the currently-selected intent chip. The intent is *not* reset to 工作 on Home press — only on cold start (per FR-03). (Critique C-4 resolved: "stay on current intent" is the chosen branch.)
5. **Preserve** the session dismiss list for the current intent.

This gives the user a reliable "go home, clean slate, same context" behavior. Process is not restarted.

### FR-15 · AI Dock (v0.1 simplified placeholder) **[v2 — CUT DOWN]**
**Description.** A persistent bottom bar showing the drawer-launch button and a single clearly-labeled AI-coming-soon affordance. *(v1's three non-functional affordances — text field, mic, ✦ — are reduced to one non-interactive pill; critique §4 item 1–2 accepted.)*
**Acceptance.**
- The dock is always visible at the bottom of the home screen above the system nav bar.
- The dock contains, left to right:
  1. An app-drawer button with content description `"打开应用抽屉"` (launches FR-13).
  2. A single centered pill reading `"✦ AI · 即将上线"` which is rendered as a `Text` inside a disabled-looking container (no ripple on press, no keyboard opens, no Snackbar, no signal). Purely informational.
- No text input, no keyboard, no mic button in v0.1.

### FR-16 · Seed data load **[v2 — SOFTENED]**
**Description.** The 16 seed cards defined in §9 are loaded into memory on first app launch.
**Acceptance.**
- On cold start with no prior data, `CardRepository.getAll()` returns exactly 16 cards whose `id`, `intent`, `type`, and `tags` fields match §9 **exactly** (these are load-bearing for the engine and the profile).
- `title`, `description`, `icon`, `actionLabel`, `whyLabel` may differ from §9 by **line-level copy edits approved in the implementing PR** — typos, whitespace, minor tone fixes are allowed without a PRD revision. (Critique C-7 / R-17 accepted; the byte-for-byte gate in v1 was process theater.)
- Seed cards are declared in `data/seed/SeedCards.kt` as a `val SEED_CARDS: List<SeedCard>`. No JSON, no assets.

### FR-17 · Default-launcher prompt **[v2 — NEW]**
**Description.** When the app is opened and is not the current default home app, surface a minimal, dismissible in-app banner so the user can complete setup without an onboarding flow.
**Acceptance.**
- On every `onResume` of `MainActivity`, check whether our package is the resolved `category.HOME` handler (`PackageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME), 0)`).
- If **not** the default, render a dismissible banner between the header and the Hero card with text `"设为默认启动器"` and a secondary `✕` (dismiss-banner) button.
- Tapping the banner fires `startActivity(Intent(Settings.ACTION_HOME_SETTINGS))`. If that intent resolves to nothing (rare), fall back to `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`.
- Tapping ✕ hides the banner for the remainder of the process lifetime (not persisted — will re-appear next cold start if still not default).
- This is explicitly **not onboarding** — no full-screen takeover, no mandatory step. (Resolves v1 contradiction between US-22 and §7 no-onboarding; critique §3.3 / R-5.)

### FR-18 · Accessibility minimum bar **[v2 — NEW]**
**Description.** The v0.1 baseline a11y set, enforced by lint + manual QA.
**Acceptance.**
- **Touch targets:** every interactive element (chips, buttons, ✕ dismiss, drawer items, dock pill) has a touch target ≥48dp × 48dp. Smaller visible hitboxes must use `Modifier.minimumInteractiveComponentSize()` or explicit padding.
- **Content descriptions:** every icon-only button (👍, 👎, ✕, drawer icon, profile badge, banner ✕) has a non-null `contentDescription`. Purely decorative icons use `contentDescription = null` explicitly.
- **Font scaling:** the UI must remain usable (no clipped primary actions, no hidden titles) at 130% font scale. At 200% font scale, cards may grow vertically but the Hero's title and action button must remain visible without horizontal scroll. Acceptance is a manual screenshot check at both scales on the golden-path QA device.
- **TalkBack traversal order** on the Hero card: `title → description → whyLabel → 👍 → 👎`. Implemented via `Modifier.semantics { traversalIndex = ... }` or by composable order.
- **Non-color indicators:** every state difference currently conveyed by color alone also has a non-color cue (see FR-03 leading ✓ glyph; active-intent header string).
- **Reduce-motion:** when the OS "Remove animations" setting is on (`Settings.Global.TRANSITION_ANIMATION_SCALE == 0f`), the FR-06 dismiss animation and FR-02 hero crossfade collapse to instant state changes (0ms). Other animations may remain but must not be the *only* feedback signal.
- **Contrast:** text on glassmorphic cards targets WCAG AA (≥4.5:1 for body, ≥3:1 for large text). Where the gradient backdrop varies, an 8% solid-white overlay is applied under text blocks to ensure the floor contrast.

### FR-19 · Error / empty / degenerate states **[v2 — NEW]**
**Description.** The states the launcher must handle without crashing or looking broken.

| State | Trigger | Required UI |
|---|---|---|
| **Cold-start, empty profile** | First launch or post-reset; `feedback_events` and `user_profile` both empty | Render normally per FR-11 cold-start rule. Hero is the first seed card for 工作. No special banner beyond FR-17. |
| **Room unavailable** | Room open throws, disk full, DB corrupt (FR-09 `isHealthy = false`) | Home screen renders the seed cards from in-memory `SEED_CARDS` directly (bypassing the repo). All feedback buttons show a Snackbar `"存储暂时不可用，反馈未保存"`. No crash. Logged at ERROR to logcat. |
| **All-negative profile** | Every tag in `user_profile` has weight < 0 (exploitation set is empty of positives) | FR-11's formula naturally still returns a sorted list; ties break by seed order. No fallback branch needed — the engine does not filter by sign. |
| **All cards dismissed for intent** | Dismiss list size == 4 for active intent | Hero slot shows a glass card with emoji 🌱, title `"已看完本模式推荐"`, description `"试试其他模式，或点击下方恢复"`, and a button `"恢复本模式"` that clears the intent's dismiss list and re-renders. Discover feed area shows nothing. |
| **Intent has zero matching seed cards** | Should never happen in v0.1 (each intent has 4 cards in §9) | Defensive: show the same empty state as above. |
| **PackageManager returns 0 apps (drawer)** | Fresh emulator / restricted profile | Drawer renders a single glass card `"没有可启动的应用"`. |
| **Default-launcher revoked** | User sets another default after ours | FR-17 banner re-appears on next resume. No other UI change. |

All empty-state strings live in `strings.xml` for future localization even though v0.1 is Chinese-only.

### FR-20 · Dev-build instrumentation **[v2 — NEW]**
**Description.** Debug-build-only affordances to make the flywheel testable and demoable.
**Acceptance.**
- Under `if (BuildConfig.DEBUG)`:
  - Logcat tag `FLYWHEEL` logs every feedback event write at DEBUG level with card id, signal, weight, and resulting top-3 tag snapshot.
  - `RecommendationEngine.setDeterministicMode(seed: Long)` (FR-11) is exposed.
  - A long-press on the profile badge reveals a hidden dev panel with three buttons:
    1. `"Seed demo profile"` — inserts a fixed set of ~20 synthetic feedback events biased toward coffee/design/music so the flywheel's top-tag list is obviously populated for demos and SM-07 verification.
    2. `"Dump profile to logcat"` — prints full `user_profile` table.
    3. `"Clear dismiss list"` — clears the in-memory dismiss set for the active intent.
- None of the above ships in release builds.
- `NFR-06` (no telemetry) is not violated: logcat is local-device-only and no network egress occurs.

---

## 6. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Performance | Cold-start to **Hero-card-with-non-placeholder-content committed** ≤ **500ms** on Pixel 6 (Android 14), median of 10 cold starts. Measured via Macrobenchmark. (v1 "first paint" tightened per critique §7 SM-05.) |
| NFR-02 | Performance | All Room queries used in the main UI path complete in ≤ **50ms** at the 95th percentile on Pixel 6. |
| NFR-03 | Performance | Intent-chip switch (FR-04) re-renders feed within **200ms**. |
| NFR-04 | Offline-first | The entire app functions with airplane mode enabled. Zero network calls in v0.1. |
| NFR-05 | Battery | **No background work.** No `WorkManager`, no `JobScheduler`, no services, no foreground services. All work happens while the activity is resumed. |
| NFR-06 | Privacy | 100% of user data — feedback events, profile, cards — is stored locally in Room. No analytics SDK, no crash reporter, no network telemetry in v0.1. Local logcat is permitted. |
| NFR-07 | Stability | Zero crashes across the golden-path QA script (below) on a fresh install on Pixel 6 Android 14, run 3× consecutively with force-stop between runs, no other launcher installed during the test. (Critique §7 SM-06 resolved.) |
| NFR-08 | Accessibility | See FR-18. This NFR is now a pointer to the FR, which is the full spec. |
| NFR-09 | Theming | Dark-first only in v0.1. Light mode deferred. |
| NFR-10 | APK size | Release APK ≤ 15 MB. |
| NFR-11 | Theming surfaces | All of the following are themed to the dark glassmorphic palette (critique §3.5): bottom sheet scrim (`rgba(10,15,30,0.72)`), status bar (transparent + light-content icons), nav bar (transparent), all dialogs (Material 3 dark surface `#161a2e`), drawer background (same gradient as home), Snackbars replace Toasts everywhere. IME hints use `EditorInfo.IME_FLAG_NO_FULLSCREEN`. |
| NFR-12 | Wallpaper | The launcher **ignores system wallpaper** in v0.1. The gradient background is drawn directly; no `WallpaperManager` integration. (Critique §3.3.) |

**Golden-path QA script (NFR-07):**
1. Fresh install, set as default launcher via FR-17 banner.
2. Cold start → verify Hero = `work_01` (cold-start default 工作).
3. Tap each intent chip in turn (工作→通勤→午餐→休息→工作). Verify feed changes and no crash.
4. Perform 10 feedback actions across intents: 3×👍, 2×👎, 3×✕, 2× action-tap.
5. Open Profile v47 — verify top tags populated and reset button works on the last run.
6. Open app drawer, launch a 3rd-party app, press Home, verify FR-14 re-entry semantics.
7. Force-stop and re-launch — verify feedback counter persists.
8. Reboot device and re-launch — verify feedback counter persists.

---

## 7. Out of Scope (v0.1)

Explicit exclusions. Any PR touching these is rejected.

**From AGENTS.md (reiterated):**
- ❌ Real LLM calls (OpenAI, Claude, Gemini, local models — none)
- ❌ Cloud sync of any kind (no Firebase, no Supabase, no custom backend)
- ❌ Real-time data sources: calendar, weather, location, contacts, notifications
- ❌ Home-screen widgets (neither hosting them nor being one)
- ❌ Multi-user / multi-profile support
- ❌ Accessibility-service-based app-usage tracking

**Additional v0.1 exclusions:**
- ❌ Light theme
- ❌ Icon packs / custom icon theming
- ❌ Folders on the home screen
- ❌ Home-screen customization (long-press to edit, drag-drop)
- ❌ Search across apps/contacts/web (the AI Dock is a placeholder)
- ❌ Actual execution of card actions (tapping an actionLabel fires a signal + Snackbar only)
- ❌ Notifications, notification badges
- ❌ **Swipe-up gesture to open the drawer** (button-only in v0.1; gesture returns in v0.2 after collision testing with system gesture nav). **[Moved here in v2]**
- ❌ **AI Dock text input and voice input** — single non-interactive "coming soon" pill only. **[Tightened in v2]**
- ❌ **Live PackageManager broadcast for newly-installed apps** — drawer refreshes only on navigate-to or cold start. (v1 soft-scoped US-28 is now hard-scoped.) **[Moved here in v2]**
- ❌ **Feedback event timeline in Profile v47** — top tags only in v0.1. **[Moved here in v2]**
- ❌ **SCROLL_PAST implicit negative signal** — removed entirely. **[Cut in v2]**
- ❌ **Persistence of selected intent across process death** — always reset to 工作 on cold start.
- ❌ Onboarding flow beyond the FR-17 banner.
- ❌ Settings screen (reset lives in Profile v47 sheet; nothing else is configurable).
- ❌ Localization — Simplified Chinese strings only in v0.1. Strings.xml used to enable future i18n without code changes.
- ❌ Tablet / foldable adaptive layouts.
- ❌ Room schema migration logic — destructive migration only (FR-09).

---

## 8. Success Metrics

MVP is considered successful when **all** of the following are verifiable on a Pixel 6 (Android 14) release build. Metrics revised from v1 are marked **[v2]**.

| ID | Metric | Target | How to verify |
|---|---|---|---|
| SM-01 | App can be set as default launcher, and Home re-entry is correct **[v2]** | ✅ | Settings → Apps → Default apps → Home app shows "AI Launcher". After selecting, pressing Home while on `AppDrawerScreen` returns to `HomeScreen` with intent preserved (FR-14). |
| SM-02 | All 4 intent chips switch feed content **[v2]** | ✅ | The set of card IDs returned for any two distinct intents differs by ≥2 IDs (critique §7 SM-02 resolved). |
| SM-03 | Feedback persists across restart **[v2]** | ✅ | Apply 5× 👍 on specific cards → force-stop app → relaunch → `FeedbackDao.count()` returns ≥5 and Profile v47 counter displays ≥5 (no longer dependent on timeline UI). |
| SM-04 | Hero changes after concentrated negative feedback **[v2]** | ✅ | Starting from an empty profile on intent 午餐, tap 👎 on the current Hero twice in succession. After the second 👎, the rendered Hero's `cardId` is different from the starting Hero. (Replaces v1's gameable metric, critique C-6.) |
| SM-05 | Cold-start time to Hero content committed **[v2]** | <500ms | Macrobenchmark from `onCreate` to first frame where HeroCard composable has non-placeholder content, median of 10 cold starts on Pixel 6. |
| SM-06 | Zero crashes on golden path **[v2]** | 0 | NFR-07 script runs clean 3× consecutively on the specified device in the specified state. |
| SM-07 | ε-greedy exploration verified via unit test **[v2]** | ≥1 | A JVM unit test calls `RecommendationEngine.recommend(LUNCH, 4)` 1000 times with a strongly-biased profile (weight +10 on `coffee`) and a deterministic RNG seed. Assert: the top-exploitation card appears 1000× at position 0; the exploration slot (position 3) contains a non-top card in ≥50% of calls. (Moved off release-gate for session-level; critique §4 item 6 accepted.) |
| SM-08 | App drawer functional **[v2]** | ✅ | Drawer item count == `queryIntentActivities(ACTION_MAIN, CATEGORY_LAUNCHER).size` at render time; tapping any item launches its main intent. |
| SM-09 | Profile reset end-to-end **[v2 — NEW]** | ✅ | After seeding signals, tap 重置画像 → confirm → verify `feedback_events` and `user_profile` tables are empty AND Hero has reverted to cold-start seed order (`work_01` under default intent). |
| SM-10 | Dwell signal actually emits **[v2 — NEW]** | ✅ | Instrumented test: scroll a Discover card into view, hold for 2500ms, scroll off. Verify exactly one `DWELL` row in `feedback_events` for that `card_id`. |
| SM-11 | Dismiss hides card from visible feed **[v2 — NEW]** | ✅ | Instrumented test: tap ✕ on a Discover card. Verify the card is absent from the LazyColumn composition within 250ms and a DISMISS row is present in `feedback_events`. |
| SM-12 | Cold-start empty state is sensible **[v2 — NEW]** | ✅ | Fresh install, first launch: Hero renders with non-placeholder content, `whyLabel` displays verbatim seed string, no crash, no blank areas. |

---

## 9. MVP Seed Data Spec

**The dev agent should treat `id`, `intent`, `type`, and `tags` as load-bearing and match §9 exactly. Text fields (`title`, `description`, `icon`, `actionLabel`, `whyLabel`) may receive line-level copy edits in PR review per FR-16.**

16 cards total: 4 intents × 4 cards. Symmetric 1/2/1 split (CONTINUE / DISCOVER / NEW) per intent. The catalog is intentionally kept at 16 for v0.1; catalog expansion to prove week-over-week learning is a v0.2 deliverable — see Revision Log §R-ACCEPT-2.

Card schema:
```kotlin
data class SeedCard(
    val id: String,            // stable, e.g. "commute_01"
    val title: String,
    val description: String,   // one line, ≤60 chars
    val intent: Intent,        // COMMUTE | WORK | LUNCH | REST
    val type: CardType,        // CONTINUE | DISCOVER | NEW
    val icon: String,          // emoji (v0.1)
    val actionLabel: String,
    val whyLabel: String,      // "why recommended" micro-copy
    val tags: List<String>     // for ε-greedy scoring
)
```

**Copy-voice rule (new in v2):** `whyLabel` strings must be phrased as **testable retrospective claims about the seed catalog itself** — never time-dependent ("12 点了"), never fabricated-behavioral ("你这周听了 5 次", "本周连续 4 天炸物"). Allowed voices:
- Mock-behavioral framed as example: "示例：你最近的咖啡习惯"
- Card-type-driven: "ε-greedy 探索 · 你可能没试过"
- Context-agnostic: "高频选项"

Critique §6-specific fixes are applied below (`work_03` time-stamped schedule reworded, `lunch_03` whyLabel reworded, `lunch_04` reworded, `rest_02` reworded, `rest_03` simplified). Card IDs and tags are unchanged from v1 to preserve engine behavior.

### Intent: 通勤 (COMMUTE)

| id | title | description | type | icon | actionLabel | whyLabel | tags |
|---|---|---|---|---|---|---|---|
| `commute_01` | 继续听《三体》 | 上次停在第 14 章，还剩 23 分钟 | CONTINUE | 🎧 | 继续播放 | 示例：你在听的有声书 | `[audiobook, scifi, commute, morning]` |
| `commute_02` | 常用通勤路线 | 示例路线预计 18 分钟 | DISCOVER | 🚇 | 查看路线 | 示例：你的常用路线 | `[transit, subway, commute, morning]` |
| `commute_03` | 新闻早报 · 3 分钟 | 科技、财经、本地头条速览 | DISCOVER | 📰 | 开始播报 | 示例：早间高频选择 | `[news, briefing, commute, morning]` |
| `commute_04` | 试听《硅谷早知道》 | 科技播客，看看你是否感兴趣 | NEW | 🎙️ | 试听 | ε-greedy 探索 · 匹配科技偏好 | `[podcast, tech, commute, explore]` |

### Intent: 工作 (WORK)

| id | title | description | type | icon | actionLabel | whyLabel | tags |
|---|---|---|---|---|---|---|---|
| `work_01` | 继续 Figma 稿件 | 示例文件 "Launcher v2 - Home" | CONTINUE | 🎨 | 打开 Figma | 示例：最近编辑的设计稿 | `[design, figma, work, focus]` |
| `work_02` | 开启专注模式 45 min | 屏蔽通知、白噪音、番茄钟 | DISCOVER | 🎯 | 开始专注 | 示例：工作日常用 | `[focus, pomodoro, work, afternoon]` |
| `work_03` | 今日日程概览 | 示例：3 个会议、2 个待办 | DISCOVER | 📅 | 查看日程 | 工作模式常看 | `[calendar, meeting, work]` |
| `work_04` | 试试 Linear 建单 | 轻量级项目管理，给你看看 | NEW | 📋 | 了解一下 | ε-greedy 探索 · 项目管理类 | `[productivity, pm, work, explore]` |

### Intent: 午餐 (LUNCH)

| id | title | description | type | icon | actionLabel | whyLabel | tags |
|---|---|---|---|---|---|---|---|
| `lunch_01` | 瑞幸生椰拿铁 | 15 元券可用 | CONTINUE | ☕ | 一键下单 | 示例：高频选择 | `[coffee, luckin, lunch, habit]` |
| `lunch_02` | 附近餐厅 · 20% off | 步行约 4 分钟，人均 58 元 | DISCOVER | 🍲 | 查看菜单 | 午餐时间 · 附近优惠 | `[restaurant, lunch, nearby, deal]` |
| `lunch_03` | 叫个外卖 | 示例：常点的 3 家都在营业 | DISCOVER | 🛵 | 打开美团 | 午餐时间高频选择 | `[delivery, meituan, lunch]` |
| `lunch_04` | 今日轻食套餐 | 鸡胸肉 + 藜麦碗 | NEW | 🥗 | 看看 | ε-greedy 探索 · 健康选项 | `[salad, healthy, lunch, explore]` |

### Intent: 休息 (REST)

| id | title | description | type | icon | actionLabel | whyLabel | tags |
|---|---|---|---|---|---|---|---|
| `rest_01` | 继续《漫长的季节》E08 | 上次停在 18:23，剩 42 分钟 | CONTINUE | 📺 | 继续看 | 示例：最近在追的剧 | `[tv, drama, rest, evening]` |
| `rest_02` | 放首《夜的第七章》 | 周杰伦 | DISCOVER | 🎵 | 播放 | 示例：休息时常听 | `[music, jay, rest]` |
| `rest_03` | 10 分钟身体扫描冥想 | 睡前放松 | DISCOVER | 🧘 | 开始 | 示例：冥想类高频选择 | `[meditation, wellness, rest, evening]` |
| `rest_04` | 试试《塞尔达》王国之泪 | 开放世界解谜游戏 | NEW | 🎮 | 了解 | ε-greedy 探索 · 解谜偏好 | `[game, zelda, rest, explore]` |

**Parsing notes for the dev agent:**
- Store these in `data/seed/SeedCards.kt` as a `val SEED_CARDS: List<SeedCard>` — hardcoded, no JSON.
- The `tags` column values (lowercase, alphanumeric) are also the keys used in the `user_profile` weight table.
- `intent` values map to the enum: `COMMUTE=通勤, WORK=工作, LUNCH=午餐, REST=休息`.
- Declaration order per intent is `_01` (CONTINUE) → `_02`, `_03` (DISCOVER) → `_04` (NEW). This matters for the FR-11 cold-start rule: on empty profile, the Hero is `_01` and the exploration slot naturally falls to `_04`.
- **Tag vocabulary note:** brand-specific tags (`jay`, `luckin`, `figma`, `zelda`, `meituan`) are acknowledged dead-ends — each appears on exactly one card and accumulates weight from that card alone (critique §6 R-16 accepted as low-risk for v0.1, expansion planned in v0.2).

---

## 10. Tech Constraints (reiterated from AGENTS.md — do not contradict)

- **Language:** Kotlin 2.0.21. No Java sources except auto-generated.
- **UI:** Jetpack Compose, Compose BOM 2024.12.01, Material 3. No XML layouts except `AndroidManifest.xml`.
- **Architecture:** MVVM. **Single activity** (`MainActivity`) + Compose Navigation.
- **DI:** Hilt 2.52 for all repositories, the recommendation engine, and ViewModels.
- **Database:** Room 2.6.1. Tables: `feedback_events`, `user_profile`, `cards` (cache). Room version 1; destructive migration policy for v0.1 (FR-09).
- **Build:** AGP 8.7.3, Gradle 8.11.1, JDK 17.
- **Package:** `com.bobpan.ailauncher`.
- **SDK range:** minSdk 26, targetSdk 34.
- **Theme:** Dark-first, glassmorphic. Primary `#7CB4FF`, secondary `#FF7EC8`, positive `#64E6A0`, bg gradient `#0a0f1e → #161a2e`.
- **Module layout:** exactly as specified in AGENTS.md `## Module Structure`.
- **No network dependencies** in the app module in v0.1 (OkHttp/Retrofit/Ktor are forbidden).

---

## Appendix A · Resolved Questions (formerly Open Questions)

1. **Dwell implementation:** `onGloballyPositioned` + LazyColumn viewport math, throttled at 100ms, using an injected `Clock` for testability. Dev is free to refactor if semantics match FR-07.
2. **<4 launchable apps in drawer:** drawer shows what exists; no empty-state illustration required (FR-13 + FR-19).
3. **"Profile v47" versioning:** literal string "Profile v47" — not dynamic in v0.1.
4. **Card-side tag weights:** fixed 1.0 (FR-11).
5. **Cold-start NEW-card special case:** rejected; NEW cards fall into the exploration slot by seed order instead.
6. **Persisting selected intent across restart:** rejected for v0.1 (see §7); always reset to 工作.

## Appendix B · Glossary

- **Hero card** — the single large card at the top of the home screen.
- **Discover feed** — the vertical scroll of smaller cards below the intent chips.
- **Intent** — one of 4 user-declared context modes (通勤/工作/午餐/休息).
- **Signal** — a single user interaction with a card producing a weighted feedback event.
- **Session (dismiss session)** — the `(intent, process_lifetime)`-scoped in-memory set of dismissed card IDs. See FR-06.
- **Appearance** — a contiguous span during which a Discover card is ≥50% visible. See FR-07.
- **Profile v47** — the debug bottom sheet exposing learned tag weights.
- **ε-greedy** — exploration/exploitation strategy for recommendation; in v0.1 implemented as "reserve 1 exploration slot when ≥2 candidates available" per FR-11.

---

## 11. Revision Log (v1 → v2)

This section is the ledger for v2. Every critique item is tracked below as either **FIX** (integrated into the PRD above) or **REJECT** (with a one-line reason).

### Critical challenges (from critique §2)

| Ref | Status | Resolution |
|---|---|---|
| **C-1** Dwell tracking vague | FIX | FR-07 rewritten with 8 numbered rules, Hero excluded, pause/end conditions defined, test hook via injected `Clock`. |
| **C-2** ε-greedy math ambiguous | FIX | FR-11 rewritten with fixed card-side tag weight (1.0), fixed-slot allocation (1 explore slot when ≥2 candidates), deterministic RNG injection, cold-start rule disambiguated. |
| **C-3** 16-card catalog too small | **REJECT (partial)** | Catalog kept at 16 for v0.1; pitch scoped down to "visibly different within a 10-minute demo" rather than "noticeably better within a week" (see §1 scope-down). Expansion is a v0.2 deliverable. Rationale: expanding to 24–32 cards now adds days of copy/voice work that the flywheel tech doesn't need to be proven. |
| **C-4** Home-button re-entry ambiguous | FIX | FR-14 now specifies 5 ordered steps: dismiss sheets, pop stack, scroll to top, preserve intent, preserve dismiss list. "Stay on current intent" branch chosen. |
| **C-5** Session definition missing | FIX | FR-06 defines "dismiss session" as `(intent, process_lifetime)`; glossary entry added. |
| **C-6** SM-04 gameable | FIX | SM-04 replaced with concrete "Hero cardId changes after 2× 👎 on same card" test. |
| **C-7** Byte-for-byte seed match over-constrained | FIX | FR-16 softened: `id`, `intent`, `type`, `tags` are strict; text fields may receive PR-reviewed copy edits. |
| **C-8** No Room migration policy | FIX | FR-09 documents `fallbackToDestructiveMigration()` as the v0.1 policy. |

### Missing requirements (critique §3)

| Ref | Status | Resolution |
|---|---|---|
| §3.1 Empty/degenerate states | FIX | New FR-19 enumerates 7 states with required UI. |
| §3.1 Room corruption | FIX | FR-09 `isHealthy` flag; FR-19 graceful-degradation row. |
| §3.1 Profile reset race | FIX | FR-12 spells out 4-step reset flow including dismiss-list clear and feed re-render. |
| §3.2 Spam 👍 | FIX | FR-02 adds 400ms same-card debounce. |
| §3.2 Rapid intent switching pollutes profile | FIX | FR-04 rapid-switch suppression (<500ms Appearances emit no signal). |
| §3.2 Thumb-up then thumb-down | FIX | FR-02 toggle-vs-accumulate resolved as accumulate. |
| §3.2 Dislike-only fallback | FIX | FR-19 all-negative-profile row: no special case, sort handles it. |
| §3.3 Default-launcher prompt missing | FIX | New FR-17 (non-onboarding banner). |
| §3.3 Losing default status | FIX | FR-17: banner re-appears on resume when not default. |
| §3.3 Work profile apps | **REJECT** | v0.1 shows alongside primary, no badging. Rationale: target persona Kai doesn't run a managed profile; adding filter logic now isn't worth the code. (§R-ACCEPT-1) |
| §3.3 `android:exported` Android 12+ | FIX | FR-14 mandates `android:exported="true"` on MainActivity. |
| §3.3 Wallpaper integration | FIX | NFR-12 explicitly ignores system wallpaper. |
| §3.3 Home-gesture collision | FIX | Swipe-up drawer gesture cut; button-only in v0.1 (FR-13, §7). |
| §3.3 Recent-apps / overview | **REJECT** | Android default overview behavior accepted as-is for v0.1. No launcher-custom overview. |
| §3.3 Lock-screen interactions | **REJECT** | Out of scope for v0.1; standard activity recreation is sufficient. |
| §3.4 TalkBack traversal | FIX | FR-18 specifies Hero card traversal order. |
| §3.4 Font scaling | FIX | FR-18: usable at 130%, Hero readable at 200%. |
| §3.4 Reduce motion | FIX | FR-18 reduce-motion clause collapses two animations to 0ms. |
| §3.4 Color-only signaling | FIX | FR-03 adds leading ✓ glyph on selected chip. |
| §3.4 ✕ touch target | FIX | FR-05 / FR-18 mandate ≥48dp. |
| §3.5 Bottom-sheet scrim | FIX | NFR-11 + FR-12 specify color. |
| §3.5 Status bar / nav bar | FIX | NFR-11. |
| §3.5 Dialogs | FIX | NFR-11 mandates Material 3 dark surface. |
| §3.5 Keyboard theming | **REJECT** | FR-15 no longer opens a keyboard (text input cut), so moot. |
| §3.5 App drawer background | FIX | NFR-11: same gradient as home. |
| §3.5 Toasts vs. Snackbars | FIX | NFR-11 mandates Snackbars everywhere. |
| §3.6 Dev-build seed hook | FIX | New FR-20 (debug-only dev panel, seed demo profile button). |
| §3.6 Logging requirement | FIX | FR-20 `FLYWHEEL` logcat tag. |
| §3.6 Deterministic RNG | FIX | FR-11 `@Named("recEngineRng") Random` injection; seeded in tests. |

### Over-scoped features (critique §4)

| Ref | Status | Resolution |
|---|---|---|
| §4.1 AI Dock placeholder | FIX (CUT) | FR-15 reduced to single non-interactive pill. (R-CUT-1 logged.) |
| §4.2 Keyboard-opening placeholders | FIX (CUT) | No keyboard in v0.1 (FR-15). |
| §4.3 Swipe-up drawer gesture | FIX (CUT) | Button only; gesture moved to v0.2 (FR-13, §7). (R-CUT-2) |
| §4.4 SCROLL_PAST signal | FIX (CUT) | Removed from §3 signal list and from FR-07. (R-CUT-1) |
| §4.5 Feedback event timeline | FIX (CUT) | FR-12 now shows top tags + counter only. (R-CUT-3) |
| §4.6 SM-07 as release gate | FIX | SM-07 moved to unit-test verification, not session-level. |
| §4.7 Live-install drawer refresh | FIX (CUT) | Moved to §7 hard exclusion. |

### Ambiguous requirements (critique §5 table)

| Row | Topic | Resolution |
|---|---|---|
| FR-03 | Last-selected persistence | FIX: always reset to 工作 on cold start (FR-03 + §7). |
| FR-04 | Hero always swap | FIX: always swap (FR-04). |
| FR-05 | 3–6 cards range | FIX: clamped to 4 deterministically (FR-05). |
| FR-06 | Session meaning | FIX: FR-06 + glossary. |
| FR-07 | Dwell semantics | FIX: full rewrite. |
| FR-08 | Action no-op text | FIX: Snackbar with literal `"${actionLabel} — 即将上线"` (FR-08). |
| FR-10 | `tag_count` meaning | FIX: worked example in FR-10 (card's tag count, not user-total). |
| FR-11 | Card-side weight | FIX: 1.0 implicit (FR-11). |
| FR-12 | Reset side-effects | FIX: 4-step flow (FR-12). |
| FR-13 | Back-nav "state" | FIX: intent + dismiss + scroll all preserved. |
| FR-14 | Home semantics | FIX: see C-4. |
| US-03 / FR-01 | whyLabel source | FIX: seed verbatim, no dynamic generation in v0.1. |
| FR-02 | Replace on 👍? | FIX: re-evaluate Hero on both 👍 and 👎; may or may not change. |
| US-19 | Header strings | FIX: 4 fixed strings defined in FR-04. |

### Seed data problems (critique §6)

| Ref | Status | Resolution |
|---|---|---|
| 1/2/1 split symmetric but limiting | **REJECT (partial)** | Kept for v0.1 simplicity; cross-card learning tested via unit tests (SM-07), not in-session. |
| `commute_02` hyper-specific route | FIX | Softened to "常用通勤路线" + "示例路线". |
| `work_03` time-stamped schedule | FIX | Reworded to "示例：3 个会议、2 个待办" (no real times). |
| `lunch_03` "12 点了" whyLabel | FIX | Changed to "午餐时间高频选择". |
| `lunch_04` fabricated "本周 4 天炸物" | FIX | Changed to "ε-greedy 探索 · 健康选项". |
| `rest_02` fabricated "这周听了 5 次" | FIX | Changed to "示例：休息时常听". |
| `rest_03` double-voice whyLabel | FIX | Simplified to "示例：冥想类高频选择". |
| Inconsistent whyLabel voice | FIX | New copy-voice rule in §9 + reword pass. |
| Brand-specific dead-end tags | **REJECT** | Kept as-is for v0.1; cross-card learning tested via synthetic seed profile (FR-20). Tag-vocabulary governance is v0.2. (R-ACCEPT-2) |
| `commute_04` "试听 3 分钟" implies real audio | FIX | Simplified to "试听" (FR-08 Snackbar handles the reality). |
| `work_04` fabricated "常用项目管理类" | FIX | Changed to "ε-greedy 探索 · 项目管理类". |
| No negative-tag test fixture | **REJECT** | v0.1 relies on unit-test synthetic profile (FR-20 demo seed) to exercise negative-tag paths, not the seed catalog itself. |
| Expand to 6–8 cards/intent | **REJECT** | See C-3 rationale; v0.2 expansion. |

### Measurability (critique §7)

| Ref | Status | Resolution |
|---|---|---|
| SM-01 Home-re-entry sub-step | FIX | SM-01 now tests FR-14 return from drawer. |
| SM-02 "visibly different" | FIX | ≥2 ID differences (SM-02). |
| SM-03 timeline dependency | FIX | DAO count + counter display, not timeline (SM-03). |
| SM-04 weak | FIX | Replaced (SM-04, critique C-6). |
| SM-05 "first paint" vacuous | FIX | "Hero content committed" (NFR-01, SM-05). |
| SM-06 device/state unspecified | FIX | NFR-07 + SM-06 specify Pixel 6 Android 14 fresh install, 3×, force-stop between runs. |
| SM-07 session sampling impossible | FIX | Moved to unit-test (SM-07). |
| SM-08 install-churn unstable | FIX | Drawer count == queryIntentActivities size at render (SM-08). |
| Missing: profile-reset metric | FIX | New SM-09. |
| Missing: dwell-signal metric | FIX | New SM-10. |
| Missing: ✕ dismiss metric | FIX | New SM-11. |
| Missing: cold-start empty-state metric | FIX | New SM-12. |

### Risks (critique §8)

Top-3 fixed: R-1 (dwell), R-2 (ε-greedy), R-3 (home re-entry) — all addressed via FR-07, FR-11, FR-14 rewrites.
R-4 (swipe collision), R-5 (default prompt), R-6 (seed copy), R-7 (dismiss), R-8 (a11y), R-10 (empty states), R-11 (formula), R-12 (dock), R-13 (reset race), R-15 (migration), R-17 (byte-for-byte), R-18 (toasts), R-20 (RNG): all fixed above.
R-14 (font scaling): addressed in FR-18 (usable at 130%, readable at 200%).
R-16 (tag vocabulary): **REJECTED for v0.1** — low severity, acknowledged debt for v0.2.
R-19 (work profile): **REJECTED for v0.1** — target persona unaffected.

### Summary counts

- **Critique items addressed (FIX):** 57
- **Critique items rejected-with-reason (REJECT):** 9
- **New FRs added in v2:** FR-17 (default banner), FR-18 (a11y), FR-19 (empty states), FR-20 (dev instrumentation)
- **v1 features cut in v2 (R-CUT):** SCROLL_PAST signal, feedback timeline, AI Dock text/mic/✦ (reduced to pill), swipe-up drawer gesture
- **v1 requirements explicitly accepted as debt (R-ACCEPT):** brand-specific tag dead-ends, catalog size 16, work-profile drawer inclusion

---

*End of PRD v2.*
