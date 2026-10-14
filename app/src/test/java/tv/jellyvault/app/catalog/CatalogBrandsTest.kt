package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBrandsTest {
    @Test
    fun ids_match_tmdb() {
        assertEquals(3, CatalogStudio.PIXAR.companyId)
        assertEquals(420, CatalogStudio.MARVEL_STUDIOS.companyId)
        assertEquals(10194, CatalogSaga.TOY_STORY.collectionId)
        assertEquals(49, CatalogNetwork.HBO.networkId)
        assertEquals(24, CatalogStudio.entries.size)
        assertEquals(28, CatalogSaga.entries.size)
        assertEquals(15, CatalogNetwork.entries.size)
    }

    @Test
    fun related_studios_per_genre() {
        assertEquals(
            listOf(CatalogStudio.PIXAR, CatalogStudio.STUDIO_GHIBLI, CatalogStudio.DREAMWORKS_ANIMATION),
            relatedStudios(CatalogGenre.ANIMATION),
        )
        assertEquals(listOf(CatalogStudio.BLUMHOUSE), relatedStudios(CatalogGenre.HORROR))
        assertTrue(relatedStudios(CatalogGenre.WESTERN).isEmpty())
    }

    @Test
    fun for_you_studios() {
        assertEquals(
            listOf(CatalogStudio.PIXAR, CatalogStudio.MARVEL_STUDIOS, CatalogStudio.WALT_DISNEY, CatalogStudio.STUDIO_GHIBLI),
            CatalogStudio.FOR_YOU,
        )
    }

    @Test
    fun catalogue_is_larger() {
        assertEquals(6125, CatalogStudio.DISNEY_ANIMATION.companyId)
        assertEquals(2396, CatalogSaga.ASTERIX.collectionId)
        assertEquals(3186, CatalogNetwork.HBO_MAX.networkId)
    }
}
