package tv.jellyvault.app.ui.detail.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.jellyfin.sdk.model.api.ItemSortBy
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.cards.GridCard
import tv.jellyvault.app.ui.data.RowColumn
import tv.jellyvault.app.ui.detail.CardGrid
import tv.jellyvault.app.ui.playback.scale
import tv.jellyvault.app.ui.util.ScrollToTopBringIntoViewSpec

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionMixedGrid(
    preferences: UserPreferences,
    state: CollectionState,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    letterPosition: suspend (Char) -> Int,
    modifier: Modifier = Modifier,
    onFocusPosition: (RowColumn) -> Unit = {},
) {
    val gridFocusRequester = remember { FocusRequester() }

    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            val density = LocalDensity.current

            val cardViewOptions = state.viewOptions.cardViewOptions
            CardGrid(
                pager = state.items,
                onClickItem = { index: Int, item: BaseItem -> onClickItem.invoke(RowColumn(0, index), item) },
                onLongClickItem = { index: Int, item: BaseItem -> onLongClickItem.invoke(RowColumn(0, index), item) },
                onClickPlay = { index: Int, item: BaseItem -> onClickPlay.invoke(RowColumn(0, index), item) },
                letterPosition = letterPosition,
                gridFocusRequester = gridFocusRequester,
                showJumpButtons = false, // TODO add preference
                showLetterButtons = state.sortAndDirection.sort == ItemSortBy.SORT_NAME,
                modifier =
                    Modifier
                        .fillMaxSize(),
                initialPosition = 0,
                positionCallback = { _, newPosition ->
                    onFocusPosition.invoke(RowColumn(0, newPosition))
                },
                cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = cardViewOptions.contentScale.scale,
                        imageAspectRatio = cardViewOptions.aspectRatio.ratio,
                        imageType = cardViewOptions.imageType,
                        showTitle = cardViewOptions.showTitles,
                        fillWidth = widthPx,
                        modifier = mod,
                    )
                },
                columns = cardViewOptions.columns,
                spacing = cardViewOptions.spacing.dp,
                bringIntoViewSpec =
                    remember(cardViewOptions, density) {
                        val spacingPx = with(density) { cardViewOptions.spacing.dp.toPx() }
                        if (cardViewOptions.showDetails) {
                            ScrollToTopBringIntoViewSpec(spacingPx)
                        } else {
                            defaultBringIntoViewSpec
                        }
                    },
            )
        }
    }
}
