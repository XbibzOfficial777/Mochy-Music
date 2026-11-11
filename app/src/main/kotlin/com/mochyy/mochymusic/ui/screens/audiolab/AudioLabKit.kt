// shared pieces of every audio lab page (cards, stage, toggle rows, sliders, chips, segments) so the hub, equalizer and audio fx read as one set

package com.mochyy.mochymusic.ui.screens.audiolab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.eq.audio.AudioTap
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.LocalNavAnimatedContentScope
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.component.drawChromeOutline
import com.mochyy.mochymusic.ui.utils.AppMotion
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.flow.first
import com.mochyy.mochymusic.ui.component.RemoteFocus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

object LabColors {
    val Card = Color(0xFF1C1C1E)
    val Stage = Color(0xFF0E0E10)
    val StageLine = Color(0xFF2E2E32)
    val Row = Color(0xFF26262A)
    val RowSelected = Color(0xFF34343A)
    val Chip = Color(0xFF232327)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Faint = Color(0xFF5C5C63)
    val Ink = Color(0xFF8A8A92)

    /** the equalizer's node colours, soft enough to sit on mochy's greys and apart enough to tell */
    val Nodes = listOf(
        Color(0xFFF28BA8), Color(0xFFF2A46B), Color(0xFFF2D46B), Color(0xFF8FD98B),
        Color(0xFF6BD9C4), Color(0xFF6BBAF2), Color(0xFF8C9EF2), Color(0xFFB98CF2),
        Color(0xFFE08CF2), Color(0xFFF26B6B),
    )

    fun node(index: Int) = Nodes[index.mod(Nodes.size)]
}

@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Solid fill only. Frosting these cards with the settings page's glassRoot samples that
    // RenderNode while it is still being recorded — that is an instant crash on open.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(LabColors.Card)
            .padding(padding),
        content = content,
    )
}

/** outlined dark box for a live picture, bounded so its animation repaints only itself and not the page and glass over it */
@Composable
fun LabStage(
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Stage)
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(18.dp))
            .damageBoundary(),
    ) {
        content()
    }
}

@Composable
fun LabToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingDot: Boolean = false,
) {
    val (hideDescriptions) = com.mochyy.mochymusic.utils.rememberPreference(
        com.mochyy.mochymusic.constants.HideSettingsDescriptionsKey,
        defaultValue = true,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDot) {
            val dot by animateColorAsState(if (checked) Color.White else Color(0xFFD9D9DE), tween(200), label = "dot")
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
            )
            if (subtitle != null && !hideDescriptions) {
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = if (enabled) LabColors.Secondary else LabColors.Faint,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** a slider with its name on the left and its value on the right, like the Audio FX design */
@Composable
fun LabSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
            )
        }
        DefaultPlayerSeekBarSlider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}

/** the inner darker panel the sliders sit in */
@Composable
fun LabPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Row)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** options that fold away while their effect is off */
@Composable
fun LabCollapsible(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220, 60)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(140)),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun LabSectionLabel(text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 1.4.sp,
            color = LabColors.Secondary,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** [busy] spins the icon and holds off taps, so a press that waits on the network shows it's working */
@Composable
fun LabTextAction(text: String, iconRes: Int? = null, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            // the turn is read in the layer, so spinning never recomposes the row
            val turn = if (busy) rememberInfiniteTransition(label = "busy")
                .animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "turn") else null
            Icon(
                painterResource(iconRes), null, tint = if (enabled) Color.White else LabColors.Faint,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn?.value ?: 0f },
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (enabled) Color.White else LabColors.Faint)
    }
}

data class LabChip(val key: String, val label: String, val dot: Color? = null)

/** a scrolling row of pills; the picked one is white */
@Composable
fun LabChips(
    chips: List<LabChip>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((String) -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val selected = chip.key == selectedKey
            val bg by animateColorAsState(if (selected) Color.White else LabColors.Chip, tween(180), label = "chipBg")
            val fg by animateColorAsState(if (selected) Color.Black else Color.White, tween(180), label = "chipFg")
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(bg)
                    .then(
                        if (onLongPress != null) Modifier.combinedClickableCompat(
                            interaction, onClick = { onSelect(chip.key) }, onLongClick = { onLongPress(chip.key) },
                        ) else Modifier.clickable(interaction, null) { onSelect(chip.key) }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chip.dot != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(chip.dot))
                    Spacer(Modifier.width(8.dp))
                }
                Text(chip.label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = fg, maxLines = 1)
            }
        }
        trailing()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = this.then(
    Modifier.combinedClickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick, onLongClick = onLongClick)
)

/**
 * Finger-follow for a row of pages (Graph / Bands / AutoEQ). Mix is continuous so the glass
 * chip and the pages stay on the same spring ([AppMotion.Slide]).
 */
