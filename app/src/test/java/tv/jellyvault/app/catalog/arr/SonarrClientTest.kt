package tv.jellyvault.app.catalog.arr

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.catalog.arr.FakeArr.Companion.fixture
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset

@OptIn(ExperimentalCoroutinesApi::class)
class SonarrClientTest {
    private val testDispatcher = StandardTestDispatcher()
    private val settings = mockk<ArrSettingsRepository>()
    private val configured =
        ArrConfig(url = "https://arr.example.test/sonarr", apiKey = "K", qualityProfileId = 1, rootFolderPath = "/data/tv")

    @Before
    fun setUp() {
        JvDispatchers.configure(testDispatcher)
        coEvery { settings.current(ArrService.SONARR) } returns configured
    }

    @After
    fun tearDown() = JvDispatchers.reset()

    private fun client(fake: FakeArr) = SonarrClient(ArrHttp(OkHttpClient.Builder().addInterceptor(fake).build()), settings)

    @Test
    fun series_by_tvdb_id_parses_seasons() =
        runTest(testDispatcher) {
            val fake = FakeArr(mapOf("GET /api/v3/series" to (200 to fixture("sonarr_series_list.json"))))
            val s = client(fake).seriesByTvdbId(81189)!!
            assertEquals(13, s.id)
            assertEquals(listOf(0, 1, 2), s.seasons.map { it.seasonNumber })
            assertEquals(100.0, s.seasons[1].statistics!!.percentOfEpisodes, 0.0)
            assertEquals(
                "81189",
                fake.requests
                    .single()
                    .url
                    .queryParameter("tvdbId"),
            )
        }

    @Test
    fun add_series_monitors_only_selected_seasons_without_monitor_option() =
        runTest(testDispatcher) {
            val fake =
                FakeArr(
                    mapOf(
                        "GET /series/lookup" to (200 to fixture("sonarr_series_lookup.json")),
                        "POST /api/v3/series" to (201 to """{"id": 20, "tvdbId": 81189, "monitored": true, "seasons": []}"""),
                    ),
                )
            val s = client(fake).addSeries(81189, setOf(2))
            assertEquals(20, s.id)
            assertEquals("tvdb:81189", fake.requests[0].url.queryParameter("term"))

            val posted = Json.parseToJsonElement(fake.bodies[1]!!).jsonObject
            val seasons = posted["seasons"]!!.jsonArray.map { it.jsonObject }
            assertEquals(
                mapOf(0 to false, 1 to false, 2 to true),
                seasons.associate { it["seasonNumber"]!!.jsonPrimitive.int to it["monitored"]!!.jsonPrimitive.boolean },
            )
            assertEquals(1, posted["qualityProfileId"]!!.jsonPrimitive.int)
            assertEquals("/data/tv", posted["rootFolderPath"]!!.jsonPrimitive.content)
            assertEquals(true, posted["monitored"]!!.jsonPrimitive.boolean)
            assertEquals(true, posted["seasonFolder"]!!.jsonPrimitive.boolean)
            val addOptions = posted["addOptions"]!!.jsonObject
            assertEquals(true, addOptions["searchForMissingEpisodes"]!!.jsonPrimitive.boolean)
            assertFalse(addOptions.containsKey("monitor"))
        }

    @Test
    fun monitor_seasons_adds_to_existing_and_searches_each_new_season() =
        runTest(testDispatcher) {
            val fake =
                FakeArr(
                    mapOf(
                        "GET /api/v3/series/13" to
                            (200 to Json.parseToJsonElement(fixture("sonarr_series_list.json")).jsonArray[0].toString()),
                        "PUT /api/v3/series/13" to (202 to "{}"),
                        "POST /api/v3/command" to (201 to "{}"),
                    ),
                )
            client(fake).monitorSeasons(13, setOf(2))

            val put = Json.parseToJsonElement(fake.bodies[1]!!).jsonObject
            val monitored =
                put["seasons"]!!.jsonArray.associate {
                    it.jsonObject["seasonNumber"]!!.jsonPrimitive.int to it.jsonObject["monitored"]!!.jsonPrimitive.boolean
                }
            assertEquals(mapOf(0 to false, 1 to true, 2 to true), monitored)
            assertEquals("/data/tv/Breaking Bad", put["path"]!!.jsonPrimitive.content)

            val command = Json.parseToJsonElement(fake.bodies[2]!!).jsonObject
            assertEquals("SeasonSearch", command["name"]!!.jsonPrimitive.content)
            assertEquals(13, command["seriesId"]!!.jsonPrimitive.int)
            assertEquals(2, command["seasonNumber"]!!.jsonPrimitive.int)
            assertEquals(3, fake.requests.size)
        }

    @Test
    fun queue_uses_include_series() =
        runTest(testDispatcher) {
            val fake = FakeArr(mapOf("/api/v3/queue" to (200 to fixture("sonarr_queue.json"))))
            assertEquals(2, client(fake).queue().size)
            assertEquals(
                "true",
                fake.requests
                    .single()
                    .url
                    .queryParameter("includeSeries"),
            )
        }

    @Test
    fun lookup_seasons_use_sonarr_numbering() =
        runTest(testDispatcher) {
            val fake = FakeArr(mapOf("GET /series/lookup" to (200 to fixture("sonarr_series_lookup.json"))))
            assertEquals(listOf(0, 1, 2), client(fake).lookupSeasons(81189).map { it.seasonNumber })
            assertEquals(
                "tvdb:81189",
                fake.requests
                    .single()
                    .url
                    .queryParameter("term"),
            )
        }
}
