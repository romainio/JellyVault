package tv.jellyvault.app.ui.preferences.tmdb

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.account.AccountSnapshot
import tv.jellyvault.app.catalog.account.LinkRequest
import tv.jellyvault.app.catalog.account.SavedRating
import tv.jellyvault.app.catalog.account.TmdbAccountException
import tv.jellyvault.app.catalog.account.TmdbAccountRepository
import tv.jellyvault.app.catalog.account.TmdbAccountState
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbKeyStore
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbAccountViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = mockk<TmdbAccountRepository>(relaxed = true)
    private val accountState = MutableStateFlow<TmdbAccountState>(TmdbAccountState.NotLinked)
    private lateinit var keyStore: TmdbKeyStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
        every { repo.state } returns accountState
        keyStore = TmdbKeyStore(File(tmp.root, "tmdb_key"), buildDefault = "")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        JvDispatchers.reset()
    }

    @Test
    fun linking_shows_the_approval_page_then_finishes() =
        runTest(dispatcher) {
            coEvery { repo.startLink() } returns LinkRequest("tok", "https://www.themoviedb.org/authenticate/tok")
            val vm = TmdbAccountViewModel(repo, keyStore)
            vm.startLink()
            assertEquals(
                "https://www.themoviedb.org/authenticate/tok",
                vm.state.value.link
                    ?.url,
            )
            vm.finishLink()
            coVerify { repo.finishLink("tok") }
            assertNull(vm.state.value.link)
            assertNull(vm.state.value.error)
        }

    @Test
    fun an_unapproved_request_says_so_and_keeps_the_code_on_screen() =
        runTest(dispatcher) {
            coEvery { repo.startLink() } returns LinkRequest("tok", "u")
            coEvery { repo.finishLink("tok") } throws TmdbAccountException("no", 401)
            val vm = TmdbAccountViewModel(repo, keyStore)
            vm.startLink()
            vm.finishLink()
            assertEquals(R.string.tmdb_account_not_approved, vm.state.value.error)
            assertEquals(
                "tok",
                vm.state.value.link
                    ?.token,
            )
        }

    @Test
    fun tmdb_unreachable_is_reported() =
        runTest(dispatcher) {
            coEvery { repo.startLink() } throws TmdbAccountException("down")
            val vm = TmdbAccountViewModel(repo, keyStore)
            vm.startLink()
            assertEquals(R.string.tmdb_account_unreachable, vm.state.value.error)
        }

    @Test
    fun the_account_state_follows_the_repository_and_unlink_forgets() =
        runTest(dispatcher) {
            val vm = TmdbAccountViewModel(repo, keyStore)
            accountState.value = TmdbAccountState.Linked("jellyfan")
            assertEquals(TmdbAccountState.Linked("jellyfan"), vm.state.value.account)
            vm.unlink()
            coVerify { repo.unlink() }
        }

    @Test
    fun a_linked_account_shows_what_was_received_with_the_newest_movie_and_series() =
        runTest(dispatcher) {
            fun item(
                id: Int,
                type: TmdbMediaType,
            ) = TmdbItem(id, type, "T$id", null, "/p.jpg", null, null, 7.0, emptyList())
            coEvery { repo.snapshot() } returns
                AccountSnapshot(
                    ratings =
                        listOf(
                            SavedRating(item(1, TmdbMediaType.MOVIE), 9.0),
                            SavedRating(item(2, TmdbMediaType.MOVIE), 7.0),
                            SavedRating(item(3, TmdbMediaType.TV), 8.0),
                        ),
                    watchlist = listOf(item(4, TmdbMediaType.MOVIE)),
                    at = 1_000,
                )
            val vm = TmdbAccountViewModel(repo, keyStore)
            accountState.value = TmdbAccountState.Linked("jellyfan")
            assertEquals(ReceivedRatings(ratings = 3, watchlist = 1, latest = listOf("T1", "T3"), readAt = 1_000), vm.state.value.received)
            accountState.value = TmdbAccountState.NotLinked
            assertNull(vm.state.value.received)
        }

    @Test
    fun the_entered_key_shows_masked_and_clearing_it_removes_it() =
        runTest(dispatcher) {
            val vm = TmdbAccountViewModel(repo, keyStore)
            assertNull(vm.state.value.maskedKey)
            assertFalse(vm.state.value.usesBuildKey)

            vm.setKey("abcdefgh12345678")
            assertEquals("•••• 5678", vm.state.value.maskedKey)
            assertEquals("abcdefgh12345678", keyStore.current())

            vm.setKey("")
            assertNull(vm.state.value.maskedKey)
        }

    @Test
    fun a_malformed_key_is_not_saved_and_says_so_until_a_valid_one_is_entered() =
        runTest(dispatcher) {
            val vm = TmdbAccountViewModel(repo, keyStore)

            vm.setKey("abcd efgh12345678")
            assertEquals(R.string.tmdb_key_invalid, vm.state.value.error)
            assertNull(vm.state.value.maskedKey)
            assertEquals("", keyStore.current())

            vm.setKey("abcdefgh12345678")
            assertNull(vm.state.value.error)
            assertEquals("abcdefgh12345678", keyStore.current())
        }

    @Test
    fun a_build_key_is_reported_until_the_user_enters_their_own() =
        runTest(dispatcher) {
            keyStore = TmdbKeyStore(File(tmp.root, "other"), buildDefault = "built-in")
            val vm = TmdbAccountViewModel(repo, keyStore)
            assertNull(vm.state.value.maskedKey)
            assertTrue(vm.state.value.usesBuildKey)

            vm.setKey("mine-1234")
            assertFalse(vm.state.value.usesBuildKey)
        }
}
