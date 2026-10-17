package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.taste.mixDirectorFilms
import tv.jellyvault.app.catalog.taste.rankDirectors
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbNamed
import tv.jellyvault.app.data.model.HomeRowConfig

class DirectorsRowTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val nolan = TmdbNamed(525, "Christopher Nolan")
    private val fincher = TmdbNamed(7467, "David Fincher")
    private val villeneuve = TmdbNamed(137427, "Denis Villeneuve")
    private val once = TmdbNamed(1, "Once Only")

    private fun film(
        id: Int,
        votes: Int = 5000,
    ) = TmdbItem(id, TmdbMediaType.MOVIE, "T$id", null, "/p.jpg", null, "2010-01-01", 8.0, emptyList(), voteCount = votes)

    @Test
    fun favorite_directors_are_the_most_liked_with_two_films_at_least() {
        val liked = listOf(fincher, nolan, once, nolan, null, fincher, villeneuve, nolan, villeneuve)
        assertEquals(listOf(nolan, fincher, villeneuve), rankDirectors(liked, max = 3, minLiked = 2))
        // A tie keeps the most recently rated first
        assertEquals(listOf(fincher, villeneuve), rankDirectors(listOf(fincher, villeneuve, villeneuve, fincher), 3, 2))
        assertTrue(rankDirectors(listOf(once, nolan), 3, 2).isEmpty())
    }

    @Test
    fun films_are_mixed_one_director_after_the_other_without_seen_or_unknown_ones() {
        val mixed =
            mixDirectorFilms(
                listOf(listOf(film(1), film(2), film(3)), listOf(film(10), film(11, votes = 12), film(12))),
                watched = setOf(TmdbKey(TmdbMediaType.MOVIE, 2)),
                minVotes = 300,
            )
        assertEquals(listOf(1, 10, 3, 12), mixed.map { it.tmdbId })
    }

    @Test
    fun the_row_goes_after_because_once_and_never_into_a_home_without_personal_rows() {
        val because = HomeRowConfig.TmdbBecause(TmdbMediaType.MOVIE, 0)
        val rows = listOf(HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE), because, HomeRowConfig.TmdbWatchlist())
        assertEquals(listOf(rows[0], because, HomeRowConfig.TmdbDirectors(), rows[2]), addDirectorsRow(rows))
        val noPersonal = listOf(HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE))
        assertEquals(noPersonal, addDirectorsRow(noPersonal))
        val migration = DirectorsRowMigration(folder.root)
        assertEquals(4, migration.migrate("user", rows).size)
        // Removed by the user afterwards: it stays removed
        assertEquals(rows, migration.migrate("user", rows))
    }

    @Test
    fun the_title_names_directors_by_their_last_name() {
        assertEquals("Nolan, Fincher, Villeneuve", directorNames(listOf("Christopher Nolan", "David Fincher", "Denis Villeneuve")))
    }
}
