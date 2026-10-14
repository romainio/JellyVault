@file:UseSerializers(UUIDSerializer::class)

package tv.jellyvault.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.CatalogMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.preferences.PrefContentScale
import tv.jellyvault.app.ui.AspectRatio
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.components.ViewOptionImageType
import tv.jellyvault.app.ui.data.SortAndDirection
import java.util.UUID

@Serializable
sealed interface HomeRowConfig {
    val viewOptions: HomeRowViewOptions

    fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig

    /**
     * Continue watching media that the user has started but not finished
     */
    @Serializable
    @SerialName("ContinueWatching")
    data class ContinueWatching(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ContinueWatching = this.copy(viewOptions = viewOptions)
    }

    /**
     * Next up row for next episodes in a series the user has started
     */
    @Serializable
    @SerialName("NextUp")
    data class NextUp(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): NextUp = this.copy(viewOptions = viewOptions)
    }

    /**
     * Combined [ContinueWatching] and [NextUp]
     */
    @Serializable
    @SerialName("ContinueWatchingCombined")
    data class ContinueWatchingCombined(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ContinueWatchingCombined = this.copy(viewOptions = viewOptions)
    }

    /**
     * Media recently added to a library
     */
    @Serializable
    @SerialName("RecentlyAdded")
    data class RecentlyAdded(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): RecentlyAdded = this.copy(viewOptions = viewOptions)
    }

    /**
     * Media recently released (premiere date) in a library
     */
    @Serializable
    @SerialName("RecentlyReleased")
    data class RecentlyReleased(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): RecentlyReleased = this.copy(viewOptions = viewOptions)
    }

    /**
     * Row of a genres in a library
     */
    @Serializable
    @SerialName("Genres")
    data class Genres(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.genreDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Genres = this.copy(viewOptions = viewOptions)
    }

    /**
     * Row of a studios in a library
     */
    @Serializable
    @SerialName("Studios")
    data class Studios(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.genreDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Studios = this.copy(viewOptions = viewOptions)
    }

    /**
     * Favorites for a specific type
     */
    @Serializable
    @SerialName("Favorite")
    data class Favorite(
        val kind: BaseItemKind,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Favorite = this.copy(viewOptions = viewOptions)
    }

    /**
     * Currently recording
     */
    @Serializable
    @SerialName("Recordings")
    data class Recordings(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Recordings = this.copy(viewOptions = viewOptions)
    }

    /**
     * Programs on now/recommended
     */
    @Serializable
    @SerialName("TvPrograms")
    data class TvPrograms(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.liveTvDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TvPrograms = this.copy(viewOptions = viewOptions)
    }

    /**
     * Live TV channels
     */
    @Serializable
    @SerialName("TvChannels")
    data class TvChannels(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.liveTvDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TvChannels = this.copy(viewOptions = viewOptions)
    }

    /**
     * Fetch suggestions from [tv.jellyvault.app.services.SuggestionService] for the given parent ID
     */
    @Serializable
    @SerialName("Suggestions")
    data class Suggestions(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Suggestions = this.copy(viewOptions = viewOptions)
    }

    /**
     * Fetch by parent ID such as a library, collection, or playlist with optional simple sorting
     */
    @Serializable
    @SerialName("ByParent")
    data class ByParent(
        val parentId: UUID,
        val recursive: Boolean = false,
        val sort: SortAndDirection? = null,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ByParent = this.copy(viewOptions = viewOptions)
    }

    /**
     * An arbitrary [GetItemsRequest] allowing to query for anything
     */
    @Serializable
    @SerialName("GetItems")
    data class GetItems(
        val name: String,
        val getItems: GetItemsRequest,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): GetItems = this.copy(viewOptions = viewOptions)
    }

    /**
     * Trending titles of the week from TMDB, owned titles are marked
     */
    @Serializable
    @SerialName("TmdbTrending")
    data class TmdbTrending(
        val mediaType: TmdbMediaType,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TmdbTrending = this.copy(viewOptions = viewOptions)
    }

    /**
     * Recommendations computed from what the user watched ("Recommended for you")
     */
    @Serializable
    @SerialName("TmdbForYou")
    data class TmdbForYou(
        val mediaType: TmdbMediaType,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TmdbForYou = this.copy(viewOptions = viewOptions)
    }

    /**
     * The [slot]-th "because you watched X" row; hidden when there is no such row
     */
    @Serializable
    @SerialName("TmdbBecause")
    data class TmdbBecause(
        val mediaType: TmdbMediaType,
        val slot: Int = 0,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TmdbBecause = this.copy(viewOptions = viewOptions)
    }

    /**
     * "Ta liste": the watchlist of the linked TMDB account (the IMDb watchlist, copied by the sync)
     */
    @Serializable
    @SerialName("TmdbWatchlist")
    data class TmdbWatchlist(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TmdbWatchlist = this.copy(viewOptions = viewOptions)
    }

    /**
     * "Styles variés": expands on the home into the styles planned for the period (see ShelfSource)
     */
    @Serializable
    @SerialName("TmdbShelves")
    data class TmdbShelves(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TmdbShelves = this.copy(viewOptions = viewOptions)
    }

    /**
     * One planned style, only created when [TmdbShelves] expands (never saved)
     */
    @Serializable
    @SerialName("TmdbShelf")
    data class TmdbShelf(
        val shelfId: String,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TmdbShelf = this.copy(viewOptions = viewOptions)
    }

    /**
     * Popular, well-rated titles of a genre from TMDB, owned titles are marked
     */
    @Serializable
    @SerialName("TmdbGenre")
    data class TmdbGenre(
        val genre: CatalogGenre,
        val mediaType: CatalogMediaType = CatalogMediaType.BOTH,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): TmdbGenre = this.copy(viewOptions = viewOptions)
    }
}

/**
 * Root class for home page settings
 *
 * Contains the list of rows and a version
 */
@Serializable
@SerialName("HomePageSettings")
data class HomePageSettings(
    val rows: List<HomeRowConfig>,
    val version: Int,
) {
    companion object {
        val EMPTY = HomePageSettings(listOf(), SUPPORTED_HOME_PAGE_SETTINGS_VERSION)
    }
}

/**
 * This is the max version supported by this version of the app
 */
const val SUPPORTED_HOME_PAGE_SETTINGS_VERSION = 1

/**
 * View options for displaying a row
 *
 * Allows for changing things like height or aspect ratio
 */
@Serializable
data class HomeRowViewOptions(
    val heightDp: Int = Cards.HEIGHT_2X3_DP,
    val spacing: Int = 16,
    val contentScale: PrefContentScale = PrefContentScale.FILL,
    val aspectRatio: AspectRatio = AspectRatio.TALL,
    val imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    val showTitles: Boolean = false,
    val useSeries: Boolean = true,
    val episodeContentScale: PrefContentScale = PrefContentScale.FILL,
    val episodeAspectRatio: AspectRatio = AspectRatio.TALL,
    val episodeImageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
) {
    companion object {
        val genreDefault =
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.WIDE,
            )

        val liveTvDefault =
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_LIVE_TV,
                aspectRatio = AspectRatio.WIDE,
                contentScale = PrefContentScale.FIT,
            )
    }
}
