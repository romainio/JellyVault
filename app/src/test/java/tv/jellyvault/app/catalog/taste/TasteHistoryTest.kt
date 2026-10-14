package tv.jellyvault.app.catalog.taste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

class TasteHistoryTest {
    private fun key(id: Int) = TmdbKey(TmdbMediaType.MOVIE, id)

    private fun played(vararg ids: Int) = ids.map { PlayedTitle(key(it), "T$it") }

    @Test
    fun recent_titles_weigh_more_and_favorites_most() {
        val history = buildTasteHistory(played(1, 2, 3), favorites = setOf(key(3)), maxSeeds = 12)
        val weights = history.seeds.associate { it.key.tmdbId to it.weight }
        assertTrue(weights.getValue(1) > weights.getValue(2))
        assertTrue(weights.getValue(3) > weights.getValue(2))
    }

    @Test
    fun seeds_are_capped_to_the_heaviest() {
        val history = buildTasteHistory(played(*(1..40).toList().toIntArray()), emptySet(), maxSeeds = 10)
        assertEquals(10, history.seeds.size)
        assertEquals((1..10).toList(), history.seeds.map { it.key.tmdbId })
    }

    @Test
    fun every_played_title_is_marked_watched_even_beyond_the_seeds() {
        val history = buildTasteHistory(played(*(1..40).toList().toIntArray()), emptySet(), maxSeeds = 5)
        assertEquals(40, history.watched.size)
    }

    @Test
    fun favorites_never_played_still_seed_and_count_as_known() {
        val history = buildTasteHistory(played(1), favorites = setOf(key(9)), maxSeeds = 12)
        assertTrue(history.seeds.any { it.key.tmdbId == 9 })
        assertTrue(key(9) in history.watched)
    }

    @Test
    fun no_history_gives_no_seeds() {
        assertTrue(buildTasteHistory(emptyList(), emptySet(), 12).seeds.isEmpty())
    }

    @Test
    fun unplayed_favorites_never_outweigh_what_was_actually_watched() {
        val favorites = (100..130).map { key(it) }.toSet()
        val history = buildTasteHistory(played(1, 2, 3, 4, 5), favorites, maxSeeds = 12)
        assertTrue((1..5).all { id -> history.seeds.any { it.key.tmdbId == id } })
    }

    @Test
    fun the_same_title_played_twice_seeds_once() {
        val history = buildTasteHistory(played(1, 1, 2), emptySet(), maxSeeds = 12)
        assertEquals(listOf(1, 2), history.seeds.map { it.key.tmdbId })
    }
}
