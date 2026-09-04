package com.mamboa.yearview.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.TitleGravity

/**
 * Configuration for month-related styling and formatting in the YearView.
 *
 * ## Custom Fonts
 *
 * To apply a custom font family, set it directly on [nameStyle] and/or [todayNameStyle]:
 *
 * ```
 * MonthConfig(
 *     nameStyle = TextStyle(
 *         fontFamily = FontFamily(Font(R.font.my_custom_font)),
 *         fontSize = 12.sp,
 *         fontWeight = FontWeight.Bold,
 *         color = Color.Black
 *     )
 * )
 * ```
 *
 * [TextStyle] already supports [FontFamily], so there is no need for a separate
 * `fontFamily` parameter — all font customisation is handled through the text style fields.
 */
@Immutable
data class MonthConfig(
    /**
     * Gravity of the month title within its container.
     */
    val titleGravity: TitleGravity = TitleGravity.CENTER,

    /**
     * Margin below the month title.
     */
    val marginBelowMonthName: Dp = 8.dp,

    /**
     * Style for the background of a selected month.
     *
     * The highlight is painted *over* the month (matching the Legacy view), so the
     * default is deliberately translucent — at full opacity, tapping a month would
     * blank it out for the duration of the flash. Transparency comes from
     * [ComposeBackgroundStyle.opacity], not from the alpha channel of the colour.
     */
    val selectionBackgroundItemStyle: ComposeBackgroundStyle = ComposeBackgroundStyle(
        color = Color.Blue,
        shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
        selectionMargin = 5.dp,
        opacity = 30
    ),

    /**
     * Style for the background of a month.
     */
    val backgroundItemStyle: ComposeBackgroundStyle = ComposeBackgroundStyle(
        color = Color.Transparent,
        shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
        selectionMargin = 2.dp
    ),

    /**
     * Text style for a month name.
     */
    val nameStyle: TextStyle = TextStyle(
        color = Color.Black,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    ),

    /**
     * Text style for the name of the current month.
     */
    val todayNameStyle: TextStyle = TextStyle(
        color = Color.Black,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    ),

    /**
     * Format for displaying the month name.
     */
    val nameFormat: String = "MMMM",

    /**
     * Configuration for regular (non-weekend, non-today, non-selected) day cells
     * within this month.
     */
    val simpleDayConfig: DayConfig = DayConfig(
        backgroundItemStyle = ComposeBackgroundStyle(
            color = Color.Transparent,
            shape = BackgroundShape.Circle(radius = 1.0f)
        ),
        textStyle = TextStyle(
            color = Color.Black,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        ),
        backgroundRadius = 1f
    ),

    /**
     * Configuration for weekend day cells within this month.
     */
    val weekendDayConfig: DayConfig = DayConfig(
        backgroundItemStyle = ComposeBackgroundStyle(
            color = Color.Transparent,
            shape = BackgroundShape.Circle(radius = 1.0f)
        ),
        textStyle = TextStyle(
            color = Color.Gray,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        ),
        backgroundRadius = 1f
    )
)
