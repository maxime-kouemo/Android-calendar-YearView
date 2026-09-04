package com.mamboa.yearview.compose

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import com.mamboa.yearview.compose.pathprovider.ComposePathProvider
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.MergeType
import com.mamboa.yearview.core.pathprovider.ResourcePathProvider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Converts [ComposeBackgroundStyle.opacity] (0–100) to a 0f–1f alpha. */
internal val ComposeBackgroundStyle.opacityAlpha: Float
    get() = opacity.toFloat() / 100f

/**
 * Draws a styled background (shape + optional image) inside [bounds].
 *
 * Path and matrix scratch buffers come from [resources] so that a full draw pass
 * allocates nothing per cell.
 */
internal fun DrawScope.drawStyledBackground(
    bounds: Rect,
    style: ComposeBackgroundStyle,
    painter: Painter?,
    resources: DrawResources,
) {
    when (style.mergeType) {
        MergeType.CLIP -> drawStyledBackgroundInnerClip(bounds, style, painter, resources)
        else -> drawStyledBackgroundMerge(bounds, style, painter, resources)
    }
}

/**
 * Draws the background with merging for the given bounds and style.
 *
 * @param bounds The bounds within which the background should be drawn.
 * @param style The style configuration for the background item.
 * @param painter The painter used to draw the background image, if any.
 */
private fun DrawScope.drawStyledBackgroundMerge(
    bounds: Rect,
    style: ComposeBackgroundStyle,
    painter: Painter?,
    resources: DrawResources,
) {
    // 1. Draw Image using existing method
    if (painter != null) {
        drawScaledImage(bounds, style, painter)
    }

    // 2. Draw shape with color
    val colorAlpha = style.opacityAlpha
    val colorWithOpacity = style.color.copy(alpha = colorAlpha)

    // Reuse scratch path to avoid per-call allocation
    val scratchPath = resources.scratchPath
    scratchPath.reset()
    scratchPath.createShapePath(bounds, style.shape, resources)
    drawPath(
        path = scratchPath,
        color = colorWithOpacity,
        style = Fill
    )
}

/**
 * Draws the background with clipping for the given bounds and style.
 *
 * @param bounds The bounds within which the background should be drawn.
 * @param style The style configuration for the background item.
 * @param painter The painter used to draw the background image, if any.
 */
private fun DrawScope.drawStyledBackgroundInnerClip(
    bounds: Rect,
    style: ComposeBackgroundStyle,
    painter: Painter?,
    resources: DrawResources,
) {
    // Use the dedicated clip scratch path — separate from the general-purpose scratchPath
    // because clipPath() retains the Path reference for the duration of the lambda.
    val clipScratchPath = resources.clipScratchPath
    clipScratchPath.reset()
    clipScratchPath.createShapePath(bounds, style.shape, resources)
    clipPath(clipScratchPath) {
        drawClippedContent(bounds, style, painter)
    }
}

/**
 * Creates a shape path based on the provided [BackgroundShape] and [bounds].
 *
 * ## Units
 *
 * [BackgroundShape] is a `core` type and therefore cannot use [androidx.compose.ui.unit.Dp].
 * Its one true length — [BackgroundShape.RoundedSquare.cornerRadius] — is documented as
 * dp and is scaled by [DrawResources.density] here, so a given style renders at the same
 * physical size on every screen. [BackgroundShape.Circle.radius] and
 * [BackgroundShape.Star.innerRadiusRatio] are unitless ratios of the cell and are used
 * as-is.
 *
 * @param bounds The bounds within which the shape should be drawn.
 * @param shape The [BackgroundShape] defining the shape to be drawn.
 * @param resources Supplies the drawable-path cache, resolved inner paddings, the display
 *   density, and the pooled scratch path / matrix used when scaling custom shapes.
 * @return The created [Path] representing the shape.
 */
