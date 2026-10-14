package tv.jellyvault.app.ui.detail.tmdb

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.arr.ArrException
import tv.jellyvault.app.catalog.arr.ArrService
import tv.jellyvault.app.catalog.arr.RequestStatus
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.tmdbGenreName
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.RequestOrRestoreFocus
import tv.jellyvault.app.ui.cards.ItemRow
import tv.jellyvault.app.ui.cards.SeasonCard
import tv.jellyvault.app.ui.components.Button
import tv.jellyvault.app.ui.components.ButtonStyle
import tv.jellyvault.app.ui.components.CircularProgress
import tv.jellyvault.app.ui.components.ExpandableFaButton
import tv.jellyvault.app.ui.components.ExpandablePlayButton
import tv.jellyvault.app.ui.components.FaButtonIcon
import tv.jellyvault.app.ui.components.GenreText
import tv.jellyvault.app.ui.components.HeaderUtils
import tv.jellyvault.app.ui.components.JvBanner
import tv.jellyvault.app.ui.components.JvBannerMessage
import tv.jellyvault.app.ui.components.OverviewText
import tv.jellyvault.app.ui.components.QuickDetailsText
import tv.jellyvault.app.ui.components.ScrollableDialog
import tv.jellyvault.app.ui.components.TimeRemaining
import tv.jellyvault.app.ui.components.TitleOrLogo
import tv.jellyvault.app.ui.components.VideoStreamDetails
import tv.jellyvault.app.ui.detail.DETAIL_OVERVIEW_LINES
import tv.jellyvault.app.ui.detail.rate.RateButton
import tv.jellyvault.app.ui.dot
import tv.jellyvault.app.ui.formatDuration
import tv.jellyvault.app.ui.nav.BackdropGeometry
import tv.jellyvault.app.ui.nav.DimBackdrop
import tv.jellyvault.app.ui.nav.isScrolledToRows
import tv.jellyvault.app.ui.preferences.arr.messageRes
import tv.jellyvault.app.ui.rememberInt
import tv.jellyvault.app.ui.theme.jv.JvColors
import tv.jellyvault.app.ui.tryRequestFocus
import tv.jellyvault.app.ui.util.KeepVisibleBringIntoViewSpec
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.ExceptionHandler
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/**
 * Detail page of a TMDB title that is not in the library: info, trailer, similar titles and Radarr/Sonarr request
 */
@Composable
fun TmdbDetailPage(
    item: TmdbItem,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: TmdbDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    LaunchedEffect(item) { viewModel.init(item) }
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }

    var banner by remember { mutableStateOf<JvBannerMessage?>(null) }
    val event = state.event
    val requestedRadarr = stringResource(R.string.tmdb_requested_radarr)
    val noTrailerApp = stringResource(R.string.tmdb_no_trailer_app)
    val allSeasonsRequested = stringResource(R.string.tmdb_all_seasons_requested)
    val scanStarted = stringResource(R.string.tmdb_refresh_scan_started)
    val scanRefused = stringResource(R.string.tmdb_refresh_scan_refused)
    val requestedSonarr = stringResource(R.string.tmdb_requested_sonarr)
    val failedMessage =
        (event as? TmdbDetailEvent.Failed)?.let {
            val name = stringResource(if (it.service == ArrService.RADARR) R.string.arr_radarr else R.string.arr_sonarr)
            if (it.code != null) stringResource(it.message, name, it.code) else stringResource(it.message, name)
        }
    LaunchedEffect(event) {
        val text =
            when (event) {
                is TmdbDetailEvent.Requested -> if (event.service == ArrService.RADARR) requestedRadarr else requestedSonarr
                is TmdbDetailEvent.Failed -> failedMessage
                TmdbDetailEvent.AllSeasonsRequested -> allSeasonsRequested
                is TmdbDetailEvent.Refreshed -> if (event.scanRequested) scanStarted else scanRefused
                null -> null
            }
        if (text != null) {
            banner = JvBannerMessage(text, isError = event is TmdbDetailEvent.Failed)
            viewModel.consumeEvent()
        }
    }

    val details = (state.details as? DataLoadingState.Success)?.data
    TmdbDetailContent(
        item = details?.item ?: item,
        details = details,
        loadError = state.details is DataLoadingState.Error,
        state = state,
        showLogos = preferences.appPreferences.interfacePreferences.showLogos,
        rateFromTv = preferences.appPreferences.interfacePreferences.rateFromTv,
        onRetry = viewModel::retry,
        onPrimaryClick = viewModel::onPrimaryClick,
        onTrailerClick = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (e: ActivityNotFoundException) {
                banner = JvBannerMessage(noTrailerApp, isError = true)
            }
        },
        onNavigate = viewModel::navigate,
        onBrowse = viewModel::browse,
        onPlay = viewModel::play,
        onOpenJellyfinPage = viewModel::openJellyfinPage,
        onRefresh = viewModel::refreshNow,
        onToggleWatched = viewModel::toggleWatched,
        onToggleFavorite = viewModel::toggleFavorite,
        modifier = modifier,
    )
    JvBanner(banner, onDismiss = { banner = null })

    state.seasonDialog?.let { dialog ->
        SeasonRequestDialog(
            choices = dialog.choices,
            onRequest = viewModel::requestSeasons,
            onDismiss = viewModel::dismissSeasonDialog,
        )
    }
}

