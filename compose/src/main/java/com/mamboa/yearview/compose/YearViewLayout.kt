package com.mamboa.yearview.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider

/**
 * Holds the computed layout geometry for the calendar grid.
 * Stored as a single snapshot so that tap/long-press handlers always
 * see a consistent view of month rects, day rects, and last-row Y values.
 */
internal data class CalendarLayoutData(
    val monthRects: List<MonthRect> = emptyList(),
    val dayRects: List<DayRect> = emptyList(),
    val lastRowYValues: FloatArray = FloatArray(MONTHS_PER_YEAR)
) {
    /**
     * [dayRects] grouped by 0-based month index.
     *
     * Hit-testing runs on every pointer move, and a linear scan of ~365 rects per
     * event is wasteful when the month has already been resolved. Computed lazily so
     * layouts that are never touched (off-screen pages in a pager) pay nothing, and
     * derived from [dayRects], so it is deliberately excluded from equality.
     */
    val dayRectsByMonth: Map<Int, List<DayRect>> by lazy { dayRects.groupBy { it.monthIndex } }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalendarLayoutData) return false
        return monthRects == other.monthRects &&
                dayRects == other.dayRects &&
                lastRowYValues.contentEquals(other.lastRowYValues)
    }

    override fun hashCode(): Int {
        var result = monthRects.hashCode()
        result = 31 * result + dayRects.hashCode()
        result = 31 * result + lastRowYValues.contentHashCode()
        return result
    }
}

/** Hit-test result for a touch on the calendar grid. */
internal data class HitTestResult(
    val monthIndex: Int,
    /** Non-null when a specific day cell was hit. */
    val dayRect: DayRect? = null
)

/** Number of days in a week — used as grid column count. */
internal const val DAYS_PER_WEEK = 7
/** Number of months in a year. */
internal const val MONTHS_PER_YEAR = 12
/** Maximum days in any month — used for pre-allocating arrays. */
internal const val MAX_DAYS_PER_MONTH = 31
/**
 * Maximum number of vertical rows in the month grid: 1 header row + up to 6 day rows.
 * Used as the divisor for the vertical cell size so positions never overflow.
 */
internal const val MAX_GRID_ROWS = 7
/**
 * Encoding factor for compact (month, day) lookup keys.
 * A day is encoded as `month0 * MONTH_DAY_ENCODING_FACTOR + day` where
 * `month0` is the 0-based month index.
 */
internal const val MONTH_DAY_ENCODING_FACTOR = 100

/**
 * Per-year calendar facts that depend only on the year and the date-time provider:
 * how many days each month has and which day of the week each month starts on.
 *
 * Both values are needed by the layout pass *and* by every draw pass. Querying the
 * provider for them per frame means 24 date allocations per frame for data that only
 * changes when the year does, so they are computed once and shared.
 */
internal class YearMonthMetadata private constructor(
    /** Number of days in each month, indexed 0 (January) … 11 (December). */
    val daysInMonth: IntArray,
    /** ISO day of week (1 = Monday … 7 = Sunday) of the 1st of each month. */
    val firstDayOfWeekInMonth: IntArray,
) {
    /**
     * Column offset of the 1st of [monthIndex] within a week that starts on
     * [firstDayOfWeek], i.e. how many blank cells precede day 1.
     */
    fun firstDayOffset(monthIndex: Int, firstDayOfWeek: Int): Int =
        (firstDayOfWeekInMonth[monthIndex] - firstDayOfWeek + DAYS_PER_WEEK) % DAYS_PER_WEEK

    // Structural equality so that using this as a `remember` key does not invalidate
    // downstream caches merely because a new (but identical) instance was created.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is YearMonthMetadata) return false
        return daysInMonth.contentEquals(other.daysInMonth) &&
                firstDayOfWeekInMonth.contentEquals(other.firstDayOfWeekInMonth)
    }

    override fun hashCode(): Int =
        31 * daysInMonth.contentHashCode() + firstDayOfWeekInMonth.contentHashCode()

    companion object {
        fun compute(year: Int, dateTimeProvider: ICalendarDateTimeProvider): YearMonthMetadata {
            val days = IntArray(MONTHS_PER_YEAR)
            val firstDows = IntArray(MONTHS_PER_YEAR)
            for (i in 0 until MONTHS_PER_YEAR) {
                val month = i + 1
                days[i] = dateTimeProvider.daysInMonth(year, month)
                firstDows[i] = dateTimeProvider.firstDayOfWeekInMonth(year, month)
            }
            return YearMonthMetadata(days, firstDows)
        }
    }
}

