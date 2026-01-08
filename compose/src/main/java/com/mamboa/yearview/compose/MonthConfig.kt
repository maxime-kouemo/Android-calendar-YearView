package com.mamboa.yearview.compose

import android.os.Parcelable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.TitleGravity
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Configuration class for month-related styling and formatting in the YearView.
 */
@Parcelize
data class MonthConfig(
    /**
     * Gravity of the month title within its container.
     */
    val titleGravity: TitleGravity = TitleGravity.CENTER,

    /**
     * Margin below the month title.
     */
    val marginBelowMonthName: @RawValue Dp = 8.dp,

    /**
     * Style for the background of a selected month.
     */
    val selectionBackgroundItemStyle: BackgroundItemStyle.ComposeStyle = BackgroundItemStyle.ComposeStyle(
        color = Color.Blue,
        shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
        selectionMargin = 5.0f
    ),

    /**
     * Style for the background of a month.
     */
    val backgroundItemStyle: BackgroundItemStyle.ComposeStyle = BackgroundItemStyle.ComposeStyle(
        color = Color.Transparent,
        shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
        selectionMargin = 2.0f
    ),

    /**
     * Text style for a month name.
     */
    val nameStyle: @RawValue TextStyle = TextStyle(
        color = Color.Black,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    ),

    /**
     * Text style for the name of the current month.
     */
    val todayNameStyle: @RawValue TextStyle = TextStyle(
        color = Color.Black,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    ),

    /**
     * Format for displaying the month name.
     */
    val nameFormat: String = "MMMM"
): Parcelable
