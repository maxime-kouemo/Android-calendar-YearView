package com.mamboa.yearview.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Marker interface for image providers.
 *
 * Each module supplies its own implementations:
 * - **core**: [com.mamboa.yearview.core.imageprovider.ResourceImageProvider] (drawable resource ID)
 * - **legacy**: `DrawableImageProvider` (Android Drawable), `BitmapImageProvider` (Android Bitmap)
 * - **compose**: `ImageBitmapImageProvider` (Compose ImageBitmap)
 */
interface ImageProvider

/**
 * Describes the source of an image.
 *
 * Uses the provider pattern so each module can supply framework-specific
 * image sources while the sealed class stays in core.
 */
@Parcelize
sealed class ImageSource : Parcelable {

    /**
     * An image supplied by an [ImageProvider].
     *
     * @param provider The module-specific image provider.
     */
    data class Provided(val provider: @RawValue ImageProvider) : ImageSource()

    /**
     * No image to display/load.
     */
    data object None : ImageSource()
}
