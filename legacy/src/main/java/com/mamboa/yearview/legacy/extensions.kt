package com.mamboa.yearview.legacy

import android.content.res.TypedArray
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.StyleableRes
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.pathprovider.ResourcePathProvider
import com.mamboa.yearview.legacy.imageprovider.DrawableImageProvider
import com.mamboa.yearview.legacy.pathprovider.XmlPathProvider

/**
 * Wraps a nullable [Drawable] as an [ImageSource].
 *
 * A `null` drawable — the normal result of an absent `*_image` attribute — maps to
 * [ImageSource.None] rather than a [DrawableImageProvider] holding `null`, so callers
 * can rely on a single "no image" representation.
 */
internal fun Drawable?.toImageSource(): ImageSource =
    if (this == null) ImageSource.None else ImageSource.Provided(DrawableImageProvider(this))

/**
 * Reads an `<enum>`-typed styleable attribute as an enum constant, falling back to
 * [default] when the declared value has no matching ordinal.
 *
 * Attribute enums are stored as plain integers, so indexing directly into `entries` with
 * the raw value throws [IndexOutOfBoundsException] when out of range — and because attributes are
 * parsed inside the `View` constructor, that failure takes down layout inflation of the
 * entire screen. Degrading to the default keeps a bad attribute value survivable and
 * merely mis-styled.
 */
internal inline fun <reified E : Enum<E>> TypedArray.getEnumOrDefault(
    @StyleableRes index: Int,
    default: E
): E {
    val ordinal = getInteger(index, default.ordinal)
    val values = enumValues<E>()
    return values.getOrElse(ordinal) {
        Log.w(
            "YearView",
            "Attribute value $ordinal is out of range for ${E::class.java.simpleName} " +
                "(0..${values.lastIndex}); falling back to $default."
        )
        default
    }
}

/**
 * Integer codes used in XML attributes to represent [BackgroundShape] types.
 * These map to the `<enum>` values defined in `attrs.xml`.
 */
object BackgroundShapeCode {
    const val CIRCLE = 0
    const val SQUARE = 1
    const val ROUNDED_SQUARE = 2
    const val STAR = 3
    const val CUSTOM = 4
}

/** Defaults shared by [toBackgroundShape] and [TypedArray.getBackgroundShape]. */
internal object ShapeDefaults {
    /** Corner radius in **dp** for [BackgroundShape.RoundedSquare]. */
    const val CORNER_RADIUS_DP = 5f

    /**
     * Radius multiplier for [BackgroundShape.Circle].
     *
     * `1f` means "exactly enclose the content"; the size itself comes from the
     * owning config's background radius. The previous default of `5f` was passed
     * straight through as a *pixel margin*, so circles ignored the configured
     * radius entirely and were always five pixels larger than the text.
     */
    const val CIRCLE_RADIUS_MULTIPLIER = 1f

    const val STAR_POINTS = 5
    const val STAR_INNER_RADIUS_RATIO = 0.5f

    /** [BackgroundShape.Star] rejects anything outside this range in its `init` block. */
    val STAR_POINTS_RANGE = 3..10

    /** A ratio of exactly `0` collapses the star to a point, so keep it above zero. */
    val STAR_INNER_RADIUS_RANGE = 0.05f..1f
}

/**
 * Converts an XML attribute integer code to a [BackgroundShape].
 *
 * Unknown codes fall back to [BackgroundShape.Square]. They previously fell back to
 * [BackgroundShape.Circle], which silently produced a shape the caller never asked
 * for; a square is the neutral choice because it matches the drawn cell bounds.
 *
 * @see BackgroundShapeCode for the mapping of integer codes to shapes
 */
