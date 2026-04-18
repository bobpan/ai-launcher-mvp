package com.bobpan.ailauncher.data.model

import androidx.compose.runtime.Immutable

/**
 * Metadata for one launchable app in the drawer. FR-13.
 * Icon Drawable is loaded lazily at render time via `PackageAppsRepository.loadIcon(pkg)`.
 */
@Immutable
data class AppInfo(
    val packageName: String,
    val label:       String,
    val iconKey:     String   // stable key for Compose keyed effects; v0.1 == packageName
)
