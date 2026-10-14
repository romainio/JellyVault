package tv.jellyvault.app.ui.detail.series

import android.content.res.Resources
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUID
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.ExtrasItem
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.Person
import tv.jellyvault.app.data.model.Trailer
import tv.jellyvault.app.data.model.studioNames
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.services.TrailerService
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.RequestOrRestoreFocus
import tv.jellyvault.app.ui.cards.ExtrasRow
import tv.jellyvault.app.ui.cards.ItemRow
import tv.jellyvault.app.ui.cards.PersonRow
import tv.jellyvault.app.ui.cards.SeasonCard
import tv.jellyvault.app.ui.components.ButtonStyle
import tv.jellyvault.app.ui.components.ConfirmDialog
import tv.jellyvault.app.ui.components.ContextMenu
import tv.jellyvault.app.ui.components.ContextMenuActions
import tv.jellyvault.app.ui.components.ContextMenuDialog
import tv.jellyvault.app.ui.components.DeleteButton
import tv.jellyvault.app.ui.components.DialogItem
import tv.jellyvault.app.ui.components.DialogParams
import tv.jellyvault.app.ui.components.ErrorMessage
import tv.jellyvault.app.ui.components.ExpandableFaButton
import tv.jellyvault.app.ui.components.ExpandablePlayButton
import tv.jellyvault.app.ui.components.GenreText
import tv.jellyvault.app.ui.components.HeaderUtils
import tv.jellyvault.app.ui.components.LoadingPage
import tv.jellyvault.app.ui.components.Optional
import tv.jellyvault.app.ui.components.OverviewText
import tv.jellyvault.app.ui.components.PersonContextActions
import tv.jellyvault.app.ui.components.QuickDetails
import tv.jellyvault.app.ui.components.TitleOrLogo
import tv.jellyvault.app.ui.components.TrailerButton
import tv.jellyvault.app.ui.components.rememberBackAction
import tv.jellyvault.app.ui.data.AddPlaylistViewModel
import tv.jellyvault.app.ui.data.ItemDetailsDialog
import tv.jellyvault.app.ui.data.ItemDetailsDialogInfo
import tv.jellyvault.app.ui.detail.CastDialog
import tv.jellyvault.app.ui.detail.DETAIL_OVERVIEW_LINES
import tv.jellyvault.app.ui.detail.PlaylistDialog
import tv.jellyvault.app.ui.detail.SHOW_CAST
import tv.jellyvault.app.ui.detail.rate.RateButton
import tv.jellyvault.app.ui.letNotEmpty
import tv.jellyvault.app.ui.nav.BackdropGeometry
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.nav.DimBackdrop
import tv.jellyvault.app.ui.nav.isScrolledToRows
import tv.jellyvault.app.ui.rememberInt
import tv.jellyvault.app.ui.theme.jv.JvColors
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.ExceptionHandler
import java.util.UUID
import kotlin.time.Duration

