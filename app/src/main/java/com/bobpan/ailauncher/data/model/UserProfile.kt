package com.bobpan.ailauncher.data.model

import androidx.compose.runtime.Immutable

/**
 * Snapshot of user profile tag-weight map consumed by the engine (FR-10).
 */
@Immutable
data class UserProfile(
    val weights:     Map<String, Float>,
    val totalEvents: Int,
    val updatedMs:   Long
) {
    fun weightOf(tag: String): Float = weights[tag] ?: 0f

    companion object {
        val Empty = UserProfile(weights = emptyMap(), totalEvents = 0, updatedMs = 0L)
    }
}
