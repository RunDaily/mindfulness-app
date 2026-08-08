package com.life.mindfulnessapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.VipRepository
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
    private val appPreferences: AppPreferences,
    private val usageRecordRepository: UsageRecordRepository,
    private val vipRepository: VipRepository
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(PermissionStatus(false, false, false))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    private val _isServiceRunning = MutableStateFlow(true)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    /** 主题模式：true = 夜间，false = 日间 */
    val isDarkTheme: StateFlow<Boolean> = appPreferences.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 实时 VIP 等级，供 UI 判断是否显示 VIP 门禁提示 */
    val vipLevel: StateFlow<Int> = vipRepository.vipLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 是否是 VIP 用户 */
    fun isVip(): Boolean = vipRepository.isVip()

    /** 加强保活开关：开启后额外运行一个独立守护前台服务 */
    val enhancedKeepAlive: StateFlow<Boolean> = appPreferences.enhancedKeepAlive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 胶囊已用时长是否显示到秒 */
    val capsuleUsedShowSeconds: StateFlow<Boolean> = appPreferences.capsuleUsedShowSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 迷你胶囊尺寸档：standard / compact，默认 standard */
    val capsuleMiniSize: StateFlow<String> = appPreferences.capsuleMiniSize
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.CAPSULE_MINI_SIZE_STANDARD
        )

    /** 胶囊停靠位置：left / center / right，默认 left */
    val capsuleDockPosition: StateFlow<String> = appPreferences.capsuleDockPosition
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.CAPSULE_DOCK_LEFT
        )

    /** 意图门离开倒计时秒数（60 / 120 / 300） */
    val awayCountdownSeconds: StateFlow<Int> = appPreferences.awayCountdownSeconds
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.DEFAULT_AWAY_COUNTDOWN_SECONDS
        )

    fun refreshPermissions() {
        viewModelScope.launch {
            _permissionStatus.value = checkPermissionsUseCase()
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    /**
     * 切换加强保活开关。
     * 调用方负责根据返回结果实际启动或停止 WatchdogForegroundService。
     */
    fun setEnhancedKeepAlive(enabled: Boolean) {
        appPreferences.setEnhancedKeepAlive(enabled)
    }

    fun setCapsuleUsedShowSeconds(enabled: Boolean) {
        appPreferences.setCapsuleUsedShowSeconds(enabled)
    }

    fun setCapsuleMiniSize(size: String) {
        appPreferences.setCapsuleMiniSize(size)
    }

    fun setCapsuleDockPosition(position: String) {
        appPreferences.setCapsuleDockPosition(position)
    }

    fun setAwayCountdownSeconds(seconds: Int) {
        appPreferences.setAwayCountdownSeconds(seconds)
    }

    /** 切换主题模式（true = 夜间，false = 日间）*/
    fun setDarkTheme(dark: Boolean) {
        appPreferences.setDarkTheme(dark)
    }

    // ── 清除本地数据 ────────────────────────────────────────────────────────

    private val _isClearingData = MutableStateFlow(false)
    val isClearingData: StateFlow<Boolean> = _isClearingData

    /** 清除本地全部使用记录（不影响限额设置）*/
    fun clearLocalUsageData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isClearingData.value = true
            usageRecordRepository.deleteAllRecords()
            _isClearingData.value = false
            onDone()
        }
    }
}
