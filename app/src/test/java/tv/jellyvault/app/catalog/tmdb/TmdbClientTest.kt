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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbClientTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        JvDispatchers.configure(testDispatcher)
    }

    @After
    fun tearDown() {
        JvDispatchers.reset()
    }

    private class FakeTmdb(
        private val routes: Map<String, Pair<Int, String>>,
        private val failWith: IOException? = null,
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            failWith?.let { throw it }
            val (code, body) =
                routes.entries.firstOrNull { request.url.encodedPath.endsWith(it.key) }?.value
                    ?: (404 to """{"status_message":"not found"}""")
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun fixture(name: String): String = javaClass.getResource("/tmdb/$name")!!.readText()

    private fun client(
        fake: FakeTmdb,
        token: String = "test-token",
    ) = TmdbClient(OkHttpClient.Builder().addInterceptor(fake).build()) { token }.also { it.language = { FRENCH } }

    @Test
    fun directed_movies_keep_only_directing_credits_most_popular_first() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/person/525/movie_credits" to (200 to fixture("person_movie_credits.json"))))
            val movies = client(fake).directedMovies(525)
            assertEquals(listOf("Interstellar", "Inception"), movies.map { it.title })
            assertEquals(TmdbMediaType.MOVIE, movies.first().mediaType)
        }

    @Test
    fun trending_movies_parses_and_sends_auth_and_locale() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (200 to fixture("trending_movie_week.json"))))
            val items = client(fake).trending(TmdbMediaType.MOVIE)

            assertEquals(listOf(1022789, 533535), items.map { it.tmdbId })
            val first = items.first()
            assertEquals("Vice-versa 2", first.title)
            assertEquals(TmdbMediaType.MOVIE, first.mediaType)
            assertEquals(2024, first.year)
            assertEquals("https://image.tmdb.org/t/p/w500/poster1.jpg", first.posterUrl)
            assertEquals("https://image.tmdb.org/t/p/original/backdrop1.jpg", first.backdropUrl)
            assertNull(items[1].backdropUrl)

            val request = fake.requests.single()
            assertEquals("Bearer test-token", request.header("Authorization"))
            assertEquals("fr-FR", request.url.queryParameter("language"))
            assertEquals("FR", request.url.queryParameter("region"))
        }

    @Test
    fun a_v3_key_is_sent_as_api_key_without_an_authorization_header() =
        runTest(testDispatcher) {
            val key = "0123456789abcdef0123456789abcdef"
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (200 to fixture("trending_movie_week.json"))))
            client(fake, token = key).trending(TmdbMediaType.MOVIE)

            val request = fake.requests.single()
            assertEquals(key, request.url.queryParameter("api_key"))
            assertNull(request.header("Authorization"))
        }

    @Test
    fun without_a_credential_nothing_is_configured_and_no_request_is_made() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (200 to fixture("trending_movie_week.json"))))
            val client = client(fake, token = "")
            assertFalse(client.isConfigured)
            try {
                client.trending(TmdbMediaType.MOVIE)
                fail("expected TmdbException")
            } catch (e: TmdbException) {
                assertTrue(fake.requests.isEmpty())
            }
        }

    @Test
    fun a_malformed_credential_is_not_configured_and_no_request_is_made() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (200 to fixture("trending_movie_week.json"))))
            val client = client(fake, token = "abc def")
            assertFalse(client.isConfigured)
            try {
                client.trending(TmdbMediaType.MOVIE)
                fail("expected TmdbException")
            } catch (e: TmdbException) {
                assertTrue(fake.requests.isEmpty())
            }
        }

    @Test
    fun a_refused_key_is_not_part_of_the_error_message() =
        runTest(testDispatcher) {
            val key = "0123456789abcdef0123456789abcdef"
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (401 to """{"status_message":"Invalid API key"}""")))
            try {
                client(fake, token = key).trending(TmdbMediaType.MOVIE)
                fail("expected TmdbException")
            } catch (e: TmdbException) {
                assertFalse(e.message!!.contains(key))
            }
        }

    @Test
    fun drops_items_without_poster_title_or_with_unknown_type() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (200 to fixture("trending_movie_week.json"))))
            val ids = client(fake).trending(TmdbMediaType.MOVIE).map { it.tmdbId }
            assertFalse(1 in ids)
            assertFalse(2 in ids)
            assertFalse(3 in ids)
        }

    @Test
    fun trending_tv_uses_name_and_first_air_date() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/trending/tv/week" to (200 to fixture("trending_tv_week.json"))))
            val item = client(fake).trending(TmdbMediaType.TV).single()
            assertEquals("House of the Dragon", item.title)
            assertEquals(TmdbMediaType.TV, item.mediaType)
            assertEquals(2022, item.year)
        }

    @Test
    fun discover_sends_genre_and_quality_filters_and_uses_requested_type() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/discover/movie" to (200 to fixture("discover_movie.json"))))
            val item = client(fake).discover(TmdbMediaType.MOVIE, genreId = 28).single()

            assertEquals(TmdbMediaType.MOVIE, item.mediaType)
            val url = fake.requests.single().url
            assertEquals("28", url.queryParameter("with_genres"))
            assertEquals("popularity.desc", url.queryParameter("sort_by"))
            assertEquals("5.0", url.queryParameter("vote_average.gte"))
            assertEquals("200", url.queryParameter("vote_count.gte"))
            assertEquals("false", url.queryParameter("include_adult"))
        }

    @Test
    fun responses_are_cached() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (200 to fixture("trending_movie_week.json"))))
            val client = client(fake)
            client.trending(TmdbMediaType.MOVIE)
            client.trending(TmdbMediaType.MOVIE)
            assertEquals(1, fake.requests.size)
        }

    @Test
    fun cache_expires_after_ttl() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (200 to fixture("trending_movie_week.json"))))
            val client = client(fake)
            var now = 0L
            client.now = { now }
            client.trending(TmdbMediaType.MOVIE)
            now = TmdbClient.CACHE_TTL_MS + 1
            client.trending(TmdbMediaType.MOVIE)
            assertEquals(2, fake.requests.size)
        }

    @Test
    fun http_error_becomes_TmdbException_and_is_not_cached() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(mapOf("/3/trending/movie/week" to (401 to """{"status_message":"bad key"}""")))
            val client = client(fake)
            repeat(2) {
                try {
                    client.trending(TmdbMediaType.MOVIE)
                    fail("expected TmdbException")
                } catch (e: TmdbException) {
                    assertTrue(e.message!!.contains("401"))
                }
            }
            assertEquals(2, fake.requests.size)
        }

    @Test
    fun network_error_becomes_TmdbException() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(emptyMap(), failWith = IOException("offline"))
            try {
                client(fake).trending(TmdbMediaType.TV)
                fail("expected TmdbException")
            } catch (e: TmdbException) {
                assertTrue(e.cause is IOException)
            }
        }

    @Test
    fun missing_token_is_not_configured_and_throws_without_network() =
        runTest(testDispatcher) {
            val fake = FakeTmdb(emptyMap())
            val client = client(fake, token = "")
            assertFalse(client.isConfigured)
            try {
                client.trending(TmdbMediaType.MOVIE)
                fail("expected TmdbException")
            } catch (_: TmdbException) {
            }
            assertTrue(fake.requests.isEmpty())
        }
}
