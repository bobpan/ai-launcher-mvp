package com.bobpan.ailauncher.data.repo

import com.bobpan.ailauncher.data.db.entity.UserProfileEntry
import com.bobpan.ailauncher.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile>
    fun observeTopTags(limit: Int = 10): Flow<List<UserProfileEntry>>
    suspend fun snapshot(): UserProfile
}
