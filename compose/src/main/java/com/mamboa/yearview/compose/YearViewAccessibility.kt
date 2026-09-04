package com.mamboa.yearview.compose

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Accessibility navigation mode for [YearView].
 *
 * In [MonthNavigation], TalkBack swipes move between the 12 month blocks.
 * After activating (double-tapping) a month the mode switches to
 * [DayNavigation] where swipes move between individual day cells.
 */
internal sealed interface AccessibilityMode {
    /** Swipe navigates through months. */
    data object MonthNavigation : AccessibilityMode

    /**
     * Swipe navigates through individual day cells.
     *
     * @property activatedMonthIndex The 0-based month that was double-tapped
     *   to enter this mode. Focus starts on this month's first day.
     */
    data class DayNavigation(val activatedMonthIndex: Int) : AccessibilityMode
}

/**
 * Positions a composable at exact pixel coordinates [rect] within its parent.
 *
 * Unlike [Modifier.offset] which only changes visual placement, this uses
 * [Modifier.layout] so the **layout bounds** (used by TalkBack for the focus
 * rectangle) are set to the exact [rect] position and size.
 */
private fun Modifier.placeAt(rect: Rect): Modifier = this.layout { measurable, _ ->
    val w = rect.width.roundToInt().coerceAtLeast(0)
    val h = rect.height.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(
        androidx.compose.ui.unit.Constraints.fixed(w, h)
    )
    layout(w, h) {
        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
    }
}

/**
 * Renders invisible overlay composables on top of the Canvas to provide
 * TalkBack-compatible navigation for [YearView].
 *
 * In **month mode** twelve invisible boxes are placed over each month block.
 * In **day mode** an invisible box is placed over every day cell for the
 * entire year, ordered chronologically, so swiping seamlessly crosses
 * month boundaries.
 */
