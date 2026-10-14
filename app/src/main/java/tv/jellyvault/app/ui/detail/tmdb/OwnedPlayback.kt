package tv.jellyvault.app.ui.detail.tmdb

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import tv.jellyvault.app.data.ChosenStreams
import tv.jellyvault.app.data.ItemPlaybackRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.services.UserPreferencesService
import java.util.UUID
import javax.inject.Inject

/**
 * What the TMDB page needs to play a title that is in the library: the movie, or the episode to watch next, and its streams
 */
data class OwnedPlayback(
    val item: BaseItem,
    val chosenStreams: ChosenStreams?,
    /** The movie or series itself with its user data, for the watched and favorite buttons */
    val title: BaseItem = item,
) {
    val isEpisode: Boolean get() = item.type == BaseItemKind.EPISODE
}

interface OwnedPlaybackSource {
    /** The item as the Jellyfin detail page loads it (media sources, user data) */
    suspend fun fullItem(id: UUID): BaseItem

    /** The next up episode, else the first one */
    suspend fun nextEpisode(seriesId: UUID): BaseItem?

    suspend fun chosenStreams(item: BaseItem): ChosenStreams?
}

class JellyfinOwnedPlaybackSource
    @Inject
    constructor(
        private val api: ApiClient,
        private val itemPlaybackRepository: ItemPlaybackRepository,
        private val userPreferencesService: UserPreferencesService,
    ) : OwnedPlaybackSource {
        override suspend fun fullItem(id: UUID): BaseItem = BaseItem(api.userLibraryApi.getItem(id).content)

        override suspend fun nextEpisode(seriesId: UUID): BaseItem? {
            val next =
                api.tvShowsApi
                    .getNextUp(seriesId = seriesId)
                    .content.items
                    .firstOrNull()
                    ?: api.tvShowsApi
                        .getEpisodes(seriesId, limit = 1)
                        .content.items
                        .firstOrNull()
            return next?.let { fullItem(it.id) }
        }

        override suspend fun chosenStreams(item: BaseItem): ChosenStreams? =
            itemPlaybackRepository.getSelectedTracks(item.id, item, userPreferencesService.getCurrent())
    }

class OwnedPlaybackLoader
    @Inject
    constructor(
        private val source: OwnedPlaybackSource,
    ) {
        /**
         * Null when nothing can be played or Jellyfin cannot be reached: the page then opens the Jellyfin page instead
         */
        suspend fun load(owned: BaseItem): OwnedPlayback? =
            try {
                if (owned.type == BaseItemKind.SERIES) {
                    source.nextEpisode(owned.id)?.let { OwnedPlayback(it, streamsOrNull(it), seriesOrListed(owned)) }
                } else {
                    val movie = source.fullItem(owned.id)
                    OwnedPlayback(movie, streamsOrNull(movie))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not load playback details")
                null
            }

        /** The series with its user data; the listed item still allows playing when this fails */
        private suspend fun seriesOrListed(owned: BaseItem): BaseItem =
            try {
                source.fullItem(owned.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not load the series")
                owned
            }

        private suspend fun streamsOrNull(item: BaseItem): ChosenStreams? =
            try {
                source.chosenStreams(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not load the chosen streams")
                null
            }
    }
