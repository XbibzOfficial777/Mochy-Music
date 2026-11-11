// customize's animation and element blur settings, mirrored here by mainactivity so draw code and nav lambdas can read them

package com.mochyy.mochymusic.ui.utils

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

// the speeds the animation slider stops at, slowest to fastest
val AnimationSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

object AppMotion {
    /** false: nothing animates, every compose animation jumps straight to its end */
    var enabled by mutableStateOf(true)

    /** how much faster than android's own animation speed things move, 1 is just android's */
    var speed by mutableFloatStateOf(1f)

    /** false: lyric lines, title morphs, menus and page wipes stay sharp, glass blur isn't touched */
    var elementBlur by mutableStateOf(true)

    private var systemScale by mutableFloatStateOf(1f)

    /** handed to the window's recomposer, so every animation in the app reads it each frame */
    val durationScale: MotionDurationScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = if (!enabled) 0f else systemScale / speed.coerceAtLeast(0.1f)
    }

    /*
     * Named springs the UI is allowed to use. A one-off spring in a composable is how a motion
     * "wasn't defined": it can't be tuned from here and it won't follow the same feel as the rest
     * of the player. Damping around 0.85 (almost no bounce) and a moderate stiffness: alive, not
     * snappy, not floaty.
     */
    /** a thumb or a highlight sliding across a control */
    val Slide = spring<Float>(dampingRatio = 0.86f, stiffness = 380f)
    val SlideDp = spring<Dp>(dampingRatio = 0.86f, stiffness = 380f)
    /** two full-screen looks swapping (Music ↔ Video) */
    val Scene = spring<Float>(dampingRatio = 0.9f, stiffness = 240f)
    /** a control appearing or settling */
    val Appear = spring<Float>(dampingRatio = 0.9f, stiffness = 520f)
    /** press scale, slightly snappier so the finger feels it */
    val Press = spring<Float>(dampingRatio = 0.72f, stiffness = 900f)

    // keeps android's animator duration scale in step, the way compose's own scale did before we replaced it
    fun followSystemScale(context: Context): ContentObserver {
        val resolver = context.contentResolver
        fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        systemScale = read()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemScale = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        return observer
    }
}

/** how a speed reads on screen: 1x, 1.25x, 0.5x */
fun animationSpeedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
    return "${text}x"
}

/**
 * Long-running decorative motion must honor both the app switch and Android's animator scale.
 * The recomposer's MotionDurationScale is snapshot state, so changing the system setting while
 * this screen is open updates the result without polling or an extra settings observer.
 */
@Composable
fun rememberMotionAllowed(): Boolean {
    val scope = rememberCoroutineScope()
    val scale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return AppMotion.enabled && scale > 0f && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
}

/** [transition], or no transition at all while animations are switched off */
fun AnimatedContentTransitionScope<NavBackStackEntry>.motionEnter(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?,
): EnterTransition? = if (AppMotion.enabled) transition() else EnterTransition.None

/** [transition], or no transition at all while animations are switched off */
fun AnimatedContentTransitionScope<NavBackStackEntry>.motionExit(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?,
): ExitTransition? = if (AppMotion.enabled) transition() else ExitTransition.None

/** The NavHost's own defaults, which can't be null: [transition], or none while animations are off */
fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultMotionEnter(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
): EnterTransition = if (AppMotion.enabled) transition() else EnterTransition.None

/** The NavHost's own defaults, which can't be null: [transition], or none while animations are off */
fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultMotionExit(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
): ExitTransition = if (AppMotion.enabled) transition() else ExitTransition.None
