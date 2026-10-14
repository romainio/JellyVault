package tv.jellyvault.app.catalog.shelves

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.catalogRowTitle
import tv.jellyvault.app.catalog.defaultCatalogRows
import tv.jellyvault.app.catalog.defaultHomeOrder
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.services.HomeRowConfigDisplay
import tv.jellyvault.app.ui.util.ResStringProvider

class ShelfRowsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val trendM = HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE)
    private val trendT = HomeRowConfig.TmdbTrending(TmdbMediaType.TV)
    private val forYouM = HomeRowConfig.TmdbForYou(TmdbMediaType.MOVIE)
    private val forYouT = HomeRowConfig.TmdbForYou(TmdbMediaType.TV)
    private val becauseM = HomeRowConfig.TmdbBecause(TmdbMediaType.MOVIE, 0)
    private val cont = HomeRowConfig.ContinueWatchingCombined()
    private val shelves = HomeRowConfig.TmdbShelves()
    private val list = HomeRowConfig.TmdbWatchlist()
    private val action = HomeRowConfig.TmdbGenre(CatalogGenre.ACTION)
    private val comedy = HomeRowConfig.TmdbGenre(CatalogGenre.COMEDY)

    private fun display(vararg configs: HomeRowConfig) =
        configs.mapIndexed { i, c -> HomeRowConfigDisplay(i, ResStringProvider(R.string.app_name), c) }

    @Test
    fun the_varied_styles_row_expands_into_the_planned_shelves() {
        val rows = expandShelfRows(display(trendM, shelves, cont), listOf("fr_films", "tv_crime", "unknown"))
        assertEquals(listOf(trendM, HomeRowConfig.TmdbShelf("fr_films"), HomeRowConfig.TmdbShelf("tv_crime"), cont), rows.map { it.config })
        assertEquals(ResStringProvider(R.string.shelf_french_films), rows[1].title)
        assertEquals(rows.size, rows.map { it.id }.toSet().size)
    }

    @Test
    fun the_default_home_puts_continue_third_and_the_styles_after_the_personal_rows() {
        val order = defaultHomeOrder(listOf(cont), defaultCatalogRows(true), emptyList())
        assertEquals(listOf(trendM, forYouM, cont, trendT, becauseM, forYouT, list, shelves), order)
    }

    @Test
    fun titles_of_the_new_rows() {
        assertEquals(ResStringProvider(R.string.home_varied_styles), catalogRowTitle(shelves))
        assertEquals(ResStringProvider(R.string.shelf_tv_crime), catalogRowTitle(HomeRowConfig.TmdbShelf("tv_crime")))
    }

    @Test
    fun the_last_default_home_gets_the_new_order_and_its_genres_become_the_varied_styles() {
        val old = listOf(trendM, trendT, forYouM, becauseM, forYouT, cont, action, comedy)
        assertEquals(listOf(trendM, forYouM, cont, trendT, becauseM, forYouT, list, shelves), migrateToVariety(old))
    }

    @Test
    fun a_custom_order_is_kept_only_the_default_genre_rows_change() {
        val custom = listOf(cont, action, trendM, comedy)
        assertEquals(listOf(cont, shelves, trendM), migrateToVariety(custom))
        val noCatalog = listOf(cont)
        assertEquals(noCatalog, migrateToVariety(noCatalog))
        val already = listOf(trendM, shelves)
        assertEquals(already, migrateToVariety(already))
    }

    @Test
    fun genre_rows_the_user_added_stay_and_removed_defaults_do_not_come_back() {
        val western = HomeRowConfig.TmdbGenre(CatalogGenre.WESTERN)
        val moviesOnly = HomeRowConfig.TmdbGenre(CatalogGenre.ACTION, tv.jellyvault.app.catalog.CatalogMediaType.MOVIE)
        assertEquals(listOf(trendM, shelves, western, moviesOnly), migrateToVariety(listOf(trendM, action, western, moviesOnly, comedy)))
        // The user had removed every default genre row: no styles forced on them, only "Ta liste" after the personal rows
        assertEquals(listOf(cont, trendM, forYouM, list, western), migrateToVariety(listOf(cont, trendM, forYouM, western)))
    }

    @Test
    fun the_migration_runs_once_per_user() {
        val migration = VarietyMigration(folder.root)
        assertEquals(listOf(trendM, shelves), migration.migrate("u1", listOf(trendM, action)))
        assertEquals(listOf(trendM, action), migration.migrate("u1", listOf(trendM, action)))
        migration.markDone("u2")
        assertEquals(listOf(trendM, action), migration.migrate("u2", listOf(trendM, action)))
    }
}
