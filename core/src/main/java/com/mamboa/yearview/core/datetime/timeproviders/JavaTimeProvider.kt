package com.mamboa.yearview.core.datetime.timeproviders

import com.mamboa.yearview.core.datetime.CalendarDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * [ICalendarDateTimeProvider] implementation backed by **java.time** (the
 * Java 8+ Date and Time API).
 *
 * Requires Android API 26+ or core library desugaring.
 *
 * All millis conversions use **UTC noon** to avoid timezone edge-cases
 * (matching the convention of other providers).
 */
class JavaTimeProvider : ICalendarDateTimeProvider {

    // ── Today / Current ──────────────────────────────────────────────

    override fun today(): CalendarDate {
        val now = LocalDate.now()
        return CalendarDate(now.year, now.monthValue, now.dayOfMonth)
    }

    override fun currentYear(): Int = LocalDate.now().year

    // ── Date construction ────────────────────────────────────────────

    override fun dateOf(year: Int, month: Int, day: Int): CalendarDate =
        CalendarDate(year, month, day)

    // ── Month queries ────────────────────────────────────────────────

    override fun daysInMonth(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).lengthOfMonth()

    override fun firstDayOfWeekInMonth(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1).dayOfWeek.value // ISO: 1=Mon..7=Sun

    // ── Day-of-week ──────────────────────────────────────────────────

    override fun dayOfWeek(date: CalendarDate): Int =
        LocalDate.of(date.year, date.month, date.day).dayOfWeek.value

    override fun dayOfWeek(year: Int, month: Int, day: Int): Int =
        LocalDate.of(year, month, day).dayOfWeek.value

    // ── Today / weekend checks ───────────────────────────────────────

    override fun isToday(date: CalendarDate): Boolean {
        val today = LocalDate.now()
        return date.year == today.year &&
                date.month == today.monthValue &&
                date.day == today.dayOfMonth
    }

    override fun isToday(year: Int, month: Int, day: Int): Boolean {
        val today = LocalDate.now()
        return year == today.year &&
                month == today.monthValue &&
                day == today.dayOfMonth
    }

    override fun isWeekendDay(date: CalendarDate, weekendDays: Set<Int>): Boolean =
        weekendDays.contains(dayOfWeek(date))

    // ── Formatting ───────────────────────────────────────────────────

    override fun format(date: CalendarDate, pattern: String, locale: Locale): String {
        val ld = LocalDate.of(date.year, date.month, date.day)
        return DateTimeFormatter.ofPattern(pattern, locale).format(ld)
    }

    override fun shortDayOfWeekName(dayOfWeek: Int, locale: Locale): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, locale)

    override fun monthDisplayName(month: Int, locale: Locale): String {
        val ld = LocalDate.of(2000, month, 1)
        return ld.month.getDisplayName(TextStyle.FULL, locale)
    }

    override fun formatMonth(year: Int, month: Int, pattern: String, locale: Locale): String {
        val ld = LocalDate.of(year, month, 1)
        return DateTimeFormatter.ofPattern(pattern, locale).format(ld)
    }

    // ── Parsing ──────────────────────────────────────────────────────

    override fun parse(dateString: String, pattern: String, locale: Locale): CalendarDate {
        val formatter = DateTimeFormatter.ofPattern(pattern, locale)
        val accessor = formatter.parse(dateString)

        // Extract fields, defaulting to sensible values when the pattern
        // doesn't contain them (e.g. pattern = "MMMM" has no day/year).
        val year = if (accessor.isSupported(ChronoField.YEAR)) accessor.get(ChronoField.YEAR) else 0
        val month = if (accessor.isSupported(ChronoField.MONTH_OF_YEAR)) accessor.get(ChronoField.MONTH_OF_YEAR) else 1
        val day = if (accessor.isSupported(ChronoField.DAY_OF_MONTH)) accessor.get(ChronoField.DAY_OF_MONTH) else 1

        return CalendarDate(year, month, day)
    }

    // ── Millis conversion ──────��─────────────────────────────────────

    override fun toMillis(date: CalendarDate): Long {
        // Use noon UTC to avoid timezone edge-cases
        val ldt = LocalDateTime.of(date.year, date.month, date.day, 12, 0)
        return ldt.toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    override fun fromMillis(millis: Long): CalendarDate {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        return CalendarDate(ldt.year, ldt.monthValue, ldt.dayOfMonth)
    }
}
