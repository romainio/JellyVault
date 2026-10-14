package tv.jellyvault.app.catalog.taste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.account.SavedRating
import tv.jellyvault.app.catalog.key
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

class RatingsTasteTest {
    private val movie = TmdbMediaType.MOVIE

    private fun item(
        id: Int,
        rating: Double = 7.5,
        votes: Int = 1000,
        type: TmdbMediaType = movie,
    ) = TmdbItem(id, type, "T$id", null, "/p.jpg", null, "2020-01-01", rating, listOf(18), "en", votes, 10.0)

    private fun rated(
        id: Int,
        rating: Double,
    ) = SavedRating(item(id), rating)

    private val jellyfin =
        TasteHistory(
            listOf(TasteSeed(TmdbKey(movie, 1), "Jelly1", 1.0), TasteSeed(TmdbKey(movie, 2), "Jelly2", 0.4)),
            setOf(TmdbKey(movie, 1), TmdbKey(movie, 2)),
        )

    @Test
    fun high_ratings_become_seeds_weighted_by_the_rating() {
        val history = jellyfin.withRatings(listOf(rated(10, 10.0), rated(11, 8.0), rated(12, 7.0)), movie)
        val weights = history.seeds.associate { it.key.tmdbId to it.weight }
        assertEquals(1.5, weights.getValue(10), 1e-9)
        assertTrue(weights.getValue(11) < weights.getValue(10))
        assertTrue(12 !in weights)
        assertEquals("T10", history.seeds.first { it.key.tmdbId == 10 }.title)
    }

    @Test
    fun a_title_rated_and_played_keeps_the_heavier_weight_once() {
        val history = jellyfin.withRatings(listOf(rated(2, 10.0)), movie)
        assertEquals(1, history.seeds.count { it.key.tmdbId == 2 })
        assertEquals(1.5, history.seeds.first { it.key.tmdbId == 2 }.weight, 1e-9)
    }

    @Test
    fun low_ratings_become_negative_seeds_and_every_rated_title_counts_as_watched() {
        val history = jellyfin.withRatings(listOf(rated(20, 3.0), rated(21, 5.0), rated(22, 6.0)), movie)
        assertEquals(listOf(20, 21), history.negative.map { it.key.tmdbId })
        assertTrue(history.seeds.none { it.key.tmdbId in setOf(20, 21, 22) })
        assertTrue(listOf(20, 21, 22).all { TmdbKey(movie, it) in history.watched })
    }

    @Test
    fun ratings_of_the_other_type_are_ignored_and_seeds_are_capped() {
        val series = SavedRating(item(30, type = TmdbMediaType.TV), 10.0)
        val many = (100..140).map { rated(it, 9.0) }
        val history = jellyfin.withRatings(many + series, movie)
        assertTrue(history.seeds.none { it.key.tmdbId == 30 })
        assertEquals(MAX_TASTE_SEEDS, history.seeds.size)
        assertTrue(history.liked.size >= 40)
    }

    @Test
    fun negative_seeds_push_a_title_down_and_out() {
        val good = TasteSeed(TmdbKey(movie, 1), "Good", 1.0)
        val bad = TasteSeed(TmdbKey(movie, 2), "Bad", 1.0)
        val ranked =
            rankForYou(
                listOf(SeedRecommendations(good, listOf(item(50), item(51)))),
                emptySet(),
                10,
                negative = listOf(SeedRecommendations(bad, listOf(item(50)))),
            )
        // 50 led on its own, a disliked title also points to it: it drops behind 51
        assertEquals(listOf(51, 50), ranked.map { it.tmdbId })
        val worse = bad.copy(weight = 2.0)
        val gone =
            rankForYou(
                listOf(SeedRecommendations(good, listOf(item(50), item(51)))),
                emptySet(),
                10,
                negative = listOf(SeedRecommendations(worse, listOf(item(50)))),
            )
        assertEquals(listOf(51), gone.map { it.tmdbId })
    }

    @Test
    fun for_you_can_ask_for_better_rated_titles() {
        val seed = TasteSeed(TmdbKey(movie, 1), "S", 1.0)
        val ranked =
            rankForYou(
                listOf(
                    SeedRecommendations(seed, listOf(item(60, rating = 6.2), item(61, rating = 7.0, votes = 300), item(62, rating = 7.0))),
                ),
                emptySet(),
                10,
                minRating = 6.5,
                minVotes = 500,
            )
        assertEquals(listOf(62), ranked.map { it.tmdbId })
    }

    @Test
    fun liked_titles_feed_the_genre_affinity() {
        val history = jellyfin.withRatings(listOf(rated(10, 9.0)), movie)
        assertEquals(listOf(10), history.liked.map { it.key.tmdbId })
    }
}
