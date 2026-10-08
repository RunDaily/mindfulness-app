package com.life.mindfulnessapp.ui.features

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage
import com.life.mindfulnessapp.domain.model.ExploreChartMetric
import com.life.mindfulnessapp.domain.model.SystemUsageDayDetail
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreAppDetailUi(
    val app: AppInfo? = null,
    val completeDays: List<SystemUsageDayDetail> = emptyList(),
    val weekUsage: AppWeeklySystemUsage = AppWeeklySystemUsage.Zero,
    val chartLoading: Boolean = true,
    val hasUsagePermission: Boolean = true,
    val chartMetric: ExploreChartMetric = ExploreChartMetric.Duration,
    val selectedDayStartMs: Long? = null
) {
    val selectedDay: SystemUsageDayDetail?
        get() {
            val key = selectedDayStartMs ?: return defaultSelectedDay
            return completeDays.firstOrNull { it.dayStartMs == key } ?: defaultSelectedDay
        }

    val defaultSelectedDay: SystemUsageDayDetail?
        get() = completeDays.lastOrNull()
}

@HiltViewModel
class ExploreAppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemUsageRepository: SystemUsageRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val checkPermissionsUseCase: CheckPermissionsUseCase
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle["packageName"])

    private val _ui = MutableStateFlow(ExploreAppDetailUi())
    val ui: StateFlow<ExploreAppDetailUi> = _ui.asStateFlow()

    init {
        load()
    }

    fun setChartMetric(metric: ExploreChartMetric) {
        _ui.value = _ui.value.copy(chartMetric = metric)
    }

    fun selectDay(dayStartMs: Long) {
        _ui.value = _ui.value.copy(selectedDayStartMs = dayStartMs)
    }

    fun load() {
        viewModelScope.launch {
            val hasPerm = checkPermissionsUseCase().hasUsageStats
            val app = getInstalledAppsUseCase.getApp(packageName)
                ?: AppInfo(
                    packageName = packageName,
                    appName = packageName.substringAfterLast('.'),
                    icon = null
                )
            if (!hasPerm) {
                _ui.value = ExploreAppDetailUi(
                    app = app,
                    chartLoading = false,
                    hasUsagePermission = false
                )
                return@launch
            }
            _ui.value = _ui.value.copy(
                app = app,
                chartLoading = true,
                hasUsagePermission = true
            )
            val detail = systemUsageRepository.getExploreAppUsageDetail(packageName)
            val defaultDay = detail.completeDays.lastOrNull()?.dayStartMs
            _ui.value = ExploreAppDetailUi(
                app = app,
                completeDays = detail.completeDays,
                weekUsage = detail.weekUsage,
                chartLoading = false,
                hasUsagePermission = true,
                chartMetric = _ui.value.chartMetric,
                selectedDayStartMs = defaultDay
            )
        }
    }
}
