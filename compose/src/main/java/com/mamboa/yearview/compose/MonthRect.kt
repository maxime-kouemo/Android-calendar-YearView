package com.mamboa.yearview.compose

import androidx.compose.ui.geometry.Rect

data class MonthRect(
    val rect: Rect,
    val month: Int,
    val lastRowY: Float,
    val selectionRect: Rect,
    val selectionMargin: Float
)
