package tv.jellyvault.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.HomeRowViewOptions
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.SlimItemFields
import tv.jellyvault.app.ui.data.RowColumn
import tv.jellyvault.app.util.GetItemsRequestHandler
import tv.jellyvault.app.util.GetNextUpRequestHandler
import tv.jellyvault.app.util.GetResumeItemsRequestHandler
import java.time.LocalDateTime
import java.util.UUID

private fun getRecommendedRows(
    parentId: UUID,
    enableRewatching: Boolean,
) = listOf(
    RecommendedRow(
        title = R.string.continue_watching,
        handler = GetResumeItemsRequestHandler,
        request =
            GetResumeItemsRequest(
                parentId = parentId,
                fields = SlimItemFields,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                enableUserData = true,
                enableTotalRecordCount = false,
            ),
    ),
    RecommendedRow(
        title = R.string.next_up,
        handler = GetNextUpRequestHandler,
        request =
            GetNextUpRequest(
                fields = SlimItemFields,
                imageTypeLimit = 1,
                parentId = parentId,
                enableResumable = false,
                enableUserData = true,
                enableRewatching = enableRewatching,
            ),
    ),
    RecommendedRow(
        title = R.string.recently_released,
        handler = GetItemsRequestHandler,
        request =
            GetItemsRequest(
                parentId = parentId,
                fields = SlimItemFields,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                recursive = true,
                enableUserData = true,
                sortBy =
                    listOf(
                        ItemSortBy.PREMIERE_DATE,
                        ItemSortBy.SERIES_SORT_NAME,
                        ItemSortBy.AIRED_EPISODE_ORDER,
                    ),
                sortOrder =
                    listOf(
                        SortOrder.DESCENDING,
                        SortOrder.ASCENDING,
                        SortOrder.DESCENDING,
                    ),
                enableTotalRecordCount = false,
                maxPremiereDate = LocalDateTime.now(),
                isUnaired = false,
            ),
    ),
    RecommendedRow(
        title = R.string.recently_added,
        handler = GetItemsRequestHandler,
        request =
            GetItemsRequest(
                parentId = parentId,
                fields = SlimItemFields,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                recursive = true,
                enableUserData = true,
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                enableTotalRecordCount = false,
            ),
    ),
    RecommendedRow(
        title = R.string.top_unwatched,
        handler = GetItemsRequestHandler,
        request =
            GetItemsRequest(
                parentId = parentId,
                fields = SlimItemFields,
                includeItemTypes = listOf(BaseItemKind.SERIES),
                recursive = true,
                enableUserData = true,
                isPlayed = false,
                sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                sortOrder = listOf(SortOrder.DESCENDING),
                enableTotalRecordCount = false,
            ),
    ),
)

/**
 * The "recommended" tab of a TV show library
 */
@Composable
fun RecommendedTvShow(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedViewModel =
        hiltViewModel<RecommendedViewModel, RecommendedViewModel.Factory>(
            creationCallback = {
                it.create(
                    parentId = parentId,
                    suggestionsType = BaseItemKind.SERIES,
                    recommendedRows =
                        getRecommendedRows(
                            parentId,
                            preferences.appPreferences.homePagePreferences.enableRewatchingNextUp,
                        ),
                    viewOptions = HomeRowViewOptions(),
                )
            },
        ),
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        modifier = modifier,
    )
}
