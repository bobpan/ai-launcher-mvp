# ARCHITECTURE v1 — AI Launcher MVP (v0.1)

**Status:** Approved for build · **Owner:** Tech Architect · **Last updated:** 2026-04-18
**Companion docs:** `AGENTS.md` (invariants), `docs/PRD-v2.md` (functional spec), `docs/DESIGN-v1.md` (visual spec)

> **Reading rule for the dev agent:** this document is prescriptive. Every class name, package path, method signature, and DI binding below is load-bearing. If a name here contradicts PRD-v2, *this document* is wrong — file an issue; don't silently rename. Copy-level text (UI strings) belongs to PRD-v2 §9 and DESIGN-v1.md, not here.

---

## 0. Locked Inputs

| Item | Value |
|---|---|
| Package | `com.bobpan.ailauncher` |
| minSdk | 26 |
| targetSdk / compileSdk | 34 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01 |
| AGP | 8.7.3 |
| Gradle | 8.11.1 |
| JDK | 17 (Temurin) |
| Hilt | 2.52 |
| Room | 2.6.1 |
| KSP | 2.0.21-1.0.28 |

No deviations. Upgrading any pin is a new architecture rev.

---

## 1. System Architecture Overview

Layered, unidirectional MVVM. Dependencies point **downward only**; Data never knows UI exists, Domain never knows Room exists (except through repository interfaces that live in Data).

```
┌─────────────────────────────────────────────────────────────────────┐
│  UI LAYER (Compose, @HiltViewModel collectors)                      │
│                                                                     │
│   MainActivity                                                      │
│    └── AppNavHost (Compose Navigation)                              │
│         ├── HomeScreen ────────► HomeViewModel                      │
│         ├── AppDrawerScreen ───► AppDrawerViewModel                 │
│         └── ProfileDebugSheet ─► ProfileDebugViewModel              │
│                                                                     │
│   Components (stateless):                                           │
│     HeroCard · IntentChipStrip · DiscoverCard · AiDock ·            │
│     DefaultLauncherBanner · GlassCard                               │
│                                                                     │
│   Theme: AiLauncherTheme (dark-first, glassmorphic)                 │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ StateFlow<UiState> (read)
                                │ ViewModel method calls (write)
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  VIEWMODEL LAYER (androidx.lifecycle.ViewModel, @HiltViewModel)     │
│                                                                     │
│   HomeViewModel · AppDrawerViewModel · ProfileDebugViewModel        │
│                                                                     │
│   Holds: UiState StateFlow, in-memory session state                 │
│           (dismiss set, banner-dismissed flag, dwell tracker)       │
│   Uses:  Repositories, RecommendationEngine, Clock                  │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ interfaces only
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  DOMAIN LAYER (pure Kotlin, no Android deps)                        │
│                                                                     │
│   Models: Intent, CardType, Signal, Card, UserProfile,              │
│           ConfidenceLevel, FeedbackEvent (domain), SeedCard         │
│                                                                     │
│   RecommendationEngine (interface + EpsilonGreedyEngine impl)       │
│     Inputs:  Intent, List<Card>, Set<dismissedIds>,                 │
│              UserProfile snapshot, Random, Clock                    │
│     Output:  Recommendation (Hero + Discover slots)                 │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ interfaces only
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  DATA LAYER (Room, repositories)                                    │
│                                                                     │
│   Repositories (interfaces in data/repo; impls in data/repo/impl):  │
│     FeedbackRepository · CardRepository · ProfileRepository         │
│                                                                     │
│   Room:                                                             │
│     AppDatabase (v1)                                                │
│       ├── FeedbackEventDao → feedback_events                        │
│       ├── UserProfileDao   → user_profile                           │
│       └── CachedCardDao    → cached_cards  (seed cache)             │
│                                                                     │
│   Seed: SeedCards.SEED_CARDS (hardcoded List<SeedCard>)             │
│         Loaded into memory at App start; mirrored to cached_cards   │
│         on first run via SeedLoader.                                │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
                        Android Platform
                   (PackageManager, Settings,
                    Lifecycle, LayoutCoordinates)
```

**Rules:**
- UI imports Domain + ViewModel. UI does **not** import `data.db.*` or Room types.
- ViewModel imports Domain + Repository interfaces. VMs do **not** import DAO / Entity classes.
- Domain has **zero** Android imports. Pure Kotlin + `javax.inject`. (Hilt `@Inject` is a javax annotation — permitted.)
- Data exposes **domain models** across its API boundary. Entities are translated to domain models inside repository impls via mapper functions (e.g., `FeedbackEventEntity.toDomain()`).

---

## 2. Module / Package Structure

Single Gradle module (`:app`). Finalised package tree:

