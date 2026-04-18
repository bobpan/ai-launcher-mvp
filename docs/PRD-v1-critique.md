# PRD v1 — Critical Review

**Reviewer:** Devil's-Advocate Agent · **Date:** 2026-04-18
**Target PRD:** `docs/PRD-v1.md` · **Scope:** content only (tech stack + design baseline in `AGENTS.md` are locked and off-limits)

---

## 1. Executive Verdict

**Not shippable as-is. Revise to v2 before a single line of Phase 3 code is written.**

The PRD is well-structured, the flywheel narrative is crisp, and the seed data is usable — but it is a *happy-path* document. Roughly 30% of the surface area that a launcher must actually handle is either under-specified or entirely missing: first-run empty-profile behavior, "no cards left after dismiss" states, default-launcher re-entry semantics, dwell-tracking definition-of-truth, the exact math of the ε-greedy engine, and the Profile v47 reset transaction. Any competent dev will ship *something* from this spec, but two devs will ship **two very different apps** — which guarantees Phase 4 QA churn and at least one painful re-architecture.

**The single biggest risk:** FR-07 (dwell tracking) + FR-11 (ε-greedy) are the entire moat of the product and both are under-specified to the point that the flywheel demo in SM-04/SM-07 can be gamed or trivially broken. If the flywheel doesn't *visibly* work on day one, we've shipped "another launcher with a bottom sheet."

---

## 2. Critical Challenges

Ordered by how hard they'll bite in Phase 3/4.

### C-1 · FR-07 "dwell tracking" is the flywheel's load-bearing wall and it is vague
The only visibility metric the user never consciously provides. FR-07 says "≥50% visible for >2000ms → +1" and "<500ms → −0.5" but:
- What about the **500ms–2000ms** band? Dead zone? Neutral? The PRD literally has a gap in the signal space.
- Is the timer paused when the screen is off? When the drawer is open on top? When Profile v47 sheet covers the card?
- Does re-entering the viewport (scroll back up) re-arm a fresh dwell event, or is it one event per session per card?
- The Hero card is *always* ≥50% visible at render — does it emit a DWELL every 2s forever? This alone will dominate the feedback table.
- LazyColumn recycles composables; `onGloballyPositioned` fires on layout, not on visibility. The implementation choice (Appendix A.1) is punted to dev — but the **semantics** are also punted, which is the real bug.

**Consequence:** two devs implement this two ways; SM-04 is non-reproducible; the profile will either be dominated by Hero-dwell noise or be nearly empty. This is the #1 thing to fix in v2.

### C-2 · FR-11 ε-greedy math is ambiguous and under-constrained
"80% by profile rank, 20% random" sounds clean, but:
- What is `card.tag_weight`? The PRD never defines the card-side weight — only `profile.tag_weight`. Is every tag on a card weighted 1.0? Do `NEW`-type cards get a bonus? The scoring formula `Σ(card.tag_weight × profile.tag_weight)` references a value with no source.
- With 4 cards per intent and `count` likely 3–6, "20% of 4 slots" is 0.8 → round to 1? Round to 0? The PRD doesn't say. In practice exploration will be 0 or 25% per render, never 20%.
- SM-07 wants "100-card feed sample" — but the feed is 3–6 cards per session. Sample across sessions? Across intent switches? The only way to generate 100 samples is to re-call `recommend()` 20+ times, which re-seeds the RNG each call. The metric is not verifiable without a dev-only test hook that isn't specified.
- "Cards tagged for that intent that are not already in the exploitation set" — with only 4 cards per intent, after the top-3 are exploited there is exactly 1 candidate for exploration. ε-greedy with `|candidates|=1` is not exploration, it's "always pick the last card."
- Parsing note 4 says `CardType.NEW` cards "should be weighted as the random 20% even before the profile has data" — this *contradicts* FR-11's cold-start clause ("seed-declaration order"). Which wins?

**Consequence:** the engine is under-specified at exactly the level of detail a unit test needs. Phase 3 will write it, Phase 4 will fail SM-07, Phase 5 will rewrite it.

### C-3 · The 4-cards-per-intent catalog is too small to prove anything
16 cards, 4 per intent. After the user dismisses 1 and dislikes 1, the feed has 2 cards. After a week of use, every card has been seen ~50 times and the profile is a flat line. The flywheel's entire promise — "noticeably better within a week" (§2) — is **untestable with this seed size**. The demo will feel repetitive within 10 minutes.

