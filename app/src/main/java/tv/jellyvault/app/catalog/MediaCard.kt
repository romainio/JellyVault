package tv.jellyvault.app.catalog

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbLanguage
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.tmdbGenreName
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.ui.nav.Destination
import java.util.UUID

/**
 * An entry of a catalog row: either an item in the user's Jellyfin library or a TMDB-only title
 */
sealed interface MediaCard {
    val tmdb: TmdbItem?

    data class Owned(
        val item: BaseItem,
        override val tmdb: TmdbItem?,
    ) : MediaCard

    data class Remote(
        override val tmdb: TmdbItem,
        /** Rated by the user (IMDb ratings through TMDB): shown with the "seen" check */
        val seen: Boolean = false,
    ) : MediaCard
}

val TmdbItem.key: TmdbKey get() = TmdbKey(mediaType, tmdbId)

fun MediaCard.toBaseItem(): BaseItem =
    when (this) {
        is MediaCard.Owned -> item
        is MediaCard.Remote -> tmdb.toRemoteBaseItem(seen)
    }

/**
 * Wraps a TMDB title so the existing card/row UI can display it. The ID is synthetic and never sent to Jellyfin.
 */
fun TmdbItem.toRemoteBaseItem(
    seen: Boolean = false,
    language: TmdbLanguage = TmdbLanguage.current(),
): BaseItem =
    BaseItem(
        data =
            BaseItemDto(
                id = UUID.nameUUIDFromBytes("tmdb:${mediaType.path}:$tmdbId".toByteArray()),
                type = if (mediaType == TmdbMediaType.MOVIE) BaseItemKind.MOVIE else BaseItemKind.SERIES,
                name = title,
                overview = overview,
                productionYear = year,
                communityRating = voteAverage?.toFloat(),
                providerIds = mapOf("Tmdb" to tmdbId.toString()),
                genres = genreIds.mapNotNull { tmdbGenreName(it, mediaType, language) },
                userData = if (seen) seenUserData(tmdbId) else null,
            ),
        imageUrlOverride = posterUrl,
        backdropUrlOverride = backdropUrl,
        destinationOverride = Destination.TmdbDetail(this),
    )

private fun TmdbItem.seenUserData(tmdbId: Int) =
    UserItemDataDto(
        playbackPositionTicks = 0,
        playCount = 1,
        isFavorite = false,
        played = true,
        key = "tmdb:${mediaType.path}:$tmdbId",
        itemId = UUID.nameUUIDFromBytes("tmdb:${mediaType.path}:$tmdbId".toByteArray()),
    )
