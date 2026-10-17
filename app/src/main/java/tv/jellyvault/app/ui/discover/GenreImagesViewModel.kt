package tv.jellyvault.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.discoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.ui.launchIO
import javax.inject.Inject

/** Genre cards are about 400 px wide */
private const val CARD_BACKDROP_SIZE = "w780"

/** Only titles everyone knows: the picture should say the genre at a glance */
private const val PICTURE_MIN_VOTES = 1000
private const val STAND_UP_PICTURE_MIN_VOTES = 30

/**
 * Pictures of the Genres tab: the backdrop of a famous title of each genre, never the same title twice
 */
@HiltViewModel
class GenreImagesViewModel
    @Inject
    constructor(
        private val tmdb: TmdbClient,
    ) : ViewModel() {
        private val _images = MutableStateFlow<Map<CatalogGenre, String>>(emptyMap())
        val images: StateFlow<Map<CatalogGenre, String>> = _images

        private var mediaType: TmdbMediaType? = null

        fun init(
            mediaType: TmdbMediaType,
            genres: List<CatalogGenre>,
        ) {
            if (this.mediaType == mediaType || !tmdb.isConfigured) return
            this.mediaType = mediaType
            _images.value = emptyMap()
            // One genre after the other, so a title already shown is skipped for the next genres
            viewModelScope.launchIO {
                val used = mutableSetOf<Int>()
                genres.forEach { genre ->
                    val picked =
                        try {
                            candidates(genre, mediaType).firstOrNull { it.backdropPath != null && it.tmdbId !in used }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "No picture for %s", genre)
                            null
                        }
                    if (picked != null) {
                        used += picked.tmdbId
                        val url = "${TmdbItem.IMAGE_BASE_URL}/$CARD_BACKDROP_SIZE${picked.backdropPath}"
                        _images.update { it + (genre to url) }
                    }
                }
            }
        }

        private suspend fun candidates(
            genre: CatalogGenre,
            mediaType: TmdbMediaType,
        ): List<TmdbItem> {
            // The genre's own titles (its keywords for stand-up, no cartoons in live-action genres), famous ones only
            val base = genre.discoverQuery(mediaType) ?: return emptyList()
            val votes =
                when {
                    // Stand-up shows collect few votes
                    genre.fewVotes -> STAND_UP_PICTURE_MIN_VOTES

                    mediaType == TmdbMediaType.MOVIE -> PICTURE_MIN_VOTES

                    else -> PICTURE_MIN_VOTES / 3
                }
            return tmdb.discover(base.copy(minVotes = votes)).items
        }
    }
