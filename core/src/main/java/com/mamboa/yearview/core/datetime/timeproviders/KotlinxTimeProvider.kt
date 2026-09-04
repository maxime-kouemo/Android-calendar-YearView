package com.mamboa.yearview.core.datetime.timeproviders

import com.mamboa.yearview.core.datetime.CalendarDate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * [ICalendarDateTimeProvider] implementation backed by **kotlinx-datetime**.
 *
 * kotlinx-datetime does not include a formatting/parsing API, so this
 * provider bridges to `java.time.format.DateTimeFormatter` on the JVM
 * for [format], [parse], [shortDayOfWeekName], and [monthDisplayName].
 *
 * All millis conversions use **UTC noon** to avoid timezone edge-cases,
 * matching the convention of the other providers.
 */
class KotlinxTimeProvider : ICalendarDateTimeProvider {

    private val tz: TimeZone = TimeZone.currentSystemDefault()

    // ── Today / Current ──────────────────────────────────────────────

    override fun today(): CalendarDate {
        val now = Clock.System.now().toLocalDateTime(tz).date
        return CalendarDate(now.year, now.monthNumber, now.dayOfMonth)
    }

    override fun currentYear(): Int =
        Clock.System.now().toLocalDateTime(tz).year

    // ── Date construction ────────────────────────────────────────────

    override fun dateOf(year: Int, month: Int, day: Int): CalendarDate =
        CalendarDate(year, month, day)

    // ── Month queries ────────────────────────────────────────────────

    override fun daysInMonth(year: Int, month: Int): Int {
        // Go to first day of next month and subtract one day
        val firstOfMonth = LocalDate(year, month, 1)
        val lastDay = if (month == 12) {
            LocalDate(year + 1, 1, 1)
        } else {
            LocalDate(year, month + 1, 1)
        }
        // Difference in days gives us the length of the month
        return (lastDay.toEpochDays() - firstOfMonth.toEpochDays())
    }

    override fun firstDayOfWeekInMonth(year: Int, month: Int): Int =
        LocalDate(year, month, 1).dayOfWeek.isoDayNumber // 1=Mon..7=Sun

    // ── Day-of-week ──────────────────────────────────────────────────

    override fun dayOfWeek(date: CalendarDate): Int =
        LocalDate(date.year, date.month, date.day).dayOfWeek.isoDayNumber

    override fun dayOfWeek(year: Int, month: Int, day: Int): Int =
        LocalDate(year, month, day).dayOfWeek.isoDayNumber

    // ── Today / weekend checks ───────────────────────────────────────

    override fun isToday(date: CalendarDate): Boolean {
        val now = Clock.System.now().toLocalDateTime(tz).date
        return date.year == now.year &&
                date.month == now.monthNumber &&
                date.day == now.dayOfMonth
    }

    override fun isToday(year: Int, month: Int, day: Int): Boolean {
        val now = Clock.System.now().toLocalDateTime(tz).date
        return year == now.year &&
                month == now.monthNumber &&
                day == now.dayOfMonth
    }

    override fun isWeekendDay(date: CalendarDate, weekendDays: Set<Int>): Boolean =
        weekendDays.contains(dayOfWeek(date))

    // ── Formatting (bridges to java.time) ────────────────────────────

    override fun format(date: CalendarDate, pattern: String, locale: Locale): String {
        val javaDate = java.time.LocalDate.of(date.year, date.month, date.day)
        return DateTimeFormatter.ofPattern(pattern, locale).format(javaDate)
    }

    override fun shortDayOfWeekName(dayOfWeek: Int, locale: Locale): String =
        java.time.DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, locale)

    override fun monthDisplayName(month: Int, locale: Locale): String =
        java.time.Month.of(month).getDisplayName(TextStyle.FULL, locale)

    override fun formatMonth(year: Int, month: Int, pattern: String, locale: Locale): String {
        val javaDate = java.time.LocalDate.of(year, month, 1)
        return DateTimeFormatter.ofPattern(pattern, locale).format(javaDate)
    }

    // ── Parsing (bridges to java.time) ───────────────────────────────

    override fun parse(dateString: String, pattern: String, locale: Locale): CalendarDate {
        val formatter = DateTimeFormatter.ofPattern(pattern, locale)
        val accessor = formatter.parse(dateString)

        val y = if (accessor.isSupported(java.time.temporal.ChronoField.YEAR))
            accessor.get(java.time.temporal.ChronoField.YEAR) else 0
        val m = if (accessor.isSupported(java.time.temporal.ChronoField.MONTH_OF_YEAR))
            accessor.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) else 1
        val d = if (accessor.isSupported(java.time.temporal.ChronoField.DAY_OF_MONTH))
            accessor.get(java.time.temporal.ChronoField.DAY_OF_MONTH) else 1

        return CalendarDate(y, m, d)
    }

    // ── Millis conversion ────────────────────────────────────────────

    override fun toMillis(date: CalendarDate): Long {
        // Use noon UTC to avoid timezone edge-cases (via java.time bridge)
        val javaLdt = java.time.LocalDateTime.of(date.year, date.month, date.day, 12, 0)
        return javaLdt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    }

    override fun fromMillis(millis: Long): CalendarDate {
        val ldt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
        return CalendarDate(ldt.year, ldt.monthNumber, ldt.dayOfMonth)
    }
}
