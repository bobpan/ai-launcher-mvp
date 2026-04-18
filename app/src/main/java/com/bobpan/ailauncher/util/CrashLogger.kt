package com.bobpan.ailauncher.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global crash logger — catches any uncaught exception from any thread,
 * writes a full report to the app's external-files dir (no permission needed),
 * then delegates to the default handler so the system still shows its dialog.
 *
 * Log path on device:
 *   /sdcard/Android/data/com.bobpan.ailauncher/files/crash.log          (latest)
 *   /sdcard/Android/data/com.bobpan.ailauncher/files/crashes/<ts>.log   (history)
 *
 * Users can grab it via any file manager — no root, no adb needed.
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LATEST_NAME = "crash.log"
    private const val DIR_NAME = "crashes"
    private const val MAX_HISTORY = 20

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(appContext, thread, throwable)
            } catch (writeErr: Throwable) {
                // Never let logger swallow the original crash
                Log.e(TAG, "Failed to write crash report", writeErr)
            }
            // Let default handler terminate the process / show dialog
            previous?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "Installed; crashes will be written to ${externalDir(appContext)}")
    }

    /** Also call this from non-fatal error paths you want recorded. */
    fun logNonFatal(context: Context, tag: String, throwable: Throwable) {
        try {
            writeReport(context.applicationContext, Thread.currentThread(), throwable, nonFatalTag = tag)
        } catch (t: Throwable) {
            Log.e(TAG, "logNonFatal failed", t)
        }
    }

    private fun externalDir(context: Context): File? =
        context.getExternalFilesDir(null) ?: context.filesDir

    private fun writeReport(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        nonFatalTag: String? = null
    ) {
        val dir = externalDir(context) ?: return
        if (!dir.exists()) dir.mkdirs()
        val historyDir = File(dir, DIR_NAME).also { if (!it.exists()) it.mkdirs() }

        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val tsPretty = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val report = buildReport(context, thread, throwable, tsPretty, nonFatalTag)

        // Latest (overwritten)
        val latest = File(dir, LATEST_NAME)
        latest.writeText(report)

        // History copy
        val historical = File(historyDir, "$ts${if (nonFatalTag != null) "_nonfatal" else ""}.log")
        historical.writeText(report)

        // Rotate — keep newest MAX_HISTORY
        historyDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_HISTORY)
            ?.forEach { it.delete() }

        Log.e(TAG, "Crash report written: ${latest.absolutePath}")
    }

    private fun buildReport(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        timestamp: String,
        nonFatalTag: String?
    ): String {
        val sw = java.io.StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("==================== AI Launcher Crash Report ====================")
            pw.println("Time:        $timestamp")
            pw.println("Kind:        ${if (nonFatalTag != null) "NON-FATAL ($nonFatalTag)" else "FATAL"}")
            pw.println("Thread:      ${thread.name} (id=${thread.id})")
            pw.println("Package:     ${context.packageName}")
            pw.println("Android:     ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            pw.println("Device:      ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            pw.println("Brand:       ${Build.BRAND}")
            pw.println("Product:     ${Build.PRODUCT}")
            pw.println("ABI:         ${Build.SUPPORTED_ABIS.joinToString(",")}")
            pw.println("Fingerprint: ${Build.FINGERPRINT}")
            try {
                val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                pw.println("App version: ${pi.versionName} (code ${pi.longVersionCode})")
            } catch (_: Throwable) {
            }
            pw.println("------------------------------------------------------------------")
            pw.println("Exception:")
            throwable.printStackTrace(pw)
            pw.println("------------------------------------------------------------------")
            var cause: Throwable? = throwable.cause
            var depth = 1
            while (cause != null && depth < 8) {
                pw.println("Caused by [$depth]:")
                cause.printStackTrace(pw)
                pw.println("------------------------------------------------------------------")
                cause = cause.cause
                depth++
            }
            pw.println("End of report")
        }
        return sw.toString()
    }
}