```
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/bobpan/ailauncher/
    │   │   ├── AiLauncherApp.kt            — Application subclass, @HiltAndroidApp
    │   │   ├── MainActivity.kt             — @AndroidEntryPoint, Compose root, launcher intent-filter host
    │   │   │
    │   │   ├── di/                         — Hilt modules (see §9)
    │   │   │   ├── AppModule.kt
    │   │   │   ├── DatabaseModule.kt
    │   │   │   ├── RepositoryModule.kt
    │   │   │   └── DomainModule.kt
    │   │   │
    │   │   ├── data/
    │   │   │   ├── db/
    │   │   │   │   ├── AppDatabase.kt              — @Database(version=1), TypeConverters
    │   │   │   │   ├── Converters.kt               — Intent/Signal/CardType <-> String
    │   │   │   │   ├── entity/
    │   │   │   │   │   ├── FeedbackEventEntity.kt
    │   │   │   │   │   ├── UserProfileEntryEntity.kt
    │   │   │   │   │   └── CachedCardEntity.kt
    │   │   │   │   ├── dao/
    │   │   │   │   │   ├── FeedbackEventDao.kt
    │   │   │   │   │   ├── UserProfileDao.kt
    │   │   │   │   │   └── CachedCardDao.kt
    │   │   │   │   └── mapper/
    │   │   │   │       └── Mappers.kt              — entity <-> domain
    │   │   │   │
    │   │   │   ├── seed/
    │   │   │   │   ├── SeedCards.kt                — val SEED_CARDS: List<SeedCard>
    │   │   │   │   └── SeedLoader.kt               — one-shot sync into cached_cards on first run
    │   │   │   │
    │   │   │   └── repo/
    │   │   │       ├── FeedbackRepository.kt       — interface
    │   │   │       ├── CardRepository.kt           — interface
    │   │   │       ├── ProfileRepository.kt        — interface
    │   │   │       └── impl/
    │   │   │           ├── FeedbackRepositoryImpl.kt
    │   │   │           ├── CardRepositoryImpl.kt
    │   │   │           └── ProfileRepositoryImpl.kt
    │   │   │
    │   │   ├── domain/
    │   │   │   ├── model/
    │   │   │   │   ├── Intent.kt                   — enum
    │   │   │   │   ├── CardType.kt                 — enum
    │   │   │   │   ├── Card.kt                     — data class
    │   │   │   │   ├── SeedCard.kt                 — data class (raw seed shape)
    │   │   │   │   ├── Signal.kt                   — sealed interface
    │   │   │   │   ├── FeedbackEvent.kt            — data class (domain-level)
    │   │   │   │   ├── UserProfile.kt              — data class
    │   │   │   │   ├── ConfidenceLevel.kt          — enum
    │   │   │   │   └── Recommendation.kt           — data class (engine output)
    │   │   │   │
    │   │   │   ├── time/
    │   │   │   │   └── Clock.kt                    — interface + SystemClock impl
    │   │   │   │
    │   │   │   └── recommend/
    │   │   │       ├── RecommendationEngine.kt     — interface
    │   │   │       └── EpsilonGreedyEngine.kt      — impl
    │   │   │
    │   │   ├── ui/
    │   │   │   ├── theme/
    │   │   │   │   ├── Color.kt
    │   │   │   │   ├── Type.kt
    │   │   │   │   ├── Theme.kt                    — AiLauncherTheme {}
    │   │   │   │   └── Gradients.kt                — background + glass brushes
    │   │   │   │
    │   │   │   ├── navigation/
    │   │   │   │   ├── AppNavHost.kt
    │   │   │   │   └── Routes.kt                   — const route strings
    │   │   │   │
    │   │   │   ├── home/
    │   │   │   │   ├── HomeScreen.kt
    │   │   │   │   ├── HomeViewModel.kt            — @HiltViewModel
    │   │   │   │   └── HomeUiState.kt              — sealed UiState
    │   │   │   │
    │   │   │   ├── drawer/
    │   │   │   │   ├── AppDrawerScreen.kt
    │   │   │   │   ├── AppDrawerViewModel.kt       — @HiltViewModel
    │   │   │   │   ├── AppDrawerUiState.kt
    │   │   │   │   └── InstalledApp.kt             — data class (label, package, icon Drawable)
    │   │   │   │
    │   │   │   ├── profile/
    │   │   │   │   ├── ProfileDebugSheet.kt
    │   │   │   │   ├── ProfileDebugViewModel.kt    — @HiltViewModel
    │   │   │   │   └── ProfileDebugUiState.kt
    │   │   │   │
    │   │   │   └── components/
    │   │   │       ├── HeroCard.kt
    │   │   │       ├── DiscoverCard.kt
    │   │   │       ├── IntentChipStrip.kt
    │   │   │       ├── AiDock.kt
    │   │   │       ├── DefaultLauncherBanner.kt
    │   │   │       ├── GlassCard.kt                — shared glassmorphic container
    │   │   │       ├── EmptyFeedCard.kt            — FR-19 "all dismissed" state
    │   │   │       └── DwellTracker.kt             — Modifier.dwellTracker(...) per FR-07
    │   │   │
    │   │   └── util/
    │   │       ├── Logging.kt                      — `FLYWHEEL` tag helper (debug-gated)
    │   │       ├── DebounceClicker.kt              — 400ms debounce util for FR-02
    │   │       ├── LifecycleExt.kt                 — collectAsStateWithLifecycle helpers
    │   │       └── PreviewData.kt                  — @Preview fixtures (debug-only)
    │   │
    │   └── res/
    │       ├── values/
    │       │   ├── strings.xml                     — all user-visible Chinese copy
    │       │   ├── themes.xml                      — bootstrap Theme.AiLauncher (black window bg)
    │       │   └── colors.xml
    │       ├── values-night/                       — same (dark-first; light deferred)
    │       ├── drawable/
    │       │   └── ic_launcher_foreground.xml
    │       └── mipmap-anydpi-v26/
    │           └── ic_launcher.xml
    │
    ├── test/java/com/bobpan/ailauncher/           — JVM unit tests
    │   ├── domain/recommend/EpsilonGreedyEngineTest.kt
    │   ├── ui/home/HomeViewModelTest.kt
    │   ├── ui/profile/ProfileDebugViewModelTest.kt
    │   ├── data/repo/FakeFeedbackRepository.kt    — shared test double
    │   ├── data/repo/FakeCardRepository.kt
    │   ├── data/repo/FakeProfileRepository.kt
    │   └── domain/time/FakeClock.kt
    │
    └── androidTest/java/com/bobpan/ailauncher/   — instrumented (minimize in MVP)
        ├── data/db/FeedbackEventDaoTest.kt
        ├── data/db/UserProfileDaoTest.kt
        └── LauncherIntentFilterTest.kt           — confirms CATEGORY_HOME resolution
```

**Package purposes:**

| Package | Purpose | Allowed deps |
|---|---|---|
| `di` | Hilt modules only | All layers |
| `data.db.*` | Room: entities, DAOs, database, mappers | `androidx.room`, domain models |
| `data.seed` | Hardcoded seed cards + first-run loader | domain models, DAOs |
| `data.repo` | Repository **interfaces** | domain models, kotlinx.coroutines |
| `data.repo.impl` | Repository **impls** | DAOs, mappers, domain |
| `domain.model` | Pure Kotlin data classes / enums | **no Android, no Room** |
| `domain.time` | `Clock` abstraction | none |
| `domain.recommend` | `RecommendationEngine` + impl | domain.model, domain.time, kotlin.random |
| `ui.theme` | Compose theme | Compose only |
| `ui.navigation` | Nav host + routes | Compose Navigation |
| `ui.home/drawer/profile` | Screens + VMs + UiStates | domain, repo interfaces, Compose |
| `ui.components` | Stateless composables | Compose, domain.model |
| `util` | Extension/helper (non-business) | Any |

---

## 3. Gradle Configuration

### 3.1 `gradle/libs.versions.toml` (version catalog)

```toml
[versions]
# Build
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
jdk = "17"

# AndroidX core
androidxCore = "1.13.1"
activityCompose = "1.9.3"
lifecycle = "2.8.7"

# Compose
composeBom = "2024.12.01"
composeCompiler = "1.5.15"  # unused at runtime (Kotlin 2 uses its own compose plugin), kept for reference

# Navigation
navigationCompose = "2.8.4"

# Hilt
hilt = "2.52"
hiltNavigationCompose = "1.2.0"

# Room
room = "2.6.1"

# DataStore (used ONLY for banner-dismissed flag if needed; optional — see §4.6)
datastore = "1.1.1"

# Serialization (internal use; no JSON over wire since offline-first)
kotlinxSerialization = "1.7.3"

# Coroutines
coroutines = "1.9.0"

# Testing
junit = "4.13.2"
mockk = "1.13.13"
coroutinesTest = "1.9.0"
androidxTest = "1.6.1"
androidxTestExt = "1.2.1"
androidxTestRunner = "1.6.2"
espresso = "3.6.1"
archCoreTesting = "2.2.0"
truth = "1.4.4"

[libraries]
# Core
androidx-core-ktx          = { module = "androidx.core:core-ktx", version.ref = "androidxCore" }
androidx-activity-compose  = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose   = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx       = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }

# Compose BOM + UI
compose-bom                = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui                 = { module = "androidx.compose.ui:ui" }
compose-ui-graphics        = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling         = { module = "androidx.compose.ui:ui-tooling" }
compose-foundation         = { module = "androidx.compose.foundation:foundation" }
compose-material3          = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }

# Navigation
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }

# Hilt
hilt-android               = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler              = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose    = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Room
room-runtime               = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx                   = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler              = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing               = { module = "androidx.room:room-testing", version.ref = "room" }

# DataStore (only if we add it; scoped to process-lifetime state replacements — see §4.6)
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Serialization
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

# Coroutines
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }

# Testing — unit
junit                      = { module = "junit:junit", version.ref = "junit" }
mockk                      = { module = "io.mockk:mockk", version.ref = "mockk" }
kotlinx-coroutines-test    = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesTest" }
androidx-arch-core-testing = { module = "androidx.arch.core:core-testing", version.ref = "archCoreTesting" }
truth                      = { module = "com.google.truth:truth", version.ref = "truth" }

# Testing — instrumented
androidx-test-core         = { module = "androidx.test:core-ktx", version.ref = "androidxTest" }
androidx-test-ext-junit    = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
androidx-test-runner       = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
mockk-android              = { module = "io.mockk:mockk-android", version.ref = "mockk" }

[plugins]
android-application        = { id = "com.android.application",   version.ref = "agp" }
kotlin-android             = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose             = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization       = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp                        = { id = "com.google.devtools.ksp",   version.ref = "ksp" }
hilt                       = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### 3.2 Project root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

### 3.3 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AI Launcher"
include(":app")
```

