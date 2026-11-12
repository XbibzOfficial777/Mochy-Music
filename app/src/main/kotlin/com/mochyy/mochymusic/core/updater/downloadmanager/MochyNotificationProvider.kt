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
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.palette.graphics.Palette
import com.google.common.collect.ImmutableList
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.NowPlayingChrome

/**
 * MediaStyle card tinted from the current cover, including Android 16.
 *
 * Platform rules that matter here:
 * - From Oreo, a MediaStyle notification with a media session is colourised from
 *   artwork unless the app opts out with [Notification.Builder.setColorized] false.
 *   See Notification.MediaStyle.
 * - Android 16 Live Updates (promoted ongoing) must NOT be colourised and must NOT
 *   use MediaStyle. Requesting promotion on this card would force a grey slab.
 *   See https://developer.android.com/develop/ui/views/notifications/live-update
 *
 * So this provider keeps MediaStyle, sets an explicit palette colour, and never
 * asks to be promoted.
 */
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
            onNotificationChangedCallback.onNotificationChanged(paint(updated))
        }
        return paint(mediaNotification)
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        defaultProvider.handleCustomCommand(session, action, extras)

    private fun paint(mediaNotification: MediaNotification): MediaNotification {
        return runCatching {
            val notification = mediaNotification.notification
            // Compat recover keeps Media3's MediaStyle + session token. Platform
            // recoverBuilder can drop them, and without a session token Android
            // will not colourise the card.
            val builder = NotificationCompat.Builder(context, notification)
            val artwork = usableArtwork(notification)
            val color = NowPlayingChrome.artworkColor
                ?: artwork?.let { colorFromCover(it) }?.also { NowPlayingChrome.artworkColor = it }
            if (color != null) {
                builder.setColor(color)
            }
            builder.setColorized(true)
            if (artwork != null) builder.setLargeIcon(artwork)
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            builder.setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            builder.setSmallIcon(R.drawable.mochy_notification)
            builder.setOngoing(true)
            builder.setOnlyAlertOnce(true)
            builder.setSilent(true)

            val lyric = NowPlayingChrome.lyricLine?.trim()?.takeIf { it.isNotEmpty() }?.take(90)
            if (lyric != null) {
                builder.setSubText(lyric)
            }

            val painted = builder.build()
            if (color != null) {
                painted.color = color
                painted.extras.putBoolean(Notification.EXTRA_COLORIZED, true)
            }
            if (lyric != null) {
                painted.extras.putCharSequence("android.media.metadata.LYRICS", lyric)
            }
            copyMediaSessionToken(notification, painted)
            MediaNotification(mediaNotification.notificationId, painted)
        }.getOrElse { mediaNotification }
    }

    private fun usableArtwork(notification: Notification): Bitmap? {
        val bitmap = largeArtwork(notification) ?: return null
        // CoilBitmapLoader's 64×64 fallback is an empty square; SystemUI's
        // palette then paints the lock-screen card grey.
        if (bitmap.width < 96 || bitmap.height < 96) return null
        return bitmap
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
}
