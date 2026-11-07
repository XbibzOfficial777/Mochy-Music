package com.mochyy.mochymusic.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min

/**
 * The size of the window this composition is actually drawn into, in dp.
 *
 * Screens used to size themselves from `Configuration.screenWidthDp` / `screenHeightDp`, which
 * describe the *display*, not the window. Those two are the same only in full screen on a normal
 * phone. In split screen, in a freeform window, on a foldable with an app pair, in desktop windowing
 * or inside a multi-window Cast/DeX session they are not, so a sheet that asked for 0.78 × screen
 * height could be taller than the space it was given and clip its own buttons.
 *
 * `LocalWindowInfo.containerSize` is the window the content occupies, and it is a snapshot state:
 * resizing the window recomposes the readers instead of requiring the activity to be recreated.
 *
 * Cached per (size, density) so the dp conversion runs when either actually changes rather than on
 * every frame of an animation that happens to read it.
 */
@Composable
fun rememberWindowSize(): WindowSize {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return remember(size, density) {
        with(density) {
            WindowSize(width = size.width.toDp(), height = size.height.toDp())
        }
    }
}

/** Window dimensions in dp; [shortest] is the smaller side, the usual basis for responsive sizing. */
data class WindowSize(val width: Dp, val height: Dp) {
    val shortest: Dp = min(width, height)
}
