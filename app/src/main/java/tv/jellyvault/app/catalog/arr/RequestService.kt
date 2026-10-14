package tv.jellyvault.app.catalog.arr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import tv.jellyvault.app.catalog.key
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.library.LibraryItemSource
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.BaseItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of « Actualiser »: whether Jellyfin accepted the scan
 */
data class RefreshOutcome(
    val scanRequested: Boolean,
)

/**
 * Requests TMDB titles from Radarr/Sonarr and computes their status (including Jellyfin availability)
 */
@Singleton
class RequestService
    @Inject
    constructor(
        private val radarr: RadarrClient,
        private val sonarr: SonarrClient,
        private val settings: ArrSettingsRepository,
        private val libraryIndex: LibraryIndex,
        private val librarySource: LibraryItemSource,
    ) {
        private val requestMutex = Mutex()

        @Volatile
        private var lastIndexRefreshMs: Long? = null

        @Volatile
        private var boostUntilMs: Long = 0L

        private val boosting: Boolean get() = now() < boostUntilMs

        internal var now: () -> Long = System::currentTimeMillis

        suspend fun currentStatus(details: TmdbDetails): RequestStatus =
            try {
                val status = computeStatus(details)
                if (status == RequestStatus.ReadySoon && refreshLibraryIndexIfDue()) computeStatus(details) else status
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArrException) {
                Timber.w(e, "Request status failed")
                RequestStatus.Error(e)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected request status failure")
                RequestStatus.Error(ArrException.InvalidResponse(e))
            }

        private suspend fun computeStatus(details: TmdbDetails): RequestStatus {
            val owned = ownedItem(details)
            return when (details.item.mediaType) {
                TmdbMediaType.MOVIE -> {
                    val configured = settings.current(ArrService.RADARR).isConfigured
                    if (!configured) return computeMovieStatus(false, owned, null, emptyList())
                    val movie = radarr.movieByTmdbId(details.item.tmdbId)
                    val queue = if (movie != null && !movie.hasFile) radarr.queue() else emptyList()
                    computeMovieStatus(true, owned, movie, queue)
                }

                TmdbMediaType.TV -> {
                    val configured = settings.current(ArrService.SONARR).isConfigured
                    val tvdbId = details.tvdbId
                    if (!configured || tvdbId == null) return computeSeriesStatus(configured, tvdbId, owned, null, emptyList())
                    val series = sonarr.seriesByTvdbId(tvdbId)
                    val queue = if (series != null) sonarr.queue() else emptyList()
                    computeSeriesStatus(true, tvdbId, owned, series, queue)
                }
            }
        }

        /**
         * Downloaded titles only become watchable once Jellyfin has them: refresh the library index, at most once a minute
         */
        private suspend fun refreshLibraryIndexIfDue(): Boolean {
            val last = lastIndexRefreshMs
            val throttle = if (boosting) BOOST_INDEX_THROTTLE_MS else INDEX_REFRESH_THROTTLE_MS
            if (last != null && now() - last < throttle) return false
            lastIndexRefreshMs = now()
            libraryIndex.refresh()
            return true
        }

        /**
         * « Actualiser »: ask Jellyfin to scan, read its library again, then watch closely for a minute
         */
        suspend fun refreshNow(): RefreshOutcome {
            val scan = librarySource.requestLibraryScan()
            libraryIndex.refresh()
            lastIndexRefreshMs = now()
            boostUntilMs = now() + BOOST_MS
            return RefreshOutcome(scan)
        }

        /**
         * Emits the status now, then every 5 s while active (60 s otherwise), and immediately when [refresh] emits
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun status(
            details: TmdbDetails,
            refresh: Flow<Unit>,
        ): Flow<RequestStatus> =
            channelFlow {
                val trigger = Channel<Unit>(Channel.CONFLATED)
                launch { refresh.collect { trigger.trySend(Unit) } }
                while (true) {
                    val status = currentStatus(details)
                    send(status)
                    val delayMs = refreshDelayMs(status, boosting)
                    select {
                        trigger.onReceive { }
                        onTimeout(delayMs) { }
                    }
                }
            }

        suspend fun requestMovie(details: TmdbDetails) {
            if (!requestMutex.tryLock()) return
            try {
                if (radarr.movieByTmdbId(details.item.tmdbId) == null) {
                    radarr.addMovie(details.item.tmdbId)
                }
            } finally {
                requestMutex.unlock()
            }
        }

        suspend fun requestSeries(
            details: TmdbDetails,
            seasons: Set<Int>,
        ) {
            val tvdbId = details.tvdbId ?: throw ArrException.Http(404)
            if (!requestMutex.tryLock()) return
            try {
                val existing = sonarr.seriesByTvdbId(tvdbId)
                val known = (existing?.seasons ?: sonarr.lookupSeasons(tvdbId)).map { it.seasonNumber }.toSet()
                val wanted = seasons intersect known
                if (wanted.isEmpty()) {
                    throw ArrException.InvalidResponse(IllegalArgumentException("Seasons $seasons are unknown to Sonarr ($known)"))
                }
                if (existing == null) {
                    sonarr.addSeries(tvdbId, wanted)
                } else {
                    val alreadyMonitored =
                        existing.seasons
                            .filter { it.monitored }
                            .map { it.seasonNumber }
                            .toSet()
                    val newSeasons = wanted - alreadyMonitored
                    if (newSeasons.isNotEmpty()) sonarr.monitorSeasons(existing.id, newSeasons)
                }
            } finally {
                requestMutex.unlock()
            }
        }

        /**
         * Seasons that can be requested, numbered as Sonarr (TVDB) numbers them, with TMDB names when the numbers match.
         * Seasons already monitored in Sonarr are locked.
         */
        suspend fun seasonChoices(details: TmdbDetails): List<SeasonChoice> {
            val tvdbId = details.tvdbId ?: return emptyList()
            val existing = sonarr.seriesByTvdbId(tvdbId)
            val seasons = existing?.seasons ?: sonarr.lookupSeasons(tvdbId)
            val tmdbByNumber = details.seasons.associateBy { it.number }
            return seasons
                .filter { it.seasonNumber > 0 }
                .sortedBy { it.seasonNumber }
                .map { season ->
                    val tmdb = tmdbByNumber[season.seasonNumber]
                    SeasonChoice(
                        number = season.seasonNumber,
                        name = tmdb?.name?.takeIf { it.isNotBlank() },
                        episodeCount = tmdb?.episodeCount ?: season.statistics?.totalEpisodeCount?.takeIf { it > 0 },
                        locked = existing != null && season.monitored,
                    )
                }
        }

        private suspend fun ownedItem(details: TmdbDetails): BaseItem? {
            val id = libraryIndex.awaitIndex()[details.item.key] ?: return null
            return try {
                librarySource.fetchItems(listOf(id)).firstOrNull()?.let { BaseItem(it, useSeriesForPrimary = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not fetch owned item")
                null
            }
        }

        companion object {
            const val ACTIVE_REFRESH_MS = 5_000L
            const val REQUESTED_REFRESH_MS = 15_000L
            const val READY_SOON_REFRESH_MS = 15_000L
            const val BOOST_REFRESH_MS = 5_000L
            const val BOOST_MS = 60_000L
            const val IDLE_REFRESH_MS = 60_000L
            const val INDEX_REFRESH_THROTTLE_MS = 15_000L
            const val BOOST_INDEX_THROTTLE_MS = 5_000L

            /**
             * How long to wait before checking the status again
             */
            fun refreshDelayMs(
                status: RequestStatus,
                boosting: Boolean,
            ): Long =
                when (status) {
                    is RequestStatus.Downloading -> ACTIVE_REFRESH_MS

                    RequestStatus.ReadySoon -> if (boosting) BOOST_REFRESH_MS else READY_SOON_REFRESH_MS

                    // Downloads can be short, and a failed one is often retried by Radarr/Sonarr: check often
                    RequestStatus.Requested, is RequestStatus.DownloadFailed -> REQUESTED_REFRESH_MS

                    else -> IDLE_REFRESH_MS
                }
        }
    }
