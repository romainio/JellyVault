package tv.jellyvault.app.catalog.taste

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import tv.jellyvault.app.catalog.account.TmdbAccountRepository
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.ServerRepository
import java.util.UUID
import javax.inject.Inject

/**
 * What the current user watched and liked, for recommendations
 */
interface TasteSource {
    /** The signed-in user, the recommendations are theirs alone */
    val userId: UUID?

    /** Changes when the taste data behind [history] changes (e.g. new IMDb ratings), so caches recompute */
    fun version(): Long = 0

    /** Empty when there is no signed-in user or nothing was watched */
    suspend fun history(mediaType: TmdbMediaType): TasteHistory
}

class JellyfinTasteSource
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val account: TmdbAccountRepository,
    ) : TasteSource {
        override val userId: UUID? get() = serverRepository.currentUser?.id

        override fun version(): Long = account.version()

        override suspend fun history(mediaType: TmdbMediaType): TasteHistory {
            val userId = serverRepository.currentUser?.id ?: return TasteHistory(emptyList(), emptySet())
            val jellyfin = if (mediaType == TmdbMediaType.MOVIE) movieHistory(userId) else seriesHistory(userId)
            // IMDb ratings, copied to the linked TMDB account
            return jellyfin.withRatings(account.snapshot().ratings, mediaType)
        }

        private suspend fun movieHistory(userId: UUID): TasteHistory {
            val played = query(userId, BaseItemKind.MOVIE, isPlayed = true).mapNotNull { it.toPlayed(TmdbMediaType.MOVIE) }
            val favorites = query(userId, BaseItemKind.MOVIE, isFavorite = true).mapNotNull { it.toPlayed(TmdbMediaType.MOVIE) }
            return build(played, favorites)
        }

        private suspend fun seriesHistory(userId: UUID): TasteHistory {
            // A series has no played date of its own: read it from the latest episodes played
            val episodes = query(userId, BaseItemKind.EPISODE, isPlayed = true, limit = EPISODE_LIMIT)
            val seriesIds = episodes.mapNotNull { it.seriesId }.distinct()
            val seriesById =
                if (seriesIds.isEmpty()) {
                    emptyMap()
                } else {
                    api.itemsApi
                        .getItems(userId = userId, ids = seriesIds, fields = listOf(ItemFields.PROVIDER_IDS))
                        .content.items
                        .associateBy { it.id }
                }
            val played = seriesIds.mapNotNull { seriesById[it]?.toPlayed(TmdbMediaType.TV) }
            val favorites = query(userId, BaseItemKind.SERIES, isFavorite = true).mapNotNull { it.toPlayed(TmdbMediaType.TV) }
            return build(played, favorites)
        }

        private fun build(
            played: List<PlayedTitle>,
            favorites: List<PlayedTitle>,
        ) = buildTasteHistory(
            playedNewestFirst = played,
            favorites = favorites.map { it.key }.toSet(),
            maxSeeds = MAX_SEEDS,
            favoriteTitles = favorites.associate { it.key to it.title },
        )

        private suspend fun query(
            userId: UUID,
            kind: BaseItemKind,
            isPlayed: Boolean? = null,
            isFavorite: Boolean? = null,
            limit: Int = TITLE_LIMIT,
        ): List<BaseItemDto> =
            api.itemsApi
                .getItems(
                    userId = userId,
                    recursive = true,
                    includeItemTypes = listOf(kind),
                    isPlayed = isPlayed,
                    isFavorite = isFavorite,
                    sortBy = listOf(ItemSortBy.DATE_PLAYED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = listOf(ItemFields.PROVIDER_IDS),
                    enableImages = false,
                    limit = limit,
                ).content.items

        private fun BaseItemDto.toPlayed(type: TmdbMediaType): PlayedTitle? {
            val tmdbId = LibraryIndex.parseTmdbId(providerIds) ?: return null
            return PlayedTitle(TmdbKey(type, tmdbId), name.orEmpty())
        }

        companion object {
            const val MAX_SEEDS = MAX_TASTE_SEEDS
            const val TITLE_LIMIT = 200
            const val EPISODE_LIMIT = 300
        }
    }
