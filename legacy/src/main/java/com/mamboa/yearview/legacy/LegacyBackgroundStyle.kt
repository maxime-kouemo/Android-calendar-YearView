package com.mamboa.yearview.legacy

import android.os.Parcelable
import androidx.annotation.ColorInt
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.ImageSource
import com.mamboa.yearview.core.MergeType
import kotlinx.parcelize.Parcelize

/**
 * Legacy (Android XML / Canvas) background style that uses `@ColorInt` colours.
 *
 * Lives in the legacy module so that `core` does not depend on any
 * view-framework-specific types.
 */
@Parcelize
data class LegacyBackgroundStyle(
    @ColorInt val color: Int = android.graphics.Color.TRANSPARENT,
    override val shape: BackgroundShape = BackgroundShape.Square,
    override val selectionMargin: Float = 2.0f,
    override val image: ImageSource = ImageSource.None,
    override val colorOpacity: Int = 100,
    override val imageOpacity: Int = 100,
    override val mergeType: MergeType = MergeType.OVERLAY
) : BackgroundItemStyle(shape, selectionMargin, image, colorOpacity, imageOpacity, mergeType),
    Parcelable
