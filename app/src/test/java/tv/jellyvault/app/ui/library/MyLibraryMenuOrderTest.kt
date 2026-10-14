package tv.jellyvault.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jellyvault.app.data.model.NavDrawerPinnedItem
import tv.jellyvault.app.data.model.NavPinType
import tv.jellyvault.app.services.NavDrawerService

class MyLibraryMenuOrderTest {
    @Test
    fun my_library_takes_the_saved_place_of_favorites() {
        val pins = mapOf("a_favorites" to NavDrawerPinnedItem(1, "a_favorites", NavPinType.PINNED, 2))
        val migrated = NavDrawerService.migrateFavoritesPin(pins)
        assertEquals(2, migrated["a_mylibrary"]!!.order)
        assertEquals(NavPinType.PINNED, migrated["a_mylibrary"]!!.type)
    }

    @Test
    fun an_existing_my_library_pin_wins() {
        val pins =
            mapOf(
                "a_favorites" to NavDrawerPinnedItem(1, "a_favorites", NavPinType.PINNED, 2),
                "a_mylibrary" to NavDrawerPinnedItem(1, "a_mylibrary", NavPinType.UNPINNED, 5),
            )
        assertEquals(5, NavDrawerService.migrateFavoritesPin(pins)["a_mylibrary"]!!.order)
    }

    @Test
    fun nothing_saved_stays_empty() {
        assertNull(NavDrawerService.migrateFavoritesPin(emptyMap())["a_mylibrary"])
    }
}
