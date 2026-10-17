package tv.jellyvault.app.catalog.tmdb

import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.jellyvault.app.catalog.CatalogFilter
import tv.jellyvault.app.catalog.withinCatalogFilter
import tv.jellyvault.app.services.hilt.StandardOkHttpClient
import tv.jellyvault.app.util.JvDispatchers
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal TMDB v3 client for trending and discover lists
 */
@Singleton
class TmdbClient
    @Inject
    constructor(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
        private val credential: TmdbCredential,
    ) {
        private val client =
            okHttpClient
                .newBuilder()
                .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        private val json = Json { ignoreUnknownKeys = true }

        private val cache = lruCache<Pair<Long, TmdbResultPage>>(MAX_CACHED_RESPONSES)

        private val detailsCache = lruCache<Pair<Long, TmdbDetails>>(MAX_CACHED_DETAILS)

        private val brandCache = lruCache<Pair<Long, Any>>(MAX_CACHED_DETAILS)

        internal var now: () -> Long = System::currentTimeMillis

        /** Read at each request: the interface language can change while the app runs */
        internal var language: () -> TmdbLanguage = TmdbLanguage::current

        /** The user's limits on suggestions, read at each request: applied after the cache, which keeps raw pages */
        internal var filter: () -> CatalogFilter = { CatalogFilter.current }

        val isConfigured: Boolean get() = TmdbAuth.of(credential.current()) != null

        suspend fun trending(
            mediaType: TmdbMediaType,
            page: Int = 1,
        ): List<TmdbItem> =
            getList("trending/${mediaType.path}/week", mapOf("page" to page.toString()), mediaType).withinCatalogFilter(filter())

        suspend fun discover(
            mediaType: TmdbMediaType,
            genreId: Int,
        ): List<TmdbItem> = discoverPage(mediaType, genreId, 1).items

        /**
         * Popular, well-rated titles of a genre, one TMDB page (20 titles)
         */
        suspend fun discoverPage(
            mediaType: TmdbMediaType,
            genreId: Int,
            page: Int,
        ): TmdbResultPage =
            discover(TmdbDiscoverQuery(mediaType, genreId = genreId, minVotes = MIN_VOTE_COUNT, minRating = MIN_VOTE_AVERAGE), page)

        /**
         * One page of a discover [query] (20 titles)
         */
        suspend fun discover(
            query: TmdbDiscoverQuery,
            page: Int = 1,
        ): TmdbResultPage {
            val filtered = filter().apply(query) ?: return TmdbResultPage(emptyList(), page, 0)
            return getPage("discover/${query.mediaType.path}", filtered.params(page), query.mediaType)
        }

        /**
         * A TMDB list (popular, top rated, now playing for movies, on the air for series)
         */
        suspend fun list(
            type: TmdbListType,
            mediaType: TmdbMediaType,
            page: Int = 1,
        ): TmdbResultPage {
            val path =
                when (type) {
                    TmdbListType.POPULAR -> {
                        "${mediaType.path}/popular"
                    }

                    TmdbListType.TOP_RATED -> {
                        "${mediaType.path}/top_rated"
                    }

                    TmdbListType.NOW_PLAYING -> {
                        require(mediaType == TmdbMediaType.MOVIE) { "now_playing is only for movies" }
                        "movie/now_playing"
                    }

                    TmdbListType.ON_THE_AIR -> {
                        require(mediaType == TmdbMediaType.TV) { "on_the_air is only for series" }
                        "tv/on_the_air"
                    }
                }
            return getPage(
                path,
                mapOf("page" to page.toString()),
                mediaType,
            ).let { it.copy(items = it.items.withinCatalogFilter(filter())) }
        }

        /**
         * Movies and series matching [query] (people are dropped)
         */
        suspend fun search(
            query: String,
            page: Int = 1,
        ): TmdbResultPage =
            getPage(
                "search/multi",
                mapOf("query" to query, "include_adult" to "false", "page" to page.toString()),
                TmdbMediaType.MOVIE,
            )

        /**
         * What TMDB recommends after a title, best first (20 titles)
         */
        suspend fun recommendations(
            mediaType: TmdbMediaType,
            tmdbId: Int,
        ): List<TmdbItem> = getList("${mediaType.path}/$tmdbId/recommendations", emptyMap(), mediaType).withinCatalogFilter(filter())

        suspend fun details(
            mediaType: TmdbMediaType,
            tmdbId: Int,
        ): TmdbDetails {
            val append =
                "videos,credits,recommendations,similar,images,external_ids," +
                    if (mediaType == TmdbMediaType.MOVIE) "release_dates" else "content_ratings"
            val path = "${mediaType.path}/$tmdbId"
            val lang = language()
            val url =
                buildUrl(
                    path,
                    mapOf(
                        "append_to_response" to append,
                        "include_video_language" to lang.mediaLanguages.joinToString(","),
                        "include_image_language" to (lang.mediaLanguages + "null").joinToString(","),
                    ),
                    lang,
                )
            val key = url.toString()
            detailsCache[key]?.let { (time, details) ->
                if (now() - time < CACHE_TTL_MS) return details.withinFilter()
            }
            val raw = getJson<TmdbRawDetails>(url, path)
            val details = raw.toDetails(mediaType, lang) ?: throw TmdbException("TMDB title $path has no title or poster")
            detailsCache[key] = now() to details
            return details.withinFilter()
        }

        private fun TmdbDetails.withinFilter(): TmdbDetails {
            val filter = filter()
            return copy(
                recommendations = recommendations.withinCatalogFilter(filter),
                similarTitles = similarTitles.withinCatalogFilter(filter),
            )
        }

        /**
         * The movies [personId] directed, most popular first (TMDB's crew filter of discover also matches producers and writers)
         */
        suspend fun directedMovies(personId: Int): List<TmdbItem> =
            cached("person/$personId/movie_credits") { url, path ->
                getJson<TmdbRawPersonCredits>(url, path)
                    .crew
                    .filter { it.job == "Director" }
                    .mapNotNull { it.toItem(TmdbMediaType.MOVIE) }
                    .distinctBy { it.tmdbId }
                    .sortedByDescending { it.popularity }
            }.withinCatalogFilter(filter())

        suspend fun collection(id: Int): TmdbCollection =
            cached("collection/$id") { url, path -> getJson<TmdbRawCollection>(url, path).toCollection() }

        suspend fun company(id: Int): TmdbBrand = cached("company/$id") { url, path -> getJson<TmdbRawBrand>(url, path).toBrand() }

        suspend fun network(id: Int): TmdbBrand = cached("network/$id") { url, path -> getJson<TmdbRawBrand>(url, path).toBrand() }

        @Suppress("UNCHECKED_CAST")
        private suspend fun <T : Any> cached(
            path: String,
            fetch: suspend (HttpUrl, String) -> T,
        ): T {
            val url = buildUrl(path, emptyMap())
            val key = url.toString()
            brandCache[key]?.let { (time, value) ->
                if (now() - time < CACHE_TTL_MS) return value as T
            }
            val value = fetch(url, path)
            brandCache[key] = now() to value
            return value
        }

        private suspend fun getList(
            path: String,
            params: Map<String, String>,
            requestedType: TmdbMediaType,
        ): List<TmdbItem> = getPage(path, params, requestedType).items

        private suspend fun getPage(
            path: String,
            params: Map<String, String>,
            requestedType: TmdbMediaType,
        ): TmdbResultPage {
            val url = buildUrl(path, params)
            val key = url.toString()
            cache[key]?.let { (time, page) ->
                if (now() - time < CACHE_TTL_MS) return page
            }
            val raw = getJson<TmdbPage>(url, path)
            val page = TmdbResultPage(raw.results.mapNotNull { it.toItem(requestedType) }, raw.page, raw.totalPages)
            cache[key] = now() to page
            return page
        }

        private fun buildUrl(
            path: String,
            params: Map<String, String>,
            language: TmdbLanguage = language(),
        ): HttpUrl {
            if (!isConfigured) throw TmdbException("TMDB read token is not configured")
            return BASE_URL
                .toHttpUrl()
                .newBuilder()
                .addPathSegments(path)
                .addQueryParameter("language", language.tag)
                .addQueryParameter("region", REGION)
                .apply { params.toSortedMap().forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
        }

        private suspend inline fun <reified T> getJson(
            url: HttpUrl,
            path: String,
        ): T =
            withContext(JvDispatchers.IO) {
                try {
                    val request =
                        Request
                            .Builder()
                            .tmdbRequest(url, credential.current())
                            .header("Accept", "application/json")
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw TmdbException("TMDB HTTP ${response.code} for $path")
                        }
                        json.decodeFromString<T>(response.body.string())
                    }
                } catch (e: IOException) {
                    throw TmdbException("TMDB unreachable", e)
                } catch (e: SerializationException) {
                    throw TmdbException("Invalid TMDB response for $path", e)
                } catch (e: IllegalArgumentException) {
                    // Last, as SerializationException is one too: a credential OkHttp refuses in a header; its message quotes the key, so no cause
                    throw TmdbException("TMDB credential is invalid")
                }
            }

        companion object {
            /** Searches and genre pages add up: keep memory bounded on low-RAM TVs */
            const val MAX_CACHED_RESPONSES = 200
            const val MAX_CACHED_DETAILS = 100

            private fun <V> lruCache(maxEntries: Int): MutableMap<String, V> =
                Collections.synchronizedMap(
                    object : LinkedHashMap<String, V>(16, 0.75f, true) {
                        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > maxEntries
                    },
                )

            const val BASE_URL = "https://api.themoviedb.org/3/"
            const val REGION = "FR"
            const val MIN_VOTE_AVERAGE = 5.0
            const val MIN_VOTE_COUNT = 200
            const val TIMEOUT_SECONDS = 10L
            const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        }
    }
