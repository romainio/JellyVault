package tv.jellyvault.app.catalog.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A studio or network: its name and logo (logos are often dark on transparent)
 */
data class TmdbBrand(
    val name: String,
    val logoUrl: String?,
)

/**
 * A saga: its movies in release order
 */
data class TmdbCollection(
    val name: String,
    val backdropUrl: String?,
    val parts: List<TmdbItem>,
)

@Serializable
internal data class TmdbRawBrand(
    val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
) {
    fun toBrand() = TmdbBrand(name, logoPath?.let { "${TmdbItem.IMAGE_BASE_URL}/w300$it" })
}

@Serializable
internal data class TmdbRawCollection(
    val name: String = "",
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val parts: List<TmdbRawItem> = emptyList(),
) {
    fun toCollection() =
        TmdbCollection(
            name = name,
            backdropUrl = backdropPath?.let { "${TmdbItem.IMAGE_BASE_URL}/w780$it" },
            // Upcoming parts have no date yet: keep them, after the released ones
            parts =
                parts
                    .mapNotNull { it.toItem(TmdbMediaType.MOVIE) }
                    .sortedWith(compareBy(nullsLast()) { it.releaseDate }),
        )
}
