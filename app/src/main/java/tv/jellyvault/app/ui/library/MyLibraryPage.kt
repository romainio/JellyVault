package tv.jellyvault.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.api.BaseItemKind
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.CollectionFolderFilter
import tv.jellyvault.app.data.model.GetItemsFilter
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.components.CollectionFolderView
import tv.jellyvault.app.ui.components.LoadingPage
import tv.jellyvault.app.ui.components.TabDetails
import tv.jellyvault.app.ui.components.TabbedPage
import tv.jellyvault.app.ui.components.ViewOptionsPoster
import tv.jellyvault.app.ui.data.MovieSortOptions
import tv.jellyvault.app.ui.data.SeriesSortOptions
import tv.jellyvault.app.ui.nav.NavDrawerItem
import tv.jellyvault.app.ui.theme.jv.JvShapes
import tv.jellyvault.app.ui.tryRequestFocus

/**
 * "Ma bibliothèque": everything the user owns, as a Movies tab and a Series tab (library grids with sort and filters)
 */
@Composable
fun MyLibraryPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: MyLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val current =
        when (val st = state) {
            MyLibraryState.Loading -> {
                LoadingPage(modifier)
                return
            }

            MyLibraryState.Empty -> {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.my_library_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return
            }

            is MyLibraryState.Error -> {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                Column(
                    modifier = modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    Text(stringResource(R.string.catalog_load_error), color = MaterialTheme.colorScheme.error)
                    Button(
                        shape = ButtonDefaults.shape(JvShapes.Button),
                        onClick = viewModel::retry,
                        modifier = Modifier.focusRequester(focusRequester),
                    ) {
                        Text(stringResource(R.string.tmdb_retry))
                    }
                }
                return
            }

            is MyLibraryState.Tabs -> {
                st.tabs
            }
        }
    val tabDetails = remember(current) { current.map { TabDetails(it.title) } }
    var showHeader by rememberSaveable { mutableStateOf(true) }
    TabbedPage(
        itemId = NavDrawerItem.MyLibrary.id,
        tabs = tabDetails,
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, details ->
        val tab = current.getOrNull(tabIndex) ?: return@TabbedPage
        CollectionFolderView(
            preferences = preferences,
            onClickItem = { _, item -> viewModel.navigationManager.navigateTo(item.destination()) },
            itemId = tab.library.itemId,
            viewModelKey = "mylibrary_${tab.itemKind.name}",
            initialFilter = CollectionFolderFilter(filter = GetItemsFilter(includeItemTypes = listOf(tab.itemKind))),
            showTitle = false,
            recursive = true,
            sortOptions = if (tab.itemKind == BaseItemKind.MOVIE) MovieSortOptions else SeriesSortOptions,
            defaultViewOptions = ViewOptionsPoster,
            modifier = Modifier.fillMaxSize().focusRequester(details.contentFocusRequester),
            positionCallback = { columns, position -> showHeader = position < columns },
            playEnabled = true,
            focusRequesterOnEmpty = details.tabFocusRequester,
        )
    }
}
