package tv.jellyvault.app.catalog

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbResultPage
import java.util.UUID

class CatalogRepositoryTest {
    private fun genreQuery(
        genre: CatalogGenre,
        type: TmdbMediaType,
    ) = genre.discoverQuery(type)!!.copy(minVotes = 200, minRating = 5.0)

    private fun page(items: List<TmdbItem>) = TmdbResultPage(items, 1, 1)

    private val tmdb = mockk<TmdbClient>()
    private val index = mockk<LibraryIndex>()
    private val source = mockk<LibraryItemSource>()
    private val repo = CatalogRepository(tmdb, index, source, mockk(relaxed = true))

    private fun item(
        id: Int,
        type: TmdbMediaType,
    ) = TmdbItem(id, type, "T$id", null, "/p.jpg", null, null, null, emptyList())

    @Test
    fun owned_items_become_Owned_with_badge_others_Remote() =
        runTest {
            val ownedId = UUID.randomUUID()
            coEvery { tmdb.trending(TmdbMediaType.MOVIE) } returns listOf(item(1, TmdbMediaType.MOVIE), item(2, TmdbMediaType.MOVIE))
            coEvery { index.awaitIndex(any()) } returns mapOf(TmdbKey(TmdbMediaType.MOVIE, 1) to ownedId)
            coEvery { source.fetchItems(listOf(ownedId)) } returns
                listOf(BaseItemDto(id = ownedId, type = BaseItemKind.MOVIE, name = "Mine"))

            val cards = repo.trendingRow(TmdbMediaType.MOVIE)

            val owned = cards[0] as MediaCard.Owned
            assertEquals(ownedId, owned.item.id)
            assertTrue(owned.item.inLibraryBadge)
            assertTrue(cards[1] is MediaCard.Remote)
        }

    @Test
    fun trending_drops_obscure_foreign_titles_and_tops_up_from_page_two() =
        runTest {
            fun foreign(id: Int) = item(id, TmdbMediaType.MOVIE).copy(originalLanguage = "ko", voteCount = 40, popularity = 10.0)

            fun french(id: Int) = item(id, TmdbMediaType.MOVIE).copy(originalLanguage = "fr")
            coEvery { tmdb.trending(TmdbMediaType.MOVIE) } returns listOf(french(1), foreign(2), french(3))
            coEvery { tmdb.trending(TmdbMediaType.MOVIE, 2) } returns listOf(foreign(4), french(5))
            coEvery { index.awaitIndex(any()) } returns emptyMap()

            assertEquals(listOf("T1", "T3", "T5"), repo.trendingRow(TmdbMediaType.MOVIE).map { it.tmdb?.title })
        }

    @Test
    fun trending_survives_a_failing_second_page() =
        runTest {
            coEvery { tmdb.trending(TmdbMediaType.MOVIE) } returns listOf(item(1, TmdbMediaType.MOVIE))
            coEvery { tmdb.trending(TmdbMediaType.MOVIE, 2) } throws
                tv.jellyvault.app.catalog.tmdb
                    .TmdbException("down")
            coEvery { index.awaitIndex(any()) } returns emptyMap()

            assertEquals(1, repo.trendingRow(TmdbMediaType.MOVIE).size)
        }

    @Test
    fun same_numeric_id_for_movie_and_tv_are_distinct() =
        runTest {
            val showId = UUID.randomUUID()
            coEvery { tmdb.discover(genreQuery(CatalogGenre.ACTION, TmdbMediaType.MOVIE), 1) } returns
                page(listOf(item(100, TmdbMediaType.MOVIE)))
            coEvery { tmdb.discover(genreQuery(CatalogGenre.ACTION, TmdbMediaType.TV), 1) } returns
                page(listOf(item(100, TmdbMediaType.TV)))
            coEvery { index.awaitIndex(any()) } returns mapOf(TmdbKey(TmdbMediaType.TV, 100) to showId)
            coEvery { source.fetchItems(listOf(showId)) } returns listOf(BaseItemDto(id = showId, type = BaseItemKind.SERIES))

            val cards = repo.genreRow(CatalogGenre.ACTION, CatalogMediaType.BOTH)

            assertEquals(2, cards.size)
            assertTrue(cards[0] is MediaCard.Remote)
            assertTrue(cards[1] is MediaCard.Owned)
        }

    @Test
    fun both_interleaves_movies_and_series_and_dedupes() =
        runTest {
            coEvery { tmdb.discover(genreQuery(CatalogGenre.COMEDY, TmdbMediaType.MOVIE), 1) } returns
                page(
                    listOf(
                        item(1, TmdbMediaType.MOVIE),
                        item(2, TmdbMediaType.MOVIE),
                        item(1, TmdbMediaType.MOVIE),
                        item(3, TmdbMediaType.MOVIE),
                    ),
                )
            coEvery { tmdb.discover(genreQuery(CatalogGenre.COMEDY, TmdbMediaType.TV), 1) } returns page(listOf(item(9, TmdbMediaType.TV)))
            coEvery { index.awaitIndex(any()) } returns emptyMap()

            val keys = repo.genreRow(CatalogGenre.COMEDY, CatalogMediaType.BOTH).map { it.tmdb!!.key }

            assertEquals(
                listOf(
                    TmdbKey(TmdbMediaType.MOVIE, 1),
                    TmdbKey(TmdbMediaType.TV, 9),
                    TmdbKey(TmdbMediaType.MOVIE, 2),
                    TmdbKey(TmdbMediaType.MOVIE, 3),
                ),
                keys,
            )
            coVerify(exactly = 0) { source.fetchItems(any()) }
        }

    @Test
    fun genre_without_tv_equivalent_uses_movies_only() =
        runTest {
            coEvery { tmdb.discover(genreQuery(CatalogGenre.HORROR, TmdbMediaType.MOVIE), 1) } returns
                page(listOf(item(5, TmdbMediaType.MOVIE)))
            coEvery { index.awaitIndex(any()) } returns emptyMap()

            assertEquals(1, repo.genreRow(CatalogGenre.HORROR, CatalogMediaType.BOTH).size)
            assertEquals(0, repo.genreRow(CatalogGenre.HORROR, CatalogMediaType.TV).size)
        }

    @Test
    fun failing_owned_fetch_degrades_to_Remote() =
        runTest {
            val ownedId = UUID.randomUUID()
            coEvery { tmdb.trending(TmdbMediaType.TV) } returns listOf(item(1, TmdbMediaType.TV))
            coEvery { index.awaitIndex(any()) } returns mapOf(TmdbKey(TmdbMediaType.TV, 1) to ownedId)
            coEvery { source.fetchItems(any()) } throws RuntimeException("boom")

            assertTrue(repo.trendingRow(TmdbMediaType.TV).single() is MediaCard.Remote)
        }

    @Test
    fun isConfigured_follows_client() {
        every { tmdb.isConfigured } returns false
        assertEquals(false, repo.isConfigured)
    }
}
