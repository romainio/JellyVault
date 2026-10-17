package tv.jellyvault.app.catalog.shelves

import androidx.annotation.StringRes
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbSort
import java.time.LocalDate

/**
 * Families of styles: two shelves of the same group are never shown side by side
 */
enum class ShelfGroup {
    FRENCH,
    ROMANCE,
    ACTION,
    COMEDY,
    CRIME,
    THRILLER,
    SCIFI,
    DRAMA,
    PRESTIGE,
    TRUE_STORY,
    FAMILY,
    HORROR,
    DOCUMENTARY,
}

/**
 * One style of the varied home ("Films français", "Thrillers d'action"…). A home plan is a list of shelf ids,
 * so a server can send the same plan later.
 */
class Shelf(
    val id: String,
    @param:StringRes val titleRes: Int,
    val mediaType: TmdbMediaType,
    val group: ShelfGroup,
    /** TMDB genres this style stands for, to match the user's taste */
    val genres: List<Int>,
    val baseWeight: Double = 1.0,
    /** The TMDB query, for [LocalDate] "today" (some styles are relative: recent gems, last 15 years) */
    val query: (LocalDate) -> TmdbDiscoverQuery,
)

private object G {
    const val ACTION = 28
    const val ADVENTURE = 12
    const val ANIMATION = 16
    const val COMEDY = 35
    const val CRIME = 80
    const val DOCUMENTARY = 99
    const val DRAMA = 18
    const val FAMILY = 10751
    const val FANTASY = 14
    const val HISTORY = 36
    const val HORROR = 27
    const val ROMANCE = 10749
    const val SCIENCE_FICTION = 878
    const val THRILLER = 53
    const val MYSTERY = 9648
    const val TV_SCIFI_FANTASY = 10765
    const val TV_ACTION_ADVENTURE = 10759
    const val TV_WAR_POLITICS = 10768
    const val WAR = 10752
}

private object K {
    const val TRUE_STORY = 9672
    const val SPY = 470
    const val HEIST = 10051
    const val SUPERHERO = 9715
    const val PSYCHOLOGICAL_THRILLER = 12565
}

/** Live-action styles: keyword and genre matches would bring cartoons (Cars 2 is a spy film, Zootopia a crime one) */
private val ADULT = listOf(G.ANIMATION, G.FAMILY)
private val NO_ANIMATION = listOf(G.ANIMATION)

private const val MOVIE_VOTES = 300
private const val TV_VOTES = 100
private const val TV_MINISERIES = 2

private fun movie(
    genreId: Int? = null,
    andGenres: List<Int> = emptyList(),
    anyGenres: List<Int> = emptyList(),
    keywords: List<Int> = emptyList(),
    country: String? = null,
    sort: TmdbSort = TmdbSort.POPULARITY,
    minVotes: Int = MOVIE_VOTES,
    minRating: Double? = null,
    after: LocalDate? = null,
    before: LocalDate? = null,
    without: List<Int> = emptyList(),
) = TmdbDiscoverQuery(
    TmdbMediaType.MOVIE,
    genreId = genreId,
    andGenreIds = andGenres,
    anyGenreIds = anyGenres,
    keywordIds = keywords,
    originCountry = country,
    sort = sort,
    minVotes = minVotes,
    minRating = minRating,
    releasedAfter = after,
    releasedBefore = before,
    mainstreamOnly = true,
    withoutGenreIds = without,
)

private fun tv(
    genreId: Int? = null,
    keywords: List<Int> = emptyList(),
    country: String? = null,
    sort: TmdbSort = TmdbSort.POPULARITY,
    minVotes: Int = TV_VOTES,
    minRating: Double? = null,
    tvType: Int? = null,
    after: LocalDate? = null,
    without: List<Int> = emptyList(),
) = TmdbDiscoverQuery(
    TmdbMediaType.TV,
    genreId = genreId,
    keywordIds = keywords,
    originCountry = country,
    sort = sort,
    minVotes = minVotes,
    minRating = minRating,
    tvType = tvType,
    releasedAfter = after,
    mainstreamOnly = true,
    withoutGenreIds = without,
)

object ShelfCatalog {
    private val M = TmdbMediaType.MOVIE
    private val T = TmdbMediaType.TV

