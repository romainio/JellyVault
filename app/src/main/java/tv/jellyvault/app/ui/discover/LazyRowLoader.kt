package tv.jellyvault.app.ui.discover

/**
 * Decides which rows to load: the first [initial] rows, then rows up to [ahead] below the lowest visible one
 */
class LazyRowLoader(
    private val initial: Int = INITIAL_ROWS,
    private val ahead: Int = ROWS_AHEAD,
) {
    private val requested = HashSet<Int>()

    fun reset() = requested.clear()

    /**
     * Let a failed row be requested again
     */
    fun forget(index: Int) {
        requested.remove(index)
    }

    /**
     * @return the rows to start loading now, never one already requested
     */
    fun next(
        lastVisible: Int,
        total: Int,
    ): List<Int> {
        val last = minOf(total - 1, maxOf(initial - 1, lastVisible + ahead))
        return (0..last).filter { requested.add(it) }
    }

    companion object {
        const val INITIAL_ROWS = 5
        const val ROWS_AHEAD = 3
    }
}
