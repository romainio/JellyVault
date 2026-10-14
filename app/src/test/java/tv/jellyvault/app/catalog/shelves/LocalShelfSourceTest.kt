package tv.jellyvault.app.catalog.shelves

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jellyvault.app.catalog.taste.TasteAffinityStore
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import java.io.File
import java.time.LocalDate
import java.util.UUID

class LocalShelfSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val user = UUID.randomUUID()
    private var currentUser: UUID? = user
    private val monday = LocalDate.of(2026, 10, 5)

    private fun source(store: TasteAffinityStore = TasteAffinityStore(folder.root)) = LocalShelfSource(folder.root, store) { currentUser }

    @Test
    fun the_home_plan_is_frozen_for_the_period_even_when_the_taste_changes() =
        runTest {
            val store = TasteAffinityStore(folder.root)
            val source = source(store)
            val first = source.plan(ShelfScope.HOME, monday)
            store.save(user, mapOf(27 to 1.0, 99 to 1.0))
            assertEquals(first, source.plan(ShelfScope.HOME, monday.plusDays(2)))
            // Survives a restart
            assertEquals(first, source(store).plan(ShelfScope.HOME, monday.plusDays(1)))
            assertNotEquals(first, source.plan(ShelfScope.HOME, monday.plusDays(3)))
        }

    @Test
    fun scopes_have_their_own_size_and_types() =
        runTest {
            val source = source()
            assertEquals(16, source.plan(ShelfScope.HOME, monday).size)
            val movies = source.plan(ShelfScope.MOVIES, monday)
            assertEquals(12, movies.size)
            assertTrue(movies.all { ShelfCatalog.byId(it)!!.mediaType == TmdbMediaType.MOVIE })
            assertTrue(source.plan(ShelfScope.SERIES, monday).all { ShelfCatalog.byId(it)!!.mediaType == TmdbMediaType.TV })
        }

    @Test
    fun each_user_gets_a_plan_from_their_own_taste() =
        runTest {
            val store = TasteAffinityStore(folder.root)
            store.save(user, mapOf(27 to 1.0))
            val other = UUID.randomUUID()
            val source = source(store)
            val mine = source.plan(ShelfScope.HOME, monday)
            currentUser = other
            assertEquals(planShelves(ShelfCatalog.ALL, monday, emptyMap(), 16, 4), source.plan(ShelfScope.HOME, monday))
            currentUser = user
            assertEquals(mine, source.plan(ShelfScope.HOME, monday))
        }

    @Test
    fun a_corrupt_file_or_an_unknown_shelf_id_never_breaks_the_home() =
        runTest {
            File(folder.root, LocalShelfSource.FILE).writeText("{not json")
            File(folder.root, TasteAffinityStore.FILE).writeText("[]")
            assertEquals(16, source().plan(ShelfScope.HOME, monday).size)
        }
}
