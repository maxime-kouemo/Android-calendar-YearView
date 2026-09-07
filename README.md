<div align="center">

# YearView

**A full-year, twelve-month calendar for Android — in Jetpack Compose *and* the classic View system.**

The twelve-months-on-one-screen view you know from the Samsung calendar, drawn onto a
single canvas. Pick the front-end that matches your codebase; both share one calendar
engine and one styling vocabulary.

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Language](https://img.shields.io/badge/kotlin-100%25-7F52FF.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![JitPack](https://img.shields.io/badge/JitPack-1.0.2-blue.svg)](https://jitpack.io/#maxime-kouemo/Android-calendar-YearView)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

</div>

<p align="center">
  <img src="demo_files/big_demo.gif" width="320" alt="YearView demo"/>
</p>

---

## Table of contents

- [Why YearView](#why-yearview)
- [Modules](#modules)
- [Which module should I use?](#which-module-should-i-use)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
  - [Jetpack Compose](#jetpack-compose)
  - [Android Views (XML)](#android-views-xml)
- [Features](#features)
- [Date engine](#date-engine)
- [Accessibility](#accessibility)
- [Documentation](#documentation)
- [Sample app](#sample-app)
- [License](#license)

---

## Why YearView

Android ships a picker for **one month**. It does not ship one for a **year**.

`YearView` renders all twelve months at once, and it does so on a **single canvas** — one
`View` or one `@Composable`, with no nested grids, no twelve child layouts and no 366
per-day widgets. Geometry, text measurement and calendar metadata are measured once and
cached between draws, so the whole year stays cheap to render and cheap to page through
inside a `ViewPager2` or a `HorizontalPager`.

## Modules

| Module | Maven coordinate | What it is |
|---|---|---|
| **`:compose`** | `…Android-calendar-YearView:compose` | The Jetpack Compose composable. Styling in `Color` / `Dp` / `TextStyle`. |
| **`:legacy`** | `…Android-calendar-YearView:legacy` | The Android `View`. Styling with `yv_*` XML attributes or setters. |
| **`:core`** | `…Android-calendar-YearView:core` | Shared calendar model, shapes, image sources and date providers. |

The group is `com.github.maxime-kouemo.Android-calendar-YearView` in full; it is elided
above for width.

You never declare `:core` yourself — both front-ends expose it with `api`, so
`BackgroundShape`, `ImageSource`, `MergeType`, `TitleGravity`, `FontType`, `CalendarDate`,
`DayOfWeekConstants` and the time providers all arrive transitively.

## Which module should I use?

The two front-ends are **functionally equivalent** — same features, same rendering, same
calendar engine. Choose on idiom, not capability:

| | `:compose` | `:legacy` |
|---|---|---|
| Host screen | Compose | XML layouts |
| Configuration | Immutable `YearViewState` | `yv_*` attributes + setters |
| Selection | Observable `YearViewSelectionState` | `selectedDate` + listener callbacks |
| Callbacks give you | Epoch millis (`Long`) | `CalendarDate` |
| Units | `Dp`, `Color`, `TextStyle` | px `Int`, `@ColorInt`, `Typeface` |
| Also offers | Compose `Path` shapes, tunable touch targets | `Drawable` backgrounds, D-pad navigation, `Parcelable` configs |

A field-by-field breakdown lives in **[FEATURE_COMPARISON.md](FEATURE_COMPARISON.md)**.

## Requirements

| | |
|---|---|
| **minSdk** | 26 |
| **compileSdk** | 35 |
| **Java / Kotlin target** | 17 |
| **Compose** (for `:compose`) | Any recent BOM; `ui`, `ui-text`, `ui-graphics` and `foundation` arrive transitively |

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

Then add the front-end you need:

```kotlin
// app/build.gradle.kts
dependencies {
    // Jetpack Compose
    implementation("com.github.maxime-kouemo.Android-calendar-YearView:compose:1.0.2")

    // …or the View system
    implementation("com.github.maxime-kouemo.Android-calendar-YearView:legacy:1.0.2")
}
```

> **Note**
> `:core` arrives transitively — both front-ends expose it with `api`, so you never
> declare it yourself. The artifacts are published under the Maven group
> `com.github.maxime-kouemo.Android-calendar-YearView`, the same coordinate JitPack
> serves, so `mavenLocal()` and JitPack resolve identically.

## Quick start

### Jetpack Compose

```kotlin
// com.mamboa.yearview.compose.YearView

@Composable
fun YearScreen() {
    YearView(modifier = Modifier.fillMaxSize())
}
```

That already renders the current year in a 4 × 3 grid, fully localised and fully accessible.
Add a year, a selection holder and a click handler:

```kotlin
@Composable
fun YearScreen(year: Int) {
    val state = rememberSaveable(year, saver = YearViewState.Saver) {
        YearViewState(year = year, isDaySelectionVisuallySticky = true)
    }
    val selection = rememberYearViewSelectionState()

    YearView(
        modifier = Modifier.fillMaxSize(),
        state = state,
        selection = selection,
        onDayClick = { millis -> /* … */ },
        onRangeSelected = { start, end -> /* … */ },
    )
}
```

Full guide: **[compose/README.md](compose/README.md)**

### Android Views (XML)

```xml
<com.mamboa.yearview.legacy.YearView
    android:id="@+id/yearView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:yv_current_year="2026"
    app:yv_rows="4"
    app:yv_columns="3"
    app:yv_first_day_of_week="monday" />
```

```kotlin
yearView.setMonthGestureListener(object : YearView.MonthGestureListener {
    override fun onDayClick(date: CalendarDate) { /* … */ }
    override fun onMonthClick(date: CalendarDate) { /* … */ }
    override fun onRangeSelected(start: CalendarDate, end: CalendarDate) { /* … */ }
})
```

Full guide: **[legacy/README.md](legacy/README.md)**

## Features

Available in **both** front-ends:

- 📅 **All twelve months at once**, in a grid you choose (`4 × 3`, `3 × 4`, `2 × 6`, …)
- 🎨 **Everything is styleable** — month titles, weekday headers, ordinary days, weekends,
  today and the selected day each get their own colour, size, font and background
- 🔷 **Five background shapes** — circle, square, rounded square, star, or any vector drawable
- 🖼️ **Background images** with `overlay` / `clip` merge modes
- 👆 **Taps and long-presses** on days and months, with a month-selection flash
- 📌 **Sticky day selection** that survives rotation and process death
- 🗓️ **Range selection** by tap-to-tap *or* by dragging across days
- 🌍 **Localised** month and weekday names, **RTL mirroring**, configurable first day of week
  and custom weekend days
- ♿ **Accessible** — every day is an individually focusable, clickable screen-reader node
- 🧩 **Pluggable date engine** — `kotlinx-datetime` (default), `java.time`, or Joda-Time
- 🔍 **Debug mode** that paints every touch target so you can see what is actually tappable
- ⚡ **Cached layout** — geometry, text measurement and calendar metadata computed once

## Date engine

No date library is baked in. Everything goes through `ICalendarDateTimeProvider`, and three
implementations ship in `:core`:

```kotlin
KotlinxTimeProvider()  // default — kotlinx-datetime
JavaTimeProvider()     // java.time
JodaTimeProvider()     // Joda-Time
```

Pass one via `dateTimeProvider` on either front-end, or implement the interface against
whatever your app already uses.

## Accessibility

Both front-ends expose every day as an individually focusable, clickable node, so
explore-by-touch and swipe navigation work out of the box.

- **`:compose`** adds a two-level traversal: swipe between the twelve months, activate one,
  then move day by day — continuing chronologically across month boundaries.
- **`:legacy`** builds on `ExploreByTouchHelper`, which also gives D-pad and keyboard users
  day-by-day traversal, and localises its announcements through `res/values/strings.xml`.

## Documentation

| Document | Covers |
|---|---|
| **[compose/README.md](compose/README.md)** | Full Compose guide — state objects, styling, selection, pagers, persistence |
| **[legacy/README.md](legacy/README.md)** | Full View guide — XML attribute reference, programmatic API, ProGuard |
| **[FEATURE_COMPARISON.md](FEATURE_COMPARISON.md)** | Compose vs Legacy, field by field |

## Sample app

The `:app` module is a runnable demo of both front-ends. Clone the repo and run:

```bash
./gradlew :app:installDebug
```

## License

```
MIT License

Copyright (c) 2019 mamboa
```

See [LICENSE](LICENSE) for the full text.

---

<div align="center">

Made by [**mamboa**](https://github.com/maxime-kouemo)

</div>
