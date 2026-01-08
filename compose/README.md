# YearView for Jetpack Compose - User Guide

Welcome to the comprehensive guide for using the `YearView` composable in Jetpack Compose. This highly customizable component from the `com.mamboa.yearview.compose` package allows you to display a yearly calendar view with extensive styling options for months and days, various layout configurations, and user interaction capabilities. This tutorial will walk you through integrating `YearView` into your Android app and utilizing its powerful features.

## Table of Contents
- [Overview](#overview)
- [Setup](#setup)
- [Basic Usage](#basic-usage)
- [Customization Options](#customization-options)
  - [Layout Configuration](#layout-configuration)
  - [Month Styling with MonthConfig](#month-styling-with-monthconfig)
  - [Day Styling](#day-styling)
  - [Background Styling](#background-styling)
  - [Interaction and Callbacks](#interaction-and-callbacks)
  - [Multi-Selection Mode](#multi-selection-mode)
- [Example Implementation](#example-implementation)
- [Year Pager Navigation](#year-pager-navigation)
- [Property Reference](#property-reference)
- [Conclusion](#conclusion)

## Overview

`YearView` is a flexible Jetpack Compose component designed to display a full-year calendar. It supports customizable grid layouts, month and day styling, interactive click and long-press events, multi-selection for date ranges, background images, custom shapes, and accessibility features. Whether you need a simple year-at-a-glance view or a complex date picker, `YearView` offers the tools to tailor the calendar to your app's needs.

## Setup

Before using `YearView`, ensure that the library or module containing `YearView.kt` is included in your project. If you're using a custom library like `com.mamboa.yearview`, add it to your `build.gradle`:

```gradle
dependencies {
    implementation project(":yearview")
}
```

Make sure your project is set up to use Jetpack Compose by enabling it in your `build.gradle`:

```gradle
buildFeatures {
    compose true
}
composeOptions {
    kotlinCompilerExtensionVersion "1.5.0"
}
dependencies {
    implementation "androidx.compose.ui:ui:1.5.0"
    implementation "androidx.compose.material3:material3:1.1.1"
    implementation "androidx.compose.runtime:runtime:1.5.0"
}
```

## Basic Usage

To display a simple yearly calendar, use `YearView` with minimal parameters. Here's a basic example:

```kotlin
@Composable
fun SimpleYearView() {
    val currentYear = DateTime().year().get()
    YearView(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        year = currentYear
    )
}
```

This will render a calendar for the current year with default styling and a 4x3 grid layout (4 rows, 3 columns).

## Customization Options

`YearView` provides a wide range of parameters to customize its appearance and behavior.

### Layout Configuration

Adjust the grid layout of the calendar by setting the number of rows and columns, spacing, and the first day of the week.

- **Rows and Columns**: Set `rows` and `columns` to configure the grid. Ensure `rows * columns >= 12` to display all months.
- **Spacing**: Use `verticalSpacing` and `horizontalSpacing` to control gaps between months.
- **First Day of Week**: Set `firstDayOfWeek` to define the starting day (1 for Monday, 7 for Sunday).

```kotlin
YearView(
    year = 2023,
    rows = 4,
    columns = 3,
    verticalSpacing = 16.dp,
    horizontalSpacing = 16.dp,
    firstDayOfWeek = 7 // Sunday as the first day
)
```

### Month Styling with MonthConfig

The `MonthConfig` data class groups settings for month appearance, including title alignment, margins, and background styles.

- **Title Gravity**: Set `titleGravity` to align the month name (`CENTER`, `START`, `LEFT`, `RIGHT`, `END`).
- **Margin Below Month Name**: Use `marginBelowMonthName` to adjust spacing below the title.
- **Name Styles**: Define text styles with `nameStyle` for regular months and `todayNameStyle` for the current month.
- **Name Format**: Use `nameFormat` to set the month name format (e.g., "MMMM" for full name, "MMM" for abbreviated).

```kotlin
YearView(
    year = 2023,
    monthConfig = MonthConfig(
        titleGravity = TitleGravity.CENTER,
        marginBelowMonthName = 8.dp,
        nameStyle = TextStyle(
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        ),
        todayNameStyle = TextStyle(
            color = Color.Blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        ),
        nameFormat = "MMMM"
    )
)
```

### Day Styling

Style individual days with text and background configurations for normal days, weekends, today's date, and selected days.

- **Day Text Styles**: Customize text with `simpleDayStyle`, `weekendDayStyle`, `todayStyle`, and `selectedDayStyle`.
- **Day Name Style**: Style day-of-week headers with `dayNameStyle`.

```kotlin
YearView(
    year = 2023,
    simpleDayStyle = TextStyle(color = Color.Black, fontSize = 10.sp),
    weekendDayStyle = TextStyle(color = Color.Red, fontSize = 10.sp),
    todayStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
    selectedDayStyle = TextStyle(color = Color.White, fontSize = 10.sp),
    dayNameStyle = TextStyle(color = Color.DarkGray, fontSize = 10.sp)
)
```

### Background Styling

Customize backgrounds for months, today's date, and selected days using `BackgroundItemStyle`.

- **Month Backgrounds**: Set via `MonthConfig` with `backgroundItemStyle` and `selectionBackgroundItemStyle`.
- **Day Backgrounds**: Use `todayBackgroundItemStyle` for today, `selectedDayBackgroundItemStyle` for selected days, and `multiSelectionBackgroundItemStyle` for ranges.
- **Background Properties**: Include `color`, `shape` (e.g., `RoundedSquare`, `Circle`, `Square`), `density` (opacity over images), `image` (optional drawable or bitmap), and `selectionMargin`.

```kotlin
YearView(
    year = 2023,
    monthConfig = MonthConfig(
        backgroundItemStyle = BackgroundItemStyle(
            color = Color(0xFFE3F2FD),
            shape = BackgroundShape.RoundedSquare(cornerRadius = 8f),
            density = 70,
            image = ImageSource.DrawableRes(R.drawable.my_background_image)
        ),
        selectionBackgroundItemStyle = BackgroundItemStyle(
            color = Color(0xFF1976D2),
            shape = BackgroundShape.RoundedSquare(cornerRadius = 8f),
            density = 30
        )
    ),
    todayBackgroundItemStyle = BackgroundItemStyle(
        color = Color(0xFF1976D2),
        shape = BackgroundShape.Circle(radius = 5.0f)
    ),
    selectedDayBackgroundItemStyle = BackgroundItemStyle(
        color = Color(0xFF4CAF50),
        shape = BackgroundShape.RoundedSquare(cornerRadius = 8f)
    )
)
```

### Interaction and Callbacks

`YearView` supports click and long-press events for days and months to handle user interactions.

- **Day Events**: Use `onDayClick` and `onDayLongClick` for day interactions.
- **Month Events**: Use `onMonthClick` and `onMonthLongClick` for month interactions.
- **Sticky Selection**: Enable `isDaySelectionVisuallySticky` to keep selections highlighted until a new selection is made.

```kotlin
YearView(
    year = 2023,
    isDaySelectionVisuallySticky = true,
    onDayClick = { timestamp ->
        val dateTime = DateTime(timestamp)
        Log.d("YearView", "Day clicked: ${dateTime.toString("yyyy-MM-dd")}")
    },
    onDayLongClick = { timestamp ->
        val dateTime = DateTime(timestamp)
        Log.d("YearView", "Day long-pressed: ${dateTime.toString("yyyy-MM-dd")}")
    },
    onMonthClick = { timestamp ->
        val dateTime = DateTime(timestamp)
        Log.d("YearView", "Month clicked: ${dateTime.toString("MMMM yyyy")}")
    },
    onMonthLongClick = { timestamp ->
        val dateTime = DateTime(timestamp)
        Log.d("YearView", "Month long-pressed: ${dateTime.toString("MMMM yyyy")}")
    }
)
```

### Multi-Selection Mode

Enable range selection for selecting multiple days, ideal for date range pickers.

- **Enable Multi-Selection**: Set `enableMultiSelection` to `true`.
- **Range Callback**: Use `onRangeSelected` to receive start and end timestamps of the selected range.
- **Range Background**: Customize with `multiSelectionBackgroundItemStyle`.

```kotlin
YearView(
    year = 2023,
    enableMultiSelection = true,
    multiSelectionBackgroundItemStyle = BackgroundItemStyle(
        color = Color.Cyan.copy(alpha = 0.3f),
        shape = BackgroundShape.Square,
        selectionMargin = 5.0f
    ),
    onRangeSelected = { startTimestamp, endTimestamp ->
        val start = DateTime(startTimestamp)
        val end = DateTime(endTimestamp)
        Log.d("YearView", "Range selected: ${start.toString("yyyy-MM-dd")} to ${end.toString("yyyy-MM-dd")}")
    }
)
```

## Example Implementation

Here's a comprehensive example integrating various customization options:

```kotlin
@Composable
fun CustomYearView() {
    val currentYear = DateTime().year().get()
    val context = LocalContext.current
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF0F0F0)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxHeight(1f).padding(8.dp)) {
                    YearView(
                        modifier = Modifier.fillMaxSize(),
                        year = currentYear,
                        rows = 4,
                        columns = 3,
                        firstDayOfWeek = 7,
                        verticalSpacing = 12.dp,
                        horizontalSpacing = 12.dp,
                        isDaySelectionVisuallySticky = true,
                        enableMultiSelection = false,
                        monthConfig = MonthConfig(
                            titleGravity = TitleGravity.CENTER,
                            marginBelowMonthName = 8.dp,
                            nameStyle = TextStyle(
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            todayNameStyle = TextStyle(
                                color = Color(0xFF1976D2),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            nameFormat = "MMMM",
                            backgroundItemStyle = BackgroundItemStyle(
                                color = Color.White,
                                shape = BackgroundShape.RoundedSquare(cornerRadius = 8f),
                                density = 80
                            ),
                            selectionBackgroundItemStyle = BackgroundItemStyle(
                                color = Color(0xFF1976D2).copy(alpha = 0.2f),
                                shape = BackgroundShape.RoundedSquare(cornerRadius = 10f),
                                density = 40
                            )
                        ),
                        dayNameStyle = TextStyle(color = Color.DarkGray, fontSize = 9.sp),
                        simpleDayStyle = TextStyle(color = Color.Black, fontSize = 9.sp),
                        weekendDayStyle = TextStyle(color = Color(0xFFD32F2F), fontSize = 9.sp),
                        todayStyle = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        selectedDayStyle = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        todayBackgroundItemStyle = BackgroundItemStyle(
                            color = Color(0xFF1976D2),
                            shape = BackgroundShape.RoundedSquare(cornerRadius = 6f)
                        ),
                        selectedDayBackgroundItemStyle = BackgroundItemStyle(
                            color = Color(0xFF4CAF50),
                            shape = BackgroundShape.Circle(radius = 0f),
                            density = 100
                        ),
                        onDayClick = { timestamp ->
                            val dateTime = DateTime(timestamp)
                            val dayString = dateTime.toString("EEEE, dd MMMM yyyy")
                            Toast.makeText(context, "Selected: $dayString", Toast.LENGTH_LONG).show()
                        },
                        onMonthClick = { timestamp ->
                            val dateTime = DateTime(timestamp)
                            val monthYearString = dateTime.toString("MMMM yyyy")
                            Toast.makeText(context, "Month Focus: $monthYearString", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
```

## Year Pager Navigation

Create a navigable year pager to switch between years easily:

```kotlin
val MIN_YEAR = 1945
val MAX_YEAR = 2045
val currentYear = DateTime().year().get()

Column(modifier = Modifier.fillMaxSize()) {
    val pagerState = rememberPagerState(pageCount = { MAX_YEAR - MIN_YEAR + 1 })
    val years = (MIN_YEAR..MAX_YEAR).toList()
    val coroutineScope = rememberCoroutineScope()
    
    // Year tabs
    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        edgePadding = 0.dp
    ) {
        years.forEachIndexed { index, year ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                text = { Text(text = year.toString()) }
            )
        }
    }
    
    // Year pager
    HorizontalPager(state = pagerState) { page ->
        val year = MIN_YEAR + page
        Box(modifier = Modifier.fillMaxSize()) {
            YearView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                year = year
                // Add your customization here
            )
        }
    }
    
    // Scroll to current year on first launch
    LaunchedEffect(Unit) {
        val initialPage = currentYear - MIN_YEAR
        if (initialPage >= 0 && initialPage < pagerState.pageCount) {
            pagerState.scrollToPage(initialPage)
        }
    }
}
```

## Property Reference

### YearView Properties

| Property | Description | Default |
|----------|-------------|---------|
| `year` | Year to display | 2023 |
| `rows` | Number of rows for months | 4 |
| `columns` | Number of columns for months | 3 |
| `verticalSpacing` | Space between month rows | 8.dp |
| `horizontalSpacing` | Space between month columns | 8.dp |
| `monthConfig` | Month appearance configuration | MonthConfig() |
| `firstDayOfWeek` | Starting day of week (1=Mon, 7=Sun) | 1 |
| `todayBackgroundItemStyle` | Background style for current day | BackgroundItemStyle |
| `selectedDayBackgroundItemStyle` | Background style for selected days | BackgroundItemStyle |
| `dayNameTranscendsWeekend` | Apply day name style to weekend labels | false |
| `isDaySelectionVisuallySticky` | Keep selection visible until next selection | false |
| `simpleDayStyle` | Style for regular days | TextStyle |
| `weekendDayStyle` | Style for weekend days | TextStyle |
| `todayStyle` | Style for current day | TextStyle |
| `dayNameStyle` | Style for day-of-week labels | TextStyle |
| `selectedDayStyle` | Style for selected days | TextStyle |
| `monthNameFormat` | Format for month names | "MMMM" |
| `dayFormat` | Format for identifying days | "yyyy-MM-dd" |
| `onMonthClick` | Called when month clicked | (timestamp) -> Unit |
| `onMonthLongClick` | Called when month long-pressed | (timestamp) -> Unit |
| `onDayClick` | Called when day clicked | (timestamp) -> Unit |
| `onDayLongClick` | Called when day long-pressed | (timestamp) -> Unit |
| `enableMultiSelection` | Enable date range selection | false |
| `multiSelectionBackgroundItemStyle` | Style for selected range | BackgroundItemStyle |
| `onRangeSelected` | Called when date range selected | (startTime, endTime) -> Unit |

### BackgroundItemStyle Properties

| Property | Description | Default |
|----------|-------------|---------|
| `color` | Background color | Color |
| `shape` | Shape (Circle, Square, RoundedSquare) | BackgroundShape |
| `selectionMargin` | Margin around the item | 2.0f |
| `image` | Background image | ImageSource.None |
| `density` | Color opacity over image (0-100) | 100 |

### MonthConfig Properties

| Property | Description | Default |
|----------|-------------|---------|
| `titleGravity` | Month name alignment | TitleGravity.CENTER |
| `marginBelowMonthName` | Space below month name | 8.dp |
| `selectionBackgroundItemStyle` | Style when month selected | BackgroundItemStyle |
| `backgroundItemStyle` | Month background style | BackgroundItemStyle |
| `nameStyle` | Month name text style | TextStyle |
| `todayNameStyle` | Current month name style | TextStyle |
| `nameFormat` | Month name format | "MMMM" |

## Conclusion

The `YearView` composable in Jetpack Compose offers a powerful and flexible way to display and interact with a yearly calendar. With extensive customization options for layout, styling, and user interactions, you can create a calendar that perfectly fits your app's design and functionality requirements. Experiment with the parameters and integrate callbacks to enhance user experience. For further assistance or to dive deeper, refer to the source code or reach out for support.