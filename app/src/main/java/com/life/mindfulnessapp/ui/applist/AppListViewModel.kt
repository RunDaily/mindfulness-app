package com.life.mindfulnessapp.ui.applist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val appLimitRepository: AppLimitRepository
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredApps: StateFlow<List<AppInfo>> get() = _apps

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _apps.value = getInstalledAppsUseCase()
            _isLoading.value = false
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            val allApps = getInstalledAppsUseCase()
            _apps.value = if (query.isBlank()) {
                allApps
            } else {
                allApps.filter {
                    it.appName.contains(query, ignoreCase = true) ||
                            it.packageName.contains(query, ignoreCase = true)
                }
            }
        }
    }

    fun addToMonitor(
        appInfo: AppInfo,
        dailyLimitMinutes: Int,
        weeklyLimitMinutes: Int,
        timeLimitEnabled: Boolean = true,
        overTimeMessage: String = ""
    ) {
        viewModelScope.launch {
            appLimitRepository.saveAppLimit(
                AppLimitEntity(
                    packageName = appInfo.packageName,
                    appName = appInfo.appName,
                    dailyLimitMinutes = dailyLimitMinutes,
                    weeklyLimitMinutes = weeklyLimitMinutes,
                    isEnabled = true,
                    timeLimitEnabled = timeLimitEnabled,
                    overTimeMessage = overTimeMessage
                )
            )
            loadApps()
        }
    }

    fun removeFromMonitor(packageName: String) {
        viewModelScope.launch {
            appLimitRepository.deleteAppLimit(packageName)
            loadApps()
        }
    }

}
