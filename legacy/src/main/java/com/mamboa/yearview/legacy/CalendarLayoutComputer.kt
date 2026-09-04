package com.mamboa.yearview.legacy

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider
import com.mamboa.yearview.core.utils.Utils

/**
 * Pre-computed data for a single month, computed once per year/config change.
 */
internal class CachedMonthData(
    /** Column offset for day 1 (used as `firstDay` param in drawAMonth). */
    val firstDayOffset: Int,
    /** Number of days in this month. */
    val daysInMonth: Int,
    /** Localised display name of this month. */
    val monthName: String,
    /** ISO day-of-week (1=Mon..7=Sun) for each day; index 0 = day 1. */
    val dayOfWeek: IntArray
)

/**
 * Responsible for computing and caching calendar layout data.
 *
 * Extracted from [YearView] to isolate calendar math and grid layout
 * from rendering and gesture handling.
 *
 * All public state is safe to read from the UI thread only (consistent
 * with [android.view.View] threading rules).
 */
internal class CalendarLayoutComputer(
    private val context: Context,
    private val monthBlocks: Array<Rect>
) {

    /** Cached layout for all 12 months. Null means "needs recompute". */
    var cachedMonthData: Array<CachedMonthData>? = null
        private set

    /** Cached day-of-week header labels (first char), length 7. */
    var cachedDayHeaderNames: Array<String>? = null
        private set

    /** Cached today: year, month (1-based), day. -1 means unknown/stale. */
    var cachedTodayYear: Int = -1
        private set
    var cachedTodayMonth: Int = -1
        private set
    var cachedTodayDay: Int = -1
        private set

    /** Timestamp (ms) of the last [refreshToday] call to throttle per-frame provider calls. */
    private var lastTodayRefreshMs: Long = 0L

    /**
     * Vertical offset (px) from a month block's top edge to the first grid row, i.e.
     * the space reserved for the drawn month title.
     *
     * This lives here rather than in [MonthRenderer] because it is *geometry*, not
     * rendering: hit-testing needs it to locate the day grid. While the renderer owned
     * it the array was only populated as a side effect of `onDraw`, so any touch
     * arriving before the first frame was resolved against an offset of zero and
     * mapped to the wrong row.
     */
    val monthTitleOffsets = IntArray(MonthGridGeometry.MAX_MONTHS)

    /** Set whenever anything feeding [monthTitleOffsets] changes. */
    private var titleOffsetsDirty = true

    /** Reusable bounds for title measurement. UI thread only. */
    private val titleBounds = Rect()

    /**
     * Invalidates the pre-computed calendar layout so it will be rebuilt before the next draw.
     */
    fun invalidateLayout() {
        cachedMonthData = null
        cachedDayHeaderNames = null
        titleOffsetsDirty = true
    }

    /**
     * Marks [monthTitleOffsets] stale without discarding the calendar layout.
     *
     * Call this when the month-name paints or [MonthConfig.marginBelowMonthName]
     * change: the dates are unaffected but the reserved title height is not.
     */
    fun invalidateTitleMetrics() {
        titleOffsetsDirty = true
    }

    /**
     * Recomputes [monthTitleOffsets] if stale. Cheap and idempotent, so it is safe to
     * call from both `onDraw` and hit-testing.
     *
     * @param currentYear the displayed year, needed to decide which month (if any)
     *   is rendered with [todayMonthNamePaint] and may therefore be a different height
     */
    fun ensureTitleOffsets(
        monthNamePaint: Paint,
        todayMonthNamePaint: Paint,
        marginBelowMonthName: Int,
        currentYear: Int
    ) {
        if (!titleOffsetsDirty) return
        val months = cachedMonthData ?: return
        for (i in months.indices) {
            val isTodayMonth = currentYear == cachedTodayYear && (i + 1) == cachedTodayMonth
            val paint = if (isTodayMonth) todayMonthNamePaint else monthNamePaint
            val name = months[i].monthName
            paint.getTextBounds(name, 0, name.length, titleBounds)
            monthTitleOffsets[i] =
                titleBounds.height() * MONTH_TITLE_HEIGHT_MULTIPLIER + marginBelowMonthName
        }
        titleOffsetsDirty = false
    }

    /**
     * Ensures the pre-computed calendar layout is up-to-date.
     * Called once at the start of onDraw; the computed data is then consumed
     * by draw methods without any further DateTime allocations.
     */
    fun ensureLayout(
        dateTimeProvider: ICalendarDateTimeProvider,
        currentYear: Int,
        firstDayOfWeek: Int,
        monthNameFormat: String
    ) {
        if (cachedMonthData != null) {
            refreshToday(dateTimeProvider)
            return
        }
        computeLayout(dateTimeProvider, currentYear, firstDayOfWeek, monthNameFormat)
    }

    /**
     * Refreshes the cached "today" values at most once per [TODAY_REFRESH_INTERVAL_MS]
     * to avoid calling [ICalendarDateTimeProvider.today] on every frame.
     */
    private fun refreshToday(dateTimeProvider: ICalendarDateTimeProvider) {
        val now = System.currentTimeMillis()
        if (now - lastTodayRefreshMs < TODAY_REFRESH_INTERVAL_MS) return
        lastTodayRefreshMs = now
        val today = dateTimeProvider.today()
        // The "today" month is titled with a different paint, so rolling over midnight
        // into a new month can change the reserved title height.
        if (today.year != cachedTodayYear || today.month != cachedTodayMonth) {
            titleOffsetsDirty = true
        }
        cachedTodayYear = today.year
        cachedTodayMonth = today.month
        cachedTodayDay = today.day
    }

    /**
     * Pre-computes every piece of calendar data:
     * - month names, first-day offsets, days-in-month
     * - ISO day-of-week for every day (weekend detection)
     * - day-of-week header labels
     * - today's (year, month, day)
     */
    private fun computeLayout(
        dateTimeProvider: ICalendarDateTimeProvider,
        currentYear: Int,
        firstDayOfWeek: Int,
        monthNameFormat: String
    ) {
        val locale = Utils.getCurrentLocale(context)
        val daysPerWeek = MonthGridGeometry.DAYS_PER_WEEK
        val months = Array(MonthGridGeometry.MAX_MONTHS) { i ->
            val month = i + 1
            val firstDow = dateTimeProvider.firstDayOfWeekInMonth(currentYear, month)
            val dayOffset = (firstDow - firstDayOfWeek + daysPerWeek) % daysPerWeek

            val days = dateTimeProvider.daysInMonth(currentYear, month)
            val name = dateTimeProvider.formatMonth(currentYear, month, monthNameFormat, locale)

            val dowArray = IntArray(days) { d ->
                dateTimeProvider.dayOfWeek(currentYear, month, d + 1)
            }

            CachedMonthData(dayOffset, days, name, dowArray)
        }
        cachedMonthData = months

        // Day-of-week header labels (first character of short name)
        cachedDayHeaderNames = Array(daysPerWeek) { x ->
            val dayIndex = getDayIndex(x, firstDayOfWeek)
            dateTimeProvider.shortDayOfWeekName(dayIndex, locale)[0].toString()
        }

        // Today
        val today = dateTimeProvider.today()
        cachedTodayYear = today.year
        cachedTodayMonth = today.month
        cachedTodayDay = today.day
    }

    /**
     * Number of month blocks that currently hold valid bounds.
     *
     * This is `min(rows × columns, 12)`. It is **not** always 12: a grid smaller than a
     * year (for example 2 × 2) can only position four months, and the remaining entries
     * of [monthBlocks] are left empty. Renderers and hit-testing must iterate up to this
     * count rather than assuming twelve, otherwise the surplus months are drawn into a
     * stale or zero-sized rectangle at the top-left corner.
     */
    var blockCount: Int = 0
        private set

    /** Scratch buffer for [MonthGridGeometry.blockBoundsInto], reused to avoid allocation. */
    private val blockBounds = IntArray(4)

    /**
     * Splits the view into a `columns × rows` grid of month blocks.
     *
     * At most [MonthGridGeometry.MAX_MONTHS] blocks are populated; any remaining grid
     * slots (and any month that does not fit) are cleared and reported via [blockCount].
     * Called from `onSizeChanged`, so it only recalculates when dimensions change.
     */
    fun splitViewInBlocks(
        viewWidth: Int,
        viewHeight: Int,
        columns: Int,
        rows: Int,
        horizontalSpacing: Int,
        verticalSpacing: Int
    ) {
        if (viewWidth <= 0 || viewHeight <= 0) return

        val usableBlocks = MonthGridGeometry.blockCountFor(rows, columns)

        var k = 0
        outer@ for (i in 0..<rows) {
            for (j in 0..<columns) {
                if (k >= usableBlocks) break@outer

                MonthGridGeometry.blockBoundsInto(
                    out = blockBounds,
                    row = i,
                    column = j,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    columns = columns,
                    rows = rows,
                    horizontalSpacing = horizontalSpacing,
                    verticalSpacing = verticalSpacing
                )
                monthBlocks[k].set(blockBounds[0], blockBounds[1], blockBounds[2], blockBounds[3])
                k++
            }
        }

        // Clear leftovers so a shrinking grid cannot leave stale bounds behind that
        // would still answer `true` to a hit test.
        for (unused in k until monthBlocks.size) {
            monthBlocks[unused].setEmpty()
        }
        blockCount = k
    }

    companion object {
        /** Minimum interval (ms) between [refreshToday] provider calls. */
        private const val TODAY_REFRESH_INTERVAL_MS = 60_000L

        /**
         * Multiplier applied to the month-name text height to reserve vertical space
         * for the title row. The total title area height is:
         *
         *     textHeight × MONTH_TITLE_HEIGHT_MULTIPLIER + marginBelowMonthName
         *
         * **Assumption:** month names are single-line. Multi-line or very large
         * text sizes may overflow the reserved area.
         */
        private const val MONTH_TITLE_HEIGHT_MULTIPLIER = 2

        /**
         * Returns the day index (ISO: 1=Monday..7=Sunday), taking into account what the user
         * wants as first day of the week.
         *
         * @param position of the day ([0;7[)
         * @param firstDayOfWeek the configured first day of week (1=Mon..7=Sun)
         * @return the day's index
         */
        internal fun getDayIndex(position: Int, firstDayOfWeek: Int): Int {
            return (firstDayOfWeek + position - 1) % MonthGridGeometry.DAYS_PER_WEEK + 1
        }
    }
}
