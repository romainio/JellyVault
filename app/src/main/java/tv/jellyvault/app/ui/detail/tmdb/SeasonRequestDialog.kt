package tv.jellyvault.app.ui.detail.tmdb

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.arr.SeasonChoice
import tv.jellyvault.app.ui.theme.jv.JvShapes
import tv.jellyvault.app.ui.tryRequestFocus

/**
 * Choose which seasons of a series to request, numbered as Sonarr numbers them. Locked seasons are already requested.
 */
@Composable
fun SeasonRequestDialog(
    choices: List<SeasonChoice>,
    onRequest: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val locked = choices.filter { it.locked }.map { it.number }.toSet()
    val requestable = choices.map { it.number }.filter { it !in locked }.toSet()
    val latest = choices.maxOfOrNull { it.number }
    var choosing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(choosing) { firstFocus.tryRequestFocus() }

    // Back from "Choisir…" returns to the three choices
    Dialog(onDismissRequest = { if (choosing) choosing = false else onDismiss() }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .width(480.dp)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp), JvShapes.Dialog)
                    .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.tmdb_request),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!choosing) {
                ListItem(
                    selected = false,
                    enabled = requestable.isNotEmpty(),
                    onClick = { onRequest(requestable) },
                    headlineContent = { Text(stringResource(R.string.tmdb_all_seasons)) },
                    modifier = Modifier.focusRequester(firstFocus),
                )
                ListItem(
                    selected = false,
                    enabled = latest != null && latest !in locked,
                    onClick = { latest?.let { onRequest(setOf(it)) } },
                    headlineContent = { Text(stringResource(R.string.tmdb_latest_season)) },
                )
                ListItem(
                    selected = false,
                    enabled = requestable.isNotEmpty(),
                    onClick = { choosing = true },
                    headlineContent = { Text(stringResource(R.string.tmdb_choose_seasons)) },
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(choices) { season ->
                        val isLocked = season.number in locked
                        val checked = isLocked || season.number in selected
                        ListItem(
                            selected = false,
                            enabled = !isLocked,
                            onClick = {
                                selected = if (season.number in selected) selected - season.number else selected + season.number
                            },
                            headlineContent = {
                                val name = season.name ?: stringResource(R.string.tmdb_season_number, season.number)
                                Text(
                                    if (season.episodeCount != null) {
                                        stringResource(R.string.tmdb_season_episodes, name, season.episodeCount)
                                    } else {
                                        name
                                    },
                                )
                            },
                            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
                            modifier = if (season == choices.firstOrNull { !it.locked }) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
                Button(
                    shape = ButtonDefaults.shape(JvShapes.Button),
                    enabled = selected.isNotEmpty(),
                    onClick = { onRequest(selected) },
                ) {
                    Text(stringResource(R.string.tmdb_request))
                }
            }
        }
    }
}