internal fun Path.createShapePath(
    bounds: Rect,
    shape: BackgroundShape,
    resources: DrawResources,
): Path = apply {
    val drawablePathCache = resources.drawablePathCache
    val resolvedInnerPaddings = resources.resolvedInnerPaddings
    val scaleScratchPath = resources.scaleScratchPath
    val scaleMatrix = resources.scaleMatrix
    val density = resources.density
    when (shape) {
        is BackgroundShape.Circle -> {
            val baseSize = minOf(bounds.width, bounds.height)
            val size = baseSize * shape.radius
            val centerX = bounds.center.x
            val centerY = bounds.center.y
            addOval(
                Rect(
                    left = centerX - size / 2,
                    top = centerY - size / 2,
                    right = centerX + size / 2,
                    bottom = centerY + size / 2
                )
            )
        }

        is BackgroundShape.RoundedSquare -> {
            // cornerRadius is declared in dp (see BackgroundShape); scale it to pixels.
            val radiusPx = shape.cornerRadius * density
            addRoundRect(RoundRect(bounds, CornerRadius(radiusPx, radiusPx)))
        }

        is BackgroundShape.Star -> {
            addStarPath(bounds, shape.numberOfLegs, shape.innerRadiusRatio)
        }

        is BackgroundShape.Custom -> {
            when (val provider = shape.provider) {
                is ResourcePathProvider -> {
                    val cachedPath = drawablePathCache[provider.drawableRes]
                    if (cachedPath != null) {
                        scaleAndTranslatePath(
                            sourcePath = cachedPath,
                            targetBounds = bounds,
                            innerPadding = if (provider.innerPadding == 0) 0f
                                else resolvedInnerPaddings[provider.innerPadding] ?: 0f,
                            scaleScratchPath = scaleScratchPath,
                            scaleMatrix = scaleMatrix,
                        )
                    } else {
                        addRect(bounds)
                    }
                }
                is ComposePathProvider -> {
                    scaleAndTranslatePath(
                        sourcePath = provider.path,
                        targetBounds = bounds,
                        // innerPadding is a Dp: `.value` alone would be a raw dp number
                        // used as pixels, shrinking the padding on high-density screens.
                        innerPadding = provider.innerPadding.value * density,
                        scaleScratchPath = scaleScratchPath,
                        scaleMatrix = scaleMatrix,
                    )
                }
                else -> addRect(bounds)
            }
        }

        is BackgroundShape.Square -> {
            addRect(bounds)
        }
    }
}

/**
 * Adds a star-shaped path to the current path.
 *
 * @param bounds The bounding rectangle for the star shape.
 * @param numberOfLegs The number of legs on the star.
 * @param innerRadiusRatio The ratio of the inner radius to the outer radius.
 */
private fun Path.addStarPath(
    bounds: Rect,
    numberOfLegs: Int,
    innerRadiusRatio: Float
) {
    val centerX = bounds.center.x
    val centerY = bounds.center.y
    val outerRadius = minOf(bounds.width, bounds.height) / 2f
    val innerRadius = outerRadius * innerRadiusRatio
    val angleStep = (2f * PI / numberOfLegs).toFloat()

    moveTo(
        centerX + (outerRadius * cos(-PI / 2)).toFloat(),
        centerY + (outerRadius * sin(-PI / 2)).toFloat()
    )

    for (i in 0 until numberOfLegs) {
        val outerAngle = -PI / 2 + i * angleStep
        val innerAngle = outerAngle + angleStep / 2

        lineTo(
            centerX + (innerRadius * cos(innerAngle)).toFloat(),
            centerY + (innerRadius * sin(innerAngle)).toFloat()
        )

        val nextOuterAngle = -PI / 2 + (i + 1) * angleStep
        lineTo(
            centerX + (outerRadius * cos(nextOuterAngle)).toFloat(),
            centerY + (outerRadius * sin(nextOuterAngle)).toFloat()
        )
    }
    close()
}

/**
 * Draws the clipped content within the given bounds.
 * The clip path is applied by the caller via [clipPath] before invoking this function,
 * so only image and fill-rect drawing are needed here.
 *
 * @param bounds The bounds within which the content should be clipped.
 * @param style The style configuration for the background item.
 * @param painter The painter used to draw the content.
 */
