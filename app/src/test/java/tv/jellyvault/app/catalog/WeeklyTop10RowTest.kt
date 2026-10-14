package tv.jellyvault.app.catalog

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.library.LibraryItemSource
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbResultPage
import tv.jellyvault.app.catalog.top10.WeeklyTop10

class WeeklyTop10RowTest {
    private fun tv(
        id: Int,
        title: String,
    ) = TmdbItem(id, TmdbMediaType.TV, title, null, "/p.jpg", null, null, null, emptyList())

    @Test
    fun unmatched_titles_are_skipped_and_rank_is_kept() =
        runTest {
            val tmdb = mockk<TmdbClient>()
            val top10 = mockk<WeeklyTop10>()
            val index = mockk<LibraryIndex>()
            coEvery { index.awaitIndex(any()) } returns emptyMap()
            coEvery { top10.titles() } returns listOf("Monster", "Nothing", "The Gentlemen")
            coEvery { tmdb.search("Monster", any()) } returns TmdbResultPage(listOf(tv(1, "Monstre")), 1, 1)
            coEvery { tmdb.search("Nothing", any()) } returns TmdbResultPage(emptyList(), 1, 1)
            coEvery { tmdb.search("The Gentlemen", any()) } returns
                TmdbResultPage(
                    listOf(TmdbItem(9, TmdbMediaType.MOVIE, "Film", null, "/f.jpg", null, null, null, emptyList()), tv(2, "The Gentlemen")),
                    1,
                    1,
                )
            val repo = CatalogRepository(tmdb, index, mockk<LibraryItemSource>(relaxed = true), top10)
            val cards = repo.weeklyTop10Row()
            assertEquals(listOf("1. Monstre", "3. The Gentlemen"), cards.map { (it as MediaCard.Remote).tmdb.title })
        }
}
