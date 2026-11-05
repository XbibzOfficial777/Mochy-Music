package com.mochyy.mochymusic.ui.player.share

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer

/** Receipt PNGs need a software bitmap, including when the recorded layer is hardware-backed. */
internal suspend fun GraphicsLayer.toShareBitmap(): Bitmap {
    val bitmap = toImageBitmap().asAndroidBitmap()
    val hardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
    return if (hardware) requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) else bitmap
}
