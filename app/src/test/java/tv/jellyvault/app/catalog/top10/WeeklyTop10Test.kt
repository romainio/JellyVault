package tv.jellyvault.app.catalog.top10

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.LocalDate

class WeeklyTop10Test {
    private val header = "country_name\tcountry_iso2\tweek\tcategory\tweekly_rank\tshow_title\tseason_title\tcumulative_weeks_in_top_10"

    private fun row(
        c: String,
        week: String,
        cat: String,
        rank: Int,
        title: String,
    ) = "X\t$c\t$week\t$cat\t$rank\t$title\t$title: Season 1\t1"

    @Test
    fun latest_french_tv_week_in_rank_order_without_duplicates() {
        val lines =
            sequenceOf(
                header,
                row("AR", "2026-09-20", "TV", 1, "Other"),
                row("FR", "2026-09-13", "TV", 1, "Old"),
                row("FR", "2026-09-20", "TV", 3, "The Perfect Lie"),
                row("FR", "2026-09-20", "TV", 1, "Monster"),
                row("FR", "2026-09-20", "Films", 1, "A film"),
                row("FR", "2026-09-20", "TV", 2, "The Gentlemen"),
                row("FR", "2026-09-20", "TV", 4, "The Gentlemen"),
            )
        val top = parseWeeklyTop10(lines)!!
        assertEquals(LocalDate.of(2026, 9, 20), top.week)
        assertEquals(listOf("Monster", "The Gentlemen", "The Perfect Lie"), top.titles)
    }

    @Test
    fun no_matching_rows_gives_null() {
        assertNull(parseWeeklyTop10(sequenceOf(header, row("AR", "2026-09-20", "TV", 1, "X"))))
    }

    @Test
    fun refresh_once_the_week_is_old_and_not_more_than_daily() {
        val today = LocalDate.of(2026, 9, 28)
        val now = 1_000_000_000L
        val day = 24 * 3600 * 1000L
        assertTrue(needsRefresh(null, null, today, now))
        assertFalse(needsRefresh(LocalDate.of(2026, 9, 21), now - 2 * day, today, now))
        assertTrue(needsRefresh(LocalDate.of(2026, 9, 20), now - 2 * day, today, now))
        assertFalse(needsRefresh(LocalDate.of(2026, 9, 20), now - day / 2, today, now))
    }

    @Test
    fun a_failed_download_keeps_the_cache() =
        runTest {
            val dir =
                kotlin.io.path
                    .createTempDirectory()
                    .toFile()
            val cache = WeeklyTop10Cache(File(dir, "top10.json"))
            cache.write(Top10(LocalDate.of(2026, 9, 13), listOf("Kept")), fetchedAt = 0L)
            val titles =
                cache.refreshOrKeep(
                    today = LocalDate.of(2026, 9, 28),
                    nowMillis = 10 * 24 * 3600 * 1000L,
                ) { throw IOException("offline") }
            assertEquals(listOf("Kept"), titles)
        }

    @Test
    fun a_new_week_replaces_the_cache() =
        runTest {
            val dir =
                kotlin.io.path
                    .createTempDirectory()
                    .toFile()
            val cache = WeeklyTop10Cache(File(dir, "top10.json"))
            val titles =
                cache.refreshOrKeep(
                    today = LocalDate.of(2026, 9, 28),
                    nowMillis = 5L,
                ) { Top10(LocalDate.of(2026, 9, 20), listOf("New")) }
            assertEquals(listOf("New"), titles)
            // Fresh cache: no second download
            assertEquals(
                listOf("New"),
                cache.refreshOrKeep(LocalDate.of(2026, 9, 28), 6L) { throw IllegalStateException("should not download") },
            )
        }

    @Test
    fun a_failed_first_download_is_not_retried_the_same_day() =
        runTest {
            val dir =
                kotlin.io.path
                    .createTempDirectory()
                    .toFile()
            val cache = WeeklyTop10Cache(File(dir, "top10.json"))
            var calls = 0
            val failing: suspend () -> Top10? = {
                calls++
                throw IOException("403")
            }
            assertEquals(emptyList<String>(), cache.refreshOrKeep(LocalDate.of(2026, 9, 28), 1_000L, failing))
            assertEquals(emptyList<String>(), cache.refreshOrKeep(LocalDate.of(2026, 9, 28), 2_000L, failing))
            assertEquals(1, calls)
        }
}
