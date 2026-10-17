package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.preferences.CatalogPopularity
import tv.jellyvault.app.preferences.CatalogPreferences
import java.time.LocalDate

class CatalogFilterTest {
    private val query = TmdbDiscoverQuery(TmdbMediaType.MOVIE, genreId = 18, minVotes = 200)

    private fun item(date: String?) = TmdbItem(1, TmdbMediaType.MOVIE, "T", null, "/p.jpg", null, date, 7.0, emptyList())

    @Test
    fun default_filter_changes_nothing() {
        assertSame(query, CatalogFilter().apply(query))
        assertTrue(CatalogFilter().keeps(item("1950-01-01")))
    }

    @Test
    fun min_year_moves_the_release_floor_up_but_never_down() {
        val filter = CatalogFilter(minYear = 1990)
        assertEquals(LocalDate.of(1990, 1, 1), filter.apply(query)!!.releasedAfter)
        val recent = query.copy(releasedAfter = LocalDate.of(2025, 9, 27))
        assertEquals(LocalDate.of(2025, 9, 27), filter.apply(recent)!!.releasedAfter)
    }

    @Test
    fun a_row_entirely_before_the_min_year_is_dropped() {
        val classics = query.copy(releasedBefore = LocalDate.of(1989, 12, 31))
        assertNull(CatalogFilter(minYear = 1990).apply(classics))
        // 90s classics stay
        assertEquals(
            LocalDate.of(1990, 1, 1),
            CatalogFilter(minYear = 1990).apply(query.copy(releasedBefore = LocalDate.of(1999, 12, 31)))!!.releasedAfter,
        )
    }

    @Test
    fun popularity_scales_the_vote_floor_and_ceiling() {
        val gems = query.copy(minVotes = 1200, maxVotes = 6000)
        val hits = CatalogFilter(voteFactor = 3.0).apply(gems)!!
        assertEquals(3600, hits.minVotes)
        assertEquals(18000, hits.maxVotes)
        assertEquals(600, CatalogFilter(voteFactor = 0.5).apply(gems)!!.minVotes)
        // Never below a meaningful floor, never above what the row asked for
        assertEquals(30, CatalogFilter(voteFactor = 0.5).apply(query.copy(minVotes = 50))!!.minVotes)
        assertEquals(10, CatalogFilter(voteFactor = 0.5).apply(query.copy(minVotes = 10))!!.minVotes)
    }

    @Test
    fun ready_made_lists_drop_older_titles_and_keep_undated_ones() {
        val filter = CatalogFilter(minYear = 1990)
        assertFalse(filter.keeps(item("1989-06-01")))
        assertTrue(filter.keeps(item("1990-01-01")))
        assertTrue(filter.keeps(item(null)))
        assertEquals(1, listOf(item("1980-01-01"), item("2000-01-01")).withinCatalogFilter(filter).size)
    }

    @Test
    fun preferences_map_to_a_filter() {
        val prefs =
            CatalogPreferences
                .newBuilder()
                .setMinYear(1990)
                .setPopularity(CatalogPopularity.POPULARITY_HITS)
                .build()
        assertEquals(CatalogFilter(1990, 3.0), CatalogFilter.of(prefs))
        assertEquals(CatalogFilter(), CatalogFilter.of(CatalogPreferences.getDefaultInstance()))
    }
}
