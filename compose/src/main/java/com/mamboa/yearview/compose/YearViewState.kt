package com.mamboa.yearview.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.datetime.DayOfWeekConstants
import com.mamboa.yearview.core.datetime.timeproviders.KotlinxTimeProvider

/**
 * Immutable state holder for the [YearView] composable.
 *
 * Wraps every configurable attribute (layout, styling, selection behaviour, …)
 * so the entire state can be:
 * - Passed to / from a ViewModel,
 * - Preserved across configuration changes with `rememberSaveable` (use [Saver]),
 * - Diffed efficiently by Compose (data-class equality).
 *
 * Callbacks are intentionally **not** part of this class – they stay as
 * separate lambda parameters on [YearView] because lambdas are not serializable
 * and do not represent "state".
 *
 * Neither is the *selection*: which day is highlighted and which range is active
 * changes in response to user input, so it lives in the mutable, observable
 * [YearViewSelectionState] (see [rememberYearViewSelectionState]). Keeping
 * configuration immutable and selection observable means each has exactly one owner.
 *
 * Example usage:
 * ```
 * val state = rememberSaveable(saver = YearViewState.Saver) {
 *     YearViewState(year = 2025)
 * }
 * YearView(
 *     modifier = Modifier.fillMaxSize(),
 *     state = state,
 *     onDayClick = { millis -> … }
 * )
 * ```
 *
 * **Note:** [TextStyle.fontFamily] is not preserved across save / restore because
 * arbitrary [FontFamily] instances (e.g. resource-based fonts) cannot be
 * serialised. Re-supply custom font families after restoration if needed.
 */
