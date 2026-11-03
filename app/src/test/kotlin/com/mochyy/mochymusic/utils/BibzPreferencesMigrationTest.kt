package com.mochyy.mochymusic.utils

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mochyy.mochymusic.constants.HidePlayerStatusBarKey
import com.mochyy.mochymusic.constants.LegacyBibzVideoQualityKey
import com.mochyy.mochymusic.constants.PlayerStatusBarKey
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.constants.VideoQualityKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BibzPreferencesMigrationTest {
    @Test fun everyOldQualityMigratesIncluding4k() = runBlocking {
        for (size in listOf(144, 240, 360, 480, 720, 1080, 1440, 2160)) {
            val old = mutablePreferencesOf(LegacyBibzVideoQualityKey to "P$size")
            assertTrue(BibzPreferencesMigration.shouldMigrate(old))
            val result = BibzPreferencesMigration.migrate(old)
            assertEquals("Q$size", result[VideoQualityKey])
            assertNull(result[LegacyBibzVideoQualityKey])
            assertFalse(BibzPreferencesMigration.shouldMigrate(result))
        }
    }

    @Test fun explicitNewSettingAlwaysWinsAndOldValuesAreRemoved() = runBlocking {
        val old = mutablePreferencesOf(
            LegacyBibzVideoQualityKey to "P2160",
            VideoQualityKey to VideoQuality.AUTO.name,
            HidePlayerStatusBarKey to true,
            PlayerStatusBarKey to true,
        )
        val result = BibzPreferencesMigration.migrate(old)
        assertEquals(VideoQuality.AUTO.name, result[VideoQualityKey])
        assertEquals(true, result[PlayerStatusBarKey])
        assertNull(result[LegacyBibzVideoQualityKey])
        assertNull(result[HidePlayerStatusBarKey])
    }

    @Test fun oldStatusBarChoiceIsInverted() = runBlocking {
        for (hidden in listOf(true, false)) {
            val result = BibzPreferencesMigration.migrate(mutablePreferencesOf(HidePlayerStatusBarKey to hidden))
            assertEquals(!hidden, result[PlayerStatusBarKey])
        }
        assertFalse(BibzPreferencesMigration.shouldMigrate(emptyPreferences()))
    }

    @Test fun unknownOldQualityPreservesTheOld720Default() = runBlocking {
        val result = BibzPreferencesMigration.migrate(mutablePreferencesOf(LegacyBibzVideoQualityKey to "unknown"))
        assertEquals(VideoQuality.Q720.name, result[VideoQualityKey])
    }
}
