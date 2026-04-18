package com.bobpan.ailauncher

import android.app.Application
import com.bobpan.ailauncher.data.seed.SeedInstaller
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LauncherApp : Application() {

    @Inject lateinit var seedInstaller: SeedInstaller

    override fun onCreate() {
        super.onCreate()
        // Fire-and-forget seed installer; races the first frame, does not block it.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            seedInstaller.installIfEmpty()
        }
    }
}
