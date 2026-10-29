// LiquidTabIndicator.kt
//
// The nav bar's lit-tab pill as a drop of liquid glass you can slide.
//
// - Tap a tab and the drop flows over to it: it stretches with its speed and squashes back as it
//   lands, with a little lift on the way (a droplet pulse).
// - Press and slide anywhere along the bar and the drop lifts and follows the finger. Let go and it
//   lands on the nearest tab (a flick carries it on), and that tab opens. A tick under the finger
//   each time it crosses into another tab.
// - The tabs light up by how close the drop is to them, so their icons fill in as it passes over.
// - In the Liquid glass theme it's glass. On Android 13+ the icons under it are seen through it:
//   magnified, bent at its rim and split a touch into colour there. Older phones get the
//   magnification alone, by scaling the icon.
//   The other themes keep the bar's plain pill, sliding and stretching just the same.

package com.mochyy.mochymusic.ui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Resting, the drop sits this far in from the bar's top and bottom (and from its slot's ends, half the gap). */
private val RestInset = 8.dp
private val RestGap = 4.dp

/** Lifted (pressed or sliding), it swells to nearly the bar's height and a little wider than its slot. */
private val LiftedInset = 3.dp
private val LiftedExtraWidth = 10.dp

/** How much the glass magnifies what's under it, resting and lifted. */
private const val RestMagnify = 0.05f
private const val LiftedMagnify = 0.22f

/** Older phones: how much bigger the icon under a lifted drop is drawn instead. */
private const val FallbackIconZoom = 0.18f

/** Squash and stretch: width gained and height lost per tab per second of speed, and their caps. */
private const val StretchPerSpeed = 0.055f
private const val MaxStretch = 0.42f
private const val SquashPerSpeed = 0.018f
private const val MaxSquash = 0.14f

/** Past either end the drop follows the finger at this fraction, then stops: a rubber band. */
private const val EdgeResistance = 0.3f
private const val EdgeOvershoot = 0.35f

/** A flick carries the drop on by its speed times this, in seconds, before it picks a tab. */
private const val FlickProjection = 0.09f

/** Flowing to a tapped tab, landing after a slide, and following the finger (a little behind it). */
private val FlowSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 340f)
private val LandSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 420f)
private val FollowSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 1400f)
private val LiftSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 520f)
private val SettleLiftSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 260f)
private val PressSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)

/** The glass itself. */
private val GlassFill = Color.White.copy(alpha = 0.10f)
private val GlassFillLifted = Color.White.copy(alpha = 0.055f)
private val RimLight = Color.White.copy(alpha = 0.42f)
private val RimShade = Color.White.copy(alpha = 0.16f)
private val Sheen = Color.White.copy(alpha = 0.10f)

@Stable
class LiquidTabState internal constructor(initialIndex: Int) {
    /** where the drop is, in tabs: 0 over the first, 1.5 halfway between the second and third */
    internal val position = Animatable(initialIndex.coerceAtLeast(0).toFloat())

    /** 0 hidden (a page no tab owns), 1 shown */
    internal val shown = Animatable(if (initialIndex >= 0) 1f else 0f)

    /** 0 resting in the bar, 1 picked up under the finger */
    internal val lift = Animatable(0f)

    /** the finger is on the bar, for the bar's own squish */
    internal val press = Animatable(0f)

    /** while a slide owns the drop, tab changes from outside leave it alone */
    internal var sliding = false

    /** the last slide's landing, called off when the next press comes before it's done */
    internal var landJob: Job? = null

    /** how lit a tab is: 1 with the drop right over it, fading out as it moves a tab away */
    fun litOf(index: Int): Float =
        shown.value * (1f - abs(position.value - index)).coerceIn(0f, 1f)

