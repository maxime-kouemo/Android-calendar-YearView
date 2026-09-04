package com.mamboa.yearview.legacy

import com.mamboa.yearview.legacy.MonthGridGeometry.DAYS_PER_WEEK
import com.mamboa.yearview.legacy.MonthGridGeometry.FIRST_DAY_ROW
import com.mamboa.yearview.legacy.MonthGridGeometry.NO_CELL
import com.mamboa.yearview.legacy.MonthGridGeometry.TOTAL_GRID_ROWS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry contract for the month grid.
 *
 * The central guarantee is the **round-trip**: the point a cell is drawn at must
 * hit-test back to that same cell. A regression here is invisible on screen but
 * silently shifts every touch target, so it is asserted exhaustively rather than
 * by sampling.
 */
class MonthGridGeometryTest {

    private val blockLeft = 40
    private val blockWidth = 210          // 30px per column
    private val contentTop = 100
    private val contentHeight = 280       // 40px per row

    private val cellWidth = MonthGridGeometry.cellWidth(blockWidth)
    private val cellHeight = MonthGridGeometry.cellHeight(contentHeight)

    // ── Round-trip: draw anchor → hit test ───────────────────────────

    @Test
    fun `every cell centre hit-tests back to its own cell`() {
        for (row in 0 until TOTAL_GRID_ROWS) {
            for (column in 0 until DAYS_PER_WEEK) {
                val x = MonthGridGeometry.cellCenterX(blockLeft, cellWidth, column)
                val y = MonthGridGeometry.cellCenterY(contentTop, cellHeight, row)

                assertEquals(
                    "column round-trip failed at ($row,$column)",
                    column,
                    MonthGridGeometry.columnAt(x, blockLeft, cellWidth)
                )
                assertEquals(
                    "row round-trip failed at ($row,$column)",
                    row,
                    MonthGridGeometry.rowAt(y, contentTop, cellHeight)
                )
            }
        }
    }

    @Test
    fun `every point inside a cell maps to that cell`() {
        for (column in 0 until DAYS_PER_WEEK) {
            val left = MonthGridGeometry.cellLeft(blockLeft, cellWidth, column)
            for (offset in 0 until cellWidth) {
                assertEquals(
                    "x=${left + offset} should belong to column $column",
                    column,
                    MonthGridGeometry.columnAt(left + offset, blockLeft, cellWidth)
                )
            }
        }
    }

    /**
     * Regression guard for the half-cell offset bug: the left half of column 0
     * used to fall outside the block entirely and was unreachable by touch.
     */
    @Test
    fun `column zero is fully contained within the block`() {
        val firstCellLeft = MonthGridGeometry.cellLeft(blockLeft, cellWidth, 0)
        assertEquals(blockLeft, firstCellLeft)
        assertEquals(0, MonthGridGeometry.columnAt(blockLeft, blockLeft, cellWidth))
    }

    /** The grid must consume the block rather than leaving a half-cell gutter. */
    @Test
    fun `last column ends within one rounding step of the block edge`() {
        val lastCellRight = MonthGridGeometry.cellLeft(blockLeft, cellWidth, DAYS_PER_WEEK)
        val blockRight = blockLeft + blockWidth
        assertTrue(
            "grid right edge $lastCellRight should be within $DAYS_PER_WEEK px of $blockRight",
            blockRight - lastCellRight < DAYS_PER_WEEK
        )
    }

    // ── Out-of-range coordinates ─────────────────────────────────────

    @Test
    fun `coordinates outside the grid yield NO_CELL`() {
        assertEquals(NO_CELL, MonthGridGeometry.columnAt(blockLeft - 1, blockLeft, cellWidth))
        assertEquals(
            NO_CELL,
            MonthGridGeometry.columnAt(blockLeft + cellWidth * DAYS_PER_WEEK, blockLeft, cellWidth)
        )
        assertEquals(NO_CELL, MonthGridGeometry.rowAt(contentTop - 1, contentTop, cellHeight))
        assertEquals(
            NO_CELL,
            MonthGridGeometry.rowAt(contentTop + cellHeight * TOTAL_GRID_ROWS, contentTop, cellHeight)
        )
    }

    @Test
    fun `degenerate cell sizes yield NO_CELL instead of dividing by zero`() {
        assertEquals(NO_CELL, MonthGridGeometry.columnAt(50, 0, 0))
        assertEquals(NO_CELL, MonthGridGeometry.rowAt(50, 0, 0))
        assertEquals(0, MonthGridGeometry.cellWidth(0))
        assertEquals(0, MonthGridGeometry.cellWidth(-10))
        assertEquals(0, MonthGridGeometry.cellHeight(0))
        assertEquals(0, MonthGridGeometry.cellHeight(-10))
    }

