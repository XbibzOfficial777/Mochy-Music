// ChromeTheme.kt
//
// Which theme the floating chrome (the nav bar and the mini player) is drawn in, for code that only
// draws and has no composition to read a preference from: the chrome's outline, its glass, and the
// nav bar's pill. MainActivity keeps it in step with Settings > Appearance > Theme. Snapshot state,
// so whatever reads it while drawing is redrawn when it changes.

package com.mochyy.mochymusic.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ChromeTheme {
    /** Liquid glass: clearer glass with a lit rim, and the nav bar's pill a glass lens */
    var liquidGlass by mutableStateOf(false)
}
