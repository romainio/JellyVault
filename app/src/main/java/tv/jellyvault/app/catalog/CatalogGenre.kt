package tv.jellyvault.app.catalog

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import tv.jellyvault.app.R

@Serializable
enum class CatalogMediaType {
    MOVIE,
    TV,
    BOTH,
}

/**
 * Genres available for catalog rows. TMDB uses different genre IDs for movies and series, null means no series equivalent.
 */
@Serializable
enum class CatalogGenre(
    val movieGenreId: Int,
    val tvGenreId: Int?,
    @param:StringRes val titleRes: Int,
    /** Only titles from one of these studios (TMDB company ids, "or"), e.g. feature cartoons */
    val companyIds: List<Int> = emptyList(),
) {
    ACTION(28, 10759, R.string.catalog_genre_action),
    COMEDY(35, 35, R.string.catalog_genre_comedy),
    THRILLER(53, 9648, R.string.catalog_genre_thriller),
    SCIENCE_FICTION(878, 10765, R.string.catalog_genre_science_fiction),
    ANIMATION(16, 16, R.string.catalog_genre_animation),
    CARTOONS(16, null, R.string.catalog_genre_cartoons, listOf(6125, 3, 10342, 521, 6704, 2251, 9383, 11537, 297)),
    DRAMA(18, 18, R.string.catalog_genre_drama),
    DOCUMENTARY(99, 99, R.string.catalog_genre_documentary),
    HORROR(27, null, R.string.catalog_genre_horror),
    ROMANCE(10749, null, R.string.catalog_genre_romance),
    CRIME(80, 80, R.string.catalog_genre_crime),
    FAMILY(10751, 10751, R.string.catalog_genre_family),
    FANTASY(14, 10765, R.string.catalog_genre_fantasy),
    WAR(10752, 10768, R.string.catalog_genre_war),
    WESTERN(37, 37, R.string.catalog_genre_western),
    ;

    companion object {
        val DEFAULTS = listOf(ACTION, COMEDY, THRILLER, SCIENCE_FICTION, ANIMATION, DRAMA, DOCUMENTARY)
    }
}
