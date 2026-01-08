package com.mamboa.yearview.core

import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
sealed class BackgroundItemStyle(
    /**
     * Defines the shape of the background item as defined by[BackgroundShape].
     */
    open val shape: BackgroundShape,

    /**
     * Defines the margin around the selection area, influencing the spacing between
     * the content and the background shape boundary.
     */
    open val selectionMargin: Float = 2.0f,

    /**
     * Defines the image source for the background item as defined by [ImageSource].
     */
    open val image: ImageSource = ImageSource.None,

    /**
     * Defines the opacity of the background color over the image.
     */
    open val opacity: Int = 100,

    /**
     * Defines the merge type of the background item as defined by [MergeType].
     */
    open val mergeType: MergeType = MergeType.OVERLAY
): Parcelable {
    data class ComposeStyle(
        val color: @RawValue Color = Color.Transparent,
        override val shape: BackgroundShape = BackgroundShape.Square,
        override val selectionMargin: Float = 2.0f,
        override val image: ImageSource = ImageSource.None,
        override val opacity: Int = 100,
        override val mergeType: MergeType = MergeType.OVERLAY
    ) : BackgroundItemStyle(shape, selectionMargin, image, opacity, mergeType)

    data class AndroidXMLStyle(
        @ColorInt val color: Int = android.graphics.Color.TRANSPARENT,
        override val shape: BackgroundShape = BackgroundShape.Square,
        override val selectionMargin: Float = 2.0f,
        override val image: ImageSource = ImageSource.None,
        override val opacity: Int = 100,
        override val mergeType: MergeType = MergeType.OVERLAY
    ) : BackgroundItemStyle(shape, selectionMargin, image, opacity, mergeType)
}