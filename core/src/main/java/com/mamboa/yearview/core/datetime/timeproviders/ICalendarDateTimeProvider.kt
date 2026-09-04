package com.mamboa.yearview.core.datetime.timeproviders

import com.mamboa.yearview.core.datetime.CalendarDate
import java.util.Locale

/**
 * Abstraction over date/time operations required by the YearView calendar.
 *
 * Implementations can delegate to any date/time library:
 * - **Joda-Time** (`JodaDateTimeProvider`)
 * - **java.time** (Android 8+ / desugaring)
 * - **kotlinx-datetime**
 *
 * All day-of-week values follow the **ISO-8601** convention used by
 * [com.mamboa.yearview.core.datetime.DayOfWeekConstants] (Monday = 1 … Sunday = 7).
 *
 * ## Quick-start
 * ```kotlin
 * // Use the default (Joda) implementation shipped with the library:
 * val provider: CalendarDateTimeProvider = JodaDateTimeProvider()
 *
 * // Or supply your own java.time implementation:
 * val provider: CalendarDateTimeProvider = JavaTimeDateTimeProvider()
 * ```
 */
interface ICalendarDateTimeProvider {

    // ── Today / Current ──────────────────────────────────────────────

    /**
     * Returns today's date.
     */
    fun today(): CalendarDate

    /**
     * Returns the current year.
     */
    fun currentYear(): Int

    // ── Date construction ────────────────────────────────────────────

    /**
     * Creates a [CalendarDate] from the given components.
     *
     * @param year  Calendar year.
     * @param month Month (1-based: 1 = January … 12 = December).
     * @param day   Day of the month.
     */
    fun dateOf(year: Int, month: Int, day: Int): CalendarDate

    // ── Month queries ────────────────────────────────────────────────

    /**
     * Returns the number of days in the given [month] of the given [year].
     *
     * @param year  Calendar year.
     * @param month Month (1-based).
     */
    fun daysInMonth(year: Int, month: Int): Int

    /**
     * Returns the ISO day-of-week (1 = Monday … 7 = Sunday) of the first day
     * of the given month.
     *
     * @param year  Calendar year.
     * @param month Month (1-based).
     */
    fun firstDayOfWeekInMonth(year: Int, month: Int): Int

    // ── Day-of-week ──────────────────────────────────────────────────

    /**
     * Returns the ISO day-of-week (1 = Monday … 7 = Sunday) for the given date.
     */
    fun dayOfWeek(date: CalendarDate): Int

    /**
     * Returns the ISO day-of-week (1 = Monday … 7 = Sunday) for the given
     * year, month, and day.
     */
    fun dayOfWeek(year: Int, month: Int, day: Int): Int

    // ── Today / weekend checks ───────────────────────────────────────

    /**
     * Returns `true` if the given date represents today.
     */
    fun isToday(date: CalendarDate): Boolean

    /**
     * Returns `true` if the given [year], [month] (1-based), and [day]
     * represent today's date.
     */
    fun isToday(year: Int, month: Int, day: Int): Boolean

    /**
     * Returns `true` if the day-of-week of the given date is contained
     * in [weekendDays].
     *
     * @param date        The date to check.
     * @param weekendDays A set of ISO day-of-week values considered as weekend
     *                    (e.g. `setOf(SATURDAY, SUNDAY)`).
     */
    fun isWeekendDay(date: CalendarDate, weekendDays: Set<Int>): Boolean

    /**
     * Returns `true` if the given day-of-week index (ISO 1–7) is present
     * in [weekendDays].
     */
    fun isWeekendDay(dayOfWeek: Int, weekendDays: Set<Int>): Boolean {
        return weekendDays.contains(dayOfWeek)
    }

    // ── Formatting ───────────────────────────────────────────────────

    /**
     * Formats a [CalendarDate] using the given [pattern] and [locale].
     *
     * @param date    The date to format.
     * @param pattern A date-format pattern (e.g. `"yyyy-MM-dd"`, `"MMMM"`).
     * @param locale  The locale to use for month/day names.
     * @return The formatted date string.
     */
    fun format(date: CalendarDate, pattern: String, locale: Locale): String

    /**
     * Returns the short name of the day-of-week (e.g. "Mon", "Tue")
     * for the given ISO [dayOfWeek] value.
     *
     * @param dayOfWeek ISO day-of-week (1 = Monday … 7 = Sunday).
     * @param locale    The locale for localised names.
     */
    fun shortDayOfWeekName(dayOfWeek: Int, locale: Locale): String

    /**
     * Returns the full display name of the given [month] in the given [locale].
     *
     * @param month  Month (1-based: 1 = January … 12 = December).
     * @param locale The locale for localised names.
     */
    fun monthDisplayName(month: Int, locale: Locale): String

    /**
     * Formats a month using the given [pattern] and [locale].
     *
     * @param year    Calendar year.
     * @param month   Month (1-based).
     * @param pattern A date-format pattern (e.g. `"MMMM"`, `"MMM yyyy"`).
     * @param locale  The locale.
     */
    fun formatMonth(year: Int, month: Int, pattern: String, locale: Locale): String

    /**
     * Formats a [CalendarDate] as a full, locale-appropriate date string
     * suitable for accessibility / screen-reader announcements.
     *
     * The default implementation uses [java.text.DateFormat.getDateInstance]
     * with [java.text.DateFormat.FULL] so that date component ordering
     * matches the locale convention (e.g. "Sunday, August 30, 2026" for
     * `en_US`, "dimanche 30 août 2026" for `fr_FR`).
     *
     * Override this in custom providers if a different library-specific
     * approach is preferred.
     */
    fun formatAccessibleDate(date: CalendarDate, locale: Locale): String {
        val calendar = java.util.Calendar.getInstance(locale).apply {
            set(date.year, date.month - 1, date.day)
        }
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, locale)
            .format(calendar.time)
    }

    // ── Parsing ────────────���─────────────────────────────────────────

    /**
     * Parses a date string into a [CalendarDate] using the given [pattern]
     * and [locale].
     *
     * @param dateString The string to parse (e.g. `"2025-06-30"`).
     * @param pattern    The expected pattern (e.g. `"yyyy-MM-dd"`).
     * @param locale     The locale to use for parsing.
     * @return The parsed [CalendarDate].
     * @throws IllegalArgumentException if parsing fails.
     */
    fun parse(dateString: String, pattern: String, locale: Locale): CalendarDate

    // ── Millis conversion ────────────────────────────────────────────

    /**
     * Converts a [CalendarDate] to epoch milliseconds (UTC noon is
     * recommended to avoid timezone edge-cases).
     */
    fun toMillis(date: CalendarDate): Long

    /**
     * Creates a [CalendarDate] from epoch milliseconds.
     */
    fun fromMillis(millis: Long): CalendarDate

    // ── Comparison ───────────────────────────────────────────────────

    /**
     * Returns `true` if [date1] is strictly before [date2].
     */
    fun isBefore(date1: CalendarDate, date2: CalendarDate): Boolean {
        return when {
            date1.year != date2.year -> date1.year < date2.year
            date1.month != date2.month -> date1.month < date2.month
            else -> date1.day < date2.day
        }
    }

    /**
     * Returns `true` if [date1] is strictly after [date2].
     */
    fun isAfter(date1: CalendarDate, date2: CalendarDate): Boolean {
        return isBefore(date2, date1)
    }

    /**
     * Returns `true` if [date] falls within the range from [start] to [end],
     * inclusive on both sides.
     */
    fun isInRange(date: CalendarDate, start: CalendarDate, end: CalendarDate): Boolean {
        return !isBefore(date, start) && !isAfter(date, end)
    }
}