package com.mamboa.yearview.core.datetime.timeproviders

import com.mamboa.yearview.core.datetime.CalendarDate
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import java.util.Locale

/**
 * Default [ICalendarDateTimeProvider] implementation backed by **Joda-Time**.
 *
 * This is the out-of-the-box provider shipped with the library.
 * To switch to a different date/time back-end (e.g. `java.time` or
 * `kotlinx-datetime`), create a new class that implements
 * [ICalendarDateTimeProvider] and pass it to the YearView instead.
 */
class JodaTimeProvider : ICalendarDateTimeProvider {

    // ── Today / Current ──────────────────────────────────────────────

    override fun today(): CalendarDate {
        val ld = LocalDate()
        return CalendarDate(ld.year, ld.monthOfYear, ld.dayOfMonth)
    }

    override fun currentYear(): Int = DateTime().year().get()

    // ── Date construction ────────────────────────────────────────────

    override fun dateOf(year: Int, month: Int, day: Int): CalendarDate =
        CalendarDate(year, month, day)

    // ── Month queries ────────────────────────────────────────────────

    override fun daysInMonth(year: Int, month: Int): Int =
        DateTime(year, month, 1, 12, 0).dayOfMonth().maximumValue

    override fun firstDayOfWeekInMonth(year: Int, month: Int): Int =
        DateTime(year, month, 1, 12, 0).dayOfWeek().get()

    // ── Day-of-week ──────────────────────────────────────────────────

    override fun dayOfWeek(date: CalendarDate): Int =
        DateTime(date.year, date.month, date.day, 12, 0).dayOfWeek().get()

    override fun dayOfWeek(year: Int, month: Int, day: Int): Int =
        DateTime(year, month, day, 12, 0).dayOfWeek().get()

    // ── Today / weekend checks ───────────────────────────────────────

    override fun isToday(date: CalendarDate): Boolean {
        val today = LocalDate()
        return date.year == today.year &&
                date.month == today.monthOfYear &&
                date.day == today.dayOfMonth
    }

    override fun isToday(year: Int, month: Int, day: Int): Boolean {
        val today = LocalDate()
        return year == today.year &&
                month == today.monthOfYear &&
                day == today.dayOfMonth
    }

    override fun isWeekendDay(date: CalendarDate, weekendDays: Set<Int>): Boolean =
        weekendDays.contains(dayOfWeek(date))

    // ── Formatting ───────────────────────────────────────────────────

    override fun format(date: CalendarDate, pattern: String, locale: Locale): String {
        val dt = DateTime(date.year, date.month, date.day, 12, 0)
        return DateTimeFormat.forPattern(pattern).withLocale(locale).print(dt)
    }

    override fun shortDayOfWeekName(dayOfWeek: Int, locale: Locale): String {
        // Use a fixed reference date and set the day-of-week
        val dt = DateTime(0).withDayOfWeek(dayOfWeek)
        return dt.dayOfWeek().getAsShortText(locale)
    }

    override fun monthDisplayName(month: Int, locale: Locale): String {
        val dt = DateTime(2000, month, 1, 12, 0)
        return dt.monthOfYear().getAsText(locale)
    }

    override fun formatMonth(year: Int, month: Int, pattern: String, locale: Locale): String {
        val dt = DateTime(year, month, 1, 12, 0)
        return DateTimeFormat.forPattern(pattern).withLocale(locale).print(dt)
    }

    // ── Parsing ──────────────────────────────────────────────────────

    override fun parse(dateString: String, pattern: String, locale: Locale): CalendarDate {
        val dt = DateTimeFormat.forPattern(pattern).withLocale(locale).parseDateTime(dateString)
        return CalendarDate(dt.year, dt.monthOfYear, dt.dayOfMonth)
    }

    // ── Millis conversion ────────────────────────────────────────────

    override fun toMillis(date: CalendarDate): Long =
        DateTime(date.year, date.month, date.day, 12, 0).millis

    override fun fromMillis(millis: Long): CalendarDate {
        val dt = DateTime(millis)
        return CalendarDate(dt.year, dt.monthOfYear, dt.dayOfMonth)
    }
}
