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

    private fun queries(target: BrowseTarget) = browseRows(target, today).mapNotNull { (it.kind as? BrowseRowKind.Query)?.query }

    private fun kinds(target: BrowseTarget) = browseRows(target, today).map { it.kind }

    @Test
    fun genre_page_rows_then_related_studios() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.ANIMATION, TmdbMediaType.MOVIE))
        assertEquals(9, rows.size)
        assertTrue(rows.all { it.genreId == 16 && it.mediaType == TmdbMediaType.MOVIE })
        val (popular, top, fresh, gems, classics) = rows
        assertEquals(TmdbDiscoverQuery(TmdbMediaType.MOVIE, genreId = 16, minVotes = 200, minRating = 5.0, mainstreamOnly = true), popular)
        assertTrue(fresh.mainstreamOnly && top.mainstreamOnly)
        assertTrue(!gems.mainstreamOnly && !classics.mainstreamOnly)
        assertEquals(TmdbSort.RATING, top.sort)
        assertEquals(1000, top.minVotes)
        assertEquals(LocalDate.of(2025, 9, 27), fresh.releasedAfter)
        assertEquals(LocalDate.of(1999, 12, 31), classics.releasedBefore)
        assertEquals(500, classics.minVotes)
        assertEquals(listOf(3, 10342, 521), rows.takeLast(3).map { it.companyId })
        // Animation keeps anime among its gems
        assertEquals(listOf("fr", "en", "ja"), gems.originalLanguages)
    }

    @Test
    fun live_action_genres_never_show_cartoons() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.DRAMA, TmdbMediaType.MOVIE))
        assertTrue(rows.all { 16 in it.withoutGenreIds })
        assertEquals(listOf(16), gridQuery(BrowseTarget.Genre(CatalogGenre.DRAMA, TmdbMediaType.MOVIE))!!.withoutGenreIds)
        assertTrue(queries(BrowseTarget.Genre(CatalogGenre.FAMILY, TmdbMediaType.MOVIE)).first().withoutGenreIds.isEmpty())
    }

    @Test
    fun gems_are_settled_well_known_french_or_english_live_action_films() {
        val gems = queries(BrowseTarget.Genre(CatalogGenre.DRAMA, TmdbMediaType.MOVIE))[3]
        // Best known first: sorting by rating put niche titles with inflated ratings first
        assertEquals(TmdbSort.VOTES, gems.sort)
        assertEquals(LocalDate.of(1990, 1, 1), gems.releasedAfter)
        assertEquals(7.2, gems.minRating)
        assertEquals(1200, gems.minVotes)
        assertEquals(6000, gems.maxVotes)
        assertEquals(listOf("fr", "en"), gems.originalLanguages)
        assertEquals(LocalDate.of(2025, 3, 27), gems.releasedBefore)
        assertEquals(setOf(16, 99, 10770, 10751), gems.withoutGenreIds.toSet())
        // A documentary page keeps documentaries
        val docs = queries(BrowseTarget.Genre(CatalogGenre.DOCUMENTARY, TmdbMediaType.MOVIE))[3]
        assertTrue(99 !in docs.withoutGenreIds)
    }

    @Test
    fun rows_below_popular_leave_out_the_titles_already_shown() {
        val rows = browseRows(BrowseTarget.Genre(CatalogGenre.COMEDY, TmdbMediaType.MOVIE), today).map { it.kind as BrowseRowKind.Query }
        val (popular, top, _, gems) = rows
        assertTrue(popular.exclude.isEmpty())
        assertEquals(listOf(popular.query), top.exclude)
        assertEquals(listOf(popular.query, top.query), gems.exclude)
        assertTrue(gems.onePerSaga && !top.onePerSaga)
    }

    @Test
    fun series_genre_uses_the_series_genre_id_and_has_no_studio_rows() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.ACTION, TmdbMediaType.TV))
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.genreId == 10759 })
        assertEquals(200, rows[4].minVotes)
        assertEquals(300, rows[3].minVotes)
    }

    @Test
    fun studio_page_has_no_gems_dedupes_its_rows_and_ends_with_its_sagas() {
        val rows = browseRows(BrowseTarget.Studio(CatalogStudio.PIXAR), today)
        val studio = queries(BrowseTarget.Studio(CatalogStudio.PIXAR))
        assertEquals(4, studio.size)
        assertTrue(studio.all { it.companyId == 3 && it.genreId == null })
        assertTrue(studio.none { it.mainstreamOnly || it.maxVotes != null })
        val (popular, _, top, classics) = rows.map { it.kind as? BrowseRowKind.Query }
        assertEquals(300, top!!.query.minVotes)
        assertEquals(listOf(popular!!.query), top.exclude)
        assertEquals(listOf(popular.query, top.query), classics!!.exclude)
        val sagas = rows.drop(4)
        assertEquals(BrowseRowKind.CollectionParts(10194), sagas.first().kind)
        assertTrue(sagas.all { it.titleFromCollection })
        val network = queries(BrowseTarget.Network(CatalogNetwork.HBO))
        assertTrue(network.all { it.networkId == 49 && it.mediaType == TmdbMediaType.TV })
    }

    @Test
    fun marvel_is_popular_then_its_sagas_then_every_film_in_release_order() {
        val kinds = kinds(BrowseTarget.Studio(CatalogStudio.MARVEL_STUDIOS))
        assertEquals(TmdbSort.POPULARITY, (kinds.first() as BrowseRowKind.Query).query.sort)
        assertEquals(BrowseRowKind.CollectionParts(86311), kinds[1])
        assertEquals(9, kinds.count { it is BrowseRowKind.CollectionParts })
        val releaseOrder = kinds.last() as BrowseRowKind.Query
        assertEquals(TmdbSort.RELEASE_ASC, releaseOrder.query.sort)
        // Every MCU film, not the first 20; no cartoons or shorts
        assertEquals(3, releaseOrder.pages)
        assertEquals(LocalDate.of(2008, 1, 1), releaseOrder.query.releasedAfter)
        assertEquals(listOf(16), releaseOrder.query.withoutGenreIds)
    }

    @Test
    fun disney_rows_are_kinds_of_disney_films() {
        val rows = queries(BrowseTarget.Studio(CatalogStudio.WALT_DISNEY))
        val (popular, classics, recent, pixar, princesses) = rows
        assertEquals(2, popular.companyId)
        assertEquals(16, classics.genreId)
        assertEquals(LocalDate.of(2005, 12, 31), classics.releasedBefore)
        assertEquals(listOf(2, 6125, 3166), classics.companyIds)
        assertEquals(LocalDate.of(2006, 1, 1), recent.releasedAfter)
        assertEquals(3, pixar.companyId)
        assertTrue(7376 in princesses.keywordIds)
        val (remakes, adventures) = rows.drop(5)
        assertEquals(listOf(16), remakes.withoutGenreIds)
        assertTrue(245230 in remakes.keywordIds)
        assertEquals(remakes.keywordIds, adventures.withoutKeywordIds)
        assertEquals(BrowseRowKind.CollectionParts(295), kinds(BrowseTarget.Studio(CatalogStudio.WALT_DISNEY)).last())
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
        assertEquals(50, queries(BrowseTarget.Studio(CatalogStudio.A24))[1].minVotes)
        assertEquals(50, queries(BrowseTarget.Network(CatalogNetwork.ARTE))[1].minVotes)
        assertEquals(200, queries(BrowseTarget.Genre(CatalogGenre.DRAMA, TmdbMediaType.MOVIE))[2].minVotes)
    }

    @Test
    fun free_targets_browse_like_catalogue_ones() {
        val company = browseRows(BrowseTarget.AnyCompany(3, "Pixar"), today)
        assertEquals(browseRows(BrowseTarget.Studio(CatalogStudio.PIXAR), today), company)
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
