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

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbBrandsTest {
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

    private fun client(fake: Fake) =
        TmdbClient(OkHttpClient.Builder().addInterceptor(fake).build()) { "t" }.also { it.language = { FRENCH } }

    @Test
    fun collection_parts_are_sorted_by_release_date_with_undated_last() =
        runTest(testDispatcher) {
            val fake =
                Fake(
                    mapOf(
                        "/3/collection/10194" to
                            """
                            {"id":10194,"name":"Toy Story - Saga","backdrop_path":"/b.jpg","parts":[
                              {"id":3,"media_type":"movie","title":"Toy Story 3","poster_path":"/3.jpg","release_date":"2010-06-16"},
                              {"id":5,"media_type":"movie","title":"Toy Story 5","poster_path":"/5.jpg","release_date":""},
                              {"id":1,"media_type":"movie","title":"Toy Story","poster_path":"/1.jpg","release_date":"1995-11-22"},
                              {"id":9,"media_type":"movie","title":"No poster","release_date":"2001-01-01"}
                            ]}
                            """.trimIndent(),
                    ),
                )
            val collection = client(fake).collection(10194)
            assertEquals("Toy Story - Saga", collection.name)
            assertEquals("https://image.tmdb.org/t/p/w780/b.jpg", collection.backdropUrl)
            assertEquals(listOf(1, 3, 5), collection.parts.map { it.tmdbId })
        }

    @Test
    fun company_and_network_logos_are_cached() =
        runTest(testDispatcher) {
            val fake =
                Fake(
                    mapOf(
                        "/3/company/3" to """{"id":3,"name":"Pixar","logo_path":"/pixar.png"}""",
                        "/3/network/49" to """{"id":49,"name":"HBO","logo_path":null}""",
                    ),
                )
            val client = client(fake)
            val pixar = client.company(3)
            client.company(3)
            assertEquals("Pixar", pixar.name)
            assertEquals("https://image.tmdb.org/t/p/w300/pixar.png", pixar.logoUrl)
            assertNull(client.network(49).logoUrl)
            assertEquals(2, fake.requests.size)
        }
}
