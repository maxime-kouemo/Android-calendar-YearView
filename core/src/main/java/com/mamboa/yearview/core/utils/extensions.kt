package com.mamboa.yearview.core.utils

import android.graphics.Path
import androidx.compose.ui.graphics.Color
import com.mamboa.yearview.core.BackgroundShape

fun Int.toBackgroundShape(
    roundedRadius: Float = 5f,
    circleRadius: Float = 5f,
    starPoints: Int = 5,
    starInnerRadius: Float = 0.5f,
    path: Path? = null,
    innerPadding: Int = 0
): BackgroundShape {
    return when (this) {
        0 -> BackgroundShape.Circle(radius = circleRadius)
        1 -> BackgroundShape.Square
        2 -> BackgroundShape.RoundedSquare(cornerRadius = roundedRadius)
        3 -> BackgroundShape.Star(numberOfLegs = starPoints, innerRadiusRatio = starInnerRadius)
        4 -> if (path != null) {
            BackgroundShape.xmlCustom(xmlPath = path, innerPadding = innerPadding)
        } else {
            BackgroundShape.Square // Fallback if no path is provided
        }
        else -> BackgroundShape.Circle(radius = circleRadius)
    }
}

fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255.0f + 0.5f).toInt(),
        (red * 255.0f + 0.5f).toInt(),
        (green * 255.0f + 0.5f).toInt(),
        (blue * 255.0f + 0.5f).toInt()
    )
}