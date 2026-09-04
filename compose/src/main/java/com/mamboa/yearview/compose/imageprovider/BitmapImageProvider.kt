package com.mamboa.yearview.compose.imageprovider

import androidx.compose.ui.graphics.ImageBitmap
import com.mamboa.yearview.core.ImageProvider

/**
 * An [ImageProvider] backed by a Compose [ImageBitmap].
 *
 * Declared as a `data class` so it participates in Compose's equality-based
 * recomposition skipping: with identity equality, a provider reconstructed during
 * recomposition would look "changed" and force the whole calendar to redraw.
 */
data class BitmapImageProvider(
    val imageBitmap: ImageBitmap
) : ImageProvider
