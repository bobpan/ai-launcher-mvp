package com.bobpan.ailauncher.data.repo

import com.bobpan.ailauncher.data.db.entity.FeedbackEventEntity
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.Signal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FeedbackRepository {
    val isHealthy: StateFlow<Boolean>

    fun observeCount(): Flow<Int>
    fun observeEvents(): Flow<List<FeedbackEventEntity>>

    /**
     * Atomic: persist the signal event + distribute its weight across the card's tags
     * per FR-10 (delta = event.weight / card.tags.size). Returns insert id, or null
     * if Room is unhealthy and the write was skipped.
     */
    suspend fun record(signal: Signal, card: Card): Long?

    /** FR-12 reset: DELETE from feedback_events + user_profile in one txn. */
    suspend fun clearAll()
}