private const val HEADER_ROW = 0
private const val SIMILAR_ROW = HEADER_ROW + 1
private const val SAME_GENRE_ROW = SIMILAR_ROW + 1

/**
 * Laid out like the Jellyfin title page (MovieDetails): header, row of buttons, then rows of titles. The header is drawn
 * from [item] while [details] load, so the page never starts as a bare spinner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TmdbDetailContent(
    item: TmdbItem,
    details: TmdbDetails?,
    loadError: Boolean,
    state: TmdbDetailState,
    showLogos: Boolean,
    rateFromTv: Boolean,
    onRetry: () -> Unit,
    onPrimaryClick: () -> Unit,
    onTrailerClick: (String) -> Unit,
    onNavigate: (BaseItem) -> Unit,
    onBrowse: (BrowseTarget) -> Unit,
    onPlay: () -> Unit,
    onOpenJellyfinPage: () -> Unit,
    onRefresh: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var position by rememberInt(HEADER_ROW)
    // Saved with the page: coming back (eg from a similar title) restores the focus instead of jumping to the primary button
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val restoring = remember { initialFocusDone }
    val focusRequesters = remember { List(SAME_GENRE_ROW + 1) { FocusRequester() } }
    val primaryFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var pageHasFocus by remember { mutableStateOf(false) }
    val status = state.status

    if (restoring) RequestOrRestoreFocus(focusRequesters.getOrNull(position))
    var previousStatus by remember { mutableStateOf<RequestStatus?>(null) }
    LaunchedEffect(status) {
        val previous = previousStatus
        previousStatus = status
        if (status == null) return@LaunchedEffect
        if (!initialFocusDone) {
            // First visit: the primary button once it is usable; it is disabled on « Disponible bientôt », « Actualiser » then
            (if (shouldFocusPrimary(status)) primaryFocus else refreshFocus).tryRequestFocus()
            initialFocusDone = true
        } else if (previous != null && previous::class != status::class && !pageHasFocus && shouldFocusPrimary(status)) {
            // The focused button went away (« Actualiser » once « Disponible bientôt » becomes « Regarder »)
            primaryFocus.tryRequestFocus()
        }
    }
    LaunchedEffect(loadError) { if (loadError) retryFocus.tryRequestFocus() }

    // Rows keep the normal horizontal scrolling: the top alignment below would push the focused card off the left edge
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    // Scroll only as much as needed: focusing the buttons (after the summary got focus first) must not
    // push the title off screen, which both the TV "pivot" and a scroll-to-top did
    CompositionLocalProvider(LocalBringIntoViewSpec provides KeepVisibleBringIntoViewSpec) {
        Box(modifier = modifier.onFocusChanged { pageHasFocus = it.hasFocus }) {
            // Rows scrolled over the image: darken it, like the Jellyfin title pages
            val listState = rememberLazyListState()
            val scrolledToRows by remember { derivedStateOf { listState.isScrolledToRows() } }
            DimBackdrop(if (scrolledToRows) BackdropGeometry.DIM_ROWS else 0f)
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header and buttons in one block: focusing a button scrolls so the whole block (title/logo included) is visible
                item {
                    val headerBringIntoView = remember { BringIntoViewRequester() }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(headerBringIntoView)
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        position = HEADER_ROW
                                        scope.launch(ExceptionHandler()) { headerBringIntoView.bringIntoView() }
                                    }
                                },
                    ) {
                        TmdbHeader(
                            item = item,
                            details = details,
                            playback = state.playback,
                            showLogos = showLogos,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = HeaderUtils.topPadding, bottom = 16.dp),
                        )
                        if (loadError) {
                            LoadErrorRow(
                                onRetry = onRetry,
                                retryFocus = retryFocus,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        } else {
                            TmdbButtons(
                                item = item,
                                details = details,
                                state = state,
                                rateFromTv = rateFromTv,
                                primaryFocus = primaryFocus,
                                refreshFocus = refreshFocus,
                                onPrimaryClick = onPrimaryClick,
                                onTrailerClick = onTrailerClick,
                                onBrowse = onBrowse,
                                onPlay = onPlay,
                                onOpenJellyfinPage = onOpenJellyfinPage,
                                onRefresh = onRefresh,
                                onToggleWatched = onToggleWatched,
                                onToggleFavorite = onToggleFavorite,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .focusRequester(focusRequesters[HEADER_ROW]),
                            )
                        }
                    }
                }
                (status as? RequestStatus.Error)?.let { error ->
                    item {
                        val name = stringResource(if (item.mediaType == TmdbMediaType.MOVIE) R.string.arr_radarr else R.string.arr_sonarr)
                        val reason = error.reason
                        Text(
                            text =
                                if (reason is ArrException.Http) {
                                    stringResource(reason.messageRes(), name, reason.code)
                                } else {
                                    stringResource(reason.messageRes(), name)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = HeaderUtils.startPadding),
                        )
                    }
                }
                listOf(
                    Triple(SIMILAR_ROW, R.string.tmdb_similar, state.similar),
                    Triple(SAME_GENRE_ROW, R.string.tmdb_same_genre, state.sameGenre),
                ).filter { it.third.isNotEmpty() }
                    .forEach { (row, rowTitle, rowItems) ->
                        item(key = "row_$row") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                                // Same cards as the « More like this » row of the Jellyfin page
                                ItemRow(
                                    title = stringResource(rowTitle),
                                    items = rowItems,
                                    onClickItem = { _, similar ->
                                        position = row
                                        onNavigate(similar)
                                    },
                                    onLongClickItem = { _, _ -> position = row },
                                    cardContent = { _, similar, cardModifier, onClick, onLongClick ->
                                        SeasonCard(
                                            item = similar,
                                            onClick = onClick,
                                            onLongClick = onLongClick,
                                            modifier = cardModifier,
                                            showImageOverlay = true,
                                            imageHeight = Cards.height2x3,
                                            imageWidth = Dp.Unspecified,
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequesters[row]),
                                )
                            }
                        }
                    }
            }
        }
    }
}

/**
 * Same structure and paddings as MovieDetailsHeader
 */
