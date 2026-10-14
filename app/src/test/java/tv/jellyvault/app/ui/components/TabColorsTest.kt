package tv.jellyvault.app.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TabColorsTest {
    private val text = Color(0xFFF4F1F8)
    private val secondary = Color(0xFFB7AFC4)
    private val night = Color(0xFF1A1720)

    private fun colors(
        selected: Boolean,
        focused: Boolean,
    ) = tabColors(selected, focused, text, secondary, night)

    @Test
    fun active_tab_is_a_white_pill() {
        assertEquals(text to night, colors(selected = true, focused = false))
        assertEquals(text to night, colors(selected = true, focused = true))
    }

    @Test
    fun focused_tab_is_glass_and_resting_tab_is_plain() {
        assertEquals(text.copy(alpha = 0.14f) to text, colors(selected = false, focused = true))
        assertEquals(Color.Transparent to secondary, colors(selected = false, focused = false))
    }
}
