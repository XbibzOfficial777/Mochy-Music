package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R

/*
 * "Music" and "Video" as one two-way switch, so the player's two modes are a single tap apart
 * instead of two trips into the ••• menu ("See video", "Full video").
 *
 * Where it appears:
 *   * in the player, over the cover at the top centre while the song has a video to show, and
 *   * in the full video mode, so "Music" brings the ordinary player straight back.
 * The choice is remembered across songs through the same preference the chevron uses
 * (VideoModeReturnStyleKey), so leaving video mode returns to the style that was on before it.
 *
 * Deliberately light: no blur and no backdrop capture, just a translucent pill with an animated
 * highlight, so it costs nothing on top of the glass the player already draws.
 */

private val PillHeight = 30.dp
private val PillCorner = 15.dp
private val SegmentCorner = 12.dp

@Composable
internal fun MediaModeToggle(
    videoSelected: Boolean,
    onMusic: () -> Unit,
    onVideo: () -> Unit,
    modifier: Modifier = Modifier,
    /** fades the whole switch in and out instead of popping it */
    visible: Boolean = true,
) {
    val shown by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "mediaModeShown",
    )
    if (shown <= 0.01f) return
    val music = stringResource(R.string.player_mode_music)
    val video = stringResource(R.string.player_mode_video)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .graphicsLayer {
                alpha = shown
                // a small settle on the way in: alive, not bouncy
                translationY = (1f - shown) * -6.dp.toPx()
            }
            .defaultMinSize(minHeight = PillHeight)
            .clip(RoundedCornerShape(PillCorner))
            .background(Color.Black.copy(alpha = 0.34f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(PillCorner),
            )
            .padding(3.dp)
            .semantics {
                role = Role.Tab
                contentDescription = if (videoSelected) video else music
            },
    ) {
        MediaModeSegment(
            label = music,
            selected = !videoSelected,
            onClick = { if (videoSelected) onMusic() },
        )
        MediaModeSegment(
            label = video,
            selected = videoSelected,
            onClick = { if (!videoSelected) onVideo() },
        )
    }
}

@Composable
private fun MediaModeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // the highlight cross-fades rather than switching, so the eye follows the choice
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
        label = "mediaModeFill",
    )
    // white on the pill, near-black once the segment has the white fill under it
    val textColor = lerp(Color.White, Color(0xFF111114), fill)
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1200f),
        label = "mediaModePress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(RoundedCornerShape(SegmentCorner))
            .background(Color.White.copy(alpha = 0.92f * fill))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            // the unfilled side stays legible over bright artwork without a second background
            modifier = Modifier.alpha(if (selected) 1f else 0.86f),
        )
    }
}
