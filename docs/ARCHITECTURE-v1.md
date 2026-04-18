# ARCHITECTURE v1 — AI Launcher MVP (v0.1)

**Status:** Approved for implementation · **Owner:** Tech Architect · **Last updated:** 2026-04-18
**Scope:** Complete technical architecture covering every FR/NFR in `docs/PRD-v2.md`.
**Audience:** Phase 3 dev agent. This doc + `docs/PRD-v2.md` + `docs/DESIGN-v1.md` + `AGENTS.md` = complete build spec. No class names, method signatures, or DI wirings are left to inference.

**Tech stack (LOCKED from AGENTS.md):** Kotlin 2.0.21 · Compose BOM 2024.12.01 · Material 3 · Hilt 2.52 · Room 2.6.1 · AGP 8.7.3 · Gradle 8.11.1 · JDK 17 · minSdk 26 / targetSdk 34 · Package `com.bobpan.ailauncher`.

---

## 1. System Architecture Overview

Layered MVVM with unidirectional data flow. UI observes `StateFlow<UiState>` from ViewModels; ViewModels call Repository interfaces; Repositories own Room DAOs and in-memory seed; the `RecommendationEngine` is a pure function (no state) invoked by `HomeViewModel` with a `UserProfile` snapshot + dismiss list + seeded `Random`.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                            PRESENTATION (ui/)                            │
│  ┌──────────────┐  ┌───────────────┐  ┌────────────────────────────────┐ │
│  │ HomeScreen   │  │ AppDrawerScrn │  │ ProfileDebugSheet (modal)      │ │
│  │ + components │  │               │  │                                │ │
│  │ (Hero/Chip/  │  │               │  │                                │ │
│  │  Discover/   │  │               │  │                                │ │
│  │  AiDock/     │  │               │  │                                │ │
│  │  Banner)     │  │               │  │                                │ │
│  └──────┬───────┘  └───────┬───────┘  └─────────────┬──────────────────┘ │
│         │ StateFlow<HomeUiState>   │ StateFlow<...>  │ StateFlow<...>    │
│  ┌──────▼──────────────────────────▼─────────────────▼────────────────┐  │
│  │                     VIEWMODEL LAYER (ui/*/)                        │  │
│  │  HomeViewModel · AppDrawerViewModel · ProfileDebugViewModel        │  │
│  └──────┬─────────────────────────────────────────────────────────────┘  │
└─────────┼────────────────────────────────────────────────────────────────┘
          │ suspend fns / Flow
┌─────────▼────────────────────────────────────────────────────────────────┐
│                          DOMAIN (domain/recommend/)                      │
│  RecommendationEngine (ε-greedy, pure, injected Random + Clock)          │
└─────────┬────────────────────────────────────────────────────────────────┘
          │ reads UserProfile snapshot
┌─────────▼────────────────────────────────────────────────────────────────┐
│                              DATA (data/)                                │
│  ┌────────────────────┐  ┌────────────────────┐  ┌───────────────────┐   │
│  │ FeedbackRepository │  │ CardRepository     │  │ ProfileRepository │   │
│  │ (Room + Clock)     │  │ (seed + Room cache)│  │ (Room)            │   │
│  └─────────┬──────────┘  └─────────┬──────────┘  └─────────┬─────────┘   │
│            │ DAOs                  │ seed +DAO             │ DAO         │
│  ┌─────────▼──────────────────────────────────────────────▼─────────┐   │
│  │  Room: AppDatabase v1                                            │   │
│  │    feedback_events · user_profile · cached_cards                 │   │
│  │    fallbackToDestructiveMigration (FR-09)                        │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  In-memory: SeedCards (val SEED_CARDS)                           │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘

                   ▲            DI (Hilt)              ▲
                   │  DatabaseModule · RepositoryModule │
                   │  DomainModule     · AppModule      │
                   └────────────────────────────────────┘
```

**Flow of a 👍 tap (worked example):**
`HeroCard.onLike` → `HomeViewModel.onSignal(Signal.Like(cardId))` → `FeedbackRepository.record(...)` writes `FeedbackEvent` + updates `UserProfile` in a single Room transaction (FR-10) → VM re-fetches `UserProfile` + calls `RecommendationEngine.recommend(intent, 4, profile, dismissSet, rng)` → new `HomeUiState` emitted → `HomeScreen` recomposes → Hero crossfades.

---

## 2. Module / Package Structure

All production sources live under `app/src/main/java/com/bobpan/ailauncher/`. Every file listed is a required artifact for v0.1.

```
app/src/main/java/com/bobpan/ailauncher/
├── AiLauncherApp.kt                       # @HiltAndroidApp Application
├── MainActivity.kt                        # @AndroidEntryPoint, Compose root, singleTask
├── di/
│   ├── DatabaseModule.kt                  # @Module @InstallIn(SingletonComponent)
│   ├── RepositoryModule.kt                # @Module @InstallIn(SingletonComponent)
│   ├── DomainModule.kt                    # @Module @InstallIn(SingletonComponent)
│   └── AppModule.kt                       # Clock, Random, CoroutineDispatchers
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt                 # @Database v1, entities[3]
│   │   ├── entity/
│   │   │   ├── FeedbackEventEntity.kt
│   │   │   ├── UserProfileEntry.kt
│   │   │   └── CachedCardEntity.kt
│   │   ├── dao/
│   │   │   ├── FeedbackDao.kt
│   │   │   ├── UserProfileDao.kt
│   │   │   └── CachedCardDao.kt
│   │   └── Converters.kt                  # List<String> <-> JSON for tags
│   ├── model/
│   │   ├── Intent.kt                      # enum COMMUTE/WORK/LUNCH/REST
│   │   ├── CardType.kt                    # enum CONTINUE/DISCOVER/NEW/HERO
│   │   ├── Card.kt                        # data class (domain)
│   │   ├── SeedCard.kt                    # data class matching PRD §9 schema
│   │   ├── Signal.kt                      # sealed class (Tap/Dwell/Like/...)
│   │   ├── UserProfile.kt                 # data class: tagWeights + meta
│   │   ├── ConfidenceLevel.kt             # enum LOW/MEDIUM/HIGH
│   │   └── FeedbackEvent.kt               # domain-layer mirror of entity
│   ├── seed/
│   │   ├── SeedCards.kt                   # val SEED_CARDS: List<SeedCard>
│   │   └── SeedLoader.kt                  # populates cached_cards on first launch
│   └── repo/
│       ├── FeedbackRepository.kt          # interface
│       ├── FeedbackRepositoryImpl.kt
│       ├── CardRepository.kt              # interface
│       ├── CardRepositoryImpl.kt
│       ├── ProfileRepository.kt           # interface
│       └── ProfileRepositoryImpl.kt
├── domain/
│   └── recommend/
│       ├── RecommendationEngine.kt        # interface
│       └── EpsilonGreedyEngine.kt         # impl (FR-11)
├── ui/
│   ├── theme/
│   │   ├── Color.kt                       # palette from AGENTS.md
│   │   ├── Type.kt
│   │   └── Theme.kt                       # AiLauncherTheme { ... }
│   ├── nav/
│   │   ├── NavGraph.kt                    # NavHost + routes
│   │   └── Routes.kt                      # object Routes { HOME, DRAWER }
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   ├── HomeViewModel.kt
│   │   └── HomeUiState.kt                 # sealed class
│   ├── components/
│   │   ├── HeroCard.kt
│   │   ├── IntentChipStrip.kt
│   │   ├── DiscoverCard.kt
│   │   ├── DiscoverFeed.kt
│   │   ├── AiDock.kt
│   │   ├── Header.kt
│   │   ├── DefaultLauncherBanner.kt       # FR-17
│   │   ├── EmptyStateCard.kt              # FR-19
│   │   └── DwellTracker.kt                # onGloballyPositioned logic (FR-07)
│   ├── profile/
│   │   ├── ProfileDebugSheet.kt
│   │   ├── ProfileDebugViewModel.kt
│   │   └── ProfileDebugUiState.kt         # sealed class
│   └── drawer/
│       ├── AppDrawerScreen.kt
│       ├── AppDrawerViewModel.kt
│       └── AppDrawerUiState.kt            # sealed class
└── util/
    ├── Clock.kt                           # interface + SystemClock impl
    ├── Logging.kt                         # FLYWHEEL logcat tag helpers (FR-20)
    ├── PackageManagerExt.kt               # installed-apps query
    └── ComposeExt.kt                      # minimumInteractiveComponentSize helpers
```

**Test sources:**

```
app/src/test/java/com/bobpan/ailauncher/        # JVM unit tests
├── domain/recommend/EpsilonGreedyEngineTest.kt
├── ui/home/HomeViewModelTest.kt
├── ui/drawer/AppDrawerViewModelTest.kt
├── ui/profile/ProfileDebugViewModelTest.kt
├── fakes/
│   ├── FakeFeedbackRepository.kt
│   ├── FakeCardRepository.kt
│   ├── FakeProfileRepository.kt
│   └── TestClock.kt
└── util/MainDispatcherRule.kt

app/src/androidTest/java/com/bobpan/ailauncher/  # Instrumented
├── data/db/FeedbackDaoTest.kt
├── data/db/UserProfileDaoTest.kt
└── data/db/CachedCardDaoTest.kt
```

---

## 3. Gradle Configuration

### 3.1 `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.13.1"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
lifecycle = "2.8.7"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
room = "2.6.1"
datastore = "1.1.1"
navigationCompose = "2.8.4"
kotlinxSerialization = "1.7.3"
kotlinxCoroutines = "1.9.0"
junit = "4.13.2"
androidxTestJunit = "1.2.1"
androidxTestRunner = "1.6.2"
androidxTestCore = "1.6.1"
mockk = "1.13.13"
espresso = "3.6.1"

[libraries]
androidx-core-ktx               = { module = "androidx.core:core-ktx",                             version.ref = "coreKtx" }
androidx-activity-compose       = { module = "androidx.activity:activity-compose",                 version.ref = "activityCompose" }
androidx-compose-bom            = { module = "androidx.compose:compose-bom",                       version.ref = "composeBom" }
androidx-compose-ui             = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics    = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-tooling     = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-material3      = { module = "androidx.compose.material3:material3" }
androidx-compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx  = { module = "androidx.lifecycle:lifecycle-runtime-ktx",           version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose",    version.ref = "lifecycle" }
androidx-navigation-compose     = { module = "androidx.navigation:navigation-compose",             version.ref = "navigationCompose" }
hilt-android                    = { module = "com.google.dagger:hilt-android",                     version.ref = "hilt" }
hilt-compiler                   = { module = "com.google.dagger:hilt-android-compiler",            version.ref = "hilt" }
hilt-navigation-compose         = { module = "androidx.hilt:hilt-navigation-compose",              version.ref = "hiltNavigationCompose" }
room-runtime                    = { module = "androidx.room:room-runtime",                         version.ref = "room" }
room-compiler                   = { module = "androidx.room:room-compiler",                        version.ref = "room" }
room-ktx                        = { module = "androidx.room:room-ktx",                             version.ref = "room" }
room-testing                    = { module = "androidx.room:room-testing",                         version.ref = "room" }
datastore-preferences           = { module = "androidx.datastore:datastore-preferences",           version.ref = "datastore" }
kotlinx-serialization-json      = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json",   version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android      = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android",   version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test         = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test",      version.ref = "kotlinxCoroutines" }
junit                           = { module = "junit:junit",                                        version.ref = "junit" }
androidx-test-junit             = { module = "androidx.test.ext:junit",                            version.ref = "androidxTestJunit" }
androidx-test-core              = { module = "androidx.test:core",                                 version.ref = "androidxTestCore" }
androidx-test-runner            = { module = "androidx.test:runner",                               version.ref = "androidxTestRunner" }
androidx-test-espresso-core     = { module = "androidx.test.espresso:espresso-core",               version.ref = "espresso" }
mockk                           = { module = "io.mockk:mockk",                                     version.ref = "mockk" }
mockk-android                   = { module = "io.mockk:mockk-android",                             version.ref = "mockk" }

[plugins]
android-application    = { id = "com.android.application",             version.ref = "agp" }
kotlin-android         = { id = "org.jetbrains.kotlin.android",        version.ref = "kotlin" }
kotlin-compose         = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization   = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp                    = { id = "com.google.devtools.ksp",             version.ref = "ksp" }
hilt                   = { id = "com.google.dagger.hilt.android",      version.ref = "hilt" }
```

### 3.2 Project-level `build.gradle.kts`

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
        google()
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
rootProject.name = "ai-launcher-mvp"
include(":app")
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
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Misc
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
```

**gradle.properties additions (required):**

```properties
android.useAndroidX=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.nonTransitiveRClass=true
ksp.incremental=true
```

---

## 4. Data Layer (Room)

### 4.1 Entity: `FeedbackEventEntity`

File: `data/db/entity/FeedbackEventEntity.kt`

| Column        | Kotlin type | SQLite type | Notes |
|---------------|-------------|-------------|-------|
| `id`          | `Long`      | INTEGER PK autogen |   |
| `card_id`     | `String`    | TEXT NOT NULL | indexed |
| `intent`      | `String`    | TEXT NOT NULL | `Intent.name` |
| `signal`      | `String`    | TEXT NOT NULL | `SignalType.name` (flat enum, see §5) |
| `weight`      | `Float`     | REAL NOT NULL |   |
| `timestamp_ms`| `Long`      | INTEGER NOT NULL | from injected `Clock.nowMillis()` |

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
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "card_id")      val cardId: String,
    @ColumnInfo(name = "intent")       val intent: String,
    @ColumnInfo(name = "signal")       val signal: String,
    @ColumnInfo(name = "weight")       val weight: Float,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long
)
```

### 4.2 Entity: `UserProfileEntry`

File: `data/db/entity/UserProfileEntry.kt`

| Column       | Kotlin type | SQLite type | Notes |
|--------------|-------------|-------------|-------|
| `tag`        | `String`    | TEXT PK NOT NULL |   |
| `weight`     | `Float`     | REAL NOT NULL |   |
| `updated_ms` | `Long`      | INTEGER NOT NULL |   |

```kotlin
@Entity(
    tableName = "user_profile",
    indices = [Index(value = ["weight"])]   // supports ORDER BY weight DESC LIMIT 10
)
data class UserProfileEntry(
    @PrimaryKey                        val tag: String,
    @ColumnInfo(name = "weight")       val weight: Float,
    @ColumnInfo(name = "updated_ms")   val updatedMs: Long
)
```

### 4.3 Entity: `CachedCardEntity`

File: `data/db/entity/CachedCardEntity.kt`

Cards are primarily served from `SEED_CARDS` in-memory (FR-16). `cached_cards` exists for the "Room cache" role named in AGENTS.md and allows future dynamic catalogs without a schema bump. The seed loader writes all 16 cards to this table on first launch; the repo returns the in-memory list in v0.1.

| Column           | Kotlin type    | SQLite type | Notes |
|------------------|----------------|-------------|-------|
| `id`             | `String`       | TEXT PK NOT NULL | stable seed id |
| `title`          | `String`       | TEXT NOT NULL |   |
| `description`    | `String`       | TEXT NOT NULL |   |
| `intent`         | `String`       | TEXT NOT NULL | indexed |
| `type`           | `String`       | TEXT NOT NULL | `CardType.name` |
| `icon`           | `String`       | TEXT NOT NULL | emoji |
| `action_label`   | `String`       | TEXT NOT NULL |   |
| `why_label`      | `String`       | TEXT NOT NULL |   |
| `tags_json`      | `String`       | TEXT NOT NULL | JSON-encoded `List<String>` via `Converters` |
| `seed_order`     | `Int`          | INTEGER NOT NULL | 0-based declaration order, for tiebreaker |

```kotlin
@Entity(
    tableName = "cached_cards",
    indices = [Index(value = ["intent"])]
)
data class CachedCardEntity(
    @PrimaryKey                       val id: String,
    val title: String,
    val description: String,
    val intent: String,
    val type: String,
    val icon: String,
    @ColumnInfo(name = "action_label") val actionLabel: String,
    @ColumnInfo(name = "why_label")    val whyLabel: String,
    @ColumnInfo(name = "tags_json")    val tagsJson: String,
    @ColumnInfo(name = "seed_order")   val seedOrder: Int
)
```

### 4.4 DAOs

**`FeedbackDao`** — `data/db/dao/FeedbackDao.kt`

```kotlin
@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: FeedbackEventEntity): Long

    @Query("SELECT * FROM feedback_events ORDER BY timestamp_ms DESC")
    suspend fun getAll(): List<FeedbackEventEntity>

    @Query("SELECT COUNT(*) FROM feedback_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM feedback_events")
    fun countFlow(): Flow<Int>

    @Query("""
        SELECT * FROM feedback_events
        WHERE card_id = :cardId AND signal = :signalName
          AND timestamp_ms >= :sinceMs
        ORDER BY timestamp_ms DESC LIMIT 1
    """)
    suspend fun latestForDebounce(cardId: String, signalName: String, sinceMs: Long): FeedbackEventEntity?

    @Query("DELETE FROM feedback_events")
    suspend fun deleteAll()
}
```

**`UserProfileDao`** — `data/db/dao/UserProfileDao.kt`

```kotlin
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE tag = :tag LIMIT 1")
    suspend fun getByTag(tag: String): UserProfileEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UserProfileEntry)

    @Query("SELECT * FROM user_profile")
    suspend fun getAll(): List<UserProfileEntry>

    @Query("SELECT * FROM user_profile")
    fun observeAll(): Flow<List<UserProfileEntry>>

    @Query("SELECT * FROM user_profile ORDER BY ABS(weight) DESC LIMIT :n")
    suspend fun topByAbsWeight(n: Int): List<UserProfileEntry>

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}
```

**`CachedCardDao`** — `data/db/dao/CachedCardDao.kt`

```kotlin
@Dao
interface CachedCardDao {
    @Query("SELECT COUNT(*) FROM cached_cards")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CachedCardEntity>)

    @Query("SELECT * FROM cached_cards ORDER BY seed_order ASC")
    suspend fun getAll(): List<CachedCardEntity>

    @Query("SELECT * FROM cached_cards WHERE intent = :intent ORDER BY seed_order ASC")
    suspend fun getByIntent(intent: String): List<CachedCardEntity>
}
```

### 4.5 Converters

File: `data/db/Converters.kt`

```kotlin
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromTags(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toTags(raw: String): List<String> = json.decodeFromString(raw)
}
```

### 4.6 Database

File: `data/db/AppDatabase.kt`

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
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedbackDao(): FeedbackDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun cachedCardDao(): CachedCardDao

    companion object {
        const val NAME = "ai_launcher.db"
    }
}
```

Builder (in `DatabaseModule`, §9) MUST apply:

```kotlin
Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME)
    .fallbackToDestructiveMigration()           // FR-09
    .fallbackToDestructiveMigrationOnDowngrade()
    .build()
```

Room schema JSON is exported to `app/schemas/` by setting KSP arg `room.schemaLocation=$projectDir/schemas` in `app/build.gradle.kts` under `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.

### 4.7 Seed Data Loader

File: `data/seed/SeedLoader.kt`

```kotlin
@Singleton
class SeedLoader @Inject constructor(
    private val cachedCardDao: CachedCardDao,
    private val json: Json,
    @Named("ioDispatcher") private val io: CoroutineDispatcher
) {
    suspend fun ensureSeeded() = withContext(io) {
        if (cachedCardDao.count() == 0) {
            val entities = SEED_CARDS.mapIndexed { index, s ->
                CachedCardEntity(
                    id = s.id,
                    title = s.title,
                    description = s.description,
                    intent = s.intent.name,
                    type = s.type.name,
                    icon = s.icon,
                    actionLabel = s.actionLabel,
                    whyLabel = s.whyLabel,
                    tagsJson = json.encodeToString(s.tags),
                    seedOrder = index
                )
            }
            cachedCardDao.insertAll(entities)
        }
    }
}
```

Called once from `MainActivity.onCreate` (after DI initialization, inside `lifecycleScope.launch`).

### 4.8 Repository transactional contract (FR-10)

`FeedbackRepositoryImpl.record(event, card)` wraps both writes (insert `feedback_events` + upsert N rows in `user_profile`) in `database.withTransaction { ... }` per FR-10. The formula `weightDelta = event.weight / card.tags.size` is applied per-tag.

---

## 5. Domain Models

File layout under `data/model/` (domain-flavored models live here per AGENTS.md; they are not tied to Room).

### 5.1 `Intent.kt`

```kotlin
enum class Intent(val displayName: String, val headerFormat: String) {
    COMMUTE("通勤", "通勤模式 · %s"),
    WORK   ("工作", "工作模式 · %s"),
    LUNCH  ("午餐", "午餐时间 · %s"),
    REST   ("休息", "休息时间 · %s");

    companion object { val DEFAULT: Intent = WORK }  // FR-03 cold-start
}
```

### 5.2 `CardType.kt`

```kotlin
enum class CardType { CONTINUE, DISCOVER, NEW, HERO }
```

> `HERO` is a **display role**, not a seed attribute. Seed cards are only CONTINUE/DISCOVER/NEW. At render time, `HomeViewModel` marks the top-ranked card as `displayType = HERO` in the UI state. This distinction lets the UI layer style the hero without the engine caring.

### 5.3 `SeedCard.kt` and `Card.kt`

```kotlin
data class SeedCard(
    val id: String,
    val title: String,
    val description: String,
    val intent: Intent,
    val type: CardType,                // CONTINUE | DISCOVER | NEW
    val icon: String,                  // emoji
    val actionLabel: String,
    val whyLabel: String,
    val tags: List<String>
)

data class Card(
    val id: String,
    val title: String,
    val description: String,
    val intent: Intent,
    val type: CardType,                // seed type (CONTINUE | DISCOVER | NEW)
    val displayType: CardType,         // HERO when shown in hero slot, else == type
    val icon: String,
    val actionLabel: String,
    val whyLabel: String,
    val tags: List<String>,
    val seedOrder: Int                 // for deterministic tiebreak
)
```

### 5.4 `Signal.kt` (sealed)

```kotlin
sealed class Signal {
    abstract val cardId: String
    abstract val intent: Intent

    data class Tap        (override val cardId: String, override val intent: Intent) : Signal()
    data class Dwell      (override val cardId: String, override val intent: Intent, val durationMs: Long) : Signal()
    data class Like       (override val cardId: String, override val intent: Intent) : Signal()   // 👍
    data class Dislike    (override val cardId: String, override val intent: Intent) : Signal()   // 👎
    data class Dismiss    (override val cardId: String, override val intent: Intent) : Signal()   // ✕
    data class ActionTapped(override val cardId: String, override val intent: Intent) : Signal()  // FR-08
    data class IntentSwitch(override val cardId: String, override val intent: Intent, val fromIntent: Intent) : Signal()
    data class RevealAppeared(override val cardId: String, override val intent: Intent) : Signal() // appearance start
}

/** Flat enum persisted to Room. */
enum class SignalType(val weight: Float) {
    TAP(+2f),
    DWELL(+1f),
    THUMB_UP(+3f),
    THUMB_DOWN(-3f),
    DISMISS(-2f),
    ACTION_TAP(+2f),
    INTENT_SWITCH(0f),    // bookkeeping, no profile effect
    REVEAL_APPEARED(0f);  // bookkeeping, no profile effect
}

fun Signal.toType(): SignalType = when (this) {
    is Signal.Tap            -> SignalType.TAP
    is Signal.Dwell          -> SignalType.DWELL
    is Signal.Like           -> SignalType.THUMB_UP
    is Signal.Dislike        -> SignalType.THUMB_DOWN
    is Signal.Dismiss        -> SignalType.DISMISS
    is Signal.ActionTapped   -> SignalType.ACTION_TAP
    is Signal.IntentSwitch   -> SignalType.INTENT_SWITCH
    is Signal.RevealAppeared -> SignalType.REVEAL_APPEARED
}
```

Only signals with non-zero weight update the profile (per FR-10). Zero-weight signals still persist to `feedback_events` so debug tooling (FR-20) can replay them.

### 5.5 `UserProfile.kt`

```kotlin
data class UserProfile(
    /** Tag → weight snapshot (positive or negative). */
    val tagWeights: Map<String, Float>,
    val totalEvents: Int,
    val updatedMs: Long
) {
    fun weightOf(tag: String): Float = tagWeights[tag] ?: 0f

    companion object {
        val EMPTY = UserProfile(emptyMap(), 0, 0L)
    }
}
```

### 5.6 `ConfidenceLevel.kt`

```kotlin
enum class ConfidenceLevel { LOW, MEDIUM, HIGH;
    companion object {
        /** Deterministic mapping from total event count. */
        fun fromEventCount(n: Int): ConfidenceLevel = when {
            n < 5  -> LOW
            n < 20 -> MEDIUM
            else   -> HIGH
        }
    }
}
```

Used by `Header` composable to render a badge next to the profile pill. Not referenced by the engine.

### 5.7 `FeedbackEvent.kt` (domain mirror)

```kotlin
data class FeedbackEvent(
    val id: Long = 0L,
    val cardId: String,
    val intent: Intent,
    val signalType: SignalType,
    val weight: Float,
    val timestampMs: Long
)
```

Repositories accept `FeedbackEvent` (domain) and convert to `FeedbackEventEntity` internally.

---

## 6. Repository Interfaces

All three live under `data/repo/`. Implementations are `@Singleton`; interfaces bound in `RepositoryModule`.

### 6.1 `FeedbackRepository`

```kotlin
interface FeedbackRepository {
    /** True if Room is open and writable; false forces FR-19 graceful-degrade. */
    val isHealthy: StateFlow<Boolean>

    /**
     * Persists a feedback event AND updates user_profile in the same transaction (FR-10).
     * Applies 400ms same-(cardId, signalType) debounce per FR-02.
     * Uses injected Clock for timestamps (FR-07 test hook).
     * Returns the stored event id, or null when debounced/unhealthy.
     */
    suspend fun record(signal: Signal, card: Card): Long?

    suspend fun count(): Int
    fun countFlow(): Flow<Int>
    suspend fun getAll(): List<FeedbackEvent>

    /** Clears feedback_events AND user_profile atomically (FR-12 reset). */
    suspend fun clearAll()
}
```

### 6.2 `CardRepository`

```kotlin
interface CardRepository {
    /** All 16 seed cards in declaration order (FR-16). In-memory backed. */
    suspend fun getAll(): List<Card>

    /** Cards for one intent, ordered by seed_order ASC. */
    suspend fun getForIntent(intent: Intent): List<Card>

    suspend fun getById(id: String): Card?
}
```

### 6.3 `ProfileRepository`

```kotlin
interface ProfileRepository {
    suspend fun snapshot(): UserProfile
    fun observe(): Flow<UserProfile>
    suspend fun topTags(n: Int = 10): List<Pair<String, Float>>
    /** Used internally by FeedbackRepositoryImpl.record. */
    suspend fun applyDelta(tag: String, delta: Float, nowMs: Long)
    suspend fun reset()
}
```

---

## 7. RecommendationEngine

File: `domain/recommend/RecommendationEngine.kt`

```kotlin
interface RecommendationEngine {
    /**
     * Returns up to [count] cards for [intent], composed via FR-11's fixed-slot ε-greedy:
     *   - 1 exploration slot when availableCards.size >= 2
     *   - remainder are top-scored exploitation picks
     *   - exploitation ordering: score DESC, tiebreak = seedOrder ASC
     *   - exploration slot placed at position (available.size - 1)
     *
     * Purely synchronous and stateless; caller provides all inputs.
     */
    fun recommend(
        intent: Intent,
        count: Int,
        availableCards: List<Card>,            // already filtered by intent & dismiss list
        profile: UserProfile
    ): List<Card>

    /** FR-20 debug hook (no-op in release). */
    fun setDeterministicMode(seed: Long)
}
```

File: `domain/recommend/EpsilonGreedyEngine.kt`

```kotlin
@Singleton
class EpsilonGreedyEngine @Inject constructor(
    @Named("recEngineRng") private val rngProvider: Provider<Random>,
    private val clock: Clock
) : RecommendationEngine {

    @Volatile private var overrideRng: Random? = null

    override fun setDeterministicMode(seed: Long) {
        if (BuildConfig.DEBUG) overrideRng = Random(seed)
    }

    private fun rng(): Random = overrideRng ?: rngProvider.get()

    /** score(c, P) = Σ (1.0 × P.weight[tag]) for tag in c.tags  (FR-11) */
    internal fun score(card: Card, profile: UserProfile): Float =
        card.tags.fold(0f) { acc, tag -> acc + profile.weightOf(tag) }

    override fun recommend(
        intent: Intent,
        count: Int,
        availableCards: List<Card>,
        profile: UserProfile
    ): List<Card> {
        if (availableCards.isEmpty()) return emptyList()
        if (availableCards.size == 1) return availableCards.take(count)

        // Exploitation pool: all-but-one, sorted by score desc, then seedOrder asc
        val sorted = availableCards.sortedWith(
            compareByDescending<Card> { score(it, profile) }
                .thenBy { it.seedOrder }
        )
        val exploitationCount = (availableCards.size - 1)
        val exploitation = sorted.take(exploitationCount)
        val explorationPool = availableCards - exploitation.toSet()
        val exploration = explorationPool[rng().nextInt(explorationPool.size)]

        // Place exploration at bottom (position = available.size - 1)
        val composed = buildList {
            addAll(exploitation)            // positions 0 .. size-2
            add(exploration)                // position size-1
        }
        return composed.take(count)
    }
}
```

Notes for the dev agent:

- `Provider<Random>` lets tests swap the binding per run; the `overrideRng` gate is a separate path required only by FR-20's runtime `setDeterministicMode`.
- Dismissed cards are filtered **outside** the engine (by `HomeViewModel`), per FR-11 "The dismiss-list is passed in as a parameter by the caller".
- Engine has no knowledge of the Hero role; `HomeViewModel` tags position 0 as `displayType = HERO` before emitting UI state.
- `Clock` is injected even though the engine doesn't currently use wall-time — reserved for future exploration-decay logic and to mirror the FR-07 test-hook convention.

---

## 8. ViewModel Layer

All three ViewModels are `@HiltViewModel`, expose `val uiState: StateFlow<…>`, and handle events via named methods (no Compose events lifted through sealed `Intent`-style sum types for v0.1 — too much ceremony for 3 VMs).

### 8.1 `HomeViewModel`

File: `ui/home/HomeViewModel.kt`

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cardRepo: CardRepository,
    private val feedbackRepo: FeedbackRepository,
    private val profileRepo: ProfileRepository,
    private val engine: RecommendationEngine,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** In-memory dismiss session per FR-06 (intent → Set<cardId>). */
    private val dismissed: MutableMap<Intent, MutableSet<String>> =
        Intent.values().associateWith { mutableSetOf<String>() }.toMutableMap()

    private var activeIntent: Intent = Intent.DEFAULT
    private var lastIntentSwitchMs: Long = 0L                        // FR-04 rapid-switch

    fun start() { /* load cards, observe profile, emit initial state */ }
    fun onIntentSelected(intent: Intent)
    fun onSignal(signal: Signal, card: Card)                         // routes to repo + refresh
    fun onDismiss(card: Card)                                        // hides + records DISMISS
    fun onRestoreDismissed()                                         // FR-19 "恢复本模式"
    fun onActionTapped(card: Card)                                   // FR-08
    fun onHomeButtonPressed()                                        // FR-14 canonical state
    fun refresh()                                                    // FR-12 post-reset re-render
    /** Called by DwellTracker; FR-07 filters apply here. */
    fun onCardAppearanceStarted(cardId: String)
    fun onCardAppearanceEnded(cardId: String, dwellMs: Long)
}
```

**`HomeUiState`** (`ui/home/HomeUiState.kt`):

```kotlin
sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Ready(
        val activeIntent: Intent,
        val headerText: String,                      // "工作模式 · 14:22"
        val showDefaultLauncherBanner: Boolean,      // FR-17
        val hero: Card?,                             // null only in EmptyAllDismissed
        val discover: List<Card>,                    // 0..3
        val confidence: ConfidenceLevel,
        val emptyState: EmptyState = EmptyState.None
    ) : HomeUiState()
    data class Degraded(val reason: DegradeReason) : HomeUiState()   // FR-19 Room unavailable
}

enum class EmptyState { None, AllDismissedForIntent }
enum class DegradeReason { RoomUnavailable }
```

### 8.2 `AppDrawerViewModel`

File: `ui/drawer/AppDrawerViewModel.kt`

```kotlin
@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Named("ioDispatcher") private val io: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppDrawerUiState>(AppDrawerUiState.Loading)
    val uiState: StateFlow<AppDrawerUiState> = _uiState.asStateFlow()

    fun refresh()                                  // called onStart of screen
    fun onAppTapped(item: AppDrawerItem): LaunchResult
}

data class AppDrawerItem(
    val packageName: String,
    val label: String,
    val iconDrawable: Drawable
)

sealed class AppDrawerUiState {
    data object Loading : AppDrawerUiState()
    data class Ready(val apps: List<AppDrawerItem>) : AppDrawerUiState()
    data object Empty : AppDrawerUiState()
}

sealed class LaunchResult {
    data object Success : LaunchResult()
    data class Failure(val reason: String) : LaunchResult()   // triggers Snackbar
}
```

### 8.3 `ProfileDebugViewModel`

File: `ui/profile/ProfileDebugViewModel.kt`

```kotlin
@HiltViewModel
class ProfileDebugViewModel @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val feedbackRepo: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileDebugUiState>(ProfileDebugUiState.Loading)
    val uiState: StateFlow<ProfileDebugUiState> = _uiState.asStateFlow()

    fun load()
    fun onResetConfirmed()                           // FR-12 4-step reset
    fun onSeedDemoProfile()                          // FR-20 debug only
    fun onDumpProfile()                              // FR-20 debug only
}

sealed class ProfileDebugUiState {
    data object Loading : ProfileDebugUiState()
    data class Ready(
        val topTags: List<TagWeight>,                // size ≤ 10
        val totalEvents: Int,
        val confidence: ConfidenceLevel
    ) : ProfileDebugUiState()
}

data class TagWeight(val tag: String, val weight: Float)
```

The reset flow (FR-12) emits a side-effect callback to `HomeViewModel.refresh()` via a shared event bus OR via the Compose-level `onResetSuccess: () -> Unit` parameter. Architecture choice: **callback lambda** (simpler, no extra class).

---

## 9. Hilt DI Graph

Application: `AiLauncherApp` annotated `@HiltAndroidApp`.
`MainActivity` annotated `@AndroidEntryPoint`.
All three ViewModels annotated `@HiltViewModel` (shown in §8).

### 9.1 `DatabaseModule`

File: `di/DatabaseModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideFeedbackDao(db: AppDatabase): FeedbackDao = db.feedbackDao()
    @Provides fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()
    @Provides fun provideCachedCardDao(db: AppDatabase): CachedCardDao = db.cachedCardDao()

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; prettyPrint = false }
}
```

### 9.2 `RepositoryModule`

File: `di/RepositoryModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindFeedbackRepo(impl: FeedbackRepositoryImpl): FeedbackRepository

    @Binds @Singleton
    abstract fun bindCardRepo(impl: CardRepositoryImpl): CardRepository

    @Binds @Singleton
    abstract fun bindProfileRepo(impl: ProfileRepositoryImpl): ProfileRepository
}
```

### 9.3 `DomainModule`

File: `di/DomainModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {
    @Binds @Singleton
    abstract fun bindEngine(impl: EpsilonGreedyEngine): RecommendationEngine

    companion object {
        @Provides @Singleton @Named("recEngineRng")
        fun provideRng(): Random = Random.Default        // test replaces with Random(42L)
    }
}
```

### 9.4 `AppModule`

File: `di/AppModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideClock(): Clock = SystemClock

    @Provides @Singleton @Named("ioDispatcher")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @Named("defaultDispatcher")
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton @Named("mainDispatcher")
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}
```

**Full binding summary (DI dependency table):**

| Interface / qualifier            | Impl                       | Scope     |
|----------------------------------|----------------------------|-----------|
| `AppDatabase`                    | Room-generated             | Singleton |
| `FeedbackDao` / `UserProfileDao` / `CachedCardDao` | Room-generated | Unscoped (via DB) |
| `Json`                           | `Json { }`                 | Singleton |
| `FeedbackRepository`             | `FeedbackRepositoryImpl`   | Singleton |
| `CardRepository`                 | `CardRepositoryImpl`       | Singleton |
| `ProfileRepository`              | `ProfileRepositoryImpl`    | Singleton |
| `RecommendationEngine`           | `EpsilonGreedyEngine`      | Singleton |
| `@Named("recEngineRng") Random`  | `Random.Default`           | Singleton |
| `Clock`                          | `SystemClock`              | Singleton |
| `@Named("ioDispatcher") CoroutineDispatcher` | `Dispatchers.IO` | Singleton |
| `@Named("defaultDispatcher") CoroutineDispatcher` | `Dispatchers.Default` | Singleton |
| `@Named("mainDispatcher") CoroutineDispatcher`  | `Dispatchers.Main.immediate` | Singleton |
| `SeedLoader`                     | itself (constructor-inject)| Singleton |

---

## 10. Navigation

Single activity (`MainActivity`) → single `NavHost` with two destinations.

File: `ui/nav/Routes.kt`

```kotlin
object Routes {
    const val HOME = "home"
    const val DRAWER = "drawer"
}
```

File: `ui/nav/NavGraph.kt`

```kotlin
@Composable
fun AiLauncherNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDrawer = { navController.navigate(Routes.DRAWER) }
            )
        }
        composable(Routes.DRAWER) {
            AppDrawerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

`MainActivity.setContent { AiLauncherTheme { AiLauncherNavGraph(rememberNavController()) } }`.

Home-button re-entry (FR-14) is implemented in `MainActivity.onNewIntent`:

```kotlin
override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    if (intent.action == android.content.Intent.ACTION_MAIN &&
        intent.categories?.contains(android.content.Intent.CATEGORY_HOME) == true) {
        navController.popBackStack(Routes.HOME, inclusive = false)
        homeViewModel.onHomeButtonPressed()   // dismiss sheet, scroll to top, preserve intent
    }
}
```

Bottom sheets (Profile v47) are **not** navigation destinations — they are Compose `ModalBottomSheet` controlled by a `rememberSaveable` state in `HomeScreen`. Dismissed via `HomeViewModel.onHomeButtonPressed()`.

---

## 11. AndroidManifest.xml

File: `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- FR-13: Android 11+ package visibility for querying launchable apps -->
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>

    <!-- No <uses-permission> entries required by v0.1. No network, no storage. -->

    <application
        android:name=".AiLauncherApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AiLauncher"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true"
            android:resizeableActivity="true"
            android:clearTaskOnLaunch="false"
            android:windowSoftInputMode="adjustNothing"
            android:theme="@style/Theme.AiLauncher">

            <!-- FR-14: launcher intent filter -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`res/values/strings.xml` (seed):

