package tv.jellyvault.app.catalog.shelves

import timber.log.Timber
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.CatalogMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.services.HomeRowConfigDisplay
import tv.jellyvault.app.ui.util.ResStringProvider
import java.io.File
import java.io.IOException

/** Expanded rows get ids far from the saved rows' ids */
private const val SHELF_ROW_ID_BASE = 10_000

/**
 * Replace each "Styles variés" row by one row per planned style (unknown ids are skipped)
 */
fun expandShelfRows(
    rows: List<HomeRowConfigDisplay>,
    plan: List<String>,
): List<HomeRowConfigDisplay> {
    var next = SHELF_ROW_ID_BASE
    return rows.flatMap { row ->
        val config = row.config
        if (config !is HomeRowConfig.TmdbShelves) {
            listOf(row)
        } else {
            plan.mapNotNull { id ->
                ShelfCatalog.byId(id)?.let { shelf ->
                    HomeRowConfigDisplay(next++, ResStringProvider(shelf.titleRes), HomeRowConfig.TmdbShelf(id, config.viewOptions))
                }
            }
        }
    }
}

private fun HomeRowConfig.isCatalogRow() =
    this is HomeRowConfig.TmdbTrending ||
        this is HomeRowConfig.TmdbGenre ||
        this is HomeRowConfig.TmdbForYou ||
        this is HomeRowConfig.TmdbBecause ||
        this is HomeRowConfig.TmdbShelves ||
        this is HomeRowConfig.TmdbWatchlist

/** The home created by default before the varied styles (2026-10) */
private fun isLastDefaultTop(rows: List<HomeRowConfig>): Boolean {
    if (rows.size < 6) return false
    val (a, b, c, d, e) = rows
    val f = rows[5]
    return a is HomeRowConfig.TmdbTrending && a.mediaType == TmdbMediaType.MOVIE &&
        b is HomeRowConfig.TmdbTrending && b.mediaType == TmdbMediaType.TV &&
        c is HomeRowConfig.TmdbForYou && c.mediaType == TmdbMediaType.MOVIE &&
        d is HomeRowConfig.TmdbBecause && d.mediaType == TmdbMediaType.MOVIE &&
        e is HomeRowConfig.TmdbForYou && e.mediaType == TmdbMediaType.TV &&
        f is HomeRowConfig.ContinueWatchingCombined
}

/**
 * The old default's genre rows become one "Styles variés" row, at the first one's place; a home without them
 * gets no styles (the user removed them).
 * The last default home also gets the new top order, with continue watching third. Other orders are kept.
 */
fun migrateToVariety(rows: List<HomeRowConfig>): List<HomeRowConfig> {
    if (rows.any { it is HomeRowConfig.TmdbShelves }) return rows
    if (rows.none { it.isCatalogRow() }) return rows
    val reordered =
        if (isLastDefaultTop(rows)) {
            val (trendM, trendT, forYouM, because, forYouT) = rows
            listOf(trendM, forYouM, rows[5], trendT, because, forYouT) + rows.drop(6)
        } else {
            rows
        }
    val withList = addWatchlist(reordered)
    // Only the genre rows of the old default become the styles: genre rows the user added stay
    val firstDefault = withList.indexOfFirst { it.isOldDefaultGenre() }
    if (firstDefault < 0) return withList
    return withList.filterIndexed { i, row -> i == firstDefault || !row.isOldDefaultGenre() }.map {
        if (it.isOldDefaultGenre()) HomeRowConfig.TmdbShelves() else it
    }
}

private fun HomeRowConfig.isOldDefaultGenre() =
    this is HomeRowConfig.TmdbGenre && genre in CatalogGenre.DEFAULTS && mediaType == CatalogMediaType.BOTH

/**
 * "Ta liste" goes after the last personal row (hidden while no TMDB account is linked)
 */
private fun addWatchlist(rows: List<HomeRowConfig>): List<HomeRowConfig> {
    if (rows.any { it is HomeRowConfig.TmdbWatchlist }) return rows
    val lastPersonal = rows.indexOfLast { it is HomeRowConfig.TmdbForYou || it is HomeRowConfig.TmdbBecause }
    if (lastPersonal < 0) return rows
    return rows.take(lastPersonal + 1) + HomeRowConfig.TmdbWatchlist() + rows.drop(lastPersonal + 1)
}

/**
 * Applies [migrateToVariety] once per user, so a choice the user makes later is never undone
 */
class VarietyMigration(
    private val dir: File,
) {
    fun markDone(key: String) {
        try {
            dir.mkdirs()
            File(dir, "$key$MARKER_SUFFIX").createNewFile()
        } catch (e: IOException) {
            Timber.w(e, "Could not record the variety migration")
        }
    }

    fun migrate(
        key: String,
        rows: List<HomeRowConfig>,
    ): List<HomeRowConfig> {
        if (File(dir, "$key$MARKER_SUFFIX").exists()) return rows
        markDone(key)
        return migrateToVariety(rows)
    }

    companion object {
        const val MARKER_SUFFIX = ".variety_migrated"
    }
}

/**
 * Home rows loaded only when the user scrolls near them: the styles after the first [eagerShelves] ones. Every
 * other row loads at once, as before.
 */
fun onDemandRows(
    rows: List<HomeRowConfigDisplay>,
    eagerShelves: Int,
): Set<Int> =
    rows.indices
        .filter { rows[it].config is HomeRowConfig.TmdbShelf }
        .drop(eagerShelves)
        .toSet()

/**
 * The waiting rows to load now that the user sees row [lastVisible]
 */
fun dueRows(
    waiting: Set<Int>,
    lastVisible: Int,
    ahead: Int,
): Set<Int> = waiting.filter { it <= lastVisible + ahead }.toSet()
