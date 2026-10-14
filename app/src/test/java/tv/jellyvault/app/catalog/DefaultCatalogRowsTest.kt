package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.ui.util.ResProviderStringProvider
import tv.jellyvault.app.ui.util.ResStringProvider

class DefaultCatalogRowsTest {
    @Test
    fun default_rows_are_trending_then_personal_rows_then_varied_styles() {
        val rows = defaultCatalogRows(isConfigured = true)
        assertEquals(
            listOf(HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE), HomeRowConfig.TmdbTrending(TmdbMediaType.TV)) +
                personalRows() + HomeRowConfig.TmdbWatchlist() + HomeRowConfig.TmdbShelves(),
            rows,
        )
    }

    @Test
    fun no_catalog_rows_without_token() {
        assertTrue(defaultCatalogRows(isConfigured = false).isEmpty())
    }

    @Test
    fun titles() {
        assertEquals(ResStringProvider(R.string.catalog_trending_movies), catalogRowTitle(HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE)))
        assertEquals(ResStringProvider(R.string.catalog_trending_series), catalogRowTitle(HomeRowConfig.TmdbTrending(TmdbMediaType.TV)))
        assertEquals(ResStringProvider(R.string.catalog_genre_drama), catalogRowTitle(HomeRowConfig.TmdbGenre(CatalogGenre.DRAMA)))
        assertEquals(
            ResProviderStringProvider(R.string.catalog_genre_movies_format, ResStringProvider(R.string.catalog_genre_drama)),
            catalogRowTitle(HomeRowConfig.TmdbGenre(CatalogGenre.DRAMA, CatalogMediaType.MOVIE)),
        )
    }

    @Test
    fun home_order_is_movies_on_top_then_continue_then_the_other_catalog_rows_then_libraries() {
        val cont = HomeRowConfig.ContinueWatchingCombined()
        val recent = HomeRowConfig.RecentlyAdded(java.util.UUID.randomUUID())
        val order = defaultHomeOrder(listOf(cont), defaultCatalogRows(isConfigured = true), listOf(recent))
        assertEquals(HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE), order[0])
        assertEquals(HomeRowConfig.TmdbForYou(TmdbMediaType.MOVIE), order[1])
        assertEquals(cont, order[2])
        assertEquals(HomeRowConfig.TmdbShelves(), order[order.size - 2])
        assertEquals(recent, order.last())
    }

    @Test
    fun home_order_without_tmdb_is_continue_then_libraries() {
        val cont = HomeRowConfig.ContinueWatchingCombined()
        val recent = HomeRowConfig.RecentlyAdded(java.util.UUID.randomUUID())
        assertEquals(listOf(cont, recent), defaultHomeOrder(listOf(cont), defaultCatalogRows(isConfigured = false), listOf(recent)))
    }

    @Test
    fun old_default_order_is_migrated_other_orders_are_kept() {
        val cont = HomeRowConfig.ContinueWatchingCombined()
        val movies = HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE)
        val series = HomeRowConfig.TmdbTrending(TmdbMediaType.TV)
        val genre = HomeRowConfig.TmdbGenre(CatalogGenre.ACTION)
        assertEquals(listOf(movies, series, cont, genre), migrateHomeOrder(listOf(cont, movies, series, genre)))
        val custom = listOf(genre, cont, movies, series)
        assertEquals(custom, migrateHomeOrder(custom))
        val migrated = listOf(movies, series, cont, genre)
        assertEquals(migrated, migrateHomeOrder(migrated))
    }
}
