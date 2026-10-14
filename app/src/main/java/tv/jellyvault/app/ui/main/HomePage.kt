package tv.jellyvault.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.data.model.HomeRowViewOptions
import tv.jellyvault.app.data.model.QuickDetailsData
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.cards.BannerCard
import tv.jellyvault.app.ui.cards.BannerCardWithTitle
import tv.jellyvault.app.ui.cards.GenreCard
import tv.jellyvault.app.ui.cards.ItemRow
import tv.jellyvault.app.ui.cards.Shelf
import tv.jellyvault.app.ui.cards.ShelfCard
import tv.jellyvault.app.ui.cards.ShelfInfo
import tv.jellyvault.app.ui.cards.StudioCard
import tv.jellyvault.app.ui.cards.ViewMoreCard
import tv.jellyvault.app.ui.components.CircularProgress
import tv.jellyvault.app.ui.components.ContextMenu
import tv.jellyvault.app.ui.components.ContextMenuActions
import tv.jellyvault.app.ui.components.ContextMenuDialog
import tv.jellyvault.app.ui.components.EpisodeName
import tv.jellyvault.app.ui.components.ErrorMessage
import tv.jellyvault.app.ui.components.FocusableItemRow
import tv.jellyvault.app.ui.components.HeaderUtils
import tv.jellyvault.app.ui.components.LoadingPage
import tv.jellyvault.app.ui.components.PlaceholderItemRow
import tv.jellyvault.app.ui.components.QuickDetails
import tv.jellyvault.app.ui.components.TitleOrLogo
import tv.jellyvault.app.ui.components.rememberLogoUrl
import tv.jellyvault.app.ui.data.AddPlaylistViewModel
import tv.jellyvault.app.ui.data.ItemDetailsDialog
import tv.jellyvault.app.ui.data.ItemDetailsDialogInfo
import tv.jellyvault.app.ui.data.RowColumn
import tv.jellyvault.app.ui.detail.PlaylistDialog
import tv.jellyvault.app.ui.ifElse
import tv.jellyvault.app.ui.isNotNullOrBlank
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.playback.isPlayKeyUp
import tv.jellyvault.app.ui.playback.playable
import tv.jellyvault.app.ui.playback.scale
import tv.jellyvault.app.ui.rememberPosition
import tv.jellyvault.app.ui.tryRequestFocus
import tv.jellyvault.app.ui.util.LocalInterfaceCustomization
import tv.jellyvault.app.ui.util.ScrollToTopBringIntoViewSpec
import tv.jellyvault.app.util.HomeRowLoadingState
import tv.jellyvault.app.util.LoadingState
import java.util.UUID
import kotlin.time.Duration

