package com.bobpan.ailauncher.data.model

/**
 * Discrete confidence classification for the Hero ConfidenceBar.
 * [fraction] is the visual fill level reported to the bar.
 */
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
