package com.mamboa.yearview.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mamboa.yearview.compose.imageprovider.BitmapImageProvider
import com.mamboa.yearview.core.imageprovider.ResourceImageProvider

import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.TitleGravity
import com.mamboa.yearview.core.datetime.timeproviders.ICalendarDateTimeProvider
import com.mamboa.yearview.core.datetime.timeproviders.KotlinxTimeProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// Calendar grid constants (MONTHS_PER_YEAR, DAYS_PER_WEEK, MAX_GRID_ROWS,
// MAX_DAYS_PER_MONTH, MONTH_DAY_ENCODING_FACTOR) are defined in YearViewLayout.kt.

/**
 * Composition local that enables debug visualisation of day touch-areas.
 *
 * When `true`, semi-transparent red rectangles are drawn around every day
 * cell's touch target in the [YearView].  Defaults to `false`.
 *
 * Usage:
 * ```
 * CompositionLocalProvider(LocalYearViewDebug provides true) {
 *     YearView(state = myState)
 *     Draw the Yearview below
 * }
 * ```
 */
val LocalYearViewDebug = staticCompositionLocalOf { false }

/** Duration (ms) of the month selection highlight flash. */
private const val MONTH_SELECTION_FLASH_MS = 300L

/**
 * A full-year calendar view rendered on a single [Canvas].
 *
 * All 12 months are laid out in a [YearViewState.rows] × [YearViewState.columns] grid with configurable
 * spacing, styling, and interaction callbacks.
 *
 * @param modifier Modifier applied to the root [Box] that hosts the canvas.
 * @param state Immutable **configuration** for the YearView (layout, styling, selection
 *   behaviour, …). Can be created via [remember] or
 *   [rememberSaveable][androidx.compose.runtime.saveable.rememberSaveable]
 *   (with [YearViewState.Saver]) and is safe to pass to / from a ViewModel.
 * @param selection Observable holder for the current day selection and multi-selection
 *   range. It is the single source of truth for both: taps write to it and application
 *   code can read or modify it at any time (e.g. `selection.clearAll()`), with the
 *   calendar redrawing automatically. Defaults to a
 *   [rememberYearViewSelectionState] instance owned by this composable.
 * @param dateTimeProvider The date/time provider used for all calendar calculations.
 *   Defaults to [KotlinxTimeProvider]. Supply a custom implementation
 *   to use java.time, kotlinx-datetime, or any other date/time library.
 * @param onMonthClick Callback invoked when a month is clicked.
 *   Receives the epoch-millis timestamp of the 1st day of the tapped month.
 * @param onMonthLongClick Callback invoked when a month is long-clicked.
 *   Receives the epoch-millis timestamp of the 1st day of the long-pressed month.
 * @param onDayClick Callback invoked when a day is clicked.
 *   Receives the epoch-millis timestamp of the tapped day.
 * @param onDayLongClick Callback invoked when a day is long-clicked.
 *   Receives the epoch-millis timestamp of the long-pressed day.
 * @param onSelectedDayChange Callback invoked when the visually-selected day changes
 *   (only relevant when [YearViewState.isDaySelectionVisuallySticky] is `true`).
 *   The parameter is the epoch-millis timestamp of the newly selected day,
 *   or `-1L` when the selection is cleared (deselected).
 *   This matches the Legacy `MonthGestureListener.onSelectedDayChange` signature.
 * @param onDayDeselected Callback invoked when a previously selected day is deselected
 *   (only relevant when [YearViewState.isDaySelectionVisuallySticky] is `true`).
 *   The parameter is the epoch-millis timestamp of the day that was deselected.
 *   This complements [onSelectedDayChange] (which receives `-1L` on
 *   deselection) by providing the deselected day's timestamp directly.
 * @param onRangeSelected Callback invoked when a range of days is *completed*
 *   (by a second tap or by lifting a drag). The parameters are the start and end
 *   epoch-millis timestamps of the selected range. Intermediate states — such as a
 *   start with no end yet — are not reported here; observe
 *   [YearViewSelectionState.rangeStart] / [YearViewSelectionState.rangeEnd] instead.
 */
