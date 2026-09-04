package com.mamboa.yearview.legacy

import com.mamboa.yearview.legacy.MonthGridGeometry.DAYS_PER_WEEK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Calendar-math contract for [CalendarLayoutComputer].
 *
 * These map a screen column to an ISO day-of-week and are the basis for both
 * the header labels and weekend detection, so they are checked for every
 * possible `firstDayOfWeek`.
 */
class CalendarLayoutComputerTest {

    @Test
    fun `column zero always maps to the configured first day of week`() {
        for (firstDayOfWeek in 1..7) {
            assertEquals(
                "firstDayOfWeek=$firstDayOfWeek",
                firstDayOfWeek,
                CalendarLayoutComputer.getDayIndex(0, firstDayOfWeek)
            )
        }
    }

    @Test
    fun `each week is a permutation of the seven ISO days`() {
        for (firstDayOfWeek in 1..7) {
            val week = (0 until DAYS_PER_WEEK).map {
                CalendarLayoutComputer.getDayIndex(it, firstDayOfWeek)
            }
            assertEquals(
                "firstDayOfWeek=$firstDayOfWeek produced $week",
                (1..7).toSet(),
                week.toSet()
            )
        }
    }

    @Test
    fun `days advance cyclically by one`() {
        for (firstDayOfWeek in 1..7) {
            for (position in 1 until DAYS_PER_WEEK) {
                val previous = CalendarLayoutComputer.getDayIndex(position - 1, firstDayOfWeek)
                val current = CalendarLayoutComputer.getDayIndex(position, firstDayOfWeek)
                val expected = previous % 7 + 1
                assertEquals(
                    "firstDayOfWeek=$firstDayOfWeek position=$position",
                    expected,
                    current
                )
            }
        }
    }

    @Test
    fun `monday-first produces the canonical ISO ordering`() {
        val week = (0 until DAYS_PER_WEEK).map { CalendarLayoutComputer.getDayIndex(it, 1) }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), week)
    }

    @Test
    fun `sunday-first puts the weekend at the edges`() {
        val week = (0 until DAYS_PER_WEEK).map { CalendarLayoutComputer.getDayIndex(it, 7) }
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), week)
    }

    @Test
    fun `all returned indices are within the ISO range`() {
        for (firstDayOfWeek in 1..7) {
            for (position in 0 until DAYS_PER_WEEK) {
                val day = CalendarLayoutComputer.getDayIndex(position, firstDayOfWeek)
                assertTrue("day $day out of ISO range", day in 1..7)
            }
        }
    }
}
