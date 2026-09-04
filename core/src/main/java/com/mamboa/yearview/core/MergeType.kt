package com.mamboa.yearview.core

/**
 * Describes the type of merge to be performed between the shape and the image in [BackgroundItemStyle]
 */
enum class MergeType {
    /**
     * The shape is drawn on top of the image.
     */
    OVERLAY,

    /**
     * The image is clipped to the shape.
     *
     * **Performance:** In the legacy Canvas renderer, this uses [android.graphics.Canvas.saveLayer]
     * which allocates an off-screen buffer per call. Prefer [OVERLAY] for per-day-cell
     * backgrounds; reserve [CLIP] for month-level backgrounds where the cost is bounded.
     */
    CLIP
}