package com.bobpan.ailauncher.data.repo.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.bobpan.ailauncher.data.model.AppInfo
import com.bobpan.ailauncher.data.repo.PackageAppsRepository
import com.bobpan.ailauncher.util.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dispatchers: AppDispatchers
) : PackageAppsRepository {

    override suspend fun launchableApps(): List<AppInfo> = withContext(dispatchers.io) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching {
            pm.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())

        resolved
            .asSequence()
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                AppInfo(
                    packageName = pkg,
                    label       = ri.loadLabel(pm).toString(),
                    iconKey     = pkg
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override suspend fun loadIcon(packageName: String): Drawable? = withContext(dispatchers.io) {
        runCatching {
            ctx.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }

    override suspend fun launchIntentFor(packageName: String): Intent? = withContext(dispatchers.io) {
        ctx.packageManager.getLaunchIntentForPackage(packageName)
    }
}
