package com.mamboa.yearview.legacy

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import com.mamboa.yearview.core.FontType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Configuration class for day-name header styling (M, T, W, T, F, S, S row) in Legacy YearView.
 *
 * This data class stores resolved values (@ColorInt, @Px) for programmatic usage.
 * Use the companion object factory method to create instances from resource IDs.
 */
@Parcelize
data class DayNameConfig(
    /**
     * Text color for the day-name header (resolved color value).
     */
    @ColorInt
    val textColor: Int = Color.BLACK,

    /**
     * Text size for the day-name header in pixels (resolved size).
     */
    @Px
    val textSize: Int = 0,

    /**
     * Font type for the day-name header text.
     */
    val fontType: FontType = FontType.NORMAL,

    /**
     * Custom typeface for day-name header text.
     * Not included in parceling — [Typeface] is not [Parcelable].
     * After configuration change, callers must re-set custom typefaces.
     */
    @IgnoredOnParcel
    val fontTypeFace: Typeface? = null,

    /**
     * If true, the day-name header style (font type, text color) will also be applied
     * to weekend day names instead of using the weekend-specific styling.
     */
    val transcendsWeekend: Boolean = false
) : Parcelable {

    companion object {
        /**
         * Creates a DayNameConfig from resource IDs.
         *
         * @param context Android context for resource resolution
         * @param textColorRes Color resource ID for day-name text
         * @param textSizeRes Dimension resource ID for day-name text size
         * @param fontType Font type for the day-name text
         * @param fontTypeFace Custom typeface for day-name text
         * @param transcendsWeekend Whether day-name style overrides weekend styling
         * @return DayNameConfig with resolved resource values
         */
        fun fromResources(
            context: Context,
            @ColorRes textColorRes: Int,
            @DimenRes textSizeRes: Int,
            fontType: FontType = FontType.NORMAL,
            fontTypeFace: Typeface? = null,
            transcendsWeekend: Boolean = false
        ): DayNameConfig {
            return DayNameConfig(
                textColor = ContextCompat.getColor(context, textColorRes),
                textSize = context.resources.getDimensionPixelSize(textSizeRes),
                fontType = fontType,
                fontTypeFace = fontTypeFace,
                transcendsWeekend = transcendsWeekend
            )
        }
    }
}
