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
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Configuration class for day-related styling in Legacy YearView.
 * 
 * This data class stores resolved values (@ColorInt, @Px) for programmatic usage.
 * Use the companion object factory methods to create instances from resource IDs.
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
     * Text color for the day (resolved color value).
     */
    @ColorInt
    val textColor: Int = android.graphics.Color.WHITE,

    /**
     * Text size for the day in pixels (resolved size).
     */
    @Px
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
     * Background radius for the day in pixels (used with certain shapes).
     */
    @Px
    val backgroundRadius: Int = 5
) : Parcelable {
    
    companion object {
        /**
         * Creates a DayConfig from resource IDs.
         * 
         * This is the recommended way to create DayConfig instances to ensure
         * values come from resource files (colors.xml, dimens.xml).
         *
         * @param context Android context for resource resolution
         * @param backgroundItemStyle Style for the day background
         * @param textColorRes Color resource ID (e.g., R.color.yearview_today_text)
         * @param textSizeRes Dimension resource ID (e.g., R.dimen.yearview_today_text_size)
         * @param fontType Font type for the day text
         * @param fontTypeFace Custom typeface for day text
         * @param backgroundRadiusRes Dimension resource ID for background radius
         * @return DayConfig with resolved resource values
         */
        fun fromResources(
            context: Context,
            backgroundItemStyle: BackgroundItemStyle.AndroidXMLStyle = BackgroundItemStyle.AndroidXMLStyle(
                color = android.graphics.Color.RED,
                shape = BackgroundShape.Circle(radius = 5.0f)
            ),
            @ColorRes textColorRes: Int,
            @DimenRes textSizeRes: Int,
            fontType: FontType = FontType.NORMAL,
            fontTypeFace: Typeface? = null,
            @DimenRes backgroundRadiusRes: Int
        ): DayConfig {
            return DayConfig(
                backgroundItemStyle = backgroundItemStyle,
                textColor = ContextCompat.getColor(context, textColorRes),
                textSize = context.resources.getDimensionPixelSize(textSizeRes),
                fontType = fontType,
                fontTypeFace = fontTypeFace,
                backgroundRadius = context.resources.getDimensionPixelSize(backgroundRadiusRes)
            )
        }
    }
}
