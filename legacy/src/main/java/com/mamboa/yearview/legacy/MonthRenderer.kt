package com.mamboa.yearview.legacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.TitleGravity
import com.mamboa.yearview.core.utils.Utils
import com.mamboa.yearview.core.imageprovider.ResourceImageProvider
import com.mamboa.yearview.core.pathprovider.ResourcePathProvider
import com.mamboa.yearview.legacy.imageprovider.BitmapImageProvider
import com.mamboa.yearview.legacy.imageprovider.DrawableImageProvider
import com.mamboa.yearview.legacy.pathprovider.XmlPathProvider
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Handles all Canvas drawing for the year-view calendar.
 *
 * Extracted from [YearView] to separate rendering concerns from view
 * lifecycle, gesture handling, and state management. All methods must
 * be called on the UI thread only — pooled temp objects are reused
 * across draw calls.
 */
internal class MonthRenderer(private val context: Context) {

    /**
     * Display density, used to convert [BackgroundShape.RoundedSquare.cornerRadius]
     * from the density-independent pixels it is documented to carry into the raw
     * pixels [Canvas] expects. It was previously used unscaled, so the same corner
     * radius looked twice as round on an mdpi device as on an xhdpi one.
     */
    private val density: Float = context.resources.displayMetrics.density

    // ========== Caches & Pooled Objects ==========
    // IMPORTANT: All temp* fields below are shared mutable objects reused across draw calls
    // to avoid per-frame allocations. They are safe ONLY because onDraw runs on the UI thread.

    /** Reusable Rect for temporary text measurement. */
    private val tempTextBounds = Rect()
    /** Reusable RectF for draw methods. */
    private val tempRectF = RectF()
    /** Reusable Paint for styled-background drawing. */
    private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Reusable Path for shape building. */
    private val tempShapePath = Path()
    /** Reusable Matrix for path transformations. */
    private val tempMatrix = Matrix()
    /** Reusable Path for scaled/translated path output. */
    private val tempScaledPath = Path()
    /** Reusable RectF for path bounds computation. */
    private val tempPathBounds = RectF()
    /** Reusable RectF for padded bounds computation. */
    private val tempPaddedBounds = RectF()
    /** Reusable RectF holding a vector drawable's viewport as a source rectangle. */
    private val tempViewportBounds = RectF()
    /** Reusable Path for star shape building. */
    private val tempStarPath = Path()

    /**
     * Cache for paths extracted from vector drawable resources.
     *
     * Failures are cached as [VectorPath.UNPARSEABLE] as well as successes: parsing walks
     * the whole XML document, and without a negative entry a malformed drawable would be
     * re-parsed on every frame.
     */
    private val drawablePathCache = mutableMapOf<Int, VectorPath>()

    /**
     * Cache for normalized (unit-radius, origin-centered) star paths, keyed by
     * (numPoints, innerRadiusRatio). Avoids recalculating trig on every frame.
     */
    private val starPathCache = mutableMapOf<Long, Path>()

