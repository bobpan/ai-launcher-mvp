package com.bobpan.ailauncher

import android.app.Application
import android.content.Context
import android.util.Log
import com.bobpan.ailauncher.data.seed.SeedInstaller
import com.bobpan.ailauncher.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LauncherApp : Application() {

    @Inject lateinit var seedInstaller: SeedInstaller

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Install crash handler as EARLY as possible.
        CrashLogger.install(base)
        CrashLogger.crumb("LauncherApp.attachBaseContext done")
    }

    override fun onCreate() {
        CrashLogger.crumb("LauncherApp.onCreate begin (pre-super)")
        super.onCreate()
        CrashLogger.crumb("LauncherApp.onCreate post-super (Hilt injected: ${::seedInstaller.isInitialized})")
        Log.i("LauncherApp", "onCreate: Hilt injection state = ${::seedInstaller.isInitialized}")

        val coroutineCrash = CoroutineExceptionHandler { _, t ->
            CrashLogger.logNonFatal(this, "SeedInstaller-coroutine", t)
        }
        CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineCrash).launch {
            try {
                CrashLogger.crumb("SeedInstaller.installIfEmpty begin")
                seedInstaller.installIfEmpty()
                CrashLogger.crumb("SeedInstaller.installIfEmpty done")
            } catch (t: Throwable) {
                CrashLogger.logNonFatal(this@LauncherApp, "SeedInstaller", t)
            }
        }

        CrashLogger.crumb("LauncherApp.onCreate end")
    }
}
