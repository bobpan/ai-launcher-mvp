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
        // Install crash handler as EARLY as possible — before Hilt injection,
        // before onCreate. Any exception in the subsequent startup chain
        // will end up in /sdcard/Android/data/<pkg>/files/crash.log.
        CrashLogger.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("LauncherApp", "onCreate begin")

        // Fire-and-forget seed installer — also wrap in a coroutine exception
        // handler so seed failures get captured, not silently swallowed.
        val coroutineCrash = CoroutineExceptionHandler { _, t ->
            CrashLogger.logNonFatal(this, "SeedInstaller", t)
        }
        CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineCrash).launch {
            try {
                seedInstaller.installIfEmpty()
            } catch (t: Throwable) {
                CrashLogger.logNonFatal(this@LauncherApp, "SeedInstaller", t)
            }
        }

        Log.i("LauncherApp", "onCreate done")
    }
}
