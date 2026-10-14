package tv.jellyvault.app.ui.playback.overlay

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.PlaylistItem
import tv.jellyvault.app.ui.cards.SeasonCard
import tv.jellyvault.app.ui.components.HiddenFocusBox
import tv.jellyvault.app.ui.ifElse
import tv.jellyvault.app.ui.playback.ControllerViewState
import tv.jellyvault.app.ui.tryRequestFocus

@Composable
fun QueueRowOverlay(
    queue: List<PlaylistItem>,
    controllerViewState: ControllerViewState,
    nextState: OverlayViewState,
    onChangeState: (OverlayViewState) -> Unit,
    onClickPlaylist: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val hiddenFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        HiddenFocusBox(hiddenFocusRequester) {
            onChangeState.invoke(nextState)
        }
        Text(
            text = stringResource(R.string.queue),
            style = MaterialTheme.typography.titleLarge,
        )
        LazyRow(
            contentPadding =
                PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(focusRequester)
                    .onFocusChanged {
                        if (it.hasFocus) {
                            controllerViewState.pulseControls()
                        }
                    },
        ) {
            itemsIndexed(queue) { index, item ->
                val interactionSource =
                    remember { MutableInteractionSource() }
                val isFocused =
                    interactionSource.collectIsFocusedAsState().value
                LaunchedEffect(isFocused) {
                    if (isFocused) controllerViewState.pulseControls()
                }
                SeasonCard(
                    item = item.item,
                    onClick = {
                        onClickPlaylist.invoke(item.item)
                        controllerViewState.hideControls()
                    },
                    onLongClick = {},
                    imageHeight = 140.dp,
                    interactionSource = interactionSource,
                    modifier =
                        Modifier.ifElse(
                            index == 0,
                            Modifier.focusRequester(focusRequester),
                        ),
                )
            }
        }
    }
}
