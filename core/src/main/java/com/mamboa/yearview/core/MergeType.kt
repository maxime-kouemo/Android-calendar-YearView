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
     */
    CLIP
}