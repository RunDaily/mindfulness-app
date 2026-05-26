package com.life.mindfulnessapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(PermissionStatus(false, false, false))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    private val _isServiceRunning = MutableStateFlow(true)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    /** 每小时使用提醒开关状态（直接来自 AppPreferences 的 StateFlow） */
    val hourlyReminderEnabled: StateFlow<Boolean> = appPreferences.hourlyReminderEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 主题模式：true = 夜间，false = 日间 */
    val isDarkTheme: StateFlow<Boolean> = appPreferences.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 探索 Tab 彩蛋是否已解锁 */
    val isExploreUnlocked: StateFlow<Boolean> = appPreferences.isExploreUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 解锁探索 Tab 彩蛋 */
    fun unlockExplore() {
        appPreferences.setExploreUnlocked(true)
    }

    /** 是否开放全部拦截主题 */
    val allThemesUnlocked: StateFlow<Boolean> = appPreferences.allThemesUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 切换拦截主题开放状态 */
    fun setAllThemesUnlocked(unlocked: Boolean) {
        appPreferences.setAllThemesUnlocked(unlocked)
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            _permissionStatus.value = checkPermissionsUseCase()
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setHourlyReminderEnabled(enabled: Boolean) {
        appPreferences.setHourlyReminderEnabled(enabled)
    }

    /** 切换主题模式（true = 夜间，false = 日间）*/
    fun setDarkTheme(dark: Boolean) {
        appPreferences.setDarkTheme(dark)
    }
}
