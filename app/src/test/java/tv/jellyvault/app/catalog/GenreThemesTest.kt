package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

class GenreThemesTest {
    private fun queries(
        genre: CatalogGenre,
        type: TmdbMediaType,
    ) = genreThemes(genre, type).map { it.query }

    @Test
    fun query_params_join_keywords_with_or_and_genres_with_and() {
        val params =
            TmdbDiscoverQuery(
                TmdbMediaType.MOVIE,
                genreId = 35,
                andGenreIds = listOf(10749),
                keywordIds = listOf(1, 2),
                originCountry = "FR",
            ).params(1)
        assertEquals("35,10749", params["with_genres"])
        assertEquals("1|2", params["with_keywords"])
        assertEquals("FR", params["with_origin_country"])
    }

    @Test
    fun plain_query_sends_none_of_the_new_params() {
        val params = TmdbDiscoverQuery(TmdbMediaType.MOVIE, genreId = 35).params(1)
        assertFalse(params.containsKey("with_keywords"))
        assertFalse(params.containsKey("with_origin_country"))
        assertEquals("35", params["with_genres"])
    }

    @Test
    fun comedy_has_romcom_french_and_stand_up_rows() {
        val rows = queries(CatalogGenre.COMEDY, TmdbMediaType.MOVIE)
        assertTrue(rows.any { it.andGenreIds == listOf(10749) })
        assertTrue(rows.any { it.originCountry == "FR" })
        assertTrue(rows.any { 9716 in it.keywordIds })
        assertTrue(rows.all { it.genreId == 35 && it.mediaType == TmdbMediaType.MOVIE })
    }

    @Test
    fun every_theme_keeps_a_real_vote_floor_and_a_title() {
        CatalogGenre.entries.forEach { genre ->
            TmdbMediaType.entries.forEach { type ->
                genreThemes(genre, type).forEach {
                    assertTrue("$genre $type ${it.titleRes}", it.query.minVotes >= 30)
                }
            }
        }
    }

    @Test
    fun series_get_series_themes_only() {
        val rows = queries(CatalogGenre.COMEDY, TmdbMediaType.TV)
        assertTrue(rows.all { it.mediaType == TmdbMediaType.TV && it.genreId == 35 })
        assertTrue(rows.none { 9716 in it.keywordIds })
    }

    @Test
    fun genres_without_a_series_equivalent_have_no_series_themes() {
        assertTrue(genreThemes(CatalogGenre.HORROR, TmdbMediaType.TV).isEmpty())
    }

    @Test
    fun genre_page_shows_themes_before_studios() {
        val rows = browseRows(BrowseTarget.Genre(CatalogGenre.COMEDY, TmdbMediaType.MOVIE), java.time.LocalDate.of(2026, 9, 27))
        val themeCount = genreThemes(CatalogGenre.COMEDY, TmdbMediaType.MOVIE).size
        assertTrue(themeCount > 0)
        val queries = rows.map { (it.kind as BrowseRowKind.Query).query }
        assertEquals(genreThemes(CatalogGenre.COMEDY, TmdbMediaType.MOVIE).map { it.query }, queries.subList(5, 5 + themeCount))
    }
}
