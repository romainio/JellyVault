package tv.jellyvault.app.catalog

import timber.log.Timber
import tv.jellyvault.app.data.model.HomeRowConfig
import java.io.File
import java.io.IOException

/**
 * Applies [addPersonalRows] once per user, so a personal row the user removes later does not come back
 */
class PersonalRowsMigration(
    private val dir: File,
) {
    /**
     * A home page created with the personal rows already in it must not be migrated later
     */
    fun markDone(key: String) {
        try {
            dir.mkdirs()
            File(dir, "$key$MARKER_SUFFIX").createNewFile()
        } catch (e: IOException) {
            Timber.w(e, "Could not record the personal rows migration")
        }
    }

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
            Timber.w(e, "Could not record the personal rows migration")
        }
        return addPersonalRows(rows)
    }

    companion object {
        const val MARKER_SUFFIX = ".personal_rows_added"
    }
}
