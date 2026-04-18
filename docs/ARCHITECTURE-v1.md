# ARCHITECTURE v1 — AI Launcher MVP (v0.1)

**Status:** Approved for build · **Owner:** Tech Architect · **Last updated:** 2026-04-18
**Companion docs:** `AGENTS.md`, `docs/PRD-v2.md`, `docs/DESIGN-v1.md`
**Scope:** The Phase 3 dev agent should be able to implement v0.1 without making architectural decisions. Every module, class, interface, DAO, DI binding, and Gradle line below is prescriptive. Where this document appears to disagree with PRD-v2 or DESIGN-v1, **PRD-v2 wins on behavior, DESIGN-v1 wins on visual/interaction, this doc wins on technical wiring**.

**Locked tech stack (reiterated):**
Kotlin 2.0.21 · Compose BOM 2024.12.01 · Material 3 · Hilt 2.52 · Room 2.6.1 · AGP 8.7.3 · Gradle 8.11.1 · JDK 17 · minSdk 26 · targetSdk 34 · package `com.bobpan.ailauncher`.

---

## 1. System Architecture Overview

Classic MVVM + clean-layering, single-activity, single-module. Unidirectional data flow: UI → intent callbacks → ViewModel → Domain/Repo → Room → Flow → ViewModel → StateFlow → UI.

```
┌──────────────────────────────────────────────────────────────────┐
│                           UI LAYER                                │
│  (Jetpack Compose · Material 3 · single Activity)                 │
│                                                                    │
│   MainActivity ──► NavHost ──► HomeScreen / AppDrawerScreen       │
│                          │                                         │
│                          ├── HeroForYouCard                       │
│                          ├── IntentChipStrip                      │
│                          ├── DiscoverCard × N                     │
│                          ├── AiDock                               │
│                          ├── ProfileDebugSheet (overlay)          │
│                          └── theme/ · components/                 │
│                                                                    │
│   stateless composables ◄── collectAsStateWithLifecycle ──┐       │
└───────────────────────────────────────────────────────────┼──────┘
                                                            │
                                                            ▼
┌──────────────────────────────────────────────────────────────────┐
│                        VIEWMODEL LAYER                            │
│  (androidx.lifecycle · StateFlow · Hilt @HiltViewModel)          │
│                                                                   │
│   HomeViewModel ── exposes StateFlow<HomeUiState>                 │
│   AppDrawerViewModel ── exposes StateFlow<DrawerUiState>          │
│   ProfileDebugViewModel ── exposes StateFlow<ProfileSheetState>   │
│                                                                   │
│   Input: user actions (typed methods)                             │
│   Output: immutable UiState sealed hierarchies                    │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                         DOMAIN LAYER                              │
│  (pure Kotlin · no Android deps · deterministic)                  │
│                                                                   │
│   RecommendationEngine (ε-greedy stub, FR-11)                     │
│     inputs:  intent, profile snapshot, dismiss set, catalog       │
│     deps:    Random (seeded in tests), Clock                      │
│     output:  List<Card> (size ≤ count)                            │
│                                                                   │
│   Domain models: Intent, Card, CardType, Signal, UserProfile,     │
│                  ConfidenceLevel, AppInfo                         │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                           DATA LAYER                              │
│  (Room · coroutines · Flow · DataStore)                           │
│                                                                   │
│   Repositories (interfaces in data/repo, impls in data/repo/impl) │
│     FeedbackRepository  ── writes events, updates profile txn     │
│     CardRepository      ── seed + cached catalog                  │
│     ProfileRepository   ── top tags, reset, counter (Flow)        │
│     PackageAppsRepository ── PackageManager wrap for drawer       │
│                                                                   │
│   Room:  LauncherDatabase                                         │
│     • feedback_events (FeedbackEventEntity)                       │
│     • user_profile    (UserProfileEntry)                          │
│     • cached_cards    (CachedCardEntity)                          │
│     DAOs expose Flow<T> for all hot reads                         │
│                                                                   │
│   Seed: SeedCards.kt (hardcoded val SEED_CARDS) → installed into  │
│         cached_cards on first boot via SeedInstaller              │
│                                                                   │
│   DataStore Preferences: (reserved; v0.1 uses none)               │
└──────────────────────────────────────────────────────────────────┘
```

**Threading contract.**
- UI: Main (Compose).
- ViewModel: `viewModelScope` default `Dispatchers.Main.immediate`; repo/engine calls hop to `Dispatchers.IO` via repo impls.
- Room: DAO suspend funcs run on Room's built-in IO executor; Flow emissions are main-safe by Room contract.
- Engine: synchronous, pure, runs on caller's dispatcher (called from IO inside `HomeViewModel.refresh`).
- No `GlobalScope`. No `runBlocking`. No services. No `WorkManager`.

**Navigation model.** Single `MainActivity` hosting one `NavHost` with two destinations (`home`, `drawer`). The `profile_debug` sheet is **not** a nav route — it is a Compose overlay (`ModalBottomSheet`) scoped to `HomeScreen` composition and controlled by `HomeViewModel.profileSheetOpen: Boolean`.

---

## 2. Module / Package Structure

Single Gradle module `:app`. Exact tree under `app/src/main/java/com/bobpan/ailauncher/`:

```
app/src/main/java/com/bobpan/ailauncher/
├── MainActivity.kt                              // Activity, launcher intent filter owner
├── LauncherApp.kt                               // @HiltAndroidApp Application
│
├── di/
│   ├── AppModule.kt                             // Clock, CoroutineDispatchers
│   ├── DatabaseModule.kt                        // Room DB + DAOs (@Provides)
│   ├── DomainModule.kt                          // Random (@Named recEngineRng), Engine
│   └── RepositoryModule.kt                      // @Binds interface → impl
│
├── data/
│   ├── db/
│   │   ├── LauncherDatabase.kt                  // @Database(version=1)
│   │   ├── Converters.kt                        // TypeConverter<List<String>> via kotlinx-serialization
│   │   ├── entity/
│   │   │   ├── FeedbackEventEntity.kt
│   │   │   ├── UserProfileEntry.kt
│   │   │   └── CachedCardEntity.kt
│   │   └── dao/
│   │       ├── FeedbackDao.kt
│   │       ├── UserProfileDao.kt
│   │       └── CardDao.kt
│   │
│   ├── model/
│   │   ├── Intent.kt                            // enum class Intent
│   │   ├── CardType.kt                          // enum class CardType
│   │   ├── Card.kt                              // immutable data class (domain)
│   │   ├── Signal.kt                            // sealed class Signal
│   │   ├── UserProfile.kt                       // snapshot domain model
│   │   ├── ConfidenceLevel.kt                   // enum class (LOW/MED/HIGH)
│   │   └── AppInfo.kt                           // drawer item
│   │
│   ├── seed/
│   │   ├── SeedCard.kt                          // val SEED_CARDS: List<Card>
│   │   ├── SeedCards.kt                         // the 16-card catalog from PRD §9
│   │   └── SeedInstaller.kt                     // first-boot populator
│   │
│   └── repo/
│       ├── FeedbackRepository.kt                // interface
│       ├── CardRepository.kt                    // interface
│       ├── ProfileRepository.kt                 // interface
│       ├── PackageAppsRepository.kt             // interface
│       └── impl/
│           ├── FeedbackRepositoryImpl.kt
│           ├── CardRepositoryImpl.kt
│           ├── ProfileRepositoryImpl.kt
│           └── PackageAppsRepositoryImpl.kt
│
├── domain/
│   └── recommend/
│       ├── RecommendationEngine.kt              // interface
│       └── EpsilonGreedyEngine.kt               // impl (FR-11)
│
├── ui/
│   ├── theme/
│   │   ├── Color.kt                             // LauncherPalette + darkColorScheme
│   │   ├── Type.kt                              // LauncherTypography
│   │   ├── Shape.kt                             // LauncherShapes
│   │   ├── Motion.kt                            // Motion tokens (object)
│   │   ├── Glass.kt                             // GlassTokens + glassSurface Modifier
│   │   └── Theme.kt                             // @Composable LauncherTheme
│   │
│   ├── home/
│   │   ├── HomeScreen.kt                        // @Composable top-level
│   │   ├── HomeViewModel.kt                     // @HiltViewModel
│   │   ├── HomeUiState.kt                       // sealed + data class HomeContent
│   │   └── HeaderRow.kt
│   │
│   ├── components/
│   │   ├── HeroForYouCard.kt
│   │   ├── IntentChip.kt                        // + IntentChipStrip
│   │   ├── DiscoverCard.kt                      // + NewPill
│   │   ├── AiDock.kt
│   │   ├── ConfidenceBar.kt
│   │   ├── FeedbackButtonRow.kt
│   │   ├── DefaultLauncherBanner.kt
│   │   └── EmptyIntentState.kt
│   │
│   ├── profile/
│   │   ├── ProfileDebugSheet.kt                 // @Composable ModalBottomSheet
│   │   ├── ProfileDebugRow.kt
│   │   ├── ProfileDebugViewModel.kt             // @HiltViewModel
│   │   └── ProfileSheetState.kt                 // sealed
│   │
│   ├── drawer/
│   │   ├── AppDrawerScreen.kt
│   │   ├── AppIconTile.kt
│   │   ├── AppDrawerViewModel.kt                // @HiltViewModel
│   │   └── DrawerUiState.kt                     // sealed
│   │
│   └── nav/
│       └── LauncherNavHost.kt                   // NavHost + routes
│
└── util/
    ├── Clock.kt                                 // interface + SystemClock impl
    ├── Dispatchers.kt                           // AppDispatchers (IO/Main/Default)
    ├── Dwell.kt                                 // per-card appearance tracker (FR-07)
    ├── DefaultLauncherCheck.kt                  // PackageManager helper
    └── Ext.kt                                   // small extensions
```

**Root project paths (not under the Kotlin source set):**
```
/
├── settings.gradle.kts
├── build.gradle.kts                             // project-level
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts                         // app-level
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/bobpan/ailauncher/…
        │   └── res/
        │       ├── values/
        │       │   ├── strings.xml
        │       │   ├── themes.xml
        │       │   └── colors.xml
        │       └── xml/
        │           └── backup_rules.xml
        ├── test/
        │   └── java/com/bobpan/ailauncher/…    // unit tests
        └── androidTest/
            └── java/com/bobpan/ailauncher/…    // instrumented tests (minimal)
```

---

## 3. Gradle Configuration

### 3.1 `gradle/libs.versions.toml` (version catalog — single source of truth)

```toml
[versions]
# Build
agp                       = "8.7.3"
kotlin                    = "2.0.21"
ksp                       = "2.0.21-1.0.27"

# AndroidX core
coreKtx                   = "1.13.1"
activityCompose           = "1.9.3"
lifecycle                 = "2.8.7"
navigationCompose         = "2.8.4"
datastore                 = "1.1.1"

# Compose (via BOM)
composeBom                = "2024.12.01"

# DI
hilt                      = "2.52"
hiltNavCompose            = "1.2.0"

# Persistence
room                      = "2.6.1"

# Kotlinx
coroutines                = "1.9.0"
serializationJson         = "1.7.3"

# Testing
junit                     = "4.13.2"
androidxTestExt           = "1.2.1"
androidxTestEspresso      = "3.6.1"
mockk                     = "1.13.13"
coroutinesTest            = "1.9.0"
turbine                   = "1.2.0"
roomTesting               = "2.6.1"

[libraries]
# AndroidX core
androidx-core-ktx                  = { module = "androidx.core:core-ktx",                         version.ref = "coreKtx" }
androidx-activity-compose          = { module = "androidx.activity:activity-compose",             version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose   = { module = "androidx.lifecycle:lifecycle-runtime-compose",   version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx       = { module = "androidx.lifecycle:lifecycle-runtime-ktx",       version.ref = "lifecycle" }
androidx-navigation-compose        = { module = "androidx.navigation:navigation-compose",         version.ref = "navigationCompose" }
androidx-datastore-preferences     = { module = "androidx.datastore:datastore-preferences",       version.ref = "datastore" }

# Compose BOM + libs
androidx-compose-bom                = { module = "androidx.compose:compose-bom",                  version.ref = "composeBom" }
androidx-compose-ui                 = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics        = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling         = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-test-junit4     = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest   = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-compose-material3          = { module = "androidx.compose.material3:material3" }
androidx-compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }

# Hilt
hilt-android                        = { module = "com.google.dagger:hilt-android",                version.ref = "hilt" }
hilt-android-compiler               = { module = "com.google.dagger:hilt-android-compiler",       version.ref = "hilt" }
androidx-hilt-navigation-compose    = { module = "androidx.hilt:hilt-navigation-compose",         version.ref = "hiltNavCompose" }

# Room
androidx-room-runtime               = { module = "androidx.room:room-runtime",                    version.ref = "room" }
androidx-room-ktx                   = { module = "androidx.room:room-ktx",                        version.ref = "room" }
androidx-room-compiler              = { module = "androidx.room:room-compiler",                   version.ref = "room" }
androidx-room-testing               = { module = "androidx.room:room-testing",                    version.ref = "roomTesting" }

# Kotlinx
kotlinx-coroutines-core             = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android          = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json          = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serializationJson" }

# Unit testing
junit                               = { module = "junit:junit",                                   version.ref = "junit" }
mockk                               = { module = "io.mockk:mockk",                                version.ref = "mockk" }
kotlinx-coroutines-test             = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesTest" }
turbine                             = { module = "app.cash.turbine:turbine",                      version.ref = "turbine" }

# Instrumented testing
androidx-test-ext-junit             = { module = "androidx.test.ext:junit",                       version.ref = "androidxTestExt" }
androidx-test-espresso-core         = { module = "androidx.test.espresso:espresso-core",          version.ref = "androidxTestEspresso" }

[plugins]
android-application     = { id = "com.android.application",                version.ref = "agp" }
kotlin-android          = { id = "org.jetbrains.kotlin.android",           version.ref = "kotlin" }
kotlin-compose          = { id = "org.jetbrains.kotlin.plugin.compose",    version.ref = "kotlin" }
kotlin-serialization    = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp                     = { id = "com.google.devtools.ksp",                version.ref = "ksp" }
hilt                    = { id = "com.google.dagger.hilt.android",         version.ref = "hilt" }
```

