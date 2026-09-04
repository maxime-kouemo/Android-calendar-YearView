package com.mamboa.yearview.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.mamboa.yearview.core.BackgroundShape

/**
 * Configuration for day cell styling in the YearView.
 *
 * ## Custom Fonts
 *
 * To apply a custom font family, set it directly on [textStyle]:
 *
 * ```
 * DayConfig(
 *     textStyle = TextStyle(
 *         fontFamily = FontFamily(Font(R.font.my_custom_font)),
 *         fontSize = 10.sp,
 *         fontWeight = FontWeight.Bold,
 *         textAlign = TextAlign.Center,
 *         color = Color.White
 *     )
 * )
 * ```
 *
 * [TextStyle] already supports [FontFamily], so there is no need for a separate
 * `fontFamily` parameter — all font customisation is handled through [textStyle].
 */
@Immutable
data class DayConfig(
    val backgroundItemStyle: ComposeBackgroundStyle = ComposeBackgroundStyle(
        color = Color.Transparent,
        shape = BackgroundShape.Circle(radius = 1.0f)
    ),
    val textStyle: TextStyle = TextStyle(
        color = Color.Black,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center
    ),
    /**
     * Breathing room around the day text, used as a multiplier for the background size.
     * A value of 1f means the background will fit snugly around the text.
     */
    val backgroundRadius: Float = 1f
)
