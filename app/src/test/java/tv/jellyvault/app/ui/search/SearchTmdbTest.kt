package tv.jellyvault.app.ui.search

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.MediaCard
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

@OptIn(ExperimentalCoroutinesApi::class)
class SearchTmdbTest {
    private val catalog = mockk<CatalogRepository>().also { every { it.isConfigured } returns true }

    private fun card(id: Int) = MediaCard.Remote(TmdbItem(id, TmdbMediaType.MOVIE, "T$id", null, "/p.jpg", null, null, null, emptyList()))

    @Test
    fun blank_query_does_not_call_tmdb() =
        runTest(UnconfinedTestDispatcher()) {
            val search = TmdbSearch(catalog, backgroundScope)
            search.search("   ")
            assertEquals(SearchResult.NoQuery, search.results.value)
            coVerify(exactly = 0) { catalog.searchCards(any()) }
        }

    @Test
    fun results_are_exposed() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { catalog.searchCards("toy story") } returns listOf(card(1), card(2))
            val search = TmdbSearch(catalog, backgroundScope)
            search.search("toy story")
            val result = search.results.value as SearchResult.Success
            assertEquals(2, result.items.size)
        }

    @Test
    fun stale_response_is_ignored() =
        runTest(UnconfinedTestDispatcher()) {
            val slow = CompletableDeferred<List<MediaCard>>()
            coEvery { catalog.searchCards("toy") } coAnswers { slow.await() }
            coEvery { catalog.searchCards("toy story") } returns listOf(card(2))
            val search = TmdbSearch(catalog, backgroundScope)
            search.search("toy")
            search.search("toy story")
            slow.complete(listOf(card(1), card(3), card(4)))
            val result = search.results.value as SearchResult.Success
            assertEquals(1, result.items.size)
        }

    @Test
    fun tmdb_error_becomes_error_result() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { catalog.searchCards(any()) } throws TmdbException("down")
            val search = TmdbSearch(catalog, backgroundScope)
            search.search("toy")
            assertTrue(search.results.value is SearchResult.Error)
        }

    @Test
    fun not_configured_is_no_query() =
        runTest(UnconfinedTestDispatcher()) {
            every { catalog.isConfigured } returns false
            val search = TmdbSearch(catalog, backgroundScope)
            search.search("toy")
            assertEquals(SearchResult.NoQuery, search.results.value)
            coVerify(exactly = 0) { catalog.searchCards(any()) }
        }
}
