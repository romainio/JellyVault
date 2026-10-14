package tv.jellyvault.app.catalog.arr

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrSettingsRepositoryTest {
    private class MemoryDataStore : DataStore<ArrSettings> {
        private val state = MutableStateFlow(ArrSettings.getDefaultInstance())
        override val data: Flow<ArrSettings> = state

        override suspend fun updateData(transform: suspend (t: ArrSettings) -> ArrSettings): ArrSettings {
            state.value = transform(state.value)
            return state.value
        }
    }

    private val repo = ArrSettingsRepository(MemoryDataStore())

    @Test
    fun defaults_are_empty_and_not_configured() =
        runTest {
            val c = repo.current(ArrService.RADARR)
            assertEquals("", c.url)
            assertFalse(c.canConnect)
            assertFalse(c.isConfigured)
        }

    @Test
    fun round_trip_per_service() =
        runTest {
            repo.update(ArrService.RADARR) {
                it.copy(url = "https://arr.example.test/radarr/", apiKey = "k", qualityProfileId = 4, rootFolderPath = "/m")
            }
            repo.update(ArrService.SONARR) { it.copy(url = "https://arr.example.test/sonarr", apiKey = "s") }

            val r = repo.config(ArrService.RADARR).first()
            assertEquals("https://arr.example.test/radarr/", r.url)
            assertEquals(4, r.qualityProfileId)
            assertTrue(r.isConfigured)
            assertEquals("s", repo.current(ArrService.SONARR).apiKey)
            assertEquals("", repo.current(ArrService.SONARR).rootFolderPath)
        }

    @Test
    fun incomplete_settings_are_not_configured() =
        runTest {
            repo.update(ArrService.SONARR) { it.copy(url = "https://arr.example.test/sonarr", apiKey = "s") }
            val c = repo.current(ArrService.SONARR)
            assertTrue(c.canConnect)
            assertFalse(c.isConfigured)
        }
}
