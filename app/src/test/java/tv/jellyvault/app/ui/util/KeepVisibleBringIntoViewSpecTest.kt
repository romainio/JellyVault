package tv.jellyvault.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepVisibleBringIntoViewSpecTest {
    private val spec = KeepVisibleBringIntoViewSpec

    @Test
    fun a_fully_visible_element_does_not_scroll() {
        // Button at 300 px, 40 px high, in a 1000 px list: the title above must stay on screen
        assertEquals(0f, spec.calculateScrollDistance(offset = 300f, size = 40f, containerSize = 1000f), 0.01f)
    }

    @Test
    fun a_hidden_element_scrolls_just_enough() {
        assertEquals(-50f, spec.calculateScrollDistance(offset = -50f, size = 40f, containerSize = 1000f), 0.01f)
        assertEquals(40f, spec.calculateScrollDistance(offset = 1000f, size = 40f, containerSize = 1000f), 0.01f)
    }
}