@Composable
fun SeriesDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel =
        hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            creationCallback = {
                it.create(destination.itemId, null, SeriesPageType.DETAILS)
            },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsState()
    val playlistState by playlistViewModel.playlistState.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var showCast by remember { mutableStateOf(false) }

    var showWatchConfirmation by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = { itemId, watched ->
                    if (itemId == destination.itemId) {
                        // Confirm if marking whole series
                        showWatchConfirmation = true
                    } else {
                        viewModel.setWatched(itemId, watched, null)
                    }
                },
                onClickFavorite = { itemId, favorite ->
                    viewModel.setFavorite(itemId, favorite, null)
                },
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists()
                    showPlaylistDialog = Optional.present(itemId)
                },
                onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                onDeleteItem = viewModel::deleteItem,
                onChooseVersion = { item, source ->
                    viewModel.savePlayVersion(
                        item,
                        source.id!!.toUUID(),
                    )
                },
                onChooseTracks = { result ->
                    viewModel.saveTrackSelection(
                        result.item,
                        result.itemPlayback,
                        result.trackIndex,
                        result.streamType,
                    )
                },
                onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                onClearChosenStreams = {},
                onShowCast = { showCast = true },
                castForItemId = destination.itemId,
            )
        }

    LifecycleResumeEffect(destination.itemId) {
        viewModel.refresh()

        onPauseOrDispose {
            viewModel.release()
        }
    }

    when (val st = state.series) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier, onBack = rememberBackAction())
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<BaseItem> -> {
            LifecycleResumeEffect(destination.itemId) {
                viewModel.onResumePage()

                onPauseOrDispose {}
            }
            val series = st.data
            val played = series.data.userData?.played ?: false
            SeriesDetailsContent(
                preferences = preferences,
                series = series,
                seasons = state.seasons,
                trailers = state.trailers,
                extras = state.extras,
                people = state.people,
                similar = state.similar,
                played = played,
                favorite = series.data.userData?.isFavorite ?: false,
                canDelete = state.canDeleteSeries,
                modifier = modifier,
                onClickItem = { index, item ->
                    viewModel.navigateTo(item.destination())
                },
                onClickPerson = {
                    viewModel.navigateTo(
                        Destination.MediaItem(
                            it.id,
                            BaseItemKind.PERSON,
                        ),
                    )
                },
                onLongClickItem = { index, season ->
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = true,
                            item = season,
                            chosenStreams = null,
                            showGoTo = true,
                            showStreamChoices = false,
                            canDelete = viewModel.canDelete(season, preferences.appPreferences),
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                overviewOnClick = {
                    overviewDialog = ItemDetailsDialogInfo(series)
                },
                playOnClick = { shuffle ->
                    if (shuffle) {
                        viewModel.navigateTo(
                            Destination.PlaybackList(
                                itemId = series.id,
                                shuffle = true,
                            ),
                        )
                    } else {
                        viewModel.playNextUp()
                    }
                },
                watchOnClick = { showWatchConfirmation = true },
                favoriteOnClick = {
                    val favorite = series.data.userData?.isFavorite ?: false
                    viewModel.setFavorite(series.id, !favorite, null)
                },
                trailerOnClick = {
                    TrailerService.onClick(context, it, viewModel::navigateTo)
                },
                onClickExtra = { _, extra ->
                    viewModel.navigateTo(extra.destination)
                },
                onShowContextMenu = {
                    showContextMenu = it
                },
                actions = contextActions,
            )
            if (showWatchConfirmation) {
                ConfirmDialog(
                    title = series.name ?: "",
                    body =
                        stringResource(if (played) R.string.mark_entire_series_as_unplayed else R.string.mark_entire_series_as_played),
                    onCancel = {
                        showWatchConfirmation = false
                    },
                    onConfirm = {
                        viewModel.setWatchedSeries(!played)
                        showWatchConfirmation = false
                    },
                )
            }
        }
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = viewModel.streamChoiceService::chooseSource,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    if (showCast && state.people.isNotEmpty()) {
        CastDialog(
            people = state.people,
            onClickPerson = {
                showCast = false
                viewModel.navigateTo(Destination.MediaItem(it.id, BaseItemKind.PERSON))
            },
            onDismiss = { showCast = false },
        )
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath = false,
            onDismissRequest = { overviewDialog = null },
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            onSearch = playlistViewModel::loadPlaylists,
            elevation = 3.dp,
        )
    }
}

private const val HEADER_ROW = 0
private const val SEASONS_ROW = HEADER_ROW + 1
private const val PEOPLE_ROW = SEASONS_ROW + 1
private const val TRAILER_ROW = PEOPLE_ROW + 1
private const val EXTRAS_ROW = TRAILER_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1