**Consequence:** the #1 "wow" moment (personalization visibly changing the feed) cannot happen. Either expand the seed to 8–12 cards/intent or explicitly scope-down the pitch.

### C-4 · User Story 25 ("Home button re-entry") vs. FR-14 semantics is a trap
FR-14 says "On Home press while our app is already foreground, we return to the home screen (not the previous in-app screen)." This is the single most-frequently-broken launcher behavior in side-project launchers. The PRD does not say:
- What does "return to the home screen" mean when Profile v47 bottom sheet is open? Close it?
- When the app drawer is open? Drop back to home, or stay in drawer? (Users expect Home-from-drawer → home.)
- Does the back stack reset? Navigation state preserved?
- Compose Navigation + single activity + `launchMode=singleTask` interaction with `HOME` category is notoriously finicky — there is zero guidance here.

**Consequence:** this is the kind of bug that makes users uninstall after one day. Needs a dedicated FR, not a one-liner inside FR-14.

### C-5 · FR-06 dismiss "for the remainder of the session" has no session definition
"The feed re-flows without that card for the remainder of the session." What is a session?
- Until app is force-stopped?
- Until the intent chip is changed?
- Until tomorrow?
- Until reboot (then FR-09 says feedback persists — does the dismiss persist as a *hide*, or only as a signal?)

If "session" = process lifetime, then after cold-start every dismissed card is back. User dismisses `lunch_04` five days running, the profile weight goes to −10, but it *still shows up* as the hero next morning because exploitation picks the highest-scoring remaining card. The flywheel looks broken even though it's "working."

**Consequence:** the most visceral negative signal (dismiss) has the least durable behavioral effect. Users will notice immediately.

### C-6 · "Profile visibly updates after ≥5 signals" (SM-04) is gameable and weak
- 5× 👍 on the same card updates the same tags — the ordering may or may not change depending on tag overlap with other cards. The metric says "at least one tag weight has changed" — trivially true after 1 signal. "Top-10 tag ordering reflects the signals" — reflects *how*? Monotonically? By how much?
- There's no metric for the Hero card actually *changing* to a different card after strong negative feedback — which is the demo the user expects.

**Consequence:** we can pass SM-04 and still ship a launcher that feels static.

### C-7 · FR-16 "byte-for-byte" seed match is over-constrained
"Returns exactly 16 cards matching §9 byte-for-byte in title, description, intent, type, icon, actionLabel, whyLabel." This locks copy changes behind a spec revision. Expect at least 3 cards to have typos or better copy discovered during Phase 4 UI review. Is the QA gate really going to fail because someone fixed "叫个外卖" → "点个外卖"?

**Consequence:** unnecessary Phase 4 ceremony. Either soften to "matches §9 with any copy deltas ≤ line-level" or accept that seed data is source-of-truth and lives in code, not PRD.

### C-8 · User Story 26 ("feedback survives reboot") + FR-09 — no migration story
Room version = 1 in v0.1. Fine. But there's no statement of what happens on schema upgrade, nor a "wipe on version mismatch" fallback. A single Phase 5 schema change will nuke every beta tester's profile unless migrations are written, which the PRD doesn't require. The honest answer is probably "destructive migration is acceptable in v0.1" — but **say it**.

---

## 3. Missing Requirements

Things that should be FRs, NFRs, or §7 exclusions but currently are none of the above.

