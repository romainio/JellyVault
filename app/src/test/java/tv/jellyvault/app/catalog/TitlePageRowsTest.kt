package tv.jellyvault.app.catalog

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbCollection
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbNamed
import tv.jellyvault.app.catalog.tmdb.TmdbResultPage

class TitlePageRowsTest {
    private val tmdb = mockk<TmdbClient>()
    private val index = mockk<LibraryIndex>().also { coEvery { it.awaitIndex(any()) } returns emptyMap() }
    private val repo = CatalogRepository(tmdb, index, mockk(relaxed = true), mockk(relaxed = true))

    private fun item(
        id: Int,
        genres: List<Int> = listOf(28),
        votes: Int = 5000,
        type: TmdbMediaType = TmdbMediaType.MOVIE,
    ) = TmdbItem(id, type, "T$id", null, "/p.jpg", null, "2020-01-01", 7.0, genres, voteCount = votes)

    private fun details(
        recommendations: List<TmdbItem> = emptyList(),
        genres: List<Int> = listOf(28, 12),
        collection: TmdbNamed? = null,
        director: TmdbNamed? = null,
        type: TmdbMediaType = TmdbMediaType.MOVIE,
    ) = TmdbDetails(
        item = item(1, type = type),
        runtimeMinutes = null,
        seasons = emptyList(),
        genres = emptyList(),
        tagline = null,
        certification = null,
        logoUrl = null,
        trailerKey = null,
        recommendations = recommendations,
        tvdbId = null,
        collection = collection,
        genreIds = genres,
        director = director,
    )

    private fun page(vararg items: TmdbItem) = TmdbResultPage(items.toList(), 1, 1)

    private fun List<MediaCard>.ids() = map { it.tmdb!!.tmdbId }

    @Test
    fun saga_similar_and_director_rows_never_repeat_a_title() =
        runTest {
            coEvery { tmdb.collection(10) } returns TmdbCollection("Saga", null, listOf(item(1), item(2), item(3)))
            val director = TmdbNamed(525, "Christopher Nolan")
            coEvery { tmdb.directedMovies(525) } returns listOf(item(1), item(3), item(4), item(5), item(6), item(8))
            val rows =
                repo.titleRows(
                    details(recommendations = listOf(item(2), item(4), item(7)), collection = TmdbNamed(10, "Saga"), director = director),
                )
            assertEquals(listOf(2, 3), rows.saga.ids())
            assertEquals(listOf(4, 7), rows.similar.ids())
            assertEquals(listOf(5, 6, 8), rows.more.ids())
            assertEquals(director, rows.director)
        }

    @Test
    fun recommendations_keep_to_live_action_and_known_titles() =
        runTest {
            coEvery { tmdb.discover(any<TmdbDiscoverQuery>(), any()) } returns page()
            val rows = repo.titleRows(details(listOf(item(2, genres = listOf(16, 10751)), item(3, votes = 40), item(4))))
            assertEquals(listOf(4), rows.similar.ids())
        }

    @Test
    fun a_director_with_few_other_films_gives_way_to_the_same_genres() =
        runTest {
            val director = TmdbNamed(9, "Debut")
            coEvery { tmdb.directedMovies(9) } returns listOf(item(8), item(9, votes = 10))
            val genreQuery = sameGenreQuery(details())!!
            coEvery { tmdb.discover(genreQuery, 1) } returns page(item(1), item(11), item(12))
            val rows = repo.titleRows(details(director = director))
            assertNull(rows.director)
            assertEquals(listOf(11, 12), rows.more.ids())
            assertEquals(28, genreQuery.genreId)
            assertEquals(listOf(12), genreQuery.andGenreIds)
            assertEquals(listOf(16), genreQuery.withoutGenreIds)
        }

    @Test
    fun series_get_the_same_genre_row_and_no_saga() =
        runTest {
            coEvery { tmdb.discover(any<TmdbDiscoverQuery>(), any()) } returns page(item(20, type = TmdbMediaType.TV))
            val rows = repo.titleRows(details(type = TmdbMediaType.TV, genres = listOf(80, 18), collection = TmdbNamed(10, "x")))
            assertTrue(rows.saga.isEmpty())
            assertEquals(listOf(20), rows.more.ids())
        }

    @Test
    fun a_failing_row_leaves_the_others() =
        runTest {
            coEvery { tmdb.collection(10) } throws TmdbException("down")
            coEvery { tmdb.discover(any<TmdbDiscoverQuery>(), any()) } throws TmdbException("down")
            coEvery { tmdb.directedMovies(any()) } throws TmdbException("down")
            val rows = repo.titleRows(details(listOf(item(4)), collection = TmdbNamed(10, "Saga"), director = TmdbNamed(1, "x")))
            assertTrue(rows.saga.isEmpty() && rows.more.isEmpty())
            assertEquals(listOf(4), rows.similar.ids())
        }

    @Test
    fun saga_names_lose_their_collection_suffix() {
        assertEquals("Avengers", collectionRowName("Avengers - Saga"))
        assertEquals("The Avengers", collectionRowName("The Avengers Collection"))
        assertEquals("Los Vengadores", collectionRowName("Los Vengadores - Colección"))
        assertEquals("Saga", collectionRowName("Saga"))
    }

    @Test
    fun saga_key_groups_the_films_of_a_saga() {
        assertEquals("gabriel's inferno", sagaKey("Gabriel's Inferno"))
        assertEquals("gabriel's inferno", sagaKey("Gabriel's Inferno: Part II"))
        assertEquals("gabriel's inferno", sagaKey("Gabriel's Inferno - Part III"))
        assertEquals("rocky", sagaKey("Rocky IV"))
        assertEquals("toy story", sagaKey("Toy Story 2"))
        assertEquals("spider-man", sagaKey("Spider-Man 3"))
        assertEquals("x-men", sagaKey("X-Men"))
        assertEquals("billy elliot", sagaKey("Billy Elliot"))
    }
}
