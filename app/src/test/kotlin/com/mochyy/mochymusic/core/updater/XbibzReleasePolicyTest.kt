package com.mochyy.mochymusic.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XbibzReleasePolicyTest {
    @Test
    fun everyForkFlavorAvoidsTheOfficialUpdateChannel() {
        assertTrue(isXbibzVersion("8.3.2-xbibz.7"))
        assertTrue(isXbibzVersion("8.3.2-XBIBZ.7"))
        assertFalse(isXbibzVersion("8.3.2 build#1226"))
        // Only published GMS release builds may offer the signed fork assets.
        assertFalse(isXbibzRelease("com.mochyy.mochymusic", "universal", false, "8.3.2-xbibz.7"))
        assertFalse(isXbibzRelease("com.mochyy.mochymusic.debug", "universal", true, "8.3.2-xbibz.7"))
    }

    @Test
    fun allThreeGmsReleasesUseTheForkChannel() {
        for (abi in listOf("arm64", "armeabi", "universal")) {
            assertTrue(isXbibzRelease("com.mochyy.mochymusic", abi, true, "8.2.3-xbibz.5"))
        }
        assertFalse(isXbibzRelease("com.mochyy.mochymusic.debug", "armeabi", true, "8.2.3-xbibz.5"))
        assertFalse(isXbibzRelease("com.mochyy.mochymusic", "x86", true, "8.2.3-xbibz.6"))
        assertFalse(isXbibzRelease("com.mochyy.mochymusic", "armeabi", false, "8.2.3-xbibz.5"))
        assertFalse(isXbibzRelease("com.mochyy.mochymusic", "armeabi", true, "8.2.3"))
    }

    @Test
    fun matchesOnlyTheInstalledArchitecture() {
        val oldArm64 = "Mochy-Music-v8.2.3-xbibz.4-arm64-gms.apk"
        val arm64 = "Mochy-Music-v8.2.3-xbibz.5-arm64-v8a-gms.apk"
        val arm32 = "Mochy-Music-v8.2.3-xbibz.5-armeabi-v7a-gms.apk"
        val universal = "Mochy-Music-v8.2.3-xbibz.6-universal-gms.apk"
        val decoys = listOf("SHA256SUMS.txt", "app-universal-foss.apk", "app-arm64-foss.apk", "app-universal-gms.apk.sig")
        val assets = listOf(oldArm64, arm64, arm32, universal) + decoys
        assertEquals(listOf(oldArm64, arm64), assets.filter { isCompatibleXbibzAsset("arm64", it) })
        assertEquals(listOf(arm32), assets.filter { isCompatibleXbibzAsset("armeabi", it) })
        assertEquals(listOf(universal), assets.filter { isCompatibleXbibzAsset("universal", it) })
        assertTrue(isCompatibleXbibzAsset("universal", "APP-UNIVERSAL-GMS.APK"))
        assertEquals(emptyList<String>(), assets.filter { isCompatibleXbibzAsset("x86", it) })
        assertTrue(isCompatibleXbibzAsset("ARMEABI".lowercase(), "APP-ARMEABI-V7A-GMS.APK"))
    }

    @Test
    fun deviceAbisPickTheMatchingSplitNotTheInstalledFlavor() {
        assertEquals("arm64", preferredXbibzArchitecture(arrayOf("arm64-v8a", "armeabi-v7a"), "universal"))
        assertEquals("armeabi", preferredXbibzArchitecture(arrayOf("armeabi-v7a"), "universal"))
        assertEquals("universal", preferredXbibzArchitecture(arrayOf("x86_64", "x86"), "universal"))
        assertEquals("arm64", preferredXbibzArchitecture(arrayOf("arm64-v8a"), "armeabi"))
    }

    @Test
    fun offeredAssetsAllowUniversalFallbackNeverTheOtherArm() {
        val arm64 = "Mochy-Music-v8.3.3-xbibz.13-arm64-v8a-gms.apk"
        val arm32 = "Mochy-Music-v8.3.3-xbibz.13-armeabi-v7a-gms.apk"
        val universal = "Mochy-Music-v8.3.3-xbibz.13-universal-gms.apk"
        assertTrue(isOfferedXbibzAsset("arm64", arm64))
        assertTrue(isOfferedXbibzAsset("arm64", universal))
        assertFalse(isOfferedXbibzAsset("arm64", arm32))
        assertTrue(isOfferedXbibzAsset("armeabi", arm32))
        assertTrue(isOfferedXbibzAsset("armeabi", universal))
        assertFalse(isOfferedXbibzAsset("armeabi", arm64))
    }

    @Test
    fun preReleaseToggleKeepsStableTagsAndSkipsDesktop() {
        assertTrue(isEligibleXbibzUpdateTag("v8.3.3-xbibz.14", prerelease = false, includePreRelease = false))
        assertFalse(isEligibleXbibzUpdateTag("v8.3.3-xbibz.14", prerelease = true, includePreRelease = false))
        assertFalse(isEligibleXbibzUpdateTag("v8.3.3-xbibz.15-pre.1", prerelease = true, includePreRelease = false))
        assertTrue(isEligibleXbibzUpdateTag("v8.3.3-xbibz.14", prerelease = true, includePreRelease = true))
        assertTrue(isEligibleXbibzUpdateTag("v8.3.3-xbibz.15-pre.1", prerelease = true, includePreRelease = true))
        assertTrue(isEligibleXbibzUpdateTag("v8.3.3-xbibz.15-pre", prerelease = true, includePreRelease = true))
        assertFalse(isEligibleXbibzUpdateTag("v8.3.3-xbibz.desktop-pre.1", prerelease = true, includePreRelease = true))
        assertFalse(isEligibleXbibzUpdateTag("v8.3.2", prerelease = false, includePreRelease = true))
    }

    @Test
    fun missingAbiDoesNotFallBackToAnIncompatibleApk() {
        assertFalse(isCompatibleXbibzAsset("armeabi", "Mochy-Music-v8.2.3-xbibz.4-arm64-gms.apk"))
        assertFalse(isCompatibleXbibzAsset("arm64", "Mochy-Music-v8.2.3-xbibz.5-armeabi-v7a-gms.apk"))
        assertFalse(isCompatibleXbibzAsset("arm64", "app-arm64-v8a-gms.apk.sig"))
        assertFalse(isCompatibleXbibzAsset("universal", "Mochy-Music-v8.2.3-xbibz.5-arm64-v8a-gms.apk"))
        assertFalse(isCompatibleXbibzAsset("universal", "Mochy-Music-v8.2.3-xbibz.5-armeabi-v7a-gms.apk"))
        assertFalse(isCompatibleXbibzAsset("arm64", "Mochy-Music-v8.2.3-xbibz.6-universal-gms.apk"))
        assertFalse(isCompatibleXbibzAsset("armeabi", "Mochy-Music-v8.2.3-xbibz.6-universal-gms.apk"))
    }
}