    // ── Cell ↔ day-of-month ──────────────────────────────────────────

    @Test
    fun `day of month round-trips through row and column for every offset`() {
        for (firstDayOffset in 0 until DAYS_PER_WEEK) {
            for (day in 1..31) {
                val row = MonthGridGeometry.rowOf(day, firstDayOffset)
                val column = MonthGridGeometry.columnOf(day, firstDayOffset)
                assertEquals(
                    "day $day with offset $firstDayOffset",
                    day,
                    MonthGridGeometry.dayOfMonthAt(row, column, firstDayOffset)
                )
            }
        }
    }

    @Test
    fun `day one sits at the configured first-day offset`() {
        for (firstDayOffset in 0 until DAYS_PER_WEEK) {
            assertEquals(FIRST_DAY_ROW, MonthGridGeometry.rowOf(1, firstDayOffset))
            assertEquals(firstDayOffset, MonthGridGeometry.columnOf(1, firstDayOffset))
        }
    }

    /** Worst case: 31 days starting on the last column still fits the 6 day rows. */
    @Test
    fun `longest month with maximum offset fits inside the grid`() {
        val lastRow = MonthGridGeometry.rowOf(31, DAYS_PER_WEEK - 1)
        assertTrue("row $lastRow overflows the grid", lastRow < TOTAL_GRID_ROWS)
    }

    @Test
    fun `padding cells before day one are reported as non-positive`() {
        val firstDayOffset = 3
        for (column in 0 until firstDayOffset) {
            val day = MonthGridGeometry.dayOfMonthAt(FIRST_DAY_ROW, column, firstDayOffset)
            assertTrue("padding cell $column should be < 1 but was $day", day < 1)
        }
    }

    // ── Block splitting ──────────────────────────────────────────────

    @Test
    fun `block count is clamped to a year`() {
        assertEquals(12, MonthGridGeometry.blockCountFor(rows = 6, columns = 2))
        assertEquals(12, MonthGridGeometry.blockCountFor(rows = 3, columns = 4))
        // Larger-than-a-year grids keep all 12 months and leave trailing slots empty.
        assertEquals(12, MonthGridGeometry.blockCountFor(rows = 5, columns = 3))
        // Smaller grids can only show what fits.
        assertEquals(4, MonthGridGeometry.blockCountFor(rows = 2, columns = 2))
        assertEquals(0, MonthGridGeometry.blockCountFor(rows = 0, columns = 5))
    }

    @Test
    fun `blocks never overlap and stay inside the view`() {
        val viewWidth = 1080
        val viewHeight = 1920
        val configurations = listOf(6 to 2, 4 to 3, 3 to 4, 2 to 6, 12 to 1)

        for ((rows, columns) in configurations) {
            val blocks = splitBlocks(viewWidth, viewHeight, rows, columns, 10, 10)

            for ((index, block) in blocks.withIndex()) {
                assertTrue(
                    "$rows x $columns block $index has non-positive width: $block",
                    block.right > block.left
                )
                assertTrue(
                    "$rows x $columns block $index has non-positive height: $block",
                    block.bottom > block.top
                )
                assertTrue(
                    "$rows x $columns block $index escapes the view: $block",
                    block.left >= 0 && block.top >= 0 &&
                        block.right <= viewWidth && block.bottom <= viewHeight
                )
            }

            for (i in blocks.indices) {
                for (j in i + 1 until blocks.size) {
                    assertTrue(
                        "$rows x $columns blocks $i and $j overlap: ${blocks[i]} / ${blocks[j]}",
                        !blocks[i].intersects(blocks[j])
                    )
                }
            }
        }
    }

    @Test
    fun `blocks in the same grid row share vertical bounds`() {
        val blocks = splitBlocks(1080, 1920, rows = 4, columns = 3, 8, 8)
        for (row in 0 until 4) {
            val rowBlocks = (0 until 3).map { blocks[row * 3 + it] }
            val top = rowBlocks.first().top
            val bottom = rowBlocks.first().bottom
            rowBlocks.forEach {
                assertEquals("top mismatch in row $row", top, it.top)
                assertEquals("bottom mismatch in row $row", bottom, it.bottom)
            }
        }
    }

