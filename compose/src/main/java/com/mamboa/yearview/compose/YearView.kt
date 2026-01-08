package com.mamboa.yearview.compose

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.TitleGravity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import java.time.DayOfWeek

/**
 * A preview composable to test the YearView
 */
@Preview(
    name = "yearview", showSystemUi = true, showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun YearViewPreview() {
    val currentYear = DateTime().year().get()

    Box(
        modifier = Modifier
            .background(Color.White)
            .padding(16.dp)
    ) {
        YearView(
            year = currentYear,
            rows = 4,
            columns = 3,
            verticalSpacing = 8.dp,
            horizontalSpacing = 8.dp,
            monthConfig = MonthConfig(
                titleGravity = TitleGravity.CENTER,
                marginBelowMonthName = 8.dp,
                selectionBackgroundItemStyle = BackgroundItemStyle.ComposeStyle(
                    color = Color(0xFF1976D2),
                    shape = BackgroundShape.Circle(radius = 5.0f)
                ),
                backgroundItemStyle = BackgroundItemStyle.ComposeStyle(
                    color = Color(0xFFE3F2FD),
                    shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
                    selectionMargin = 2.0f
                ),
                nameStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                todayNameStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                nameFormat = "MMMM"
            ),
            todayConfig = DayConfig(
                backgroundItemStyle = BackgroundItemStyle.ComposeStyle(
                    color = Color(0xFF1976D2),
                    shape = BackgroundShape.Circle(radius = 5.0f)
                )
            ),
            selectedDayConfig = DayConfig(
                backgroundItemStyle = BackgroundItemStyle.ComposeStyle(
                    color = Color(0xFF4CAF50),
                    shape = BackgroundShape.Square
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            ),
            dayFormat = "yyyy-MM-dd",
            onDayClick = { timestamp ->
                val dateTime = DateTime(timestamp)
                println("Day clicked: ${dateTime.toString("yyyy-MM-dd")}")
            },
            onMonthClick = { timestamp ->
                val dateTime = DateTime(timestamp)
                println("Month clicked: ${dateTime.toString("MMMM yyyy")}")
            }
        )
    }
}

@Composable
fun YearView(
    modifier: Modifier = Modifier,
    /**
     * The year to display in the YearView.
     */
    year: Int = 2023,
    /**
     * The number of rows to display in the YearView.
     */
    rows: Int = 4,
    /**
     * The number of columns to display in the YearView.
     */
    columns: Int = 3,
    /**
     * The vertical spacing between months in the YearView.
     */
    verticalSpacing: Dp = 8.dp,
    /**
     * The horizontal spacing between months in the YearView.
     */
    horizontalSpacing: Dp = 8.dp,
    /**
     * Arbitrary string representing the selected day in the format defined by [dayFormat].
     */
    arbitrarySelectedDay: String = "",
    /**
     * Configuration for the month display, including styles and background as defined by [MonthConfig].
     */
    monthConfig: MonthConfig = MonthConfig(),
    /**
     * The first day of the week, where 1 = Monday, 2 = Tuesday, etc.
     */
    firstDayOfWeek: Int = DayOfWeek.MONDAY.value,
    /**
     * Configuration for the display of today's date, including styles and background as defined by [DayConfig].
     */
    todayConfig: DayConfig = DayConfig(),
    /**
     * Style for the background of the selected day.
     */
    selectedDayConfig: DayConfig = DayConfig(
        backgroundItemStyle = BackgroundItemStyle.ComposeStyle(
            color = Color.Blue,
            shape = BackgroundShape.Square
        ),
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    ),
    /**
     * Whether the day names transcend weekends, meaning they will be displayed regardless of whether they fall on a weekend.
     */
    dayNameTranscendsWeekend: Boolean = false,
    /**
     * Whether to enable day selection.
     */
    isDaySelectionVisuallySticky: Boolean = false,
    /**
     * Style for the text of a simple day (not selected, not today).
     */
    simpleDayStyle: TextStyle = TextStyle(
        color = Color.Black,
        fontSize = 10.sp,
        textAlign = TextAlign.Center
    ),
    /**
     * Style for the text of a simple day (not selected, not today).
     */
    weekendDayStyle: TextStyle = TextStyle(
        color = Color.Gray,
        fontSize = 10.sp,
        textAlign = TextAlign.Center
    ),
    /**
     * Style for the text of the month name.
     */
    dayNameStyle: TextStyle = TextStyle(
        color = Color.Black,
        fontSize = 10.sp,
        textAlign = TextAlign.Center
    ),
    /**
     * Custom format for day strings, defaults to "yyyy-MM-dd".
     */
    dayFormat: String = "yyyy-MM-dd", // Custom format for day strings
    /**
     * Set of weekend days, where 1 = Monday, 2 = Tuesday, etc.
     */
    weekendDays: Set<Int> = setOf(DateTimeConstants.SATURDAY, DateTimeConstants.SUNDAY),
    /**
     * Callback invoked when a month is clicked.
     */
    onMonthClick: (Long) -> Unit = {},
    /**
     * Callback invoked when a month is long-clicked.
     */
    onMonthLongClick: (Long) -> Unit = {},
    /**
     * Callback invoked when a day is clicked.
     */
    onDayClick: (Long) -> Unit = {},
    /**
     * Callback invoked when a day is long-clicked.
     */
    onDayLongClick: (Long) -> Unit = {},
    /**
     * Enable multi-selection of days.
     */
    enableMultiSelection: Boolean = false,
    /**
     * Style for the background of the multi-selected days.
     */
    multiSelectionBackgroundItemStyle: BackgroundItemStyle.ComposeStyle = BackgroundItemStyle.ComposeStyle(
        color = Color.Cyan.copy(alpha = 0.3f),
        shape = BackgroundShape.Square,
        selectionMargin = 5.0f
    ),
    /**
     * Callback invoked when a range of days is selected.
     * The parameters are the start and end timestamps of the selected range.
     */
    onRangeSelected: (Long, Long) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    // Convert dp values to pixels
    val vSpacingPx = with(density) { verticalSpacing.toPx() }
    val hSpacingPx = with(density) { horizontalSpacing.toPx() }
    val marginBelowMonthNamePx = with(density) { monthConfig.marginBelowMonthName.toPx() }
    val monthSelectionMarginPx =
        with(density) { monthConfig.selectionBackgroundItemStyle.selectionMargin.dp.toPx() }

    // State
    var selectedMonthId by remember { mutableIntStateOf(-1) }
    var selectedDay by remember { mutableStateOf(arbitrarySelectedDay) }
    var rangeStart by remember { mutableStateOf<String?>(null) }
    var rangeEnd by remember { mutableStateOf<String?>(null) }
    var monthRects by remember { mutableStateOf<List<MonthRect>>(emptyList()) }
    var dayRects by remember { mutableStateOf<List<DayRect>>(emptyList()) }
    var lastRowYValues by remember { mutableStateOf(FloatArray(12)) }

    // Load painters for background images if available - using a different approach to avoid composable in remember calculation
    val monthBackgroundPainter: Painter? =
        getPainterFromImageSource(monthConfig.backgroundItemStyle.image)
    val todayBackgroundPainter: Painter? =
        getPainterFromImageSource(todayConfig.backgroundItemStyle.image)
    val selectedDayBackgroundPainter: Painter? =
        getPainterFromImageSource(selectedDayConfig.backgroundItemStyle.image)
    val multiSelectionBackgroundPainter: Painter? =
        getPainterFromImageSource(multiSelectionBackgroundItemStyle.image)
    val monthSelectionBackgroundPainter: Painter? =
        getPainterFromImageSource(monthConfig.selectionBackgroundItemStyle.image)

    // Constants
    val numMonths = 12
    val numDays = 7
    val dayFormatter = remember {
        DateTimeFormat.forPattern(dayFormat).withLocale(configuration.locales[0])
    }
    val monthFormatter = remember {
        DateTimeFormat.forPattern(monthConfig.nameFormat).withLocale(configuration.locales[0])
    }

    // Pre-allocate storage for day positions to minimize object creation
    val maxDaysPerMonth = 31
    val dayPositions = remember(rows, columns) {
        Array(numMonths) { Array(maxDaysPerMonth + numDays) { FloatArray(2) } }
    }
    val dayTouchRects = remember(rows, columns) {
        Array(numMonths) { Array(maxDaysPerMonth) { Rect(0f, 0f, 0f, 0f) } }
    }

    // Cache day name measurements
    val dayNames =
        remember(
            dayNameStyle,
            weekendDayStyle,
            dayNameTranscendsWeekend,
            firstDayOfWeek,
            weekendDays
        ) {
            val names =
                mutableListOf<Triple<String, TextStyle, TextLayoutResult>>()
            for (i in 0 until numDays) {
                val dayOfWeek = getDayIndex(i, firstDayOfWeek)
                val dateTime = DateTime().withYear(year).withDayOfWeek(dayOfWeek)
                val dayName = dateTime.dayOfWeek().asShortText.substring(0, 1)
                val style = if (weekendDays.contains(dateTime.dayOfWeek().get()) &&
                    !dayNameTranscendsWeekend
                )
                    weekendDayStyle else dayNameStyle
                val layout = textMeasurer.measure(text = dayName, style = style)
                names.add(Triple(dayName, style, layout))
            }
            names
        }

    // Helper function to detect if a day is today
    val isToday = { month: Int, day: Int ->
        val dateTime = DateTime().withYear(year).withMonthOfYear(month + 1).withDayOfMonth(day)
        dateTime.toLocalDate() == LocalDate()
    }

    // Helper function to detect if a day is weekend
    val isWeekend = { month: Int, day: Int ->
        val dateTime = DateTime().withYear(year).withMonthOfYear(month + 1).withDayOfMonth(day)
        weekendDays.contains(dateTime.dayOfWeek().get())
    }

    // Helper function to detect if a day is selected
    val isSelectedDay = { month: Int, day: Int ->
        if (selectedDay.isEmpty()) false
        else {
            val dateTime = DateTime().withYear(year).withMonthOfYear(month + 1).withDayOfMonth(day)
            selectedDay == dayFormatter.print(dateTime)
        }
    }

    // Handle tap and long press events
    @SuppressLint("DefaultLocale")
    fun handleTap(offset: Offset) {
        var isDayClicked = false
        var targetMonthIndex = -1

        // First, find the month block containing the click point
        for ((index, monthRect) in monthRects.withIndex()) {
            if (monthRect.rect.contains(offset)) {
                targetMonthIndex = index
                break
            }
        }

        if (targetMonthIndex >= 0) {
            // Only search within the days of the target month
            val daysInTargetMonth = DateTime().withYear(year).withMonthOfYear(targetMonthIndex + 1)
                .dayOfMonth().maximumValue
            val startIndex = dayRects.indexOfFirst {
                it.date.startsWith(
                    "${year}-${
                        String.format(
                            "%02d",
                            targetMonthIndex + 1
                        )
                    }"
                )
            }
            val endIndex = startIndex + daysInTargetMonth

            if (startIndex >= 0 && endIndex <= dayRects.size) {
                for (i in startIndex until endIndex) {
                    val dayRect = dayRects[i]
                    if (dayRect.rect.contains(offset)) {
                        val dateTime = dayFormatter.parseDateTime(dayRect.date)
                        val timeInMillis = dateTime.millis
                        val dayDescription = "Day ${dateTime.dayOfMonth().get()}, ${
                            dateTime.monthOfYear().asText
                        }, $year"
                        val stateDescription = when {
                            selectedDay == dayRect.date -> "Selected"
                            dateTime.toLocalDate() == LocalDate() -> "Today"
                            else -> ""
                        }
                        val fullDescription = if (stateDescription.isNotEmpty()) {
                            "$dayDescription, $stateDescription"
                        } else {
                            dayDescription
                        }
                        // Accessibility announcement (though not directly supported in Canvas, can be logged or used with custom accessibility handling)
                        println("Accessibility: Tapped $fullDescription")

                        if (enableMultiSelection) {
                            if (rangeStart == null) {
                                rangeStart = dayRect.date
                                println("Accessibility: Range selection started at $dayDescription")
                            } else if (rangeEnd == null) {
                                rangeEnd = dayRect.date
                                // Ensure start is before end
                                val startDateTime = dayFormatter.parseDateTime(rangeStart!!)
                                val endDateTime = dayFormatter.parseDateTime(rangeEnd!!)
                                if (startDateTime.isAfter(endDateTime)) {
                                    val temp = rangeStart
                                    rangeStart = rangeEnd
                                    rangeEnd = temp
                                }
                                // Trigger callback for range selection
                                onRangeSelected(startDateTime.millis, endDateTime.millis)
                                println(
                                    "Accessibility: Range selection completed from ${
                                        startDateTime.dayOfMonth().get()
                                    } to ${endDateTime.dayOfMonth().get()} ${
                                        endDateTime.monthOfYear().asText
                                    }"
                                )
                            } else {
                                // Reset range if both start and end are set
                                rangeStart = dayRect.date
                                rangeEnd = null
                                println("Accessibility: Range selection reset, new start at $dayDescription")
                            }
                        } else {
                            if (isDaySelectionVisuallySticky) {
                                selectedDay = if (selectedDay == dayRect.date) "" else dayRect.date
                            }
                            onDayClick(timeInMillis)
                        }
                        isDayClicked = true
                        break
                    }
                }
            }

            if (!isDayClicked) {
                val monthRect = monthRects[targetMonthIndex]
                val dateTime = DateTime()
                    .withYear(year)
                    .withMonthOfYear(monthRect.month + 1)
                    .withDayOfMonth(1)
                    .withHourOfDay(1)

                val monthDescription = "Month of ${dateTime.monthOfYear().asText} $year"
                println("Accessibility: Tapped $monthDescription")
                selectedMonthId = targetMonthIndex
                onMonthClick(dateTime.millis)

                coroutineScope.launch {
                    delay(300)
                    selectedMonthId = -1
                }
            }
        }
    }

    fun handleLongPress(offset: Offset) {
        var isDayClicked = false
        var targetMonthIndex = -1

        // First, find the month block containing the click point
        for ((index, monthRect) in monthRects.withIndex()) {
            if (monthRect.rect.contains(offset)) {
                targetMonthIndex = index
                break
            }
        }

        if (targetMonthIndex >= 0) {
            // Only search within the days of the target month
            val daysInTargetMonth = DateTime().withYear(year).withMonthOfYear(targetMonthIndex + 1)
                .dayOfMonth().maximumValue
            val startIndex = dayRects.indexOfFirst {
                it.date.startsWith(
                    "${year}-${
                        String.format(
                            "%02d",
                            targetMonthIndex + 1
                        )
                    }"
                )
            }
            val endIndex = startIndex + daysInTargetMonth

            if (startIndex >= 0 && endIndex <= dayRects.size) {
                for (i in startIndex until endIndex) {
                    val dayRect = dayRects[i]
                    if (dayRect.rect.contains(offset)) {
                        val dateTime = dayFormatter.parseDateTime(dayRect.date)
                        onDayLongClick(dateTime.millis)
                        isDayClicked = true
                        break
                    }
                }
            }

            if (!isDayClicked) {
                val monthRect = monthRects[targetMonthIndex]
                val dateTime = DateTime()
                    .withYear(year)
                    .withMonthOfYear(monthRect.month + 1)
                    .withDayOfMonth(1)
                    .withHourOfDay(1)

                onMonthLongClick(dateTime.millis)
            }
        }
    }

    // Cache month and day rectangles based on dimensions and parameters
    // Use a key based on dimensions to recalculate only when canvas size changes
    val canvasSizeKey = remember { mutableStateOf(0f to 0f) }
    val cachedMonthRects = remember(
        canvasSizeKey.value,
        columns,
        rows,
        hSpacingPx,
        vSpacingPx,
        monthSelectionMarginPx
    ) {
        if (canvasSizeKey.value.first > 0 && canvasSizeKey.value.second > 0) {
            calculateMonthBlocks(
                canvasSizeKey.value.first,
                canvasSizeKey.value.second,
                columns,
                rows,
                numMonths,
                hSpacingPx,
                vSpacingPx,
                monthSelectionMarginPx
            )
        } else {
            emptyList()
        }
    }

    // Cache day positions and touch rectangles without drawing
    val cachedDayData = remember(
        canvasSizeKey.value,
        columns,
        rows,
        year,
        firstDayOfWeek,
        dayNameTranscendsWeekend,
        monthConfig.marginBelowMonthName
    ) {
        if (canvasSizeKey.value.first == 0f || canvasSizeKey.value.second == 0f || cachedMonthRects.isEmpty()) {
            Pair(emptyList<DayRect>(), FloatArray(numMonths))
        } else {
            val newDayRects = mutableListOf<DayRect>()
            val newLastRowYValues = FloatArray(numMonths)

            for (i in 0 until numMonths) {
                val monthTime = DateTime().withYear(year).withMonthOfYear(i + 1)
                val dayOfWeek =
                    ((monthTime.withDayOfMonth(1).dayOfWeek().get() - firstDayOfWeek + 7) % 7)
                val monthRect = cachedMonthRects[i]
                val daysInMonth = monthTime.dayOfMonth().maximumValue

                val monthNameLayout = textMeasurer.measure(
                    text = monthTime.monthOfYear().getAsText(configuration.locales[0]),
                    style = if (isToday(i, 1)) monthConfig.todayNameStyle else monthConfig.nameStyle
                )
                val nameHeight = monthNameLayout.size.height

                val adjustedMonthRect = Rect(
                    left = monthRect.rect.left,
                    top = monthRect.rect.top + nameHeight + marginBelowMonthNamePx,
                    right = monthRect.rect.right,
                    bottom = monthRect.rect.bottom
                )

                val numDaysInWeek = 7
                val xUnit = adjustedMonthRect.width / numDaysInWeek
                val yUnit = (adjustedMonthRect.height - marginBelowMonthNamePx) / numDaysInWeek
                var lastRowY = 0f
                var dayOfMonth = 1 - dayOfWeek

                // Start from 0 to match drawMonth's logic
                for (y in 0..numDaysInWeek) {
                    for (x in 0 until numDaysInWeek) {
                        val xValue = adjustedMonthRect.left + xUnit * x + xUnit / 2
                        val yValue =
                            adjustedMonthRect.top + yUnit * (y + 1) + yUnit / 2 // Add 1 to y to match drawing offset

                        if (dayOfMonth in 1..daysInMonth) {
                            val dateTime = DateTime()
                                .withYear(year)
                                .withMonthOfYear(i + 1)
                                .withDayOfMonth(dayOfMonth)
                            val dateString = dayFormatter.print(dateTime)
                            val dayTextLayout = textMeasurer.measure(
                                text = dayOfMonth.toString(),
                                style = simpleDayStyle
                            )
                            val textWidth = dayTextLayout.size.width
                            val textHeight = dayTextLayout.size.height

                            val touchPadding = 4f
                            val touchWidth = textWidth + touchPadding * 2
                            val touchHeight = textHeight + touchPadding * 2
                            val touchRect = Rect(
                                left = xValue - touchWidth / 2,
                                top = yValue - touchHeight / 2,
                                right = xValue + touchWidth / 2,
                                bottom = yValue + touchHeight / 2
                            )

                            newDayRects.add(DayRect(touchRect, dateString))
                            lastRowY = yValue + textHeight / 2
                        }
                        dayOfMonth++
                    }
                }
                newLastRowYValues[i] = lastRowY
            }
            Pair(newDayRects, newLastRowYValues)
        }
    }

    // Update state with cached values
    monthRects = cachedMonthRects
    dayRects = cachedDayData.first
    lastRowYValues = cachedDayData.second

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        handleTap(offset)
                    },
                    onLongPress = { offset ->
                        handleLongPress(offset)
                    }
                )
            }
            .semantics {
                contentDescription =
                    "Year View Calendar for $year, displaying 12 months with interactive days and months."
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            // Update the canvas size key for caching
            canvasSizeKey.value = canvasWidth to canvasHeight

            // Draw months (redraw based on current state)
            if (monthRects.isNotEmpty()) {
                for (i in 0 until numMonths) {
                    val monthRect = monthRects[i]
                    // Check if the month is within the visible canvas bounds
                    if (monthRect.rect.bottom > 0 && monthRect.rect.top < canvasHeight &&
                        monthRect.rect.right > 0 && monthRect.rect.left < canvasWidth
                    ) {
                        val monthTime = DateTime().withYear(year).withMonthOfYear(i + 1)
                        val dayOfWeek =
                            ((monthTime.withDayOfMonth(1).dayOfWeek()
                                .get() - firstDayOfWeek + 7) % 7)

                        val daysInMonth = monthTime.dayOfMonth().maximumValue

                        val (monthDayRects, lastRowY) = drawMonth(
                            monthRect = monthRect,
                            month = i,
                            firstDay = dayOfWeek,
                            daysInMonth = daysInMonth,
                            simpleDayStyle = simpleDayStyle,
                            weekendDayStyle = weekendDayStyle,
                            todayStyle = todayConfig.textStyle,
                            dayNameStyle = dayNameStyle,
                            monthNameStyle = monthConfig.nameStyle,
                            todayMonthNameStyle = monthConfig.todayNameStyle,
                            selectedDayStyle = selectedDayConfig.textStyle,
                            todayBackgroundItemStyle = todayConfig.backgroundItemStyle,
                            selectedDayBackgroundItemStyle = selectedDayConfig.backgroundItemStyle,
                            monthTitleGravity = monthConfig.titleGravity,
                            marginBelowMonthNamePx = marginBelowMonthNamePx,
                            firstDayOfWeek = firstDayOfWeek,
                            dayNameTranscendsWeekend = dayNameTranscendsWeekend,
                            year = year,
                            isToday = isToday,
                            isWeekend = isWeekend,
                            isSelectedDay = isSelectedDay,
                            dayFormatter = dayFormatter,
                            monthFormatter = monthFormatter,
                            textMeasurer = textMeasurer,
                            dayNames = dayNames,
                            dayPositions = dayPositions[i],
                            dayTouchRects = dayTouchRects[i],
                            rangeStart = rangeStart,
                            rangeEnd = rangeEnd,
                            multiSelectionBackgroundItemStyle = multiSelectionBackgroundItemStyle,
                            monthBackgroundItemStyle = monthConfig.backgroundItemStyle,
                            monthPainter = monthBackgroundPainter,
                            todayPainter = todayBackgroundPainter,
                            selectedDayPainter = selectedDayBackgroundPainter,
                            multiSelectionPainter = multiSelectionBackgroundPainter
                        )
                        monthRect.lastRowY = lastRowY
                        lastRowYValues[i] = lastRowY
                    }
                }

                // TODO: add the signature call
                // TODO: Remove later, this is only for test purposes: to check if days are in their selectable areas
                /*dayRects.forEach { dayRect ->
                    drawRect(
                        color = Color.Blue.copy(alpha = 0.4f),
                        topLeft = dayRect.rect.topLeft,
                        size = dayRect.rect.size,
                        style = Fill
                    )
                }*/

                // Draw selection
                if (selectedMonthId >= 0 && selectedMonthId < monthRects.size) {
                    val monthRect = monthRects[selectedMonthId]
                    val lastRowY = monthRect.lastRowY
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
                            monthSelectionBackgroundPainter
                        )
                    }
                }
            }
        }
    }
}

