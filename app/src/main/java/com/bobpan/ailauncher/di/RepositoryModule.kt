package com.bobpan.ailauncher.di

import com.bobpan.ailauncher.data.repo.CardRepository
import com.bobpan.ailauncher.data.repo.FeedbackRepository
import com.bobpan.ailauncher.data.repo.PackageAppsRepository
import com.bobpan.ailauncher.data.repo.ProfileRepository
import com.bobpan.ailauncher.data.repo.impl.CardRepositoryImpl
import com.bobpan.ailauncher.data.repo.impl.FeedbackRepositoryImpl
import com.bobpan.ailauncher.data.repo.impl.PackageAppsRepositoryImpl
import com.bobpan.ailauncher.data.repo.impl.ProfileRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindFeedback(impl: FeedbackRepositoryImpl): FeedbackRepository

    @Binds @Singleton
    abstract fun bindCards(impl: CardRepositoryImpl): CardRepository

    @Binds @Singleton
    abstract fun bindProfile(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun bindApps(impl: PackageAppsRepositoryImpl): PackageAppsRepository
}
