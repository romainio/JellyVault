package tv.jellyvault.app.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonStyleTest {
    private val text = Color(0xFFF4F1F8)
    private val night = Color(0xFF1A1720)

    @Test
    fun secondary_is_glass_then_solid_on_focus() {
        val p = buttonPalette(ButtonStyle.Secondary, onSurface = text, inverseOnSurface = night)
        assertEquals(text.copy(alpha = 0.14f), p.container)
        assertEquals(text, p.content)
        assertEquals(text, p.focusedContainer)
        assertEquals(night, p.focusedContent)
    }

    @Test
    fun disabled_primary_stays_readable() {
        // The TMDB status ("Téléchargement 42 %") is a disabled primary button: glass with light text
        val p = buttonPalette(ButtonStyle.Primary, onSurface = text, inverseOnSurface = night)
        assertEquals(text.copy(alpha = 0.14f), p.disabledContainer)
        assertEquals(text.copy(alpha = 0.7f), p.disabledContent)
    }

    @Test
    fun primary_is_glass_until_focused() {
        // Only the focused button is solid, so focus never hides behind a white "Lire"
        val p = buttonPalette(ButtonStyle.Primary, onSurface = text, inverseOnSurface = night)
        assertEquals(text.copy(alpha = 0.24f), p.container)
        assertEquals(text, p.content)
        assertEquals(text, p.focusedContainer)
        assertEquals(night, p.focusedContent)
    }
}
