package tv.jellyvault.app.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import tv.jellyvault.app.R
import tv.jellyvault.app.data.filter.DefaultTvFilterOptions
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.CollectionFolderFilter
import tv.jellyvault.app.data.model.GetItemsFilter
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.ui.components.CollectionFolderView
import tv.jellyvault.app.ui.components.ErrorMessage
import tv.jellyvault.app.ui.components.GenreCardGrid
import tv.jellyvault.app.ui.components.RecommendedTvShow
import tv.jellyvault.app.ui.components.StudioCardGrid
import tv.jellyvault.app.ui.components.TabDetails
import tv.jellyvault.app.ui.components.TabbedPage
import tv.jellyvault.app.ui.components.ViewOptionsPoster
import tv.jellyvault.app.ui.data.SeriesSortOptions
import tv.jellyvault.app.ui.nav.Destination
import javax.inject.Inject

@HiltViewModel
class CollectionFolderTvViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
    ) : ViewModel()

@Composable
fun CollectionFolderTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderTvViewModel = hiltViewModel(),
) {
    val tabs =
        remember {
            listOf(
                TabDetails(R.string.recommended),
                TabDetails(R.string.library),
                TabDetails(R.string.genres),
                TabDetails(R.string.studios),
            )
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
            // Recommended
            0 -> {
                RecommendedTvShow(
                    preferences = preferences,
                    parentId = destination.itemId,
                    onFocusPosition = { pos ->
                        showHeader = pos.row < 1
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Library
            1 -> {
                CollectionFolderView(
                    preferences = preferences,
                    itemId = destination.itemId,
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.SERIES),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = SeriesSortOptions,
                    filterOptions = DefaultTvFilterOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    onClickItem = onClickItem,
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Genres
            2 -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    collectionType = CollectionType.TVSHOWS,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Studios
            3 -> {
                StudioCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}
