package com.mochyy.mochymusic.utils

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.constants.HidePlayerStatusBarKey
import com.mochyy.mochymusic.constants.LegacyBibzVideoQualityKey
import com.mochyy.mochymusic.constants.PlayerStatusBarKey
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.constants.VideoQualityKey

/** Transfer the .6 Bibz settings once, before any consumer reads the official settings. */
internal object BibzPreferencesMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[LegacyBibzVideoQualityKey] != null || currentData[HidePlayerStatusBarKey] != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        prefs[LegacyBibzVideoQualityKey]?.let { old ->
            if (prefs[VideoQualityKey] == null) {
                // Old Bibz stored P720, while official stores Q720. Invalid old values used
                // the old screen's 720p default; a newer explicit official choice always wins.
                val migrated = VideoQuality.entries.firstOrNull { it.name == old.replaceFirst("P", "Q") }
                    ?: VideoQuality.Q720
                prefs[VideoQualityKey] = migrated.name
            }
            prefs.remove(LegacyBibzVideoQualityKey)
        }
        prefs[HidePlayerStatusBarKey]?.let { hidden ->
            if (prefs[PlayerStatusBarKey] == null) prefs[PlayerStatusBarKey] = !hidden
            prefs.remove(HidePlayerStatusBarKey)
        }
        return prefs
    }

    override suspend fun cleanUp() = Unit
}
