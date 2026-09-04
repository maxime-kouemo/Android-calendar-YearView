package com.mamboa.yearview.compose

import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider

/**
 * Holds pre-parsed range bounds for O(1) per-day membership checks.
 *
 * Instead of materializing a [HashSet] of every day in the range (which
 * allocates on every drag-move event), this stores the normalised YMD
 * bounds and performs a simple integer comparison in [contains].
 *
 * @property lo Lower YMD bound (inclusive), encoded as `year * 10000 + month * 100 + day`.
 * @property hi Upper YMD bound (inclusive), encoded the same way.
 *              When `lo == hi` a single day is selected.
 *              When both are `-1` no range is active.
 */
internal class RangeBounds private constructor(
    private val lo: Int,
    private val hi: Int,
) {
    /** Returns `true` if the given 0-based [month] and 1-based [day] fall within the range. */
    fun contains(year: Int, month: Int, day: Int): Boolean {
        if (lo < 0) return false
        val ymd = year * 10000 + (month + 1) * MONTH_DAY_ENCODING_FACTOR + day
        return ymd in lo..hi
    }

    companion object {
        /** Sentinel instance representing "no range". */
        val EMPTY = RangeBounds(lo = -1, hi = -1)

        /**
         * Computes the active range bounds from the current range / drag state.
         * Drag-based range takes priority over tap-based range.
         */
        fun compute(
            rangeStart: String?,
            rangeEnd: String?,
            dragRangeStart: String?,
            dragRangeEnd: String?,
            year: Int,
            dayFormat: String,
            currentLocale: java.util.Locale,
            dateTimeProvider: ICalendarDateTimeProvider,
        ): RangeBounds {
            // Determine which range source to use: drag takes priority while active
            val effectiveStart: String?
            val effectiveEnd: String?
            if (dragRangeStart != null && dragRangeEnd != null) {
                effectiveStart = dragRangeStart
                effectiveEnd = dragRangeEnd
            } else {
                effectiveStart = rangeStart
                effectiveEnd = rangeEnd
            }
            val start = effectiveStart?.let {
                try {
                    dateTimeProvider.parse(it, dayFormat, currentLocale)
                } catch (_: Exception) {
                    null
                }
            }
            val end = effectiveEnd?.let {
                try {
                    dateTimeProvider.parse(it, dayFormat, currentLocale)
                } catch (_: Exception) {
                    null
                }
            }
            return when {
                start != null && end != null -> {
                    val startYmd =
                        start.year * 10000 + start.month * MONTH_DAY_ENCODING_FACTOR + start.day
                    val endYmd =
                        end.year * 10000 + end.month * MONTH_DAY_ENCODING_FACTOR + end.day
                    RangeBounds(lo = minOf(startYmd, endYmd), hi = maxOf(startYmd, endYmd))
                }

                start != null -> {
                    val ymd =
                        start.year * 10000 + start.month * MONTH_DAY_ENCODING_FACTOR + start.day
                    RangeBounds(lo = ymd, hi = ymd)
                }

                else -> EMPTY
            }
        }
    }
}
