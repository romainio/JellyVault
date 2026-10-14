package tv.jellyvault.app.ui.discover

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.cards.GridCard
import tv.jellyvault.app.ui.components.HeaderUtils
import tv.jellyvault.app.ui.main.HomePageContent
import tv.jellyvault.app.ui.main.HomePageHeader
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.rememberPosition
import tv.jellyvault.app.ui.theme.jv.JvShapes
import tv.jellyvault.app.ui.tryRequestFocus

private const val GRID_COLUMNS = 6

/** Same size as the row cards, so six fit next to the menu */
private val GRID_CARD_WIDTH = 116.dp

/**
 * Streaming-service style page of a genre, studio, network or saga: big header, themed rows, then every title
 */
@Composable
fun TmdbBrowsePage(
    target: BrowseTarget,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: TmdbBrowseViewModel = hiltViewModel(key = "browse_$target"),
) {
    LaunchedEffect(target) { viewModel.init(target) }
    val rows by viewModel.rows.collectAsState()
    val grid = viewModel.grid.collectAsState().value
    val gridItems =
        grid
            ?.items
            ?.collectAsState()
            ?.value
            .orEmpty()
    val gridError = grid?.error?.collectAsState()?.value
    val gridLoading = grid?.loading?.collectAsState()?.value ?: false
    val gridEmpty = grid?.empty?.collectAsState()?.value ?: false
    val focusManager = LocalFocusManager.current
    var position by rememberPosition()
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.collect { viewModel.onVisibleRow(it) }
    }
    val allTitle = stringResource(R.string.browse_all_titles)
    val pageTitle = target.title()
    // Focused grid card (-1: focus is in the rows), kept across Back so focus returns to it
    var gridFocus by rememberSaveable { mutableIntStateOf(-1) }
    val restoreIndex = remember { gridFocus }
    val restoreRequester = remember { FocusRequester() }
    if (restoreIndex >= 0) {
        val ready = gridItems.size > restoreIndex && rows.isNotEmpty()
        LaunchedEffect(ready) {
            if (ready) {
                // Rows, then the grid title, then one item per grid line
                listState.scrollToItem(rows.size + 1 + restoreIndex / GRID_COLUMNS)
                restoreRequester.tryRequestFocus()
                // The rows set their own backdrop first: put back the one of the restored card
                viewModel.updateBackdrop(gridItems[restoreIndex])
            }
        }
    }
    val cardWidthPx = with(LocalDensity.current) { GRID_CARD_WIDTH.roundToPx() }
    val showLogo = preferences.appPreferences.interfacePreferences.showLogos
    HomePageContent(
        homeRows = rows,
        position = position,
        onFocusPosition = {
            position = it
            gridFocus = -1
        },
        takeFocus = restoreIndex < 0,
        headerComposable = { focusedItem ->
            Column {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, top = 16.dp),
                )
                HomePageHeader(
                    item = gridItems.getOrNull(gridFocus) ?: focusedItem,
                    showLogo = showLogo,
                    modifier = HeaderUtils.modifier,
                )
            }
        },
        onClickItem = { _, item -> viewModel.navigationManager.navigateTo(item.destination()) },
        onLongClickItem = { _, _ -> },
        onClickPlay = { _, item -> if (item.playable) viewModel.navigationManager.navigateTo(Destination.Playback(item)) },
        showClock = preferences.appPreferences.interfacePreferences.showClock,
        // While a grid card has focus, the rows must not put their own backdrop back
        onUpdateBackdrop = { if (gridFocus < 0) viewModel.updateBackdrop(it) },
        showLogo = showLogo,
        showViewMore = false,
        onRetry = viewModel::retry,
        listState = listState,
        modifier = modifier,
        footer =
            if (grid == null) {
                null
            } else {
                {
                    item(key = "grid_title") {
                        Text(
                            text = "$allTitle · $pageTitle",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }
                    if (gridItems.isEmpty() && gridError == null && (gridLoading || gridEmpty)) {
                        item(key = "grid_status") {
                            Text(
                                text = stringResource(if (gridEmpty) R.string.discover_genre_empty else R.string.loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
                            )
                        }
                    }
                    itemsIndexed(gridItems.chunked(GRID_COLUMNS), key = { index, _ -> "grid_$index" }) { lineIndex, line ->
                        // Ask for the next page when this line is on screen, again whenever a page arrives
                        // (a page of titles already shown adds no new line)
                        LaunchedEffect(lineIndex, gridItems.size) { grid.onPosition((lineIndex + 1) * GRID_COLUMNS) }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp).focusGroup(),
                        ) {
                            line.forEachIndexed { column, item ->
                                val index = lineIndex * GRID_COLUMNS + column
                                GridCard(
                                    item = item,
                                    onClick = { viewModel.navigationManager.navigateTo(item.destination()) },
                                    onLongClick = {},
                                    imageContentScale = ContentScale.FillBounds,
                                    // Owned titles load their Jellyfin image at this size
                                    fillWidth = cardWidthPx,
                                    modifier =
                                        Modifier
                                            .width(GRID_CARD_WIDTH)
                                            .then(if (index == restoreIndex) Modifier.focusRequester(restoreRequester) else Modifier)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    gridFocus = index
                                                    viewModel.updateBackdrop(item)
                                                }
                                            },
                                )
                            }
                        }
                    }
                    if (gridError != null) {
                        item(key = "grid_retry") {
                            Button(
                                shape = ButtonDefaults.shape(JvShapes.Button),
                                onClick = {
                                    // The button goes away once the page loads: keep focus in the grid
                                    focusManager.moveFocus(FocusDirection.Up)
                                    grid.retry()
                                },
                                modifier = Modifier.padding(start = 24.dp),
                            ) {
                                Text(stringResource(R.string.tmdb_retry))
                            }
                        }
                    }
                }
            },
    )
}

@Composable
private fun BrowseTarget.title(): String =
    when (this) {
        is BrowseTarget.Genre -> {
            stringResource(
                if (mediaType == TmdbMediaType.MOVIE) R.string.catalog_genre_movies_format else R.string.catalog_genre_series_format,
                stringResource(genre.titleRes),
            )
        }

        is BrowseTarget.Studio -> {
            studio.displayName
        }

        is BrowseTarget.Network -> {
            network.displayName
        }

        is BrowseTarget.Saga -> {
            saga.displayName
        }

        is BrowseTarget.AnyCompany -> {
            name
        }

        is BrowseTarget.AnyNetwork -> {
            name
        }

        is BrowseTarget.AnyCollection -> {
            name
        }
    }
