package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.drawChromeOutline
import com.mochyy.mochymusic.ui.utils.AppMotion
import kotlin.math.roundToInt

/*
 * Music / Video as one sliding liquid-glass switch.
 *
 * The highlight is a single chip that travels, so the eye follows the choice instead of watching
 * two fills cross-fade. The track is the same frost the nav bar uses (GlassPillBackground + the
 * liquid rim), not a second blur of its own. Dragging the chip past halfway commits the same way
 * a tap on the other label does.
 *
 * Springs come from AppMotion (Slide, Appear, Press): they are the registered motion for this
 * control, not one-off numbers.
 */

private val TrackHeight = 34.dp
private val TrackShape = RoundedCornerShape(17.dp)
private val ChipInset = 3.dp
private val ChipShape = RoundedCornerShape(14.dp)

@Composable
internal fun MediaModeToggle(
    videoSelected: Boolean,
    onMusic: () -> Unit,
    onVideo: () -> Unit,
    glassState: GlassState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val shown by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.Appear,
        label = "mediaModeShown",
    )
    if (shown <= 0.01f) return
    val music = stringResource(R.string.player_mode_music)
    val video = stringResource(R.string.player_mode_video)

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * -8.dp.toPx()
            }
            .semantics {
                role = Role.Switch
                contentDescription = if (videoSelected) video else music
            },
    ) {
        GlassTrack(
            videoSelected = videoSelected,
            music = music,
            video = video,
            glassState = glassState,
            onMusic = onMusic,
            onVideo = onVideo,
        )
    }
}

@Composable
private fun GlassTrack(
    videoSelected: Boolean,
    music: String,
    video: String,
    glassState: GlassState,
    onMusic: () -> Unit,
    onVideo: () -> Unit,
) {
    val rest by animateFloatAsState(
        targetValue = if (videoSelected) 1f else 0f,
        animationSpec = AppMotion.Slide,
        label = "mediaModeThumb",
    )
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .height(TrackHeight)
            .width(196.dp)
            .clip(TrackShape)
            .drawWithContent {
                drawContent()
                drawChromeOutline(Offset.Zero, size, size.height / 2f, 1f)
            },
    ) {
        GlassPillBackground(
            state = glassState,
            blurRadius = { 22f },
            tint = Color.White.copy(alpha = 0.10f),
            foundationColor = Color.Black.copy(alpha = 0.28f),
            shape = TrackShape,
            bleed = true,
            modifier = Modifier.fillMaxSize(),
        )

        val trackWidth = maxWidth
        val chipWidth = (trackWidth - ChipInset * 2) / 2
        val travel = trackWidth - ChipInset * 2 - chipWidth
        val travelPx = with(density) { travel.toPx() }
        val mix = if (dragging) {
            (rest + drag / travelPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        } else {
            rest
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = ChipInset, top = ChipInset, bottom = ChipInset)
                .offset { IntOffset((mix * travelPx).roundToInt(), 0) }
                .width(chipWidth)
                .fillMaxHeight()
                .clip(ChipShape)
                .graphicsLayer {
                    // a little extra light on the travelling chip, not a second blur
                    shadowElevation = 0f
                }
                .drawWithContent {
                    drawRect(Color.White.copy(alpha = 0.22f))
                    drawChromeOutline(Offset.Zero, size, size.height / 2f, 0.85f)
                },
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(videoSelected, travelPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragCancel = {
                            dragging = false
                            drag = 0f
                        },
                        onDragEnd = {
                            val end = (rest + drag / travelPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            dragging = false
                            drag = 0f
                            if (end >= 0.5f) onVideo() else onMusic()
                        },
                    )
                }
                .pointerInput(videoSelected) {
                    detectTapGestures { offset ->
                        if (offset.x < size.width / 2f) onMusic() else onVideo()
                    }
                },
        ) {
            ModeLabel(music, selected = mix < 0.5f, modifier = Modifier.weight(1f))
            ModeLabel(video, selected = mix >= 0.5f, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModeLabel(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = AppMotion.Slide,
        label = "mediaModeLabel",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxHeight()) {
        Text(
            text = label,
            color = lerp(Color.White.copy(alpha = 0.62f), Color.White, fill),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.2.sp,
        )
    }
}
