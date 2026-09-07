<div align="center">

# YearView — Legacy (Android View)

**A full-year, twelve-month calendar for the classic Android View system.**

Drop it into an XML layout, style every pixel with `yv_*` attributes, and get taps,
long-presses, sticky day selection, date ranges, TalkBack and D-pad navigation for free.

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Language](https://img.shields.io/badge/kotlin-100%25-7F52FF.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](../LICENSE)

</div>

<!--
  Add your screenshots / GIFs here. Suggested layout:

  <p align="center">
    <img src="../demo_files/big_demo.gif" width="320" alt="YearView demo"/>
  </p>
-->

<p align="center">
  <img src="../demo_files/big_demo.gif" width="320" alt="YearView legacy demo"/>
</p>

---

## Table of contents

- [Why YearView](#why-yearview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Listening to interactions](#listening-to-interactions)
- [Configuring the calendar](#configuring-the-calendar)
  - [Grid and calendar basics](#grid-and-calendar-basics)
  - [Two ways to style](#two-ways-to-style)
  - [Text, colours and fonts](#text-colours-and-fonts)
  - [Backgrounds and shapes](#backgrounds-and-shapes)
  - [Background images](#background-images)
  - [Weekend days](#weekend-days)
- [Selection](#selection)
  - [Sticky single-day selection](#sticky-single-day-selection)
  - [Range (multi) selection](#range-multi-selection)
- [Localisation and date libraries](#localisation-and-date-libraries)
- [Accessibility](#accessibility)
- [Debug mode](#debug-mode)
- [State restoration](#state-restoration)
- [XML attribute reference](#xml-attribute-reference)
- [Programmatic API reference](#programmatic-api-reference)
- [ProGuard / R8](#proguard--r8)
- [Using Jetpack Compose instead](#using-jetpack-compose-instead)
- [License](#license)

---

## Why YearView

Android ships a month picker. It does not ship a **year** picker — the twelve-months-on-one-screen
view you know from the Samsung calendar. `YearView` is that view.

Everything is drawn onto a **single canvas**: one `View`, no nested `RecyclerView`s, no twelve
child layouts. Geometry, text measurement and calendar metadata are cached between draws, so the
whole year stays cheap to render and cheap to scroll inside a `ViewPager2`.

## Features

- 📅 **All twelve months at once**, in a grid you choose (`4 × 3`, `3 × 4`, `6 × 2`, …)
- 🎨 **Everything is styleable** — month titles, weekday headers, ordinary days, weekends, today
  and the selected day each get their own colour, size, font and background
- 🔷 **Five background shapes** — circle, square, rounded square, star, or any **vector drawable**
- 🖼️ **Background images** per month / day, with independent colour and image opacity and two
  merge modes (`overlay`, `clip`)
- 👆 **Taps and long-presses** on both days and months, with a customisable selection flash
- 📌 **Sticky day selection** that survives rotation
- 🗓️ **Range selection** by tap-to-tap *or* by dragging across days
- 🌍 **Localised** month and weekday names, **RTL mirroring**, configurable first day of week
  and custom weekend days
- ♿ **Accessible** — every day is a focusable TalkBack node, plus full D-pad / keyboard traversal
- 🧩 **Pluggable date engine** — `kotlinx-datetime` (default), `java.time`, or Joda-Time
- 💾 **Parcelable configuration** — pass styling through `Intent`s and `Bundle`s
- 🔍 **Debug mode** that paints every touch target so you can see what is actually tappable

## Requirements

| | |
|---|---|
| **minSdk** | 26 |
| **compileSdk** | 35 |
| **Java / Kotlin target** | 17 |
| **Dependencies pulled in** | `com.mamboa.yearview:core` (transitively, via `api`) |

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
dependencies {
    implementation("com.github.maxime-kouemo.Android-calendar-YearView:legacy:1.0.2")
}
```

> **Note**
> The artifacts are published with the Maven group `com.mamboa.yearview`. If you resolve them
> from `mavenLocal()` or your own Maven repository rather than JitPack, use
> `com.mamboa.yearview:legacy:1.0.2` instead.

You do **not** need to declare `:core` yourself — `:legacy` exposes it with `api`, so
`CalendarDate`, `BackgroundShape`, `TitleGravity`, `FontType` and the time providers all come
along automatically.

## Quick start

Add the view to a layout:

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

That's it — you already have a fully localised, fully accessible year calendar.

Now wire up a listener:

```kotlin
class YearFragment : Fragment(R.layout.fragment_year) {

    private val dateTimeProvider = KotlinxTimeProvider()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val yearView = view.findViewById<YearView>(R.id.yearView)

        yearView.setMonthGestureListener(object : YearView.MonthGestureListener {
            override fun onDayClick(date: CalendarDate) {
                val text = dateTimeProvider.format(date, "yyyy-MM-dd", Locale.getDefault())
                Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
            }
        })
    }
}
```

> **Tip**
> `MonthGestureListener` has real Java default methods, so you only override the callbacks you
> actually care about — from Kotlin *and* from Java.

Don't forget to release the listener so the view does not outlive your fragment:

```kotlin
override fun onDestroyView() {
    yearView.setMonthGestureListener(null)
    super.onDestroyView()
}
```

## Listening to interactions

```kotlin
yearView.setMonthGestureListener(object : YearView.MonthGestureListener {

    override fun onDayClick(date: CalendarDate) { /* … */ }

    override fun onDayLongClick(date: CalendarDate) { /* … */ }

    override fun onMonthClick(date: CalendarDate) { /* 1st of the tapped month */ }

    override fun onMonthLongClick(date: CalendarDate) { /* … */ }

    /** Only fires when `isDaySelectionVisuallySticky` is true. */
    override fun onSelectedDayChange(date: CalendarDate) { /* … */ }

    /** Complements the above: reports the day that was just un-selected. */
    override fun onDayDeselected(date: CalendarDate) { /* … */ }

    /** Only fires when `isMultiSelectionEnabled` is true, once a range is complete. */
    override fun onRangeSelected(start: CalendarDate, end: CalendarDate) { /* … */ }
})
```

Every callback receives a [`CalendarDate`](../core/src/main/java/com/mamboa/yearview/core/datetime/CalendarDate.kt)
— a plain `(year, month, day)` triple with no dependency on any date library. Convert it with the
provider:

```kotlin
val millis = dateTimeProvider.toMillis(date)
val text   = dateTimeProvider.format(date, "EEEE d MMMM yyyy", Locale.getDefault())
```

## Configuring the calendar

### Grid and calendar basics

```xml
app:yv_current_year="2026"
app:yv_rows="4"
app:yv_columns="3"
app:yv_vertical_spacing="10dp"
app:yv_horizontal_spacing="20dp"
app:yv_first_day_of_week="sunday"
app:yv_day_pattern="yyyy-MM-dd"
```

or at runtime:

```kotlin
yearView.year = 2026
yearView.rows = 4
yearView.columns = 3
yearView.firstDayOfWeek = DayOfWeekConstants.SUNDAY   // 1 = Monday … 7 = Sunday
yearView.setVerticalSpacing(R.dimen.year_v_spacing)   // dimension resource
yearView.setHorizontalSpacingPx(48)                   // raw pixels
```

> **Note**
> `rows × columns` does **not** have to equal 12. The view draws
> `min(rows × columns, 12)` months, so a `2 × 2` grid renders a legible four-month calendar.
> Defaults are `6 × 2`.

### Two ways to style

Every style lives in one of four `Parcelable` configuration objects. You can either replace the
whole object…

```kotlin
yearView.todayConfig = DayConfig(
    backgroundItemStyle = LegacyBackgroundStyle(
        color = Color.parseColor("#D10606"),
        shape = BackgroundShape.Circle(radius = 1.0f)
    ),
    textColor = Color.WHITE,
    textSize = 30,                 // pixels
    fontType = FontType.BOLD,
    backgroundRadius = 1.4f        // breathing room around the number
)
```

…or nudge a single field with a granular setter:

```kotlin
yearView.setTodayTextColor(R.color.brand_red)   // colour *resource*
yearView.setTodayTextColorInt(0xFFD10606.toInt()) // resolved @ColorInt
yearView.setTodayTextSize(R.dimen.today_size)   // dimension *resource*
yearView.setTodayTextSizePx(30)                 // raw pixels
```

> **Important**
> The plain setters (`setTodayTextColor`, `setTodayTextSize`, `setVerticalSpacing`, …) take
> **resource IDs**. The `…Int` / `…Px` variants take **already-resolved values**. Both paths
> write to the same config object.

The four configuration objects:

| Object | Covers |
|---|---|
| `MonthConfig` | Month title gravity, margin below the title, month name + today's month name text, month background, month selection highlight, name format |
| `DayConfig` | One day style — used four times: `simpleDayConfig`, `weekendDayConfig`, `todayConfig`, `selectedDayConfig` |
| `DayNameConfig` | The `M T W T F S S` header row, plus `transcendsWeekend` |
| `LegacyBackgroundStyle` | Colour, shape, margin, image, colour opacity, image opacity, merge type |

Each of `MonthConfig`, `DayConfig` and `DayNameConfig` also has a `fromResources(...)` companion
so you can build one entirely from `colors.xml` / `dimens.xml`:

```kotlin
yearView.weekendDayConfig = DayConfig.fromResources(
    context = requireContext(),
    textColorRes = R.color.weekend_text,
    textSizeRes = R.dimen.day_text_size,
    backgroundRadiusRes = R.dimen.day_background_radius,
    fontType = FontType.BOLD
)
```

### Text, colours and fonts

```xml
app:yv_month_name_text_color="@android:color/holo_blue_dark"
app:yv_month_name_text_size="14sp"
app:yv_month_name_font="@font/teddy_bears"
app:yv_month_name_font_type="bold_italic"
app:yv_month_title_gravity="center"
app:yv_margin_below_month_name="10dp"

app:yv_today_month_name_text_color="#D00606"
app:yv_today_month_name_font="@font/calling_heart"

app:yv_simple_day_text_color="@android:color/black"
app:yv_simple_day_text_size="10sp"

app:yv_weekend_text_color="@android:color/holo_green_dark"
app:yv_weekend_font="@font/pinch_my_ride"

app:yv_day_name_text_color="@android:color/holo_orange_dark"
app:yv_day_name_font_type="italic"
app:yv_name_week_transcend_weekend="false"
```

`yv_*_font_type` accepts `normal`, `bold`, `italic` or `bold_italic` and maps to
`FontType`. Typefaces can also be swapped at runtime:

```kotlin
yearView.setMonthNameFontTypeFace(ResourcesCompat.getFont(context, R.font.teddy_bears))
yearView.setMonthNameFontType(FontType.BOLD_ITALIC)
```

`yv_month_title_gravity` accepts `center`, `start`, `left`, `right`, `end`. `start` / `end`
mirror in RTL layouts; `left` / `right` do not.

By default, weekend **columns** in the header row take the weekend text style. Set
`yv_name_week_transcend_weekend="true"` to keep the day-name style everywhere instead.

### Backgrounds and shapes

Any background — month, month selection, today, selected day, range — accepts one of five shapes:

| Shape | XML value | Extra attributes |
|---|---|---|
| Circle | `circle` | — |
| Square | `square` | — |
| Rounded square | `rounded_square` | `*_rounded_radius` (dp) |
| Star | `star` | `*_star_points` (3–10), `*_star_inner_radius` (0–1) |
| Custom vector | `custom` | `*_custom_shape` (vector drawable reference) |

```xml
app:yv_month_background_shape="star"
app:yv_month_background_star_points="6"
app:yv_month_background_star_inner_radius="0.45"
app:yv_month_background_color="@color/purple_500"
app:yv_month_background_color_density="30"

app:yv_today_background_shape="circle"
app:yv_today_background_color="#D10606"
app:yv_today_background_radius="2dp"
```

In Kotlin, shapes come from `:core`:

```kotlin
yearView.setTodayBackgroundShape(BackgroundShape.Circle(radius = 1.0f))
yearView.setSelectedDayBackgroundShape(BackgroundShape.RoundedSquare(cornerRadius = 8f))

// Any vector drawable can become a shape.
yearView.setMonthBackgroundItemStyle(
    LegacyBackgroundStyle(
        color = Color.parseColor("#4CAF50"),
        shape = BackgroundShape.Custom(
            provider = ResourcePathProvider(
                drawableRes = R.drawable.heart,
                innerPadding = R.dimen.inner_radius
            )
        ),
        colorOpacity = 30
    )
)
```

> **Important**
> Transparency comes from `colorOpacity` / `imageOpacity` (`0`–`100`), **not** from the alpha
> channel of the colour. `LegacyBackgroundStyle(color = 0x4DFF0000)` renders fully opaque; use
> `LegacyBackgroundStyle(color = Color.RED, colorOpacity = 30)`.

### Background images

Colour and image opacity are independent, so you can put a fully opaque shape behind a
translucent photo (or the reverse):

```xml
app:yv_month_background_image="@drawable/shopping"
app:yv_month_background_image_opacity="70"
app:yv_month_background_color="@color/purple_500"
app:yv_month_background_color_density="30"
app:yv_month_background_merge_type="clip"
```

`merge_type` decides how the two combine:

- **`overlay`** — the colour is painted on top of the image.
- **`clip`** — the image is clipped to the shape's outline.

Images can also be supplied programmatically from a resource, a `Bitmap`, or **any `Drawable`**:

```kotlin
LegacyBackgroundStyle(
    image = ImageSource.Provided(ResourceImageProvider(R.drawable.shopping))
)
LegacyBackgroundStyle(
    image = ImageSource.Provided(BitmapImageProvider(myBitmap))
)
LegacyBackgroundStyle(
    image = ImageSource.Provided(DrawableImageProvider(myDrawable))  // legacy-only
)
```

### Weekend days

Weekend days use **ISO-8601** numbering (1 = Monday … 7 = Sunday). Declare them as an int array:

```xml
<!-- res/values/arrays.xml -->
<array name="weekendDays">
    <item>5</item> <!-- Friday   -->
    <item>6</item> <!-- Saturday -->
</array>
```

```xml
app:yv_weekend_days="@array/weekendDays"
```

or at runtime:

```kotlin
yearView.setWeekendDays(
    intArrayOf(DayOfWeekConstants.FRIDAY, DayOfWeekConstants.SATURDAY)
)
```

The library ships a default `@array/weekendDays` (Saturday + Sunday) you can reference directly.

## Selection

### Sticky single-day selection

```xml
app:yv_is_day_selection_visually_sticky="true"
app:yv_selected_day_text="2026-01-22"
app:yv_selected_day_background_shape="circle"
app:yv_selected_day_background_radius="5dp"
app:yv_selected_day_text_size="10sp"
```

With sticky selection on, a tapped day keeps its highlight until another day is tapped; tapping
the same day again clears it. With it off, a tap simply fires `onDayClick` and nothing is
highlighted.

```kotlin
yearView.isDaySelectionVisuallySticky = true
yearView.selectedDate = CalendarDate(2026, 1, 22)
val current: CalendarDate? = yearView.selectedDate
```

`yv_selected_day_text` is parsed with `yv_day_pattern` (default `yyyy-MM-dd`).

### Range (multi) selection

```xml
app:yv_enable_multi_selection="true"
app:yv_multi_selection_background_color="#4DB6AC"
app:yv_multi_selection_background_color_density="30"
app:yv_multi_selection_background_shape="square"
app:yv_multi_selection_background_radius="5dp"
```

```kotlin
yearView.isMultiSelectionEnabled = true

yearView.setMonthGestureListener(object : YearView.MonthGestureListener {
    override fun onRangeSelected(start: CalendarDate, end: CalendarDate) {
        // Endpoints are normalised: start is always <= end.
        viewModel.onRangeChosen(start, end)
    }
})

yearView.clearMultiSelection()
```

A range is built either by **tapping the two endpoints** or by **pressing and dragging** across
days. `onRangeSelected` fires once the range is complete.

> **Note**
> The active range has no public getter — capture it in `onRangeSelected` and keep it in your own
> state. (The Compose module exposes it as observable state instead; see
> [Using Jetpack Compose instead](#using-jetpack-compose-instead).)

## Localisation and date libraries

Month names and weekday abbreviations come from the device locale automatically. RTL locales
mirror the month grid, the weekday columns and `start` / `end` title gravity.

The date engine is pluggable — swap it if your app already standardises on one:

```kotlin
yearView.dateTimeProvider = JavaTimeProvider()   // java.time
yearView.dateTimeProvider = JodaTimeProvider()   // Joda-Time
yearView.dateTimeProvider = KotlinxTimeProvider() // kotlinx-datetime (default)
```

You can also implement `ICalendarDateTimeProvider` yourself for a non-Gregorian calendar.

## Accessibility

Accessibility is on by default and requires no configuration:

- Every day is exposed as an individually focusable, clickable **virtual view**, so TalkBack
  announces "12 January 2026, today" rather than a bare "12".
- `YearViewAccessibilityHelper` extends `ExploreByTouchHelper`, which also gives **D-pad and
  keyboard** users day-by-day traversal.
- Announcements are ordinary string resources (`yearview_a11y_*`), so they localise through the
  normal resource system — override them in your app to re-word or translate.

> **Note**
> At twelve months on a phone screen, day touch targets are naturally smaller than the 48 dp
> Material recommendation. Increase the day text size or use a coarser grid when the calendar is
> the primary interactive surface of a screen.

## Debug mode

```xml
app:yv_debug_mode="true"
```

```kotlin
yearView.debugMode = true
```

Paints a translucent outline around every day's touch target — invaluable when tuning text sizes
and spacing.

## State restoration

The view saves and restores the current year, the selected day, the active range, and the sticky
and multi-selection flags through `onSaveInstanceState`, so rotation just works.

Configuration objects are `@Parcelize`, so a whole style can travel through an `Intent`:

```kotlin
intent.putExtra("todayConfig", yearView.todayConfig)
```

> **Note**
> `Typeface` is not `Parcelable`, so custom typefaces are **not** restored. Re-apply them after a
> configuration change with `setMonthNameFontTypeFace(...)` and friends.

## XML attribute reference

All attributes are prefixed `yv_` to avoid collisions in your app's global resource namespace.
Lengths use `format="dimension"`, so give them in `dp` / `sp`.

### Grid

| Attribute | Format | Default |
|---|---|---|
| `yv_current_year` | integer | current year |
| `yv_rows` | integer | `6` |
| `yv_columns` | integer | `2` |
| `yv_vertical_spacing` | dimension | `5px` |
| `yv_horizontal_spacing` | dimension | `5px` |
| `yv_first_day_of_week` | `monday`…`sunday` | `monday` |
| `yv_weekend_days` | int-array reference | Sat + Sun |
| `yv_day_pattern` | string | `yyyy-MM-dd` |
| `yv_debug_mode` | boolean | `false` |

### Month title

| Attribute | Format |
|---|---|
| `yv_month_title_gravity` | `center`, `start`, `left`, `right`, `end` |
| `yv_margin_below_month_name` | dimension |
| `yv_month_name_text_color` | color |
| `yv_month_name_text_size` | dimension |
| `yv_month_name_font` | font reference |
| `yv_month_name_font_type` | `normal`, `bold`, `italic`, `bold_italic` |
| `yv_today_month_name_text_color` | color |
| `yv_today_month_name_text_size` | dimension |
| `yv_today_month_name_font` | font reference |
| `yv_today_month_name_font_type` | font type |

### Month background & selection highlight

Both groups share the same shape vocabulary; replace `<prefix>` with
`yv_month_background` or `yv_month_selection`.

| Attribute | Format |
|---|---|
| `<prefix>_color` | color |
| `<prefix>_color_density` | integer `0`–`100` |
| `<prefix>_image` | drawable reference |
| `<prefix>_image_opacity` | integer `0`–`100` |
| `<prefix>_shape` | `circle`, `square`, `rounded_square`, `star`, `custom` |
| `<prefix>_rounded_radius` | float (dp) |
| `<prefix>_star_points` | integer `3`–`10` |
| `<prefix>_star_inner_radius` | float `0`–`1` |
| `<prefix>_custom_shape` | vector drawable reference |
| `<prefix>_merge_type` | `overlay`, `clip` |
| `yv_month_selection_margin` | dimension *(selection only)* |

### Today

| Attribute | Format |
|---|---|
| `yv_today_text_color` | color |
| `yv_today_text_size` | dimension |
| `yv_today_font` | font reference |
| `yv_today_font_type` | font type |
| `yv_today_background_color` | color |
| `yv_today_background_color_density` | integer `0`–`100` |
| `yv_today_background_image` | drawable reference |
| `yv_today_background_image_opacity` | integer `0`–`100` |
| `yv_today_background_radius` | dimension |
| `yv_today_background_shape` | shape |
| `yv_today_rounded_radius` / `yv_today_star_points` / `yv_today_star_inner_radius` / `yv_today_custom_shape` | shape parameters |

### Selected day

| Attribute | Format |
|---|---|
| `yv_is_day_selection_visually_sticky` | boolean |
| `yv_selected_day_text` | string (parsed with `yv_day_pattern`) |
| `yv_selected_day_text_color` | color |
| `yv_selected_day_text_size` | dimension |
| `yv_selected_day_font` | font reference |
| `yv_selected_day_font_type` | font type |
| `yv_selected_day_background_color` | color |
| `yv_selected_day_background_color_density` | integer `0`–`100` |
| `yv_selected_day_background_image` | drawable reference |
| `yv_selected_day_background_image_opacity` | integer `0`–`100` |
| `yv_selected_day_background_radius` | dimension |
| `yv_selected_day_background_shape` | shape |
| `yv_selected_day_rounded_radius` / `yv_selected_day_star_points` / `yv_selected_day_star_inner_radius` / `yv_selected_day_custom_shape` | shape parameters |

### Range selection

| Attribute | Format |
|---|---|
| `yv_enable_multi_selection` | boolean |
| `yv_multi_selection_background_color` | color |
| `yv_multi_selection_background_color_density` | integer `0`–`100` |
| `yv_multi_selection_background_radius` | dimension |
| `yv_multi_selection_background_shape` | shape |
| `yv_multi_selection_rounded_radius` / `yv_multi_selection_star_points` / `yv_multi_selection_star_inner_radius` / `yv_multi_selection_custom_shape` | shape parameters |

### Ordinary days, weekends and the header row

| Attribute | Format |
|---|---|
| `yv_simple_day_text_color` / `yv_weekend_text_color` / `yv_day_name_text_color` | color |
| `yv_simple_day_text_size` / `yv_weekend_text_size` / `yv_day_name_text_size` | dimension |
| `yv_simple_day_font` / `yv_weekend_font` / `yv_day_name_font` | font reference |
| `yv_simple_day_font_type` / `yv_weekend_font_type` / `yv_day_name_font_type` | font type |
| `yv_name_week_transcend_weekend` | boolean |

## Programmatic API reference

### Properties

| Property | Type | Notes |
|---|---|---|
| `year` | `Int` | The displayed year |
| `rows` / `columns` | `Int` | Grid shape; `min(rows × columns, 12)` months are drawn |
| `firstDayOfWeek` | `Int` | ISO 1–7, coerced into range |
| `dayPattern` | `String` | Pattern used to parse / format day strings |
| `dateTimeProvider` | `ICalendarDateTimeProvider` | Pluggable date engine |
| `selectedDate` | `CalendarDate?` | Read *and* write the sticky selection |
| `isDaySelectionVisuallySticky` | `Boolean` | |
| `isMultiSelectionEnabled` | `Boolean` | |
| `debugMode` | `Boolean` | Draws touch targets |
| `monthConfig` | `MonthConfig` | |
| `simpleDayConfig` / `weekendDayConfig` / `todayConfig` / `selectedDayConfig` | `DayConfig` | |
| `dayNameConfig` | `DayNameConfig` | |
| `multiSelectionBackgroundItemStyle` | `LegacyBackgroundStyle` | |

### Methods

| Method | Notes |
|---|---|
| `setMonthGestureListener(listener?)` | Pass `null` in `onDestroyView` |
| `setWeekendDays(IntArray?)` | ISO 1–7 |
| `clearMultiSelection()` | Drops the active range |
| `setVerticalSpacing(@DimenRes)` / `setVerticalSpacingPx(@Px)` | Same pattern for horizontal spacing |
| `set…TextColor(@ColorRes)` / `set…TextColorInt(@ColorInt)` | Resource vs resolved |
| `set…TextSize(@DimenRes)` / `set…TextSizePx(@Px)` | Resource vs resolved |
| `set…FontType(FontType)` / `set…FontTypeFace(Typeface?)` | Runtime font swapping |
| `set…BackgroundShape(BackgroundShape)` | Today / selected day |
| `setMonthBackgroundItemStyle(...)` / `setSelectionBackgroundItemStyle(...)` / `setMultiSelectionBackgroundItemStyle(...)` | Whole-style assignment |

## ProGuard / R8

Nothing to do. The library ships its own `consumer-rules.pro`, and the published AAR is
deliberately **not** minified so your stack traces stay readable.

## Using Jetpack Compose instead

If your screen is already Compose, use the **`:compose`** module instead — it renders the same
calendar with a declarative, `Dp`/`Color`/`TextStyle`-based API and exposes the selection as
observable state.

See [`compose/README.md`](../compose/README.md), and
[`FEATURE_COMPARISON.md`](../FEATURE_COMPARISON.md) for a side-by-side of the two.

## License

MIT — see [LICENSE](../LICENSE).
