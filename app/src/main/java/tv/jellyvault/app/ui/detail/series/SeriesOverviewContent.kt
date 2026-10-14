package tv.jellyvault.app.ui.detail.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PersonKind
import tv.jellyvault.app.R
import tv.jellyvault.app.data.ChosenStreams
import tv.jellyvault.app.data.ExtrasItem
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.Person
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.AspectRatios
import tv.jellyvault.app.ui.cards.BannerCard
import tv.jellyvault.app.ui.cards.ExtrasRow
import tv.jellyvault.app.ui.cards.PersonRow
import tv.jellyvault.app.ui.components.ErrorMessage
import tv.jellyvault.app.ui.components.HeaderUtils
import tv.jellyvault.app.ui.components.LoadingPage
import tv.jellyvault.app.ui.components.TabDetails
import tv.jellyvault.app.ui.components.TabRow
import tv.jellyvault.app.ui.components.TitleOrLogo
import tv.jellyvault.app.ui.formatSeasonNumber
import tv.jellyvault.app.ui.ifElse
import tv.jellyvault.app.ui.logTab
import tv.jellyvault.app.ui.nav.BackdropGeometry
import tv.jellyvault.app.ui.nav.DimBackdrop
import tv.jellyvault.app.ui.playback.isPlayKeyUp
import tv.jellyvault.app.ui.rememberInt
import tv.jellyvault.app.ui.tryRequestFocus
import tv.jellyvault.app.ui.util.StringStringProvider
import tv.jellyvault.app.ui.util.rememberDelayedNestedScroll
import kotlin.time.Duration

