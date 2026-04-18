package com.bobpan.ailauncher.di

import android.content.Context
import androidx.room.Room
import com.bobpan.ailauncher.data.db.Converters
import com.bobpan.ailauncher.data.db.LauncherDatabase
import com.bobpan.ailauncher.data.db.dao.CardDao
import com.bobpan.ailauncher.data.db.dao.FeedbackDao
import com.bobpan.ailauncher.data.db.dao.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
        json: Json
    ): LauncherDatabase =
        Room.databaseBuilder(ctx, LauncherDatabase::class.java, LauncherDatabase.NAME)
            .addTypeConverter(Converters(json))
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideFeedbackDao(db: LauncherDatabase): FeedbackDao    = db.feedbackDao()
    @Provides fun provideProfileDao(db: LauncherDatabase):  UserProfileDao = db.userProfileDao()
    @Provides fun provideCardDao(db: LauncherDatabase):     CardDao        = db.cardDao()
}
