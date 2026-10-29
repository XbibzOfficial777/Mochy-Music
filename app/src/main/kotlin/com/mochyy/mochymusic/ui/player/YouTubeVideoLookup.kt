// YouTubeVideoLookup.kt

package com.mochyy.mochymusic.ui.player

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.mochyy.mochymusic.utils.YTPlayerUtils

data class OfficialMusicVideo(
    val videoId: String,
    val stream: YTPlayerUtils.ResolvedVideoStream,
) {
    val streamUrl: String get() = stream.url
}

object YouTubeVideoUrlCache {
    private val cache = ExpiringVideoCache<OfficialMusicVideo>()
    private const val MAX_CACHE_MS = 4 * 60 * 60 * 1000L

    fun key(mediaId: String, maxHeight: Int, self: Boolean): String =
        "${if (self) "self" else "search"}:$maxHeight:$mediaId"

    fun get(key: String): OfficialMusicVideo? = cache.get(key)

    fun put(key: String, video: OfficialMusicVideo?) {
        // A temporary failure must be retryable. URLs expire, so never cache those indefinitely.
        video ?: return
        val lifetime = ((video.stream.expiresInSeconds.toLong() - 90L).coerceAtLeast(0L) * 1000L)
            .coerceAtMost(MAX_CACHE_MS)
        cache.put(key, video, lifetime)
    }

    fun remove(key: String) = cache.remove(key)
}

suspend fun findOfficialMusicVideo(title: String, artist: String, maxHeight: Int): OfficialMusicVideo? {
    val videoId = findOfficialMusicVideoId(title, artist) ?: return null
    val stream = YTPlayerUtils.resolveVideoStream(videoId, maxHeight).getOrNull() ?: return null
    return OfficialMusicVideo(videoId = videoId, stream = stream)
}

// The ID also serves caption lookup without resolving a stream; transient network failures aren't cached.
suspend fun findOfficialMusicVideoId(title: String, artist: String): String? {
    if (title.isBlank() || artist.isBlank()) return null
    val key = "$title|$artist"
    if (OfficialVideoIdCache.contains(key)) return OfficialVideoIdCache.get(key)

    val results = YouTube.search(query = "$title $artist", filter = YouTube.SearchFilter.FILTER_VIDEO)
        .getOrElse { return null }.items

    val normalizedTitle = normalizeCanvasSongTitle(title)
    val normalizedArtist = normalizeCanvasArtistName(artist)

    val match = results
        .filterIsInstance<SongItem>()
        .filter { it.musicVideoType == MUSIC_VIDEO_TYPE_OMV }
        .firstOrNull { candidate ->
            val candidateTitle = normalizeCanvasSongTitle(candidate.title)
            val candidateArtist = normalizeCanvasArtistName(candidate.artists.joinToString { it.name })
            (candidateTitle.contains(normalizedTitle, ignoreCase = true) || normalizedTitle.contains(candidateTitle, ignoreCase = true)) &&
                (candidateArtist.contains(normalizedArtist, ignoreCase = true) || normalizedArtist.contains(candidateArtist, ignoreCase = true))
        }
    OfficialVideoIdCache.put(key, match?.id)
    return match?.id
}

private object OfficialVideoIdCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, String?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun get(key: String): String? = map[key]

    @Synchronized
    fun put(key: String, videoId: String?) {
        map[key] = videoId
        if (map.size > maxSize) map.remove(map.keys.first())
    }
}
