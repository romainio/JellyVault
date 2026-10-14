package tv.jellyvault.app.catalog.arr

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jellyvault.app.data.model.BaseItem
import java.util.UUID

class ComputeStatusTest {
    private val owned = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))

    private fun season(
        n: Int,
        monitored: Boolean,
        files: Int,
        count: Int,
    ) = SonarrSeason(n, monitored, SonarrSeasonStatistics(files, count, count, if (count == 0) 0.0 else 100.0 * files / count))

    @Test
    fun movie_rules() {
        val movie = RadarrMovie(12, 27205, hasFile = false, monitored = true)
        assertEquals(RequestStatus.Available(owned), computeMovieStatus(true, owned, null, emptyList()))
        assertEquals(RequestStatus.NotConfigured, computeMovieStatus(false, null, movie, emptyList()))
        assertEquals(RequestStatus.NotRequested, computeMovieStatus(true, null, null, emptyList()))
        assertEquals(
            RequestStatus.Downloading(75),
            computeMovieStatus(true, null, movie, listOf(QueueRecord(movieId = 12, size = 1000.0, sizeleft = 250.0))),
        )
        assertEquals(RequestStatus.Requested, computeMovieStatus(true, null, movie, listOf(QueueRecord(movieId = 99, size = 1.0))))
        assertEquals(RequestStatus.ReadySoon, computeMovieStatus(true, null, movie.copy(hasFile = true), emptyList()))
    }

    @Test
    fun owned_movie_is_available_even_if_radarr_not_configured() {
        assertEquals(RequestStatus.Available(owned), computeMovieStatus(false, owned, null, emptyList()))
    }

    @Test
    fun series_rules() {
        val base = SonarrSeries(13, 81189, true, listOf(season(0, false, 0, 0), season(1, true, 7, 7), season(2, true, 0, 13)))
        assertEquals(RequestStatus.Unavailable, computeSeriesStatus(true, null, null, null, emptyList()))
        assertEquals(RequestStatus.NotConfigured, computeSeriesStatus(false, 81189, null, null, emptyList()))
        assertEquals(RequestStatus.NotRequested, computeSeriesStatus(true, 81189, null, null, emptyList()))
        assertEquals(
            RequestStatus.Downloading(50),
            computeSeriesStatus(true, 81189, null, base, listOf(QueueRecord(seriesId = 13, size = 10.0, sizeleft = 5.0))),
        )
        assertEquals(RequestStatus.Partial(1, 2, null), computeSeriesStatus(true, 81189, null, base, emptyList()))
        assertEquals(RequestStatus.Partial(1, 2, owned), computeSeriesStatus(true, 81189, owned, base, emptyList()))

        val complete = base.copy(seasons = listOf(season(1, true, 7, 7), season(2, true, 13, 13)))
        assertEquals(RequestStatus.ReadySoon, computeSeriesStatus(true, 81189, null, complete, emptyList()))
        assertEquals(RequestStatus.Available(owned), computeSeriesStatus(true, 81189, owned, complete, emptyList()))

        val none = base.copy(seasons = listOf(season(1, true, 0, 7), season(2, false, 0, 0)))
        assertEquals(RequestStatus.Requested, computeSeriesStatus(true, 81189, null, none, emptyList()))
        assertEquals(RequestStatus.Available(owned), computeSeriesStatus(true, 81189, owned, null, emptyList()))
    }

    @Test
    fun queue_percent_aggregates_and_handles_zero_size() {
        assertNull(queuePercent(emptyList()))
        assertEquals(0, queuePercent(listOf(QueueRecord(size = 0.0, sizeleft = 0.0))))
        assertEquals(50, queuePercent(listOf(QueueRecord(size = 1000.0, sizeleft = 900.0), QueueRecord(size = 1000.0, sizeleft = 100.0))))
        assertEquals(99, queuePercent(listOf(QueueRecord(size = 1000.0, sizeleft = 0.0))))
    }
}