```xml
<resources>
    <string name="app_name">AI Launcher</string>
    <!-- FR-19 empty states, FR-17 banner, etc. Dev agent fills in. -->
</resources>
```

`res/xml/data_extraction_rules.xml` — empty rules (no cloud backup for v0.1, NFR-06):

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup><exclude domain="database" path="ai_launcher.db" /></cloud-backup>
    <device-transfer><exclude domain="database" path="ai_launcher.db" /></device-transfer>
</data-extraction-rules>
```

---

## 12. Testing Architecture

### 12.1 JVM unit tests (`src/test/`)

**`EpsilonGreedyEngineTest`** — satisfies SM-07.

Construction:
```kotlin
private val rng = Random(42L)
private val engine = EpsilonGreedyEngine(
    rngProvider = Provider { rng },
    clock = TestClock(startMs = 1_700_000_000_000L)
)
```

Key test cases:
- `recommend_emptyProfile_returnsSeedOrder` — with `UserProfile.EMPTY`, 4 LUNCH cards, exploitation order equals seed order for the 3 exploitation slots; exploration slot is drawn from the remainder.
- `recommend_biasedProfile_topIsExploitation` — given `profile.tagWeights = mapOf("coffee" to 10f)`, run 1000× with seeded RNG, assert position 0 is `lunch_01` (contains `coffee`) in all 1000 calls.
- `recommend_explorationSlotVaries` — same run, assert position 3 (`available.size - 1`) is a non-top card in ≥500/1000 calls (satisfies SM-07 ≥50%).
- `recommend_singleCard_returnsAsIs` — pool size 1.
- `recommend_emptyPool_returnsEmpty`.
- `recommend_tiesBreakBySeedOrder` — all-zero profile, explicit order preserved.

**ViewModel tests** — use `FakeFeedbackRepository`, `FakeCardRepository`, `FakeProfileRepository` + real `EpsilonGreedyEngine(rng=Random(42L))` + `TestClock` + `kotlinx-coroutines-test` `runTest`.

`FakeFeedbackRepository` stores a `MutableList<FeedbackEvent>` and a `MutableMap<String, Float>` for tags; `record(...)` applies the FR-10 formula. Suitable for SM-04 test on VM level.

`TestClock`:
```kotlin
class TestClock(var nowMs: Long = 0L) : Clock {
    override fun nowMillis(): Long = nowMs
    fun advance(ms: Long) { nowMs += ms }
}
```

`MainDispatcherRule` sets `Dispatchers.Main` to `StandardTestDispatcher` for tests.

**`HomeViewModelTest`** key cases:
- Cold start → `HomeUiState.Ready` with hero = `work_01`, 3 discover cards (FR-01, SM-12).
- Intent switch → state updates; intent switch within 500ms of feed render discards pending appearances (FR-04).
- 2× 👎 on hero → hero cardId changes (SM-04).
- Dismiss ✕ → card removed from discover list; DISMISS event recorded in FakeFeedbackRepo (SM-11).
- All-dismissed → `EmptyState.AllDismissedForIntent` (FR-19).
- Degraded mode → `FakeFeedbackRepository(isHealthy=false)` forces `HomeUiState.Degraded`.

### 12.2 Instrumented tests (`src/androidTest/`)

Use `Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()` per test.

- **`FeedbackDaoTest`**: insert → getAll, count, debounce-window query works, deleteAll.
- **`UserProfileDaoTest`**: upsert replaces, `topByAbsWeight(10)` orders by `ABS(weight) DESC`, deleteAll.
- **`CachedCardDaoTest`**: `insertAll(16) → count == 16`, `getByIntent(WORK)` returns 4 in seed order.

Clock injection per FR-07 applies to dwell-related tests (future Compose UI tests under `src/androidTest/`), but v0.1 instrumented coverage is DAO-only; Compose UI tests land in v0.2.

### 12.3 `AndroidJUnitRunner` config

Already set in `defaultConfig.testInstrumentationRunner`. No custom runner.

---

## 13. Performance Budgets (NFR)

NFR-01 (cold-start ≤500ms), NFR-02 (Room ≤50ms p95), NFR-03 (chip switch ≤200ms).

**Compose stability rules (non-negotiable):**

- All UI state classes (`HomeUiState.Ready`, `Card`, `TagWeight`, etc.) are **stable** (all `val`, no `List<Card>` without type args, no `kotlin.collections.Map` — use `kotlinx.collections.immutable.PersistentList` if we upgrade; for v0.1 `List<Card>` read from `MutableStateFlow` is marked `@Immutable` at class level).
- Annotate all data classes returned from VMs with `@Immutable`.
- `Card`, `AppDrawerItem`, and `TagWeight` get `@Immutable`.
- Mark composable params that accept lambdas as `() -> Unit` not nullable.
- No `remember { mutableStateOf(...) }` holding `List<Card>` — always drive via `collectAsStateWithLifecycle()` from `StateFlow`.

**LazyColumn keying:**

```kotlin
LazyColumn {
    items(
        items = state.discover,
        key = { it.id },
        contentType = { it.type.name }
    ) { card -> DiscoverCard(card = card, ...) }
}
```

Same rule for `LazyVerticalGrid` in `AppDrawerScreen` with `key = { it.packageName }`.

**Room indexes (already in §4):**

- `feedback_events` indexes on `card_id`, `intent`, `timestamp_ms`.
- `user_profile` index on `weight` for the top-10 query.
- `cached_cards` index on `intent`.

**Query plan targets:**

- `FeedbackDao.count()` → index-only scan, <5ms typical.
- `UserProfileDao.topByAbsWeight(10)` → sort over ≤~50 rows (small tag space), <5ms.
- `CachedCardDao.getByIntent(intent)` → covered by index, ≤16 rows → trivial.

**Macrobenchmark (v0.2 deliverable, stubbed in v0.1):** `:app:benchmark` variant reserved; v0.1 measures cold-start manually via `adb shell am start -W`.

**Dispatcher discipline:**

- Repositories switch to `@Named("ioDispatcher")` internally on DB calls via `withContext(io)`; ViewModels never block.
- `RecommendationEngine.recommend` is pure synchronous and called from `Dispatchers.Default` within VM's `viewModelScope.launch(defaultDispatcher)`.

---

## 14. Security / Privacy

Per NFR-04, NFR-05, NFR-06:

- **Permissions:** none declared. No `android.permission.INTERNET`, no runtime permissions. The absence of INTERNET is itself a build-time guarantee that no library performs network I/O.
- **Network libraries:** forbidden in `app/build.gradle.kts`. A CI check (§15) asserts the dependency tree contains no `okhttp3`, `retrofit2`, `ktor`, `volley`.
- **Backup:** `android:allowBackup="false"` + `data_extraction_rules.xml` excludes the Room DB from cloud backup and device transfer.
- **Logging:** FR-20 `FLYWHEEL` logcat tag gated behind `BuildConfig.DEBUG`. Release builds emit no per-signal logs.
- **Analytics:** none. No Firebase, no Crashlytics, no Sentry.
- **Data location:** `/data/data/com.bobpan.ailauncher/databases/ai_launcher.db` (private app storage; not world-readable on any API level).
- **Exports:** `MainActivity` is `android:exported="true"` **only** because Android 12+ requires it for launcher activities (FR-14). No other activity, service, receiver, or provider is exported.
- **ProGuard:** release keeps Room entities, Hilt generated code, and kotlinx.serialization `@Serializable` classes. `proguard-rules.pro`:
  ```
  -keep class com.bobpan.ailauncher.data.db.entity.** { *; }
  -keepnames class * extends androidx.room.RoomDatabase
  -keep class kotlinx.serialization.** { *; }
  -keep @kotlinx.serialization.Serializable class * { *; }
  ```

---

## 15. Build / CI

**Primary invariant:** `./gradlew :app:assembleDebug` must succeed at the end of every commit. The dev agent is expected to run this locally before every push.

**Local commands:**

```bash
./gradlew :app:assembleDebug           # must be green
./gradlew :app:testDebugUnitTest       # JVM tests
./gradlew :app:connectedDebugAndroidTest   # instrumented (requires emulator/device)
./gradlew :app:lintDebug               # Android lint
./gradlew :app:dependencies            # for dep audit
```

**CI (GitHub Actions suggested, not required for v0.1):**

```yaml
# .github/workflows/android.yml
name: Android CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
      - name: Dep audit (no network libs)
        run: |
          ! ./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E 'okhttp|retrofit|ktor|volley'
