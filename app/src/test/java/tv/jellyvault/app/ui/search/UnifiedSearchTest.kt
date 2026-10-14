package tv.jellyvault.app.ui.search

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.data.model.BaseItem
import java.util.UUID

class UnifiedSearchTest {
    private fun item(
        kind: BaseItemKind,
        id: UUID = UUID.randomUUID(),
    ) = BaseItem(BaseItemDto(id = id, type = kind))

    @Test
    fun tmdb_first_then_jellyfin_movies_and_series_not_already_shown() {
        val owned = item(BaseItemKind.MOVIE)
        val remote = item(BaseItemKind.SERIES)
        val homeVideo = item(BaseItemKind.MOVIE)
        val person = item(BaseItemKind.PERSON)
        val episode = item(BaseItemKind.EPISODE)
        val jellyfinCopyOfOwned = item(BaseItemKind.MOVIE, owned.id)
        val merged =
            mergeSearchResults(
                jellyfin = SearchResult.Success(listOf(person, jellyfinCopyOfOwned, homeVideo, episode)),
                tmdb = SearchResult.Success(listOf(owned, remote)),
            ) as SearchResult.Success
        assertEquals(listOf(owned.id, remote.id, homeVideo.id), merged.items.map { it!!.id })
    }

    @Test
    fun still_searching_while_either_side_searches() {
        assertEquals(SearchResult.Searching, mergeSearchResults(SearchResult.Searching, SearchResult.Success(emptyList())))
        assertEquals(SearchResult.Searching, mergeSearchResults(SearchResult.Success(emptyList()), SearchResult.Searching))
    }

    @Test
    fun one_side_failing_keeps_the_other() {
        val movie = item(BaseItemKind.MOVIE)
        val fromJellyfin =
            mergeSearchResults(
                SearchResult.Success(listOf(movie)),
                SearchResult.Error(RuntimeException()),
            ) as SearchResult.Success
        assertEquals(listOf(movie.id), fromJellyfin.items.map { it!!.id })
        val fromTmdb =
            mergeSearchResults(
                SearchResult.Error(RuntimeException()),
                SearchResult.Success(listOf(movie)),
            ) as SearchResult.Success
        assertEquals(listOf(movie.id), fromTmdb.items.map { it!!.id })
        assertTrue(mergeSearchResults(SearchResult.Error(RuntimeException()), SearchResult.Error(RuntimeException())) is SearchResult.Error)
    }

    @Test
    fun tmdb_not_configured_means_jellyfin_only_and_no_query_stays_no_query() {
        val movie = item(BaseItemKind.MOVIE)
        val merged = mergeSearchResults(SearchResult.Success(listOf(movie)), SearchResult.NoQuery) as SearchResult.Success
        assertEquals(1, merged.items.size)
        assertEquals(SearchResult.NoQuery, mergeSearchResults(SearchResult.NoQuery, SearchResult.NoQuery))
    }
}
