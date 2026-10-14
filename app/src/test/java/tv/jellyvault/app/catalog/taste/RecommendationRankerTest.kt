package tv.jellyvault.app.catalog.taste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

class RecommendationRankerTest {
    private fun item(
        id: Int,
        rating: Double = 7.5,
        votes: Int = 1000,
        language: String = "en",
    ) = TmdbItem(id, TmdbMediaType.MOVIE, "T$id", null, "/p.jpg", null, "2020-01-01", rating, emptyList(), language, votes, 10.0)

    private fun seed(
        id: Int,
        weight: Double = 1.0,
    ) = TasteSeed(TmdbKey(TmdbMediaType.MOVIE, id), "Seed$id", weight)

    private fun rec(
        seed: TasteSeed,
        vararg items: TmdbItem,
    ) = SeedRecommendations(seed, items.toList())

    @Test
    fun titles_recommended_by_several_seeds_rank_first() {
        val a = seed(100)
        val b = seed(101)
        val ranked = rankForYou(listOf(rec(a, item(1), item(2)), rec(b, item(3), item(2))), emptySet(), 10)
        assertEquals(2, ranked.first().tmdbId)
    }

    @Test
    fun heavier_seeds_weigh_more() {
        val heavy = seed(100, weight = 2.0)
        val light = seed(101, weight = 0.5)
        val ranked = rankForYou(listOf(rec(light, item(1)), rec(heavy, item(2))), emptySet(), 10)
        assertEquals(listOf(2, 1), ranked.map { it.tmdbId })
    }

    @Test
    fun watched_titles_seeds_and_low_quality_are_excluded() {
        val s = seed(100)
        val watched = TmdbKey(TmdbMediaType.MOVIE, 1)
        val ranked =
            rankForYou(
                listOf(rec(s, item(1), item(2, rating = 4.0), item(3, votes = 10), item(100), item(4))),
                setOf(watched),
                10,
            )
        assertEquals(listOf(4), ranked.map { it.tmdbId })
    }

    @Test
    fun obscure_foreign_titles_are_excluded() {
        val ranked = rankForYou(listOf(rec(seed(100), item(1, language = "ko", votes = 150), item(2))), emptySet(), 10)
        assertEquals(listOf(2), ranked.map { it.tmdbId })
    }

    @Test
    fun result_is_capped_and_deduplicated() {
        val items = (1..30).map { item(it) }
        val ranked = rankForYou(listOf(rec(seed(100), *items.toTypedArray()), rec(seed(101), *items.toTypedArray())), emptySet(), 12)
        assertEquals(12, ranked.size)
        assertEquals(12, ranked.map { it.tmdbId }.toSet().size)
    }

    @Test
    fun because_rows_follow_the_heaviest_seeds_and_do_not_repeat_titles() {
        val a = seed(100, 2.0)
        val b = seed(101, 1.0)
        val c = seed(102, 0.5)
        val shared = (1..8).map { item(it) }
        val onlyB = (20..27).map { item(it) }
        val rows =
            becauseRows(
                listOf(rec(c, item(50)), rec(a, *shared.toTypedArray()), rec(b, *(shared + onlyB).toTypedArray())),
                exclude = emptySet(),
                maxRows = 2,
                minItems = 6,
                perRow = 20,
            )
        assertEquals(listOf("Seed100", "Seed101"), rows.map { it.seed.title })
        assertEquals((20..27).toList(), rows[1].items.map { it.tmdbId })
        assertTrue(rows.all { it.items.size >= 6 })
    }

    @Test
    fun because_rows_skip_seeds_with_too_little_to_show() {
        val rows = becauseRows(listOf(rec(seed(100), item(1), item(2))), emptySet(), 2, 6, 20)
        assertTrue(rows.isEmpty())
    }
}