@Composable
fun YearView(
    modifier: Modifier = Modifier,
    state: YearViewState = YearViewState(),
    selection: YearViewSelectionState = rememberYearViewSelectionState(),
    dateTimeProvider: ICalendarDateTimeProvider = remember { KotlinxTimeProvider() },
    onMonthClick: (Long) -> Unit = {},
    onMonthLongClick: (Long) -> Unit = {},
    onDayClick: (Long) -> Unit = {},
    onDayLongClick: (Long) -> Unit = {},
    onSelectedDayChange: (Long) -> Unit = {},
    onDayDeselected: (Long) -> Unit = {},
    onRangeSelected: (Long, Long) -> Unit = { _, _ -> },
) {
    // Read debug flag from CompositionLocal instead of a parameter
    val debugDrawTouchAreas = LocalYearViewDebug.current
    // Unpack state for convenient access inside the composable body
    val year = state.year
    val rows = state.rows
    val columns = state.columns
    val verticalSpacing = state.verticalSpacing
    val horizontalSpacing = state.horizontalSpacing
    val monthConfig = state.monthConfig
    val firstDayOfWeek = state.firstDayOfWeek
    val todayConfig = state.todayConfig
    val selectedDayConfig = state.selectedDayConfig
    val dayNameTranscendsWeekend = state.dayNameTranscendsWeekend
    val isDaySelectionVisuallySticky = state.isDaySelectionVisuallySticky
    val simpleDayConfig = monthConfig.simpleDayConfig
    val weekendDayConfig = monthConfig.weekendDayConfig
    val dayNameStyle = state.dayNameStyle
    val dayFormat = state.dayFormat
    val weekendDays = state.weekendDays
    val enableMultiSelection = state.enableMultiSelection
    val multiSelectionBackgroundItemStyle = state.multiSelectionBackgroundItemStyle
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Mirrors the month grid and the weekday columns for RTL locales. Read once and
    // shared by the layout pass and the draw pass so touch targets, TalkBack focus
    // rectangles and pixels are all derived from the same orientation.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    // Convert dp values to pixels
    val vSpacingPx = with(density) { verticalSpacing.toPx() }
    val hSpacingPx = with(density) { horizontalSpacing.toPx() }
    val marginBelowMonthNamePx = with(density) { monthConfig.marginBelowMonthName.toPx() }
    val monthSelectionMarginPx =
        with(density) { monthConfig.selectionBackgroundItemStyle.selectionMargin.toPx() }
    val dayTouchPaddingPx = with(density) { state.dayTouchPadding.toPx() }

    // State
    var selectedMonthId by remember { mutableIntStateOf(-1) }
    // Plain ref — Job doesn't need snapshot tracking; writing to a
    // mutableStateOf<Job?> would trigger an unnecessary recomposition.
    val flashJobRef = remember { object { var job: Job? = null } }
    DisposableEffect(Unit) {
        onDispose { flashJobRef.job?.cancel() }
    }
    // Selection and range live entirely in `selection`, which is snapshot-backed and
    // saveable. There is deliberately no internal mirror synced by LaunchedEffect: a
    // mirror makes the parameter and the copy fight over ownership, so re-emitting the
    // same value from the host cannot restore a selection and restored values get
    // overwritten on the first effect run.

    // Drag-based range selection state (live while finger moves)
    var dragRangeStart by remember { mutableStateOf<String?>(null) }
    var dragRangeEnd by remember { mutableStateOf<String?>(null) }
    // Load painters for background images if available - using a different approach to avoid composable in remember calculation
    val monthBackgroundPainter: Painter? =
        getPainterFromImageSource(monthConfig.backgroundItemStyle.image)
    val todayBackgroundPainter: Painter? =
        getPainterFromImageSource(todayConfig.backgroundItemStyle.image)
    val selectedDayBackgroundPainter: Painter? =
        getPainterFromImageSource(selectedDayConfig.backgroundItemStyle.image)
    val simpleDayBackgroundPainter: Painter? =
        getPainterFromImageSource(simpleDayConfig.backgroundItemStyle.image)
    val weekendDayBackgroundPainter: Painter? =
        getPainterFromImageSource(weekendDayConfig.backgroundItemStyle.image)
    val multiSelectionBackgroundPainter: Painter? =
        getPainterFromImageSource(multiSelectionBackgroundItemStyle.image)
    val monthSelectionBackgroundPainter: Painter? =
        getPainterFromImageSource(monthConfig.selectionBackgroundItemStyle.image)

    // Pre-compute paths from ResourceShapeProvider shapes
    val context = LocalContext.current
    val drawablePathCache: Map<Int, Path> = remember(
        simpleDayConfig, weekendDayConfig, todayConfig, selectedDayConfig,
        monthConfig, multiSelectionBackgroundItemStyle
    ) {
        val cache = mutableMapOf<Int, Path>()
        val allStyles = listOfNotNull(
            simpleDayConfig.backgroundItemStyle,
            weekendDayConfig.backgroundItemStyle,
            todayConfig.backgroundItemStyle,
            selectedDayConfig.backgroundItemStyle,
            monthConfig.backgroundItemStyle,
            monthConfig.selectionBackgroundItemStyle,
            multiSelectionBackgroundItemStyle
        )
        for (style in allStyles) {
            val shape = style.shape
            if (shape is BackgroundShape.Custom) {
                val provider = shape.provider
                if (provider is com.mamboa.yearview.core.pathprovider.ResourcePathProvider && provider.drawableRes != 0) {
                    cache.getOrPut(provider.drawableRes) {
                        extractPathFromVectorResource(context, provider.drawableRes) ?: Path()
                    }
                }
            }
        }
        cache
    }

    // Pre-resolve ResourceShapeProvider innerPadding dimension resource IDs to px values
    // so drawing functions never need Android Context.
    val resolvedInnerPaddings: Map<Int, Float> = remember(
        simpleDayConfig, weekendDayConfig, todayConfig, selectedDayConfig,
        monthConfig, multiSelectionBackgroundItemStyle
    ) {
        val paddings = mutableMapOf<Int, Float>()
        val allStyles = listOfNotNull(
            simpleDayConfig.backgroundItemStyle,
            weekendDayConfig.backgroundItemStyle,
            todayConfig.backgroundItemStyle,
            selectedDayConfig.backgroundItemStyle,
            monthConfig.backgroundItemStyle,
            monthConfig.selectionBackgroundItemStyle,
            multiSelectionBackgroundItemStyle
        )
        for (style in allStyles) {
            val shape = style.shape
            if (shape is BackgroundShape.Custom) {
                val provider = shape.provider
                if (provider is com.mamboa.yearview.core.pathprovider.ResourcePathProvider && provider.innerPadding != 0) {
                    paddings.getOrPut(provider.innerPadding) {
                        context.resources.getDimension(provider.innerPadding)
                    }
                }
            }
        }
        paddings
    }

    // Reusable scratch Path for draw scope — avoids allocating a new Path per day cell.
    val scratchPath = remember { Path() }
    // Dedicated scratch Path for clip operations — kept separate from scratchPath because
    // clipPath() retains its Path reference for the duration of the clip lambda, so reusing
    // scratchPath inside that lambda would cause rendering corruption.
    val clipScratchPath = remember { Path() }
    // Dedicated scratch Path for scale-and-translate operations inside createShapePath,
    // avoiding a per-call Path allocation in scaleAndTranslatePath.
    val scaleScratchPath = remember { Path() }

    val currentLocale = configuration.locales[0]

    // Cache day name measurements – day names are locale-dependent but
    // year-independent, so we use a fixed reference date to avoid unnecessary
    // DateTime allocations tied to the current year.
    val dayNames =
        remember(
            dayNameStyle,
            weekendDayConfig,
            dayNameTranscendsWeekend,
            firstDayOfWeek,
            weekendDays,
            currentLocale,
            state.dayNameLength,
            dateTimeProvider,
        ) {
            val names =
                mutableListOf<DayNameEntry>()
            for (i in 0 until DAYS_PER_WEEK) {
                val dayOfWeek = getDayIndex(i, firstDayOfWeek)
                // `take` rather than `[0]`: the abbreviation can legitimately be empty
                // (which used to throw) and single-character truncation mangles locales
                // whose abbreviations are multi-character or multi-code-unit.
                val dayName = dateTimeProvider.shortDayOfWeekName(dayOfWeek, currentLocale)
                    .take(state.dayNameLength.coerceAtLeast(0))
                val style = if (weekendDays.contains(dayOfWeek) && !dayNameTranscendsWeekend) {
                    weekendDayConfig.textStyle
                } else {
                    dayNameStyle
                }
                val layout = textMeasurer.measure(text = dayName, style = style)
                names.add(DayNameEntry(dayName, style, layout))
            }
            names
        }

    // Pre-measure day number text layouts (1–31) for every distinct DayConfig style.
    // This avoids calling textMeasurer.measure() per day cell on every draw pass.
    val dayTextLayoutCache: Map<DayTextKey, TextLayoutResult> = remember(
        simpleDayConfig.textStyle, weekendDayConfig.textStyle,
        todayConfig.textStyle, selectedDayConfig.textStyle
    ) {
        val cache = mutableMapOf<DayTextKey, TextLayoutResult>()
        val distinctStyles = setOf(
            simpleDayConfig.textStyle,
            weekendDayConfig.textStyle,
            todayConfig.textStyle,
            selectedDayConfig.textStyle
        )
        for (style in distinctStyles) {
            for (day in 1..MAX_DAYS_PER_MONTH) {
                cache[DayTextKey(day, style)] = textMeasurer.measure(
                    text = day.toString(),
                    style = style
                )
            }
        }
        cache
    }

    // Pre-measure month name text layouts for all 12 months under both nameStyle
    // and todayNameStyle so drawMonthName never calls textMeasurer.measure() each frame.
    val monthNameLayoutCache: Map<MonthNameKey, TextLayoutResult> = remember(
        monthConfig.nameStyle, monthConfig.todayNameStyle,
        monthConfig.nameFormat, year, currentLocale
    ) {
        val cache = mutableMapOf<MonthNameKey, TextLayoutResult>()
        val distinctStyles = setOf(monthConfig.nameStyle, monthConfig.todayNameStyle)
        for (style in distinctStyles) {
            for (m in 0 until MONTHS_PER_YEAR) {
                val text = dateTimeProvider.formatMonth(year, m + 1, monthConfig.nameFormat, currentLocale)
                cache[MonthNameKey(m, style)] = textMeasurer.measure(text = text, style = style)
            }
        }
        cache
    }

    // Per-month calendar facts (length, weekday of the 1st). Computed once per year
    // instead of 24 provider calls on every draw pass, and shared with the layout pass
    // so both agree on the same numbers.
    val monthMetadata = remember(year, dateTimeProvider) {
        YearMonthMetadata.compute(year, dateTimeProvider)
    }

    // Precompute day metadata lookups to avoid per-call DateTime allocations.
    // Encoded as month * MONTH_DAY_ENCODING_FACTOR + day (month is 0-based as used by callers).

    // Today lookup: single encoded value (or -1 if today is not in this year)
    val today = remember(year) { dateTimeProvider.today() }
    val todayEncoded = remember(year) {
        if (today.year == year) (today.month - 1) * MONTH_DAY_ENCODING_FACTOR + today.day else -1
    }
    val isToday = { month: Int, day: Int -> month * MONTH_DAY_ENCODING_FACTOR + day == todayEncoded }
    // 0-based index of today's month (or -1 if today is not in this year)
    val todayMonthIndex = if (today.year == year) today.month - 1 else -1

    // Weekend lookup: precompute a set of encoded (month, day) pairs for all weekend days
    val weekendSet = remember(monthMetadata, weekendDays) {
        // ~9 weekend days per month × 12 months; use weekendDays.size for a tighter estimate
        val set = HashSet<Int>(if (weekendDays.isEmpty()) 0 else MONTHS_PER_YEAR * weekendDays.size * 5)
        if (weekendDays.isNotEmpty()) {
            for (m in 0 until MONTHS_PER_YEAR) {
                val daysInMonth = monthMetadata.daysInMonth[m]
                val firstDow = monthMetadata.firstDayOfWeekInMonth[m]
                for (d in 1..daysInMonth) {
                    val dow = ((firstDow - 1 + (d - 1)) % 7) + 1 // 1=Mon..7=Sun (ISO convention)
                    if (weekendDays.contains(dow)) {
                        set.add(m * MONTH_DAY_ENCODING_FACTOR + d)
                    }
                }
            }
        }
        set
    }
    val isWeekend = { month: Int, day: Int -> weekendSet.contains(month * MONTH_DAY_ENCODING_FACTOR + day) }

    // Selected day lookup: single encoded value (or -1 if nothing is selected).
    // Wrapped in derivedStateOf so that lambdas which capture selectedDayEncoded
    // read through the State, causing the Canvas draw layer to invalidate
    // automatically when the selection changes.
    val selectedDayEncoded by remember(year, dayFormat, currentLocale, dateTimeProvider, selection) {
        derivedStateOf {
            val selectedDay = selection.selectedDay
            if (selectedDay.isEmpty()) -1
            else {
                try {
                    val dt = dateTimeProvider.parse(selectedDay, dayFormat, currentLocale)
                    if (dt.year == year) (dt.month - 1) * MONTH_DAY_ENCODING_FACTOR + dt.day else -1
                } catch (_: Exception) { -1 }
            }
        }
    }
    val isSelectedDay = { month: Int, day: Int -> month * MONTH_DAY_ENCODING_FACTOR + day == selectedDayEncoded }

    // Cache month and day rectangles based on dimensions and parameters
    // Use a key based on dimensions to recalculate only when canvas size changes.
    // Two separate float states avoid Pair allocation on every resize.
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    var canvasHeightPx by remember { mutableFloatStateOf(0f) }
    // NOTE: every value read inside calculateCalendarLayout must appear as a key here.
    // The resulting geometry drives both hit-testing and the accessibility overlay, so a
    // missing key means taps and TalkBack focus rectangles drift away from what is drawn.
    val layoutData = remember(
        canvasWidthPx, canvasHeightPx,
        columns,
        rows,
        hSpacingPx,
        vSpacingPx,
        monthSelectionMarginPx,
        year,
        firstDayOfWeek,
        marginBelowMonthNamePx,
        todayMonthIndex,
        monthConfig.nameStyle,
        monthConfig.todayNameStyle,
        simpleDayConfig.textStyle,
        dayTextLayoutCache,
        monthNameLayoutCache,
        monthMetadata,
        dayTouchPaddingPx,
        isRtl,
    ) {
        calculateCalendarLayout(
            canvasWidth = canvasWidthPx,
            canvasHeight = canvasHeightPx,
            columns = columns,
            rows = rows,
            hSpacingPx = hSpacingPx,
            vSpacingPx = vSpacingPx,
            monthSelectionMarginPx = monthSelectionMarginPx,
            year = year,
            firstDayOfWeek = firstDayOfWeek,
            marginBelowMonthNamePx = marginBelowMonthNamePx,
            todayMonthIndex = todayMonthIndex,
            monthNameStyle = monthConfig.nameStyle,
            todayMonthNameStyle = monthConfig.todayNameStyle,
            simpleDayTextStyle = simpleDayConfig.textStyle,
            dayTextLayoutCache = dayTextLayoutCache,
            monthNameLayoutCache = monthNameLayoutCache,
            monthMetadata = monthMetadata,
            dayTouchPaddingPx = dayTouchPaddingPx,
            isRtl = isRtl,
        )
    }

    // Keep latest references so the gesture handler never holds stale closures.
    // layoutData is a plain val from remember(), so we must wrap it in a State
    // to ensure the gesture handler's closure always reads the current value.
    val currentLayoutData by rememberUpdatedState(layoutData)
    val currentOnDayClick by rememberUpdatedState(onDayClick)
    val currentOnDayLongClick by rememberUpdatedState(onDayLongClick)
    val currentOnMonthClick by rememberUpdatedState(onMonthClick)
    val currentOnMonthLongClick by rememberUpdatedState(onMonthLongClick)
    val currentOnDayDeselected by rememberUpdatedState(onDayDeselected)
    val currentOnSelectedDayChange by rememberUpdatedState(onSelectedDayChange)
    val currentOnRangeSelected by rememberUpdatedState(onRangeSelected)

    // Gesture handler — delegates all tap / long-press / drag logic
    val gestureHandler = remember(
        dateTimeProvider, dayFormat, currentLocale, year,
        enableMultiSelection, isDaySelectionVisuallySticky, selection
    ) {
        YearViewGestureHandler(
            dateTimeProvider = dateTimeProvider,
            dayFormat = dayFormat,
            locale = currentLocale,
            year = year,
            enableMultiSelection = enableMultiSelection,
            isDaySelectionVisuallySticky = isDaySelectionVisuallySticky,
            getLayoutData = { currentLayoutData },
            getSelectedDay = { selection.selectedDay },
            setSelectedDay = { selection.selectedDay = it },
            getRangeStart = { selection.rangeStart },
            getRangeEnd = { selection.rangeEnd },
            // Both ends are written together, so an observer of the selection state
            // never sees a new start paired with a stale end.
            setRange = { start, end -> selection.setRange(start, end) },
            getDragRangeStart = { dragRangeStart },
            setDragRangeStart = { dragRangeStart = it },
            getDragRangeEnd = { dragRangeEnd },
            setDragRangeEnd = { dragRangeEnd = it },
            setSelectedMonthId = { selectedMonthId = it },
            onDayDeselected = { currentOnDayDeselected(it) },
            onDayClick = { currentOnDayClick(it) },
            onDayLongClick = { currentOnDayLongClick(it) },
            onMonthClick = { currentOnMonthClick(it) },
            onMonthLongClick = { currentOnMonthLongClick(it) },
            onSelectedDayChange = { currentOnSelectedDayChange(it) },
            onRangeSelected = { s, e -> currentOnRangeSelected(s, e) },
            flashMonthSelection = { _ ->
                flashJobRef.job?.cancel()
                flashJobRef.job = coroutineScope.launch {
                    delay(MONTH_SELECTION_FLASH_MS)
                    selectedMonthId = -1
                }
            },
        )
    }

    // Keep latest handler references so pointerInput(Unit) never holds stale closures
    val currentHandleTap by rememberUpdatedState { offset: Offset -> gestureHandler.handleTap(offset) }
    val currentHandleLongPress by rememberUpdatedState { offset: Offset -> gestureHandler.handleLongPress(offset) }
    val currentHandleDragStart by rememberUpdatedState { offset: Offset -> gestureHandler.handleDragStart(offset) }
    val currentHandleDrag by rememberUpdatedState { offset: Offset -> gestureHandler.handleDrag(offset) }
    val currentHandleDragEnd by rememberUpdatedState { gestureHandler.handleDragEnd() }

    // Pre-compute range bounds for O(1) per-day membership checks.
    // Uses RangeBounds (simple lo/hi integer comparison) instead of a
    // materialised HashSet to avoid allocating on every drag-move event.
    // Drag-based range takes priority over tap-based range.
    // Keyed on everything the lambda captures by value: without keys the derived state
    // would be created once and keep comparing against the first composition's year /
    // format / locale.
    val rangeBounds by remember(year, dayFormat, currentLocale, dateTimeProvider, selection) {
        derivedStateOf {
            RangeBounds.compute(
                rangeStart = selection.rangeStart,
                rangeEnd = selection.rangeEnd,
                dragRangeStart = dragRangeStart,
                dragRangeEnd = dragRangeEnd,
                year = year,
                dayFormat = dayFormat,
                currentLocale = currentLocale,
                dateTimeProvider = dateTimeProvider,
            )
        }
    }
    val isInRange = { month: Int, day: Int -> rangeBounds.contains(year, month, day) }

    // Build the style bundle every composition so that lambda fields (isToday,
    // isWeekend, isSelectedDay, isInRange) always capture the latest State
    // values (selectedDayEncoded, rangeBounds, etc.).  The object itself is
    // lightweight — it only holds references — and DayStyleBundle is @Stable
    // with custom equals/hashCode that excludes lambdas, so downstream
    // Compose comparisons still skip work when the data inputs haven't changed.
    val sharedStyle = DayStyleBundle(
        simpleDayConfig = simpleDayConfig,
        weekendDayConfig = weekendDayConfig,
        todayConfig = todayConfig,
        selectedDayConfig = selectedDayConfig,
        monthNameStyle = monthConfig.nameStyle,
        todayMonthNameStyle = monthConfig.todayNameStyle,
        isToday = isToday,
        isWeekend = isWeekend,
        isSelectedDay = isSelectedDay,
        isInRange = isInRange,
        multiSelectionBackgroundItemStyle = multiSelectionBackgroundItemStyle,
        monthBackgroundItemStyle = monthConfig.backgroundItemStyle,
        monthPainter = monthBackgroundPainter,
        todayPainter = todayBackgroundPainter,
        selectedDayPainter = selectedDayBackgroundPainter,
        simpleDayPainter = simpleDayBackgroundPainter,
        weekendDayPainter = weekendDayBackgroundPainter,
        multiSelectionPainter = multiSelectionBackgroundPainter,
    )
    val sharedResources = remember(
        textMeasurer, drawablePathCache, resolvedInnerPaddings,
        dayTextLayoutCache, monthNameLayoutCache, dayTouchPaddingPx,
        density.density,
    ) {
        DrawResources(
            textMeasurer = textMeasurer,
            drawablePathCache = drawablePathCache,
            resolvedInnerPaddings = resolvedInnerPaddings,
            scratchPath = scratchPath,
            clipScratchPath = clipScratchPath,
            scaleScratchPath = scaleScratchPath,
            dayTextLayouts = dayTextLayoutCache,
            monthNameLayouts = monthNameLayoutCache,
            dayTouchPaddingPx = dayTouchPaddingPx,
            density = density.density,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (canvasWidthPx != w) canvasWidthPx = w
                if (canvasHeightPx != h) canvasHeightPx = h
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        currentHandleTap(offset)
                    },
                    onLongPress = { offset ->
                        currentHandleLongPress(offset)
                    }
                )
            }
            // When multi-selection is enabled a second pointer-input handler
            // tracks drag gestures for range selection. It uses
            // requireUnconsumed = false so it sees every down event regardless
            // of whether detectTapGestures consumed it.
            //
            // Nothing is consumed — and no range is extended — until the pointer has
            // travelled further than the platform touch slop. That distinction matters:
            // consuming a change makes detectTapGestures treat the gesture as cancelled,
            // so consuming eagerly on the first move would swallow ordinary taps and
            // long-presses whenever the finger wobbled by a pixel. Past the slop the
            // gesture is unambiguously a drag, and consuming then stops parent scroll
            // containers from stealing it.
            .then(
                if (enableMultiSelection) {
                    Modifier.pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            currentHandleDragStart(down.position)

                            var pointerId = down.id
                            var isDragging = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pointer = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                if (pointer == null || !pointer.pressed) {
                                    currentHandleDragEnd()
                                    break
                                }
                                pointerId = pointer.id

                                if (!isDragging &&
                                    (pointer.position - down.position).getDistance() > touchSlop
                                ) {
                                    isDragging = true
                                }

                                if (isDragging) {
                                    currentHandleDrag(pointer.position)
                                    // Read dragRangeStart lazily each iteration so the
                                    // check stays correct even if state changes mid-drag.
                                    if (dragRangeStart != null) {
                                        pointer.consume()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = false) {
                contentDescription =
                    "Year View Calendar for $year, displaying 12 months with interactive days and months."
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Capture a consistent snapshot of layout geometry for the entire draw pass
            val drawLayout = layoutData

            // Local array for last-row Y values produced by drawMonth().
            // We must NOT mutate drawLayout.lastRowYValues in-place because Compose
            // snapshot system tracks references, not array contents, so in-place
            // mutation would be invisible and could leave stale cached values.
            val drawnLastRowYValues = FloatArray(MONTHS_PER_YEAR)

            // Draw months (redraw based on current state)
            if (drawLayout.monthRects.isNotEmpty()) {
                for (i in 0 until MONTHS_PER_YEAR) {
                    val monthRect = drawLayout.monthRects[i]
                    // Check if the month is within the visible canvas bounds
                    if (monthRect.rect.bottom > 0 && monthRect.rect.top < canvasHeight &&
                        monthRect.rect.right > 0 && monthRect.rect.left < canvasWidth
                    ) {
                        val monthDrawCtx = MonthDrawContext(
                            layout = MonthLayoutInfo(
                                monthRect = monthRect,
                                month = i,
                                firstDay = monthMetadata.firstDayOffset(i, firstDayOfWeek),
                                daysInMonth = monthMetadata.daysInMonth[i],
                                year = year,
                                isRtl = isRtl,
                                monthTitleGravity = monthConfig.titleGravity,
                                marginBelowMonthNamePx = marginBelowMonthNamePx,
                                isCurrentMonth = (i == todayMonthIndex),
                                dayNames = dayNames,
                                monthFormat = monthConfig.nameFormat,
                                locale = currentLocale,
                                dateTimeProvider = dateTimeProvider,
                            ),
                            style = sharedStyle,
                            resources = sharedResources,
                        )
                        drawnLastRowYValues[i] = drawMonth(monthDrawCtx)
                    }
                }

                // DEBUG: Draw touch-area rectangles around every day number
                if (debugDrawTouchAreas) {
                    val debugTouchPaint = Color.Red.copy(alpha = 0.35f)
                    for (dr in drawLayout.dayRects) {
                        drawRect(
                            color = debugTouchPaint,
                            topLeft = Offset(dr.rect.left, dr.rect.top),
                            size = Size(dr.rect.width, dr.rect.height),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // Draw selection
                if (selectedMonthId >= 0 && selectedMonthId < drawLayout.monthRects.size) {
                    val monthRect = drawLayout.monthRects[selectedMonthId]
                    // drawnLastRowYValues is only filled for months that survived the
                    // visibility check above, so fall back to the pre-computed layout value
                    // (and finally to the month rect) for a culled month — otherwise the
                    // highlight would collapse to a zero-height sliver at the canvas top.
                    val lastRowY = drawnLastRowYValues.getOrElse(selectedMonthId) { 0f }
                        .takeIf { it > 0f }
                        ?: drawLayout.lastRowYValues.getOrElse(selectedMonthId) { 0f }
                            .takeIf { it > 0f }
                        ?: monthRect.rect.bottom
                    val selectionRect = Rect(
                        monthRect.selectionRect.left,
                        monthRect.selectionRect.top,
                        monthRect.selectionRect.right,
                        lastRowY + monthSelectionMarginPx
                    )

                    // Only draw selection if it's within visible bounds
                    if (selectionRect.bottom > 0 && selectionRect.top < canvasHeight &&
                        selectionRect.right > 0 && selectionRect.left < canvasWidth
                    ) {
                        drawStyledBackground(
                            selectionRect,
                            monthConfig.selectionBackgroundItemStyle,
                            monthSelectionBackgroundPainter,
                            sharedResources,
                        )
                    }
                }
            }
        }

        // ── Accessibility overlay ────────────────────────────────────
        // Invisible composables positioned over month / day areas to give
        // TalkBack proper navigation semantics.
        YearViewAccessibilityOverlay(
            layoutData = layoutData,
            year = year,
            dateTimeProvider = dateTimeProvider,
            locale = currentLocale,
            todayMonthIndex = todayMonthIndex,
            todayDay = if (today.year == year) today.day else -1,
            selectedDayEncoded = selectedDayEncoded,
            isDaySelectionVisuallySticky = isDaySelectionVisuallySticky,
            todayLabel = state.todayLabel,
            selectedLabel = state.selectedLabel,
            exitMonthActionLabel = state.exitMonthActionLabel,
            onDayClick = { currentOnDayClick(it) },
            onDayDeselected = { currentOnDayDeselected(it) },
            onSelectedDayChange = { currentOnSelectedDayChange(it) },
            dayFormat = dayFormat,
            selection = selection,
        )
    }
}

/**
 * Draws a single month block (name, day-of-week headers, day numbers with
 * backgrounds) inside the month's pre-computed rectangle.
 *
 * @param ctx All layout, style, and resource data for this month.
 * @return The Y-coordinate of the bottom edge of the last drawn day row (px).
 *   Used by the caller to position the month-selection highlight accurately,
 *   since the actual last row may be higher than the pre-computed month rect bottom.
 */
private fun DrawScope.drawMonth(
    ctx: MonthDrawContext
): Float = with(ctx) {
    // Draw month background if provided and not transparent
    val bgStyle = monthBackgroundItemStyle
    if (bgStyle != null && (bgStyle.color != Color.Transparent || bgStyle.image != ImageSource.None)) {
        // selectionMargin is a Dp; DrawScope is a Density, so convert at the draw boundary.
        val marginPx = bgStyle.selectionMargin.toPx()
        val bounds = Rect(
            left = monthRect.rect.left - marginPx,
            top = monthRect.rect.top - marginPx,
            right = monthRect.rect.right + marginPx,
            bottom = monthRect.rect.bottom + marginPx
        )
        drawStyledBackground(bounds, bgStyle, monthPainter, resources)
    }

    // Draw month name (uses pre-cached layout; formatMonth is only called as a fallback)
    val nameStyle = if (isCurrentMonth) todayMonthNameStyle else monthNameStyle
    val nameHeight = drawMonthName(nameStyle, monthRect, monthTitleGravity, textMeasurer, month, year, monthFormat, locale, dateTimeProvider, monthNameLayouts)

    // Day grid area: the month rect moved below the month name.
    val gridBounds = Rect(
        left = monthRect.rect.left,
        top = monthRect.rect.top + nameHeight + marginBelowMonthNamePx,
        right = monthRect.rect.right,
        bottom = monthRect.rect.bottom
    )

    var lastRowY = 0f

    // Shared with calculateCalendarLayout so drawn cells and touch targets cannot drift.
    forEachCalendarCell(
        gridBounds = gridBounds,
        firstDayOffset = firstDay,
        daysInMonth = daysInMonth,
        isRtl = isRtl,
        onHeaderCell = { column, centerX, centerY ->
            drawDayName(column, centerX, centerY, dayNames)
        },
        onDayCell = { dayOfMonth, centerX, centerY ->
            val textHeight = drawDayNumber(ctx, dayOfMonth, centerX, centerY)
            lastRowY = centerY + textHeight / 2
        },
    )

    lastRowY
}

/**
 * Draws the month name and returns the measured text height (px) so callers
 * can offset the day grid below it without re-measuring.
 */
private fun DrawScope.drawMonthName(
    nameStyle: TextStyle,
    monthRect: MonthRect,
    monthTitleGravity: TitleGravity,
    textMeasurer: TextMeasurer,
    monthIndex: Int,
    year: Int,
    monthFormat: String,
    locale: Locale,
    dateTimeProvider: ICalendarDateTimeProvider,
    monthNameLayouts: Map<MonthNameKey, TextLayoutResult>,
): Int {
    val monthNameTextLayout = monthNameLayouts[MonthNameKey(monthIndex, nameStyle)]
        ?: run {
            // Fallback: format and measure on the fly (should not happen in normal usage)
            val monthName = dateTimeProvider.formatMonth(year, monthIndex + 1, monthFormat, locale)
            textMeasurer.measure(text = monthName, style = nameStyle)
        }
    val nameWidth = monthNameTextLayout.size.width

    // Resolve START/END to LEFT/RIGHT based on layout direction (RTL support)
    val isRtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
    val resolvedGravity = when (monthTitleGravity) {
        TitleGravity.START -> if (isRtl) TitleGravity.RIGHT else TitleGravity.LEFT
        TitleGravity.END -> if (isRtl) TitleGravity.LEFT else TitleGravity.RIGHT
        else -> monthTitleGravity
    }
    val xStart = when (resolvedGravity) {
        TitleGravity.LEFT -> monthRect.rect.left + monthRect.selectionMargin
        TitleGravity.CENTER -> (monthRect.rect.left + monthRect.rect.right) / 2 - nameWidth / 2
        TitleGravity.RIGHT -> monthRect.rect.right - nameWidth - monthRect.selectionMargin
        else -> (monthRect.rect.left + monthRect.rect.right) / 2 - nameWidth / 2
    }

    drawText(
        textLayoutResult = monthNameTextLayout,
        topLeft = Offset(xStart, monthRect.rect.top)
    )

    return monthNameTextLayout.size.height
}

private fun DrawScope.drawDayName(
    x: Int,
    xValue: Float,
    yValue: Float,
    dayNames: List<DayNameEntry>,
) {
    val dayNameLayout = dayNames[x].layout
    drawText(
        textLayoutResult = dayNameLayout,
        topLeft = Offset(
            xValue - dayNameLayout.size.width / 2,
            yValue - dayNameLayout.size.height / 2
        )
    )
}

/**
 * Draws a single day number cell and returns its text height (px).
 *
 * @param ctx The shared month drawing context.
 * @param dayOfMonth The 1-based day of the month to draw.
 * @param xValue Horizontal centre of the day cell (px).
 * @param yValue Vertical centre of the day cell (px).
 */
private fun DrawScope.drawDayNumber(
    ctx: MonthDrawContext,
    dayOfMonth: Int,
    xValue: Float,
    yValue: Float
): Float = with(ctx) {
    val isCurrentDayToday = isToday(month, dayOfMonth)
    val isCurrentDaySelected = isSelectedDay(month, dayOfMonth)
    val isCurrentDayWeekend = isWeekend(month, dayOfMonth)
    val isCurrentDayInRange = isInRange(month, dayOfMonth)

    // Determine text style and optional background based on day type
    val activeDayConfig = when {
        isCurrentDaySelected -> selectedDayConfig
        isCurrentDayToday -> todayConfig
        isCurrentDayWeekend -> weekendDayConfig
        else -> simpleDayConfig
    }

    // Look up the pre-measured text layout from the cache (avoids per-frame allocation).
    val dayTextKey = DayTextKey(dayOfMonth, activeDayConfig.textStyle)
    val dayTextLayout = dayTextLayouts[dayTextKey]
        ?: textMeasurer.measure(text = dayOfMonth.toString(), style = activeDayConfig.textStyle)

    val textWidth = dayTextLayout.size.width.toFloat()
    val textHeight = dayTextLayout.size.height.toFloat()

    // Draw background if needed
    if (activeDayConfig.backgroundItemStyle.color != Color.Transparent || activeDayConfig.backgroundItemStyle.image != ImageSource.None) {
        val backgroundSize =
            maxOf(textWidth, textHeight) * activeDayConfig.backgroundRadius + dayTouchPaddingPx
        val backgroundRect = Rect(
            left = xValue - backgroundSize / 2,
            top = yValue - backgroundSize / 2,
            right = xValue + backgroundSize / 2,
            bottom = yValue + backgroundSize / 2
        )
        val backgroundPainter = when {
            isCurrentDaySelected -> selectedDayPainter
            isCurrentDayToday -> todayPainter
            isCurrentDayWeekend -> weekendDayPainter
            else -> simpleDayPainter
        }
        drawStyledBackground(
            backgroundRect,
            activeDayConfig.backgroundItemStyle,
            backgroundPainter,
            resources,
        )
    }
    // Draw multi-selection background if the day is in range and multi-selection style is provided
    val multiStyle = multiSelectionBackgroundItemStyle
    if (isCurrentDayInRange && multiStyle != null) {
        val backgroundSize =
            maxOf(textWidth, textHeight) + dayTouchPaddingPx * 2
        val backgroundRect = Rect(
            left = xValue - backgroundSize / 2,
            top = yValue - backgroundSize / 2,
            right = xValue + backgroundSize / 2,
            bottom = yValue + backgroundSize / 2
        )
        drawStyledBackground(backgroundRect, multiStyle, multiSelectionPainter, resources)
    }

    // Draw the day text, centered properly
    drawText(
        textLayoutResult = dayTextLayout,
        topLeft = Offset(
            x = xValue - textWidth / 2,
            y = yValue - textHeight / 2
        ),
        color = activeDayConfig.textStyle.color
    )

    textHeight
}

internal fun getDayIndex(position: Int, firstDayOfWeek: Int): Int {
    return ((firstDayOfWeek - 1 + position) % 7) + 1
}

@Composable
private fun getPainterFromImageSource(imageSource: ImageSource): Painter? {
    return when (imageSource) {
        is ImageSource.Provided -> {
            when (val provider = imageSource.provider) {
                is ResourceImageProvider -> painterResource(provider.resId)
                is BitmapImageProvider -> {
                    remember(provider.imageBitmap) {
                        BitmapPainter(provider.imageBitmap)
                    }
                }
                else -> null
            }
        }

        else -> null
    }
}
