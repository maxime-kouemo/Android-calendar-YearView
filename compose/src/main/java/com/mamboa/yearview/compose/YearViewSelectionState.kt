package com.mamboa.yearview.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider
import com.mamboa.yearview.core.datetime.timeproviders.KotlinxTimeProvider
import java.util.Locale

/**
 * Observable holder for everything the user can *select* in a [YearView]:
 * the sticky day selection and the multi-selection range.
 *
 * ### Why this is separate from [YearViewState]
 *
 * [YearViewState] is immutable **configuration** — how the calendar looks and behaves.
 * Selection, by contrast, changes in response to user input, so it lives in a [Stable]
 * holder backed by snapshot state. Keeping the two apart means there is exactly one
 * owner of the selection: reading [selectedDay] or [rangeStart] always reflects what
 * is currently drawn, whether the change came from a tap or from application code.
 *
 * Every property is snapshot state, so mutating it from anywhere (a callback, a
 * `LaunchedEffect`, a ViewModel-driven effect) recomposes and redraws the calendar.
 *
 * Example:
 * ```
 * val selection = rememberYearViewSelectionState(selectedDay = "2026-01-10")
 *
 * YearView(state = state, selection = selection)
 *
 * // Observe changes declaratively — no callback plumbing required.
 * LaunchedEffect(selection.rangeStart, selection.rangeEnd) {
 *     viewModel.onRangeChanged(selection.rangeStart, selection.rangeEnd)
 * }
 *
 * Button(onClick = { selection.clearAll() }) { Text("Reset") }
 * ```
 *
 * All day strings use the pattern configured by [YearViewState.dayFormat].
 *
 * @param selectedDay Initially selected day, or `""` for no selection.
 * @param rangeStart Initial range start, or `null` for no range.
 * @param rangeEnd Initial range end, or `null` when only a start has been picked.
 */
@Stable
class YearViewSelectionState(
    selectedDay: String = "",
    rangeStart: String? = null,
    rangeEnd: String? = null,
) {
    /**
     * The currently selected day, formatted with [YearViewState.dayFormat],
     * or `""` when nothing is selected.
     *
     * Only meaningful when [YearViewState.isDaySelectionVisuallySticky] is `true`;
     * otherwise taps are reported through callbacks without persisting a selection.
     */
    var selectedDay: String by mutableStateOf(selectedDay)

    /**
     * Start of the multi-selection range, or `null` when no range is active.
     *
     * Read-only from the outside: use [setRange] so both ends always change together
     * and observers never see a start paired with a stale end.
     */
    var rangeStart: String? by mutableStateOf(rangeStart)
        private set

    /**
     * End of the multi-selection range. `null` while the user has picked a start
     * but not yet an end.
     *
     * Read-only from the outside: use [setRange].
     */
    var rangeEnd: String? by mutableStateOf(rangeEnd)
        private set

    /** `true` when both ends of a range are set. */
    val hasCompleteRange: Boolean
        get() = rangeStart != null && rangeEnd != null

    /**
     * Replaces both ends of the range in a single snapshot write.
     *
     * Writing both ends together is deliberate: a caller observing
     * [rangeStart] / [rangeEnd] can never be woken up with a half-updated range.
     */
    fun setRange(start: String?, end: String?) {
        rangeStart = start
        rangeEnd = end
    }

    /** Clears the sticky day selection. */
    fun clearSelection() {
        selectedDay = ""
    }

    /** Clears the multi-selection range. */
    fun clearRange() = setRange(null, null)

    /** Clears both the day selection and the range. */
    fun clearAll() {
        clearSelection()
        clearRange()
    }

    override fun toString(): String =
        "YearViewSelectionState(selectedDay='$selectedDay', rangeStart=$rangeStart, rangeEnd=$rangeEnd)"

    companion object {
        /** [Saver] used by [rememberYearViewSelectionState] to survive process death. */
        val Saver: Saver<YearViewSelectionState, Any> = listSaver(
            save = { listOf(it.selectedDay, it.rangeStart, it.rangeEnd) },
            restore = { saved ->
                YearViewSelectionState(
                    selectedDay = saved[0] as? String ?: "",
                    rangeStart = saved.getOrNull(1) as? String,
                    rangeEnd = saved.getOrNull(2) as? String,
                )
            }
        )
    }
}

/**
 * Creates a [YearViewSelectionState] that is remembered across recompositions and
 * restored after configuration changes / process death.
 *
 * The parameters are **initial values only** — they seed the holder on first
 * composition and are ignored afterwards, because from that point on the holder is
 * the single source of truth. To change the selection later, write to the returned
 * object (e.g. `selection.selectedDay = "2026-03-01"`).
 */
@Composable
fun rememberYearViewSelectionState(
    selectedDay: String = "",
    rangeStart: String? = null,
    rangeEnd: String? = null,
): YearViewSelectionState = rememberSaveable(saver = YearViewSelectionState.Saver) {
    YearViewSelectionState(
        selectedDay = selectedDay,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
    )
}

/**
 * Parses [YearViewSelectionState.selectedDay] and returns the corresponding epoch
 * millis, or `null` when nothing is selected or the value cannot be parsed with
 * [dayFormat].
 */
fun YearViewSelectionState.selectedDayMillis(
    dayFormat: String = "yyyy-MM-dd",
    locale: Locale = Locale.ROOT,
    dateTimeProvider: ICalendarDateTimeProvider = KotlinxTimeProvider(),
): Long? {
    val day = selectedDay
    if (day.isBlank()) return null
    return try {
        dateTimeProvider.toMillis(dateTimeProvider.parse(day, dayFormat, locale))
    } catch (_: Exception) {
        null
    }
}
