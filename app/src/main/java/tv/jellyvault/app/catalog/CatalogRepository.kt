package tv.jellyvault.app.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.library.LibraryItemSource
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.shelves.Shelf
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbListType
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.mainstream
import tv.jellyvault.app.catalog.top10.WeeklyTop10
import tv.jellyvault.app.data.model.BaseItem
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds catalog rows from TMDB, marking titles the user already has in Jellyfin
 */
@Singleton
class CatalogRepository
    @Inject
    constructor(
        private val tmdb: TmdbClient,
        private val libraryIndex: LibraryIndex,
        private val source: LibraryItemSource,
        private val weeklyTop10: WeeklyTop10,
        private val seenTitles: SeenTitles,
    ) {
        constructor(
            tmdb: TmdbClient,
            libraryIndex: LibraryIndex,
            source: LibraryItemSource,
            weeklyTop10: WeeklyTop10,
        ) : this(tmdb, libraryIndex, source, weeklyTop10, SeenTitles { emptySet() })

        val isConfigured: Boolean get() = tmdb.isConfigured

        /**
         * This week's trending titles for a French viewer, two TMDB pages (about 40 titles) without the obscure
         * foreign-language ones. Series start with this week's French top 10, numbered.
         */
        suspend fun trendingRow(mediaType: TmdbMediaType): List<MediaCard> {
            val trending = tmdb.trending(mediaType).mainstream() + secondPage { tmdb.trending(mediaType, 2).mainstream() }
            if (mediaType == TmdbMediaType.MOVIE) return toCards(trending)
            val top =
                try {
                    topTenSeries()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Top 10 failed, trending only")
                    emptyList()
                }
            val inTop = top.map { it.key }.toSet()
            return toCards(top + trending.filterNot { it.key in inTop })
        }

        /**
         * The search page before anything is typed: this week's trending movies and series, mixed (about 80 titles)
         */
        suspend fun searchMosaic(): List<MediaCard> =
            coroutineScope {
                val movies =
                    async {
                        tmdb.trending(TmdbMediaType.MOVIE).mainstream() +
                            secondPage { tmdb.trending(TmdbMediaType.MOVIE, 2).mainstream() }
                    }
                val series =
                    async {
                        tmdb.trending(TmdbMediaType.TV).mainstream() +
                            secondPage { tmdb.trending(TmdbMediaType.TV, 2).mainstream() }
                    }
                toCards(interleave(movies.await(), series.await()))
            }

        /**
         * About 40 titles of a home style: two TMDB pages, without the obscure foreign-language titles
         */
        suspend fun shelfRow(
            shelf: Shelf,
            today: LocalDate,
        ): List<MediaCard> {
            val query = shelf.query(today)
            val first = tmdb.discover(query, 1)
            val second = if (first.totalPages > 1) secondPage { tmdb.discover(query, 2).items } else emptyList()
            return toCards((first.items + second).forQuery(query))
        }

        private suspend fun secondPage(fetch: suspend () -> List<TmdbItem>): List<TmdbItem> =
            try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Second page failed")
                emptyList()
            }

        suspend fun genreRow(
            genre: CatalogGenre,
            mediaType: CatalogMediaType,
        ): List<MediaCard> {
            val items =
                when (mediaType) {
                    CatalogMediaType.MOVIE -> {
                        if (genre.companyIds.isNotEmpty()) {
                            tmdb
                                .discover(
                                    TmdbDiscoverQuery(
                                        TmdbMediaType.MOVIE,
                                        genreId = genre.movieGenreId,
                                        companyIds = genre.companyIds,
                                        minRating = 5.0,
                                    ),
                                ).items
                        } else {
                            tmdb.discover(TmdbMediaType.MOVIE, genre.movieGenreId)
                        }
                    }

                    CatalogMediaType.TV -> {
                        genre.tvGenreId?.let { tmdb.discover(TmdbMediaType.TV, it) }.orEmpty()
                    }

                    CatalogMediaType.BOTH -> {
                        coroutineScope {
                            val movies = async { tmdb.discover(TmdbMediaType.MOVIE, genre.movieGenreId) }
                            val series = async { genre.tvGenreId?.let { tmdb.discover(TmdbMediaType.TV, it) }.orEmpty() }
                            interleave(movies.await(), series.await())
                        }
                    }
                }
            return toCards(items.mainstream())
        }

        /**
         * Convert TMDB titles to cards, marking the ones already in the library
         */
        suspend fun cards(items: List<TmdbItem>): List<MediaCard> = toCards(items)

        suspend fun listRow(
            type: TmdbListType,
            mediaType: TmdbMediaType,
        ): List<MediaCard> {
            val items = tmdb.list(type, mediaType).items
            // TMDB's popular list is where obscure foreign titles show up most
            return toCards(if (type == TmdbListType.POPULAR) items.mainstream() else items)
        }

        suspend fun searchCards(query: String): List<MediaCard> = toCards(tmdb.search(query).items)

        suspend fun queryRow(query: TmdbDiscoverQuery): List<MediaCard> = toCards(tmdb.discover(query).items.forQuery(query))

        /**
         * [query] without the titles of the rows above it ([exclude]), empty when too few are left to make a row worth showing
         */
        suspend fun queryRow(
            query: TmdbDiscoverQuery,
            exclude: List<TmdbDiscoverQuery>,
            pages: Int = 1,
            onePerSaga: Boolean = false,
        ): List<MediaCard> {
            val first = tmdb.discover(query)
            val more = (2..minOf(pages, first.totalPages)).flatMap { page -> secondPage { tmdb.discover(query, page).items } }
            val items = (first.items + more).forQuery(query).let { if (onePerSaga) it.distinctBy { item -> sagaKey(item.title) } else it }
            if (exclude.isEmpty()) return toCards(items)
            // Same requests as the rows above: answered from the TMDB cache
            val shown = exclude.flatMap { tmdb.discover(it).items.forQuery(it) }.map { it.key }.toSet()
            val rest = items.filterNot { it.key in shown }
            return if (rest.size < MIN_DISTINCT_TITLES) emptyList() else toCards(rest)
        }

        /**
         * The rows under a TMDB title page, see [TitlePageRows]. A row that fails stays empty, the others still show.
         */
        suspend fun titleRows(details: TmdbDetails): TitlePageRows {
            val self = details.item.key
            val saga =
                details.collection
                    ?.takeIf { details.item.mediaType == TmdbMediaType.MOVIE }
                    ?.let { orEmpty("Saga row") { tmdb.collection(it.id).parts } }
                    .orEmpty()
                    .filter { it.key != self }
            val similar = fittingRecommendations(details, details.recommendations, saga.map { it.key }.toSet())
            val shown = (saga + similar).map { it.key }.toSet() + self
            val byDirector =
                details.director
                    ?.let { director -> orEmpty("Director row") { tmdb.directedMovies(director.id).knownFilms() } }
                    .orEmpty()
                    .filter { it.key !in shown }
            val (more, director) =
                if (byDirector.size >= DIRECTOR_MIN_TITLES) {
                    byDirector to details.director
                } else {
                    val sameGenre =
                        sameGenreQuery(details)?.let { query ->
                            orEmpty("Same genre row") { tmdb.discover(query).items.forQuery(query) }
                        }
                    sameGenre.orEmpty().filter { it.key !in shown } to null
                }
            return TitlePageRows(saga = toCards(saga), similar = toCards(similar), more = toCards(more), director = director)
        }

        private suspend fun orEmpty(
            what: String,
            fetch: suspend () -> List<TmdbItem>,
        ): List<TmdbItem> =
            try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "%s failed", what)
                emptyList()
            }

        /** The name TMDB gives a collection, in the interface language */
        suspend fun collectionName(id: Int): String = tmdb.collection(id).name

        /**
         * One page of [query], with the total number of pages
         */
        suspend fun queryPage(
            query: TmdbDiscoverQuery,
            page: Int,
        ): Pair<List<MediaCard>, Int> {
            val result = tmdb.discover(query, page)
            return toCards(result.items.forQuery(query)) to result.totalPages
        }

        private fun List<TmdbItem>.forQuery(query: TmdbDiscoverQuery) = if (query.mainstreamOnly) mainstream() else this

        /**
         * The movies of a saga, in release order
         */
        suspend fun sagaRow(saga: CatalogSaga): List<MediaCard> = collectionRow(saga.collectionId)

        /**
         * France Top 10 series of the week, matched to TMDB series, in rank order ("1. Title")
         */
        suspend fun weeklyTop10Row(): List<MediaCard> = toCards(topTenSeries())

        private suspend fun topTenSeries(): List<TmdbItem> =
            weeklyTop10.titles().mapIndexedNotNull { index, title ->
                val match =
                    try {
                        tmdb.search(title).items.firstOrNull { it.mediaType == TmdbMediaType.TV }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "No TMDB match for %s", title)
                        null
                    }
                // Rank from the list, even when an earlier title had no match
                match?.copy(title = "${index + 1}. ${match.title}")
            }

        /**
         * The movies of any TMDB collection, in release order
         */
        suspend fun collectionRow(id: Int): List<MediaCard> = toCards(tmdb.collection(id).parts)

        /**
         * One page of a genre for [mediaType], with the total number of pages (0 if the genre has no equivalent for this type)
         */
        suspend fun genrePage(
            genre: CatalogGenre,
            mediaType: TmdbMediaType,
            page: Int,
        ): Pair<List<MediaCard>, Int> {
            val genreId = if (mediaType == TmdbMediaType.MOVIE) genre.movieGenreId else genre.tvGenreId
            if (genreId == null) return emptyList<MediaCard>() to 0
            val result = tmdb.discoverPage(mediaType, genreId, page)
            return toCards(result.items) to result.totalPages
        }

        private suspend fun toCards(items: List<TmdbItem>): List<MediaCard> {
            val unique = items.distinctBy { it.key }
            val index = libraryIndex.awaitIndex()
            val ownedIds = unique.mapNotNull { index[it.key] }
            val owned =
                if (ownedIds.isEmpty()) {
                    emptyMap()
                } else {
                    try {
                        source.fetchItems(ownedIds).associateBy { it.id }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch owned items, showing them as remote")
                        emptyMap()
                    }
                }
            val seen =
                try {
                    seenTitles.keys()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "No seen titles")
                    emptySet()
                }
            return unique.map { tmdbItem ->
                val dto = index[tmdbItem.key]?.let { owned[it] }
                if (dto != null) {
                    MediaCard.Owned(
                        BaseItem(dto, useSeriesForPrimary = true, inLibraryBadge = true, seenOnImdb = tmdbItem.key in seen),
                        tmdbItem,
                    )
                } else {
                    MediaCard.Remote(tmdbItem, seen = tmdbItem.key in seen)
                }
            }
        }
    }

