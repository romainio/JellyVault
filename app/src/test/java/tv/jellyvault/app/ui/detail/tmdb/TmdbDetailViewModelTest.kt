package tv.jellyvault.app.ui.detail.tmdb

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.arr.ArrException
import tv.jellyvault.app.catalog.arr.ArrService
import tv.jellyvault.app.catalog.arr.RequestService
import tv.jellyvault.app.catalog.arr.RequestStatus
import tv.jellyvault.app.catalog.arr.SeasonChoice
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbSeason
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.FavoriteWatchManager
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val tmdb = mockk<TmdbClient>()
    private val requests = mockk<RequestService>(relaxed = true)
    private val catalog = mockk<CatalogRepository>()
    private val backdrop = mockk<BackdropService>(relaxed = true)
    private val navigation = mockk<NavigationManager>(relaxed = true)
    private val playbackLoader = mockk<OwnedPlaybackLoader>(relaxed = true)
    private val favoriteWatch = mockk<FavoriteWatchManager>(relaxed = true)

    private fun item(type: TmdbMediaType) = TmdbItem(10, type, "Titre", "Résumé", "/p.jpg", "/b.jpg", "2020-01-01", 7.0, emptyList())

    private fun details(type: TmdbMediaType) =
        TmdbDetails(
            item = item(type),
            runtimeMinutes = 100,
            seasons = listOf(TmdbSeason(1, "S1", 7, null), TmdbSeason(2, "S2", 8, null)),
            genres = listOf("Drame"),
            tagline = null,
            certification = "12",
            logoUrl = null,
            trailerKey = "yt",
            recommendations = emptyList(),
            tvdbId = if (type == TmdbMediaType.TV) 81189 else null,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
        coEvery { catalog.cards(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        JvDispatchers.reset()
    }

    private fun viewModel(
        type: TmdbMediaType,
        status: RequestStatus,
    ): TmdbDetailViewModel {
        coEvery { tmdb.details(type, 10) } returns details(type)
        every { requests.status(any(), any()) } returns flowOf(status)
        return TmdbDetailViewModel(
            tmdb,
            requests,
            catalog,
            backdrop,
            navigation,
            playbackLoader,
            favoriteWatch,
        ).also { it.init(item(type)) }
    }

    @Test
    fun loads_details_then_status() =
        runTest(dispatcher) {
            val vm = viewModel(TmdbMediaType.MOVIE, RequestStatus.NotRequested)
            assertTrue(vm.state.value.details is DataLoadingState.Success)
            assertEquals(RequestStatus.NotRequested, vm.state.value.status)
            coVerify { backdrop.submit("tmdb_movie_10", "https://image.tmdb.org/t/p/w1280/b.jpg") }
        }

    @Test
    fun details_error_is_shown_and_retry_reloads() =
        runTest(dispatcher) {
            coEvery { tmdb.details(TmdbMediaType.MOVIE, 10) } throws TmdbException("down")
            every { requests.status(any(), any()) } returns flowOf(RequestStatus.NotRequested)
            val vm = TmdbDetailViewModel(tmdb, requests, catalog, backdrop, navigation, playbackLoader, favoriteWatch)
            vm.init(item(TmdbMediaType.MOVIE))
            assertTrue(vm.state.value.details is DataLoadingState.Error)

            coEvery { tmdb.details(TmdbMediaType.MOVIE, 10) } returns details(TmdbMediaType.MOVIE)
            vm.retry()
            assertTrue(vm.state.value.details is DataLoadingState.Success)
        }

    @Test
    fun primary_click_on_movie_requests_once_and_emits_toast_event() =
        runTest(dispatcher) {
            val vm = viewModel(TmdbMediaType.MOVIE, RequestStatus.NotRequested)
            vm.onPrimaryClick()
            coVerify(exactly = 1) { requests.requestMovie(any()) }
            assertEquals(TmdbDetailEvent.Requested(ArrService.RADARR), vm.state.value.event)
            vm.consumeEvent()
            assertNull(vm.state.value.event)
        }

    @Test
    fun primary_click_on_series_opens_dialog_with_requested_seasons() =
        runTest(dispatcher) {
            coEvery { requests.seasonChoices(any()) } returns
                listOf(SeasonChoice(1, "S1", 7, locked = true), SeasonChoice(2, "S2", 8, locked = false))
            val vm = viewModel(TmdbMediaType.TV, RequestStatus.NotRequested)
            vm.onPrimaryClick()
            val dialog = vm.state.value.seasonDialog!!
            assertEquals(listOf(1, 2), dialog.choices.map { it.number })
            assertEquals(listOf(1), dialog.choices.filter { it.locked }.map { it.number })

            vm.requestSeasons(setOf(2))
            assertNull(vm.state.value.seasonDialog)
            coVerify(exactly = 1) { requests.requestSeries(any(), setOf(2)) }
            assertEquals(TmdbDetailEvent.Requested(ArrService.SONARR), vm.state.value.event)
        }

    @Test
    fun request_failure_emits_error_event() =
        runTest(dispatcher) {
            coEvery { requests.requestMovie(any()) } throws ArrException.Unreachable(IOException("x"))
            val vm = viewModel(TmdbMediaType.MOVIE, RequestStatus.NotRequested)
            vm.onPrimaryClick()
            assertEquals(TmdbDetailEvent.Failed(ArrService.RADARR, R.string.arr_error_unreachable, null), vm.state.value.event)
            assertEquals(false, vm.state.value.sending)
        }

    @Test
    fun available_primary_click_falls_back_to_the_jellyfin_page_without_playback_details() =
        runTest(dispatcher) {
            val owned = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))
            coEvery { playbackLoader.load(any()) } returns null
            val vm = viewModel(TmdbMediaType.MOVIE, RequestStatus.Available(owned))
            vm.onPrimaryClick()
            verify { navigation.navigateTo(owned.destination()) }
        }

    @Test
    fun not_configured_click_opens_settings() =
        runTest(dispatcher) {
            val vm = viewModel(TmdbMediaType.MOVIE, RequestStatus.NotConfigured)
            vm.onPrimaryClick()
            verify { navigation.navigateTo(Destination.ArrSettings(ArrService.RADARR)) }
        }

    @Test
    fun polling_stops_when_hidden_and_restarts_when_visible() =
        runTest(dispatcher) {
            var collections = 0
            var active = 0
            coEvery { tmdb.details(TmdbMediaType.MOVIE, 10) } returns details(TmdbMediaType.MOVIE)
            every { requests.status(any(), any()) } returns
                flow {
                    collections++
                    active++
                    try {
                        emit(RequestStatus.NotRequested)
                        awaitCancellation()
                    } finally {
                        active--
                    }
                }
            val vm = TmdbDetailViewModel(tmdb, requests, catalog, backdrop, navigation, playbackLoader, favoriteWatch)
            vm.init(item(TmdbMediaType.MOVIE))
            assertEquals(1, collections)
            assertEquals(1, active)
            vm.onHidden()
            assertEquals(0, active)
            vm.onVisible()
            assertEquals(2, collections)
            assertEquals(1, active)
            vm.onVisible()
            assertEquals(2, collections)
            vm.onHidden()
        }

    @Test
    fun unexpected_exception_resets_sending_and_reports_error() =
        runTest(dispatcher) {
            coEvery { requests.requestMovie(any()) } throws IllegalStateException("boom")
            val vm = viewModel(TmdbMediaType.MOVIE, RequestStatus.NotRequested)
            vm.onPrimaryClick()
            assertEquals(false, vm.state.value.sending)
            assertEquals(TmdbDetailEvent.Failed(ArrService.RADARR, R.string.arr_error_invalid, null), vm.state.value.event)
        }

    @Test
    fun button_stays_disabled_until_refreshed_status_arrives() =
        runTest(dispatcher) {
            val statuses = MutableSharedFlow<RequestStatus>(replay = 1)
            statuses.emit(RequestStatus.NotRequested)
            coEvery { tmdb.details(TmdbMediaType.MOVIE, 10) } returns details(TmdbMediaType.MOVIE)
            every { requests.status(any(), any()) } returns statuses
            val vm = TmdbDetailViewModel(tmdb, requests, catalog, backdrop, navigation, playbackLoader, favoriteWatch)
            vm.init(item(TmdbMediaType.MOVIE))
            vm.onPrimaryClick()
            assertEquals(true, vm.state.value.sending)
            vm.onPrimaryClick()
            coVerify(exactly = 1) { requests.requestMovie(any()) }
            statuses.emit(RequestStatus.Requested)
            assertEquals(false, vm.state.value.sending)
            assertEquals(RequestStatus.Requested, vm.state.value.status)
        }

    @Test
    fun all_seasons_already_requested_shows_a_message_instead_of_an_empty_dialog() =
        runTest(dispatcher) {
            coEvery { requests.seasonChoices(any()) } returns
                listOf(SeasonChoice(1, "S1", 7, locked = true), SeasonChoice(2, "S2", 8, locked = true))
            val vm = viewModel(TmdbMediaType.TV, RequestStatus.Partial(1, 2, null))
            vm.onPrimaryClick()
            assertNull(vm.state.value.seasonDialog)
            assertEquals(TmdbDetailEvent.AllSeasonsRequested, vm.state.value.event)
        }
}