@Composable
fun HomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        viewModel.init()
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    val loading = state.loadingState
    val refreshing = state.refreshState
    val homeRows = state.homeRows

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state, modifier, onRetry = viewModel::retry)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
            var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
            var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }

            val playlistState by playlistViewModel.playlistState.collectAsState()
            var position by rememberPosition()

            val onFocusPosition = remember { { it: RowColumn -> position = it } }
            val currentHomePrefs by rememberUpdatedState(preferences.appPreferences.homePagePreferences)
            val onClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        if (currentHomePrefs.clickToPlay &&
                            homeRows.getOrNull(clickedPosition.row)?.isContinueWatchingNextUp == true
                        ) {
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        } else {
                            viewModel.navigationManager.navigateTo(item.destination())
                        }
                    }
                }
            val onLongClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        if (!item.isRemote) {
                            val row =
                                (homeRows.getOrNull(clickedPosition.row) as? HomeRowLoadingState.Success)
                            val canRemoveContinueWatching =
                                row?.rowType is HomeRowConfig.ContinueWatching || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                            val canRemoveNextUp =
                                row?.rowType is HomeRowConfig.NextUp || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                            showContextMenu =
                                ContextMenu.ForBaseItem(
                                    fromLongClick = true,
                                    item = item,
                                    chosenStreams = null,
                                    showGoTo = true,
                                    showStreamChoices = false,
                                    canDelete =
                                        viewModel.canDelete(
                                            item,
                                            preferences.appPreferences,
                                        ),
                                    canRemoveContinueWatching = canRemoveContinueWatching,
                                    canRemoveNextUp = canRemoveNextUp,
                                    actions =
                                        ContextMenuActions(
                                            navigateTo = viewModel.navigationManager::navigateTo,
                                            onClickWatch = viewModel::setWatched,
                                            onClickFavorite = viewModel::setFavorite,
                                            onClickAddPlaylist = { itemId ->
                                                playlistViewModel.loadPlaylists()
                                                showPlaylistDialog = itemId
                                            },
                                            onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                                            onDeleteItem = {
                                                viewModel.deleteItem(position, it)
                                            },
                                            onChooseVersion = { _, _ ->
                                                // Not supported on this page
                                            },
                                            onChooseTracks = {
                                                // Not supported on this page
                                            },
                                            onShowOverview = {
                                                overviewDialog = ItemDetailsDialogInfo(it)
                                            },
                                            onClearChosenStreams = {},
                                            onClickRemoveFromNextUp = viewModel::removeFromNextUp,
                                        ),
                                )
                        }
                    }
                }
            val onClickPlay =
                remember {
                    { _: RowColumn, item: BaseItem ->
                        if (item.playable) {
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        }
                    }
                }
            val onClickViewMore =
                remember {
                    { _: RowColumn, row: HomeRowLoadingState.Success ->
                        viewModel.navigationManager.navigateTo(
                            Destination.MoreHomeRow(row.title, row.rowType!!, row.items.size),
                        )
                    }
                }

            val listState = rememberLazyListState()
            LaunchedEffect(listState) {
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: 0
                }.collect { viewModel.onVisibleRow(it) }
            }
            HomePageContent(
                homeRows = homeRows,
                listState = listState,
                position = position,
                onFocusPosition = onFocusPosition,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                loadingState = refreshing,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = viewModel::updateBackdrop,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                showViewMore = true,
                onClickViewMore = onClickViewMore,
                onRetry = viewModel::retry,
                modifier = modifier,
                modernRows = LocalInterfaceCustomization.current.shelfHome,
            )
            overviewDialog?.let { info ->
                ItemDetailsDialog(
                    info = info,
                    showFilePath = false,
                    onDismissRequest = { overviewDialog = null },
                )
            }
            showContextMenu?.let { contextMenu ->
                ContextMenuDialog(
                    onDismissRequest = { showContextMenu = null },
                    getMediaSource = null,
                    contextMenu = contextMenu,
                    preferredSubtitleLanguage = null,
                )
            }
            showPlaylistDialog?.let { itemId ->
                PlaylistDialog(
                    title = stringResource(R.string.add_to_playlist),
                    state = playlistState,
                    onDismissRequest = { showPlaylistDialog = null },
                    onClick = {
                        playlistViewModel.addToPlaylist(it.id, itemId)
                        showPlaylistDialog = null
                    },
                    createEnabled = true,
                    onCreatePlaylist = {
                        playlistViewModel.createPlaylistAndAddItem(it, itemId)
                        showPlaylistDialog = null
                    },
                    onSearch = playlistViewModel::loadPlaylists,
                    elevation = 3.dp,
                )
            }
        }
    }
}