    val ALL: List<Shelf> =
        listOf(
            Shelf("fr_films", R.string.shelf_french_films, M, ShelfGroup.FRENCH, emptyList()) { movie(country = "FR") },
            Shelf(
                "fr_comedies",
                R.string.shelf_french_comedies,
                M,
                ShelfGroup.FRENCH,
                listOf(G.COMEDY),
            ) { movie(G.COMEDY, country = "FR", without = NO_ANIMATION) },
            Shelf("romance", R.string.shelf_romance, M, ShelfGroup.ROMANCE, listOf(G.ROMANCE)) { movie(G.ROMANCE, without = NO_ANIMATION) },
            Shelf("emotion", R.string.shelf_emotion, M, ShelfGroup.DRAMA, listOf(G.DRAMA, G.ROMANCE)) {
                movie(G.DRAMA, andGenres = listOf(G.ROMANCE), minRating = 7.0, without = ADULT)
            },
            Shelf("scifi_fantasy", R.string.shelf_scifi_fantasy, M, ShelfGroup.SCIFI, listOf(G.SCIENCE_FICTION, G.FANTASY)) {
                movie(anyGenres = listOf(G.SCIENCE_FICTION, G.FANTASY), without = NO_ANIMATION)
            },
            Shelf("crime", R.string.shelf_crime, M, ShelfGroup.CRIME, listOf(G.CRIME)) { movie(G.CRIME, without = ADULT) },
            Shelf("heists", R.string.theme_heist, M, ShelfGroup.CRIME, listOf(G.CRIME, G.THRILLER)) {
                movie(keywords = listOf(K.HEIST), without = ADULT)
            },
            Shelf("true_story", R.string.theme_true_story, M, ShelfGroup.TRUE_STORY, listOf(G.DRAMA, G.HISTORY)) {
                movie(keywords = listOf(K.TRUE_STORY), without = ADULT)
            },
            Shelf("blockbusters", R.string.shelf_blockbusters, M, ShelfGroup.ACTION, listOf(G.ACTION, G.ADVENTURE, G.SCIENCE_FICTION)) {
                movie(country = "US", sort = TmdbSort.REVENUE, minVotes = 2000, after = it.minusYears(15))
            },
            Shelf("action_adventure", R.string.shelf_action_adventure, M, ShelfGroup.ACTION, listOf(G.ACTION, G.ADVENTURE)) {
                movie(G.ACTION, andGenres = listOf(G.ADVENTURE), without = NO_ANIMATION)
            },
            Shelf("action_thrillers", R.string.shelf_action_thrillers, M, ShelfGroup.THRILLER, listOf(G.ACTION, G.THRILLER)) {
                movie(G.ACTION, andGenres = listOf(G.THRILLER), without = ADULT)
            },
            Shelf("adventure", R.string.shelf_adventure, M, ShelfGroup.ACTION, listOf(G.ADVENTURE)) { movie(G.ADVENTURE) },
            Shelf("superheroes", R.string.theme_superhero, M, ShelfGroup.ACTION, listOf(G.ACTION, G.SCIENCE_FICTION)) {
                movie(keywords = listOf(K.SUPERHERO), without = listOf(G.FAMILY))
            },
            Shelf("spies", R.string.theme_spy, M, ShelfGroup.THRILLER, listOf(G.ACTION, G.THRILLER)) {
                movie(anyGenres = listOf(G.ACTION, G.THRILLER), keywords = listOf(K.SPY), without = ADULT)
            },
            Shelf("psychological", R.string.theme_psychological, M, ShelfGroup.THRILLER, listOf(G.THRILLER, G.MYSTERY)) {
                movie(keywords = listOf(K.PSYCHOLOGICAL_THRILLER), without = ADULT)
            },
            Shelf("comedies", R.string.shelf_comedies, M, ShelfGroup.COMEDY, listOf(G.COMEDY)) { movie(G.COMEDY, without = NO_ANIMATION) },
            Shelf("recent_gems", R.string.shelf_recent_gems, M, ShelfGroup.PRESTIGE, emptyList()) {
                movie(minRating = 7.0, after = it.minusYears(2))
            },
            Shelf("masterpieces", R.string.theme_masterpieces, M, ShelfGroup.PRESTIGE, emptyList(), baseWeight = 0.8) {
                movie(sort = TmdbSort.RATING, minVotes = 5000, minRating = 8.0)
            },
            Shelf("cult_90s_2000s", R.string.shelf_cult_90s_2000s, M, ShelfGroup.PRESTIGE, emptyList()) {
                movie(minVotes = 3000, after = LocalDate.of(1990, 1, 1), before = LocalDate.of(2009, 12, 31))
            },
            Shelf("animation", R.string.shelf_animation, M, ShelfGroup.FAMILY, listOf(G.ANIMATION, G.FAMILY)) { movie(G.ANIMATION) },
            Shelf("horror", R.string.shelf_horror, M, ShelfGroup.HORROR, listOf(G.HORROR), baseWeight = 0.7) {
                movie(G.HORROR, without = NO_ANIMATION)
            },
            Shelf("documentaries", R.string.shelf_documentaries, M, ShelfGroup.DOCUMENTARY, listOf(G.DOCUMENTARY), baseWeight = 0.6) {
                movie(G.DOCUMENTARY, minVotes = 150)
            },
            Shelf("tv_crime", R.string.shelf_tv_crime, T, ShelfGroup.CRIME, listOf(G.CRIME)) { tv(G.CRIME, without = NO_ANIMATION) },
            Shelf("tv_french", R.string.theme_french_series, T, ShelfGroup.FRENCH, emptyList()) { tv(country = "FR") },
            Shelf("tv_comedy", R.string.shelf_tv_comedy, T, ShelfGroup.COMEDY, listOf(G.COMEDY)) { tv(G.COMEDY) },
            Shelf("tv_miniseries", R.string.shelf_tv_miniseries, T, ShelfGroup.DRAMA, listOf(G.DRAMA)) { tv(tvType = TV_MINISERIES) },
            Shelf(
                "tv_scifi",
                R.string.shelf_tv_scifi,
                T,
                ShelfGroup.SCIFI,
                listOf(G.SCIENCE_FICTION, G.FANTASY),
            ) { tv(G.TV_SCIFI_FANTASY, without = NO_ANIMATION) },
            Shelf("tv_true_story", R.string.shelf_tv_true_story, T, ShelfGroup.TRUE_STORY, listOf(G.DRAMA, G.CRIME)) {
                tv(keywords = listOf(K.TRUE_STORY), without = NO_ANIMATION)
            },
            Shelf(
                "tv_drama",
                R.string.shelf_tv_drama,
                T,
                ShelfGroup.DRAMA,
                listOf(G.DRAMA),
            ) { tv(G.DRAMA, minVotes = 300, without = NO_ANIMATION) },
            Shelf("tv_mystery", R.string.shelf_tv_mystery, T, ShelfGroup.THRILLER, listOf(G.MYSTERY, G.THRILLER)) {
                tv(G.MYSTERY, without = NO_ANIMATION)
            },
            Shelf("tv_action", R.string.shelf_tv_action, T, ShelfGroup.ACTION, listOf(G.ACTION, G.ADVENTURE)) {
                tv(G.TV_ACTION_ADVENTURE, without = NO_ANIMATION)
            },
            Shelf("tv_animation", R.string.shelf_tv_animation, T, ShelfGroup.FAMILY, listOf(G.ANIMATION)) { tv(G.ANIMATION) },
            Shelf("tv_documentaries", R.string.shelf_tv_documentaries, T, ShelfGroup.DOCUMENTARY, listOf(G.DOCUMENTARY), baseWeight = 0.6) {
                tv(G.DOCUMENTARY)
            },
            Shelf("tv_british", R.string.shelf_tv_british, T, ShelfGroup.DRAMA, listOf(G.DRAMA, G.CRIME)) { tv(country = "GB") },
            Shelf("tv_war_politics", R.string.shelf_tv_war_politics, T, ShelfGroup.TRUE_STORY, listOf(G.WAR, G.HISTORY), baseWeight = 0.7) {
                tv(G.TV_WAR_POLITICS)
            },
            Shelf("tv_recent_gems", R.string.shelf_tv_recent_gems, T, ShelfGroup.PRESTIGE, emptyList()) {
                tv(minRating = 7.5, after = it.minusYears(2))
            },
            Shelf("tv_cult", R.string.theme_cult_series, T, ShelfGroup.PRESTIGE, emptyList()) {
                tv(sort = TmdbSort.RATING, minVotes = 2000, minRating = 8.3)
            },
        )

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String): Shelf? = byId[id]
}
