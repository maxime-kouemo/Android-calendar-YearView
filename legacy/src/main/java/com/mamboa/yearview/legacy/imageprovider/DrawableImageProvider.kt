package com.mamboa.yearview.legacy.imageprovider

import android.graphics.drawable.Drawable
import com.mamboa.yearview.core.ImageProvider

/**
 * An [ImageProvider] backed by an already-resolved Android [Drawable].
 *
 * Declared as a `data class` so the renderer's bitmap cache can key on it by value.
 * Note that `Drawable` itself does not override `equals`, so two providers wrapping
 * *distinct but visually identical* drawables still compare unequal — prefer
 * [com.mamboa.yearview.core.imageprovider.ResourceImageProvider] when you have a
 * resource ID, since that compares by ID and always hits the cache.
 */
data class DrawableImageProvider(
    val drawable: Drawable?,
) : ImageProvider
