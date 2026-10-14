package tv.jellyvault.app.catalog.shelves

import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.pow
import kotlin.random.Random

/**
 * The home changes twice a week: Monday to Wednesday, then Thursday to Sunday
 */
data class ShelfPeriod(
    val start: LocalDate,
) {
    val seed: Long
        get() =
            start.get(IsoFields.WEEK_BASED_YEAR) * 1000L +
                start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) * 2L +
                if (start.dayOfWeek == DayOfWeek.MONDAY) 0 else 1

    fun previous(): ShelfPeriod = of(start.minusDays(1))

    companion object {
        fun of(date: LocalDate): ShelfPeriod {
            val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
            return ShelfPeriod(if (date.dayOfWeek.value <= DayOfWeek.WEDNESDAY.value) monday else monday.plusDays(3))
        }
    }
}

/** A shelf shown in the previous period is half as likely to come back */
private const val PREVIOUS_PERIOD_FACTOR = 0.5

/**
 * The shelves of [date]'s period, in display order. The same inputs always give the same plan.
 *
 * Shelves are drawn without replacement, weighted by base weight × (1 + taste affinity), with the previous
 * period's shelves less likely. At least [minSeries] series shelves are kept, and two shelves of the same group
 * are never side by side when another order exists.
 *
 * @param affinity share of each TMDB genre in the user's taste (0 to 1), empty without a profile
 */
fun planShelves(
    pool: List<Shelf>,
    date: LocalDate,
    affinity: Map<Int, Double>,
    count: Int,
    minSeries: Int,
): List<String> {
    val period = ShelfPeriod.of(date)
    val previous = draw(pool, period.previous(), affinity, count, minSeries, emptySet())
    return draw(pool, period, affinity, count, minSeries, previous.toSet())
}

private fun draw(
    pool: List<Shelf>,
    period: ShelfPeriod,
    affinity: Map<Int, Double>,
    count: Int,
    minSeries: Int,
    previous: Set<String>,
): List<String> {
    val random = Random(period.seed)
    // Efraimidis–Spirakis: key = u^(1/w), the largest keys win a weighted draw without replacement
    val keyed =
        pool
            .map { shelf ->
                val weight =
                    shelf.baseWeight * (1 + shelfAffinity(shelf, affinity)) * if (shelf.id in previous) PREVIOUS_PERIOD_FACTOR else 1.0
                shelf to random.nextDouble().pow(1.0 / weight)
            }.sortedByDescending { it.second }
    val chosen = keyed.take(count).toMutableList()
    val rest = keyed.drop(count)
    // Swap the weakest movie shelves for the strongest remaining series shelves until there are enough series
    val missing = minSeries - chosen.count { it.first.mediaType == TmdbMediaType.TV }
    if (missing > 0) {
        val series = rest.filter { it.first.mediaType == TmdbMediaType.TV }.take(missing)
        val weakestMovies = chosen.filter { it.first.mediaType == TmdbMediaType.MOVIE }.takeLast(series.size)
        chosen.removeAll(weakestMovies.toSet())
        chosen += series
        chosen.sortByDescending { it.second }
    }
    return spreadGroups(chosen.map { it.first }).map { it.id }
}

/**
 * Keep the draw order, but take the next shelf of another group when the next one repeats the last group
 */
private fun spreadGroups(ordered: List<Shelf>): List<Shelf> {
    val remaining = ordered.toMutableList()
    val result = mutableListOf<Shelf>()
    while (remaining.isNotEmpty()) {
        val last = result.lastOrNull()?.group
        val next = remaining.firstOrNull { it.group != last }
        if (next != null) {
            remaining.remove(next)
            result += next
            continue
        }
        // Only shelves of the last group are left: slot each one between two shelves of other groups
        val stuck = remaining.removeAt(0)
        val slot =
            (0..result.size).firstOrNull { i ->
                result.getOrNull(i - 1)?.group != stuck.group && result.getOrNull(i)?.group != stuck.group
            }
        if (slot != null) result.add(slot, stuck) else result += stuck
    }
    return result
}

/**
 * How much of the user's taste a shelf covers: the mean affinity of its genres
 */
internal fun shelfAffinity(
    shelf: Shelf,
    affinity: Map<Int, Double>,
): Double = if (shelf.genres.isEmpty() || affinity.isEmpty()) 0.0 else shelf.genres.sumOf { affinity[it] ?: 0.0 } / shelf.genres.size
