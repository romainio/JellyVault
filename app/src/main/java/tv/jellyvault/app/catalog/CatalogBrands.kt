package tv.jellyvault.app.catalog

import kotlinx.serialization.Serializable

/**
 * Film studios browsable from the Studios & sagas tab (TMDB company ids)
 */
@Serializable
enum class CatalogStudio(
    val companyId: Int,
    val displayName: String,
) {
    PIXAR(3, "Pixar"),
    WALT_DISNEY(2, "Walt Disney Pictures"),
    MARVEL_STUDIOS(420, "Marvel Studios"),
    LUCASFILM(1, "Lucasfilm"),
    DREAMWORKS_ANIMATION(521, "DreamWorks Animation"),
    STUDIO_GHIBLI(10342, "Studio Ghibli"),
    ILLUMINATION(6704, "Illumination"),
    WARNER_BROS(174, "Warner Bros. Pictures"),
    A24(41077, "A24"),
    BLUMHOUSE(3172, "Blumhouse"),
    DISNEY_ANIMATION(6125, "Walt Disney Animation Studios"),
    SONY_ANIMATION(2251, "Sony Pictures Animation"),
    UNIVERSAL(33, "Universal Pictures"),
    PARAMOUNT(4, "Paramount Pictures"),
    TWENTIETH_CENTURY(127928, "20th Century Studios"),
    LIONSGATE(1632, "Lionsgate"),
    LEGENDARY(923, "Legendary Pictures"),
    STUDIOCANAL(694, "StudioCanal"),
    GAUMONT(9, "Gaumont"),
    PATHE(7981, "Pathé"),
    EUROPACORP(6896, "EuropaCorp"),
    LAIKA(11537, "LAIKA"),
    AARDMAN(297, "Aardman"),
    TOHO(882, "TOHO"),
    ;

    companion object {
        /** Studio rows of Films → Pour vous */
        val FOR_YOU = listOf(PIXAR, MARVEL_STUDIOS, WALT_DISNEY, STUDIO_GHIBLI)
    }
}

/**
 * Film sagas (TMDB collection ids)
 */
@Serializable
enum class CatalogSaga(
    val collectionId: Int,
    val displayName: String,
) {
    HARRY_POTTER(1241, "Harry Potter"),
    STAR_WARS(10, "Star Wars"),
    LORD_OF_THE_RINGS(119, "Le Seigneur des anneaux"),
    JURASSIC_PARK(328, "Jurassic Park"),
    FAST_AND_FURIOUS(9485, "Fast and Furious"),
    JAMES_BOND(645, "James Bond"),
    MISSION_IMPOSSIBLE(87359, "Mission : Impossible"),
    TOY_STORY(10194, "Toy Story"),
    PIRATES(295, "Pirates des Caraïbes"),
    SHREK(2150, "Shrek"),
    INDIANA_JONES(84, "Indiana Jones"),
    MATRIX(2344, "Matrix"),
    BACK_TO_THE_FUTURE(264, "Retour vers le futur"),
    HUNGER_GAMES(131635, "Hunger Games"),
    HOBBIT(121938, "Le Hobbit"),
    TRANSFORMERS(8650, "Transformers"),
    JOHN_WICK(404609, "John Wick"),
    ROCKY(1575, "Rocky"),
    ALIEN(8091, "Alien"),
    TERMINATOR(528, "Terminator"),
    DESPICABLE_ME(86066, "Moi, moche et méchant"),
    ICE_AGE(8354, "L'Âge de glace"),
    ASTERIX(2396, "Astérix & Obélix"),
    LES_VISITEURS(52888, "Les Visiteurs"),
    TWILIGHT(33514, "Twilight"),
    MEN_IN_BLACK(86055, "Men in Black"),
    KUNG_FU_PANDA(77816, "Kung Fu Panda"),
    DRAGONS(98874, "Dragons"),
}

/**
 * Series networks and streaming services (TMDB network ids)
 */
@Serializable
enum class CatalogNetwork(
    val networkId: Int,
    val displayName: String,
) {
    NETFLIX(213, "Netflix"),
    HBO(49, "HBO"),
    DISNEY_PLUS(2739, "Disney+"),
    APPLE_TV(2552, "Apple TV"),
    PRIME_VIDEO(1024, "Prime Video"),
    CANAL_PLUS(285, "Canal+"),
    ARTE(1628, "ARTE"),
    HBO_MAX(3186, "HBO Max"),
    PARAMOUNT_PLUS(4330, "Paramount+"),
    CRUNCHYROLL(1112, "Crunchyroll"),
    TF1(290, "TF1"),
    M6(712, "M6"),
    FRANCE_2(361, "France 2"),
    BBC_ONE(4, "BBC One"),
    AMC(174, "AMC"),
}

/**
 * Studios shown as extra rows on a movie genre page
 */
