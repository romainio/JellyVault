package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig

class PersonalRowsMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cont = HomeRowConfig.ContinueWatchingCombined()
    private val movies = HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE)
    private val series = HomeRowConfig.TmdbTrending(TmdbMediaType.TV)
    private val action = HomeRowConfig.TmdbGenre(CatalogGenre.ACTION)

    @Test
    fun personal_rows_go_right_after_the_last_trending_row() {
        assertEquals(
            listOf(movies, series) + personalRows() + listOf(cont, action),
            addPersonalRows(listOf(movies, series, cont, action)),
        )
    }

    @Test
    fun homes_without_catalog_rows_or_with_personal_rows_are_left_alone() {
        assertEquals(listOf(cont), addPersonalRows(listOf(cont)))
        val already = listOf(movies, HomeRowConfig.TmdbForYou(TmdbMediaType.TV), cont)
        assertEquals(already, addPersonalRows(already))
    }

    @Test
    fun applied_once_per_user_so_a_row_the_user_removed_stays_removed() {
        val migration = PersonalRowsMigration(folder.root)
        val first = migration.migrate("user1", listOf(movies, series, cont))
        assertEquals(listOf(movies, series) + personalRows() + cont, first)
        assertEquals(listOf(movies, series, cont), migration.migrate("user1", listOf(movies, series, cont)))
        assertEquals(listOf(movies, series, cont), PersonalRowsMigration(folder.root).migrate("user1", listOf(movies, series, cont)))
        assertEquals(listOf(movies, series) + personalRows() + cont, migration.migrate("user2", listOf(movies, series, cont)))
    }

    @Test
    fun a_freshly_created_default_home_is_never_migrated_again() {
        val migration = PersonalRowsMigration(folder.root)
        migration.markDone("user1")
        assertEquals(listOf(movies, series, cont), migration.migrate("user1", listOf(movies, series, cont)))
    }
}