private fun calculateMonthBlocks(
    width: Float,
    height: Float,
    columns: Int,
    rows: Int,
    numMonths: Int,
    horizontalSpacing: Float,
    verticalSpacing: Float,
    monthSelectionMargin: Float = 5.0f
): List<MonthRect> {
    val monthRects = mutableListOf<MonthRect>()

    val blockWidth = (width - horizontalSpacing * (columns - 1)) / columns
    val blockHeight = (height - verticalSpacing * (rows - 1)) / rows
    val leftPadding = (width - (blockWidth * columns + horizontalSpacing * (columns - 1))) / 2f
    val topPadding = (height - (blockHeight * rows + verticalSpacing * (rows - 1))) / 2f

    var k = 0
    for (i in 0 until rows) {
        for (j in 0 until columns) {
            if (k >= numMonths) break

            val left = leftPadding + j * (blockWidth + horizontalSpacing)
            val top = topPadding + i * (blockHeight + verticalSpacing)
            val right = left + blockWidth
            val bottom = top + blockHeight

            val baseRect = Rect(left, top, right, bottom)
            val selectionRect = Rect(
                left - monthSelectionMargin,
                top - monthSelectionMargin,
                right + monthSelectionMargin,
                bottom + monthSelectionMargin // This will be adjusted later based on lastRowY
            )

            monthRects.add(
                MonthRect(
                    rect = baseRect,
                    month = k,
                    lastRowY = 0f, // This will be set later when drawing
                    selectionRect = selectionRect,
                    selectionMargin = monthSelectionMargin // Store as Dp for later use
                )
            )
            k++
        }
    }
    return monthRects
}

