package tv.jellyvault.app.catalog.arr

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.library.LibraryItemSource
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbSeason
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RequestServiceTest {
    private val radarr = mockk<RadarrClient>(relaxed = true)
    private val sonarr = mockk<SonarrClient>(relaxed = true)
    private val settings = mockk<ArrSettingsRepository>()
    private val index = mockk<LibraryIndex>()
    private val source = mockk<LibraryItemSource>()
    private val service = RequestService(radarr, sonarr, settings, index, source)

    private val ok = ArrConfig("https://arr.example.test/x", "k", qualityProfileId = 1, rootFolderPath = "/d")

    private fun details(
        type: TmdbMediaType,
        tvdb: Int? = null,
    ) = TmdbDetails(
        item = TmdbItem(27205, type, "T", null, "/p.jpg", null, null, null, emptyList()),
        runtimeMinutes = null,
        seasons = listOf(TmdbSeason(1, "S1", 7, null), TmdbSeason(2, "S2", 13, null)),
        genres = emptyList(),
        tagline = null,
        certification = null,
        logoUrl = null,
        trailerKey = null,
        recommendations = emptyList(),
        tvdbId = tvdb,
    )

    init {
        coEvery { settings.current(any()) } returns ok
        coEvery { index.awaitIndex(any()) } returns emptyMap()
    }

    @Test
    fun movie_status_not_requested_then_downloading() =
        runTest {
            coEvery { radarr.movieByTmdbId(27205) } returns null
            assertEquals(RequestStatus.NotRequested, service.currentStatus(details(TmdbMediaType.MOVIE)))

            coEvery { radarr.movieByTmdbId(27205) } returns RadarrMovie(12, 27205, false, true)
            coEvery { radarr.queue() } returns listOf(QueueRecord(movieId = 12, size = 4.0, sizeleft = 1.0))
            assertEquals(RequestStatus.Downloading(75), service.currentStatus(details(TmdbMediaType.MOVIE)))
        }

    @Test
    fun errors_become_error_status() =
        runTest {
            coEvery { radarr.movieByTmdbId(any()) } throws ArrException.Unauthorized(401)
            val status = service.currentStatus(details(TmdbMediaType.MOVIE))
            assertEquals(true, (status as RequestStatus.Error).reason is ArrException.Unauthorized)
        }

    @Test
    fun concurrent_requests_send_a_single_post() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { radarr.movieByTmdbId(any()) } returns null
            coEvery { radarr.addMovie(27205) } coAnswers {
                gate.await()
                RadarrMovie(12, 27205, false, true)
            }
            val a = async { service.requestMovie(details(TmdbMediaType.MOVIE)) }
            val b = async { service.requestMovie(details(TmdbMediaType.MOVIE)) }
            runCurrent()
            gate.complete(Unit)
            a.await()
            b.await()
            coVerify(exactly = 1) { radarr.addMovie(27205) }
        }

    @Test
    fun new_series_is_added_with_selected_seasons() =
        runTest {
            coEvery { sonarr.seriesByTvdbId(81189) } returns null
            coEvery { sonarr.lookupSeasons(81189) } returns listOf(SonarrSeason(1, false, null), SonarrSeason(2, false, null))
            coEvery { sonarr.addSeries(81189, setOf(2)) } returns SonarrSeries(20, 81189)
            service.requestSeries(details(TmdbMediaType.TV, tvdb = 81189), setOf(2))
            coVerify(exactly = 1) { sonarr.addSeries(81189, setOf(2)) }
            coVerify(exactly = 0) { sonarr.monitorSeasons(any(), any()) }
        }

    @Test
    fun existing_series_only_adds_new_seasons() =
        runTest {
            coEvery { sonarr.seriesByTvdbId(81189) } returns
                SonarrSeries(13, 81189, true, listOf(SonarrSeason(1, true, null), SonarrSeason(2, false, null)))
            service.requestSeries(details(TmdbMediaType.TV, tvdb = 81189), setOf(1, 2))
            coVerify(exactly = 1) { sonarr.monitorSeasons(13, setOf(2)) }
            coVerify(exactly = 0) { sonarr.addSeries(any(), any()) }
        }

    @Test
    fun unexpected_exception_becomes_error_status() =
        runTest {
            coEvery { radarr.movieByTmdbId(any()) } throws IllegalStateException("boom")
            val status = service.currentStatus(details(TmdbMediaType.MOVIE))
            assertEquals(true, (status as RequestStatus.Error).reason is ArrException.InvalidResponse)
        }

    @Test
    fun ready_soon_refreshes_library_index_throttled_and_becomes_available() =
        runTest {
            val ownedId = UUID.randomUUID()
            var indexed = false
            coEvery { radarr.movieByTmdbId(27205) } returns RadarrMovie(12, 27205, hasFile = true, monitored = true)
            coEvery { index.awaitIndex(any()) } answers
                { if (indexed) mapOf(TmdbKey(TmdbMediaType.MOVIE, 27205) to ownedId) else emptyMap() }
            coEvery { index.refresh() } answers { }
            var now = 0L
            service.now = { now }

            assertEquals(RequestStatus.ReadySoon, service.currentStatus(details(TmdbMediaType.MOVIE)))
            coVerify(exactly = 1) { index.refresh() }
            now += 10_000
            service.currentStatus(details(TmdbMediaType.MOVIE))
            coVerify(exactly = 1) { index.refresh() }

            indexed = true
            coEvery { source.fetchItems(listOf(ownedId)) } returns listOf(BaseItemDto(id = ownedId, type = BaseItemKind.MOVIE))
            now += RequestService.INDEX_REFRESH_THROTTLE_MS
            assertEquals(true, service.currentStatus(details(TmdbMediaType.MOVIE)) is RequestStatus.Available)
        }

    @Test
    fun season_choices_follow_sonarr_numbering() =
        runTest {
            coEvery { sonarr.seriesByTvdbId(81189) } returns null
            coEvery { sonarr.lookupSeasons(81189) } returns
                listOf(
                    SonarrSeason(0, false, null),
                    SonarrSeason(1, false, null),
                    SonarrSeason(2, false, null),
                    SonarrSeason(3, false, null),
                )
            val choices = service.seasonChoices(details(TmdbMediaType.TV, tvdb = 81189))
            assertEquals(listOf(1, 2, 3), choices.map { it.number })
            assertEquals(listOf("S1", "S2", null), choices.map { it.name })
            assertEquals(listOf(7, 13, null), choices.map { it.episodeCount })
            assertEquals(emptyList<Int>(), choices.filter { it.locked }.map { it.number })
        }

    @Test
    fun season_choices_lock_monitored_sonarr_seasons() =
        runTest {
            coEvery { sonarr.seriesByTvdbId(81189) } returns
                SonarrSeries(13, 81189, true, listOf(SonarrSeason(1, true, null), SonarrSeason(2, false, null)))
            val choices = service.seasonChoices(details(TmdbMediaType.TV, tvdb = 81189))
            assertEquals(listOf(1), choices.filter { it.locked }.map { it.number })
        }

    @Test
    fun requesting_seasons_unknown_to_sonarr_fails_instead_of_monitoring_nothing() =
        runTest {
            coEvery { sonarr.seriesByTvdbId(81189) } returns null
            coEvery { sonarr.lookupSeasons(81189) } returns listOf(SonarrSeason(1, false, null))
            try {
                service.requestSeries(details(TmdbMediaType.TV, tvdb = 81189), setOf(5))
                fail("expected InvalidResponse")
            } catch (_: ArrException.InvalidResponse) {
            }
            coVerify(exactly = 0) { sonarr.addSeries(any(), any()) }
        }

    @Test
    fun requested_is_polled_every_15_seconds() =
        runTest {
            coEvery { radarr.movieByTmdbId(27205) } returns RadarrMovie(12, 27205, hasFile = false, monitored = true)
            coEvery { radarr.queue() } returns emptyList()
            val seen = mutableListOf<RequestStatus>()
            backgroundScope.launch { service.status(details(TmdbMediaType.MOVIE), emptyFlow()).collect { seen += it } }
            runCurrent()
            assertEquals(listOf<RequestStatus>(RequestStatus.Requested), seen)
            advanceTimeBy(14_000)
            runCurrent()
            assertEquals(1, seen.size)
            advanceTimeBy(1_500)
            runCurrent()
            assertEquals(2, seen.size)
        }

    @Test
    fun refresh_delays() {
        assertEquals(15_000L, RequestService.refreshDelayMs(RequestStatus.ReadySoon, boosting = false))
        assertEquals(5_000L, RequestService.refreshDelayMs(RequestStatus.ReadySoon, boosting = true))
        assertEquals(5_000L, RequestService.refreshDelayMs(RequestStatus.Downloading(10), boosting = false))
        assertEquals(15_000L, RequestService.refreshDelayMs(RequestStatus.Requested, boosting = false))
        assertEquals(15_000L, RequestService.refreshDelayMs(RequestStatus.DownloadFailed("x"), boosting = false))
        assertEquals(60_000L, RequestService.refreshDelayMs(RequestStatus.NotRequested, boosting = false))
    }

    @Test
    fun refresh_now_asks_jellyfin_to_scan_then_reads_the_index_again() =
        runTest {
            coEvery { source.requestLibraryScan() } returns true
            coEvery { index.refresh() } returns Unit
            assertEquals(RefreshOutcome(scanRequested = true), service.refreshNow())
            coVerify(exactly = 1) { source.requestLibraryScan() }
            coVerify(exactly = 1) { index.refresh() }
        }

    @Test
    fun a_refused_scan_still_reads_the_index_again() =
        runTest {
            coEvery { source.requestLibraryScan() } returns false
            coEvery { index.refresh() } returns Unit
            assertEquals(RefreshOutcome(scanRequested = false), service.refreshNow())
            coVerify(exactly = 1) { index.refresh() }
        }
}
