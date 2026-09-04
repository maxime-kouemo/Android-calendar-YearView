package com.mamboa.yearview.core

import android.os.Parcelable

/**
 * Abstract base for background item styles. Subclasses provide
 * framework-specific colour representations (e.g. Android XML `@ColorInt`).
 *
 * Not sealed — concrete implementations live in their respective modules:
 * - `LegacyBackgroundStyle` in the legacy module
 *
 * **Note:** the compose module's `ComposeBackgroundStyle` intentionally does *not*
 * extend this class. It expresses sizes as `Dp` rather than raw pixels and does not
 * implement [Parcelable] (Compose's inline value classes cannot be marshalled by
 * `@Parcelize`). Nothing in either module ever consumed a style through this base
 * type, so the shared contract lives in the unit-free [BackgroundShape] / [ImageSource]
 * / [MergeType] vocabulary instead.
 */
abstract class BackgroundItemStyle(
    /**
     * Defines the shape of the background item as defined by [BackgroundShape].
     */
    open val shape: BackgroundShape,

    /**
     * Defines the margin around the selection area, influencing the spacing between
     * the content and the background shape boundary.
     *
     * Expressed in **raw pixels**; resolve it from a dimension resource so that it
     * scales with display density.
     */
    open val selectionMargin: Float = 2.0f,

    /**
     * Defines the image source for the background item as defined by [ImageSource].
     */
    open val image: ImageSource = ImageSource.None,

    /**
     * Opacity of the fill [colour][com.mamboa.yearview.legacy.LegacyBackgroundStyle.color],
     * `0` (invisible) to `100` (fully applied).
     *
     * Multiplies the colour's own alpha channel rather than replacing it, so a fully
     * transparent colour stays invisible at any value.
     *
     * This used to be a single `opacity` property that doubled as a cross-fade: the
     * image was drawn at `100 - opacity` while the colour was drawn at `opacity`.
     * Because the default was `100`, *any* configured background image was drawn
     * completely transparent and never appeared. Image and colour opacity are now
     * independent — see [imageOpacity].
     */
    open val colorOpacity: Int = 100,

    /**
     * Opacity of the background [image], `0` (invisible) to `100` (fully opaque).
     *
     * Independent of [colorOpacity]; the colour is composited on top of the image, so
     * a translucent colour over an opaque image gives the usual tint effect.
     */
    open val imageOpacity: Int = 100,

    /**
     * Defines the merge type of the background item as defined by [MergeType].
     */
    open val mergeType: MergeType = MergeType.OVERLAY
) : Parcelable
