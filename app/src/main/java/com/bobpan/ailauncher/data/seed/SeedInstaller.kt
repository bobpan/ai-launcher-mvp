package com.bobpan.ailauncher.data.seed

import com.bobpan.ailauncher.data.db.dao.CardDao
import com.bobpan.ailauncher.util.AppDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-boot seed installer — materializes [SEED_CARDS] into the `cached_cards` table
 * if and only if the table is currently empty (FR-16). Idempotent.
 */
@Singleton
class SeedInstaller @Inject constructor(
    private val cardDao: CardDao,
    private val json: Json,
    private val dispatchers: AppDispatchers
) {
    suspend fun installIfEmpty() = withContext(dispatchers.io) {
        if (cardDao.count() == 0) {
            cardDao.insertAll(SEED_CARDS.map { it.toEntity(json) })
        }
    }
}