private fun DrawScope.drawMonth(
    monthRect: MonthRect,
    month: Int,
    firstDay: Int,
    daysInMonth: Int,
    simpleDayStyle: TextStyle,
    weekendDayStyle: TextStyle,
    todayStyle: TextStyle,
    dayNameStyle: TextStyle,
    monthNameStyle: TextStyle,
    todayMonthNameStyle: TextStyle,
    selectedDayStyle: TextStyle,
    todayBackgroundItemStyle: BackgroundItemStyle.ComposeStyle,
    selectedDayBackgroundItemStyle: BackgroundItemStyle.ComposeStyle,
    monthTitleGravity: TitleGravity,
    marginBelowMonthNamePx: Float,
    firstDayOfWeek: Int,
    dayNameTranscendsWeekend: Boolean,
    year: Int,
    isToday: (Int, Int) -> Boolean,
    isWeekend: (Int, Int) -> Boolean,
    isSelectedDay: (Int, Int) -> Boolean,
    dayFormatter: org.joda.time.format.DateTimeFormatter,
    monthFormatter: org.joda.time.format.DateTimeFormatter,
    textMeasurer: TextMeasurer,
    dayNames: List<Triple<String, TextStyle, TextLayoutResult>> = emptyList(),
    dayPositions: Array<FloatArray> = emptyArray(),
    dayTouchRects: Array<Rect> = emptyArray(),
    rangeStart: String? = null,
    rangeEnd: String? = null,
    multiSelectionBackgroundItemStyle: BackgroundItemStyle.ComposeStyle? = null,
    monthBackgroundItemStyle: BackgroundItemStyle.ComposeStyle? = null,
    monthPainter: Painter? = null,
    todayPainter: Painter? = null,
    selectedDayPainter: Painter? = null,
    multiSelectionPainter: Painter? = null
): Pair<List<DayRect>, Float> {
    println("Debug: Drawing month ${month + 1} with firstDay=$firstDay, daysInMonth=$daysInMonth")
    // Draw month background if provided and not transparent
    if (monthBackgroundItemStyle != null && (monthBackgroundItemStyle.color != Color.Transparent || monthBackgroundItemStyle.image != ImageSource.None)) {
        val bounds = Rect(
            left = monthRect.rect.left - monthBackgroundItemStyle.selectionMargin,
            top = monthRect.rect.top - monthBackgroundItemStyle.selectionMargin,
            right = monthRect.rect.right + monthBackgroundItemStyle.selectionMargin,
            bottom = monthRect.rect.bottom + monthBackgroundItemStyle.selectionMargin
        )
        drawStyledBackground(bounds, monthBackgroundItemStyle, monthPainter)
    }

    // Draw month name
    val monthTime = DateTime().withYear(year).withMonthOfYear(month + 1)
    val formattedMonthName = monthFormatter.print(monthTime)
    val currentDate = DateTime()
    val isCurrentMonth =
        (monthTime.year().get() == currentDate.year().get() && monthTime.monthOfYear()
            .get() == currentDate.monthOfYear().get())
    val nameStyle = if (isCurrentMonth) todayMonthNameStyle else monthNameStyle
    drawMonthName(formattedMonthName, nameStyle, monthRect, monthTitleGravity, textMeasurer)

    // Adjust rect for day drawing (move below month name)
    val monthNameTextLayout = textMeasurer.measure(text = formattedMonthName, style = nameStyle)
    val nameHeight = monthNameTextLayout.size.height
    val adjustedMonthRect = Rect(
        left = monthRect.rect.left,
        top = monthRect.rect.top + nameHeight + marginBelowMonthNamePx,
        right = monthRect.rect.right,
        bottom = monthRect.rect.bottom
    )

    // Calculate grid cell size
    val numDays = 7 // Days in a week
    val xUnit = adjustedMonthRect.width / numDays
    val yUnit = adjustedMonthRect.height / numDays

    val dayRects = mutableListOf<DayRect>()
    var lastRowY = 0f

    // Start from 1 - firstDay to account for first day offset
    var dayOfMonth = 1 - firstDay
    var dayIndex = 0

    for (y in 0..numDays) {
        for (x in 0 until numDays) {
            val xValue = adjustedMonthRect.left + xUnit * x + xUnit / 2  // Center horizontally
            val yValue = adjustedMonthRect.top + yUnit * y + yUnit / 2   // Center vertically

            // Store positions for reuse
            if (dayIndex < dayPositions.size) {
                dayPositions[dayIndex][0] = xValue
                dayPositions[dayIndex][1] = yValue
            }

            // Draw day titles (first row)
            if (y == 0) {
                drawDayName(
                    x,
                    xValue,
                    yValue,
                    dayNames,
                    firstDayOfWeek,
                    dayNameTranscendsWeekend,
                    weekendDayStyle,
                    dayNameStyle,
                    textMeasurer
                )
            }
            // Draw day numbers
            else {
                if (dayOfMonth in 1..daysInMonth) {
                    val dayRect = drawDayNumber(
                        month, dayOfMonth, xValue, yValue, year, isToday, isWeekend, isSelectedDay,
                        simpleDayStyle, weekendDayStyle, todayStyle, selectedDayStyle,
                        todayBackgroundItemStyle, selectedDayBackgroundItemStyle,
                        dayFormatter, textMeasurer, dayTouchRects,
                        rangeStart, rangeEnd, multiSelectionBackgroundItemStyle,
                        todayPainter, selectedDayPainter,
                        multiSelectionPainter
                    )
                    dayRects.add(dayRect)
                    val textHeight = textMeasurer.measure(
                        text = dayOfMonth.toString(),
                        style = simpleDayStyle
                    ).size.height
                    lastRowY = yValue + textHeight / 2
                    println("Debug: Added day $dayOfMonth to dayRects, position: x=$xValue, y=$yValue, rect: left=${dayRect.rect.left}, top=${dayRect.rect.top}, right=${dayRect.rect.right}, bottom=${dayRect.rect.bottom}")
                }
                dayOfMonth++
            }
            dayIndex++
        }
    }

    println("Debug: Total days added for month ${month + 1}: ${dayRects.size}")
    return Pair(dayRects, lastRowY)
}

