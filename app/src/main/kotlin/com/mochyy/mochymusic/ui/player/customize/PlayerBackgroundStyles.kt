// PlayerBackgroundStyles.kt

package com.mochyy.mochymusic.ui.player.customize

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.ui.player.flagship.downscaledLayer
import com.mochyy.mochymusic.ui.utils.rememberMotionAllowed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun PlayerBackgroundContent(
    style: PlayerBackgroundStyle,
    thumbnailUrl: String?,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    when (style) {
        PlayerBackgroundStyle.COVER_GRADIENT -> Unit
        PlayerBackgroundStyle.SOLID -> SolidBackground(pureBlack = pureBlack, modifier = modifier)
        PlayerBackgroundStyle.DARK_GRADIENT -> DarkGradientBackground(modifier = modifier)
        PlayerBackgroundStyle.APPLE_MUSIC -> AppleMusicBackground(
            thumbnailUrl = thumbnailUrl,
            animate = animate,
            modifier = modifier,
        )
        PlayerBackgroundStyle.AMBIENT -> StandaloneAmbientBackground(
            thumbnailUrl = thumbnailUrl,
            animate = animate,
            modifier = modifier,
        )
    }
}

// the ambient backdrop on its own, from the picture's colours and with its own clock, for previews and the other styles' pages
@Composable
private fun StandaloneAmbientBackground(thumbnailUrl: String?, animate: Boolean, modifier: Modifier = Modifier) {
    val colors = com.mochyy.mochymusic.ui.player.flagship.rememberAmbientColors()
    com.mochyy.mochymusic.ui.player.flagship.FollowPicture(colors, thumbnailUrl)
    com.mochyy.mochymusic.ui.player.flagship.AmbientBackdrop(
        colors = colors,
        time = com.mochyy.mochymusic.ui.player.flagship.rememberAmbientClock(running = animate),
        modifier = modifier,
    )
}

@Composable
fun PlayerBackgroundPreview(
    style: PlayerBackgroundStyle,
    thumbnailUrl: String?,
    width: Dp,
    height: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,

    warp: Boolean = true,
    /** a clock shared by several of these (crossfading copies), so they don't each run their own */
    timeProvider: (() -> Float)? = null,
) {
    if (style != PlayerBackgroundStyle.COVER_GRADIENT) {
        PlayerBackgroundContent(
            style = style,
            thumbnailUrl = thumbnailUrl,
            pureBlack = false,
            // Blob previews may orbit beyond the canvas; keep them inside their card.
            modifier = modifier.clipToBounds(),
            animate = animate,
        )
        return
    }
    Box(modifier = modifier.background(Color(0xFF1A1A1C))) {
        CoverGradientBackdrop(
            thumbnailUrl = thumbnailUrl,
            width = width,
            height = height,
            animate = animate,
            shader = if (warp) rememberWarpShader() else null,
            timeProvider = timeProvider,
        )
    }
}

@Composable
private fun SolidBackground(pureBlack: Boolean, modifier: Modifier = Modifier) {
    val color = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    Box(modifier = modifier.background(color))
}

@Composable
private fun DarkGradientBackground(modifier: Modifier = Modifier) {
    val brush = remember {
        Brush.verticalGradient(
            0f to Color(0xFF2A2A2E),
            0.55f to Color(0xFF1A1A1D),
            1f to Color(0xFF0E0E10),
        )
    }
    Box(modifier = modifier.background(brush))
}

@Composable
private fun AppleMusicBackground(thumbnailUrl: String?, animate: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fallbackColorInt = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val motionAllowed = rememberMotionAllowed() && animate
    var palette by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(thumbnailUrl, fallbackColorInt) {
        if (thumbnailUrl == null) {
            palette = emptyList()
            return@LaunchedEffect
        }
        // Keep image I/O and palette extraction off the UI thread; don't turn cancellation into a
        // failed palette when the cover changes or this preview is removed.
        val colors = try {
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).image?.toBitmap()
            } ?: return@LaunchedEffect
            withContext(Dispatchers.Default) {
                val generated = Palette.from(bitmap)
                    .maximumColorCount(8)
                    .resizeBitmapArea(100 * 100)
                    .generate()
                com.mochyy.mochymusic.ui.theme.PlayerColorExtractor.extractAppleMusicColors(
                    palette = generated,
                    fallbackColor = fallbackColorInt,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (colors != null) palette = colors
    }

    // A 30 Hz clock pauses when the preview/player is hidden or decorative motion is disabled.
    val drift = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motionAllowed) {
        if (!motionAllowed) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withInfiniteAnimationFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    drift.floatValue = (drift.floatValue + dt * BlobSpeedRadPerSec) % TwoPi
                }
                last = now
            }
            delay(BlobWakeMillis)
        }
    }

    // Keep the six palette transitions/shaders cached, and don't animate them in a static preview.
    val blobColors = List(AppleBlobCount) { index ->
        val target = palette.getOrNull(index) ?: surfaceColor
        if (motionAllowed) {
            animateColorAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 800),
                label = "appleMusicBlob$index",
            )
        } else rememberUpdatedState(target)
    }
    val base = palette.firstOrNull() ?: surfaceColor

    Spacer(
        modifier = modifier
            .downscaledLayer()
            .drawColorBlobs(base, blobColors) { drift.floatValue },
    )
}

private const val TwoPi = (2.0 * Math.PI).toFloat()
private const val BlobSpeedRadPerSec = TwoPi / (AppleBlobCycleMs / 1000f)
private const val BlobWakeMillis = 32L

// Prebuild radial brushes once per palette change and only translate their origin-centred shaders
// on each throttled frame. This keeps the full-resolution player from paying 120 Hz for slow drift.
internal fun Modifier.drawColorBlobs(
    base: Color,
    colors: List<State<Color>>,
    phase: () -> Float,
): Modifier = drawWithCache {
    val radii = FloatArray(colors.size) { index -> size.minDimension * (0.55f + 0.08f * (index % 3)) }
    if (size.minDimension <= 0f) return@drawWithCache onDrawBehind {}
    val brushes = colors.mapIndexed { index, colorState ->
        val color = colorState.value
        Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)),
            center = Offset.Zero,
            radius = radii[index],
        )
    }
    val veil = Brush.verticalGradient(
        listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.45f)),
    )
    onDrawBehind {
        drawRect(base)
        val t = phase()
        for (index in brushes.indices) {
            val angle = appleBlobAngle(t, index)
            val orbitX = size.width * (0.20f + 0.06f * (index % 3))
            val orbitY = size.height * (0.14f + 0.05f * (index % 4))
            val centerX = size.width * (0.28f + 0.15f * (index % 3)) + orbitX * kotlin.math.cos(angle)
            val centerY = size.height * (0.24f + 0.18f * (index % 4)) + orbitY * kotlin.math.sin(angle)
            translate(centerX, centerY) {
                drawCircle(brush = brushes[index], radius = radii[index], center = Offset.Zero)
            }
        }
        drawRect(brush = veil)
    }
}
