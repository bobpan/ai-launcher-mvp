package com.bobpan.ailauncher.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global crash logger — catches any uncaught exception from any thread,
 * writes the report to MULTIPLE locations for maximum user-accessibility.
 *
 * Writes in order (stops at first success but tries all for redundancy):
 *  1. /sdcard/Download/ailauncher_crash.log    — most-visible path on every phone
 *  2. /sdcard/Android/data/<pkg>/files/crash.log   — private external dir
 *  3. filesDir/crash.log                        — internal storage fallback
 *
 * Also records a breadcrumb of startup checkpoints so we know which phase
 * crashed (attachBaseContext → onCreate → Hilt-inject → MainActivity.onCreate).
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val DOWNLOAD_NAME = "ailauncher_crash.log"
    private const val LATEST_NAME = "crash.log"
    private const val HISTORY_DIR = "crashes"
    private const val MAX_HISTORY = 20

    @Volatile private var installed = false

    private val breadcrumbs = mutableListOf<String>()

    fun install(context: Context) {
        if (installed) return
        installed = true

        crumb("CrashLogger.install")

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeEverywhere(appContext, thread, throwable, nonFatalTag = null)
            } catch (writeErr: Throwable) {
                Log.e(TAG, "writeEverywhere failed", writeErr)
            }
            previous?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "Installed")
    }

    /** Record a startup-phase breadcrumb so we know which step reached. */
    fun crumb(tag: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        synchronized(breadcrumbs) {
            breadcrumbs.add("[$ts] $tag")
            if (breadcrumbs.size > 50) breadcrumbs.removeAt(0)
        }
        Log.i(TAG, "crumb: $tag")
    }

    fun logNonFatal(context: Context, tag: String, throwable: Throwable) {
        try {
            writeEverywhere(context.applicationContext, Thread.currentThread(), throwable, nonFatalTag = tag)
        } catch (t: Throwable) {
            Log.e(TAG, "logNonFatal failed", t)
        }
    }

    private fun writeEverywhere(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        nonFatalTag: String?
    ) {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val tsPretty = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val report = buildReport(context, thread, throwable, tsPretty, nonFatalTag)

        // 1) Public Downloads — most discoverable path
        val dlOk = runCatching { writeToDownloads(context, report, ts, nonFatalTag) }
            .onFailure { Log.e(TAG, "downloads write failed", it) }
            .getOrDefault(false)

        // 2) External files dir (no permission)
        val extOk = runCatching {
            val dir = context.getExternalFilesDir(null) ?: return@runCatching false
            if (!dir.exists()) dir.mkdirs()
            File(dir, LATEST_NAME).writeText(report)
            val histDir = File(dir, HISTORY_DIR).also { if (!it.exists()) it.mkdirs() }
            File(histDir, "$ts.log").writeText(report)
            histDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_HISTORY)
                ?.forEach { it.delete() }
            true
        }.onFailure { Log.e(TAG, "external-files write failed", it) }.getOrDefault(false)

        // 3) Internal files dir (always works)
        val intOk = runCatching {
            File(context.filesDir, LATEST_NAME).writeText(report)
            true
        }.onFailure { Log.e(TAG, "internal write failed", it) }.getOrDefault(false)

        Log.e(TAG, "Report written: downloads=$dlOk external=$extOk internal=$intOk")
    }

    private fun writeToDownloads(context: Context, report: String, ts: String, nonFatalTag: String?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: use MediaStore (no permission needed for own files)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, DOWNLOAD_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            // Delete prior same-named row then insert (simplest overwrite)
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            try {
                resolver.delete(collection, "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(DOWNLOAD_NAME))
            } catch (_: Throwable) { /* ignore */ }
            val uri = resolver.insert(collection, values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) } ?: return false
            // History with timestamped name
            val histValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "ailauncher_crash_$ts.log")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val histUri = resolver.insert(collection, histValues)
            histUri?.let { resolver.openOutputStream(it)?.use { os -> os.write(report.toByteArray()) } }
            return true
        } else {
            // Pre-Android 10: write directly to Downloads dir (requires WRITE_EXTERNAL_STORAGE)
            val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dl.exists()) dl.mkdirs()
            File(dl, DOWNLOAD_NAME).writeText(report)
            File(dl, "ailauncher_crash_$ts.log").writeText(report)
            return true
        }
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
            } catch (_: Throwable) { /* ignore */ }
            pw.println("------------------------------------------------------------------")
            pw.println("Startup breadcrumbs (last phases reached before crash):")
            synchronized(breadcrumbs) {
                if (breadcrumbs.isEmpty()) pw.println("  (none — crash before any checkpoint)")
                else breadcrumbs.forEach { pw.println("  $it") }
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
