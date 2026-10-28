package com.mochyy.mochymusic.ui.screens.recap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.lyrics.AiTranslationSession
import com.mochyy.mochymusic.ui.player.menu.AiTranslationSettingsSheet
import com.mochyy.mochymusic.ui.screens.settings.VideoQualityDialog
import java.time.LocalDate

/** Synthetic fixtures for CI only. Never included in a release variant. */
class IssueSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "receipt"
        setContent {
            when (screen) {
                "quality" -> {
                    var selected by remember { mutableStateOf(VideoQuality.P720) }
                    var visible by remember { mutableStateOf(true) }
                    Box(Modifier.fillMaxSize().background(Color(0xFF252528))) {
                        Text("Selected: ${selected.label}", color = Color.White)
                        if (visible) VideoQualityDialog(
                            selected = selected,
                            onSelect = { selected = it; visible = false },
                            onDismiss = { visible = false },
                        )
                    }
                }
                "ai" -> {
                    var visible by remember { mutableStateOf(true) }
                    val config by AiTranslationSession.config.collectAsState()
                    Box(Modifier.fillMaxSize().background(Color(0xFF252528))) {
                        Text("AI configured: ${config?.model.orEmpty()}", color = Color.White)
                        if (visible) AiTranslationSettingsSheet(
                            onDismiss = { visible = false },
                            onContinue = { visible = false },
                        )
                    }
                }
                else -> BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF303034))) {
                    val density = LocalDensity.current
                    val geo = remember(maxWidth, maxHeight, density.density) {
                        StoryGeometry(
                            width = with(density) { maxWidth.toPx() },
                            height = with(density) { maxHeight.toPx() },
                            top = 0f,
                            density = density.density,
                        )
                    }
                    val state = remember(geo) { StoryState(geo, Rect.Zero) }
                    val data = remember { fixture() }
                    LaunchedEffect(state) { state.pages[P_RECEIPT].enter.snapTo(1f) }
                    ReceiptPage(state, data)
                }
            }
        }
    }
}

private fun fixture(): RecapStoryData {
    val songs = (1..10).map { i -> RecapSong("s$i", "Fixture song $i", "Artist $i", null, null, 3_600_000L, i) }
    val artists = (1..10).map { i -> RecapArtist("a$i", "Fixture artist $i", null, 3_600_000L, i, songs[i - 1]) }
    val today = LocalDate.of(2026, 10, 28)
    val stats = ListeningStats(
        range = StatsRange.monthOf(today), totalMs = 36_000_000L, plays = 50,
        songCount = 10, artistCount = 10, albumCount = 5, topSongs = songs, topArtists = artists,
        weekdayMs = List(7) { 0L }, hourMs = List(24) { 0L }, bucketMs = List(31) { 0L },
    )
    return RecapStoryData(stats, 90, "CI", null, "0000", "2026", today, 36_000_000L)
}
