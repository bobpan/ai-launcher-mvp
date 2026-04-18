package com.bobpan.ailauncher.domain.recommend

import androidx.compose.runtime.Immutable
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.data.model.UserProfile

@Immutable
data class RecommendationResult(
    val ordered:  List<Card>,
    val scores:   Map<String, Float>,
    val maxScore: Float
)

/**
 * Pure ε-greedy recommender. FR-11 + Locked Decision #17 (inject RNG via @Named).
 */
interface RecommendationEngine {
    fun recommend(
        intent:    Intent,
        profile:   UserProfile,
        catalog:   List<Card>,
        dismissed: Set<String>,
        count:     Int = 4
    ): RecommendationResult

    /** DEBUG-only hook (FR-20); release no-op. */
    fun setDeterministicMode(seed: Long)
}
