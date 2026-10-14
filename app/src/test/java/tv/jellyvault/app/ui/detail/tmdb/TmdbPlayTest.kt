package tv.jellyvault.app.ui.detail.tmdb

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.arr.RefreshOutcome
import tv.jellyvault.app.catalog.arr.RequestService
import tv.jellyvault.app.catalog.arr.RequestStatus
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.FavoriteWatchManager
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbPlayTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val tmdb = mockk<TmdbClient>()
    private val requests = mockk<RequestService>(relaxed = true)
    private val catalog = mockk<CatalogRepository>()
    private val backdrop = mockk<BackdropService>(relaxed = true)
    private val navigation = mockk<NavigationManager>(relaxed = true)
    private val loader = mockk<OwnedPlaybackLoader>()
    private val favoriteWatch = mockk<FavoriteWatchManager>()

    private val owned = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))
    private val full = BaseItem(BaseItemDto(id = owned.id, type = BaseItemKind.MOVIE, name = "full"))

    private val tmdbItem = TmdbItem(10, TmdbMediaType.MOVIE, "Titre", "Résumé", "/p.jpg", "/b.jpg", "2020-01-01", 7.0, emptyList())

    private val details =
        TmdbDetails(
            item = tmdbItem,
            runtimeMinutes = 100,
            seasons = emptyList(),
            genres = emptyList(),
            tagline = null,
            certification = null,
            logoUrl = null,
            trailerKey = null,
            recommendations = emptyList(),
            tvdbId = null,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
        coEvery { catalog.cards(any()) } returns emptyList()
        coEvery { tmdb.details(TmdbMediaType.MOVIE, 10) } returns details
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        JvDispatchers.reset()
    }

    private fun viewModel(
        status: RequestStatus,
        playback: OwnedPlayback?,
    ): TmdbDetailViewModel {
        every { requests.status(any(), any()) } returns flowOf(status)
        coEvery { loader.load(any()) } returns playback
        return TmdbDetailViewModel(tmdb, requests, catalog, backdrop, navigation, loader, favoriteWatch).also { it.init(tmdbItem) }
    }

    @Test
    fun play_goes_straight_to_playback_when_details_are_loaded() =
        runTest(dispatcher) {
            val vm = viewModel(RequestStatus.Available(owned), OwnedPlayback(full, null))
            vm.onPrimaryClick()
            verify { navigation.navigateTo(Destination.Playback(full)) }
        }

    @Test
    fun without_playback_details_the_button_opens_the_jellyfin_page() =
        runTest(dispatcher) {
            val vm = viewModel(RequestStatus.Available(owned), null)
            vm.onPrimaryClick()
            verify { navigation.navigateTo(owned.destination()) }
        }

    @Test
    fun returning_to_the_page_reloads_the_playback_details() =
        runTest(dispatcher) {
            val vm = viewModel(RequestStatus.Available(owned), OwnedPlayback(full, null))
            coVerify(exactly = 1) { loader.load(owned) }
            vm.onHidden()
            vm.onVisible()
            coVerify(exactly = 2) { loader.load(owned) }
        }

    @Test
    fun refresh_ignores_a_second_press_while_running() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<RefreshOutcome>()
            coEvery { requests.refreshNow() } coAnswers { gate.await() }
            val vm = viewModel(RequestStatus.ReadySoon, null)
            vm.refreshNow()
            vm.refreshNow()
            assertTrue(vm.state.value.refreshing)
            gate.complete(RefreshOutcome(scanRequested = true))
            coVerify(exactly = 1) { requests.refreshNow() }
            assertFalse(vm.state.value.refreshing)
        }

    @Test
    fun refresh_reports_whether_jellyfin_accepted_the_scan() =
        runTest(dispatcher) {
            coEvery { requests.refreshNow() } returns RefreshOutcome(scanRequested = true)
            val vm = viewModel(RequestStatus.ReadySoon, null)
            vm.refreshNow()
            assertEquals(TmdbDetailEvent.Refreshed(true), vm.state.value.event)
            vm.consumeEvent()
            coEvery { requests.refreshNow() } returns RefreshOutcome(scanRequested = false)
            vm.refreshNow()
            assertEquals(TmdbDetailEvent.Refreshed(false), vm.state.value.event)
        }

    @Test
    fun a_late_playback_load_is_dropped_when_the_title_is_no_longer_owned() =
        runTest(dispatcher) {
            val statuses = kotlinx.coroutines.flow.MutableSharedFlow<RequestStatus>(extraBufferCapacity = 8)
            every { requests.status(any(), any()) } returns statuses
            val gate = CompletableDeferred<OwnedPlayback?>()
            coEvery { loader.load(any()) } coAnswers { gate.await() }
            val vm = TmdbDetailViewModel(tmdb, requests, catalog, backdrop, navigation, loader, favoriteWatch).also { it.init(tmdbItem) }
            statuses.tryEmit(RequestStatus.Available(owned))
            statuses.tryEmit(RequestStatus.NotRequested)
            gate.complete(OwnedPlayback(full, null))
            assertEquals(null, vm.state.value.playback)
        }

    @Test
    fun the_eye_marks_the_owned_title_watched_then_reloads_what_to_play() =
        runTest(dispatcher) {
            val vm = viewModel(RequestStatus.Available(owned), OwnedPlayback(full, null))
            coEvery { favoriteWatch.setWatched(full.id, true) } returns userData(played = true, favorite = false)
            vm.toggleWatched()
            coVerify(exactly = 1) { favoriteWatch.setWatched(full.id, true) }
            coVerify(exactly = 2) { loader.load(owned) }
        }

    @Test
    fun the_heart_updates_the_title_at_once() =
        runTest(dispatcher) {
            val vm = viewModel(RequestStatus.Available(owned), OwnedPlayback(full, null))
            coEvery { favoriteWatch.setFavorite(full.id, true) } returns userData(played = false, favorite = true)
            vm.toggleFavorite()
            assertTrue(
                vm.state.value.playback!!
                    .title.favorite,
            )
        }

    private fun userData(
        played: Boolean,
        favorite: Boolean,
    ) = UserItemDataDto(
        playbackPositionTicks = 0,
        playCount = 0,
        isFavorite = favorite,
        played = played,
        key = "k",
        itemId = full.id,
    )

    @Test
    fun the_primary_button_takes_focus_unless_it_is_disabled_waiting_for_jellyfin() {
        assertTrue(shouldFocusPrimary(null))
        assertTrue(shouldFocusPrimary(RequestStatus.Available(owned)))
        assertTrue(shouldFocusPrimary(RequestStatus.DownloadFailed(null)))
        assertFalse(shouldFocusPrimary(RequestStatus.ReadySoon))
    }
}
