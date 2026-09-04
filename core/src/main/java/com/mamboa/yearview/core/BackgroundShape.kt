package com.mamboa.yearview.core

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Marker interface for custom shape providers.
 *
 * Each module supplies its own implementations:
 * - **core**: [com.mamboa.yearview.core.pathprovider.ResourcePathProvider] (vector drawable resource)
 * - **legacy**: `XmlPathProvider` (Android `android.graphics.Path`)
 * - **compose**: `ComposePathProvider` (Compose `Path`)
 */
interface CustomShapeProvider

/**
 * Represents different shapes that can be used as backgrounds.
 *
 * Built-in shapes ([Circle], [Square], [RoundedSquare], [Star]) cover common
 * use cases. For arbitrary shapes use [Custom] with a module-specific
 * [CustomShapeProvider].
 *
 * ## Units
 *
 * Most parameters here are **unitless ratios** relative to the cell being drawn
 * ([Circle.radius], [Star.innerRadiusRatio]) and are therefore resolution independent
 * by construction. The one genuine length is [RoundedSquare.cornerRadius], which is
 * expressed in **density-independent pixels (dp)**; renderers are responsible for
 * scaling it by the display density.
 */
@Parcelize
sealed class BackgroundShape : Parcelable {
    /**
     * Represents a circle shape.
     *
     * @param radius **Unitless multiplier** of the cell's shorter side, not a length:
     *   `1.0f` produces a circle that exactly spans the cell, `0.5f` one that spans half
     *   of it. Because it is relative, it needs no density conversion.
     */
    data class Circle(val radius: Float) : BackgroundShape()

    /**
     * Represents a square shape.
     */
    data object Square : BackgroundShape()

    /**
     * Represents a rounded square shape.
     *
     * @param cornerRadius Corner radius in **density-independent pixels (dp)**. This is a
     *   real length, so a renderer must multiply it by the display density before use —
     *   otherwise the same value yields visibly different corners on different screens.
     */
    data class RoundedSquare(val cornerRadius: Float) : BackgroundShape()

    /**
     * Represents a star shape with a specified number of legs and inner radius ratio.
     *
     * @param numberOfLegs The number of legs in the star. Must be between 3 and 10 (inclusive).
     *   Values outside this range will cause an [IllegalArgumentException].
     * @param innerRadiusRatio The ratio of the inner radius to the outer radius (0f–1f).
     *   Unitless, so no density conversion applies.
     */
    data class Star(
        val numberOfLegs: Int = 5,
        val innerRadiusRatio: Float = 0.5f,
        val maximumNumberOfLegs: Int = 10,
    ) : BackgroundShape() {
        init {
            require(numberOfLegs in 3..maximumNumberOfLegs) {
                "numberOfLegs must be between 3 and $maximumNumberOfLegs, but was $numberOfLegs"
            }
        }
    }

    /**
     * Represents a custom shape defined by a [CustomShapeProvider].
     *
     * @param provider The module-specific provider of the custom shape.
     */
    data class Custom(val provider: @RawValue CustomShapeProvider) : BackgroundShape()
}
