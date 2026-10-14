package tv.jellyvault.app.ui.data

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.services.MusicService
import tv.jellyvault.app.services.PlaylistCreator
import tv.jellyvault.app.ui.detail.PlaylistLoadingState
import tv.jellyvault.app.ui.detail.music.addToQueue
import tv.jellyvault.app.ui.launchDefault
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.showToast
import tv.jellyvault.app.util.ExceptionHandler
import java.util.UUID
import javax.inject.Inject

/**
 * A supplementary [ViewModel] for adding items to a server playlist
 * @see tv.jellyvault.app.ui.detail.PlaylistDialog
 */
@HiltViewModel
class AddPlaylistViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val playlistCreator: PlaylistCreator,
        private val musicService: MusicService,
    ) : ViewModel() {
        val playlistState = MutableStateFlow<PlaylistLoadingState>(PlaylistLoadingState.Pending)

        fun loadPlaylists(query: String = "") {
            viewModelScope.launchIO {
                this@AddPlaylistViewModel.playlistState.value = PlaylistLoadingState.Loading
                try {
                    val playlists = playlistCreator.getServerPlaylists(query, null)
                    this@AddPlaylistViewModel.playlistState.value =
                        PlaylistLoadingState.Success(playlists, query)
                } catch (ex: Exception) {
                    playlistState.value = PlaylistLoadingState.Error(ex)
                }
            }
        }

        fun addToPlaylist(
            playlistId: UUID,
            itemId: UUID,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                try {
                    playlistCreator.addToServerPlaylist(playlistId, itemId)
                    showToast(context, context.getString(R.string.success), Toast.LENGTH_SHORT)
                } catch (ex: Exception) {
                    Timber.e(ex, "Error adding %s to playlist %s", itemId, playlistId)
                    showToast(context, context.getString(R.string.jv_error_playlist_add), Toast.LENGTH_SHORT)
                }
            }
        }

        fun createPlaylistAndAddItem(
            playlistName: String,
            itemId: UUID,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                val playlistId = playlistCreator.createServerPlaylist(playlistName, listOf(itemId))
                if (playlistId == null) {
                    showToast(context, context.getString(R.string.jv_error_playlist_create), Toast.LENGTH_LONG)
                } else {
                    showToast(context, context.getString(R.string.success), Toast.LENGTH_SHORT)
                }
            }
        }

        fun addToQueue(item: BaseItem) {
            viewModelScope.launchDefault {
                addToQueue(api, musicService, item, -1)
            }
        }
    }