/**
 * Walks the cells of one month's grid in a single, canonical order.
 *
 * Layout (hit-test rectangles, TalkBack focus rectangles) and drawing must agree on
 * where every cell sits down to the pixel. They used to compute cell centres with two
 * separate — and subtly different — nested loops; this function is the one place that
 * mapping is defined, so the two passes cannot drift apart again.
 *
 * Row 0 is the day-of-week header; rows 1..[MAX_GRID_ROWS]-1 hold day numbers.
 * [onDayCell] is only invoked for cells that carry a real day of the month.
 *
 * @param gridBounds The month's day-grid area, i.e. the month rect already offset
 *   below the month name.
 * @param firstDayOffset Number of empty cells before day 1 (see
 *   [YearMonthMetadata.firstDayOffset]).
 * @param daysInMonth Number of days in the month being iterated.
 * @param isRtl When `true`, the week runs right-to-left: the first day of the week sits
 *   in the rightmost column and day numbers advance leftwards. Only the *horizontal*
 *   mapping is mirrored — the callbacks still receive logical column indices and real
 *   day numbers, so callers need no RTL awareness of their own.
 */
internal inline fun forEachCalendarCell(
    gridBounds: Rect,
    firstDayOffset: Int,
    daysInMonth: Int,
    isRtl: Boolean,
    onHeaderCell: (column: Int, centerX: Float, centerY: Float) -> Unit,
    onDayCell: (dayOfMonth: Int, centerX: Float, centerY: Float) -> Unit,
) {
    val xUnit = gridBounds.width / DAYS_PER_WEEK
    val yUnit = gridBounds.height / MAX_GRID_ROWS

    for (row in 0 until MAX_GRID_ROWS) {
        val centerY = gridBounds.top + yUnit * row + yUnit / 2
        for (column in 0 until DAYS_PER_WEEK) {
            // `column` is the logical position within the week; in RTL it maps to a
            // physical slot counted from the right edge instead of the left.
            val physicalColumn = if (isRtl) DAYS_PER_WEEK - 1 - column else column
            val centerX = gridBounds.left + xUnit * physicalColumn + xUnit / 2
            if (row == 0) {
                onHeaderCell(column, centerX, centerY)
            } else {
                val dayOfMonth = (row - 1) * DAYS_PER_WEEK + column + 1 - firstDayOffset
                if (dayOfMonth in 1..daysInMonth) {
                    onDayCell(dayOfMonth, centerX, centerY)
                }
            }
        }
    }
}

/**
 * Computes the full calendar layout geometry: month rectangles, day touch-rectangles,
 * and last-row Y values for every month. Returns a single [CalendarLayoutData] snapshot.
 */
