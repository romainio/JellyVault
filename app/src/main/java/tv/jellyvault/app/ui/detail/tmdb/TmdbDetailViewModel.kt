package tv.jellyvault.app.ui.detail.tmdb

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.arr.ArrException
import tv.jellyvault.app.catalog.arr.ArrService
import tv.jellyvault.app.catalog.arr.RefreshOutcome
import tv.jellyvault.app.catalog.arr.RequestService
import tv.jellyvault.app.catalog.arr.RequestStatus
import tv.jellyvault.app.catalog.arr.SeasonChoice
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.toBaseItem
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.FavoriteWatchManager
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.preferences.arr.messageRes
import tv.jellyvault.app.util.DataLoadingState
import java.util.UUID
import javax.inject.Inject

data class SeasonDialogState(
    val choices: List<SeasonChoice>,
)

sealed interface TmdbDetailEvent {
    /** Every season is already requested or available: nothing to choose */
    data object AllSeasonsRequested : TmdbDetailEvent

    data class Requested(
        val service: ArrService,
    ) : TmdbDetailEvent

    /** « Actualiser » finished; [scanRequested] is false when Jellyfin did not accept the scan */
    data class Refreshed(
        val scanRequested: Boolean,
    ) : TmdbDetailEvent

    data class Failed(
        val service: ArrService,
        @param:StringRes val message: Int,
        val code: Int?,
    ) : TmdbDetailEvent
}

data class TmdbDetailState(
    val details: DataLoadingState<TmdbDetails> = DataLoadingState.Pending,
    val status: RequestStatus? = null,
    val similar: List<BaseItem> = emptyList(),
    /** TMDB "similar": same genres and keywords */
    val sameGenre: List<BaseItem> = emptyList(),
    val seasonDialog: SeasonDialogState? = null,
    val sending: Boolean = false,
    val event: TmdbDetailEvent? = null,
    /** The movie or next episode to play, once the title is in the library */
    val playback: OwnedPlayback? = null,
    val refreshing: Boolean = false,
)

