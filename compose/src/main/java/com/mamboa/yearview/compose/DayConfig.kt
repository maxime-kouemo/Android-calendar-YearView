package com.mamboa.yearview.compose

import android.os.Parcelable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class DayConfig(
    val backgroundItemStyle: BackgroundItemStyle.ComposeStyle = BackgroundItemStyle.ComposeStyle(
        color = Color.Red,
        shape = BackgroundShape.Circle(radius = 5.0f)
    ),
    val textStyle: @RawValue TextStyle = TextStyle(
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
): Parcelable
