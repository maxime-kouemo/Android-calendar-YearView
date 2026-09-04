package com.mamboa.yearview.legacy

import android.graphics.Rect
import android.view.View

/**
 * Bundles all per-frame drawing parameters into a single object,
 * replacing the 16+ individual parameters previously threaded through
 * [MonthRenderer.drawMonths] → [MonthRenderer.drawAMonth] → [MonthRenderer.drawDay].
 *
 * Pre-allocated once in [YearView] and updated via [update] before each draw pass
 * to avoid per-frame allocations.
 *
 * **[weekendDays]** is the single source of truth for weekend day indices,
 * eliminating the previous dual storage in both `YearView` and `Paints`.
 *
 * Use [create] to instantiate — the private constructor ensures all fields
 * are initialized before first use.
 */
internal class DrawContext private constructor(
    var layout: Array<CachedMonthData>,
    var monthBlocks: Array<Rect>,
    var paints: MonthRenderer.Paints,
    var monthConfig: MonthConfig,
    var dayNameConfig: DayNameConfig,
    var todayConfig: DayConfig,
    var selectedDayConfig: DayConfig,
    var multiSelectionBackgroundItemStyle: LegacyBackgroundStyle,
    var horizontalSpacing: Int,
    var layoutDirection: Int,
    var cachedDayHeaderNames: Array<String>?,
    var firstDayOfWeek: Int,
    var currentYear: Int,
    var selectionManager: SelectionManager,
    var layoutComputer: CalendarLayoutComputer,
    /** Weekend day indices (ISO 1=Mon..7=Sun). Single source of truth. */
    var weekendDays: IntArray?
) {

    /**
     * Number of months that actually have a positioned block this frame.
     *
     * Derived from [CalendarLayoutComputer.blockCount] rather than assumed to be 12,
     * because a grid smaller than a year cannot position every month. Draw loops must
     * bound themselves by this value.
     */
    val visibleMonthCount: Int
        get() = layoutComputer.blockCount

    /**
     * Whether the host view resolved to a right-to-left layout direction.
     *
     * Drives day-column mirroring in [MonthRenderer] and month-title gravity
     * resolution. Read from the resolved [layoutDirection] rather than the locale so
     * that an explicit `android:layoutDirection` on the view is honoured.
     */
    val isRtl: Boolean
        get() = layoutDirection == View.LAYOUT_DIRECTION_RTL

    /**
     * Updates all fields in-place. Called once per [YearView.onDraw] — zero allocations.
     */
    fun update(
        layout: Array<CachedMonthData>,
        monthBlocks: Array<Rect>,
        paints: MonthRenderer.Paints,
        monthConfig: MonthConfig,
        dayNameConfig: DayNameConfig,
        todayConfig: DayConfig,
        selectedDayConfig: DayConfig,
        multiSelectionBackgroundItemStyle: LegacyBackgroundStyle,
        horizontalSpacing: Int,
        layoutDirection: Int,
        cachedDayHeaderNames: Array<String>?,
        firstDayOfWeek: Int,
        currentYear: Int,
        selectionManager: SelectionManager,
        layoutComputer: CalendarLayoutComputer,
        weekendDays: IntArray?
    ) {
        this.layout = layout
        this.monthBlocks = monthBlocks
        this.paints = paints
        this.monthConfig = monthConfig
        this.dayNameConfig = dayNameConfig
        this.todayConfig = todayConfig
        this.selectedDayConfig = selectedDayConfig
        this.multiSelectionBackgroundItemStyle = multiSelectionBackgroundItemStyle
        this.horizontalSpacing = horizontalSpacing
        this.layoutDirection = layoutDirection
        this.cachedDayHeaderNames = cachedDayHeaderNames
        this.firstDayOfWeek = firstDayOfWeek
        this.currentYear = currentYear
        this.selectionManager = selectionManager
        this.layoutComputer = layoutComputer
        this.weekendDays = weekendDays
    }

    companion object {
        /**
         * Creates a [DrawContext] with all required fields, guaranteeing
         * all fields are initialized before first use.
         */
        fun create(
            layout: Array<CachedMonthData>,
            monthBlocks: Array<Rect>,
            paints: MonthRenderer.Paints,
            monthConfig: MonthConfig,
            dayNameConfig: DayNameConfig,
            todayConfig: DayConfig,
            selectedDayConfig: DayConfig,
            multiSelectionBackgroundItemStyle: LegacyBackgroundStyle,
            horizontalSpacing: Int,
            layoutDirection: Int,
            cachedDayHeaderNames: Array<String>?,
            firstDayOfWeek: Int,
            currentYear: Int,
            selectionManager: SelectionManager,
            layoutComputer: CalendarLayoutComputer,
            weekendDays: IntArray?
        ): DrawContext = DrawContext(
            layout = layout,
            monthBlocks = monthBlocks,
            paints = paints,
            monthConfig = monthConfig,
            dayNameConfig = dayNameConfig,
            todayConfig = todayConfig,
            selectedDayConfig = selectedDayConfig,
            multiSelectionBackgroundItemStyle = multiSelectionBackgroundItemStyle,
            horizontalSpacing = horizontalSpacing,
            layoutDirection = layoutDirection,
            cachedDayHeaderNames = cachedDayHeaderNames,
            firstDayOfWeek = firstDayOfWeek,
            currentYear = currentYear,
            selectionManager = selectionManager,
            layoutComputer = layoutComputer,
            weekendDays = weekendDays
        )
    }
}