@Composable
internal fun YearViewAccessibilityOverlay(
    layoutData: CalendarLayoutData,
    year: Int,
    dateTimeProvider: ICalendarDateTimeProvider,
    locale: Locale,
    todayMonthIndex: Int,
    todayDay: Int,
    selectedDayEncoded: Int,
    isDaySelectionVisuallySticky: Boolean,
    todayLabel: String,
    selectedLabel: String,
    exitMonthActionLabel: String,
    onDayClick: (Long) -> Unit,
    onDayDeselected: (Long) -> Unit,
    onSelectedDayChange: (Long) -> Unit,
    dayFormat: String,
    selection: YearViewSelectionState,
) {
    if (layoutData.monthRects.isEmpty()) return

    // ── Accessibility mode state ────────────────────────────────────
    var mode by remember { mutableStateOf<AccessibilityMode>(AccessibilityMode.MonthNavigation) }

    // Track the last focused day's month so we can return focus there on exit
    var lastFocusedMonthIndex by remember { mutableIntStateOf(0) }

    // Focus requesters for month overlays (used when returning from day mode)
    val monthFocusRequesters = remember { List(MONTHS_PER_YEAR) { FocusRequester() } }

    // Focus requesters for day overlays (used when entering day mode)
    val dayFocusRequesters = remember(layoutData.dayRects.size) {
        List(layoutData.dayRects.size) { FocusRequester() }
    }

    var pendingFocusDayIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var pendingFocusMonthIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }

    LaunchedEffect(pendingFocusDayIndex) {
        if (pendingFocusDayIndex >= 0 && pendingFocusDayIndex < dayFocusRequesters.size) {
            try {
                dayFocusRequesters[pendingFocusDayIndex].requestFocus()
            } catch (_: Exception) { }
            pendingFocusDayIndex = Int.MIN_VALUE
        }
    }

    LaunchedEffect(pendingFocusMonthIndex) {
        if (pendingFocusMonthIndex in 0 until MONTHS_PER_YEAR) {
            try {
                monthFocusRequesters[pendingFocusMonthIndex].requestFocus()
            } catch (_: Exception) { }
            pendingFocusMonthIndex = Int.MIN_VALUE
        }
    }

    when (mode) {
        is AccessibilityMode.MonthNavigation -> {
            MonthAccessibilityOverlay(
                layoutData = layoutData,
                year = year,
                dateTimeProvider = dateTimeProvider,
                locale = locale,
                focusRequesters = monthFocusRequesters,
                onMonthActivated = { monthIndex ->
                    lastFocusedMonthIndex = monthIndex
                    mode = AccessibilityMode.DayNavigation(monthIndex)
                    // Find the first day (day == 1) of this month in the dayRects list
                    val firstDayIdx = layoutData.dayRects.indexOfFirst {
                        it.monthIndex == monthIndex && it.day == 1
                    }
                    if (firstDayIdx >= 0) {
                        pendingFocusDayIndex = firstDayIdx
                    }
                },
            )
        }

        is AccessibilityMode.DayNavigation -> {
            DayAccessibilityOverlay(
                layoutData = layoutData,
                year = year,
                dateTimeProvider = dateTimeProvider,
                locale = locale,
                todayMonthIndex = todayMonthIndex,
                todayDay = todayDay,
                selectedDayEncoded = selectedDayEncoded,
                isDaySelectionVisuallySticky = isDaySelectionVisuallySticky,
                todayLabel = todayLabel,
                selectedLabel = selectedLabel,
                exitMonthActionLabel = exitMonthActionLabel,
                focusRequesters = dayFocusRequesters,
                onDayActivated = { dayRect ->
                    lastFocusedMonthIndex = dayRect.monthIndex
                    val calDate = dateTimeProvider.dateOf(dayRect.year, dayRect.month, dayRect.day)
                    val timeInMillis = dateTimeProvider.toMillis(calDate)
                    if (isDaySelectionVisuallySticky) {
                        val dateString = dateTimeProvider.format(calDate, dayFormat, locale)
                        if (selection.selectedDay == dateString) {
                            // Deselect — matches Legacy: do NOT fire onDayClick
                            selection.clearSelection()
                            onSelectedDayChange(-1L)
                            onDayDeselected(timeInMillis)
                            return@DayAccessibilityOverlay
                        } else {
                            selection.selectedDay = dateString
                            onSelectedDayChange(timeInMillis)
                        }
                    }
                    onDayClick(timeInMillis)
                },
                onDayFocused = { dayRect ->
                    lastFocusedMonthIndex = dayRect.monthIndex
                },
                onExitToMonths = {
                    val returnMonth = lastFocusedMonthIndex
                    mode = AccessibilityMode.MonthNavigation
                    pendingFocusMonthIndex = returnMonth
                },
            )
        }
    }
}

// ── Month-level overlay ──────────────────────────────────────────────

@Composable
private fun MonthAccessibilityOverlay(
    layoutData: CalendarLayoutData,
    year: Int,
    dateTimeProvider: ICalendarDateTimeProvider,
    locale: Locale,
    focusRequesters: List<FocusRequester>,
    onMonthActivated: (Int) -> Unit,
) {
    // fillMaxSize so the placeAt pixel coordinates map to screen positions.
    // isTraversalGroup + traversalIndex force TalkBack to follow month order.
    Box(modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        for (i in layoutData.monthRects.indices) {
            val monthRect = layoutData.monthRects[i]
            val monthName = dateTimeProvider.monthDisplayName(monthRect.month + 1, locale)
            val description = "$monthName $year"

            Box(
                modifier = Modifier
                    .placeAt(monthRect.rect)
                    .then(
                        if (i < focusRequesters.size) {
                            Modifier.focusRequester(focusRequesters[i])
                        } else {
                            Modifier
                        }
                    )
                    // A single semantics block, applied before focusable(), mirroring the
                    // day overlay. The previous clearAndSetSemantics() sat between the two
                    // and wiped the focus semantics that focusable() contributes, so month
                    // and day cells reported themselves differently to TalkBack. These
                    // boxes have no content, so there are no descendant semantics to clear.
                    .semantics {
                        traversalIndex = i.toFloat()
                        contentDescription = description
                        role = Role.Button
                        onClick(label = null) {
                            onMonthActivated(i)
                            true
                        }
                    }
                    .focusable()
            )
        }
    }
}

