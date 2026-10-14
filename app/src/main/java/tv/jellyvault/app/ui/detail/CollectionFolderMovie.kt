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
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.CollectionFolderFilter
import tv.jellyvault.app.data.model.GetItemsFilter
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.components.CollectionFolderView
import tv.jellyvault.app.ui.components.ErrorMessage
import tv.jellyvault.app.ui.components.GenreCardGrid
import tv.jellyvault.app.ui.components.RecommendedMovie
import tv.jellyvault.app.ui.components.TabDetails
import tv.jellyvault.app.ui.components.TabbedPage
import tv.jellyvault.app.ui.components.ViewOptionsPoster
import tv.jellyvault.app.ui.data.MovieSortOptions
import tv.jellyvault.app.ui.data.VideoSortOptions
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.preferences.PreferencesViewModel

@Composable
fun CollectionFolderMovie(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val tabs =
        remember {
            listOf(
                TabDetails(R.string.recommended),
                TabDetails(R.string.library),
                TabDetails(R.string.collections),
                TabDetails(R.string.genres),
            )
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
                RecommendedMovie(
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
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_library",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = MovieSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Collections
            2 -> {
                CollectionFolderView(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_collection",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = VideoSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Genres
            3 -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    collectionType = CollectionType.MOVIES,
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
