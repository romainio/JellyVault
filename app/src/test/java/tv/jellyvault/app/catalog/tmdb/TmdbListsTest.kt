package tv.jellyvault.app.catalog.tmdb

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbListsTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = JvDispatchers.configure(testDispatcher)

    @After
    fun tearDown() = JvDispatchers.reset()

    private class Fake(
        private val routes: Map<String, String>,
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val body = routes.entries.firstOrNull { request.url.encodedPath.endsWith(it.key) }?.value
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(if (body != null) 200 else 404)
                .message("test")
                .body((body ?: "{}").toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun fixture(name: String) = javaClass.getResource("/tmdb/$name")!!.readText()

    private fun client(fake: Fake) =
        TmdbClient(OkHttpClient.Builder().addInterceptor(fake).build()) { "t" }.also { it.language = { FRENCH } }

    private val emptyPage = """{"page": 1, "total_pages": 1, "results": []}"""

    @Test
    fun popular_movies_page_2_uses_path_and_page() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/movie/popular" to fixture("movie_popular.json")))
            val page = client(fake).list(TmdbListType.POPULAR, TmdbMediaType.MOVIE, page = 2)
            assertEquals(listOf(1, 2), page.items.map { it.tmdbId })
            assertEquals(TmdbMediaType.MOVIE, page.items[0].mediaType)
            assertEquals(2, page.page)
            assertEquals(40, page.totalPages)
            assertEquals(
                "2",
                fake.requests
                    .single()
                    .url
                    .queryParameter("page"),
            )
            assertEquals(
                "fr-FR",
                fake.requests
                    .single()
                    .url
                    .queryParameter("language"),
            )
        }

    @Test
    fun top_rated_tv_path() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/tv/top_rated" to emptyPage))
            client(fake).list(TmdbListType.TOP_RATED, TmdbMediaType.TV)
            assertEquals(
                "/3/tv/top_rated",
                fake.requests
                    .single()
                    .url.encodedPath,
            )
        }

    @Test
    fun now_playing_only_for_movies_and_on_the_air_only_for_series() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/movie/now_playing" to emptyPage, "/3/tv/on_the_air" to emptyPage))
            val c = client(fake)
            c.list(TmdbListType.NOW_PLAYING, TmdbMediaType.MOVIE)
            c.list(TmdbListType.ON_THE_AIR, TmdbMediaType.TV)
            assertEquals(listOf("/3/movie/now_playing", "/3/tv/on_the_air"), fake.requests.map { it.url.encodedPath })
            try {
                c.list(TmdbListType.NOW_PLAYING, TmdbMediaType.TV)
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
            try {
                c.list(TmdbListType.ON_THE_AIR, TmdbMediaType.MOVIE)
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
            assertEquals(2, fake.requests.size)
        }

    @Test
    fun search_keeps_movies_and_series_only() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/search/multi" to fixture("search_multi.json")))
            val page = client(fake).search("Toy Story")
            assertEquals(listOf(863, 1234), page.items.map { it.tmdbId })
            assertEquals(listOf(TmdbMediaType.MOVIE, TmdbMediaType.TV), page.items.map { it.mediaType })
            assertEquals(3, page.totalPages)
            val url = fake.requests.single().url
            assertEquals("Toy Story", url.queryParameter("query"))
            assertEquals("false", url.queryParameter("include_adult"))
        }

    @Test
    fun discover_page_sends_page_and_filters() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/discover/movie" to fixture("movie_popular.json")))
            val page = client(fake).discoverPage(TmdbMediaType.MOVIE, genreId = 28, page = 2)
            assertEquals(40, page.totalPages)
            val url = fake.requests.single().url
            assertEquals("2", url.queryParameter("page"))
            assertEquals("28", url.queryParameter("with_genres"))
            assertEquals("5.0", url.queryParameter("vote_average.gte"))
            assertEquals("200", url.queryParameter("vote_count.gte"))
            assertEquals("popularity.desc", url.queryParameter("sort_by"))
        }

    @Test
    fun lists_are_cached_per_page() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/movie/popular" to fixture("movie_popular.json")))
            val c = client(fake)
            c.list(TmdbListType.POPULAR, TmdbMediaType.MOVIE, 1)
            c.list(TmdbListType.POPULAR, TmdbMediaType.MOVIE, 1)
            c.list(TmdbListType.POPULAR, TmdbMediaType.MOVIE, 2)
            assertEquals(2, fake.requests.size)
            assertTrue(fake.requests.map { it.url.queryParameter("page") }.containsAll(listOf("1", "2")))
        }

    @Test
    fun cache_keeps_only_the_most_recent_responses() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/search/multi" to emptyPage))
            val c = client(fake)
            c.search("q0")
            repeat(TmdbClient.MAX_CACHED_RESPONSES) { c.search("q${it + 1}") }
            val before = fake.requests.size
            c.search("q${TmdbClient.MAX_CACHED_RESPONSES}")
            assertEquals(before, fake.requests.size)
            c.search("q0")
            assertEquals(before + 1, fake.requests.size)
        }
}
