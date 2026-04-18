package com.bobpan.ailauncher.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DwellControllerTest {

    private class FakeClock(var now: Long = 0L) : Clock {
        override fun nowMs(): Long = now
    }

    @Test
    fun `dwell emits once when threshold crossed`() {
        val clock = FakeClock()
        val emitted = mutableListOf<Pair<String, Long>>()
        val ctrl = DwellController(clock, thresholdMs = 2_000L) { id, ms -> emitted += id to ms }

        clock.now = 0L
        ctrl.onVisibilityChange("c1", 0.8f)     // begin
        clock.now = 1_500L
        ctrl.onVisibilityChange("c1", 0.8f)     // accrue 1500
        assertTrue(emitted.isEmpty())
        clock.now = 2_100L
        ctrl.onVisibilityChange("c1", 0.8f)     // total ~2100 -> emit
        assertEquals(1, emitted.size)
        assertEquals("c1", emitted[0].first)
        // Further visibility ticks don't re-emit within same Appearance.
        clock.now = 3_000L
        ctrl.onVisibilityChange("c1", 0.8f)
        assertEquals(1, emitted.size)
    }

    @Test
    fun `scrolling off before threshold does not emit`() {
        val clock = FakeClock()
        val emitted = mutableListOf<Pair<String, Long>>()
        val ctrl = DwellController(clock, thresholdMs = 2_000L) { id, ms -> emitted += id to ms }

        clock.now = 0L
        ctrl.onVisibilityChange("c1", 0.8f)
        clock.now = 1_000L
        ctrl.onVisibilityChange("c1", 0.1f)     // scroll off
        assertTrue(emitted.isEmpty())
    }
}
