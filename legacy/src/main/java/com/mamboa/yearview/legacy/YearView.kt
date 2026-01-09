package com.mamboa.yearview.legacy

import android.content.Context
import android.content.res.TypedArray
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
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Pair
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.FontRes
import androidx.annotation.IntRange
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.FontType
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.TitleGravity
import com.mamboa.yearview.core.utils.Utils
import com.mamboa.yearview.core.utils.toBackgroundShape
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import java.lang.Double
import java.util.Locale
import kotlin.Array
import kotlin.Boolean
import kotlin.Exception
import kotlin.Float
import kotlin.Int
import kotlin.IntArray
import kotlin.Long
import kotlin.String
import kotlin.arrayOfNulls
import kotlin.let
import kotlin.math.abs
import kotlin.text.substring

/**
 * Created by mamboa on 9/4/2018.
 */
class YearView : View {

    // ========== PRIMARY: Configuration Objects (Source of Truth) ==========
    private var monthConfig: MonthConfig = MonthConfig()
    private var todayConfig: DayConfig = DayConfig()
    private var selectedDayConfig: DayConfig = DayConfig()
    private var simpleDayConfig: DayConfig = DayConfig()
    private var weekendDayConfig: DayConfig = DayConfig()

    // ========== View-Level Properties (Not in configs) ==========
    private var mYear = 2018
    private var verticalSpacing = 5
    private var horizontalSpacing = 5
    @IntRange(from = 1, to = 12) private var columns = 2
    @IntRange(from = 1, to = 12) private var rows = 6
    private var mWidth = 10
    private var mHeight = 10
    private var firstDayOfWeek = 1 //Monday: 1 to Sunday: 7
    private var mContext: Context?
    private var weekendDays: IntArray? = IntArray(366)
    
    // Day name row properties (header: M, T, W, T, F, S, S - not in any config)
    private var dayNameTextColor = Color.BLACK
    private var dayNameTextSize = 0
    private var dayNameFontType = FontType.NORMAL
    private var dayNameFontTypeFace: Typeface? = null
    private var dayNameTranscendsWeekend = false
    private val DEFAULT_ALIGN = Paint.Align.CENTER
    private var monthGestureListener: MonthGestureListener? = null
    var isDaySelectionVisuallySticky: Boolean = false
        private set

    private var simpleDayNumberPaint: Paint? = null
    private var todayTextPaint: Paint? = null
    private var todayBackgroundPaint: Paint? = null
    private var selectionPaint: Paint? = null
    private var monthNamePaint: Paint? = null
    private var todayMonthNamePaint: Paint? = null
    private var weekendDayPaint: Paint? = null
    private var dayNamePaint: Paint? = null
    private var selectedDayTextPaint: Paint? = null
    private var selectedDayBackgroundPaint: Paint? = null

    private lateinit var monthBlocks: Array<Rect?>
    private lateinit var originalMonthBlocks: Array<Rect?>
    private lateinit var daysBlocks: ArrayList<Pair<Rect, String>>
    private var lastRowPositionInMonth: IntArray? = null
    private var mGestureDetector: GestureDetector? = null

    private var mOnDownDelay = 0

    private var selectedMonthID = -1
    private var selectedDay: String? = ""

    private var handler: Handler? = null

    private var isClearSelectionLaunched = false
    private val clearSelectionRunnable = Runnable {
        selectedMonthID = -1
        isClearSelectionLaunched = false
        invalidate()
    }

