package tv.jellyvault.app.ui.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import tv.jellyvault.app.preferences.PlaybackPreferences
import tv.jellyvault.app.preferences.skipBackOnResume
import tv.jellyvault.app.ui.seekBack

@OptIn(UnstableApi::class)
class MediaSessionPlayer(
    player: Player,
    private val playbackPreferences: PlaybackPreferences,
) : ForwardingSimpleBasePlayer(player) {
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        Timber.v("handleSetPlayWhenReady: playWhenReady=$playWhenReady")
        if (playWhenReady) {
            playbackPreferences.skipBackOnResume?.let {
                player.seekBack(it)
            }
        }
        return super.handleSetPlayWhenReady(playWhenReady)
    }
}
