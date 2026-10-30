package com.mochyy.mochymusic.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XbibzReleasePolicyTest {
    @Test
    fun bothArmGmsReleasesUseTheForkChannel() {
        for (abi in listOf("arm64", "armeabi")) {
            assertTrue(isXbibzRelease("com.mochyy.mochymusic", abi, true, "8.2.3-xbibz.5"))
        }
        assertFalse(isXbibzRelease("com.mochyy.mochymusic.debug", "armeabi", true, "8.2.3-xbibz.5"))
        assertFalse(isXbibzRelease("com.mochyy.mochymusic", "universal", true, "8.2.3-xbibz.5"))
        assertFalse(isXbibzRelease("com.mochyy.mochymusic", "armeabi", false, "8.2.3-xbibz.5"))
        assertFalse(isXbibzRelease("com.mochyy.mochymusic", "armeabi", true, "8.2.3"))
    }

    @Test
    fun matchesOnlyTheInstalledArchitecture() {
        val oldArm64 = "Mochy-Music-v8.2.3-xbibz.4-arm64-gms.apk"
        val arm64 = "Mochy-Music-v8.2.3-xbibz.5-arm64-v8a-gms.apk"
        val arm32 = "Mochy-Music-v8.2.3-xbibz.5-armeabi-v7a-gms.apk"
        val decoys = listOf("SHA256SUMS.txt", "Mochy-Music-universal-gms.apk", "app-arm64-foss.apk")
        val assets = listOf(oldArm64, arm64, arm32) + decoys
        assertEquals(listOf(oldArm64, arm64), assets.filter { isCompatibleXbibzAsset("arm64", it) })
        assertEquals(listOf(arm32), assets.filter { isCompatibleXbibzAsset("armeabi", it) })
        assertEquals(emptyList<String>(), assets.filter { isCompatibleXbibzAsset("x86", it) })
        assertTrue(isCompatibleXbibzAsset("ARMEABI".lowercase(), "APP-ARMEABI-V7A-GMS.APK"))
    }

    @Test
    fun missingAbiDoesNotFallBackToAnIncompatibleApk() {
        assertFalse(isCompatibleXbibzAsset("armeabi", "Mochy-Music-v8.2.3-xbibz.4-arm64-gms.apk"))
        assertFalse(isCompatibleXbibzAsset("arm64", "Mochy-Music-v8.2.3-xbibz.5-armeabi-v7a-gms.apk"))
        assertFalse(isCompatibleXbibzAsset("arm64", "app-arm64-v8a-gms.apk.sig"))
    }
}
