package com.bobpan.ailauncher.data.seed

import com.bobpan.ailauncher.data.model.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedCardsTest {

    @Test
    fun `exactly 16 cards`() {
        assertEquals(16, SEED_CARDS.size)
    }

    @Test
    fun `4 cards per intent`() {
        Intent.values().forEach { i ->
            val count = SEED_CARDS.count { it.intent == i }
            assertEquals("intent $i count", 4, count)
        }
    }

    @Test
    fun `all ids unique`() {
        val ids = SEED_CARDS.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun `seed order spans 0-15 uniquely`() {
        assertEquals((0..15).toSet(), SEED_CARDS.map { it.seedOrder }.toSet())
    }

    @Test
    fun `every card has at least one tag`() {
        assertTrue(SEED_CARDS.all { it.tags.isNotEmpty() })
    }

    @Test
    fun `work_01 is first in WORK declaration`() {
        val work = SEED_CARDS.filter { it.intent == Intent.WORK }.sortedBy { it.seedOrder }
        assertEquals("work_01", work.first().id)
    }
}
