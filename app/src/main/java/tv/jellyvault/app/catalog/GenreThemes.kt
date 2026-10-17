// The file holds ThemeRow plus the genreThemes() builders; renaming it would change the GenreThemesKt facade class name
@file:Suppress("ktlint:standard:filename")

package tv.jellyvault.app.catalog

import androidx.annotation.StringRes
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbSort
import java.time.LocalDate

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
    const val MARTIAL_ARTS = 779
    const val SURVIVAL = 10349
    const val ROAD_MOVIE = 167043
    const val PARODY = 9755
    const val CHRISTMAS = 207317
    const val CONSPIRACY = 10410
    const val KIDNAPPING = 1930
    const val SPACE = 9882
    const val ARTIFICIAL_INTELLIGENCE = 310
    const val ALIEN = 9951
    const val POST_APOCALYPTIC = 4458
    const val COURTROOM = 33519
    const val SPORTS = 6075
    const val FAMILY_RELATIONSHIPS = 10235
    const val SLASHER = 12339
    const val HAUNTED_HOUSE = 3358
    const val ZOMBIE = 12377
    const val FOUND_FOOTAGE = 163053
    const val MAFIA = 10391
    const val PRISON = 378
    const val DETECTIVE = 703
    const val WORLD_WAR_II = 1956
    const val MAGIC = 2343
    const val DRAGON = 12554
    const val MUSICAL = 4344
    const val WEDDING = 13027
    const val REVENGE = 9748
}

/**
 * Keyword groups behind the styles the user's IMDb ratings point to (twist endings, scams, cartels...), shared by the
 * genre pages and the home styles
 */
internal object TasteKeywords {
    val TWISTS = listOf(326438, 275311, 362567, 174089, 157171, 11469)
    val SCAMS = listOf(10453, 11454, 4373, 5636)
    val CARTELS = listOf(10175, 2149)
    val RACING = listOf(830, 10039)
    val GENIUSES = listOf(6257, 2303, 14760)
    val AVIATION = listOf(2533, 10168)
    val SPECIAL_FORCES = listOf(15218, 1589, 11219, 379459)
}

/** TMDB keywords "stand-up comedy" and "stand-up special" */
private val STAND_UP = listOf(9716, 376694)

/**
 * The Hangover, Ted, Superbad: comedies made for adults (rated R), nothing but comedy, most seen first
 */
internal fun adultComedyQuery() =
    TmdbDiscoverQuery(
        TmdbMediaType.MOVIE,
        genreId = COMEDY,
        withoutGenreIds = listOf(ANIMATION, FAMILY, DRAMA, CRIME, ACTION, HORROR, ROMANCE, THRILLER, MYSTERY),
        withoutKeywordIds = STAND_UP,
        usCertification = "R",
        releasedAfter = LocalDate.of(2000, 1, 1),
        minVotes = ADULT_COMEDY_MIN_VOTES,
        sort = TmdbSort.VOTES,
        mainstreamOnly = true,
    )

private const val ADULT_COMEDY_MIN_VOTES = 1500
private const val HORROR = 27
private const val THRILLER = 53
private const val MYSTERY = 9648

private const val ROMANCE = 10749
private const val COMEDY = 35
private const val CRIME = 80
private const val FAMILY = 10751
private const val HISTORY = 36
private const val DRAMA = 18
private const val ACTION = 28
private const val FANTASY = 14
private const val ANIMATION = 16

/** Keyword rows of live-action genres match cartoons too (Cars 2 is a spy film): kept out */
private val NO_CARTOONS = listOf(ANIMATION, FAMILY)

private const val THEME_MIN_VOTES_MOVIE = 300
private const val THEME_MIN_VOTES_TV = 100
private const val DOCUMENTARY_MIN_VOTES = 50

private fun minVotes(type: TmdbMediaType) = if (type == TmdbMediaType.MOVIE) THEME_MIN_VOTES_MOVIE else THEME_MIN_VOTES_TV