    /**
     * LRU cache for resolved bitmaps to avoid decoding during onDraw.
     */
    private val bitmapCache = object : LinkedHashMap<ImageSource, Bitmap?>(
        MAX_BITMAP_CACHE_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ImageSource, Bitmap?>?): Boolean {
            return size > MAX_BITMAP_CACHE_SIZE
        }
    }

    /**
     * Per-month Y position of the **bottom edge** of the last drawn day row, used to size
     * the month-selection highlight.
     *
     * This is deliberately the row's bottom rather than its centre: using the centre
     * clipped the final row of day numbers in half inside the highlight.
     */
    val lastRowBottomInMonth = IntArray(MonthGridGeometry.MAX_MONTHS)

    fun clearBitmapCache() {
        bitmapCache.clear()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public draw entry points (called from YearView.onDraw)
    // ════════════════════════════════════════════════════════════��══════

    /**
     * Draws every month that has a positioned block on [canvas].
     *
     * Iterates [DrawContext.visibleMonthCount] rather than a hard-coded 12: when the
     * configured grid is smaller than a year the surplus months have no block, and
     * drawing them would paint garbage into an empty rectangle at the origin.
     */
    fun drawMonths(canvas: Canvas, ctx: DrawContext) {
        lastRowBottomInMonth.fill(0)
        for (i in 0 until ctx.visibleMonthCount) {
            val md = ctx.layout[i]
            drawAMonth(canvas, i, md.firstDayOffset, md.daysInMonth, md.monthName, ctx)
        }
    }

    /**
     * Draws the month-selection highlight overlay.
     */
    fun drawSelection(
        canvas: Canvas,
        selectedMonthID: Int,
        monthBlocks: Array<Rect>,
        monthConfig: MonthConfig
    ) {
        if (selectedMonthID <= -1) return
        val block = monthBlocks[selectedMonthID]
        val selectionMargin = monthConfig.selectionBackgroundItemStyle.selectionMargin
        // Symmetric around the block. The old version subtracted `horizontalSpacing`
        // from both edges, sliding the whole highlight left off its own month.
        tempRectF.set(
            block.left - selectionMargin,
            block.top - selectionMargin,
            block.right + selectionMargin,
            lastRowBottomInMonth[selectedMonthID] + selectionMargin
        )
        drawStyledBackground(canvas, tempRectF, monthConfig.selectionBackgroundItemStyle)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Internal drawing helpers
    // ��══════════════════════════════════════════════════════════════════

    private fun drawAMonth(
        canvas: Canvas,
        month: Int,
        firstDay: Int,
        daysInMonth: Int,
        monthName: String,
        ctx: DrawContext
    ) {
        drawMonthName(canvas, month, monthName, ctx)

        val block = ctx.monthBlocks[month]
        val contentTop = block.top + ctx.layoutComputer.monthTitleOffsets[month]
        val contentHeight = block.bottom - contentTop

        // Draw month background if provided
        if (ctx.monthConfig.backgroundItemStyle.color != Color.TRANSPARENT || ctx.monthConfig.backgroundItemStyle.image !is ImageSource.None) {
            val bgStyle = ctx.monthConfig.backgroundItemStyle
            tempRectF.set(
                block.left - bgStyle.selectionMargin,
                contentTop - bgStyle.selectionMargin,
                block.right + bgStyle.selectionMargin,
                block.bottom + bgStyle.selectionMargin
            )
            drawStyledBackground(canvas, tempRectF, bgStyle)
        }

        val xUnit = MonthGridGeometry.cellWidth(block.width())
        val yUnit = MonthGridGeometry.cellHeight(contentHeight)
        // Not block.left: integer division drops up to six pixels off the seven
        // columns, and gridOriginX splits that slack evenly so the grid sits centred
        // in its block. YearView.getClickedDay derives its origin the same way.
        val gridLeft = MonthGridGeometry.gridOriginX(block.left, block.width())
        val isRtl = ctx.isRtl

        // Compute the vertical baseline offset to center text within each cell.
        // drawText positions the text baseline at yValue; to visually center the text
        // we shift by half the font height: -(ascent + descent) / 2.
        val dayFm = ctx.paints.simpleDayNumber.fontMetrics
        val dayCenterOffset = -(dayFm.ascent + dayFm.descent) / 2f
        val headerFm = ctx.paints.dayName.fontMetrics
        val headerCenterOffset = -(headerFm.ascent + headerFm.descent) / 2f

        var dayOfMonth = 1 - firstDay
        for (y in 0 until MonthGridGeometry.TOTAL_GRID_ROWS) {
            for (x in 0 until MonthGridGeometry.DAYS_PER_WEEK) {
                // Anchor on the cell's centre, not its top-left corner. Both paints use
                // Align.CENTER, so anchoring at the corner pushed the whole grid half a
                // cell up and to the left: half of column 0 fell outside the block and
                // could never be touched, while a half-cell gutter was wasted on the right.
                // MonthGridGeometry is shared with YearView.getClickedDay so that the drawn
                // position and the hit-tested position cannot drift apart.
                //
                // `x` is the logical column (0 = first day of the week); in an RTL
                // layout the week reads from the right, so only the *placement* is
                // mirrored — the day numbering and header lookup stay logical.
                val xValue = MonthGridGeometry.cellCenterX(
                    gridLeft, xUnit, MonthGridGeometry.visualColumn(x, isRtl)
                )
                val yAnchor = MonthGridGeometry.cellCenterY(contentTop, yUnit, y)

                if (y == MonthGridGeometry.HEADER_ROW) {
                    val yValue = yAnchor + headerCenterOffset.toInt()
                    drawDayHeader(canvas, x, xValue, yValue, ctx)
                } else {
                    if (dayOfMonth in 1..daysInMonth) {
                        val yValue = yAnchor + dayCenterOffset.toInt()
                        drawDay(canvas, month, dayOfMonth, xValue, yValue, ctx)
                        lastRowBottomInMonth[month] =
                            MonthGridGeometry.cellBottom(contentTop, yUnit, y)
                    }
                    dayOfMonth++
                }
            }
        }
    }

    private fun drawMonthName(
        canvas: Canvas,
        index: Int,
        monthName: String,
        ctx: DrawContext
    ) {
        val isTodayMonth = ctx.currentYear == ctx.layoutComputer.cachedTodayYear && (index + 1) == ctx.layoutComputer.cachedTodayMonth
        val paint = if (isTodayMonth) ctx.paints.todayMonthName else ctx.paints.monthName
        val block = ctx.monthBlocks[index]

        paint.getTextBounds(monthName, 0, monthName.length, tempTextBounds)

        val yValue = block.top + tempTextBounds.height()
        val width = tempTextBounds.width()

        val isRtl = ctx.isRtl
        val resolvedGravity = when (ctx.monthConfig.titleGravity) {
            TitleGravity.START -> if (isRtl) TitleGravity.RIGHT else TitleGravity.LEFT
            TitleGravity.END -> if (isRtl) TitleGravity.LEFT else TitleGravity.RIGHT
            else -> ctx.monthConfig.titleGravity
        }
        // The paint uses Align.CENTER, so these are centre anchors: the text spans
        // [xStart - width/2, xStart + width/2].
        //
        // No `horizontalSpacing` term here. The previous code subtracted one, two or
        // half a spacing depending on gravity, which was compensating for a month-block
        // model that was itself skewed to the right. With symmetric blocks that
        // correction becomes the bug — it detached the title from the grid beneath it.
        val xStart = when (resolvedGravity) {
            TitleGravity.LEFT -> block.left + width / 2
            TitleGravity.RIGHT -> block.right - width / 2
            else -> (block.left + block.right) / 2
        }

        canvas.drawText(monthName, xStart.toFloat(), yValue.toFloat(), paint)
    }

    private fun drawDayHeader(
        canvas: Canvas,
        x: Int,
        xValue: Int,
        yValue: Int,
        ctx: DrawContext
    ) {
        val dayIndex = CalendarLayoutComputer.getDayIndex(x, ctx.firstDayOfWeek)
        val dayName = ctx.cachedDayHeaderNames?.get(x) ?: dayIndex.toString()

        val paint = if (ctx.weekendDays?.contains(dayIndex) == true && !ctx.dayNameConfig.transcendsWeekend) {
            ctx.paints.weekendDay
        } else {
            ctx.paints.dayName
        }
        canvas.drawText(dayName, xValue.toFloat(), yValue.toFloat(), paint)
    }

    private fun drawDay(
        canvas: Canvas,
        month: Int,
        dayOfMonth: Int,
        xValue: Int,
        yValue: Int,
        ctx: DrawContext
    ) {
        val isWeekEnd = isCachedWeekend(month, dayOfMonth, ctx)

        if (ctx.selectionManager.isDayInDragRange(ctx.currentYear, month, dayOfMonth)
            || ctx.selectionManager.isDayInRange(ctx.currentYear, month, dayOfMonth)) {
            drawMultiSelectionBackground(canvas, dayOfMonth, xValue, yValue, ctx.paints.simpleDayNumber, ctx.multiSelectionBackgroundItemStyle)
        }

        if (ctx.selectionManager.isSelectedDay(ctx.currentYear, month, dayOfMonth)) {
            drawDayWithShape(canvas, dayOfMonth, ctx.selectedDayConfig, ctx.paints.selectedDayText, ctx.paints.selectedDayBackground, xValue, yValue)
        } else if (isToday(month, dayOfMonth, ctx)) {
            drawDayWithShape(canvas, dayOfMonth, ctx.todayConfig, ctx.paints.todayText, ctx.paints.todayBackground, xValue, yValue)
        } else {
            val dayText = dayOfMonth.toString()
            val paint = if (isWeekEnd) ctx.paints.weekendDay else ctx.paints.simpleDayNumber
            canvas.drawText(dayText, xValue.toFloat(), yValue.toFloat(), paint)
        }
    }

    private fun isToday(month: Int, dayOfMonth: Int, ctx: DrawContext): Boolean {
        return ctx.currentYear == ctx.layoutComputer.cachedTodayYear && (month + 1) == ctx.layoutComputer.cachedTodayMonth && dayOfMonth == ctx.layoutComputer.cachedTodayDay
    }

    private fun isCachedWeekend(month: Int, dayOfMonth: Int, ctx: DrawContext): Boolean {
        val dow = ctx.layoutComputer.cachedMonthData?.get(month)?.dayOfWeek?.get(dayOfMonth - 1) ?: return false
        return ctx.weekendDays?.contains(dow) == true
    }

    // ═══════════════════════════════════════════════════════════════════
    // Shape drawing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Draws a day number with the background shape configured on [config].
     *
     * Every branch is sized from the same `margin`, derived from
     * [DayConfig.backgroundRadius]. Previously only the square shapes honoured it:
     * circles were sized from `Circle.radius` (a *unitless multiplier*, passed where a
     * pixel margin was expected) and stars received the margin where the callee
     * expected a radius — with the default `backgroundRadius` of `1f` that margin is
     * `0`, so star backgrounds were drawn at zero size and were simply invisible.
     *
     * [BackgroundShape.Circle.radius] is now applied as what it is documented to be: a
     * multiplier on the resulting radius.
     */
    private fun drawDayWithShape(
        canvas: Canvas, dayOfMonth: Int, config: DayConfig,
        textPaint: Paint, backgroundPaint: Paint, xValue: Int, yValue: Int
    ) {
        val style = config.backgroundItemStyle
        val margin = DayConfig.multiplierToPixelMargin(config.backgroundRadius, config.textSize)
        when (val shape = style.shape) {
            is BackgroundShape.Circle -> drawCircleAroundText(
                canvas, dayOfMonth, textPaint, backgroundPaint, xValue, yValue, margin,
                shape.radius, style
            )
            is BackgroundShape.RoundedSquare -> drawRoundedSquareAroundText(
                canvas, dayOfMonth, textPaint, backgroundPaint, xValue, yValue, margin, shape.cornerRadius, style
            )
            is BackgroundShape.Star -> drawStarAroundText(
                canvas, dayOfMonth, textPaint, backgroundPaint, xValue, yValue, margin, shape, style
            )
            else -> drawSquareAroundText(canvas, textPaint, backgroundPaint, dayOfMonth, xValue, yValue, margin, style)
        }
    }

    /**
     * Radius of a shape enclosing the day text plus [margin] on every side.
     *
     * Shared by the circle and star branches so the two cannot drift apart.
     * [tempTextBounds] must already hold the measured text.
     */
    private fun enclosingRadius(margin: Int): Int =
        maxOf(tempTextBounds.width(), tempTextBounds.height()) / 2 + margin

    private fun drawSquareAroundText(
        canvas: Canvas, textPaint: Paint, backgroundPaint: Paint,
        dayOfMonth: Int, xValue: Int, yValue: Int, margin: Int, style: LegacyBackgroundStyle? = null
    ) {
        val dayText = dayOfMonth.toString()
        textPaint.getTextBounds(dayText, 0, dayText.length, tempTextBounds)
        tempRectF.set(
            (xValue - (tempTextBounds.width() / 2) - margin).toFloat(),
            (yValue - tempTextBounds.height() - margin).toFloat(),
            (xValue + (tempTextBounds.width() / 2) + margin).toFloat(),
            (yValue + margin).toFloat()
        )
        if (style != null && style.image !is ImageSource.None) {
            drawStyledBackground(canvas, tempRectF, style)
        } else {
            canvas.drawRect(tempRectF, backgroundPaint)
        }
        canvas.drawText(dayText, xValue.toFloat(), yValue.toFloat(), textPaint)
    }

    private fun drawCircleAroundText(
        canvas: Canvas, dayOfMonth: Int, textPaint: Paint, backgroundPaint: Paint,
        xValue: Int, yValue: Int, margin: Int, radiusMultiplier: Float = 1f,
        style: LegacyBackgroundStyle? = null
    ) {
        val dayText = dayOfMonth.toString()
        textPaint.getTextBounds(dayText, 0, dayText.length, tempTextBounds)
        val fm = textPaint.fontMetrics
        val diffAscDesc = abs(fm.ascent + fm.descent).toInt()
        val centerY = yValue - diffAscDesc / 2
        val radius = (enclosingRadius(margin) * radiusMultiplier).toInt()

        if (style != null && style.image !is ImageSource.None) {
            tempRectF.set((xValue - radius).toFloat(), (centerY - radius).toFloat(), (xValue + radius).toFloat(), (centerY + radius).toFloat())
            drawStyledBackground(canvas, tempRectF, style)
        } else {
            canvas.drawCircle(xValue.toFloat(), centerY.toFloat(), radius.toFloat(), backgroundPaint)
        }
        canvas.drawText(dayText, xValue.toFloat(), yValue.toFloat(), textPaint)
    }

    private fun drawRoundedSquareAroundText(
        canvas: Canvas, dayOfMonth: Int, textPaint: Paint, backgroundPaint: Paint,
        xValue: Int, yValue: Int, margin: Int, roundedRadius: Float, style: LegacyBackgroundStyle? = null
    ) {
        val dayText = dayOfMonth.toString()
        textPaint.getTextBounds(dayText, 0, dayText.length, tempTextBounds)
        tempRectF.set(
            (xValue - (tempTextBounds.width() / 2) - margin).toFloat(),
            (yValue - tempTextBounds.height() - margin).toFloat(),
            (xValue + (tempTextBounds.width() / 2) + margin).toFloat(),
            (yValue + margin).toFloat()
        )
        if (style != null && style.image !is ImageSource.None) {
            drawStyledBackground(canvas, tempRectF, style)
        } else {
            // cornerRadius is expressed in dp — see [density].
            val cornerPx = roundedRadius * density
            canvas.drawRoundRect(tempRectF, cornerPx, cornerPx, backgroundPaint)
        }
        canvas.drawText(dayText, xValue.toFloat(), yValue.toFloat(), textPaint)
    }

    /**
     * Draws a star background sized to enclose the day text plus [margin].
     *
     * A star must fully contain the text, so it is sized from the enclosing radius —
     * the same input the circle branch uses — rather than from the bare margin, which
     * collapsed to zero at the default [DayConfig.backgroundRadius].
     */
    private fun drawStarAroundText(
        canvas: Canvas, dayOfMonth: Int, textPaint: Paint, backgroundPaint: Paint,
        xValue: Int, yValue: Int, margin: Int, starShape: BackgroundShape.Star,
        style: LegacyBackgroundStyle? = null
    ) {
        val dayText = dayOfMonth.toString()
        textPaint.getTextBounds(dayText, 0, dayText.length, tempTextBounds)
        val fm = textPaint.fontMetrics
        val diffAscDesc = abs(fm.ascent + fm.descent).toInt()
        val centerY = yValue - diffAscDesc / 2
        // A star's inner vertices sit well inside its bounding box, so scale the
        // enclosing radius up to keep the digits within the filled area.
        val radius = (enclosingRadius(margin) * STAR_ENCLOSING_RADIUS_SCALE).toInt()
        tempRectF.set((xValue - radius).toFloat(), (centerY - radius).toFloat(), (xValue + radius).toFloat(), (centerY + radius).toFloat())
        val starStyle = LegacyBackgroundStyle(
            color = backgroundPaint.color,
            shape = starShape,
            selectionMargin = 0f,
            image = style?.image ?: ImageSource.None,
            colorOpacity = style?.colorOpacity ?: 100,
            imageOpacity = style?.imageOpacity ?: 100,
            mergeType = style?.mergeType ?: MergeType.OVERLAY
        )
        drawStyledBackground(canvas, tempRectF, starStyle)
        canvas.drawText(dayText, xValue.toFloat(), yValue.toFloat(), textPaint)
    }

    /**
     * Draws the range-selection background behind a day number.
     *
     * The inset comes from [LegacyBackgroundStyle.selectionMargin], which the
     * `yv_multi_selection_background_radius` attribute feeds. It was previously a
     * hard-coded 2 px, leaving that attribute declared but never read.
     */
    private fun drawMultiSelectionBackground(
        canvas: Canvas, dayOfMonth: Int, xValue: Int, yValue: Int,
        simpleDayNumberPaint: Paint, style: LegacyBackgroundStyle
    ) {
        val dayText = dayOfMonth.toString()
        simpleDayNumberPaint.getTextBounds(dayText, 0, dayText.length, tempTextBounds)
        val fm = simpleDayNumberPaint.fontMetrics
        val diffAscDesc = abs(fm.ascent + fm.descent).toInt()
        val margin = style.selectionMargin
        tempRectF.set(
            xValue - tempTextBounds.width() / 2f - margin,
            yValue - tempTextBounds.height() - diffAscDesc / 2f - margin,
            xValue + tempTextBounds.width() / 2f + margin,
            yValue + margin
        )
        drawStyledBackground(canvas, tempRectF, style)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Styled background (shape + image compositing)
    // ═══════════════════════════════════════════════════════════════════

    private fun drawStyledBackground(canvas: Canvas, bounds: RectF, style: LegacyBackgroundStyle) {
        when (style.mergeType) {
            MergeType.CLIP -> drawStyledBackgroundInnerClip(canvas, bounds, style)
            else -> drawStyledBackgroundMerge(canvas, bounds, style)
        }
    }

    private fun drawStyledBackgroundMerge(canvas: Canvas, bounds: RectF, style: LegacyBackgroundStyle) {
        if (style.image !is ImageSource.None) {
            resolveBitmap(style.image)?.let {
                tempPaint.reset()
                tempPaint.isAntiAlias = true
                tempPaint.alpha = imageAlphaOf(style)
                canvas.drawBitmap(it, null, bounds, tempPaint)
            }
        }
        val colorAlpha = colorAlphaOf(style)
        if (colorAlpha == 0) return
        val shapePath = buildShapePath(bounds, style.shape)
        tempPaint.reset()
        tempPaint.isAntiAlias = true
        tempPaint.color = ColorUtils.setAlphaComponent(style.color, colorAlpha)
        canvas.drawPath(shapePath, tempPaint)
    }

    /**
     * Draws a styled background using shape clipping via [Canvas.saveLayer].
     *
     * **Performance note:** [Canvas.saveLayer] allocates an off-screen bitmap buffer,
     * which is significantly more expensive than direct drawing. Avoid using
     * [MergeType.CLIP] for per-day-cell backgrounds (up to 366 calls per frame).
     * Prefer [MergeType.OVERLAY] for day-level styling and reserve [MergeType.CLIP]
     * for month-level backgrounds where the cost is bounded to 12 calls.
     */
    private fun drawStyledBackgroundInnerClip(canvas: Canvas, bounds: RectF, style: LegacyBackgroundStyle) {
        val clipPath = buildShapePath(bounds, style.shape)
        val hasImage = style.image !is ImageSource.None

        if (hasImage) {
            val saveCount = canvas.saveLayer(bounds, null)
            tempPaint.reset()
            tempPaint.isAntiAlias = true
            tempPaint.color = Color.WHITE
            canvas.drawPath(clipPath, tempPaint)

            resolveBitmap(style.image)?.let {
                tempPaint.reset()
                tempPaint.isAntiAlias = true
                tempPaint.alpha = imageAlphaOf(style)
                tempPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(it, null, bounds, tempPaint)
                tempPaint.xfermode = null
            }

            drawColorLayer(canvas, clipPath, style)
            canvas.restoreToCount(saveCount)
        } else {
            // No clip/save needed: with no image to mask, the colour layer just fills
            // `clipPath` itself, so clipping to that same path would be a no-op — and
            // this runs twelve times per frame.
            drawColorLayer(canvas, clipPath, style)
        }
    }

    private fun drawColorLayer(canvas: Canvas, shapePath: Path, style: LegacyBackgroundStyle) {
        val colorAlpha = colorAlphaOf(style)
        if (colorAlpha == 0) return
        tempPaint.reset()
        tempPaint.isAntiAlias = true
        tempPaint.color = ColorUtils.setAlphaComponent(style.color, colorAlpha)
        canvas.drawPath(shapePath, tempPaint)
    }

    /**
     * Effective 0–255 alpha of the fill colour.
     *
     * Scales the colour's **own** alpha channel by [LegacyBackgroundStyle.colorOpacity]
     * instead of overwriting it. Overwriting turned [Color.TRANSPARENT] (`0x00000000`)
     * into opaque black at the default opacity of 100, so a month configured with only
     * a background image was painted as a solid black rectangle.
     */
    private fun colorAlphaOf(style: LegacyBackgroundStyle): Int =
        (Color.alpha(style.color) * style.colorOpacity.coerceIn(0, 100) / 100f).toInt()

    /** Effective 0–255 alpha of the background image. */
    private fun imageAlphaOf(style: LegacyBackgroundStyle): Int =
        (style.imageOpacity.coerceIn(0, 100) / 100f * 255).toInt()

    // ═══════════════════════════════════════════════════════════════════
    // Path & bitmap helpers
    // ═══════════════════════════════════════════════════════════════════

    private fun resolveBitmap(imageSource: ImageSource): Bitmap? {
        if (bitmapCache.containsKey(imageSource)) return bitmapCache[imageSource]
        val bitmap = when (imageSource) {
            is ImageSource.Provided -> when (val provider = imageSource.provider) {
                is ResourceImageProvider -> BitmapFactory.decodeResource(context.resources, provider.resId)
                is DrawableImageProvider -> Utils.drawableToBitmap(provider.drawable)
                is BitmapImageProvider -> provider.bitmap
                else -> null
            }
            else -> null
        }
        bitmapCache[imageSource] = bitmap
        return bitmap
    }

    private fun buildShapePath(bounds: RectF, shape: BackgroundShape): Path {
        val path = tempShapePath
        path.reset()
        when (shape) {
            is BackgroundShape.Circle -> {
                val radius = min(bounds.width(), bounds.height()) / 2f
                path.addCircle(bounds.centerX(), bounds.centerY(), radius, Path.Direction.CW)
            }
            is BackgroundShape.RoundedSquare -> {
                val cornerPx = shape.cornerRadius * density
                path.addRoundRect(bounds, cornerPx, cornerPx, Path.Direction.CW)
            }
            is BackgroundShape.Star -> {
                path.addPath(createStarPath(bounds.centerX(), bounds.centerY(), min(bounds.width(), bounds.height()) / 2f, shape.innerRadiusRatio, shape.numberOfLegs))
            }
            is BackgroundShape.Square -> path.addRect(bounds, Path.Direction.CW)
            is BackgroundShape.Custom -> {
                when (val provider = shape.provider) {
                    is XmlPathProvider -> scaleAndTranslatePathInto(provider.path, bounds, path, context.resources.getDimension(provider.innerPadding))
                    is ResourcePathProvider -> {
                        val vector = getCachedDrawablePath(provider.drawableRes)
                        if (vector != null) {
                            val padding = if (provider.innerPadding == 0) 0f
                            else context.resources.getDimension(provider.innerPadding)
                            // Prefer the author's viewport so artwork keeps the framing it
                            // was drawn with; fall back to bounding-box fit if absent.
                            val source = if (vector.hasViewport) {
                                tempViewportBounds.set(
                                    0f, 0f, vector.viewportWidth, vector.viewportHeight
                                )
                                tempViewportBounds
                            } else {
                                null
                            }
                            scaleAndTranslatePathInto(vector.path, bounds, path, padding, source)
                        } else {
                            path.addRect(bounds, Path.Direction.CW)
                        }
                    }
                    else -> path.addRect(bounds, Path.Direction.CW)
                }
            }
        }
        return path
    }

    /**
     * Returns a star path centered at ([centerX], [centerY]) with the given [radius].
     * Uses a cached normalized (unit-radius, origin-centered) star path keyed by
     * ([numPoints], [innerRadiusRatio]) to avoid recalculating trig per frame.
     */
    private fun createStarPath(centerX: Float, centerY: Float, radius: Float, innerRadiusRatio: Float, numPoints: Int): Path {
        val cacheKey = numPoints.toLong() shl 32 or (innerRadiusRatio.toBits().toLong() and 0xFFFFFFFFL)
        val unitStar = starPathCache.getOrPut(cacheKey) {
            buildUnitStarPath(innerRadiusRatio, numPoints)
        }
        tempStarPath.reset()
        tempStarPath.addPath(unitStar)
        tempMatrix.setScale(radius, radius)
        tempMatrix.postTranslate(centerX, centerY)
        tempStarPath.transform(tempMatrix)
        return tempStarPath
    }

    /**
     * Builds a normalized star path with unit radius centered at the origin.
     * Computed once per unique (numPoints, innerRadiusRatio) pair and cached.
     */
    private fun buildUnitStarPath(innerRadiusRatio: Float, numPoints: Int): Path {
        val path = Path()
        val angleStep = 360f / numPoints
        for (i in 0 until numPoints) {
            val outerAngle = (i * angleStep) - 90
            val innerAngle = outerAngle + (angleStep / 2)
            val outerRadians = Math.toRadians(outerAngle.toDouble()).toFloat()
            val innerRadians = Math.toRadians(innerAngle.toDouble()).toFloat()
            val outerX = cos(outerRadians.toDouble()).toFloat()
            val outerY = sin(outerRadians.toDouble()).toFloat()
            val innerX = innerRadiusRatio * cos(innerRadians.toDouble()).toFloat()
            val innerY = innerRadiusRatio * sin(innerRadians.toDouble()).toFloat()
            if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
            path.lineTo(innerX, innerY)
        }
        path.close()
        return path
    }

    /**
     * Maps [sourcePath] into [targetBounds] (inset by [innerPadding]) and appends the
     * result to [outputPath].
     *
     * [sourceBounds] is the rectangle the path should be treated as occupying. Pass the
     * vector's viewport when one is known so that padding the author baked into the
     * artwork is preserved; pass `null` to fit the path's own bounding box, which fills
     * the target exactly.
     */
    private fun scaleAndTranslatePathInto(
        sourcePath: Path,
        targetBounds: RectF,
        outputPath: Path,
        innerPadding: Float = 0f,
        sourceBounds: RectF? = null
    ) {
        tempPaddedBounds.set(
            targetBounds.left + innerPadding,
            targetBounds.top + innerPadding,
            targetBounds.right - innerPadding,
            targetBounds.bottom - innerPadding
        )
        if (sourceBounds != null) {
            tempPathBounds.set(sourceBounds)
        } else {
            @Suppress("DEPRECATION")
            sourcePath.computeBounds(tempPathBounds, true)
        }
        // A degenerate source cannot be scaled into anything meaningful.
        if (tempPathBounds.width() <= 0f || tempPathBounds.height() <= 0f) {
            outputPath.addRect(tempPaddedBounds, Path.Direction.CW)
            return
        }

        val scaleX = tempPaddedBounds.width() / tempPathBounds.width()
        val scaleY = tempPaddedBounds.height() / tempPathBounds.height()
        tempMatrix.setScale(scaleX, scaleY, tempPathBounds.left, tempPathBounds.top)
        // Map the *source rectangle's* origin onto the target, not the path's own
        // origin: that is what keeps artwork positioned inside its viewport.
        tempMatrix.postTranslate(
            tempPaddedBounds.left - tempPathBounds.left,
            tempPaddedBounds.top - tempPathBounds.top
        )
        tempScaledPath.reset()
        tempScaledPath.addPath(sourcePath)
        tempScaledPath.transform(tempMatrix)
        outputPath.addPath(tempScaledPath)
    }

    /**
     * Parses a `<vector>` drawable into a single [Path] expressed in **viewport
     * coordinates**.
     *
     * Three things this deliberately handles, all of which a naive `pathData`-only scan
     * gets wrong:
     *
     * - **`<group>` transforms.** Groups may translate, scale, rotate and pivot their
     *   children, and they nest. A matrix stack is maintained so each `<path>` is baked
     *   with the full parent chain applied, matching AOSP's `VectorDrawable.VGroup`
     *   composition order (`pivot⁻¹ → scale → rotate → translate+pivot`).
     * - **`<clip-path>`.** These carry `pathData` too, but they describe a *mask*, not
     *   fillable geometry. Adding them would union a clip rectangle into the shape, so
     *   only literal `path` tags are collected.
     * - **The viewport.** The returned [VectorPath] carries `viewportWidth`/`Height` so
     *   the caller can scale against the author's canvas rather than the path's own
     *   bounding box. Without it a glyph that intentionally sits in a corner of its
     *   viewport gets stretched to fill the whole cell.
     *
     * Returns [VectorPath.UNPARSEABLE] rather than throwing: an unusable `custom` shape
     * should degrade to a square, never crash a draw pass.
     */
    @android.annotation.SuppressLint("ResourceType")
    private fun extractPathFromVectorResource(
        @androidx.annotation.DrawableRes resId: Int
    ): VectorPath {
        var parser: android.content.res.XmlResourceParser? = null
        return try {
            parser = context.resources.getXml(resId)

            val resultPath = Path()
            var found = false
            var viewportWidth = 0f
            var viewportHeight = 0f

            // Accumulated group transform. Index 0 is identity (no enclosing group), so
            // the top of the stack is always `matrixStack[depth]`.
            val matrixStack = ArrayList<Matrix>()
            matrixStack.add(Matrix())
            val localMatrix = Matrix()
            val scratchPath = Path()

            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        TAG_VECTOR -> {
                            viewportWidth = parser.androidFloat(ATTR_VIEWPORT_WIDTH, 0f)
                            viewportHeight = parser.androidFloat(ATTR_VIEWPORT_HEIGHT, 0f)
                        }

                        TAG_GROUP -> {
                            val pivotX = parser.androidFloat(ATTR_PIVOT_X, 0f)
                            val pivotY = parser.androidFloat(ATTR_PIVOT_Y, 0f)
                            localMatrix.reset()
                            localMatrix.postTranslate(-pivotX, -pivotY)
                            localMatrix.postScale(
                                parser.androidFloat(ATTR_SCALE_X, 1f),
                                parser.androidFloat(ATTR_SCALE_Y, 1f)
                            )
                            localMatrix.postRotate(parser.androidFloat(ATTR_ROTATION, 0f), 0f, 0f)
                            localMatrix.postTranslate(
                                parser.androidFloat(ATTR_TRANSLATE_X, 0f) + pivotX,
                                parser.androidFloat(ATTR_TRANSLATE_Y, 0f) + pivotY
                            )
                            // child = parent ∘ local
                            val combined = Matrix(matrixStack.last())
                            combined.preConcat(localMatrix)
                            matrixStack.add(combined)
                        }

                        TAG_PATH -> {
                            val pathData = parser.getAttributeValue(ANDROID_NS, ATTR_PATH_DATA)
                            if (!pathData.isNullOrEmpty()) {
                                scratchPath.reset()
                                scratchPath.addPath(
                                    PathParser.createPathFromPathData(pathData)
                                )
                                scratchPath.transform(matrixStack.last())
                                resultPath.addPath(scratchPath)
                                found = true
                            }
                        }
                    }

                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        // Guard the pop: never drop the identity base matrix, even if the
                        // document is unbalanced.
                        if (parser.name == TAG_GROUP && matrixStack.size > 1) {
                            matrixStack.removeAt(matrixStack.size - 1)
                        }
                    }
                }
                eventType = parser.next()
            }

            if (!found) VectorPath.UNPARSEABLE
            else VectorPath(resultPath, viewportWidth, viewportHeight)
        } catch (e: Exception) {
            android.util.Log.w(
                "MonthRenderer",
                "Could not parse vector drawable 0x${resId.toString(16)} as a custom shape; " +
                    "falling back to a square.",
                e
            )
            VectorPath.UNPARSEABLE
        } finally {
            parser?.close()
        }
    }

    /** Reads an `android:`-namespaced float attribute, tolerating a missing value. */
    private fun android.content.res.XmlResourceParser.androidFloat(
        name: String,
        fallback: Float
    ): Float = getAttributeFloatValue(ANDROID_NS, name, fallback)

    private fun getCachedDrawablePath(
        @androidx.annotation.DrawableRes drawableRes: Int
    ): VectorPath? = drawablePathCache
        .getOrPut(drawableRes) { extractPathFromVectorResource(drawableRes) }
        .takeIf { it !== VectorPath.UNPARSEABLE }

    /**
     * A `<vector>` drawable flattened to one [Path], plus the viewport it was authored
     * against.
     *
     * The viewport is what makes the shape scale faithfully: fitting by the path's own
     * bounding box discards any intentional padding the author left around the artwork.
     * A non-positive viewport means the drawable did not declare one, in which case the
     * caller falls back to bounding-box fitting.
     */
    private class VectorPath(
        val path: Path,
        val viewportWidth: Float,
        val viewportHeight: Float
    ) {
        /** True when the viewport is usable as a source rectangle for scaling. */
        val hasViewport: Boolean get() = viewportWidth > 0f && viewportHeight > 0f

        companion object {
            /** Sentinel cached for drawables that could not be parsed. */
            val UNPARSEABLE = VectorPath(Path(), 0f, 0f)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Debug overlay
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Outlines the month blocks and the individual day cells for visual debugging.
     *
     * The rectangles are the *exact* bounds [YearView.getClickedDay] resolves a touch
     * against, so anything that looks misaligned here really is misaligned.
     *
     * Stroke widths are scaled by display density and drawn at full opacity: the
     * original 2px / 35%-alpha lines were effectively invisible on a high-density
     * screen, especially over a month background image.
     */
    fun drawDebugOverlay(canvas: Canvas, ctx: DrawContext) {
        val layout = ctx.layout
        val thin = DEBUG_CELL_STROKE_DP * density
        val thick = DEBUG_BLOCK_STROKE_DP * density

        tempPaint.reset()
        tempPaint.isAntiAlias = true
        tempPaint.style = Paint.Style.STROKE

        for (i in 0 until ctx.visibleMonthCount) {
            val md = layout[i]
            val block = ctx.monthBlocks[i]
            val contentTop = block.top + ctx.layoutComputer.monthTitleOffsets[i]
            val contentHeight = block.bottom - contentTop
            val xUnit = MonthGridGeometry.cellWidth(block.width())
            val yUnit = MonthGridGeometry.cellHeight(contentHeight)
            if (xUnit <= 0 || yUnit <= 0) continue

            val gridLeft = MonthGridGeometry.gridOriginX(block.left, block.width())

            // Block bounds — makes uneven column widths or an off-centre grid obvious.
            tempPaint.color = DEBUG_BLOCK_COLOR
            tempPaint.strokeWidth = thick
            tempRectF.set(
                block.left.toFloat(),
                block.top.toFloat(),
                block.right.toFloat(),
                block.bottom.toFloat()
            )
            canvas.drawRect(tempRectF, tempPaint)

            tempPaint.strokeWidth = thin
            for (row in 0 until MonthGridGeometry.TOTAL_GRID_ROWS) {
                for (col in 0 until MonthGridGeometry.DAYS_PER_WEEK) {
                    val isHeader = row == MonthGridGeometry.HEADER_ROW
                    // The header row is drawn but is not a touch target, so it gets a
                    // different colour; padding cells outside the month are skipped
                    // because they are not touch targets either.
                    if (!isHeader) {
                        val day = MonthGridGeometry.dayOfMonthAt(row, col, md.firstDayOffset)
                        if (day < 1 || day > md.daysInMonth) continue
                    }
                    tempPaint.color = if (isHeader) DEBUG_HEADER_COLOR else DEBUG_CELL_COLOR
                    // Mirrored exactly like the real cells, so the overlay keeps
                    // matching the touch targets in an RTL layout.
                    val visualCol = MonthGridGeometry.visualColumn(col, ctx.isRtl)
                    tempRectF.set(
                        MonthGridGeometry.cellLeft(gridLeft, xUnit, visualCol).toFloat(),
                        MonthGridGeometry.cellTop(contentTop, yUnit, row).toFloat(),
                        MonthGridGeometry.cellLeft(gridLeft, xUnit, visualCol + 1).toFloat(),
                        MonthGridGeometry.cellBottom(contentTop, yUnit, row).toFloat()
                    )
                    canvas.drawRect(tempRectF, tempPaint)
                }
            }
        }
    }

    // ══════��════════════════════════════════════════════════════════════
    // Paint holder — groups all paints to avoid passing 10+ individual params
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Holds references to all [Paint] objects used for drawing.
     * Created and updated by [YearView]; passed into draw methods.
     *
     * There is deliberately no paint for the range-selection background: that one is
     * shape-aware and goes through [drawStyledBackground], which builds its own paint
     * from [LegacyBackgroundStyle].
     */
    class Paints(
        val simpleDayNumber: Paint,
        val todayText: Paint,
        val todayBackground: Paint,
        val monthName: Paint,
        val todayMonthName: Paint,
        val weekendDay: Paint,
        val dayName: Paint,
        val selectedDayText: Paint,
        val selectedDayBackground: Paint
    )

    companion object {
        /**
         * Factor applied to the text-enclosing radius when drawing a star background.
         *
         * A star inscribed in a circle of radius *r* only reaches *r* at its points; the
         * filled area between them is much smaller. Scaling up keeps the day number
         * inside the star rather than spilling over its concave edges.
         */
        private const val STAR_ENCLOSING_RADIUS_SCALE = 1.8f

        /** Maximum number of entries in the bitmap LRU cache. */
        private const val MAX_BITMAP_CACHE_SIZE = 16

        // ── Debug overlay appearance ─────────────────────────────────
        // Opaque and density-scaled on purpose: these lines must stay readable on top
        // of a month background image on a 3x screen.

        /** Outline of a day cell — the touch target resolved by `getClickedDay`. */
        private const val DEBUG_CELL_COLOR = Color.RED

        /** Outline of the day-name header row, which is deliberately *not* touchable. */
        private const val DEBUG_HEADER_COLOR = 0xFF9C27B0.toInt()

        /** Outline of the whole month block, to expose uneven or off-centre blocks. */
        private const val DEBUG_BLOCK_COLOR = 0xFF2962FF.toInt()

        private const val DEBUG_CELL_STROKE_DP = 0.5f
        private const val DEBUG_BLOCK_STROKE_DP = 1.5f

        // ── <vector> drawable parsing ────────────────────────────────
        /** Namespace every `<vector>` structural attribute lives in. */
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val TAG_VECTOR = "vector"
        private const val TAG_GROUP = "group"
        private const val TAG_PATH = "path"
        private const val ATTR_PATH_DATA = "pathData"
        private const val ATTR_VIEWPORT_WIDTH = "viewportWidth"
        private const val ATTR_VIEWPORT_HEIGHT = "viewportHeight"
        private const val ATTR_TRANSLATE_X = "translateX"
        private const val ATTR_TRANSLATE_Y = "translateY"
        private const val ATTR_SCALE_X = "scaleX"
        private const val ATTR_SCALE_Y = "scaleY"
        private const val ATTR_ROTATION = "rotation"
        private const val ATTR_PIVOT_X = "pivotX"
        private const val ATTR_PIVOT_Y = "pivotY"
    }
}
