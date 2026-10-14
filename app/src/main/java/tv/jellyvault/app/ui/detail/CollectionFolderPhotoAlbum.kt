package tv.jellyvault.app.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import tv.jellyvault.app.data.filter.DefaultFilterOptions
import tv.jellyvault.app.data.filter.ItemFilterBy
import tv.jellyvault.app.data.model.CollectionFolderFilter
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.components.CollectionFolderView
import tv.jellyvault.app.ui.components.CollectionFolderViewModel
import tv.jellyvault.app.ui.components.GridClickActions
import tv.jellyvault.app.ui.components.ViewOptionsWide
import tv.jellyvault.app.ui.data.VideoSortOptions
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.toServerString
import java.util.UUID

@Composable
fun CollectionFolderPhotoAlbum(
    preferences: UserPreferences,
    itemId: UUID,
    recursive: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    sortOptions: List<ItemSortBy> = VideoSortOptions,
    // Note: making the VM here and passing down is bad practice, but we need the grid state when clicking on items
    // TODO refactor this
    viewModel: CollectionFolderViewModel =
        hiltViewModel<CollectionFolderViewModel, CollectionFolderViewModel.Factory>(
            key = itemId.toServerString(),
        ) {
            it.create(
                itemId = itemId.toServerString(),
                initialSortAndDirection = null,
                recursive = recursive,
                collectionFilter = filter,
                useSeriesForPrimary = false,
                defaultViewOptions = ViewOptionsWide,
            )
        },
) {
    var showHeader by rememberSaveable { mutableStateOf(true) }
    val state by viewModel.state.collectAsState()
    CollectionFolderView(
        preferences = preferences,
        actions =
            remember {
                GridClickActions(
                    onClickItem = { index, item ->
                        val destination =
                            if (item.type == BaseItemKind.PHOTO) {
                                Destination.Slideshow(
                                    parentId = itemId,
                                    index = index,
                                    filter =
                                        CollectionFolderFilter(
                                            filter = state.filter,
                                        ),
                                    sortAndDirection = state.sortAndDirection,
                                    recursive = recursive,
                                    startSlideshow = false,
                                )
                            } else {
                                item.destination(index)
                            }
                        viewModel.navigateTo(destination)
                    },
                )
            },
        itemId = itemId.toServerString(),
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = sortOptions,
        modifier = modifier,
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = ViewOptionsWide,
        playEnabled = true,
        filterOptions = filterOptions,
        viewModel = viewModel,
    )
}
