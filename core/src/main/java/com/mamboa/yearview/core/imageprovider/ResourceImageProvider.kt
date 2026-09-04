package com.mamboa.yearview.core.imageprovider

import androidx.annotation.DrawableRes
import com.mamboa.yearview.core.ImageProvider

/**
 * An [ImageProvider] that references a drawable resource by its ID.
 *
 * Works in both Legacy and Compose modules:
 * - **Legacy**: decoded via `BitmapFactory.decodeResource()`
 * - **Compose**: loaded via `painterResource()`
 *
 * Declared as a `data class` so two providers pointing at the same resource compare
 * equal. Renderers key their bitmap caches on the enclosing
 * [com.mamboa.yearview.core.ImageSource], so with identity equality a freshly
 * constructed provider would miss the cache and re-decode the drawable on every
 * configuration change.
 */
data class ResourceImageProvider(
    @DrawableRes val resId: Int
) : ImageProvider
