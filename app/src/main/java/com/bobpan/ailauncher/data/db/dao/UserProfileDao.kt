package com.bobpan.ailauncher.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.bobpan.ailauncher.data.db.entity.UserProfileEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    /** FR-10 transactional upsert. */
    @Query(
        """
        INSERT INTO user_profile (tag, weight, updated_ms)
        VALUES (:tag, :delta, :now)
        ON CONFLICT(tag) DO UPDATE SET
            weight = weight + :delta,
            updated_ms = :now
        """
    )
    suspend fun upsertDelta(tag: String, delta: Float, now: Long)

    @Query("SELECT * FROM user_profile")
    fun observeAll(): Flow<List<UserProfileEntry>>

    @Query("SELECT * FROM user_profile ORDER BY ABS(weight) DESC LIMIT :limit")
    fun observeTopByAbs(limit: Int): Flow<List<UserProfileEntry>>

    @Query("SELECT * FROM user_profile ORDER BY weight DESC LIMIT :limit")
    suspend fun topPositive(limit: Int): List<UserProfileEntry>

    @Query("SELECT * FROM user_profile")
    suspend fun snapshot(): List<UserProfileEntry>

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}
