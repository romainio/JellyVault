package tv.jellyvault.app.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.jellyfin.sdk.model.api.ItemSortBy
import tv.jellyvault.app.data.filter.DefaultFilterOptions
import tv.jellyvault.app.data.filter.ItemFilterBy
import tv.jellyvault.app.data.model.CollectionFolderFilter
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.components.CollectionFolderView
import tv.jellyvault.app.ui.components.ViewOptionsPoster
import tv.jellyvault.app.ui.components.ViewOptionsWide
import tv.jellyvault.app.ui.data.VideoSortOptions
import tv.jellyvault.app.ui.preferences.PreferencesViewModel
import java.util.UUID

@Composable
fun CollectionFolderGeneric(
    preferences: UserPreferences,
    itemId: UUID,
    usePosters: Boolean,
    recursive: Boolean,
    playEnabled: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    sortOptions: List<ItemSortBy> = VideoSortOptions,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    var showHeader by rememberSaveable { mutableStateOf(true) }
    val viewOptions =
        remember(usePosters) {
            if (usePosters) {
                ViewOptionsPoster
            } else {
                ViewOptionsWide
            }
        }
    CollectionFolderView(
        preferences = preferences,
        onClickItem = { index, item ->
            preferencesViewModel.navigationManager.navigateTo(item.destination(index))
        },
        itemId = itemId,
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = sortOptions,
        modifier = modifier,
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = viewOptions,
        playEnabled = playEnabled,
        filterOptions = filterOptions,
    )
}
