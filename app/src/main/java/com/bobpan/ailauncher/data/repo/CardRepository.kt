package com.bobpan.ailauncher.data.repo

import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.Intent
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun observeAll(): Flow<List<Card>>
    fun observeByIntent(intent: Intent): Flow<List<Card>>
    suspend fun byIntent(intent: Intent): List<Card>
    suspend fun all(): List<Card>
}
