package com.mochyy.mochymusic.ui.utils

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

// a phone on its side is always wider than this, a phone held upright never is, and every tablet is
private const val WideMinWidthDp = 600

// These read Configuration on purpose. They answer "is this a wide window / a landscape one", which
// is about the display the activity was laid out for, and they are used while choosing a layout, not
// for measuring a composable; LocalWindowInfo would answer a different question and would also make
// them non-read-only. The warning is waived here with that reason.
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
@ReadOnlyComposable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= WideMinWidthDp

// the window is wider than it is tall, so the player can put its cover beside the controls
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
@ReadOnlyComposable
fun isLandscapeWindow(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp
}

// a tv, watched from across the room: a square cover stretched over the whole screen there is one huge blurry crop
@Composable
@ReadOnlyComposable
fun isTvLayout(): Boolean =
    (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
