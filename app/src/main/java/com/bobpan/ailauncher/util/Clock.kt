package com.bobpan.ailauncher.util

/**
 * Narrow Clock abstraction for deterministic dwell/feedback tests (FR-07 test hook).
 * Not using kotlinx-datetime to keep APK size under NFR-10 budget.
 */
interface Clock {
    fun nowMs(): Long

    object System : Clock {
        override fun nowMs(): Long = java.lang.System.currentTimeMillis()
    }
}
