package tv.jellyvault.app.services

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import tv.jellyvault.app.LegacyNames
import tv.jellyvault.app.services.tvprovider.TvProviderWorker.Companion.CHANNELS_PREFS
import tv.jellyvault.app.services.tvprovider.TvProviderWorker.Companion.LATEST_CHANNEL_KEY
import javax.inject.Inject

/**
 * Builds before 1.1.0 lived in a different package: background jobs and TV home rows saved by them name classes
 * that no longer exist. Drops them; the app schedules and publishes new ones on its own.
 */
class PackageRenameMigration
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val workManager: WorkManager,
    ) {
        fun run() {
            LegacyNames.WORK_NAMES.forEach { workManager.cancelUniqueWork(it) }
            workManager.pruneWork()
            forgetTvRows()
        }

        private fun forgetTvRows() {
            val channels = context.getSharedPreferences(CHANNELS_PREFS, Context.MODE_PRIVATE)
            val resolver = context.contentResolver
            try {
                channels.getString(LATEST_CHANNEL_KEY, null)?.let { resolver.delete(it.toUri(), null, null) }
            } catch (e: Exception) {
                Timber.w(e, "Could not delete the old TV channel")
            }
            try {
                // The provider only lets an app delete its own Watch Next rows
                resolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
            } catch (e: Exception) {
                Timber.w(e, "Could not delete the old Watch Next rows")
            }
            channels.edit(commit = true) { remove(LATEST_CHANNEL_KEY) }
        }

        companion object {
            const val FIRST_RENAMED = "1.1.0"
        }
    }
