package tv.jellyvault.app.catalog

import timber.log.Timber
import tv.jellyvault.app.data.model.HomeRowConfig
import java.io.File
import java.io.IOException

/**
 * Applies [addDirectorsRow] once per user, so the row does not come back after the user removes it
 */
class DirectorsRowMigration(
    private val dir: File,
) {
    /**
     * A home page created with the row already in it must not be migrated later
     */
    fun markDone(key: String) {
        try {
            dir.mkdirs()
            File(dir, "$key$MARKER_SUFFIX").createNewFile()
        } catch (e: IOException) {
            Timber.w(e, "Could not record the directors row migration")
        }
    }

    fun migrate(
        key: String,
        rows: List<HomeRowConfig>,
    ): List<HomeRowConfig> {
        if (File(dir, "$key$MARKER_SUFFIX").exists()) return rows
        markDone(key)
        return addDirectorsRow(rows)
    }

    companion object {
        const val MARKER_SUFFIX = ".directors_row_added"
    }
}
