package tv.jellyvault.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.tv.material3.MaterialTheme
import tv.jellyvault.app.preferences.AppThemeColors
import tv.jellyvault.app.ui.theme.colors.BlueThemeColors
import tv.jellyvault.app.ui.theme.colors.BoldBlueThemeColors
import tv.jellyvault.app.ui.theme.colors.BrownThemeColors
import tv.jellyvault.app.ui.theme.colors.GreenThemeColors
import tv.jellyvault.app.ui.theme.colors.OledThemeColors
import tv.jellyvault.app.ui.theme.colors.OrangeThemeColors
import tv.jellyvault.app.ui.theme.colors.RedThemeColors
import tv.jellyvault.app.ui.theme.jv.JvMaterialTypography
import tv.jellyvault.app.ui.theme.jv.JvThemeColors

val LocalTheme =
    compositionLocalOf<AppThemeColors> { AppThemeColors.PURPLE }

fun getThemeColors(appThemeColors: AppThemeColors): ThemeColors =
    when (appThemeColors) {
        AppThemeColors.PURPLE -> JvThemeColors
        AppThemeColors.BLUE -> BlueThemeColors
        AppThemeColors.GREEN -> GreenThemeColors
        AppThemeColors.ORANGE -> OrangeThemeColors
        AppThemeColors.OLED_BLACK -> OledThemeColors
        AppThemeColors.BOLD_BLUE -> BoldBlueThemeColors
        AppThemeColors.RED -> RedThemeColors
        AppThemeColors.BROWN -> BrownThemeColors
        AppThemeColors.UNRECOGNIZED -> JvThemeColors
    }

@Composable
fun JellyVaultTheme(
    darkTheme: Boolean = true,
    appThemeColors: AppThemeColors = AppThemeColors.PURPLE,
    content: @Composable () -> Unit,
) {
    val themeColors = getThemeColors(appThemeColors)

    val colorScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
    CompositionLocalProvider(LocalTheme provides appThemeColors) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = if (darkTheme) themeColors.darkSchemeMaterial else themeColors.lightSchemeMaterial,
            typography = JvMaterialTypography,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = AppTypography,
                content = content,
            )
        }
    }
}