@Immutable
data class YearViewState(
    /**
     * The year to display in the YearView.
     * Defaults to the current year (evaluated once at class-load time).
     */
    val year: Int = DEFAULT_YEAR,

    /**
     * The number of rows used to lay out the 12 month blocks.
     */
    val rows: Int = 4,

    /**
     * The number of columns used to lay out the 12 month blocks.
     */
    val columns: Int = 3,

    /**
     * Vertical spacing between month blocks.
     */
    val verticalSpacing: Dp = 8.dp,

    /**
     * Horizontal spacing between month blocks.
     */
    val horizontalSpacing: Dp = 8.dp,

    /**
     * Configuration for month name display, backgrounds, and selection styling.
     */
    val monthConfig: MonthConfig = MonthConfig(),

    /**
     * The first day of the week, where 1 = Monday, 2 = Tuesday, … 7 = Sunday.
     */
    val firstDayOfWeek: Int = DayOfWeekConstants.MONDAY,

    /**
     * Configuration for today's date display.
     */
    val todayConfig: DayConfig = DayConfig(),

    /**
     * Configuration for the visually-selected day display.
     */
    val selectedDayConfig: DayConfig = DayConfig(
        backgroundItemStyle = ComposeBackgroundStyle(
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
     * When `true`, day-name headers are drawn for weekend columns as well,
     * using [dayNameStyle] instead of [MonthConfig.weekendDayConfig].
     */
    val dayNameTranscendsWeekend: Boolean = false,

    /**
     * When `true`, tapping a day toggles its selection state visually
     * (the highlight "sticks" until the user taps again or selects another day).
     */
    val isDaySelectionVisuallySticky: Boolean = false,

    /**
     * Text style applied to the day-of-week header row.
     */
    val dayNameStyle: TextStyle = TextStyle(
        color = Color.Blue,
        fontSize = 10.sp,
        textAlign = TextAlign.Center
    ),

    /**
     * Number of leading characters to keep from each localised day-of-week abbreviation
     * in the header row.
     *
     * The default of `1` gives the compact "M T W T F S S" look that suits twelve months
     * on one screen, but a single letter is only meaningful in a handful of languages —
     * it is ambiguous in English (T/T, S/S) and can split a grapheme or produce nonsense
     * in scripts whose abbreviations are inherently multi-character.
     *
     * Set this to `2` or `3` for such locales, or to **`0` (or any negative value) to use
     * the locale's full short name** unmodified.
     *
     * Values longer than the abbreviation itself are harmless — the name is simply used
     * in full — and an empty abbreviation yields an empty header rather than an error.
     */
    val dayNameLength: Int = 1,

    /**
     * Date pattern used to format / parse day strings.
     */
    val dayFormat: String = "yyyy-MM-dd",

    /**
     * Padding added around each day number to form its touch target (and the
     * TalkBack focus rectangle).
     *
     * A day cell is only as large as its text plus twice this padding, so the
     * default keeps twelve months legible on a phone screen at the cost of a
     * target smaller than the 48dp recommended by the Material accessibility
     * guidelines. Increase it (together with [rows] / [columns] or the day text
     * size) when the calendar is the primary interactive surface of a screen.
     */
    val dayTouchPadding: Dp = 2.dp,

    /**
     * Set of days considered "weekend", using ISO-8601 day-of-week encoding
     * from [DayOfWeekConstants] (1 = Monday … 7 = Sunday).
     *
     * Use [DayOfWeekConstants] constants (e.g. [DayOfWeekConstants.FRIDAY])
     * rather than raw integers to keep call-sites self-documenting.
     *
     * Example – Middle-Eastern weekend (Friday + Saturday):
     * ```
     * weekendDays = setOf(DayOfWeekConstants.FRIDAY, DayOfWeekConstants.SATURDAY)
     * ```
     */
    val weekendDays: Set<Int> = setOf(DayOfWeekConstants.SATURDAY, DayOfWeekConstants.SUNDAY),

    /**
     * Enable range / multi-selection of days.
     *
     * When `true`, the first tap starts the range and the second tap ends it,
     * triggering [YearView]'s `onRangeSelected` callback.
     */
    val enableMultiSelection: Boolean = false,

    /**
     * Background style applied to every day inside the selected range
     * when [enableMultiSelection] is `true`.
     *
     * Note that transparency comes from [ComposeBackgroundStyle.opacity], not from the
     * alpha channel of [ComposeBackgroundStyle.color].
     */
    val multiSelectionBackgroundItemStyle: ComposeBackgroundStyle = ComposeBackgroundStyle(
        color = Color.Cyan,
        shape = BackgroundShape.Square,
        selectionMargin = 5.dp,
        opacity = 30
    ),

    /**
     * Accessibility label prefix for today's date.
     *
     * When a day is today, the TalkBack announcement is prefixed with this string.
     * Defaults to `"today"`. Set to a localised string for non-English locales.
     */
    val todayLabel: String = "today",

    /**
     * Accessibility label suffix for the selected day.
     *
     * When a day is selected (and [isDaySelectionVisuallySticky] is `true`),
     * the TalkBack announcement is suffixed with this string.
     * Defaults to `"selected"`. Set to a localised string for non-English locales.
     */
    val selectedLabel: String = "selected",

    /**
     * Accessibility label for the custom action that exits day navigation
     * and returns to month-level navigation.
     *
     * Defaults to `"Exit month"`. Set to a localised string for non-English locales.
     */
    val exitMonthActionLabel: String = "Exit month"
) {
    init {
        require(rows * columns == MONTHS_PER_YEAR) {
            "rows ($rows) * columns ($columns) = ${rows * columns} must be " +
                    "== $MONTHS_PER_YEAR to fit all months."
        }
    }

    companion object {
        /**
         * Default year, resolved lazily on first access so that:
         * 1. No [KotlinxTimeProvider] is allocated at class-load time.
         * 2. If the app process survives across a year boundary,
         *    the first access after the new year returns the correct value
         *    (though subsequent accesses keep returning the cached result).
         */
        private val DEFAULT_YEAR: Int by lazy { KotlinxTimeProvider().currentYear() }

        /**
         * A [Saver] that can be used with `rememberSaveable` to preserve a
         * [YearViewState] across configuration changes and process death.
         *
         * Every field is decomposed into Bundle-safe primitives (`Int`, `Long`,
         * `Float`, `Boolean`, `String`).
         *
         * **Resilience:** restoration never throws. A saved bundle can outlive the
         * code that wrote it — a user backgrounds the app, the app is updated, and the
         * process is then recreated from the old bundle — so every field is read with a
         * checked cast and falls back to its default when missing or of an unexpected
         * type. Enum names are matched leniently for the same reason. The alternative
         * (unchecked `as` casts) turns any renamed or retyped field into a crash on
         * resume, which is both hard to reproduce and impossible for the user to escape.
         *
         * **Limitation:** [TextStyle.fontFamily] is **not** preserved because
         * arbitrary [FontFamily] instances cannot be serialised. Re-supply custom
         * font families after restoration if needed.
         */
        val Saver: Saver<YearViewState, Any> = mapSaver(
            save = { state ->
                buildMap {
                    put("year", state.year)
                    put("rows", state.rows)
                    put("columns", state.columns)
                    put("verticalSpacing", state.verticalSpacing.value)
                    put("horizontalSpacing", state.horizontalSpacing.value)
                    putMonthConfig("monthConfig", state.monthConfig)
                    put("firstDayOfWeek", state.firstDayOfWeek)
                    putDayConfig("todayConfig", state.todayConfig)
                    putDayConfig("selectedDayConfig", state.selectedDayConfig)
                    put("dayNameTranscendsWeekend", state.dayNameTranscendsWeekend)
                    put("isDaySelectionVisuallySticky", state.isDaySelectionVisuallySticky)
                    putTextStyle("dayNameStyle", state.dayNameStyle)
                    put("dayNameLength", state.dayNameLength)
                    put("dayFormat", state.dayFormat)
                    put("dayTouchPadding", state.dayTouchPadding.value)
                    put("weekendDays", ArrayList(state.weekendDays.toList()))
                    put("enableMultiSelection", state.enableMultiSelection)
                    putComposeStyle("multiSelBg", state.multiSelectionBackgroundItemStyle)
                    put("todayLabel", state.todayLabel)
                    put("selectedLabel", state.selectedLabel)
                    put("exitMonthActionLabel", state.exitMonthActionLabel)
                }
            },
            restore = { map ->
                val defaults = YearViewState()
                // rows and columns are validated against each other by `init`, so they
                // must be restored as a pair: accepting one and defaulting the other
                // could produce a product other than 12 and throw during restore.
                val savedRows = map["rows"] as? Int
                val savedColumns = map["columns"] as? Int
                val validGrid = savedRows != null && savedColumns != null &&
                        savedRows * savedColumns == MONTHS_PER_YEAR
                YearViewState(
                    year = map["year"] as? Int ?: defaults.year,
                    rows = if (validGrid) savedRows!! else defaults.rows,
                    columns = if (validGrid) savedColumns!! else defaults.columns,
                    verticalSpacing = map.restoreDp("verticalSpacing", defaults.verticalSpacing),
                    horizontalSpacing = map.restoreDp("horizontalSpacing", defaults.horizontalSpacing),
                    monthConfig = map.restoreMonthConfig("monthConfig"),
                    firstDayOfWeek = map["firstDayOfWeek"] as? Int ?: defaults.firstDayOfWeek,
                    todayConfig = map.restoreDayConfig("todayConfig"),
                    selectedDayConfig = map.restoreDayConfig(
                        "selectedDayConfig",
                        fallback = defaults.selectedDayConfig,
                    ),
                    dayNameTranscendsWeekend = map["dayNameTranscendsWeekend"] as? Boolean
                        ?: defaults.dayNameTranscendsWeekend,
                    isDaySelectionVisuallySticky = map["isDaySelectionVisuallySticky"] as? Boolean
                        ?: defaults.isDaySelectionVisuallySticky,
                    dayNameStyle = map.restoreTextStyle("dayNameStyle", defaults.dayNameStyle),
                    dayNameLength = map["dayNameLength"] as? Int ?: defaults.dayNameLength,
                    dayFormat = map["dayFormat"] as? String ?: defaults.dayFormat,
                    dayTouchPadding = map.restoreDp("dayTouchPadding", defaults.dayTouchPadding),
                    weekendDays = (map["weekendDays"] as? List<*>)
                        ?.filterIsInstance<Int>()
                        ?.toSet()
                        ?: defaults.weekendDays,
                    enableMultiSelection = map["enableMultiSelection"] as? Boolean
                        ?: defaults.enableMultiSelection,
                    multiSelectionBackgroundItemStyle = map.restoreComposeStyle(
                        "multiSelBg",
                        fallback = defaults.multiSelectionBackgroundItemStyle,
                    ),
                    todayLabel = map["todayLabel"] as? String ?: defaults.todayLabel,
                    selectedLabel = map["selectedLabel"] as? String ?: defaults.selectedLabel,
                    exitMonthActionLabel = map["exitMonthActionLabel"] as? String
                        ?: defaults.exitMonthActionLabel,
                )
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Saver helpers – decompose Compose types into Bundle-safe primitives
//
// Every `restore*` helper below takes the value it should fall back to and reads with
// checked casts. A bundle written by an older build of the app can be handed back to a
// newer one after a process restart, so a missing or retyped entry must degrade to a
// default rather than throw: an exception here surfaces as a crash on resume that the
// user cannot clear without wiping the app's data.
// ---------------------------------------------------------------------------

/** Reads a `Float` written from a [Dp] and rebuilds it, or returns [default]. */
private fun Map<String, Any?>.restoreDp(key: String, default: Dp): Dp =
    (this[key] as? Float)?.dp ?: default

/** Resolves an enum constant by name, falling back to [default] for unknown names. */
private inline fun <reified E : Enum<E>> Map<String, Any?>.restoreEnum(
    key: String,
    default: E,
): E {
    val name = this[key] as? String ?: return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
}

// ---- TextStyle ----

private fun MutableMap<String, Any?>.putTextStyle(prefix: String, style: TextStyle) {
    put("$prefix.color", style.color.value.toLong())
    put("$prefix.fsVal", style.fontSize.value)
    put("$prefix.fsType", when {
        style.fontSize.isSp -> "sp"
        style.fontSize.isEm -> "em"
        else -> "unspecified"
    })
    put("$prefix.fw", style.fontWeight?.weight)
    put("$prefix.ta", textAlignToName(style.textAlign))
}

private fun Map<String, Any?>.restoreTextStyle(
    prefix: String,
    fallback: TextStyle,
): TextStyle {
    // The colour key is always written, so its absence means this block was never
    // saved (a partial or older bundle) — restore the caller's default wholesale
    // rather than assembling a half-empty style.
    val rawColor = this["$prefix.color"] as? Long ?: return fallback
    val fsVal = this["$prefix.fsVal"] as? Float
    val fontSize = when (this["$prefix.fsType"] as? String) {
        "sp" -> fsVal?.sp ?: TextUnit.Unspecified
        "em" -> fsVal?.em ?: TextUnit.Unspecified
        else -> TextUnit.Unspecified
    }
    // FontWeight rejects anything outside 1..1000 with an exception.
    val fontWeight = (this["$prefix.fw"] as? Int)?.let { FontWeight(it.coerceIn(1, 1000)) }
    return TextStyle(
        color = Color(rawColor.toULong()),
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = nameToTextAlign(this["$prefix.ta"] as? String) ?: TextAlign.Unspecified,
    )
}

private fun textAlignToName(align: TextAlign?): String? = when (align) {
    TextAlign.Left -> "Left"
    TextAlign.Right -> "Right"
    TextAlign.Center -> "Center"
    TextAlign.Justify -> "Justify"
    TextAlign.Start -> "Start"
    TextAlign.End -> "End"
    else -> null
}

private fun nameToTextAlign(name: String?): TextAlign? = when (name) {
    "Left" -> TextAlign.Left
    "Right" -> TextAlign.Right
    "Center" -> TextAlign.Center
    "Justify" -> TextAlign.Justify
    "Start" -> TextAlign.Start
    "End" -> TextAlign.End
    else -> null
}

// ---- BackgroundShape ----

private fun MutableMap<String, Any?>.putShape(prefix: String, shape: BackgroundShape) {
    when (shape) {
        is BackgroundShape.Circle -> {
            put("$prefix.type", "Circle")
            put("$prefix.p1", shape.radius)
        }
        is BackgroundShape.Square -> {
            put("$prefix.type", "Square")
        }
        is BackgroundShape.RoundedSquare -> {
            put("$prefix.type", "RoundedSquare")
            put("$prefix.p1", shape.cornerRadius)
        }
        is BackgroundShape.Star -> {
            put("$prefix.type", "Star")
            put("$prefix.p1", shape.numberOfLegs)
            put("$prefix.p2", shape.innerRadiusRatio)
        }
        is BackgroundShape.Custom -> {
            val provider = shape.provider
            if (provider is com.mamboa.yearview.core.pathprovider.ResourcePathProvider) {
                put("$prefix.type", "ResourceShape")
                put("$prefix.p1", provider.drawableRes)
                put("$prefix.p2", provider.innerPadding)
            } else {
                // Non-serialisable provider; fall back to Square.
                put("$prefix.type", "Square")
            }
        }
    }
}

private fun Map<String, Any?>.restoreShape(
    prefix: String,
    fallback: BackgroundShape,
): BackgroundShape =
    when (this["$prefix.type"] as? String) {
        "Circle" -> BackgroundShape.Circle(radius = this["$prefix.p1"] as? Float ?: 1f)
        "Square" -> BackgroundShape.Square
        "RoundedSquare" ->
            BackgroundShape.RoundedSquare(cornerRadius = this["$prefix.p1"] as? Float ?: 0f)
        // Star's constructor rejects leg counts outside 3..10.
        "Star" -> BackgroundShape.Star(
            numberOfLegs = (this["$prefix.p1"] as? Int ?: 5).coerceIn(3, 10),
            innerRadiusRatio = this["$prefix.p2"] as? Float ?: 0.5f,
        )
        "ResourceShape" -> BackgroundShape.Custom(
            provider = com.mamboa.yearview.core.pathprovider.ResourcePathProvider(
                drawableRes = this["$prefix.p1"] as? Int ?: 0,
                innerPadding = this["$prefix.p2"] as? Int ?: 0,
            )
        )
        else -> fallback
    }

// ---- ImageSource ----

private fun MutableMap<String, Any?>.putImageSource(prefix: String, src: ImageSource) {
    when (src) {
        is ImageSource.Provided -> {
            val provider = src.provider
            if (provider is com.mamboa.yearview.core.imageprovider.ResourceImageProvider) {
                put("$prefix.type", "Resource")
                put("$prefix.resId", provider.resId)
            } else {
                put("$prefix.type", "None") // non-serialisable provider
            }
        }
        is ImageSource.None -> put("$prefix.type", "None")
    }
}

private fun Map<String, Any?>.restoreImageSource(prefix: String): ImageSource {
    val resId = this["$prefix.resId"] as? Int
    return if (this["$prefix.type"] as? String == "Resource" && resId != null) {
        ImageSource.Provided(
            provider = com.mamboa.yearview.core.imageprovider.ResourceImageProvider(resId = resId)
        )
    } else {
        ImageSource.None
    }
}

// ---- ComposeBackgroundStyle ----

private fun MutableMap<String, Any?>.putComposeStyle(
    prefix: String,
    style: ComposeBackgroundStyle,
) {
    put("$prefix.color", style.color.value.toLong())
    putShape("$prefix.shape", style.shape)
    put("$prefix.selMargin", style.selectionMargin.value)
    putImageSource("$prefix.img", style.image)
    put("$prefix.opacity", style.opacity)
    put("$prefix.merge", style.mergeType.name)
}

private fun Map<String, Any?>.restoreComposeStyle(
    prefix: String,
    fallback: ComposeBackgroundStyle,
): ComposeBackgroundStyle {
    val rawColor = this["$prefix.color"] as? Long ?: return fallback
    return ComposeBackgroundStyle(
        color = Color(rawColor.toULong()),
        shape = restoreShape("$prefix.shape", fallback.shape),
        selectionMargin = restoreDp("$prefix.selMargin", fallback.selectionMargin),
        image = restoreImageSource("$prefix.img"),
        opacity = (this["$prefix.opacity"] as? Int ?: fallback.opacity).coerceIn(0, 100),
        mergeType = restoreEnum("$prefix.merge", fallback.mergeType),
    )
}

// ---- DayConfig ----

private fun MutableMap<String, Any?>.putDayConfig(prefix: String, cfg: DayConfig) {
    putComposeStyle("$prefix.bg", cfg.backgroundItemStyle)
    putTextStyle("$prefix.ts", cfg.textStyle)
    put("$prefix.bgR", cfg.backgroundRadius)
}

private fun Map<String, Any?>.restoreDayConfig(
    prefix: String,
    fallback: DayConfig = DayConfig(),
): DayConfig =
    DayConfig(
        backgroundItemStyle = restoreComposeStyle("$prefix.bg", fallback.backgroundItemStyle),
        textStyle = restoreTextStyle("$prefix.ts", fallback.textStyle),
        backgroundRadius = this["$prefix.bgR"] as? Float ?: fallback.backgroundRadius,
    )

// ---- MonthConfig ----

private fun MutableMap<String, Any?>.putMonthConfig(prefix: String, cfg: MonthConfig) {
    put("$prefix.gravity", cfg.titleGravity.name)
    put("$prefix.marginBelow", cfg.marginBelowMonthName.value)
    putComposeStyle("$prefix.selBg", cfg.selectionBackgroundItemStyle)
    putComposeStyle("$prefix.bg", cfg.backgroundItemStyle)
    putTextStyle("$prefix.nameStyle", cfg.nameStyle)
    putTextStyle("$prefix.todayNameStyle", cfg.todayNameStyle)
    put("$prefix.nameFormat", cfg.nameFormat)
    putDayConfig("$prefix.simpleDay", cfg.simpleDayConfig)
    putDayConfig("$prefix.weekendDay", cfg.weekendDayConfig)
}

private fun Map<String, Any?>.restoreMonthConfig(
    prefix: String,
    fallback: MonthConfig = MonthConfig(),
): MonthConfig =
    MonthConfig(
        titleGravity = restoreEnum("$prefix.gravity", fallback.titleGravity),
        marginBelowMonthName = restoreDp("$prefix.marginBelow", fallback.marginBelowMonthName),
        selectionBackgroundItemStyle = restoreComposeStyle(
            "$prefix.selBg",
            fallback.selectionBackgroundItemStyle,
        ),
        backgroundItemStyle = restoreComposeStyle("$prefix.bg", fallback.backgroundItemStyle),
        nameStyle = restoreTextStyle("$prefix.nameStyle", fallback.nameStyle),
        todayNameStyle = restoreTextStyle("$prefix.todayNameStyle", fallback.todayNameStyle),
        nameFormat = this["$prefix.nameFormat"] as? String ?: fallback.nameFormat,
        simpleDayConfig = restoreDayConfig("$prefix.simpleDay", fallback.simpleDayConfig),
        weekendDayConfig = restoreDayConfig("$prefix.weekendDay", fallback.weekendDayConfig),
    )
