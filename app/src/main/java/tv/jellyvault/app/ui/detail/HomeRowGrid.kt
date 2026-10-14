package tv.jellyvault.app.ui.detail

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.FavoriteWatchManager
import tv.jellyvault.app.services.HomeSettingsService
import tv.jellyvault.app.services.MediaManagementService
import tv.jellyvault.app.services.MusicService
import tv.jellyvault.app.services.NavDrawerService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.ServerReportService
import tv.jellyvault.app.services.StreamChoiceService
import tv.jellyvault.app.services.ThemeSongPlayer
import tv.jellyvault.app.services.UserPreferencesService
import tv.jellyvault.app.services.deleteItem
import tv.jellyvault.app.services.tvAccess
import tv.jellyvault.app.ui.cards.GridCard
import tv.jellyvault.app.ui.components.ContextMenuProvider
import tv.jellyvault.app.ui.components.ErrorMessage
import tv.jellyvault.app.ui.components.GenreCardGrid
import tv.jellyvault.app.ui.components.GridTitle
import tv.jellyvault.app.ui.components.LoadingPage
import tv.jellyvault.app.ui.components.StudioCardGrid
import tv.jellyvault.app.ui.components.rememberContextMenu
import tv.jellyvault.app.ui.launchDefault
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.playback.scale
import tv.jellyvault.app.ui.tryRequestFocus
import tv.jellyvault.app.ui.util.StringProvider
import tv.jellyvault.app.ui.util.StringStringProvider
import tv.jellyvault.app.util.ApiRequestPager
import tv.jellyvault.app.util.ExceptionHandler
import tv.jellyvault.app.util.HomeRowLoadingState
import tv.jellyvault.app.util.JvDispatchers
import java.util.UUID

@HiltViewModel(assistedFactory = HomeRowGridViewModel.Factory::class)
class HomeRowGridViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val navigationManager: NavigationManager,
        private val themeSongPlayer: ThemeSongPlayer,
        private val mediaManagementService: MediaManagementService,
        private val musicService: MusicService,
        val streamChoiceService: StreamChoiceService,
        val serverReportService: ServerReportService,
        @Assisted private val title: StringProvider,
        @Assisted private val rowConfig: HomeRowConfig,
    ) : ViewModel(),
        ContextMenuProvider {
        @AssistedFactory
        interface Factory {
            fun create(
                title: StringProvider,
                rowConfig: HomeRowConfig,
            ): HomeRowGridViewModel
        }

        private val _state = MutableStateFlow(HomeRowGridState())
        val state: StateFlow<HomeRowGridState> = _state

        init {
            viewModelScope.launchDefault {
                when (rowConfig) {
                    is HomeRowConfig.Genres,
                    is HomeRowConfig.Studios,
                    -> {
                        // Genres & Studios will be looked up by another ViewModel, so just return
                        _state.update {
                            it.copy(
                                loading =
                                    HomeRowLoadingState.Success(
                                        title,
                                        emptyList(),
                                        rowConfig.viewOptions,
                                    ),
                            )
                        }
                        return@launchDefault
                    }

                    else -> {}
                }
                try {
                    val preferences = userPreferencesService.getCurrent()
                    val prefs = preferences.appPreferences.homePagePreferences
                    serverRepository.currentUserDto?.let { userDto ->
                        val libraries =
                            navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                        val result =
                            homeSettingsService.fetchDataForRow(
                                row = rowConfig,
                                scope = viewModelScope,
                                prefs = prefs,
                                userDto = userDto,
                                libraries = libraries,
                                isRefresh = true,
                                limit = 1_000, // TODO
                                usePaging = true,
                            )
                        Timber.v(
                            "Got %s items for %s",
                            (result as? HomeRowLoadingState.Success)?.items?.size,
                            rowConfig,
                        )
                        _state.update { it.copy(loading = result) }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching: %s", rowConfig)
                    _state.update {
                        it.copy(
                            loading =
                                HomeRowLoadingState.Error(
                                    title,
                                    null,
                                    ex,
                                ),
                        )
                    }
                }
            }
        }

        override fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + JvDispatchers.IO) {
                favoriteWatchManager.setWatched(itemId, played)
                (state.value.loading as? HomeRowLoadingState.Success)?.let {
                    (it.items as? ApiRequestPager<*>)?.refreshItem(position, itemId)
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
                (state.value.loading as? HomeRowLoadingState.Success)?.let {
                    (it.items as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

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
                    // TODO refresh
                }
            }
        }
    }

data class HomeRowGridState(
    val loading: HomeRowLoadingState = HomeRowLoadingState.Pending(StringStringProvider("")),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeRowGrid(
    preferences: UserPreferences,
    destination: Destination.MoreHomeRow,
    modifier: Modifier = Modifier,
    viewModel: HomeRowGridViewModel =
        hiltViewModel<HomeRowGridViewModel, HomeRowGridViewModel.Factory>(
            creationCallback = { it.create(destination.title, destination.config) },
        ),
) {
    val state by viewModel.state.collectAsState()
    val contextMenu = rememberContextMenu(preferences, viewModel)
    val gridFocusRequester = remember { FocusRequester() }
    val viewOptions = destination.config.viewOptions

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        GridTitle(destination.title.getString())

        when (val st = state.loading) {
            is HomeRowLoadingState.Error -> {
                ErrorMessage(st.message, st.exception, Modifier.fillMaxSize())
            }

            is HomeRowLoadingState.Loading,
            is HomeRowLoadingState.Pending,
            -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            is HomeRowLoadingState.Success -> {
                when (destination.config) {
                    is HomeRowConfig.Genres -> {
                        GenreCardGrid(
                            itemId = destination.config.parentId,
                            includeItemTypes = null,
                            modifier = Modifier.fillMaxSize(),
                            initialPosition = destination.initialPosition,
                            collectionType = CollectionType.UNKNOWN,
                        )
                    }

                    is HomeRowConfig.Studios -> {
                        StudioCardGrid(
                            itemId = destination.config.parentId,
                            includeItemTypes = null,
                            modifier = Modifier.fillMaxSize(),
                            initialPosition = destination.initialPosition,
                        )
                    }

                    else -> {
                        val onClickItem =
                            remember {
                                { index: Int, item: BaseItem ->
                                    viewModel.navigateTo(item.destination(index))
                                }
                            }
                        LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                        CardGrid(
                            pager = st.items,
                            onClickItem = onClickItem,
                            onLongClickItem = contextMenu::showContextMenu,
                            onClickPlay = { _, _ -> },
                            letterPosition = { -1 },
                            gridFocusRequester = gridFocusRequester,
                            showJumpButtons = false,
                            showLetterButtons = false,
                            modifier = Modifier.fillMaxSize(),
                            initialPosition = destination.initialPosition,
                            positionCallback = { columns, newPosition ->
                            },
                            cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                                GridCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    imageContentScale = viewOptions.contentScale.scale,
                                    imageAspectRatio = viewOptions.aspectRatio.ratio,
                                    imageType = viewOptions.imageType,
                                    showTitle = true, // viewOptions.showTitles,
                                    fillWidth = widthPx,
                                    modifier = mod,
                                )
                            },
                            columns = if (viewOptions.aspectRatio.ratio > 1f) 4 else 6,
                            spacing = viewOptions.spacing.dp,
                            bringIntoViewSpec = LocalBringIntoViewSpec.current,
                        )
                    }
                }
            }
        }
    }
    contextMenu.Compose()
}
