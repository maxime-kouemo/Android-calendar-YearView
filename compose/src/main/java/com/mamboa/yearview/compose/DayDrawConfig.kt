package com.mamboa.yearview.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.mamboa.yearview.core.BackgroundShape

data class DayDrawConfig(
    val textStyle: TextStyle,
    val backgroundShape: BackgroundShape?,
    val backgroundColor: Color,
    val backgroundRadius: Float,
    val alpha: Float = 1f
)
