package com.mochyy.mochymusic.core.updater

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import com.mochyy.mochymusic.BuildConfig

/** Launcher/recents/in-app name gets " (Beta)" on the pre-release channel or a -pre version. */
fun isPreReleaseBranding(versionName: String, includePreRelease: Boolean): Boolean =
    includePreRelease || versionName.contains("-pre", ignoreCase = true)

fun appDisplayName(base: String, versionName: String = BuildConfig.VERSION_NAME, includePreRelease: Boolean = includePreReleaseUpdatesEnabled()): String {
    if (!isPreReleaseBranding(versionName, includePreRelease)) return base
    return if (base.contains("(Beta)", ignoreCase = true)) base else "$base (Beta)"
}

fun Activity.applyAppDisplayName(name: String) {
    title = name
    if (Build.VERSION.SDK_INT >= 33) {
        setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(name).build())
    } else {
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(name))
    }
}