```

**Versioning:** `versionCode = 1`, `versionName = "0.1.0"` for v0.1 ship.

**Build variants:**

- `debug` — `minify=false`, `FLYWHEEL` logging on, FR-20 dev panel accessible, `RecommendationEngine.setDeterministicMode` callable.
- `release` — `minify=true`, `shrinkResources=true`, ProGuard rules as §14, FR-20 gates disabled.

**Gradle JVM args** (in `gradle.properties`, already listed in §3.4): `-Xmx4g` to accommodate KSP + Room + Hilt processing on 16-GB CI runners.

---

## Appendix A · Decisions not explicit in PRD-v2

The PRD is deliberately prescriptive; the following architecture decisions resolve remaining latitude. All are logged here so nothing is implicit.

1. **`CardType.HERO` as display role, not seed attribute.** PRD §5 lists HERO as a card type, but §9 seed data only uses CONTINUE/DISCOVER/NEW. Resolution: keep HERO in the enum (per task spec) and add a second field `displayType: CardType` on the runtime `Card` model. Hero rendering is a UI-layer concern; the engine is unaware.
2. **`SignalType` is a flat enum, `Signal` is a sealed class.** The sealed class is the VM/UI vocabulary (carries per-signal payload like `Dwell.durationMs`); the enum is the persistence representation (simpler to store + query). `Signal.toType()` converts. This keeps the `feedback_events.signal` column a single TEXT.
3. **`REVEAL_APPEARED` and `INTENT_SWITCH` as zero-weight signals.** These are useful for debugging the FR-07 state machine and FR-04 rapid-switch suppression via FR-20 logcat, but they must not alter `user_profile`. The `weight == 0f` short-circuit in `FeedbackRepositoryImpl.record` avoids the N-way `user_profile` upsert for them.
4. **`CachedCardEntity` exists even though v0.1 serves seeds from memory.** AGENTS.md lists "cached cards" as a Room table, and `SeedLoader` maintains the invariant that on-disk and in-memory are identical at cold-start. This costs ~2KB of storage and makes v0.2 dynamic catalogs trivially pluggable.
5. **`ConfidenceLevel` thresholds (5 / 20 events).** PRD mentions the concept but not bounds. Resolution: `LOW < 5`, `MEDIUM < 20`, `HIGH ≥ 20`. Pure UI concern, no engine impact.
6. **`Provider<Random>` injection in `EpsilonGreedyEngine`.** PRD says the engine "accepts an injected Random"; using `Provider<Random>` rather than a direct `Random` lets the FR-20 `setDeterministicMode` runtime override coexist with the DI-provided instance without rebinding. Tests still bind `Random(42L)` directly and the Provider returns the same instance.
7. **`datastore-preferences` is included in deps but not used in v0.1.** The PRD hard-scopes out persisting selected intent across cold starts. We keep the dependency because the FR-17 "banner dismissed for this process lifetime" flag, profile-sheet UI preferences, and v0.2 intent persistence will all land there; adding it now costs nothing and avoids a second Gradle sync when v0.2 begins.
8. **Home-button re-entry is routed through `MainActivity.onNewIntent` + `HomeViewModel.onHomeButtonPressed()`.** PRD FR-14 lists 5 steps but not the wiring. Resolution: `launchMode="singleTask"` guarantees no activity restart; `onNewIntent` receives CATEGORY_HOME; the VM method performs all 5 steps in order on the main thread.
9. **Bottom sheet state is Compose-local, not a Nav destination.** Material 3 `ModalBottomSheet` is not a screen; making it a nav route complicates FR-14 step 1 ("dismiss any open bottom sheet") by adding a backstack entry to pop. Keeping it in `HomeScreen` state is simpler and still satisfies the back-gesture requirement via `BackHandler`.
10. **`kotlinx-coroutines-android` is the chosen coroutine dep.** PRD lists `kotlinx-coroutines-test` for tests but not the main-source dep; `kotlinx-coroutines-android` is the standard Android artifact and includes `Dispatchers.Main.immediate` used by VM launches.
11. **`hilt-navigation-compose` added.** Not in the PRD-listed deps but required by `hiltViewModel()` in Compose — without it, `@HiltViewModel` VMs cannot be obtained from within a `composable { }` block. Treated as a transitive necessity of the locked Hilt + Compose stack.
12. **`material-icons-extended` added.** The design uses ✦, ✓, ✕, 👍, 👎 glyphs; emoji render inline but dismiss ✕ and chip ✓ are Material icons (`Icons.Default.Close`, `Icons.Default.Check`). Needed to satisfy FR-03 and FR-05 touch targets with icon-only buttons.
13. **`androidx.lifecycle-runtime-compose` added.** For `collectAsStateWithLifecycle()`, which is the recommended way to observe `StateFlow` from Compose and avoids wasted work while paused (relevant to NFR-05 battery and FR-07 pause-resume behavior).

---

*End of ARCHITECTURE v1.*
