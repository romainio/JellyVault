package tv.jellyvault.app.util

import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import tv.jellyvault.app.JellyVaultApplication
import tv.jellyvault.app.R
import tv.jellyvault.app.ui.showToast
import kotlin.coroutines.CoroutineContext

/**
 * A general [CoroutineExceptionHandler] which can optionally show a [Toast] when an exception is thrown
 *
 * @param autoToast automatically show a toast with the exception's message
 */
class ExceptionHandler(
    private val autoToast: Boolean = false,
) : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler

    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        if (exception is CancellationException) {
            // Don't log/toast cancellations
            return
        }
        Timber.e(exception, "Exception in coroutine")

        if (autoToast) {
            runBlocking {
                showToast(
                    JellyVaultApplication.instance,
                    JellyVaultApplication.instance.getString(R.string.jv_error_generic),
                    Toast.LENGTH_LONG,
                )
            }
        }
    }
}
