package tv.jellyvault.app.catalog.arr

import kotlinx.serialization.Serializable

enum class ArrService {
    RADARR,
    SONARR,
}

@Serializable
data class SystemStatus(
    val version: String = "",
)

@Serializable
data class QualityProfile(
    val id: Int,
    val name: String,
)

@Serializable
data class RootFolder(
    val path: String,
    val freeSpace: Long? = null,
)

@Serializable
data class QueueStatusMessage(
    val title: String? = null,
    val messages: List<String> = emptyList(),
)

@Serializable
data class QueueRecord(
    val movieId: Int? = null,
    val seriesId: Int? = null,
    val seasonNumber: Int? = null,
    val size: Double = 0.0,
    val sizeleft: Double = 0.0,
    val status: String? = null,
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
    val errorMessage: String? = null,
    val statusMessages: List<QueueStatusMessage> = emptyList(),
)

@Serializable
internal data class QueuePage(
    val records: List<QueueRecord> = emptyList(),
)

@Serializable
data class RadarrMovie(
    val id: Int,
    val tmdbId: Int,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
)

@Serializable
data class SonarrSeasonStatistics(
    val episodeFileCount: Int = 0,
    val episodeCount: Int = 0,
    val totalEpisodeCount: Int = 0,
    val percentOfEpisodes: Double = 0.0,
)

@Serializable
data class SonarrSeason(
    val seasonNumber: Int,
    val monitored: Boolean = false,
    val statistics: SonarrSeasonStatistics? = null,
)

@Serializable
data class SonarrSeries(
    val id: Int,
    val tvdbId: Int,
    val monitored: Boolean = false,
    val seasons: List<SonarrSeason> = emptyList(),
)

@Serializable
internal data class SonarrLookup(
    val seasons: List<SonarrSeason> = emptyList(),
)
