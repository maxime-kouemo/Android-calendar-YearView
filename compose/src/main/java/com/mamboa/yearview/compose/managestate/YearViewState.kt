/*
package com.mamboa.yearview.compose.managestate

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mamboa.yearview.core.BackgroundItemStyle
import com.mamboa.yearview.core.BackgroundShape
import com.mamboa.yearview.compose.DayConfig
import com.mamboa.yearview.compose.MonthConfig
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.time.DayOfWeek

@Parcelize
data class YearViewState(
    // Calendar layout
    val year: Int = 2025,
    val rows: Int = 4,
    val columns: Int = 3,
    val firstDayOfWeekValue: Int = DayOfWeek.MONDAY.value,

    // Spacing (dp converted to Float)
    val monthSpacingHorizontal: Float = 16f,
    val monthSpacingVertical: Float = 16f,

    // Selection configuration
    val isDaySelectionEnabled: Boolean = true,
    val isDaySelectionVisuallySticky: Boolean = true,


    // Additional style configurations
    // (These properties should be defined as needed, ensuring they are Parcelable)
    val monthConfig: MonthConfig = MonthConfig(),
    val dayNameStyle: @RawValue TextStyle = TextStyle(color = Color.Black, fontSize = 8.sp),
    val simpleDayStyle: @RawValue TextStyle = TextStyle(color = Color.Black, fontSize = 8.sp),
    val weekendDayStyle: @RawValue TextStyle = TextStyle(color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Normal),
    val todayConfig: DayConfig = DayConfig(),
    val selectedDayStyle: @RawValue TextStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
    val selectedDayBackgroundItemStyle: BackgroundItemStyle = BackgroundItemStyle.ComposeStyle(
        color = Color(0xFF4CAF50),
        shape = BackgroundShape.Circle(radius = 8f)
    )
) : Parcelable, MutableState<YearViewState> {
    companion object : Parceler<YearViewState> {
        val saver: Saver<YearViewState, *> = listSaver(
            save = { state ->
                listOf(
                    state.year,
                    state.rows,
                    state.columns,
                    state.firstDayOfWeekValue,
                    state.monthSpacingHorizontal,
                    state.monthSpacingVertical,
                    state.isDaySelectionEnabled,
                    state.isDaySelectionVisuallySticky,
                    state.monthConfig,
                    state.dayNameStyle,
                    state.simpleDayStyle,
                    state.weekendDayStyle,
                    state.todayConfig,
                    state.selectedDayStyle,
                    state.selectedDayBackgroundItemStyle
                )
            },
            restore = { restored ->
                YearViewState(
                    year = restored[0] as Int,
                    rows = restored[1] as Int,
                    columns = restored[2] as Int,
                    firstDayOfWeekValue = restored[3] as Int,
                    monthSpacingHorizontal = restored[4] as Float,
                    monthSpacingVertical = restored[5] as Float,
                    isDaySelectionEnabled = restored[6] as Boolean,
                    isDaySelectionVisuallySticky = restored[7] as Boolean,
                    monthConfig = restored[8] as MonthConfig,
                    dayNameStyle = restored[9] as TextStyle,
                    simpleDayStyle = restored[10] as TextStyle,
                    weekendDayStyle = restored[11] as TextStyle,
                    todayConfig = restored[12] as DayConfig,
                    selectedDayStyle = restored[13] as TextStyle,
                    selectedDayBackgroundItemStyle = restored[14] as BackgroundItemStyle
                )
            }
        )

        override fun YearViewState.write(dest: Parcel, flags: Int) {
            TODO("Not yet implemented")
        }

        override fun create(parcel: Parcel): YearViewState = TODO()
    }

    override fun describeContents(): Int {
        TODO("Not yet implemented")
    }

    override var value: YearViewState
        get() = TODO("Not yet implemented")
        set(value) {}
}


*/
/*@Composable
fun rememberYearViewState(
    initial: YearViewState = YearViewState()
): MutableState<YearViewState> = rememberSaveable(saver = YearViewState.saver) { mutableStateOf(initial) }*//*


@Composable
fun rememberYearViewState(initial: YearViewState = YearViewState()): YearViewState {
    return rememberSaveable(saver = YearViewState.saver) {
        mutableStateOf(initial).value
    }
}
*/
