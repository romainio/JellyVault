package tv.jellyvault.app.ui.theme.jv

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import tv.jellyvault.app.R

/** Bundled with the app (SIL Open Font License, see assets/licenses) */
val Manrope =
    FontFamily(
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
        Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    )

private fun style(
    size: Int,
    weight: FontWeight,
    lineHeight: Int? = null,
    tracking: Double? = null,
) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
    letterSpacing = tracking?.em ?: TextUnit.Unspecified,
)

private val tvDefaults = androidx.tv.material3.Typography()

/**
 * Sizes of the design spec, put on the roles the screens already read (title: headlineMedium, rows: titleLarge, info: titleSmall)
 */
val JvTypography =
    androidx.tv.material3.Typography(
        displayLarge = tvDefaults.displayLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
        displayMedium = tvDefaults.displayMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
        displaySmall = tvDefaults.displaySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
        headlineLarge = tvDefaults.headlineLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
        headlineMedium = style(32, FontWeight.ExtraBold, lineHeight = 34, tracking = -0.03),
        headlineSmall = style(22, FontWeight.Bold, tracking = -0.02),
        titleLarge = style(17, FontWeight.Bold),
        titleMedium = style(15, FontWeight.SemiBold),
        titleSmall = style(13, FontWeight.SemiBold),
        bodyLarge = style(15, FontWeight.Medium, lineHeight = 22),
        bodyMedium = style(14, FontWeight.Medium, lineHeight = 21),
        bodySmall = style(13, FontWeight.Medium),
        labelLarge = style(14, FontWeight.Bold),
        labelMedium = style(12, FontWeight.SemiBold),
        labelSmall = style(11, FontWeight.SemiBold),
    )

/** Same scale for dialogs and settings drawn with material3 */
val JvMaterialTypography =
    androidx.compose.material3.Typography(
        displayLarge = JvTypography.displayLarge,
        displayMedium = JvTypography.displayMedium,
        displaySmall = JvTypography.displaySmall,
        headlineLarge = JvTypography.headlineLarge,
        headlineMedium = JvTypography.headlineMedium,
        headlineSmall = JvTypography.headlineSmall,
        titleLarge = JvTypography.titleLarge,
        titleMedium = JvTypography.titleMedium,
        titleSmall = JvTypography.titleSmall,
        bodyLarge = JvTypography.bodyLarge,
        bodyMedium = JvTypography.bodyMedium,
        bodySmall = JvTypography.bodySmall,
        labelLarge = JvTypography.labelLarge,
        labelMedium = JvTypography.labelMedium,
        labelSmall = JvTypography.labelSmall,
    )