class LabSlide internal constructor(
    private val mixState: MutableFloatState,
    private val widthState: MutableIntState,
    private val draggingState: MutableState<Boolean>,
    private val lastIndex: () -> Int,
    private val onSelect: () -> (Int) -> Unit,
) {
    val mix: Float get() = mixState.floatValue
    var widthPx: Int
        get() = widthState.intValue
        set(value) { widthState.intValue = value.coerceAtLeast(1) }

    fun dragPages(delta: Float) {
        draggingState.value = true
        mixState.floatValue = (mixState.floatValue + delta).coerceIn(0f, lastIndex().toFloat())
    }

    fun dragPx(delta: Float) {
        draggingState.value = true
        mixState.floatValue = (mixState.floatValue - delta / widthPx).coerceIn(0f, lastIndex().toFloat())
    }

    fun end() {
        val to = mixState.floatValue.roundToInt().coerceIn(0, lastIndex())
        draggingState.value = false
        onSelect()(to)
    }
}

@Composable
fun rememberLabSlide(
    selectedIndex: Int,
    lastIndex: Int,
    onSelect: (Int) -> Unit,
): LabSlide {
    val mixState = remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    val widthState = remember { mutableIntStateOf(1) }
    val draggingState = remember { mutableStateOf(false) }
    val lastIndexState = rememberUpdatedState(lastIndex)
    val onSelectState = rememberUpdatedState(onSelect)
    val slide = remember {
        LabSlide(mixState, widthState, draggingState, { lastIndexState.value }, { onSelectState.value })
    }
    LaunchedEffect(selectedIndex, draggingState.value) {
        if (draggingState.value) return@LaunchedEffect
        val dest = selectedIndex.toFloat()
        if (abs(mixState.floatValue - dest) < 0.001f) return@LaunchedEffect
        animate(initialValue = mixState.floatValue, targetValue = dest, animationSpec = AppMotion.Slide) { v, _ ->
            mixState.floatValue = v
        }
    }
    return slide
}

/** same drop as the dashboard tab pill: fill + sheen + rim, never a nested glass capture */
private val LabDropFill = Color.White.copy(alpha = 0.10f)
private val LabDropSheen = Color.White.copy(alpha = 0.10f)
private val LabDropRimLight = Color.White.copy(alpha = 0.42f)
private val LabDropRimShade = Color.White.copy(alpha = 0.16f)

/** a liquid-glass track whose drop slides — drag it or tap a label. No backdrop blur: this sits inside glassRoot. */
@Composable
fun LabSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    position: Float? = null,
    onDragPages: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(height / 2)
    val rest by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = AppMotion.Slide,
        label = "labSegment",
    )
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(LabColors.Card)
            .drawWithContent {
                drawContent()
                drawChromeOutline(Offset.Zero, size, size.height / 2f, 1f)
            },
    ) {
        val last = options.lastIndex.coerceAtLeast(0)
        val travelPx = with(density) { (maxWidth / options.size.coerceAtLeast(1)).toPx() }.coerceAtLeast(1f)
        val mix = when {
            position != null -> position.coerceIn(0f, last.toFloat())
            dragging -> (rest + drag / travelPx).coerceIn(0f, last.toFloat())
            else -> rest
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val n = options.size.coerceAtLeast(1)
                    val slot = size.width / n
                    val inset = 5.dp.toPx()
                    val gap = 4.dp.toPx()
                    val drop = Size((slot - gap).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
                    if (drop.width <= 0f || drop.height <= 0f) return@drawBehind
                    val topLeft = Offset(mix * slot + gap / 2f, inset)
                    val corner = CornerRadius(drop.height / 2f)
                    drawRoundRect(color = LabDropFill, topLeft = topLeft, size = drop, cornerRadius = corner)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to LabDropSheen,
                            0.55f to Color.Transparent,
                            startY = topLeft.y,
                            endY = topLeft.y + drop.height,
                        ),
                        topLeft = topLeft,
                        size = drop,
                        cornerRadius = corner,
                    )
                    val rim = 1.dp.toPx()
                    val half = rim / 2f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to LabDropRimLight,
                            0.4f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to LabDropRimShade,
                            start = topLeft,
                            end = Offset(topLeft.x + drop.width, topLeft.y + drop.height),
                        ),
                        topLeft = Offset(topLeft.x + half, topLeft.y + half),
                        size = Size(drop.width - rim, drop.height - rim),
                        cornerRadius = CornerRadius((corner.x - half).coerceAtLeast(0f)),
                        style = Stroke(rim),
                    )
                }
                .pointerInput(selectedIndex, options.size, travelPx, onDragPages != null) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onHorizontalDrag = { _, amount ->
                            if (onDragPages != null) onDragPages(amount / travelPx) else drag += amount
                        },
                        onDragCancel = {
                            dragging = false
                            drag = 0f
                            onDragEnd?.invoke()
                        },
                        onDragEnd = {
                            if (onDragPages != null) {
                                dragging = false
                                onDragEnd?.invoke()
                            } else {
                                val end = (rest + drag / travelPx).coerceIn(0f, last.toFloat())
                                dragging = false
                                drag = 0f
                                onSelect(end.roundToInt())
                            }
                        },
                    )
                }
                .pointerInput(options.size) {
                    detectTapGestures { offset ->
                        val i = (offset.x / size.width * options.size).toInt().coerceIn(0, last)
                        onSelect(i)
                    }
                },
        ) {
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { index, label ->
                    val fill = (1f - abs(mix - index)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = lerp(Color.White.copy(alpha = 0.55f), Color.White, fill),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** pages that travel with [slide] — wrap-content so they can sit in a scrolling column */
@Composable
fun LabPager(
    pageCount: Int,
    slide: LabSlide,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val last = (pageCount - 1).coerceAtLeast(0)
    val mix = slide.mix
    val from = floor(mix).toInt().coerceIn(0, last)
    val to = ceil(mix).toInt().coerceIn(0, last)
    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { slide.widthPx = it.width }
            .pointerInput(last) {
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        slide.dragPx(amount)
                    },
                    onDragCancel = { slide.end() },
                    onDragEnd = { slide.end() },
                )
            },
    ) {
        for (page in from..to) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = (page - mix) * slide.widthPx
                        val dist = abs(page - mix).coerceIn(0f, 1f)
                        alpha = 1f - 0.08f * dist
                    },
            ) { content(page) }
        }
    }
}

