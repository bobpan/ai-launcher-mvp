package com.bobpan.ailauncher.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bobpan.ailauncher.data.db.entity.CachedCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CachedCardEntity>)

    @Query("SELECT COUNT(*) FROM cached_cards")
    suspend fun count(): Int

    @Query("SELECT * FROM cached_cards ORDER BY seed_order ASC")
    fun observeAll(): Flow<List<CachedCardEntity>>

    @Query("SELECT * FROM cached_cards WHERE intent = :intent ORDER BY seed_order ASC")
    fun observeByIntent(intent: String): Flow<List<CachedCardEntity>>

    @Query("SELECT * FROM cached_cards WHERE intent = :intent ORDER BY seed_order ASC")
    suspend fun byIntent(intent: String): List<CachedCardEntity>

    @Query("SELECT * FROM cached_cards ORDER BY seed_order ASC")
    suspend fun all(): List<CachedCardEntity>
}
