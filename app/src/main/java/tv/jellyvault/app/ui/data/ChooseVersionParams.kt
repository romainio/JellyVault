package tv.jellyvault.app.ui.data

import org.jellyfin.sdk.model.api.MediaSourceInfo
import tv.jellyvault.app.data.ChosenStreams

/**
 * Necessary details & action for choosing a version of some content
 */
data class ChooseVersionParams(
    val chosenStreams: ChosenStreams?,
    val mediaSources: List<MediaSourceInfo>,
    val onChooseVersion: (MediaSourceInfo) -> Unit,
)
