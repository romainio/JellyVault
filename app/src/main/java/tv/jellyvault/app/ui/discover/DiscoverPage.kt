package tv.jellyvault.app.ui.discover

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.components.TabDetails
import tv.jellyvault.app.ui.components.TabbedPage
import tv.jellyvault.app.ui.main.HomePageContent
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.rememberPosition
import tv.jellyvault.app.ui.util.LocalInterfaceCustomization

/**
 * Films / Séries page: TMDB discovery ("Pour vous" rows and genres) instead of the plain library
 */
@Composable
fun DiscoverPage(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    mediaType: TmdbMediaType,
    modifier: Modifier = Modifier,
) {
    val tabs =
        remember(mediaType) {
            listOf(
                TabDetails(R.string.discover_for_you),
                TabDetails(R.string.genres),
                TabDetails(if (mediaType == TmdbMediaType.MOVIE) R.string.discover_studios_sagas else R.string.discover_networks),
            )
        }
    var showHeader by rememberSaveable { mutableStateOf(true) }
    TabbedPage(
        // Own key: the old library page remembered a tab index (up to 3) under the library id
        itemId = "discover_${destination.itemId}",
        tabs = tabs,
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, tabDetails ->
        val contentModifier = Modifier.fillMaxSize().focusRequester(tabDetails.contentFocusRequester)
        when (tabIndex) {
            0 -> {
                DiscoverRows(preferences, mediaType, onFocusRow = { showHeader = it < 1 }, modifier = contentModifier)
            }

            1 -> {
                val viewModel: DiscoverViewModel = hiltViewModel(key = "discover_${mediaType.name}")
                TmdbGenreCards(
                    mediaType = mediaType,
                    tmdbConfigured = viewModel.tmdbConfigured,
                    onClick = { genre ->
                        viewModel.navigationManager.navigateTo(Destination.TmdbBrowse(BrowseTarget.Genre(genre, mediaType)))
                    },
                    modifier = contentModifier,
                )
            }

            else -> {
                TmdbBrandCards(mediaType = mediaType, modifier = contentModifier)
            }
        }
    }
}

@Composable
private fun DiscoverRows(
    preferences: UserPreferences,
    mediaType: TmdbMediaType,
    onFocusRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(key = "discover_${mediaType.name}"),
) {
    LaunchedEffect(mediaType) { viewModel.init(mediaType) }
    val rows by viewModel.rows.collectAsState()
    var position by rememberPosition()
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.collect { viewModel.onVisibleRow(it) }
    }
    HomePageContent(
        homeRows = rows,
        position = position,
        onFocusPosition = {
            position = it
            onFocusRow(it.row)
        },
        onClickItem = { _, item -> viewModel.navigationManager.navigateTo(item.destination()) },
        onLongClickItem = { _, _ -> },
        onClickPlay = { _, item ->
            if (item.playable) viewModel.navigationManager.navigateTo(Destination.Playback(item))
        },
        showClock = preferences.appPreferences.interfacePreferences.showClock,
        onUpdateBackdrop = viewModel::updateBackdrop,
        showLogo = preferences.appPreferences.interfacePreferences.showLogos,
        showViewMore = false,
        onRetry = viewModel::retry,
        listState = listState,
        modifier = modifier,
        modernRows = LocalInterfaceCustomization.current.shelfHome,
    )
}
