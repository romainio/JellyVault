package tv.jellyvault.app.ui.search

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.toBaseItem
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.preferences.updateSearchPreferences
import tv.jellyvault.app.services.FavoriteWatchManager
import tv.jellyvault.app.services.KeyValueService
import tv.jellyvault.app.services.LiveTvService
import tv.jellyvault.app.services.MediaManagementService
import tv.jellyvault.app.services.NavDrawerService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.ServerReportService
import tv.jellyvault.app.services.UserPreferencesService
import tv.jellyvault.app.services.deleteItem
import tv.jellyvault.app.services.tvAccess
import tv.jellyvault.app.ui.ProgramItemFields
import tv.jellyvault.app.ui.SlimItemFields
import tv.jellyvault.app.ui.components.ContextMenuProvider
import tv.jellyvault.app.ui.components.VoiceInputManager
import tv.jellyvault.app.ui.components.baseItemKinds
import tv.jellyvault.app.ui.data.RowColumn
import tv.jellyvault.app.ui.detail.livetv.ProgramDialogState
import tv.jellyvault.app.ui.isNotNullOrBlank
import tv.jellyvault.app.ui.launchDefault
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.showToast
import tv.jellyvault.app.ui.toBaseItems
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.ExceptionHandler
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.SearchRelevance
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val appPreferences: DataStore<AppPreferences>,
        val voiceInputManager: VoiceInputManager,
        val userPreferencesService: UserPreferencesService,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val mediaManagementService: MediaManagementService,
        private val serverReportService: ServerReportService,
        private val liveTvService: LiveTvService,
        private val keyValueService: KeyValueService,
        private val navDrawerService: NavDrawerService,
        private val catalogRepository: CatalogRepository,
    ) : ViewModel(),
        ContextMenuProvider {
        private val _state = MutableStateFlow(SearchState())
        val state: StateFlow<SearchState> = _state
        val position = MutableStateFlow(RowColumn(0, 0))

        private val tmdbSearch = TmdbSearch(catalogRepository, viewModelScope)

        /**
         * Movies and series from TMDB matching the query, including titles not on the server
         */
        val tmdbResults: StateFlow<SearchResult> = tmdbSearch.results

        /**
         * The single search grid: movies and series from the server and from TMDB
         */
        val unifiedResults: StateFlow<SearchResult> =
            combine(state.map { it.combinedResults }.distinctUntilChanged(), tmdbSearch.results, ::mergeSearchResults)
                .stateIn(viewModelScope, SharingStarted.Eagerly, SearchResult.NoQuery)

        private val _mosaic = MutableStateFlow<List<BaseItem>>(emptyList())

        /** What the page shows before anything is typed: trending movies and series */
        val mosaic: StateFlow<List<BaseItem>> = _mosaic

        private var currentQuery: String? = null
        private var combinedMode = false

        private val _programDialogState = MutableStateFlow(ProgramDialogState())
        val programDialogState: StateFlow<ProgramDialogState> = _programDialogState

        private var userLibraryTypes: Set<BaseItemKind> = emptySet()

        init {
            init()
            loadMosaic()
        }

        private fun loadMosaic() {
            if (!catalogRepository.isConfigured) return
            viewModelScope.launchIO {
                try {
                    _mosaic.value = catalogRepository.searchMosaic().map { it.toBaseItem() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "No search mosaic")
                }
            }
        }

        private fun init() {
            viewModelScope.launchDefault {
                userLibraryTypes =
                    navDrawerService.state.value.allLibraries
                        .flatMap { it.collectionType.baseItemKinds }
                        .toSet()

                val excludedSearchableTypes =
                    serverRepository.currentUser?.id?.let { userId ->
                        try {
                            keyValueService
                                .get<List<BaseItemKind>>(
                                    userId,
                                    EXCLUDED_SEARCHABLE_TYPES_KEY,
                                    emptyList(),
                                ).firstOrNull()
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error occurred fetching excluded search types")
                            emptyList()
                        }
                    } ?: emptyList()
                val searchableTypes = determineSearchableTypes(excludedSearchableTypes)
                val possibleSearchableTypes = determineSearchableTypes(emptyList())
                _state.update {
                    it.copy(
                        includedSearchableTypes = searchableTypes,
                        possibleSearchableTypes = possibleSearchableTypes,
                        excludedSearchableTypes = excludedSearchableTypes,
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                searchableTypes.forEach { put(it, SearchResult.NoQuery) }
                            },
                    )
                }
            }
        }

        private fun determineSearchableTypes(excludedSearchableTypes: List<BaseItemKind>): List<BaseItemKind> {
            val tvAccess = serverRepository.currentUserDto?.tvAccess == true
            val searchableTypes =
                allSearchableTypes.filter {
                    val excluded = excludedSearchableTypes.contains(it)
                    // Only include if there's a relevant, accessible library
                    val hasLibrary = userLibraryTypes.contains(it)
                    when (it) {
                        // No library type for person
                        BaseItemKind.PERSON -> !excluded

                        // Remove live tv search if user doesn't have access
                        BaseItemKind.TV_CHANNEL,
                        BaseItemKind.LIVE_TV_PROGRAM,
                        BaseItemKind.TV_PROGRAM,
                        BaseItemKind.PROGRAM,
                        -> tvAccess && !excluded

                        else -> hasLibrary && !excluded
                    }
                }
            return searchableTypes
        }

        fun search(
            query: String?,
            combined: Boolean = false,
            force: Boolean = false,
        ) {
            if (isRepeatedSearch(currentQuery, query, force)) {
                return
            }
            currentQuery = query
            // JellyVault always shows one combined grid of movies and series
            combinedMode = true
            tmdbSearch.search(query)
            if (query.isNotNullOrBlank()) {
                _state.update {
                    it.copy(
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                it.includedSearchableTypes.forEach {
                                    put(it, SearchResult.Searching)
                                }
                            },
                        combinedResults = SearchResult.Searching,
                    )
                }
                searchCombined(query)
            } else {
                _state.update {
                    it.copy(
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                it.includedSearchableTypes.forEach {
                                    put(it, SearchResult.NoQuery)
                                }
                            },
                        combinedResults = SearchResult.NoQuery,
                    )
                }
            }
        }

        private fun searchType(
            query: String,
            type: BaseItemKind,
        ) {
            viewModelScope.launchIO {
                try {
                    val items: List<BaseItem> =
                        when (type) {
                            BaseItemKind.LIVE_TV_PROGRAM -> {
                                val request =
                                    GetItemsRequest(
                                        searchTerm = query,
                                        recursive = true,
                                        includeItemTypes = listOf(type),
                                        fields = ProgramItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    )
                                api.itemsApi.getItems(request).toBaseItems(api, false)
                            }

                            BaseItemKind.MUSIC_ARTIST -> {
                                api.artistsApi
                                    .getArtists(
                                        searchTerm = query,
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    ).toBaseItems(api, false)
                            }

                            BaseItemKind.PERSON -> {
                                api.personsApi
                                    .getPersons(
                                        searchTerm = query,
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                    ).toBaseItems(api, false)
                            }

                            else -> {
                                val request =
                                    GetItemsRequest(
                                        searchTerm = query,
                                        recursive = true,
                                        includeItemTypes = listOf(type),
                                        fields = SlimItemFields,
                                        limit = SEARCH_LIMIT,
                                        enableTotalRecordCount = false,
                                    )
                                api.itemsApi.getItems(request).toBaseItems(api, false)
                            }
                        }
                    val sorted =
                        items.sortedWith(
                            compareBy<BaseItem> { SearchRelevance.score(it, query) },
                        )
                    Timber.v("Search finished for %s, %s results", type, sorted.size)
                    _state.value.results[type] = SearchResult.Success(sorted)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception searching for $type")
                    _state.value.results[type] = SearchResult.Error(ex)
                }
            }
        }

        private fun searchCombined(query: String) {
            viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
                try {
                    Timber.v("Starting searchCombined")
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            fields = SlimItemFields,
                            limit = SEARCH_LIMIT,
                        )

                    val result = api.itemsApi.getItems(request).content
                    val items =
                        result.items.map {
                            BaseItem(it, false)
                        }
                    val sorted =
                        items.sortedWith(
                            compareBy<BaseItem> { SearchRelevance.score(it, query) },
                        )
                    Timber.v("searchCombined complete %s results", sorted.size)
                    _state.update { it.copy(combinedResults = SearchResult.Success(sorted)) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception in combined search")
                    _state.update { it.copy(combinedResults = SearchResult.Error(ex)) }
                }
            }
        }

        fun setCombinedResults(enabled: Boolean) {
            viewModelScope.launchIO {
                appPreferences.updateData {
                    it.updateSearchPreferences {
                        combinedSearchResults = enabled
                    }
                }
            }
        }

        fun setVoiceSearchButtonVisible(visible: Boolean) {
            viewModelScope.launchIO {
                appPreferences.updateData {
                    it.updateSearchPreferences {
                        showVoiceSearchButton = visible
                    }
                }
            }
        }

        init {
            addCloseable(voiceInputManager)
        }

        override fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        override fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        override fun deleteItem(
            index: Int,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    refreshItem(item.id)
                }
            }
        }

        private suspend fun refreshItem(itemId: UUID) {
            try {
                val position = position.value
                val searchResult =
                    if (combinedMode) {
                        state.value.combinedResults
                    } else {
                        state.value.includedSearchableTypes.getOrNull(position.row)?.let {
                            state.value.results[it]
                        }
                    } ?: return
                val items = (searchResult as? SearchResult.Success)?.items ?: return

                Timber.v("Item refresh: position=%s", position)
                val item = items.getOrNull(position.column)
                // Exact item deleted (eg a movie) or deleted item was within the series
                if (item != null && item.id == itemId) {
                    val newItem =
                        api.userLibraryApi
                            .getItem(item.id)
                            .content
                            .let { BaseItem(it) }
                    val newList =
                        SearchResult.Success(
                            items.toMutableList().apply {
                                set(position.column, newItem)
                            },
                        )
                    if (combinedMode) {
                        _state.update {
                            it.copy(
                                combinedResults = newList,
                            )
                        }
                    } else {
                        state.value.includedSearchableTypes.getOrNull(position.row)?.let { type ->
                            state.value.results[type] = newList
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing item %s", itemId)
                showToast(context, context.getString(R.string.jv_error_refresh))
            }
        }

        override fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
                favoriteWatchManager.setWatched(itemId, played)
                refreshItem(itemId)
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
                refreshItem(itemId)
            }
        }

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)

        fun fetchProgramForDialog(programId: UUID) {
            _programDialogState.update { it.copy(loading = DataLoadingState.Loading) }
            viewModelScope.launchDefault {
                try {
                    val result = liveTvService.fetchProgramForDialog(programId)
                    _programDialogState.update { it.copy(loading = DataLoadingState.Success(result)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching program $programId")
                    _programDialogState.update { it.copy(loading = DataLoadingState.Error(ex)) }
                }
            }
        }

        fun cancelRecording(
            series: Boolean,
            timerId: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                try {
                    val result = liveTvService.cancelRecording(series, timerId)
                    // TODO update program card?
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error canceling timer %s, series=%s", timerId, series)
                    showToast(context, context.getString(R.string.jv_error_recording))
                }
            }
        }

        fun record(
            programId: UUID,
            series: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    liveTvService.record(programId, series)
                    // TODO update program card?
                    refreshItem(programId)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error recording %s, series=%s", programId, series)
                    showToast(context, context.getString(R.string.jv_error_recording))
                }
            }
        }

        fun onClickExcludeSearchableType(type: BaseItemKind) {
            viewModelScope.launchIO {
                try {
                    val state = state.value
                    val updateSearch: Boolean
                    val newExcludes =
                        state.excludedSearchableTypes.toMutableList().apply {
                            if (type in this) {
                                updateSearch = true
                                remove(type)
                            } else {
                                // If combined, need to update, otherwise just hide the newly excluded results
                                updateSearch = combinedMode
                                add(type)
                            }
                        }
                    Timber.v("newExcludes=%s", newExcludes)
                    serverRepository.currentUser?.id?.let { userId ->
                        keyValueService.save(userId, EXCLUDED_SEARCHABLE_TYPES_KEY, newExcludes)
                    }
                    val searchableTypes = determineSearchableTypes(newExcludes)
                    _state.update {
                        it.copy(
                            includedSearchableTypes = searchableTypes,
                            excludedSearchableTypes = newExcludes,
                        )
                    }
                    if (updateSearch && currentQuery.isNotNullOrBlank()) {
                        Timber.d("Need to update search")
                        search(currentQuery, combinedMode, true)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception toggling %s", type)
                    showToast(context, context.getString(R.string.jv_error_generic))
                }
            }
        }

        companion object {
            private const val EXCLUDED_SEARCHABLE_TYPES_KEY = "excludedSearchableTypes"
        }
    }

