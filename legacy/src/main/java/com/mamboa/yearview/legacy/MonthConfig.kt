package com.mamboa.yearview.legacy

import android.content.Context
import android.graphics.Typeface
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.FontType
import com.mamboa.yearview.core.TitleGravity
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Configuration class for month-related styling and formatting in Legacy YearView.
 * 
 * This data class stores resolved values (@ColorInt, @Px) for programmatic usage.
 * Use the companion object factory methods to create instances from resource IDs.
 */
@Parcelize
data class MonthConfig(
    /**
     * Gravity/alignment of the month title within its container.
     */
    val titleGravity: TitleGravity = TitleGravity.CENTER,

    /**
     * Margin below the month title in pixels (resolved size).
     */
    @Px
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
     * Text color for a month name (resolved color value).
     */
    @ColorInt
    val nameTextColor: Int = android.graphics.Color.BLACK,

    /**
     * Text size for a month name in pixels (resolved size).
     */
    @Px
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
     * Text color for the current month name (resolved color value).
     */
    @ColorInt
    val todayNameTextColor: Int = android.graphics.Color.BLACK,

    /**
     * Text size for the current month name in pixels (resolved size).
     */
    @Px
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
) : Parcelable {
    
    companion object {
        /**
         * Creates a MonthConfig from resource IDs.
         * 
         * This is the recommended way to create MonthConfig instances to ensure
         * values come from resource files (colors.xml, dimens.xml).
         *
         * @param context Android context for resource resolution
         * @param titleGravity Gravity/alignment of the month title
         * @param marginBelowMonthNameRes Dimension resource ID for margin below month name
         * @param selectionBackgroundItemStyle Style for selected month background
         * @param backgroundItemStyle Style for month background
         * @param nameTextColorRes Color resource ID for month name (e.g., R.color.yearview_month_name_text)
         * @param nameTextSizeRes Dimension resource ID for month name size
         * @param nameFontType Font type for month name
         * @param nameFontTypeFace Custom typeface for month name
         * @param todayNameTextColorRes Color resource ID for today's month name
         * @param todayNameTextSizeRes Dimension resource ID for today's month name size
         * @param todayNameFontType Font type for today's month name
         * @param todayNameFontTypeFace Custom typeface for today's month name
         * @param nameFormat Format pattern for month name display
         * @return MonthConfig with resolved resource values
         */
        fun fromResources(
            context: Context,
            titleGravity: TitleGravity = TitleGravity.CENTER,
            @DimenRes marginBelowMonthNameRes: Int,
            selectionBackgroundItemStyle: BackgroundItemStyle.AndroidXMLStyle = BackgroundItemStyle.AndroidXMLStyle(
                color = android.graphics.Color.BLUE,
                shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
                selectionMargin = 5.0f
            ),
            backgroundItemStyle: BackgroundItemStyle.AndroidXMLStyle = BackgroundItemStyle.AndroidXMLStyle(
                color = android.graphics.Color.TRANSPARENT,
                shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
                selectionMargin = 2.0f
            ),
            @ColorRes nameTextColorRes: Int,
            @DimenRes nameTextSizeRes: Int,
            nameFontType: FontType = FontType.NORMAL,
            nameFontTypeFace: Typeface? = null,
            @ColorRes todayNameTextColorRes: Int,
            @DimenRes todayNameTextSizeRes: Int,
            todayNameFontType: FontType = FontType.NORMAL,
            todayNameFontTypeFace: Typeface? = null,
            nameFormat: String = "MMMM"
        ): MonthConfig {
            return MonthConfig(
                titleGravity = titleGravity,
                marginBelowMonthName = context.resources.getDimensionPixelSize(marginBelowMonthNameRes),
                selectionBackgroundItemStyle = selectionBackgroundItemStyle,
                backgroundItemStyle = backgroundItemStyle,
                nameTextColor = ContextCompat.getColor(context, nameTextColorRes),
                nameTextSize = context.resources.getDimensionPixelSize(nameTextSizeRes),
                nameFontType = nameFontType,
                nameFontTypeFace = nameFontTypeFace,
                todayNameTextColor = ContextCompat.getColor(context, todayNameTextColorRes),
                todayNameTextSize = context.resources.getDimensionPixelSize(todayNameTextSizeRes),
                todayNameFontType = todayNameFontType,
                todayNameFontTypeFace = todayNameFontTypeFace,
                nameFormat = nameFormat
            )
        }
    }
}