/** the 32dp light circle with a dark glyph that mochy's settings rows lead with */
@Composable
fun LabGlyph(iconRes: Int, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(0xFFE5E5EA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = Color.Black, modifier = Modifier.size(size * 0.55f))
    }
}

/** dims a section while what it belongs to is off, without removing it */
fun Modifier.dimmedWhen(off: Boolean, alpha: Float): Modifier = graphicsLayer { this.alpha = if (off) alpha else 1f }

@Composable
fun animatedDim(off: Boolean): Float =
    animateFloatAsState(if (off) 0.45f else 1f, tween(220), label = "dim").value

/** whether pictures should move: not while the player covers the app, the page is opening or closing, or it is scrolled out of view */
@Composable
fun rememberPicturesHeld(onScreen: State<Boolean>): State<Boolean> {
    val obscured = LocalAppContentObscured.current
    val navTransition = LocalNavAnimatedContentScope.current?.transition
    return remember(obscured, navTransition, onScreen) {
        derivedStateOf {
            !onScreen.value || obscured.value || (navTransition != null &&
                (navTransition.currentState != EnterExitState.Visible || navTransition.targetState != EnterExitState.Visible))
        }
    }
}

/** seconds, advancing every frame while the picture is free to move, or checking back a few times a second when moving says no */
@Composable
fun rememberLabTime(held: State<Boolean>, moving: () -> Boolean = { true }): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val currentMoving by androidx.compose.runtime.rememberUpdatedState(moving)
    LaunchedEffect(held) {
        var last = 0L
        while (true) {
            if (held.value) {
                snapshotFlow { held.value }.first { !it }
                last = 0L
            }
            if (!currentMoving()) {
                kotlinx.coroutines.delay(250)
                last = 0L
                continue
            }
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
            }
        }
    }
    return time
}

/** true while any part of this is inside the window; written only when it flips */
@Composable
fun Modifier.trackOnScreen(state: MutableState<Boolean>): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        val visible = bounds.height > 0f && bounds.bottom > 0f && bounds.top < view.height
        if (visible != state.value) state.value = visible
    }
}

/** asks the audio tap to fill while this page shows a live picture */
@Composable
fun ListenToAudio() {
    DisposableEffect(Unit) {
        AudioTap.listen()
        onDispose { AudioTap.stopListening() }
    }
}

/** a smoothed music level 0..1 for pictures to breathe with, updated from the frame clock */
class LevelFollower {
    private val scratch = FloatArray(1024)
    var level = 0f
        private set
    var punch = 0f
        private set
    private var slow = 0f

    /** call once per frame with the seconds since the last one */
    fun update(dt: Float) {
        val now = AudioTap.level(scratch)
        level += (now - level) * (if (now > level) 0.5f else 0.12f)
        slow += (now - slow) * 0.03f
        // how far above its own recent average it is: a kick or a downbeat
        val p = ((now - slow * 1.25f) * 6f).coerceIn(0f, 1f)
        punch = if (p > punch) p else (punch - dt * 3f).coerceAtLeast(0f)
    }
}

@Composable
fun rememberOnScreen(): MutableState<Boolean> = remember { mutableStateOf(true) }

@Composable
fun dpToPx(dp: Dp): Float = with(LocalDensity.current) { dp.toPx() }
