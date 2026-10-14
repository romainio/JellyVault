package tv.jellyvault.app.catalog.arr

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
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
class ArrHttpTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = JvDispatchers.configure(testDispatcher)

    @After
    fun tearDown() = JvDispatchers.reset()

    private fun http(fake: FakeArr) = ArrHttp(OkHttpClient.Builder().addInterceptor(fake).build())

    private val config = ArrConfig(url = "https://arr.example.test/radarr/", apiKey = "KEY")

    @Test
    fun base_url_with_sub_path_and_trailing_slash() =
        runTest(testDispatcher) {
            val fake = FakeArr(mapOf("/radarr/api/v3/system/status" to (200 to "{}")))
            http(fake).get(config, "system/status")
            assertEquals(
                "https://arr.example.test/radarr/api/v3/system/status",
                fake.requests
                    .single()
                    .url
                    .toString(),
            )
            http(fake).get(config.copy(url = "https://arr.example.test/radarr"), "system/status")
            assertEquals("https://arr.example.test/radarr/api/v3/system/status", fake.requests[1].url.toString())
        }

    @Test
    fun sends_api_key_and_basic_auth_only_when_user_set() =
        runTest(testDispatcher) {
            val fake = FakeArr(mapOf("/api/v3/system/status" to (200 to "{}")))
            http(fake).get(config, "system/status")
            assertEquals("KEY", fake.requests[0].header("X-Api-Key"))
            assertNull(fake.requests[0].header("Authorization"))

            http(fake).get(config.copy(basicUser = "u", basicPassword = "p"), "system/status")
            assertEquals("Basic dTpw", fake.requests[1].header("Authorization"))
        }

    @Test
    fun errors_are_typed() =
        runTest(testDispatcher) {
            suspend fun expect(
                fake: FakeArr,
                check: (ArrException) -> Boolean,
            ) {
                try {
                    http(fake).get(config, "system/status")
                    fail("expected ArrException")
                } catch (e: ArrException) {
                    assertTrue(e.toString(), check(e))
                }
            }
            expect(FakeArr(mapOf("/system/status" to (401 to "")))) { it is ArrException.Unauthorized }
            expect(FakeArr(mapOf("/system/status" to (403 to "")))) { it is ArrException.Unauthorized }
            expect(FakeArr(mapOf("/system/status" to (500 to "")))) { it is ArrException.Http && it.code == 500 }
            expect(FakeArr(emptyMap(), failWith = IOException("down"))) { it is ArrException.Unreachable }
        }

    @Test
    fun blank_config_is_not_configured_and_sends_nothing() =
        runTest(testDispatcher) {
            val fake = FakeArr(emptyMap())
            try {
                http(fake).get(ArrConfig(), "system/status")
                fail("expected NotConfigured")
            } catch (_: ArrException.NotConfigured) {
            }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun basic_auth_rejection_is_distinguished_from_api_key_rejection() =
        runTest(testDispatcher) {
            val basic = FakeArr(mapOf("/system/status" to (401 to "")), headers = mapOf("WWW-Authenticate" to "Basic realm=\"Restricted\""))
            try {
                http(basic).get(config, "system/status")
                fail("expected Unauthorized")
            } catch (e: ArrException.Unauthorized) {
                assertTrue(e.basicAuth)
            }
            try {
                http(FakeArr(mapOf("/system/status" to (401 to "")))).get(config, "system/status")
                fail("expected Unauthorized")
            } catch (e: ArrException.Unauthorized) {
                assertEquals(false, e.basicAuth)
            }
        }

    @Test
    fun invalid_url_or_header_is_invalid_settings_not_a_crash() =
        runTest(testDispatcher) {
            val fake = FakeArr(emptyMap())
            try {
                http(fake).get(config.copy(url = "arr.example.test/radarr"), "system/status")
                fail("expected InvalidSettings")
            } catch (_: ArrException.InvalidSettings) {
            }
            try {
                http(fake).get(config.copy(apiKey = "clé"), "system/status")
                fail("expected InvalidSettings")
            } catch (_: ArrException.InvalidSettings) {
            }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun invisible_characters_from_the_tv_keyboard_are_ignored() =
        runTest(testDispatcher) {
            val fake = FakeArr(mapOf("/api/v3/system/status" to (200 to "{}")))
            http(fake).get(
                ArrConfig(url = " https://arr.example.test/radarr\u200B\n", apiKey = "KEY\n", basicUser = "u\t", basicPassword = "p\n"),
                "system/status",
            )
            val request = fake.requests.single()
            assertEquals("https://arr.example.test/radarr/api/v3/system/status", request.url.toString())
            assertEquals("KEY", request.header("X-Api-Key"))
            assertEquals("Basic dTpw", request.header("Authorization"))
        }
}
