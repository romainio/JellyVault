package tv.jellyvault.app.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.ExtrasItem
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.Person
import tv.jellyvault.app.data.model.Trailer
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.services.TrailerService
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.RequestOrRestoreFocus
import tv.jellyvault.app.ui.cards.ChapterRow
import tv.jellyvault.app.ui.cards.ExtrasRow
import tv.jellyvault.app.ui.cards.ItemRow
import tv.jellyvault.app.ui.cards.PersonRow
import tv.jellyvault.app.ui.cards.SeasonCard
import tv.jellyvault.app.ui.components.ContextMenu
import tv.jellyvault.app.ui.components.ContextMenuActions
import tv.jellyvault.app.ui.components.ContextMenuDialog
import tv.jellyvault.app.ui.components.ErrorMessage
import tv.jellyvault.app.ui.components.ExpandablePlayButtons
import tv.jellyvault.app.ui.components.HeaderUtils
import tv.jellyvault.app.ui.components.LoadingPage
import tv.jellyvault.app.ui.components.Optional
import tv.jellyvault.app.ui.components.PersonContextActions
import tv.jellyvault.app.ui.components.rememberBackAction
import tv.jellyvault.app.ui.data.AddPlaylistViewModel
import tv.jellyvault.app.ui.data.ChooseVersionParams
import tv.jellyvault.app.ui.data.ItemDetailsDialog
import tv.jellyvault.app.ui.data.ItemDetailsDialogInfo
import tv.jellyvault.app.ui.detail.CastDialog
import tv.jellyvault.app.ui.detail.PlaylistDialog
import tv.jellyvault.app.ui.detail.SHOW_CAST
import tv.jellyvault.app.ui.detail.rate.RateButton
import tv.jellyvault.app.ui.letNotEmpty
import tv.jellyvault.app.ui.nav.BackdropGeometry
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.nav.DimBackdrop
import tv.jellyvault.app.ui.nav.isScrolledToRows
import tv.jellyvault.app.ui.rememberInt
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.ExceptionHandler
import java.util.UUID
import kotlin.time.Duration

