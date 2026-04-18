package com.bobpan.ailauncher

import android.app.Application
import android.content.Context
import com.bobpan.ailauncher.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp

/**
 * Minimal Application — mirrors reference launcher's proven pattern.
 *
 * Intentionally NO @Inject fields here. Any class-level @Inject in Application
 * forces Hilt to materialize a large dependency graph before Application.onCreate
 * returns; a failure there would crash before CrashLogger's UncaughtExceptionHandler
 * is wired up.
 *
 * SeedInstaller call has been moved to HomeViewModel.init — it will run lazily
 * the first time Home is composed.
 */
@HiltAndroidApp
class LauncherApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Install crash handler as EARLY as possible.
        CrashLogger.install(base)
        CrashLogger.crumb("LauncherApp.attachBaseContext done")
    }

    override fun onCreate() {
        CrashLogger.crumb("LauncherApp.onCreate begin (pre-super)")
        super.onCreate()
        CrashLogger.crumb("LauncherApp.onCreate end (post-super)")
    }
}