private fun DrawScope.drawStyledBackground(
    bounds: Rect,
    style: BackgroundItemStyle.ComposeStyle,
    painter: Painter?
) {
    when (style.mergeType) {
        MergeType.CLIP -> {
            drawStyledBackgroundInnerClip(bounds, style, painter)
        }
        else -> {
            drawStyledBackgroundMerge(bounds, style, painter)
        }
    }
}

private fun DrawScope.drawMonthName(
    monthName: String,
    nameStyle: TextStyle,
    monthRect: MonthRect,
    monthTitleGravity: TitleGravity,
    textMeasurer: TextMeasurer
) {
    val monthNameTextLayout = textMeasurer.measure(text = monthName, style = nameStyle)
    val nameWidth = monthNameTextLayout.size.width
    val nameHeight = monthNameTextLayout.size.height

    val xStart = when (monthTitleGravity) {
        TitleGravity.START, TitleGravity.LEFT -> monthRect.rect.left + monthRect.selectionMargin // Align with the start of the first column
        TitleGravity.CENTER -> (monthRect.rect.left + monthRect.rect.right) / 2 - nameWidth / 2 // Center as is
        TitleGravity.END, TitleGravity.RIGHT -> monthRect.rect.right - nameWidth - monthRect.selectionMargin // Align with the end of the last column
    }

    /*// this is for test purposes:
    // Draw background rectangle
    drawRect(
        color = Color.LightGray.copy(alpha = 0.3f),
        topLeft = Offset(xStart, monthRect.rect.top),
        size = Size(nameWidth.toFloat() , nameHeight.toFloat() ),
        style = Fill
    )*/

    drawText(
        textMeasurer = textMeasurer,
        text = monthName,
        style = nameStyle,
        topLeft = Offset(xStart, monthRect.rect.top)
    )
    // add a background behind the month name area
}

