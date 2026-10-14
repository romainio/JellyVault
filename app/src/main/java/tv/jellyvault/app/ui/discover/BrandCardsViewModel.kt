package tv.jellyvault.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.CatalogNetwork
import tv.jellyvault.app.catalog.CatalogSaga
import tv.jellyvault.app.catalog.CatalogStudio
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.ui.launchIO
import javax.inject.Inject

/**
 * A studio, network or saga card: the name shows until (or instead of) its image
 */
data class BrandCard(
    val target: BrowseTarget,
    val name: String,
    val imageUrl: String?,
    val isLogo: Boolean,
)

/**
 * Cards of the Studios & sagas (movies) or Networks (series) tab, with logos loaded from TMDB
 */
@HiltViewModel
class BrandCardsViewModel
    @Inject
    constructor(
        private val tmdb: TmdbClient,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _studios = MutableStateFlow<List<BrandCard>>(emptyList())

        /** Studios for movies, networks for series */
        val studios: StateFlow<List<BrandCard>> = _studios

        private val _sagas = MutableStateFlow<List<BrandCard>>(emptyList())
        val sagas: StateFlow<List<BrandCard>> = _sagas

        private var mediaType: TmdbMediaType? = null

        /** 18 cards at once would flood a small TV: same limit as the rows */
        private val semaphore = Semaphore(DiscoverViewModel.MAX_CONCURRENT_ROWS)

        fun init(mediaType: TmdbMediaType) {
            if (this.mediaType == mediaType) return
            this.mediaType = mediaType
            if (mediaType == TmdbMediaType.MOVIE) {
                _studios.value = CatalogStudio.entries.map { BrandCard(BrowseTarget.Studio(it), it.displayName, null, isLogo = true) }
                _sagas.value = CatalogSaga.entries.map { BrandCard(BrowseTarget.Saga(it), it.displayName, null, isLogo = false) }
                CatalogStudio.entries.forEachIndexed { i, s -> loadImage(_studios, i) { tmdb.company(s.companyId).logoUrl } }
                CatalogSaga.entries.forEachIndexed { i, s -> loadImage(_sagas, i) { tmdb.collection(s.collectionId).backdropUrl } }
            } else {
                _studios.value = CatalogNetwork.entries.map { BrandCard(BrowseTarget.Network(it), it.displayName, null, isLogo = true) }
                _sagas.value = emptyList()
                CatalogNetwork.entries.forEachIndexed { i, n -> loadImage(_studios, i) { tmdb.network(n.networkId).logoUrl } }
            }
        }

        private fun loadImage(
            cards: MutableStateFlow<List<BrandCard>>,
            index: Int,
            fetch: suspend () -> String?,
        ) {
            viewModelScope.launchIO {
                val url =
                    try {
                        semaphore.withPermit { fetch() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Brand image failed")
                        null
                    }
                if (url != null) cards.update { list -> list.toMutableList().also { it[index] = it[index].copy(imageUrl = url) } }
            }
        }
    }