### 3.2 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AI Launcher"
include(":app")
```

### 3.3 Project-level `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.kotlin.android)        apply false
    alias(libs.plugins.kotlin.compose)        apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.ksp)                   apply false
    alias(libs.plugins.hilt)                  apply false
}
```

### 3.4 App-level `app/build.gradle.kts`

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
    namespace  = "com.bobpan.ailauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bobpan.ailauncher"
        minSdk        = 26
        targetSdk     = 34
        versionCode   = 1
        versionName   = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("Boolean", "DETERMINISTIC_ENGINE", "true")
        }
        release {
            isMinifyEnabled   = false            // v0.1 ships debug-signed per CI
            isShrinkResources = false
            buildConfigField("Boolean", "DETERMINISTIC_ENGINE", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental",    "true")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
}
```

### 3.5 `gradle.properties` (required entries)

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=false
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
ksp.incremental=true
```

---

## 4. Data Layer

### 4.1 Entities

#### `FeedbackEventEntity`

Persists every Signal emission (PRD FR-09).

```kotlin
@Entity(
    tableName = "feedback_events",
    indices = [
        Index(value = ["card_id"]),
        Index(value = ["intent"]),
        Index(value = ["timestamp_ms"])
    ]
)
data class FeedbackEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "card_id")      val cardId: String,
    @ColumnInfo(name = "intent")       val intent: String,   // Intent.name
    @ColumnInfo(name = "signal")       val signal: String,   // SignalType.name
    @ColumnInfo(name = "weight")       val weight: Float,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long
)
```

#### `UserProfileEntry`

One row per tag; running weight (PRD FR-10).

```kotlin
@Entity(
    tableName = "user_profile",
    indices = [Index(value = ["weight"])]   // top-tag query hot path
)
data class UserProfileEntry(
    @PrimaryKey
    @ColumnInfo(name = "tag")          val tag: String,
    @ColumnInfo(name = "weight")       val weight: Float,
    @ColumnInfo(name = "updated_ms")   val updatedMs: Long
)
```

#### `CachedCardEntity`

Seed catalog materialized into DB on first boot. Tag list serialized as JSON.

```kotlin
@Entity(
    tableName = "cached_cards",
    indices = [Index(value = ["intent"])]
)
data class CachedCardEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")            val id: String,
    @ColumnInfo(name = "title")         val title: String,
    @ColumnInfo(name = "description")   val description: String,
    @ColumnInfo(name = "intent")        val intent: String,       // Intent.name
    @ColumnInfo(name = "type")          val type: String,         // CardType.name
    @ColumnInfo(name = "icon")          val icon: String,         // emoji
    @ColumnInfo(name = "action_label")  val actionLabel: String,
    @ColumnInfo(name = "why_label")     val whyLabel: String,
    @ColumnInfo(name = "tags_json")     val tagsJson: String,     // JSON-serialized List<String>
    @ColumnInfo(name = "seed_order")    val seedOrder: Int        // stable ordering
)
```

#### `Converters.kt`

```kotlin
class Converters @Inject constructor(
    private val json: Json   // provided by DomainModule
) {
    @TypeConverter fun tagsToJson(list: List<String>): String = json.encodeToString(list)
    @TypeConverter fun jsonToTags(raw: String): List<String>  = json.decodeFromString(raw)
}
```

(Converters are registered via `@TypeConverters(Converters::class)` on the DB.)

### 4.2 DAOs

#### `FeedbackDao`

```kotlin
@Dao
interface FeedbackDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: FeedbackEventEntity): Long

    @Query("SELECT COUNT(*) FROM feedback_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM feedback_events")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM feedback_events ORDER BY timestamp_ms DESC")
    fun observeAll(): Flow<List<FeedbackEventEntity>>

    @Query("SELECT * FROM feedback_events ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<FeedbackEventEntity>

    @Query("DELETE FROM feedback_events")
    suspend fun clear()
}
```

#### `UserProfileDao`

```kotlin
@Dao
interface UserProfileDao {

    /** Upsert used by FR-10 transactional update. */
    @Query("""
        INSERT INTO user_profile (tag, weight, updated_ms)
        VALUES (:tag, :delta, :now)
        ON CONFLICT(tag) DO UPDATE SET
            weight = weight + :delta,
            updated_ms = :now
    """)
    suspend fun upsertDelta(tag: String, delta: Float, now: Long)

    @Query("SELECT * FROM user_profile")
    fun observeAll(): Flow<List<UserProfileEntry>>

    @Query("SELECT * FROM user_profile ORDER BY ABS(weight) DESC LIMIT :limit")
    fun observeTopByAbs(limit: Int): Flow<List<UserProfileEntry>>

    @Query("SELECT * FROM user_profile ORDER BY weight DESC LIMIT :limit")
    suspend fun topPositive(limit: Int): List<UserProfileEntry>

