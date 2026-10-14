package tv.jellyvault.app.ui.detail.collection

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tv.jellyvault.app.R
import tv.jellyvault.app.data.filter.DefaultFilterOptions
import tv.jellyvault.app.data.filter.FilterValueOption
import tv.jellyvault.app.data.filter.ItemFilterBy
import tv.jellyvault.app.data.model.GetItemsFilter
import tv.jellyvault.app.ui.components.ButtonStyle
import tv.jellyvault.app.ui.components.DeleteButton
import tv.jellyvault.app.ui.components.ExpandableFaButton
import tv.jellyvault.app.ui.components.ExpandablePlayButton
import tv.jellyvault.app.ui.components.FilterByButton
import tv.jellyvault.app.ui.components.SortByButton
import tv.jellyvault.app.ui.data.MovieSortOptions
import tv.jellyvault.app.ui.data.SortAndDirection
import tv.jellyvault.app.ui.theme.jv.JvColors
import kotlin.time.Duration

@Composable
fun CollectionButtons(
    state: CollectionState,
    onSortChange: (SortAndDirection) -> Unit,
    onClickPlayAll: (Boolean) -> Unit,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    onClickViewOptions: () -> Unit,
    favoriteOnClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    canDelete: Boolean,
    moreOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortOptions = MovieSortOptions
    val filterOptions = DefaultFilterOptions
    val firstFocus = remember { FocusRequester() }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .focusGroup()
                    .focusRestorer(firstFocus),
        ) {
            item {
                ExpandablePlayButton(
                    title = R.string.play,
                    resume = Duration.ZERO,
                    icon = Icons.Default.PlayArrow,
                    style = ButtonStyle.Primary,
                    onClick = { onClickPlayAll.invoke(false) },
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
            item {
                ExpandableFaButton(
                    title = R.string.shuffle,
                    iconStringRes = R.string.fa_shuffle,
                    onClick = { onClickPlayAll.invoke(true) },
                )
            }

            item("favorite") {
                val favorite = remember(state.collection) { state.collection?.favorite == true }
                ExpandableFaButton(
                    title = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                    iconStringRes = R.string.fa_heart,
                    onClick = favoriteOnClick,
                    iconColor = if (favorite) JvColors.Heart else Color.Unspecified,
                    modifier = Modifier,
                )
            }
            if (canDelete) {
                item("delete") {
                    DeleteButton(
                        title = state.collection?.title ?: "",
                        onConfirmDelete = onConfirmDelete,
                        modifier = Modifier,
                    )
                }
            }

            item {
                ExpandableFaButton(
                    title = R.string.view_options,
                    iconStringRes = R.string.fa_sliders,
                    onClick = onClickViewOptions,
                    modifier = Modifier,
                )
            }

            // More button
            item("more") {
                ExpandablePlayButton(
                    title = R.string.more,
                    resume = Duration.ZERO,
                    icon = Icons.Default.MoreVert,
                    onClick = { moreOnClick.invoke() },
                    modifier = Modifier,
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .focusGroup(),
        ) {
            item {
                SortByButton(
                    sortOptions = sortOptions,
                    current = state.sortAndDirection,
                    onSortChange = onSortChange,
                    modifier = Modifier,
                )
            }
            item {
                FilterByButton(
                    filterOptions = filterOptions,
                    current = state.itemFilter,
                    onFilterChange = onFilterChange,
                    getPossibleValues = getPossibleFilterValues,
                    modifier = Modifier,
                )
            }
        }
    }
}
