package tv.jellyvault.app.ui.detail.tmdb

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.ui.components.DialogItem
import tv.jellyvault.app.ui.components.DialogPopup
import tv.jellyvault.app.ui.components.ExpandablePlayButton
import tv.jellyvault.app.ui.components.ScrollableDialog
import tv.jellyvault.app.ui.formatDuration
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

sealed interface TmdbMoreEntry {
    data object Cast : TmdbMoreEntry

    data class Saga(
        val target: BrowseTarget,
    ) : TmdbMoreEntry

    data class Studio(
        val target: BrowseTarget,
    ) : TmdbMoreEntry

    data object Info : TmdbMoreEntry

    /** The Jellyfin page of a title in the library (chapters, versions…) */
    data object JellyfinPage : TmdbMoreEntry
}

/**
 * Entries of the « … » menu of a TMDB detail page, empty parts left out
 */
fun tmdbMoreEntries(
    details: TmdbDetails,
    owned: Boolean = false,
): List<TmdbMoreEntry> =
    buildList {
        if (details.cast.isNotEmpty()) add(TmdbMoreEntry.Cast)
        details.collection?.let { add(TmdbMoreEntry.Saga(BrowseTarget.AnyCollection(it.id, it.name))) }
        if (details.item.mediaType == TmdbMediaType.MOVIE) {
            details.studio?.let { add(TmdbMoreEntry.Studio(BrowseTarget.AnyCompany(it.id, it.name))) }
        } else {
            details.network?.let { add(TmdbMoreEntry.Studio(BrowseTarget.AnyNetwork(it.id, it.name))) }
        }
        add(TmdbMoreEntry.Info)
        if (owned) add(TmdbMoreEntry.JellyfinPage)
    }

/** A TMDB date (yyyy-MM-dd) in the interface's short format, e.g. 14/10/2026 in French or 10/14/26 in English */
private fun localDate(raw: String): String =
    runCatching { LocalDate.parse(raw).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) }.getOrDefault(raw)

/**
 * Director or creators, dates, countries, runtime or seasons, budget
 */
@Composable
fun TmdbInfoDialog(
    details: TmdbDetails,
    onDismiss: () -> Unit,
) {
    val movie = details.item.mediaType == TmdbMediaType.MOVIE
    val rows =
        buildList<Pair<Int, String>> {
            if (details.makers.isNotEmpty()) {
                add((if (movie) R.string.tmdb_info_made_by else R.string.tmdb_info_created_by) to details.makers.joinToString(", "))
            }
            details.releaseDate?.let { add(R.string.tmdb_info_release to localDate(it)) }
            if (details.countries.isNotEmpty()) add(R.string.tmdb_info_countries to details.countries.joinToString(", "))
        }
    val resources = LocalResources.current
    val runtime = details.runtimeMinutes?.takeIf { it > 0 }?.let { resources.formatDuration(it.minutes) }
    val seasons =
        details.seasons.size.takeIf { !movie && it > 0 }?.let { count ->
            pluralStringResource(R.plurals.tmdb_seasons, count, count) + (details.episodeCount?.let { " · $it épisodes" } ?: "")
        }
    val budget =
        details.budget?.let { NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(it) }
    ScrollableDialog(onDismissRequest = onDismiss) {
        item {
            Text(
                stringResource(R.string.tmdb_info),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        rows.forEach { (label, value) -> item { InfoRow(label, value) } }
        runtime?.let { item { InfoRow(R.string.tmdb_info_runtime, it) } }
        seasons?.let { item { InfoRow(R.string.tmdb_info_seasons, it) } }
        budget?.let { item { InfoRow(R.string.tmdb_info_budget, it) } }
    }
}

@Composable
private fun InfoRow(
    @StringRes label: Int,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Round « … » button of the TMDB detail page and the dialogs it opens
 */
@Composable
fun TmdbMoreButton(
    details: TmdbDetails,
    owned: Boolean,
    onOpenJellyfinPage: () -> Unit,
    onBrowse: (BrowseTarget) -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    var showCast by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val movie = details.item.mediaType == TmdbMediaType.MOVIE
    // Same « More » button as the Jellyfin page
    ExpandablePlayButton(
        title = R.string.tmdb_more,
        resume = Duration.ZERO,
        icon = Icons.Default.MoreVert,
        onClick = { showMore = true },
    )
    if (showMore) {
        DialogPopup(
            showDialog = true,
            title = details.item.title,
            dialogItems =
                tmdbMoreEntries(details, owned).map { entry ->
                    when (entry) {
                        TmdbMoreEntry.Cast -> {
                            DialogItem(
                                context.getString(R.string.tmdb_cast),
                                Icons.Default.Person,
                                dismissOnClick = true,
                            ) { showCast = true }
                        }

                        is TmdbMoreEntry.Saga -> {
                            DialogItem(
                                context.getString(R.string.tmdb_saga_format, details.collection?.name.orEmpty()),
                                Icons.AutoMirrored.Filled.List,
                                dismissOnClick = true,
                            ) { onBrowse(entry.target) }
                        }

                        is TmdbMoreEntry.Studio -> {
                            DialogItem(
                                if (movie) {
                                    context.getString(R.string.tmdb_studio_format, details.studio?.name.orEmpty())
                                } else {
                                    context.getString(R.string.tmdb_network_format, details.network?.name.orEmpty())
                                },
                                Icons.Default.Home,
                                dismissOnClick = true,
                            ) { onBrowse(entry.target) }
                        }

                        TmdbMoreEntry.JellyfinPage -> {
                            DialogItem(
                                context.getString(R.string.tmdb_full_page),
                                Icons.AutoMirrored.Filled.ArrowForward,
                                dismissOnClick = true,
                            ) { onOpenJellyfinPage() }
                        }

                        TmdbMoreEntry.Info -> {
                            DialogItem(context.getString(R.string.tmdb_info), Icons.Default.Info, dismissOnClick = true) { showInfo = true }
                        }
                    }
                },
            onDismissRequest = { showMore = false },
            waitToLoad = false,
        )
    }
    if (showCast) TmdbCastDialog(details.cast) { showCast = false }
    if (showInfo) TmdbInfoDialog(details) { showInfo = false }
}
