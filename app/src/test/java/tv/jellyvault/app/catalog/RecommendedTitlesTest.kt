package tv.jellyvault.app.catalog

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

class RecommendedTitlesTest {
    private val tmdb = mockk<TmdbClient>()
    private val catalog = mockk<CatalogRepository>()
    private val service = RecommendedTitles(tmdb, catalog)

    private fun item(
        id: Int,
        genres: List<Int> = emptyList(),
        votes: Int = 1000,
    ) = TmdbItem(id, TmdbMediaType.MOVIE, "T$id", null, "/p.jpg", null, "2020-01-01", 7.0, genres, voteCount = votes)

    private fun details(
        recommendations: List<TmdbItem>,
        similar: List<TmdbItem> = emptyList(),
        genres: List<Int> = emptyList(),
    ) = TmdbDetails(
        item = item(1),
        runtimeMinutes = null,
        seasons = emptyList(),
        genres = emptyList(),
        tagline = null,
        certification = null,
        logoUrl = null,
        trailerKey = null,
        recommendations = recommendations,
        tvdbId = null,
        similarTitles = similar,
        genreIds = genres,
    )

    @Test
    fun returns_tmdb_recommendations_as_cards_without_the_title_itself() =
        runTest {
            every { tmdb.isConfigured } returns true
            coEvery { tmdb.details(TmdbMediaType.MOVIE, 1) } returns details(listOf(item(1), item(2), item(3)))
            coEvery { catalog.cards(any()) } answers { firstArg<List<TmdbItem>>().map { MediaCard.Remote(it) } }
            val result = service.forTitle(TmdbMediaType.MOVIE, mapOf("Tmdb" to "1"))
            assertEquals(listOf("T2", "T3"), result.map { it.name })
        }

    @Test
    fun never_falls_back_to_tmdb_similar_titles() =
        runTest {
            every { tmdb.isConfigured } returns true
            coEvery { tmdb.details(TmdbMediaType.MOVIE, 1) } returns details(emptyList(), listOf(item(4)))
            coEvery { catalog.cards(any()) } answers { firstArg<List<TmdbItem>>().map { MediaCard.Remote(it) } }
            assertTrue(service.forTitle(TmdbMediaType.MOVIE, mapOf("Tmdb" to "1")).isEmpty())
        }

    @Test
    fun live_action_titles_get_no_cartoons_nor_unknown_titles() =
        runTest {
            every { tmdb.isConfigured } returns true
            val recommendations = listOf(item(2, genres = listOf(16, 10751)), item(3, votes = 12), item(4, genres = listOf(28)))
            coEvery { tmdb.details(TmdbMediaType.MOVIE, 1) } returns details(recommendations, genres = listOf(28, 12))
            coEvery { catalog.cards(any()) } answers { firstArg<List<TmdbItem>>().map { MediaCard.Remote(it) } }
            assertEquals(listOf("T4"), service.forTitle(TmdbMediaType.MOVIE, mapOf("Tmdb" to "1")).map { it.name })
        }

    @Test
    fun cartoons_keep_cartoon_recommendations() =
        runTest {
            every { tmdb.isConfigured } returns true
            coEvery { tmdb.details(TmdbMediaType.MOVIE, 1) } returns details(listOf(item(2, genres = listOf(16))), genres = listOf(16))
            coEvery { catalog.cards(any()) } answers { firstArg<List<TmdbItem>>().map { MediaCard.Remote(it) } }
            assertEquals(listOf("T2"), service.forTitle(TmdbMediaType.MOVIE, mapOf("Tmdb" to "1")).map { it.name })
        }

    @Test
    fun empty_without_tmdb_id_or_token() =
        runTest {
            every { tmdb.isConfigured } returns true
            assertTrue(service.forTitle(TmdbMediaType.MOVIE, mapOf("Imdb" to "tt1")).isEmpty())
            every { tmdb.isConfigured } returns false
            assertTrue(service.forTitle(TmdbMediaType.MOVIE, mapOf("Tmdb" to "1")).isEmpty())
        }

    @Test
    fun empty_when_tmdb_fails() =
        runTest {
            every { tmdb.isConfigured } returns true
            coEvery { tmdb.details(any(), any()) } throws TmdbException("down")
            assertTrue(service.forTitle(TmdbMediaType.MOVIE, mapOf("Tmdb" to "1")).isEmpty())
        }
}