@Composable
private fun TmdbHeader(
    item: TmdbItem,
    details: TmdbDetails?,
    playback: OwnedPlayback?,
    showLogos: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            title = item.title,
            logoImageUrl = details?.logoUrl,
            showLogo = showLogos,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = HeaderUtils.startPadding),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            TmdbQuickDetails(item, details, playback, Modifier.padding(start = HeaderUtils.startPadding))

            val genres = remember(item, details) { details?.genres ?: item.genreIds.mapNotNull { tmdbGenreName(it, item.mediaType) } }
            if (genres.isNotEmpty()) GenreText(genres, Modifier.padding(start = HeaderUtils.startPadding))

            // Same details as the Jellyfin page once the title is in the library: quality, audio language, subtitles
            playback?.let {
                VideoStreamDetails(
                    chosenStreams = it.chosenStreams,
                    numberOfVersions = it.item.data.mediaSourceCount ?: 0,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, top = 4.dp, bottom = 16.dp),
                )
            }
            details?.tagline?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
            }
            item.overview?.let { overview ->
                var showFull by remember { mutableStateOf(false) }
                // Its text has its own 8 dp padding, which lines it up with the start padding above
                OverviewText(
                    overview = overview,
                    maxLines = DETAIL_OVERVIEW_LINES,
                    textBoxHeight = Dp.Unspecified,
                    onClick = { showFull = true },
                )
                if (showFull) {
                    ScrollableDialog(onDismissRequest = { showFull = false }) {
                        item { Text(overview, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
    }
}

/**
 * The Jellyfin quick details line: « 2020  •  1h 45m  •  12  •  7,4★ », then the end time when the title can be played
 */
@Composable
private fun TmdbQuickDetails(
    item: TmdbItem,
    details: TmdbDetails?,
    playback: OwnedPlayback?,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val seasons =
        details
            ?.seasons
            ?.size
            ?.takeIf { item.mediaType == TmdbMediaType.TV && it > 0 }
            ?.let { pluralStringResource(R.plurals.tmdb_seasons, it, it) }
    val text =
        remember(item, details, seasons, resources) {
            val parts =
                listOfNotNull(
                    item.year?.toString(),
                    if (item.mediaType == TmdbMediaType.MOVIE) {
                        details?.runtimeMinutes?.takeIf { it > 0 }?.let { resources.formatDuration(it.minutes) }
                    } else {
                        seasons
                    },
                    details?.certification,
                )
            buildAnnotatedString {
                parts.forEachIndexed { index, part ->
                    if (index > 0) dot()
                    append(part)
                }
                item.voteAverage?.takeIf { it > 0 }?.let {
                    if (parts.isNotEmpty()) dot()
                    append(String.format(Locale.getDefault(), "%.1f", it))
                    appendInlineContent(id = "star")
                }
            }
        }
    Row(modifier = modifier) {
        QuickDetailsText(text)
        playback?.item?.timeRemainingOrRuntime?.let { TimeRemaining(it) }
    }
}

@Composable
private fun LoadErrorRow(
    onRetry: () -> Unit,
    retryFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.tmdb_details_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = HeaderUtils.startPadding),
        )
        Box(modifier = Modifier.padding(8.dp)) {
            ExpandablePlayButton(
                title = stringResource(R.string.tmdb_retry),
                icon = { ButtonIcon(Icons.Default.Refresh) },
                onClick = onRetry,
                style = ButtonStyle.Primary,
                modifier = Modifier.focusRequester(retryFocus),
            )
        }
    }
}

