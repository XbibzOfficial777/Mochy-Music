// AppThemePicker.kt
//
// Settings > Appearance > Theme: the app's three looks side by side, each card a little phone
// showing what the theme does to the chrome - a mini player and the nav bar over some colour - so
// you see the glass before picking it.

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AppTheme
import com.mochyy.mochymusic.ui.theme.InterFontFamily

private val CardShape = RoundedCornerShape(20.dp)
private val ChromeDark = Color(0xFF131313)

@Composable
fun AppThemePicker(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppTheme.entries.forEach { theme ->
            ThemeCard(
                theme = theme,
                selected = theme == selected,
                onClick = { onSelect(theme) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeCard(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "themeCardScale",
    )
    val border by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.10f),
        label = "themeCardBorder",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(CardShape)
                .border(if (selected) 2.dp else 1.dp, border, CardShape),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawThemePreview(theme) }
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.displayName,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

/** A tiny phone: some page colour, a mini player and the nav bar, drawn the way [theme] draws them. */
private fun DrawScope.drawThemePreview(theme: AppTheme) {
    // the page: dark, with two soft blobs of colour so the glass has something to show
    drawRect(Color(0xFF0B0B0D))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFF5E8A).copy(alpha = 0.85f), Color.Transparent),
            center = Offset(size.width * 0.25f, size.height * 0.62f),
            radius = size.width * 0.55f,
        ),
        radius = size.width * 0.55f,
        center = Offset(size.width * 0.25f, size.height * 0.62f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF6C7BFF).copy(alpha = 0.8f), Color.Transparent),
            center = Offset(size.width * 0.8f, size.height * 0.8f),
            radius = size.width * 0.5f,
        ),
        radius = size.width * 0.5f,
        center = Offset(size.width * 0.8f, size.height * 0.8f),
    )
    // a few lines of "content"
    val line = Color.White.copy(alpha = 0.18f)
    val margin = size.width * 0.1f
    repeat(3) { i ->
        drawRoundRect(
            color = line,
            topLeft = Offset(margin, size.height * (0.14f + i * 0.09f)),
            size = Size(size.width * (if (i == 0) 0.55f else 0.75f), size.height * 0.035f),
            cornerRadius = CornerRadius(size.height * 0.02f),
        )
    }

    val barHeight = size.height * 0.11f
    val miniTop = size.height * 0.68f
    val barTop = size.height * 0.83f
    drawChrome(theme, Offset(margin * 0.6f, miniTop), Size(size.width - margin * 1.2f, barHeight))
    drawChrome(theme, Offset(margin * 0.6f, barTop), Size(size.width - margin * 1.2f, barHeight))

    // the mini player's cover and the bar's four tabs, with the lit one's pill
    val barWidth = size.width - margin * 1.2f
    val cover = barHeight * 0.64f
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color(0xFFFFB86B), Color(0xFFFF5E8A))),
        topLeft = Offset(margin * 0.6f + barHeight * 0.18f, miniTop + (barHeight - cover) / 2f),
        size = Size(cover, cover),
        cornerRadius = CornerRadius(cover * 0.25f),
    )
    val slot = barWidth / 4f
    val pillInset = barHeight * 0.16f
    val pillTopLeft = Offset(margin * 0.6f + pillInset * 0.6f, barTop + pillInset)
    val pillSize = Size(slot - pillInset * 0.4f, barHeight - pillInset * 2f)
    drawLitPill(theme, pillTopLeft, pillSize)
    repeat(4) { i ->
        drawCircle(
            color = Color.White.copy(alpha = if (i == 0) 1f else 0.6f),
            radius = barHeight * 0.11f,
            center = Offset(margin * 0.6f + slot * (i + 0.5f), barTop + barHeight / 2f),
        )
    }
}

private fun DrawScope.drawChrome(theme: AppTheme, topLeft: Offset, chromeSize: Size) {
    val corner = CornerRadius(chromeSize.height / 2f)
    when (theme) {
        AppTheme.FROSTED -> {
            drawRoundRect(ChromeDark.copy(alpha = 0.82f), topLeft, chromeSize, corner)
            drawRoundRect(Color.White.copy(alpha = 0.06f), topLeft, chromeSize, corner, style = Stroke(1.dp.toPx()))
        }
        AppTheme.PURE_BLACK -> {
            drawRoundRect(Color.Black, topLeft, chromeSize, corner)
            drawRoundRect(Color.White.copy(alpha = 0.06f), topLeft, chromeSize, corner, style = Stroke(1.dp.toPx()))
        }
        AppTheme.LIQUID_GLASS -> {
            drawRoundRect(ChromeDark.copy(alpha = 0.42f), topLeft, chromeSize, corner)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.5f to Color.Transparent,
                    startY = topLeft.y,
                    endY = topLeft.y + chromeSize.height,
                ),
                topLeft = topLeft,
                size = chromeSize,
                cornerRadius = corner,
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.5f),
                    0.4f to Color.White.copy(alpha = 0.05f),
                    1f to Color.White.copy(alpha = 0.2f),
                    start = topLeft,
                    end = Offset(topLeft.x + chromeSize.width, topLeft.y + chromeSize.height),
                ),
                topLeft = topLeft,
                size = chromeSize,
                cornerRadius = corner,
                style = Stroke(1.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawLitPill(theme: AppTheme, topLeft: Offset, pillSize: Size) {
    val corner = CornerRadius(pillSize.height / 2f)
    drawRoundRect(Color.White.copy(alpha = if (theme == AppTheme.LIQUID_GLASS) 0.08f else 0.12f), topLeft, pillSize, corner)
    if (theme != AppTheme.LIQUID_GLASS) return
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.6f),
            0.45f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.25f),
            start = topLeft,
            end = Offset(topLeft.x + pillSize.width, topLeft.y + pillSize.height),
        ),
        topLeft = topLeft,
        size = pillSize,
        cornerRadius = corner,
        style = Stroke(0.8.dp.toPx()),
    )
}