sealed interface SearchResult {
    data object NoQuery : SearchResult

    data object Searching : SearchResult

    data class Error(
        val ex: Exception,
    ) : SearchResult

    data class Success(
        val items: List<BaseItem?>,
    ) : SearchResult
}

data class SearchState(
    val results: SnapshotStateMap<BaseItemKind, SearchResult> = SnapshotStateMap(),
    val combinedResults: SearchResult = SearchResult.NoQuery,
    val possibleSearchableTypes: List<BaseItemKind> = emptyList(),
    val includedSearchableTypes: List<BaseItemKind> = emptyList(),
    val excludedSearchableTypes: List<BaseItemKind> = emptyList(),
)

private val allSearchableTypes =
    listOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.EPISODE,
        BaseItemKind.BOX_SET,
        BaseItemKind.PERSON,
        BaseItemKind.TV_CHANNEL,
        BaseItemKind.LIVE_TV_PROGRAM,
        BaseItemKind.MUSIC_ALBUM,
        BaseItemKind.MUSIC_ARTIST,
        BaseItemKind.AUDIO,
        BaseItemKind.MUSIC_VIDEO,
        BaseItemKind.PLAYLIST,
        BaseItemKind.VIDEO,
        BaseItemKind.PHOTO,
        BaseItemKind.PHOTO_ALBUM,
    )

private const val SEARCH_LIMIT = 50
