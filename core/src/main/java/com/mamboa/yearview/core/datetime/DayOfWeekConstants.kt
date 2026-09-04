package com.mamboa.yearview.core.datetime

/**
 * Library-agnostic day-of-week constants following the ISO-8601 convention
 * (Monday = 1 … Sunday = 7).
 *
 * These mirror the values used by Joda-Time's `DateTimeConstants` and
 * java.time's `DayOfWeek`, so existing code that stores weekend-day sets
 * or first-day-of-week preferences continues to work unchanged.
 */
object DayOfWeekConstants {
    /** Monday – ISO value 1. */
    const val MONDAY = 1
    /** Tuesday – ISO value 2. */
    const val TUESDAY = 2
    /** Wednesday – ISO value 3. */
    const val WEDNESDAY = 3
    /** Thursday – ISO value 4. */
    const val THURSDAY = 4
    /** Friday – ISO value 5. */
    const val FRIDAY = 5
    /** Saturday – ISO value 6. */
    const val SATURDAY = 6
    /** Sunday – ISO value 7. */
    const val SUNDAY = 7
}
