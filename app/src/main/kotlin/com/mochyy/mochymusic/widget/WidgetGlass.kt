package com.mochyy.mochymusic.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap

/**
 * Pre-renders the liquid-glass card the home-screen widget uses. RemoteViews cannot
 * run shaders, so the frost is a downscaled cover, tinted 46%, with a 1.2 dp rim
 * lit from the top-left — the same recipe as in-app glass, cheap enough for a widget.
 */
object WidgetGlass {
    fun frosted(
        cover: Bitmap,
        widthPx: Int,
        heightPx: Int,
        cornerPx: Float,
        rimPx: Float,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(8)
        val h = heightPx.coerceAtLeast(8)
        val tinyW = 48
        val tinyH = (48f * h / w).toInt().coerceIn(24, 64)
        val tiny = Bitmap.createScaledBitmap(cover, tinyW, tinyH, true)
        val stretched = Bitmap.createScaledBitmap(tiny, w, h, true)
        if (tiny != cover && tiny != stretched) tiny.recycle()

        val out = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val round = Path().apply {
            addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), cornerPx, cornerPx, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(round)
        canvas.drawBitmap(stretched, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        if (stretched != cover) stretched.recycle()
        // 46% liquid tint
        canvas.drawColor(Color.argb(117, 8, 10, 18))
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w * 0.55f, h * 0.45f,
                Color.argb(48, 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sheen)
        canvas.restore()
        val inset = rimPx / 2f
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rimPx
            color = Color.argb(72, 255, 255, 255)
        }
        canvas.drawRoundRect(
            RectF(inset, inset, w - inset, h - inset),
            (cornerPx - inset).coerceAtLeast(0f),
            (cornerPx - inset).coerceAtLeast(0f),
            rim,
        )
        return out
    }

}
