package com.bobpan.ailauncher.di

import com.bobpan.ailauncher.domain.recommend.EpsilonGreedyEngine
import com.bobpan.ailauncher.domain.recommend.RecommendationEngine
import com.bobpan.ailauncher.util.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    /**
     * Provider-qualified RNG factory. Production: [Random.Default].
     * Tests override via @TestInstallIn with Random(42L) (per ARCH §7.2).
     *
     * @JvmSuppressWildcards prevents Kotlin from emitting Function0<? extends Random>
     * which Dagger treats as a different type than Function0<Random>.
     */
    @Provides
    @Singleton
    @Named("recEngineRng")
    fun provideRecEngineRng(): @JvmSuppressWildcards () -> Random = { Random.Default }

    @Provides
    @Singleton
    fun provideRecommendationEngine(
        @Named("recEngineRng") rng: @JvmSuppressWildcards () -> Random,
        clock: Clock
    ): RecommendationEngine = EpsilonGreedyEngine(rng, clock)
}
