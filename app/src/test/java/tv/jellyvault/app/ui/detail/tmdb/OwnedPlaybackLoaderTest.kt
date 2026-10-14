package tv.jellyvault.app.ui.detail.tmdb

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.data.ChosenStreams
import tv.jellyvault.app.data.model.BaseItem
import java.util.UUID

class OwnedPlaybackLoaderTest {
    private fun item(kind: BaseItemKind) = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = kind))

    private class FakeSource(
        val full: (UUID) -> BaseItem = { error("unexpected") },
        val next: BaseItem? = null,
        val streams: ChosenStreams? = null,
        val failStreams: Boolean = false,
    ) : OwnedPlaybackSource {
        override suspend fun fullItem(id: UUID) = full(id)

        override suspend fun nextEpisode(seriesId: UUID) = next

        override suspend fun chosenStreams(item: BaseItem): ChosenStreams? {
            if (failStreams) throw java.io.IOException("offline")
            return streams
        }
    }

    @Test
    fun a_movie_is_reloaded_in_full_with_its_streams() =
        runTest {
            val listItem = item(BaseItemKind.MOVIE)
            val full = item(BaseItemKind.MOVIE)
            val streams = mockk<ChosenStreams>()
            val playback = OwnedPlaybackLoader(FakeSource(full = { full }, streams = streams)).load(listItem)!!
            assertEquals(full, playback.item)
            assertEquals(streams, playback.chosenStreams)
            assertTrue(!playback.isEpisode)
        }

    @Test
    fun a_series_plays_its_next_episode() =
        runTest {
            val episode = item(BaseItemKind.EPISODE)
            val playback = OwnedPlaybackLoader(FakeSource(next = episode)).load(item(BaseItemKind.SERIES))!!
            assertEquals(episode, playback.item)
            assertTrue(playback.isEpisode)
            // The series could not be reloaded: the listed item stands in
            assertEquals(BaseItemKind.SERIES, playback.title.type)
        }

    @Test
    fun a_series_keeps_its_full_item_for_watched_and_favorite() =
        runTest {
            val listed = item(BaseItemKind.SERIES)
            val series = BaseItem(BaseItemDto(id = listed.id, type = BaseItemKind.SERIES, name = "full"))
            val playback = OwnedPlaybackLoader(FakeSource(full = { series }, next = item(BaseItemKind.EPISODE))).load(listed)!!
            assertEquals(series, playback.title)
        }

    @Test
    fun a_series_without_episodes_gives_null() =
        runTest {
            assertNull(OwnedPlaybackLoader(FakeSource(next = null)).load(item(BaseItemKind.SERIES)))
        }

    @Test
    fun a_failing_fetch_gives_null() =
        runTest {
            val loader = OwnedPlaybackLoader(FakeSource(full = { throw java.io.IOException("offline") }))
            assertNull(loader.load(item(BaseItemKind.MOVIE)))
        }

    @Test
    fun failing_streams_still_give_a_playable_item() =
        runTest {
            val full = item(BaseItemKind.MOVIE)
            val playback = OwnedPlaybackLoader(FakeSource(full = { full }, failStreams = true)).load(item(BaseItemKind.MOVIE))!!
            assertEquals(full, playback.item)
            assertNull(playback.chosenStreams)
        }
}