@HiltViewModel
class TmdbDetailViewModel
    @Inject
    constructor(
        private val tmdb: TmdbClient,
        private val requestService: RequestService,
        private val catalogRepository: CatalogRepository,
        private val backdropService: BackdropService,
        private val navigationManager: NavigationManager,
        private val playbackLoader: OwnedPlaybackLoader,
        private val favoriteWatchManager: FavoriteWatchManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TmdbDetailState())
        val state: StateFlow<TmdbDetailState> = _state

        private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private var item: TmdbItem? = null
        private var statusJob: Job? = null
        private var loadedDetails: TmdbDetails? = null
        private var visible = true

        @Volatile
        private var playbackFor: UUID? = null
        private var playbackJob: Job? = null

        @Volatile
        private var awaitingStatus = false

        fun init(item: TmdbItem) {
            if (this.item == item) return
            this.item = item
            load(item)
        }

        fun retry() {
            item?.let(::load)
        }

        /**
         * The page is on screen: (re)start watching the request status, which also refreshes it
         */
        fun onVisible() {
            visible = true
            // The resume position may have changed while another page was on top
            playbackFor = null
            loadedDetails?.let(::startStatus)
            // Back from another title page: that page left its own backdrop behind
            val shown = item ?: return
            viewModelScope.launchIO {
                backdropService.submit(backdropKey(shown), (loadedDetails?.item ?: shown).detailBackdropUrl)
            }
        }

        /**
         * The page is hidden (another page on top, app in background): stop polling Radarr/Sonarr
         */
        fun onHidden() {
            visible = false
            statusJob?.cancel()
            statusJob = null
        }

        fun retryStatus() {
            refreshSignal.tryEmit(Unit)
        }

        fun consumeEvent() {
            _state.update { it.copy(event = null) }
        }

        fun dismissSeasonDialog() {
            _state.update { it.copy(seasonDialog = null) }
        }

        fun onPrimaryClick() {
            val details = (_state.value.details as? DataLoadingState.Success)?.data ?: return
            val service = serviceFor(details)
            when (val status = _state.value.status) {
                RequestStatus.NotConfigured -> {
                    navigationManager.navigateTo(Destination.ArrSettings(service))
                }

                RequestStatus.NotRequested,
                is RequestStatus.Partial,
                -> {
                    if (details.item.mediaType == TmdbMediaType.MOVIE) {
                        sendRequest(service) { requestService.requestMovie(details) }
                    } else {
                        openSeasonDialog(details)
                    }
                }

                is RequestStatus.Available -> {
                    play()
                }

                is RequestStatus.Error -> {
                    retryStatus()
                }

                else -> {}
            }
        }

        /**
         * Play the movie or the next episode; without loaded details, open the Jellyfin page as before
         */
        fun play() {
            _state.value.playback?.let { navigationManager.navigateTo(Destination.Playback(it.item)) } ?: openJellyfinPage()
        }

        /**
         * The Jellyfin page of the title (chapters, versions…)
         */
        fun openJellyfinPage() {
            ownedItem(_state.value.status)?.let { navigationManager.navigateTo(it.destination()) }
        }

        /**
         * « Actualiser » on "Disponible bientôt": scan, read the library again, then check the status
         */
        fun refreshNow() {
            if (_state.value.refreshing) return
            _state.update { it.copy(refreshing = true) }
            viewModelScope.launchIO {
                val outcome =
                    try {
                        requestService.refreshNow()
                    } catch (e: CancellationException) {
                        _state.update { it.copy(refreshing = false) }
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Refresh failed")
                        RefreshOutcome(scanRequested = false)
                    }
                refreshSignal.tryEmit(Unit)
                _state.update { it.copy(refreshing = false, event = TmdbDetailEvent.Refreshed(outcome.scanRequested)) }
            }
        }

        /**
         * The « vu » eye of an owned title, on the movie or the whole series like the Jellyfin page
         */
        fun toggleWatched() {
            val title = _state.value.playback?.title ?: return
            viewModelScope.launchIO {
                try {
                    val userData = favoriteWatchManager.setWatched(title.id, !title.played)
                    updateTitle(title.id) { it.copy(userData = userData) }
                    // The next episode or the resume position change with it
                    ownedItem(_state.value.status)?.let {
                        playbackFor = null
                        loadPlayback(it)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error updating watch status for %s", title.id)
                }
            }
        }

        fun toggleFavorite() {
            val title = _state.value.playback?.title ?: return
            viewModelScope.launchIO {
                try {
                    val userData = favoriteWatchManager.setFavorite(title.id, !title.favorite)
                    updateTitle(title.id) { it.copy(userData = userData) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error updating favorite for %s", title.id)
                }
            }
        }

        private fun updateTitle(
            id: UUID,
            change: (BaseItemDto) -> BaseItemDto,
        ) {
            _state.update { state ->
                val playback = state.playback?.takeIf { it.title.id == id } ?: return@update state
                val title = playback.title.copy(data = change(playback.title.data))
                // For a movie the played item is the title itself
                val item = if (playback.item.id == id) title else playback.item
                state.copy(playback = playback.copy(item = item, title = title))
            }
        }

        private fun ownedItem(status: RequestStatus?): BaseItem? =
            when (status) {
                is RequestStatus.Available -> status.item
                is RequestStatus.Partial -> status.item
                else -> null
            }

        private fun loadPlayback(owned: BaseItem) {
            if (playbackFor == owned.id) return
            playbackFor = owned.id
            playbackJob?.cancel()
            playbackJob =
                viewModelScope.launchIO {
                    val playback = playbackLoader.load(owned)
                    // The title may have left the library or the page restarted while loading
                    if (playbackFor == owned.id) _state.update { it.copy(playback = playback) }
                }
        }

        private fun clearPlayback() {
            playbackFor = null
            playbackJob?.cancel()
            if (_state.value.playback != null) _state.update { it.copy(playback = null) }
        }

        fun navigate(item: BaseItem) {
            navigationManager.navigateTo(item.destination())
        }

        /**
         * Open a saga, studio or network page from the « … » menu
         */
        fun browse(target: BrowseTarget) {
            navigationManager.navigateTo(Destination.TmdbBrowse(target))
        }

        fun requestSeasons(seasons: Set<Int>) {
            val details = (_state.value.details as? DataLoadingState.Success)?.data ?: return
            _state.update { it.copy(seasonDialog = null) }
            sendRequest(ArrService.SONARR) { requestService.requestSeries(details, seasons) }
        }

        private fun load(item: TmdbItem) {
            statusJob?.cancel()
            viewModelScope.launchIO {
                _state.update { it.copy(details = DataLoadingState.Loading) }
                // The header is drawn from the item right away: its backdrop too
                backdropService.submit(backdropKey(item), item.detailBackdropUrl)
                val details =
                    try {
                        tmdb.details(item.mediaType, item.tmdbId)
                    } catch (e: TmdbException) {
                        Timber.w(e, "TMDB details failed")
                        _state.update { it.copy(details = DataLoadingState.Error(e)) }
                        return@launchIO
                    }
                _state.update { it.copy(details = DataLoadingState.Success(details)) }
                // No poster fallback: a portrait poster cropped to the screen is blurry, the plain page background looks better
                backdropService.submit(backdropKey(item), details.item.detailBackdropUrl)
                loadedDetails = details
                startStatus(details)
                val similar = catalogRepository.cards(details.recommendations).map { it.toBaseItem() }
                val sameGenre = catalogRepository.cards(details.similarTitles).map { it.toBaseItem() }
                _state.update { it.copy(similar = similar, sameGenre = sameGenre) }
            }
        }

        private fun backdropKey(item: TmdbItem) = "tmdb_${item.mediaType.path}_${item.tmdbId}"

        private fun startStatus(details: TmdbDetails) {
            if (!visible || statusJob?.isActive == true) return
            statusJob =
                viewModelScope.launchIO {
                    requestService.status(details, refreshSignal).collect { status ->
                        val refreshedAfterRequest = awaitingStatus
                        awaitingStatus = false
                        _state.update { it.copy(status = status, sending = if (refreshedAfterRequest) false else it.sending) }
                        ownedItem(status)?.let(::loadPlayback) ?: clearPlayback()
                    }
                }
        }

        private fun openSeasonDialog(details: TmdbDetails) {
            viewModelScope.launchIO {
                val choices =
                    try {
                        requestService.seasonChoices(details)
                    } catch (e: ArrException) {
                        _state.update {
                            it.copy(event = TmdbDetailEvent.Failed(ArrService.SONARR, e.messageRes(), (e as? ArrException.Http)?.code))
                        }
                        return@launchIO
                    }
                if (choices.none { !it.locked }) {
                    _state.update { it.copy(event = TmdbDetailEvent.AllSeasonsRequested) }
                } else {
                    _state.update { it.copy(seasonDialog = SeasonDialogState(choices)) }
                }
            }
        }

        private fun sendRequest(
            service: ArrService,
            block: suspend () -> Unit,
        ) {
            if (_state.value.sending) return
            _state.update { it.copy(sending = true) }
            viewModelScope.launchIO {
                try {
                    block()
                    // Keep the button disabled until the refreshed status arrives, to avoid a second request
                    awaitingStatus = true
                    _state.update { it.copy(event = TmdbDetailEvent.Requested(service)) }
                    refreshSignal.tryEmit(Unit)
                } catch (e: CancellationException) {
                    _state.update { it.copy(sending = false) }
                    throw e
                } catch (e: ArrException) {
                    Timber.w(e, "Request failed")
                    _state.update {
                        it.copy(
                            sending = false,
                            event = TmdbDetailEvent.Failed(service, e.messageRes(), (e as? ArrException.Http)?.code),
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Unexpected request failure")
                    _state.update { it.copy(sending = false, event = TmdbDetailEvent.Failed(service, R.string.arr_error_invalid, null)) }
                }
            }
        }

        private fun serviceFor(details: TmdbDetails) =
            if (details.item.mediaType == TmdbMediaType.MOVIE) ArrService.RADARR else ArrService.SONARR
    }
