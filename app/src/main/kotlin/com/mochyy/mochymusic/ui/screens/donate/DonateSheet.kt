// donate sheet: Ko-fi, Saweria, and the embedded QRIS poster

package com.mochyy.mochymusic.ui.screens.donate

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.update.VersionRing
import com.mochyy.mochymusic.ui.screens.update.openUrl
import com.mochyy.mochymusic.ui.utils.AppMotion

private val TitleOverlap = 18.dp

fun PopupSheetState.showDonateSheet() {
    val popup = this
    show(
        fullBleed = true,
        buttonBar = {
            DonatePillButton(text = stringResource(R.string.donate_dismiss), onClick = popup::dismiss, quiet = true)
        },
    ) {
        DonateSheetContent()
    }
}

@Composable
private fun DonateSheetContent() {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var qrisOpen by remember { mutableStateOf(false) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, AppMotion.Appear) }

    fun Modifier.rise(step: Int): Modifier = graphicsLayer {
        val p = ((reveal.value - step * 0.08f) / 0.55f).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * 22.dp.toPx()
        val s = 0.97f + 0.03f * p
        scaleX = s
        scaleY = s
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val characterWidth = characterWidth(maxWidth, maxHeight)

        VersionRing(
            text = "MOCHY MUSIC",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .layout { measurable, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity)
                    val placeable = measurable.measure(loose)
                    layout(0, 0) { placeable.place(x = (-198).dp.roundToPx(), y = (-109).dp.roundToPx()) }
                }
                .graphicsLayer { translationY = -scroll.value.toFloat() },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(CharacterTop))
            DonateCharacter(
                width = characterWidth,
                overlapBelow = TitleOverlap,
                modifier = Modifier.align(Alignment.CenterHorizontally).rise(0),
            )
            Text(
                text = stringResource(R.string.donate_title),
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.2).sp,
                color = Color.White,
                modifier = Modifier.rise(1),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.donate_subtitle, ForkDeveloperName),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = DonateSoftText,
                modifier = Modifier.rise(1),
            )

            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.rise(2)) {
                DonateLinkRow(
                    title = stringResource(R.string.donate_kofi),
                    icon = R.drawable.ic_kofi,
                    iconSize = 22.dp,
                    onClick = { context.openUrl(KofiUrl) },
                )
                DonateLinkRow(
                    title = stringResource(R.string.donate_saweria),
                    icon = R.drawable.ic_saweria,
                    iconSize = 20.dp,
                    onClick = { context.openUrl(SaweriaUrl) },
                )
            }

            Spacer(Modifier.height(14.dp))
            QrisPayCard(
                expanded = qrisOpen,
                onToggle = { qrisOpen = !qrisOpen },
                modifier = Modifier.rise(3),
            )

            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.donate_thanks),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = DonateSoftText,
                modifier = Modifier.rise(4),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun QrisPayCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val sheen = rememberInfiniteTransition(label = "qrisSheen")
    val sheenX by sheen.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "qrisSheenX",
    )
    Column(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.985f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onToggle)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
            ) {
                Text("QR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.donate_qris),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = Color.White.copy(alpha = 0.88f),
                )
                Text(
                    text = stringResource(if (expanded) R.string.donate_qris_close else R.string.donate_qris_hint),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(AppMotion.Appear) + expandVertically(
                spring(dampingRatio = 0.86f, stiffness = 380f),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .graphicsLayer {
                        val p = if (expanded) 1f else 0f
                        scaleX = 0.96f + 0.04f * p
                        scaleY = 0.96f + 0.04f * p
                    }
                    .drawWithContent {
                        drawContent()
                        val x = size.width * sheenX
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to Color.Transparent,
                                0.45f to Color.White.copy(alpha = 0.10f),
                                0.55f to Color.White.copy(alpha = 0.16f),
                                1f to Color.Transparent,
                                start = Offset(x - size.width * 0.35f, 0f),
                                end = Offset(x + size.width * 0.15f, size.height),
                            ),
                        )
                    },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(R.drawable.qris_xbibz)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.donate_qris),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DonateLinkRow(
    title: String,
    @DrawableRes icon: Int,
    iconSize: Dp,
    onClick: () -> Unit,
    subtitle: String? = null,
    iconTint: Color = Color.Unspecified,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = Color.White.copy(alpha = 0.88f),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}
