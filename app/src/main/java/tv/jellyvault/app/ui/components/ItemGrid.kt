package tv.jellyvault.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.services.FavoriteWatchManager
import tv.jellyvault.app.services.MediaManagementService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.ServerReportService
import tv.jellyvault.app.services.deleteItem
import tv.jellyvault.app.ui.cards.GridCard
import tv.jellyvault.app.ui.data.AddPlaylistViewModel
import tv.jellyvault.app.ui.detail.CardGrid
import tv.jellyvault.app.ui.equalsNotNull
import tv.jellyvault.app.ui.launchDefault
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.rememberInt
import tv.jellyvault.app.ui.showToast
import tv.jellyvault.app.ui.tryRequestFocus
import tv.jellyvault.app.util.ApiRequestPager
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.ExceptionHandler
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.RequestHandler
import java.util.UUID

@HiltViewModel(assistedFactory = ItemGridViewModel.Factory::class)
class ItemGridViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val navigationManager: NavigationManager,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val mediaManagementService: MediaManagementService,
        val serverRepository: ServerRepository,
        val serverReportService: ServerReportService,
        @Assisted private val destination: Destination.ItemGrid<*>,
    ) : ViewModel(),
        ContextMenuProvider {
        @AssistedFactory
        interface Factory {
            fun create(destination: Destination.ItemGrid<*>): ItemGridViewModel
        }

        private val _state = MutableStateFlow(ItemGridState())
        val state: StateFlow<ItemGridState> = _state

        init {
            viewModelScope.launchIO {
                try {
                    val request = destination.request as Any
                    val pager =
                        ApiRequestPager(
                            api,
                            request,
                            destination.requestHandler as RequestHandler<Any>,
                            viewModelScope,
                            useSeriesForPrimary = true,
                        ).init()
                    if (pager.isNotEmpty()) {
                        pager.getBlocking(0)
                    }
                    _state.update {
                        it.copy(items = DataLoadingState.Success(pager))
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching items")
                    _state.update { it.copy(items = DataLoadingState.Error(ex)) }
                }
            }
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
                    refreshAfterDelete(index, item)
                }
            }
        }

        private suspend fun refreshAfterDelete(
            position: Int,
            deletedItem: BaseItem,
        ) {
            try {
                val pager =
                    ((state.value.items as? DataLoadingState.Success)?.data as? ApiRequestPager<*>)
                position.let {
                    Timber.v("Item deleted: position=%s", it)
                    val item = pager?.get(it)
                    // Exact item deleted (eg a movie) or deleted item was within the series
                    if (item?.id == deletedItem.id ||
                        equalsNotNull(item?.data?.id, deletedItem.data.seriesId)
                    ) {
                        pager?.refreshPagesAfter(position)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing after deleted item %s", deletedItem.id)
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
                (state.value.items as? DataLoadingState.Success)?.let {
                    (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
                (state.value.items as? DataLoadingState.Success)?.let {
                    (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)
    }

data class ItemGridState(
    val items: DataLoadingState<List<BaseItem?>> = DataLoadingState.Pending,
)

/**
 * Display a grid of a list of arbitrary items [tv.jellyvault.app.data.ExtrasItem]
 */
@Composable
fun ItemGrid(
    preferences: UserPreferences,
    destination: Destination.ItemGrid<*>,
    modifier: Modifier = Modifier,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    viewModel: ItemGridViewModel =
        hiltViewModel<ItemGridViewModel, ItemGridViewModel.Factory>(
            creationCallback = { it.create(destination) },
        ),
) {
    val contextMenu = rememberContextMenu(preferences, viewModel)

    val state by viewModel.state.collectAsState()
    when (val st = state.items) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<List<BaseItem?>> -> {
            var position by rememberInt(destination.initialPosition)
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }

            Column(modifier = modifier) {
                GridTitle(destination.title.getString())

                CardGrid(
                    pager = st.data,
                    onClickItem = { index: Int, item: BaseItem ->
                        position = index
                        viewModel.navigateTo(item.destination(index))
                    },
                    onLongClickItem = { index: Int, item: BaseItem ->
                        position = index
                        contextMenu.showContextMenu(index, item)
                    },
                    onClickPlay = { _, item -> viewModel.navigateTo(Destination.Playback(item)) },
                    letterPosition = { c: Char -> 0 },
                    gridFocusRequester = focusRequester,
                    showJumpButtons = false,
                    showLetterButtons = false,
                    initialPosition = destination.initialPosition,
                    spacing = destination.viewOptions.spacing.dp,
                    cardContent = @Composable { (item, index, onClick, onLongClick, widthPx, mod) ->
                        GridCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            fillWidth = widthPx,
                            modifier = mod,
                            imageAspectRatio = destination.viewOptions.aspectRatio.ratio,
                        )
                    },
                    columns = destination.viewOptions.columns,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    contextMenu.Compose()
}
