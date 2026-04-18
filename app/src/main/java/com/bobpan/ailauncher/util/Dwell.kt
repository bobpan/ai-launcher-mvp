package com.bobpan.ailauncher.util

/**
 * FR-07 dwell tracker. Owned by the HomeScreen composable; receives visibility fractions from
 * DiscoverCard's `onGloballyPositioned` throttled callback and emits one DWELL event per
 * Appearance that crosses [thresholdMs]. Paused by UI (sheet/drawer/lifecycle).
 */
class DwellController(
    private val clock: Clock,
    private val thresholdMs: Long = 2_000L,
    private val onDwell: (cardId: String, durationMs: Long) -> Unit
) {
    private data class Appearance(
        var startedAtMs: Long,
        var accruedMs:   Long,
        var isActive:    Boolean,
        var emitted:     Boolean
    )

    private val appearances = mutableMapOf<String, Appearance>()
    private var paused: Boolean = false

    /** Called by cards with 0..1 visibility fraction at most every 100ms (FR-07.1). */
    fun onVisibilityChange(cardId: String, fraction: Float) {
        val now = clock.nowMs()
        val visible = fraction >= 0.5f
        val existing = appearances[cardId]

        if (visible) {
            if (existing == null) {
                appearances[cardId] = Appearance(
                    startedAtMs = now,
                    accruedMs   = 0L,
                    isActive    = !paused,
                    emitted     = false
                )
            } else {
                // Re-arm only if the previous Appearance already ended (isActive was reset to false
                // via onVisibilityChange fraction <0.5). If currently active, keep accruing.
                if (!existing.isActive && !existing.emitted) {
                    existing.startedAtMs = now
                    existing.accruedMs   = 0L
                    existing.isActive    = !paused
                }
            }
        } else {
            // Card left the viewport — end Appearance, reset.
            existing?.let {
                accrue(it, now)
                it.isActive = false
                if (!it.emitted) appearances.remove(cardId) else it.accruedMs = 0L
            }
        }

        appearances[cardId]?.let { app ->
            if (app.isActive) {
                accrue(app, now)
                if (!app.emitted && app.accruedMs >= thresholdMs) {
                    app.emitted = true
                    onDwell(cardId, app.accruedMs)
                }
            }
        }
    }

    /** Called when sheet/drawer/lifecycle pauses dwell (FR-07.5). */
    fun setPaused(newPaused: Boolean) {
        if (paused == newPaused) return
        val now = clock.nowMs()
        if (newPaused) {
            appearances.values.forEach { app ->
                if (app.isActive) accrue(app, now)
                app.isActive = false
            }
        } else {
            appearances.values.forEach { app ->
                if (!app.emitted) {
                    app.startedAtMs = now
                    app.isActive    = true
                }
            }
        }
        paused = newPaused
    }

    /** Ends all Appearances (intent change, profile reset). */
    fun endAll() {
        appearances.clear()
    }

    private fun accrue(app: Appearance, nowMs: Long) {
        if (!app.isActive) return
        val delta = (nowMs - app.startedAtMs).coerceAtLeast(0L)
        app.accruedMs += delta
        app.startedAtMs = nowMs
    }
}
