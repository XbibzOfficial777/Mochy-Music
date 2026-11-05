package com.mochyy.mochymusic.core.updater

/** Every build of this branch must avoid offering an upstream-signed APK. */
internal fun isXbibzVersion(version: String): Boolean = version.contains("-xbibz.", ignoreCase = true)

/** Only the three published GMS flavors may offer our signed fork assets. */
internal fun isXbibzRelease(
    applicationId: String,
    architecture: String,
    castAvailable: Boolean,
    version: String,
): Boolean = applicationId == "com.mochyy.mochymusic" &&
    architecture in setOf("arm64", "armeabi", "universal") && castAvailable &&
    isXbibzVersion(version)

/** Match the known fork GMS asset naming convention without cross-ABI fallbacks. */
internal fun isCompatibleXbibzAsset(architecture: String, fileName: String): Boolean {
    val pattern = when (architecture) {
        // Accept older -arm64-gms.apk releases as well as the explicit -arm64-v8a form.
        "arm64" -> Regex("""(?:^|[-_])arm64(?:-v8a)?[-_]gms\.apk$""", RegexOption.IGNORE_CASE)
        "armeabi" -> Regex("""(?:^|[-_])(?:armeabi(?:-v7a)?|armv7a?)[-_]gms\.apk$""", RegexOption.IGNORE_CASE)
        "universal" -> Regex("""(?:^|[-_])universal[-_]gms\.apk$""", RegexOption.IGNORE_CASE)
        else -> return false
    }
    return pattern.containsMatchIn(fileName)
}
