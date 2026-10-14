package tv.jellyvault.app.ui.detail.music

import androidx.compose.runtime.Stable
import org.jellyfin.sdk.model.api.LyricDto
import tv.jellyvault.app.data.model.AudioItem
import tv.jellyvault.app.services.BackdropResult
import tv.jellyvault.app.services.MusicServiceState

@Stable
data class NowPlayingState(
    val musicServiceState: MusicServiceState,
    val currentMediaItem: AudioItem? = null,
    val lyrics: LyricDto? = null,
    val currentLyricIndex: Int? = null,
    val visualizerPermissions: Boolean = false,
    val backdropResult: BackdropResult = BackdropResult.NONE,
) {
    val hasLyrics: Boolean get() = lyrics != null && lyrics.lyrics.isNotEmpty()
}