fun Int.toBackgroundShape(
    roundedRadius: Float = ShapeDefaults.CORNER_RADIUS_DP,
    circleRadius: Float = ShapeDefaults.CIRCLE_RADIUS_MULTIPLIER,
    starPoints: Int = ShapeDefaults.STAR_POINTS,
    starInnerRadius: Float = ShapeDefaults.STAR_INNER_RADIUS_RATIO,
    path: Path? = null,
    innerPadding: Int = 0
): BackgroundShape {
    return when (this) {
        BackgroundShapeCode.CIRCLE -> BackgroundShape.Circle(radius = circleRadius)
        BackgroundShapeCode.SQUARE -> BackgroundShape.Square
        BackgroundShapeCode.ROUNDED_SQUARE -> BackgroundShape.RoundedSquare(cornerRadius = roundedRadius)
        BackgroundShapeCode.STAR -> BackgroundShape.Star(
            numberOfLegs = starPoints.coerceIn(ShapeDefaults.STAR_POINTS_RANGE),
            innerRadiusRatio = starInnerRadius.coerceIn(ShapeDefaults.STAR_INNER_RADIUS_RANGE)
        )
        BackgroundShapeCode.CUSTOM -> if (path != null) {
            BackgroundShape.Custom(
                provider = XmlPathProvider(path = path, innerPadding = innerPadding)
            )
        } else {
            BackgroundShape.Square // Fallback if no path is provided
        }
        else -> BackgroundShape.Square
    }
}

/**
 * Reads a complete [BackgroundShape] from a group of related styleable attributes.
 *
 * XML previously fed only the shape code and a corner radius into [toBackgroundShape],
 * leaving two of the five shapes inert: `star` was always a 5-point star with a 0.5
 * inner ratio regardless of configuration, and `custom` had no way to supply a path so
 * it always degraded to a square. Every shape parameter now has a backing attribute.
 *
 * Out-of-range star values are clamped rather than rejected — [BackgroundShape.Star]
 * throws from its `init` block, and that would happen inside the `View` constructor.
 *
 * @param defaultShapeCode code to use when `shapeIndex` is absent from the XML
 */
internal fun TypedArray.getBackgroundShape(
    @StyleableRes shapeIndex: Int,
    @StyleableRes roundedRadiusIndex: Int,
    @StyleableRes starPointsIndex: Int,
    @StyleableRes starInnerRadiusIndex: Int,
    @StyleableRes customShapeIndex: Int,
    defaultShapeCode: Int = BackgroundShapeCode.SQUARE
): BackgroundShape {
    return when (val code = getInteger(shapeIndex, defaultShapeCode)) {
        BackgroundShapeCode.CIRCLE ->
            BackgroundShape.Circle(radius = ShapeDefaults.CIRCLE_RADIUS_MULTIPLIER)

        BackgroundShapeCode.SQUARE -> BackgroundShape.Square

        BackgroundShapeCode.ROUNDED_SQUARE -> BackgroundShape.RoundedSquare(
            cornerRadius = getFloat(roundedRadiusIndex, ShapeDefaults.CORNER_RADIUS_DP)
        )

        BackgroundShapeCode.STAR -> BackgroundShape.Star(
            numberOfLegs = getInteger(starPointsIndex, ShapeDefaults.STAR_POINTS)
                .coerceIn(ShapeDefaults.STAR_POINTS_RANGE),
            innerRadiusRatio = getFloat(starInnerRadiusIndex, ShapeDefaults.STAR_INNER_RADIUS_RATIO)
                .coerceIn(ShapeDefaults.STAR_INNER_RADIUS_RANGE)
        )

        BackgroundShapeCode.CUSTOM -> {
            val drawableRes = getResourceId(customShapeIndex, 0)
            if (drawableRes != 0) {
                BackgroundShape.Custom(provider = ResourcePathProvider(drawableRes))
            } else {
                Log.w(
                    "YearView",
                    "shape=\"custom\" requires a companion *_custom_shape vector drawable; " +
                        "falling back to a square."
                )
                BackgroundShape.Square
            }
        }

        else -> {
            Log.w("YearView", "Unknown background shape code $code; falling back to a square.")
            BackgroundShape.Square
        }
    }
}
