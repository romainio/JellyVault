package tv.jellyvault.app.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens once the home page is worth showing: its first rows loaded and their posters in the image cache.
 * The launch intro stays on screen until then, so the app appears filled instead of popping in row by row
 */
@Singleton
class StartupGate
    @Inject
    constructor() {
        private val _open = MutableStateFlow(false)
        val open: StateFlow<Boolean> = _open

        fun open() {
            _open.value = true
        }
    }
