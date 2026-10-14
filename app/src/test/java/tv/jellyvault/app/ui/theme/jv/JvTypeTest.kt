package tv.jellyvault.app.ui.theme.jv

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import tv.jellyvault.app.ui.theme.AppTypography

class JvTypeTest {
    @Test
    fun scale_matches_the_spec() {
        with(JvTypography) {
            assertEquals(32.sp, headlineMedium.fontSize)
            assertEquals(FontWeight.ExtraBold, headlineMedium.fontWeight)
            assertEquals(22.sp, headlineSmall.fontSize)
            assertEquals(17.sp, titleLarge.fontSize)
            assertEquals(FontWeight.Bold, titleLarge.fontWeight)
            assertEquals(13.sp, titleSmall.fontSize)
            assertEquals(14.sp, bodyMedium.fontSize)
            assertEquals(14.sp, labelLarge.fontSize)
            assertEquals(FontWeight.Bold, labelLarge.fontWeight)
        }
    }

    @Test
    fun every_role_uses_manrope_in_both_libraries() {
        listOf(JvTypography.headlineMedium, JvTypography.bodyMedium, JvTypography.labelSmall, JvTypography.displaySmall)
            .forEach { assertSame(Manrope, it.fontFamily) }
        listOf(JvMaterialTypography.bodyLarge, JvMaterialTypography.titleMedium, JvMaterialTypography.labelLarge)
            .forEach { assertSame(Manrope, it.fontFamily) }
        assertSame(JvTypography, AppTypography)
    }
}
