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
    /** Only titles with one of these TMDB keywords: a genre TMDB does not have, e.g. stand-up shows */
    val keywordIds: List<Int> = emptyList(),
    /** Never titles with these keywords: stand-up shows are their own genre, not comedies */
    val withoutKeywordIds: List<Int> = emptyList(),
    /** Titles with few votes (stand-up shows): pages with lower vote floors */
    val fewVotes: Boolean = false,
) {
    ACTION(28, 10759, R.string.catalog_genre_action),
    SUPERHEROES(28, 10759, R.string.catalog_genre_superheroes, keywordIds = listOf(SUPERHERO)),
    ADVENTURE(12, null, R.string.catalog_genre_adventure),
    COMEDY(35, 35, R.string.catalog_genre_comedy, withoutKeywordIds = STAND_UP_KEYWORDS),
    THRILLER(53, 9648, R.string.catalog_genre_thriller),
    MYSTERY(9648, null, R.string.catalog_genre_mystery),
    SCIENCE_FICTION(878, 10765, R.string.catalog_genre_science_fiction),
    ANIMATION(16, 16, R.string.catalog_genre_animation),
    CARTOONS(16, null, R.string.catalog_genre_cartoons, listOf(6125, 3, 10342, 521, 6704, 2251, 9383, 11537, 297)),
    DRAMA(18, 18, R.string.catalog_genre_drama),
    TRUE_STORY(18, 18, R.string.catalog_genre_true_story, keywordIds = listOf(TRUE_STORY_KEYWORD)),
    DOCUMENTARY(99, 99, R.string.catalog_genre_documentary),
    STAND_UP(35, null, R.string.catalog_genre_stand_up, keywordIds = STAND_UP_KEYWORDS, fewVotes = true),
    HORROR(27, null, R.string.catalog_genre_horror),
    ROMANCE(10749, null, R.string.catalog_genre_romance),
    CRIME(80, 80, R.string.catalog_genre_crime),
    FAMILY(10751, 10751, R.string.catalog_genre_family),
    FANTASY(14, 10765, R.string.catalog_genre_fantasy),
    HISTORY(36, null, R.string.catalog_genre_history),
    WAR(10752, 10768, R.string.catalog_genre_war),
    SPORTS(18, null, R.string.catalog_genre_sports, keywordIds = listOf(SPORTS_KEYWORD)),
    MUSIC(10402, null, R.string.catalog_genre_music),
    WESTERN(37, 37, R.string.catalog_genre_western),
    ;

    companion object {
        val DEFAULTS = listOf(ACTION, COMEDY, THRILLER, SCIENCE_FICTION, ANIMATION, DRAMA, DOCUMENTARY)
    }
}

/** TMDB keywords "stand-up comedy" and "stand-up special" */
private val STAND_UP_KEYWORDS = listOf(9716, 376694)

/** TMDB keywords "superhero", "based on a true story", "sports" */
private const val SUPERHERO = 9715
private const val TRUE_STORY_KEYWORD = 9672
private const val SPORTS_KEYWORD = 6075
