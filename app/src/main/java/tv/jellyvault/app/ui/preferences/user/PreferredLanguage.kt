package tv.jellyvault.app.ui.preferences.user

import tv.jellyvault.app.R
import tv.jellyvault.app.ui.isNotNullOrBlank
import tv.jellyvault.app.ui.util.ConcatStringProvider
import tv.jellyvault.app.ui.util.EmptyStringProvider
import tv.jellyvault.app.ui.util.ResStringProvider
import tv.jellyvault.app.ui.util.StringProvider
import tv.jellyvault.app.ui.util.StringStringProvider

/**
 * UI information for displaying a preferred language
 */
sealed interface PreferredLanguageType {
    val displayString: StringProvider

    data class ServerProfile(
        val name: String?,
    ) : PreferredLanguageType {
        override val displayString: StringProvider
            get() =
                ConcatStringProvider(
                    " - ",
                    buildList {
                        add(ResStringProvider(R.string.use_user_profile))
                        if (name.isNotNullOrBlank()) {
                            add(StringStringProvider(name))
                        }
                    },
                )
    }

    data object AnyLanguage : PreferredLanguageType {
        override val displayString: StringProvider
            get() = ResStringProvider(R.string.any_language)
    }

    data class Language(
        val iso: String,
        val name: String,
    ) : PreferredLanguageType {
        override val displayString: StringProvider
            get() = StringStringProvider(name)
    }

    data object Divider : PreferredLanguageType {
        override val displayString: StringProvider
            get() = EmptyStringProvider
    }
}

data class PreferredLanguage(
    val selected: PreferredLanguageType = PreferredLanguageType.ServerProfile(null),
    val options: List<PreferredLanguageType> = emptyList(),
)
