package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbSort
import java.time.LocalDate

class BrowseTargetTest {
    private val today = LocalDate.of(2026, 9, 27)

    private fun queries(target: BrowseTarget) = browseRows(target, today).map { (it.kind as BrowseRowKind.Query).query }

    @Test
    fun genre_page_rows_then_related_studios() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.ANIMATION, TmdbMediaType.MOVIE))
        assertEquals(9, rows.size)
        assertTrue(rows.all { it.genreId == 16 && it.mediaType == TmdbMediaType.MOVIE })
        val (popular, top, fresh, gems, classics) = rows
        assertEquals(TmdbDiscoverQuery(TmdbMediaType.MOVIE, genreId = 16, minVotes = 200, minRating = 5.0, mainstreamOnly = true), popular)
        assertTrue(fresh.mainstreamOnly)
        assertTrue(!top.mainstreamOnly && !gems.mainstreamOnly && !classics.mainstreamOnly)
        assertEquals(TmdbSort.RATING, top.sort)
        assertEquals(1000, top.minVotes)
        assertEquals(LocalDate.of(2025, 9, 27), fresh.releasedAfter)
        assertEquals(7.5, gems.minRating)
        assertEquals(2000, gems.maxVotes)
        assertEquals(LocalDate.of(1999, 12, 31), classics.releasedBefore)
        assertEquals(500, classics.minVotes)
        assertEquals(listOf(3, 10342, 521), rows.takeLast(3).map { it.companyId })
    }

    @Test
    fun series_genre_uses_the_series_genre_id_and_has_no_studio_rows() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.ACTION, TmdbMediaType.TV))
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.genreId == 10759 })
        assertEquals(200, rows[4].minVotes)
    }

    @Test
    fun studio_and_network_pages_lower_the_top_rated_threshold() {
        val studio = queries(BrowseTarget.Studio(CatalogStudio.PIXAR))
        assertEquals(5, studio.size)
        assertTrue(studio.all { it.companyId == 3 && it.genreId == null })
        assertTrue(studio.none { it.mainstreamOnly })
        assertEquals(300, studio[1].minVotes)
        val network = queries(BrowseTarget.Network(CatalogNetwork.HBO))
        assertTrue(network.all { it.networkId == 49 && it.mediaType == TmdbMediaType.TV })
    }

    @Test
    fun saga_has_one_row_and_no_grid() {
        val target = BrowseTarget.Saga(CatalogSaga.TOY_STORY)
        assertEquals(listOf(BrowseRowKind.SagaParts(CatalogSaga.TOY_STORY)), browseRows(target, today).map { it.kind })
        assertNull(gridQuery(target))
    }

    @Test
    fun grid_is_the_popular_query_and_missing_for_genres_without_series() {
        assertEquals(
            TmdbDiscoverQuery(TmdbMediaType.MOVIE, companyId = 420, minVotes = 200, minRating = 5.0),
            gridQuery(BrowseTarget.Studio(CatalogStudio.MARVEL_STUDIOS)),
        )
        assertNull(gridQuery(BrowseTarget.Genre(CatalogGenre.HORROR, TmdbMediaType.TV)))
        assertTrue(browseRows(BrowseTarget.Genre(CatalogGenre.HORROR, TmdbMediaType.TV), today).isEmpty())
    }

    @Test
    fun new_titles_need_fewer_votes_for_studios_and_networks() {
        assertEquals(50, queries(BrowseTarget.Studio(CatalogStudio.A24))[2].minVotes)
        assertEquals(50, queries(BrowseTarget.Network(CatalogNetwork.ARTE))[2].minVotes)
        assertEquals(200, queries(BrowseTarget.Genre(CatalogGenre.DRAMA, TmdbMediaType.MOVIE))[2].minVotes)
    }

    @Test
    fun free_targets_browse_like_catalogue_ones() {
        val company = queries(BrowseTarget.AnyCompany(3, "Pixar"))
        assertEquals(queries(BrowseTarget.Studio(CatalogStudio.PIXAR)), company)
        assertTrue(queries(BrowseTarget.AnyNetwork(49, "HBO")).all { it.networkId == 49 && it.mediaType == TmdbMediaType.TV })
        assertEquals(
            listOf(BrowseRowKind.CollectionParts(10194)),
            browseRows(BrowseTarget.AnyCollection(10194, "Toy Story"), today).map { it.kind },
        )
        assertNull(gridQuery(BrowseTarget.AnyCollection(10194, "Toy Story")))
    }

    @Test
    fun cartoons_are_animation_from_animation_studios() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.CARTOONS, TmdbMediaType.MOVIE))
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.genreId == 16 && it.companyIds.containsAll(listOf(6125, 3, 10342)) })
        assertTrue(browseRows(BrowseTarget.Genre(CatalogGenre.CARTOONS, TmdbMediaType.TV), today).isEmpty())
    }
}
