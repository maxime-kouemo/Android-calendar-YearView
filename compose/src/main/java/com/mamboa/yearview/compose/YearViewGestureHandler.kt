package com.mamboa.yearview.compose

import androidx.compose.ui.geometry.Offset
import com.mamboa.yearview.core.datetime.CalendarDate
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider
import java.util.Locale

/**
 * Encapsulates all gesture-handling logic (tap, long-press, drag) for [YearView].
 *
 * Every callback and piece of mutable state is accessed through lambdas so the
 * handler never holds stale references when Compose recomposes.
 */
internal class YearViewGestureHandler(
    private val dateTimeProvider: ICalendarDateTimeProvider,
    private val dayFormat: String,
    private val locale: Locale,
    private val year: Int,
    private val enableMultiSelection: Boolean,
    private val isDaySelectionVisuallySticky: Boolean,
    // --- state accessors ---
    private val getLayoutData: () -> CalendarLayoutData,
    private val getSelectedDay: () -> String,
    private val setSelectedDay: (String) -> Unit,
    private val getRangeStart: () -> String?,
    private val getRangeEnd: () -> String?,
    /**
     * Writes both ends of the range at once. A single setter (rather than one per end)
     * guarantees observers never see a transient state where a freshly-set start is
     * paired with a stale end.
     */
    private val setRange: (start: String?, end: String?) -> Unit,
    private val getDragRangeStart: () -> String?,
    private val setDragRangeStart: (String?) -> Unit,
    private val getDragRangeEnd: () -> String?,
    private val setDragRangeEnd: (String?) -> Unit,
    private val setSelectedMonthId: (Int) -> Unit,
    // --- callbacks ---
    private val onDayDeselected: (Long) -> Unit,
    private val onDayClick: (Long) -> Unit,
    private val onDayLongClick: (Long) -> Unit,
    private val onMonthClick: (Long) -> Unit,
    private val onMonthLongClick: (Long) -> Unit,
    private val onSelectedDayChange: (Long) -> Unit,
    private val onRangeSelected: (Long, Long) -> Unit,
    private val flashMonthSelection: (Int) -> Unit,
) {

    /**
     * Finds which month (and optionally which day) contains [offset].
     * Returns `null` if the touch landed outside all month blocks.
     *
     * Runs on every pointer *move* during a drag, so it only ever examines the
     * candidate month's own day cells (≤ 31) via [CalendarLayoutData.dayRectsByMonth],
     * rather than scanning all ~365 rects for the year.
     */
    fun hitTest(offset: Offset): HitTestResult? {
        val snapshot = getLayoutData()

        val targetMonthIndex = snapshot.monthRects.indexOfFirst { it.rect.contains(offset) }
        if (targetMonthIndex < 0) return null

        val candidates = snapshot.dayRectsByMonth[targetMonthIndex]
        if (candidates != null) {
            for (dayRect in candidates) {
                if (dayRect.rect.contains(offset)) {
                    return HitTestResult(targetMonthIndex, dayRect)
                }
            }
        }
        return HitTestResult(targetMonthIndex, dayRect = null)
    }

    fun handleTap(offset: Offset) {
        val hit = hitTest(offset) ?: return

        if (hit.dayRect != null) {
            handleDayGesture(hit.dayRect, isLongPress = false)
        } else {
            // Month area tapped (outside any day cell)
            val layoutData = getLayoutData()
            val monthRect = layoutData.monthRects[hit.monthIndex]
            val monthDate = dateTimeProvider.dateOf(year, monthRect.month + 1, 1)
            setSelectedMonthId(hit.monthIndex)
            onMonthClick(dateTimeProvider.toMillis(monthDate))
            flashMonthSelection(hit.monthIndex)
        }
    }

    fun handleLongPress(offset: Offset) {
        val hit = hitTest(offset) ?: return

        if (hit.dayRect != null) {
            handleDayGesture(hit.dayRect, isLongPress = true)
        } else {
            val layoutData = getLayoutData()
            val monthRect = layoutData.monthRects[hit.monthIndex]
            val monthDate = dateTimeProvider.dateOf(year, monthRect.month + 1, 1)
            onMonthLongClick(dateTimeProvider.toMillis(monthDate))
        }
    }

    /**
     * Shared day-gesture logic for both tap and long-press.
     * Delegates to [handleMultiSelectionRange] when multi-selection is enabled,
     * or to [handleDaySelection] for single-selection / sticky mode.
     */
    private fun handleDayGesture(dayRect: DayRect, isLongPress: Boolean) {
        val calDate = dateTimeProvider.dateOf(dayRect.year, dayRect.month, dayRect.day)
        val dateString = dateTimeProvider.format(calDate, dayFormat, locale)
        val timeInMillis = dateTimeProvider.toMillis(calDate)

        if (enableMultiSelection) {
            handleMultiSelectionRange(dateString, calDate)
        } else {
            handleDaySelection(dateString, timeInMillis, isLongPress = isLongPress)
        }
    }

    /**
     * Parses a day string, returning `null` when it does not match [dayFormat].
     *
     * Range endpoints can originate from the hoisted `rangeStart` / `rangeEnd`
     * parameters of [YearView], so they are not guaranteed to be well-formed —
     * a malformed value must never crash a tap.
     */
    private fun parseOrNull(dateString: String): CalendarDate? = try {
        dateTimeProvider.parse(dateString, dayFormat, locale)
    } catch (_: Exception) {
        null
    }

    /**
     * Emits a normalised (start <= end) range and notifies listeners.
     */
    private fun commitRange(first: CalendarDate, second: CalendarDate) {
        val (startDate, endDate) = if (dateTimeProvider.isAfter(first, second)) {
            second to first
        } else {
            first to second
        }
        setRange(
            dateTimeProvider.format(startDate, dayFormat, locale),
            dateTimeProvider.format(endDate, dayFormat, locale),
        )
        onRangeSelected(dateTimeProvider.toMillis(startDate), dateTimeProvider.toMillis(endDate))
    }

    /**
     * Multi-selection range logic: first tap sets start, second tap sets end
     * (normalised so start <= end), third tap resets the range.
     */
    private fun handleMultiSelectionRange(
        dateString: String,
        calDate: CalendarDate,
    ) {
        val rangeStart = getRangeStart()
        val rangeEnd = getRangeEnd()
        // An unparseable existing start is treated as "no start", so the tap restarts
        // the range instead of throwing.
        val startDate = rangeStart?.let { parseOrNull(it) }
        when {
            startDate == null -> setRange(dateString, null)
            rangeEnd == null -> commitRange(startDate, calDate)
            // Reset the range once both ends are set
            else -> setRange(dateString, null)
        }
    }

    /**
     * Shared selection logic for both tap and long-press, matching Legacy behavior:
     * - Sticky mode: toggling off fires [onDayDeselected] + [onSelectedDayChange] but
     *   does **not** fire [onDayClick]/[onDayLongClick].
     * - Sticky mode: selecting a new day fires [onDayClick]/[onDayLongClick] +
     *   [onSelectedDayChange].
     * - Non-sticky mode: always fires [onDayClick]/[onDayLongClick].
     */
    private fun handleDaySelection(dateString: String, timeInMillis: Long, isLongPress: Boolean) {
        if (isDaySelectionVisuallySticky) {
            val selectedDay = getSelectedDay()
            if (selectedDay == dateString) {
                // Deselect — matches Legacy: fire deselected + selectedDayChange(-1),
                // but do NOT fire onDayClick/onDayLongClick
                setSelectedDay("")
                onSelectedDayChange(-1L)
                onDayDeselected(timeInMillis)
                return
            } else {
                setSelectedDay(dateString)
                onSelectedDayChange(timeInMillis)
            }
        }
        // Fire click/long-click callback
        if (isLongPress) onDayLongClick(timeInMillis) else onDayClick(timeInMillis)
    }

    fun handleDragStart(offset: Offset) {
        if (!enableMultiSelection) return
        val hit = hitTest(offset) ?: return
        if (hit.dayRect != null) {
            val calDate = dateTimeProvider.dateOf(hit.dayRect.year, hit.dayRect.month, hit.dayRect.day)
            val dateString = dateTimeProvider.format(calDate, dayFormat, locale)
            setDragRangeStart(dateString)
            setDragRangeEnd(dateString)
        }
    }

    fun handleDrag(offset: Offset) {
        if (!enableMultiSelection || getDragRangeStart() == null) return
        val hit = hitTest(offset) ?: return
        if (hit.dayRect != null) {
            val calDate = dateTimeProvider.dateOf(hit.dayRect.year, hit.dayRect.month, hit.dayRect.day)
            val dateString = dateTimeProvider.format(calDate, dayFormat, locale)
            setDragRangeEnd(dateString)
        }
    }

    fun handleDragEnd() {
        if (!enableMultiSelection) return
        val start = getDragRangeStart()
        val end = getDragRangeEnd()
        if (start != null && end != null && start != end) {
            val startDate = parseOrNull(start)
            val endDate = parseOrNull(end)
            if (startDate != null && endDate != null) {
                commitRange(startDate, endDate)
            }
        }
        setDragRangeStart(null)
        setDragRangeEnd(null)
    }

}
