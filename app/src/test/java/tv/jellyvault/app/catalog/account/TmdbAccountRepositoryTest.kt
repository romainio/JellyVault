package tv.jellyvault.app.catalog.account

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbAccountRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val api = mockk<TmdbAccountClient>()
    private var time = 0L
    private val owner = java.util.UUID.randomUUID()
    private var user: java.util.UUID? = owner

    @Before
    fun setUp() = JvDispatchers.configure(dispatcher)

    @After
    fun tearDown() = JvDispatchers.reset()

    private fun repo() = TmdbAccountRepository(api, folder.root, scope) { user }.also { it.now = { time } }

    private fun item(
        id: Int,
        type: TmdbMediaType = TmdbMediaType.MOVIE,
    ) = TmdbItem(id, type, "T$id", null, "/p.jpg", null, null, 7.0, emptyList())

    private fun linkedRepo(): TmdbAccountRepository {
        coEvery { api.createSession("tok") } returns "sess"
        coEvery { api.account("sess") } returns TmdbAccountInfo(42, "jellyfan")
        coEvery { api.rated(TmdbMediaType.MOVIE, 42, "sess") } returns listOf(RatedTitle(item(1), 9.0))
        coEvery { api.rated(TmdbMediaType.TV, 42, "sess") } returns listOf(RatedTitle(item(2, TmdbMediaType.TV), 4.0))
        coEvery { api.watchlist(TmdbMediaType.MOVIE, 42, "sess") } returns listOf(item(3))
        coEvery { api.watchlist(TmdbMediaType.TV, 42, "sess") } returns listOf(item(4, TmdbMediaType.TV))
        return repo()
    }

    @Test
    fun linking_saves_the_session_and_survives_a_restart() =
        runTest(dispatcher) {
            coEvery { api.createRequestToken() } returns "tok"
            val repo = linkedRepo()
            val request = repo.startLink()
            assertEquals("https://www.themoviedb.org/authenticate/tok", request.url)
            repo.finishLink(request.token)
            assertEquals(TmdbAccountState.Linked("jellyfan"), repo.state.value)
            assertEquals(TmdbAccountState.Linked("jellyfan"), repo().state.value)
        }

    @Test
    fun not_linked_gives_an_empty_snapshot_without_calls() =
        runTest(dispatcher) {
            val repo = repo()
            assertEquals(TmdbAccountState.NotLinked, repo.state.value)
            assertTrue(repo.snapshot().ratings.isEmpty())
            coVerify(exactly = 0) { api.rated(any(), any(), any()) }
        }

    @Test
    fun the_snapshot_has_ratings_and_watchlist_and_is_cached() =
        runTest(dispatcher) {
            val repo = linkedRepo()
            repo.finishLink("tok")
            val snap = repo.snapshot()
            assertEquals(listOf(9.0, 4.0), snap.ratings.map { it.rating })
            assertEquals(listOf(3, 4), snap.watchlist.map { it.tmdbId })
            repo.snapshot()
            coVerify(exactly = 1) { api.rated(TmdbMediaType.MOVIE, 42, "sess") }
            // From disk after a restart, without the network
            assertEquals(2, repo().snapshot().ratings.size)
            coVerify(exactly = 1) { api.rated(TmdbMediaType.MOVIE, 42, "sess") }
        }

    @Test
    fun an_old_snapshot_is_served_at_once_and_refreshed_in_the_background() =
        runTest(dispatcher) {
            val repo = linkedRepo()
            repo.finishLink("tok")
            repo.snapshot()
            time = TmdbAccountRepository.TTL_MS + 1
            coEvery { api.rated(TmdbMediaType.MOVIE, 42, "sess") } returns listOf(RatedTitle(item(1), 9.0), RatedTitle(item(5), 8.0))
            assertEquals(2, repo.snapshot().ratings.size)
            assertEquals(3, repo.snapshot().ratings.size)
        }

    @Test
    fun a_network_failure_keeps_the_last_snapshot() =
        runTest(dispatcher) {
            val repo = linkedRepo()
            repo.finishLink("tok")
            repo.snapshot()
            time = TmdbAccountRepository.TTL_MS + 1
            coEvery { api.rated(any(), any(), any()) } throws TmdbAccountException("down")
            repo.snapshot()
            assertEquals(2, repo.snapshot().ratings.size)
            assertTrue(repo.state.value is TmdbAccountState.Linked)
        }

    @Test
    fun an_expired_session_falls_back_to_nothing_and_says_so() =
        runTest(dispatcher) {
            val repo = linkedRepo()
            coEvery { api.rated(any(), any(), any()) } throws TmdbAccountException("expired", 401)
            repo.finishLink("tok")
            assertTrue(repo.snapshot().ratings.isEmpty())
            assertEquals(TmdbAccountState.Expired, repo.state.value)
        }

    @Test
    fun unlinking_forgets_everything() =
        runTest(dispatcher) {
            val repo = linkedRepo()
            repo.finishLink("tok")
            repo.snapshot()
            repo.unlink()
            assertEquals(TmdbAccountState.NotLinked, repo.state.value)
            assertTrue(repo.snapshot().ratings.isEmpty())
            assertEquals(TmdbAccountState.NotLinked, repo().state.value)
        }

    @Test
    fun the_first_read_never_waits_on_the_network() =
        runTest(dispatcher) {
            val repo = linkedRepo()
            coEvery { api.createSession("tok") } returns "sess"
            repo.finishLink("tok")
            java.io.File(folder.root, TmdbAccountRepository.SNAPSHOT_FILE).delete()
            val slow = linkedRepo()
            coEvery { api.rated(TmdbMediaType.MOVIE, 42, "sess") } coAnswers {
                kotlinx.coroutines.delay(60_000)
                listOf(RatedTitle(item(1), 9.0))
            }
            // Nothing cached: empty at once, the fetch goes on in the background
            assertTrue(slow.snapshot().ratings.isEmpty())
            assertTrue(slow.snapshot().ratings.isEmpty())
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(2, slow.snapshot().ratings.size)
            // One read when linking, then a single background read for both snapshot() calls
            coVerify(exactly = 2) { api.rated(TmdbMediaType.MOVIE, 42, "sess") }
        }

    @Test
    fun the_account_belongs_to_the_jellyfin_user_who_linked_it() =
        runTest(dispatcher) {
            val repo = linkedRepo()
            repo.finishLink("tok")
            repo.snapshot()
            user = java.util.UUID.randomUUID()
            assertTrue(repo.snapshot().ratings.isEmpty())
            assertTrue(repo.snapshot().watchlist.isEmpty())
            assertEquals(TmdbAccountState.NotLinked, repo.refreshState())
            user = owner
            assertEquals(TmdbAccountState.Linked("jellyfan"), repo.refreshState())
            assertEquals(2, repo.snapshot().ratings.size)
        }

    @Test
    fun the_version_changes_with_new_data_and_on_unlink() =
        runTest(dispatcher) {
            val repo = linkedRepo()
            repo.finishLink("tok")
            val linked = repo.version()
            repo.unlink()
            assertTrue(repo.version() != linked)
        }

    @Test
    fun a_rating_from_the_tv_counts_at_once_and_can_be_removed() =
        runTest(dispatcher) {
            coEvery { api.createRequestToken() } returns "tok"
            coEvery { api.rate(any(), any(), any(), any()) } returns Unit
            val repo = linkedRepo()
            repo.finishLink(repo.startLink().token)
            repo.snapshot()
            val before = repo.version()
            repo.rate(item(7), 8.0)
            coVerify { api.rate(TmdbMediaType.MOVIE, 7, "sess", 8.0) }
            assertEquals(8.0, repo.ratingOf(item(7)))
            assertEquals(listOf(7, 1, 2), repo.snapshot().ratings.map { it.item.tmdbId })
            assertTrue(repo.version() > before)
            repo.rate(item(7), null)
            assertEquals(null, repo.ratingOf(item(7)))
        }
}