private fun DrawScope.drawDayName(
    x: Int,
    xValue: Float,
    yValue: Float,
    dayNames: List<Triple<String, TextStyle, TextLayoutResult>>,
    firstDayOfWeek: Int,
    dayNameTranscendsWeekend: Boolean,
    weekendDayStyle: TextStyle,
    dayNameStyle: TextStyle,
    textMeasurer: TextMeasurer
) {
    val dayNameData = dayNames.getOrNull(x)
    if (dayNameData != null) {
        val (dayName, style, dayNameLayout) = dayNameData
        drawText(
            textMeasurer = textMeasurer,
            text = dayName,
            style = style,
            topLeft = Offset(
                xValue - dayNameLayout.size.width / 2,
                yValue - dayNameLayout.size.height / 2
            )
        )
    } else {
        val dayOfWeek = getDayIndex(x, firstDayOfWeek)
        val dateTime = DateTime().withYear(2023).withDayOfWeek(dayOfWeek) // Year is arbitrary
        val dayName = dateTime.dayOfWeek().asShortText.substring(0, 1)

        val style = if ((dateTime.dayOfWeek().get() == DateTimeConstants.SATURDAY ||
                    dateTime.dayOfWeek().get() == DateTimeConstants.SUNDAY) &&
            !dayNameTranscendsWeekend
        )
            weekendDayStyle else dayNameStyle

        val dayNameLayout = textMeasurer.measure(text = dayName, style = style)

        drawText(
            textMeasurer = textMeasurer,
            text = dayName,
            style = style,
            topLeft = Offset(
                xValue - dayNameLayout.size.width / 2,
                yValue - dayNameLayout.size.height / 2
            )
        )
    }
}

