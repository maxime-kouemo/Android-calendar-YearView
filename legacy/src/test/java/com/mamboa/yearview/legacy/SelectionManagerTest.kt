package com.mamboa.yearview.legacy

import com.mamboa.yearview.core.datetime.CalendarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural contract for [SelectionManager]'s three selection modes:
 * transient (no sticky), sticky single-day, and range/multi-selection.
 */
class SelectionManagerTest {

    private val provider = FakeCalendarDateTimeProvider()
    private val manager = SelectionManager()

    private fun date(month: Int, day: Int) = CalendarDate(YEAR, month, day)

    /** `month` is 0-based in the query API, mirroring the renderer's loop index. */
    private fun monthIndex(month: Int) = month - 1

    // ── Transient mode (default) ─────────────────────────────────────

    @Test
    fun `transient mode reports clicks without retaining selection`() {
        val result = manager.handleDayClicked(date(3, 10), provider, isLongPress = false)

        assertTrue(result is SelectionManager.ClickResult.DayClicked)
        assertFalse((result as SelectionManager.ClickResult.DayClicked).isLongPress)
        // In transient mode nothing is retained, so the result is the only place the
        // clicked date survives — it must carry it.
        assertEquals(date(3, 10), result.date)
        assertNull("transient mode must not retain a selection", manager.selectedDay)
    }

    @Test
    fun `long press is propagated in the click result`() {
        val result = manager.handleDayClicked(date(3, 10), provider, isLongPress = true)
        assertTrue((result as SelectionManager.ClickResult.DayClicked).isLongPress)
    }

    @Test
    fun `a null date is a no-op`() {
        val result = manager.handleDayClicked(null, provider, isLongPress = false)
        assertEquals(SelectionManager.ClickResult.None, result)
    }

    // ── Sticky single-day mode ───────────────────────────────────────

    @Test
    fun `sticky mode retains the clicked day`() {
        manager.setSticky(true)
        manager.handleDayClicked(date(3, 10), provider, isLongPress = false)

        assertEquals(date(3, 10), manager.selectedDay)
        assertTrue(manager.isSelectedDay(YEAR, monthIndex(3), 10))
    }

    @Test
    fun `clicking the selected day again deselects it`() {
        manager.setSticky(true)
        manager.handleDayClicked(date(3, 10), provider, isLongPress = false)

        val result = manager.handleDayClicked(date(3, 10), provider, isLongPress = false)

        assertTrue(result is SelectionManager.ClickResult.DayDeselected)
        assertEquals(
            date(3, 10),
            (result as SelectionManager.ClickResult.DayDeselected).date
        )
        assertNull(manager.selectedDay)
    }

    @Test
    fun `turning sticky off clears the retained day`() {
        manager.setSticky(true)
        manager.handleDayClicked(date(3, 10), provider, isLongPress = false)

        manager.setSticky(false)

        assertNull(manager.selectedDay)
    }

    @Test
    fun `isSelectedDay discriminates on year month and day`() {
        manager.setSticky(true)
        manager.handleDayClicked(date(3, 10), provider, isLongPress = false)

        assertFalse(manager.isSelectedDay(YEAR + 1, monthIndex(3), 10))
        assertFalse(manager.isSelectedDay(YEAR, monthIndex(4), 10))
        assertFalse(manager.isSelectedDay(YEAR, monthIndex(3), 11))
    }

    // ── Tap-based range selection ────────────────────────────────────

    @Test
    fun `first tap starts a range and second completes it`() {
        manager.setMultiSelectionEnabled(true)

        assertEquals(
            SelectionManager.ClickResult.RangeStarted,
            manager.handleDayClicked(date(3, 5), provider, isLongPress = false)
        )

        val completed = manager.handleDayClicked(date(3, 9), provider, isLongPress = false)

        assertTrue(completed is SelectionManager.ClickResult.RangeSelected)
        assertEquals(date(3, 5), manager.rangeStart)
        assertEquals(date(3, 9), manager.rangeEnd)
    }

    @Test
    fun `a reversed tap range is normalised`() {
        manager.setMultiSelectionEnabled(true)
        manager.handleDayClicked(date(3, 20), provider, isLongPress = false)

        val completed = manager.handleDayClicked(date(3, 4), provider, isLongPress = false)
                as SelectionManager.ClickResult.RangeSelected

        assertEquals(date(3, 4), manager.rangeStart)
        assertEquals(date(3, 20), manager.rangeEnd)
        // The result must be normalised too, not just the stored range: listeners read
        // the endpoints straight off the callback.
        assertEquals(date(3, 4), completed.start)
        assertEquals(date(3, 20), completed.end)
    }

    @Test
    fun `a third tap restarts the range`() {
        manager.setMultiSelectionEnabled(true)
        manager.handleDayClicked(date(3, 5), provider, isLongPress = false)
        manager.handleDayClicked(date(3, 9), provider, isLongPress = false)

        val restarted = manager.handleDayClicked(date(4, 1), provider, isLongPress = false)

        assertEquals(SelectionManager.ClickResult.RangeStarted, restarted)
        assertEquals(date(4, 1), manager.rangeStart)
        assertNull(manager.rangeEnd)
    }

