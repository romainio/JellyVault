package tv.jellyvault.app.ui.discover

import org.junit.Assert.assertEquals
import org.junit.Test

class LazyRowLoaderTest {
    @Test
    fun first_rows_load_at_once() {
        assertEquals(listOf(0, 1, 2, 3, 4), LazyRowLoader().next(lastVisible = 0, total = 20))
    }

    @Test
    fun rows_load_three_ahead_of_the_last_visible_row() {
        val loader = LazyRowLoader()
        loader.next(0, 20)
        assertEquals(listOf(5, 6), loader.next(lastVisible = 3, total = 20))
        assertEquals(emptyList<Int>(), loader.next(lastVisible = 3, total = 20))
    }

    @Test
    fun jumping_far_loads_everything_up_to_the_visible_row_plus_ahead() {
        val loader = LazyRowLoader()
        loader.next(0, 20)
        assertEquals((5..13).toList(), loader.next(lastVisible = 10, total = 20))
        assertEquals((14..19).toList(), loader.next(lastVisible = 30, total = 20))
    }

    @Test
    fun a_row_is_never_requested_twice() {
        val loader = LazyRowLoader()
        loader.next(4, 8)
        assertEquals(emptyList<Int>(), loader.next(4, 8))
        loader.forget(2)
        assertEquals(listOf(2), loader.next(0, 8))
        loader.reset()
        assertEquals(listOf(0, 1, 2, 3, 4), loader.next(0, 8))
    }
}