### 3.4 App `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.bobpan.ailauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bobpan.ailauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // v0.1 — APKs are side-loaded
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    // Room generates schema JSONs — checked into version control so we can diff schema changes.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose (BOM-pinned)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Serialization (used for any in-memory JSON diagnostics; no network)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.truth)
    testImplementation(libs.room.testing)

    // Instrumented tests (minimal — see §12)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.compose.bom))
}
```

### 3.5 `gradle.properties` (notable)

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

android.useAndroidX=true
android.nonTransitiveRClass=true
android.defaults.buildfeatures.buildconfig=true

kotlin.code.style=official
ksp.incremental=true
```

### 3.6 Gradle wrapper
`gradle/wrapper/gradle-wrapper.properties` must pin `8.11.1`. The wrapper jar and `gradlew` / `gradlew.bat` are generated during the dev phase; do **not** commit a hand-rolled `gradle-wrapper.jar`.

---

## 4. Data Layer (Room Spec)

### 4.1 Entities

All entities live in `data/db/entity/`. They are pure Room objects — domain code never sees them.

#### `FeedbackEventEntity`
Table: `feedback_events` — append-only log (FR-09).

| Column | Kotlin type | SQL | Nullable | Default | Index |
|---|---|---|---|---|---|
| `id` | `Long` | INTEGER PRIMARY KEY AUTOINCREMENT | no | auto | PK |
| `card_id` | `String` | TEXT | no | — | `idx_feedback_card_id` |
| `intent` | `String` | TEXT | no | — | `idx_feedback_intent` |
| `signal` | `String` | TEXT | no | — | — |
| `weight` | `Float` | REAL | no | — | — |
| `timestamp_ms` | `Long` | INTEGER | no | — | `idx_feedback_timestamp` |

```kotlin
@Entity(
    tableName = "feedback_events",
    indices = [
        Index("card_id", name = "idx_feedback_card_id"),
        Index("intent", name = "idx_feedback_intent"),
        Index("timestamp_ms", name = "idx_feedback_timestamp"),
    ]
)
data class FeedbackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "card_id")      val cardId: String,
    @ColumnInfo(name = "intent")       val intent: String,   // Intent.name
    @ColumnInfo(name = "signal")       val signal: String,   // Signal type tag
    @ColumnInfo(name = "weight")       val weight: Float,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
)
```

#### `UserProfileEntryEntity`
Table: `user_profile` — running tag weights (FR-10).

| Column | Kotlin type | SQL | Nullable | Default | Index |
|---|---|---|---|---|---|
| `tag` | `String` | TEXT PRIMARY KEY | no | — | PK |
| `weight` | `Float` | REAL | no | `0.0` | — |
| `updated_ms` | `Long` | INTEGER | no | `0` | — |

```kotlin
@Entity(tableName = "user_profile")
data class UserProfileEntryEntity(
    @PrimaryKey @ColumnInfo(name = "tag") val tag: String,
    @ColumnInfo(name = "weight", defaultValue = "0.0") val weight: Float = 0.0f,
    @ColumnInfo(name = "updated_ms", defaultValue = "0") val updatedMs: Long = 0L,
)
```

#### `CachedCardEntity`
Table: `cached_cards` — mirror of seed catalog for query-through-Room diagnostics and future (v0.2) dynamic cards. v0.1's **source of truth remains `SEED_CARDS`**; `CardRepositoryImpl.getAll()` reads from memory, not from this table. The table is populated once by `SeedLoader` so debug tooling can inspect it.

| Column | Kotlin type | SQL | Nullable | Default | Index |
|---|---|---|---|---|---|
| `id` | `String` | TEXT PRIMARY KEY | no | — | PK |
| `title` | `String` | TEXT | no | — | — |
| `description` | `String` | TEXT | no | — | — |
| `intent` | `String` | TEXT | no | — | `idx_cached_intent` |
| `type` | `String` | TEXT | no | — | — |
| `icon` | `String` | TEXT | no | — | — |
| `action_label` | `String` | TEXT | no | — | — |
| `why_label` | `String` | TEXT | no | — | — |
| `tags_csv` | `String` | TEXT | no | `""` | — |
| `seed_order` | `Int` | INTEGER | no | `0` | — |

```kotlin
@Entity(
    tableName = "cached_cards",
    indices = [Index("intent", name = "idx_cached_intent")]
)
data class CachedCardEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "intent") val intent: String,     // Intent.name
    @ColumnInfo(name = "type")   val type: String,       // CardType.name
    val icon: String,
    @ColumnInfo(name = "action_label") val actionLabel: String,
    @ColumnInfo(name = "why_label")    val whyLabel: String,
    @ColumnInfo(name = "tags_csv", defaultValue = "\"\"") val tagsCsv: String = "",
    @ColumnInfo(name = "seed_order", defaultValue = "0")  val seedOrder: Int = 0,
)
```

**No foreign keys.** Feedback events reference cards by ID-string only; cards can come and go without DB cascade logic. Acceptable because the seed list is code-versioned.

### 4.2 DAOs

#### `FeedbackEventDao`

```kotlin
@Dao
interface FeedbackEventDao {

    @Insert
    suspend fun insert(event: FeedbackEventEntity): Long

    @Query("SELECT COUNT(*) FROM feedback_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM feedback_events")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM feedback_events ORDER BY timestamp_ms DESC")
    suspend fun getAll(): List<FeedbackEventEntity>

    @Query("SELECT * FROM feedback_events WHERE card_id = :cardId ORDER BY timestamp_ms DESC")
    suspend fun getByCard(cardId: String): List<FeedbackEventEntity>

    @Query("""
        SELECT * FROM feedback_events
        WHERE card_id = :cardId AND signal = :signal AND timestamp_ms > :sinceMs
        ORDER BY timestamp_ms DESC LIMIT 1
    """)
    suspend fun lastSameSignalSince(cardId: String, signal: String, sinceMs: Long): FeedbackEventEntity?

    @Query("DELETE FROM feedback_events")
    suspend fun clear()
}
```

#### `UserProfileDao`

```kotlin
@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile ORDER BY weight DESC")
    fun observeAll(): Flow<List<UserProfileEntryEntity>>

    @Query("SELECT * FROM user_profile ORDER BY weight DESC")
    suspend fun getAll(): List<UserProfileEntryEntity>

    @Query("SELECT * FROM user_profile ORDER BY ABS(weight) DESC LIMIT :limit")
    suspend fun getTopByAbsWeight(limit: Int): List<UserProfileEntryEntity>

    @Query("SELECT * FROM user_profile WHERE tag = :tag LIMIT 1")
    suspend fun find(tag: String): UserProfileEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UserProfileEntryEntity)

    @Transaction
    suspend fun addWeight(tag: String, delta: Float, nowMs: Long) {
        val existing = find(tag)
        val newWeight = (existing?.weight ?: 0.0f) + delta
        upsert(UserProfileEntryEntity(tag = tag, weight = newWeight, updatedMs = nowMs))
    }

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}
```

#### `CachedCardDao`

```kotlin
@Dao
interface CachedCardDao {

    @Query("SELECT COUNT(*) FROM cached_cards")
    suspend fun count(): Int

    @Query("SELECT * FROM cached_cards ORDER BY seed_order ASC")
    suspend fun getAll(): List<CachedCardEntity>

    @Query("SELECT * FROM cached_cards WHERE intent = :intent ORDER BY seed_order ASC")
    suspend fun getByIntent(intent: String): List<CachedCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cards: List<CachedCardEntity>)

    @Query("DELETE FROM cached_cards")
    suspend fun clear()
}
```

### 4.3 Database class

```kotlin
@Database(
    entities = [
        FeedbackEventEntity::class,
        UserProfileEntryEntity::class,
        CachedCardEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedbackEventDao(): FeedbackEventDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun cachedCardDao(): CachedCardDao

    companion object {
        const val DB_NAME = "ai_launcher.db"
    }
}
```

### 4.4 TypeConverters

