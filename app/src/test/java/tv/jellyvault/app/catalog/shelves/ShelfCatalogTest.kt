package tv.jellyvault.app.catalog.shelves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShelfCatalogTest {
    private val today = LocalDate.of(2026, 10, 17)

    private fun query(id: String) = ShelfCatalog.byId(id)!!.query(today)

    @Test
    fun spies_are_live_action_action_or_thrillers() {
        // The spy keyword alone brought Cars 2 and the Penguins of Madagascar
        val spies = query("spies")
        assertEquals(listOf(28, 53), spies.anyGenreIds)
        assertTrue(spies.withoutGenreIds.containsAll(listOf(16, 10751)))
        assertEquals("28|53", spies.params(1)["with_genres"])
    }

    @Test
    fun adult_styles_have_no_cartoons_and_cartoon_styles_keep_them() {
        listOf("heists", "true_story", "psychological", "crime", "action_thrillers", "emotion").forEach {
            assertTrue(it, query(it).withoutGenreIds.containsAll(listOf(16, 10751)))
        }
        listOf("comedies", "fr_comedies", "romance", "horror", "tv_drama", "tv_action").forEach {
            assertTrue(it, 16 in query(it).withoutGenreIds)
        }
        // Spider-Verse is a superhero film, The Incredibles a family one
        assertEquals(listOf(10751), query("superheroes").withoutGenreIds)
        listOf("animation", "tv_animation", "blockbusters").forEach { assertTrue(it, query(it).withoutGenreIds.isEmpty()) }
    }

    @Test
    fun taste_styles_exist() {
        assertEquals(
            tv.jellyvault.app.catalog
                .adultComedyQuery(),
            query("adult_comedies"),
        )
        listOf("scams", "twists", "cartels", "racing", "special_forces", "geniuses").forEach {
            assertTrue(it, query(it).keywordIds.isNotEmpty() && 16 in query(it).withoutGenreIds)
        }
    }
}
