package com.mamboa.yearview.compose

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import com.mamboa.yearview.core.TitleGravity
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider

/**
 * Composite key for the pre-measured day text layout cache.
 * Combines the day number (1–31) with the [TextStyle] identity so that
 * different styles (simple, weekend, today, selected) each get their own entry.
 */
data class DayTextKey(val day: Int, val style: TextStyle)

/**
 * Composite key for the pre-measured month name layout cache.
 * Combines the 0-based month index with the [TextStyle] so that
 * normal and "today" month name styles each get their own entry.
 */
data class MonthNameKey(val monthIndex: Int, val style: TextStyle)

/**
 * Pre-measured day-of-week header entry, replacing a raw [Triple].
 * Holds the single-character day name, its [TextStyle], and the cached [TextLayoutResult].
 */
data class DayNameEntry(
    val name: String,
    val style: TextStyle,
    val layout: TextLayoutResult,
)

// ── Sub-context: layout & calendar info ─────────────────────────────

/**
 * Pure layout and calendar data for a single month cell —
 * rectangle bounds, month/year indices, day-of-week offset, etc.
 */
data class MonthLayoutInfo(
    /** Pre-computed rectangle for this month's layout area. */
    val monthRect: MonthRect,
    /** 0-based month index. */
    val month: Int,
    /** Day-of-week offset for the 1st of the month. */
    val firstDay: Int,
    /** Number of days in this month. */
    val daysInMonth: Int,
    /** Calendar year. */
    val year: Int,
    /** Month title gravity (START, CENTER, END). */
    val monthTitleGravity: TitleGravity,
    /** Pixel margin below the month name before the day grid. */
    val marginBelowMonthNamePx: Float,
    /** Whether this month is the current month (for month name styling). */
    val isCurrentMonth: Boolean = false,
    /** Pre-computed day-name entries (name, style, measured layout). */
    val dayNames: List<DayNameEntry> = emptyList(),
    /** Month name format string (e.g. "MMMM"). */
    val monthFormat: String,
    /** Locale for formatting. */
    val locale: java.util.Locale,
    /** Calendar/date-time provider abstraction. */
    val dateTimeProvider: ICalendarDateTimeProvider,
    /**
     * Whether the day grid runs right-to-left.
     *
     * Must match the value passed to [calculateCalendarLayout], because the touch and
     * TalkBack rectangles are derived from the same [forEachCalendarCell] traversal.
     */
    val isRtl: Boolean = false,
)

// ── Sub-context: day styling & selection ─────────────────────────────

/**
 * All styling configuration for day cells — [DayConfig]s, text styles,
 * background painters, selection/range state, and predicate lambdas.
 *
 * Uses a regular class with manual [equals]/[hashCode] that excludes
 * lambda fields (whose identity always differs across recompositions).
 *
 * Marked [@Stable] because the class provides custom [equals]/[hashCode]
 * and Compose cannot otherwise infer stability for classes with lambda fields.
 */
