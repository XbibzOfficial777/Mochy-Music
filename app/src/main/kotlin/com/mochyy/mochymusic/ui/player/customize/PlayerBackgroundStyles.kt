// PlayerBackgroundStyles.kt

package com.mochyy.mochymusic.ui.player.customize

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.ui.utils.rememberMotionAllowed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
            modifier = modifier,
            animate = animate,
        )
        PlayerBackgroundStyle.AMBIENT -> StandaloneAmbientBackground(
            thumbnailUrl = thumbnailUrl,
            modifier = modifier,
        )
    }
}

// the ambient backdrop on its own, from the picture's colours and with its own clock, for previews and the other styles' pages
@Composable
private fun StandaloneAmbientBackground(thumbnailUrl: String?, modifier: Modifier = Modifier) {
    val colors = com.mochyy.mochymusic.ui.player.flagship.rememberAmbientColors()
    com.mochyy.mochymusic.ui.player.flagship.FollowPicture(colors, thumbnailUrl)
    com.mochyy.mochymusic.ui.player.flagship.AmbientBackdrop(
        colors = colors,
        time = com.mochyy.mochymusic.ui.player.flagship.rememberAmbientClock(running = true),
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
            // Blobs can orbit past the canvas edge; a small selector/studio preview must not
            // paint over its neighbours. The full player already clips its outer sheet.
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
private fun AppleMusicBackground(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    animate: Boolean,
) {
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
        // Do image I/O and palette calculation off the UI thread, but publish the result on the
        // composition's dispatcher. Do not swallow cancellation when a cover changes or disappears.
        val colors = try {
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).image?.toBitmap()
            } ?: return@LaunchedEffect // keep the previous palette on a failed load, as before
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

    // Removing the transition also removes its frame clock when this is a static preview, hidden
    // behind video/canvas, or either the app or Android has switched decorative motion off.
    val drift = if (motionAllowed) {
        val transition = rememberInfiniteTransition(label = "appleMusicMorph")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = AppleBlobCycleMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "appleMusicDrift",
        )
    } else null

    // Keep colour state reads in drawWithCache. The 800 ms palette change need not recompose the
    // player on every frame, and the six shaders are reused on ordinary drift frames.
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
    Box(
        modifier = modifier
            .background(base)
            .drawColorBlobs(blobColors) { drift?.value ?: 0f },
    )
}

internal fun Modifier.drawColorBlobs(
    colors: List<State<Color>>,
    phase: () -> Float,
): Modifier = drawWithCache {
    val radii = FloatArray(colors.size) { index -> size.minDimension * (0.55f + 0.08f * (index % 3)) }
    // A zero-size layout cannot be seen; radialGradient requires a positive radius.
    if (size.minDimension <= 0f) return@drawWithCache onDrawBehind {}
    val brushes = colors.mapIndexed { index, colorState ->
        val color = colorState.value
        Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)),
            center = Offset.Zero,
            radius = radii[index],
        )
    }
    val scrim = Brush.verticalGradient(
        listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.45f)),
    )
    onDrawBehind {
        val t = phase()
        brushes.forEachIndexed { index, brush ->
            val angle = appleBlobAngle(t, index)
            val orbitX = size.width * (0.20f + 0.06f * (index % 3))
            val orbitY = size.height * (0.14f + 0.05f * (index % 4))
            val centerX = size.width * (0.28f + 0.15f * (index % 3)) + orbitX * kotlin.math.cos(angle)
            val centerY = size.height * (0.24f + 0.18f * (index % 4)) + orbitY * kotlin.math.sin(angle)
            // Translate a cached origin-centred shader instead of allocating a new one each frame.
            translate(centerX, centerY) {
                drawCircle(brush = brush, radius = radii[index], center = Offset.Zero)
            }
        }
        drawRect(brush = scrim)
    }
}