private fun DrawScope.drawDayNumber(
    month: Int,
    dayOfMonth: Int,
    xValue: Float,
    yValue: Float,
    year: Int,
    isToday: (Int, Int) -> Boolean,
    isWeekend: (Int, Int) -> Boolean,
    isSelectedDay: (Int, Int) -> Boolean,
    simpleDayStyle: TextStyle,
    weekendDayStyle: TextStyle,
    todayStyle: TextStyle,
    selectedDayStyle: TextStyle,
    todayBackgroundItemStyle: BackgroundItemStyle.ComposeStyle,
    selectedDayBackgroundItemStyle: BackgroundItemStyle.ComposeStyle,
    dayFormatter: org.joda.time.format.DateTimeFormatter,
    textMeasurer: TextMeasurer,
    dayTouchRects: Array<Rect>, // TODO: remove this when caching logic works
    rangeStart: String? = null,
    rangeEnd: String? = null,
    multiSelectionBackgroundItemStyle: BackgroundItemStyle.ComposeStyle? = null,
    todayPainter: Painter? = null,
    selectedDayPainter: Painter? = null,
    multiSelectionPainter: Painter? = null
): DayRect {
    val isCurrentDayToday = isToday(month, dayOfMonth)
    val isCurrentDaySelected = isSelectedDay(month, dayOfMonth)
    val isCurrentDayWeekend = isWeekend(month, dayOfMonth)

    // Create a date string for this day
    val dateTime = DateTime()
        .withYear(year)
        .withMonthOfYear(month + 1)
        .withDayOfMonth(dayOfMonth)

    val dateString = dayFormatter.print(dateTime)

    // Debug logging to check touch rectangle for each day
    println("Debug: Month ${month + 1} Day $dayOfMonth position: x=$xValue, y=$yValue")

    // Check if this day is within the selected range
    val isInRange = if (rangeStart != null && rangeEnd != null) {
        val startDateTime = dayFormatter.parseDateTime(rangeStart)
        val endDateTime = dayFormatter.parseDateTime(rangeEnd)
        !dateTime.isBefore(startDateTime) && !dateTime.isAfter(endDateTime)
    } else {
        false
    }

    // Store the clickable region for this day
    val dayTextLayout = textMeasurer.measure(
        text = dayOfMonth.toString(),
        style = simpleDayStyle
    )

    val textWidth = dayTextLayout.size.width.toFloat()
    val textHeight = dayTextLayout.size.height.toFloat()

    // Determine the touch area based on the text dimensions with a small padding
    val touchPadding = 4f // Smaller padding for more precise touch area
    val touchWidth = textWidth + touchPadding * 2
    val touchHeight = textHeight + touchPadding * 2
    val touchRectLeft = xValue - touchWidth / 2
    val touchRectTop = yValue - touchHeight / 2
    val touchRectRight = xValue + touchWidth / 2
    val touchRectBottom = yValue + touchHeight / 2

    // Debug logging to check touch rectangle for each day
    println("Debug: Month ${month + 1} Day $dayOfMonth touch rect: left=$touchRectLeft, top=$touchRectTop, right=$touchRectRight, bottom=$touchRectBottom")

    val touchRect = if (dayOfMonth - 1 < dayTouchRects.size) {
        dayTouchRects[dayOfMonth - 1] = Rect(
            left = touchRectLeft,
            top = touchRectTop,
            right = touchRectRight,
            bottom = touchRectBottom
        )
        dayTouchRects[dayOfMonth - 1]
    } else {
        Rect(
            left = touchRectLeft,
            top = touchRectTop,
            right = touchRectRight,
            bottom = touchRectBottom
        )
    }

    // Determine text style and optional background
    val drawConfig = when {
        isCurrentDaySelected -> DayDrawConfig(
            textStyle = selectedDayStyle,
            backgroundShape = selectedDayBackgroundItemStyle.shape,
            backgroundColor = selectedDayBackgroundItemStyle.color,
            backgroundRadius = 1f
        )

        isCurrentDayToday -> DayDrawConfig(
            textStyle = todayStyle,
            backgroundShape = todayBackgroundItemStyle.shape,
            backgroundColor = todayBackgroundItemStyle.color,
            backgroundRadius = 1f
        )

        isCurrentDayWeekend -> DayDrawConfig(
            textStyle = weekendDayStyle,
            backgroundShape = null,
            backgroundColor = Color.Transparent,
            backgroundRadius = 0.0f
        )

        else -> DayDrawConfig(
            textStyle = simpleDayStyle,
            backgroundShape = null,
            backgroundColor = Color.Transparent,
            backgroundRadius = 0.0f
        )
    }

    // Draw background if needed
    if (drawConfig.backgroundShape != null && drawConfig.backgroundColor != Color.Transparent) {
        val backgroundSize =
            maxOf(textWidth, textHeight) * drawConfig.backgroundRadius + touchPadding
        val backgroundRect = Rect(
            left = xValue - backgroundSize / 2,
            top = yValue - backgroundSize / 2,
            right = xValue + backgroundSize / 2,
            bottom = yValue + backgroundSize / 2
        )
        val backgroundStyle = when {
            isCurrentDaySelected -> selectedDayBackgroundItemStyle
            isCurrentDayToday -> todayBackgroundItemStyle
            else -> BackgroundItemStyle.ComposeStyle(
                color = Color.Transparent,
                shape = BackgroundShape.Square
            )
        }
        val backgroundPainter = when {
            isCurrentDaySelected -> selectedDayPainter
            isCurrentDayToday -> todayPainter
            else -> null
        }
        drawStyledBackground(backgroundRect, backgroundStyle, backgroundPainter)
    }
    // Draw multi-selection background if the day is in range and multi-selection style is provided
    if (isInRange && multiSelectionBackgroundItemStyle != null) {
        val backgroundSize =
            maxOf(textWidth, textHeight) + touchPadding * 2
        val backgroundRect = Rect(
            left = xValue - backgroundSize / 2,
            top = yValue - backgroundSize / 2,
            right = xValue + backgroundSize / 2,
            bottom = yValue + backgroundSize / 2
        )
        drawStyledBackground(
            backgroundRect,
            multiSelectionBackgroundItemStyle,
            multiSelectionPainter
        )
    }

    // Draw the day text, centered properly
    drawText(
        textLayoutResult = dayTextLayout,
        topLeft = Offset(
            x = xValue - textWidth / 2,
            y = yValue - textHeight / 2
        ),
        color = drawConfig.textStyle.color
    )

    // TODO: this is for test purposes
    /*drawRect(
        color = Color.Red.copy(alpha = 0.6f),
        topLeft = touchRect.topLeft,
        size = touchRect.size,
        style = Fill
    )*/

    return DayRect(touchRect, dateString)
}