@Stable
class DayStyleBundle(
    // Day configs
    val simpleDayConfig: DayConfig,
    val weekendDayConfig: DayConfig,
    val todayConfig: DayConfig,
    val selectedDayConfig: DayConfig,
    // Text styles
    val monthNameStyle: TextStyle,
    val todayMonthNameStyle: TextStyle,
    // Day predicate lambdas  (excluded from equals/hashCode – lambda identity always differs)
    val isToday: (Int, Int) -> Boolean,
    val isWeekend: (Int, Int) -> Boolean,
    val isSelectedDay: (Int, Int) -> Boolean,
    // Selection / range
    /** Pre-computed range membership check using encoded (month * 100 + day) set. */
    val isInRange: (Int, Int) -> Boolean = { _, _ -> false },
    val multiSelectionBackgroundItemStyle: ComposeBackgroundStyle? = null,
    // Background styles & painters
    val monthBackgroundItemStyle: ComposeBackgroundStyle? = null,
    val monthPainter: Painter? = null,
    val todayPainter: Painter? = null,
    val selectedDayPainter: Painter? = null,
    val simpleDayPainter: Painter? = null,
    val weekendDayPainter: Painter? = null,
    val multiSelectionPainter: Painter? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DayStyleBundle) return false
        return simpleDayConfig == other.simpleDayConfig &&
            weekendDayConfig == other.weekendDayConfig &&
            todayConfig == other.todayConfig &&
            selectedDayConfig == other.selectedDayConfig &&
            monthNameStyle == other.monthNameStyle &&
            todayMonthNameStyle == other.todayMonthNameStyle &&
            multiSelectionBackgroundItemStyle == other.multiSelectionBackgroundItemStyle &&
            monthBackgroundItemStyle == other.monthBackgroundItemStyle &&
            monthPainter == other.monthPainter &&
            todayPainter == other.todayPainter &&
            selectedDayPainter == other.selectedDayPainter &&
            simpleDayPainter == other.simpleDayPainter &&
            weekendDayPainter == other.weekendDayPainter &&
            multiSelectionPainter == other.multiSelectionPainter
    }

    override fun hashCode(): Int {
        var result = simpleDayConfig.hashCode()
        result = 31 * result + weekendDayConfig.hashCode()
        result = 31 * result + todayConfig.hashCode()
        result = 31 * result + selectedDayConfig.hashCode()
        result = 31 * result + monthNameStyle.hashCode()
        result = 31 * result + todayMonthNameStyle.hashCode()
        result = 31 * result + (multiSelectionBackgroundItemStyle?.hashCode() ?: 0)
        result = 31 * result + (monthBackgroundItemStyle?.hashCode() ?: 0)
        result = 31 * result + (monthPainter?.hashCode() ?: 0)
        result = 31 * result + (todayPainter?.hashCode() ?: 0)
        result = 31 * result + (selectedDayPainter?.hashCode() ?: 0)
        result = 31 * result + (simpleDayPainter?.hashCode() ?: 0)
        result = 31 * result + (weekendDayPainter?.hashCode() ?: 0)
        result = 31 * result + (multiSelectionPainter?.hashCode() ?: 0)
        return result
    }
}

// ── Sub-context: draw-time resources ─────────────────────────────────

/**
 * Shared, reusable drawing resources — text measurer, scratch paths,
 * caches — that are independent of any particular month's data.
 */
data class DrawResources(
    /** Text measurer for on-demand measurements. */
    val textMeasurer: TextMeasurer,
    /** Drawable-path cache keyed by resource id. */
    val drawablePathCache: Map<Int, Path> = emptyMap(),
    /** Pre-resolved ResourceShapeProvider innerPadding dimension values (dimen res ID -> px). */
    val resolvedInnerPaddings: Map<Int, Float> = emptyMap(),
    /** Reusable scratch [Path] to avoid per-cell allocations in draw scope. */
    val scratchPath: Path = Path(),
    /**
     * Dedicated scratch [Path] for clip operations. Kept separate from [scratchPath]
     * because `clipPath()` retains its Path reference for the duration of the clip lambda;
     * reusing [scratchPath] inside that lambda would cause rendering corruption.
     */
    val clipScratchPath: Path = Path(),
    /**
     * Dedicated scratch [Path] for scale-and-translate operations inside
     * [scaleAndTranslatePath], avoiding a per-call [Path] allocation when
     * scaling custom drawable / custom-provider shapes.
     */
    val scaleScratchPath: Path = Path(),
    /**
     * Reusable [Matrix] for the scale step of custom-shape rendering.
     *
     * `Matrix()` allocates a backing `FloatArray(16)`, which would otherwise happen
     * once per custom-shape cell per frame and undo the benefit of pooling the paths
     * around it.
     */
    val scaleMatrix: Matrix = Matrix(),
    /**
     * Pre-measured [TextLayoutResult] for day numbers 1–31, keyed by day number.
     * Each entry maps a day number to its measured layout for every distinct [DayConfig.textStyle].
     * Avoids calling [TextMeasurer.measure] on every draw pass.
     */
    val dayTextLayouts: Map<DayTextKey, TextLayoutResult> = emptyMap(),
    /**
     * Pre-measured [TextLayoutResult] for all 12 month names, keyed by
     * (0-based month index, [TextStyle]).  Covers both the normal name style
     * and today's name style so that `drawMonthName` never calls
     * [TextMeasurer.measure] at draw time.
     */
    val monthNameLayouts: Map<MonthNameKey, TextLayoutResult> = emptyMap(),
    /** Touch target padding around day text (px), converted from dp at composition time. */
    val dayTouchPaddingPx: Float = 0f,
    /**
     * Display density (px per dp).
     *
     * `DrawScope` is itself a `Density`, but the shape builders are extensions on
     * [Path] rather than on the draw scope, and they still need to turn the dp-valued
     * dimensions carried by `core`'s [com.mamboa.yearview.core.BackgroundShape] into
     * pixels. Carrying the scale factor here keeps that conversion in one place instead
     * of forcing every geometry helper to take a `Density` receiver.
     */
    val density: Float = 1f,
)

