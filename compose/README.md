<div align="center">

# YearView — Jetpack Compose

**A full-year, twelve-month calendar composable.**

One `@Composable`, one immutable state object, one observable selection holder.
Declarative styling in `Dp` / `Color` / `TextStyle`, ranges, TalkBack support and
`rememberSaveable` persistence out of the box.

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](../LICENSE)

</div>

<!--
  Add your screenshots / GIFs here. Suggested layout:

  <p align="center">
    <img src="../demo_files/big_demo.gif" width="320" alt="YearView demo"/>
  </p>
-->

<p align="center">
  <img src="../demo_files/big_demo.gif" width="320" alt="YearView Compose demo"/>
</p>

---

## Table of contents

- [Why YearView](#why-yearview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [The two state objects](#the-two-state-objects)
- [Handling interactions](#handling-interactions)
- [Styling](#styling)
  - [Layout](#layout)
  - [Month titles](#month-titles)
  - [Days, weekends, today and the selected day](#days-weekends-today-and-the-selected-day)
  - [The weekday header row](#the-weekday-header-row)
  - [Backgrounds, shapes and opacity](#backgrounds-shapes-and-opacity)
  - [Background images](#background-images)
  - [Custom fonts](#custom-fonts)
- [Selection](#selection)
  - [Sticky single-day selection](#sticky-single-day-selection)
  - [Range selection](#range-selection)
  - [Driving selection from a ViewModel](#driving-selection-from-a-viewmodel)
- [A year pager](#a-year-pager)
- [Persisting state](#persisting-state)
- [Localisation and date libraries](#localisation-and-date-libraries)
- [Accessibility](#accessibility)
- [Debug mode](#debug-mode)
- [Complete example](#complete-example)
- [API reference](#api-reference)
- [Using the View system instead](#using-the-view-system-instead)
- [License](#license)

---

## Why YearView

Compose gives you a date picker for one month. `YearView` gives you the whole year — the
twelve-months-on-one-screen view you know from the Samsung calendar.

All twelve months are drawn onto a **single `Canvas`**: no nested `LazyGrid`s, no 366 composables.
Text layout, geometry and calendar metadata are measured once and cached, and the composable is
skippable, so it stays cheap inside a `HorizontalPager`.

## Features

- 📅 **All twelve months at once**, in a grid you choose (`4 × 3`, `3 × 4`, `2 × 6`, …)
- 🎨 **Idiomatic Compose styling** — `TextStyle`, `Color`, `Dp` everywhere
- 🧊 **Immutable configuration** in a single `@Immutable data class`, safe to hoist into a ViewModel
- 👀 **Observable selection** — read, write, or clear the selection from anywhere; the calendar
  redraws automatically
- 🔷 **Five background shapes** — circle, square, rounded square, star, vector drawable, or any
  Compose `Path`
- 🖼️ **Background images** with `overlay` / `clip` merge modes
- 👆 **Taps and long-presses** on days and months, with a month-selection flash
- 🗓️ **Range selection** by tap-to-tap *or* drag
- 🌍 **Localised** month and weekday names, **RTL mirroring**, configurable first day of week and
  weekend days
- ♿ **Two-level TalkBack traversal** — swipe between months, enter one, then move day by day
- 💾 **`rememberSaveable` support** via `YearViewState.Saver` and `YearViewSelectionState.Saver`
- 🧩 **Pluggable date engine** — `kotlinx-datetime` (default), `java.time`, or Joda-Time
- 🔍 **Debug mode** that paints every touch target

## Requirements

| | |
|---|---|
| **minSdk** | 26 |
| **compileSdk** | 35 |
| **Java / Kotlin target** | 17 |
| **Compose** | Any recent BOM; `ui`, `ui-text`, `ui-graphics` and `foundation` arrive transitively |

## Installation

Add JitPack to your repositories:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Then add the module:

```kotlin
// app/build.gradle.kts
android {
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:<your-bom>"))
    implementation("com.github.maxime-kouemo.Android-calendar-YearView:compose:1.0.2")
}
```

> **Note**
> The artifacts are published with the Maven group `com.mamboa.yearview`. If you resolve them
> from `mavenLocal()` or your own Maven repository rather than JitPack, use
> `com.mamboa.yearview:compose:1.0.2` instead.

You do **not** need to declare `:core` — `:compose` exposes it with `api`, so `BackgroundShape`,
`ImageSource`, `MergeType`, `TitleGravity`, `CalendarDate`, `DayOfWeekConstants` and the time
providers all come along automatically.

## Quick start

```kotlin
import com.mamboa.yearview.compose.YearView

@Composable
fun YearScreen() {
    YearView(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}
```

That renders the current year in a 4 × 3 grid, fully localised and fully accessible.

Add a year and a click handler:

```kotlin
@Composable
fun YearScreen(year: Int) {
    val state = remember(year) { YearViewState(year = year) }
    val provider = remember { KotlinxTimeProvider() }

    YearView(
        modifier = Modifier.fillMaxSize(),
        state = state,
        onDayClick = { millis ->
            val date = provider.fromMillis(millis)
            Log.d("YearView", provider.format(date, "yyyy-MM-dd", Locale.getDefault()))
        }
    )
}
```

## The two state objects

`YearView` deliberately splits **configuration** from **selection**, so each has exactly one owner.

| | `YearViewState` | `YearViewSelectionState` |
|---|---|---|
| What it holds | How the calendar looks and behaves | What the user has selected |
| Kind | `@Immutable data class` | `@Stable` class backed by snapshot state |
| Changed by | You, via `copy(...)` | Taps *and* your code |
| Persisted with | `YearViewState.Saver` | `YearViewSelectionState.Saver` |

```kotlin
val state = rememberSaveable(saver = YearViewState.Saver) {
    YearViewState(year = 2026, rows = 4, columns = 3)
}
val selection = rememberYearViewSelectionState(selectedDay = "2026-01-10")

YearView(state = state, selection = selection)
```

> **Important**
> `rows × columns` **must equal 12**. `YearViewState` throws in its `init` block otherwise, so a
> typo fails loudly instead of silently dropping months.

Callbacks are *not* part of `YearViewState` — lambdas are not state and are not serialisable, so
they stay as separate parameters on the composable.

## Handling interactions

Every callback receives **epoch millis**:

```kotlin
YearView(
    state = state,
    selection = selection,

    onDayClick        = { millis -> /* tapped day */ },
    onDayLongClick    = { millis -> /* long-pressed day */ },
    onMonthClick      = { millis -> /* 1st of the tapped month */ },
    onMonthLongClick  = { millis -> /* … */ },

    // Sticky selection only. Receives -1L when the selection is cleared.
    onSelectedDayChange = { millis -> /* … */ },
    // Complements the above with the deselected day's own timestamp.
    onDayDeselected     = { millis -> /* … */ },

    // Multi-selection only. Fires once a range is complete; start <= end.
    onRangeSelected = { startMillis, endMillis -> /* … */ },
)
```

Convert them with the date-time provider:

```kotlin
val date = provider.fromMillis(millis)
val text = provider.format(date, "EEEE d MMMM yyyy", Locale.getDefault())
```

## Styling

Everything below is a field on `YearViewState` (or on the `MonthConfig` it holds), so you change
it the Compose way — by producing a new state:

```kotlin
val state = remember { YearViewState() }
val redState = remember(state) {
    state.copy(todayConfig = state.todayConfig.copy(textStyle = TextStyle(color = Color.Red)))
}
```

### Layout

```kotlin
YearViewState(
    year = 2026,
    rows = 4,
    columns = 3,                                  // rows * columns must be 12
    verticalSpacing = 12.dp,
    horizontalSpacing = 12.dp,
    firstDayOfWeek = DayOfWeekConstants.SUNDAY,   // 1 = Monday … 7 = Sunday
    weekendDays = setOf(DayOfWeekConstants.SATURDAY, DayOfWeekConstants.SUNDAY),
    dayTouchPadding = 4.dp,                       // size of each day's touch target
)
```

### Month titles

```kotlin
MonthConfig(
    titleGravity = TitleGravity.CENTER,   // CENTER, START, LEFT, RIGHT, END
    marginBelowMonthName = 8.dp,
    nameFormat = "MMMM",                  // "MMM" for abbreviated
    nameStyle = TextStyle(
        color = Color.Black,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
    ),
    todayNameStyle = TextStyle(
        color = Color(0xFFD00606),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
    ),
)
```

`START` / `END` mirror in RTL layouts; `LEFT` / `RIGHT` do not.

### Days, weekends, today and the selected day

Ordinary and weekend days are nested inside `MonthConfig`; today and the selected day live on
`YearViewState`:

```kotlin
YearViewState(
    monthConfig = MonthConfig(
        simpleDayConfig = DayConfig(
            textStyle = TextStyle(color = Color.Black, fontSize = 9.sp)
        ),
        weekendDayConfig = DayConfig(
            textStyle = TextStyle(color = Color(0xFFD32F2F), fontSize = 9.sp)
        ),
    ),
    todayConfig = DayConfig(
        backgroundItemStyle = ComposeBackgroundStyle(
            color = Color(0xFFD10606),
            shape = BackgroundShape.Circle(radius = 1.0f),
        ),
        textStyle = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
    ),
    selectedDayConfig = DayConfig(
        backgroundItemStyle = ComposeBackgroundStyle(
            color = Color(0xFF4CAF50),
            shape = BackgroundShape.Circle(radius = 1.0f),
        ),
        textStyle = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
    ),
)
```

`DayConfig.backgroundRadius` is a **multiplier** (default `1f`) that adds breathing room between
the day number and its background edge — it is resolution-independent, so it looks the same on
every density.

### The weekday header row

```kotlin
YearViewState(
    dayNameStyle = TextStyle(color = Color.DarkGray, fontSize = 9.sp),
    dayNameLength = 1,               // "M T W T F S S"
    dayNameTranscendsWeekend = false,
)
```

> **Tip**
> `dayNameLength = 1` gives the compact single-letter look, but one letter is ambiguous in
> English (T/T, S/S) and meaningless in several scripts. Set `2` or `3` for those locales, or
> **`0` to use the locale's full short name** unmodified.

By default weekend **columns** in the header take the weekend text style. Set
`dayNameTranscendsWeekend = true` to keep `dayNameStyle` everywhere.

### Backgrounds, shapes and opacity

`ComposeBackgroundStyle` describes every background in the library:

```kotlin
ComposeBackgroundStyle(
    color = Color(0xFF1976D2),
    shape = BackgroundShape.RoundedSquare(cornerRadius = 8f),  // cornerRadius is in dp
    selectionMargin = 4.dp,
    image = ImageSource.None,
    opacity = 100,                 // 0..100
    mergeType = MergeType.OVERLAY,
)
```

Available shapes (all from `:core`):

| Shape | Constructor |
|---|---|
| Circle | `BackgroundShape.Circle(radius = 1.0f)` |
| Square | `BackgroundShape.Square` |
| Rounded square | `BackgroundShape.RoundedSquare(cornerRadius = 8f)` |
| Star | `BackgroundShape.Star(numberOfLegs = 6, innerRadiusRatio = 0.45f)` — 3–10 legs |
| Vector drawable | `BackgroundShape.Custom(ResourcePathProvider(R.drawable.heart, R.dimen.inner_radius))` |
| Any Compose `Path` | `BackgroundShape.Custom(ComposePathProvider(myPath, innerPadding = 2.dp))` |

> **Important**
> Transparency comes from `opacity`, **not** from the colour's alpha channel:
>
> ```kotlin
> ComposeBackgroundStyle(color = Color.Cyan.copy(alpha = 0.3f))  // ❌ renders fully opaque
> ComposeBackgroundStyle(color = Color.Cyan, opacity = 30)       // ✅
> ```

### Background images

```kotlin
ComposeBackgroundStyle(
    color = Color(0xFF4CAF50),
    shape = BackgroundShape.Custom(
        provider = ResourcePathProvider(
            drawableRes = R.drawable.heart,
            innerPadding = R.dimen.inner_radius,
        )
    ),
    image = ImageSource.Provided(ResourceImageProvider(R.drawable.shopping)),
    mergeType = MergeType.CLIP,   // clip the image to the heart outline
    opacity = 30,
)
```

- **`MergeType.OVERLAY`** — the colour is painted on top of the image.
- **`MergeType.CLIP`** — the image is clipped to the shape's outline.

Images can come from a drawable resource (`ResourceImageProvider`) or an `ImageBitmap`
(`BitmapImageProvider`).

### Custom fonts

There is no separate `fontFamily` parameter — `TextStyle` already has one:

```kotlin
val teddyBears = FontFamily(Font(R.font.teddy_bears))

MonthConfig(
    nameStyle = TextStyle(
        fontFamily = teddyBears,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
    )
)
```

> **Note**
> `TextStyle.fontFamily` is **not** preserved by `YearViewState.Saver` — arbitrary `FontFamily`
> instances cannot be serialised. Re-supply custom fonts after restoration.

## Selection

### Sticky single-day selection

```kotlin
val state = remember { YearViewState(isDaySelectionVisuallySticky = true) }
val selection = rememberYearViewSelectionState(selectedDay = "2026-01-10")

YearView(state = state, selection = selection)

Text("Selected: ${selection.selectedDay.ifBlank { "none" }}")
```

With sticky selection on, a tapped day keeps its highlight until another day is tapped; tapping
the same day again clears it. With it off, a tap simply fires `onDayClick`.

Day strings use the pattern in `YearViewState.dayFormat` (default `yyyy-MM-dd`). To get millis:

```kotlin
val millis: Long? = selection.selectedDayMillis(dayFormat = state.dayFormat)
```

### Range selection

```kotlin
val state = remember {
    YearViewState(
        enableMultiSelection = true,
        multiSelectionBackgroundItemStyle = ComposeBackgroundStyle(
            color = Color.Cyan,
            shape = BackgroundShape.Square,
            selectionMargin = 5.dp,
            opacity = 30,
        ),
    )
}
val selection = rememberYearViewSelectionState()

YearView(
    state = state,
    selection = selection,
    onRangeSelected = { start, end -> viewModel.onRangeChosen(start, end) },
)

if (selection.hasCompleteRange) {
    Text("${selection.rangeStart} → ${selection.rangeEnd}")
    Button(onClick = { selection.clearRange() }) { Text("Clear") }
}
```

A range is built either by **tapping the two endpoints** or by **pressing and dragging** across
days. Endpoints are normalised so `start <= end`.

### Driving selection from a ViewModel

Because `YearViewSelectionState` is snapshot state, you can observe it declaratively instead of
plumbing callbacks:

```kotlin
val selection = rememberYearViewSelectionState()

YearView(state = state, selection = selection)

LaunchedEffect(selection.rangeStart, selection.rangeEnd) {
    viewModel.onRangeChanged(selection.rangeStart, selection.rangeEnd)
}

// …and write to it from anywhere:
Button(onClick = { selection.setRange("2026-03-01", "2026-03-15") }) { Text("March") }
Button(onClick = { selection.clearAll() }) { Text("Reset") }
```

| Member | Purpose |
|---|---|
| `selectedDay: String` | The sticky selection, `""` when empty. Read **and** write |
| `rangeStart` / `rangeEnd: String?` | Range endpoints; read-only — write via `setRange` |
| `hasCompleteRange: Boolean` | Both ends set |
| `setRange(start, end)` | Writes both ends in one snapshot, so observers never see a half-updated range |
| `clearSelection()` / `clearRange()` / `clearAll()` | |

## A year pager

```kotlin
private const val MIN_YEAR = 1945
private const val MAX_YEAR = 2045

@Composable
fun YearPager() {
    val years = remember { (MIN_YEAR..MAX_YEAR).toList() }
    val pagerState = rememberPagerState(pageCount = { years.size })
    val currentYear = remember { KotlinxTimeProvider().currentYear() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState, currentYear) {
        pagerState.scrollToPage((currentYear - MIN_YEAR).coerceIn(years.indices))
    }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
            years.forEachIndexed { index, year ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(year.toString()) },
                )
            }
        }

        HorizontalPager(state = pagerState, beyondViewportPageCount = 1) { page ->
            val year = years[page]
            val state = remember(year) { YearViewState(year = year) }
            YearView(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                state = state,
            )
        }
    }
}
```

## Persisting state

Both state objects ship a `Saver`:

```kotlin
val state = rememberSaveable(saver = YearViewState.Saver) { YearViewState(year = 2026) }
val selection = rememberYearViewSelectionState()   // already uses rememberSaveable internally
```

`YearViewState.Saver` decomposes every field into Bundle-safe primitives and reads them back with
checked casts and per-field fallbacks — a bundle written by an older build of your app degrades to
defaults instead of crashing on resume.

> **Note**
> Two things are not restored: `TextStyle.fontFamily`, and non-serialisable custom shape / image
> providers (a `ComposePathProvider` falls back to `Square`). Re-supply them after restoration.

## Localisation and date libraries

Month names and weekday abbreviations follow the device locale. RTL locales mirror the month grid,
the weekday columns and `START` / `END` title gravity.

```kotlin
YearView(
    state = state,
    dateTimeProvider = remember { JavaTimeProvider() },   // or JodaTimeProvider(), KotlinxTimeProvider()
)
```

You can implement `ICalendarDateTimeProvider` yourself for a non-Gregorian calendar.

## Accessibility

An invisible semantics overlay gives TalkBack **two-level traversal**:

1. Swipes move between the twelve **months**.
2. Activating a month switches to **day-level** navigation, which continues chronologically across
   month boundaries.
3. A custom action returns to month navigation.

The three spoken labels are plain strings on `YearViewState`, so resolve them from your own
resources for non-English locales:

```kotlin
YearViewState(
    todayLabel = stringResource(R.string.a11y_today),
    selectedLabel = stringResource(R.string.a11y_selected),
    exitMonthActionLabel = stringResource(R.string.a11y_exit_month),
)
```

> **Note**
> At twelve months on a phone screen, day touch targets are naturally smaller than the 48 dp
> Material recommendation. Increase `dayTouchPadding` or the day text size when the calendar is
> the primary interactive surface of a screen.

## Debug mode

Debug visualisation is a `CompositionLocal`, not a parameter, so you can flip it for a whole
subtree:

```kotlin
CompositionLocalProvider(LocalYearViewDebug provides BuildConfig.DEBUG) {
    YearView(state = state)
}
```

It paints a translucent red rectangle around every day's touch target.

## Complete example

```kotlin
@Composable
fun CustomYearView(year: Int) {
    val context = LocalContext.current
    val provider = remember { KotlinxTimeProvider() }
    val teddyBears = remember { FontFamily(Font(R.font.teddy_bears)) }

    val state = remember(year, teddyBears) {
        YearViewState(
            year = year,
            rows = 4,
            columns = 3,
            verticalSpacing = 12.dp,
            horizontalSpacing = 12.dp,
            firstDayOfWeek = DayOfWeekConstants.SUNDAY,
            isDaySelectionVisuallySticky = true,
            enableMultiSelection = false,
            dayNameStyle = TextStyle(color = Color.DarkGray, fontSize = 9.sp),
            monthConfig = MonthConfig(
                titleGravity = TitleGravity.CENTER,
                marginBelowMonthName = 4.dp,
                nameFormat = "MMMM",
                nameStyle = TextStyle(
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = teddyBears,
                ),
                todayNameStyle = TextStyle(
                    color = Color(0xFFD00606),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                backgroundItemStyle = ComposeBackgroundStyle(
                    color = Color(0xFF4CAF50),
                    shape = BackgroundShape.Custom(
                        provider = ResourcePathProvider(
                            drawableRes = R.drawable.heart,
                            innerPadding = R.dimen.inner_radius,
                        )
                    ),
                    image = ImageSource.Provided(ResourceImageProvider(R.drawable.shopping)),
                    mergeType = MergeType.CLIP,
                    opacity = 30,
                ),
                selectionBackgroundItemStyle = ComposeBackgroundStyle(
                    color = Color(0xFF1976D2),
                    shape = BackgroundShape.RoundedSquare(cornerRadius = 8f),
                    opacity = 30,
                ),
                simpleDayConfig = DayConfig(
                    textStyle = TextStyle(color = Color.Black, fontSize = 8.sp)
                ),
                weekendDayConfig = DayConfig(
                    textStyle = TextStyle(color = Color(0xFFD32F2F), fontSize = 9.sp)
                ),
            ),
            todayConfig = DayConfig(
                backgroundItemStyle = ComposeBackgroundStyle(
                    color = Color(0xFFD10606),
                    shape = BackgroundShape.Circle(radius = 1.0f),
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            ),
            selectedDayConfig = DayConfig(
                backgroundItemStyle = ComposeBackgroundStyle(
                    color = Color(0xFF4CAF50),
                    shape = BackgroundShape.Circle(radius = 1.0f),
                ),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            ),
        )
    }

    val selection = rememberYearViewSelectionState(selectedDay = "2026-01-10")

    YearView(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        state = state,
        selection = selection,
        dateTimeProvider = provider,
        onDayClick = { millis ->
            val date = provider.fromMillis(millis)
            val text = provider.format(date, "yyyy-MM-dd", Locale.getDefault())
            Toast.makeText(context, "Day: $text", Toast.LENGTH_SHORT).show()
        },
        onMonthClick = { millis ->
            val date = provider.fromMillis(millis)
            val text = provider.format(date, "MMMM yyyy", Locale.getDefault())
            Toast.makeText(context, "Month: $text", Toast.LENGTH_SHORT).show()
        },
    )
}
```

## API reference

### `YearView`

| Parameter | Type | Default |
|---|---|---|
| `modifier` | `Modifier` | `Modifier` |
| `state` | `YearViewState` | `YearViewState()` |
| `selection` | `YearViewSelectionState` | `rememberYearViewSelectionState()` |
| `dateTimeProvider` | `ICalendarDateTimeProvider` | `KotlinxTimeProvider()` |
| `onMonthClick` | `(Long) -> Unit` | `{}` |
| `onMonthLongClick` | `(Long) -> Unit` | `{}` |
| `onDayClick` | `(Long) -> Unit` | `{}` |
| `onDayLongClick` | `(Long) -> Unit` | `{}` |
| `onSelectedDayChange` | `(Long) -> Unit` | `{}` — `-1L` when cleared |
| `onDayDeselected` | `(Long) -> Unit` | `{}` |
| `onRangeSelected` | `(Long, Long) -> Unit` | `{ _, _ -> }` |

### `YearViewState`

| Property | Type | Default |
|---|---|---|
| `year` | `Int` | current year |
| `rows` | `Int` | `4` |
| `columns` | `Int` | `3` |
| `verticalSpacing` | `Dp` | `8.dp` |
| `horizontalSpacing` | `Dp` | `8.dp` |
| `monthConfig` | `MonthConfig` | `MonthConfig()` |
| `firstDayOfWeek` | `Int` | `MONDAY` (1) |
| `todayConfig` | `DayConfig` | `DayConfig()` |
| `selectedDayConfig` | `DayConfig` | blue square, white text |
| `dayNameTranscendsWeekend` | `Boolean` | `false` |
| `isDaySelectionVisuallySticky` | `Boolean` | `false` |
| `dayNameStyle` | `TextStyle` | blue, `10.sp`, centred |
| `dayNameLength` | `Int` | `1` — `0` for the locale's full short name |
| `dayFormat` | `String` | `"yyyy-MM-dd"` |
| `dayTouchPadding` | `Dp` | `2.dp` |
| `weekendDays` | `Set<Int>` | `{SATURDAY, SUNDAY}` |
| `enableMultiSelection` | `Boolean` | `false` |
| `multiSelectionBackgroundItemStyle` | `ComposeBackgroundStyle` | cyan square, `opacity = 30` |
| `todayLabel` | `String` | `"today"` |
| `selectedLabel` | `String` | `"selected"` |
| `exitMonthActionLabel` | `String` | `"Exit month"` |

*Companion:* `YearViewState.Saver`.
*Invariant:* `rows * columns == 12`.

### `MonthConfig`

| Property | Type | Default |
|---|---|---|
| `titleGravity` | `TitleGravity` | `CENTER` |
| `marginBelowMonthName` | `Dp` | `8.dp` |
| `selectionBackgroundItemStyle` | `ComposeBackgroundStyle` | blue rounded square, `opacity = 30` |
| `backgroundItemStyle` | `ComposeBackgroundStyle` | transparent rounded square |
| `nameStyle` | `TextStyle` | black, `12.sp`, bold |
| `todayNameStyle` | `TextStyle` | black, `12.sp`, bold |
| `nameFormat` | `String` | `"MMMM"` |
| `simpleDayConfig` | `DayConfig` | black, `10.sp` |
| `weekendDayConfig` | `DayConfig` | gray, `10.sp` |

### `DayConfig`

| Property | Type | Default |
|---|---|---|
| `backgroundItemStyle` | `ComposeBackgroundStyle` | transparent circle |
| `textStyle` | `TextStyle` | black, `10.sp`, centred |
| `backgroundRadius` | `Float` | `1f` — multiplier, not pixels |

### `ComposeBackgroundStyle`

| Property | Type | Default |
|---|---|---|
| `color` | `Color` | `Color.Transparent` |
| `shape` | `BackgroundShape` | `Square` |
| `selectionMargin` | `Dp` | `2.dp` |
| `image` | `ImageSource` | `None` |
| `opacity` | `Int` | `100` (0–100) |
| `mergeType` | `MergeType` | `OVERLAY` |

### Other public API

| Symbol | Purpose |
|---|---|
| `rememberYearViewSelectionState(selectedDay, rangeStart, rangeEnd)` | Creates a saveable selection holder |
| `YearViewSelectionState.selectedDayMillis(dayFormat, locale, provider)` | Parses `selectedDay` into epoch millis, or `null` |
| `LocalYearViewDebug` | `CompositionLocal<Boolean>` enabling touch-target visualisation |
| `ComposePathProvider(path, innerPadding)` | Uses an arbitrary Compose `Path` as a shape |
| `BitmapImageProvider(imageBitmap)` | Uses an `ImageBitmap` as a background image |

## Using the View system instead

If your screen is XML-based, use the **`:legacy`** module — the same calendar as an
`android.view.View` with `yv_*` XML attributes, `Drawable` backgrounds and keyboard navigation.

See [`legacy/README.md`](../legacy/README.md), and
[`FEATURE_COMPARISON.md`](../FEATURE_COMPARISON.md) for a side-by-side of the two.

## License

MIT — see [LICENSE](../LICENSE).
