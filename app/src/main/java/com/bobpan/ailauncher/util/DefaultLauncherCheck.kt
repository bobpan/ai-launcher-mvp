package com.bobpan.ailauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * PackageManager helper for FR-17 default-launcher detection.
 */
object DefaultLauncherCheck {
    fun isDefault(ctx: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == ctx.packageName
    }
}