    @Query("SELECT * FROM user_profile")
    suspend fun snapshot(): List<UserProfileEntry>

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}
```

#### `CardDao`

```kotlin
@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CachedCardEntity>)

    @Query("SELECT COUNT(*) FROM cached_cards")
    suspend fun count(): Int

    @Query("SELECT * FROM cached_cards ORDER BY seed_order ASC")
    fun observeAll(): Flow<List<CachedCardEntity>>

    @Query("SELECT * FROM cached_cards WHERE intent = :intent ORDER BY seed_order ASC")
    fun observeByIntent(intent: String): Flow<List<CachedCardEntity>>

    @Query("SELECT * FROM cached_cards WHERE intent = :intent ORDER BY seed_order ASC")
    suspend fun byIntent(intent: String): List<CachedCardEntity>

    @Query("SELECT * FROM cached_cards ORDER BY seed_order ASC")
    suspend fun all(): List<CachedCardEntity>
}
```

### 4.3 Database

```kotlin
@Database(
    entities = [
        FeedbackEventEntity::class,
        UserProfileEntry::class,
        CachedCardEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun feedbackDao():    FeedbackDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun cardDao():        CardDao

    companion object {
        const val NAME = "launcher.db"
    }
}
```

**Migration policy (FR-09):** `.fallbackToDestructiveMigration()` is enabled in the builder (see §9 DI). This is documented in release notes; v0.1 does not ship migration logic.

**Transactional write (FR-10):** the combined `insert(event) + upsertDelta(tag, Δ) * N` operation is wrapped in a `@Transaction` suspend function on `FeedbackRepositoryImpl` using `database.withTransaction { ... }` (from `androidx.room:room-ktx`). Either all writes succeed or none do.

### 4.4 Seed data & installer

`data/seed/SeedCards.kt` declares a `val SEED_CARDS: List<Card>` — 16 entries matching PRD-v2 §9 exactly (id/intent/type/tags load-bearing; text fields from DESIGN-v1 §8.3 copywriting table).

```kotlin
// data/seed/SeedCards.kt
val SEED_CARDS: List<Card> = listOf(
    Card(id = "commute_01", intent = Intent.COMMUTE, type = CardType.CONTINUE,
         icon = "🎧", title = "继续听《三体》",
         description = "上次停在第 14 章，还剩 23 分钟",
         actionLabel = "继续播放", whyLabel = "示例：你在听的有声书",
         tags = listOf("audiobook","scifi","commute","morning"),
         seedOrder = 0),
    // … 15 more, indexing seedOrder 1..15 in the exact declaration order from PRD §9
)
```

`data/seed/SeedInstaller.kt` populates `cached_cards` on first boot:

```kotlin
class SeedInstaller @Inject constructor(
    private val cardDao: CardDao,
    private val json:    Json,
    private val dispatchers: AppDispatchers
) {
    /** Called from LauncherApp.onCreate (fire-and-forget) or first CardRepository.getAll() call. */
    suspend fun installIfEmpty() = withContext(dispatchers.io) {
        if (cardDao.count() == 0) {
            cardDao.insertAll(SEED_CARDS.mapIndexed { idx, c -> c.toEntity(json, idx) })
        }
    }
}

// Card <-> entity mapping extension
fun Card.toEntity(json: Json, seedOrder: Int) = CachedCardEntity(
    id = id, title = title, description = description,
    intent = intent.name, type = type.name,
    icon = icon, actionLabel = actionLabel, whyLabel = whyLabel,
    tagsJson = json.encodeToString(tags),
    seedOrder = seedOrder
)
```

`CardRepositoryImpl.getAll()` calls `installer.installIfEmpty()` before its first emit so the flow is never empty on first boot.

---

## 5. Domain Models

All domain models are **immutable** (`data class` or `enum` or `sealed class`/`object`), framework-agnostic, and serializable via `kotlinx.serialization` where needed.

### 5.1 `Intent.kt`

```kotlin
enum class Intent { COMMUTE, WORK, LUNCH, REST }
```

Default active intent on cold start = `Intent.WORK` (PRD FR-03).

### 5.2 `CardType.kt`

```kotlin
enum class CardType { HERO, CONTINUE, DISCOVER, NEW }
```

> Note: `HERO` is kept in the enum for UI-layer categorization (which slot a given Card occupies) but is **not** used in the seed catalog — seed cards are only `CONTINUE`, `DISCOVER`, or `NEW`. The `HERO` slot is a rendering decision made by `HomeViewModel` based on `recommend()` output position 0.

### 5.3 `Card.kt`

```kotlin
@Immutable
data class Card(
    val id:           String,
    val intent:       Intent,
    val type:         CardType,
    val icon:         String,
    val title:        String,
    val description:  String,
    val actionLabel:  String,
    val whyLabel:     String,
    val tags:         List<String>,
    val seedOrder:    Int = 0
)
```

The `@Immutable` annotation (from `androidx.compose.runtime`) marks the class as stable for Compose skippability.

### 5.4 `Signal.kt`

Sealed class — one subclass per signal type, all carrying `cardId` + `timestampMs` (FR-07 test-hook Clock).

```kotlin
sealed class Signal {
    abstract val cardId:      String
    abstract val timestampMs: Long

    data class Tap(            override val cardId: String, override val timestampMs: Long) : Signal()
    data class Dwell(          override val cardId: String, override val timestampMs: Long, val durationMs: Long) : Signal()
    data class Like(           override val cardId: String, override val timestampMs: Long) : Signal()
    data class Dislike(        override val cardId: String, override val timestampMs: Long) : Signal()
    data class Dismiss(        override val cardId: String, override val timestampMs: Long) : Signal()
    data class ActionTapped(   override val cardId: String, override val timestampMs: Long) : Signal()
    data class IntentSwitch(   override val cardId: String, override val timestampMs: Long, val to: Intent) : Signal()
    data class RevealAppeared( override val cardId: String, override val timestampMs: Long) : Signal()

    /** Maps each signal to its persisted type name + weight per PRD §3/FR-02/06/07/08. */
    val persistedType: SignalType
        get() = when (this) {
            is Tap            -> SignalType.TAP
            is Dwell          -> SignalType.DWELL
            is Like           -> SignalType.THUMB_UP
            is Dislike        -> SignalType.THUMB_DOWN
            is Dismiss        -> SignalType.DISMISS
            is ActionTapped   -> SignalType.ACTION_TAP
            is IntentSwitch   -> SignalType.INTENT_SWITCH
            is RevealAppeared -> SignalType.REVEAL
        }

    val weight: Float
        get() = when (this) {
            is Like         -> +3f
            is ActionTapped -> +2f
            is Dwell        -> +1f
            is Tap          ->  0f
            is RevealAppeared -> 0f
            is IntentSwitch ->  0f
            is Dismiss      -> -2f
            is Dislike      -> -3f
        }
}

enum class SignalType { TAP, DWELL, THUMB_UP, THUMB_DOWN, DISMISS, ACTION_TAP, INTENT_SWITCH, REVEAL }
```

**Decision note (not explicit in PRD/DESIGN):** `Tap`, `IntentSwitch`, and `RevealAppeared` carry weight `0f`. They are persisted but do not modify the profile; they exist for instrumentation (FR-20 FLYWHEEL logcat) and future analytics. Only `Like/Dislike/ActionTapped/Dwell/Dismiss` have profile-modifying weight.

### 5.5 `UserProfile.kt`

Snapshot used by the engine; derived from the DAO list.

```kotlin
@Immutable
data class UserProfile(
    val weights:     Map<String, Float>,   // tag -> running weight
    val totalEvents: Int,
    val updatedMs:   Long
) {
    fun weightOf(tag: String): Float = weights[tag] ?: 0f

    companion object {
        val Empty = UserProfile(weights = emptyMap(), totalEvents = 0, updatedMs = 0L)
    }
}
```

### 5.6 `ConfidenceLevel.kt`

Used by `HomeUiState` / `ConfidenceBar` to convert an engine score into a visual level.

```kotlin
enum class ConfidenceLevel(val fraction: Float) {
    LOW(0.35f), MED(0.65f), HIGH(0.92f);

    companion object {
        fun from(score: Float, maxScore: Float): ConfidenceLevel {
            if (maxScore <= 0f) return LOW
            val f = (score / maxScore).coerceIn(0f, 1f)
            return when {
                f >= 0.75f -> HIGH
                f >= 0.40f -> MED
                else       -> LOW
            }
        }
    }
}
```

### 5.7 `AppInfo.kt`

```kotlin
@Immutable
data class AppInfo(
    val packageName: String,
    val label:       String,
    val iconKey:     String   // stable key; actual Drawable fetched at render via PackageManager
)
```

---

## 6. Repository Interfaces

All repositories live in `data/repo/`. Implementations in `data/repo/impl/` bound via Hilt `@Binds`.

### 6.1 `FeedbackRepository`

```kotlin
interface FeedbackRepository {

    /** Health flag consulted by HomeViewModel (FR-09 / FR-19 Room-unavailable). */
    val isHealthy: StateFlow<Boolean>

    /** Running total count (live). Used by Profile v47 header. */
    fun observeCount(): Flow<Int>

    /** Full event log stream (debug / FR-20 dump). */
    fun observeEvents(): Flow<List<FeedbackEventEntity>>

    /**
     * Persists a Signal atomically with the associated UserProfile deltas.
     * Implements FR-10 formula: for each tag in card.tags, weight += signal.weight / card.tags.size.
     * Wrapped in a single Room transaction.
     *
     * @return the inserted row id, or null if Room is unhealthy (write was skipped).
     */
    suspend fun record(signal: Signal, card: Card): Long?

    /** FR-12 reset: DELETE from feedback_events + user_profile in one txn. */
    suspend fun clearAll()
}
```

### 6.2 `CardRepository`

```kotlin
interface CardRepository {

    /** Seed-installer-gated; first call triggers SeedInstaller.installIfEmpty(). */
    fun observeAll(): Flow<List<Card>>

    fun observeByIntent(intent: Intent): Flow<List<Card>>

    suspend fun byIntent(intent: Intent): List<Card>

    suspend fun all(): List<Card>
}
```

### 6.3 `ProfileRepository`

```kotlin
interface ProfileRepository {

    fun observeProfile(): Flow<UserProfile>

    fun observeTopTags(limit: Int = 10): Flow<List<UserProfileEntry>>

    suspend fun snapshot(): UserProfile
}
```

### 6.4 `PackageAppsRepository`

```kotlin
interface PackageAppsRepository {
    suspend fun launchableApps(): List<AppInfo>
    suspend fun loadIcon(packageName: String): android.graphics.drawable.Drawable?
    suspend fun launchIntentFor(packageName: String): android.content.Intent?
}
```

---

## 7. RecommendationEngine

Pure Kotlin, injected `Clock` + `Random`, deterministic in tests. Implements FR-11 exactly.

### 7.1 Interface

```kotlin
package com.bobpan.ailauncher.domain.recommend

interface RecommendationEngine {

    /**
     * @param intent          active intent
     * @param profile         snapshot (empty on cold start)
     * @param catalog         full seed catalog
     * @param dismissed       in-memory dismiss set for this intent
     * @param count           how many slots to fill (v0.1 always 4)
     * @return ordered list (≤ count), position 0 is Hero source per FR-11.
     */
    fun recommend(
        intent:    Intent,
        profile:   UserProfile,
        catalog:   List<Card>,
        dismissed: Set<String>,
        count:     Int = 4
    ): RecommendationResult

    /**
     * DEBUG-only hook (FR-20). Release builds: no-op or throws IllegalStateException.
     */
    fun setDeterministicMode(seed: Long)
}

@Immutable
data class RecommendationResult(
    val ordered:   List<Card>,
    val scores:    Map<String, Float>,   // card.id -> exploitation score (for ConfidenceBar)
    val maxScore:  Float
)
```

### 7.2 Implementation — `EpsilonGreedyEngine`

```kotlin
class EpsilonGreedyEngine @Inject constructor(
    @Named("recEngineRng") private val rngProvider: () -> Random,
    private val clock: Clock
) : RecommendationEngine {

    @Volatile
    private var overrideRng: Random? = null

    private fun rng(): Random = overrideRng ?: rngProvider()

    override fun recommend(
        intent: Intent,
        profile: UserProfile,
        catalog: List<Card>,
        dismissed: Set<String>,
        count: Int
    ): RecommendationResult {
        val available = catalog
            .asSequence()
            .filter { it.intent == intent && it.id !in dismissed }
            .sortedBy { it.seedOrder }   // stable tiebreak
            .toList()

        if (available.isEmpty()) {
            return RecommendationResult(emptyList(), emptyMap(), 0f)
        }

        // Scoring — FR-11: score(c,P) = Σ P.weight[tag] for tag in c.tags, card-side weight = 1.0.
        val scored: List<Pair<Card, Float>> = available.map { card ->
            val s = card.tags.sumOf { tag -> profile.weightOf(tag).toDouble() }.toFloat()
            card to s
        }

        // Stable sort: by score DESC, then by seedOrder ASC (already in available order).
        val sortedExploit = scored
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Pair<Card, Float>>> { it.value.second }
                    .thenBy { it.index }   // preserve seed order on ties
            )
            .map { it.value }

        // Single-candidate case: no room for exploration.
        if (available.size <= 1) {
            val ordered = sortedExploit.map { it.first }.take(count)
            return RecommendationResult(
                ordered = ordered,
                scores  = ordered.associate { it.id to scored.first { s -> s.first.id == it.id }.second },
                maxScore = sortedExploit.firstOrNull()?.second ?: 0f
            )
        }

        // FR-11 slot allocation: reserve exactly 1 exploration slot, remainder exploitation.
        val exploitCount = available.size - 1
        val exploit      = sortedExploit.take(exploitCount).map { it.first }
        val explorePool  = available - exploit.toSet()
        val exploreChoice = if (explorePool.isNotEmpty()) {
            explorePool[rng().nextInt(explorePool.size)]
        } else null

        // Placement: top exploit at position 0, remaining exploit in score order,
        // exploration inserted at position available.size - 1 (the bottom).
        val ordered = buildList {
            addAll(exploit)
            exploreChoice?.let { add(it) }
        }.take(count)

        val maxScore = sortedExploit.firstOrNull()?.second ?: 0f

        return RecommendationResult(
            ordered  = ordered,
            scores   = scored.associate { it.first.id to it.second },
            maxScore = maxScore
        )
    }

    override fun setDeterministicMode(seed: Long) {
        if (!BuildConfig.DEBUG) {
            // silently ignore in release; FR-20 is debug-only
            return
        }
        overrideRng = Random(seed)
    }
}
```

**Test mode.** `DomainModule` provides `@Named("recEngineRng")` as `Random.Default` in production and `Random(42L)` in test `@TestInstallIn` overrides (see §12). `Clock` is injected to ensure FR-07 dwell logic is deterministic; the engine itself does not consult the clock for FR-11 decisions (pure function of inputs), but `Clock.now()` is wired through so downstream consumers (repo `timestamp_ms`) use the same source.

---

## 8. ViewModel Layer

All view models use `StateFlow<*UiState>` exposed as `val uiState: StateFlow<...>`, with input methods for user actions. **All `UiState` types are sealed interfaces with `@Immutable` data class variants** so Compose skippability is preserved.

### 8.1 `HomeUiState`

```kotlin
// ui/home/HomeUiState.kt
sealed interface HomeUiState {
    data object Loading : HomeUiState