    /**
     * The whole grid must be symmetric about the view's centre.
     *
     * Regression guard for the "compensation padding" model, which added
     * `horizontalSpacing * columns / 2` to every block's **left** edge only. At 3
     * columns and 30px spacing that produced a 75px left margin against a 30px right
     * margin, visibly sliding the calendar off-centre — and the skew grew with the
     * spacing, so it went unnoticed while spacing was measured in raw pixels.
     */
    @Test
    fun `grid is horizontally centred in the view`() {
        val viewWidth = 1080
        for (columns in 1..4) {
            for (spacing in listOf(0, 10, 30, 64)) {
                val blocks = splitBlocks(viewWidth, 1920, rows = 3, columns = columns, spacing, spacing)
                if (blocks.isEmpty()) continue
                val leftMargin = blocks.first().left
                val rightMargin = viewWidth - blocks.take(columns).last().right
                assertEquals(
                    "columns=$columns spacing=$spacing left margin $leftMargin " +
                        "!= right margin $rightMargin",
                    leftMargin,
                    rightMargin
                )
            }
        }
    }

    /**
     * Every column must be the same width. The old model inset the outermost columns
     * by twice the spacing, so a 3-column grid produced widths of 270/285/270.
     */
    @Test
    fun `blocks in the same grid row have equal widths`() {
        for (spacing in listOf(0, 10, 30, 64)) {
            val blocks = splitBlocks(1080, 1920, rows = 4, columns = 3, spacing, spacing)
            val widths = (0 until 3).map { blocks[it].right - blocks[it].left }
            assertEquals(
                "spacing=$spacing produced unequal column widths: $widths",
                1,
                widths.distinct().size
            )
        }
    }

    /** The gap between adjacent blocks is the configured spacing, no more, no less. */
    @Test
    fun `gap between adjacent blocks equals the configured spacing`() {
        val spacing = 30
        val blocks = splitBlocks(1080, 1920, rows = 4, columns = 3, spacing, spacing)
        for (column in 0 until 2) {
            assertEquals(spacing, blocks[column + 1].left - blocks[column].right)
        }
    }

    // ── Grid origin ──────────────────────────────────────────────────

    /**
     * `cellWidth` floors `blockWidth / 7`, so up to six pixels are unused. They must be
     * split between both sides rather than all dumped on the right, otherwise the seven
     * day columns sit off-centre inside their own block.
     */
    @Test
    fun `gridOriginX centres the day columns inside the block`() {
        for (blockWidth in 100..140) {
            val blockLeft = 37
            val origin = MonthGridGeometry.gridOriginX(blockLeft, blockWidth)
            val cellWidth = MonthGridGeometry.cellWidth(blockWidth)
            val leftSlack = origin - blockLeft
            val rightSlack = (blockLeft + blockWidth) - (origin + cellWidth * DAYS_PER_WEEK)
            assertTrue(
                "width=$blockWidth left slack $leftSlack vs right slack $rightSlack",
                kotlin.math.abs(leftSlack - rightSlack) <= 1
            )
            assertTrue("grid escapes the block at width=$blockWidth", leftSlack >= 0 && rightSlack >= 0)
        }
    }

    /** Draw anchor and hit-test must agree when both derive from [MonthGridGeometry.gridOriginX]. */
    @Test
    fun `cell centre round-trips through the centred grid origin`() {
        val blockLeft = 75
        val blockWidth = 333
        val origin = MonthGridGeometry.gridOriginX(blockLeft, blockWidth)
        val cellWidth = MonthGridGeometry.cellWidth(blockWidth)
        for (column in 0 until DAYS_PER_WEEK) {
            val x = MonthGridGeometry.cellCenterX(origin, cellWidth, column)
            assertEquals(column, MonthGridGeometry.columnAt(x, origin, cellWidth))
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun intersects(other: Bounds): Boolean =
            left < other.right && other.left < right &&
                top < other.bottom && other.top < bottom
    }

    private fun splitBlocks(
        viewWidth: Int,
        viewHeight: Int,
        rows: Int,
        columns: Int,
        horizontalSpacing: Int,
        verticalSpacing: Int
    ): List<Bounds> {
        val out = IntArray(4)
        val result = mutableListOf<Bounds>()
        val count = MonthGridGeometry.blockCountFor(rows, columns)
        var k = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if (k >= count) return result
                MonthGridGeometry.blockBoundsInto(
                    out, row, column, viewWidth, viewHeight,
                    columns, rows, horizontalSpacing, verticalSpacing
                )
                result += Bounds(out[0], out[1], out[2], out[3])
                k++
            }
        }
        return result
    }
}
