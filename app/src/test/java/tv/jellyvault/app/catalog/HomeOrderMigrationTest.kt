package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig

class HomeOrderMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cont = HomeRowConfig.ContinueWatchingCombined()
    private val movies = HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE)
    private val series = HomeRowConfig.TmdbTrending(TmdbMediaType.TV)

    @Test
    fun old_default_is_moved_once_then_the_user_order_is_kept() {
        val migration = HomeOrderMigration(folder.root)
        assertEquals(listOf(movies, series, cont), migration.migrate("user1", listOf(cont, movies, series)))
        // The user deliberately puts "Continue" back on top: it stays there
        assertEquals(listOf(cont, movies, series), migration.migrate("user1", listOf(cont, movies, series)))
        // Survives a restart
        assertEquals(listOf(cont, movies, series), HomeOrderMigration(folder.root).migrate("user1", listOf(cont, movies, series)))
    }

    @Test
    fun each_user_is_migrated_on_their_own() {
        val migration = HomeOrderMigration(folder.root)
        migration.migrate("user1", listOf(cont, movies, series))
        assertEquals(listOf(movies, series, cont), migration.migrate("user2", listOf(cont, movies, series)))
    }
}
