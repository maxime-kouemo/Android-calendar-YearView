package com.mamboa.yearview.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType

/**
 * Compose-specific background style that uses [androidx.compose.ui.graphics.Color]
 * and [Dp].
 *
 * Lives in the compose module so that `core` does not depend on Compose UI types.
 *
 * ## Transparency
 *
 * Transparency is controlled **exclusively** by [opacity] (0–100), matching the legacy
 * view. The alpha channel of [color] is ignored when the background is drawn, so
 *
 * ```
 * // Wrong — the alpha channel is discarded, this renders fully opaque:
 * ComposeBackgroundStyle(color = Color.Cyan.copy(alpha = 0.3f))
 *
 * // Right:
 * ComposeBackgroundStyle(color = Color.Cyan, opacity = 30)
 * ```
 *
 * ## Relationship to `core`
 *
 * This type deliberately does **not** extend `com.mamboa.yearview.core.BackgroundItemStyle`.
 * That base class expresses its dimensions as raw `Float` pixels and requires
 * [android.os.Parcelable], neither of which suits a Compose API:
 *
 * - Sizes here are [Dp], so the same style renders identically on every screen density.
 *   A `Float` pixel value cannot express that.
 * - `@Parcelize` could not marshal Compose's inline value classes ([Color], [Dp]) and
 *   would have thrown at runtime had anything actually parcelled a style. Persist a
 *   whole [YearViewState] with [YearViewState.Saver] instead — that path decomposes
 *   every field into Bundle-safe primitives and is the supported one.
 *
 * The genuinely shared, unit-free vocabulary ([BackgroundShape], [ImageSource],
 * [MergeType]) still comes from `core`, so both renderers keep describing the same
 * concepts.
 */
@Immutable
data class ComposeBackgroundStyle(
    /**
     * Fill colour of the shape. Its alpha channel is ignored — see [opacity].
     */
    val color: Color = Color.Transparent,

    /**
     * Outline of the background.
     *
     * Note that [BackgroundShape.RoundedSquare.cornerRadius] is a `Float` expressed in
     * **dp** (it is a `core` type and cannot reference [Dp]); it is scaled by the display
     * density at draw time, exactly like [selectionMargin].
     */
    val shape: BackgroundShape = BackgroundShape.Square,

    /**
     * Extra space added around the content on every side before the shape is drawn,
     * effectively inflating the background.
     */
    val selectionMargin: Dp = 2.dp,

    /**
     * Optional image painted inside the shape.
     */
    val image: ImageSource = ImageSource.None,

    /**
     * Opacity of the background, 0 (fully transparent) – 100 (fully opaque).
     * This — not [color]'s alpha channel — is what controls transparency.
     */
    val opacity: Int = 100,

    /**
     * How [image] and [color] are combined.
     */
    val mergeType: MergeType = MergeType.OVERLAY,
)
