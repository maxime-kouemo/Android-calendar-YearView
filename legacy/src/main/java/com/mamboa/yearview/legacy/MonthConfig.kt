package com.mamboa.yearview.legacy

import android.graphics.Typeface
import android.os.Parcelable
import androidx.annotation.ColorInt
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.FontType
import com.mamboa.yearview.core.TitleGravity
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Configuration class for month-related styling and formatting in Legacy YearView.
 * Uses Android View types (Int, @ColorInt, Typeface) for XML attribute compatibility.
 */
@Parcelize
data class MonthConfig(
    /**
     * Gravity/alignment of the month title within its container.
     */
    val titleGravity: TitleGravity = TitleGravity.CENTER,

    /**
     * Margin below the month title in pixels.
     */
    val marginBelowMonthName: Int = 8,

    /**
     * Style for the background of a selected month.
     */
    val selectionBackgroundItemStyle: BackgroundItemStyle.AndroidXMLStyle = BackgroundItemStyle.AndroidXMLStyle(
        color = android.graphics.Color.BLUE,
        shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
        selectionMargin = 5.0f
    ),

    /**
     * Style for the background of a month.
     */
    val backgroundItemStyle: BackgroundItemStyle.AndroidXMLStyle = BackgroundItemStyle.AndroidXMLStyle(
        color = android.graphics.Color.TRANSPARENT,
        shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
        selectionMargin = 2.0f
    ),

    /**
     * Text color for a month name (Android ColorInt).
     */
    @ColorInt
    val nameTextColor: Int = android.graphics.Color.BLACK,

    /**
     * Text size for a month name in pixels.
     */
    val nameTextSize: Int = 12,

    /**
     * Font type for a month name.
     */
    val nameFontType: FontType = FontType.NORMAL,

    /**
     * Custom typeface for month name.
     */
    val nameFontTypeFace: @RawValue Typeface? = null,

    /**
     * Text color for the current month name (Android ColorInt).
     */
    @ColorInt
    val todayNameTextColor: Int = android.graphics.Color.BLACK,

    /**
     * Text size for the current month name in pixels.
     */
    val todayNameTextSize: Int = 12,

    /**
     * Font type for the current month name.
     */
    val todayNameFontType: FontType = FontType.NORMAL,

    /**
     * Custom typeface for today's month name.
     */
    val todayNameFontTypeFace: @RawValue Typeface? = null,

    /**
     * Format for displaying the month name (e.g., "MMMM" for full name, "MMM" for abbreviated).
     */
    val nameFormat: String = "MMMM"
) : Parcelable
