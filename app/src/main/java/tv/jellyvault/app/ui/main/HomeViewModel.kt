package tv.jellyvault.app.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Dimension
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.hiddenRows
import tv.jellyvault.app.catalog.isHiddenBy
import tv.jellyvault.app.catalog.shelves.ShelfScope
import tv.jellyvault.app.catalog.shelves.ShelfSource
import tv.jellyvault.app.catalog.shelves.dueRows
import tv.jellyvault.app.catalog.shelves.expandShelfRows
import tv.jellyvault.app.catalog.shelves.onDemandRows
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.DatePlayedService
import tv.jellyvault.app.services.FavoriteWatchManager
import tv.jellyvault.app.services.HomePageResolvedSettings
import tv.jellyvault.app.services.HomeRowConfigDisplay
import tv.jellyvault.app.services.HomeSettingsService
import tv.jellyvault.app.services.ImageUrlService
import tv.jellyvault.app.services.LatestNextUpService
import tv.jellyvault.app.services.MediaManagementService
import tv.jellyvault.app.services.NavDrawerService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.ServerReportService
import tv.jellyvault.app.services.StartupGate
import tv.jellyvault.app.services.UserPreferencesService
import tv.jellyvault.app.services.deleteItem
import tv.jellyvault.app.services.tvAccess
import tv.jellyvault.app.ui.collectLatestIn
import tv.jellyvault.app.ui.combinePair
import tv.jellyvault.app.ui.data.RowColumn
import tv.jellyvault.app.ui.launchDefault
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.showToast
import tv.jellyvault.app.util.ExceptionHandler
import tv.jellyvault.app.util.HomeRowLoadingState
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.LoadingState
import java.util.UUID
import javax.inject.Inject