All enum <-> String conversion happens at the **mapper layer**, not in Room (keeps Room columns simple strings and future-proofs enum renames). The `Converters` class is therefore minimal and exists for one purpose: converting `List<String>` ↔ CSV for the `tags_csv` column when we need it.

```kotlin
class Converters {
    @TypeConverter fun tagsFromCsv(csv: String): List<String> =
        if (csv.isBlank()) emptyList() else csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    @TypeConverter fun tagsToCsv(tags: List<String>): String = tags.joinToString(",")
}
```

Note: `CachedCardEntity.tagsCsv` is declared as `String` (not `List<String>`) intentionally — the converter is available but the entity uses CSV directly for index-friendliness.

### 4.5 Migration strategy

**v0.1 uses `fallbackToDestructiveMigration()`** — per PRD FR-09. Justification:
- MVP is pre-GA; no external users are guaranteed data retention.
- Feedback events are cheap to regenerate (the user re-teaches over a single session).
- Introducing `Migration` classes now would burn time on a moving schema.
- Release notes must state "beta: DB may reset between builds that change schema."

Schema JSONs are exported to `app/schemas/` (see §3.4 KSP args) and committed. When v0.2 enters GA we flip to explicit migrations and delete the fallback call.

### 4.6 Seed data loader

`SeedCards.kt`:

```kotlin
// data/seed/SeedCards.kt
val SEED_CARDS: List<SeedCard> = listOf(
    // 16 entries matching PRD-v2 §9 exactly on id/intent/type/tags.
    // Text fields may receive PR-reviewed copy edits per FR-16.
    // Declaration order per intent: _01 (CONTINUE), _02/_03 (DISCOVER), _04 (NEW).
    // Order across intents follows: COMMUTE block, WORK block, LUNCH block, REST block.
)
```

`SeedLoader`:

```kotlin
@Singleton
class SeedLoader @Inject constructor(
    private val cachedCardDao: CachedCardDao,
) {
    /** Idempotent. Call from CardRepositoryImpl.init() via coroutine on Dispatchers.IO. */
    suspend fun loadIfEmpty() {
        if (cachedCardDao.count() > 0) return
        val rows = SEED_CARDS.mapIndexed { idx, seed ->
            CachedCardEntity(
                id = seed.id,
                title = seed.title,
                description = seed.description,
                intent = seed.intent.name,
                type = seed.type.name,
                icon = seed.icon,
                actionLabel = seed.actionLabel,
                whyLabel = seed.whyLabel,
                tagsCsv = seed.tags.joinToString(","),
                seedOrder = idx,
            )
        }
        cachedCardDao.upsertAll(rows)
    }
}
```

`CardRepositoryImpl` calls `seedLoader.loadIfEmpty()` exactly once during its lazy first access. **But** `getHeroForIntent` / `getDiscoverFeed` read from the in-memory `SEED_CARDS` list (FR-19 "Room unavailable" fallback works trivially). The Room copy is a mirror for debugging.

**No DataStore required in v0.1.** Session-scoped state (banner-dismissed flag, dismiss set) is pure `HomeViewModel` in-memory — per FR-06 and FR-17 neither persists across process death. `datastore-preferences` is listed in the catalog but remains unused unless a future FR needs it; do **not** add the implementation dependency to `app/build.gradle.kts` until it does.

---

## 5. Domain Models

Pure Kotlin. Zero Android imports.

```kotlin
// domain/model/Intent.kt
enum class Intent { COMMUTE, WORK, LUNCH, REST }

// domain/model/CardType.kt
enum class CardType { CONTINUE, DISCOVER, NEW, HERO }
// HERO is a *rendering hint* emitted by the engine, not a seed type.
// Seed cards only use CONTINUE / DISCOVER / NEW. HERO is assigned at recommend-time
// to whichever card occupies the Hero slot.
```

```kotlin
// domain/model/Card.kt — runtime card, used everywhere above the data layer.
data class Card(
    val id: String,
    val title: String,
    val description: String,
    val intent: Intent,
    val type: CardType,        // seed type (CONTINUE/DISCOVER/NEW)
    val icon: String,          // emoji in v0.1
    val actionLabel: String,
    val whyLabel: String,
    val tags: List<String>,
    val seedOrder: Int,        // stable tie-breaker (FR-11)
)

// domain/model/SeedCard.kt — shape declared in SeedCards.kt.
// Identical to Card sans seedOrder (that's assigned by index during load).
data class SeedCard(
    val id: String,
    val title: String,
    val description: String,
    val intent: Intent,
    val type: CardType,
    val icon: String,
    val actionLabel: String,
    val whyLabel: String,
    val tags: List<String>,
)
```

```kotlin
// domain/model/Signal.kt
sealed interface Signal {
    val weight: Float
    val tag: String   // stable string for persistence, logs, and DAO queries

    data object ThumbUp    : Signal { override val weight = +3.0f; override val tag = "THUMB_UP" }
    data object ThumbDown  : Signal { override val weight = -3.0f; override val tag = "THUMB_DOWN" }
    data object Dismiss    : Signal { override val weight = -2.0f; override val tag = "DISMISS" }
    data object ActionTap  : Signal { override val weight = +2.0f; override val tag = "ACTION_TAP" }
    data object Dwell      : Signal { override val weight = +1.0f; override val tag = "DWELL" }
    data object Tap        : Signal { override val weight = +2.0f; override val tag = "TAP" }
    // Ambient / non-scored signals — weight=0 so profile is unaffected; persisted only for diagnostics.
    data object IntentSwitch    : Signal { override val weight = 0.0f; override val tag = "INTENT_SWITCH" }
    data object RevealAppeared  : Signal { override val weight = 0.0f; override val tag = "REVEAL_APPEARED" }

    companion object {
        fun fromTag(tag: String): Signal = when (tag) {
            "THUMB_UP"   -> ThumbUp
            "THUMB_DOWN" -> ThumbDown
            "DISMISS"    -> Dismiss
            "ACTION_TAP" -> ActionTap
            "DWELL"      -> Dwell
            "TAP"        -> Tap
            "INTENT_SWITCH"   -> IntentSwitch
            "REVEAL_APPEARED" -> RevealAppeared
            else -> error("Unknown signal tag: $tag")
        }
    }
}
```

> **Architect decision A-1 (not explicit in PRD-v2):** `Tap`, `IntentSwitch`, `RevealAppeared` aren't in PRD-v2's scored signal list but are requested in the task spec. `Tap` is given weight +2 (treated as equivalent to `ActionTap` for non-hero generic card taps — currently unused in v0.1 flows, available for future). `IntentSwitch` and `RevealAppeared` are weight 0 to avoid polluting the profile while still being recordable for diagnostics. FR-02 weights (±3), FR-06 (−2), FR-07 (+1), FR-08 (+2) match PRD-v2.

```kotlin
// domain/model/FeedbackEvent.kt — domain version (no Long PK).
data class FeedbackEvent(
    val cardId: String,
    val intent: Intent,
    val signal: Signal,
    val timestampMs: Long,
    val persistedId: Long? = null,   // set after DAO insert
) {
    val weight: Float get() = signal.weight
}
```

```kotlin
// domain/model/UserProfile.kt
data class UserProfile(
    val tags: Map<String, Float>,       // tag -> weight
    val totalEventCount: Int,
    val lastUpdatedMs: Long,
) {
    fun weightOf(tag: String): Float = tags[tag] ?: 0.0f
    fun top(n: Int): List<Pair<String, Float>> =
        tags.entries.sortedByDescending { kotlin.math.abs(it.value) }.take(n).map { it.key to it.value }

    companion object { val EMPTY = UserProfile(tags = emptyMap(), totalEventCount = 0, lastUpdatedMs = 0L) }
}
```

