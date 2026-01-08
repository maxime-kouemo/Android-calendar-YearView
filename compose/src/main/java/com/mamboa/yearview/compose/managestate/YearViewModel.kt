/*
package com.mamboa.yearview.compose.managestate

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class YearViewModel(
    initialState: YearViewState = YearViewState()
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<YearViewState> = _state

    fun updateState(update: YearViewState.() -> YearViewState) {
        _state.value = _state.value.update()
    }

    // Example: update year
    fun setYear(year: Int) {
        updateState { copy(year = year) }
    }

    fun setColumns(columns: Int) {
        updateState { copy(columns = columns) }
    }

    fun setRows(rows: Int) {
        updateState { copy(rows = rows) }
    }

    // Add more update methods as needed for your UI
}*/
