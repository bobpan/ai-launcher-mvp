package com.bobpan.ailauncher.data.repo.impl

import com.bobpan.ailauncher.data.db.dao.FeedbackDao
import com.bobpan.ailauncher.data.db.dao.UserProfileDao
import com.bobpan.ailauncher.data.db.entity.UserProfileEntry
import com.bobpan.ailauncher.data.model.UserProfile
import com.bobpan.ailauncher.data.repo.ProfileRepository
import com.bobpan.ailauncher.util.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: UserProfileDao,
    private val feedbackDao: FeedbackDao,
    private val dispatchers: AppDispatchers
) : ProfileRepository {

    override fun observeProfile(): Flow<UserProfile> =
        combine(profileDao.observeAll(), feedbackDao.countFlow()) { entries, count ->
            UserProfile(
                weights     = entries.associate { it.tag to it.weight },
                totalEvents = count,
                updatedMs   = entries.maxOfOrNull { it.updatedMs } ?: 0L
            )
        }.flowOn(dispatchers.io)

    override fun observeTopTags(limit: Int): Flow<List<UserProfileEntry>> =
        profileDao.observeTopByAbs(limit).flowOn(dispatchers.io)

    override suspend fun snapshot(): UserProfile = withContext(dispatchers.io) {
        val entries = profileDao.snapshot()
        UserProfile(
            weights     = entries.associate { it.tag to it.weight },
            totalEvents = feedbackDao.count(),
            updatedMs   = entries.maxOfOrNull { it.updatedMs } ?: 0L
        )
    }
}