fun relatedStudios(genre: CatalogGenre): List<CatalogStudio> =
    when (genre) {
        CatalogGenre.ANIMATION -> listOf(CatalogStudio.PIXAR, CatalogStudio.STUDIO_GHIBLI, CatalogStudio.DREAMWORKS_ANIMATION)
        CatalogGenre.FAMILY -> listOf(CatalogStudio.WALT_DISNEY, CatalogStudio.ILLUMINATION)
        CatalogGenre.SCIENCE_FICTION -> listOf(CatalogStudio.LUCASFILM, CatalogStudio.MARVEL_STUDIOS)
        CatalogGenre.ACTION -> listOf(CatalogStudio.MARVEL_STUDIOS)
        CatalogGenre.HORROR -> listOf(CatalogStudio.BLUMHOUSE)
        CatalogGenre.DRAMA -> listOf(CatalogStudio.A24)
        else -> emptyList()
    }

/**
 * A saga shown as a row of its studio's page; [name] until TMDB gives the localized one
 */
data class StudioSaga(
    val collectionId: Int,
    val name: String,
)

/**
 * Sagas of a studio page, most famous first (TMDB collection ids)
 */
fun studioSagas(companyId: Int?): List<StudioSaga> =
    when (companyId) {
        // Marvel Studios
        420 -> {
            listOf(
                StudioSaga(86311, "Avengers"),
                StudioSaga(131292, "Iron Man"),
                StudioSaga(131295, "Captain America"),
                StudioSaga(131296, "Thor"),
                StudioSaga(284433, "Guardians of the Galaxy"),
                StudioSaga(531241, "Spider-Man (MCU)"),
                StudioSaga(529892, "Black Panther"),
                StudioSaga(422834, "Ant-Man"),
                StudioSaga(618529, "Doctor Strange"),
            )
        }

        // Lucasfilm
        1 -> {
            listOf(StudioSaga(10, "Star Wars"), StudioSaga(84, "Indiana Jones"))
        }

        // Pixar
        3 -> {
            listOf(
                StudioSaga(10194, "Toy Story"),
                StudioSaga(468222, "The Incredibles"),
                StudioSaga(137697, "Finding Nemo"),
                StudioSaga(87118, "Cars"),
                StudioSaga(137696, "Monsters, Inc."),
            )
        }

        // Walt Disney Pictures
        2 -> {
            listOf(StudioSaga(295, "Pirates of the Caribbean"))
        }

        // Walt Disney Animation Studios
        6125 -> {
            listOf(StudioSaga(386382, "Frozen"))
        }

        // DreamWorks Animation
        521 -> {
            listOf(
                StudioSaga(2150, "Shrek"),
                StudioSaga(77816, "Kung Fu Panda"),
                StudioSaga(98874, "How to Train Your Dragon"),
                StudioSaga(14740, "Madagascar"),
            )
        }

        // Illumination
        6704 -> {
            listOf(StudioSaga(86066, "Despicable Me"), StudioSaga(544669, "Minions"))
        }

        // Warner Bros. Pictures
        174 -> {
            listOf(
                StudioSaga(1241, "Harry Potter"),
                StudioSaga(435259, "Fantastic Beasts"),
                StudioSaga(263, "The Dark Knight"),
                StudioSaga(2344, "The Matrix"),
                StudioSaga(8945, "Mad Max"),
            )
        }

        // Universal Pictures
        33 -> {
            listOf(StudioSaga(328, "Jurassic Park"), StudioSaga(9485, "Fast & Furious"), StudioSaga(264, "Back to the Future"))
        }

        // Paramount Pictures
        4 -> {
            listOf(StudioSaga(87359, "Mission: Impossible"), StudioSaga(8650, "Transformers"))
        }

        // 20th Century Studios
        127928 -> {
            listOf(
                StudioSaga(87096, "Avatar"),
                StudioSaga(8091, "Alien"),
                StudioSaga(173710, "Planet of the Apes"),
                StudioSaga(8354, "Ice Age"),
            )
        }

        // Lionsgate
        1632 -> {
            listOf(StudioSaga(131635, "The Hunger Games"), StudioSaga(404609, "John Wick"))
        }

        // Gaumont
        9 -> {
            listOf(StudioSaga(52888, "Les Visiteurs"))
        }

        // Pathé
        7981 -> {
            listOf(StudioSaga(2396, "Astérix & Obélix"))
        }

        else -> {
            emptyList()
        }
    }

private val COLLECTION_SUFFIX = Regex("""\s*([-–:]\s*)?(Saga|Collection|Colección|Filmreihe)$""", RegexOption.IGNORE_CASE)

/** "Avengers - Saga", "The Avengers Collection" → "Avengers", "The Avengers" */
fun collectionRowName(tmdbName: String): String = tmdbName.replace(COLLECTION_SUFFIX, "").trim().ifEmpty { tmdbName }
