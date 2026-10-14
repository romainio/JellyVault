package tv.jellyvault.app.ui.detail.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tv.jellyvault.app.data.ChosenStreams
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.components.EpisodeName
import tv.jellyvault.app.ui.components.HeaderUtils
import tv.jellyvault.app.ui.components.OverviewText
import tv.jellyvault.app.ui.components.QuickDetails
import tv.jellyvault.app.ui.components.VideoStreamDetails

@Composable
fun FocusedEpisodeHeader(
    preferences: UserPreferences,
    ep: BaseItem?,
    chosenStreams: ChosenStreams?,
    overviewOnClick: () -> Unit,
    overviewOnFocus: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dto = ep?.data
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        EpisodeName(dto, modifier = Modifier.padding(start = HeaderUtils.startPadding))

        ep?.ui?.quickDetails?.let {
            QuickDetails(
                it,
                ep.timeRemainingOrRuntime,
                Modifier.padding(start = HeaderUtils.startPadding),
            )
        }

        if (dto != null) {
            VideoStreamDetails(
                chosenStreams = chosenStreams,
                numberOfVersions = dto.mediaSourceCount ?: 0,
                modifier = Modifier.padding(start = HeaderUtils.startPadding),
            )
        }
        OverviewText(
            overview = dto?.overview ?: "",
            maxLines = 3,
            onClick = overviewOnClick,
            modifier = Modifier.onFocusChanged(overviewOnFocus),
        )
    }
}