    @Immutable
    data class Content(
        val selectedIntent:    Intent,
        val heroCard:          Card?,
        val discoverCards:     List<Card>,
        val scores:            Map<String, Float>,
        val maxScore:          Float,
        val showDefaultBanner: Boolean,
        val storageHealthy:    Boolean,
        val allDismissed:      Boolean,
        val feedbackCounter:   Int
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}
```

### 8.2 `HomeViewModel`

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cardRepository:      CardRepository,
    private val feedbackRepository:  FeedbackRepository,
    private val profileRepository:   ProfileRepository,
    private val engine:              RecommendationEngine,
    private val clock:               Clock,
    private val dispatchers:         AppDispatchers,
    savedState:                      SavedStateHandle
) : ViewModel() {

    // --- Inputs (explicit state flows combined into uiState) ---
    private val _selectedIntent       = MutableStateFlow(Intent.WORK)                // FR-03 cold-start
    private val _dismissedByIntent    = MutableStateFlow<Map<Intent, Set<String>>>(emptyMap())
    private val _bannerDismissed      = MutableStateFlow(false)
    private val _showDefaultBanner    = MutableStateFlow(false)                      // set by MainActivity
    val profileSheetOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // --- Output ---
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
        // destructure 8 values via index access
        val intent     = values[0] as Intent
        val dismissMap = values[1] as Map<Intent, Set<String>>
        val catalog    = values[2] as List<Card>
        val profile    = values[3] as UserProfile
        val count      = values[4] as Int
        val healthy    = values[5] as Boolean
        val show       = values[6] as Boolean
        val bannerDn   = values[7] as Boolean

        val dismissed = dismissMap[intent].orEmpty()
        val result    = engine.recommend(intent, profile, catalog, dismissed, count = 4)

        HomeUiState.Content(
            selectedIntent    = intent,
            heroCard          = result.ordered.firstOrNull(),
            discoverCards     = result.ordered.drop(1),
            scores            = result.scores,
            maxScore          = result.maxScore,
            showDefaultBanner = show && !bannerDn,
            storageHealthy    = healthy,
            allDismissed      = dismissed.size >= 4 && catalog.any { it.intent == intent },
            feedbackCounter   = count
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState.Loading
    )

    // --- Actions ---
    fun selectIntent(intent: Intent) {
        if (_selectedIntent.value == intent) return
        // FR-06 (b): re-entering intent clears its dismiss list
        _dismissedByIntent.update { it - intent }
        _selectedIntent.value = intent
    }

    fun submitFeedback(card: Card, signal: Signal) {
        viewModelScope.launch(dispatchers.io) {
            feedbackRepository.record(signal, card)
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
        if (durationMs < 2_000L) return                 // FR-07 threshold
        submitFeedback(card, Signal.Dwell(card.id, clock.nowMs(), durationMs))
    }

    fun restoreDismissedForActiveIntent() {
        _dismissedByIntent.update { it - _selectedIntent.value }
    }

    fun setBannerVisibility(show: Boolean) { _showDefaultBanner.value = show }
    fun dismissBanner()                    { _bannerDismissed.value = true }

    fun onHomeButtonPressed() {
        // FR-14 canonical state
        profileSheetOpen.value = false
        // nav-level pop happens in MainActivity; scroll-to-top is owned by HomeScreen state
    }

    fun refresh() {
        // triggered on Profile reset (FR-12) — flows re-emit automatically; method is a hook
        // for UI-level side effects (scroll-to-top, collapse sheet).
        profileSheetOpen.value = false
    }
}
```

### 8.3 `AppDrawerViewModel`

```kotlin
sealed interface DrawerUiState {
    data object Loading : DrawerUiState
    @Immutable data class Content(val apps: List<AppInfo>) : DrawerUiState
    data object Empty : DrawerUiState
    data class Error(val message: String) : DrawerUiState
}

@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    private val packageApps: PackageAppsRepository,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrawerUiState>(DrawerUiState.Loading)
    val uiState: StateFlow<DrawerUiState> = _uiState.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch(dispatchers.io) {
            _uiState.value = DrawerUiState.Loading
            _uiState.value = runCatching { packageApps.launchableApps() }
                .map { apps -> if (apps.isEmpty()) DrawerUiState.Empty else DrawerUiState.Content(apps) }
                .getOrElse { t -> DrawerUiState.Error(t.message ?: "unknown") }
        }
    }

    suspend fun launchIntentFor(pkg: String) = packageApps.launchIntentFor(pkg)
}
```

### 8.4 `ProfileDebugViewModel`

```kotlin
sealed interface ProfileSheetState {
    data object Loading : ProfileSheetState
    @Immutable data class Loaded(
        val topTags: List<UserProfileEntry>,
        val counter: Int
    ) : ProfileSheetState
    data object Empty : ProfileSheetState
}

@HiltViewModel
class ProfileDebugViewModel @Inject constructor(
    private val profileRepository:  ProfileRepository,
    private val feedbackRepository: FeedbackRepository,
    private val dispatchers:        AppDispatchers
) : ViewModel() {

    val uiState: StateFlow<ProfileSheetState> =
        combine(
            profileRepository.observeTopTags(limit = 10),
            feedbackRepository.observeCount()
        ) { tags, count ->
            if (count == 0 && tags.isEmpty()) ProfileSheetState.Empty
            else ProfileSheetState.Loaded(tags, count)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileSheetState.Loading
        )

    fun resetProfile(onDone: () -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            feedbackRepository.clearAll()
            withContext(dispatchers.main) { onDone() }
        }
    }
}
```

---

## 9. Hilt Dependency Injection

### 9.1 `AppModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideClock(): Clock = Clock.System

