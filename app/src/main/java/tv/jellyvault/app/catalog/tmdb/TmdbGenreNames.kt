package tv.jellyvault.app.catalog.tmdb

private val MOVIE_GENRES_FR =
    mapOf(
        28 to "Action",
        12 to "Aventure",
        16 to "Animation",
        35 to "Comédie",
        80 to "Crime",
        99 to "Documentaire",
        18 to "Drame",
        10751 to "Familial",
        14 to "Fantastique",
        36 to "Histoire",
        27 to "Horreur",
        10402 to "Musique",
        9648 to "Mystère",
        10749 to "Romance",
        878 to "Science-Fiction",
        10770 to "Téléfilm",
        53 to "Thriller",
        10752 to "Guerre",
        37 to "Western",
    )

private val TV_GENRES_FR =
    mapOf(
        10759 to "Action & Aventure",
        16 to "Animation",
        35 to "Comédie",
        80 to "Crime",
        99 to "Documentaire",
        18 to "Drame",
        10751 to "Familial",
        10762 to "Kids",
        9648 to "Mystère",
        10763 to "News",
        10764 to "Réalité",
        10765 to "Science-Fiction & Fantastique",
        10766 to "Feuilleton",
        10767 to "Talk",
        10768 to "War & Politics",
        37 to "Western",
    )

private val MOVIE_GENRES_EN =
    mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Science Fiction",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
    )

private val TV_GENRES_EN =
    mapOf(
        10759 to "Action & Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        10762 to "Kids",
        9648 to "Mystery",
        10763 to "News",
        10764 to "Reality",
        10765 to "Sci-Fi & Fantasy",
        10766 to "Soap",
        10767 to "Talk",
        10768 to "War & Politics",
        37 to "Western",
    )

/**
 * Name of a TMDB genre id as TMDB lists it, in French for a French interface and in English otherwise; null if unknown for
 * this type
 */
fun tmdbGenreName(
    id: Int,
    mediaType: TmdbMediaType,
    language: TmdbLanguage = TmdbLanguage.current(),
): String? {
    val french = language.code == "fr"
    val names =
        if (mediaType == TmdbMediaType.MOVIE) {
            if (french) MOVIE_GENRES_FR else MOVIE_GENRES_EN
        } else {
            if (french) TV_GENRES_FR else TV_GENRES_EN
        }
    return names[id]
}
