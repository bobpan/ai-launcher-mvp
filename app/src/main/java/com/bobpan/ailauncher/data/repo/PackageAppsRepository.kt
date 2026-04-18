package com.bobpan.ailauncher.data.repo

import android.graphics.drawable.Drawable
import com.bobpan.ailauncher.data.model.AppInfo

interface PackageAppsRepository {
    suspend fun launchableApps(): List<AppInfo>
    suspend fun loadIcon(packageName: String): Drawable?
    suspend fun launchIntentFor(packageName: String): android.content.Intent?
}
