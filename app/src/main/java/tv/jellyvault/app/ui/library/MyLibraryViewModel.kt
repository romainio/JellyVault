package tv.jellyvault.app.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.services.NavDrawerService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.tvAccess
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.main.settings.Library
import javax.inject.Inject

data class MyLibraryTab(
    @param:StringRes val title: Int,
    val library: Library,
    val itemKind: BaseItemKind,
)

/**
 * Tabs of "Ma bibliothèque": the first movies library and the first TV shows library, if any
 */
fun myLibraryTabs(libraries: List<Library>): List<MyLibraryTab> =
    listOfNotNull(
        libraries.firstOrNull { it.collectionType == CollectionType.MOVIES }?.let {
            MyLibraryTab(R.string.my_library_movies, it, BaseItemKind.MOVIE)
        },
        libraries.firstOrNull { it.collectionType == CollectionType.TVSHOWS }?.let {
            MyLibraryTab(R.string.my_library_series, it, BaseItemKind.SERIES)
        },
    )

sealed interface MyLibraryState {
    data object Loading : MyLibraryState

    data class Tabs(
        val tabs: List<MyLibraryTab>,
    ) : MyLibraryState

    /** No movies or TV shows library on the server */
    data object Empty : MyLibraryState

    data class Error(
        val exception: Throwable,
    ) : MyLibraryState
}

@HiltViewModel
class MyLibraryViewModel
    @Inject
    constructor(
        private val navDrawerService: NavDrawerService,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow<MyLibraryState>(MyLibraryState.Loading)
        val state: StateFlow<MyLibraryState> = _state

        init {
            load()
        }

        fun retry() = load()

        private fun load() {
            _state.value = MyLibraryState.Loading
            viewModelScope.launchIO {
                _state.value =
                    try {
                        val user = serverRepository.currentUser ?: throw IllegalStateException("No user")
                        val tvAccess = serverRepository.currentUserDto?.tvAccess ?: false
                        val tabs = myLibraryTabs(navDrawerService.getAllUserLibraries(user.id, tvAccess))
                        if (tabs.isEmpty()) MyLibraryState.Empty else MyLibraryState.Tabs(tabs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Could not load libraries")
                        MyLibraryState.Error(e)
                    }
            }
        }
    }