```kotlin
// domain/model/ConfidenceLevel.kt
enum class ConfidenceLevel { COLD_START, LEARNING, CONFIDENT }
// Derivation rule (used by HomeViewModel for header accents; NOT by engine scoring):
//   totalEventCount == 0              -> COLD_START
//   totalEventCount in 1..9           -> LEARNING
//   totalEventCount >= 10             -> CONFIDENT
// Rule placed in UserProfileKt extension; not a business-critical path.
```

```kotlin
// domain/model/Recommendation.kt — engine output envelope.
data class Recommendation(
    val hero: Card?,                  // null iff candidates is empty
    val discoverFeed: List<Card>,     // ordered; excludes hero
    val explorationCardId: String?,   // id of the exploration-slot card (for logs/tests)
    val generatedAtMs: Long,
)
```

---

## 6. Repository Interfaces

All in `data/repo/`. Impls in `data/repo/impl/`. Impls are `@Singleton` and constructor-inject DAOs.

### `FeedbackRepository`

```kotlin
interface FeedbackRepository {

    /** FR-09 health flag. False if Room couldn't open. UI falls back to in-memory seeds. */
    val isHealthy: Boolean

    /**
     * Persists one feedback event and updates user_profile transactionally (FR-10).
     * Applies the 400ms same-card debounce (FR-02) for THUMB_UP/THUMB_DOWN internally.
     * Returns the persisted FeedbackEvent (with [persistedId]) or null if debounced.
     */
    suspend fun emit(
        cardId: String,
        intent: Intent,
        signal: Signal,
        cardTags: List<String>,
    ): FeedbackEvent?

    /** Total event count for Profile v47 counter (FR-12). Reactive. */
    fun observeCount(): Flow<Int>

    suspend fun count(): Int

    /** All events, newest first. Used by debug/dev tools (FR-20); not shown in UI in v0.1. */
    suspend fun getAll(): List<FeedbackEvent>

    /** Wipes feedback_events. Called inside ProfileRepository.reset() in one transaction. */
    suspend fun clearAll()
}
```

### `CardRepository`

```kotlin
interface CardRepository {

    /** All 16 seed cards (in-memory, always available). */
    fun getAll(): List<Card>

    fun getForIntent(intent: Intent): List<Card>

    /**
     * Returns the Hero + Discover feed composition for the current state.
     * This is the single entry point used by HomeViewModel on every render.
     *
     * [dismissedIds] is the in-memory session dismiss set (FR-06), passed by the caller.
     * [profile] is the latest UserProfile snapshot.
     */
    suspend fun getRecommendation(
        intent: Intent,
        dismissedIds: Set<String>,
        profile: UserProfile,
    ): Recommendation

    /** First-run side effect: mirror SEED_CARDS into cached_cards. Safe to call repeatedly. */
    suspend fun primeCache()
}
```

### `ProfileRepository`

```kotlin
interface ProfileRepository {

    /** Reactive profile snapshot — emits on every user_profile change. */
    fun observeProfile(): Flow<UserProfile>

    suspend fun getSnapshot(): UserProfile

    /**
     * FR-12 reset flow step 1+2:
     *   DELETE FROM feedback_events; DELETE FROM user_profile;
     * …in a single Room @Transaction. The rest of FR-12 (clear dismiss, reset intent,
     * close sheet, refresh) is orchestrated by ProfileDebugViewModel / HomeViewModel.
     */
    suspend fun reset()

    /** Debug-only: seed synthetic profile (FR-20). No-op in release. */
    suspend fun seedDemoProfile()
}
```

---

## 7. Domain — Recommendation Engine

### 7.1 `Clock`

```kotlin
// domain/time/Clock.kt
interface Clock { fun nowMs(): Long }

class SystemClock @Inject constructor() : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
```

Injected via `DomainModule`. The `Dwell` tracker, the debounce check, and every `timestampMs` assignment MUST use this — never `System.currentTimeMillis()` directly. (FR-07 Rule 8.)

### 7.2 `RecommendationEngine` interface

```kotlin
// domain/recommend/RecommendationEngine.kt
interface RecommendationEngine {

    /**
     * Pure function. No I/O. No state across calls.
     *
     * @param intent        active intent
     * @param candidates    all seed cards for [intent] (caller pre-filters; usually 4)
     * @param dismissedIds  session dismiss set (FR-06); excluded from ranking
     * @param profile       latest UserProfile snapshot
     * @param count         feed size requested. v0.1 always passes 4.
     */
    fun recommend(
        intent: Intent,
        candidates: List<Card>,
        dismissedIds: Set<String>,
        profile: UserProfile,
        count: Int = 4,
    ): Recommendation

    /** Debug hook (FR-20). No-op in release. */
    fun setDeterministicMode(seed: Long)
}
```

### 7.3 `EpsilonGreedyEngine` impl

Pseudocode → actual Kotlin implementation of FR-11:

```kotlin
// domain/recommend/EpsilonGreedyEngine.kt
@Singleton
class EpsilonGreedyEngine @Inject constructor(
    @Named("recEngineRng") private var rng: Random,    // var to allow setDeterministicMode
    private val clock: Clock,
) : RecommendationEngine {

    override fun recommend(
        intent: Intent,
        candidates: List<Card>,
        dismissedIds: Set<String>,
        profile: UserProfile,
        count: Int,
    ): Recommendation {

        // 1. Filter out dismissed; keep seed-declaration (seedOrder) order for stable ties.
        val available = candidates
            .filter { it.intent == intent && it.id !in dismissedIds }
            .sortedBy { it.seedOrder }

        if (available.isEmpty()) {
            return Recommendation(
                hero = null,
                discoverFeed = emptyList(),
                explorationCardId = null,
                generatedAtMs = clock.nowMs(),
            )
        }

        // 2. Score each card: sum of profile weights over its tags (card-side weight = 1.0 per FR-11).
        fun score(c: Card): Float = c.tags.fold(0.0f) { acc, t -> acc + profile.weightOf(t) }

        // 3. Single-candidate early exit — no exploration possible.
        if (available.size == 1) {
            val only = available.first()
            return Recommendation(
                hero = only.copy(type = CardType.HERO),
                discoverFeed = emptyList(),
                explorationCardId = null,
                generatedAtMs = clock.nowMs(),
            )
        }

        // 4. Rank by score desc, tie-break by seedOrder asc (already sorted that way).
        val ranked = available.sortedWith(
            compareByDescending<Card> { score(it) }.thenBy { it.seedOrder }
        )

        // 5. Fixed-slot allocation (FR-11): exactly 1 exploration slot when size >= 2.
        val exploitationCount = (available.size - 1).coerceAtMost(count - 1)
        val exploitation = ranked.take(exploitationCount)

        // 6. Exploration: uniform random pick from (available - exploitation).
        val explorationPool = available - exploitation.toSet()
        val exploration = explorationPool.random(rng)

        // 7. Assemble: Hero = top exploitation pick (tagged HERO).
        //    Discover feed = remaining exploitation in score order, with exploration appended last.
        val hero = exploitation.first().copy(type = CardType.HERO)
        val discover = buildList {
            addAll(exploitation.drop(1))
            add(exploration)
        }.take(count - 1)

        return Recommendation(
            hero = hero,
            discoverFeed = discover,
            explorationCardId = exploration.id,
            generatedAtMs = clock.nowMs(),
        )
    }

    override fun setDeterministicMode(seed: Long) {
        if (BuildConfig.DEBUG) rng = Random(seed)
    }
}
```

**Cold-start behaviour** (profile empty): `score(c) == 0.0f` for all cards → tie-break by `seedOrder` → Hero is `_01` (the CONTINUE card) for the active intent. Exploration slot pulls uniformly from the remaining 3, which on cold start includes the `_04` NEW card at seed order 4, per FR-11 cold-start rule.

### 7.4 RNG injection

```kotlin
// di/DomainModule.kt (excerpt)
@Provides @Singleton @Named("recEngineRng")
fun provideRecEngineRng(): Random = Random.Default
```

