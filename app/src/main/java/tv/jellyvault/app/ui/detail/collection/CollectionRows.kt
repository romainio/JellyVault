package tv.jellyvault.app.ui.detail.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jellyfin.sdk.model.api.BaseItemKind
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.HomeRowViewOptions
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.AspectRatio
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.data.RowColumn
import tv.jellyvault.app.ui.main.HomePageContent
import tv.jellyvault.app.ui.rememberPosition
import tv.jellyvault.app.util.HomeRowLoadingState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionRows(
    preferences: UserPreferences,
    state: CollectionState,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocusPosition: (RowColumn) -> Unit = {},
) {
    var position by rememberPosition(0, 0)

    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
        ) {
            val cardViewOptions = state.viewOptions.cardViewOptions
            val homeRows =
                remember(state.separateItems, cardViewOptions) {
                    state.separateItems.map { (type, row) ->
                        if (row is HomeRowLoadingState.Success) {
                            // TODO not great to do this in the UI
                            val viewOptions =
                                when (type) {
                                    BaseItemKind.EPISODE -> {
                                        HomeRowViewOptions(
                                            heightDp = Cards.HEIGHT_EPISODE,
                                            episodeAspectRatio = AspectRatio.WIDE,
                                            showTitles = cardViewOptions.showTitles,
                                            useSeries = false,
                                        )
                                    }

                                    BaseItemKind.MUSIC_ARTIST,
                                    BaseItemKind.MUSIC_ALBUM,
                                    BaseItemKind.PLAYLIST,
                                    BaseItemKind.AUDIO,
                                    -> {
                                        HomeRowViewOptions(
                                            heightDp = Cards.HEIGHT_EPISODE,
                                            aspectRatio = AspectRatio.SQUARE,
                                            showTitles = cardViewOptions.showTitles,
                                        )
                                    }

                                    BaseItemKind.VIDEO,
                                    BaseItemKind.MUSIC_VIDEO,
                                    -> {
                                        HomeRowViewOptions(
                                            heightDp = Cards.HEIGHT_EPISODE,
                                            aspectRatio = AspectRatio.WIDE,
                                            showTitles = cardViewOptions.showTitles,
                                        )
                                    }

                                    else -> {
                                        HomeRowViewOptions(
                                            showTitles = cardViewOptions.showTitles,
                                        )
                                    }
                                }

                            row.copy(viewOptions = viewOptions)
                        } else {
                            row
                        }
                    }
                }
            HomePageContent(
                homeRows = homeRows,
                position = position,
                onFocusPosition = { newPosition ->
                    position = newPosition
                    onFocusPosition.invoke(newPosition)
                },
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                showClock = false,
                onUpdateBackdrop = {},
                headerComposable = {},
                takeFocus = false,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                showViewMore = false,
                modifier = Modifier,
            )
        }
    }
}