### 3.1 Error states / degenerate states
- **Empty feedback table + empty profile (cold start):** FR-11 says "seed-declaration order." What does the Hero show? `commute_01` because default intent is `工作` and … wait, default is `工作` per FR-03, so Hero should be `work_01`. Confirm. What's the `whyLabel`? The seed says "你最近的工作文件" — a lie on first launch.
- **Corrupt Room DB:** what if the DB file is corrupt on launch? Fall back to empty state? Crash? (Today: Hilt will inject a broken DB and we'll crash in `HomeViewModel.init`.)
- **Profile with every tag at negative weight:** exploitation returns nothing above 0. Does the engine fall back to random? To seed order? Undefined.
- **All cards for an intent are dismissed in this session:** FR-05 says "3–6 cards." What if 0 remain? Empty state? Reset dismiss list? Show exploration-only?
- **Room write failure (disk full):** no requirement for graceful degradation.
- **FR-06 dismiss of the *only* card of an intent:** hero AND feed both empty.
- **Profile v47 reset race:** FR-12 says reset clears `user_profile` and `feedback_events`, but nothing about the currently-rendered feed. Does the user see the same cards (still cached in VM state) with now-stale "why" labels? Does the UI auto-refresh?

### 3.2 Feedback flywheel edge cases
- **Spam 👍 on the same card:** no debounce, no rate limit, no cap. Kai taps 👍 30 times → `coffee` tag weight = +90 → lunch feed is permanently `lunch_01`. No requirement to dampen duplicate signals.
- **Rapid intent switching:** user taps all 4 chips in 2 seconds. Does each chip-switch fire implicit dwell signals on the cards that were visible for <500ms? Per FR-07, yes → every chip tap pollutes the profile with 3–6 SCROLL_PAST events. Intent-switching becomes a *negative* signal for cards the user never even looked at.
- **Dislike-only user:** 20 × 👎 → every tag weight < 0 → exploitation set is empty / negative. Fallback behavior undefined (see 3.1).
- **Thumb-up then thumb-down on same card without reload:** is +3 then −3 net zero, or is the second press ignored? No requirement for toggle vs. accumulate.
- **Dismiss then scroll back up and re-see the card (if re-flowed):** FR-06 says "feed re-flows without that card" so this shouldn't happen, but again — session definition is fuzzy.
- **Action-tap from Profile v47's feedback timeline?** Not specified; probably not a thing, but worth an explicit "timeline items are non-interactive."

### 3.3 Android launcher realities
- **Default launcher prompt UX:** user installs APK, opens app — it is NOT the default launcher. Do we surface an in-app CTA ("Set as default")? The PRD says no onboarding (§7) — but then how does Kai know to go to Settings? This contradicts US-24.
- **Losing default status:** user sets another launcher as default. On next launch of our APK, is there any detection/UI? Not specified.
- **Recent-apps / overview screen behavior:** launchers have special overview-screen semantics. Undefined.
- **Work profile / managed devices:** if Kai's phone has a work profile, `queryIntentActivities` returns work apps too — do we show them, hide them, badge them? Silent.
- **`android:exported` attributes for Android 12+:** not called out. Build will fail if missed.
- **`MediaProjection` / wallpaper integration:** most launchers expose the wallpaper behind them. We don't mention wallpaper handling at all. Background is specified as a gradient (§3 / DESIGN), so presumably we don't use the system wallpaper — but **say so** and note the UX expectation ("launcher ignores system wallpaper in v0.1").
- **Home gesture conflict:** on gesture-nav devices, swipe-up from bottom is the system Home gesture. FR-13 says swipe-up opens the drawer. Collision with system gesture? Needs a dedicated-zone / minimum-velocity spec. (This is the single biggest "oh shit" bug in homemade Android launchers.)
- **Notification listener / lock screen interactions:** not in scope per §7, fine — but the launcher is the first thing the user sees after unlock. What if the `MainActivity` is destroyed while the screen is off? Does it recreate state? Acceptance silent.

### 3.4 Accessibility (NFR-08 is the bare minimum and it shows)
- **TalkBack traversal order on the hero card:** is it title → description → why → 👍 → 👎? Undefined. Glassmorphic cards often have bad contrast (white-on-gradient) which is a WCAG AA fail by default — no mention.
- **Font scaling:** the design spec is pixel-perfect; what happens at 130% / 200% font scale? Hero card overflows? Card-type badges wrap? No requirement.
- **Reduce motion:** FR-06 has a 200ms slide+fade animation. OS "remove animations" setting? Not addressed.
- **Color-only signaling:** selected chip is distinguished by "filled accent color, glow" (US-18). Colorblind users? No secondary indicator.
- **Touch target on ✕ dismiss button:** NFR-08 says ≥48dp; the TikTok-style card design will fight you on this. Call out explicitly.

### 3.5 Dark theme / surface completeness
The PRD says "dark-first, glassmorphic" but doesn't enumerate every surface:
- **Bottom sheet scrim color?** Unspecified. Default Material is translucent black which will clash with the navy gradient.
- **System status bar + nav bar tint:** edge-to-edge expected; transparent bars + appropriate icon tint not specified.
- **Dialogs (the reset confirmation in FR-12):** Material 3 default dialog on a dark theme — is it themed to match the glassmorphism, or plain Material dark? Silent.
- **Keyboard theme:** FR-15 opens the keyboard from the AI Dock. Does the IME get a dark theme hint? (`EditorInfo.imeOptions`.)
- **App drawer background:** FR-13 doesn't specify — same gradient? Solid? Transparent over wallpaper? (See launcher-realities above.)
- **Toast styling:** FR-08 / FR-15 use toasts. Android toasts are not themeable on 12+ and will look out-of-character. Should probably be Snackbars.

### 3.6 Instrumentation / testability
- No requirement for a **dev build flag** that seeds the profile with synthetic history (needed for SM-07 and for demos).
- No **logging requirement** for feedback events during QA (NFR-06 forbids analytics, but local logcat is fine and should be mandated).
- No **deterministic seed** for the ε-greedy RNG (mandatory for reproducible tests).

---

## 4. Over-scoped Features (cut these from v0.1)

Ship the flywheel. Everything else is distraction.

1. **FR-15 AI Dock placeholder** — three non-functional affordances that invite confusion. Replace with a single "✦ AI (coming v0.2)" pill or remove the dock entirely in v0.1. The "visible but non-functional" UX is a classic demo-killer: reviewers *will* tap it, see a toast, and write "feels unfinished."
2. **US-22 and US-23 keyboard-opening placeholders** — same story, specific to the text field. Opening a keyboard with no submit action is user-hostile. Delete the keyboard focus; stub the whole field as a Button-that-toasts.
3. **FR-13 app drawer swipe-up gesture threshold (120dp)** — collides with system gesture-nav. Ship the drawer behind a **button in the header or the dock**, defer the gesture to v0.2. (This is the single most-likely-to-break-on-real-devices feature in the spec.)
4. **FR-07 SCROLL_PAST (-0.5) signal** — punitive implicit signal for something the user didn't consciously do. Drop the −0.5 band entirely, keep only the +1 dwell. Asymmetric is fine; trying to model a "bored scroll-past" with zero ground truth is noise.
5. **FR-12 feedback event timeline (20 most recent)** — nice-to-have, not critical for proving the flywheel. Top-10 tag weights alone demonstrate learning. Cut timeline to ship faster; add back in v0.2 if needed.
6. **SM-07 as a go/no-go gate** — keep the ε-greedy requirement (FR-11), but move SM-07 from release-blocker to "verified in dev build with a test hook." In-user-session verification is pseudoscience at 4-card catalogs.
7. **US-28 (newly installed apps appear in drawer)** — the PRD already soft-scoped this to "after relaunch." Just move it to §7 out-of-scope for v0.1 and stop mentioning `PackageManager` broadcast anywhere. One less thing to test.

---

## 5. Ambiguous Requirements (two devs, two apps)

| Ref | What's ambiguous | How devs will diverge |
|---|---|---|
| FR-03 | "Exactly one chip is always selected" + "default at cold start is 工作" | Dev A persists last-selected chip; Dev B always resets to 工作 on cold start. PRD is silent. |
| FR-04 | "Hero card and Discover feed both re-render" | Dev A keeps the same hero if its intent-tags intersect; Dev B always swaps the hero. Very different UX. |
| FR-05 | "3 and 6 cards" | Dev A picks min(6, available); Dev B picks a random 3–6 per render. Feed stability wildly different. |
| FR-06 | "remainder of the session" | See C-5. Session = process? intent? day? |
| FR-07 | Dwell semantics | See C-1. |
| FR-08 | "Action itself may be a no-op toast" | Dev A: toast says "Would open Luckin Coffee"; Dev B: toast says `actionLabel` value; Dev C: deep-links via `Intent.ACTION_VIEW` when a package is guessable. |
| FR-10 | `event.weight / tag_count` — is `tag_count` the card's tag count or the user's total tag count? | The phrasing literally reads either way. Affects every weight by 10x+. |
| FR-11 | "cards sorted by Σ(card.tag_weight × profile.tag_weight)" | Card tag weight undefined (see C-2). Dev A assumes 1.0; Dev B assumes reciprocal of tag_count; Dev C assumes `NEW`=0.5, `CONTINUE`=1.5. |
| FR-12 | "Reset profile" clears `user_profile` and `feedback_events` | Does it also reset the dismiss list / session state / currently-rendered feed? |
| FR-13 | "A back gesture/button returns to the home screen without restarting state" | Does "state" include intent selection? Scroll position? Dismiss list? |
| FR-14 | Home-button semantics | See C-4. |
| US-03 / FR-01 | "Why recommended" micro-label source | Dev A: uses seed `whyLabel` verbatim; Dev B: dynamically generates from top profile tag; Dev C: mixes. The product meaning of "transparency" depends on this choice. |
| FR-02 | "Hero card is replaced with the next-ranked card within 300ms" on 👎 | On 👍, is it also replaced? FR-02 doesn't say. Users will expect yes. |
| US-19 | Header greeting reflects intent | What are the exact strings? "工作模式 · 14:22" is an example, not a spec. Four greetings × clock format = 4 strings + a time format. Not specified. |

---

## 6. Seed Data Problems

### Coverage (are all card types × intents exercised?)
| Intent | CONTINUE | DISCOVER | NEW |
|---|---|---|---|
| COMMUTE | 1 (`commute_01`) | 2 (`commute_02`, `commute_03`) | 1 (`commute_04`) |
| WORK | 1 (`work_01`) | 2 (`work_02`, `work_03`) | 1 (`work_04`) |
| LUNCH | 1 (`lunch_01`) | 2 (`lunch_02`, `lunch_03`) | 1 (`lunch_04`) |
| REST | 1 (`rest_01`) | 2 (`rest_02`, `rest_03`) | 1 (`rest_04`) |

Every intent has a 1/2/1 CONTINUE/DISCOVER/NEW split. Symmetric — but **boring and small**:
- 4 cards per intent means a feed of 3–6 cards (FR-05) can only ever show at most 4. The 3–6 range is a lie on this catalog.
- Exploration (20%) over 4 cards is mathematically degenerate (see C-2).
- No intent has >1 `CONTINUE` card → you can't test "which recent activity wins?"
- No intent has >1 `NEW` card → you can't test exploration rotation.

### Specific card problems
- **`commute_02`:** "人民广场 → 陆家嘴" is hardcoded route for one specific user. The demo persona is Kai in Shanghai — fine — but this is the only card referencing a specific geography. Inconsistent: `lunch_02` is vague ("公司楼下"), `commute_02` is hyper-specific.
- **`work_03`:** "10:30 评审 · 14:00 1:1 · 16:00 站会" is a *time-stamped* card in a launcher that explicitly cannot read the calendar (§7). Seeing "10:30 评审" at 9 PM is the exact kind of thing that makes Kai uninstall. Either make the description time-agnostic or add a very clear "mock schedule" framing.
- **`lunch_03`:** "12 点了，该吃饭了" in `whyLabel` — same problem. Shown at 4 PM, this reads as broken. The PRD has no rule against time-referencing strings in static seeds.
- **`lunch_04` whyLabel:** "本周已连续 4 天炸物" — implies tracking that doesn't exist. This is *lying to the user* about what the AI knows. Transparency pillar violated by the seed data itself.
- **`rest_02` whyLabel:** "你这周听了 5 次" — same lie-to-user problem. Zero data, specific number.
- **`rest_03` whyLabel:** "AI 猜你今天有点累" + "你周三常用冥想类" — two different "why" statements mashed into one. Inconsistent across cards: some are behavioral ("你最近的咖啡习惯"), some are contextual ("12 点了"), some are fabricated ("AI 猜"). Pick one voice.
- **Duplicate tag noise:** `morning` appears on 3 of 4 commute cards; `commute` on 4 of 4; `lunch` on 4 of 4. Once the profile has weight on `lunch` it has weight on *every* lunch card equally — the signal is noise because the tag discriminates nothing. Need more tag diversity per intent or the engine has nothing to rank on.
- **Tag vocabulary is unmanaged:** `jay` (rest_02) vs `luckin` (lunch_01) vs `figma` (work_01) — these are brand-specific tags that will each accumulate weight from exactly one card. Dead-end tags. Compare to `coffee`, `music`, `design` which are reusable. The spec says "tags are also keys used in user_profile" (parsing note 2) — no governance on tag granularity.
- **`commute_04` actionLabel "试听 3 分钟":** implies real audio playback. FR-08 explicitly says actions are no-ops. Card copy ≠ feature reality.
- **`work_04` whyLabel:** "你常用项目管理类" — same "pretend we know this" issue. No signal source for this claim on cold start.
- **No negative-tag test fixture:** every card has positive vibes. No way to test "user hates morning news" because there's no card that would *only* lose if the `news` tag were negative — `commute_03` is the only news card, so a −10 on `news` just kills it and no other card changes. Expand to prove cross-card learning.

**Net:** the seed data is sufficient to *wire* the flywheel but insufficient to *prove* it works. Expansion to 6–8 cards per intent with shared discriminating tags (e.g., `healthy` across lunch and rest, `tech` across commute and work) is strongly recommended.

---

## 7. Measurability Issues

| Metric | Problem | Fix |
|---|---|---|
| SM-01 | "✅" is binary pass/fail; the phrasing is fine but there's no check that Home-button-re-entry works (per C-4). | Add a sub-step: "pressing Home while app is foreground returns to home screen module, not to drawer or sheet." |
| SM-02 | "Visibly different set of Discover cards" — visibly to whom? A screenshot diff? A tester's eye? | Specify: "the set of card IDs returned for intent A and intent B has ≥2 differences." |
| SM-03 | "Still shows the 5 events and updated tag weights" — shows where? Profile v47. But FR-12 displays **20 most recent**, not all. If tester does >20 signals, older ones disappear and SM-03 passes accidentally or fails spuriously. | Use a DAO-level count, not UI. |
| SM-04 | See C-6. The metric is passable with trivial work. | Require: "after 5× 👍 on `lunch_01`, the next Hero render for intent LUNCH is `lunch_01` with ≥X weight differential to the 2nd card." |
| SM-05 | <500ms first paint, median of 10 cold starts — fair — but "first paint" ≠ "first *usable* paint." Room reads happen after `setContent`; the home screen paints empty then fills. Metric measured this way is vacuous. | Define first paint as "HeroCard composable has non-placeholder content committed." |
| SM-06 | "Golden path runs clean 3× consecutively" — on what device? In what state (fresh install vs. upgraded)? With what other launcher installed? | Specify device, state, and whether force-stop between runs is allowed. |
| SM-07 | See C-2. Not verifiable with the seed catalog. | Move to dev-build-only test with a RecommendationEngine unit test across 1000 calls. |
| SM-08 | "Every installed launchable app appears" — provably? Against what baseline? Instrument's installed apps change during test. | Specify: "the count of items in the drawer equals the count returned by `queryIntentActivities(ACTION_MAIN, CATEGORY_LAUNCHER)` at the moment of render." |

Also **missing metrics**:
- No metric for Profile v47 reset actually working end-to-end.
- No metric for dwell-signal actually being emitted (the most-used signal is not in SM).
- No metric for ✕ dismiss removing the card from the visible feed.
- No metric for the default-state (cold start, empty profile) looking sensible.

---

## 8. Risk Matrix

| # | Risk | Severity | Likelihood | Notes |
|---|---|---|---|---|
| R-1 | Dwell tracking (FR-07) shipped with wrong semantics; signal table is dominated by Hero-dwell noise → profile is garbage → flywheel demo fails | **High** | **High** | See C-1. Single biggest bug-vector. |
| R-2 | ε-greedy engine under-specified (FR-11); exploration is 0% on 4-card intents; SM-07 fails | High | High | See C-2. Directly blocks release-gate metric. |
| R-3 | Home-button re-entry broken on gesture-nav Pixel; app re-launches instead of returning to home module | High | High | C-4. Classic homemade-launcher bug, guaranteed without explicit spec. |
| R-4 | Swipe-up-for-drawer (FR-13) conflicts with system gesture nav, gesture either never fires or triggers system | High | Med | Easy to reproduce on Pixel 6 gesture nav. |
| R-5 | No onboarding + no in-app "set as default" CTA → user installs and never sets as default → SM-01 never exercised in the wild | Med | High | PRD contradiction between US-24 and §7 "no onboarding." |
| R-6 | Seed data contains time-/behavior-specific strings (`work_03`, `lunch_03`, `rest_02`, `lunch_04`) that read as "broken" when shown in wrong context | Med | High | 4 of 16 cards affected. First-impression killer. |
| R-7 | Dismiss semantics (FR-06 "for the remainder of the session") evaporates on cold start → users perceive "my 👎 didn't work" | Med | High | C-5. Highly visible. |
| R-8 | Accessibility minimum (NFR-08) passes compile-time but TalkBack is unusable, glassmorphic contrast fails WCAG | Med | Med | Review-destroying if anyone audits. |
| R-9 | Seed catalog too small to demonstrate learning within a 10-minute demo | Med | High | Undermines the core pitch. |
| R-10 | Empty/degenerate-profile states (all negative, empty, corrupt Room) crash or render weirdly | High | Med | No spec today. |
| R-11 | FR-10 formula `event.weight / tag_count` ambiguity causes 10x profile scale drift | Med | Med | Unit test will catch; easy fix in v2. |
| R-12 | AI Dock placeholder invites user taps → "feels unfinished" reviewer bias | Med | High | Cut per §4. |
| R-13 | Profile v47 reset race with live UI state leaves stale cards on screen | Low | Med | Cosmetic but embarrassing. |
| R-14 | Font scaling / large text overflows glassmorphic Hero card | Low | High | Known Compose + pixel-perfect design tension. |
| R-15 | Room destructive migration on v0.1 → v0.2 wipes beta testers | Low | High | Acceptable if stated; currently not stated. |
| R-16 | Tag vocabulary ungoverned; brand-specific tags (`jay`, `luckin`, `figma`) become dead-ends that don't discriminate | Low | Med | Learn-ability degrades but doesn't crash. |
| R-17 | FR-16 byte-for-byte seed match blocks late copy edits | Low | Med | Process overhead. |
| R-18 | Toast-based "coming soon" stubs look un-themed on Android 12+ | Low | High | Use Snackbars. |
| R-19 | App drawer shows work-profile apps without expectation-setting | Low | Low | Unlikely in the Kai persona. |
| R-20 | No deterministic RNG seed in ε-greedy → flaky tests | Low | High | Trivial fix. |

**Top 3 for the PM to fix in v2:** R-1, R-2, R-3. If any one of these ships unfixed, the product demo fails.

---

## 9. Recommended v2 Edits (Concise)

1. **Rewrite FR-07** with: visibility definition, pause conditions, one-event-per-card-per-appearance rule, explicit exclusion of the Hero card from auto-dwell, and the 500–2000ms dead-band resolution.
2. **Rewrite FR-11** with: card-side tag weight definition, rounding rule for ε slot count on small sets, cold-start vs. `NEW`-card tie-breaker, RNG seed requirement.
3. **Add FR-17 "Home-button semantics"** explicitly covering bottom sheet, drawer, and navigation-state reset behavior.
4. **Add FR-18 "Default-launcher prompt"** — minimal in-app CTA when app is opened and is not the default home app (does not count as "onboarding").
5. **Add FR-19 "Empty / degenerate states"** covering cold start, all-negative profile, empty-intent-after-dismiss, Room read failure.
6. **Define "session"** in the glossary and reference it from FR-06.
7. **Expand seed catalog to 6–8 cards/intent** OR explicitly scope-down SM-07 and the "within a week" promise.
8. **Fix seed copy** in `work_03`, `lunch_03`, `lunch_04`, `rest_02`, `rest_03` to remove fabricated-state references.
9. **Cut FR-15 to a single non-interactive affordance.** Remove keyboard focus.
10. **Move swipe-up drawer gesture behind a button**; keep gesture as v0.2.
11. **Disambiguate FR-10** formula with a worked example.
12. **Add a dev-build seed-profile hook** to make SM-07 verifiable.
13. **Soften FR-16** from byte-for-byte to "matches §9 modulo minor copy edits approved in PR."
14. **Add explicit destructive-migration policy** for Room.
15. **Enumerate dark-theme surfaces** (bottom sheet scrim, status bar, dialogs, drawer bg, toast/snackbar).

---

*End of critique.*
