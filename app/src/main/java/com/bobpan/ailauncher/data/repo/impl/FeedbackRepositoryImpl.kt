package com.bobpan.ailauncher.data.repo.impl

import android.util.Log
import androidx.room.withTransaction
import com.bobpan.ailauncher.BuildConfig
import com.bobpan.ailauncher.data.db.LauncherDatabase
import com.bobpan.ailauncher.data.db.dao.FeedbackDao
import com.bobpan.ailauncher.data.db.dao.UserProfileDao
import com.bobpan.ailauncher.data.db.entity.FeedbackEventEntity
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.Signal
import com.bobpan.ailauncher.data.repo.FeedbackRepository
import com.bobpan.ailauncher.util.AppDispatchers
import com.bobpan.ailauncher.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepositoryImpl @Inject constructor(
    private val db: LauncherDatabase,
    private val feedbackDao: FeedbackDao,
    private val profileDao: UserProfileDao,
    private val dispatchers: AppDispatchers,
    private val clock: Clock
) : FeedbackRepository {

    private val _isHealthy = MutableStateFlow(true)
    override val isHealthy: StateFlow<Boolean> = _isHealthy.asStateFlow()

    override fun observeCount(): Flow<Int> = feedbackDao.countFlow()

    override fun observeEvents(): Flow<List<FeedbackEventEntity>> = feedbackDao.observeAll()

    override suspend fun record(signal: Signal, card: Card): Long? = withContext(dispatchers.io) {
        if (!_isHealthy.value) return@withContext null
        val tagCount = card.tags.size.coerceAtLeast(1)
        val delta = signal.weight / tagCount
        val now = clock.nowMs()

        val entity = FeedbackEventEntity(
            cardId      = card.id,
            intent      = card.intent.name,
            signal      = signal.persistedType.name,
            weight      = signal.weight,
            timestampMs = signal.timestampMs
        )

        val insertedId = runCatching {
            db.withTransaction {
                val id = feedbackDao.insert(entity)
                // FR-10: only profile-modifying signals alter tags; weight=0f means no-op but
                // we still short-circuit for clarity and to avoid writing zeros.
                if (signal.weight != 0f) {
                    card.tags.forEach { tag ->
                        profileDao.upsertDelta(tag, delta, now)
                    }
                }
                id
            }
        }.onFailure { t ->
            Log.e(TAG, "Failed to record feedback", t)
            _isHealthy.value = false
        }.getOrNull()

        if (BuildConfig.DEBUG && insertedId != null) {
            val top3 = runCatching { profileDao.topPositive(3) }.getOrDefault(emptyList())
            Log.d(
                "FLYWHEEL",
                "evt=${signal.persistedType} card=${card.id} w=${signal.weight} " +
                    "top3=${top3.joinToString { "${it.tag}=${"%.2f".format(it.weight)}" }}"
            )
        }

        insertedId
    }

    override suspend fun clearAll() = withContext(dispatchers.io) {
        runCatching {
            db.withTransaction {
                feedbackDao.clear()
                profileDao.clear()
            }
        }.onFailure { t ->
            Log.e(TAG, "Failed to clear feedback tables", t)
            _isHealthy.value = false
        }
        Unit
    }

    private companion object {
        const val TAG = "FeedbackRepo"
    }
}