private fun getDayIndex(position: Int, firstDayOfWeek: Int): Int {
    return ((firstDayOfWeek - 1 + position) % 7) + 1
}

@Composable
private fun getPainterFromImageSource(imageSource: ImageSource): Painter? {
    return when (imageSource) {
        is ImageSource.DrawableRes -> painterResource(imageSource.resId)
        is ImageSource.BitmapCompose -> remember(imageSource.bitmapCompose) {
            BitmapPainter(imageSource.bitmapCompose)
        }

        else -> null
    }
}

/**
 * Draws the background with merging for the given bounds and style.
 *
 * @param bounds The bounds within which the background should be drawn.
 * @param style The style configuration for the background item.
 * @param painter The painter used to draw the background image, if any.
 */
private fun DrawScope.drawStyledBackgroundMerge(
    bounds: Rect,
    style: BackgroundItemStyle.ComposeStyle,
    painter: Painter?
) {
    // 1. Draw Image using existing method
    if (painter != null) {
        drawScaledImage(bounds, style, painter)
    }

    // 2. Draw shape with color
    val colorAlpha = style.opacity.toFloat() / 100f
    val colorWithOpacity = style.color.copy(alpha = colorAlpha)

    // Create path and draw it directly (no clipping needed for merge)
    val shapePath = Path().createShapePath(bounds, style.shape)
    drawPath(
        path = shapePath,
        color = colorWithOpacity,
        style = Fill
    )
}

/**
 * Draws the background with clipping for the given bounds and style.
 *
 * @param bounds The bounds within which the background should be drawn.
 * @param style The style configuration for the background item.
 * @param painter The painter used to draw the background image, if any.
 */
private fun DrawScope.drawStyledBackgroundInnerClip(
    bounds: Rect,
    style: BackgroundItemStyle.ComposeStyle,
    painter: Painter?
) {
    val shapePath = Path().createShapePath(bounds, style.shape)
    clipPath(shapePath) {
        drawClippedContent(bounds, style, painter, shapePath)
    }
}

/**
 * Creates a shape path based on the provided [BackgroundShape] and [bounds].
 *
 * @param bounds The bounds within which the shape should be drawn.
 * @param shape The [BackgroundShape] defining the shape to be drawn.
 * @return The created [Path] representing the shape.
 */