val HomeRowLoadingState?.isContinueWatchingNextUp: Boolean
    get() =
        (this as? HomeRowLoadingState.Success).let { row ->
            row?.rowType is HomeRowConfig.ContinueWatching ||
                row?.rowType is HomeRowConfig.NextUp ||
                row?.rowType is HomeRowConfig.ContinueWatchingCombined
        }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageContent(
    homeRows: List<HomeRowLoadingState>,
    position: RowColumn,
    onFocusPosition: (RowColumn) -> Unit,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    showClock: Boolean,
    onUpdateBackdrop: (BaseItem) -> Unit,
    showLogo: Boolean,
    showViewMore: Boolean,
    modifier: Modifier = Modifier,
    loadingState: LoadingState? = null,
    listState: LazyListState = rememberLazyListState(),
    takeFocus: Boolean = true,
    showEmptyRows: Boolean = false,
    headerComposable: @Composable (focusedItem: BaseItem?) -> Unit = { focusedItem ->
        HomePageHeader(
            item = focusedItem,
            showLogo = showLogo,
            modifier = HeaderUtils.modifier,
        )
    },
    onClickViewMore: (RowColumn, HomeRowLoadingState.Success) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    footer: (LazyListScope.() -> Unit)? = null,
    /** Modern design: no header nor backdrop, the focused card opens wide and its details show under its row */
    modernRows: Boolean = false,
) {
    val focusedItem =
        remember(homeRows, position) {
            (homeRows.getOrNull(position.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(
                position.column,
            )
        }

    val rowFocusRequesters = remember(homeRows.size) { List(homeRows.size) { FocusRequester() } }
    var firstFocused by remember { mutableStateOf(false) }

    val currentPosition by rememberUpdatedState(position)
    val currentOnFocusPosition by rememberUpdatedState(onFocusPosition)
    val currentOnClickPlay by rememberUpdatedState(onClickPlay)

    if (takeFocus) {
        LaunchedEffect(homeRows) {
            if (!firstFocused && homeRows.isNotEmpty()) {
                if (position.row >= 0) {
                    val index = position.row.coerceIn(0, rowFocusRequesters.lastIndex)
                    rowFocusRequesters.getOrNull(index)?.tryRequestFocus()
                    firstFocused = true
                } else {
                    // Waiting for the first home row to load, then focus on it
                    firstFocusableRowIndex(homeRows)
                        ?.let {
                            rowFocusRequesters[it].tryRequestFocus()
                            firstFocused = true
                            delay(50)
                            listState.scrollToItem(it)
                        }
                }
            }
        }
    }
    LaunchedEffect(onUpdateBackdrop, focusedItem, modernRows) {
        if (!modernRows) focusedItem?.let { onUpdateBackdrop.invoke(it) }
    }
    Box(
        // Modern rows have no backdrop: cover the one a previous page left behind
        modifier = modifier.ifElse(modernRows, Modifier.background(MaterialTheme.colorScheme.background)),
    ) {
        Column(
            modifier =
                Modifier
                    .focusProperties {
                        onEnter = {
                            rowFocusRequesters.getOrNull(currentPosition.row)?.tryRequestFocus()
                        }
                    }.fillMaxSize(),
        ) {
            if (modernRows) {
                Spacer(Modifier.height(16.dp))
            } else {
                headerComposable.invoke(focusedItem)
            }

            val density = LocalDensity.current
            val spaceAbovePx =
                remember(density) {
                    with(density) {
                        // The size of the row titles & spacing
                        50.dp.toPx()
                    }
                }
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            // Modern rows keep the focused card at the start of its row, where its details are written
            val shelfBringIntoViewSpec =
                remember(density) { ScrollToTopBringIntoViewSpec(with(density) { Shelf.SPACING.toPx() }) }
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx),
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding =
                        PaddingValues(
                            bottom = Cards.height2x3,
                        ),
                    modifier =
                        Modifier
                            .focusRestorer(),
                ) {
                    itemsIndexed(homeRows) { rowIndex, row ->
                        val rowBringIntoViewSpec = if (modernRows) shelfBringIntoViewSpec else defaultBringIntoViewSpec
                        val rowModifier =
                            Modifier
                                .animateItem(placementSpec = null)
                                .padding(bottom = 8.dp)
                        CompositionLocalProvider(
                            LocalBringIntoViewSpec provides rowBringIntoViewSpec,
                        ) {
                            when (val r = row) {
                                is HomeRowLoadingState.Loading,
                                is HomeRowLoadingState.Pending,
                                -> {
                                    PlaceholderItemRow(
                                        title = r.title.getString(),
                                        cardHeight = if (modernRows) Shelf.HEIGHT else Cards.height2x3,
                                        spacing = if (modernRows) Shelf.SPACING else 16.dp,
                                        modifier = rowModifier,
                                    )
                                }

                                is HomeRowLoadingState.Error -> {
                                    FocusableItemRow(
                                        title = r.title.getString(),
                                        subtitle = stringResource(R.string.jv_home_row_error),
                                        isError = true,
                                        onClick = onRetry,
                                        modifier = rowModifier,
                                    )
                                }

                                is HomeRowLoadingState.Success -> {
                                    if (row.items.isNotEmpty()) {
                                        val viewOptions = row.viewOptions
                                        Column(rowModifier) {
                                            ItemRow(
                                                title = row.title.getString(),
                                                items = row.items,
                                                onClickItem =
                                                    remember(rowIndex, onClickItem) {
                                                        { index, item ->
                                                            onClickItem.invoke(
                                                                RowColumn(
                                                                    rowIndex,
                                                                    index,
                                                                ),
                                                                item,
                                                            )
                                                        }
                                                    },
                                                onLongClickItem =
                                                    remember(rowIndex, onLongClickItem) {
                                                        { index, item ->
                                                            onLongClickItem.invoke(
                                                                RowColumn(rowIndex, index),
                                                                item,
                                                            )
                                                        }
                                                    },
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .focusGroup()
                                                        .focusRequester(rowFocusRequesters[rowIndex]),
                                                horizontalPadding = if (modernRows) Shelf.SPACING else viewOptions.spacing.dp,
                                                cardContent = { index, item, cardModifier, onClick, onLongClick ->
                                                    val onFocus =
                                                        remember(rowIndex, index) {
                                                            { isFocused: Boolean ->
                                                                if (isFocused) {
                                                                    currentOnFocusPosition(
                                                                        RowColumn(
                                                                            rowIndex,
                                                                            index,
                                                                        ),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    val onKey =
                                                        remember(item) {
                                                            { event: KeyEvent ->
                                                                if (isPlayKeyUp(event) && item?.playable == true) {
                                                                    Timber.v("Clicked play on ${item.id}")
                                                                    currentOnClickPlay(
                                                                        currentPosition,
                                                                        item,
                                                                    )
                                                                    true
                                                                } else {
                                                                    false
                                                                }
                                                            }
                                                        }
                                                    val focusModifier =
                                                        cardModifier
                                                            .onFocusChanged { onFocus(it.isFocused) }
                                                            .onKeyEvent { onKey(it) }
                                                    if (modernRows && item?.type != BaseItemKind.GENRE &&
                                                        item?.type != BaseItemKind.STUDIO
                                                    ) {
                                                        ShelfCard(
                                                            item = item,
                                                            onClick = onClick,
                                                            onLongClick = onLongClick,
                                                            cornerText =
                                                                if (item?.showLibraryCheck ==
                                                                    true
                                                                ) {
                                                                    "✓"
                                                                } else {
                                                                    item?.ui?.episodeUnplayedCornerText
                                                                },
                                                            modifier = focusModifier,
                                                        )
                                                    } else {
                                                        HomePageCardContent(
                                                            index = index,
                                                            item = item,
                                                            onClick = onClick,
                                                            onLongClick = onLongClick,
                                                            viewOptions = viewOptions,
                                                            modifier = focusModifier,
                                                        )
                                                    }
                                                },
                                                showViewMore = showViewMore && row.showViewMore,
                                                viewMoreCardContent = { mod ->
                                                    HomePageViewMoreCard(
                                                        isEpisode = row.items.last()?.type == BaseItemKind.EPISODE,
                                                        onClick = {
                                                            onClickViewMore.invoke(
                                                                RowColumn(
                                                                    rowIndex,
                                                                    r.items.size,
                                                                ),
                                                                r,
                                                            )
                                                        },
                                                        onLongClick = {},
                                                        viewOptions =
                                                            if (modernRows) {
                                                                viewOptions.copy(heightDp = Shelf.HEIGHT.value.toInt(), showTitles = false)
                                                            } else {
                                                                viewOptions
                                                            },
                                                        modifier =
                                                            mod.onFocusChanged {
                                                                if (it.isFocused) {
                                                                    currentOnFocusPosition.invoke(
                                                                        RowColumn(
                                                                            rowIndex,
                                                                            r.items.size,
                                                                        ),
                                                                    )
                                                                }
                                                            },
                                                    )
                                                },
                                            )
                                            if (modernRows && position.row == rowIndex) {
                                                ShelfInfo(
                                                    item = focusedItem,
                                                    modifier = Modifier.padding(start = Shelf.SPACING + 8.dp, top = 4.dp, bottom = 12.dp),
                                                )
                                            }
                                        }
                                    } else if (showEmptyRows) {
                                        FocusableItemRow(
                                            title = r.title.getString(),
                                            subtitle = stringResource(R.string.no_results),
                                            modifier = rowModifier,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    footer?.invoke(this)
                }
            }
        }
        when (loadingState) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                Box(
                    modifier =
                        Modifier
                            .padding(if (showClock) 40.dp else 20.dp)
                            .size(40.dp)
                            .align(Alignment.TopEnd),
                ) {
                    CircularProgress(Modifier.fillMaxSize())
                }
            }

            else -> {}
        }
    }
}

@Composable
fun HomePageHeader(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item?.type == BaseItemKind.EPISODE
    val dto = item?.data
    HomePageHeader(
        title = item?.title,
        subtitle = if (isEpisode) dto?.name else null,
        overview = dto?.overview,
        overviewTwoLines = isEpisode,
        quickDetails = item?.ui?.quickDetails,
        timeRemaining = item?.timeRemainingOrRuntime,
        endsAt = item?.data?.endDate,
        showLogo = showLogo,
        logoImageUrl = rememberLogoUrl(item),
        // Episodes show their episode name instead
        genres = if (isEpisode) null else dto?.genres?.take(3)?.joinToString(", "),
        modifier = modifier,
    )
}

@Composable
fun HomePageHeader(
    title: String?,
    subtitle: String?,
    overview: String?,
    overviewTwoLines: Boolean,
    quickDetails: QuickDetailsData?,
    timeRemaining: Duration?,
    endsAt: DateTime?,
    showLogo: Boolean,
    logoImageUrl: String?,
    modifier: Modifier = Modifier,
    genres: String? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            title = title,
            logoImageUrl = logoImageUrl,
            showLogo = showLogo,
            modifier = Modifier.fillMaxWidth(.75f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(.6f),
        ) {
            if (subtitle != null) {
                EpisodeName(subtitle)
            }
            QuickDetails(quickDetails, timeRemaining, endsAt = endsAt)
            if (genres.isNotNullOrBlank()) {
                Text(
                    text = genres,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val overviewModifier =
                Modifier
                    .padding(0.dp)
                    .height(headerOverviewHeight(overviewTwoLines))
                    .width(440.dp)
            if (overview.isNotNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (overviewTwoLines) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = overviewModifier,
                )
            } else {
                Spacer(overviewModifier)
            }
        }
    }
}

/**
 * Height of the header overview box: 2 or 4 lines of bodyMedium (21 sp line height)
 */
fun headerOverviewHeight(twoLines: Boolean): Dp = if (twoLines) 44.dp else 84.dp

@Composable
fun HomePageCardContent(
    index: Int,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    when (item?.type) {
        BaseItemKind.GENRE -> {
            GenreCard(
                genreId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        BaseItemKind.STUDIO -> {
            StudioCard(
                studioId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        else -> {
            val imageType =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeImageType.imageType
                    } else {
                        viewOptions.imageType.imageType
                    }
                }
            val ratio =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeAspectRatio.ratio
                    } else {
                        viewOptions.aspectRatio.ratio
                    }
                }
            val scale =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeContentScale.scale
                    } else {
                        viewOptions.contentScale.scale
                    }
                }
            val cornerText =
                if (item?.showLibraryCheck == true) {
                    "✓"
                } else {
                    item?.ui?.episodeUnplayedCornerText
                }
            if (viewOptions.showTitles) {
                BannerCardWithTitle(
                    title = item?.title,
                    subtitle = item?.subtitle,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = cornerText,
                    played = item?.seen ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                )
            } else {
                BannerCard(
                    name = item?.data?.seriesName ?: item?.name,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = cornerText,
                    played = item?.seen ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    interactionSource = null,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                )
            }
        }
    }
}

@Composable
fun HomePageViewMoreCard(
    isEpisode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    val aspectRatio =
        remember(isEpisode, viewOptions) {
            if (isEpisode) {
                viewOptions.episodeAspectRatio
            } else {
                viewOptions.aspectRatio
            }
        }
    ViewMoreCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        aspectRatio = aspectRatio,
        size = DpSize(height = viewOptions.heightDp.dp, width = Dp.Unspecified),
        showTitle = viewOptions.showTitles,
    )
}

/**
 * The row to focus when the home page first loads: the first row with items, but only once every row above it
 * has finished loading, so a lower row that loads first doesn't steal focus from the top of the page.
 */
internal fun firstFocusableRowIndex(rows: List<HomeRowLoadingState>): Int? {
    for ((index, row) in rows.withIndex()) {
        if (!row.completed) return null
        if (row is HomeRowLoadingState.Success && row.items.isNotEmpty()) return index
    }
    return null
}
