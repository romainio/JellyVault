package tv.jellyvault.app.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.CollectionFolderFilter
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.ui.components.CollectionFolderView
import tv.jellyvault.app.ui.components.ErrorMessage
import tv.jellyvault.app.ui.components.LoadingPage
import tv.jellyvault.app.ui.components.TabDetails
import tv.jellyvault.app.ui.components.TabbedPage
import tv.jellyvault.app.ui.components.ViewOptions
import tv.jellyvault.app.ui.data.VideoSortOptions
import tv.jellyvault.app.ui.detail.livetv.DvrSchedule
import tv.jellyvault.app.ui.detail.livetv.TvGuideGrid
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.util.StringStringProvider
import tv.jellyvault.app.util.DataLoadingState
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LiveTvCollectionViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(LiveTvRecordingFolderState())

        init {
            viewModelScope.launchIO {
                try {
                    val folders =
                        api.liveTvApi
                            .getRecordingFolders(userId = serverRepository.currentUser?.id)
                            .content.items
                            .map { TabId(it.name ?: "Recordings", it.id) }
                    state.value =
                        LiveTvRecordingFolderState(DataLoadingState.Success(folders))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching recording folders")
                    state.value =
                        LiveTvRecordingFolderState(DataLoadingState.Error(ex))
                }
            }
        }
    }

data class LiveTvRecordingFolderState(
    val folders: DataLoadingState<List<TabId>> = DataLoadingState.Pending,
)

data class TabId(
    val title: String,
    val id: UUID,
)

@Composable
fun CollectionFolderLiveTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: LiveTvCollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val st = state.folders) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<List<TabId>> -> {
            val tabs =
                remember {
                    listOf(
                        TabDetails(R.string.tv_guide),
                        TabDetails(R.string.tv_dvr_schedule),
                    ) +
                        st.data.map {
                            TabDetails(StringStringProvider(it.title))
                        }
                }
            val onClickItem =
                remember {
                    { position: Int, item: BaseItem ->
                        viewModel.navigationManager.navigateTo(item.destination())
                    }
                }

            var showHeader by rememberSaveable { mutableStateOf(true) }
            TabbedPage(
                itemId = destination.itemId.toString(),
                tabs = tabs,
                modifier = modifier,
                showTabs = showHeader,
            ) { tabIndex, tabDetails ->
                when (tabIndex) {
                    0 -> {
                        TvGuideGrid(
                            preferences = preferences,
                            onRowPosition = {
                                showHeader = it <= 0
                            },
                            Modifier
                                .fillMaxSize()
                                .focusRequester(tabDetails.contentFocusRequester),
                        )
                    }

                    1 -> {
                        DvrSchedule(
                            requestFocusAfterLoading = true,
                            focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .focusRequester(tabDetails.contentFocusRequester),
                        )
                    }

                    else -> {
                        val folderIndex = tabIndex - 2
                        if (folderIndex in st.data.indices) {
                            CollectionFolderView(
                                preferences = preferences,
                                onClickItem = onClickItem,
                                itemId = st.data[folderIndex].id,
                                initialFilter = CollectionFolderFilter(),
                                showTitle = false,
                                recursive = false,
                                sortOptions = VideoSortOptions,
                                modifier = Modifier.focusRequester(tabDetails.contentFocusRequester),
                                positionCallback = { columns, position ->
                                    showHeader = position < columns
                                },
                                playEnabled = false,
                                defaultViewOptions = ViewOptions(),
                                focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                            )
                        } else {
                            ErrorMessage("Invalid tab index $tabIndex", null)
                        }
                    }
                }
            }
        }
    }
}
