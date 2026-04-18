package com.bobpan.ailauncher.data.repo.impl

import com.bobpan.ailauncher.data.db.dao.CardDao
import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.data.repo.CardRepository
import com.bobpan.ailauncher.data.seed.SeedInstaller
import com.bobpan.ailauncher.data.seed.toDomain
import com.bobpan.ailauncher.util.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    private val cardDao: CardDao,
    private val seedInstaller: SeedInstaller,
    private val json: Json,
    private val dispatchers: AppDispatchers
) : CardRepository {

    override fun observeAll(): Flow<List<Card>> =
        cardDao.observeAll()
            .onStart { seedInstaller.installIfEmpty() }
            .map { list -> list.map { it.toDomain(json) } }
            .flowOn(dispatchers.io)

    override fun observeByIntent(intent: Intent): Flow<List<Card>> =
        cardDao.observeByIntent(intent.name)
            .onStart { seedInstaller.installIfEmpty() }
            .map { list -> list.map { it.toDomain(json) } }
            .flowOn(dispatchers.io)

    override suspend fun byIntent(intent: Intent): List<Card> = withContext(dispatchers.io) {
        seedInstaller.installIfEmpty()
        cardDao.byIntent(intent.name).map { it.toDomain(json) }
    }

    override suspend fun all(): List<Card> = withContext(dispatchers.io) {
        seedInstaller.installIfEmpty()
        cardDao.all().map { it.toDomain(json) }
    }
}
