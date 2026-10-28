package com.mochyy.mochymusic.utils

import com.mochyy.mochymusic.constants.VideoQuality

/**
 * Rank the supplied formats by resolution and then bitrate. The cap is a real cap: choosing 360p
 * never silently downloads a 4K stream. Use the short edge for portrait videos as well as landscape.
 * URL resolution and decoder probing are injected so this policy can be tested without Android.
 */
internal fun <T> rankVideoFormats(
    formats: List<T>,
    maxHeight: Int,
    width: (T) -> Int?,
    height: (T) -> Int?,
    bitrate: (T) -> Int,
    hasUrl: (T) -> Boolean,
    supported: (T) -> Boolean,
): List<T> = formats.asSequence()
    .filter { candidate ->
        val w = width(candidate) ?: return@filter false
        val h = height(candidate) ?: return@filter false
        w > 0 && h > 0 && minOf(w, h) <= maxHeight && hasUrl(candidate) && supported(candidate)
    }
    .sortedWith(compareByDescending<T> { minOf(width(it)!!, height(it)!!) }.thenByDescending(bitrate))
    .toList()

/** Android decoders name the elementary codec, not the MP4/WebM container. Unknowns are tried. */
internal fun videoSampleMime(mimeType: String): String? {
    val codec = Regex("codecs\\s*=\\s*[\"']?([^\"',;\\s]+)", RegexOption.IGNORE_CASE)
        .find(mimeType)?.groupValues?.get(1)?.lowercase() ?: return null
    return when {
        codec.startsWith("avc1") || codec.startsWith("avc3") -> "video/avc"
        codec.startsWith("av01") -> "video/av01"
        codec.startsWith("hvc1") || codec.startsWith("hev1") -> "video/hevc"
        codec.startsWith("vp09") || codec == "vp9" -> "video/x-vnd.on2.vp9"
        codec.startsWith("vp08") || codec == "vp8" -> "video/x-vnd.on2.vp8"
        else -> null
    }
}

/** After a decoder/network failure, don't retry the same resolution indefinitely. */
internal fun nextLowerVideoLimit(failedHeight: Int, currentLimit: Int): Int? =
    VideoQuality.entries.lastOrNull { it.maxHeight < minOf(failedHeight, currentLimit) }?.maxHeight
