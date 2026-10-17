package tv.jellyvault.app.catalog.taste

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import tv.jellyvault.app.catalog.CatalogFilter
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.MediaCard
import tv.jellyvault.app.catalog.key
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbLanguage
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BecauseCards(
    val seedTitle: String,
    val cards: List<MediaCard>,
)

/**
 * Films of the directors the user likes most, mixed, with their names for the row title
 */
data class DirectorCards(
    val directors: List<String>,
    val cards: List<MediaCard>,
)

/**
 * Personal recommendations from what the user watched: TMDB recommends titles after each recent or favorite
 * title, and the titles recommended most often rank first. Computed once per [TTL_MS] for all rows.
 */
@Singleton
class Recommender
    @Inject
    constructor(
        private val taste: TasteSource,
        private val tmdb: TmdbClient,
        private val catalog: CatalogRepository,
        private val affinityStore: TasteAffinityStore,
    ) {
        private class Computed(
            val at: Long,
            val history: TasteHistory,
            val results: List<SeedRecommendations>,
            val negative: List<SeedRecommendations> = emptyList(),
            val version: Long = 0,
            /** The catalog limits the recommendations were filtered with */
            val filter: CatalogFilter = CatalogFilter(),
        ) {
            /** Pour toi: wide but well rated, and pushed away from what the user disliked */
            fun forYou() = rankForYou(results, history.watched, FOR_YOU_SIZE, negative, FOR_YOU_MIN_RATING, FOR_YOU_MIN_VOTES)
        }

        private val mutex = Mutex()
        private val cache = HashMap<Triple<UUID?, TmdbMediaType, String>, Computed>()

        internal var now: () -> Long = System::currentTimeMillis

        /**
         * Forget what was computed, e.g. when the user changes
         */
        suspend fun reset() = mutex.withLock { cache.clear() }

        suspend fun forYou(mediaType: TmdbMediaType): List<MediaCard> {
            val computed = compute(mediaType)
            return catalog.cards(computed.forYou())
        }

        /**
         * Up to [maxRows] "because you watched X" rows, made of titles that are not in "Pour toi" either
         */
        suspend fun because(
            mediaType: TmdbMediaType,
            maxRows: Int = BECAUSE_ROWS,
        ): List<BecauseCards> {
            val computed = compute(mediaType)
            val shownInForYou = computed.forYou().map { it.key }.toSet()
            val rows =
                becauseRows(
                    computed.results.filter { it.seed.title.isNotBlank() },
                    computed.history.watched + shownInForYou,
                    maxRows,
                    BECAUSE_MIN_ITEMS,
                    BECAUSE_ROW_SIZE,
                )
            return rows.map { BecauseCards(it.seed.title, catalog.cards(it.items)) }
        }

        private suspend fun compute(mediaType: TmdbMediaType): Computed =
            mutex.withLock {
                // Keyed by user: a profile switch must never show the previous profile's taste; and by language, as the titles are
                val cacheKey = Triple(taste.userId, mediaType, TmdbLanguage.current().tag)
                val version = taste.version()
                // New IMDb ratings or another TMDB account: recompute at once
                val filter = CatalogFilter.current
                // New limits in Settings › Catalog too
                cache[cacheKey]?.takeIf { now() - it.at < TTL_MS && it.version == version && it.filter == filter }?.let { return it }
                val history = taste.history(mediaType)
                val semaphore = Semaphore(PARALLEL_CALLS)
                val (results, negative) =
                    coroutineScope {
                        val positive =
                            history.seeds.map { seed ->
                                async { semaphore.withPermit { recommendationsAfter(mediaType, seed) } }
                            }
                        val against =
                            history.negative.map { seed ->
                                async { semaphore.withPermit { recommendationsAfter(mediaType, seed) } }
                            }
                        positive.map { it.await() } to against.map { it.await() }
                    }
                val computed = Computed(now(), history, results, negative, version, filter)
                // Nothing to recommend (no history, TMDB down): try again next time instead of caching the emptiness
                if (results.any { it.items.isNotEmpty() }) {
                    cache[cacheKey] = computed
                    // The home plan reads this snapshot later, without waiting on the network
                    taste.userId?.let { user ->
                        val mine = cache.filterKeys { it.first == user }.values
                        // What TMDB recommends after the user's titles, plus the titles they rated 8 or more
                        val liked = mine.map { SeedRecommendations(LIKED_SEED, it.history.liked) }
                        affinityStore.save(user, genreAffinity(mine.flatMap { it.results } + liked))
                    }
                }
                computed
            }

        /**
         * "Vos réalisateurs": the films not seen yet of the directors behind most of the movies the user rated 8 or more
         * (Nolan, Fincher...), mixed so each director shows up early. Null without at least two liked films by one director.
         */
        suspend fun favoriteDirectors(): DirectorCards? {
            val history = compute(TmdbMediaType.MOVIE).history
            val semaphore = Semaphore(PARALLEL_CALLS)
            // The title pages of the liked films are cached for hours: only the first run asks TMDB
            val directors =
                coroutineScope {
                    history.liked.take(MAX_LIKED_FOR_DIRECTORS).map { film ->
                        async {
                            semaphore.withPermit {
                                try {
                                    tmdb.details(TmdbMediaType.MOVIE, film.tmdbId).director
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Timber.w(e, "No director for %s", film.tmdbId)
                                    null
                                }
                            }
                        }
                    }
                }.map { it.await() }
            val favorites = rankDirectors(directors, DIRECTOR_ROWS_MAX, DIRECTOR_MIN_LIKED)
            if (favorites.isEmpty()) return null
            val films =
                favorites.map { director ->
                    try {
                        tmdb.directedMovies(director.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "No films for %s", director.name)
                        emptyList()
                    }
                }
            val mixed = mixDirectorFilms(films, history.watched, DIRECTOR_FILM_MIN_VOTES)
            if (mixed.size < BECAUSE_MIN_ITEMS) return null
            return DirectorCards(favorites.map { it.name }, catalog.cards(mixed))
        }

        private suspend fun recommendationsAfter(
            mediaType: TmdbMediaType,
            seed: TasteSeed,
        ): SeedRecommendations =
            try {
                SeedRecommendations(seed, tmdb.recommendations(mediaType, seed.key.tmdbId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "No recommendations after %s", seed.key)
                SeedRecommendations(seed, emptyList())
            }

        companion object {
            const val FOR_YOU_SIZE = 20
            const val FOR_YOU_MIN_RATING = 6.5
            const val FOR_YOU_MIN_VOTES = 500
            private val LIKED_SEED = TasteSeed(TmdbKey(TmdbMediaType.MOVIE, 0), "", 1.0)
            const val BECAUSE_ROWS = 2
            const val BECAUSE_MIN_ITEMS = 6
            const val BECAUSE_ROW_SIZE = 20
            const val TTL_MS = 30 * 60 * 1000L
            const val PARALLEL_CALLS = 4

            /** Liked films looked at for their director, most recently rated first */
            const val MAX_LIKED_FOR_DIRECTORS = 60
            const val DIRECTOR_ROWS_MAX = 3
            const val DIRECTOR_MIN_LIKED = 2
            const val DIRECTOR_FILM_MIN_VOTES = 300
        }
    }
