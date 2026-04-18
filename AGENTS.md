# AI Launcher MVP — Project Context for Agents

This file is loaded by every sub-agent working on this project.

## Project Identity
- **Name:** AI Launcher MVP
- **Type:** Android Launcher application
- **Repo:** https://github.com/bobpan/ai-launcher-mvp
- **Package ID:** `com.bobpan.ailauncher`
- **Min SDK:** 26 (Android 8.0) · **Target SDK:** 34 (Android 14)

## Tech Stack (LOCKED — do not deviate)
- **Language:** Kotlin 2.0.21
- **UI:** Jetpack Compose (BOM 2024.12.01) — Material 3
- **Architecture:** MVVM + single-activity, Compose Navigation
- **DI:** Hilt 2.52
- **Database:** Room 2.6.1 (for feedback events, user profile, cached cards)
- **Build:** Android Gradle Plugin 8.7.3, Gradle 8.11.1
- **Java:** 17

## Design Baseline (LOCKED)
The UI is **方案 D: AI Feed Launcher** — see `docs/DESIGN-v1.md`.

Core UI modules (from top to bottom of home screen):
1. **Header** — greeting + current context mode + profile badge
2. **Hero "FOR YOU" card** — AI's highest-confidence recommendation, with strong 👍👎 feedback
3. **Intent Chips strip** — 通勤 / 工作 / 午餐 / 休息 (selectable; switches feed)
4. **Discover Feed** — TikTok-style card stream with implicit + light-dismiss feedback
5. **AI Dock** — bottom input bar (text + voice + ✦ AI icon)

## Feedback Flywheel (core innovation)
Every card emits signals:
- Explicit: 👍, 👎, ✕ (dismiss)
- Implicit: dwell time, tap-through, scroll-past
- Action: executed recommendation (e.g. "下单美式" tapped)

Signals flow into `FeedbackRepository` → `UserProfile` → `RecommendationEngine` (stub in MVP using heuristics + multi-armed-bandit ε-greedy).

## MVP Scope (v0.1)
✅ IN SCOPE:
- Home screen with all 5 modules above (fully styled per design)
- Mock data layer — hardcoded seed cards for 4 intents
- Room persistence for feedback events + user profile
- Intent switching changes feed content
- Feedback buttons update local profile (visible in a debug "Profile v47" bottom sheet)
- Recommendation stub: ε-greedy over tagged cards, biased by profile
- Launcher intent-filter so it can actually be set as default launcher
- App drawer (basic grid, accessible via swipe up or dock button)

❌ OUT OF SCOPE (for v0.1):
- Real LLM calls
- Cloud sync
- Real-time data (calendar/weather) — use mock
- Widgets
- Multi-user
- Accessibility service integration

## Module Structure
```
app/src/main/java/com/bobpan/ailauncher/
  ├── MainActivity.kt              — single activity, Compose root
  ├── di/                          — Hilt modules
  ├── data/
  │   ├── db/                      — Room database, DAOs, entities
  │   ├── model/                   — domain models (Card, Intent, Signal, Profile)
  │   ├── seed/                    — hardcoded seed cards
  │   └── repo/                    — FeedbackRepository, CardRepository, ProfileRepository
  ├── domain/
  │   └── recommend/               — RecommendationEngine (ε-greedy stub)
  ├── ui/
  │   ├── theme/                   — Color, Type, Theme (dark-first, glassmorphic)
  │   ├── home/                    — HomeScreen + HomeViewModel
  │   ├── components/              — HeroCard, IntentChip, DiscoverCard, AiDock
  │   ├── profile/                 — ProfileDebugSheet
  │   └── drawer/                  — AppDrawerScreen
  └── util/                        — extensions, preview helpers
```

## Style Direction
- **Dark-first** color palette (bg #0a0f1e → #161a2e gradient)
- **Glassmorphic** cards (blur + 4-8% white overlay + subtle gradient borders)
- **Ambient light** radial gradients (blur 55px) for depth
- Primary accent: #7CB4FF (blue) · Secondary accent: #FF7EC8 (pink) · Positive: #64E6A0

## Git Discipline
- Commit after every meaningful unit (don't batch)
- Conventional commits: `type(scope): description`
- Branch: `main` (direct commits OK for MVP)

## When You Run Out of Context
Read these in order:
1. `docs/PRD-v2.md` — what we're building and why
2. `docs/DESIGN-v2.md` — visual spec and user flows
3. `docs/ARCHITECTURE-v2.md` — technical decisions
4. This file (`AGENTS.md`) — project invariants
