package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.ChromeShadow
import com.mochyy.mochymusic.ui.component.FrostedChromeGlass
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.NavBarShape
import com.mochyy.mochymusic.ui.component.drawChromeOutline
import com.mochyy.mochymusic.ui.component.liquidTabIndicator
import com.mochyy.mochymusic.ui.component.liquidTabSlide
import com.mochyy.mochymusic.ui.component.rememberChromeAccentWash
import com.mochyy.mochymusic.ui.component.rememberLiquidTabState
import com.mochyy.mochymusic.ui.utils.AppMotion

/*
 * Music / Video uses the same control as the dashboard tab bar: frosted chrome track + a liquid
 * glass drop that flows between the two labels (liquidTabIndicator / liquidTabSlide).
 *
 * The track frosts the player's cover capture (a sibling of this overlay, not nested glassRoot).
 */

private val TrackHeight = 44.dp
private val TrackWidth = 220.dp

@Composable
internal fun MediaModeToggle(
    videoSelected: Boolean,
    onMusic: () -> Unit,
    onVideo: () -> Unit,
    glassState: GlassState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    pureBlack: Boolean = false,
) {
    val shown by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.Appear,
        label = "mediaModeShown",
    )
    if (shown <= 0.01f) return
    val music = stringResource(R.string.player_mode_music)
    val video = stringResource(R.string.player_mode_video)
    val selectedIndex = if (videoSelected) 1 else 0
    val liquid = rememberLiquidTabState(selectedIndex)
    val accentWash = rememberChromeAccentWash()

    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = liquid.barScale
                alpha = shown
                translationY = (1f - shown) * -8.dp.toPx()
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                role = Role.Switch
                contentDescription = if (videoSelected) video else music
            },
    ) {
        Box(
            modifier = Modifier
                .height(TrackHeight)
                .width(TrackWidth)
                .dropShadow(NavBarShape, ChromeShadow)
                .clip(NavBarShape)
                .drawWithContent {
                    drawContent()
                    drawChromeOutline(Offset.Zero, size, size.height / 2f)
                },
        ) {
            FrostedChromeGlass(
                state = glassState,
                pureBlack = pureBlack,
                accentWash = accentWash,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .liquidTabIndicator(liquid, 2)
                    .liquidTabSlide(
                        state = liquid,
                        tabCount = 2,
                        selectedIndex = selectedIndex,
                        onSlideTo = { index -> if (index == 0) onMusic() else onVideo() },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeLabel(
                    label = music,
                    lit = { liquid.litOf(0) },
                    onClick = onMusic,
                    modifier = Modifier.weight(1f),
                )
                ModeLabel(
                    label = video,
                    lit = { liquid.litOf(1) },
                    onClick = onVideo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ModeLabel(
    label: String,
    lit: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = lit()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
    ) {
        Text(
            text = label,
            color = lerp(Color.White.copy(alpha = 0.55f), Color.White, fill),
            fontSize = 13.sp,
            fontWeight = if (fill > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.2.sp,
        )
    }
}
