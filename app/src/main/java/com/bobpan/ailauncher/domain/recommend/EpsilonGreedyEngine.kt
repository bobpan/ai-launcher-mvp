package com.bobpan.ailauncher.domain.recommend

import com.bobpan.ailauncher.BuildConfig
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.data.model.UserProfile
import com.bobpan.ailauncher.util.Clock
import kotlin.random.Random

/**
 * ε-greedy implementation per FR-11:
 *   - Score(c,P) = Σ P.weight[tag] for tag in c.tags (card-side weight = 1.0).
 *   - Tiebreak: seedOrder ascending (stable).
 *   - Slot allocation: if available ≥ 2, reserve exactly 1 exploration slot.
 */
class EpsilonGreedyEngine(
    private val rngProvider: () -> Random,
    @Suppress("unused") private val clock: Clock
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
            .sortedBy { it.seedOrder }
            .toList()

        if (available.isEmpty()) {
            return RecommendationResult(emptyList(), emptyMap(), 0f)
        }

        // Scores (FR-11 formula).
        val scored: List<Pair<Card, Float>> = available.map { card ->
            val s = card.tags.sumOf { tag -> profile.weightOf(tag).toDouble() }.toFloat()
            card to s
        }

        // Stable DESC-by-score sort; ties preserved in seed order (available was already sorted).
        val sortedExploit = scored
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Pair<Card, Float>>> { it.value.second }
                    .thenBy { it.index }
            )
            .map { it.value }

        val scoresMap = scored.associate { it.first.id to it.second }

        if (available.size <= 1) {
            val ordered = sortedExploit.map { it.first }.take(count)
            return RecommendationResult(
                ordered  = ordered,
                scores   = scoresMap,
                maxScore = sortedExploit.firstOrNull()?.second ?: 0f
            )
        }

        // Fixed-slot allocation: 1 explore slot, remainder exploit.
        val exploitCount = available.size - 1
        val exploit      = sortedExploit.take(exploitCount).map { it.first }
        val explorePool  = available - exploit.toSet()
        val exploreChoice = if (explorePool.isNotEmpty()) {
            explorePool[rng().nextInt(explorePool.size)]
        } else null

        val ordered = buildList {
            addAll(exploit)
            exploreChoice?.let { add(it) }
        }.take(count)

        return RecommendationResult(
            ordered  = ordered,
            scores   = scoresMap,
            maxScore = sortedExploit.firstOrNull()?.second ?: 0f
        )
    }

    override fun setDeterministicMode(seed: Long) {
        if (!BuildConfig.DEBUG) return
        overrideRng = Random(seed)
    }
}
