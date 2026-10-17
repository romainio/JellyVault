package tv.jellyvault.app.catalog.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TmdbMediaType(
    val path: String,
) {
    MOVIE("movie"),
    TV("tv"),
}

/**
 * A movie or series from TMDB, already filtered to be displayable (has a title and a poster)
 */
@Serializable
data class TmdbItem(
    val tmdbId: Int,
    val mediaType: TmdbMediaType,
    val title: String,
    val overview: String?,
    val posterPath: String,
    val backdropPath: String?,
    val releaseDate: String?,
    val voteAverage: Double?,
    val genreIds: List<Int>,
    /** ISO 639-1 code of the original language, null when TMDB does not say */
    val originalLanguage: String? = null,
    val voteCount: Int = 0,
    val popularity: Double = 0.0,
) {
    val year: Int? get() = releaseDate?.take(4)?.toIntOrNull()

    /** Cards are about 230 px wide and grow on focus: w342 looked soft once scaled */
    val posterUrl: String get() = "$IMAGE_BASE_URL/w500$posterPath"

    /** The home and browse headers draw it full-screen: w780 looked soft on a 1080p TV */
    val backdropUrl: String? get() = backdropPath?.let { "$IMAGE_BASE_URL/w1280$it" }

    /** Full-screen backdrop of the title page */
    val detailBackdropUrl: String? get() = backdropUrl

    companion object {
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p"
    }
}

class TmdbException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
internal data class TmdbPage(
    val results: List<TmdbRawItem> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
)

enum class TmdbListType {
    POPULAR,
    TOP_RATED,

    /** Movies only */
    NOW_PLAYING,

    /** Series only */
    ON_THE_AIR,
}

/**
 * One page of TMDB results, already filtered to displayable movies and series
 */
data class TmdbResultPage(
    val items: List<TmdbItem>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
internal data class TmdbRawItem(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
) {
    /**
     * @param requestedType the type that was queried, used when the response has no media_type (discover endpoints)
     * @return null if this entry cannot be displayed
     */
    fun toItem(requestedType: TmdbMediaType): TmdbItem? {
        val type =
            when (mediaType) {
                null -> requestedType
                TmdbMediaType.MOVIE.path -> TmdbMediaType.MOVIE
                TmdbMediaType.TV.path -> TmdbMediaType.TV
                else -> return null
            }
        val displayTitle = (title ?: name)?.takeIf { it.isNotBlank() } ?: return null
        val poster = posterPath ?: return null
        return TmdbItem(
            tmdbId = id,
            mediaType = type,
            title = displayTitle,
            overview = overview?.takeIf { it.isNotBlank() },
            posterPath = poster,
            backdropPath = backdropPath,
            releaseDate = (releaseDate ?: firstAirDate)?.takeIf { it.isNotBlank() },
            voteAverage = voteAverage,
            genreIds = genreIds,
            originalLanguage = originalLanguage?.takeIf { it.isNotBlank() },
            voteCount = voteCount,
            popularity = popularity,
        )
    }
}
