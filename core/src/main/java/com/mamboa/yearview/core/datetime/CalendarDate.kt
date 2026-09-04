package com.mamboa.yearview.core.datetime

/**
 * A lightweight, library-agnostic representation of a calendar date.
 *
 * This class holds the minimal set of date fields (year, month, day) needed by
 * the calendar UI without coupling to any specific date/time library
 * (Joda-Time, java.time, kotlinx-datetime, …).
 *
 * Use [com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider] to create, query, and format [CalendarDate]
 * instances.
 *
 * @property year  The calendar year (e.g. 2025).
 * @property month The month of the year, **1-based** (1 = January … 12 = December).
 * @property day   The day of the month (1 … 28/29/30/31).
 */
data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int
)
