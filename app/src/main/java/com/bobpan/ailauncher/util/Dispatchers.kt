package com.bobpan.ailauncher.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable dispatcher bundle so tests can swap in [kotlinx.coroutines.test.StandardTestDispatcher].
 */
data class AppDispatchers(
    val main:    CoroutineDispatcher,
    val io:      CoroutineDispatcher,
    val default: CoroutineDispatcher
) {
    companion object {
        val Default = AppDispatchers(
            main    = Dispatchers.Main.immediate,
            io      = Dispatchers.IO,
            default = Dispatchers.Default
        )
    }
}