    constructor(context: Context?) : super(context) {
        mContext = context
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        mContext = context

        val a = context.obtainStyledAttributes(attrs, R.styleable.YearView)
        try {
            mYear = a.getInteger(R.styleable.YearView_current_year, mYear)
            rows = a.getInteger(R.styleable.YearView_rows, rows)
            columns = a.getInteger(R.styleable.YearView_columns, columns)
            verticalSpacing = a.getInteger(R.styleable.YearView_vertical_spacing, verticalSpacing)
            horizontalSpacing = a.getInteger(R.styleable.YearView_horizontal_spacing, horizontalSpacing)
            firstDayOfWeek = a.getInteger(R.styleable.YearView_firstDayOfWeek, firstDayOfWeek)
            
            dayNameTextColor = a.getInteger(R.styleable.YearView_day_name_text_color, dayNameTextColor)
            dayNameTextSize = a.getDimensionPixelSize(R.styleable.YearView_day_name_text_size, (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE.toFloat(), resources.displayMetrics) + 0.5).toInt())
            dayNameFontType = FontType.entries[a.getInteger(R.styleable.YearView_day_name_font_type, FontType.NORMAL.ordinal)]
            dayNameFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_day_name_font, 0), a)
            dayNameTranscendsWeekend = a.getBoolean(R.styleable.YearView_name_week_transcend_weekend, dayNameTranscendsWeekend)
            
            isDaySelectionVisuallySticky = a.getBoolean(R.styleable.YearView_is_day_selection_visually_sticky, isDaySelectionVisuallySticky)
            
            selectedDay = a.getString(R.styleable.YearView_selected_day_text)
            if (!isDaySelectionVisuallySticky) {
                selectedDay = ""
            }

            val weekendDaysID = a.getResourceId(R.styleable.YearView_weekend_days, 0)
            if (weekendDaysID > 0) weekendDays = a.resources.getIntArray(weekendDaysID)
            
            val xmlMonthTitleGravity = TitleGravity.entries[a.getInteger(R.styleable.YearView_month_title_gravity, TitleGravity.CENTER.ordinal)]
            val xmlMarginBelowMonthName = a.getInteger(R.styleable.YearView_margin_below_month_name, 5)
            val xmlMonthSelectionColor = a.getColor(R.styleable.YearView_month_selection_color, Color.BLUE)
            val xmlMonthBackgroundColor = a.getColor(R.styleable.YearView_month_background_color, Color.TRANSPARENT)
            val xmlMonthBackgroundShape = a.getInteger(R.styleable.YearView_month_background_shape, 0).toBackgroundShape()
            val xmlMonthNameTextColor = a.getInteger(R.styleable.YearView_month_name_text_color, Color.BLACK)
            val xmlMonthNameTextSize = a.getDimensionPixelSize(R.styleable.YearView_month_name_text_size, (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE.toFloat(), resources.displayMetrics) + 0.5).toInt())
            val xmlMonthNameFontType = FontType.entries[a.getInteger(R.styleable.YearView_month_name_font_type, FontType.NORMAL.ordinal)]
            val xmlMonthNameFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_month_name_font, 0), a)
            val xmlTodayMonthNameTextColor = a.getInteger(R.styleable.YearView_today_month_name_text_color, Color.BLACK)
            val xmlTodayMonthNameTextSize = a.getDimensionPixelSize(R.styleable.YearView_today_month_name_text_size, (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE.toFloat(), resources.displayMetrics) + 0.5).toInt())
            val xmlTodayMonthNameFontType = FontType.entries[a.getInteger(R.styleable.YearView_today_month_name_font_type, FontType.NORMAL.ordinal)]
            val xmlTodayMonthNameFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_today_month_name_font, 0), a)
            val xmlMonthSelectionMargin = a.getInteger(R.styleable.YearView_month_selection_margin, 5)
            val xmlMonthBackgroundColorDensity = a.getInteger(R.styleable.YearView_month_background_color_density, 100)
            val xmlMonthBackgroundMergeType = MergeType.entries[a.getInteger(R.styleable.YearView_month_background_merge_type, MergeType.OVERLAY.ordinal)]
            val xmlMonthBackgroundImage = a.getDrawable(R.styleable.YearView_month_background_image)
            
            val xmlTodayTextColor = a.getColor(R.styleable.YearView_today_text_color, Color.WHITE)
            val xmlTodayTextSize = a.getDimensionPixelSize(R.styleable.YearView_today_text_size, (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE.toFloat(), resources.displayMetrics) + 0.5).toInt())
            val xmlTodayFontType = FontType.entries[a.getInteger(R.styleable.YearView_today_font_type, FontType.NORMAL.ordinal)]
            val xmlTodayFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_today_font, 0), a)
            val xmlTodayBackgroundColor = a.getColor(R.styleable.YearView_today_background_color, Color.RED)
            val xmlTodayBackgroundShape = a.getInteger(R.styleable.YearView_today_background_shape, 0).toBackgroundShape()
            val xmlTodayBackgroundRadius = a.getInteger(R.styleable.YearView_today_background_radius, 5)
            
            val xmlSelectedDayTextColor = a.getInteger(R.styleable.YearView_selected_day_text_color, Color.WHITE)
            val xmlSelectedDayTextSize = a.getDimensionPixelSize(R.styleable.YearView_selected_day_text_size, (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE.toFloat(), resources.displayMetrics) + 0.5).toInt())
            val xmlSelectedDayFontType = FontType.entries[a.getInteger(R.styleable.YearView_selected_day_font_type, FontType.NORMAL.ordinal)]
            val xmlSelectedDayTypeFace = buildFont(a.getResourceId(R.styleable.YearView_selected_day_font, 0), a)
            val xmlSelectedDayBackgroundColor = a.getColor(R.styleable.YearView_selected_day_background_color, Color.BLUE)
            val xmlSelectedDayBackgroundShape = a.getInteger(R.styleable.YearView_selected_day_background_shape, 0).toBackgroundShape()
            val xmlSelectedDayBackgroundRadius = a.getInteger(R.styleable.YearView_selected_day_background_radius, 5)
            
            val xmlSimpleDayTextColor = a.getColor(R.styleable.YearView_simple_day_text_color, Color.BLACK)
            val xmlSimpleDayTextSize = a.getDimensionPixelSize(R.styleable.YearView_simple_day_text_size, (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE.toFloat(), resources.displayMetrics) + 0.5).toInt())
            val xmlSimpleDayFontType = FontType.entries[a.getInteger(R.styleable.YearView_simple_day_font_type, FontType.NORMAL.ordinal)]
            val xmlSimpleDayFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_simple_day_font, 0), a)
            
            val xmlWeekendTextColor = a.getColor(R.styleable.YearView_weekend_text_color, Color.BLACK)
            val xmlWeekendTextSize = a.getDimensionPixelSize(R.styleable.YearView_weekend_text_size, (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_TEXT_SIZE.toFloat(), resources.displayMetrics) + 0.5).toInt())
            val xmlWeekendFontType = FontType.entries[a.getInteger(R.styleable.YearView_weekend_font_type, FontType.NORMAL.ordinal)]
            val xmlWeekendFontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_weekend_font, 0), a)
            
            // Build MonthConfig
            monthConfig = MonthConfig(
                titleGravity = xmlMonthTitleGravity,
                marginBelowMonthName = xmlMarginBelowMonthName,
                selectionBackgroundItemStyle = BackgroundItemStyle.AndroidXMLStyle(
                    color = xmlMonthSelectionColor,
                    shape = xmlMonthBackgroundShape,
                    selectionMargin = xmlMonthSelectionMargin.toFloat()
                ),
                backgroundItemStyle = BackgroundItemStyle.AndroidXMLStyle(
                    color = xmlMonthBackgroundColor,
                    shape = xmlMonthBackgroundShape,
                    image = ImageSource.ReceivedDrawable(xmlMonthBackgroundImage),
                    opacity = xmlMonthBackgroundColorDensity,
                    mergeType = xmlMonthBackgroundMergeType
                ),
                nameTextColor = xmlMonthNameTextColor,
                nameTextSize = xmlMonthNameTextSize,
                nameFontType = xmlMonthNameFontType,
                nameFontTypeFace = xmlMonthNameFontTypeFace,
                todayNameTextColor = xmlTodayMonthNameTextColor,
                todayNameTextSize = xmlTodayMonthNameTextSize,
                todayNameFontType = xmlTodayMonthNameFontType,
                todayNameFontTypeFace = xmlTodayMonthNameFontTypeFace
            )
            
            // Build TodayConfig
            todayConfig = DayConfig(
                backgroundItemStyle = BackgroundItemStyle.AndroidXMLStyle(
                    color = xmlTodayBackgroundColor,
                    shape = xmlTodayBackgroundShape
                ),
                textColor = xmlTodayTextColor,
                textSize = xmlTodayTextSize,
                fontType = xmlTodayFontType,
                fontTypeFace = xmlTodayFontTypeFace,
                backgroundRadius = xmlTodayBackgroundRadius
            )
            
            // Build SelectedDayConfig
            selectedDayConfig = DayConfig(
                backgroundItemStyle = BackgroundItemStyle.AndroidXMLStyle(
                    color = xmlSelectedDayBackgroundColor,
                    shape = xmlSelectedDayBackgroundShape
                ),
                textColor = xmlSelectedDayTextColor,
                textSize = xmlSelectedDayTextSize,
                fontType = xmlSelectedDayFontType,
                fontTypeFace = xmlSelectedDayTypeFace,
                backgroundRadius = xmlSelectedDayBackgroundRadius
            )
            
            // Build SimpleDayConfig
            simpleDayConfig = DayConfig(
                backgroundItemStyle = BackgroundItemStyle.AndroidXMLStyle(
                    color = Color.TRANSPARENT,
                    shape = BackgroundShape.Square
                ),
                textColor = xmlSimpleDayTextColor,
                textSize = xmlSimpleDayTextSize,
                fontType = xmlSimpleDayFontType,
                fontTypeFace = xmlSimpleDayFontTypeFace,
                backgroundRadius = 0
            )
            
            // Build WeekendDayConfig
            weekendDayConfig = DayConfig(
                backgroundItemStyle = BackgroundItemStyle.AndroidXMLStyle(
                    color = Color.TRANSPARENT,
                    shape = BackgroundShape.Square
                ),
                textColor = xmlWeekendTextColor,
                textSize = xmlWeekendTextSize,
                fontType = xmlWeekendFontType,
                fontTypeFace = xmlWeekendFontTypeFace,
                backgroundRadius = 0
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            a.recycle()
        }

        mOnDownDelay = ViewConfiguration.getTapTimeout()

        setupSimpleDayNumberPaint()
        setupWeekendPaint()
        setupDayNamePaint()
        setupMonthNamePaint()
        setupTodayMonthNamePaint()
        setupTodayTextPaint()
        setupTodayBackgroundPaint()
        setupMonthSelectionPaint()
        setupSelectedDayTextPaint()
        setupSelectedDayBackgroundPaint()

        mGestureDetector = GestureDetector(context, CalendarGestureListener())

        handler = Looper.myLooper()?.let { Handler(it) }
    }

    private fun buildFont(@FontRes fontID: Int, typeArray: TypedArray?): Typeface? {
        return if (fontID == 0 || typeArray == null) null else typeArray.resources.getFont(
            fontID
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        mWidth = width
        mHeight = height
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // View is now attached
    }

    override fun onDetachedFromWindow() {
        if (mContext != null) mContext = null
        doCleanup()
        super.onDetachedFromWindow()
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        splitViewInBlocks() //-1
        drawMonths(canvas) //-2
        drawSelection(canvas) //-3
    }

    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()

        //save here
        return superState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        super.onRestoreInstanceState(state)
        //restore here
        requestLayout()
    }

    /**
     * Split the view in a grid of columns * rows having 12 blocks in all. Each block will be used to draw a month inside
     */
    private fun splitViewInBlocks() {
        monthBlocks = arrayOfNulls(12)
        originalMonthBlocks = arrayOfNulls(12)
        daysBlocks = ArrayList(366)

        // allows us to compensate a right side padding
        //that happens because when we draw text we use Align.Center
        val horizontalCompensationPadding =
            if (columns % 2 != 0) (horizontalSpacing * columns / 2) else (horizontalSpacing * columns / rows)

        var k = 0
        for (i in 0..<rows) {
            for (j in 0..<columns) {
                val currentHorizontalSpacing = horizontalSpacing / 2
                val currentVerticalSpacing = verticalSpacing / 2

                val left =
                    horizontalCompensationPadding + (if (j == 0) currentHorizontalSpacing * 2 else currentHorizontalSpacing) + (j * mWidth / columns) //+ horizontal spacing to compensate for the align center
                val top = currentVerticalSpacing + (i * mHeight / rows)
                val right =
                    (j + 1) * mWidth / columns - (if (j == columns - 1) currentHorizontalSpacing * 2 else currentHorizontalSpacing)
                val bottom = (i + 1) * mHeight / rows - currentVerticalSpacing
                monthBlocks[k] = Rect(left, top, right, bottom)
                originalMonthBlocks[k] = Rect(left, top, right, bottom)
                k++
            }
        }
    }

    private fun drawMonths(canvas: Canvas) {
        lastRowPositionInMonth = IntArray(12)
        val dateTime = DateTime().withDate(mYear, 2, 1).withHourOfDay(12)

        for (i in 0..11) {
            val monthTime = dateTime.withMonthOfYear(i + 1)
            var dayOfWeek = dateTime.withMonthOfYear(i + 1).dayOfWeek().get()
            if (firstDayOfWeek != DateTimeConstants.SUNDAY) dayOfWeek -= firstDayOfWeek

            drawAMonth(
                canvas,
                i,
                dayOfWeek,
                monthTime.dayOfMonth().maximumValue,
                monthTime.monthOfYear().getAsText(
                    Utils.getCurrentLocale(mContext)
                )
            )
        }
    }

    private fun drawSelection(canvas: Canvas) {
        if (selectedMonthID > -1) {
            val selectionMargin = monthConfig.selectionBackgroundItemStyle.selectionMargin
            canvas.drawRect(
                (originalMonthBlocks[selectedMonthID]!!.left - selectionMargin - horizontalSpacing).toFloat(),
                (originalMonthBlocks[selectedMonthID]!!.top - selectionMargin).toFloat(),
                (originalMonthBlocks[selectedMonthID]!!.right + selectionMargin - horizontalSpacing).toFloat(),
                (lastRowPositionInMonth!![selectedMonthID] + selectionMargin).toFloat(),
                selectionPaint!!
            )

            if (!isClearSelectionLaunched) {
                handler?.postDelayed(clearSelectionRunnable, (mOnDownDelay * 2).toLong())
                isClearSelectionLaunched = true
            }
        }
    }

    /**
     * Draws the label name of a month
     *
     * @param canvas    the canvas' reference
     * @param index     the index of the month ([0,11])
     * @param monthName the String name of the month
     */
    private fun drawMonthName(canvas: Canvas, index: Int, monthName: String) {
        var paint: Paint?
        val today = DateTime().dayOfMonth
        try {
            paint = if (isToday(index, today)) Paint(todayMonthNamePaint)
            else Paint(monthNamePaint)
        } catch (ex: Exception) {
            ex.stackTrace
            paint = Paint(monthNamePaint)
        }


        val textBounds = Rect()
        paint.getTextBounds(monthName, 0, monthName.length, textBounds)

        var xStart: Int
        val yValue = monthBlocks[index]!!.top + textBounds.height()
        val width = textBounds.width()

        xStart = when (monthConfig.titleGravity) {
            TitleGravity.START, TitleGravity.LEFT -> monthBlocks[index]!!.left + width / 2 - horizontalSpacing / 2 //if ALIGN.LEFT monthBlocks[index].left + horizontalSpacing/2;
            TitleGravity.CENTER -> (monthBlocks[index]!!.left + monthBlocks[index]!!.right) / 2 - horizontalSpacing //if ALIGN.LEFT (monthBlocks[index].left + monthBlocks[index].right)/2 - width /2;
            TitleGravity.END, TitleGravity.RIGHT -> monthBlocks[index]!!.right - width / 2 - horizontalSpacing * 2 //if ALIGN.LEFT  monthBlocks[index].right - width - horizontalSpacing/2;
        }

        canvas.drawText(monthName + "", xStart.toFloat(), yValue.toFloat(), paint)

        //shift the rest below so that clicking the name won't trigger the month animation
        val left = monthBlocks[index]!!.left
        val top = monthBlocks[index]!!.top + textBounds.height() * 2 + monthConfig.marginBelowMonthName
        val right = monthBlocks[index]!!.right
        val bottom = monthBlocks[index]!!.bottom
        monthBlocks[index] = Rect(left, top, right, bottom)
    }

    private fun prepareMonthBackground(): BackgroundItemStyle.AndroidXMLStyle {
        val baseStyle = monthConfig.backgroundItemStyle
        val backgroundShape = when (val shape = baseStyle.shape) {
            is BackgroundShape.RoundedSquare -> {
                val radius = shape.cornerRadius
                if (radius > 0) BackgroundShape.RoundedSquare(radius) else shape
            }
            else -> shape
        }
        return baseStyle.copy(
            shape = backgroundShape
        )
    }

    /**
     * Draws a month
     *
     * @param canvas
     * @param month       the index of the month as in the list of monthBlock
     * @param firstDay    the first day of the month
     * @param daysInMonth the maximum number of days in that month
     * @param monthName   the string name of a month
     */
    private fun drawAMonth(
        canvas: Canvas,
        month: Int,
        firstDay: Int,
        daysInMonth: Int,
        monthName: String
    ) {
        drawMonthName(canvas, month, monthName)

        // val preparedBackgroundStyle = prepareMonthBackground()

        // Draw month background if provided
        if (monthConfig.backgroundItemStyle.color != Color.TRANSPARENT || monthConfig.backgroundItemStyle.image !is ImageSource.None
        ) {
            val bounds = RectF(
                monthBlocks[month]!!.left - monthConfig.backgroundItemStyle.selectionMargin,
                monthBlocks[month]!!.top - monthConfig.backgroundItemStyle.selectionMargin,
                monthBlocks[month]!!.right + monthConfig.backgroundItemStyle.selectionMargin,
                monthBlocks[month]!!.bottom + monthConfig.backgroundItemStyle.selectionMargin
            )
            drawStyledBackground(
                canvas,
                bounds,
                monthConfig.backgroundItemStyle,
            )
        }

        val xUnit = monthBlocks[month]!!.width() / numDays
        val yUnit = monthBlocks[month]!!.height() / numDays

        var dayOfMonth = 1 - firstDay
        for (y in 0..7) {
            for (x in 0..6) {
                val xValue = monthBlocks[month]!!.left + (xUnit * x)
                val yValue = monthBlocks[month]!!.top + (yUnit * y)

                val dateTime = DateTime()
                    .withYear(mYear)
                    .withMonthOfYear(month + 1)
                    .withDayOfWeek(getDayIndex(x))

                //draw day titles
                if (y == 0) {
                    val pDoW = dateTime.dayOfWeek()
                    val dayName =
                        pDoW.getAsShortText(Utils.getCurrentLocale(mContext))[0]

                    //weekend days
                    if (isDayPresentInWeekendDays(dateTime.dayOfWeek) && !dayNameTranscendsWeekend) {
                        //todo: add background for title ?
                        canvas.drawText(
                            dayName + "", xValue.toFloat(), yValue.toFloat(),
                            weekendDayPaint!!
                        )
                    } else {
                        canvas.drawText(
                            dayName + "", xValue.toFloat(), yValue.toFloat(),
                            dayNamePaint!!
                        )
                    }
                } else {
                    if (dayOfMonth in 1..daysInMonth) {
                        val isWeekEnd = isWeekend(month, dayOfMonth)
                        savePositionForSelection(
                            xValue,
                            yValue,
                            dayOfMonth,
                            isWeekEnd,
                            mYear,
                            month + 1
                        )

                        if (isSelectedDay(month, dayOfMonth)) {
                            val selectedShape = selectedDayConfig.backgroundItemStyle.shape
                            when (selectedShape) {
                                is BackgroundShape.Circle -> drawCircleAroundText(
                                    canvas,
                                    dayOfMonth,
                                    selectedDayTextPaint!!,
                                    selectedDayBackgroundPaint!!,
                                    xValue,
                                    yValue,
                                    selectedDayConfig.backgroundRadius
                                )

                                is BackgroundShape.RoundedSquare -> drawRoundedSquareAroundText(
                                    canvas,
                                    dayOfMonth,
                                    selectedDayTextPaint!!,
                                    selectedDayBackgroundPaint!!,
                                    xValue,
                                    yValue,
                                    selectedDayConfig.backgroundRadius,
                                    when (val shape = selectedDayConfig.backgroundItemStyle.shape) {
                                        is BackgroundShape.RoundedSquare -> shape.cornerRadius
                                        else -> 0f
                                    }
                                )

                                is BackgroundShape.Star -> {
                                    drawStarAroundText(
                                        canvas,
                                        dayOfMonth,
                                        selectedDayTextPaint!!,
                                        selectedDayBackgroundPaint!!,
                                        xValue,
                                        yValue,
                                        selectedDayConfig.backgroundRadius,
                                        selectedDayConfig.backgroundItemStyle.shape as BackgroundShape.Star
                                    )
                                }

                                else -> drawSquareAroundText(
                                    canvas,
                                    selectedDayTextPaint!!,
                                    selectedDayBackgroundPaint!!,
                                    dayOfMonth,
                                    xValue,
                                    yValue,
                                    selectedDayConfig.backgroundRadius
                                )
                            }
                        } else if (isToday(month, dayOfMonth)) {
                            val todayShape = todayConfig.backgroundItemStyle.shape
                            when (todayShape) {
                                is BackgroundShape.Circle -> drawCircleAroundText(
                                    canvas, dayOfMonth,
                                    todayTextPaint!!,
                                    todayBackgroundPaint!!, xValue, yValue, todayConfig.backgroundRadius
                                )

                                is BackgroundShape.RoundedSquare -> drawRoundedSquareAroundText(
                                    canvas,
                                    dayOfMonth,
                                    todayTextPaint!!,
                                    todayBackgroundPaint!!,
                                    xValue,
                                    yValue,
                                    todayConfig.backgroundRadius,
                                    when (val shape = (todayConfig.backgroundItemStyle as BackgroundItemStyle.AndroidXMLStyle).shape) {
                                        is BackgroundShape.RoundedSquare -> shape.cornerRadius
                                        else -> 0f
                                    }
                                )

                                is BackgroundShape.Star -> drawStarAroundText(
                                    canvas,
                                    dayOfMonth,
                                    todayTextPaint!!,
                                    todayBackgroundPaint!!,
                                    xValue,
                                    yValue,
                                    todayConfig.backgroundRadius,
                                    todayShape
                                )

                                else -> drawSquareAroundText(
                                    canvas,
                                    todayTextPaint!!,
                                    todayBackgroundPaint!!,
                                    dayOfMonth,
                                    xValue,
                                    yValue,
                                    todayConfig.backgroundRadius
                                )
                            }
                        } else {
                            if (isWeekEnd) {
                                //todo: draw a color to the background ?
                                canvas.drawText(
                                    dayOfMonth.toString() + "", xValue.toFloat(), yValue.toFloat(),
                                    weekendDayPaint!!
                                )
                            } else {
                                //todo: draw a color to the background ?
                                canvas.drawText(
                                    dayOfMonth.toString() + "", xValue.toFloat(), yValue.toFloat(),
                                    simpleDayNumberPaint!!
                                )
                            }
                        }
                        lastRowPositionInMonth!![month] =
                            yValue //we save the last line for the selection
                    }
                    dayOfMonth++
                }
            }
        }
    }

    /**
     * Save the coordinates areas that, when pressed upon, will trigger the selection
     *
     * @param xValue     the x start value of the area of the selection
     * @param yValue     the  y start value of the area of the selection
     * @param dayOfMonth the day of the month (this is use at debug time, when we need to see the available area for that the user can click on)
     * @param isWeekend  is this a weekend day (this is use at debug time, when we need to see the available area for that the user can click on)
     * @param year       the year of the view (this is use at debug time, when we need to see the available area for that the user can click on)
     * @param month      the month of the view (this is use at debug time, when we need to see the available area for that the user can click on)
     */
    private fun savePositionForSelection(
        xValue: Int,
        yValue: Int,
        dayOfMonth: Int,
        isWeekend: Boolean,
        year: Int,
        month: Int
    ) {
        val date = "$year-$month-$dayOfMonth"

        //border around date's day
        val paint = if (isWeekend) weekendDayPaint else simpleDayNumberPaint
        val textBounds = Rect()
        paint!!.getTextBounds(
            dayOfMonth.toString() + "",
            0,
            (dayOfMonth.toString() + "").length,
            textBounds
        )

        //dividing factor for the width of the selection will change to 0.9 if dayOfMonth is a digit
        val factor = if (dayOfMonth < 10) 0.9 else 1.5

        //border around date's day
        val border = Rect()
        border.left = xValue - (textBounds.width() / factor).toInt()
        border.top = yValue - (textBounds.height() * 1.5).toInt()
        border.right = xValue + (textBounds.width() / factor).toInt()
        border.bottom = yValue + textBounds.height() / 2
        daysBlocks.add(Pair(border, date))

        // test visually the touch area that will acknowledge the day click
        //canvas.drawRect(border, paint);
    }

    /**
     * Draws a square around a given day
     *
     * @param canvas          the canvas
     * @param textPaint       the text paint
     * @param backgroundPaint the background paint
     * @param dayOfMonth      the day of the month
     * @param xValue          the x of where to draw the day of the month's value
     * @param yValue          the y of where to draw the day of the month's value
     * @param margin          around the month value
     */
    private fun drawSquareAroundText(
        canvas: Canvas,
        textPaint: Paint,
        backgroundPaint: Paint,
        dayOfMonth: Int,
        xValue: Int,
        yValue: Int,
        margin: Int
    ) {
        val boxRect = RectF()
        val bounds = Rect()
        textPaint.getTextBounds(
            dayOfMonth.toString() + "",
            0,
            (dayOfMonth.toString() + "").length,
            bounds
        )

        //background square ALIGN.CENTER
        boxRect.left = (xValue - (bounds.width() / 2) - margin).toFloat()
        boxRect.top = (yValue - bounds.height() - margin).toFloat()
        boxRect.right = (xValue + (bounds.width() / 2) + margin).toFloat()
        boxRect.bottom = (yValue + margin).toFloat()

        //background square ALIGN.LEFT
        /*boxRect.left = xValue - margin;
        boxRect.top = yValue - diffAscDesc - diffTop - margin;
        boxRect.right = xValue + bounds.width() + margin;
        boxRect.bottom = yValue  + diffBottom + margin;*/
        canvas.drawRect(boxRect, backgroundPaint)
        canvas.drawText(dayOfMonth.toString() + "", xValue.toFloat(), yValue.toFloat(), textPaint)
    }

    /**
     * Draws a circle around a given day
     *
     * @param canvas          the canvas
     * @param dayOfMonth      the day of the month
     * @param textPaint       the text paint
     * @param backgroundPaint the background paint
     * @param xValue          the x of where to draw the day of the month's value
     * @param yValue          the y of where to draw the day of the month's value
     * @param margin          radius the month value
     */
    private fun drawCircleAroundText(
        canvas: Canvas,
        dayOfMonth: Int,
        textPaint: Paint,
        backgroundPaint: Paint,
        xValue: Int,
        yValue: Int,
        margin: Int
    ) {
        val bounds = Rect()
        textPaint.getTextBounds(
            dayOfMonth.toString() + "",
            0,
            (dayOfMonth.toString() + "").length,
            bounds
        )

        val fm = textPaint.fontMetrics
        val diffAscDesc = (abs((fm.ascent + fm.descent).toDouble())).toInt()

        //background square ALIGN.CENTER
        val centerX = xValue
        val centerY = yValue - diffAscDesc / 2

        //background square ALIGN.LEFT
        /* int centerX = xValue + bounds.width()/2;
        int centerY = yValue - diffAscDesc/2;*/

        //we make sure that the circle will always surround the value of the digits
        val radius =
            (if (bounds.width() > bounds.height()) bounds.width() else bounds.height()) / 2 + margin

        canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), radius.toFloat(), backgroundPaint)
        canvas.drawText(dayOfMonth.toString() + "", xValue.toFloat(), yValue.toFloat(), textPaint)
    }

    /**
     * Draws a rounded square around a given day
     *
     * @param canvas          the canvas
     * @param dayOfMonth      the day of the month
     * @param textPaint       the text paint
     * @param backgroundPaint the background paint
     * @param xValue          the x of where to draw the day of the month's value
     * @param yValue          the y of where to draw the day of the month's value
     * @param margin          margin around the month value
     * @param roundedRadius   radius of the rounded corners
     */
    private fun drawRoundedSquareAroundText(
        canvas: Canvas, dayOfMonth: Int, textPaint: Paint,
        backgroundPaint: Paint, xValue: Int, yValue: Int, margin: Int, roundedRadius: Float
    ) {
        val boxRect = RectF()
        val bounds = Rect()
        textPaint.getTextBounds(
            dayOfMonth.toString() + "",
            0,
            (dayOfMonth.toString() + "").length,
            bounds
        )

        // Background rounded square ALIGN.CENTER
        boxRect.left = (xValue - (bounds.width() / 2) - margin).toFloat()
        boxRect.top = (yValue - bounds.height() - margin).toFloat()
        boxRect.right = (xValue + (bounds.width() / 2) + margin).toFloat()
        boxRect.bottom = (yValue + margin).toFloat()

        canvas.drawRoundRect(boxRect, roundedRadius, roundedRadius, backgroundPaint)
        canvas.drawText(dayOfMonth.toString() + "", xValue.toFloat(), yValue.toFloat(), textPaint)
    }

    /**
     * Draws a star around a given day
     *
     * @param canvas          the canvas
     * @param dayOfMonth      the day of the month
     * @param textPaint       the text paint
     * @param backgroundPaint the background paint
     * @param xValue          the x of where to draw the day of the month's value
     * @param yValue          the y of where to draw the day of the month's value
     * @param radius          radius of the star points
     * @param starShape       the star shape configuration with numberOfLegs and innerRadiusRatio
     */
    private fun drawStarAroundText(
        canvas: Canvas, dayOfMonth: Int, textPaint: Paint,
        backgroundPaint: Paint, xValue: Int, yValue: Int, radius: Int, starShape: BackgroundShape.Star
    ) {

        // Calculate text bounds for centering
        val bounds = Rect()
        textPaint.getTextBounds(
            dayOfMonth.toString() + "",
            0,
            (dayOfMonth.toString() + "").length,
            bounds
        )

        val fm = textPaint.fontMetrics
        val diffAscDesc = (abs((fm.ascent + fm.descent).toDouble())).toInt()

        // Calculate center point
        val centerX = xValue
        val centerY = yValue - diffAscDesc / 2

        // Create bounds for the star background
        val starBounds = RectF(
            (centerX - radius).toFloat(),
            (centerY - radius).toFloat(),
            (centerX + radius).toFloat(),
            (centerY + radius).toFloat()
        )

        // Create background style for today using the configured star shape
        val todayStyle = BackgroundItemStyle.AndroidXMLStyle(
            backgroundPaint.color,
            starShape,
            0f,
            ImageSource.None,
            100 // Full opacity for today indicator
        )

        // Draw the star background
        drawStyledBackground(canvas, starBounds, todayStyle)

        // Draw the text on top
        canvas.drawText(dayOfMonth.toString() + "", xValue.toFloat(), yValue.toFloat(), textPaint)
    }

    /*private void drawStarAroundText(Canvas canvas, int dayOfMonth, Paint textPaint,
                                    Paint backgroundPaint, int xValue, int yValue, int radius, int numPoints) {
        // Validate number of points
        numPoints = Math.max(3, Math.min(7, numPoints));

        Rect bounds = new Rect();
        textPaint.getTextBounds(dayOfMonth + "", 0, (dayOfMonth + "").length(), bounds);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        int diffAscDesc = (int) (Math.abs(fm.ascent + fm.descent));

        // Calculate center point (same as circle method)
        int centerX = xValue;
        int centerY = yValue - diffAscDesc / 2;

        // Create path for star
        Path starPath = new Path();
        float angleStep = 360f / numPoints;
        float innerRadius = radius * 0.4f; // Inner radius for star points

        for (int i = 0; i < numPoints; i++) {
            float outerAngle = (i * angleStep) - 90;
            float innerAngle = outerAngle + (angleStep / 2);

            // Convert angles to radians
            float outerRadians = (float) Math.toRadians(outerAngle);
            float innerRadians = (float) Math.toRadians(innerAngle);

            float outerX = centerX + radius * (float) Math.cos(outerRadians);
            float outerY = centerY + radius * (float) Math.sin(outerRadians);
            float innerX = centerX + innerRadius * (float) Math.cos(innerRadians);
            float innerY = centerY + innerRadius * (float) Math.sin(innerRadians);

            if (i == 0) {
                starPath.moveTo(outerX, outerY);
            } else {
                starPath.lineTo(outerX, outerY);
            }
            starPath.lineTo(innerX, innerY);
        }
        starPath.close();

        // Draw the star background and text
        canvas.drawPath(starPath, backgroundPaint);
        canvas.drawText(dayOfMonth + "", xValue, yValue, textPaint);
    }*/
    private fun drawStyledBackground(
        canvas: Canvas,
        bounds: RectF,
        style: BackgroundItemStyle.AndroidXMLStyle
    ) {
        when (style.mergeType) {
            MergeType.CLIP -> {
                drawStyledBackgroundInnerClip(canvas, bounds, style)
            }

            else -> {
                drawStyledBackgroundMerge(canvas, bounds, style)
            }
        }
    }

    /**
     * Draws the background with merging for the given bounds and style.
     *
     * @param canvas The canvas to draw on.
     * @param bounds The bounds within which the background should be drawn.
     * @param style The style configuration for the background item.
     */
    private fun drawStyledBackgroundMerge(
        canvas: Canvas,
        bounds: RectF,
        style: BackgroundItemStyle.AndroidXMLStyle
    ) {
        // 1. Draw Image first if provided
        if (style.image !is ImageSource.None) {
            var bitmap: Bitmap? = null
            when (style.image) {
                is ImageSource.DrawableRes -> {
                    mContext?.let {
                        bitmap = BitmapFactory.decodeResource(
                            it.resources,
                            (style.image as ImageSource.DrawableRes).resId
                        )
                    }
                }

                is ImageSource.Bitmap -> {
                    bitmap = (style.image as ImageSource.Bitmap).bitmap
                }

                is ImageSource.ReceivedDrawable -> {
                    bitmap =
                        Utils.drawableToBitmap((style.image as ImageSource.ReceivedDrawable).drawable)
                }

                else -> {}
            }

            bitmap?.let {
                val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
                imagePaint.alpha = ((100f - style.opacity) / 100f * 255).toInt()
                canvas.drawBitmap(it, null, bounds, imagePaint)
            }
        }

        // 2. Draw shape with color on top (overlay mode)
        val colorAlpha = (style.opacity / 100f * 255).toInt()
        val colorWithOpacity = ColorUtils.setAlphaComponent(style.color, colorAlpha)

        val shapePath = Path()
        when (style.shape) {
            is BackgroundShape.Circle -> {
                val radius =
                    (Double.min(bounds.width().toDouble(), bounds.height().toDouble()) / 2f).toFloat()
                shapePath.addCircle(bounds.centerX(), bounds.centerY(), radius, Path.Direction.CW)
            }

            is BackgroundShape.RoundedSquare -> shapePath.addRoundRect(
                bounds,
                (style.shape as BackgroundShape.RoundedSquare).cornerRadius,
                (style.shape as BackgroundShape.RoundedSquare).cornerRadius,
                Path.Direction.CW
            )

            is BackgroundShape.Star -> {
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()
                val currentRadius =
                    (Double.min(bounds.width().toDouble(), bounds.height().toDouble()) / 2f).toFloat()
                val innerRadius =
                    currentRadius * (style.shape as BackgroundShape.Star).innerRadiusRatio

                val numPoints = (style.shape as BackgroundShape.Star).numberOfLegs
                val angleStep = 360f / numPoints

                var i = 0
                while (i < numPoints) {
                    val outerAngle = (i * angleStep) - 90
                    val innerAngle = outerAngle + (angleStep / 2)

                    val outerRadians = Math.toRadians(outerAngle.toDouble()).toFloat()
                    val innerRadians = Math.toRadians(innerAngle.toDouble()).toFloat()

                    val outerX = centerX + currentRadius * Math.cos(outerRadians.toDouble()).toFloat()
                    val outerY = centerY + currentRadius * Math.sin(outerRadians.toDouble()).toFloat()
                    val innerX = centerX + innerRadius * Math.cos(innerRadians.toDouble()).toFloat()
                    val innerY = centerY + innerRadius * Math.sin(innerRadians.toDouble()).toFloat()

                    if (i == 0) {
                        shapePath.moveTo(outerX, outerY)
                    } else {
                        shapePath.lineTo(outerX, outerY)
                    }
                    shapePath.lineTo(innerX, innerY)
                    i++
                }
                shapePath.close()
            }

            is BackgroundShape.Square -> shapePath.addRect(bounds, Path.Direction.CW)

            is BackgroundShape.xmlCustom -> {
                val customPath = (style.shape as BackgroundShape.xmlCustom).xmlPath
                val scaledPath = scaleAndTranslatePath(customPath, bounds, 0f)
                shapePath.addPath(scaledPath)
            }

            else -> shapePath.addRect(bounds, Path.Direction.CW)
        }

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        backgroundPaint.color = colorWithOpacity
        canvas.drawPath(shapePath, backgroundPaint)
    }

    /**
     * Draws the background with clipping for the given bounds and style.
     *
     * @param canvas The canvas to draw on.
     * @param bounds The bounds within which the background should be drawn.
     * @param style The style configuration for the background item.
     */
    private fun drawStyledBackgroundInnerClip(
        canvas: Canvas,
        bounds: RectF,
        style: BackgroundItemStyle.AndroidXMLStyle
    ) {
        // Create path for clipping based on shape
        val clipPath = Path()
        when (style.shape) {
            is BackgroundShape.Circle -> {
                val radius =
                    (Double.min(bounds.width().toDouble(), bounds.height().toDouble()) / 2f).toFloat()
                clipPath.addCircle(bounds.centerX(), bounds.centerY(), radius, Path.Direction.CW)
            }
            is BackgroundShape.RoundedSquare -> clipPath.addRoundRect(
                bounds,
                (style.shape as BackgroundShape.RoundedSquare).cornerRadius,
                (style.shape as BackgroundShape.RoundedSquare).cornerRadius,
                Path.Direction.CW
            )
            is BackgroundShape.Star -> {
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()
                val currentRadius =
                    (Double.min(bounds.width().toDouble(), bounds.height().toDouble()) / 2f).toFloat()
                val innerRadius =
                    currentRadius * (style.shape as BackgroundShape.Star).innerRadiusRatio

                val numPoints = (style.shape as BackgroundShape.Star).numberOfLegs
                val angleStep = 360f / numPoints

                var i = 0
                while (i < numPoints) {
                    val outerAngle = (i * angleStep) - 90
                    val innerAngle = outerAngle + (angleStep / 2)

                    val outerRadians = Math.toRadians(outerAngle.toDouble()).toFloat()
                    val innerRadians = Math.toRadians(innerAngle.toDouble()).toFloat()

                    val outerX = centerX + currentRadius * Math.cos(outerRadians.toDouble()).toFloat()
                    val outerY = centerY + currentRadius * Math.sin(outerRadians.toDouble()).toFloat()
                    val innerX = centerX + innerRadius * Math.cos(innerRadians.toDouble()).toFloat()
                    val innerY = centerY + innerRadius * Math.sin(innerRadians.toDouble()).toFloat()

                    if (i == 0) {
                        clipPath.moveTo(outerX, outerY)
                    } else {
                        clipPath.lineTo(outerX, outerY)
                    }
                    clipPath.lineTo(innerX, innerY)
                    i++
                }
                clipPath.close()
            }
            is BackgroundShape.Square -> clipPath.addRect(bounds, Path.Direction.CW)
            is BackgroundShape.xmlCustom -> {
                val customPath = (style.shape as BackgroundShape.xmlCustom).xmlPath
                val scaledPath = scaleAndTranslatePath(customPath, bounds, 0f)
                clipPath.addPath(scaledPath)
            }
            else -> clipPath.addRect(bounds, Path.Direction.CW)
        }

        // Save layer for clipping
        val saveCount = canvas.saveLayer(bounds, null)

        // Draw the clip shape as mask
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint.color = Color.WHITE
        canvas.drawPath(clipPath, maskPaint)

        // Draw image with SRC_IN to clip it to the shape
        if (style.image !is ImageSource.None) {
            var bitmap: Bitmap? = null
            when (style.image) {
                is ImageSource.DrawableRes -> {
                    mContext?.let {
                        bitmap = BitmapFactory.decodeResource(
                            it.resources,
                            (style.image as ImageSource.DrawableRes).resId
                        )
                    }
                }
                is ImageSource.Bitmap -> {
                    bitmap = (style.image as ImageSource.Bitmap).bitmap
                }
                is ImageSource.ReceivedDrawable -> {
                    bitmap = Utils.drawableToBitmap((style.image as ImageSource.ReceivedDrawable).drawable)
                }
                else -> {}
            }

            bitmap?.let {
                val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
                imagePaint.alpha = ((100f - style.opacity) / 100f * 255).toInt()
                imagePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(it, null, bounds, imagePaint)
            }
        }

        // Draw the color overlay (clip the color to the shape)
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        overlayPaint.color = ColorUtils.setAlphaComponent(
            style.color,
            (style.opacity / 100f * 255).toInt()
        )
        canvas.drawPath(clipPath, overlayPaint)
        canvas.restoreToCount(saveCount)
    }

    fun scaleAndTranslatePath(
        sourcePath: Path,
        targetBounds: RectF,
        innerPadding: Float = 0f
    ): Path {
        // Adjust target bounds with padding
        val paddedBounds = RectF(
            targetBounds.left + innerPadding,
            targetBounds.top + innerPadding,
            targetBounds.right - innerPadding,
            targetBounds.bottom - innerPadding
        )

        // Get the bounds of the source path
        val pathBounds = RectF()
        sourcePath.computeBounds(pathBounds, true)

        val scaleX = paddedBounds.width() / pathBounds.width()
        val scaleY = paddedBounds.height() / pathBounds.height()

        // Create a matrix for scaling
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, pathBounds.left, pathBounds.top)

        // Scale the path
        val scaledPath = Path(sourcePath)
        scaledPath.transform(matrix)

        // Get the bounds of the scaled path
        val scaledBounds = RectF()
        scaledPath.computeBounds(scaledBounds, true)

        // Calculate the offset to center the path in padded bounds
        val offsetX = paddedBounds.left - scaledBounds.left
        val offsetY = paddedBounds.top - scaledBounds.top

        // Create a matrix for translation
        val translateMatrix = Matrix()
        translateMatrix.setTranslate(offsetX, offsetY)

        // Translate the path
        scaledPath.transform(translateMatrix)

        return scaledPath
    }

    /**
     * Tells if the current date defined by the month and the day of the [) refers][YearView.mYear]
     */
    private fun isToday(month: Int, dayOfMonth: Int): Boolean {
        val dateTime = DateTime()
            .withYear(mYear)
            .withMonthOfYear(month + 1) //the variable monthBlocks goes from 0 to 11 while Joda Time's months go from 1 to 12
            .withDayOfMonth(dayOfMonth)
        return dateTime.toLocalDate() == LocalDate()
    }

    /**
     * Returns true if the current date defined by the month and the day of the [) refers][YearView.mYear]
     */
    private fun isSelectedDay(month: Int, dayOfMonth: Int): Boolean {
        val formatter = DateTimeFormat.forPattern(DAY_PATTERN).withLocale(Locale.ROOT)
        val dateTime = DateTime()
            .withYear(mYear)
            .withMonthOfYear(month + 1) //the variable monthBlocks goes from 0 to 11 while Joda Time's months go from 1 to 12
            .withDayOfMonth(dayOfMonth)
        return selectedDay == formatter.print(dateTime)
    }

    /**
     * Determine if a given day in a given mon is a weekend day. Notice that here we consider that
     * a weekend day is customizable
     *
     * @param month      the current month
     * @param dayOfMonth the day of the mont
     * @return it the day is considered a week end day
     */
    private fun isWeekend(month: Int, dayOfMonth: Int): Boolean {
        val dateTime = DateTime()
            .withYear(mYear)
            .withMonthOfYear(month + 1) //the variable monthBlocks goes from 0 to 11 while Joda Time's months go from 1 to 12
            .withDayOfMonth(dayOfMonth)
        return isDayPresentInWeekendDays(dateTime.dayOfWeek)
    }

    /**
     * Detects if a day is present in the list of custom weekend days
     *
     * @param dayIndex defines a day according to [DateTimeConstants], from SUNDAY
     * to SATURDAY
     * @return if a day is present in the list of weekend days
     */
    private fun isDayPresentInWeekendDays(dayIndex: Int): Boolean {
        if (weekendDays == null || weekendDays?.isEmpty() == true) return false
        for (i in weekendDays!!.indices) {
            if (weekendDays!![i] == dayIndex) return true
        }
        return false
    }

    /**
     * Returns the day index according to DateTimeConstants Joda-Time, taking into account what the user
     * wants as first day of the week
     *
     * @param position of the day ([0;7[)
     * @return the day's index
     */
    private fun getDayIndex(position: Int): Int {
        return if (firstDayOfWeek == DateTimeConstants.MONDAY || position + firstDayOfWeek <= 7) firstDayOfWeek + position else (firstDayOfWeek + position) % 7
    }

    /**
     * Set the weekend days according to [DateTimeConstants].
     * Example: newWeekendDays = {DateTimeConstants.SATURDAY, DateTimeConstants.SUNDAY}
     * The order of the days doesn't count
     *
     * @param newWeekendDays the list of weekend days
     */
    fun setWeekendDays(newWeekendDays: IntArray?) {
        weekendDays = newWeekendDays
        invalidate()
    }

    fun setVerticalSpacing(@DimenRes verticalSpacingRes: Int) {
        this.verticalSpacing = resources.getDimensionPixelSize(verticalSpacingRes)
        invalidate()
    }

    fun setHorizontalSpacing(@DimenRes horizontalSpacingRes: Int) {
        this.horizontalSpacing = resources.getDimensionPixelSize(horizontalSpacingRes)
        invalidate()
    }

    fun setColumns(@IntRange(from = 1, to = 12) columns: Int) {
        this.columns = columns
        invalidate()
    }

    fun setRows(@IntRange(from = 1, to = 12) rows: Int) {
        this.rows = rows
        invalidate()
    }

    fun setMonthSelectionColor(@ColorRes monthSelectionColorRes: Int) {
        val color = ContextCompat.getColor(context, monthSelectionColorRes)
        monthConfig = monthConfig.copy(
            selectionBackgroundItemStyle = (monthConfig.selectionBackgroundItemStyle as BackgroundItemStyle.AndroidXMLStyle).copy(color = color)
        )
        setupMonthSelectionPaint()
        invalidate()
    }

    fun setTodayTextColor(@ColorRes todayTextColorRes: Int) {
        todayConfig = todayConfig.copy(textColor = ContextCompat.getColor(context, todayTextColorRes))
        setupTodayTextPaint()
        invalidate()
    }

    fun setTodayBackgroundColor(@ColorRes todayBackgroundColorRes: Int) {
        val color = ContextCompat.getColor(context, todayBackgroundColorRes)
        todayConfig = todayConfig.copy(
            backgroundItemStyle = (todayConfig.backgroundItemStyle as BackgroundItemStyle.AndroidXMLStyle).copy(color = color)
        )
        setupTodayBackgroundPaint()
        invalidate()
    }

    fun setSimpleDayTextColor(@ColorRes simpleDayTextColorRes: Int) {
        simpleDayConfig = simpleDayConfig.copy(textColor = ContextCompat.getColor(context, simpleDayTextColorRes))
        setupSimpleDayNumberPaint()
        invalidate()
    }

    fun setWeekendTextColor(@ColorRes weekendTextColorRes: Int) {
        weekendDayConfig = weekendDayConfig.copy(textColor = ContextCompat.getColor(context, weekendTextColorRes))
        setupWeekendPaint()
        invalidate()
    }

    fun setTodayBackgroundRadius(@DimenRes todayBackgroundRadiusRes: Int) {
        todayConfig = todayConfig.copy(backgroundRadius = resources.getDimensionPixelSize(todayBackgroundRadiusRes))
        setupTodayBackgroundPaint()
        invalidate()
    }

    fun setDayNameTextColor(@ColorRes dayNameTextColorRes: Int) {
        this.dayNameTextColor = ContextCompat.getColor(context, dayNameTextColorRes)
        setupDayNamePaint()
        invalidate()
    }

    fun setMonthNameTextColor(@ColorRes monthNameTextColorRes: Int) {
        monthConfig = monthConfig.copy(nameTextColor = ContextCompat.getColor(context, monthNameTextColorRes))
        setupMonthNamePaint()
        invalidate()
    }

    fun setTodayMonthNameTextColor(@ColorRes todayMonthNameTextColorRes: Int) {
        monthConfig = monthConfig.copy(todayNameTextColor = ContextCompat.getColor(context, todayMonthNameTextColorRes))
        setupTodayMonthNamePaint()
        invalidate()
    }

    fun setMonthSelectionMargin(@DimenRes monthSelectionMarginRes: Int) {
        val margin = resources.getDimensionPixelSize(monthSelectionMarginRes).toFloat()
        monthConfig = monthConfig.copy(
            selectionBackgroundItemStyle = (monthConfig.selectionBackgroundItemStyle as BackgroundItemStyle.AndroidXMLStyle).copy(selectionMargin = margin)
        )
        setupMonthSelectionPaint()
        invalidate()
    }

    fun setMonthNameFontTypeFace(monthNameFontTypeFace: Typeface?) {
        monthConfig = monthConfig.copy(nameFontTypeFace = monthNameFontTypeFace)
        setupMonthNamePaint()
        invalidate()
    }

    fun setWeekendFontTypeFace(weekendFontTypeFace: Typeface?) {
        weekendDayConfig = weekendDayConfig.copy(fontTypeFace = weekendFontTypeFace)
        setupWeekendPaint()
        invalidate()
    }

    fun setDayNameFontTypeFace(dayNameFontTypeFace: Typeface?) {
        this.dayNameFontTypeFace = dayNameFontTypeFace
        setupDayNamePaint()
        invalidate()
    }

    fun setTodayFontTypeFace(todayFontTypeFace: Typeface?) {
        todayConfig = todayConfig.copy(fontTypeFace = todayFontTypeFace)
        setupTodayTextPaint()
        invalidate()
    }

    fun setSimpleDayFontTypeFace(simpleDayFontTypeFace: Typeface?) {
        simpleDayConfig = simpleDayConfig.copy(fontTypeFace = simpleDayFontTypeFace)
        setupSimpleDayNumberPaint()
        invalidate()
    }

    fun setTodayMonthNameFontTypeFace(todayMonthNameFontTypeFace: Typeface?) {
        monthConfig = monthConfig.copy(todayNameFontTypeFace = todayMonthNameFontTypeFace)
        setupTodayMonthNamePaint()
        invalidate()
    }

    /**
     * Updates the text size using a dimension resource from dimens.xml
     *
     * @param simpleDayTextSizeRes Resource ID for text size (e.g., R.dimen.simple_day_text_size)
     */
    fun setSimpleDayTextSize(@DimenRes simpleDayTextSizeRes: Int) {
        simpleDayConfig = simpleDayConfig.copy(textSize = resources.getDimensionPixelSize(simpleDayTextSizeRes))
        setupSimpleDayNumberPaint()
        invalidate()
    }

    /**
     * Updates the text size using a dimension resource from dimens.xml
     *
     * @param weekendTextSizeRes Resource ID for text size (e.g., R.dimen.weekend_text_size)
     */
    fun setWeekendTextSize(@DimenRes weekendTextSizeRes: Int) {
        weekendDayConfig = weekendDayConfig.copy(textSize = resources.getDimensionPixelSize(weekendTextSizeRes))
        setupWeekendPaint()
        invalidate()
    }

    /**
     * Updates the text size using a dimension resource from dimens.xml
     *
     * @param todayTextSizeRes Resource ID for text size (e.g., R.dimen.today_text_size)
     */
    fun setTodayTextSize(@DimenRes todayTextSizeRes: Int) {
        todayConfig = todayConfig.copy(textSize = resources.getDimensionPixelSize(todayTextSizeRes))
        setupTodayTextPaint()
        invalidate()
    }

    /**
     * Updates the text size using a dimension resource from dimens.xml
     *
     * @param dayNameTextSizeRes Resource ID for text size (e.g., R.dimen.day_name_text_size)
     */
    fun setDayNameTextSize(@DimenRes dayNameTextSizeRes: Int) {
        this.dayNameTextSize = resources.getDimensionPixelSize(dayNameTextSizeRes)
        setupDayNamePaint()
        invalidate()
    }

    /**
     * Updates the text size using a dimension resource from dimens.xml
     *
     * @param monthNameTextSizeRes Resource ID for text size (e.g., R.dimen.month_name_text_size)
     */
    fun setMonthNameTextSize(@DimenRes monthNameTextSizeRes: Int) {
        monthConfig = monthConfig.copy(nameTextSize = resources.getDimensionPixelSize(monthNameTextSizeRes))
        setupMonthNamePaint()
        invalidate()
    }

    /**
     * Updates the text size using a dimension resource from dimens.xml
     *
     * @param todayMonthNameTextSizeRes Resource ID for text size (e.g., R.dimen.today_month_name_text_size)
     */
    fun setTodayMonthNameTextSize(@DimenRes todayMonthNameTextSizeRes: Int) {
        monthConfig = monthConfig.copy(todayNameTextSize = resources.getDimensionPixelSize(todayMonthNameTextSizeRes))
        setupTodayMonthNamePaint()
        invalidate()
    }

    /**
     * Should be between CENTER, START, LEFT, END, RIGHT. Will be set
     * to CENTER if undefined
     *
     * @param monthTitleGravity the new title gravity
     */
    fun setMonthTitleGravity(monthTitleGravity: TitleGravity) {
        val gravity = if (monthTitleGravity != TitleGravity.CENTER && monthTitleGravity != TitleGravity.END && monthTitleGravity != TitleGravity.LEFT) 
            TitleGravity.CENTER 
        else 
            monthTitleGravity
        monthConfig = monthConfig.copy(titleGravity = gravity)
        invalidate()
    }

    /**
     * Should be between circle and square
     *
     * @param todayBackgroundShape the new background shape. Will be set to circle if undefined
     */
    fun setTodayBackgroundShape(todayBackgroundShape: BackgroundShape) {
        todayConfig = todayConfig.copy(
            backgroundItemStyle = (todayConfig.backgroundItemStyle as BackgroundItemStyle.AndroidXMLStyle).copy(shape = todayBackgroundShape)
        )
        invalidate()
    }

    /**
     * Should be between circle and square
     *
     * @param selectedDayBackgroundShape the new background shape. Will be set to square if undefined
     */
    fun setSelectedDayBackgroundShape(selectedDayBackgroundShape: BackgroundShape) {
        selectedDayConfig = selectedDayConfig.copy(
            backgroundItemStyle = (selectedDayConfig.backgroundItemStyle as BackgroundItemStyle.AndroidXMLStyle).copy(shape = selectedDayBackgroundShape)
        )
        invalidate()
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param todayFontType the new font type for today
     */
    fun setTodayFontType(todayFontType: FontType) {
        todayConfig = todayConfig.copy(fontType = todayFontType)
        setupTodayTextPaint()
        invalidate()
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param monthNameFontType the new font type for the month
     */
    fun setMonthNameFontType(monthNameFontType: FontType) {
        monthConfig = monthConfig.copy(nameFontType = monthNameFontType)
        setupMonthNamePaint()
        invalidate()
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param todayMonthNameFontType the new font type for the month
     */
    fun setTodayMonthNameFontType(todayMonthNameFontType: FontType) {
        monthConfig = monthConfig.copy(todayNameFontType = todayMonthNameFontType)
        setupTodayMonthNamePaint()
        invalidate()
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param dayNameFontType the new font type for the month
     */
    fun setDayNameFontType(dayNameFontType: FontType) {
        this.dayNameFontType = dayNameFontType
        setupDayNamePaint()
        invalidate()
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param weekendFontType the new font type for the month
     */
    fun setWeekendNameFontType(weekendFontType: FontType) {
        weekendDayConfig = weekendDayConfig.copy(fontType = weekendFontType)
        setupWeekendPaint()
        invalidate()
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param simpleDayFontType the new font type for the month
     */
    fun setSimpleDayFontType(simpleDayFontType: FontType) {
        simpleDayConfig = simpleDayConfig.copy(fontType = simpleDayFontType)
        setupSimpleDayNumberPaint()
        invalidate()
    }

    /**
     * Should be between bold, italic,
     * bold_italic and normal
     *
     * @param selectedDayFontType the new font type for the month
     */
    fun setSelectedDayFontType(selectedDayFontType: FontType) {
        selectedDayConfig = selectedDayConfig.copy(fontType = selectedDayFontType)
        setupSelectedDayTextPaint()
        invalidate()
    }

    /**
     * If true: a font type ( and a text color)  applied to the name of the days in the week,
     * will also be applied to the name of the days representing the weekend
     *
     * @param transcendsWeekend
     */
    fun setDayNameTranscendsWeekend(transcendsWeekend: Boolean) {
        dayNameTranscendsWeekend = transcendsWeekend
        invalidate()
    }

    fun setIfDaySelectionVisuallySticky(isDaySelectionVisuallySticky: Boolean) {
        this.isDaySelectionVisuallySticky = isDaySelectionVisuallySticky
        if (!isDaySelectionVisuallySticky) {
            selectedDay = ""
        }
        invalidate()
    }

    /**
     * Returns the timestamp of the currently selected day. Will return 0 if there is no selected date
     *
     * @return timestamp of the selected day
     */
    fun getSelectedDay(): Long {
        if (!TextUtils.isEmpty(selectedDay)) {
            try {
                val formatter = DateTimeFormat.forPattern(DAY_PATTERN).withLocale(Locale.ROOT)
                val dateTime = formatter.parseDateTime(selectedDay)
                return dateTime.millis
            } catch (ex: Exception) {
                ex.printStackTrace()
                return 0
            }
        }
        return 0
    }

    var year: Int
        get() = mYear
        set(mYear) {
            this.mYear = mYear
            invalidate()
        }

    fun getColumns(): Int {
        return columns
    }

    fun getRows(): Int {
        return rows
    }

    private fun doCleanup() {
        handler?.removeCallbacksAndMessages(null)
    }

    private fun setupMonthNamePaint() {
        monthNamePaint = setupTextPaint(
            monthConfig.nameTextColor,
            monthConfig.nameTextSize,
            monthConfig.nameFontType,
            DEFAULT_ALIGN,
            monthConfig.nameFontTypeFace
        )
    }

    private fun setupTodayMonthNamePaint() {
        todayMonthNamePaint = setupTextPaint(
            monthConfig.todayNameTextColor,
            monthConfig.todayNameTextSize,
            monthConfig.todayNameFontType,
            DEFAULT_ALIGN,
            monthConfig.todayNameFontTypeFace
        )
    }

    private fun setupSimpleDayNumberPaint() {
        simpleDayNumberPaint = setupTextPaint(
            simpleDayConfig.textColor,
            simpleDayConfig.textSize,
            simpleDayConfig.fontType,
            DEFAULT_ALIGN,
            simpleDayConfig.fontTypeFace
        )
    }

    private fun setupTodayTextPaint() {
        todayTextPaint = setupTextPaint(
            todayConfig.textColor,
            todayConfig.textSize,
            todayConfig.fontType,
            DEFAULT_ALIGN,
            todayConfig.fontTypeFace
        )
    }

    private fun setupTodayBackgroundPaint() {
        todayBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        todayBackgroundPaint!!.color = (todayConfig.backgroundItemStyle as BackgroundItemStyle.AndroidXMLStyle).color
        todayBackgroundPaint!!.textSize = todayConfig.textSize.toFloat()
        todayBackgroundPaint!!.textAlign = DEFAULT_ALIGN
    }

    private fun setupSelectedDayTextPaint() {
        selectedDayTextPaint = setupTextPaint(
            selectedDayConfig.textColor,
            selectedDayConfig.textSize,
            selectedDayConfig.fontType,
            DEFAULT_ALIGN,
            selectedDayConfig.fontTypeFace
        )
    }

    private fun setupSelectedDayBackgroundPaint() {
        selectedDayBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        selectedDayBackgroundPaint!!.color = (selectedDayConfig.backgroundItemStyle as BackgroundItemStyle.AndroidXMLStyle).color
        selectedDayBackgroundPaint!!.textSize = selectedDayConfig.textSize.toFloat()
        selectedDayBackgroundPaint!!.textAlign = DEFAULT_ALIGN
    }

    private fun setupDayNamePaint() {
        dayNamePaint = setupTextPaint(
            dayNameTextColor,
            dayNameTextSize,
            dayNameFontType,
            DEFAULT_ALIGN,
            dayNameFontTypeFace
        )
    }

    private fun setupWeekendPaint() {
        weekendDayPaint = setupTextPaint(
            weekendDayConfig.textColor,
            weekendDayConfig.textSize,
            weekendDayConfig.fontType,
            DEFAULT_ALIGN,
            weekendDayConfig.fontTypeFace
        )
    }

    private fun setupMonthSelectionPaint() {
        selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        selectionPaint?.color = ColorUtils.setAlphaComponent(
            monthConfig.selectionBackgroundItemStyle.color,
            SELECTION_ALPHA
        )
        selectionPaint?.strokeJoin = Paint.Join.ROUND
        selectionPaint?.style = Paint.Style.FILL_AND_STROKE
        selectionPaint?.strokeWidth = SELECTION_STROKE
        selectionPaint?.textAlign = DEFAULT_ALIGN
    }

    private fun setupTextPaint(
        textColor: Int,
        textSize: Int,
        fontType: FontType,
        paintAlign: Paint.Align,
        typeface: Typeface?
    ): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = textColor
        paint.textSize = textSize.toFloat()
        paint.textAlign = paintAlign
        when (fontType) {
            FontType.BOLD -> paint.setTypeface(
                if (typeface != null) Typeface.create(
                    typeface,
                    Typeface.BOLD
                ) else paint.setTypeface(
                    Typeface.DEFAULT_BOLD
                )
            )

            FontType.ITALIC -> paint.setTypeface(
                if (typeface != null) Typeface.create(
                    typeface,
                    Typeface.ITALIC
                ) else paint.setTypeface(
                    Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                )
            )

            FontType.BOLD_ITALIC -> paint.setTypeface(
                if (typeface != null) Typeface.create(
                    typeface,
                    Typeface.BOLD_ITALIC
                ) else paint.setTypeface(
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                )
            )

            else -> paint.setTypeface(
                typeface
                    ?: paint.setTypeface(Typeface.DEFAULT)
            )
        }
        return paint
    }

    fun setMonthBackgroundItemStyle(style: BackgroundItemStyle.AndroidXMLStyle) {
        monthConfig = monthConfig.copy(backgroundItemStyle = style)
        invalidate()
    }

    /**
     * Set the month configuration object.
     *
     * @param config The month configuration to apply
     */
    fun setMonthConfig(config: MonthConfig) {
        this.monthConfig = config
        // Rebuild paints
        setupMonthNamePaint()
        setupTodayMonthNamePaint()
        setupMonthSelectionPaint()
        invalidate()
    }

    /**
     * Get the current month configuration.
     *
     * @return The current month configuration
     */
    fun getMonthConfig(): MonthConfig {
        return monthConfig
    }

    /**
     * Set the today day configuration object.
     *
     * @param config The today day configuration to apply
     */
    fun setTodayConfig(config: DayConfig) {
        this.todayConfig = config
        // Rebuild paints
        setupTodayTextPaint()
        setupTodayBackgroundPaint()
        invalidate()
    }

    /**
     * Get the current today day configuration.
     *
     * @return The current today day configuration
     */
    fun getTodayConfig(): DayConfig {
        return todayConfig
    }

    /**
     * Set the selected day configuration object.
     *
     * @param config The selected day configuration to apply
     */
    fun setSelectedDayConfig(config: DayConfig) {
        this.selectedDayConfig = config
        // Rebuild paints
        setupSelectedDayTextPaint()
        setupSelectedDayBackgroundPaint()
        invalidate()
    }

    /**
     * Get the current selected day configuration.
     *
     * @return The current selected day configuration
     */
    fun getSelectedDayConfig(): DayConfig {
        return selectedDayConfig
    }

    /**
     * Set the simple day configuration object.
     *
     * @param config The simple day configuration to apply
     */
    fun setSimpleDayConfig(config: DayConfig) {
        this.simpleDayConfig = config
        // Rebuild paints
        setupSimpleDayNumberPaint()
        invalidate()
    }

    /**
     * Get the current simple day configuration.
     *
     * @return The current simple day configuration
     */
    fun getSimpleDayConfig(): DayConfig {
        return simpleDayConfig
    }

    /**
     * Set the weekend day configuration object.
     *
     * @param config The weekend day configuration to apply
     */
    fun setWeekendDayConfig(config: DayConfig) {
        this.weekendDayConfig = config
        setupWeekendPaint()
        invalidate()
    }

    /**
     * Get the current weekend day configuration.
     *
     * @return The current weekend day configuration
     */
    fun getWeekendDayConfig(): DayConfig {
        return weekendDayConfig
    }

    fun setMonthGestureListener(monthGestureListener: MonthGestureListener?) {
        this.monthGestureListener = monthGestureListener
    }

    interface MonthGestureListener {
        //first day of the month in millis
        fun onMonthClick(timeInMillis: Long)

        fun onMonthLongClick(timeInMillis: Long)

        fun onDayClick(timeInMillis: Long)

        fun onDayLongClick(timeInMillis: Long)
    }

    /**
     * Get the firstDay at first hour of the clicked month
     *
     * @param x
     * @param y
     * @return
     */
    private fun getClickedMonth(x: Int, y: Int): Long {
        for (i in originalMonthBlocks.indices) {
            if (originalMonthBlocks[i]!!.contains(x, y)) {
                val dayOfWeek = DateTime().withYear(mYear)
                    .withMonthOfYear(i + 1)
                    .withDayOfMonth(1)
                    .withHourOfDay(1)
                selectedMonthID = i
                return dayOfWeek.millis
            }
        }
        return 0
    }

    /**
     * Get the first hour of the clicked day
     *
     * @param x
     * @param y
     * @return
     */
    private fun getClickedDay(x: Int, y: Int): Long {
        for (i in daysBlocks.indices) {
            if (daysBlocks[i].first.contains(x, y)) {
                val formatter = DateTimeFormat.forPattern(DAY_PATTERN).withLocale(Locale.ROOT)
                val dateTime = formatter.parseDateTime(daysBlocks[i].second)
                return dateTime.millis
            }
        }
        return 0
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return mGestureDetector!!.onTouchEvent(ev)
    }

    internal inner class CalendarGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(ev: MotionEvent): Boolean {
            var isDayClicked = false
            if (monthGestureListener != null) {
                val timeInMillis = getClickedDay(ev.x.toInt(), ev.y.toInt())
                isDayClicked = isDayClicked(timeInMillis)
                invalidate()
            }

            if (monthGestureListener != null && !isDayClicked) {
                val timeInMillis = getClickedMonth(ev.x.toInt(), ev.y.toInt())
                if (timeInMillis != 0L) {
                    monthGestureListener!!.onMonthClick(timeInMillis)
                    invalidate()
                }
            }
            return true
        }

        override fun onLongPress(ev: MotionEvent) {
            var isDayClicked = false
            if (monthGestureListener != null) {
                val timeInMillis = getClickedDay(ev.x.toInt(), ev.y.toInt())
                isDayClicked = isDayClicked(timeInMillis)
                invalidate()
            }

            if (monthGestureListener != null && !isDayClicked) {
                val timeInMillis = getClickedMonth(ev.x.toInt(), ev.y.toInt())
                if (timeInMillis != 0L) {
                    monthGestureListener!!.onMonthLongClick(timeInMillis)
                    invalidate()
                }
            }
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            return true
        }

        override fun onDown(ev: MotionEvent): Boolean {
            return true
        }

        private fun isDayClicked(timeInMillis: Long): Boolean {
            var isDayClicked = false
            if (timeInMillis != 0L) {
                val formatter = DateTimeFormat.forPattern(DAY_PATTERN).withLocale(Locale.ROOT)
                val newSelectedDate = formatter.print(timeInMillis)
                if (isDaySelectionVisuallySticky) {
                    if (selectedDay == newSelectedDate) {
                        selectedDay = ""
                    } else {
                        selectedDay = newSelectedDate
                        monthGestureListener!!.onDayClick(timeInMillis)
                    }
                } else {
                    monthGestureListener!!.onDayClick(timeInMillis)
                }
                isDayClicked = true
            }
            return isDayClicked
        }
    }

    companion object {
        private const val numDays = 7
        private const val DAY_PATTERN = "yyyy-MM-dd"
        private const val DEFAULT_TEXT_SIZE = 10 //sp

        private const val SELECTION_STROKE = 5f
        private const val SELECTION_ALPHA = 255 / 2
    }
}