private fun DrawScope.drawClippedContent(
    bounds: Rect,
    style: ComposeBackgroundStyle,
    painter: Painter?,
) {
    // Draw Image if provided
    if (painter != null) {
        drawScaledImage(bounds, style, painter)
    }

    // Draw the shape with color
    val colorWithOpacity = style.color.copy(alpha = style.opacityAlpha)
    drawRect(
        color = colorWithOpacity,
        topLeft = bounds.topLeft,
        size = bounds.size,
        style = Fill
    )
}

/**
 * Draw the image with scaling and opacity
 *
 * @param bounds The bounds of the shape
 * @param style The style of the shape
 * @param painter The painter of the image
 */
private fun DrawScope.drawScaledImage(
    bounds: Rect,
    style: ComposeBackgroundStyle,
    painter: Painter
) {
    withTransform({
        translate(bounds.left, bounds.top)
    }) {
        val drawSize = if (painter.intrinsicSize != Size.Unspecified &&
            painter.intrinsicSize.width > 0 &&
            painter.intrinsicSize.height > 0
        ) {
            val painterAspect = painter.intrinsicSize.width / painter.intrinsicSize.height
            val boundsAspect = bounds.width / bounds.height
            if (painterAspect > boundsAspect) {
                Size(bounds.width, bounds.width / painterAspect)
            } else {
                Size(bounds.height * painterAspect, bounds.height)
            }
        } else {
            bounds.size
        }
        val offsetX = (bounds.width - drawSize.width) / 2f
        val offsetY = (bounds.height - drawSize.height) / 2f
        translate(offsetX, offsetY) {
            with(painter) {
                draw(size = drawSize, alpha = style.opacityAlpha)
            }
        }
    }
}

/**
 * Scale and translate the path to fit the target bounds
 *
 * @param sourcePath The source path to be scaled and translated
 * @param targetBounds The target bounds to fit the path into
 * @param innerPadding The padding to apply to the target bounds
 * @param scaleScratchPath The scratch path used for scaling
 * @param scaleMatrix The matrix used for scaling
 * @return The scaled and translated path
 */
private fun Path.scaleAndTranslatePath(
    sourcePath: Path,
    targetBounds: Rect,
    innerPadding: Float = 0f,
    scaleScratchPath: Path,
    scaleMatrix: Matrix,
): Path = apply {
    // Adjust target bounds with padding
    val paddedBounds = Rect(
        left = targetBounds.left + innerPadding,
        top = targetBounds.top + innerPadding,
        right = targetBounds.right - innerPadding,
        bottom = targetBounds.bottom - innerPadding
    )

    val pathBounds = sourcePath.getBounds()
    // A vector resource that failed to parse is cached as an empty Path (see YearView's
    // drawablePathCache), which would make the scale factors below Infinity/NaN and
    // corrupt the whole draw pass. Fall back to a plain rectangle instead.
    if (pathBounds.width <= 0f || pathBounds.height <= 0f) {
        addRect(targetBounds)
        return@apply
    }

    val scaleX = paddedBounds.width / pathBounds.width
    val scaleY = paddedBounds.height / pathBounds.height

    // Both the path and the matrix are pooled, so scaling a custom shape allocates nothing.
    scaleMatrix.reset()
    scaleMatrix.scale(scaleX, scaleY)
    val scaledPath = scaleScratchPath.apply {
        reset()
        addPath(sourcePath)
        transform(scaleMatrix)
    }

    // Calculate the offset to center the path in padded bounds
    val scaledBounds = scaledPath.getBounds()
    val offsetX = paddedBounds.left - scaledBounds.left
    val offsetY = paddedBounds.top - scaledBounds.top

    // Add the scaled path with translation offset
    addPath(
        path = scaledPath,
        offset = Offset(
            x = offsetX,
            y = offsetY
        )
    )
}
