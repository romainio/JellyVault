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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbDiscoverQueryTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = JvDispatchers.configure(testDispatcher)

    @After
    fun tearDown() = JvDispatchers.reset()

    @Test
    fun movie_query_params() {
        val params =
            TmdbDiscoverQuery(
                mediaType = TmdbMediaType.MOVIE,
                genreId = 16,
                companyId = 3,
                sort = TmdbSort.RATING,
                minVotes = 200,
                maxVotes = 2000,
                minRating = 7.5,
                releasedAfter = LocalDate.of(2025, 9, 27),
            ).params(page = 2)
        assertEquals("16", params["with_genres"])
        assertEquals("3", params["with_companies"])
        assertEquals("vote_average.desc", params["sort_by"])
        assertEquals("200", params["vote_count.gte"])
        assertEquals("2000", params["vote_count.lte"])
        assertEquals("7.5", params["vote_average.gte"])
        assertEquals("2025-09-27", params["primary_release_date.gte"])
        assertEquals("false", params["include_adult"])
        assertEquals("2", params["page"])
        assertNull(params["with_networks"])
    }

    @Test
    fun series_query_uses_air_dates_and_networks() {
        val params =
            TmdbDiscoverQuery(
                mediaType = TmdbMediaType.TV,
                networkId = 49,
                releasedBefore = LocalDate.of(1999, 12, 31),
            ).params(page = 1)
        assertEquals("49", params["with_networks"])
        assertEquals("1999-12-31", params["first_air_date.lte"])
        assertEquals("popularity.desc", params["sort_by"])
        assertNull(params["primary_release_date.lte"])
    }

    private class Fake : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            requests += chain.request()
            return Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .body(
                    """{"page":1,"total_pages":7,"results":[{"id":1,"title":"A","poster_path":"/a.jpg"}]}"""
                        .toResponseBody("application/json".toMediaType()),
                ).build()
        }
    }

    @Test
    fun client_sends_the_query_to_discover() =
        runTest(testDispatcher) {
            val fake = Fake()
            val client = TmdbClient(OkHttpClient.Builder().addInterceptor(fake).build()) { "t" }.also { it.language = { FRENCH } }
            val page = client.discover(TmdbDiscoverQuery(TmdbMediaType.TV, networkId = 49), page = 3)
            val url = fake.requests.single().url
            assertEquals("/3/discover/tv", url.encodedPath)
            assertEquals("49", url.queryParameter("with_networks"))
            assertEquals("3", url.queryParameter("page"))
            assertEquals(TmdbMediaType.TV, page.items.single().mediaType)
            assertEquals(7, page.totalPages)
        }

    @Test
    fun several_companies_or_networks_are_joined_with_or() {
        val movie = TmdbDiscoverQuery(TmdbMediaType.MOVIE, genreId = 16, companyIds = listOf(6125, 3)).params(1)
        assertEquals("6125|3", movie["with_companies"])
        val tv = TmdbDiscoverQuery(TmdbMediaType.TV, networkIds = listOf(49, 3186), airedAfter = LocalDate.of(2025, 9, 28)).params(1)
        assertEquals("49|3186", tv["with_networks"])
        assertEquals("2025-09-28", tv["air_date.gte"])
    }
}
