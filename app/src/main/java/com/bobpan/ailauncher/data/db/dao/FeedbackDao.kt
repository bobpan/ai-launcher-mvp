package com.bobpan.ailauncher.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bobpan.ailauncher.data.db.entity.FeedbackEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: FeedbackEventEntity): Long

    @Query("SELECT COUNT(*) FROM feedback_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM feedback_events")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM feedback_events ORDER BY timestamp_ms DESC")
    fun observeAll(): Flow<List<FeedbackEventEntity>>

    @Query("SELECT * FROM feedback_events ORDER BY timestamp_ms DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<FeedbackEventEntity>

    @Query("DELETE FROM feedback_events")
    suspend fun clear()
}
