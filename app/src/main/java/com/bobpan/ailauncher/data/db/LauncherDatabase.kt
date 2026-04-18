package com.bobpan.ailauncher.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bobpan.ailauncher.data.db.dao.CardDao
import com.bobpan.ailauncher.data.db.dao.FeedbackDao
import com.bobpan.ailauncher.data.db.dao.UserProfileDao
import com.bobpan.ailauncher.data.db.entity.CachedCardEntity
import com.bobpan.ailauncher.data.db.entity.FeedbackEventEntity
import com.bobpan.ailauncher.data.db.entity.UserProfileEntry

@Database(
    entities = [
        FeedbackEventEntity::class,
        UserProfileEntry::class,
        CachedCardEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LauncherDatabase : RoomDatabase() {

    abstract fun feedbackDao():    FeedbackDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun cardDao():        CardDao

    companion object {
        const val NAME = "launcher.db"
    }
}
