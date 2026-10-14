package tv.jellyvault.app.ui.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import tv.jellyvault.app.R
import tv.jellyvault.app.services.ServerReportService
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.theme.jv.JvColors
import tv.jellyvault.app.ui.tryRequestFocus
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.LoadingState
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

@HiltViewModel
class ErrorViewModel
    @Inject
    constructor(
        private val serverReportService: ServerReportService,
    ) : ViewModel() {
        fun sendLogs() {
            viewModelScope.launchIO { serverReportService.sendAppLogs() }
        }
    }

/**
 * True when the error (or one of its causes) means the server could not be reached
 */
fun Throwable?.isNetworkError(): Boolean {
    var current = this
    var depth = 0
    while (current != null && depth < 10) {
        when (current) {
            is ConnectException,
            is UnknownHostException,
            is NoRouteToHostException,
            is SocketTimeoutException,
            is TimeoutException,
            -> return true
        }
        if (current is IOException) return true
        // The Jellyfin SDK wraps transport failures in its own exceptions (e.g. its TimeoutException)
        if (current.javaClass.simpleName.contains("Timeout", ignoreCase = true)) return true
        current = current.cause.takeIf { it !== current }
        depth++
    }
    return false
}

/**
 * Lambda going back like the remote's Back key, or null when nothing can handle it
 */
@Composable
fun rememberBackAction(): (() -> Unit)? {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return null
    return remember(dispatcher) { { dispatcher.onBackPressed() } }
}

/**
 * Friendly error screen: icon, short title, one line of explanation, then "Réessayer" / "Retour".
 * Technical details and the "send logs" action stay behind "Détails".
 *
 * @param onRetry reloads the failed content; its button gets the focus first
 * @param onBack goes back to the previous screen
 */
@Composable
fun ErrorMessage(
    message: String?,
    exception: Throwable?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    viewModel: ErrorViewModel = hiltViewModel(),
) {
    val network = remember(exception) { exception.isNetworkError() }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    // Full-page errors (with an action) take the focus on their first action: Réessayer, else Retour
    val firstActionFocus = remember { FocusRequester() }
    if (onRetry != null || onBack != null) {
        LaunchedEffect(Unit) { firstActionFocus.tryRequestFocus() }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 640.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (network) JvColors.Lavender else JvColors.Error,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text =
                    stringResource(
                        if (network) R.string.jv_error_title_network else R.string.jv_error_title_generic,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text =
                    when {
                        network -> stringResource(R.string.jv_error_body_network)
                        else -> stringResource(R.string.jv_error_body_generic)
                    },
                color = JvColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                if (onRetry != null) {
                    Button(
                        onClick = onRetry,
                        style = ButtonStyle.Primary,
                        contentPadding = ErrorButtonPadding,
                        modifier = Modifier.focusRequester(firstActionFocus),
                    ) {
                        // No tint: the icon follows the button's content color (dark on the white focused container)
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.retry))
                    }
                }
                if (onBack != null) {
                    Button(
                        onClick = onBack,
                        contentPadding = ErrorButtonPadding,
                        modifier = if (onRetry == null) Modifier.focusRequester(firstActionFocus) else Modifier,
                    ) {
                        Text(stringResource(R.string.jv_back))
                    }
                }
                Button(
                    onClick = { showDetails = !showDetails },
                    contentPadding = ErrorButtonPadding,
                ) {
                    Text(stringResource(if (showDetails) R.string.jv_hide_details else R.string.jv_details))
                }
            }
            if (showDetails) {
                ErrorDetails(message, exception)
                Button(
                    onClick = { viewModel.sendLogs() },
                    contentPadding = ErrorButtonPadding,
                ) {
                    Text(stringResource(R.string.jv_send_logs))
                }
            }
        }
    }
}

private val ErrorButtonPadding =
    PaddingValues(horizontal = 16.dp, vertical = 4.dp)

@Composable
private fun ErrorDetails(
    message: String?,
    exception: Throwable?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        exception?.let { ex ->
            Text(
                text = listOfNotNull(ex.javaClass.simpleName, ex.localizedMessage).joinToString(": "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        var cause = exception?.cause
        var depth = 0
        while (cause != null && depth < 5) {
            cause.localizedMessage?.let {
                Text(
                    text = stringResource(R.string.jv_error_cause, it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            cause = cause.cause.takeIf { it !== cause }
            depth++
        }
    }
}

@Composable
fun ErrorMessage(
    error: LoadingState.Error,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) = ErrorMessage(
    message = error.message,
    exception = error.exception,
    modifier = modifier,
    onRetry = onRetry,
    onBack = onBack,
)

@Composable
fun ErrorMessage(
    error: DataLoadingState.Error,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) = ErrorMessage(
    message = error.message,
    exception = error.exception,
    modifier = modifier,
    onRetry = onRetry,
    onBack = onBack,
)
