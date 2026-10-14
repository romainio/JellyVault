@file:UseSerializers(UUIDSerializer::class)

package tv.jellyvault.app.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.arr.ArrService
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.CollectionFolderFilter
import tv.jellyvault.app.data.model.GetItemsFilter
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.preferences.PlayerBackend
import tv.jellyvault.app.ui.components.ViewOptions
import tv.jellyvault.app.ui.data.SortAndDirection
import tv.jellyvault.app.ui.detail.series.SeasonEpisodeIds
import tv.jellyvault.app.ui.preferences.PreferenceScreenOption
import tv.jellyvault.app.ui.util.StringProvider
import tv.jellyvault.app.util.RequestHandler
import java.util.UUID

/**
 * Represents a page in the app
 *
 * @param fullScreen whether the page should be full page aka not include the nav drawer
 */
@Serializable
sealed class Destination(
    val fullScreen: Boolean = false,
) : NavKey {
    @Serializable
    data class Home(
        val id: Long = 0L,
    ) : Destination()

    @Serializable
    data object HomeSettings : Destination(true)

    @Serializable
    data class Settings(
        val screen: PreferenceScreenOption,
    ) : Destination(true)

    @Serializable
    data class SubtitleSettings(
        val hdr: Boolean,
    ) : Destination(true)

    @Serializable
    data class Search(
        val query: String = "",
    ) : Destination()

    data object UserAppPreferences : Destination(true)

    @Serializable
    data class SeriesOverview(
        val itemId: UUID,
        val type: BaseItemKind,
        val seasonEpisode: SeasonEpisodeIds? = null,
    ) : Destination() {
        override fun toString(): String = "SeriesOverview(itemId=$itemId, type=$type, seasonEpisode=$seasonEpisode)"
    }

    @Serializable
    data class MediaItem(
        val itemId: UUID,
        val type: BaseItemKind,
        val collectionType: CollectionType? = null,
        val initialSongId: UUID? = null,
    ) : Destination() {
        constructor(item: BaseItem) : this(item.id, item.type, item.data.collectionType)
    }

    @Serializable
    data class Recordings(
        val itemId: UUID,
    ) : Destination()

    @Serializable
    data class Playback(
        val itemId: UUID,
        val positionMs: Long,
        val forceTranscoding: Boolean = false,
        val backend: PlayerBackend? = null,
        val shuffle: Boolean = false,
    ) : Destination(true) {
        constructor(item: BaseItem) : this(item.id, item.resumeMs)
    }

    @Serializable
    data class PlaybackList(
        val itemId: UUID,
        val filter: GetItemsFilter = GetItemsFilter(),
        val startIndex: Int? = null,
        val shuffle: Boolean = false,
        val recursive: Boolean = false,
        val sortAndDirection: SortAndDirection? = null,
    ) : Destination(true) {
        override fun toString(): String = "PlaybackList(itemId=$itemId)"
    }

    @Serializable
    data class FilteredCollection(
        val itemId: UUID,
        val parentType: BaseItemKind,
        val filter: CollectionFolderFilter,
        val recursive: Boolean,
        val collectionType: CollectionType,
    ) : Destination(false)

    @Serializable
    data class ItemGrid<T>(
        val title: StringProvider,
        @Contextual val request: T,
        val requestHandler: RequestHandler<T>,
        val initialPosition: Int = 0,
        val viewOptions: ViewOptions = ViewOptions(),
    ) : Destination(false)

    @Serializable
    data class MoreHomeRow(
        val title: StringProvider,
        val config: HomeRowConfig,
        val initialPosition: Int,
    ) : Destination(false)

    @Serializable
    data class Slideshow(
        val parentId: UUID,
        val index: Int,
        val filter: CollectionFolderFilter,
        val sortAndDirection: SortAndDirection,
        val recursive: Boolean,
        val startSlideshow: Boolean,
    ) : Destination(true)

    @Serializable
    data class ArrSettings(
        val service: ArrService,
    ) : Destination(true)

    @Serializable
    data object TmdbAccount : Destination(true)

    @Serializable
    data class TmdbBrowse(
        val target: BrowseTarget,
    ) : Destination(false)

    @Serializable
    data class TmdbDetail(
        val item: TmdbItem,
    ) : Destination(false)

    @Serializable
    data object Favorites : Destination(false)

    @Serializable
    data object MyLibrary : Destination(false)

    @Serializable
    data object NowPlaying : Destination(true)

    @Serializable
    data object UpdateApp : Destination(true)

    @Serializable
    data object License : Destination(true)

    @Serializable
    data object Debug : Destination(true)
}
