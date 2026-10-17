package tv.jellyvault.app.catalog.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class TmdbSeason(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val airDate: String?,
)

data class TmdbCastMember(
    val name: String,
    val character: String?,
    val profileUrl: String?,
)

data class TmdbNamed(
    val id: Int,
    val name: String,
)

/**
 * Everything the TMDB detail page needs, from a single TMDB call
 */
data class TmdbDetails(
    val item: TmdbItem,
    val runtimeMinutes: Int?,
    val seasons: List<TmdbSeason>,
    val genres: List<String>,
    val tagline: String?,
    val certification: String?,
    val logoUrl: String?,
    val trailerKey: String?,
    val recommendations: List<TmdbItem>,
    val tvdbId: Int?,
    val cast: List<TmdbCastMember> = emptyList(),
    val collection: TmdbNamed? = null,
    val studio: TmdbNamed? = null,
    val network: TmdbNamed? = null,
    /** Directors for movies, creators for series */
    val makers: List<String> = emptyList(),
    val releaseDate: String? = null,
    val countries: List<String> = emptyList(),
    val budget: Long? = null,
    val episodeCount: Int? = null,
    val similarTitles: List<TmdbItem> = emptyList(),
    /** tt…, to open the title on IMDb */
    val imdbId: String? = null,
    /** TMDB genre ids of the title, to keep its "similar" rows in the same kind of film */
    val genreIds: List<Int> = emptyList(),
    /** The first director of a movie, for a "Directed by" row */
    val director: TmdbNamed? = null,
) {
    val trailerUrl: String? get() = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }
}

@Serializable
internal data class TmdbRawVideo(
    val key: String,
    val site: String? = null,
    val type: String? = null,
    @SerialName("iso_639_1") val language: String? = null,
)

@Serializable
internal data class TmdbRawVideos(
    val results: List<TmdbRawVideo> = emptyList(),
)

@Serializable
internal data class TmdbRawGenre(
    val id: Int = 0,
    val name: String,
)

@Serializable
internal data class TmdbRawNamed(
    val id: Int = 0,
    val name: String = "",
)

@Serializable
internal data class TmdbRawCast(
    val name: String,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = Int.MAX_VALUE,
)

@Serializable
internal data class TmdbRawCrew(
    val id: Int = 0,
    val name: String,
    val job: String? = null,
)

@Serializable
internal data class TmdbRawCredits(
    val cast: List<TmdbRawCast> = emptyList(),
    val crew: List<TmdbRawCrew> = emptyList(),
)

@Serializable
internal data class TmdbRawCountry(
    val name: String = "",
)

@Serializable
internal data class TmdbRawCreator(
    val name: String = "",
)

@Serializable
internal data class TmdbRawImage(
    @SerialName("file_path") val filePath: String,
    @SerialName("iso_639_1") val language: String? = null,
)

@Serializable
internal data class TmdbRawImages(
    val logos: List<TmdbRawImage> = emptyList(),
)

