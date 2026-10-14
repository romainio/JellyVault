package tv.jellyvault.app.ui.theme.jv

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tv.jellyvault.app.preferences.AppThemeColors
import tv.jellyvault.app.ui.theme.getThemeColors

class JvColorsTest {
    @Test
    fun palette_values() {
        assertEquals(Color(0xFF1A1720), JvColors.Night)
        assertEquals(Color(0xFFF4F1F8), JvColors.Text)
        assertEquals(Color(0xFFCBB8FF), JvColors.Lavender)
        assertEquals(0.14f, JvColors.Glass.alpha, 0.01f)
    }

    @Test
    fun violet_default_theme_is_the_jellyvault_palette() {
        val tv = getThemeColors(AppThemeColors.PURPLE).darkScheme
        assertEquals(JvColors.Night, tv.background)
        assertEquals(JvColors.Text, tv.onSurface)
        assertEquals(JvColors.Text, tv.border)
        assertEquals(JvColors.Night, tv.inverseOnSurface)
        assertEquals(JvColors.Lavender, tv.primary)
        val m3 = getThemeColors(AppThemeColors.PURPLE).darkSchemeMaterial
        assertEquals(JvColors.Night, m3.background)
        assertEquals(JvColors.SurfaceHigh, m3.surfaceVariant)
        assertEquals(getThemeColors(AppThemeColors.PURPLE), getThemeColors(AppThemeColors.UNRECOGNIZED))
    }

    @Test
    fun oled_theme_keeps_readable_button_text() {
        // Pills draw inverseOnSurface on onSurface: both must differ in every theme
        AppThemeColors.entries.filter { it != AppThemeColors.UNRECOGNIZED }.forEach {
            val s = getThemeColors(it).darkScheme
            assertNotEquals("$it", s.onSurface, s.inverseOnSurface)
        }
    }
}
