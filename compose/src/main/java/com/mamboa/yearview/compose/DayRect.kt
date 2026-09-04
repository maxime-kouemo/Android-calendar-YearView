package com.mamboa.yearview.compose

import androidx.compose.ui.geometry.Rect

data class DayRect(
    val rect: Rect,
    /** Raw year component (e.g. 2025). Stored to avoid per-day format() during layout. */
    val year: Int,
    /** Raw 1-based month component. */
    val month: Int,
    /** Raw day-of-month component. */
    val day: Int,
    val textHeight: Float = 0f,
    /** 0-based month index this day belongs to (used for hit-testing). */
    val monthIndex: Int = 0
)
