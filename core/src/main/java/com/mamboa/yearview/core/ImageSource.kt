package com.mamboa.yearview.core

import android.graphics.drawable.Drawable
import android.os.Parcelable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// TODO separate so that Compose has its own and legacy has its own
/**
 * Describes the source of an image.
 */
@Parcelize
sealed class ImageSource: Parcelable {

    /**
     * Loads an image from a drawable resource.
     */
    data class DrawableRes(@androidx.annotation.DrawableRes val resId: Int) : ImageSource(), Parcelable

    /**
     * Loads an image from a [ImageBitmap].
     */
    data class BitmapCompose(val bitmapCompose: @RawValue ImageBitmap) : ImageSource(), Parcelable

    /**
     * Loads an image from a [android.graphics.Bitmap].
     */
    data class Bitmap(val bitmap: android.graphics.Bitmap) : ImageSource(), Parcelable

    /**
     * Loads an image from a [Drawable].
     */
    data class ReceivedDrawable(val drawable: @RawValue Drawable?) : ImageSource()

    /**
     * No image to display/load.
     */
    data object None : ImageSource(), Parcelable
}