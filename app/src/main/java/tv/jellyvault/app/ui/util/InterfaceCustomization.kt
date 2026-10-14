package tv.jellyvault.app.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.preferences.DisplayToggle
import java.util.EnumSet

/**
 * Represents various UI preferences made available via [LocalInterfaceCustomization]
 */
data class InterfaceCustomization(
    val enabledDisplayToggles: EnumSet<DisplayToggle>,
    /** JellyVault: home rows where the focused card opens wide, details under the row (no big header) */
    val shelfHome: Boolean = false,
) {
    constructor(prefs: AppPreferences) : this(
        prefs.interfacePreferences.displayTogglesList.let {
            if (it.isEmpty()) {
                EnumSet.noneOf(DisplayToggle::class.java)
            } else {
                EnumSet.copyOf(it)
            }
        },
        prefs.interfacePreferences.shelfHome,
    )
}

val LocalInterfaceCustomization =
    staticCompositionLocalOf<InterfaceCustomization> {
        InterfaceCustomization(EnumSet.allOf(DisplayToggle::class.java))
    }
