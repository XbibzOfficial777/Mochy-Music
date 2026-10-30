package com.mochyy.mochymusic.core.updater

/** The fork uses a different signing key; neither ABI may offer an upstream APK as an update. */
internal fun isXbibzRelease(
    applicationId: String,
    architecture: String,
    castAvailable: Boolean,
    version: String,
): Boolean = applicationId == "com.mochyy.mochymusic" &&
    architecture in setOf("arm64", "armeabi") && castAvailable &&
    version.contains("-xbibz.", ignoreCase = true)

/** Match the known fork GMS asset naming convention without cross-ABI fallbacks. */
internal fun isCompatibleXbibzAsset(architecture: String, fileName: String): Boolean {
    val pattern = when (architecture) {
        // Accept older -arm64-gms.apk releases as well as the explicit -arm64-v8a form.
        "arm64" -> Regex("""(?:^|[-_])arm64(?:-v8a)?[-_]gms\.apk$""", RegexOption.IGNORE_CASE)
        "armeabi" -> Regex("""(?:^|[-_])(?:armeabi(?:-v7a)?|armv7a?)[-_]gms\.apk$""", RegexOption.IGNORE_CASE)
        else -> return false
    }
    return pattern.containsMatchIn(fileName)
}
