package tv.jellyvault.app.catalog.taste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

class GenreAffinityTest {
    private fun item(
        id: Int,
        type: TmdbMediaType,
        vararg genres: Int,
    ) = TmdbItem(id, type, "T$id", null, "/p.jpg", null, null, 7.0, genres.toList())

    private fun seed(weight: Double) = TasteSeed(TmdbKey(TmdbMediaType.MOVIE, 1), "S", weight)

    @Test
    fun the_most_frequent_genre_scores_one_and_series_genres_map_to_movie_genres() {
        val affinity =
            genreAffinity(
                listOf(
                    SeedRecommendations(seed(1.0), listOf(item(1, TmdbMediaType.MOVIE, 27), item(2, TmdbMediaType.MOVIE, 27, 53))),
                    SeedRecommendations(seed(1.0), listOf(item(3, TmdbMediaType.TV, 10765))),
                ),
            )
        assertEquals(1.0, affinity.getValue(27), 1e-9)
        assertEquals(0.5, affinity.getValue(53), 1e-9)
        assertEquals(0.5, affinity.getValue(878), 1e-9)
        assertEquals(0.5, affinity.getValue(14), 1e-9)
    }

    @Test
    fun heavier_seeds_count_more_and_nothing_gives_an_empty_map() {
        val affinity =
            genreAffinity(
                listOf(
                    SeedRecommendations(seed(2.0), listOf(item(1, TmdbMediaType.MOVIE, 35))),
                    SeedRecommendations(seed(0.5), listOf(item(2, TmdbMediaType.MOVIE, 18))),
                ),
            )
        assertEquals(0.25, affinity.getValue(18), 1e-9)
        assertTrue(genreAffinity(emptyList()).isEmpty())
    }
}