@Serializable
internal data class TmdbRawExternalIds(
    @SerialName("tvdb_id") val tvdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
internal data class TmdbRawSeason(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
internal data class TmdbRawReleaseDate(
    val certification: String? = null,
)

@Serializable
internal data class TmdbRawReleaseCountry(
    @SerialName("iso_3166_1") val country: String,
    @SerialName("release_dates") val releaseDates: List<TmdbRawReleaseDate> = emptyList(),
)

@Serializable
internal data class TmdbRawReleaseDates(
    val results: List<TmdbRawReleaseCountry> = emptyList(),
)

@Serializable
internal data class TmdbRawContentRating(
    @SerialName("iso_3166_1") val country: String,
    val rating: String? = null,
)

@Serializable
internal data class TmdbRawContentRatings(
    val results: List<TmdbRawContentRating> = emptyList(),
)

@Serializable
internal data class TmdbRawDetails(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    val tagline: String? = null,
    val genres: List<TmdbRawGenre> = emptyList(),
    val seasons: List<TmdbRawSeason> = emptyList(),
    val videos: TmdbRawVideos = TmdbRawVideos(),
    val recommendations: TmdbPage = TmdbPage(),
    val images: TmdbRawImages = TmdbRawImages(),
    @SerialName("external_ids") val externalIds: TmdbRawExternalIds = TmdbRawExternalIds(),
    @SerialName("release_dates") val releaseDates: TmdbRawReleaseDates = TmdbRawReleaseDates(),
    @SerialName("content_ratings") val contentRatings: TmdbRawContentRatings = TmdbRawContentRatings(),
    val budget: Long? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("belongs_to_collection") val collection: TmdbRawNamed? = null,
    @SerialName("production_companies") val companies: List<TmdbRawNamed> = emptyList(),
    @SerialName("production_countries") val countries: List<TmdbRawCountry> = emptyList(),
    val networks: List<TmdbRawNamed> = emptyList(),
    @SerialName("created_by") val createdBy: List<TmdbRawCreator> = emptyList(),
    val credits: TmdbRawCredits = TmdbRawCredits(),
    val similar: TmdbPage = TmdbPage(),
) {
    fun toDetails(
        mediaType: TmdbMediaType,
        language: TmdbLanguage,
    ): TmdbDetails? {
        val item =
            TmdbRawItem(
                id = id,
                mediaType = mediaType.path,
                title = title,
                name = name,
                overview = overview,
                posterPath = posterPath,
                backdropPath = backdropPath,
                releaseDate = releaseDate,
                firstAirDate = firstAirDate,
                voteAverage = voteAverage,
                genreIds = emptyList(),
            ).toItem(mediaType) ?: return null
        return TmdbDetails(
            item = item,
            runtimeMinutes = runtime?.takeIf { it > 0 },
            seasons =
                seasons
                    .filter { it.seasonNumber > 0 }
                    .sortedBy { it.seasonNumber }
                    .map { TmdbSeason(it.seasonNumber, it.name ?: "", it.episodeCount, it.airDate) },
            genres = genres.map { it.name },
            tagline = tagline?.takeIf { it.isNotBlank() },
            certification = pickCertification(mediaType, releaseDates, contentRatings),
            logoUrl = pickLogoPath(images.logos, language.mediaLanguages)?.let { "${TmdbItem.IMAGE_BASE_URL}/w500$it" },
            trailerKey = pickTrailerKey(videos.results, language.mediaLanguages),
            recommendations = recommendations.results.mapNotNull { it.toItem(mediaType) },
            tvdbId = externalIds.tvdbId?.takeIf { it > 0 },
            imdbId = externalIds.imdbId?.takeIf { it.startsWith("tt") },
            cast =
                credits.cast.sortedBy { it.order }.take(MAX_CAST).map {
                    TmdbCastMember(it.name, it.character, it.profilePath?.let { p -> "${TmdbItem.IMAGE_BASE_URL}/w185$p" })
                },
            collection = collection?.takeIf { it.id > 0 && it.name.isNotBlank() }?.let { TmdbNamed(it.id, it.name) },
            studio = companies.firstOrNull { it.id > 0 && it.name.isNotBlank() }?.let { TmdbNamed(it.id, it.name) },
            network = networks.firstOrNull { it.id > 0 && it.name.isNotBlank() }?.let { TmdbNamed(it.id, it.name) },
            makers =
                if (mediaType == TmdbMediaType.MOVIE) {
                    credits.crew
                        .filter { it.job == "Director" }
                        .map { it.name }
                        .distinct()
                } else {
                    createdBy.map { it.name }.filter { it.isNotBlank() }
                },
            releaseDate = (releaseDate ?: firstAirDate)?.takeIf { it.isNotBlank() },
            countries = countries.map { it.name }.filter { it.isNotBlank() },
            budget = budget?.takeIf { it > 0 },
            episodeCount = numberOfEpisodes?.takeIf { it > 0 },
            similarTitles = similar.results.mapNotNull { it.toItem(mediaType) },
            genreIds = genres.map { it.id }.filter { it > 0 },
            director =
                if (mediaType == TmdbMediaType.MOVIE) {
                    credits.crew.firstOrNull { it.job == "Director" && it.id > 0 }?.let { TmdbNamed(it.id, it.name) }
                } else {
                    null
                },
        )
    }

    companion object {
        const val MAX_CAST = 15
    }
}

/**
 * A YouTube trailer, else teaser, in the first of [languages] that has one
 */
internal fun pickTrailerKey(
    videos: List<TmdbRawVideo>,
    languages: List<String>,
): String? {
    val youtube = videos.filter { it.site.equals("YouTube", ignoreCase = true) }
    for (type in listOf("Trailer", "Teaser")) {
        for (lang in languages) {
            youtube.firstOrNull { it.type == type && it.language == lang }?.let { return it.key }
        }
    }
    return null
}

/**
 * A title logo in the first of [languages] that has one, else a logo without text
 */
internal fun pickLogoPath(
    logos: List<TmdbRawImage>,
    languages: List<String>,
): String? =
    languages.firstNotNullOfOrNull { lang -> logos.firstOrNull { it.language == lang }?.filePath }
        ?: logos.firstOrNull { it.language == null }?.filePath

internal fun pickCertification(
    mediaType: TmdbMediaType,
    releaseDates: TmdbRawReleaseDates,
    contentRatings: TmdbRawContentRatings,
): String? {
    fun movie(country: String) =
        releaseDates.results
            .firstOrNull { it.country == country }
            ?.releaseDates
            ?.mapNotNull { it.certification?.takeIf { c -> c.isNotBlank() } }
            ?.firstOrNull()

    fun tv(country: String) =
        contentRatings.results
            .firstOrNull { it.country == country }
            ?.rating
            ?.takeIf { it.isNotBlank() }
    return if (mediaType == TmdbMediaType.MOVIE) movie("FR") ?: movie("US") else tv("FR") ?: tv("US")
}
