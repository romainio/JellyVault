package tv.jellyvault.app.catalog.taste

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jellyvault.app.catalog.tmdb.TmdbNamed

class DirectorStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val nolan = TmdbNamed(525, "Christopher Nolan")

    @Test
    fun only_films_never_looked_up_are_fetched_and_the_answers_survive_a_restart() =
        runTest {
            val asked = mutableListOf<List<Int>>()
            val first = DirectorStore(folder.root)
            val fetch: suspend (List<Int>) -> Map<Int, TmdbNamed?> = { ids ->
                asked += ids
                ids.associateWith { if (it == 1) nolan else null }
            }
            assertEquals(listOf(nolan, null), first.directors(listOf(1, 2), fetch))
            // A new process: read from disk, only film 3 is new
            assertEquals(listOf(null, nolan, null), DirectorStore(folder.root).directors(listOf(3, 1, 2), fetch))
            assertEquals(listOf(listOf(1, 2), listOf(3)), asked)
        }

    @Test
    fun a_failed_lookup_is_tried_again() =
        runTest {
            val store = DirectorStore(folder.root)
            store.directors(listOf(7)) { emptyMap() }
            var askedAgain = false
            store.directors(listOf(7)) {
                askedAgain = true
                mapOf(7 to nolan)
            }
            assertEquals(true, askedAgain)
        }
}
