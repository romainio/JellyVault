package tv.jellyvault.app.ui.preferences.arr

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.arr.ArrService
import tv.jellyvault.app.ui.components.DialogItem
import tv.jellyvault.app.ui.components.DialogParams
import tv.jellyvault.app.ui.components.DialogPopup
import tv.jellyvault.app.ui.preferences.ClickPreference
import tv.jellyvault.app.ui.preferences.StringInput
import tv.jellyvault.app.ui.preferences.StringInputDialog
import tv.jellyvault.app.ui.tryRequestFocus

private enum class ArrField { URL, API_KEY, BASIC_USER, BASIC_PASSWORD }

private enum class ArrChoice { PROFILE, FOLDER }

/**
 * Radarr or Sonarr connection settings: URL, API key, optional basic auth, then quality profile and root folder
 */
@Composable
fun ArrSettingsPage(
    service: ArrService,
    modifier: Modifier = Modifier,
    viewModel: ArrSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val config = state.config
    var editing by remember { mutableStateOf<ArrField?>(null) }
    var choosing by remember { mutableStateOf<ArrChoice?>(null) }
    val focusRequester = remember { FocusRequester() }
    val serviceName = stringResource(if (service == ArrService.RADARR) R.string.arr_radarr else R.string.arr_sonarr)
    val notSet = stringResource(R.string.arr_not_set)

    LaunchedEffect(service) {
        viewModel.init(service)
        focusRequester.tryRequestFocus()
    }
    LaunchedEffect(service, config.canConnect) {
        if (config.canConnect && state.connection == ArrSettingsViewModel.Connection.Unknown) viewModel.testConnection()
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        item {
            Text(
                text = serviceName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.arr_url),
                summary = config.url.ifBlank { notSet },
                onClick = { editing = ArrField.URL },
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.arr_api_key),
                summary = maskApiKey(config.apiKey) ?: notSet,
                onClick = { editing = ArrField.API_KEY },
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.arr_basic_user),
                summary = config.basicUser.ifBlank { notSet },
                onClick = { editing = ArrField.BASIC_USER },
            )
        }
        item {
            ClickPreference(
                title = stringResource(R.string.arr_basic_password),
                summary = maskPassword(config.basicPassword) ?: notSet,
                onClick = { editing = ArrField.BASIC_PASSWORD },
            )
        }
        item {
            val connection = state.connection
            ClickPreference(
                title = stringResource(R.string.arr_test_connection),
                summary =
                    when (connection) {
                        ArrSettingsViewModel.Connection.Unknown -> {
                            null
                        }

                        ArrSettingsViewModel.Connection.Testing -> {
                            stringResource(R.string.loading)
                        }

                        is ArrSettingsViewModel.Connection.Connected -> {
                            stringResource(R.string.arr_connected_version, serviceName, connection.version)
                        }

                        is ArrSettingsViewModel.Connection.Failed -> {
                            if (connection.code != null) {
                                stringResource(connection.message, serviceName, connection.code)
                            } else {
                                stringResource(connection.message, serviceName)
                            }
                        }
                    },
                onClick = { viewModel.testConnection() },
            )
        }
        if (state.connection is ArrSettingsViewModel.Connection.Connected) {
            item {
                ClickPreference(
                    title = stringResource(R.string.arr_quality_profile),
                    summary = state.profiles.firstOrNull { it.id == config.qualityProfileId }?.name ?: notSet,
                    onClick = { choosing = ArrChoice.PROFILE },
                )
            }
            item {
                ClickPreference(
                    title = stringResource(R.string.arr_root_folder),
                    summary = config.rootFolderPath.ifBlank { notSet },
                    onClick = { choosing = ArrChoice.FOLDER },
                )
            }
        }
    }

    editing?.let { field ->
        val (title, value, keyboard) =
            when (field) {
                ArrField.URL -> Triple(R.string.arr_url, config.url, KeyboardType.Uri)
                ArrField.API_KEY -> Triple(R.string.arr_api_key, config.apiKey, KeyboardType.Ascii)
                ArrField.BASIC_USER -> Triple(R.string.arr_basic_user, config.basicUser, KeyboardType.Text)
                ArrField.BASIC_PASSWORD -> Triple(R.string.arr_basic_password, "", KeyboardType.Password)
            }
        val onSave = { text: String ->
            when (field) {
                ArrField.URL -> viewModel.setUrl(text)
                ArrField.API_KEY -> viewModel.setApiKey(text)
                ArrField.BASIC_USER -> viewModel.setBasicUser(text)
                ArrField.BASIC_PASSWORD -> viewModel.setBasicPassword(text)
            }
            editing = null
        }
        StringInputDialog(
            input =
                StringInput(
                    title = stringResource(title),
                    value = value,
                    // The remote cannot always leave the text field: the keyboard ✓ (or Enter) saves
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                    onSubmit = onSave,
                    // The API key stays readable while typing it: 32 characters entered blind with a remote are error-prone
                    isPassword = field == ArrField.BASIC_PASSWORD,
                ),
            onSave = onSave,
            onDismissRequest = { editing = null },
        )
    }

    choosing?.let { choice ->
        val items =
            when (choice) {
                ArrChoice.PROFILE -> {
                    state.profiles.map { profile ->
                        DialogItem(
                            headlineContent = { Text(profile.name) },
                            onClick = {
                                viewModel.selectProfile(profile.id)
                                choosing = null
                            },
                        )
                    }
                }

                ArrChoice.FOLDER -> {
                    state.folders.map { folder ->
                        val freeText =
                            folder.freeSpace?.let { free ->
                                stringResource(R.string.arr_free_space, Formatter.formatShortFileSize(context, free))
                            }
                        DialogItem(
                            headlineContent = { Text(folder.path) },
                            supportingContent = freeText?.let { text -> @Composable { Text(text) } },
                            onClick = {
                                viewModel.selectFolder(folder.path)
                                choosing = null
                            },
                        )
                    }
                }
            }
        DialogPopup(
            params =
                DialogParams(
                    fromLongClick = false,
                    title = stringResource(if (choice == ArrChoice.PROFILE) R.string.arr_quality_profile else R.string.arr_root_folder),
                    items = items,
                ),
            onDismissRequest = { choosing = null },
        )
    }
}

internal fun maskApiKey(value: String): String? = if (value.isBlank()) null else "•••• " + value.takeLast(4)

internal fun maskPassword(value: String): String? = if (value.isEmpty()) null else "••••••••"
