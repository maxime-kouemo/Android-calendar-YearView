package com.mamboa.yearview.legacy

/**
 * Single source of truth for month-grid geometry.
 *
 * Every coordinate the calendar needs — where a day cell is drawn, which cell a
 * touch landed in, and how the view is split into month blocks — is computed
 * here. Rendering ([MonthRenderer]) and hit-testing ([YearView.getClickedDay])
 * previously duplicated this arithmetic and had drifted apart, which is exactly
 * the class of bug this object exists to prevent.
 *
 * ## Cell model
 *
 * A month block is a `DAYS_PER_WEEK × TOTAL_GRID_ROWS` grid. Row [HEADER_ROW] holds
 * the day-name labels; rows [FIRST_DAY_ROW] and below hold day numbers.
 *
 * A cell at (`row`, `column`) occupies the half-open rectangle
 *
 *     [cellLeft, cellLeft + cellWidth) × [cellTop, cellTop + cellHeight)
 *
 * and its **centre** — the anchor used by `Canvas.drawText` with
 * [android.graphics.Paint.Align.CENTER] — sits at
 * ([cellCenterX], [cellCenterY]), i.e. half a cell in from the cell's origin.
 *
 * This centre-inset is deliberate. An earlier version anchored cells at
 * `blockLeft + cellWidth * column`, which placed column 0's centre exactly on
 * the block's left edge: half of every Monday cell fell outside the block and
 * was unreachable by touch, while a full half-cell of dead space was left over
 * on the right. Keeping the mapping in one place makes that failure mode
 * impossible to reintroduce silently.
 *
 * All functions are pure integer arithmetic with no Android dependencies, so
 * they are directly unit-testable on the JVM.
 */
internal object MonthGridGeometry {

    /** Returned by [columnAt] / [rowAt] when a coordinate falls outside the grid. */
    const val NO_CELL = -1

    /** Number of day columns in a month block (one per weekday). */
    const val DAYS_PER_WEEK = 7

    /** Total rows per month block: 1 day-name header row + 6 day rows. */
    const val TOTAL_GRID_ROWS = 7

    /** Index of the day-name header row. */
    const val HEADER_ROW = 0

    /** Index of the first row containing day numbers. */
    const val FIRST_DAY_ROW = 1

    /** Number of months in a year — the upper bound on displayable blocks. */
    const val MAX_MONTHS = 12

    // ── Cell sizing ──────────────────────────────────────────────────

    /** Width of a single day cell, or `0` when the block has no usable width. */
    fun cellWidth(blockWidth: Int): Int =
        if (blockWidth <= 0) 0 else blockWidth / DAYS_PER_WEEK

    /** Height of a single grid row, or `0` when the block has no usable height. */
    fun cellHeight(contentHeight: Int): Int =
        if (contentHeight <= 0) 0 else contentHeight / TOTAL_GRID_ROWS

    // ── Cell → coordinates ───────────────────────────────────────────

    /** Left edge of the cell in [column]. */
    fun cellLeft(blockLeft: Int, cellWidth: Int, column: Int): Int =
        blockLeft + cellWidth * column

    /** Top edge of the cell in [row]. */
    fun cellTop(contentTop: Int, cellHeight: Int, row: Int): Int =
        contentTop + cellHeight * row

    /** Horizontal centre of the cell in [column] — the text draw anchor. */
    fun cellCenterX(blockLeft: Int, cellWidth: Int, column: Int): Int =
        blockLeft + cellWidth * column + cellWidth / 2

    /** Vertical centre of the cell in [row] — the text draw anchor. */
    fun cellCenterY(contentTop: Int, cellHeight: Int, row: Int): Int =
        contentTop + cellHeight * row + cellHeight / 2

    /** Bottom edge of the cell in [row]. */
    fun cellBottom(contentTop: Int, cellHeight: Int, row: Int): Int =
        contentTop + cellHeight * (row + 1)

    // ── Layout direction ─────────────────────────────────────────────

    /**
     * Maps a **logical** column — `0` is the first day of the week, whichever weekday
     * that is — to the column it occupies **on screen**.
     *
     * In a right-to-left layout the week must read from the right edge inwards, so
     * the columns are mirrored. Everything else in the calendar (day numbering, the
     * day-name header, weekend lookup) stays in logical order; only the final
     * placement flips.
     *
     * The mapping is an involution: `visualColumn(visualColumn(c, rtl), rtl) == c`.
     * Hit-testing relies on that — it recovers the logical column by applying the
     * very same function to the visual column it derived from the touch coordinate.
     */
    fun visualColumn(logicalColumn: Int, isRtl: Boolean): Int =
        if (isRtl) DAYS_PER_WEEK - 1 - logicalColumn else logicalColumn

    // ── Coordinates → cell ───────────────────────────────────────────

