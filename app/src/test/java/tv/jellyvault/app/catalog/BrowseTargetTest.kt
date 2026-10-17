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
    fun genre_page_is_new_popular_themes_then_top_rated_classics_and_studios() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.ANIMATION, TmdbMediaType.MOVIE))
        val themes = genreThemes(CatalogGenre.ANIMATION, TmdbMediaType.MOVIE).map { it.query }
        assertEquals(4 + themes.size + 3, rows.size)
        assertTrue(rows.all { it.genreId == 16 && it.mediaType == TmdbMediaType.MOVIE })
        val (fresh, popular) = rows
        assertEquals(LocalDate.of(2025, 9, 27), fresh.releasedAfter)
        assertEquals(TmdbDiscoverQuery(TmdbMediaType.MOVIE, genreId = 16, minVotes = 200, minRating = 5.0, mainstreamOnly = true), popular)
        assertEquals(themes, rows.subList(2, 2 + themes.size))
        val (top, classics) = rows.drop(2 + themes.size)
        assertEquals(TmdbSort.RATING, top.sort)
        assertEquals(1000, top.minVotes)
        // Older films are the Classics: no Chaplin among the top rated comedies
        assertEquals(LocalDate.of(1990, 1, 1), top.releasedAfter)
        assertEquals(LocalDate.of(1999, 12, 31), classics.releasedBefore)
        assertEquals(500, classics.minVotes)
        assertEquals(listOf(3, 10342, 521), rows.takeLast(3).map { it.companyId })
        assertTrue(rows.none { it.maxVotes != null })
    }

    @Test
    fun live_action_genres_never_show_cartoons() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.DRAMA, TmdbMediaType.MOVIE))
        assertTrue(rows.all { 16 in it.withoutGenreIds })
        assertEquals(listOf(16), gridQuery(BrowseTarget.Genre(CatalogGenre.DRAMA, TmdbMediaType.MOVIE))!!.withoutGenreIds)
        assertTrue(queries(BrowseTarget.Genre(CatalogGenre.FAMILY, TmdbMediaType.MOVIE)).first().withoutGenreIds.isEmpty())
    }

    @Test
    fun stand_up_is_its_own_genre_and_comedy_leaves_it_out() {
        val standUp = queries(BrowseTarget.Genre(CatalogGenre.STAND_UP, TmdbMediaType.MOVIE))
        assertTrue(standUp.all { it.keywordIds == listOf(9716, 376694) && it.genreId == 35 })
        assertTrue(standUp.any { it.originalLanguages == listOf("fr") })
        assertEquals(20, gridQuery(BrowseTarget.Genre(CatalogGenre.STAND_UP, TmdbMediaType.MOVIE))!!.minVotes)
        assertTrue(browseRows(BrowseTarget.Genre(CatalogGenre.STAND_UP, TmdbMediaType.TV), today).isEmpty())
        val comedy = queries(BrowseTarget.Genre(CatalogGenre.COMEDY, TmdbMediaType.MOVIE))
        assertTrue(comedy.all { it.withoutKeywordIds == listOf(9716, 376694) })
    }

    @Test
    fun rows_below_leave_out_the_titles_already_shown() {
        val rows = browseRows(BrowseTarget.Genre(CatalogGenre.COMEDY, TmdbMediaType.MOVIE), today).map { it.kind as BrowseRowKind.Query }
        val (fresh, popular) = rows
        val top = rows.single { it.query.sort == TmdbSort.RATING && it.query.keywordIds.isEmpty() && it.query.andGenreIds.isEmpty() }
        assertTrue(fresh.exclude.isEmpty())
        assertEquals(listOf(fresh.query), popular.exclude)
        assertEquals(listOf(fresh.query, popular.query), top.exclude)
        assertTrue(top.onePerSaga && !popular.onePerSaga)
    }

    @Test
    fun series_genre_uses_the_series_genre_id_and_has_no_studio_rows() {
        val rows = queries(BrowseTarget.Genre(CatalogGenre.ACTION, TmdbMediaType.TV))
        assertEquals(4 + genreThemes(CatalogGenre.ACTION, TmdbMediaType.TV).size, rows.size)
        assertTrue(rows.all { it.genreId == 10759 })
        assertEquals(200, rows.last().minVotes)
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
        assertEquals(200, queries(BrowseTarget.Genre(CatalogGenre.DRAMA, TmdbMediaType.MOVIE))[0].minVotes)
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
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.genreId == 16 && it.companyIds.containsAll(listOf(6125, 3, 10342)) })
        assertTrue(browseRows(BrowseTarget.Genre(CatalogGenre.CARTOONS, TmdbMediaType.TV), today).isEmpty())
    }

    @Test
    fun keyword_genres_browse_like_genres() {
        val superheroes = queries(BrowseTarget.Genre(CatalogGenre.SUPERHEROES, TmdbMediaType.MOVIE))
        assertTrue(superheroes.all { it.genreId == 28 && it.keywordIds == listOf(9715) })
        // New, popular, top rated, classics: the regular layout, not stand-up's
        assertEquals(4, superheroes.size)
        assertTrue(queries(BrowseTarget.Genre(CatalogGenre.TRUE_STORY, TmdbMediaType.TV)).all { it.keywordIds == listOf(9672) })
        assertTrue(browseRows(BrowseTarget.Genre(CatalogGenre.SPORTS, TmdbMediaType.TV), today).isEmpty())
    }
}
