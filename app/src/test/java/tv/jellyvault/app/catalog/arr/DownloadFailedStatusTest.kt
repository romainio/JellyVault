package tv.jellyvault.app.catalog.arr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadFailedStatusTest {
    private val movie = RadarrMovie(12, 27205, false, true)

    private fun rec(
        status: String? = "downloading",
        tracked: String? = "ok",
        state: String? = "downloading",
        error: String? = null,
        left: Double = 1.0,
        messages: List<QueueStatusMessage> = emptyList(),
    ) = QueueRecord(
        movieId = 12,
        size = 4.0,
        sizeleft = left,
        status = status,
        trackedDownloadStatus = tracked,
        trackedDownloadState = state,
        errorMessage = error,
        statusMessages = messages,
    )

    private fun status(vararg records: QueueRecord) = computeMovieStatus(true, null, movie, records.toList())

    @Test
    fun failed_states_are_failures() {
        assertEquals(RequestStatus.DownloadFailed("boom"), status(rec(status = "failed", error = "boom")))
        assertEquals(RequestStatus.DownloadFailed(null), status(rec(tracked = "error")))
        assertEquals(RequestStatus.DownloadFailed(null), status(rec(state = "importBlocked")))
        assertEquals(RequestStatus.DownloadFailed(null), status(rec(state = "IMPORTFAILED")))
    }

    @Test
    fun a_warning_is_still_a_download() {
        assertEquals(RequestStatus.Downloading(75), status(rec(tracked = "warning")))
    }

    @Test
    fun records_without_error_fields_are_not_failures() {
        assertFalse(QueueRecord(movieId = 12, size = 4.0, sizeleft = 1.0).isFailed())
        assertNull(queueFailure(listOf(QueueRecord(movieId = 12))))
    }

    @Test
    fun a_healthy_record_keeps_the_status_downloading_with_its_own_percent() {
        assertEquals(
            RequestStatus.Downloading(75),
            status(rec(status = "failed"), rec(left = 1.0)),
        )
    }

    @Test
    fun reason_comes_from_the_error_message_or_the_status_messages() {
        val messages = listOf(QueueStatusMessage("Movie.mkv", listOf("No files found are eligible for import", "Sample")))
        assertEquals(
            RequestStatus.DownloadFailed("Movie.mkv: No files found are eligible for import\nMovie.mkv: Sample"),
            status(rec(state = "importBlocked", messages = messages)),
        )
        assertEquals(RequestStatus.DownloadFailed("x".repeat(400)), status(rec(status = "failed", error = "x".repeat(900))))
    }

    @Test
    fun series_use_the_same_rule() {
        val series = SonarrSeries(13, 81189, true, emptyList())
        val failed = QueueRecord(seriesId = 13, size = 2.0, sizeleft = 1.0, status = "failed", errorMessage = "no")
        assertEquals(RequestStatus.DownloadFailed("no"), computeSeriesStatus(true, 81189, null, series, listOf(failed)))
    }

    @Test
    fun an_owned_series_stays_watchable_when_one_episode_is_stuck() {
        fun season(
            n: Int,
            files: Int,
            count: Int,
            monitored: Boolean = true,
        ) = SonarrSeason(n, monitored, SonarrSeasonStatistics(files, count, count, if (count == 0) 0.0 else 100.0 * files / count))
        val owned =
            tv.jellyvault.app.data.model.BaseItem(
                org.jellyfin.sdk.model.api
                    .BaseItemDto(id = java.util.UUID.randomUUID(), type = org.jellyfin.sdk.model.api.BaseItemKind.SERIES),
            )
        val stuck = QueueRecord(seriesId = 13, size = 2.0, sizeleft = 1.0, trackedDownloadState = "importBlocked")
        val complete = SonarrSeries(13, 81189, true, listOf(season(1, 7, 7)))
        assertEquals(RequestStatus.Available(owned), computeSeriesStatus(true, 81189, owned, complete, listOf(stuck)))
        val partial = SonarrSeries(13, 81189, true, listOf(season(1, 7, 7), season(2, 0, 10)))
        assertEquals(RequestStatus.Partial(1, 2, owned), computeSeriesStatus(true, 81189, owned, partial, listOf(stuck)))
        // Nothing downloaded at all: the failure is shown
        val empty = SonarrSeries(13, 81189, true, listOf(season(1, 0, 7)))
        assertEquals(RequestStatus.DownloadFailed(null), computeSeriesStatus(true, 81189, null, empty, listOf(stuck)))
    }
}