    /**
     * Returns the column containing [x], or [NO_CELL] if it falls outside the grid.
     * Exactly inverts [cellCenterX] for every point inside the cell.
     */
    fun columnAt(x: Int, blockLeft: Int, cellWidth: Int): Int {
        if (cellWidth <= 0 || x < blockLeft) return NO_CELL
        val column = (x - blockLeft) / cellWidth
        return if (column < DAYS_PER_WEEK) column else NO_CELL
    }

    /**
     * Returns the row containing [y], or [NO_CELL] if it falls outside the grid.
     * Exactly inverts [cellCenterY] for every point inside the cell.
     */
    fun rowAt(y: Int, contentTop: Int, cellHeight: Int): Int {
        if (cellHeight <= 0 || y < contentTop) return NO_CELL
        val row = (y - contentTop) / cellHeight
        return if (row < TOTAL_GRID_ROWS) row else NO_CELL
    }

    // ── Cell ↔ day-of-month ──────────────────────────────────────────

    /**
     * Maps a grid cell to a day of the month.
     *
     * @param firstDayOffset column index of day 1 (see `CachedMonthData.firstDayOffset`)
     * @return the day number, which may be `< 1` or `> daysInMonth` for padding
     *   cells; callers must range-check against the month length.
     */
    fun dayOfMonthAt(row: Int, column: Int, firstDayOffset: Int): Int =
        (row - FIRST_DAY_ROW) * DAYS_PER_WEEK + column + 1 - firstDayOffset

    /** Row containing [dayOfMonth]. Inverse of [dayOfMonthAt]. */
    fun rowOf(dayOfMonth: Int, firstDayOffset: Int): Int =
        FIRST_DAY_ROW + (dayOfMonth - 1 + firstDayOffset) / DAYS_PER_WEEK

    /** Column containing [dayOfMonth]. Inverse of [dayOfMonthAt]. */
    fun columnOf(dayOfMonth: Int, firstDayOffset: Int): Int =
        (dayOfMonth - 1 + firstDayOffset) % DAYS_PER_WEEK

    // ── View → month blocks ──────────────────────────────────────────

    /**
     * Number of month blocks a `rows × columns` grid can actually display.
     *
     * Grids larger than a year simply leave trailing slots empty; grids smaller
     * than a year can only show the first `rows × columns` months, and callers
     * are expected to warn about the truncation.
     */
    fun blockCountFor(rows: Int, columns: Int): Int =
        if (rows <= 0 || columns <= 0) 0 else (rows * columns).coerceAtMost(MAX_MONTHS)

    /**
     * Computes the bounds of the month block at grid position ([row], [column]).
     *
     * ## Gutter model
     *
     * The view is divided into `columns × rows` equal slots, and every slot is inset
     * by **half** the spacing on all four sides. The result is that the gap between
     * two adjacent blocks is exactly `horizontalSpacing` / `verticalSpacing`, the
     * outer margin is half that, and — crucially — every block has the same width
     * and the grid as a whole is symmetric about the view's centre.
     *
     * An earlier model added an empirical `horizontalSpacing * columns / 2`
     * "compensation" padding to the left edge only, and doubled the inset on the
     * outermost columns. That pushed the entire calendar to the right (with a
     * 3-column grid at 10dp spacing the left margin was 75px against a 30px right
     * margin) and gave the middle column a different width from its neighbours.
     * The skew grew linearly with the spacing, so it only became obvious once
     * spacing attributes moved from raw pixels to density-independent units.
     *
     * Writes `left, top, right, bottom` into [out] (length ≥ 4) instead of
     * allocating, so this can be called from layout passes without churn.
     */
    fun blockBoundsInto(
        out: IntArray,
        row: Int,
        column: Int,
        viewWidth: Int,
        viewHeight: Int,
        columns: Int,
        rows: Int,
        horizontalSpacing: Int,
        verticalSpacing: Int
    ) {
        val halfHorizontal = horizontalSpacing / 2
        val halfVertical = verticalSpacing / 2

        out[0] = column * viewWidth / columns + halfHorizontal
        out[1] = row * viewHeight / rows + halfVertical
        out[2] = (column + 1) * viewWidth / columns - halfHorizontal
        out[3] = (row + 1) * viewHeight / rows - halfVertical
    }

    /**
     * X origin of the day grid inside a month block of [blockWidth] pixels.
     *
     * [cellWidth] floors `blockWidth / 7`, discarding up to six pixels. Anchoring the
     * grid at the block's left edge would dump all of that slack on the right and
     * leave the seven columns visibly off-centre inside their own block; splitting it
     * evenly keeps them centred.
     *
     * Draw and hit-test must both derive their origin from here, otherwise the
     * rendered cell and the touch target drift apart by up to six pixels.
     */
    fun gridOriginX(blockLeft: Int, blockWidth: Int): Int {
        if (blockWidth <= 0) return blockLeft
        val slack = blockWidth - cellWidth(blockWidth) * DAYS_PER_WEEK
        return blockLeft + slack / 2
    }
}
