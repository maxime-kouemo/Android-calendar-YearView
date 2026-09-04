package com.mamboa.yearview.core.pathprovider

import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import com.mamboa.yearview.core.CustomShapeProvider

/**
 * A [CustomShapeProvider] that extracts a path from a vector drawable resource.
 *
 * Works in both Legacy and Compose modules:
 * - **Legacy**: parsed via `PathParser` from the vector drawable XML
 * - **Compose**: parsed via `PathParser` and converted to a Compose `Path`
 *
 * @param drawableRes The resource ID of the vector drawable whose path data
 *   defines the custom shape.
 * @param innerPadding Optional dimension resource ID for inner padding
 *   between the shape boundary and its content.
 */
class ResourcePathProvider(
    @DrawableRes val drawableRes: Int,
    @DimenRes val innerPadding: Int = 0
) : CustomShapeProvider