Test replacement (JVM unit test):

```kotlin
@Provides @Singleton @Named("recEngineRng")
fun provideRecEngineRng(): Random = Random(seed = 42L)
```

### 7.5 Determinism contract
- Production: `Random.Default` — non-deterministic; acceptable because the Hero is always the top-score card (deterministic) and only the exploration slot varies.
- Unit tests: `Random(42L)` — fully reproducible per SM-07 (1000 calls).
- Debug builds: `setDeterministicMode(seed)` reseeds the held `rng` for demo scripts.

---

## 8. ViewModel Layer

Pattern: every VM exposes `val uiState: StateFlow<XxxUiState>` and a set of `fun onXxx(...)` intent methods. UI collects with `collectAsStateWithLifecycle()`. UiStates are **sealed** classes with a `Loading` / `Ready` / `Error` shape.

### 8.1 `HomeViewModel`

```kotlin
// ui/home/HomeUiState.kt
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val selectedIntent: Intent,
        val headerGreeting: String,         // FR-04 fixed strings (computed at render)
        val heroCard: Card?,                // null -> show EmptyFeedCard
        val discoverCards: List<Card>,
        val profile: UserProfile,
        val confidence: ConfidenceLevel,
        val showDefaultLauncherBanner: Boolean,
        val showSnackbar: String?,          // action-tap Snackbar (FR-08), transient
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}
```

```kotlin
// ui/home/HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val feedbackRepository: FeedbackRepository,
    private val profileRepository: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _selectedIntent = MutableStateFlow(Intent.WORK)    // FR-03 cold-start default
    private val _dismissedByIntent = MutableStateFlow<Map<Intent, Set<String>>>(emptyMap())
    private val _bannerVisible = MutableStateFlow(false)
    private val _transientSnackbar = MutableStateFlow<String?>(null)
    private val _lastIntentSwitchMs = MutableStateFlow(0L)         // FR-04 rapid-switch gate

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedIntent,
        _dismissedByIntent,
        profileRepository.observeProfile(),
        _bannerVisible,
        _transientSnackbar,
    ) { intent, dismissMap, profile, banner, snackbar ->
        val dismissed = dismissMap[intent].orEmpty()
        val rec = cardRepository.getRecommendation(intent, dismissed, profile)
        HomeUiState.Ready(
            selectedIntent = intent,
            headerGreeting = greetingFor(intent, clock),
            heroCard = rec.hero,
            discoverCards = rec.discoverFeed,
            profile = profile,
            confidence = profile.confidence(),
            showDefaultLauncherBanner = banner,
            showSnackbar = snackbar,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    // Actions
    fun selectIntent(intent: Intent) { /* updates _selectedIntent, _lastIntentSwitchMs */ }
    fun submitFeedback(cardId: String, signal: Signal) { /* delegate to FeedbackRepository.emit */ }
    fun dismissCard(cardId: String)                     { /* adds to _dismissedByIntent + emit(DISMISS) */ }
    fun actionTap(cardId: String, actionLabel: String)  { /* emit(ACTION_TAP) + _transientSnackbar */ }
    fun onSnackbarShown()                                { _transientSnackbar.value = null }
    fun setBannerVisible(visible: Boolean)               { _bannerVisible.value = visible }
    fun onHomeButtonPressed()                            { /* FR-14 re-entry: reset scroll in UI, preserve intent */ }
    fun onIntentChangedRapidly(): Boolean =
        clock.nowMs() - _lastIntentSwitchMs.value < 500   // FR-04 + FR-07 rapid-switch suppression
    fun refresh()                                        { /* force re-emit — called post-profile-reset */ }
}
```

Dwell tracking: implemented as a `Modifier.dwellTracker(cardId, intent, onDwellMet)` in `ui/components/DwellTracker.kt` that uses `onGloballyPositioned` + `Lifecycle.RESUMED` gate. The callback invokes `HomeViewModel.submitFeedback(cardId, Signal.Dwell)`. The tracker holds no state of its own beyond a per-composable `remember { DwellState(clock) }`.

### 8.2 `AppDrawerViewModel`

```kotlin
// ui/drawer/AppDrawerUiState.kt
sealed interface AppDrawerUiState {
    data object Loading : AppDrawerUiState
    data class Ready(val apps: List<InstalledApp>, val filter: String) : AppDrawerUiState
    data class Error(val message: String) : AppDrawerUiState
}
```

```kotlin
// ui/drawer/AppDrawerViewModel.kt
@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _filter = MutableStateFlow("")
    private val _apps   = MutableStateFlow<List<InstalledApp>>(emptyList())

    val uiState: StateFlow<AppDrawerUiState> = combine(_apps, _filter) { apps, q ->
        val filtered = if (q.isBlank()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
        AppDrawerUiState.Ready(apps = filtered, filter = q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDrawerUiState.Loading)

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _apps.value = queryLaunchable(context)
        }
    }

    fun setFilter(q: String) { _filter.value = q }

    private fun queryLaunchable(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(mainIntent, 0)
            .map { resolveInfo ->
                InstalledApp(
                    label       = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon        = resolveInfo.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
```

`refresh()` is invoked from `LaunchedEffect(Unit)` in `AppDrawerScreen` (per §7 "drawer refreshes only on navigate-to or cold start").

### 8.3 `ProfileDebugViewModel`

```kotlin
// ui/profile/ProfileDebugUiState.kt
sealed interface ProfileDebugUiState {
    data object Loading : ProfileDebugUiState
    data class Ready(
        val topTags: List<Pair<String, Float>>,
        val totalEvents: Int,
        val showResetDialog: Boolean,
    ) : ProfileDebugUiState
}
```

```kotlin
// ui/profile/ProfileDebugViewModel.kt
@HiltViewModel
class ProfileDebugViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val feedbackRepository: FeedbackRepository,
) : ViewModel() {

    private val _showResetDialog = MutableStateFlow(false)

    val uiState: StateFlow<ProfileDebugUiState> = combine(
        profileRepository.observeProfile(),
        feedbackRepository.observeCount(),
        _showResetDialog,
    ) { profile, count, showDialog ->
        ProfileDebugUiState.Ready(
            topTags = profile.top(10),
            totalEvents = count,
            showResetDialog = showDialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileDebugUiState.Loading)

    fun askReset()                 { _showResetDialog.value = true }
    fun confirmReset(onDone: () -> Unit) {
        viewModelScope.launch {
            profileRepository.reset()
            _showResetDialog.value = false
            onDone()                 // HomeViewModel.refresh() + close sheet + reset intent
        }
    }
    fun cancelReset()              { _showResetDialog.value = false }
    fun seedDemoProfile()          { viewModelScope.launch { profileRepository.seedDemoProfile() } }
}
```

---

## 9. DI (Hilt) Graph

All modules in `di/`. Hilt processes via KSP (not kapt) per §3.4.

