package tv.jellyvault.app.catalog.shelves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import java.time.LocalDate

class ShelfPlannerTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val pool = ShelfCatalog.ALL

    private fun plan(
        date: LocalDate,
        affinity: Map<Int, Double> = emptyMap(),
        types: Set<TmdbMediaType> = TmdbMediaType.entries.toSet(),
        count: Int = 16,
        minSeries: Int = 4,
    ) = planShelves(
        pool.filter { it.mediaType in types },
        date,
        affinity,
        count,
        if (TmdbMediaType.TV in types &&
            TmdbMediaType.MOVIE in types
        ) {
            minSeries
        } else {
            0
        },
    )

    @Test
    fun the_plan_holds_for_the_whole_period_and_changes_after() {
        val a = plan(monday)
        assertEquals(a, plan(monday.plusDays(1)))
        assertEquals(a, plan(monday.plusDays(2)))
        val b = plan(monday.plusDays(3))
        assertNotEquals(a, b)
        assertEquals(b, plan(monday.plusDays(6)))
        assertNotEquals(b, plan(monday.plusDays(7)))
    }

    @Test
    fun periods_split_monday_to_wednesday_and_thursday_to_sunday() {
        assertEquals(ShelfPeriod.of(monday), ShelfPeriod.of(monday.plusDays(2)))
        assertEquals(ShelfPeriod.of(monday.plusDays(3)), ShelfPeriod.of(monday.plusDays(6)))
        assertEquals(ShelfPeriod.of(monday.minusDays(1)), ShelfPeriod.of(monday).previous())
        assertEquals(ShelfPeriod.of(monday), ShelfPeriod.of(monday.plusDays(3)).previous())
    }

    @Test
    fun sixteen_distinct_shelves_with_at_least_four_series() {
        repeat(60) { i ->
            val ids = plan(monday.plusDays(i * 3L))
            assertEquals(16, ids.size)
            assertEquals(16, ids.toSet().size)
            val series = ids.count { ShelfCatalog.byId(it)!!.mediaType == TmdbMediaType.TV }
            assertTrue("only $series series", series >= 4)
        }
    }

    @Test
    fun never_two_shelves_of_the_same_group_side_by_side_when_avoidable() {
        repeat(60) { i ->
            val groups = plan(monday.plusDays(i * 3L)).map { ShelfCatalog.byId(it)!!.group }
            groups.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        }
    }

    @Test
    fun affinity_makes_a_shelf_come_back_more_often() {
        fun frequency(affinity: Map<Int, Double>) = (0 until 200).count { i -> "horror" in plan(monday.plusDays(i * 3L), affinity) }
        val neutral = frequency(emptyMap())
        val horrorFan = frequency(mapOf(27 to 1.0))
        assertTrue("neutral=$neutral fan=$horrorFan", horrorFan > neutral + 15)
    }

    @Test
    fun a_shelf_of_the_previous_period_comes_back_less_often() {
        var repeats = 0
        var total = 0
        (1 until 200).forEach { i ->
            val date = monday.plusDays(i * 3L + 1)
            val previous = plan(ShelfPeriod.of(date).previous().start)
            val current = plan(date)
            repeats += current.count { it in previous }
            total += current.size
        }
        // Without the penalty a 16-of-29 draw repeats more than half of its shelves
        assertTrue("repeat share ${repeats.toDouble() / total}", repeats.toDouble() / total < 0.55)
    }

    @Test
    fun single_type_pages_get_only_their_type() {
        val movies = plan(monday, types = setOf(TmdbMediaType.MOVIE), count = 12)
        assertEquals(12, movies.size)
        assertTrue(movies.all { ShelfCatalog.byId(it)!!.mediaType == TmdbMediaType.MOVIE })
        val series = plan(monday, types = setOf(TmdbMediaType.TV), count = 12)
        assertEquals(12, series.size)
        assertTrue(series.all { ShelfCatalog.byId(it)!!.mediaType == TmdbMediaType.TV })
        // The series page changes from one period to the next, not only its order
        val periods = (0 until 6).map { plan(monday.plusDays(it * 3L), types = setOf(TmdbMediaType.TV), count = 12).toSet() }
        assertTrue(periods.zipWithNext().any { (a, b) -> a != b })
    }

    @Test
    fun every_shelf_has_a_unique_id_and_a_vote_floor() {
        assertEquals(pool.size, pool.map { it.id }.toSet().size)
        assertTrue(pool.size >= 28)
        pool.forEach {
            val q = it.query(monday)
            assertEquals(it.mediaType, q.mediaType)
            assertTrue(it.id, q.minVotes >= 100)
        }
    }
}