// ── Day-level overlay ────────────────────────────��───────────────────

@Composable
private fun DayAccessibilityOverlay(
    layoutData: CalendarLayoutData,
    year: Int,
    dateTimeProvider: ICalendarDateTimeProvider,
    locale: Locale,
    todayMonthIndex: Int,
    todayDay: Int,
    selectedDayEncoded: Int,
    isDaySelectionVisuallySticky: Boolean,
    todayLabel: String,
    selectedLabel: String,
    exitMonthActionLabel: String,
    focusRequesters: List<FocusRequester>,
    onDayActivated: (DayRect) -> Unit,
    onDayFocused: (DayRect) -> Unit,
    onExitToMonths: () -> Unit,
) {
    val dayRects = layoutData.dayRects
    if (dayRects.isEmpty()) return

    // Pre-format one accessible date string per day.
    //
    // formatAccessibleDate allocates a Calendar and a DateFormat internally, so doing
    // this inline meant ~365 of each on *every* recomposition — including every tap and
    // every focus change. The chronological day list for a year is fully determined by
    // the year, the locale and the provider, so the size is enough to detect a relayout.
    val dayDescriptions = remember(year, locale, dateTimeProvider, dayRects.size) {
        List(dayRects.size) { i ->
            val dayRect = dayRects[i]
            val calDate = dateTimeProvider.dateOf(dayRect.year, dayRect.month, dayRect.day)
            dateTimeProvider.formatAccessibleDate(calDate, locale)
        }
    }

    // The exit action is identical for every cell; building it once avoids 365
    // CustomAccessibilityAction + List allocations per recomposition. The callback is
    // read through rememberUpdatedState so the cached action never goes stale.
    val currentOnExitToMonths by rememberUpdatedState(onExitToMonths)
    val exitActions = remember(exitMonthActionLabel) {
        listOf(
            CustomAccessibilityAction(exitMonthActionLabel) {
                currentOnExitToMonths()
                true
            }
        )
    }

    // fillMaxSize so the placeAt pixel coordinates map to screen positions.
    // isTraversalGroup + traversalIndex force chronological swipe order.
    Box(modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        for (index in dayRects.indices) {
            val dayRect = dayRects[index]

            val isDayToday = dayRect.monthIndex == todayMonthIndex && dayRect.day == todayDay
            val isDaySelected =
                dayRect.monthIndex * MONTH_DAY_ENCODING_FACTOR + dayRect.day == selectedDayEncoded

            // "today, <date>, selected" — at most two days in the year carry a label, so
            // the common case reuses the cached string with no allocation at all.
            val fullDateText = dayDescriptions.getOrElse(index) { "" }
            val description = when {
                isDayToday && isDaySelected -> "$todayLabel, $fullDateText, $selectedLabel"
                isDayToday -> "$todayLabel, $fullDateText"
                isDaySelected -> "$fullDateText, $selectedLabel"
                else -> fullDateText
            }

            // The Box is placed at the exact cached DayRect pixel bounds via
            // placeAt(), so TalkBack draws its focus rectangle around the day cell.
            Box(
                modifier = Modifier
                    .placeAt(dayRect.rect)
                    .then(
                        if (index < focusRequesters.size) {
                            Modifier.focusRequester(focusRequesters[index])
                        } else {
                            Modifier
                        }
                    )
                    // traversalIndex forces chronological swipe order:
                    // Jan 1 (0f) → Jan 2 (1f) → … → Dec 31 (364f)
                    .semantics {
                        traversalIndex = index.toFloat()
                        contentDescription = description
                        customActions = exitActions
                        role = Role.Button
                        onClick(label = null) {
                            onDayActivated(dayRect)
                            true
                        }
                        if (isDaySelectionVisuallySticky && isDaySelected) {
                            stateDescription = selectedLabel
                        }
                    }
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            onDayFocused(dayRect)
                        }
                    }
                    .focusable()
            )
        }
    }
}
