package com.mochyy.mochymusic.ui.screens.recap

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areNavigationBarsVisible
import androidx.compose.foundation.layout.areStatusBarsVisible
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.mochyy.mochymusic.ui.component.ChromeTheme
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.component.collapsedAnchor
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackRow
import com.mochyy.mochymusic.ui.component.liquidTabIndicator
import com.mochyy.mochymusic.ui.component.liquidTabSlide
import com.mochyy.mochymusic.ui.component.rememberBottomSheetState
import com.mochyy.mochymusic.ui.component.rememberLiquidTabState
import com.mochyy.mochymusic.ui.player.HidePlayerStatusBar
import com.mochyy.mochymusic.ui.screens.search.SearchField
import com.mochyy.mochymusic.ui.utils.AppMotion

/** Deterministic debug-only scenarios. None of these fixtures are included in a release APK. */
class LatestIssuesSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val screen = intent.getStringExtra("screen") ?: "focus"
        setContent {
            MaterialTheme {
                when (screen) {
                    "focus" -> FocusAndStatusFixture()
                    "album" -> AlbumLikeFixture()
                    "motion" -> LiquidMotionFixture()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusAndStatusFixture() {
    var upperBound by remember { mutableIntStateOf(520) }
    val sheet = rememberBottomSheetState(
        dismissedBound = 0.dp,
        collapsedBound = 64.dp,
        expandedBound = upperBound.dp,
        initialAnchor = collapsedAnchor,
    )
    var enabled by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf(TextFieldValue()) }
    var editor by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    val searchRequester = remember { FocusRequester() }
    val editorRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (enabled) HidePlayerStatusBar(sheet)

    CompositionLocalProvider(LocalPlayerBottomSheetState provides sheet) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF242428)).padding(start = 12.dp, end = 12.dp, top = 38.dp),
        ) {
            Text("Search focus: $searchFocused", color = Color.White)
            Text("Editor focus: $editorFocused", color = Color.White)
            Text("Query: ${search.text}", color = Color.White)
            Text("Status visible: ${WindowInsets.areStatusBarsVisible}", color = Color.White)
            Text("Nav visible: ${WindowInsets.areNavigationBarsVisible}", color = Color.White)
            Text("Sheet bound: $upperBound", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { searchRequester.requestFocus(); keyboard?.show() }) { Text("Focus search") }
                Button(onClick = sheet::expandSoft) { Text("Open player") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { editorRequester.requestFocus(); keyboard?.show() }) { Text("Focus editor") }
                Button(onClick = { upperBound = if (upperBound == 520) 560 else 520 }) { Text("Resize sheet") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = sheet::collapseSoft) { Text("Close player") }
                Button(onClick = { enabled = !enabled }) { Text(if (enabled) "Bar off" else "Bar on") }
            }
            SearchField(
                value = search,
                onValueChange = { search = it },
                onSearch = {},
                placeholder = "Search fixture",
                focusRequester = searchRequester,
                onFocusChanged = { searchFocused = it },
            )
            BasicTextField(
                value = editor,
                onValueChange = { editor = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth().height(42.dp).background(Color(0xFF444448))
                    .focusRequester(editorRequester).onFocusChanged { editorFocused = it.isFocused }.padding(10.dp),
            )
        }
    }
}

@Composable
private fun AlbumLikeFixture() {
    var liked by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(false) }
    var menuCount by remember { mutableIntStateOf(0) }
    var rowCount by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Color(0xFF242428)).padding(top = 38.dp)) {
        Text("Liked: $liked", color = Color.White)
        Text("Menu count: $menuCount", color = Color.White)
        Text("Row count: $rowCount", color = Color.White)
        Button(onClick = { selection = !selection }) { Text("Toggle selection") }
        PlaylistTrackRow(
            thumbnailUrl = null,
            title = "Fixture track",
            subtitle = "Fixture artist",
            isActive = false,
            isPlaying = false,
            onClick = { rowCount++ },
            onLongClick = {},
            onMenuClick = { menuCount++ },
            liked = liked,
            onLikeClick = { liked = !liked },
            trailing = if (selection) { { Text("Selection tick", color = Color.White) } } else null,
        )
    }
}

@Composable
private fun LiquidMotionFixture() {
    var selected by remember { mutableIntStateOf(0) }
    var motion by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val originalGlass = ChromeTheme.liquidGlass
        val originalMotion = AppMotion.enabled
        ChromeTheme.liquidGlass = true
        AppMotion.enabled = true
        onDispose { ChromeTheme.liquidGlass = originalGlass; AppMotion.enabled = originalMotion }
    }
    val state = rememberLiquidTabState(selected)
    LaunchedEffect(selected, motion) {
        if (selected == 0) return@LaunchedEffect
        val samples = mutableListOf<Float>()
        repeat(60) {
            withFrameNanos { }
            samples += state.position.value
        }
        val target = selected.toFloat()
        val start = if (selected == 1) 2f else 0f
        val intermediate = samples.count { kotlin.math.abs(it - start) > 0.03f && kotlin.math.abs(it - target) > 0.03f }
        Log.i("LatestMotion", "enabled=$motion selected=$selected intermediate=$intermediate final=${state.position.value}")
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF242428)).padding(top = 38.dp)) {
        Text("Selected tab: $selected", color = Color.White)
        Text("Motion enabled: $motion", color = Color.White)
        Button(onClick = { motion = !motion; AppMotion.enabled = motion }) { Text("Toggle motion") }
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).background(Color(0xFF3A3A42))
                .liquidTabIndicator(state, 3)
                .liquidTabSlide(state, 3, selected) { selected = it },
        ) {
            repeat(3) { index ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { selected = index },
                    contentAlignment = Alignment.Center,
                ) { Text("Tab $index", color = Color.White) }
            }
        }
    }
}
