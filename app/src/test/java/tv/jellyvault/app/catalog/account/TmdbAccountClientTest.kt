package tv.jellyvault.app.catalog.account

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
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbLanguage
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbAccountClientTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = JvDispatchers.configure(dispatcher)

    @After
    fun tearDown() = JvDispatchers.reset()

    private class Fake(
        private val route: (Request) -> Pair<Int, String>,
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val (code, body) = route(request)
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("t")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun client(fake: Fake) =
        TmdbAccountClient(OkHttpClient.Builder().addInterceptor(fake).build()) { "read-token" }
            .also { it.language = { TmdbLanguage.of(Locale.FRANCE) } }

    private fun rated(
        id: Int,
        rating: Double,
    ) =
        """{"id":$id,"title":"T$id","poster_path":"/p.jpg","genre_ids":[18],"vote_average":7.1,"vote_count":900,"original_language":"en","rating":$rating}"""

    @Test
    fun link_flow_creates_a_token_then_a_session_then_reads_the_account() =
        runTest(dispatcher) {
            val fake =
                Fake { r ->
                    when {
                        r.url.encodedPath.endsWith("authentication/token/new") -> 200 to """{"success":true,"request_token":"abc"}"""
                        r.url.encodedPath.endsWith("authentication/session/new") -> 200 to """{"success":true,"session_id":"sess"}"""
                        r.url.encodedPath.endsWith("/account") -> 200 to """{"id":42,"username":"jellyfan"}"""
                        else -> 404 to "{}"
                    }
                }
            val api = client(fake)
            assertEquals("abc", api.createRequestToken())
            assertEquals("https://www.themoviedb.org/authenticate/abc", TmdbAccountClient.approvalUrl("abc"))
            assertEquals("sess", api.createSession("abc"))
            val post = fake.requests[1]
            assertEquals("POST", post.method)
            val body = Buffer().also { post.body!!.writeTo(it) }.readUtf8()
            assertTrue(body.contains("\"request_token\":\"abc\""))
            assertEquals(TmdbAccountInfo(42, "jellyfan"), api.account("sess"))
            assertEquals("sess", fake.requests[2].url.queryParameter("session_id"))
            assertEquals("Bearer read-token", fake.requests[2].header("Authorization"))
        }

    @Test
    fun a_refused_approval_is_a_clear_error() =
        runTest(dispatcher) {
            val api = client(Fake { 401 to """{"success":false,"status_code":17}""" })
            try {
                api.createSession("abc")
                fail()
            } catch (e: TmdbAccountException) {
                assertEquals(401, e.code)
            }
        }

    @Test
    fun ratings_read_every_page_newest_first_with_the_rating() =
        runTest(dispatcher) {
            val fake =
                Fake { r ->
                    when (r.url.queryParameter("page")) {
                        "1" -> 200 to """{"page":1,"total_pages":2,"results":[${rated(1, 9.0)},${rated(2, 4.0)}]}"""
                        else -> 200 to """{"page":2,"total_pages":2,"results":[${rated(3, 8.0)}]}"""
                    }
                }
            val ratings = client(fake).rated(TmdbMediaType.MOVIE, 42, "sess")
            assertEquals(listOf(1 to 9.0, 2 to 4.0, 3 to 8.0), ratings.map { it.item.tmdbId to it.rating })
            assertTrue(fake.requests.all { it.url.encodedPath.endsWith("account/42/rated/movies") })
            assertEquals("created_at.desc", fake.requests[0].url.queryParameter("sort_by"))
        }

    @Test
    fun watchlist_of_series_uses_the_tv_path() =
        runTest(dispatcher) {
            val fake = Fake { 200 to """{"page":1,"total_pages":1,"results":[{"id":5,"name":"S5","poster_path":"/p.jpg"}]}""" }
            val items = client(fake).watchlist(TmdbMediaType.TV, 42, "sess")
            assertEquals(listOf("S5"), items.map { it.title })
            assertTrue(
                fake.requests
                    .single()
                    .url.encodedPath
                    .endsWith("account/42/watchlist/tv"),
            )
        }

    @Test
    fun an_expired_session_says_so() =
        runTest(dispatcher) {
            val api = client(Fake { 401 to """{"status_code":3}""" })
            try {
                api.rated(TmdbMediaType.MOVIE, 42, "old")
                fail()
            } catch (e: TmdbAccountException) {
                assertTrue(e.unauthorized)
            }
        }

    @Test
    fun rating_posts_the_value_and_removing_deletes_it() =
        runTest(dispatcher) {
            val fake = Fake { 200 to """{"success":true}""" }
            val api = client(fake)
            api.rate(TmdbMediaType.MOVIE, 550, "sess", 8.0)
            api.rate(TmdbMediaType.TV, 1399, "sess", null)
            val (post, delete) = fake.requests
            assertEquals("POST", post.method)
            assertTrue(post.url.encodedPath.endsWith("/movie/550/rating"))
            assertEquals("sess", post.url.queryParameter("session_id"))
            assertEquals("""{"value":8.0}""", Buffer().also { post.body!!.writeTo(it) }.readUtf8())
            assertEquals("DELETE", delete.method)
            assertTrue(delete.url.encodedPath.endsWith("/tv/1399/rating"))
        }
}
