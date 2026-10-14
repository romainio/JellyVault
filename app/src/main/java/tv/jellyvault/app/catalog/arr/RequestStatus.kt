package tv.jellyvault.app.catalog.arr

import tv.jellyvault.app.data.model.BaseItem

sealed interface RequestStatus {
    data object NotConfigured : RequestStatus

    data object Unavailable : RequestStatus

    data object NotRequested : RequestStatus

    data object Requested : RequestStatus

    data class Downloading(
        val percent: Int,
    ) : RequestStatus

    data object ReadySoon : RequestStatus

    data class Available(
        val item: BaseItem,
    ) : RequestStatus

    data class Partial(
        val availableSeasons: Int,
        val totalSeasons: Int,
        val item: BaseItem?,
    ) : RequestStatus

    data class Error(
        val reason: ArrException,
    ) : RequestStatus

    /** The download failed or cannot be imported; [reason] is Radarr's or Sonarr's message, if any */
    data class DownloadFailed(
        val reason: String?,
    ) : RequestStatus
}

/**
 * Statuses that change quickly and are refreshed more often
 */
val RequestStatus.isActive: Boolean get() = this is RequestStatus.Downloading || this == RequestStatus.ReadySoon

internal fun queuePercent(records: List<QueueRecord>): Int? {
    if (records.isEmpty()) return null
    val size = records.sumOf { it.size }
    if (size <= 0.0) return 0
    val left = records.sumOf { it.sizeleft }
    return (100 * (1 - left / size)).toInt().coerceIn(0, 99)
}

private val FAILED_STATES = setOf("importfailed", "importblocked", "failedpending", "failed")
private const val MAX_REASON = 400

internal fun QueueRecord.isFailed(): Boolean =
    status.equals("failed", ignoreCase = true) ||
        trackedDownloadStatus.equals("error", ignoreCase = true) ||
        trackedDownloadState?.lowercase() in FAILED_STATES

private fun QueueRecord.failureText(): String? {
    errorMessage?.takeIf { it.isNotBlank() }?.let { return it.take(MAX_REASON) }
    return statusMessages
        .flatMap { m -> m.messages.map { msg -> listOfNotNull(m.title?.takeIf { it.isNotBlank() }, msg).joinToString(": ") } }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .takeIf { it.isNotBlank() }
        ?.take(MAX_REASON)
}

internal data class QueueFailure(
    val reason: String?,
)

/**
 * A failure only when every record of the title failed: one healthy record is still a download
 */
internal fun queueFailure(records: List<QueueRecord>): QueueFailure? {
    if (records.isEmpty() || !records.all { it.isFailed() }) return null
    return QueueFailure(records.firstNotNullOfOrNull { it.failureText() })
}

fun computeMovieStatus(
    configured: Boolean,
    owned: BaseItem?,
    movie: RadarrMovie?,
    queue: List<QueueRecord>,
): RequestStatus {
    if (owned != null) return RequestStatus.Available(owned)
    if (!configured) return RequestStatus.NotConfigured
    if (movie == null) return RequestStatus.NotRequested
    val mine = queue.filter { it.movieId == movie.id }
    queueFailure(mine)?.let { return RequestStatus.DownloadFailed(it.reason) }
    queuePercent(mine.filterNot { it.isFailed() })?.let { return RequestStatus.Downloading(it) }
    if (movie.hasFile) return RequestStatus.ReadySoon
    return RequestStatus.Requested
}

fun computeSeriesStatus(
    configured: Boolean,
    tvdbId: Int?,
    owned: BaseItem?,
    series: SonarrSeries?,
    queue: List<QueueRecord>,
): RequestStatus {
    if (series == null) {
        if (owned != null) return RequestStatus.Available(owned)
        if (!configured) return RequestStatus.NotConfigured
        if (tvdbId == null) return RequestStatus.Unavailable
        return RequestStatus.NotRequested
    }
    if (!configured && owned != null) return RequestStatus.Available(owned)
    if (!configured) return RequestStatus.NotConfigured
    val mine = queue.filter { it.seriesId == series.id }
    queuePercent(mine.filterNot { it.isFailed() })?.let { return RequestStatus.Downloading(it) }
    val seasons = series.seasons.filter { it.seasonNumber > 0 }

    fun complete(s: SonarrSeason) = s.statistics?.let { it.episodeCount > 0 && it.episodeFileCount >= it.episodeCount } ?: false
    val completeCount = seasons.count(::complete)
    val monitoredIncomplete = seasons.any { it.monitored && !complete(it) }
    val anyFile = seasons.any { (it.statistics?.episodeFileCount ?: 0) > 0 }
    // A stuck episode must not hide a series that can already be watched
    if (!anyFile) queueFailure(mine)?.let { return RequestStatus.DownloadFailed(it.reason) }
    return when {
        anyFile && monitoredIncomplete -> RequestStatus.Partial(completeCount, seasons.size, owned)
        anyFile && owned != null -> RequestStatus.Available(owned)
        anyFile -> RequestStatus.ReadySoon
        else -> RequestStatus.Requested
    }
}

/**
 * A season that can be requested from Sonarr. [name] and [episodeCount] come from TMDB when the season numbers match.
 */
data class SeasonChoice(
    val number: Int,
    val name: String?,
    val episodeCount: Int?,
    val locked: Boolean,
)
