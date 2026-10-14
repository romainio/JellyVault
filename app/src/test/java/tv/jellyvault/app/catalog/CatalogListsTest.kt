package tv.jellyvault.app.catalog

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.library.LibraryItemSource
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbListType
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbResultPage
import java.util.UUID

class CatalogListsTest {
    private val tmdb = mockk<TmdbClient>()
    private val index = mockk<LibraryIndex>()
    private val source = mockk<LibraryItemSource>()
    private val repo = CatalogRepository(tmdb, index, source, mockk(relaxed = true))

    private fun item(
        id: Int,
        type: TmdbMediaType,
    ) = TmdbItem(id, type, "T$id", null, "/p.jpg", null, null, null, emptyList())

    @Test
    fun list_row_marks_owned_titles() =
        runTest {
            val owned = UUID.randomUUID()
            coEvery { tmdb.list(TmdbListType.POPULAR, TmdbMediaType.MOVIE, 1) } returns
                TmdbResultPage(listOf(item(1, TmdbMediaType.MOVIE), item(2, TmdbMediaType.MOVIE)), 1, 10)
            coEvery { index.awaitIndex(any()) } returns mapOf(TmdbKey(TmdbMediaType.MOVIE, 2) to owned)
            coEvery { source.fetchItems(listOf(owned)) } returns listOf(BaseItemDto(id = owned, type = BaseItemKind.MOVIE))

            val cards = repo.listRow(TmdbListType.POPULAR, TmdbMediaType.MOVIE)
            assertTrue(cards[0] is MediaCard.Remote)
            assertTrue(cards[1] is MediaCard.Owned)
        }

    @Test
    fun search_cards_keep_movies_and_series() =
        runTest {
            coEvery { tmdb.search("toy") } returns TmdbResultPage(listOf(item(1, TmdbMediaType.MOVIE), item(1, TmdbMediaType.TV)), 1, 1)
            coEvery { index.awaitIndex(any()) } returns emptyMap()
            val cards = repo.searchCards("toy")
            assertEquals(listOf(TmdbMediaType.MOVIE, TmdbMediaType.TV), cards.map { it.tmdb!!.mediaType })
        }

    @Test
    fun genre_page_returns_cards_and_total_pages() =
        runTest {
            coEvery { tmdb.discoverPage(TmdbMediaType.MOVIE, 28, 3) } returns TmdbResultPage(listOf(item(5, TmdbMediaType.MOVIE)), 3, 42)
            coEvery { index.awaitIndex(any()) } returns emptyMap()
            val (cards, total) = repo.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 3)
            assertEquals(1, cards.size)
            assertEquals(42, total)
        }

    @Test
    fun series_genre_without_tv_id_is_empty_without_calling_tmdb() =
        runTest {
            val (cards, total) = repo.genrePage(CatalogGenre.HORROR, TmdbMediaType.TV, 1)
            assertEquals(0, cards.size)
            assertEquals(0, total)
            coVerify(exactly = 0) { tmdb.discoverPage(any(), any(), any()) }
        }
}