    @Provides @Singleton
    fun provideDispatchers(): AppDispatchers = AppDispatchers.Default

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults    = true
    }
}

// util/Dispatchers.kt
data class AppDispatchers(
    val main:    CoroutineDispatcher,
    val io:      CoroutineDispatcher,
    val default: CoroutineDispatcher
) {
    companion object {
        val Default = AppDispatchers(
            main    = Dispatchers.Main.immediate,
            io      = Dispatchers.IO,
            default = Dispatchers.Default
        )
    }
}

// util/Clock.kt
interface Clock {
    fun nowMs(): Long
    object System : Clock { override fun nowMs() = java.lang.System.currentTimeMillis() }
}
```

### 9.2 `DatabaseModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
        json: Json
    ): LauncherDatabase =
        Room.databaseBuilder(ctx, LauncherDatabase::class.java, LauncherDatabase.NAME)
            .addTypeConverter(Converters(json))
            .fallbackToDestructiveMigration()              // FR-09
            .build()

    @Provides fun provideFeedbackDao(db: LauncherDatabase): FeedbackDao    = db.feedbackDao()
    @Provides fun provideProfileDao (db: LauncherDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideCardDao    (db: LauncherDatabase): CardDao        = db.cardDao()
}
```

### 9.3 `RepositoryModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindFeedback(impl: FeedbackRepositoryImpl): FeedbackRepository
    @Binds @Singleton abstract fun bindCards   (impl: CardRepositoryImpl):     CardRepository
    @Binds @Singleton abstract fun bindProfile (impl: ProfileRepositoryImpl):  ProfileRepository
    @Binds @Singleton abstract fun bindApps    (impl: PackageAppsRepositoryImpl): PackageAppsRepository
}
```

### 9.4 `DomainModule`

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class RecEngineRng

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    /**
     * Provider-qualified RNG factory. Production: Random.Default.
     * Tests override via @TestInstallIn with Random(42L).
     * Declared as () -> Random so EpsilonGreedyEngine can re-read the provider
     * after setDeterministicMode() fires (FR-20 demo hook).
     */
    @Provides @Singleton @Named("recEngineRng")
    fun provideRecEngineRng(): () -> Random = { Random.Default }

    @Provides @Singleton
    fun provideRecommendationEngine(
        @Named("recEngineRng") rng: () -> Random,
        clock: Clock
    ): RecommendationEngine = EpsilonGreedyEngine(rng, clock)
}
```

> `@Named("recEngineRng")` is used per PRD FR-11 verbatim ("provided by Hilt as `@Singleton @Named("recEngineRng") Random`"). If the dev agent prefers a typed qualifier, `@RecEngineRng` is declared above and either may be used — pick one and stay consistent.

### 9.5 Application class

```kotlin
@HiltAndroidApp
class LauncherApp : Application() {
    @Inject lateinit var seedInstaller: SeedInstaller

    override fun onCreate() {
        super.onCreate()
        // Fire-and-forget seed install on a background coroutine.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            seedInstaller.installIfEmpty()
        }
    }
}
```

---

## 10. Navigation

Single `NavHost` with two destinations. `profile_debug` is **not** a route — it is an in-composition `ModalBottomSheet` controlled by `HomeViewModel.profileSheetOpen`.

```kotlin
// ui/nav/LauncherNavHost.kt
object Routes {
    const val HOME   = "home"
    const val DRAWER = "drawer"
    // "profile_debug" — not a nav route, documented here for clarity:
    const val PROFILE_DEBUG_ANCHOR = "profile_debug"   // used only as analytics key
}

@Composable
fun LauncherNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = Routes.HOME,
        modifier         = modifier
    ) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel        = vm,
                onOpenDrawer     = { navController.navigate(Routes.DRAWER) }
            )
        }
        composable(Routes.DRAWER) {
            val vm: AppDrawerViewModel = hiltViewModel()
            AppDrawerScreen(
                viewModel = vm,
                onBack    = { navController.popBackStack(Routes.HOME, inclusive = false) }
            )
        }
    }
}
```

