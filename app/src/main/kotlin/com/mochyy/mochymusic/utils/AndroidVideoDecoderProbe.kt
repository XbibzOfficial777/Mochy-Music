package com.mochyy.mochymusic.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.music.innertube.models.response.PlayerResponse

/**
 * Avoid requesting a codec/size the device cannot decode. For ordinary sizes a broken query
 * fails open and ExoPlayer can fall back; above 1080p, as in upstream, require a known hardware
 * decoder so an unsupported 4K stream does not overwhelm a software decoder.
 */
internal class AndroidVideoDecoderProbe {
    private val decoders = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
    }.getOrNull()
    private val answers = mutableMapOf<String, Boolean>()

    fun supports(format: PlayerResponse.StreamingData.Format): Boolean {
        val width = format.width ?: return false
        val height = format.height ?: return false
        val sharp = minOf(width, height) > 1080
        val mime = videoSampleMime(format.mimeType) ?: return !sharp
        val fps = format.fps
        val key = "$mime:$width:$height:$fps"
        return answers.getOrPut(key) {
            val available = decoders ?: return@getOrPut !sharp
            var queryFailed = false
            val supported = available.any { decoder ->
                try {
                    if (sharp && !decoder.isHardware()) return@any false
                    val type = decoder.supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) }
                        ?: return@any false
                    val caps = decoder.getCapabilitiesForType(type).videoCapabilities
                    if (fps != null && fps > 0) {
                        caps.areSizeAndRateSupported(width, height, fps.toDouble()) ||
                            caps.areSizeAndRateSupported(height, width, fps.toDouble())
                    } else {
                        caps.isSizeSupported(width, height) || caps.isSizeSupported(height, width)
                    }
                } catch (_: Exception) {
                    queryFailed = true
                    false
                }
            }
            supported || (!sharp && queryFailed)
        }
    }

    private fun MediaCodecInfo.isHardware(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return isHardwareAccelerated
        val lower = name.lowercase()
        return !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.") && !lower.contains(".sw.")
    }
}
