package com.mochyy.mochymusic.playback

/**
 * Shared bits the lock-screen / shade media card reads: current lyric line and the last
 * colour taken from the cover, so the card can follow the artwork like Spotify instead of
 * sitting as a flat grey slab.
 */
object NowPlayingChrome {
    @Volatile
    var lyricLine: String? = null

    @Volatile
    var artworkColor: Int? = null

    fun clear() {
        lyricLine = null
        artworkColor = null
    }
}
