package tv.jellyvault.app.ui.preferences.arr

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.arr.ArrConfig
import tv.jellyvault.app.catalog.arr.ArrException
import tv.jellyvault.app.catalog.arr.ArrService
import tv.jellyvault.app.catalog.arr.ArrSettingsRepository
import tv.jellyvault.app.catalog.arr.QualityProfile
import tv.jellyvault.app.catalog.arr.RadarrClient
import tv.jellyvault.app.catalog.arr.RootFolder
import tv.jellyvault.app.catalog.arr.SonarrClient
import tv.jellyvault.app.catalog.arr.SystemStatus
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset

@OptIn(ExperimentalCoroutinesApi::class)
class ArrSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val stored = MutableStateFlow(ArrConfig(url = "https://arr.example.test/radarr", apiKey = "k"))
    private val repository = mockk<ArrSettingsRepository>()
    private val radarr = mockk<RadarrClient>()
    private val sonarr = mockk<SonarrClient>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
        every { repository.config(ArrService.RADARR) } returns stored
        coEvery { repository.current(ArrService.RADARR) } answers { stored.value }
        val transform = slot<(ArrConfig) -> ArrConfig>()
        coEvery { repository.update(ArrService.RADARR, capture(transform)) } answers { stored.value = transform.captured(stored.value) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        JvDispatchers.reset()
    }

    private fun viewModel() = ArrSettingsViewModel(repository, radarr, sonarr).also { it.init(ArrService.RADARR) }

    @Test
    fun test_connection_success_loads_profiles_and_folders() =
        runTest(dispatcher) {
            coEvery { radarr.systemStatus(any()) } returns SystemStatus("6.0.4")
            coEvery { radarr.qualityProfiles(any()) } returns listOf(QualityProfile(4, "HD-1080p"))
            coEvery { radarr.rootFolders(any()) } returns listOf(RootFolder("/m", 10L))
            val vm = viewModel()
            vm.testConnection()
            val state = vm.state.value
            assertEquals(ArrSettingsViewModel.Connection.Connected("6.0.4"), state.connection)
            assertEquals(listOf(QualityProfile(4, "HD-1080p")), state.profiles)
            assertEquals(listOf(RootFolder("/m", 10L)), state.folders)
        }

    @Test
    fun test_connection_failure_shows_error() =
        runTest(dispatcher) {
            coEvery { radarr.systemStatus(any()) } throws ArrException.Unauthorized(401)
            val vm = viewModel()
            vm.testConnection()
            assertEquals(ArrSettingsViewModel.Connection.Failed(R.string.arr_error_unauthorized), vm.state.value.connection)
        }

    @Test
    fun choosing_profile_and_folder_saves_them() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.selectProfile(4)
            vm.selectFolder("/m")
            assertEquals(4, stored.value.qualityProfileId)
            assertEquals("/m", stored.value.rootFolderPath)
            assertEquals(stored.value, vm.state.value.config)
        }

    @Test
    fun editing_url_saves_trimmed_value_and_resets_connection() =
        runTest(dispatcher) {
            coEvery { radarr.systemStatus(any()) } returns SystemStatus("6.0.4")
            coEvery { radarr.qualityProfiles(any()) } returns emptyList()
            coEvery { radarr.rootFolders(any()) } returns emptyList()
            val vm = viewModel()
            vm.testConnection()
            vm.setUrl("  https://arr.example.test/other  ")
            assertEquals("https://arr.example.test/other", stored.value.url)
            assertEquals(ArrSettingsViewModel.Connection.Unknown, vm.state.value.connection)
        }

    @Test
    fun url_without_scheme_gets_https() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.setUrl("arr.example.test/radarr")
            assertEquals("https://arr.example.test/radarr", stored.value.url)
            vm.setUrl("http://10.0.0.2:7878")
            assertEquals("http://10.0.0.2:7878", stored.value.url)
        }

    @Test
    fun changing_server_resets_profile_and_folder_but_same_url_keeps_them() =
        runTest(dispatcher) {
            stored.value = stored.value.copy(qualityProfileId = 4, rootFolderPath = "/m")
            val vm = viewModel()
            vm.setUrl("https://arr.example.test/radarr")
            assertEquals(4, stored.value.qualityProfileId)
            vm.setUrl("https://other.example.test/radarr")
            assertEquals(0, stored.value.qualityProfileId)
            assertEquals("", stored.value.rootFolderPath)
        }
}
