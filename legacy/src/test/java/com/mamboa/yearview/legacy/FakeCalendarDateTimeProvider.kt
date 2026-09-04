package com.mamboa.yearview.legacy

import com.mamboa.yearview.core.datetime.CalendarDate
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Deterministic [ICalendarDateTimeProvider] for unit tests.
 *
 * Backed by `java.time` (available on the JVM test classpath) with a fixed
 * [today] so assertions never depend on the wall clock.
 */
internal class FakeCalendarDateTimeProvider(
    private val today: CalendarDate = CalendarDate(2026, 6, 15)
) : ICalendarDateTimeProvider {

    private fun CalendarDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)

    private fun LocalDate.toCalendarDate(): CalendarDate =
        CalendarDate(year, monthValue, dayOfMonth)

    override fun today(): CalendarDate = today

    override fun currentYear(): Int = today.year

    override fun dateOf(year: Int, month: Int, day: Int): CalendarDate =
        CalendarDate(year, month, day)

    override fun daysInMonth(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).lengthOfMonth()

    override fun firstDayOfWeekInMonth(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).dayOfWeek.value

    override fun dayOfWeek(date: CalendarDate): Int = date.toLocalDate().dayOfWeek.value

    override fun dayOfWeek(year: Int, month: Int, day: Int): Int =
        LocalDate.of(year, month, day).dayOfWeek.value

    override fun isToday(date: CalendarDate): Boolean = date == today

    override fun isToday(year: Int, month: Int, day: Int): Boolean =
        year == today.year && month == today.month && day == today.day

    override fun isWeekendDay(date: CalendarDate, weekendDays: Set<Int>): Boolean =
        weekendDays.contains(dayOfWeek(date))

    override fun format(date: CalendarDate, pattern: String, locale: Locale): String =
        date.toLocalDate().format(DateTimeFormatter.ofPattern(pattern, locale))

    override fun shortDayOfWeekName(dayOfWeek: Int, locale: Locale): String =
        java.time.DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, locale)

    override fun monthDisplayName(month: Int, locale: Locale): String =
        java.time.Month.of(month).getDisplayName(TextStyle.FULL, locale)

    override fun formatMonth(year: Int, month: Int, pattern: String, locale: Locale): String =
        LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern(pattern, locale))

    override fun parse(dateString: String, pattern: String, locale: Locale): CalendarDate =
        LocalDate.parse(dateString, DateTimeFormatter.ofPattern(pattern, locale)).toCalendarDate()

    /** Epoch millis at UTC midnight, matching the contract's timezone-safe intent. */
    override fun toMillis(date: CalendarDate): Long =
        date.toLocalDate().toEpochDay() * MILLIS_PER_DAY

    override fun fromMillis(millis: Long): CalendarDate =
        LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY)).toCalendarDate()

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
