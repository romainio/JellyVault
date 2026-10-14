package tv.jellyvault.app.catalog

import timber.log.Timber
import tv.jellyvault.app.data.model.HomeRowConfig
import java.io.File
import java.io.IOException

/**
 * Applies [migrateHomeOrder] once per user, so a user who later puts "Continue" back on top keeps that order
 */
class HomeOrderMigration(
    private val dir: File,
) {
    fun migrate(
        key: String,
        rows: List<HomeRowConfig>,
    ): List<HomeRowConfig> {
        val marker = File(dir, "$key$MARKER_SUFFIX")
        if (marker.exists()) return rows
        try {
            dir.mkdirs()
            marker.createNewFile()
        } catch (e: IOException) {
            Timber.w(e, "Could not record the home order migration")
        }
        return migrateHomeOrder(rows)
    }

    companion object {
        const val MARKER_SUFFIX = ".order_migrated"
    }
}