internal fun calculateCalendarLayout(
    canvasWidth: Float,
    canvasHeight: Float,
    columns: Int,
    rows: Int,
    hSpacingPx: Float,
    vSpacingPx: Float,
    monthSelectionMarginPx: Float,
    year: Int,
    firstDayOfWeek: Int,
    marginBelowMonthNamePx: Float,
    todayMonthIndex: Int,
    monthNameStyle: TextStyle,
    todayMonthNameStyle: TextStyle,
    simpleDayTextStyle: TextStyle,
    dayTextLayoutCache: Map<DayTextKey, TextLayoutResult>,
    monthNameLayoutCache: Map<MonthNameKey, TextLayoutResult>,
    /** Pre-computed per-month facts for [year]; see [YearMonthMetadata]. */
    monthMetadata: YearMonthMetadata,
    dayTouchPaddingPx: Float,
    /**
     * Mirrors both the month grid and the weekday columns for right-to-left locales.
     * Must match the value used by the draw pass, since these rectangles are what
     * taps and TalkBack focus resolve against.
     */
    isRtl: Boolean = false,
): CalendarLayoutData {
    if (canvasWidth <= 0f || canvasHeight <= 0f) return CalendarLayoutData()

    val monthRects = calculateMonthBlocks(
        canvasWidth, canvasHeight, columns, rows,
        hSpacingPx, vSpacingPx, monthSelectionMarginPx, isRtl
    )
    if (monthRects.isEmpty()) return CalendarLayoutData()

    val dayRects = mutableListOf<DayRect>()
    val lastRowYValues = FloatArray(MONTHS_PER_YEAR)

    // Use the pre-measured day text layouts directly from dayTextLayoutCache.
    // Missing entries can only happen if the caches and this computation get out of sync
    // (see the `remember` keys in YearView); degrade gracefully instead of crashing.
    val twoDigitWidth = dayTextLayoutCache[DayTextKey(10, simpleDayTextStyle)]?.size?.width ?: 0

    // Look up pre-cached month name heights
    val monthNameHeights = IntArray(MONTHS_PER_YEAR) { i ->
        val style = if (i == todayMonthIndex) todayMonthNameStyle else monthNameStyle
        monthNameLayoutCache[MonthNameKey(i, style)]?.size?.height ?: 0
    }

    for (i in 0 until MONTHS_PER_YEAR) {
        val month = i + 1
        val monthRect = monthRects[i]

        val nameHeight = monthNameHeights[i]

        val gridBounds = Rect(
            left = monthRect.rect.left,
            top = monthRect.rect.top + nameHeight + marginBelowMonthNamePx,
            right = monthRect.rect.right,
            bottom = monthRect.rect.bottom
        )

        var lastRowY = 0f

        forEachCalendarCell(
            gridBounds = gridBounds,
            firstDayOffset = monthMetadata.firstDayOffset(i, firstDayOfWeek),
            daysInMonth = monthMetadata.daysInMonth[i],
            isRtl = isRtl,
            onHeaderCell = { _, _, _ -> /* headers carry no touch target */ },
            onDayCell = { dayOfMonth, centerX, centerY ->
                val dayTextLayout = dayTextLayoutCache[DayTextKey(dayOfMonth, simpleDayTextStyle)]
                if (dayTextLayout != null) {
                    val textWidth = dayTextLayout.size.width
                    val textHeight = dayTextLayout.size.height

                    val effectiveWidth = maxOf(textWidth, twoDigitWidth)
                    val touchWidth = effectiveWidth + dayTouchPaddingPx * 2
                    val touchHeight = textHeight + dayTouchPaddingPx * 2
                    val touchRect = Rect(
                        left = centerX - touchWidth / 2,
                        top = centerY - touchHeight / 2,
                        right = centerX + touchWidth / 2,
                        bottom = centerY + touchHeight / 2
                    )

                    dayRects.add(
                        DayRect(touchRect, year, month, dayOfMonth, textHeight.toFloat(), monthIndex = i)
                    )
                    lastRowY = centerY + textHeight / 2
                }
            },
        )
        lastRowYValues[i] = lastRowY
    }

    return CalendarLayoutData(
        monthRects = monthRects,
        dayRects = dayRects,
        lastRowYValues = lastRowYValues
    )
}

/**
 * Places the 12 month blocks in a [rows] × [columns] grid.
 *
 * In RTL the reading order of the grid itself is mirrored, so January occupies the
 * top-*right* cell and the months progress leftwards along each row. Without this the
 * calendar would read left-to-right inside an otherwise right-to-left screen.
 */
private fun calculateMonthBlocks(
    width: Float,
    height: Float,
    columns: Int,
    rows: Int,
    horizontalSpacing: Float,
    verticalSpacing: Float,
    monthSelectionMargin: Float = 5.0f,
    isRtl: Boolean = false,
): List<MonthRect> {
    val monthRects = mutableListOf<MonthRect>()

    val blockWidth = (width - horizontalSpacing * (columns - 1)) / columns
    val blockHeight = (height - verticalSpacing * (rows - 1)) / rows

    var k = 0
    for (i in 0 until rows) {
        for (j in 0 until columns) {
            if (k >= MONTHS_PER_YEAR) break

            val gridColumn = if (isRtl) columns - 1 - j else j
            val left = gridColumn * (blockWidth + horizontalSpacing)
            val top = i * (blockHeight + verticalSpacing)
            val right = left + blockWidth
            val bottom = top + blockHeight

            val baseRect = Rect(left, top, right, bottom)
            val selectionRect = Rect(
                left - monthSelectionMargin,
                top - monthSelectionMargin,
                right + monthSelectionMargin,
                bottom + monthSelectionMargin // This will be adjusted later based on lastRowY
            )

            monthRects.add(
                MonthRect(
                    rect = baseRect,
                    month = k,
                    lastRowY = 0f, // This will be set later when drawing
                    selectionRect = selectionRect,
                    selectionMargin = monthSelectionMargin
                )
            )
            k++
        }
    }
    return monthRects
}
