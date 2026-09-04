package com.mamboa.yearview.legacy.imageprovider

import android.graphics.Bitmap
import com.mamboa.yearview.core.ImageProvider

/**
 * An [ImageProvider] backed by an already-decoded [Bitmap].
 *
 * Declared as a `data class` so the renderer's bitmap cache can key on it by value.
 * `Bitmap` compares by identity, so wrapping the same bitmap instance twice hits the
 * cache while re-decoding the same image does not.
 */
data class BitmapImageProvider(
    val bitmap: Bitmap
) : ImageProvider
