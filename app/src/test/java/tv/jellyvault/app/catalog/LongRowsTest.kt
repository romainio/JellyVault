package tv.jellyvault.app.catalog

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.library.LibraryItemSource
import tv.jellyvault.app.catalog.shelves.ShelfCatalog
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbResultPage
import tv.jellyvault.app.catalog.top10.WeeklyTop10
import java.time.LocalDate

class LongRowsTest {
    private val tmdb = mockk<TmdbClient>()
    private val top10 = mockk<WeeklyTop10>()
    private val index = mockk<LibraryIndex>()
    private val repo = CatalogRepository(tmdb, index, mockk<LibraryItemSource>(relaxed = true), top10)

    init {
        coEvery { index.awaitIndex(any()) } returns emptyMap()
        coEvery { top10.titles() } returns emptyList()
    }

    private fun item(
        id: Int,
        type: TmdbMediaType = TmdbMediaType.MOVIE,
        language: String = "en",
    ) = TmdbItem(id, type, "T$id", null, "/p.jpg", null, null, 7.0, emptyList(), language, 1000, 10.0)

    @Test
    fun trending_always_reads_two_pages_for_about_forty_titles() =
        runTest {
            coEvery { tmdb.trending(TmdbMediaType.MOVIE) } returns (1..20).map { item(it) }
            coEvery { tmdb.trending(TmdbMediaType.MOVIE, 2) } returns (20..40).map { item(it) }
            assertEquals((1..40).toList(), repo.trendingRow(TmdbMediaType.MOVIE).map { it.tmdb!!.tmdbId })
        }

    @Test
    fun series_trending_starts_with_the_numbered_top_ten_then_trending_without_duplicates() =
        runTest {
            coEvery { top10.titles() } returns listOf("Alpha", "Beta")
            coEvery { tmdb.search("Alpha", any()) } returns TmdbResultPage(listOf(item(7, TmdbMediaType.TV)), 1, 1)
            coEvery { tmdb.search("Beta", any()) } returns TmdbResultPage(listOf(item(8, TmdbMediaType.TV)), 1, 1)
            coEvery { tmdb.trending(TmdbMediaType.TV) } returns listOf(item(8, TmdbMediaType.TV), item(9, TmdbMediaType.TV))
            coEvery { tmdb.trending(TmdbMediaType.TV, 2) } returns listOf(item(10, TmdbMediaType.TV))
            val titles = repo.trendingRow(TmdbMediaType.TV).map { it.tmdb!!.title }
            assertEquals(listOf("1. T7", "2. T8", "T9", "T10"), titles)
            assertTrue(titles.none { it.contains("Netflix", ignoreCase = true) })
        }

    @Test
    fun series_trending_survives_a_failing_top_ten() =
        runTest {
            coEvery { top10.titles() } throws IllegalStateException("down")
            coEvery { tmdb.trending(TmdbMediaType.TV) } returns listOf(item(9, TmdbMediaType.TV))
            coEvery { tmdb.trending(TmdbMediaType.TV, 2) } returns emptyList()
            assertEquals(listOf(9), repo.trendingRow(TmdbMediaType.TV).map { it.tmdb!!.tmdbId })
        }

    @Test
    fun a_shelf_row_merges_two_pages_and_drops_obscure_titles() =
        runTest {
            val shelf = ShelfCatalog.byId("comedies")!!
            val query = shelf.query(LocalDate.of(2026, 10, 5))
            coEvery { tmdb.discover(query, 1) } returns TmdbResultPage((1..20).map { item(it) }, 1, 5)
            coEvery { tmdb.discover(query, 2) } returns
                TmdbResultPage((20..39).map { item(it) } + item(99, language = "ko").copy(voteCount = 10), 2, 5)
            val ids = repo.shelfRow(shelf, LocalDate.of(2026, 10, 5)).map { it.tmdb!!.tmdbId }
            assertEquals((1..39).toList(), ids)
        }

    @Test
    fun a_shelf_row_keeps_page_one_when_page_two_fails() =
        runTest {
            val shelf = ShelfCatalog.byId("comedies")!!
            val query = shelf.query(LocalDate.of(2026, 10, 5))
            coEvery { tmdb.discover(query, 1) } returns TmdbResultPage((1..3).map { item(it) }, 1, 5)
            coEvery { tmdb.discover(query, 2) } throws TmdbException("down")
            assertEquals(3, repo.shelfRow(shelf, LocalDate.of(2026, 10, 5)).size)
        }

    @Test
    fun a_shelf_with_a_single_page_reads_it_once() =
        runTest {
            val shelf = ShelfCatalog.byId("tv_cult")!!
            val query = shelf.query(LocalDate.of(2026, 10, 5))
            coEvery { tmdb.discover(query, 1) } returns TmdbResultPage((1..12).map { item(it, TmdbMediaType.TV) }, 1, 1)
            assertEquals(12, repo.shelfRow(shelf, LocalDate.of(2026, 10, 5)).size)
        }
}