private val SAGA_SUFFIX =
    Regex(
        """(\s*:.*$)|(\s+[–-]\s+.*$)|(\s+(part|partie|chapter|chapitre|vol\.?|volume)\s+\S+$)|(\s+(\d+|[ivx]+)$)""",
        RegexOption.IGNORE_CASE,
    )

/**
 * What a title has in common with the other films of its saga: "Gabriel's Inferno: Part II" → "gabriel's inferno"
 */
internal fun sagaKey(title: String): String {
    var key = title.trim()
    while (true) {
        val shorter = key.replace(SAGA_SUFFIX, "").trim()
        if (shorter == key || shorter.isEmpty()) return key.lowercase()
        key = shorter
    }
}

/** A row that only repeats the rows above it, but for a few titles, is not shown */
internal const val MIN_DISTINCT_TITLES = 8

/**
 * Titles the user has seen (rated on IMDb), never waiting on the network
 */
fun interface SeenTitles {
    suspend fun keys(): Set<TmdbKey>
}

internal fun <T> interleave(
    first: List<T>,
    second: List<T>,
): List<T> =
    buildList {
        val max = maxOf(first.size, second.size)
        for (i in 0 until max) {
            first.getOrNull(i)?.let(::add)
            second.getOrNull(i)?.let(::add)
        }
    }