    /** older phones: how much bigger to draw a tab's icon, as the lifted drop sits over it */
    fun iconZoomOf(index: Int): Float =
        if (!ChromeTheme.liquidGlass || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 1f
        else 1f + FallbackIconZoom * lift.value * litOf(index)

    /** the bar squishes a hair under a press and swells a hair while the drop is lifted */
    val barScale: Float get() = 1f - 0.015f * press.value * (1f - lift.value) + 0.012f * lift.value
}

/**
 * The drop, kept on [selectedIndex] (-1: hidden). Taps flow it over; a slide in progress owns it.
 */
@Composable
fun rememberLiquidTabState(selectedIndex: Int): LiquidTabState {
    val state = remember { LiquidTabState(selectedIndex) }
    val motionEnabled = com.mochyy.mochymusic.ui.utils.AppMotion.enabled
    LaunchedEffect(selectedIndex, motionEnabled) {
        if (!motionEnabled) {
            // Keep the glass, but honour the user's animation switch. Cancel a pending landing
            // before snapping so a stale spring cannot pull the indicator away again.
            state.landJob?.cancel()
            state.landJob = null
            state.sliding = false
            state.press.snapTo(0f)
            state.lift.snapTo(0f)
            state.shown.snapTo(if (selectedIndex >= 0) 1f else 0f)
            if (selectedIndex >= 0) state.position.snapTo(selectedIndex.toFloat())
            return@LaunchedEffect
        }
        if (selectedIndex < 0) {
            state.shown.animateTo(0f, tween(200))
            return@LaunchedEffect
        }
        if (state.sliding) return@LaunchedEffect
        val target = selectedIndex.toFloat()
        // back from no lit tab, it fades in where it belongs rather than flowing over from the old one
        if (state.shown.value == 0f) state.position.snapTo(target)
        launch { state.shown.animateTo(1f, tween(200)) }
        if (abs(state.position.value - target) > 0.01f) {
            // a droplet pulse: it rises a little as it leaves and settles as it lands
            launch {
                state.lift.animateTo(0.5f, LiftSpring)
                state.lift.animateTo(0f, SettleLiftSpring)
            }
            state.position.animateTo(target, FlowSpring)
        }
    }
    return state
}

/**
 * Slides along the bar: put this on the row the tabs sit in, evenly spread. [onSlideTo] gets the tab
 * a slide lands on when it isn't the one already lit. Taps are left to the tabs themselves.
 */
@Composable
fun Modifier.liquidTabSlide(
    state: LiquidTabState,
    tabCount: Int,
    selectedIndex: Int,
    onSlideTo: (Int) -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSlideTo by rememberUpdatedState(onSlideTo)
    val currentMotion by rememberUpdatedState(com.mochyy.mochymusic.ui.utils.AppMotion.enabled)

    return pointerInput(state, tabCount) {
        if (tabCount <= 0) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            state.landJob?.cancel()
            state.landJob = null
            state.sliding = false
            val slot = size.width.toFloat() / tabCount
            val touchSlop = viewConfiguration.touchSlop
            if (currentMotion) scope.launch { state.press.animateTo(1f, PressSpring) }

            // pressing the lit tab picks the drop up straight away, as if to carry it somewhere
            val downIndex = (down.position.x / slot).toInt().coerceIn(0, tabCount - 1)
            val pickedUp = downIndex == currentSelected && state.shown.value > 0f
            if (pickedUp && currentMotion) scope.launch { state.lift.animateTo(1f, LiftSpring) }

            val tracker = VelocityTracker()
            var sliding = false
            var travelX = 0f
            var travelY = 0f
            var lastTick = downIndex

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!sliding) {
                    val delta = change.positionChange()
                    travelX += delta.x
                    travelY += delta.y
                    // sideways past the slop and more sideways than not: a slide. Up or down is left be.
                    if (abs(travelX) > touchSlop && abs(travelX) > abs(travelY)) {
                        sliding = true
                        state.sliding = true
                        if (state.shown.value == 0f) {
                            scope.launch { state.position.snapTo(positionUnder(change.position.x, slot, tabCount)) }
                        }
                        scope.launch {
                            if (currentMotion) state.shown.animateTo(1f, tween(150)) else state.shown.snapTo(1f)
                        }
                        if (currentMotion) scope.launch { state.lift.animateTo(1f, LiftSpring) }
                    }
                }
                if (sliding) {
                    // taken: the tab under the finger never sees a tap
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val target = positionUnder(change.position.x, slot, tabCount)
                    scope.launch {
                        if (currentMotion) state.position.animateTo(target, FollowSpring) else state.position.snapTo(target)
                    }
                    val under = target.roundToInt().coerceIn(0, tabCount - 1)
                    if (under != lastTick) {
                        lastTick = under
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            }

            if (currentMotion) scope.launch { state.press.animateTo(0f, PressSpring) }
            if (!sliding) {
                if (pickedUp && currentMotion) scope.launch { state.lift.animateTo(0f, SettleLiftSpring) }
                // a landing this press called off can leave the drop between two tabs: home it. A tap
                // that changes the tab sends it on from there anyway.
                val lit = currentSelected
                if (lit >= 0 && !state.position.isRunning && abs(state.position.value - lit) > 0.001f) {
                    scope.launch {
                        if (currentMotion) state.position.animateTo(lit.toFloat(), FlowSpring) else state.position.snapTo(lit.toFloat())
                    }
                }
                return@awaitEachGesture
            }

            // let go: a flick carries it on, then it lands on the nearest tab
            val speed = tracker.calculateVelocity().x / slot
            val projected = state.position.value + speed * FlickProjection
            val landing = projected.roundToInt().coerceIn(0, tabCount - 1)
            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
            state.landJob = scope.land(state, landing, speed, currentMotion) { currentSelected }
            if (landing != currentSelected) currentOnSlideTo(landing)
        }
    }
}