private fun Path.createShapePath(
    bounds: Rect,
    shape: BackgroundShape
): Path = apply {
    when (shape) {
        is BackgroundShape.Circle -> {
            val size = minOf(bounds.width, bounds.height)
            val centerX = bounds.center.x
            val centerY = bounds.center.y
            addOval(
                Rect(
                    left = centerX - size / 2,
                    top = centerY - size / 2,
                    right = centerX + size / 2,
                    bottom = centerY + size / 2
                )
            )
        }

        is BackgroundShape.RoundedSquare -> {
            addRoundRect(
                RoundRect(
                    bounds,
                    CornerRadius(shape.cornerRadius, shape.cornerRadius)
                )
            )
        }

        is BackgroundShape.Star -> {
            addStarPath(bounds, shape.numberOfLegs, shape.innerRadiusRatio)
        }

        is BackgroundShape.ComposeCustom -> {
            scaleAndTranslatePath(
                sourcePath = shape.composePath,
                targetBounds = bounds,
                innerPadding = shape.innerPadding.value
            )
        }

        else -> {
            addRect(bounds)
        }
    }
}

/**
 * Adds a star-shaped path to the current path.
 *
 * @param bounds The bounding rectangle for the star shape.
 * @param numberOfLegs The number of legs on the star.
 * @param innerRadiusRatio The ratio of the inner radius to the outer radius.
 */
private fun Path.addStarPath(
    bounds: Rect,
    numberOfLegs: Int,
    innerRadiusRatio: Float
) {
    val actualPoints = numberOfLegs.coerceIn(3, 7)
    val centerX = bounds.center.x
    val centerY = bounds.center.y
    val outerRadius = minOf(bounds.width, bounds.height) / 2f
    val innerRadius = outerRadius * innerRadiusRatio
    val angleStep = (2f * Math.PI / actualPoints).toFloat()

    moveTo(
        centerX + (outerRadius * kotlin.math.cos(-Math.PI / 2)).toFloat(),
        centerY + (outerRadius * kotlin.math.sin(-Math.PI / 2)).toFloat()
    )

    for (i in 0 until actualPoints) {
        val outerAngle = -Math.PI / 2 + i * angleStep
        val innerAngle = outerAngle + angleStep / 2

        lineTo(
            centerX + (innerRadius * kotlin.math.cos(innerAngle)).toFloat(),
            centerY + (innerRadius * kotlin.math.sin(innerAngle)).toFloat()
        )

        val nextOuterAngle = -Math.PI / 2 + (i + 1) * angleStep
        lineTo(
            centerX + (outerRadius * kotlin.math.cos(nextOuterAngle)).toFloat(),
            centerY + (outerRadius * kotlin.math.sin(nextOuterAngle)).toFloat()
        )
    }
    close()
}

/**
 * Draws the clipped content within the given bounds.
 *
 * @param bounds The bounds within which the content should be clipped.
 * @param style The style configuration for the background item.
 * @param painter The painter used to draw the content.
 * @param shapePath The path defining the shape of the content, if any.
 */
private fun DrawScope.drawClippedContent(
    bounds: Rect,
    style: BackgroundItemStyle.ComposeStyle,
    painter: Painter?,
    shapePath: Path? = null
) {
    // Draw Image if provided
    if (painter != null) {
        drawScaledImage(bounds, style, painter)
    }

    // Draw the shape with color
    val colorAlpha = style.opacity.toFloat() / 100f
    val colorWithOpacity = style.color.copy(alpha = colorAlpha)
    if (shapePath != null) {
        drawPath(
            path = shapePath,
            color = colorWithOpacity,
            style = Fill
        )
    } else {
        drawRect(
            color = colorWithOpacity,
            topLeft = bounds.topLeft,
            size = bounds.size,
            style = Fill
        )
    }
}

/**
 * Draw the image with scaling and opacity
 *
 * @param bounds The bounds of the shape
 * @param style The style of the shape
 * @param painter The painter of the image
 */
private fun DrawScope.drawScaledImage(
    bounds: Rect,
    style: BackgroundItemStyle.ComposeStyle,
    painter: Painter
) {
    withTransform({
        translate(bounds.left, bounds.top)
    }) {
        var drawSize = bounds.size
        if (painter.intrinsicSize != Size.Unspecified &&
            painter.intrinsicSize.width > 0 &&
            painter.intrinsicSize.height > 0
        ) {
            val painterAspect = painter.intrinsicSize.width / painter.intrinsicSize.height
            val boundsAspect = bounds.width / bounds.height
            drawSize = if (painterAspect > boundsAspect) {
                Size(bounds.width, bounds.width / painterAspect)
            } else {
                Size(bounds.height * painterAspect, bounds.height)
            }
        }
        val offsetX = (bounds.width - drawSize.width) / 2f
        val offsetY = (bounds.height - drawSize.height) / 2f
        translate(offsetX, offsetY) {
            with(painter) {
                draw(size = drawSize, alpha = (100f - style.opacity.toFloat()) / 100f)
            }
        }
    }
}

/**
 * Scale and translate the path to fit the target bounds
 *
 * @param sourcePath The source path to be scaled and translated
 * @param targetBounds The target bounds to fit the path into
 * @return The scaled and translated path
 */
private fun Path.scaleAndTranslatePath(
    sourcePath: Path,
    targetBounds: Rect,
    innerPadding: Float = 0f
): Path = apply {
    // Adjust target bounds with padding
    val paddedBounds = Rect(
        left = targetBounds.left + innerPadding,
        top = targetBounds.top + innerPadding,
        right = targetBounds.right - innerPadding,
        bottom = targetBounds.bottom - innerPadding
    )

    val pathBounds = sourcePath.getBounds()
    val scaleX = paddedBounds.width / pathBounds.width
    val scaleY = paddedBounds.height / pathBounds.height

    // Create a new scaled path
    val scaledPath = Path().apply {
        addPath(sourcePath)
        transform(Matrix().apply {
            scale(scaleX, scaleY)
        })
    }

    // Calculate the offset to center the path in padded bounds
    val scaledBounds = scaledPath.getBounds()
    val offsetX = paddedBounds.left - scaledBounds.left
    val offsetY = paddedBounds.top - scaledBounds.top

    // Add the scaled path with translation offset
    addPath(
        path = scaledPath,
        offset = Offset(
            x = offsetX,
            y = offsetY
        )
    )
}
