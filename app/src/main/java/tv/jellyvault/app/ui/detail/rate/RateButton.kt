package tv.jellyvault.app.ui.detail.rate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.ui.components.Button
import tv.jellyvault.app.ui.components.ExpandableFaButton
import tv.jellyvault.app.ui.components.JvBanner
import tv.jellyvault.app.ui.components.JvBannerMessage
import tv.jellyvault.app.ui.components.ScrollableDialog
import tv.jellyvault.app.ui.preferences.tmdb.QrCode
import tv.jellyvault.app.ui.tryRequestFocus

/**
 * "Rate" on a title page, only when "Rate from the TV" is on. The dialog also shows the IMDb page as a QR code, since
 * IMDb stays the reference: a later IMDb rating replaces this one.
 */
@Composable
fun RateButton(
    item: TmdbItem,
    imdbId: String?,
    modifier: Modifier = Modifier,
    viewModel: RateViewModel = hiltViewModel(key = "rate-${item.mediaType.path}-${item.tmdbId}"),
) {
    LaunchedEffect(item) { viewModel.init(item) }
    RateButtonContent(item.title, imdbId, viewModel, modifier)
}

/**
 * The same on a Jellyfin page, from the title's TMDB id
 */
@Composable
fun RateButton(
    mediaType: TmdbMediaType,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    viewModel: RateViewModel = hiltViewModel(key = "rate-${mediaType.path}-$tmdbId"),
) {
    LaunchedEffect(mediaType, tmdbId) { viewModel.init(mediaType, tmdbId) }
    val state by viewModel.state.collectAsState()
    state.title?.let { RateButtonContent(it, state.imdbId, viewModel, modifier) }
}

@Composable
private fun RateButtonContent(
    title: String,
    imdbId: String?,
    viewModel: RateViewModel,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsState()
    if (!state.enabled) return
    var open by rememberSaveable { mutableStateOf(false) }

    ExpandableFaButton(
        title = state.current?.let { stringResource(R.string.rate_title_rated, it) } ?: stringResource(R.string.rate_title),
        iconStringRes = R.string.fa_star,
        active = state.current != null,
        onClick = {
            viewModel.clearError()
            open = true
        },
        modifier = modifier,
    )
    if (open) {
        RateDialog(
            title = title,
            imdbId = imdbId,
            state = state,
            onRate = { value ->
                viewModel.rate(value)
                open = false
            },
            onDismiss = { open = false },
        )
    }
    val confirmation =
        state.confirmation?.let { c ->
            JvBannerMessage(c.value?.let { stringResource(R.string.rate_saved, it) } ?: stringResource(R.string.rate_removed))
        }
    JvBanner(confirmation, onDismiss = viewModel::consumeConfirmation)
    state.error?.let { error ->
        ScrollableDialog(onDismissRequest = viewModel::clearError, width = 520.dp) {
            item { Text(stringResource(error), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
            item { Button(onClick = viewModel::clearError, contentPadding = TextPadding) { Text(stringResource(R.string.tmdb_close)) } }
        }
    }
}

@Composable
private fun RateDialog(
    title: String,
    imdbId: String?,
    state: RateState,
    onRate: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // The current rating takes the focus, or the middle of the scale
    val focus = remember { FocusRequester() }
    val focused = state.current ?: DEFAULT_FOCUS
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    ScrollableDialog(onDismissRequest = onDismiss, width = 900.dp, maxHeight = 560.dp) {
        item {
            Text(
                stringResource(R.string.rate_dialog_title, title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        state.current?.let { current ->
            item { Text(stringResource(R.string.rate_current, current.toString()), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..10).forEach { value ->
                    Button(
                        onClick = { onRate(value) },
                        enabled = !state.sending,
                        contentPadding = TextPadding,
                        modifier = if (value == focused) Modifier.focusRequester(focus) else Modifier,
                    ) {
                        Text(value.toString())
                    }
                }
            }
        }
        if (state.current != null) {
            item {
                Button(
                    onClick = { onRate(null) },
                    enabled = !state.sending,
                    contentPadding = TextPadding,
                ) { Text(stringResource(R.string.rate_remove)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                imdbId?.let { QrCode(imdbUrl(it), size = 140.dp) }
                Column(modifier = Modifier.widthIn(max = 600.dp)) {
                    Text(
                        stringResource(R.string.rate_imdb_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val DEFAULT_FOCUS = 7

private val TextPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

/** The mobile page opens the IMDb app when installed, with its rating star */
internal fun imdbUrl(imdbId: String) = "https://m.imdb.com/title/$imdbId/"