### 9.1 `AppModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton @Named("ioDispatcher")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
```

### 9.2 `DatabaseModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .fallbackToDestructiveMigration()   // FR-09
            .build()

    @Provides fun provideFeedbackEventDao(db: AppDatabase): FeedbackEventDao = db.feedbackEventDao()
    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao    = db.userProfileDao()
    @Provides fun provideCachedCardDao(db: AppDatabase): CachedCardDao      = db.cachedCardDao()
}
```

### 9.3 `RepositoryModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindFeedbackRepo(impl: FeedbackRepositoryImpl): FeedbackRepository
    @Binds @Singleton abstract fun bindCardRepo(impl: CardRepositoryImpl): CardRepository
    @Binds @Singleton abstract fun bindProfileRepo(impl: ProfileRepositoryImpl): ProfileRepository
}
```

### 9.4 `DomainModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds @Singleton
    abstract fun bindRecommendationEngine(impl: EpsilonGreedyEngine): RecommendationEngine

    @Binds @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    companion object {
        @Provides @Singleton @Named("recEngineRng")
        fun provideRecEngineRng(): Random = Random.Default
    }
}
```

### 9.5 No `ViewModelModule` needed
All three VMs are annotated `@HiltViewModel`; Hilt's `ViewModelComponent` wires them directly from their `@Inject constructor` deps. No manual ViewModelFactory.

### 9.6 Graph summary (who depends on what)

```
AiLauncherApp (@HiltAndroidApp)
 └── MainActivity (@AndroidEntryPoint)
      ├── HomeViewModel (@HiltViewModel)
      │    ├── CardRepository          ← CardRepositoryImpl
      │    │    ├── CachedCardDao      ← AppDatabase
      │    │    ├── SeedLoader
      │    │    └── RecommendationEngine ← EpsilonGreedyEngine
      │    │         ├── @Named("recEngineRng") Random
      │    │         └── Clock         ← SystemClock
      │    ├── FeedbackRepository      ← FeedbackRepositoryImpl
      │    │    ├── FeedbackEventDao
      │    │    ├── UserProfileDao
      │    │    └── Clock
      │    ├── ProfileRepository       ← ProfileRepositoryImpl
      │    │    ├── UserProfileDao
      │    │    └── FeedbackEventDao
      │    └── Clock
      ├── AppDrawerViewModel (@HiltViewModel)
      │    └── @ApplicationContext Context
      └── ProfileDebugViewModel (@HiltViewModel)
           ├── ProfileRepository
           └── FeedbackRepository
```

---

## 10. Navigation

Single activity (`MainActivity`) hosts one `NavHost`. Three destinations:

```kotlin
// ui/navigation/Routes.kt
object Routes {
    const val HOME    = "home"
    const val DRAWER  = "drawer"
    const val PROFILE_SHEET = "profile_debug"   // see note below
}
```

```kotlin
// ui/navigation/AppNavHost.kt
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition  = { EnterTransition.None },     // FR-18 reduce-motion default for MVP
        exitTransition   = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition  = { ExitTransition.None },
    ) {
        composable(Routes.HOME)   { HomeScreen(navController) }
        composable(Routes.DRAWER) { AppDrawerScreen(navController) }
    }
}
```

**Profile v47 is NOT a route.** Per FR-12 it's a `ModalBottomSheet` owned by `HomeScreen`, toggled via local `rememberSaveable { mutableStateOf(false) }`. Routing a bottom sheet via Navigation would force a dialog-destination pattern that conflicts with the FR-14 Home-button re-entry spec (which requires dismissing the sheet as step 1). Keeping it screen-local makes that trivial.

**Shared transitions:** None in v0.1. `EnterTransition.None` / `ExitTransition.None` everywhere. Rationale: (a) launcher users expect instant, (b) FR-18 reduce-motion compliance is automatic if there's no motion, (c) shared-element transitions add Compose graph complexity that the MVP doesn't need. Cross-fade/slide moves return in v0.2 with proper motion scaling.

**Back-handling:**
- `DRAWER → HOME`: default back pops the stack (1 entry). `HomeViewModel` state is preserved (VM outlives the stack pop).
- `HomeScreen` profile sheet: `BackHandler(enabled = sheetVisible)` dismisses the sheet.
- Home button (via intent-filter): `MainActivity.onNewIntent` invokes `HomeViewModel.onHomeButtonPressed()` which triggers FR-14's 5-step reset.

---

## 11. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Android 11+ (API 30+) package visibility. Needed so PackageManager returns
         all launchable apps for the drawer (FR-13, SM-08). -->
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>

    <!-- No INTERNET permission. No READ_EXTERNAL_STORAGE. No BIND_ACCESSIBILITY_SERVICE.
         Offline-first per NFR-04 / NFR-06. -->

    <application
        android:name=".AiLauncherApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@style/Theme.AiLauncher"
        android:supportsRtl="true"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true"
            android:clearTaskOnLaunch="true"
            android:configChanges="orientation|screenSize|keyboardHidden|uiMode|fontScale"
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.AiLauncher"
            android:resizeableActivity="true">

            <!-- Primary launch -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Home launcher eligibility (FR-14) -->
            <intent-filter android:priority="1">
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

**Theme `Theme.AiLauncher`** (in `res/values/themes.xml`): `parent="Theme.Material3.DayNight.NoActionBar"` with:

- `android:windowBackground = @color/bg_deep_navy` (`#0a0f1e` — prevents white flash on cold-start before Compose takes over).
- `android:statusBarColor = @android:color/transparent`
- `android:navigationBarColor = @android:color/transparent`
- `android:windowLightStatusBar = false` / `android:windowLightNavigationBar = false` (light icons on dark bg)
- `android:windowTranslucentStatus = false`, `android:windowTranslucentNavigation = false`

The Compose layer draws its own gradient over this; the `windowBackground` exists solely to avoid the system-window flash during `onCreate`.

**`data_extraction_rules.xml`** (res/xml): opts out of auto-backup of Room DB (wipes are fine for MVP, and NFR-06 mandates local-only data) — stub rule set excluding the `ai_launcher.db` file.

---

## 12. Testing Architecture

### 12.1 Unit tests (JVM; `src/test/`)

Framework: JUnit 4 + MockK + kotlinx-coroutines-test + Truth. Room NOT used here — repositories are faked.

**Test doubles** (shared under `src/test/.../data/repo/`):

```kotlin
class FakeFeedbackRepository : FeedbackRepository {
    private val events = mutableListOf<FeedbackEvent>()
    private val countFlow = MutableStateFlow(0)
    override val isHealthy = true
    override suspend fun emit(cardId, intent, signal, cardTags): FeedbackEvent? { ... }
    override fun observeCount() = countFlow
    override suspend fun count() = events.size
    override suspend fun getAll() = events.toList()
    override suspend fun clearAll() { events.clear(); countFlow.value = 0 }
}
class FakeCardRepository(private val cards: List<Card> = SEED_CARDS_AS_DOMAIN) : CardRepository { ... }
class FakeProfileRepository : ProfileRepository { ... }
class FakeClock(var now: Long = 0L) : Clock { override fun nowMs() = now }
```

**Test suites (minimum set):**

| File | Coverage |
|---|---|
| `EpsilonGreedyEngineTest` | SM-07 (1000 calls, seeded `Random(42L)`, verify Hero determinism + exploration distribution). Cold-start order (`work_01` hero). Single-candidate early exit. Empty input. All-dismissed. Ties broken by seedOrder. |
| `HomeViewModelTest` | Intent switch clears dismiss for that intent's history (FR-06 rule b). Rapid-switch suppression (<500ms) does not emit dwell. Feedback submit routes through repo. Snackbar transience. Banner visibility toggling. |
| `ProfileDebugViewModelTest` | Reset dialog flow. `confirmReset` invokes `profileRepo.reset()` + `onDone`. Top tags derivation from `observeProfile()`. |
| `FeedbackRepositoryImplTest` | Debounce (FR-02 400ms) with `FakeClock`. Transactional profile update with per-tag deltas matching FR-10 formula. |

`@get:Rule val mainDispatcherRule = MainDispatcherRule()` is included (standard coroutines-test 1.9 fixture) so VMs can use `viewModelScope`.

### 12.2 Instrumented tests (`src/androidTest/`) — minimize

Only what truly needs a device. CI runs unit tests only; instrumented tests are local-dev + pre-release manual.

| File | Coverage |
|---|---|
| `FeedbackEventDaoTest` | Insert, count, debounce query, `clear()`. Uses `Room.inMemoryDatabaseBuilder`. |
| `UserProfileDaoTest` | `addWeight` transaction correctness; `observeAll` emits on change. |
| `LauncherIntentFilterTest` | `PackageManager.resolveActivity(Intent(ACTION_MAIN) + CATEGORY_HOME)` returns our `ComponentName` after install. Confirms FR-14 manifest wiring. |