    @Test
    fun `disabling multi-selection clears the range`() {
        manager.setMultiSelectionEnabled(true)
        manager.handleDayClicked(date(3, 5), provider, isLongPress = false)
        manager.handleDayClicked(date(3, 9), provider, isLongPress = false)

        manager.setMultiSelectionEnabled(false)

        assertNull(manager.rangeStart)
        assertNull(manager.rangeEnd)
    }

    // ── Range membership ─────────────────────────────────────────────

    @Test
    fun `isDayInRange is inclusive at both ends and excludes neighbours`() {
        manager.setMultiSelectionEnabled(true)
        manager.handleDayClicked(date(3, 5), provider, isLongPress = false)
        manager.handleDayClicked(date(3, 9), provider, isLongPress = false)

        assertTrue(manager.isDayInRange(YEAR, monthIndex(3), 5))
        assertTrue(manager.isDayInRange(YEAR, monthIndex(3), 7))
        assertTrue(manager.isDayInRange(YEAR, monthIndex(3), 9))
        assertFalse(manager.isDayInRange(YEAR, monthIndex(3), 4))
        assertFalse(manager.isDayInRange(YEAR, monthIndex(3), 10))
    }

    @Test
    fun `isDayInRange spans month boundaries`() {
        manager.setMultiSelectionEnabled(true)
        manager.handleDayClicked(date(3, 28), provider, isLongPress = false)
        manager.handleDayClicked(date(5, 2), provider, isLongPress = false)

        assertTrue(manager.isDayInRange(YEAR, monthIndex(4), 15))
        assertTrue(manager.isDayInRange(YEAR, monthIndex(3), 31))
        assertFalse(manager.isDayInRange(YEAR, monthIndex(3), 27))
        assertFalse(manager.isDayInRange(YEAR, monthIndex(5), 3))
    }

    @Test
    fun `no range means no day is in range`() {
        assertFalse(manager.isDayInRange(YEAR, monthIndex(3), 5))
        assertFalse(manager.isDayInDragRange(YEAR, monthIndex(3), 5))
    }

    // ── Drag-based range selection ───────────────────────────────────

    @Test
    fun `drag preview covers the swept days in either direction`() {
        manager.dragRangeStart = date(3, 10)
        manager.dragRangeEnd = date(3, 14)

        assertTrue(manager.isDayInDragRange(YEAR, monthIndex(3), 10))
        assertTrue(manager.isDayInDragRange(YEAR, monthIndex(3), 12))
        assertTrue(manager.isDayInDragRange(YEAR, monthIndex(3), 14))
        assertFalse(manager.isDayInDragRange(YEAR, monthIndex(3), 15))

        // Sweeping backwards must preview the same span.
        manager.dragRangeStart = date(3, 14)
        manager.dragRangeEnd = date(3, 10)
        assertTrue(manager.isDayInDragRange(YEAR, monthIndex(3), 12))
    }

    @Test
    fun `committing a drag normalises order and clears the transient state`() {
        manager.setMultiSelectionEnabled(true)
        manager.dragRangeStart = date(7, 20)
        manager.dragRangeEnd = date(7, 3)

        val result = manager.commitDragRange(provider)

        assertTrue(result is SelectionManager.ClickResult.RangeSelected)
        result as SelectionManager.ClickResult.RangeSelected
        assertEquals(date(7, 3), result.start)
        assertEquals(date(7, 20), result.end)
        assertEquals(date(7, 3), manager.rangeStart)
        assertEquals(date(7, 20), manager.rangeEnd)
        assertNull(manager.dragRangeStart)
        assertNull(manager.dragRangeEnd)
        assertFalse(manager.isDragging)
    }

    @Test
    fun `a single-cell drag does not form a range`() {
        manager.setMultiSelectionEnabled(true)
        manager.dragRangeStart = date(7, 20)
        manager.dragRangeEnd = date(7, 20)

        assertNull(manager.commitDragRange(provider))
        assertNull(manager.rangeStart)
    }

    @Test
    fun `cancelling a drag leaves the committed range untouched`() {
        manager.setMultiSelectionEnabled(true)
        manager.handleDayClicked(date(3, 5), provider, isLongPress = false)
        manager.handleDayClicked(date(3, 9), provider, isLongPress = false)

        manager.dragRangeStart = date(8, 1)
        manager.dragRangeEnd = date(8, 5)
        manager.cancelDrag()

        assertNull(manager.dragRangeStart)
        assertEquals(date(3, 5), manager.rangeStart)
        assertEquals(date(3, 9), manager.rangeEnd)
    }

    // ── State restoration ────────────────────────────────────────────

    @Test
    fun `restoreRange bypasses the multi-selection gate`() {
        manager.restoreRange(date(2, 1), date(2, 28))

        assertEquals(date(2, 1), manager.rangeStart)
        assertEquals(date(2, 28), manager.rangeEnd)
        assertTrue(manager.isDayInRange(YEAR, monthIndex(2), 14))
    }

    @Test
    fun `restoreRange with nulls clears the range`() {
        manager.restoreRange(date(2, 1), date(2, 28))
        manager.restoreRange(null, null)

        assertNull(manager.rangeStart)
        assertNull(manager.rangeEnd)
    }

    private companion object {
        const val YEAR = 2026
    }
}
