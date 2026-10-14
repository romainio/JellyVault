package tv.jellyvault.app.catalog.arr

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.catalog.arr.FakeArr.Companion.fixture
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrClientTest {
    private val testDispatcher = StandardTestDispatcher()
    private val settings = mockk<ArrSettingsRepository>()
    private val configured =
        ArrConfig(url = "https://arr.example.test/radarr", apiKey = "K", qualityProfileId = 4, rootFolderPath = "/data/media/Movies")

    @Before
    fun setUp() {
        JvDispatchers.configure(testDispatcher)
        coEvery { settings.current(ArrService.RADARR) } returns configured
    }

    @After
    fun tearDown() = JvDispatchers.reset()

    private fun client(fake: FakeArr) = RadarrClient(ArrHttp(OkHttpClient.Builder().addInterceptor(fake).build()), settings)

    @Test
    fun status_profiles_folders() =
        runTest(testDispatcher) {
            val fake =
                FakeArr(
                    mapOf(
                        "/system/status" to (200 to fixture("system_status.json")),
                        "/qualityprofile" to (200 to fixture("qualityprofile.json")),
                        "/rootfolder" to (200 to fixture("rootfolder.json")),
                    ),
                )
            val c = client(fake)
            assertEquals("6.0.4.10291", c.systemStatus().version)
            assertEquals(listOf(QualityProfile(1, "Any"), QualityProfile(4, "HD-1080p")), c.qualityProfiles())
            assertEquals(listOf(RootFolder("/data/media/Movies", 2550745554944), RootFolder("/data/torrents", null)), c.rootFolders())
        }

    @Test
    fun explicit_config_is_used_for_testing_unsaved_values() =
        runTest(testDispatcher) {
            val fake = FakeArr(mapOf("/system/status" to (200 to fixture("system_status.json"))))
            client(fake).systemStatus(ArrConfig(url = "https://other.example.test/r", apiKey = "X"))
            assertEquals(
                "https://other.example.test/r/api/v3/system/status",
                fake.requests
                    .single()
                    .url
                    .toString(),
            )
            assertEquals("X", fake.requests.single().header("X-Api-Key"))
        }

    @Test
    fun movie_by_tmdb_id_found_or_null() =
        runTest(testDispatcher) {
            val found = FakeArr(mapOf("/api/v3/movie" to (200 to fixture("radarr_movie_list.json"))))
            assertEquals(RadarrMovie(12, 27205, hasFile = false, monitored = true), client(found).movieByTmdbId(27205))
            assertEquals(
                "27205",
                found.requests
                    .single()
                    .url
                    .queryParameter("tmdbId"),
            )

            val none = FakeArr(mapOf("/api/v3/movie" to (200 to "[]")))
            assertNull(client(none).movieByTmdbId(27205))
        }

    @Test
    fun add_movie_posts_lookup_with_settings() =
        runTest(testDispatcher) {
            val fake =
                FakeArr(
                    mapOf(
                        "GET /movie/lookup/tmdb" to (200 to fixture("radarr_movie_lookup.json")),
                        "POST /api/v3/movie" to (201 to """{"id": 12, "tmdbId": 27205, "hasFile": false, "monitored": true}"""),
                    ),
                )
            val movie = client(fake).addMovie(27205)
            assertEquals(12, movie.id)
            assertEquals("27205", fake.requests[0].url.queryParameter("tmdbId"))

            val posted = Json.parseToJsonElement(fake.bodies[1]!!).jsonObject
            assertEquals("Inception", posted["title"]!!.jsonPrimitive.content)
            assertEquals(4, posted["qualityProfileId"]!!.jsonPrimitive.int)
            assertEquals("/data/media/Movies", posted["rootFolderPath"]!!.jsonPrimitive.content)
            assertEquals(true, posted["monitored"]!!.jsonPrimitive.boolean)
            assertEquals("released", posted["minimumAvailability"]!!.jsonPrimitive.content)
            assertEquals(true, posted["addOptions"]!!.jsonObject["searchForMovie"]!!.jsonPrimitive.boolean)
            assertEquals("POST", fake.requests[1].method)
        }

    @Test
    fun add_movie_requires_profile_and_folder() =
        runTest(testDispatcher) {
            coEvery { settings.current(ArrService.RADARR) } returns configured.copy(qualityProfileId = 0)
            val fake = FakeArr(emptyMap())
            try {
                client(fake).addMovie(27205)
                fail("expected NotConfigured")
            } catch (_: ArrException.NotConfigured) {
            }
            assertEquals(0, fake.requests.size)
        }

    @Test
    fun queue_records() =
        runTest(testDispatcher) {
            val fake = FakeArr(mapOf("/api/v3/queue" to (200 to fixture("radarr_queue.json"))))
            val q = client(fake).queue()
            assertEquals(listOf(12, 99), q.map { it.movieId })
            assertEquals(250.0, q[0].sizeleft, 0.0)
            assertEquals(
                "true",
                fake.requests
                    .single()
                    .url
                    .queryParameter("includeMovie"),
            )
            assertEquals(
                "200",
                fake.requests
                    .single()
                    .url
                    .queryParameter("pageSize"),
            )
        }
}
