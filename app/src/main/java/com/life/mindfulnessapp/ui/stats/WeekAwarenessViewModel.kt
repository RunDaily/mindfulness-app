package com.life.mindfulnessapp.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.domain.model.WeeklyReportData
import com.life.mindfulnessapp.domain.usecase.GetWeekAwarenessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeekAwarenessViewModel @Inject constructor(
    private val getWeekAwareness: GetWeekAwarenessUseCase
) : ViewModel() {

    private val _report = MutableStateFlow<WeeklyReportData?>(null)
    val report: StateFlow<WeeklyReportData?> = _report

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _report.value = getWeekAwareness()
            } finally {
                _loading.value = false
            }
        }
    }
}
