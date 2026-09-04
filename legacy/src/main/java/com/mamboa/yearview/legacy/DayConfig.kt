package com.mamboa.yearview.legacy

import android.content.Context
import android.graphics.Typeface
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.FontType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt

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
    val backgroundItemStyle: LegacyBackgroundStyle = LegacyBackgroundStyle(
        color = android.graphics.Color.RED,
        shape = BackgroundShape.Circle(radius = 1.0f)
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
     * Not included in parceling — [Typeface] is not [Parcelable].
     * After configuration change, callers must re-set custom typefaces.
     */
    @IgnoredOnParcel
    val fontTypeFace: Typeface? = null,

    /**
     * Breathing room around the day text, used as a multiplier for the background size.
     * A value of 1f means the background will fit snugly around the text.
     * Matches the Compose module's resolution-independent approach.
     */
    val backgroundRadius: Float = 1f
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
         * @param backgroundRadiusRes Dimension resource ID for background radius (pixel value
         *   that will be converted to a resolution-independent multiplier)
         * @return DayConfig with resolved resource values
         */
        fun fromResources(
            context: Context,
            backgroundItemStyle: LegacyBackgroundStyle = LegacyBackgroundStyle(
                color = android.graphics.Color.RED,
                shape = BackgroundShape.Circle(radius = 1.0f)
            ),
            @ColorRes textColorRes: Int,
            @DimenRes textSizeRes: Int,
            fontType: FontType = FontType.NORMAL,
            fontTypeFace: Typeface? = null,
            @DimenRes backgroundRadiusRes: Int
        ): DayConfig {
            val textSizePx = context.resources.getDimensionPixelSize(textSizeRes)
            val radiusPx = context.resources.getDimensionPixelSize(backgroundRadiusRes)
            return DayConfig(
                backgroundItemStyle = backgroundItemStyle,
                textColor = ContextCompat.getColor(context, textColorRes),
                textSize = textSizePx,
                fontType = fontType,
                fontTypeFace = fontTypeFace,
                backgroundRadius = pixelsToMultiplier(radiusPx, textSizePx)
            )
        }

        /**
         * Converts a pixel-based background radius to a resolution-independent multiplier.
         * The multiplier represents how much larger the background is relative to the text.
         * A value of 1f means the background fits snugly; larger values add breathing room.
         */
        fun pixelsToMultiplier(radiusPx: Int, textSizePx: Int): Float {
            return if (textSizePx > 0) 1f + (2f * radiusPx) / textSizePx else 1f
        }

        /**
         * Converts a multiplier-based background radius back to a pixel margin
         * for use in legacy Canvas drawing operations.
         *
         * Rounds rather than truncates: [pixelsToMultiplier] introduces floating-point
         * error, so `toInt()` turned values such as `0.99999` into `0` and produced a
         * margin one pixel smaller than the caller asked for. Rounding makes this an
         * exact inverse of [pixelsToMultiplier].
         */
        fun multiplierToPixelMargin(multiplier: Float, textSizePx: Int): Int {
            return ((multiplier - 1f) * textSizePx / 2f).roundToInt()
        }
    }
}