/** The first styles load with the home, the others when the user scrolls near them */
private const val EAGER_SHELVES = 3
private const val ROWS_AHEAD = 3

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val serverReportService: ServerReportService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val latestNextUpService: LatestNextUpService,
        private val shelfSource: ShelfSource,
        private val startupGate: StartupGate,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = _state

        private var dataLoadingJob: Job? = null

        /** Serializes init()'s check-then-launch so concurrent retry()/init() calls start a single load */
        private val initMutex = Mutex()

        init {
            datePlayedService.invalidateAll()
            serverRepository.currentUserDtoFlow
                .combinePair(homeSettingsService.currentSettings)
                // Showing or hiding "Continue watching" / "Recently added" reloads the rows
                .combinePair(
                    userPreferencesService.flow
                        .map {
                            it.appPreferences.interfacePreferences.catalogPreferences
                                .hiddenRows()
                        }.distinctUntilChanged(),
                ).collectLatestIn(viewModelScope) { (userAndSettings, _) ->
                    val (userDto, settings) = userAndSettings
                    Timber.v(
                        "Got new userDto & settings, userId=%s, settings?=%s",
                        userDto?.id,
                        settings != HomePageResolvedSettings.EMPTY,
                    )
                    _state.update { HomeState.EMPTY }
                    dataLoadingJob?.cancel()
                    if (userDto == null) {
                        Timber.d("UserDto is null")
                        return@collectLatestIn
                    }
                    if (settings == HomePageResolvedSettings.EMPTY) {
                        Timber.d("Home settings are empty")
                        return@collectLatestIn
                    }
                    if (userDto.id != settings.userId) {
                        Timber.d("User IDs don't match: %s vs %s", userDto?.id, settings.userId)
                        return@collectLatestIn
                    }
                    dataLoadingJob =
                        viewModelScope.launchIO {
                            loadHomeRows(userDto, settings)
                        }
                }
        }

        fun init() {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                initMutex.withLock { initLocked() }
            }
        }

        private suspend fun initLocked() {
            serverRepository.currentUserDto?.let { userDto ->
                if (dataLoadingJob?.isActive == false) {
                    val settings =
                        homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                    if (userDto.id == settings.userId) {
                        dataLoadingJob =
                            viewModelScope.launchIO {
                                loadHomeRows(userDto, settings)
                            }
                    } else {
                        Timber.d(
                            "Init user IDs don't match: %s vs %s",
                            userDto.id,
                            settings.userId,
                        )
                    }
                } else {
                    Timber.v("Data loading job is active")
                }
            }
        }

        /**
         * Reload the home rows, keeping rows already loaded on screen
         *
         * Waits for any in-progress load to finish first, since `init()` is a no-op while
         * `dataLoadingJob` is still active (e.g. other rows still fetching during a refresh).
         */
        fun retry() {
            viewModelScope.launchIO {
                dataLoadingJob?.join()
                init()
            }
        }

        suspend fun loadHomeRows(
            userDto: UserDto,
            settings: HomePageResolvedSettings,
        ) {
            Timber.i("Starting loadHomeRows")
            try {
                val preferences = userPreferencesService.getCurrent()
                val prefs = preferences.appPreferences.homePagePreferences
                val hidden =
                    preferences.appPreferences.interfacePreferences.catalogPreferences
                        .hiddenRows()
                val shownRows = settings.rows.filterNot { it.config.isHiddenBy(hidden) }

                val libraries =
                    navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)

                val state = state.value

                // "Styles variés" becomes the styles planned for this period (frozen, no network)
                val rows =
                    if (shownRows.any { it.config is HomeRowConfig.TmdbShelves }) {
                        val plan =
                            try {
                                shelfSource.plan(ShelfScope.HOME)
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "No shelf plan")
                                emptyList()
                            }
                        expandShelfRows(shownRows, plan)
                    } else {
                        shownRows
                    }
                expandedRows = rows

                // Refreshing if a load has already occurred and the rows haven't significantly changed
                val refresh =
                    state.loadingState == LoadingState.Success && state.settings == settings && state.homeRows.size == rows.size
                Timber.v(
                    "refresh=%s, state.loadingState=%s, %s rows",
                    refresh,
                    state.loadingState,
                    rows.size,
                )
                _state.update {
                    it.copy(
                        loadingState = if (refresh) LoadingState.Success else LoadingState.Loading,
                        refreshState = LoadingState.Loading,
                        settings = settings,
                        homeRows =
                            if (refresh) {
                                it.homeRows
                            } else {
                                rows.map { row ->
                                    HomeRowLoadingState.Pending(row.title)
                                }
                            },
                    )
                }

                val semaphore = Semaphore(4)
                // TMDB rows may wait on the library index and on TMDB timeouts: give them their own
                // permits so they never hold back the Jellyfin rows (spec 5.8)
                val tmdbSemaphore = Semaphore(4)

                fun fetch(row: HomeRowConfigDisplay): Deferred<HomeRowLoadingState> {
                    val rowSemaphore =
                        when (row.config) {
                            is HomeRowConfig.TmdbTrending,
                            is HomeRowConfig.TmdbGenre,
                            is HomeRowConfig.TmdbForYou,
                            is HomeRowConfig.TmdbBecause,
                            is HomeRowConfig.TmdbShelves,
                            is HomeRowConfig.TmdbShelf,
                            is HomeRowConfig.TmdbWatchlist,
                            -> tmdbSemaphore

                            else -> semaphore
                        }
                    return viewModelScope.async(JvDispatchers.IO) {
                        rowSemaphore.withPermit {
                            Timber.v("Fetching row: %s", row)
                            try {
                                homeSettingsService.fetchDataForRow(
                                    row = row.config,
                                    scope = viewModelScope,
                                    prefs = prefs,
                                    userDto = userDto,
                                    libraries = libraries,
                                    limit = prefs.maxItemsPerRow,
                                    isRefresh = refresh,
                                )
                            } catch (ex: InvalidStatusException) {
                                if (ex.status == 404) {
                                    Timber.w(ex, "404 on row %s", row)
                                    HomeRowLoadingState.Success(
                                        row.title,
                                        emptyList(),
                                    )
                                } else {
                                    Timber.e(
                                        ex,
                                        "Error %s on row %s",
                                        ex.status,
                                        row,
                                    )
                                    HomeRowLoadingState.Error(
                                        row.title,
                                        exception = ex,
                                    )
                                }
                            } catch (ex: Exception) {
                                Timber.e(ex, "Error on row %s", row)
                                HomeRowLoadingState.Error(
                                    row.title,
                                    exception = ex,
                                )
                            }
                        }
                    }
                }

                // Styles further down load when the user scrolls near them (see onVisibleRow)
                val waiting =
                    onDemandRows(
                        rows,
                        EAGER_SHELVES,
                    ).filter { refresh.not() || state.homeRows.getOrNull(it) !is HomeRowLoadingState.Success }.toSet()
                val generation = ++loadGeneration
                onDemand = OnDemand(generation, waiting.toMutableSet(), ::fetch)
                val deferred =
                    rows
                        .withIndex()
                        .filter { (index, _) ->
                            index !in waiting &&
                                !(refresh && index in onDemandRows(rows, EAGER_SHELVES))
                        }.map { (index, row) -> index to fetch(row) }

                // Replace rows as they complete, on the first load too, so a slow row never
                // holds back the others (spec 5.8)
                val remaining = deferred.toMutableList()
                while (remaining.isNotEmpty()) {
                    val (rowIndex, rowData) =
                        select {
                            // "Return" the first remaining that is completed
                            remaining
                                .forEach { (rowIndex, deferred) ->
                                    deferred.onAwait { rowIndex to it }
                                }
                        }
                    Timber.v("Got row data index=%s", rowIndex)
                    remaining.removeIf { it.first == rowIndex }
                    _state.update { state ->
                        val newRows =
                            state.homeRows.toMutableList().apply {
                                set(rowIndex, rowData)
                            }
                        state.copy(
                            loadingState = LoadingState.Success,
                            homeRows = newRows,
                        )
                    }
                    maybeOpenStartupGate(waiting)
                }
                maybeOpenStartupGate(waiting)
                _state.update {
                    it.copy(
                        loadingState = LoadingState.Success,
                        refreshState = LoadingState.Success,
                    )
                }
                Timber.d("Home page load complete")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Exception during home page loading")
                // The error page must not hide behind the intro
                startupGate.open()
                if (state.value.loadingState == LoadingState.Success) {
                    showToast(context, context.getString(R.string.jv_error_refresh_home))
                    _state.update { it.copy(refreshState = LoadingState.Error(ex)) }
                } else {
                    _state.update {
                        it.copy(loadingState = LoadingState.Error(ex))
                    }
                }
            }
        }

        private var startupPrefetch: Job? = null

        /**
         * Once the rows on the first screen are loaded, download their posters and the first backdrop, then let the
         * launch intro go: the home appears filled. Bounded, a slow image server never keeps the intro up
         */
        private fun maybeOpenStartupGate(waiting: Set<Int>) {
            if (startupGate.open.value || startupPrefetch != null) return
            val rows = state.value.homeRows
            val firstScreen = rows.indices.filter { it !in waiting }.take(STARTUP_ROWS)
            if (firstScreen.any { !rows[it].completed }) return
            startupPrefetch =
                viewModelScope.launchIO {
                    val density = context.resources.displayMetrics.density
                    val loaded = firstScreen.mapNotNull { rows[it] as? HomeRowLoadingState.Success }
                    val urls =
                        buildList {
                            loaded.firstOrNull()?.items?.firstOrNull { it != null }?.let { first ->
                                add(
                                    (
                                        first.backdropUrlOverride
                                            ?: imageUrlService.getItemImageUrl(
                                                first,
                                                ImageType.BACKDROP,
                                                fillWidth = BackdropService.BACKDROP_FILL_WIDTH,
                                            )
                                    ) to
                                        null,
                                )
                            }
                            loaded.forEach { row ->
                                val options = row.viewOptions
                                val heightPx = (options.heightDp * density).toInt()
                                row.items.take(STARTUP_CARDS).filterNotNull().forEach { item ->
                                    val type =
                                        if (item.type ==
                                            BaseItemKind.EPISODE
                                        ) {
                                            options.episodeImageType.imageType
                                        } else {
                                            options.imageType.imageType
                                        }
                                    add(
                                        (
                                            item.imageUrlOverride
                                                ?: imageUrlService.getItemImageUrl(
                                                    item,
                                                    type,
                                                    fillWidth = null,
                                                    fillHeight = heightPx,
                                                    useSeriesForPrimary = options.useSeries,
                                                )
                                        ) to heightPx,
                                    )
                                }
                            }
                        }.filter { it.first != null }
                    val loader = context.imageLoader
                    withTimeoutOrNull(STARTUP_PREFETCH_MILLIS) {
                        urls
                            .map { (url, heightPx) ->
                                async {
                                    val request = ImageRequest.Builder(context).data(url)
                                    heightPx?.let { request.size(Dimension.Undefined, Dimension(it)) }
                                    loader.execute(request.build())
                                }
                            }.awaitAll()
                    }
                    Timber.i("Startup: %s images ready", urls.size)
                    startupGate.open()
                }
        }

        private class OnDemand(
            val generation: Int,
            val waiting: MutableSet<Int>,
            val fetch: (HomeRowConfigDisplay) -> Deferred<HomeRowLoadingState>,
        )

        private var loadGeneration = 0

        @Volatile
        private var expandedRows: List<HomeRowConfigDisplay>? = null

        @Volatile
        private var onDemand: OnDemand? = null

        /**
         * The home shows rows up to [index]: load the styles coming next
         */
        fun onVisibleRow(index: Int) {
            val pending = onDemand ?: return
            val rows = expandedRows ?: return
            val due = synchronized(pending.waiting) { dueRows(pending.waiting, index, ROWS_AHEAD).also { pending.waiting.removeAll(it) } }
            due.forEach { rowIndex ->
                val row = rows.getOrNull(rowIndex) ?: return@forEach
                viewModelScope.launch {
                    val data = pending.fetch(row).await()
                    _state.update { state ->
                        // A newer load replaced the rows meanwhile: drop this result
                        if (pending.generation != loadGeneration || rowIndex >= state.homeRows.size) {
                            state
                        } else {
                            state.copy(homeRows = state.homeRows.toMutableList().apply { set(rowIndex, data) })
                        }
                    }
                }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(JvDispatchers.Main) {
                init()
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(JvDispatchers.Main) {
                init()
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.homeRows.getOrNull(position.row)
                    if (row is HomeRowLoadingState.Success) {
                        _state.update {
                            val newRow =
                                row.items.toMutableList().apply {
                                    removeAt(position.column)
                                }
                            it.copy(
                                homeRows =
                                    it.homeRows.toMutableList().apply {
                                        set(position.row, row.copy(items = newRow))
                                    },
                            )
                        }
                    }
                }
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun removeFromNextUp(item: BaseItem) {
            if (item.type == BaseItemKind.EPISODE) {
                viewModelScope.launchDefault {
                    serverRepository.currentUser?.id?.let { userId ->
                        latestNextUpService.removeFromNextUp(userId, item)
                        init()
                    }
                }
            } else {
                Timber.w("Item is not an episode %s", item.id)
            }
        }
    }

data class HomeState(
    val loadingState: LoadingState,
    val refreshState: LoadingState,
    val homeRows: List<HomeRowLoadingState>,
    val settings: HomePageResolvedSettings,
) {
    companion object {
        val EMPTY =
            HomeState(
                LoadingState.Pending,
                LoadingState.Pending,
                emptyList(),
                HomePageResolvedSettings.EMPTY,
            )
    }
}

/**
 * Whether a row is a "is watching" type
 */
private fun isWatchingRow(row: HomeRowConfig) =
    row is HomeRowConfig.ContinueWatching ||
        row is HomeRowConfig.NextUp ||
        row is HomeRowConfig.ContinueWatchingCombined

/** Rows on the first screen of the home, waited for at launch */
private const val STARTUP_ROWS = 3

/** Posters per row visible without scrolling */
private const val STARTUP_CARDS = 8

private const val STARTUP_PREFETCH_MILLIS = 5_000L