/**
 * Same row as ExpandablePlayButtons: primary action, then icon buttons that show their label on focus
 */
@Composable
private fun TmdbButtons(
    item: TmdbItem,
    details: TmdbDetails?,
    state: TmdbDetailState,
    rateFromTv: Boolean,
    primaryFocus: FocusRequester,
    refreshFocus: FocusRequester,
    onPrimaryClick: () -> Unit,
    onTrailerClick: (String) -> Unit,
    onBrowse: (BrowseTarget) -> Unit,
    onPlay: () -> Unit,
    onOpenJellyfinPage: () -> Unit,
    onRefresh: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Until the details and the status are known, the primary button shows a small loading state
    val status = state.status.takeIf { details != null }
    var showFailure by remember { mutableStateOf(false) }
    val owned = status is RequestStatus.Available || (status is RequestStatus.Partial && status.item != null)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(8.dp),
        modifier =
            modifier
                .focusGroup()
                .focusRestorer(primaryFocus),
    ) {
        item("primary") {
            ExpandablePlayButton(
                title = primaryLabel(status, item.mediaType, state.playback),
                icon = { PrimaryIcon(status, state.sending) },
                onClick = { if (status is RequestStatus.DownloadFailed) showFailure = true else onPrimaryClick() },
                style = ButtonStyle.Primary,
                enabled = status != null && !state.sending && status.isClickable(),
                modifier = Modifier.focusRequester(primaryFocus),
            )
        }
        if (status == RequestStatus.ReadySoon) {
            item("refresh") {
                ExpandablePlayButton(
                    title = stringResource(if (state.refreshing) R.string.tmdb_refreshing else R.string.tmdb_refresh),
                    icon = { ButtonIcon(Icons.Default.Refresh) },
                    onClick = onRefresh,
                    enabled = !state.refreshing,
                    modifier = Modifier.focusRequester(refreshFocus),
                )
            }
        }
        if (status is RequestStatus.Partial && status.item != null) {
            item("play") {
                ExpandablePlayButton(
                    title = playLabelText(playLabel(state.playback)),
                    icon = { ButtonIcon(Icons.Default.PlayArrow) },
                    onClick = onPlay,
                )
            }
        }
        // Owned titles get the Jellyfin page's eye and heart, on the movie or the whole series
        state.playback?.title?.takeIf { owned }?.let { title ->
            item("watched") {
                ExpandableFaButton(
                    title = if (title.played) R.string.mark_unwatched else R.string.mark_watched,
                    iconStringRes = if (title.played) R.string.fa_eye else R.string.fa_eye_slash,
                    active = title.played,
                    onClick = onToggleWatched,
                )
            }
            item("favorite") {
                ExpandableFaButton(
                    title = if (title.favorite) R.string.remove_favorite else R.string.add_favorite,
                    iconStringRes = R.string.fa_heart,
                    iconColor = if (title.favorite) JvColors.Heart else Color.Unspecified,
                    onClick = onToggleFavorite,
                )
            }
        }
        details?.trailerUrl?.let { url ->
            item("trailer") {
                ExpandableFaButton(
                    title = R.string.tmdb_trailer,
                    iconStringRes = R.string.fa_film,
                    onClick = { onTrailerClick(url) },
                )
            }
        }
        if (details != null && rateFromTv) {
            item("rate") { RateButton(item = item, imdbId = details.imdbId) }
        }
        if (details != null) {
            item("more") {
                TmdbMoreButton(
                    details = details,
                    owned = owned,
                    onOpenJellyfinPage = onOpenJellyfinPage,
                    onBrowse = onBrowse,
                )
            }
        }
    }
    if (showFailure) {
        val failure = status as? RequestStatus.DownloadFailed
        ScrollableDialog(onDismissRequest = { showFailure = false }) {
            item {
                Text(
                    stringResource(R.string.tmdb_download_failed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item {
                Text(
                    failure?.reason ?: stringResource(R.string.tmdb_download_failed_no_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item {
                Button(onClick = { showFailure = false }) { Text(stringResource(R.string.tmdb_close)) }
            }
        }
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector) = Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))

@Composable
private fun BoxScope.PrimaryIcon(
    status: RequestStatus?,
    sending: Boolean,
) {
    if (status == null || sending) {
        CircularProgress(Modifier.size(20.dp))
        return
    }
    when (status) {
        RequestStatus.NotConfigured -> ButtonIcon(Icons.Default.Settings)

        RequestStatus.Unavailable -> ButtonIcon(Icons.Default.Info)

        RequestStatus.NotRequested,
        is RequestStatus.Partial,
        -> ButtonIcon(Icons.Default.Add)

        RequestStatus.Requested,
        RequestStatus.ReadySoon,
        -> FaButtonIcon(R.string.fa_clock)

        is RequestStatus.Downloading -> FaButtonIcon(R.string.fa_download)

        is RequestStatus.Available -> ButtonIcon(Icons.Default.PlayArrow)

        is RequestStatus.Error -> ButtonIcon(Icons.Default.Refresh)

        is RequestStatus.DownloadFailed -> ButtonIcon(Icons.Default.Warning)
    }
}

private fun RequestStatus.isClickable(): Boolean =
    when (this) {
        RequestStatus.NotConfigured,
        RequestStatus.NotRequested,
        is RequestStatus.Available,
        is RequestStatus.Partial,
        is RequestStatus.Error,
        is RequestStatus.DownloadFailed,
        -> true

        RequestStatus.Unavailable,
        RequestStatus.Requested,
        is RequestStatus.Downloading,
        RequestStatus.ReadySoon,
        -> false
    }

@Composable
private fun playLabelText(label: PlayLabel): String =
    when (label) {
        PlayLabel.Watch -> stringResource(R.string.tmdb_watch)
        PlayLabel.Resume -> stringResource(R.string.tmdb_resume)
        is PlayLabel.WatchEpisode -> stringResource(R.string.tmdb_watch_episode, label.season, label.episode)
        is PlayLabel.ResumeEpisode -> stringResource(R.string.tmdb_resume_episode, label.season, label.episode)
    }

@Composable
private fun primaryLabel(
    status: RequestStatus?,
    mediaType: TmdbMediaType,
    playback: OwnedPlayback?,
): String =
    when (status) {
        null -> {
            stringResource(R.string.loading)
        }

        RequestStatus.NotConfigured -> {
            stringResource(if (mediaType == TmdbMediaType.MOVIE) R.string.tmdb_configure_radarr else R.string.tmdb_configure_sonarr)
        }

        RequestStatus.Unavailable -> {
            stringResource(R.string.tmdb_not_in_sonarr)
        }

        RequestStatus.NotRequested -> {
            stringResource(R.string.tmdb_request)
        }

        RequestStatus.Requested -> {
            stringResource(R.string.tmdb_requested)
        }

        is RequestStatus.Downloading -> {
            stringResource(R.string.tmdb_downloading, status.percent)
        }

        RequestStatus.ReadySoon -> {
            stringResource(R.string.tmdb_ready_soon)
        }

        is RequestStatus.Available -> {
            playLabelText(playLabel(playback))
        }

        is RequestStatus.Partial -> {
            stringResource(R.string.tmdb_partial, status.availableSeasons, status.totalSeasons)
        }

        is RequestStatus.Error -> {
            stringResource(R.string.tmdb_retry)
        }

        is RequestStatus.DownloadFailed -> {
            stringResource(R.string.tmdb_download_failed)
        }
    }