@Composable
fun SeriesOverviewContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: EpisodeList,
    seasonExtras: List<ExtrasItem>,
    chosenStreams: ChosenStreams?,
    peopleInEpisode: List<Person>,
    position: SeriesOverviewPosition,
    firstItemFocusRequester: FocusRequester,
    episodeRowFocusRequester: FocusRequester,
    castCrewRowFocusRequester: FocusRequester,
    guestStarRowFocusRequester: FocusRequester,
    extrasRowFocusRequester: FocusRequester,
    onChangeSeason: (Int) -> Unit,
    onFocusEpisode: (Int) -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
    playOnClick: (Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    overviewOnClick: () -> Unit,
    personOnClick: (Person) -> Unit,
    canDelete: (BaseItem) -> Boolean,
    onConfirmDelete: (BaseItem) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    onChooseVersion: (BaseItem, MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var selectedTabIndex by rememberSaveable(position.seasonTabIndex) { mutableIntStateOf(position.seasonTabIndex) }
    LaunchedEffect(selectedTabIndex) {
        logTab("series_overview", selectedTabIndex)
    }
    val tabRowFocusRequester = remember { FocusRequester() }

    val focusedEpisode =
        (episodes as? EpisodeList.Success)?.episodes?.getOrNull(position.episodeRowIndex)
    var pageHasFocus by remember { mutableStateOf(false) }
    var cardRowHasFocus by remember { mutableStateOf(false) }
    val dimming by animateFloatAsState(if (pageHasFocus && !cardRowHasFocus) .4f else 1f)

    val scrollState = rememberScrollState()
    // Cast rows scrolled over the image: darken it, like the other title pages
    val scrolledToRows by remember { derivedStateOf { scrollState.value > SCROLLED_TO_ROWS_PX } }
    DimBackdrop(if (scrolledToRows) BackdropGeometry.DIM_ROWS else 0f)
    val scrollConnection = rememberDelayedNestedScroll()
    var requestFocusAfterSeason by remember { mutableStateOf(false) }

    val tabFocusRequesters = remember(seasons) { List(seasons.size) { FocusRequester() } }
    val contentFocusRequesters = remember(seasons) { List(seasons.size) { FocusRequester() } }
    val tabs =
        seasons.mapIndexed { index, season ->
            TabDetails(
                title =
                    StringStringProvider(
                        season?.name
                            ?: season?.data?.indexNumber?.let { formatSeasonNumber(it) }
                            ?: "",
                    ),
                tabFocusRequester = tabFocusRequesters[index],
                contentFocusRequester = contentFocusRequesters[index],
            )
        }

    val currentOnChangeSeason by rememberUpdatedState(onChangeSeason)

    Box(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp)
                    .focusGroup()
                    .nestedScroll(scrollConnection)
                    .verticalScroll(scrollState)
                    .onFocusChanged { pageHasFocus = it.hasFocus },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .focusGroup()
                        .bringIntoViewRequester(bringIntoViewRequester),
            ) {
                val paddingValues =
                    remember(preferences.appPreferences.interfacePreferences.showClock) {
                        if (preferences.appPreferences.interfacePreferences.showClock) {
                            PaddingValues(start = 0.dp, end = 184.dp)
                        } else {
                            PaddingValues(start = 0.dp, end = 16.dp)
                        }
                    }
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    tabs = tabs,
                    onClick =
                        remember {
                            {
                                selectedTabIndex = it
                                currentOnChangeSeason(it)
                                requestFocusAfterSeason = true
                            }
                        },
                    modifier =
                        Modifier
                            .focusRequester(tabRowFocusRequester)
                            .padding(paddingValues)
                            .padding(bottom = 4.dp)
                            .fillMaxWidth(),
                )
                TitleOrLogo(
                    item = series,
                    showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
                FocusedEpisodeHeader(
                    preferences = preferences,
                    ep = focusedEpisode,
                    chosenStreams = chosenStreams,
                    overviewOnClick = overviewOnClick,
                    overviewOnFocus = {
                        if (it.isFocused) {
                            scope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(.6f),
                )

//                key(position.seasonTabIndex) {
                when (val eps = episodes) {
                    EpisodeList.Loading -> {
                        LoadingPage()
                    }

                    is EpisodeList.Error -> {
                        ErrorMessage(eps.message, eps.exception)
                    }

                    is EpisodeList.Success -> {
                        if (requestFocusAfterSeason) {
                            // Changing seasons, so move focus once the new episodes are loaded
                            LaunchedEffect(Unit) {
                                firstItemFocusRequester.tryRequestFocus()
                                requestFocusAfterSeason = false
                            }
                        }
                        val state = rememberLazyListState(position.episodeRowIndex)
                        var epPosition by rememberInt(position.episodeRowIndex)
                        LazyRow(
                            state = state,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier =
                                Modifier
                                    .focusRestorer(firstItemFocusRequester)
//                                    .focusRequester(episodeRowFocusRequester)
                                    .onFocusChanged {
                                        cardRowHasFocus = it.hasFocus
                                    },
                        ) {
                            itemsIndexed(eps.episodes) { episodeIndex, episode ->
                                val interactionSource = remember { MutableInteractionSource() }
                                if (interactionSource.collectIsFocusedAsState().value) {
                                    onFocusEpisode.invoke(episodeIndex)
                                }
                                BannerCard(
                                    name = episode?.name,
                                    item = episode,
                                    aspectRatio =
                                        episode
                                            ?.aspectRatio
                                            ?.coerceAtLeast(AspectRatios.FOUR_THREE)
                                            ?: (AspectRatios.WIDE),
                                    cornerText = episode?.ui?.episodeCornerText,
                                    played = episode?.data?.userData?.played ?: false,
                                    playPercent =
                                        episode?.data?.userData?.playedPercentage
                                            ?: 0.0,
                                    onClick = {
                                        epPosition = episodeIndex
                                        if (episode != null) onClick.invoke(episode)
                                    },
                                    onLongClick = {
                                        epPosition = episodeIndex
                                        if (episode != null) onLongClick.invoke(episode)
                                    },
                                    modifier =
                                        Modifier
                                            .ifElse(
                                                episodeIndex == position.episodeRowIndex,
                                                Modifier
                                                    .focusRequester(firstItemFocusRequester),
                                            ).ifElse(
                                                episodeIndex == epPosition,
                                                Modifier.focusRequester(episodeRowFocusRequester),
                                            ).background(
                                                if (episodeIndex != position.episodeRowIndex) {
                                                    Color.Black
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                            ).graphicsLayer {
                                                alpha =
                                                    if (episodeIndex != position.episodeRowIndex) {
                                                        dimming
                                                    } else {
                                                        1f
                                                    }
                                            }.onFocusChanged {
                                                if (it.isFocused) {
                                                    scope.launch {
                                                        bringIntoViewRequester.bringIntoView()
                                                    }
                                                }
                                            }.onKeyEvent {
                                                if (episode != null && isPlayKeyUp(it)) {
                                                    onClick.invoke(episode)
                                                    return@onKeyEvent true
                                                }
                                                return@onKeyEvent false
                                            },
                                    interactionSource = interactionSource,
                                    cardHeight = 120.dp,
                                    useSeriesForPrimary = false,
                                )
                            }
                        }
                    }
//                    }
                }

                focusedEpisode?.let { ep ->
                    FocusedEpisodeFooter(
                        preferences = preferences,
                        ep = ep,
                        chosenStreams = chosenStreams,
                        playOnClick = playOnClick,
                        moreOnClick = moreOnClick,
                        watchOnClick = {
                            watchOnClick.invoke()
                            episodeRowFocusRequester.tryRequestFocus()
                        },
                        favoriteOnClick = favoriteOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                scope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        canDelete = canDelete.invoke(ep),
                        onConfirmDelete = { onConfirmDelete.invoke(ep) },
                        onChooseVersion = { onChooseVersion.invoke(ep, it) },
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(),
                    )
                }
            }

            val (guestStars, castAndCrew) =
                remember(peopleInEpisode) {
                    peopleInEpisode.partition { it.type == PersonKind.GUEST_STAR }
                }

            AnimatedVisibility(
                visible = peopleInEpisode.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (castAndCrew.isNotEmpty()) {
                        PersonRow(
                            title = R.string.cast_and_crew,
                            people = castAndCrew,
                            onClick = personOnClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(castCrewRowFocusRequester),
                        )
                    }
                    if (guestStars.isNotEmpty()) {
                        PersonRow(
                            title = R.string.guest_stars,
                            people = guestStars,
                            onClick = personOnClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(guestStarRowFocusRequester),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = seasonExtras.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ExtrasRow(
                    extras = seasonExtras,
                    onClickItem = onClickExtra,
                    onLongClickItem = { _, _ -> },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(extrasRowFocusRequester),
                )
            }
        }
    }
}

private const val SCROLLED_TO_ROWS_PX = 24