@Composable
fun SeriesDetailsContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    similar: List<BaseItem>,
    trailers: List<Trailer>,
    extras: List<ExtrasItem>,
    people: List<Person>,
    played: Boolean,
    favorite: Boolean,
    canDelete: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPerson: (Person) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    overviewOnClick: () -> Unit,
    playOnClick: (Boolean) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    onShowContextMenu: (ContextMenu) -> Unit,
    actions: ContextMenuActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var position by rememberInt()
    val focusRequesters = remember { List(SIMILAR_ROW + 1) { FocusRequester() } }
    // Rows scrolled over the image: darken it so the posters and their titles stay readable
    val listState = rememberLazyListState()
    val scrolledToRows by remember { derivedStateOf { listState.isScrolledToRows() } }
    DimBackdrop(if (scrolledToRows) BackdropGeometry.DIM_ROWS else 0f)
    val playFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(focusRequesters.getOrNull(position))

    Box(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier,
            ) {
                item {
                    SeriesDetailsHeader(
                        series = series,
                        showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                        overviewOnClick = overviewOnClick,
                        bringIntoViewRequester = bringIntoViewRequester,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .padding(top = HeaderUtils.topPadding, bottom = 16.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier =
                            Modifier
                                .padding(start = HeaderUtils.startPadding)
                                .focusRequester(focusRequesters[HEADER_ROW])
                                .focusRestorer(playFocusRequester)
                                .focusGroup()
                                .padding(bottom = 16.dp),
                    ) {
                        ExpandablePlayButton(
                            title = R.string.play,
                            resume = Duration.ZERO,
                            icon = Icons.Default.PlayArrow,
                            style = ButtonStyle.Primary,
                            onClick = {
                                position = HEADER_ROW
                                playOnClick.invoke(false)
                            },
                            modifier =
                                Modifier
                                    .focusRequester(playFocusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            scope.launch(ExceptionHandler()) {
                                                bringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    },
                        )
                        ExpandableFaButton(
                            title = R.string.shuffle,
                            iconStringRes = R.string.fa_shuffle,
                            onClick = {
                                position = HEADER_ROW
                                playOnClick.invoke(true)
                            },
                            modifier =
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                        ExpandableFaButton(
                            title = if (played) R.string.mark_unwatched else R.string.mark_watched,
                            iconStringRes = if (played) R.string.fa_eye else R.string.fa_eye_slash,
                            active = played,
                            onClick = watchOnClick,
                            modifier =
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                        ExpandableFaButton(
                            title = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                            iconStringRes = R.string.fa_heart,
                            onClick = favoriteOnClick,
                            iconColor = if (favorite) JvColors.Heart else Color.Unspecified,
                            modifier =
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                        TrailerButton(
                            trailers = trailers,
                            trailerOnClick = trailerOnClick,
                            modifier =
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                        )
                        series.data.providerIds?.get("Tmdb")?.toIntOrNull()?.let { tmdbId ->
                            RateButton(
                                TmdbMediaType.TV,
                                tmdbId,
                                Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch(ExceptionHandler()) {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                            )
                        }
                        if (canDelete) {
                            DeleteButton(
                                title = series.title ?: "",
                                onConfirmDelete = {
                                    position = HEADER_ROW
                                    actions.onDeleteItem.invoke(series)
                                },
                                modifier =
                                    Modifier
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                scope.launch(ExceptionHandler()) {
                                                    bringIntoViewRequester.bringIntoView()
                                                }
                                            }
                                        },
                            )
                        }
                    }
                }
                item {
                    ItemRow(
                        title = stringResource(R.string.tv_seasons) + " (${seasons.size})",
                        items = seasons,
                        onClickItem = { index, item ->
                            position = SEASONS_ROW
                            onClickItem.invoke(index, item)
                        },
                        onLongClickItem = { index, item ->
                            position = SEASONS_ROW
                            onLongClickItem.invoke(index, item)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[SEASONS_ROW]),
                        cardContent = @Composable { index, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                imageHeight = Cards.height2x3,
                                imageWidth = Dp.Unspecified,
                                showImageOverlay = true,
                                modifier = mod,
                            )
                        },
                    )
                }
                if (SHOW_CAST && people.isNotEmpty()) {
                    item {
                        PersonRow(
                            people = people,
                            onClick = {
                                position = PEOPLE_ROW
                                onClickPerson.invoke(it)
                            },
                            onLongClick = { index, person ->
                                position = PEOPLE_ROW
                                onShowContextMenu.invoke(
                                    ContextMenu.ForPerson(
                                        fromLongClick = true,
                                        person = person,
                                        actions =
                                            PersonContextActions(
                                                navigateTo = actions.navigateTo,
                                                onClickFavorite = actions.onClickFavorite,
                                            ),
                                    ),
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[PEOPLE_ROW]),
                        )
                    }
                }
                if (extras.isNotEmpty()) {
                    item {
                        ExtrasRow(
                            extras = extras,
                            onClickItem = { index, item ->
                                position = EXTRAS_ROW
                                onClickExtra.invoke(index, item)
                            },
                            onLongClickItem = { _, _ -> },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[EXTRAS_ROW]),
                        )
                    }
                }
                if (similar.isNotEmpty()) {
                    item {
                        ItemRow(
                            title = stringResource(R.string.more_like_this),
                            items = similar,
                            onClickItem = { index, item ->
                                position = SIMILAR_ROW
                                onClickItem.invoke(index, item)
                            },
                            onLongClickItem = { index, item ->
                                position = SIMILAR_ROW
                                if (!item.isRemote) {
                                    onShowContextMenu.invoke(
                                        ContextMenu.ForBaseItem(
                                            fromLongClick = true,
                                            item = item,
                                            chosenStreams = null,
                                            showGoTo = true,
                                            showStreamChoices = false,
                                            canDelete = false,
                                            canRemoveContinueWatching = false,
                                            canRemoveNextUp = false,
                                            actions = actions,
                                        ),
                                    )
                                }
                            },
                            cardContent = { index, item, mod, onClick, onLongClick ->
                                SeasonCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    modifier = mod,
                                    showImageOverlay = true,
                                    imageHeight = Cards.height2x3,
                                    imageWidth = Dp.Unspecified,
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[SIMILAR_ROW]),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesDetailsHeader(
    series: BaseItem,
    showLogo: Boolean,
    overviewOnClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dto = series.data
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            item = series,
            showLogo = showLogo,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = HeaderUtils.startPadding),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            QuickDetails(
                series.ui.quickDetails,
                null,
                Modifier.padding(start = HeaderUtils.startPadding),
            )
            dto.studios?.let {
                val studios = remember { series.studioNames }
                GenreText(
                    studios,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
            }
            dto.genres?.letNotEmpty {
                GenreText(it, Modifier.padding(start = HeaderUtils.startPadding, bottom = 4.dp))
            }
            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = DETAIL_OVERVIEW_LINES,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }
        }
    }
}

fun buildDialogForSeason(
    resources: Resources,
    s: BaseItem,
    canDelete: Boolean,
    onClickItem: (BaseItem) -> Unit,
    markPlayed: (Boolean) -> Unit,
    onClickPlay: (Boolean) -> Unit,
    onClickDelete: (BaseItem) -> Unit,
): DialogParams {
    val items =
        buildList {
            add(
                DialogItem(resources.getString(R.string.go_to), Icons.Default.PlayArrow) {
                    onClickItem.invoke(s)
                },
            )
            if (s.data.userData?.played == true) {
                add(
                    DialogItem(resources.getString(R.string.mark_unwatched), R.string.fa_eye) {
                        markPlayed.invoke(false)
                    },
                )
            } else {
                add(
                    DialogItem(resources.getString(R.string.mark_watched), R.string.fa_eye_slash) {
                        markPlayed.invoke(true)
                    },
                )
            }
            add(
                DialogItem(
                    resources.getString(R.string.play),
                    Icons.Default.PlayArrow,
                ) {
                    onClickPlay.invoke(false)
                },
            )
            add(
                DialogItem(
                    resources.getString(R.string.shuffle),
                    R.string.fa_shuffle,
                ) {
                    onClickPlay.invoke(true)
                },
            )
            if (canDelete) {
                add(
                    DialogItem(
                        resources.getString(R.string.delete),
                        Icons.Default.Delete,
                        iconColor = JvColors.Danger,
                    ) {
                        onClickDelete.invoke(s)
                    },
                )
            }
        }
    return DialogParams(
        title = s.name ?: resources.getString(R.string.tv_season),
        fromLongClick = true,
        items = items,
    )
}
