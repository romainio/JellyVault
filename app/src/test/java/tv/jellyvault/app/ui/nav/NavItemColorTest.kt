package tv.jellyvault.app.ui.nav

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class NavItemColorTest {
    private val text = Color(0xFFF4F1F8)
    private val secondary = Color(0xFFB7AFC4)
    private val lavender = Color(0xFFCBB8FF)
    private val night = Color(0xFF1A1720)

    private fun color(
        selected: Boolean,
        focused: Boolean,
    ) = jvNavItemColor(selected, focused, text, secondary, lavender, night)

    @Test
    fun focused_item_is_dark_on_its_white_pill() {
        assertEquals(night, color(selected = false, focused = true))
        assertEquals(night, color(selected = true, focused = true))
    }

    @Test
    fun current_page_is_lavender_and_others_are_secondary() {
        assertEquals(lavender, color(selected = true, focused = false))
        assertEquals(secondary, color(selected = false, focused = false))
    }

    @Test
    fun accent_falls_back_to_text_when_it_is_too_dark_for_the_background() {
        // OLED theme: dark grey primary on black
        assertEquals(text, readableAccent(Color(0xC04D4B4B), Color.Black, text))
        assertEquals(lavender, readableAccent(lavender, night, text))
    }
}
