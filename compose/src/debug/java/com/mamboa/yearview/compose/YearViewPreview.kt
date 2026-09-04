package com.mamboa.yearview.compose

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.core.TitleGravity
import com.mamboa.yearview.core.datetime.timeproviders.KotlinxTimeProvider
import java.util.Locale

/**
 * Preview harness for [YearView].
 *
 * Lives in the `debug` source set on purpose: only the `release` variant is published,
 * so neither this file nor the `ui-tooling-preview` dependency ends up in the AAR that
 * consumers depend on.
 */
@Preview(
    name = "yearview", showSystemUi = true, showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun YearViewPreview() {
    val dateTimeProvider = KotlinxTimeProvider()
    val currentYear = dateTimeProvider.currentYear()

    Box(
        modifier = Modifier
            .background(Color.White)
            .padding(16.dp)
    ) {
        YearView(
            state = YearViewState(
                year = currentYear,
                rows = 4,
                columns = 3,
                verticalSpacing = 8.dp,
                horizontalSpacing = 8.dp,
                monthConfig = MonthConfig(
                    titleGravity = TitleGravity.CENTER,
                    marginBelowMonthName = 8.dp,
                    selectionBackgroundItemStyle = ComposeBackgroundStyle(
                        color = Color(0xFF1976D2),
                        shape = BackgroundShape.Circle(radius = 1.0f)
                    ),
                    backgroundItemStyle = ComposeBackgroundStyle(
                        color = Color(0xFFE3F2FD),
                        shape = BackgroundShape.RoundedSquare(cornerRadius = 5.0f),
                        selectionMargin = 2.dp
                    ),
                    nameStyle = TextStyle(
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    todayNameStyle = TextStyle(
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    nameFormat = "MMMM"
                ),
                todayConfig = DayConfig(
                    backgroundItemStyle = ComposeBackgroundStyle(
                        color = Color(0xFF1976D2),
                        shape = BackgroundShape.Circle(radius = 1.0f)
                    )
                ),
                selectedDayConfig = DayConfig(
                    backgroundItemStyle = ComposeBackgroundStyle(
                        color = Color(0xFF4CAF50),
                        shape = BackgroundShape.Square
                    ),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                ),
                dayFormat = "yyyy-MM-dd"
            ),
            onDayClick = { timestamp ->
                val date = dateTimeProvider.fromMillis(timestamp)
                println("Day clicked: ${dateTimeProvider.format(date, "yyyy-MM-dd", Locale.ROOT)}")
            },
            onMonthClick = { timestamp ->
                val date = dateTimeProvider.fromMillis(timestamp)
                println("Month clicked: ${dateTimeProvider.format(date, "MMMM yyyy", Locale.ROOT)}")
            }
        )
    }
}
