# PRD v1 — AI Launcher MVP (v0.1)

**Status:** Approved for build · **Owner:** PM · **Last updated:** 2026-04-18
**Target release:** v0.1 (MVP, single build)
**Companion docs:** `AGENTS.md` (invariants), `docs/DESIGN-v1.md` (visual spec), `docs/ARCHITECTURE-v1.md` (tech decisions)

---

## 1. Product Overview

**Elevator pitch.** AI Launcher is an Android home screen that replaces the grid-of-icons metaphor with a **personalized AI feed**. Instead of hunting through app drawers, the user lands on a "FOR YOU" hero card — the one thing the AI believes they want to do *right now* — followed by a TikTok-style Discover feed tuned to their current intent (通勤 / 工作 / 午餐 / 休息). Every 👍, 👎, ✕, tap, and dwell feeds a local user profile that sharpens tomorrow's recommendations. The launcher gets smarter the more you use it, and — critically — it shows you *exactly* what it has learned, editable in a transparent "Profile v47" sheet.

**Who is this for (persona).**
> **"Kai" — 28, product designer in Shanghai. Android power user. Has 140+ apps installed, uses ~12 regularly. Already tried Niagara, Nothing Launcher, and Smart Launcher but abandoned them because "they're still just grids with extra steps." Cares about aesthetics (glassmorphic, dark mode), cares about privacy (doesn't want another cloud account), and is AI-curious but skeptical of black-box recommendations. Wants a phone that feels like a *concierge*, not a *filing cabinet*.**

Secondary persona: Android developers and prosumers who want to dogfood an AI-native launcher and are comfortable side-loading an APK.

---

## 2. Problem Statement

From Kai's point of view, traditional launchers fail in four ways:

1. **Grid-of-icons is a filing system, not a decision system.** The launcher knows nothing about context. At 8:47 AM it shows the same 5×6 grid as at 11:47 PM. The user does all the cognitive work of deciding "what do I want to do?"
2. **Shortcuts don't scale.** Folders, hotseats, and "smart suggestions" rows in Pixel Launcher top out at ~6 slots and require manual curation. After 30 apps, the grid wins.
3. **Existing "AI" launchers are opaque.** Pixel's suggestion row is a black box — no explanation, no correction mechanism. If it suggests the wrong app, the user has no way to tell it "no, never suggest this at 9 AM."
4. **No feedback loop.** Tapping an app tells the launcher nothing actionable. The user cannot thumb-up, thumb-down, or dismiss a suggestion. The launcher never improves beyond day one.

The user's unmet need: **"Show me the one thing I probably want to do right now, let me correct you when you're wrong, and get noticeably better at it within a week."**

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
2. **Every signal has a weight:** 👍 = +3, tap-through = +2, long dwell (>2s) = +1, scroll-past = −0.5, ✕ dismiss = −2, 👎 = −3. Weights are applied to the card's tags in the user profile.
3. **Exploration is mandatory.** The recommendation engine runs ε-greedy with ε = 0.2 — 80% of feed slots go to the profile's top-ranked cards, 20% go to random unseen cards. This prevents filter-bubble collapse.
4. **Transparency is mandatory.** The user can open Profile v47 at any time and see the top-weighted tags, the most recent signals, and a "reset" button. If we cannot explain a recommendation, we cannot ship it.

MVP note: the "AI" in step ① is a deterministic ε-greedy stub over the 16 seed cards. No LLM, no cloud. The flywheel mechanics are real; the intelligence is faked. This is intentional — we ship the loop first, plug in a model later.

---

## 4. User Stories

### Home screen discovery
1. As Kai, I want to see a single "FOR YOU" hero card the moment I unlock my phone, so that I don't have to decide what to do next.
2. As Kai, I want the hero card to show a title, one-line description, an icon/emoji, and a clear primary action button, so that I can act in one tap.
3. As Kai, I want to see a "why recommended" micro-label on the hero card (e.g. "基于你最近的咖啡习惯"), so that I trust the recommendation.
4. As Kai, I want a horizontally scrollable strip of 4 intent chips (通勤 / 工作 / 午餐 / 休息) under the hero, so that I can declare my current context.
5. As Kai, I want a vertically scrolling Discover feed of 3-6 cards below the chips, so that I can browse alternatives if the hero misses.
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
15. As Kai, I want to see a timeline of the last 20 feedback events, so that I can audit what I signaled.
16. As Kai, I want a "Reset profile" button in the sheet, so that I can start fresh if the AI gets stuck in a bad loop.

### Intent switching
17. As Kai, I want tapping an intent chip to immediately replace the Discover feed with cards tagged for that intent, so that I can switch context in one tap.
18. As Kai, I want the currently active intent chip to be visually distinct (filled accent color, glow), so that I always know what mode I'm in.
19. As Kai, I want the header greeting to reflect the active intent (e.g. "工作模式 · 14:22"), so that the context is always reinforced.

### App access
20. As Kai, I want to swipe up from the bottom of the home screen to open a full-screen app drawer, so that I retain traditional launcher access.
21. As Kai, I want the app drawer to be an alphabetical grid of all installed apps with icons and labels, so that I can find any app without the AI's help.
22. As Kai, I want a text input in the AI Dock at the bottom, so that I can eventually type natural-language queries (v0.1: visible but non-functional placeholder is acceptable).
23. As Kai, I want a voice mic button in the AI Dock, so that the product's direction toward voice is visible (v0.1: visible but non-functional placeholder is acceptable).

### Launcher integration
24. As Kai, I want to set AI Launcher as my default Android launcher via the system chooser, so that pressing Home opens it.
25. As Kai, I want the launcher to respond correctly to the Home button (re-entering the home screen, not restarting the app), so that it behaves like a real launcher.
26. As Kai, I want my feedback history to survive a phone reboot, so that the AI doesn't forget me overnight.
27. As Kai, I want the launcher to cold-start in under half a second, so that it feels faster than stock.
28. As Kai, I want newly installed apps to appear in the drawer without a manual refresh, so that the drawer stays current. *(v0.1: accepted if they appear after relaunch; live PackageManager broadcast is nice-to-have.)*

---

## 5. Functional Requirements

Each FR is independently testable. Acceptance criteria use "Given / When / Then."

### FR-01 · Hero "FOR YOU" card rendering
**Description.** A single large glassmorphic card at the top of the home screen rendering the currently highest-ranked card for the active intent.
**Acceptance.**
- Given the app has launched and an intent is active, when the home screen renders, then a HeroCard displays the top-ranked card's title, description, emoji/icon, action label, and "why recommended" label.
- The card has two visible buttons: 👍 and 👎.
- The card uses the glassmorphic style (blur, 4–8% white overlay, gradient border) per DESIGN-v1.md.

### FR-02 · Hero card explicit feedback
**Description.** 👍 and 👎 buttons on the hero card persist a feedback event and trigger re-ranking.
**Acceptance.**
- Given the hero card is visible, when the user taps 👍, then a `FeedbackEvent(cardId, signal=THUMB_UP, weight=+3, timestamp)` is written to Room within 50ms, the profile's tag weights are updated, and a brief confirmation animation plays.
- 👎 behaves identically with `signal=THUMB_DOWN, weight=-3`, and the hero card is replaced with the next-ranked card within 300ms.

### FR-03 · Intent chip strip
**Description.** A horizontal row of exactly 4 chips labeled 通勤 / 工作 / 午餐 / 休息, below the hero card.
**Acceptance.**
- Given the home screen is rendered, when the user views the chip strip, then 4 chips appear in the order: 通勤, 工作, 午餐, 休息.
- Exactly one chip is always selected; the default at cold start is 工作.
- The selected chip has filled accent (#7CB4FF) background; unselected chips have a glass outline.

### FR-04 · Intent chip switching
**Description.** Tapping a chip changes the active intent and refreshes both the hero card and the Discover feed.
**Acceptance.**
- Given the user is on the home screen, when they tap a non-selected chip, then within 200ms the hero card and Discover feed both re-render using cards tagged with that intent, and the header greeting updates to reflect the new mode.

### FR-05 · Discover feed rendering
**Description.** A vertically scrolling LazyColumn of 3–6 DiscoverCards below the chip strip, populated from the seed data filtered by active intent.
**Acceptance.**
- Given an intent is active, when the feed renders, then between 3 and 6 cards tagged for that intent appear in ε-greedy order (80% by profile rank, 20% random).
- Each card shows: title, description, card-type badge (continue / discover / new), icon, action label, ✕ dismiss button.

### FR-06 · Discover card dismiss (✕)
**Description.** Tapping ✕ removes the card from the current feed session and records a dismiss signal.
**Acceptance.**
- When the user taps ✕ on a Discover card, a `FeedbackEvent(signal=DISMISS, weight=-2)` is persisted, the card animates out (200ms slide+fade), and the feed re-flows without that card for the remainder of the session.

### FR-07 · Implicit dwell tracking
**Description.** The system measures how long each card is at least 50% visible in the viewport and records a dwell signal on scroll-away.
**Acceptance.**
- When a card exits the viewport after being ≥50% visible for more than 2000ms, a `FeedbackEvent(signal=DWELL, weight=+1)` is persisted.
- When a card exits after <500ms visibility, a `FeedbackEvent(signal=SCROLL_PAST, weight=-0.5)` is persisted.

### FR-08 · Card action tap-through
**Description.** Tapping the action button on any card records the strongest positive signal.
**Acceptance.**
- When the user taps a card's action label/button, a `FeedbackEvent(signal=ACTION_TAP, weight=+2)` is persisted. (MVP: the action itself may be a no-op toast like "Would open Luckin Coffee"; the signal still fires.)

### FR-09 · Feedback event persistence (Room)
**Description.** All feedback events persist to a Room database with full crash/reboot durability.
**Acceptance.**
- Every feedback event is written via `FeedbackRepository` to a Room table `feedback_events` with columns: `id` (PK autogen), `card_id`, `intent`, `signal`, `weight`, `timestamp_ms`.
- Writes complete in <50ms on a Pixel 6 (median of 100 inserts).
- After killing the process and relaunching, all previously recorded events are queryable.

### FR-10 · User profile derivation
**Description.** A `UserProfile` table stores the running sum of weights per tag, updated transactionally with every feedback event.
**Acceptance.**
- Given any feedback event, when it is persisted, then for each tag on the referenced card, `user_profile.weight` for that tag is incremented by `event.weight / tag_count` in the same DB transaction.
- A query of the profile's top 10 tags by weight returns in <50ms.

### FR-11 · ε-greedy recommendation engine
**Description.** A pure-Kotlin `RecommendationEngine` ranks seed cards for an intent using profile weights, with 20% exploration.
**Acceptance.**
- Given a call to `recommend(intent, count)`, 80% of returned slots are filled with cards sorted by `Σ(card.tag_weight × profile.tag_weight)` descending; 20% are filled with random cards tagged for that intent that are not already in the exploitation set.
- With an empty profile, the engine returns cards in seed-declaration order (deterministic cold-start).
- The engine is synchronous, in-memory, and contains zero network calls.

### FR-12 · Profile v47 debug sheet
**Description.** A Material 3 ModalBottomSheet, launched from the header profile badge, showing the user's learned profile in a human-readable form.
**Acceptance.**
- Tapping the profile badge in the header opens a bottom sheet titled "Profile v47".
- The sheet displays: (a) the top 10 tags by weight, with numeric scores; (b) a scrollable list of the 20 most recent feedback events with card title, signal emoji, and relative timestamp; (c) a "Reset profile" button that clears both `user_profile` and `feedback_events` tables after a confirmation dialog.
- The sheet respects the dark glassmorphic theme.

### FR-13 · App drawer
**Description.** A full-screen alphabetical grid of all launchable installed apps, reachable via swipe-up from the home screen or a button in the AI Dock.
**Acceptance.**
- A vertical swipe up of ≥120dp from the home screen's lower half navigates to `AppDrawerScreen`.
- The drawer lists every app returned by `PackageManager.queryIntentActivities(ACTION_MAIN, CATEGORY_LAUNCHER)`, sorted A–Z by label, as a 4-column grid with icon + label.
- Tapping an app launches it via its main intent.
- A back gesture/button returns to the home screen without restarting state.

### FR-14 · Launcher intent-filter + default-launcher eligibility
**Description.** The app's `MainActivity` registers as an Android launcher so the user can select it from Settings → Default apps → Home app.
**Acceptance.**
- `AndroidManifest.xml` declares on `MainActivity`: `<category android:name="android.intent.category.HOME" />` and `<category android:name="android.intent.category.DEFAULT" />` on an intent filter with `<action android:name="android.intent.action.MAIN" />`.
- After install on a Pixel 6 (Android 14), the user can select "AI Launcher" in the system home-app picker, and pressing Home subsequently opens our `MainActivity`.
- On Home press while our app is already foreground, we return to the home screen (not the previous in-app screen).

### FR-15 · AI Dock (v0.1 placeholder)
**Description.** A persistent bottom bar with a text field, voice mic icon, and ✦ AI icon, rendered but non-functional in v0.1.
**Acceptance.**
- The dock is always visible at the bottom of the home screen above the system nav bar.
- Tapping the text field shows keyboard but submitting does nothing (or shows a toast "Coming soon").
- Tapping the mic or ✦ icon shows a toast "Coming soon" and records no signal.

### FR-16 · Seed data load
**Description.** The 16 seed cards defined in §9 are loaded into memory (and optionally into a Room `cards` cache table) on first app launch.
**Acceptance.**
- On cold start with no prior data, `CardRepository.getAll()` returns exactly 16 cards matching §9 byte-for-byte in title, description, intent, type, icon, actionLabel, whyLabel.

---

## 6. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Performance | Cold-start first paint of HomeScreen ≤ **500ms** on Pixel 6 (Android 14). Measured from `onCreate` to first `setContent` frame committed. |
| NFR-02 | Performance | All Room queries used in the main UI path complete in ≤ **50ms** at the 95th percentile on Pixel 6. |
| NFR-03 | Performance | Intent-chip switch (FR-04) re-renders feed within **200ms**. |
| NFR-04 | Offline-first | The entire app functions with airplane mode enabled. Zero network calls in v0.1. |
| NFR-05 | Battery | **No background work.** No `WorkManager`, no `JobScheduler`, no services, no foreground services. All work happens while the activity is resumed. |
| NFR-06 | Privacy | 100% of user data — feedback events, profile, cards — is stored locally in Room. No analytics SDK, no crash reporter, no telemetry in v0.1. |
| NFR-07 | Stability | Zero crashes across the "golden path" QA script (launch → switch each intent → 10 feedback actions → open profile → open drawer → reboot → relaunch). |
| NFR-08 | Accessibility | All interactive targets are ≥48dp. Content descriptions on all icon buttons. (Full a11y is deferred; this is the minimum bar.) |
| NFR-09 | Theming | Dark-first only in v0.1. Light mode deferred. |
| NFR-10 | APK size | Release APK ≤ 15 MB. |

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
- ❌ Actual execution of card actions (tapping "下单美式" fires a signal + toast, does not open Luckin)
- ❌ Notifications, notification badges
- ❌ Gestures beyond swipe-up-for-drawer (no double-tap-to-lock, etc.)
- ❌ Onboarding flow (first launch goes straight to home)
- ❌ Settings screen (reset lives in Profile v47 sheet; nothing else is configurable)
- ❌ Localization — Simplified Chinese strings only in v0.1 (English strings where they already appear in design, but no i18n infrastructure)
- ❌ Tablet / foldable adaptive layouts

---

## 8. Success Metrics

MVP is considered successful when **all** of the following are verifiable on a Pixel 6 (Android 14) release build:

| ID | Metric | Target | How to verify |
|---|---|---|---|
| SM-01 | App can be set as default launcher | ✅ | Settings → Apps → Default apps → Home app shows "AI Launcher"; selecting it and pressing Home opens our app. |
| SM-02 | All 4 intent chips switch feed content | ✅ | Tapping each of 通勤 / 工作 / 午餐 / 休息 produces a visibly different set of Discover cards matching §9 seed data. |
| SM-03 | Feedback persists across restart | ✅ | Apply 5× 👍 on specific cards → force-stop app → relaunch → Profile v47 sheet still shows the 5 events and updated tag weights. |
| SM-04 | Profile visibly updates after ≥5 signals | ✅ | After 5 mixed feedback actions, at least one tag weight in Profile v47 has changed from its post-first-launch value; top-10 tag ordering reflects the signals. |
| SM-05 | First-paint performance | <500ms | Systrace / Macrobenchmark from `onCreate` to first committed frame, median of 10 cold starts. |
| SM-06 | Zero crashes on golden path | 0 | QA script (see NFR-07) runs clean three times consecutively. |
| SM-07 | ε-greedy exploration verified | ✅ | With a strong profile bias (20+ signals toward one card), a 100-card feed sample still shows ≥15% non-top-ranked cards appearing (20% ± tolerance). |
| SM-08 | App drawer functional | ✅ | Swipe-up opens drawer; every installed launchable app appears; tapping launches it. |

---

## 9. MVP Seed Data Spec

**The dev agent must use this verbatim.** 16 cards total: 4 intents × 4 cards. Each card has a stable `id` for feedback attribution.

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

### Intent: 通勤 (COMMUTE)

| id | title | description | type | icon | actionLabel | whyLabel | tags |
|---|---|---|---|---|---|---|---|
| `commute_01` | 继续听《三体》 | 昨天停在第 14 章，还剩 23 分钟 | CONTINUE | 🎧 | 继续播放 | 你昨天通勤时在听 | `[audiobook, scifi, commute, morning]` |
| `commute_02` | 今日地铁 2 号线畅通 | 人民广场 → 陆家嘴 预计 18 分钟 | DISCOVER | 🚇 | 查看路线 | 你的常用通勤路线 | `[transit, subway, commute, morning]` |
| `commute_03` | 新闻早报 · 3 分钟 | 科技、财经、本地头条速览 | DISCOVER | 📰 | 开始播报 | 你早上常点新闻类 | `[news, briefing, commute, morning]` |
| `commute_04` | 试试播客《硅谷早知道》 | 你没听过，AI 觉得你会喜欢 | NEW | 🎙️ | 试听 3 分钟 | ε-greedy 探索 · 匹配你的科技偏好 | `[podcast, tech, commute, explore]` |

### Intent: 工作 (WORK)

| id | title | description | type | icon | actionLabel | whyLabel | tags |
|---|---|---|---|---|---|---|---|
| `work_01` | 继续 Figma 稿件 | "Launcher v2 - Home" 3 小时前编辑 | CONTINUE | 🎨 | 打开 Figma | 你最近的工作文件 | `[design, figma, work, focus]` |
| `work_02` | 开启专注模式 45 min | 屏蔽通知、白噪音、番茄钟 | DISCOVER | 🎯 | 开始专注 | 你工作日下午常用 | `[focus, pomodoro, work, afternoon]` |
| `work_03` | 今日日程 · 3 个会议 | 10:30 评审 · 14:00 1:1 · 16:00 站会 | DISCOVER | 📅 | 查看日程 | 工作模式常看 | `[calendar, meeting, work]` |
| `work_04` | 试试 Linear 快速建单 | 你用 Jira，AI 猜你会更喜欢 Linear | NEW | 📋 | 了解一下 | ε-greedy 探索 · 你常用项目管理类 | `[productivity, pm, work, explore]` |

### Intent: 午餐 (LUNCH)

| id | title | description | type | icon | actionLabel | whyLabel | tags |
|---|---|---|---|---|---|---|---|
| `lunch_01` | 再来一杯瑞幸生椰拿铁 | 你这周点了 3 次，15 元券可用 | CONTINUE | ☕ | 一键下单 | 你的高频选择 | `[coffee, luckin, lunch, habit]` |
| `lunch_02` | 公司楼下云海肴 · 20% off | 步行 4 分钟，人均 58 元 | DISCOVER | 🍲 | 查看菜单 | 午餐时间 · 你附近的新优惠 | `[restaurant, lunch, nearby, deal]` |
| `lunch_03` | 叫个外卖 · 10 分钟送达 | 你常点的 3 家都在营业 | DISCOVER | 🛵 | 打开美团 | 12 点了，该吃饭了 | `[delivery, meituan, lunch]` |
| `lunch_04` | 试试今日轻食套餐 | 鸡胸肉 + 藜麦碗，AI 觉得你该换换 | NEW | 🥗 | 看看 | ε-greedy 探索 · 本周已连续 4 天炸物 | `[salad, healthy, lunch, explore]` |

### Intent: 休息 (REST)

| id | title | description | type | icon | actionLabel | whyLabel | tags |
|---|---|---|---|---|---|---|---|
| `rest_01` | 继续《漫长的季节》E08 | 剩 42 分钟 · 昨晚停在 18:23 | CONTINUE | 📺 | 继续看 | 你昨晚在追 | `[tv, drama, rest, evening]` |
| `rest_02` | 放首《夜的第七章》 | 周杰伦 · 你这周听了 5 次 | DISCOVER | 🎵 | 播放 | 休息时你常听 | `[music, jay, rest]` |
| `rest_03` | 10 分钟身体扫描冥想 | 睡前放松，AI 猜你今天有点累 | DISCOVER | 🧘 | 开始 | 你周三常用冥想类 | `[meditation, wellness, rest, evening]` |
| `rest_04` | 试试《塞尔达》王国之泪 | 你没玩过，AI 觉得你会上头 | NEW | 🎮 | 了解 | ε-greedy 探索 · 你最近玩解谜类 | `[game, zelda, rest, explore]` |

**Parsing notes for the dev agent:**
- Store these in `data/seed/SeedCards.kt` as a `val SEED_CARDS: List<SeedCard>` — hardcoded, no JSON.
- The `tags` column values (lowercase, kebab-free) are also the keys used in the `user_profile` weight table.
- `intent` values map to the enum: `COMMUTE=通勤, WORK=工作, LUNCH=午餐, REST=休息`.
- `CardType.NEW` cards are the ε-greedy exploration seeds — the engine should weight them as the "random 20%" even before the profile has data.

---

## 10. Tech Constraints (reiterated from AGENTS.md — do not contradict)

- **Language:** Kotlin 2.0.21. No Java sources except auto-generated.
- **UI:** Jetpack Compose, Compose BOM 2024.12.01, Material 3. No XML layouts except `AndroidManifest.xml`.
- **Architecture:** MVVM. **Single activity** (`MainActivity`) + Compose Navigation.
- **DI:** Hilt 2.52 for all repositories, the recommendation engine, and ViewModels.
- **Database:** Room 2.6.1. Tables: `feedback_events`, `user_profile`, `cards` (cache).
- **Build:** AGP 8.7.3, Gradle 8.11.1, JDK 17.
- **Package:** `com.bobpan.ailauncher`.
- **SDK range:** minSdk 26, targetSdk 34.
- **Theme:** Dark-first, glassmorphic. Primary `#7CB4FF`, secondary `#FF7EC8`, positive `#64E6A0`, bg gradient `#0a0f1e → #161a2e`.
- **Module layout:** exactly as specified in AGENTS.md `## Module Structure`.
- **No network dependencies** in the app module in v0.1 (OkHttp/Retrofit/Ktor are forbidden).

---

## Appendix A · Open Questions (non-blocking for v0.1)

1. Should dwell tracking use `onGloballyPositioned` + viewport math, or Compose's `LazyListState` first/last-visible indices? **Decision deferred to dev — pick whichever ships cleaner; must satisfy FR-07.**
2. What happens when the user has <4 launchable apps (e.g. fresh emulator)? **Decision: drawer shows what exists; no empty-state illustration required in v0.1.**
3. Do we need a "Profile v47" version bump mechanism? **No. The "v47" is stylistic/narrative — it's literally the string "Profile v47" in v0.1. Future versioning is v0.2+.**

## Appendix B · Glossary

- **Hero card** — the single large card at the top of the home screen.
- **Discover feed** — the vertical scroll of smaller cards below the intent chips.
- **Intent** — one of 4 user-declared context modes (通勤/工作/午餐/休息).
- **Signal** — a single user interaction with a card producing a weighted feedback event.
- **Profile v47** — the debug bottom sheet exposing learned tag weights + signal history.
- **ε-greedy** — exploration/exploitation ratio for recommendation; ε = 0.2 in MVP.