/** the drop's position for a finger at [x]: centred under it, held back past either end */
private fun positionUnder(x: Float, slot: Float, tabCount: Int): Float {
    val raw = x / slot - 0.5f
    val last = (tabCount - 1).toFloat()
    return when {
        raw < 0f -> (raw * EdgeResistance).coerceAtLeast(-EdgeOvershoot)
        raw > last -> (last + (raw - last) * EdgeResistance).coerceAtMost(last + EdgeOvershoot)
        else -> raw
    }
}

/**
 * Lands the drop on [landing], carrying the flick's [speed] into the spring, and sets it down. Should
 * the tab not change after all (the page refused it), it flows back to the one that's lit.
 */
private fun CoroutineScope.land(
    state: LiquidTabState,
    landing: Int,
    speed: Float,
    motionEnabled: Boolean,
    selected: () -> Int,
): Job = launch {
    if (motionEnabled) {
        launch { state.lift.animateTo(0f, SettleLiftSpring) }
        state.position.animateTo(landing.toFloat(), LandSpring, initialVelocity = speed)
    } else {
        state.lift.snapTo(0f)
        state.position.snapTo(landing.toFloat())
    }
    // Give navigation a chance to confirm the new tab even when motion is disabled.
    delay(if (motionEnabled) 450 else 150)
    state.sliding = false
    val lit = selected()
    if (lit >= 0 && lit != landing) {
        if (motionEnabled) state.position.animateTo(lit.toFloat(), FlowSpring) else state.position.snapTo(lit.toFloat())
    }
}

/**
 * Draws the drop behind the tabs and its rim over them, and on Android 13+ looks at the tabs
 * through it. Put this on the same row as [liquidTabSlide], before it.
 */
fun Modifier.liquidTabIndicator(state: LiquidTabState, tabCount: Int): Modifier = this
    .drawWithContent {
        val geometry = dropGeometry(state, tabCount, size, RestInset.toPx(), RestGap.toPx(), LiftedInset.toPx(), LiftedExtraWidth.toPx())
        if (geometry == null) {
            drawContent()
            return@drawWithContent
        }
        val (topLeft, dropSize, alpha) = geometry
        val corner = CornerRadius(dropSize.height / 2f)
        val lift = state.lift.value

        // the Frosted and Pure black themes keep the bar's plain pill; it still slides and stretches
        if (!ChromeTheme.liquidGlass) {
            drawRoundRect(color = ChromeHighlightColor, topLeft = topLeft, size = dropSize, cornerRadius = corner, alpha = alpha)
            drawContent()
            return@drawWithContent
        }

        // the body: clearer as it lifts, like a drop held up to the light
        drawRoundRect(
            color = mixColor(GlassFill, GlassFillLifted, lift),
            topLeft = topLeft,
            size = dropSize,
            cornerRadius = corner,
            alpha = alpha,
        )
        drawContent()

        // a sheen across its top half, and a rim lit from the top left
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Sheen,
                0.55f to Color.Transparent,
                startY = topLeft.y,
                endY = topLeft.y + dropSize.height,
            ),
            topLeft = topLeft,
            size = dropSize,
            cornerRadius = corner,
            alpha = alpha * (0.6f + 0.4f * lift),
        )
        val rimWidth = 1.dp.toPx() * (1f + 0.4f * lift)
        val half = rimWidth / 2f
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to RimLight,
                0.4f to Color.Transparent,
                0.65f to Color.Transparent,
                1f to RimShade,
                start = topLeft,
                end = Offset(topLeft.x + dropSize.width, topLeft.y + dropSize.height),
            ),
            topLeft = Offset(topLeft.x + half, topLeft.y + half),
            size = Size(dropSize.width - rimWidth, dropSize.height - rimWidth),
            cornerRadius = CornerRadius((corner.x - half).coerceAtLeast(0f)),
            style = Stroke(rimWidth),
            alpha = alpha * (0.7f + 0.3f * lift),
        )
    }
    .graphicsLayer {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@graphicsLayer
        if (!ChromeTheme.liquidGlass) {
            renderEffect = null
            return@graphicsLayer
        }
        val geometry = dropGeometry(state, tabCount, size, RestInset.toPx(), RestGap.toPx(), LiftedInset.toPx(), LiftedExtraWidth.toPx())
        renderEffect = if (geometry == null || geometry.alpha <= 0f) {
            null
        } else {
            val (topLeft, dropSize, alpha) = geometry
            lensEffect(
                topLeft = topLeft,
                dropSize = dropSize,
                magnify = (RestMagnify + (LiftedMagnify - RestMagnify) * state.lift.value) * alpha,
                rim = dropSize.height * 0.32f,
                refract = dropSize.height * (0.10f + 0.08f * state.lift.value),
                aberration = 0.6.dp.toPx() + 1.2.dp.toPx() * state.lift.value,
            )?.asComposeRenderEffect()
        }
    }

