package tv.jellyvault.app.ui.library

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.R
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.JellyfinUser
import tv.jellyvault.app.services.NavDrawerService
import tv.jellyvault.app.ui.nav.NavDrawerItem
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.util.UUID

class MyLibraryViewModelTest {
    private fun lib(type: CollectionType) =
        tv.jellyvault.app.ui.main.settings.Library(
            itemId = UUID.randomUUID(),
            name = type.name,
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = type,
            isRecordingFolder = false,
        )

    @Test
    fun tabs_for_movies_and_series_libraries() {
        val movies = lib(CollectionType.MOVIES)
        val shows = lib(CollectionType.TVSHOWS)
        val tabs = myLibraryTabs(listOf(lib(CollectionType.MUSIC), shows, movies, lib(CollectionType.MOVIES)))
        assertEquals(listOf(R.string.my_library_movies, R.string.my_library_series), tabs.map { it.title })
        assertEquals(listOf(movies.itemId, shows.itemId), tabs.map { it.library.itemId })
        assertEquals(listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES), tabs.map { it.itemKind })
    }

    @Test
    fun missing_library_hides_tab() {
        val tabs = myLibraryTabs(listOf(lib(CollectionType.MUSIC), lib(CollectionType.TVSHOWS)))
        assertEquals(listOf(R.string.my_library_series), tabs.map { it.title })
        assertTrue(myLibraryTabs(emptyList()).isEmpty())
    }

    @Test
    fun menu_has_my_library_instead_of_favorites() {
        val builtins = NavDrawerService.builtinItems()
        assertTrue(builtins.contains(NavDrawerItem.MyLibrary))
        assertFalse(builtins.contains(NavDrawerItem.Favorites))
        assertEquals("a_mylibrary", NavDrawerItem.MyLibrary.id)
    }

    private fun viewModel(libraries: List<tv.jellyvault.app.ui.main.settings.Library>?): MyLibraryViewModel {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
        val nav = mockk<NavDrawerService>()
        val servers = mockk<ServerRepository>()
        every { servers.currentUser } returns
            JellyfinUser(id = UUID.randomUUID(), name = "u", serverId = UUID.randomUUID(), accessToken = "t")
        every { servers.currentUserDto } returns null
        if (libraries == null) {
            coEvery { nav.getAllUserLibraries(any(), any()) } throws RuntimeException("server down")
        } else {
            coEvery { nav.getAllUserLibraries(any(), any()) } returns libraries
        }
        return MyLibraryViewModel(nav, servers, mockk(relaxed = true))
    }

    @Test
    fun no_movies_or_series_library_is_empty_state() {
        try {
            assertEquals(MyLibraryState.Empty, viewModel(listOf(lib(CollectionType.MUSIC))).state.value)
        } finally {
            Dispatchers.resetMain()
            JvDispatchers.reset()
        }
    }

    @Test
    fun failure_is_error_state_and_retry_recovers() {
        try {
            val vm = viewModel(null)
            assertTrue(vm.state.value is MyLibraryState.Error)
        } finally {
            Dispatchers.resetMain()
            JvDispatchers.reset()
        }
    }
}
