package com.mochyy.mochymusic.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochyy.mochymusic.db.entities.AlbumEntity
import com.mochyy.mochymusic.db.entities.SongAlbumMap
import com.mochyy.mochymusic.db.entities.SongEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumOrderTest {
    private lateinit var room: InternalDatabase
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), InternalDatabase::class.java,
        ).allowMainThreadQueries().build()
        database = MusicDatabase(room)
        database.insert(AlbumEntity(id = "album", title = "Album", songCount = 3, duration = 30, isLocal = true))
        // Files can be discovered or imported in any order; the tags say 1, 2, 3.
        listOf(2, 3, 1).forEach { track ->
            val id = "track$track"
            database.insert(SongEntity(id = id, title = id, duration = 10, isLocal = true))
            database.insert(SongAlbumMap(songId = id, albumId = "album", index = 1000 + track))
        }
    }

    @After
    fun tearDown() = room.close()

    @Test
    fun albumPageAndPlaybackQueueFollowTrackNumbers() = runBlocking {
        val album = database.albumWithSongs("album").first()
        assertEquals(listOf("track1", "track2", "track3"), album?.songs?.map { it.id })
    }

    @Test
    fun mediaLibraryAndAlbumMenuFollowTrackNumbers() = runBlocking {
        assertEquals(listOf("track1", "track2", "track3"), database.albumSongs("album").first().map { it.id })
    }

    @Test
    fun trackOrderIsSpecificToEachAlbum() = runBlocking {
        database.insert(AlbumEntity(id = "other", title = "Other", songCount = 3, duration = 30))
        database.insert(SongAlbumMap("track1", "other", 2))
        database.insert(SongAlbumMap("track2", "other", 0))
        database.insert(SongAlbumMap("track3", "other", 1))
        val expected = listOf("track2", "track3", "track1")
        assertEquals(expected, database.albumSongs("other").first().map { it.id })
        assertEquals(expected, database.albumWithSongs("other").first()?.songs?.map { it.id })
        assertEquals(listOf("track1", "track2", "track3"), database.albumSongs("album").first().map { it.id })
    }

    @Test
    fun equalTrackNumbersKeepTheirInsertionOrder() = runBlocking {
        database.upsert(SongAlbumMap("track1", "album", 1999))
        database.upsert(SongAlbumMap("track2", "album", 1999))
        database.upsert(SongAlbumMap("track3", "album", 1999))
        val expected = listOf("track2", "track3", "track1")
        assertEquals(expected, database.albumSongs("album").first().map { it.id })
        assertEquals(expected, database.albumWithSongs("album").first()?.songs?.map { it.id })
    }

    @Test
    fun observedAlbumUpdatesWhenTrackPositionsChange() = runBlocking {
        val expected = listOf("track3", "track1", "track2")
        val changed = async {
            withTimeout(10_000) {
                database.albumWithSongs("album").first { it?.songs?.map { song -> song.id } == expected }
            }
        }
        database.upsert(SongAlbumMap("track3", "album", 1000))
        assertEquals(expected, changed.await()?.songs?.map { it.id })
    }

    @Test
    fun absentAlbumDoesNotInventSongs() = runBlocking {
        assertNull(database.albumWithSongs("absent").first())
        assertEquals(emptyList<String>(), database.albumSongs("absent").first().map { it.id })
    }
}
