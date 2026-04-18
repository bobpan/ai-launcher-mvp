package com.bobpan.ailauncher.domain.recommend

import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.CardType
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.data.model.UserProfile
import com.bobpan.ailauncher.util.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EpsilonGreedyEngineTest {

    private fun makeEngine(seed: Long = 42L): EpsilonGreedyEngine {
        val rng = Random(seed)
        val clock = object : Clock { override fun nowMs() = 0L }
        return EpsilonGreedyEngine(rngProvider = { rng }, clock = clock)
    }

    private fun fakeCatalog(): List<Card> = listOf(
        Card("lunch_01", Intent.LUNCH, CardType.CONTINUE,
            "☕", "coffee", "", "go", "",
            listOf("coffee", "lunch", "habit"), seedOrder = 0),
        Card("lunch_02", Intent.LUNCH, CardType.DISCOVER,
            "🍲", "restaurant", "", "view", "",
            listOf("restaurant", "lunch", "nearby"), seedOrder = 1),
        Card("lunch_03", Intent.LUNCH, CardType.DISCOVER,
            "🛵", "delivery", "", "order", "",
            listOf("delivery", "lunch"), seedOrder = 2),
        Card("lunch_04", Intent.LUNCH, CardType.NEW,
            "🥗", "salad", "", "try", "",
            listOf("salad", "healthy", "lunch", "explore"), seedOrder = 3)
    )

    @Test
    fun `cold start yields seed-ordered hero`() {
        val engine = makeEngine()
        val result = engine.recommend(
            intent = Intent.LUNCH,
            profile = UserProfile.Empty,
            catalog = fakeCatalog(),
            dismissed = emptySet()
        )
        assertEquals(4, result.ordered.size)
        assertEquals("lunch_01", result.ordered.first().id)
    }

    @Test
    fun `empty available set returns empty list`() {
        val engine = makeEngine()
        val result = engine.recommend(
            intent = Intent.REST,   // no REST cards in fake catalog
            profile = UserProfile.Empty,
            catalog = fakeCatalog(),
            dismissed = emptySet()
        )
        assertTrue(result.ordered.isEmpty())
    }

    @Test
    fun `single-candidate case yields just that card`() {
        val engine = makeEngine()
        val result = engine.recommend(
            intent = Intent.LUNCH,
            profile = UserProfile.Empty,
            catalog = listOf(fakeCatalog().first()),
            dismissed = emptySet()
        )
        assertEquals(1, result.ordered.size)
        assertEquals("lunch_01", result.ordered.first().id)
    }

    @Test
    fun `biased profile keeps top exploitation at position 0`() {
        // Strongly bias toward coffee → lunch_01 must be position 0 in 1000 runs.
        val profile = UserProfile(
            weights = mapOf("coffee" to 10f),
            totalEvents = 1,
            updatedMs = 0L
        )
        val engine = makeEngine(seed = 42L)
        var firstWrong = 0
        var exploreVariety = 0
        val exploreSet = mutableSetOf<String>()
        repeat(1000) {
            val r = engine.recommend(Intent.LUNCH, profile, fakeCatalog(), emptySet())
            if (r.ordered.firstOrNull()?.id != "lunch_01") firstWrong += 1
            r.ordered.lastOrNull()?.id?.let { if (exploreSet.add(it)) exploreVariety += 1 }
        }
        assertEquals(0, firstWrong)
        // At least 2 distinct cards ever appear as the exploration (bottom) slot across 1000 runs.
        assertTrue("expected variety in explore slot; got $exploreVariety distinct", exploreVariety >= 2)
    }

    @Test
    fun `dismissed cards excluded`() {
        val engine = makeEngine()
        val result = engine.recommend(
            intent = Intent.LUNCH,
            profile = UserProfile.Empty,
            catalog = fakeCatalog(),
            dismissed = setOf("lunch_01")
        )
        assertTrue(result.ordered.none { it.id == "lunch_01" })
        assertNotNull(result.ordered.firstOrNull())
    }
}