No Compose UI tests in v0.1 — lazy but justified: the flywheel math (engine) is the risky part; UI is styled stateless components with Previews.

### 12.3 Deterministic time contract

Every test asserting timing uses `FakeClock`. Any production code doing `System.currentTimeMillis()` directly is a bug and fails a lint-equivalent grep check in CI (a simple `grep -r "System.currentTimeMillis" app/src/main/java | wc -l` == 0 in the test.yml workflow's pre-step, to be added by dev agent if scoping permits).

### 12.4 Test dispatchers
- `StandardTestDispatcher` for VM tests — lets us advance virtual time for debounce assertions.
- `UnconfinedTestDispatcher` for repo tests where we just want the flow to emit synchronously.

---

## 13. Performance Budgets

PRD NFR-01..NFR-03 compliance strategy:

| Budget | How we hit it |
|---|---|
| Cold start → Hero content < 500ms (NFR-01 / SM-05) | `MainActivity` `setContent { }` happens synchronously; `HomeViewModel` emits `Ready` as soon as `profileRepository.observeProfile()` produces its first value (cold start: `UserProfile.EMPTY` within one DB round trip, <30ms). Engine is pure in-memory over 4 cards → <1ms. `SEED_CARDS` is a `val` compiled into the APK — no file I/O. Manifest `windowBackground` matches gradient bg so no flash. |
| Room query < 50ms p95 (NFR-02) | Indexes on `feedback_events.card_id`, `feedback_events.intent`, `feedback_events.timestamp_ms`, and `cached_cards.intent`. `user_profile` is key-value PK so all reads are constant-time. Top-10 query uses `ORDER BY weight DESC LIMIT 10` — hits the PK table's full scan over ≤ tag-vocabulary-size (currently ~40 tags) which is trivial. |
| Intent switch < 200ms (NFR-03) | `_selectedIntent` is a `StateFlow` → `combine` re-fires → engine re-runs (<1ms) → Compose recomposition. No DB query is needed on the hot path (profile is already observed). |
| Compose stability | Every domain model is a `data class` with `val` fields (stable). `List<Card>` is passed as `ImmutableList` via `kotlinx.collections.immutable` — **architect decision A-2:** we add `kotlinx-collections-immutable` *only if* lint flags recomposition issues during dev. Not pinned in §3.1 now to keep the dep graph tight. |
| LazyColumn perf | `items(discoverCards, key = { it.id }) { ... }` in `HomeScreen` so card composables are reused across intent switches. `DiscoverCard` is split: outer `GlassCard` + inner content, both receiving stable types. |
| Animation budget (FR-18 reduce-motion) | Two animations only: FR-02 hero crossfade (300ms) and FR-06 dismiss slide+fade (200ms). Both gated by `LocalAnimationsEnabled.current` which reads `Settings.Global.TRANSITION_ANIMATION_SCALE`. When off, transitions collapse to 0ms. |

---

## 14. Security / Privacy

Per NFR-04 / NFR-06:

- **No permissions declared beyond manifest defaults.** No `INTERNET`, no `ACCESS_*`, no `READ_*`, no `BIND_*`.
- **No network clients.** OkHttp, Retrofit, Ktor are banned in v0.1. `kotlinx-serialization-json` is present for internal diagnostics only; it does not imply a network client.
- **No analytics / crash-reporter SDK.** No Firebase, Sentry, Crashlytics, etc. Crashes surface only to local `logcat`.
- **Data location:** Room DB at `/data/data/com.bobpan.ailauncher[.debug]/databases/ai_launcher.db`. Standard app-private storage; not readable by other apps.
- **Backup:** `android:allowBackup="false"` and `dataExtractionRules` exclude the DB, so feedback doesn't leak to cloud backups either.
- **Debug logcat** (FR-20 `FLYWHEEL` tag): local device only, no egress. Stripped from release by the `BuildConfig.DEBUG` guard.
- **PackageManager queries** (drawer): read-only; no accessibility service; no usage-stats permission.

Threat model in scope: a curious user `adb pull`-ing their own data. Out of scope: device-compromise scenarios, on-device sandboxing of other apps.

---

## 15. Build / CI

The repository already ships:
- `.github/workflows/build-apk.yml` — runs `./gradlew :app:assembleDebug --stacktrace --no-daemon` on push/PR to `main`. Publishes the APK artifact + Telegram notification.
- `.github/workflows/test.yml` — runs `./gradlew :app:testDebugUnitTest --stacktrace --no-daemon`. Uploads test reports.

Both workflows:
- Use JDK 17 (Temurin) via `actions/setup-java@v4`.
- Use `android-actions/setup-android@v3` for SDK provisioning.
- `chmod +x ./gradlew || true` before invoking — tolerant of either a committed or generated wrapper.

**Architecture-level guarantees that keep CI green:**
1. `:app:assembleDebug` has **no** extra module dependencies — single Gradle module.
2. No repositories block uses custom Maven URLs; everything is on Google Maven + Maven Central (already in `settings.gradle.kts` §3.3).
3. Unit tests do not touch Room; no `robolectric` dep, no Android resources required at test time (`unitTests.isReturnDefaultValues = true`).
4. Schemas go to `app/schemas/` (checked in) so Room's KSP step is reproducible across hosts.
5. Gradle wrapper will be generated during the dev phase via `gradle wrapper --gradle-version 8.11.1` (the dev agent runs this once and commits the result).

**Dev-agent acceptance gate** (must pass before merging any non-doc PR):
```bash
./gradlew :app:assembleDebug         # green
./gradlew :app:testDebugUnitTest     # green, all tests listed in §12.1 present & passing
./gradlew :app:lintDebug             # no FATAL
```

---

## Architect Decisions Log (items NOT explicit in PRD-v2)

Tracked here so future PRD revisions can challenge them:

- **A-1 · Signal taxonomy extension.** Added `Tap` (+2), `IntentSwitch` (0), `RevealAppeared` (0) to the sealed `Signal` hierarchy to match the task spec. PRD-v2 FR-02/06/07/08 only enumerate THUMB_UP/THUMB_DOWN/DISMISS/DWELL/ACTION_TAP. `IntentSwitch` / `RevealAppeared` are zero-weight so the profile formula (FR-10) is unaffected. `Tap` is reserved (unused in v0.1 UI flows).
- **A-2 · `kotlinx-collections-immutable` deferred.** Added to the catalog conceptually but **not** to `app/build.gradle.kts` in §3.4 unless Compose stability lint flags a problem. Keeps dep graph tight.
- **A-3 · CachedCards table is debug/mirror only.** PRD §10 says "Room tables: feedback_events, user_profile, cards (cache)." I scoped `cards` (cache) to a read-mirror; the *source of truth* for the 16 seeds remains `SEED_CARDS` in code. This preserves FR-19's "Room unavailable" fallback trivially.
- **A-4 · `HERO` CardType is a rendering hint, not a seed type.** PRD-v2 FR-01 and seed spec only use CONTINUE/DISCOVER/NEW. `HERO` is assigned at engine-output time to whichever card fills the hero slot. This lets the UI switch rendering without a second type system.
- **A-5 · Profile sheet is NOT a Navigation destination.** It's a screen-local `ModalBottomSheet`. Justified under §10; keeps FR-14 Home-re-entry logic simple.
- **A-6 · Shared transitions = none for MVP.** Explicit `EnterTransition.None` everywhere. FR-18 reduce-motion compliance by default.
- **A-7 · Type converters minimal.** Enums serialized as `.name` at the mapper layer, not in Room. Simpler, renames are explicit.
- **A-8 · No DataStore in v0.1.** The only candidate for persisted-preference (banner dismissed) is explicitly session-only per FR-17. Catalog keeps the version for future use.

---

*End of ARCHITECTURE v1.*
