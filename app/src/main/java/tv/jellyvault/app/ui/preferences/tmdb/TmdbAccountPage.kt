package tv.jellyvault.app.ui.preferences.tmdb

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.account.TmdbAccountState
import tv.jellyvault.app.ui.preferences.ClickPreference
import tv.jellyvault.app.ui.preferences.StringInput
import tv.jellyvault.app.ui.preferences.StringInputDialog
import tv.jellyvault.app.ui.tryRequestFocus

/**
 * Link the TMDB account that holds the ratings: a QR code to approve on a phone, then "C'est validé"
 */
@Composable
fun TmdbAccountPage(
    modifier: Modifier = Modifier,
    viewModel: TmdbAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focus = remember { FocusRequester() }
    val linking = state.link != null
    var editingKey by remember { mutableStateOf(false) }
    val hasKey = state.maskedKey != null || state.usesBuildKey
    // The first action of each step takes the focus
    LaunchedEffect(linking, state.account, hasKey) { focus.tryRequestFocus() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.tmdb_account),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            Text(
                text = stringResource(R.string.tmdb_account_explain),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 720.dp).padding(bottom = 16.dp),
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.tmdb_key),
                summary =
                    state.maskedKey ?: stringResource(if (state.usesBuildKey) R.string.tmdb_key_built_in else R.string.tmdb_key_missing),
                onClick = { editingKey = true },
                // Without a key there is no other action: the key row takes the focus
                modifier = if (hasKey) Modifier else Modifier.focusRequester(focus),
            )
        }
        item {
            Text(
                text = stringResource(R.string.tmdb_key_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 720.dp).padding(bottom = 16.dp),
            )
        }
        val account = state.account
        if (account is TmdbAccountState.Linked && !linking) {
            item {
                Text(
                    text =
                        account.username?.let { stringResource(R.string.tmdb_account_linked, it) }
                            ?: stringResource(R.string.tmdb_account_linked_no_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            state.received?.let { received -> item { ReceivedRatingsText(received) } }
        }
        if (account == TmdbAccountState.Expired && !linking) {
            item {
                Text(
                    text = stringResource(R.string.tmdb_account_expired),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        val link = state.link
        if (link != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    QrCode(link.url)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 520.dp)) {
                        Text(
                            text = stringResource(R.string.tmdb_account_scan),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = link.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                ClickPreference(
                    title = stringResource(R.string.tmdb_account_approved),
                    onClick = viewModel::finishLink,
                    modifier = Modifier.focusRequester(focus),
                )
            }
            item {
                ClickPreference(title = stringResource(R.string.tmdb_account_cancel), onClick = viewModel::cancelLink)
            }
        } else if (account is TmdbAccountState.Linked) {
            item {
                ClickPreference(
                    title = stringResource(R.string.tmdb_account_unlink),
                    onClick = viewModel::unlink,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        } else if (hasKey) {
            item {
                ClickPreference(
                    title = stringResource(R.string.tmdb_account_link),
                    onClick = viewModel::startLink,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    text = stringResource(error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (editingKey) {
        val onSave = { text: String ->
            viewModel.setKey(text)
            editingKey = false
        }
        StringInputDialog(
            input =
                StringInput(
                    title = stringResource(R.string.tmdb_key),
                    value = "",
                    // The remote cannot always leave the text field: the keyboard ✓ (or Enter) saves
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    onSubmit = onSave,
                    // Readable while typing: a 32-character key entered blind with a remote is error-prone
                    isPassword = false,
                ),
            onSave = onSave,
            onDismissRequest = { editingKey = false },
        )
    }
}

/**
 * What reached the app, so a stalled sync shows: the newest ratings are missing, or nothing arrived
 */
@Composable
private fun ReceivedRatingsText(received: ReceivedRatings) {
    val lines =
        when {
            received.readAt == 0L -> {
                listOf(stringResource(R.string.tmdb_account_reading))
            }

            received.ratings == 0 -> {
                listOf(stringResource(R.string.tmdb_account_no_ratings))
            }

            else -> {
                listOfNotNull(
                    stringResource(
                        R.string.tmdb_account_received,
                        received.ratings,
                        received.watchlist,
                        readAgo(received.readAt),
                    ),
                    received.latest.takeIf { it.isNotEmpty() }?.let {
                        stringResource(
                            R.string.tmdb_account_latest,
                            it.joinToString(" · "),
                        )
                    },
                    stringResource(R.string.tmdb_account_latest_hint),
                )
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 720.dp).padding(bottom = 16.dp)) {
        lines.forEach {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "just now", or "2 hours ago" in lower case to sit inside a sentence
 */
@Composable
private fun readAgo(at: Long): String {
    val now = System.currentTimeMillis()
    if (now - at < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.tmdb_account_just_now)
    return DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString().replaceFirstChar { it.lowercase() }
}