@Composable
fun MovieDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel =
        hiltViewModel<MovieViewModel, MovieViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.init()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var showCast by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.collectAsState()

    val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
    val preferredSubtitleLanguage = userDto?.configuration?.subtitleLanguagePreference

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = viewModel::setWatched,
                onClickFavorite = viewModel::setFavorite,
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists()
                    showPlaylistDialog.makePresent(itemId)
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
                onClearChosenStreams = { viewModel.clearChosenStreams(it) },
                onShowCast = { showCast = true },
                castForItemId = destination.itemId,
            )
        }

    when (val s = state.loading) {
        is DataLoadingState.Error -> {
            ErrorMessage(s, modifier, onRetry = { viewModel.init() }, onBack = rememberBackAction())
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success -> {
            val unknownStr = stringResource(R.string.unknown)
            val movie by rememberUpdatedState(s.data)
            val chosenStreams by rememberUpdatedState(state.chosenStreams)
            LifecycleResumeEffect(destination.itemId) {
                viewModel.maybePlayThemeSong(destination.itemId)
                onPauseOrDispose {
                    viewModel.release()
                }
            }
            MovieDetailsContent(
                preferences = preferences,
                movie = movie,
                state = state,
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
                playOnClick = {
                    viewModel.navigateTo(
                        Destination.Playback(
                            movie.id,
                            it.inWholeMilliseconds,
                        ),
                    )
                },
                overviewOnClick = {
                    overviewDialog =
                        ItemDetailsDialogInfo(movie)
                },
                moreOnClick = {
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = false,
                            item = movie,
                            chosenStreams = chosenStreams,
                            showGoTo = false,
                            showStreamChoices = true,
                            canDelete = state.canDelete,
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                watchOnClick = {
                    viewModel.setWatched(movie.id, !movie.played)
                },
                favoriteOnClick = {
                    viewModel.setFavorite(movie.id, !movie.favorite)
                },
                onLongClickPerson = { index, person ->
                    showContextMenu =
                        ContextMenu.ForPerson(
                            fromLongClick = true,
                            person = person,
                            actions =
                                PersonContextActions(
                                    navigateTo = viewModel::navigateTo,
                                    onClickFavorite = viewModel::setFavorite,
                                ),
                        )
                },
                onLongClickSimilar = { _, similar ->
                    // A title from TMDB has no Jellyfin item to mark watched or favorite
                    if (!similar.isRemote) {
                        showContextMenu =
                            ContextMenu.ForBaseItem(
                                fromLongClick = true,
                                item = similar,
                                chosenStreams = null,
                                showGoTo = true,
                                showStreamChoices = false,
                                canDelete = false,
                                canRemoveContinueWatching = false,
                                canRemoveNextUp = false,
                                actions = contextActions,
                            )
                    }
                },
                trailerOnClick = {
                    TrailerService.onClick(context, it, viewModel::navigateTo)
                },
                onClickExtra = { index, extra ->
                    viewModel.navigateTo(extra.destination)
                },
                canDelete = state.canDelete,
                onConfirmDelete = { state.movie?.let { viewModel.deleteItem(it) } },
                onChooseVersion = { contextActions.onChooseVersion.invoke(movie, it) },
                modifier = modifier,
            )
        }
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = viewModel.streamChoiceService::chooseSource,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
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
            showFilePath =
                userDto
                    ?.policy
                    ?.isAdministrator == true,
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
private const val PEOPLE_ROW = HEADER_ROW + 1
private const val TRAILER_ROW = PEOPLE_ROW + 1
private const val CHAPTER_ROW = TRAILER_ROW + 1
private const val EXTRAS_ROW = CHAPTER_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1

@Composable
fun MovieDetailsContent(
    preferences: UserPreferences,
    movie: BaseItem,
    state: MovieState,
    playOnClick: (Duration) -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    overviewOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPerson: (Person) -> Unit,
    onLongClickPerson: (Int, Person) -> Unit,
    onLongClickSimilar: (Int, BaseItem) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    canDelete: Boolean,
    onConfirmDelete: () -> Unit,
    onChooseVersion: (MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var position by rememberInt(0)
    val focusRequesters = remember { List(SIMILAR_ROW + 1) { FocusRequester() } }
    // Rows scrolled over the image: darken it so the posters and their titles stay readable
    val listState = rememberLazyListState()
    val scrolledToRows by remember { derivedStateOf { listState.isScrolledToRows() } }
    DimBackdrop(if (scrolledToRows) BackdropGeometry.DIM_ROWS else 0f)
    val dto = movie.data
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    RequestOrRestoreFocus(focusRequesters.getOrNull(position))

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester),
                ) {
                    MovieDetailsHeader(
                        preferences = preferences,
                        movie = movie,
                        chosenStreams = state.chosenStreams,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = HeaderUtils.topPadding, bottom = 16.dp),
                    )
                    ExpandablePlayButtons(
                        title = movie.title ?: "",
                        resumePosition = resumePosition,
                        watched = dto.userData?.played ?: false,
                        favorite = dto.userData?.isFavorite ?: false,
                        playOnClick = {
                            position = HEADER_ROW
                            playOnClick.invoke(it)
                        },
                        moreOnClick = moreOnClick,
                        watchOnClick = watchOnClick,
                        favoriteOnClick = favoriteOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                position = HEADER_ROW
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        trailers = state.trailers,
                        trailerOnClick = {
                            position = TRAILER_ROW
                            trailerOnClick.invoke(it)
                        },
                        canDelete = canDelete,
                        onConfirmDelete = onConfirmDelete,
                        extraButton =
                            dto.providerIds
                                ?.get("Tmdb")
                                ?.toIntOrNull()
                                ?.takeIf { preferences.appPreferences.interfacePreferences.rateFromTv }
                                ?.let { tmdbId -> { buttonModifier -> RateButton(TmdbMediaType.MOVIE, tmdbId, buttonModifier) } },
                        chooseVersionParams =
                            remember(state.chosenStreams, movie, onChooseVersion) {
                                ChooseVersionParams(
                                    chosenStreams = state.chosenStreams,
                                    mediaSources = movie.data.mediaSources.orEmpty(),
                                    onChooseVersion = onChooseVersion,
                                )
                            },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .focusRequester(focusRequesters[HEADER_ROW]),
                    )
                }
            }
            state.people.takeIf { SHOW_CAST }.orEmpty().letNotEmpty { people ->
                item {
                    PersonRow(
                        people = people,
                        onClick = {
                            position = PEOPLE_ROW
                            onClickPerson.invoke(it)
                        },
                        onLongClick = { index, person ->
                            position = PEOPLE_ROW
                            onLongClickPerson.invoke(index, person)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[PEOPLE_ROW]),
                    )
                }
            }
            state.chapters.letNotEmpty { chapters ->
                item {
                    ChapterRow(
                        chapters = chapters,
                        onClick = {
                            position = CHAPTER_ROW
                            playOnClick.invoke(it.position)
                        },
                        onLongClick = {},
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[CHAPTER_ROW]),
                    )
                }
            }
            state.extras.letNotEmpty { extras ->
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
            state.similar.letNotEmpty { similar ->
                item {
                    val imageHeight =
                        remember(movie.type) {
                            if (movie.type == BaseItemKind.MOVIE) {
                                Cards.height2x3
                            } else {
                                Cards.heightEpisode
                            }
                        }
                    ItemRow(
                        title = stringResource(R.string.more_like_this),
                        items = similar,
                        onClickItem = { index, item ->
                            position = SIMILAR_ROW
                            onClickItem.invoke(index, item)
                        },
                        onLongClickItem = { index, similar ->
                            position = SIMILAR_ROW
                            onLongClickSimilar.invoke(index, similar)
                        },
                        cardContent = { index, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                modifier = mod,
                                showImageOverlay = true,
                                imageHeight = imageHeight,
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
