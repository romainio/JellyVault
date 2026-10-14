// The file holds ThemeRow plus the genreThemes() builders; renaming it would change the GenreThemesKt facade class name
@file:Suppress("ktlint:standard:filename")

package tv.jellyvault.app.catalog

import androidx.annotation.StringRes
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

/**
 * A themed row ("Romantic comedies", "Based on a true story"…): a TMDB discover query with its title
 */
data class ThemeRow(
    @param:StringRes val titleRes: Int,
    val query: TmdbDiscoverQuery,
)

/** TMDB keyword ids, checked against the TMDB keyword search */
private object Keyword {
    const val TRUE_STORY = 9672
    const val BIOGRAPHY = 5565
    const val HEIST = 10051
    const val SPY = 470
    const val SUPERHERO = 9715
    const val PSYCHOLOGICAL_THRILLER = 12565
    const val SERIAL_KILLER = 10714
    const val TIME_TRAVEL = 4379
    const val DYSTOPIA = 4565
    const val COMING_OF_AGE = 10683
    const val SATIRE = 8201
    const val BLACK_COMEDY = 383021
    const val BUDDY_COMEDY = 167541
    const val STAND_UP = 9716
    const val STAND_UP_SPECIAL = 376694
}

private const val ROMANCE = 10749
private const val COMEDY = 35
private const val CRIME = 80
private const val FAMILY = 10751
private const val HISTORY = 36
private const val DRAMA = 18

private const val THEME_MIN_VOTES_MOVIE = 300
private const val THEME_MIN_VOTES_TV = 100
private const val STAND_UP_MIN_VOTES = 50

private fun minVotes(type: TmdbMediaType) = if (type == TmdbMediaType.MOVIE) THEME_MIN_VOTES_MOVIE else THEME_MIN_VOTES_TV

private fun theme(
    @StringRes title: Int,
    type: TmdbMediaType,
    genreId: Int? = null,
    andGenreIds: List<Int> = emptyList(),
    keywordIds: List<Int> = emptyList(),
    originCountry: String? = null,
    minVotes: Int = minVotes(type),
) = ThemeRow(
    title,
    TmdbDiscoverQuery(
        type,
        genreId = genreId,
        andGenreIds = andGenreIds,
        keywordIds = keywordIds,
        originCountry = originCountry,
        minVotes = minVotes,
        mainstreamOnly = true,
    ),
)

/**
 * Coherent sub-lists shown under a genre page (Comedy: romantic comedies, French comedies, stand-up…).
 * Empty when the genre has nothing for [type] or is restricted to some studios (cartoons).
 */
fun genreThemes(
    genre: CatalogGenre,
    type: TmdbMediaType,
): List<ThemeRow> {
    if (genre.companyIds.isNotEmpty()) return emptyList()
    val id = (if (type == TmdbMediaType.MOVIE) genre.movieGenreId else genre.tvGenreId) ?: return emptyList()
    val movie = type == TmdbMediaType.MOVIE

    fun kw(
        title: Int,
        vararg keywords: Int,
        votes: Int = minVotes(type),
    ) = theme(title, type, id, keywordIds = keywords.toList(), minVotes = votes)

    fun with(
        title: Int,
        vararg genres: Int,
    ) = theme(title, type, id, andGenreIds = genres.toList())

    fun country(title: Int) = theme(title, type, id, originCountry = "FR")
    val trueStory = kw(R.string.theme_true_story, Keyword.TRUE_STORY)
    val serialKiller = kw(R.string.theme_serial_killer, Keyword.SERIAL_KILLER)
    return when (genre) {
        CatalogGenre.ACTION -> {
            if (movie) {
                listOf(
                    kw(R.string.theme_heist, Keyword.HEIST),
                    kw(R.string.theme_spy, Keyword.SPY),
                    kw(R.string.theme_superhero, Keyword.SUPERHERO),
                )
            } else {
                emptyList()
            }
        }

        CatalogGenre.COMEDY -> {
            if (movie) {
                listOf(
                    with(R.string.theme_romcom, ROMANCE),
                    country(R.string.theme_french_comedy),
                    kw(R.string.theme_dark_comedy, Keyword.SATIRE, Keyword.BLACK_COMEDY),
                    kw(R.string.theme_buddy, Keyword.BUDDY_COMEDY),
                    kw(R.string.theme_stand_up, Keyword.STAND_UP, Keyword.STAND_UP_SPECIAL, votes = STAND_UP_MIN_VOTES),
                    with(R.string.theme_family_comedy, FAMILY),
                )
            } else {
                listOf(country(R.string.theme_french_comedy))
            }
        }

        CatalogGenre.THRILLER -> {
            if (movie) {
                listOf(
                    kw(R.string.theme_psychological, Keyword.PSYCHOLOGICAL_THRILLER),
                    serialKiller,
                    with(R.string.theme_crime_thriller, CRIME),
                )
            } else {
                listOf(trueStory)
            }
        }

        CatalogGenre.SCIENCE_FICTION -> {
            listOf(kw(R.string.theme_time_travel, Keyword.TIME_TRAVEL), kw(R.string.theme_dystopia, Keyword.DYSTOPIA))
        }

        CatalogGenre.ANIMATION -> {
            if (movie) listOf(kw(R.string.theme_buddy, Keyword.BUDDY_COMEDY)) else emptyList()
        }

        CatalogGenre.DRAMA -> {
            if (movie) {
                listOf(
                    trueStory,
                    kw(R.string.theme_biopic, Keyword.BIOGRAPHY),
                    kw(R.string.theme_coming_of_age, Keyword.COMING_OF_AGE),
                    with(R.string.theme_romantic_drama, ROMANCE),
                    with(R.string.theme_historical, HISTORY),
                )
            } else {
                listOf(trueStory, country(R.string.theme_french_drama))
            }
        }

        CatalogGenre.DOCUMENTARY -> {
            listOf(kw(R.string.theme_biopic, Keyword.BIOGRAPHY))
        }

        CatalogGenre.HORROR -> {
            listOf(kw(R.string.theme_psychological, Keyword.PSYCHOLOGICAL_THRILLER), serialKiller)
        }

        CatalogGenre.ROMANCE -> {
            listOf(
                with(R.string.theme_romcom, COMEDY),
                kw(R.string.theme_coming_of_age, Keyword.COMING_OF_AGE),
                with(R.string.theme_romantic_drama, DRAMA),
            )
        }

        CatalogGenre.CRIME -> {
            if (movie) listOf(kw(R.string.theme_heist, Keyword.HEIST), serialKiller, trueStory) else listOf(trueStory, serialKiller)
        }

        CatalogGenre.FAMILY -> {
            listOf(kw(R.string.theme_buddy, Keyword.BUDDY_COMEDY), kw(R.string.theme_coming_of_age, Keyword.COMING_OF_AGE))
        }

        CatalogGenre.WAR -> {
            listOf(trueStory)
        }

        else -> {
            emptyList()
        }
    }
}