// ── Composite context ────────────────────────────────────────────────

/**
 * Groups all parameters needed by `drawMonth` into a single context object,
 * composed of three focused sub-contexts:
 *
 * - [MonthLayoutInfo] — rectangle, calendar indices, formatting
 * - [DayStyleBundle] — day configs, painters, selection state
 * - [DrawResources] — text measurer, scratch paths, caches
 *
 * ### Why inline delegates?
 *
 * All sub-context properties are re-exported at the top level via `inline`
 * getter delegates. This is intentional: drawing functions use
 * `with(ctx) { monthRect; simpleDayConfig; scratchPath; … }` extensively,
 * and the flat namespace keeps those call-sites concise. The delegates
 * compile to direct field accesses with zero overhead.
 *
 * When adding a property to a sub-context, add a corresponding delegate
 * here so that draw-function call-sites remain consistent.
 *
 * Note: `@Stable` is intentionally omitted here because this class is only
 * created per-month inside `DrawScope` (not during composition), so the
 * annotation would have no effect and could mislead readers.
 */
class MonthDrawContext(
    val layout: MonthLayoutInfo,
    val style: DayStyleBundle,
    val resources: DrawResources,
) {
    // ── MonthLayoutInfo delegates ────────────────────────────────────
    inline val monthRect get() = layout.monthRect
    inline val month get() = layout.month
    inline val firstDay get() = layout.firstDay
    inline val daysInMonth get() = layout.daysInMonth
    inline val year get() = layout.year
    inline val monthTitleGravity get() = layout.monthTitleGravity
    inline val marginBelowMonthNamePx get() = layout.marginBelowMonthNamePx
    inline val isCurrentMonth get() = layout.isCurrentMonth
    inline val dayNames get() = layout.dayNames
    inline val monthFormat get() = layout.monthFormat
    inline val locale get() = layout.locale
    inline val dateTimeProvider get() = layout.dateTimeProvider
    inline val isRtl get() = layout.isRtl

    // ── DayStyleBundle delegates ────────────────────────────────────
    inline val simpleDayConfig get() = style.simpleDayConfig
    inline val weekendDayConfig get() = style.weekendDayConfig
    inline val todayConfig get() = style.todayConfig
    inline val selectedDayConfig get() = style.selectedDayConfig
    inline val monthNameStyle get() = style.monthNameStyle
    inline val todayMonthNameStyle get() = style.todayMonthNameStyle
    inline val isToday get() = style.isToday
    inline val isWeekend get() = style.isWeekend
    inline val isSelectedDay get() = style.isSelectedDay
    inline val isInRange get() = style.isInRange
    inline val multiSelectionBackgroundItemStyle get() = style.multiSelectionBackgroundItemStyle
    inline val monthBackgroundItemStyle get() = style.monthBackgroundItemStyle
    inline val monthPainter get() = style.monthPainter
    inline val todayPainter get() = style.todayPainter
    inline val selectedDayPainter get() = style.selectedDayPainter
    inline val simpleDayPainter get() = style.simpleDayPainter
    inline val weekendDayPainter get() = style.weekendDayPainter
    inline val multiSelectionPainter get() = style.multiSelectionPainter

    // ── DrawResources delegates ─────────────────────────────────────
    inline val textMeasurer get() = resources.textMeasurer
    inline val drawablePathCache get() = resources.drawablePathCache
    inline val resolvedInnerPaddings get() = resources.resolvedInnerPaddings
    inline val scratchPath get() = resources.scratchPath
    inline val clipScratchPath get() = resources.clipScratchPath
    inline val scaleScratchPath get() = resources.scaleScratchPath
    inline val scaleMatrix get() = resources.scaleMatrix
    inline val dayTextLayouts get() = resources.dayTextLayouts
    inline val monthNameLayouts get() = resources.monthNameLayouts
    inline val dayTouchPaddingPx get() = resources.dayTouchPaddingPx
    inline val density get() = resources.density
}
