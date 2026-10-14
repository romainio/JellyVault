package tv.jellyvault.app.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher

/**
 * Analogous to [kotlinx.coroutines.Dispatchers], but easier to swap for testing
 */
object JvDispatchers {
    @Suppress("ktlint:standard:property-naming")
    val Main: MainCoroutineDispatcher get() = kotlinx.coroutines.Dispatchers.Main

    @Suppress("ktlint:standard:property-naming")
    var IO: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
        internal set

    @Suppress("ktlint:standard:property-naming")
    var Default: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
        internal set
}
