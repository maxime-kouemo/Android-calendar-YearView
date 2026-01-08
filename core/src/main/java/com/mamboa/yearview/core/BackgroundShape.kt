package com.mamboa.yearview.core

import android.os.Parcelable
import androidx.annotation.DimenRes
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * This sealed class represents different shapes that can be used as backgrounds.
 */
@Parcelize
sealed class BackgroundShape : Parcelable {
    /**
     * Represents a circle shape with a specified radius.
     */
    data class Circle(val radius: Float) : BackgroundShape()

    /**
     * Represents a square shape.
     */
    data object Square : BackgroundShape()

    /**
     * Represents a rounded square shape with a specified corner radius.
     */
    data class RoundedSquare(val cornerRadius: Float) : BackgroundShape()

    /**
     * Represents a star shape with a specified number of legs and inner radius ratio.
     *
     * @param numberOfLegs The number of legs in the star.
     * @param innerRadiusRatio The ratio of the inner radius to the outer radius.
     */
    data class Star(val numberOfLegs: Int = 5, val innerRadiusRatio: Float = 0.5f) : BackgroundShape()

    /**
     * Represents a custom shape defined by a Compose Path.
     *
     * @param composePath The path defining the custom shape.
     * @param innerPadding The padding to apply inside the shape.
     */
    data class ComposeCustom(val composePath: @RawValue Path, val innerPadding: @RawValue Dp = 0.dp) : BackgroundShape()

    /**
     * Represents a custom shape defined by an Android legacy XML Path.
     *
     * @param xmlPath The path defining the custom shape.
     * @param innerPadding The padding to apply inside the shape.
     */
    data class xmlCustom(val xmlPath: @RawValue android.graphics.Path, @DimenRes val innerPadding: Int = 0) : BackgroundShape()
}