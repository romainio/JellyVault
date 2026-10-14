package tv.jellyvault.app.ui.theme.jv

import androidx.compose.ui.graphics.Color
import tv.jellyvault.app.ui.theme.ThemeColors
import tv.jellyvault.app.ui.theme.colors.PurpleThemeColors

/** JellyVault palette */
object JvColors {
    val Night = Color(0xFF1A1720)
    val Surface = Color(0xFF242030)
    val SurfaceHigh = Color(0xFF2E293B)
    val Text = Color(0xFFF4F1F8)
    val TextSecondary = Color(0xFFB7AFC4)
    val Lavender = Color(0xFFCBB8FF)
    val Glass = Text.copy(alpha = 0.14f)
    val Error = Color(0xFFFF8A8A)
    val ErrorContainer = Color(0xFF5C1F24)

    /** Favorite heart */
    val Heart = Color(0xFFFF5A6E)

    /** Delete, recording, destructive actions: readable on the dark page and on a focused (white) button */
    val Danger = Color(0xFFE5484D)
}

/**
 * The default Violet theme: dark schemes from the JellyVault palette, light schemes kept from the old purple theme
 */
val JvThemeColors: ThemeColors =
    object : ThemeColors {
        override val lightSchemeMaterial = PurpleThemeColors.lightSchemeMaterial
        override val lightScheme = PurpleThemeColors.lightScheme

        override val darkSchemeMaterial =
            androidx.compose.material3.darkColorScheme(
                primary = JvColors.Lavender,
                onPrimary = JvColors.Night,
                primaryContainer = JvColors.SurfaceHigh,
                onPrimaryContainer = JvColors.Text,
                secondary = JvColors.TextSecondary,
                onSecondary = JvColors.Night,
                secondaryContainer = JvColors.Surface,
                onSecondaryContainer = JvColors.Text,
                tertiary = JvColors.Lavender,
                onTertiary = JvColors.Night,
                background = JvColors.Night,
                onBackground = JvColors.Text,
                surface = JvColors.Night,
                onSurface = JvColors.Text,
                surfaceVariant = JvColors.SurfaceHigh,
                onSurfaceVariant = JvColors.TextSecondary,
                surfaceTint = JvColors.Lavender,
                inverseSurface = JvColors.Text,
                inverseOnSurface = JvColors.Night,
                inversePrimary = JvColors.Lavender,
                error = JvColors.Error,
                onError = JvColors.Night,
                errorContainer = JvColors.ErrorContainer,
                onErrorContainer = JvColors.Text,
                scrim = Color.Black,
                surfaceContainerLowest = JvColors.Night,
                surfaceContainerLow = JvColors.Surface,
                surfaceContainer = JvColors.Surface,
                surfaceContainerHigh = JvColors.SurfaceHigh,
                surfaceContainerHighest = JvColors.SurfaceHigh,
            )

        override val darkScheme =
            androidx.tv.material3.darkColorScheme(
                primary = JvColors.Lavender,
                onPrimary = JvColors.Night,
                primaryContainer = JvColors.SurfaceHigh,
                onPrimaryContainer = JvColors.Text,
                secondary = JvColors.TextSecondary,
                onSecondary = JvColors.Night,
                secondaryContainer = JvColors.Surface,
                onSecondaryContainer = JvColors.Text,
                tertiary = JvColors.Lavender,
                onTertiary = JvColors.Night,
                background = JvColors.Night,
                onBackground = JvColors.Text,
                surface = JvColors.Night,
                onSurface = JvColors.Text,
                surfaceVariant = JvColors.SurfaceHigh,
                onSurfaceVariant = JvColors.TextSecondary,
                surfaceTint = JvColors.Lavender,
                inverseSurface = JvColors.Text,
                inverseOnSurface = JvColors.Night,
                inversePrimary = JvColors.Lavender,
                error = JvColors.Error,
                onError = JvColors.Night,
                errorContainer = JvColors.ErrorContainer,
                onErrorContainer = JvColors.Text,
                scrim = Color.Black,
                border = JvColors.Text,
                borderVariant = JvColors.TextSecondary,
            )
    }
