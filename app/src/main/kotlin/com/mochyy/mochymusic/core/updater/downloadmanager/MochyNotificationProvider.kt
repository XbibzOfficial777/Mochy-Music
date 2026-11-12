package com.mochyy.mochymusic.core.updater.downloadmanager

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.graphics.ColorUtils
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.palette.graphics.Palette
import com.google.common.collect.ImmutableList
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.NowPlayingChrome
import java.util.Locale

@OptIn(UnstableApi::class)
class MochyNotificationProvider(
    private val context: Context,
    notificationIdProvider: DefaultMediaNotificationProvider.NotificationIdProvider,
    channelId: String,
    channelNameResourceId: Int,
) : MediaNotification.Provider {

    private val defaultProvider = DefaultMediaNotificationProvider(
        context,
        notificationIdProvider,
        channelId,
        channelNameResourceId,
    )

    fun setSmallIcon(iconResId: Int): MochyNotificationProvider {
        defaultProvider.setSmallIcon(iconResId)
        return this
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val mediaNotification = defaultProvider.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
        ) { updated ->
            onNotificationChangedCallback.onNotificationChanged(paint(updated, mediaSession))
        }
        return paint(mediaNotification, mediaSession)
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        defaultProvider.handleCustomCommand(session, action, extras)

    private fun paint(mediaNotification: MediaNotification, mediaSession: MediaSession): MediaNotification {
        return runCatching {
            val notification = mediaNotification.notification
            val builder = Notification.Builder.recoverBuilder(context, notification)
            val artwork = largeArtwork(notification)
            val color = NowPlayingChrome.artworkColor
                ?: artwork?.let { colorFromCover(it) }?.also { NowPlayingChrome.artworkColor = it }
            if (color != null) {
                builder.setColor(color)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setColorized(true)
            }
            if (artwork != null) builder.setLargeIcon(artwork)
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)
            builder.setCategory(Notification.CATEGORY_TRANSPORT)
            builder.setSmallIcon(R.drawable.mochy_notification)

            val lyric = NowPlayingChrome.lyricLine?.trim()?.takeIf { it.isNotEmpty() }?.take(90)
            if (lyric != null) {
                builder.setSubText(lyric)
                builder.extras.putCharSequence("android.media.metadata.LYRICS", lyric)
                builder.extras.putCharSequence("androidx.media3.metadata.extras.LYRICS", lyric)
            }

            if (Build.VERSION.SDK_INT >= 36 || Build.VERSION.CODENAME == "Baklava") {
                applyAndroid16Transport(builder, mediaSession.player)
            }

            val painted = builder.build()
            copyMediaSessionToken(notification, painted)
            MediaNotification(mediaNotification.notificationId, painted)
        }.getOrElse { mediaNotification }
    }

    private fun applyAndroid16Transport(builder: Notification.Builder, player: Player) {
        builder.setOngoing(true)
        setRequestPromotedOngoingSafely(builder, true)
        builder.extras.putBoolean("android.requestPromotedOngoing", true)

        val durationMs = player.duration
        val currentPosMs = player.currentPosition
        val isPlaying = player.playWhenReady && player.playbackState == Player.STATE_READY
        val formattedTime = if (durationMs != C.TIME_UNSET && durationMs > 0) {
            val totalSeconds = durationMs / 1000
            String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
        } else {
            null
        }
        if (isPlaying) {
            setShortCriticalTextSafely(builder, NowPlayingChrome.lyricLine ?: formattedTime ?: context.getString(R.string.playing_status))
            if (durationMs != C.TIME_UNSET && durationMs > 0) {
                builder.setWhen(System.currentTimeMillis() + (durationMs - currentPosMs))
                builder.setUsesChronometer(true)
                setChronometerCountDownSafely(builder, true)
            } else {
                builder.setWhen(System.currentTimeMillis())
                builder.setShowWhen(true)
            }
        } else {
            setShortCriticalTextSafely(builder, NowPlayingChrome.lyricLine ?: formattedTime ?: context.getString(R.string.paused_status))
            builder.setShowWhen(false)
            builder.setUsesChronometer(false)
        }
    }

    private fun largeArtwork(notification: Notification): Bitmap? {
        notification.getLargeIcon()?.let { icon ->
            (icon.loadDrawable(context) as? BitmapDrawable)?.bitmap?.takeIf { !it.isRecycled }?.let { return it }
            val drawable = icon.loadDrawable(context) ?: return@let
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            return bitmap
        }
        @Suppress("DEPRECATION")
        notification.extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)?.let { return it }
        return null
    }

    private fun colorFromCover(bitmap: Bitmap): Int {
        val src = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        val palette = Palette.from(src).clearFilters().maximumColorCount(16).generate()
        val pick = palette.getDarkVibrantColor(0).takeIf { it != 0 }
            ?: palette.getVibrantColor(0).takeIf { it != 0 }
            ?: palette.getDarkMutedColor(0).takeIf { it != 0 }
            ?: palette.getDominantColor(0xFF1C1C1E.toInt())
        return ColorUtils.blendARGB(pick, Color.BLACK, 0.18f)
    }

    private fun copyMediaSessionToken(from: Notification, to: Notification) {
        if (Build.VERSION.SDK_INT >= 33) {
            from.extras.getParcelable(
                Notification.EXTRA_MEDIA_SESSION,
                android.media.session.MediaSession.Token::class.java,
            )?.let { to.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, it) }
        } else {
            @Suppress("DEPRECATION")
            from.extras.getParcelable<android.media.session.MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
                ?.let { to.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, it) }
        }
    }

    private fun setShortCriticalTextSafely(builder: Notification.Builder, text: String) {
        try {
            val method = Notification.Builder::class.java.getMethod("setShortCriticalText", CharSequence::class.java)
            method.invoke(builder, text)
        } catch (_: Exception) {
            builder.extras.putCharSequence("android.shortCriticalText", text)
        }
    }

    private fun setChronometerCountDownSafely(builder: Notification.Builder, countDown: Boolean) {
        try {
            val method = Notification.Builder::class.java.getMethod(
                "setChronometerCountDown",
                Boolean::class.javaPrimitiveType,
            )
            method.invoke(builder, countDown)
        } catch (_: Exception) {
            builder.extras.putBoolean("android.chronometerCountDown", countDown)
        }
    }

    private fun setRequestPromotedOngoingSafely(builder: Notification.Builder, promoted: Boolean) {
        val methodNames = arrayOf("setRequestPromotedOngoing", "setPromotedOngoing", "setOngoingActivity")
        for (name in methodNames) {
            try {
                val method = Notification.Builder::class.java.getMethod(name, Boolean::class.javaPrimitiveType)
                method.invoke(builder, promoted)
                return
            } catch (_: Exception) {
            }
        }
    }
}