`MainActivity.onNewIntent` (for Home-button press while foreground, FR-14) calls:

```kotlin
override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    // FR-14 canonical state (see HomeViewModel.onHomeButtonPressed())
    navController.popBackStack(Routes.HOME, inclusive = false)
    homeViewModel.onHomeButtonPressed()
    // Scroll-to-top is handled in HomeScreen via a LaunchedEffect keyed off a trip-counter StateFlow.
}
```

---

## 11. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">

    <!-- NFR-06 / NFR-04: NO network permissions declared.
         Do NOT add <uses-permission android:name="android.permission.INTERNET" />. -->

    <!-- Android 11+ (API 30) PackageManager visibility for the app drawer. FR-13 / SM-08. -->
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>

    <application
        android:name=".LauncherApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/backup_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AILauncher"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true"
            android:excludeFromRecents="false"
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.AILauncher.Splash">

            <!-- Standard launcher discovery -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- FR-14: launcher / HOME eligibility -->
            <intent-filter android:priority="1">
                <action   android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Themes (`res/values/themes.xml`).** Single dark Material 3 theme with `Theme.Material3.DayNight.NoActionBar` as parent (night-only enforced via `uiMode` in code) and a `windowBackground = @color/bg_deep` so pre-Compose paint matches the gradient start color (prevents flash). `Theme.AILauncher.Splash` extends it with `android:windowSplashScreenBackground = @color/bg_deep` for API 31+ splash.

**Status / nav bars.** Set `window.statusBarColor = Color.TRANSPARENT`, `window.navigationBarColor = Color.TRANSPARENT`, and `WindowCompat.setDecorFitsSystemWindows(window, false)` in `MainActivity.onCreate` before `setContent { ... }`. Light icons via `WindowInsetsControllerCompat.isAppearanceLightStatusBars = false`. (NFR-11.)

---

## 12. Testing Architecture

### 12.1 Unit tests (JVM)

Location: `app/src/test/java/com/bobpan/ailauncher/…`

**Strategy.**
- ViewModels tested with **Fake repositories** (hand-rolled, exposing `MutableSharedFlow` / `MutableStateFlow`) and **Turbine** for `StateFlow` assertions.
- `RecommendationEngine` tested with **seeded `Random(42L)`** and synthetic `UserProfile`/catalog fixtures.
- Coroutines tested via `kotlinx-coroutines-test`: `runTest { ... }` + `StandardTestDispatcher`.
- `Clock` replaced with a `FakeClock(var now: Long)` in engine/VM tests so dwell timing is deterministic (FR-07 test hook).

**Required test classes (minimum suite for v0.1):**

| Class under test | Test class | Scenarios |
|---|---|---|
| `EpsilonGreedyEngine` | `EpsilonGreedyEngineTest` | cold-start order (empty profile → seed order); single-candidate no-explore; ≥2 candidates reserve 1 explore slot; ties break by seedOrder; biased profile at coffee +10 → SM-07 1000-iter test asserting position 0 stability and position 3 variability ≥50%; `setDeterministicMode(seed)` reproducibility |
| `HomeViewModel` | `HomeViewModelTest` | cold-start default intent = WORK; `selectIntent` clears dismiss list for target intent; `submitFeedback` writes to fake repo; `dismissCard` adds to in-memory set AND records Signal.Dismiss; `trackDwell` < 2000ms short-circuits; FR-14 `onHomeButtonPressed` closes sheet; `restoreDismissedForActiveIntent` clears only active intent |
| `FeedbackRepositoryImpl` | `FeedbackRepositoryImplTest` | FR-10 formula: 👍 +3 on 4-tag card → each tag +0.75; txn atomicity (both inserts or neither); `clearAll` clears both tables; `isHealthy` flips false on injected exception |
| `ProfileDebugViewModel` | `ProfileDebugViewModelTest` | `Empty` emitted when count==0 && tags empty; `Loaded` emitted otherwise; `resetProfile` invokes repo.clearAll and `onDone` |
| `AppDrawerViewModel` | `AppDrawerViewModelTest` | `Loading` → `Content` happy path; `Empty` when repo returns emptyList; `Error` on thrown |

**Fake repository skeleton:**

```kotlin
class FakeFeedbackRepository : FeedbackRepository {
    override val isHealthy = MutableStateFlow(true)
    private val events = MutableStateFlow<List<FeedbackEventEntity>>(emptyList())
    override fun observeCount()  = events.map { it.size }
    override fun observeEvents() = events.asStateFlow()
    val records = mutableListOf<Pair<Signal, Card>>()
    override suspend fun record(signal: Signal, card: Card): Long? {
        records += signal to card; events.update { it + stubEntity(signal, card) }; return 1L
    }
    override suspend fun clearAll() { events.value = emptyList(); records.clear() }
}
```

### 12.2 Instrumented tests (minimized)

Location: `app/src/androidTest/java/com/bobpan/ailauncher/…`

v0.1 ships **only a Room DAO smoke test** to prove the schema compiles and the transactional write works end-to-end. Everything else (SM-10 dwell, SM-11 dismiss, SM-12 cold-start) is executed as part of the **manual golden-path QA script** (PRD §6).

Required classes:

| Test | Contract |
|---|---|
| `LauncherDatabaseSmokeTest` | Uses `Room.inMemoryDatabaseBuilder`. Inserts one `FeedbackEventEntity`, one `UserProfileEntry` upsert, one `CachedCardEntity`. Asserts `FeedbackDao.count() == 1`, `UserProfileDao.snapshot().single().weight > 0`, `CardDao.all().size == 1`. Verifies `Converters` round-trips a `List<String>`. |

No Compose UI tests in v0.1 (keeps CI fast; UI tested manually against DESIGN-v1).

### 12.3 `Clock` injection (FR-07 compliance)

- Production: `AppModule` binds `Clock.System`.
- Tests: construct ViewModels/engine with `FakeClock` directly (they take `Clock` in the constructor). No Hilt test overrides needed for unit tests.
- Instrumented: not used — the DAO smoke test does not exercise dwell.

---

## 13. Performance Budgets (hitting PRD NFRs)

