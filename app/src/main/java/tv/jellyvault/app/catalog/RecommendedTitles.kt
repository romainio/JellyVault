package tv.jellyvault.app.catalog

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.BaseItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "More like this" from TMDB: real recommendations, not only what is already in the library.
 * Titles the user owns are returned as library items, the others as TMDB titles.
 */
@Singleton
class RecommendedTitles
    @Inject
    constructor(
        private val tmdb: TmdbClient,
        private val catalog: CatalogRepository,
    ) {
        /**
         * Empty when TMDB is not configured, the title has no TMDB id or TMDB fails: the caller keeps its own fallback
         */
        suspend fun forTitle(
            mediaType: TmdbMediaType,
            providerIds: Map<String, String?>?,
        ): List<BaseItem> {
            if (!tmdb.isConfigured) return emptyList()
            val tmdbId = LibraryIndex.parseTmdbId(providerIds) ?: return emptyList()
            return try {
                val details = tmdb.details(mediaType, tmdbId)
                // TMDB "similar" is mostly unrelated titles: recommendations only, kept to the same kind of title
                catalog.cards(fittingRecommendations(details, details.recommendations, emptySet())).map { it.toBaseItem() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "No TMDB recommendations for %s", tmdbId)
                emptyList()
            }
        }
    }
