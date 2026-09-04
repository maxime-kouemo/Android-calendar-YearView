package com.mamboa.yearview.legacy

import com.mamboa.yearview.core.datetime.CalendarDate
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider

/**
 * Encapsulates single-day and multi-selection (range) state for [YearView].
 *
 * Extracted from [YearView] so that the view class does not need to manage
 * selection bookkeeping inline with rendering and gesture detection.
 */
internal class SelectionManager {

    // ── Single-day selection ──────────────────────────────────────────

    var selectedDay: CalendarDate? = null

    var isDaySelectionVisuallySticky: Boolean = false
        private set

    fun setSticky(sticky: Boolean) {
        isDaySelectionVisuallySticky = sticky
        if (!sticky) selectedDay = null
    }

    // ── Multi-selection (range) ──────────────────────────────────────

    var enableMultiSelection: Boolean = false
        private set

    var rangeStart: CalendarDate? = null
        private set

    var rangeEnd: CalendarDate? = null
        private set

    fun setMultiSelectionEnabled(enabled: Boolean) {
        enableMultiSelection = enabled
        if (!enabled) clearRange()
    }

    fun clearRange() {
        rangeStart = null
        rangeEnd = null
    }

    // ── Drag-based range selection ───────────────────────────────────

    /** Transient drag start — set on ACTION_DOWN, committed on ACTION_UP. */
    var dragRangeStart: CalendarDate? = null

    /** Transient drag end — updated on ACTION_MOVE, committed on ACTION_UP. */
    var dragRangeEnd: CalendarDate? = null

    /** Returns true if a drag range selection is currently in progress. */
    val isDragging: Boolean get() = dragRangeStart != null

    /**
     * Returns true if the given day is within the *in-progress* drag range.
     * Used during drawing to preview the range while the user is still dragging.
     * [month] is 0-based.
     */
    fun isDayInDragRange(currentYear: Int, month: Int, dayOfMonth: Int): Boolean {
        val start = dragRangeStart ?: return false
        val end = dragRangeEnd ?: return false
        val m = month + 1
        val dateKey = currentYear * 10000 + m * 100 + dayOfMonth
        val startKey = start.year * 10000 + start.month * 100 + start.day
        val endKey = end.year * 10000 + end.month * 100 + end.day
        // Support dragging in either direction
        return dateKey in minOf(startKey, endKey)..maxOf(startKey, endKey)
    }

    /**
     * Commits the drag range into the final range, normalising start/end order.
     * Returns a [ClickResult.RangeSelected] if a valid range was formed, or null.
     */
    fun commitDragRange(dateTimeProvider: ICalendarDateTimeProvider): ClickResult? {
        val start = dragRangeStart
        val end = dragRangeEnd
        dragRangeStart = null
        dragRangeEnd = null
        if (start == null || end == null || start == end) return null
        val (finalStart, finalEnd) = if (dateTimeProvider.isAfter(start, end)) end to start else start to end
        rangeStart = finalStart
        rangeEnd = finalEnd
        return ClickResult.RangeSelected(finalStart, finalEnd)
    }

    fun cancelDrag() {
        dragRangeStart = null
        dragRangeEnd = null
    }

    /**
     * Directly restores a previously saved range, bypassing [handleDayClicked] logic.
     * Used by [YearView.onRestoreInstanceState] to reliably restore state regardless
     * of whether [enableMultiSelection] is currently enabled.
     *
     * @param start the range start date, or null if no range was saved
     * @param end the range end date, or null if no range was saved
     */
    fun restoreRange(start: CalendarDate?, end: CalendarDate?) {
        rangeStart = start
        rangeEnd = end
    }

    // ── Queries ──────────────────────────────────────────────────────

    /**
     * Returns true if the given day matches the currently selected day.
     * Compares year/month/day integers directly – zero allocations. [month] is 0-based.
     */
    fun isSelectedDay(currentYear: Int, month: Int, dayOfMonth: Int): Boolean {
        val sel = selectedDay ?: return false
        return sel.year == currentYear && sel.month == (month + 1) && sel.day == dayOfMonth
    }

    /**
     * Returns true if the given day is within the multi-selection range.
     * Uses primitive int comparison to avoid per-frame [CalendarDate] allocations.
     * [month] is 0-based.
     */
    fun isDayInRange(currentYear: Int, month: Int, dayOfMonth: Int): Boolean {
        val startDate = rangeStart ?: return false
        val endDate = rangeEnd ?: return false
        val m = month + 1
        // Pack year/month/day into a single comparable int: YYYYMMDD
        val dateKey = currentYear * 10000 + m * 100 + dayOfMonth
        val startKey = startDate.year * 10000 + startDate.month * 100 + startDate.day
        val endKey = endDate.year * 10000 + endDate.month * 100 + endDate.day
        return dateKey in startKey..endKey
    }

    // ── Gesture handling ─────────────────────────────────────────────

    /**
     * Result of a [handleDayClicked] call, indicating what action was taken.
     */
    sealed class ClickResult {
        /** No action taken (clickedDate was null). */
        data object None : ClickResult()

        /** A day was clicked (single-selection or non-sticky). */
        data class DayClicked(val date: CalendarDate, val isLongPress: Boolean) : ClickResult()

        /** A previously selected day was deselected (sticky mode). */
        data class DayDeselected(val date: CalendarDate) : ClickResult()

        /**
         * A range was completed. [start] is guaranteed not to be after [end],
         * regardless of the order the two endpoints were picked in.
         */
        data class RangeSelected(val start: CalendarDate, val end: CalendarDate) : ClickResult()

        /** First tap of a new range (no callback needed yet). */
        data object RangeStarted : ClickResult()
    }

    /**
     * Processes a day click/long-press, updating internal state and returning
     * a [ClickResult] that tells the caller which listener method to invoke.
     */
    fun handleDayClicked(
        clickedDate: CalendarDate?,
        dateTimeProvider: ICalendarDateTimeProvider,
        isLongPress: Boolean
    ): ClickResult {
        if (clickedDate == null) return ClickResult.None

        if (enableMultiSelection) {
            if (rangeStart == null) {
                rangeStart = clickedDate
                return ClickResult.RangeStarted
            } else if (rangeEnd == null) {
                val startDate = rangeStart!!
                // The user may have picked the later endpoint first; normalise so that
                // rangeStart <= rangeEnd always holds, both for drawing and for the callback.
                if (dateTimeProvider.isAfter(startDate, clickedDate)) {
                    rangeStart = clickedDate
                    rangeEnd = startDate
                } else {
                    rangeEnd = clickedDate
                }
                return ClickResult.RangeSelected(rangeStart!!, rangeEnd!!)
            } else {
                rangeStart = clickedDate
                rangeEnd = null
                return ClickResult.RangeStarted
            }
        } else if (isDaySelectionVisuallySticky) {
            if (selectedDay == clickedDate) {
                selectedDay = null
                return ClickResult.DayDeselected(clickedDate)
            } else {
                selectedDay = clickedDate
                return ClickResult.DayClicked(clickedDate, isLongPress)
            }
        } else {
            return ClickResult.DayClicked(clickedDate, isLongPress)
        }
    }
}
