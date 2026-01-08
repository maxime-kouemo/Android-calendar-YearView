package com.mamboa.yearview.legacy

import android.graphics.Typeface
import android.os.Parcelable
import androidx.annotation.ColorInt
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.FontType
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Configuration class for day-related styling in Legacy YearView.
 * Uses Android View types (Int, @ColorInt, Typeface) for XML attribute compatibility.
 */
@Parcelize
data class DayConfig(
    /**
     * Style for the background of the day.
     */
    val backgroundItemStyle: BackgroundItemStyle.AndroidXMLStyle = BackgroundItemStyle.AndroidXMLStyle(
        color = android.graphics.Color.RED,
        shape = BackgroundShape.Circle(radius = 5.0f)
    ),

    /**
     * Text color for the day (Android ColorInt).
     */
    @ColorInt
    val textColor: Int = android.graphics.Color.WHITE,

    /**
     * Text size for the day in pixels.
     */
    val textSize: Int = 10,

    /**
     * Font type for the day text.
     */
    val fontType: FontType = FontType.NORMAL,

    /**
     * Custom typeface for day text.
     */
    val fontTypeFace: @RawValue Typeface? = null,

    /**
     * Background radius for the day (used with certain shapes).
     */
    val backgroundRadius: Int = 5
) : Parcelable