| PRD target | Architectural mitigation |
|---|---|
| **NFR-01 · Cold start to Hero ≤500ms** | `LauncherApp.onCreate` does not touch Room on the main thread; `SeedInstaller.installIfEmpty()` runs on IO and races with first frame. `HomeViewModel.uiState.stateIn(WhileSubscribed(5_000), initialValue = Loading)` emits instantly. Themed splash (`Theme.AILauncher.Splash`) uses `@color/bg_deep` so the pre-compose paint is the gradient start color. Compose BOM 2024.12 + Material 3 baseline profile shipped by Google reduces first-frame cost. |
| **NFR-02 · Room queries ≤50ms p95** | Hot columns indexed: `feedback_events(card_id, intent, timestamp_ms)`, `user_profile(weight)`, `cached_cards(intent)`. `top10Tags` query uses `ORDER BY ABS(weight) DESC LIMIT 10` which is index-assisted (weight index). The entire 16-row `cached_cards` fits in a single page — no N+1 risk. |
| **NFR-03 · Intent switch ≤200ms** | `HomeUiState.Content` re-derivation is a pure `combine` operator; no DB round-trip on switch (catalog + profile already cached in flows). Engine is O(n·m) where n=16 cards, m≤8 tags ≈ 128 ops — sub-millisecond. |
| **Compose skippability** | Every `UiState` + `Card` + `AppInfo` annotated `@Immutable`; collections are `List<*>` (kotlin's read-only interface, treated as immutable by Compose if the class is `@Immutable`). No `mutableStateListOf` passed through composables. |
| **LazyColumn perf** | `items(state.feed, key = { it.id })` — stable keys guarantee `animateItemPlacement` correctness and recomposition skipping. `contentType = { it.type.name }` added for heterogeneous skip-lists when mixing Hero/Discover later (v0.2). |
| **NFR-05 · No background work** | No `WorkManager` / `JobScheduler` / services in Manifest. DataStore is used only if needed (v0.1 uses nothing). All work in `viewModelScope`. |
| **NFR-10 · APK ≤15MB** | No font asset; emoji-only icons. No bitmap resources in v0.1 beyond launcher icon. ProGuard can be enabled in v0.2; v0.1 ships `isMinifyEnabled = false` to keep dev-loop tight. |

---

## 14. Security & Privacy

- **No `<uses-permission android:name="android.permission.INTERNET" />`** in the Manifest. Lint rule `MissingPermission` will flag any network call at build time, protecting NFR-06.
- **Zero analytics / telemetry SDKs.** No Firebase, Crashlytics, Sentry, OkHttp, Retrofit, Ktor, Volley. The dependency graph contains nothing capable of opening a socket (verify via `./gradlew :app:dependencies | grep -i 'okhttp\|retrofit\|ktor\|firebase' → must be empty`).
- **All data local** — Room DB at `/data/data/com.bobpan.ailauncher/databases/launcher.db`. No content-provider export.
- **No dynamic code loading.** No `DexClassLoader`, no `Reflection.*`, no feature modules.
- **Backup opt-out.** `android:allowBackup="false"` + `android:fullBackupContent="false"` + `android:dataExtractionRules="@xml/backup_rules"` with an empty rules file prevents the DB from being swept into cloud backup — even local-only data stays local-only.
- **Logcat only.** FR-20 FLYWHEEL tag logs to logcat only; no file I/O, no network egress. Release build strips debug logs via R8 in v0.2; for v0.1 the dev panel is gated on `BuildConfig.DEBUG`.
- **PackageManager queries** are read-only (`queryIntentActivities`, `getLaunchIntentForPackage`, `resolveActivity`) and require no permission on targetSdk 34 when paired with the `<queries>` block above.

---

## 15. Build / CI

### 15.1 Existing workflows (verified compatible)

`.github/workflows/build-apk.yml` (present) runs:
1. JDK 17 setup (matches §3 `compileOptions`).
2. Android SDK setup.
3. `chmod +x ./gradlew || true` — tolerates missing wrapper.
4. `./gradlew :app:assembleDebug --stacktrace --no-daemon`.
5. Uploads APK + Telegram notify.

`.github/workflows/test.yml` runs `:app:testDebugUnitTest`.

**Both workflows will pass as-is once the dev agent generates the Gradle wrapper.** Nothing in this architecture breaks them.

### 15.2 First-build bootstrap (REQUIRED before CI succeeds)

The repo ships without `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, or `gradle-wrapper.properties`. The dev agent MUST run, on their local machine or inside the CI checkout step **before** the first `./gradlew` invocation:

```bash
# Requires a pre-existing `gradle` on PATH (any version ≥7).
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
chmod +x gradlew
git add gradlew gradlew.bat gradle/wrapper/
git commit -m "build: add gradle wrapper 8.11.1"
```

The generated `gradle/wrapper/gradle-wrapper.properties` must pin:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
```

Once committed, both GitHub Actions workflows will succeed without modification (they already `chmod +x ./gradlew` defensively).

### 15.3 Local developer quickstart

```bash
# One-time
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
./gradlew :app:assembleDebug

# Iterative
./gradlew :app:testDebugUnitTest          # fast, JVM
./gradlew :app:installDebug               # installs on attached device
./gradlew :app:connectedDebugAndroidTest  # Room DAO smoke test
```

### 15.4 Schema export

With `ksp.arg("room.schemaLocation", "$projectDir/schemas")`, Room will write versioned schemas to `app/schemas/com.bobpan.ailauncher.data.db.LauncherDatabase/1.json`. Commit this file alongside the DB — it is required for any future migration testing and for catching accidental schema drift.

---

## Appendix A · Decisions not explicit in PRD-v2 / DESIGN-v1

The dev agent should treat these as **architectural decisions made here** (not bugs in upstream docs):

1. **Signal subclass set.** PRD-v2 §3 enumerates signal types but does not spell out a Kotlin sealed hierarchy. This doc defines 8 subclasses (`Tap / Dwell / Like / Dislike / Dismiss / ActionTapped / IntentSwitch / RevealAppeared`) with `cardId + timestampMs` on each. `Tap`, `IntentSwitch`, and `RevealAppeared` persist with `weight = 0f` for instrumentation; they do not alter profile weights. This is consistent with PRD-v2 §3 item 2's explicit weight table (which enumerates only the 5 profile-modifying signals).
2. **Room conflict strategy.** FR-09 does not specify. This doc uses `OnConflictStrategy.ABORT` on `feedback_events.insert` (auto-generated PK makes conflicts impossible) and `OnConflictStrategy.REPLACE` on `cached_cards` seed insert (idempotent re-seed if count was nonzero but mismatched).
3. **Profile top-tag sort order.** FR-12 says "top 10 tags by weight, sorted descending by absolute weight so strongly-negative tags are visible". This doc implements that via `UserProfileDao.observeTopByAbs(limit)` using `ORDER BY ABS(weight) DESC`.
4. **`@Named("recEngineRng")` vs typed qualifier.** FR-11 literally says `@Named("recEngineRng") Random`. This doc honors that but also declares a typed `@RecEngineRng` qualifier for the dev agent's optional use.
5. **`CardType.HERO` enum value.** DESIGN-v1 uses "Hero" as a slot concept; this doc adds `CardType.HERO` to the enum for completeness, but **no seed card uses it** — the Hero slot is derived at render time from `recommend()` position 0.
6. **`Clock` is an internal interface, not `kotlinx-datetime`.** To keep APK size down (NFR-10) and avoid a new dep, `Clock` is a 1-method internal interface with a `System` singleton. Upgrade to `kotlinx-datetime` deferred to v0.2.
7. **Seed install lives in `LauncherApp.onCreate`** (fire-and-forget IO coroutine). DESIGN-v1 §2.1 shows it inside the cold-boot flow but does not pick a trigger point; putting it in `Application.onCreate` ensures it races with the first frame rather than blocking the first `CardRepository.observeAll()` subscription.
8. **Dwell timer lives in a `util/Dwell.kt` helper owned by `HomeScreen`**, not in `HomeViewModel` — the VM receives `trackDwell(card, durationMs)` when the helper decides an Appearance has crossed 2000ms, keeping the VM framework-free (no `onGloballyPositioned` leakage). This matches DESIGN-v1 §5.3 "The card itself holds no dwell state".
9. **ProGuard/R8** disabled in v0.1 for both build types (consistent with AGENTS.md dev-loop priority). Release APK is debug-signed by CI — `isMinifyEnabled = false` on release is intentional v0.1 debt.
10. **No `androidx.compose.foundation` pinned separately** — it is transitively delivered by the Compose BOM 2024.12.01 via `material3`. Adding an explicit dep would risk version skew.

---

*End of ARCHITECTURE v1.*