private fun theme(
    @StringRes title: Int,
    type: TmdbMediaType,
    genreId: Int? = null,
    andGenreIds: List<Int> = emptyList(),
    keywordIds: List<Int> = emptyList(),
    originCountry: String? = null,
    minVotes: Int = minVotes(type),
    withoutGenreIds: List<Int> = emptyList(),
    withoutKeywordIds: List<Int> = emptyList(),
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
        // A row about one genre ("family comedies") keeps it
        withoutGenreIds = withoutGenreIds - andGenreIds.toSet(),
        withoutKeywordIds = withoutKeywordIds,
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
    if (genre.companyIds.isNotEmpty() || genre.keywordIds.isNotEmpty()) return emptyList()
    val id = (if (type == TmdbMediaType.MOVIE) genre.movieGenreId else genre.tvGenreId) ?: return emptyList()
    val movie = type == TmdbMediaType.MOVIE
    val without = if (genre == CatalogGenre.ANIMATION || genre == CatalogGenre.FAMILY) emptyList() else NO_CARTOONS

    fun kw(
        title: Int,
        vararg keywords: Int,
        votes: Int = minVotes(type),
    ) = theme(
        title,
        type,
        id,
        keywordIds = keywords.toList(),
        minVotes = votes,
        withoutGenreIds = without,
        withoutKeywordIds = genre.withoutKeywordIds,
    )

    fun with(
        title: Int,
        vararg genres: Int,
    ) = theme(title, type, id, andGenreIds = genres.toList(), withoutGenreIds = without, withoutKeywordIds = genre.withoutKeywordIds)

    fun country(title: Int) =
        theme(title, type, id, originCountry = "FR", withoutGenreIds = without, withoutKeywordIds = genre.withoutKeywordIds)

    fun kws(
        title: Int,
        keywords: List<Int>,
    ) = kw(title, *keywords.toIntArray())
    val twists = kws(R.string.theme_twists, TasteKeywords.TWISTS)
    val scams = kws(R.string.theme_scams, TasteKeywords.SCAMS)
    val cartels = kws(R.string.theme_cartels, TasteKeywords.CARTELS)
    val specialForces = kws(R.string.theme_special_forces, TasteKeywords.SPECIAL_FORCES)
    val trueStory = kw(R.string.theme_true_story, Keyword.TRUE_STORY)
    val serialKiller = kw(R.string.theme_serial_killer, Keyword.SERIAL_KILLER)
    val biopic = kw(R.string.theme_biopic, Keyword.BIOGRAPHY)
    val detective = kw(R.string.theme_detective, Keyword.DETECTIVE)
    return when (genre) {
        CatalogGenre.ACTION -> {
            if (movie) {
                listOf(
                    kw(R.string.theme_heist, Keyword.HEIST),
                    kw(R.string.theme_spy, Keyword.SPY),
                    kw(R.string.theme_superhero, Keyword.SUPERHERO),
                    specialForces,
                    kws(R.string.theme_racing, TasteKeywords.RACING),
                    kw(R.string.theme_martial_arts, Keyword.MARTIAL_ARTS),
                    with(R.string.theme_action_comedy, COMEDY),
                    kw(R.string.theme_revenge, Keyword.REVENGE),
                    kws(R.string.theme_aviation, TasteKeywords.AVIATION),
                )
            } else {
                listOf(kw(R.string.theme_superhero, Keyword.SUPERHERO), kw(R.string.theme_spy, Keyword.SPY))
            }
        }

        CatalogGenre.ADVENTURE -> {
            listOf(
                kw(R.string.theme_survival, Keyword.SURVIVAL),
                with(R.string.theme_fantasy_adventure, FANTASY),
                kw(R.string.theme_road_movie, Keyword.ROAD_MOVIE),
                trueStory,
            )
        }

        CatalogGenre.COMEDY -> {
            if (movie) {
                listOf(
                    ThemeRow(R.string.theme_adult_comedy, adultComedyQuery()),
                    country(R.string.theme_french_comedy),
                    with(R.string.theme_action_comedy, ACTION),
                    kw(R.string.theme_buddy, Keyword.BUDDY_COMEDY),
                    scams,
                    with(R.string.theme_romcom, ROMANCE),
                    kw(R.string.theme_dark_comedy, Keyword.SATIRE, Keyword.BLACK_COMEDY),
                    kw(R.string.theme_parody, Keyword.PARODY),
                    with(R.string.theme_family_comedy, FAMILY),
                )
            } else {
                listOf(country(R.string.theme_french_comedy))
            }
        }

        CatalogGenre.THRILLER -> {
            if (movie) {
                listOf(
                    twists,
                    kw(R.string.theme_psychological, Keyword.PSYCHOLOGICAL_THRILLER),
                    serialKiller,
                    cartels,
                    with(R.string.theme_crime_thriller, CRIME),
                    kw(R.string.theme_conspiracy, Keyword.CONSPIRACY),
                    kw(R.string.theme_kidnapping, Keyword.KIDNAPPING),
                    kw(R.string.theme_spy, Keyword.SPY),
                )
            } else {
                listOf(trueStory, serialKiller, detective)
            }
        }

        CatalogGenre.MYSTERY -> {
            listOf(twists, detective, serialKiller, kw(R.string.theme_conspiracy, Keyword.CONSPIRACY))
        }

        CatalogGenre.SCIENCE_FICTION -> {
            listOf(
                kw(R.string.theme_space, Keyword.SPACE),
                twists,
                kw(R.string.theme_time_travel, Keyword.TIME_TRAVEL),
                kw(R.string.theme_dystopia, Keyword.DYSTOPIA),
                kw(R.string.theme_aliens, Keyword.ALIEN),
                kw(R.string.theme_ai, Keyword.ARTIFICIAL_INTELLIGENCE),
                kw(R.string.theme_post_apocalyptic, Keyword.POST_APOCALYPTIC),
            )
        }

        CatalogGenre.ANIMATION -> {
            if (movie) {
                listOf(
                    kw(R.string.theme_buddy, Keyword.BUDDY_COMEDY),
                    kw(R.string.theme_christmas, Keyword.CHRISTMAS),
                )
            } else {
                emptyList()
            }
        }

        CatalogGenre.DRAMA -> {
            if (movie) {
                listOf(
                    trueStory,
                    biopic,
                    kws(R.string.theme_geniuses, TasteKeywords.GENIUSES),
                    scams,
                    kw(R.string.theme_family_drama, Keyword.FAMILY_RELATIONSHIPS),
                    with(R.string.theme_romantic_drama, ROMANCE),
                    kw(R.string.theme_coming_of_age, Keyword.COMING_OF_AGE),
                    kw(R.string.theme_courtroom, Keyword.COURTROOM),
                    kw(R.string.theme_sports, Keyword.SPORTS),
                    with(R.string.theme_historical, HISTORY),
                )
            } else {
                listOf(trueStory, country(R.string.theme_french_drama), kw(R.string.theme_courtroom, Keyword.COURTROOM))
            }
        }

        CatalogGenre.DOCUMENTARY -> {
            // Documentaries collect few votes
            listOf(
                kw(R.string.theme_biopic, Keyword.BIOGRAPHY, votes = DOCUMENTARY_MIN_VOTES),
                kw(R.string.theme_sports, Keyword.SPORTS, votes = DOCUMENTARY_MIN_VOTES),
                kw(R.string.theme_space, Keyword.SPACE, votes = DOCUMENTARY_MIN_VOTES),
            )
        }

        CatalogGenre.HORROR -> {
            listOf(
                kw(R.string.theme_psychological, Keyword.PSYCHOLOGICAL_THRILLER),
                kw(R.string.theme_haunted_house, Keyword.HAUNTED_HOUSE),
                kw(R.string.theme_slasher, Keyword.SLASHER),
                kw(R.string.theme_zombies, Keyword.ZOMBIE),
                kw(R.string.theme_found_footage, Keyword.FOUND_FOOTAGE),
                serialKiller,
            )
        }

        CatalogGenre.ROMANCE -> {
            listOf(
                with(R.string.theme_romcom, COMEDY),
                with(R.string.theme_romantic_drama, DRAMA),
                kw(R.string.theme_coming_of_age, Keyword.COMING_OF_AGE),
                kw(R.string.theme_weddings, Keyword.WEDDING),
                kw(R.string.theme_christmas, Keyword.CHRISTMAS),
            )
        }

        CatalogGenre.CRIME -> {
            if (movie) {
                listOf(
                    kw(R.string.theme_heist, Keyword.HEIST),
                    scams,
                    cartels,
                    kw(R.string.theme_mafia, Keyword.MAFIA),
                    serialKiller,
                    detective,
                    kw(R.string.theme_prison, Keyword.PRISON),
                    trueStory,
                )
            } else {
                listOf(trueStory, serialKiller, kw(R.string.theme_mafia, Keyword.MAFIA), detective)
            }
        }

        CatalogGenre.FAMILY -> {
            listOf(
                kw(R.string.theme_christmas, Keyword.CHRISTMAS),
                kw(R.string.theme_buddy, Keyword.BUDDY_COMEDY),
                kw(R.string.theme_coming_of_age, Keyword.COMING_OF_AGE),
            )
        }

        CatalogGenre.FANTASY -> {
            listOf(kw(R.string.theme_magic, Keyword.MAGIC), kw(R.string.theme_dragons, Keyword.DRAGON))
        }

        CatalogGenre.HISTORY -> {
            listOf(biopic, kw(R.string.theme_ww2, Keyword.WORLD_WAR_II), with(R.string.theme_historical, DRAMA))
        }

        CatalogGenre.WAR -> {
            listOf(trueStory, kw(R.string.theme_ww2, Keyword.WORLD_WAR_II), specialForces)
        }

        CatalogGenre.MUSIC -> {
            listOf(kw(R.string.theme_musicals, Keyword.MUSICAL), biopic)
        }

        CatalogGenre.WESTERN -> {
            listOf(kw(R.string.theme_revenge, Keyword.REVENGE))
        }

        else -> {
            emptyList()
        }
    }
}
