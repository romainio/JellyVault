package tv.jellyvault.app.ui.detail.episode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.data.ChosenStreams
import tv.jellyvault.app.data.ItemPlaybackRepository
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.ItemPlayback
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.FavoriteWatchManager
import tv.jellyvault.app.services.MediaManagementService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.ServerReportService
import tv.jellyvault.app.services.StreamChoiceService
import tv.jellyvault.app.services.ThemeSongPlayer
import tv.jellyvault.app.services.UserPreferencesService
import tv.jellyvault.app.services.deleteItem
import tv.jellyvault.app.ui.launchDefault
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.showToast
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.ExceptionHandler
import tv.jellyvault.app.util.JvDispatchers
import java.util.UUID

@HiltViewModel(assistedFactory = EpisodeViewModel.Factory::class)
class EpisodeViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val itemPlaybackRepository: ItemPlaybackRepository,
        val streamChoiceService: StreamChoiceService,
        val serverReportService: ServerReportService,
        private val themeSongPlayer: ThemeSongPlayer,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val mediaManagementService: MediaManagementService,
        @Assisted val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): EpisodeViewModel
        }

        private val _state = MutableStateFlow(EpisodeState())
        val state: StateFlow<EpisodeState> = _state

        val canDelete = MutableStateFlow(false)

        init {
            init()
            viewModelScope.launchDefault {
                mediaManagementService.collectCanDelete(
                    state.map { (it.episode as? DataLoadingState.Success<BaseItem?>)?.data },
                ) { canDelete ->
                    this@EpisodeViewModel.canDelete.update { canDelete }
                }
            }
        }

        private fun fetchAndSetItem() {
            viewModelScope.launchIO {
                try {
                    val item =
                        api.userLibraryApi.getItem(itemId).content.let {
                            BaseItem.from(it, api)
                        }
                    _state.update { it.copy(episode = DataLoadingState.Success(item)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting episode %s", itemId)
                    showToast(context, context.getString(R.string.jv_error_update_episode))
                }
            }
        }

        fun init(): Job =
            viewModelScope.launchIO {
                try {
                    val prefs = userPreferencesService.getCurrent()
                    val item =
                        api.userLibraryApi.getItem(itemId).content.let {
                            BaseItem(it)
                        }
                    val chosenStreams =
                        itemPlaybackRepository.getSelectedTracks(item.id, item, prefs)
                    _state.update {
                        it.copy(
                            episode = DataLoadingState.Success(item),
                            chosenStreams = chosenStreams,
                        )
                    }
                    backdropService.submit(item)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting episode %s", itemId)
                    _state.update { it.copy(episode = DataLoadingState.Error(ex)) }
                }
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            fetchAndSetItem()
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            fetchAndSetItem()
        }

        fun savePlayVersion(
            item: BaseItem,
            sourceId: UUID,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                val result = itemPlaybackRepository.savePlayVersion(item.id, sourceId)
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, plc, prefs)
                    }
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun saveTrackSelection(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            trackIndex: Int,
            type: MediaStreamType,
        ) {
            viewModelScope.launchIO {
                val prefs = userPreferencesService.getCurrent()
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                val result =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = itemPlayback,
                        trackIndex = trackIndex,
                        type = type,
                    )
                val chosen =
                    result?.let {
                        itemPlaybackRepository.getChosenItemFromPlayback(item, result, plc, prefs)
                    }
                _state.update { it.copy(chosenStreams = chosen) }
            }
        }

        fun maybePlayThemeSong(seriesId: UUID) {
            viewModelScope.launchIO {
                themeSongPlayer.playThemeFor(seriesId)
            }
        }

        fun release() {
            themeSongPlayer.stop()
        }

        fun navigateTo(destination: Destination) {
            release()
            navigationManager.navigateTo(destination)
        }

        fun clearChosenStreams(chosenStreams: ChosenStreams?) {
            viewModelScope.launchIO {
                itemPlaybackRepository.deleteChosenStreams(chosenStreams)
                state.value.episode.let { item ->
                    if (item is DataLoadingState.Success<BaseItem>) {
                        val result =
                            itemPlaybackRepository.getSelectedTracks(
                                itemId,
                                item.data,
                                userPreferencesService.getCurrent(),
                            )
                        _state.update { it.copy(chosenStreams = result) }
                    }
                }
            }
        }

        fun deleteItem(item: BaseItem) {
            deleteItem(context, mediaManagementService, item) {
                navigationManager.goBack()
            }
        }
    }

data class EpisodeState(
    val episode: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val chosenStreams: ChosenStreams? = null,
)
