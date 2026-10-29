package com.mochyy.mochymusic.i18n

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.navigation.compose.rememberNavController
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.music.innertube.YouTube
import com.mochyy.mochymusic.constants.ContentCountryKey
import com.mochyy.mochymusic.constants.ContentLanguageKey
import com.mochyy.mochymusic.constants.TranslateLanguageKey
import com.mochyy.mochymusic.ui.screens.settings.GeneralSettingsScreen
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Temporary CI-only fixture: exercises the real General settings and language picker. */
class LanguageSmokeActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrapActivityContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seeded = booleanPreferencesKey("languageSmokeSeeded")
        runBlocking(Dispatchers.IO) {
            applicationContext.dataStore.edit { prefs ->
                if (prefs[seeded] != true) {
                    prefs[ContentLanguageKey] = "ja"
                    prefs[ContentCountryKey] = "JP"
                    prefs[TranslateLanguageKey] = "es"
                    prefs[seeded] = true
                }
            }
        }
        setContent {
            val content by rememberPreference(ContentLanguageKey, "")
            val country by rememberPreference(ContentCountryKey, "")
            val lyrics by rememberPreference(TranslateLanguageKey, "")
            var youtube by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                while (true) {
                    youtube = "${YouTube.locale.hl}/${YouTube.locale.gl}"
                    delay(500)
                }
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0)) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        GeneralSettingsScreen(rememberNavController())
                        Column(modifier = Modifier.align(Alignment.BottomCenter).background(Color.DarkGray).padding(4.dp)) {
                            Text("FIXTURE content=$content country=$country lyrics=$lyrics youtube=$youtube", color = Color.White)
                            Text("SYSTEM ${AppLanguage.systemLocale(this@LanguageSmokeActivity).toLanguageTag()}", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
