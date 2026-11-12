package com.mochyy.mochymusic.core.updater

/** Every build of this branch must avoid offering an upstream-signed APK. */
internal fun isXbibzVersion(version: String): Boolean = version.contains("-xbibz.", ignoreCase = true)

private val StableForkTag = Regex("""^v\d+\.\d+\.\d+-xbibz\.\d+$""", RegexOption.IGNORE_CASE)
private val PreForkTag = Regex("""^v\d+\.\d+\.\d+-xbibz\.\d+-pre(?:\.\d+)?$""", RegexOption.IGNORE_CASE)

/**
 * Stable channel: `v8.3.3-xbibz.14` only, never a GitHub pre-release.
 * Pre-release channel: those tags plus `v8.3.3-xbibz.15-pre` / `.pre.1`, including GitHub's prerelease flag.
 * Desktop tags (`…-xbibz.desktop-pre.1`) stay out — they have no APK.
 */
internal fun isEligibleXbibzUpdateTag(
    tag: String,
    prerelease: Boolean,
    includePreRelease: Boolean,
): Boolean {
    if (includePreRelease) return StableForkTag.matches(tag) || PreForkTag.matches(tag)
    return !prerelease && StableForkTag.matches(tag)
}

/** Only the three published GMS flavors may offer our signed fork assets. */
internal fun isXbibzRelease(
    applicationId: String,
    architecture: String,
    castAvailable: Boolean,
    version: String,
): Boolean = applicationId == "com.mochyy.mochymusic" &&
    architecture in setOf("arm64", "armeabi", "universal") && castAvailable &&
    isXbibzVersion(version)

/**
 * ABI of the phone, not of the APK currently installed. A 64-bit phone that happened to
 * install the universal build should still be offered the arm64 APK next time.
 */
internal fun preferredXbibzArchitecture(
    supportedAbis: Array<out String>,
    buildArchitecture: String,
): String {
    val abis = supportedAbis.map { it.lowercase() }
    when {
        abis.any { it == "arm64-v8a" } -> return "arm64"
        abis.any { it == "armeabi-v7a" || it == "armeabi" } -> return "armeabi"
    }
    return when (buildArchitecture.lowercase()) {
        "arm64", "armeabi", "universal" -> buildArchitecture.lowercase()
        else -> "universal"
    }
}

/** Exact ABI match, plus universal as a last-resort file that every device can install. */
internal fun isOfferedXbibzAsset(deviceArchitecture: String, fileName: String): Boolean =
    isCompatibleXbibzAsset(deviceArchitecture, fileName) ||
        isCompatibleXbibzAsset("universal", fileName)

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
