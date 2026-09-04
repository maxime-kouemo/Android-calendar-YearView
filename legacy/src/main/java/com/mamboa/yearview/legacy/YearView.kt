package com.mamboa.yearview.legacy

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.FontRes
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.FontType
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.TitleGravity
import com.mamboa.yearview.core.datetime.CalendarDate
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider
import com.mamboa.yearview.core.datetime.timeproviders.KotlinxTimeProvider
import com.mamboa.yearview.core.utils.Utils
import java.util.Locale

/**
 * A custom Android [View] that renders a full-year calendar grid.
 *
 * Delegates to:
 * - [CalendarLayoutComputer] for grid layout and calendar data caching
 * - [MonthRenderer] for all Canvas drawing operations
 * - [SelectionManager] for single-day and multi-selection state
 *
 * Created by mamboa on 9/4/2018.
 */
class YearView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ========== Initialization Guard ==========
    /**
     * `false` during construction; set to `true` at the end of `init`.
     * Config setters skip paint rebuilds and `invalidate()` while this is false,
     * because `init` performs a single bulk setup after all configs are assigned.
     */
    private var isInitialized = false

    // ========== PRIMARY: Configuration Objects (Source of Truth) ==========
    var monthConfig: MonthConfig = MonthConfig()
        set(value) {
            val previous = field
            field = value
            if (!isInitialized) return
            // Month names are baked into the cached layout, and `ensureLayout` returns
            // early whenever that cache is populated. Without this the new nameFormat
            // would never reach the screen.
            if (previous.nameFormat != value.nameFormat) {
                layoutComputer.invalidateLayout()
            }
            // Decoded bitmaps are keyed by ImageSource; a new image here would otherwise
            // keep rendering the previous month background until something else cleared
            // the cache. `setMonthBackgroundItemStyle` did this but direct assignment
            // did not, so the two paths disagreed.
            if (previous.backgroundItemStyle.image != value.backgroundItemStyle.image ||
                previous.selectionBackgroundItemStyle.image !=
                value.selectionBackgroundItemStyle.image
            ) {
                renderer.clearBitmapCache()
            }
            rebuildPaints()
            invalidate()
        }

    var todayConfig: DayConfig = DayConfig()
        set(value) {
            field = value
            if (!isInitialized) return
            rebuildPaints()
            invalidate()
        }

    var selectedDayConfig: DayConfig = DayConfig()
        set(value) {
            field = value
            if (!isInitialized) return
            rebuildPaints()
            invalidate()
        }

    var simpleDayConfig: DayConfig = DayConfig()
        set(value) {
            field = value
            if (!isInitialized) return
            rebuildPaints()
            invalidate()
        }

    var weekendDayConfig: DayConfig = DayConfig()
        set(value) {
            field = value
            if (!isInitialized) return
            rebuildPaints()
            invalidate()
        }

    var dayNameConfig: DayNameConfig = DayNameConfig()
        set(value) {
            field = value
            if (!isInitialized) return
            rebuildPaints()
            invalidate()
        }

    // ========== View-Level Properties (Not in configs) ==========
    private var currentYear = -1 // resolved in init via dateTimeProvider
    private var verticalSpacing = 5
    private var horizontalSpacing = 5
    @IntRange(from = 1, to = 12)
    private var columns = 2
    @IntRange(from = 1, to = 12)
    private var rows = 6
    private var viewWidth = 0
    private var viewHeight = 0
    /**
     * The day the week starts on, as an ISO day-of-week value (1 = Monday … 7 = Sunday).
     *
     * Previously settable only from XML, which left the grid's column order unreachable
     * from code. Out-of-range values are coerced rather than rejected so that a bad
     * attribute cannot abort layout inflation from the `View` constructor.
     */
    @get:IntRange(from = 1, to = 7)
    var firstDayOfWeek: Int = 1
        set(@IntRange(from = 1, to = 7) value) {
            val coerced = value.coerceIn(FIRST_DAY_OF_WEEK_RANGE)
            if (field == coerced) return
            field = coerced
            // Column order and every month's first-day offset are baked into the cache.
            layoutComputer.invalidateLayout()
            invalidate()
        }

    private var currentDayPattern = DAY_PATTERN

    private var weekendDays: IntArray? = null

    private var monthGestureListener: MonthGestureListener? = null

    /**
     * When `true`, a tapped day keeps its selected styling until another day is tapped.
     *
     * Replaces `setIfDaySelectionVisuallySticky(Boolean)`, which was a setter with no
     * matching getter sitting next to a read-only property of almost the same name.
     */
    var isDaySelectionVisuallySticky: Boolean
        get() = selectionManager.isDaySelectionVisuallySticky
        set(value) {
            selectionManager.setSticky(value)
            invalidate()
        }

    /**
     * When `true`, dragging across day cells selects a contiguous range.
     *
     * Replaces the `val enableMultiSelection` / `fun enableMultiSelection(Boolean)` pair,
     * whose identical names resolved to different members depending on call syntax.
     */
    var isMultiSelectionEnabled: Boolean
        get() = selectionManager.enableMultiSelection
        set(value) {
            selectionManager.setMultiSelectionEnabled(value)
            invalidate()
        }

    /**
     * When `true`, draws semi-transparent rectangles around each day cell
     * to visualise touch-target areas. Useful during development.
     */
    var debugMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // ========== Delegates ==========
    private val monthBlocks: Array<Rect> = Array(12) { Rect() }

    private val renderer = MonthRenderer(context)
    private val layoutComputer = CalendarLayoutComputer(context, monthBlocks)
    private val selectionManager = SelectionManager()

    /**
     * Exposes every day as an individually focusable accessibility node.
     *
     * Declared here, alongside the other collaborators, because Kotlin runs property
     * initialisers in declaration order and `init` installs this delegate — declaring
     * it further down (next to the accessibility overrides that use it) would leave it
     * `null` at that point.
     */
    private val accessibilityHelper = YearViewAccessibilityHelper(this)

    private var multiSelectionBackgroundItemStyle: LegacyBackgroundStyle = LegacyBackgroundStyle(
        color = Color.CYAN,
        shape = BackgroundShape.Square,
        selectionMargin = 0f,
        image = ImageSource.None,
        colorOpacity = DEFAULT_MULTI_SELECTION_DENSITY
    )

    /** Paints holder — rebuilt when configs change. Initialized inline. */
    private var paints: MonthRenderer.Paints = buildPaints()

    /** Pre-allocated draw context — created on first draw, updated in-place to avoid per-frame allocations. */
    private var drawContext: DrawContext? = null

    private var gestureDetector: GestureDetector

    private var tapTimeoutMs = 0
    private var selectedMonthID = -1

    private var handler: Handler = Handler(Looper.getMainLooper())

    /**
     * The date/time provider used for all calendar calculations.
     * Defaults to [KotlinxTimeProvider]. Set a custom implementation
     * to use java.time, kotlinx-datetime, or any other date/time library.
     */
    var dateTimeProvider: ICalendarDateTimeProvider = KotlinxTimeProvider()
        set(value) {
            field = value
            layoutComputer.invalidateLayout()
            invalidate()
        }

    private var isClearSelectionLaunched = false
    private val clearSelectionRunnable = Runnable {
        selectedMonthID = -1
        isClearSelectionLaunched = false
        invalidate()
    }

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.YearView, defStyleAttr, 0)
            // No catch here: recovery is per-section inside parseAttributes, so a single
            // malformed attribute no longer discards every attribute declared after it.
            try {
                parseAttributes(a)
            } finally {
                a.recycle()
            }
        }

        // Default currentYear to the actual current year if not set via XML
        if (currentYear == -1) {
            currentYear = dateTimeProvider.today().year
        }

        tapTimeoutMs = ViewConfiguration.getTapTimeout()
        paints = buildPaints()

        gestureDetector = GestureDetector(context, CalendarGestureListener())

        // Set initial accessibility description
        contentDescription = buildAccessibilityDescription()
        isFocusable = true

        // Publishes the per-day virtual view tree. Must be installed via ViewCompat
        // rather than View.setAccessibilityDelegate: ExploreByTouchHelper is an
        // AccessibilityDelegateCompat, and only the compat setter bridges it onto the
        // framework delegate.
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)

        isInitialized = true
    }


    /** Builds all paints from the current config objects. Called once in init and on config changes. */
    private fun buildPaints(): MonthRenderer.Paints {
        return MonthRenderer.Paints(
            simpleDayNumber = buildTextPaint(
                simpleDayConfig.textColor,
                simpleDayConfig.textSize,
                simpleDayConfig.fontType,
                simpleDayConfig.fontTypeFace
            ),
            todayText = buildTextPaint(
                todayConfig.textColor,
                todayConfig.textSize,
                todayConfig.fontType,
                todayConfig.fontTypeFace
            ),
            todayBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = todayConfig.backgroundItemStyle.color
                textSize = todayConfig.textSize.toFloat()
                textAlign = DEFAULT_ALIGN
            },
            monthName = buildTextPaint(
                monthConfig.nameTextColor,
                monthConfig.nameTextSize,
                monthConfig.nameFontType,
                monthConfig.nameFontTypeFace
            ),
            todayMonthName = buildTextPaint(
                monthConfig.todayNameTextColor,
                monthConfig.todayNameTextSize,
                monthConfig.todayNameFontType,
                monthConfig.todayNameFontTypeFace
            ),
            weekendDay = buildTextPaint(
                weekendDayConfig.textColor,
                weekendDayConfig.textSize,
                weekendDayConfig.fontType,
                weekendDayConfig.fontTypeFace
            ),
            dayName = buildTextPaint(
                dayNameConfig.textColor,
                dayNameConfig.textSize,
                dayNameConfig.fontType,
                dayNameConfig.fontTypeFace
            ),
            selectedDayText = buildTextPaint(
                selectedDayConfig.textColor,
                selectedDayConfig.textSize,
                selectedDayConfig.fontType,
                selectedDayConfig.fontTypeFace
            ),
            selectedDayBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = selectedDayConfig.backgroundItemStyle.color
                textSize = selectedDayConfig.textSize.toFloat()
                textAlign = DEFAULT_ALIGN
            }
            // No paint for the range-selection background: it is shape-aware and built
            // per-draw from `multiSelectionBackgroundItemStyle` inside the renderer.
        )
    }

    private fun buildTextPaint(
        textColor: Int,
        textSize: Int,
        fontType: FontType,
        typeface: Typeface?
    ): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = textSize.toFloat()
            textAlign = DEFAULT_ALIGN
            val resolvedTypeface = typeface ?: Typeface.DEFAULT
            when (fontType) {
                FontType.BOLD -> setTypeface(Typeface.create(resolvedTypeface, Typeface.BOLD))
                FontType.ITALIC -> setTypeface(Typeface.create(resolvedTypeface, Typeface.ITALIC))
                FontType.BOLD_ITALIC -> setTypeface(
                    Typeface.create(
                        resolvedTypeface,
                        Typeface.BOLD_ITALIC
                    )
                )

                else -> setTypeface(resolvedTypeface)
            }
        }
    }

    /**
     * Rebuilds every [Paint] from the current configuration objects.
     *
     * Paints only — callers that change data feeding the cached calendar layout (month
     * name format, year, first day of week) must additionally call
     * [CalendarLayoutComputer.invalidateLayout].
     */
    private fun rebuildPaints() {
        paints = buildPaints()
        // The reserved month-title height is measured with the month-name paints, so a
        // new text size or typeface changes the grid origin as well as the glyphs.
        layoutComputer.invalidateTitleMetrics()
    }

    // ═══════════════════════════════════════════════════════════════════
    // XML attribute parsing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reads the whole `YearView` attribute set into the configuration objects.
     *
     * Each group is parsed inside its own [parseSection] guard. Previously a single
     * `try`/`catch` wrapped the entire method, so one bad attribute — a malformed
     * colour, a font resource that fails to load, an out-of-range enum — silently
     * discarded every attribute declared after it and left the view half-configured
     * with no indication of how far parsing got. Per-section recovery means a failure
     * costs you exactly one group, which falls back to its documented defaults, and
     * the log names the group that failed.
     */
    private fun parseAttributes(a: TypedArray) {
        parseSection("view") { parseViewLevelAttributes(a) }
        parseSection("multi-selection") { parseMultiSelectionAttributes(a) }
        parseSection("month") { monthConfig = buildMonthConfigFromAttributes(a) }
        parseSection("today") { todayConfig = buildTodayConfigFromAttributes(a) }
        parseSection("selected day") { selectedDayConfig = buildSelectedDayConfigFromAttributes(a) }
        parseSection("simple day") { simpleDayConfig = buildSimpleDayConfigFromAttributes(a) }
        parseSection("weekend") { weekendDayConfig = buildWeekendConfigFromAttributes(a) }
        parseSection("day name") { dayNameConfig = buildDayNameConfigFromAttributes(a) }
    }

    /**
     * Runs [parse], logging and swallowing any failure so the remaining attribute
     * groups still get applied.
     *
     * Only [RuntimeException] is caught — an `Error` (`OutOfMemoryError`,
     * `StackOverflowError`) is never something a view can meaningfully continue
     * through, and swallowing it would just move the crash somewhere less diagnosable.
     */
    private inline fun parseSection(name: String, parse: () -> Unit) {
        try {
            parse()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to parse $name attributes; using defaults for that group", e)
        }
    }

    private fun parseViewLevelAttributes(a: TypedArray) {
        currentYear = a.getInteger(R.styleable.YearView_yv_current_year, currentYear)
        rows = a.getInteger(R.styleable.YearView_yv_rows, rows).coerceIn(1, MAX_MONTHS)
        columns = a.getInteger(R.styleable.YearView_yv_columns, columns).coerceIn(1, MAX_MONTHS)
        warnIfGridCannotShowWholeYear()
        verticalSpacing =
            a.getDimensionPixelSize(R.styleable.YearView_yv_vertical_spacing, verticalSpacing)
        horizontalSpacing =
            a.getDimensionPixelSize(R.styleable.YearView_yv_horizontal_spacing, horizontalSpacing)
        // Coerced rather than passed straight to the property setter, which rejects
        // out-of-range values — an exception here would abort layout inflation.
        firstDayOfWeek = a.getInteger(R.styleable.YearView_yv_first_day_of_week, firstDayOfWeek)
            .coerceIn(FIRST_DAY_OF_WEEK_RANGE)
        currentDayPattern = a.getString(R.styleable.YearView_yv_day_pattern) ?: DAY_PATTERN

        val xmlSticky =
            a.getBoolean(R.styleable.YearView_yv_is_day_selection_visually_sticky, false)
        selectionManager.setSticky(xmlSticky)
        val selectedDayText = a.getString(R.styleable.YearView_yv_selected_day_text)
        if (xmlSticky && selectedDayText != null) {
            selectionManager.selectedDay = try {
                dateTimeProvider.parse(selectedDayText, currentDayPattern, Locale.ROOT)
            } catch (_: Exception) {
                null
            }
        }

        val weekendDaysID = a.getResourceId(R.styleable.YearView_yv_weekend_days, 0)
        if (weekendDaysID > 0) weekendDays = a.resources.getIntArray(weekendDaysID)

        debugMode = a.getBoolean(R.styleable.YearView_yv_debug_mode, false)
    }

    private fun parseMultiSelectionAttributes(a: TypedArray) {
        selectionManager.setMultiSelectionEnabled(
            a.getBoolean(R.styleable.YearView_yv_enable_multi_selection, false)
        )
        multiSelectionBackgroundItemStyle = LegacyBackgroundStyle(
            color = a.getColor(
                R.styleable.YearView_yv_multi_selection_background_color,
                Color.CYAN
            ),
            shape = a.getBackgroundShape(
                shapeIndex = R.styleable.YearView_yv_multi_selection_background_shape,
                roundedRadiusIndex = R.styleable.YearView_yv_multi_selection_rounded_radius,
                starPointsIndex = R.styleable.YearView_yv_multi_selection_star_points,
                starInnerRadiusIndex = R.styleable.YearView_yv_multi_selection_star_inner_radius,
                customShapeIndex = R.styleable.YearView_yv_multi_selection_custom_shape,
                defaultShapeCode = BackgroundShapeCode.SQUARE
            ),
            // Feeds the inset used by the range highlight. Declared but never read before.
            selectionMargin = a.getDimensionPixelSize(
                R.styleable.YearView_yv_multi_selection_background_radius,
                dpToPx(DEFAULT_MULTI_SELECTION_MARGIN_DP)
            ).toFloat(),
            image = ImageSource.None,
            colorOpacity = a.getInteger(
                R.styleable.YearView_yv_multi_selection_background_color_density,
                DEFAULT_MULTI_SELECTION_DENSITY
            )
        )
    }

    private fun buildMonthConfigFromAttributes(a: TypedArray): MonthConfig {
        val titleGravity = a.getEnumOrDefault(
            R.styleable.YearView_yv_month_title_gravity,
            TitleGravity.CENTER
        )
        val marginBelowMonthName = a.getDimensionPixelSize(
            R.styleable.YearView_yv_margin_below_month_name,
            dpToPx(DEFAULT_MARGIN_BELOW_MONTH_NAME_DP)
        )
        val selectionColor = a.getColor(R.styleable.YearView_yv_month_selection_color, Color.BLUE)
        val backgroundColor =
            a.getColor(R.styleable.YearView_yv_month_background_color, Color.TRANSPARENT)
        val backgroundShape = a.getBackgroundShape(
            shapeIndex = R.styleable.YearView_yv_month_background_shape,
            roundedRadiusIndex = R.styleable.YearView_yv_month_background_rounded_radius,
            starPointsIndex = R.styleable.YearView_yv_month_background_star_points,
            starInnerRadiusIndex = R.styleable.YearView_yv_month_background_star_inner_radius,
            customShapeIndex = R.styleable.YearView_yv_month_background_custom_shape,
            defaultShapeCode = BackgroundShapeCode.CIRCLE
        )
        val nameTextColor = a.getColor(R.styleable.YearView_yv_month_name_text_color, Color.BLACK)
        val nameTextSize = a.getDimensionPixelSize(
            R.styleable.YearView_yv_month_name_text_size,
            defaultTextSizePx()
        )
        val nameFontType = a.getEnumOrDefault(
            R.styleable.YearView_yv_month_name_font_type,
            FontType.NORMAL
        )
        val nameFontTypeFace =
            buildFont(a.getResourceId(R.styleable.YearView_yv_month_name_font, 0), a)
        val todayNameTextColor =
            a.getColor(R.styleable.YearView_yv_today_month_name_text_color, Color.BLACK)
        val todayNameTextSize = a.getDimensionPixelSize(
            R.styleable.YearView_yv_today_month_name_text_size,
            defaultTextSizePx()
        )
        val todayNameFontType = a.getEnumOrDefault(
            R.styleable.YearView_yv_today_month_name_font_type,
            FontType.NORMAL
        )
        val todayNameFontTypeFace =
            buildFont(a.getResourceId(R.styleable.YearView_yv_today_month_name_font, 0), a)
        val selectionMargin = a.getDimensionPixelSize(
            R.styleable.YearView_yv_month_selection_margin,
            dpToPx(DEFAULT_MONTH_SELECTION_MARGIN_DP)
        )
        val selectionShape = a.getBackgroundShape(
            shapeIndex = R.styleable.YearView_yv_month_selection_shape,
            roundedRadiusIndex = R.styleable.YearView_yv_month_selection_rounded_radius,
            starPointsIndex = R.styleable.YearView_yv_month_selection_star_points,
            starInnerRadiusIndex = R.styleable.YearView_yv_month_selection_star_inner_radius,
            customShapeIndex = R.styleable.YearView_yv_month_selection_custom_shape,
            defaultShapeCode = BackgroundShapeCode.CIRCLE
        )
        val selectionImage = a.getDrawable(R.styleable.YearView_yv_month_selection_image)
        val selectionMergeType = a.getEnumOrDefault(
            R.styleable.YearView_yv_month_selection_merge_type,
            MergeType.OVERLAY
        )
        val backgroundMergeType = a.getEnumOrDefault(
            R.styleable.YearView_yv_month_background_merge_type,
            MergeType.OVERLAY
        )
        val backgroundImage = a.getDrawable(R.styleable.YearView_yv_month_background_image)

        return MonthConfig(
            titleGravity = titleGravity,
            marginBelowMonthName = marginBelowMonthName,
            selectionBackgroundItemStyle = LegacyBackgroundStyle(
                color = selectionColor,
                shape = selectionShape,
                selectionMargin = selectionMargin.toFloat(),
                image = selectionImage.toImageSource(),
                colorOpacity = a.getInteger(
                    R.styleable.YearView_yv_month_selection_color_density,
                    FULLY_OPAQUE
                ),
                imageOpacity = a.getInteger(
                    R.styleable.YearView_yv_month_selection_image_opacity,
                    FULLY_OPAQUE
                ),
                mergeType = selectionMergeType
            ),
            backgroundItemStyle = LegacyBackgroundStyle(
                color = backgroundColor,
                shape = backgroundShape,
                image = backgroundImage.toImageSource(),
                colorOpacity = a.getInteger(
                    R.styleable.YearView_yv_month_background_color_density,
                    FULLY_OPAQUE
                ),
                imageOpacity = a.getInteger(
                    R.styleable.YearView_yv_month_background_image_opacity,
                    FULLY_OPAQUE
                ),
                mergeType = backgroundMergeType
            ),
            nameTextColor = nameTextColor,
            nameTextSize = nameTextSize,
            nameFontType = nameFontType,
            nameFontTypeFace = nameFontTypeFace,
            todayNameTextColor = todayNameTextColor,
            todayNameTextSize = todayNameTextSize,
            todayNameFontType = todayNameFontType,
            todayNameFontTypeFace = todayNameFontTypeFace
        )
    }

    private fun buildTodayConfigFromAttributes(a: TypedArray): DayConfig {
        val textColor = a.getColor(R.styleable.YearView_yv_today_text_color, Color.WHITE)
        val textSize =
            a.getDimensionPixelSize(R.styleable.YearView_yv_today_text_size, defaultTextSizePx())
        val fontType = a.getEnumOrDefault(R.styleable.YearView_yv_today_font_type, FontType.NORMAL)
        val fontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_yv_today_font, 0), a)
        val bgColor = a.getColor(R.styleable.YearView_yv_today_background_color, Color.RED)
        val bgRadius = a.getDimensionPixelSize(
            R.styleable.YearView_yv_today_background_radius,
            dpToPx(DEFAULT_DAY_BACKGROUND_RADIUS_DP)
        )
        val bgImage = a.getDrawable(R.styleable.YearView_yv_today_background_image)
        val bgShape = a.getBackgroundShape(
            shapeIndex = R.styleable.YearView_yv_today_background_shape,
            roundedRadiusIndex = R.styleable.YearView_yv_today_rounded_radius,
            starPointsIndex = R.styleable.YearView_yv_today_star_points,
            starInnerRadiusIndex = R.styleable.YearView_yv_today_star_inner_radius,
            customShapeIndex = R.styleable.YearView_yv_today_custom_shape,
            defaultShapeCode = BackgroundShapeCode.CIRCLE
        )

        return DayConfig(
            backgroundItemStyle = LegacyBackgroundStyle(
                color = bgColor,
                shape = bgShape,
                image = bgImage.toImageSource(),
                colorOpacity = a.getInteger(
                    R.styleable.YearView_yv_today_background_color_density,
                    FULLY_OPAQUE
                ),
                imageOpacity = a.getInteger(
                    R.styleable.YearView_yv_today_background_image_opacity,
                    FULLY_OPAQUE
                )
            ),
            textColor = textColor,
            textSize = textSize,
            fontType = fontType,
            fontTypeFace = fontTypeFace,
            backgroundRadius = DayConfig.pixelsToMultiplier(bgRadius, textSize)
        )
    }

    private fun buildSelectedDayConfigFromAttributes(a: TypedArray): DayConfig {
        val textColor = a.getColor(R.styleable.YearView_yv_selected_day_text_color, Color.WHITE)
        val textSize = a.getDimensionPixelSize(
            R.styleable.YearView_yv_selected_day_text_size,
            defaultTextSizePx()
        )
        val fontType =
            a.getEnumOrDefault(R.styleable.YearView_yv_selected_day_font_type, FontType.NORMAL)
        val fontTypeFace =
            buildFont(a.getResourceId(R.styleable.YearView_yv_selected_day_font, 0), a)
        val bgColor = a.getColor(R.styleable.YearView_yv_selected_day_background_color, Color.BLUE)
        val bgShape = a.getBackgroundShape(
            shapeIndex = R.styleable.YearView_yv_selected_day_background_shape,
            roundedRadiusIndex = R.styleable.YearView_yv_selected_day_rounded_radius,
            starPointsIndex = R.styleable.YearView_yv_selected_day_star_points,
            starInnerRadiusIndex = R.styleable.YearView_yv_selected_day_star_inner_radius,
            customShapeIndex = R.styleable.YearView_yv_selected_day_custom_shape,
            defaultShapeCode = BackgroundShapeCode.CIRCLE
        )
        val bgRadius = a.getDimensionPixelSize(
            R.styleable.YearView_yv_selected_day_background_radius,
            dpToPx(DEFAULT_DAY_BACKGROUND_RADIUS_DP)
        )
        val bgImage = a.getDrawable(R.styleable.YearView_yv_selected_day_background_image)

        return DayConfig(
            backgroundItemStyle = LegacyBackgroundStyle(
                color = bgColor,
                shape = bgShape,
                image = bgImage.toImageSource(),
                colorOpacity = a.getInteger(
                    R.styleable.YearView_yv_selected_day_background_color_density,
                    FULLY_OPAQUE
                ),
                imageOpacity = a.getInteger(
                    R.styleable.YearView_yv_selected_day_background_image_opacity,
                    FULLY_OPAQUE
                )
            ),
            textColor = textColor,
            textSize = textSize,
            fontType = fontType,
            fontTypeFace = fontTypeFace,
            backgroundRadius = DayConfig.pixelsToMultiplier(bgRadius, textSize)
        )
    }

    private fun buildSimpleDayConfigFromAttributes(a: TypedArray): DayConfig {
        val textColor = a.getColor(R.styleable.YearView_yv_simple_day_text_color, Color.BLACK)
        val textSize = a.getDimensionPixelSize(
            R.styleable.YearView_yv_simple_day_text_size,
            defaultTextSizePx()
        )
        val fontType =
            a.getEnumOrDefault(R.styleable.YearView_yv_simple_day_font_type, FontType.NORMAL)
        val fontTypeFace =
            buildFont(a.getResourceId(R.styleable.YearView_yv_simple_day_font, 0), a)
        return DayConfig(
            backgroundItemStyle = LegacyBackgroundStyle(
                color = Color.TRANSPARENT,
                shape = BackgroundShape.Square
            ),
            textColor = textColor,
            textSize = textSize,
            fontType = fontType,
            fontTypeFace = fontTypeFace,
            backgroundRadius = 1f
        )
    }

    private fun buildWeekendConfigFromAttributes(a: TypedArray): DayConfig {
        val textColor = a.getColor(R.styleable.YearView_yv_weekend_text_color, Color.BLACK)
        val textSize =
            a.getDimensionPixelSize(R.styleable.YearView_yv_weekend_text_size, defaultTextSizePx())
        val fontType =
            a.getEnumOrDefault(R.styleable.YearView_yv_weekend_font_type, FontType.NORMAL)
        val fontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_yv_weekend_font, 0), a)
        return DayConfig(
            backgroundItemStyle = LegacyBackgroundStyle(
                color = Color.TRANSPARENT,
                shape = BackgroundShape.Square
            ),
            textColor = textColor,
            textSize = textSize,
            fontType = fontType,
            fontTypeFace = fontTypeFace,
            backgroundRadius = 1f
        )
    }

    private fun buildDayNameConfigFromAttributes(a: TypedArray): DayNameConfig {
        // getColor, not getInteger: a `format="color"` attribute given as `@color/foo`
        // resolves to a resource id under getInteger, which is not a colour at all.
        val textColor = a.getColor(R.styleable.YearView_yv_day_name_text_color, Color.BLACK)
        val textSize =
            a.getDimensionPixelSize(R.styleable.YearView_yv_day_name_text_size, defaultTextSizePx())
        val fontType =
            a.getEnumOrDefault(R.styleable.YearView_yv_day_name_font_type, FontType.NORMAL)
        val fontTypeFace = buildFont(a.getResourceId(R.styleable.YearView_yv_day_name_font, 0), a)
        val transcendsWeekend =
            a.getBoolean(R.styleable.YearView_yv_name_week_transcend_weekend, false)
        return DayNameConfig(
            textColor = textColor,
            textSize = textSize,
            fontType = fontType,
            fontTypeFace = fontTypeFace,
            transcendsWeekend = transcendsWeekend
        )
    }

    private fun defaultTextSizePx(): Int =
        (TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            DEFAULT_TEXT_SIZE.toFloat(),
            resources.displayMetrics
        ) + 0.5).toInt()

    /**
     * Converts a dp value to whole pixels for use as an attribute default.
     *
     * Attribute *values* are resolved by `getDimensionPixelSize`, which already scales
     * them; the defaults handed to it must be scaled the same way or an unset attribute
     * would silently mean "raw pixels" while a set one meant dp.
     */
    @Px
    private fun dpToPx(dp: Float): Int = (TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics
    ) + 0.5f).toInt()

    private fun buildFont(@FontRes fontID: Int, typeArray: TypedArray?): Typeface? =
        if (fontID == 0 || typeArray == null) null else typeArray.resources.getFont(fontID)

    // ═══════════════════════════════════════════════════════════════════
    // View lifecycle
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reports a content-derived size so `wrap_content` is usable.
     *
     * Without this the default [View.onMeasure] resolves `wrap_content` to the smallest
     * size the parent allows — typically zero — and the calendar simply does not appear.
     * The desired size is the grid dimensions multiplied by the space one month needs at
     * the currently configured text sizes; both axes still go through `resolveSize`, so
     * an explicit `match_parent` or fixed size always wins.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = paddingLeft + paddingRight +
            columns * (minimumMonthWidth() + horizontalSpacing)
        val desiredHeight = paddingTop + paddingBottom +
            rows * (minimumMonthHeight() + verticalSpacing)
        setMeasuredDimension(
            resolveSize(desiredWidth.coerceAtLeast(suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(desiredHeight.coerceAtLeast(suggestedMinimumHeight), heightMeasureSpec)
        )
    }

    /** Width one month block needs: seven day columns at the widest configured day text. */
    private fun minimumMonthWidth(): Int {
        val widestDay = maxOf(
            paints.simpleDayNumber.measureText(WIDEST_DAY_SAMPLE),
            paints.todayText.measureText(WIDEST_DAY_SAMPLE),
            paints.selectedDayText.measureText(WIDEST_DAY_SAMPLE),
            paints.weekendDay.measureText(WIDEST_DAY_SAMPLE),
            paints.dayName.measureText(WIDEST_DAY_SAMPLE)
        )
        return (widestDay * CELL_SPACING_FACTOR * MonthGridGeometry.DAYS_PER_WEEK).toInt()
    }

    /** Height one month block needs: the title area plus every grid row. */
    private fun minimumMonthHeight(): Int {
        val tallestRow = maxOf(
            paints.simpleDayNumber.fontMetrics.lineHeight(),
            paints.todayText.fontMetrics.lineHeight(),
            paints.selectedDayText.fontMetrics.lineHeight(),
            paints.weekendDay.fontMetrics.lineHeight(),
            paints.dayName.fontMetrics.lineHeight()
        )
        val titleHeight = paints.monthName.fontMetrics.lineHeight() * MONTH_TITLE_LINES +
            monthConfig.marginBelowMonthName
        return (titleHeight + tallestRow * CELL_SPACING_FACTOR * MonthGridGeometry.TOTAL_GRID_ROWS)
            .toInt()
    }

    private fun Paint.FontMetrics.lineHeight(): Float = descent - ascent

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(width, height, oldw, oldh)
        viewWidth = width
        viewHeight = height
        layoutComputer.splitViewInBlocks(
            viewWidth,
            viewHeight,
            columns,
            rows,
            horizontalSpacing,
            verticalSpacing
        )
    }

    /**
     * Overridden so the click sound and the `TYPE_VIEW_CLICKED` accessibility event are
     * emitted for taps that land on a day or a month. Callers are in [onTouchEvent] and
     * [CalendarGestureListener.handleTap]; the base implementation does all the work.
     *
     * The override looks redundant but is required: lint's `ClickableViewAccessibility`
     * check only considers a custom [onTouchEvent] accessible when the view also
     * declares [performClick].
     */
    @Suppress("RedundantOverride")
    override fun performClick(): Boolean = super.performClick()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Clear any stale runnables from a previous attach/detach cycle
        handler.removeCallbacksAndMessages(null)
        isClearSelectionLaunched = false
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        renderer.clearBitmapCache()
        super.onDetachedFromWindow()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Accessibility
    // ═══════════════════════════════════════════════════════════════════

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        accessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = ACCESSIBILITY_CLASS_NAME
        info.contentDescription = buildAccessibilityDescription()
    }

    // ── Bridge for YearViewAccessibilityHelper ───────────────────────────
    //
    // The helper lives in its own file so the traversal logic stays readable, which
    // means it needs a small, explicitly internal surface onto the view's geometry
    // and selection state. Everything below is `internal`: none of it widens the
    // published API.

    /** Resolves a touch point to a date, or `null` when it misses every day cell. */
    internal fun dayAt(x: Int, y: Int): CalendarDate? = getClickedDay(x, y)

    /**
     * Invokes [action] once per day that currently has a positioned cell, in reading
     * order. Bounded by the number of month blocks the grid can actually show, so a
     * grid smaller than a year does not advertise days that are never drawn.
     */
    internal inline fun forEachVisibleDay(action: (monthIndex: Int, dayOfMonth: Int) -> Unit) {
        val layout = layoutComputer.cachedMonthData ?: return
        for (monthIndex in 0 until layoutComputer.blockCount) {
            for (day in 1..layout[monthIndex].daysInMonth) {
                action(monthIndex, day)
            }
        }
    }

    /** Spoken label for a day: the formatted date, plus its today/selected state. */
    internal fun describeDay(monthIndex: Int, dayOfMonth: Int): CharSequence {
        val date = CalendarDate(currentYear, monthIndex + 1, dayOfMonth)
        val locale = Utils.getCurrentLocale(context)
        val formatted = dateTimeProvider.format(date, currentDayPattern, locale)
        val base = resources.getString(R.string.yearview_a11y_day, formatted)

        val isToday = currentYear == layoutComputer.cachedTodayYear &&
                monthIndex + 1 == layoutComputer.cachedTodayMonth &&
                dayOfMonth == layoutComputer.cachedTodayDay
        return when {
            isToday -> resources.getString(R.string.yearview_a11y_day_today, base)
            isDaySelected(monthIndex, dayOfMonth) ->
                resources.getString(R.string.yearview_a11y_day_selected, base)

            else -> base
        }
    }

    /** Whether a day is the selected day or falls inside the selected range. */
    internal fun isDaySelected(monthIndex: Int, dayOfMonth: Int): Boolean {
        val date = CalendarDate(currentYear, monthIndex + 1, dayOfMonth)
        return selectionManager.selectedDay == date ||
                selectionManager.isDayInRange(currentYear, monthIndex, dayOfMonth)
    }

    /**
     * Writes the on-screen bounds of a day cell into [out].
     *
     * Derived from [MonthGridGeometry] — the same source the renderer and the
     * hit-test use — so the accessibility rectangle a screen reader highlights always
     * coincides with the cell the user sees and can touch.
     *
     * @return `false` (leaving [out] untouched) when the cell is not currently
     *   positioned, e.g. before the first layout pass.
     */
    internal fun dayCellBoundsInto(out: Rect, monthIndex: Int, dayOfMonth: Int): Boolean {
        val layout = layoutComputer.cachedMonthData ?: return false
        if (monthIndex !in 0 until layoutComputer.blockCount) return false

        val block = monthBlocks[monthIndex]
        val contentTop = block.top + layoutComputer.monthTitleOffsets[monthIndex]
        val contentHeight = block.bottom - contentTop
        val xUnit = MonthGridGeometry.cellWidth(block.width())
        val yUnit = MonthGridGeometry.cellHeight(contentHeight)
        if (xUnit <= 0 || yUnit <= 0) return false

        val firstDayOffset = layout[monthIndex].firstDayOffset
        val row = MonthGridGeometry.rowOf(dayOfMonth, firstDayOffset)
        val column = MonthGridGeometry.visualColumn(
            MonthGridGeometry.columnOf(dayOfMonth, firstDayOffset),
            layoutDirection == LAYOUT_DIRECTION_RTL
        )
        val gridLeft = MonthGridGeometry.gridOriginX(block.left, block.width())

        out.set(
            MonthGridGeometry.cellLeft(gridLeft, xUnit, column),
            MonthGridGeometry.cellTop(contentTop, yUnit, row),
            MonthGridGeometry.cellLeft(gridLeft, xUnit, column + 1),
            MonthGridGeometry.cellBottom(contentTop, yUnit, row)
        )
        return true
    }

    /**
     * Performs the accessibility equivalent of tapping a day.
     *
     * Routed through the very same selection path as a real tap so that a screen
     * reader user and a sighted user cannot end up with divergent state.
     */
    internal fun activateDay(monthIndex: Int, dayOfMonth: Int): Boolean {
        val date = CalendarDate(currentYear, monthIndex + 1, dayOfMonth)
        return applyDayTap(date, isLongPress = false)
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.className = ACCESSIBILITY_CLASS_NAME
    }

    /**
     * Builds a description string for screen readers that summarizes the
     * current state: year, selected day (if any), and selected range (if any).
     */
    private fun buildAccessibilityDescription(): String {
        val sb = StringBuilder()
        // Passed as a String, not an Int: %d applies locale grouping, so 2026 is read
        // out as "2 026" in locales such as fr.
        sb.append(resources.getString(R.string.yearview_a11y_calendar, currentYear.toString()))

        selectionManager.selectedDay?.let { day ->
            val locale = Utils.getCurrentLocale(context)
            val formatted = dateTimeProvider.format(day, currentDayPattern, locale)
            sb.append(". ")
            sb.append(resources.getString(R.string.yearview_a11y_selected_day, formatted))
        }

        val rangeStart = selectionManager.rangeStart
        val rangeEnd = selectionManager.rangeEnd
        if (rangeStart != null && rangeEnd != null) {
            val locale = Utils.getCurrentLocale(context)
            val startFormatted = dateTimeProvider.format(rangeStart, currentDayPattern, locale)
            val endFormatted = dateTimeProvider.format(rangeEnd, currentDayPattern, locale)
            sb.append(". ")
            sb.append(resources.getString(R.string.yearview_a11y_range, startFormatted, endFormatted))
        }

        return sb.toString()
    }

    /**
     * Announces a selection change to screen readers and updates the
     * content description for non-event-driven accessibility queries.
     */
    private fun announceSelectionChange(announcement: String) {
        contentDescription = buildAccessibilityDescription()
        announceForAccessibility(announcement)
    }

    /**
     * Notifies the listener of a completed range and announces it.
     *
     * Shared by the two paths that can complete a range — a second tap and the release
     * of a drag — so both stay in sync.
     */
    private fun announceRangeSelected(start: CalendarDate, end: CalendarDate) {
        monthGestureListener?.onRangeSelected(start, end)
        val locale = Utils.getCurrentLocale(context)
        announceSelectionChange(
            resources.getString(
                R.string.yearview_a11y_range,
                dateTimeProvider.format(start, currentDayPattern, locale),
                dateTimeProvider.format(end, currentDayPattern, locale)
            )
        )
    }

    /**
     * Brings the cached calendar data and the month-title offsets up to date.
     *
     * Both `onDraw` and hit-testing depend on these, so it is idempotent and cheap to
     * call from either: the layout is rebuilt only when invalidated and the offsets only
     * when the title metrics changed.
     */
    private fun ensureCalendarMetrics() {
        layoutComputer.ensureLayout(
            dateTimeProvider,
            currentYear,
            firstDayOfWeek,
            monthConfig.nameFormat
        )
        layoutComputer.ensureTitleOffsets(
            monthNamePaint = paints.monthName,
            todayMonthNamePaint = paints.todayMonthName,
            marginBelowMonthName = monthConfig.marginBelowMonthName,
            currentYear = currentYear
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ensureCalendarMetrics()
        val layout = layoutComputer.cachedMonthData ?: return

        val ctx = drawContext
        if (ctx == null) {
            drawContext = DrawContext.create(
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
                cachedDayHeaderNames = layoutComputer.cachedDayHeaderNames,
                firstDayOfWeek = firstDayOfWeek,
                currentYear = currentYear,
                selectionManager = selectionManager,
                layoutComputer = layoutComputer,
                weekendDays = weekendDays
            )
        } else {
            ctx.update(
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
                cachedDayHeaderNames = layoutComputer.cachedDayHeaderNames,
                firstDayOfWeek = firstDayOfWeek,
                currentYear = currentYear,
                selectionManager = selectionManager,
                layoutComputer = layoutComputer,
                weekendDays = weekendDays
            )
        }
        val activeContext = drawContext!!
        renderer.drawMonths(canvas, activeContext)

        // Draw debug touch-target overlay
        if (debugMode) {
            renderer.drawDebugOverlay(canvas, activeContext)
        }

        // Draw month-selection highlight + schedule auto-clear
        if (selectedMonthID > -1) {
            renderer.drawSelection(
                canvas,
                selectedMonthID,
                monthBlocks,
                monthConfig
            )
            if (!isClearSelectionLaunched) {
                handler.removeCallbacks(clearSelectionRunnable)
                handler.postDelayed(clearSelectionRunnable, (tapTimeoutMs * 2).toLong())
                isClearSelectionLaunched = true
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).apply {
            year = currentYear
            savedSelectedDay = selectionManager.selectedDay
            savedRangeStart = selectionManager.rangeStart
            savedRangeEnd = selectionManager.rangeEnd
            savedIsSticky = selectionManager.isDaySelectionVisuallySticky
            savedMultiSelectionEnabled = selectionManager.enableMultiSelection
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state); return
        }
        super.onRestoreInstanceState(state.superState)
        currentYear = state.year
        selectionManager.setSticky(state.savedIsSticky)
        selectionManager.setMultiSelectionEnabled(state.savedMultiSelectionEnabled)
        selectionManager.selectedDay = state.savedSelectedDay
        selectionManager.restoreRange(state.savedRangeStart, state.savedRangeEnd)
        cancelPendingSelectionClear()
        layoutComputer.invalidateLayout()
        requestLayout()
    }

    private class SavedState : BaseSavedState {
        var year: Int = 0
        var savedSelectedDay: CalendarDate? = null
        var savedRangeStart: CalendarDate? = null
        var savedRangeEnd: CalendarDate? = null
        var savedIsSticky: Boolean = false
        var savedMultiSelectionEnabled: Boolean = false

        constructor(superState: Parcelable?) : super(superState)
        constructor(source: Parcel) : super(source) {
            year = source.readInt()
            savedSelectedDay = readCalendarDate(source)
            savedRangeStart = readCalendarDate(source)
            savedRangeEnd = readCalendarDate(source)
            savedIsSticky = source.readInt() == 1
            savedMultiSelectionEnabled = source.readInt() == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(year)
            writeCalendarDate(out, savedSelectedDay)
            writeCalendarDate(out, savedRangeStart)
            writeCalendarDate(out, savedRangeEnd)
            out.writeInt(if (savedIsSticky) 1 else 0)
            out.writeInt(if (savedMultiSelectionEnabled) 1 else 0)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)
                override fun newArray(size: Int) = arrayOfNulls<SavedState>(size)
            }

            private fun writeCalendarDate(out: Parcel, date: CalendarDate?) {
                if (date != null) {
                    out.writeInt(date.year); out.writeInt(date.month); out.writeInt(date.day)
                } else {
                    out.writeInt(-1); out.writeInt(-1); out.writeInt(-1)
                }
            }

            /** Reads a nullable [CalendarDate] written by [writeCalendarDate]. */
            private fun readCalendarDate(source: Parcel): CalendarDate? {
                val y = source.readInt()
                val m = source.readInt()
                val d = source.readInt()
                return if (y == -1) null else CalendarDate(y, m, d)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public API — setters (consolidated: config objects are the primary API)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Set the weekend days according to ISO day-of-week values (1=Monday..7=Sunday).
     */
    fun setWeekendDays(newWeekendDays: IntArray?) {
        weekendDays = newWeekendDays
        invalidate()
    }

    fun setVerticalSpacing(@DimenRes verticalSpacingRes: Int) {
        this.verticalSpacing = resources.getDimensionPixelSize(verticalSpacingRes)
        layoutComputer.splitViewInBlocks(
            viewWidth,
            viewHeight,
            columns,
            rows,
            horizontalSpacing,
            verticalSpacing
        )
        requestLayout()
        invalidate()
    }

    fun setHorizontalSpacing(@DimenRes horizontalSpacingRes: Int) {
        this.horizontalSpacing = resources.getDimensionPixelSize(horizontalSpacingRes)
        layoutComputer.splitViewInBlocks(
            viewWidth,
            viewHeight,
            columns,
            rows,
            horizontalSpacing,
            verticalSpacing
        )
        requestLayout()
        invalidate()
    }

    /**
     * Sets the number of grid columns.
     *
     * A grid larger than twelve cells is allowed — the surplus slots are simply left
     * empty, which is how configurations such as 5 × 3 can still show all twelve months.
     * A grid *smaller* than twelve can only display the first `rows × columns` months
     * and logs a warning.
     */
    fun setColumns(@IntRange(from = 1, to = 12) columns: Int) {
        require(columns in 1..MAX_MONTHS) { "columns ($columns) must be in 1..$MAX_MONTHS" }
        this.columns = columns
        warnIfGridCannotShowWholeYear()
        layoutComputer.splitViewInBlocks(
            viewWidth,
            viewHeight,
            columns,
            rows,
            horizontalSpacing,
            verticalSpacing
        )
        requestLayout()
        invalidate()
    }

    /**
     * Sets the number of grid rows. See [setColumns] for how grid sizes other than
     * twelve cells are handled.
     */
    fun setRows(@IntRange(from = 1, to = 12) rows: Int) {
        require(rows in 1..MAX_MONTHS) { "rows ($rows) must be in 1..$MAX_MONTHS" }
        this.rows = rows
        warnIfGridCannotShowWholeYear()
        layoutComputer.splitViewInBlocks(
            viewWidth,
            viewHeight,
            columns,
            rows,
            horizontalSpacing,
            verticalSpacing
        )
        requestLayout()
        invalidate()
    }

    /**
     * Logs when the configured grid has fewer than twelve cells.
     *
     * Such a grid cannot position every month, so the trailing months are not drawn.
     * This is a deliberate, visible degradation: the previous behaviour clamped
     * `columns` to `12 / rows`, which silently dropped months for row counts that do
     * not divide twelve (5 rows became a 5 × 2 grid showing only ten months) while
     * still asking the renderer to draw all twelve into unpopulated rectangles.
     */
    private fun warnIfGridCannotShowWholeYear() {
        val capacity = rows * columns
        if (capacity < MAX_MONTHS) {
            android.util.Log.w(
                TAG,
                "rows ($rows) x columns ($columns) = $capacity cells cannot fit " +
                    "$MAX_MONTHS months; only the first $capacity will be displayed."
            )
        }
    }

    // ── Convenience setters (delegate to config copy) ────────────────

    fun setMonthSelectionColor(@ColorRes res: Int) {
        monthConfig = monthConfig.copy(
            selectionBackgroundItemStyle = monthConfig.selectionBackgroundItemStyle.copy(
                color = ContextCompat.getColor(
                    context,
                    res
                )
            )
        )
    }

    fun setTodayTextColor(@ColorRes res: Int) {
        todayConfig = todayConfig.copy(textColor = ContextCompat.getColor(context, res))
    }

    fun setTodayBackgroundColor(@ColorRes res: Int) {
        todayConfig = todayConfig.copy(
            backgroundItemStyle = todayConfig.backgroundItemStyle.copy(
                color = ContextCompat.getColor(
                    context,
                    res
                )
            )
        )
    }

    fun setSimpleDayTextColor(@ColorRes res: Int) {
        simpleDayConfig = simpleDayConfig.copy(textColor = ContextCompat.getColor(context, res))
    }

    fun setWeekendTextColor(@ColorRes res: Int) {
        weekendDayConfig = weekendDayConfig.copy(textColor = ContextCompat.getColor(context, res))
    }

    fun setTodayBackgroundRadius(@DimenRes res: Int) {
        todayConfig = todayConfig.copy(
            backgroundRadius = DayConfig.pixelsToMultiplier(
                resources.getDimensionPixelSize(res), todayConfig.textSize
            )
        )
    }

    fun setDayNameTextColor(@ColorRes res: Int) {
        dayNameConfig = dayNameConfig.copy(textColor = ContextCompat.getColor(context, res))
    }

    fun setMonthNameTextColor(@ColorRes res: Int) {
        monthConfig = monthConfig.copy(nameTextColor = ContextCompat.getColor(context, res))
    }

    fun setTodayMonthNameTextColor(@ColorRes res: Int) {
        monthConfig = monthConfig.copy(todayNameTextColor = ContextCompat.getColor(context, res))
    }

    fun setMonthSelectionMargin(@DimenRes res: Int) {
        monthConfig = monthConfig.copy(
            selectionBackgroundItemStyle = monthConfig.selectionBackgroundItemStyle.copy(
                selectionMargin = resources.getDimensionPixelSize(res).toFloat()
            )
        )
    }

    fun setMonthNameFontTypeFace(tf: Typeface?) {
        monthConfig = monthConfig.copy(nameFontTypeFace = tf)
    }

    fun setWeekendFontTypeFace(tf: Typeface?) {
        weekendDayConfig = weekendDayConfig.copy(fontTypeFace = tf)
    }

    fun setDayNameFontTypeFace(tf: Typeface?) {
        dayNameConfig = dayNameConfig.copy(fontTypeFace = tf)
    }

    fun setTodayFontTypeFace(tf: Typeface?) {
        todayConfig = todayConfig.copy(fontTypeFace = tf)
    }

    fun setSimpleDayFontTypeFace(tf: Typeface?) {
        simpleDayConfig = simpleDayConfig.copy(fontTypeFace = tf)
    }

    fun setTodayMonthNameFontTypeFace(tf: Typeface?) {
        monthConfig = monthConfig.copy(todayNameFontTypeFace = tf)
    }

    fun setSimpleDayTextSize(@DimenRes res: Int) {
        simpleDayConfig = simpleDayConfig.copy(textSize = resources.getDimensionPixelSize(res))
    }

    fun setWeekendTextSize(@DimenRes res: Int) {
        weekendDayConfig = weekendDayConfig.copy(textSize = resources.getDimensionPixelSize(res))
    }

    fun setTodayTextSize(@DimenRes res: Int) {
        todayConfig = todayConfig.copy(textSize = resources.getDimensionPixelSize(res))
    }

    fun setDayNameTextSize(@DimenRes res: Int) {
        dayNameConfig = dayNameConfig.copy(textSize = resources.getDimensionPixelSize(res))
    }

    fun setMonthNameTextSize(@DimenRes res: Int) {
        monthConfig = monthConfig.copy(nameTextSize = resources.getDimensionPixelSize(res))
    }

    fun setTodayMonthNameTextSize(@DimenRes res: Int) {
        monthConfig = monthConfig.copy(todayNameTextSize = resources.getDimensionPixelSize(res))
    }

    fun setMonthTitleGravity(gravity: TitleGravity) {
        monthConfig = monthConfig.copy(titleGravity = gravity)
    }

    fun setTodayBackgroundShape(shape: BackgroundShape) {
        todayConfig =
            todayConfig.copy(backgroundItemStyle = todayConfig.backgroundItemStyle.copy(shape = shape))
    }

    fun setSelectedDayBackgroundShape(shape: BackgroundShape) {
        selectedDayConfig = selectedDayConfig.copy(
            backgroundItemStyle = selectedDayConfig.backgroundItemStyle.copy(shape = shape)
        )
    }

    fun setTodayFontType(ft: FontType) {
        todayConfig = todayConfig.copy(fontType = ft)
    }

    fun setMonthNameFontType(ft: FontType) {
        monthConfig = monthConfig.copy(nameFontType = ft)
    }

    fun setTodayMonthNameFontType(ft: FontType) {
        monthConfig = monthConfig.copy(todayNameFontType = ft)
    }

    fun setDayNameFontType(ft: FontType) {
        dayNameConfig = dayNameConfig.copy(fontType = ft)
    }

    fun setWeekendNameFontType(ft: FontType) {
        weekendDayConfig = weekendDayConfig.copy(fontType = ft)
    }

    fun setSimpleDayFontType(ft: FontType) {
        simpleDayConfig = simpleDayConfig.copy(fontType = ft)
    }

    fun setSelectedDayFontType(ft: FontType) {
        selectedDayConfig = selectedDayConfig.copy(fontType = ft)
    }

    fun setDayNameTranscendsWeekend(transcends: Boolean) {
        dayNameConfig = dayNameConfig.copy(transcendsWeekend = transcends)
    }

    // ── Raw-value overloads (@ColorInt / @Px) ────────────────────────
    // These complement the @ColorRes / @DimenRes setters above,
    // accepting already-resolved values for programmatic use.

    fun setMonthSelectionColorInt(@ColorInt color: Int) {
        monthConfig = monthConfig.copy(
            selectionBackgroundItemStyle = monthConfig.selectionBackgroundItemStyle.copy(color = color)
        )
    }

    fun setTodayTextColorInt(@ColorInt color: Int) {
        todayConfig = todayConfig.copy(textColor = color)
    }

    fun setTodayBackgroundColorInt(@ColorInt color: Int) {
        todayConfig = todayConfig.copy(
            backgroundItemStyle = todayConfig.backgroundItemStyle.copy(color = color)
        )
    }

    fun setSimpleDayTextColorInt(@ColorInt color: Int) {
        simpleDayConfig = simpleDayConfig.copy(textColor = color)
    }

    fun setWeekendTextColorInt(@ColorInt color: Int) {
        weekendDayConfig = weekendDayConfig.copy(textColor = color)
    }

    fun setDayNameTextColorInt(@ColorInt color: Int) {
        dayNameConfig = dayNameConfig.copy(textColor = color)
    }

    fun setMonthNameTextColorInt(@ColorInt color: Int) {
        monthConfig = monthConfig.copy(nameTextColor = color)
    }

    fun setTodayMonthNameTextColorInt(@ColorInt color: Int) {
        monthConfig = monthConfig.copy(todayNameTextColor = color)
    }

    fun setSimpleDayTextSizePx(@Px sizePx: Int) {
        simpleDayConfig = simpleDayConfig.copy(textSize = sizePx)
    }

    fun setWeekendTextSizePx(@Px sizePx: Int) {
        weekendDayConfig = weekendDayConfig.copy(textSize = sizePx)
    }

    fun setTodayTextSizePx(@Px sizePx: Int) {
        todayConfig = todayConfig.copy(textSize = sizePx)
    }

    fun setDayNameTextSizePx(@Px sizePx: Int) {
        dayNameConfig = dayNameConfig.copy(textSize = sizePx)
    }

    fun setMonthNameTextSizePx(@Px sizePx: Int) {
        monthConfig = monthConfig.copy(nameTextSize = sizePx)
    }

    fun setTodayMonthNameTextSizePx(@Px sizePx: Int) {
        monthConfig = monthConfig.copy(todayNameTextSize = sizePx)
    }

    fun setVerticalSpacingPx(@Px spacingPx: Int) {
        this.verticalSpacing = spacingPx
        layoutComputer.splitViewInBlocks(viewWidth, viewHeight, columns, rows, horizontalSpacing, verticalSpacing)
        requestLayout()
        invalidate()
    }

    fun setHorizontalSpacingPx(@Px spacingPx: Int) {
        this.horizontalSpacing = spacingPx
        layoutComputer.splitViewInBlocks(viewWidth, viewHeight, columns, rows, horizontalSpacing, verticalSpacing)
        requestLayout()
        invalidate()
    }

    fun setMonthSelectionMarginPx(@Px marginPx: Int) {
        monthConfig = monthConfig.copy(
            selectionBackgroundItemStyle = monthConfig.selectionBackgroundItemStyle.copy(
                selectionMargin = marginPx.toFloat()
            )
        )
    }

    fun setTodayBackgroundRadiusPx(@Px radiusPx: Int) {
        todayConfig = todayConfig.copy(
            backgroundRadius = DayConfig.pixelsToMultiplier(radiusPx, todayConfig.textSize)
        )
    }

    fun setMultiSelectionBackgroundItemStyle(style: LegacyBackgroundStyle) {
        this.multiSelectionBackgroundItemStyle = style
        rebuildPaints()
        invalidate()
    }

    fun getMultiSelectionBackgroundItemStyle(): LegacyBackgroundStyle =
        multiSelectionBackgroundItemStyle

    fun clearMultiSelection() {
        selectionManager.clearRange()
        invalidate()
    }

    /**
     * The currently selected day, or `null` when nothing is selected.
     *
     * Replaces the previous `getSelectedDay(): Long` / `setSelectedDay(Long)` pair (which
     * used `-1L` as a "no selection" sentinel) together with `getSelectedDate()` and
     * `setSelectedDay(CalendarDate?)`. One nullable property expresses the same state
     * without a sentinel and without four entry points that could disagree.
     *
     * To set from epoch millis, convert with [dateTimeProvider] first:
     * `selectedDate = dateTimeProvider.fromMillis(millis)`.
     */
    var selectedDate: CalendarDate?
        get() = selectionManager.selectedDay
        set(value) {
            selectionManager.selectedDay = value
            contentDescription = buildAccessibilityDescription()
            invalidate()
        }

    var year: Int
        get() = currentYear
        set(value) {
            currentYear = value
            cancelPendingSelectionClear()
            layoutComputer.invalidateLayout()
            contentDescription = buildAccessibilityDescription()
            invalidate()
        }

    /**
     * `SimpleDateFormat`-style pattern used to parse the initially selected day and to
     * format dates for accessibility announcements.
     *
     * Does **not** invalidate the calendar layout: the pattern has no effect on month
     * names, day counts or grid geometry, so rebuilding the cache here was pure waste.
     */
    var dayPattern: String
        get() = currentDayPattern
        set(value) {
            currentDayPattern = value
            contentDescription = buildAccessibilityDescription()
            invalidate()
        }

    fun getColumns(): Int = columns
    fun getRows(): Int = rows

    fun setMonthBackgroundItemStyle(style: LegacyBackgroundStyle) {
        renderer.clearBitmapCache()
        monthConfig = monthConfig.copy(backgroundItemStyle = style)
    }

    fun setSelectionBackgroundItemStyle(style: LegacyBackgroundStyle) {
        renderer.clearBitmapCache()
        monthConfig = monthConfig.copy(selectionBackgroundItemStyle = style)
    }

    fun setMonthGestureListener(monthGestureListener: MonthGestureListener?) {
        this.monthGestureListener = monthGestureListener
    }

    // ═══════════════════════════════════════════════════════════════════
    // Listener interface
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Receives tap, long-press and range-selection events from a [YearView].
     *
     * ### Migration from the epoch-millis callbacks
     *
     * Every callback previously existed twice — once taking a `Long` epoch timestamp and
     * once taking a [CalendarDate] — and **both were invoked for every event**, so a
     * listener implementing both was notified twice per gesture. The millis variants also
     * forced a `-1L` sentinel for "no selection" and required callers to convert back to a
     * calendar date using the same time zone the view had used.
     *
     * Only the [CalendarDate] form remains. To migrate, replace
     * `onDayClick(long timeInMillis)` with `onDayClick(CalendarDate date)`; if you need
     * epoch millis, convert with [ICalendarDateTimeProvider.toMillis] using the same
     * provider that is set on the view.
     *
     * Every method has a default no-op body, so implementations override only what they
     * need — including from Java, because the module is compiled with `-Xjvm-default=all`.
     */
    interface MonthGestureListener {

        /** Called when a month block is tapped outside of any day cell. */
        fun onMonthClick(date: CalendarDate) {}

        /** Called when a month block is long-pressed outside of any day cell. */
        fun onMonthLongClick(date: CalendarDate) {}

        /** Called when a day cell is tapped. */
        fun onDayClick(date: CalendarDate) {}

        /** Called when a day cell is long-pressed. */
        fun onDayLongClick(date: CalendarDate) {}

        /** Called when a tap clears the previously selected day. */
        fun onDayDeselected(date: CalendarDate) {}

        /** Called when a range selection is completed. [start] is never after [end]. */
        fun onRangeSelected(start: CalendarDate, end: CalendarDate) {}

        /**
         * Called when the visually-selected day changes. Only fires while
         * [isDaySelectionVisuallySticky] is `true`.
         *
         * @param date the newly selected day, or `null` when the selection was cleared.
         */
        fun onSelectedDayChange(date: CalendarDate?) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    // Gesture handling
    // ═══════════════════════════════════════════════════════════════════

    private fun cancelPendingSelectionClear() {
        handler.removeCallbacks(clearSelectionRunnable)
        isClearSelectionLaunched = false
    }

    /**
     * Returns the index of the month block containing ([x], [y]), or `-1`.
     *
     * Scans the positioned blocks directly instead of deriving a row/column from the
     * view size. The two are not equivalent: [CalendarLayoutComputer.splitViewInBlocks]
     * applies a horizontal compensation padding, so a point can map to grid cell *n*
     * while lying inside block *n − 1*. Only [CalendarLayoutComputer.blockCount] blocks
     * are considered, so cleared slots in an undersized grid never match.
     */
    private fun findMonthIndexAt(x: Int, y: Int): Int {
        if (viewWidth <= 0 || viewHeight <= 0) return -1
        for (index in 0 until layoutComputer.blockCount) {
            if (monthBlocks[index].contains(x, y)) return index
        }
        return -1
    }

    /**
     * Marks the month at [monthIndex] as visually selected.
     *
     * Kept separate from [findMonthIndexAt] so that hit-testing stays side-effect free
     * and the highlight can be applied whether or not a listener is attached.
     */
    private fun highlightMonth(monthIndex: Int) {
        if (monthIndex < 0) return
        cancelPendingSelectionClear()
        selectedMonthID = monthIndex
    }

    /**
     * Returns the day at ([x], [y]), or `null` when the point is not on a day cell.
     *
     * Shares [MonthGridGeometry] with [MonthRenderer] so the hit-tested cell is by
     * construction the same one that was drawn.
     */
    private fun getClickedDay(x: Int, y: Int): CalendarDate? {
        // Computed on demand rather than relying on onDraw having run: a touch can
        // legitimately arrive before the first frame, and resolving it against an
        // uninitialised title offset would map it to the wrong row.
        ensureCalendarMetrics()
        val layout = layoutComputer.cachedMonthData ?: return null
        val monthIndex = findMonthIndexAt(x, y)
        if (monthIndex < 0) return null

        val block = monthBlocks[monthIndex]
        val contentTop = block.top + layoutComputer.monthTitleOffsets[monthIndex]
        val contentHeight = block.bottom - contentTop
        if (contentHeight <= 0) return null

        val xUnit = MonthGridGeometry.cellWidth(block.width())
        val yUnit = MonthGridGeometry.cellHeight(contentHeight)
        if (xUnit <= 0 || yUnit <= 0) return null

        // Same origin the renderer anchors on, so the drawn cell and its touch target
        // cannot drift apart.
        val gridLeft = MonthGridGeometry.gridOriginX(block.left, block.width())
        val visualColumn = MonthGridGeometry.columnAt(x, gridLeft, xUnit)
        val row = MonthGridGeometry.rowAt(y, contentTop, yUnit)
        // NO_CELL is -1, so the row check also rejects out-of-grid points and the
        // day-name header row in one comparison.
        if (visualColumn == MonthGridGeometry.NO_CELL || row < MonthGridGeometry.FIRST_DAY_ROW) return null

        // columnAt works in screen space; day numbering works in logical space. In an
        // RTL layout those differ, and visualColumn is its own inverse.
        val column = MonthGridGeometry.visualColumn(
            visualColumn,
            layoutDirection == LAYOUT_DIRECTION_RTL
        )

        val md = layout[monthIndex]
        val dayOfMonth = MonthGridGeometry.dayOfMonthAt(row, column, md.firstDayOffset)
        if (dayOfMonth < 1 || dayOfMonth > md.daysInMonth) return null

        return CalendarDate(currentYear, monthIndex + 1, dayOfMonth)
    }

    /** Tracks whether the current gesture has been claimed as a drag (range selection). */
    private var isDragActive = false
    /** Touch-slop threshold: movement beyond this distance initiates a drag. */
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    /** Starting coordinates of the current touch sequence for slop calculation. */
    private var touchDownX = 0f
    private var touchDownY = 0f

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragActive = false
                touchDownX = ev.x
                touchDownY = ev.y
                if (selectionManager.enableMultiSelection) {
                    val day = getClickedDay(ev.x.toInt(), ev.y.toInt())
                    if (day != null) {
                        selectionManager.dragRangeStart = day
                        selectionManager.dragRangeEnd = day
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectionManager.enableMultiSelection && selectionManager.isDragging) {
                    val dx = ev.x - touchDownX
                    val dy = ev.y - touchDownY
                    if (!isDragActive && (dx * dx + dy * dy > touchSlop * touchSlop)) {
                        isDragActive = true
                        // Prevent parent from intercepting once we start dragging
                        parent?.requestDisallowInterceptTouchEvent(true)
                        // From here on the detector stops receiving events, so it would
                        // never see the UP and its pending long-press timeout (armed on
                        // DOWN) would fire in the middle of the drag. Cancel it explicitly.
                        cancelGestureDetector(ev)
                    }
                    if (isDragActive) {
                        val day = getClickedDay(ev.x.toInt(), ev.y.toInt())
                        if (day != null) {
                            selectionManager.dragRangeEnd = day
                            invalidate()
                        }
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragActive) {
                    // Commit drag range
                    val result = selectionManager.commitDragRange(dateTimeProvider)
                    if (result is SelectionManager.ClickResult.RangeSelected) {
                        announceRangeSelected(result.start, result.end)
                    } else {
                        selectionManager.cancelDrag()
                    }
                    isDragActive = false
                    invalidate()
                    // A committed range is a real interaction, so it earns the click
                    // sound / a11y click event.
                    performClick()
                    return true
                }
                selectionManager.cancelDrag()
                isDragActive = false
                // performClick() is deliberately *not* called here: this branch also runs
                // for taps that hit nothing at all. The gesture detector fires it from
                // `handleTap` only when a day or month was actually hit.
            }
            MotionEvent.ACTION_CANCEL -> {
                selectionManager.cancelDrag()
                isDragActive = false
                invalidate()
            }
        }

        // Delegate to GestureDetector for taps/long-presses when not dragging
        if (!isDragActive) {
            return gestureDetector.onTouchEvent(ev)
        }
        return true
    }

    /**
     * Feeds a synthetic [MotionEvent.ACTION_CANCEL] to [gestureDetector] so it drops any
     * pending long-press callback and forgets the in-flight gesture.
     */
    private fun cancelGestureDetector(source: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(source)
        try {
            cancelEvent.action = MotionEvent.ACTION_CANCEL
            gestureDetector.onTouchEvent(cancelEvent)
        } finally {
            cancelEvent.recycle()
        }
    }

    internal inner class CalendarGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapUp(ev: MotionEvent): Boolean {
            handleTap(ev, isLongPress = false)
            return true
        }

        override fun onLongPress(ev: MotionEvent) {
            handleTap(ev, isLongPress = true)
        }

        /**
         * Shared tap/long-press handling.
         *
         * Selection state, the month highlight and accessibility announcements are
         * applied unconditionally; only the [MonthGestureListener] callbacks are skipped
         * when no listener is attached. Previously the whole handler bailed out early on
         * a null listener, so a calendar without a listener gave no visual feedback at
         * all when a month was tapped.
         */
        private fun handleTap(ev: MotionEvent, isLongPress: Boolean) {
            val x = ev.x.toInt()
            val y = ev.y.toInt()
            val listener = monthGestureListener

            // Shared with the accessibility activation path so both cannot diverge.
            var hitSomething = applyDayTap(getClickedDay(x, y), isLongPress)
            if (!hitSomething) {
                // The tap missed every day cell — treat it as a tap on the month itself.
                val monthIndex = findMonthIndexAt(x, y)
                if (monthIndex >= 0) {
                    highlightMonth(monthIndex)
                    listener?.let { dispatchMonthClick(it, monthIndex, isLongPress) }
                    hitSomething = true
                }
            }

            // Only a tap that actually landed on content counts as a click: firing it
            // unconditionally makes every stray touch play a click sound and emit a
            // TYPE_VIEW_CLICKED accessibility event.
            if (hitSomething && !isLongPress) performClick()

            invalidate()
        }

        private fun dispatchMonthClick(
            listener: MonthGestureListener,
            monthIndex: Int,
            isLongPress: Boolean
        ) {
            val monthDate = dateTimeProvider.dateOf(currentYear, monthIndex + 1, 1)
            if (isLongPress) {
                listener.onMonthLongClick(monthDate)
            } else {
                listener.onMonthClick(monthDate)
            }
        }

        override fun onDown(ev: MotionEvent): Boolean = true

        /**
         * Applies the accessibility side effects of [result] and, when a [listener] is
         * attached, forwards the corresponding callbacks. Announcements are intentionally
         * independent of the listener: screen-reader support must not require one.
         */
        private fun dispatchClickResult(
            result: SelectionManager.ClickResult,
            listener: MonthGestureListener?
        ) = this@YearView.dispatchClickResult(result, listener)
    }

    /**
     * Applies a tap on [date] to the selection state and notifies listeners.
     *
     * Shared by the touch path ([CalendarGestureListener.handleTap]) and the
     * accessibility path ([activateDay]) so a screen-reader activation and a finger
     * tap cannot produce different state.
     *
     * @return `true` when the tap changed something, i.e. it landed on a real day.
     */
    private fun applyDayTap(date: CalendarDate?, isLongPress: Boolean): Boolean {
        val result = selectionManager.handleDayClicked(
            date,
            dateTimeProvider,
            isLongPress = isLongPress
        )
        if (result is SelectionManager.ClickResult.None) return false

        dispatchClickResult(result, monthGestureListener)
        invalidate()
        // The selection state a virtual view reports has changed, so the cached
        // accessibility tree is stale.
        accessibilityHelper.invalidateRoot()
        return true
    }

    /**
     * Applies the accessibility side effects of [result] and, when a [listener] is
     * attached, forwards the corresponding callbacks. Announcements are intentionally
     * independent of the listener: screen-reader support must not require one.
     */
    private fun dispatchClickResult(
        result: SelectionManager.ClickResult,
        listener: MonthGestureListener?
    ) {
        when (result) {
            is SelectionManager.ClickResult.DayClicked -> {
                listener?.let {
                    if (result.isLongPress) it.onDayLongClick(result.date)
                    else it.onDayClick(result.date)
                    if (selectionManager.isDaySelectionVisuallySticky) {
                        it.onSelectedDayChange(selectionManager.selectedDay)
                    }
                }
                announceSelectionForDay(selectionManager.selectedDay)
            }

            is SelectionManager.ClickResult.DayDeselected -> {
                listener?.let {
                    it.onDayDeselected(result.date)
                    it.onSelectedDayChange(null)
                }
                announceSelectionChange(
                    resources.getString(R.string.yearview_a11y_deselected)
                )
            }

            is SelectionManager.ClickResult.RangeSelected ->
                announceRangeSelected(result.start, result.end)

            else -> { /* RangeStarted, None — no callback */
            }
        }
    }

    private fun announceSelectionForDay(date: CalendarDate?) {
        if (date == null) return
        val locale = Utils.getCurrentLocale(context)
        val formatted = dateTimeProvider.format(date, currentDayPattern, locale)
        announceSelectionChange(
            resources.getString(R.string.yearview_a11y_selected_day, formatted)
        )
    }

    companion object {
        private const val TAG = "YearView"
        private const val MAX_MONTHS = 12
        private const val DAY_PATTERN = "yyyy-MM-dd"
        private const val DEFAULT_TEXT_SIZE = 10 //sp
        private val DEFAULT_ALIGN = Paint.Align.CENTER
        /** Class name reported to accessibility services. */
        private const val ACCESSIBILITY_CLASS_NAME = "com.mamboa.yearview.legacy.YearView"

        /** Valid ISO first-day-of-week values: 1 = Monday … 7 = Sunday. */
        private val FIRST_DAY_OF_WEEK_RANGE = 1..7

        /**
         * Opacity percentage meaning "leave the source alpha untouched".
         *
         * Colour density and image opacity are both expressed as 0–100 and applied
         * independently, so the neutral default for each is 100.
         */
        private const val FULLY_OPAQUE = 100

        /** Default opacity of the range-selection highlight (deliberately translucent). */
        private const val DEFAULT_MULTI_SELECTION_DENSITY = 30

        // Defaults below are in **dp**. They were previously raw pixel literals, which
        // rendered at wildly different physical sizes between an mdpi and an xxhdpi
        // screen. Converted through `dpToPx` so the default look is density-stable.

        /** Inset between a day number and the range-selection highlight edge. */
        private const val DEFAULT_MULTI_SELECTION_MARGIN_DP = 2f

        /** Gap between a month title and the first grid row. */
        private const val DEFAULT_MARGIN_BELOW_MONTH_NAME_DP = 3f

        /** Inset between a month block and its selection highlight edge. */
        private const val DEFAULT_MONTH_SELECTION_MARGIN_DP = 2f

        /** Breathing room between a day number and its background shape edge. */
        private const val DEFAULT_DAY_BACKGROUND_RADIUS_DP = 2f

        /** Widest day label a month can contain; used to size cells during measurement. */
        private const val WIDEST_DAY_SAMPLE = "30"

        /** Ratio of cell size to glyph size, i.e. the breathing room around a day number. */
        private const val CELL_SPACING_FACTOR = 1.6f

        /**
         * Text lines the month title occupies during measurement. Mirrors the multiplier
         * [CalendarLayoutComputer] applies when reserving the title area.
         */
        private const val MONTH_TITLE_LINES = 2
    }
}