private data class DropGeometry(val topLeft: Offset, val size: Size, val alpha: Float)

/** where the drop is drawn in a row of [tabCount] even slots, swollen by its lift and stretched by its speed */
private fun dropGeometry(
    state: LiquidTabState,
    tabCount: Int,
    rowSize: Size,
    restInset: Float,
    restGap: Float,
    liftedInset: Float,
    liftedExtraWidth: Float,
): DropGeometry? {
    val alpha = state.shown.value
    if (alpha <= 0f || tabCount <= 0 || rowSize.width <= 0f) return null
    val lift = state.lift.value
    val slot = rowSize.width / tabCount
    val speed = abs(state.position.velocity)
    val stretch = 1f + (speed * StretchPerSpeed).coerceAtMost(MaxStretch)
    val squash = 1f - (speed * SquashPerSpeed).coerceAtMost(MaxSquash)

    val inset = restInset + (liftedInset - restInset) * lift
    val baseWidth = slot - restGap + liftedExtraWidth * lift
    val width = baseWidth * stretch
    val height = (rowSize.height - inset * 2f) * squash
    val centerX = (state.position.value + 0.5f) * slot
    val centerY = rowSize.height / 2f
    return DropGeometry(
        topLeft = Offset(centerX - width / 2f, centerY - height / 2f),
        size = Size(width, height),
        alpha = alpha,
    )
}

private fun mixColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/*
 * The lens. Inside the drop's rounded rect the tabs are sampled closer to its centre (magnified),
 * and near its rim further out along the edge's normal, so what's just outside is squeezed into the
 * rim the way a thick drop of glass bends it; there the red and blue are pulled apart a little.
 * Outside the drop everything is left as it is.
 */
private const val LensSource = """
uniform shader content;
uniform float2 center;
uniform float2 halfSize;
uniform float corner;
uniform float magnify;
uniform float rim;
uniform float bendDepth;
uniform float aberration;

float roundRect(float2 p) {
    float2 q = abs(p) - halfSize + corner;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - corner;
}

half4 main(float2 coord) {
    float2 p = coord - center;
    float d = roundRect(p);
    half4 outside = content.eval(coord);
    if (d > 0.0) return outside;

    float2 n = float2(
        roundRect(p + float2(1.0, 0.0)) - roundRect(p - float2(1.0, 0.0)),
        roundRect(p + float2(0.0, 1.0)) - roundRect(p - float2(0.0, 1.0))
    );
    n = n / max(length(n), 0.0001);

    float edge = clamp(1.0 + d / rim, 0.0, 1.0);
    float bend = edge * edge * edge;
    float2 base = center + p / (1.0 + magnify) + n * (bend * bendDepth);
    float2 split = n * (bend * aberration);

    half4 r = content.eval(base + split);
    half4 g = content.eval(base);
    half4 b = content.eval(base - split);
    half4 seen = half4(r.r, g.g, b.b, max(max(r.a, g.a), b.a));

    // a pixel's worth of blend at the edge, so the drop's outline doesn't step
    return mix(outside, seen, half(clamp(-d, 0.0, 1.0)));
}
"""

// compiled once on first use; every effect built from it keeps the uniforms it was built with.
// Should a phone's shader compiler refuse it, the lens is simply left off rather than the app going down.
private object LensShader {
    var shader: Any? = null
    var failed = false
}

@RequiresApi(33)
private fun lensEffect(
    topLeft: Offset,
    dropSize: Size,
    magnify: Float,
    rim: Float,
    refract: Float,
    aberration: Float,
): android.graphics.RenderEffect? {
    if (LensShader.failed) return null
    val shader = LensShader.shader ?: try {
        createAgslShader(LensSource).also { LensShader.shader = it }
    } catch (e: RuntimeException) {
        LensShader.failed = true
        android.util.Log.w("LiquidTabIndicator", "glass lens shader refused, drawing the tabs plain", e)
        return null
    }
    shader.setAgslUniform("center", topLeft.x + dropSize.width / 2f, topLeft.y + dropSize.height / 2f)
    shader.setAgslUniform("halfSize", dropSize.width / 2f, dropSize.height / 2f)
    shader.setAgslUniform("corner", minOf(dropSize.width, dropSize.height) / 2f)
    shader.setAgslUniform("magnify", magnify)
    shader.setAgslUniform("rim", rim.coerceAtLeast(1f))
    shader.setAgslUniform("bendDepth", refract)
    shader.setAgslUniform("aberration", aberration)
    return agslRenderEffect(shader, "content")
}
