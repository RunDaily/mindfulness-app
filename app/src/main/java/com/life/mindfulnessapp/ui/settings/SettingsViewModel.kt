package com.life.mindfulnessapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.data.repository.VipResult
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val appPreferences: AppPreferences,
    private val usageRecordRepository: UsageRecordRepository,
    private val vipRepository: VipRepository,
    private val quoteRepository: QuoteRepository
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(PermissionStatus(false, false, false))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    private val _isServiceRunning = MutableStateFlow(true)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    /** 每日简报推送开关状态（直接来自 AppPreferences 的 StateFlow） */
    val dailyBriefEnabled: StateFlow<Boolean> = appPreferences.dailyBriefEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 简报推送时间：小时（0~23） */
    val dailyBriefHour: StateFlow<Int> = appPreferences.dailyBriefHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 21)

    /** 简报推送时间：分钟（0~59） */
    val dailyBriefMinute: StateFlow<Int> = appPreferences.dailyBriefMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    fun setDailyBriefEnabled(enabled: Boolean) {
        appPreferences.setDailyBriefEnabled(enabled)
    }

    fun setDailyBriefHour(hour: Int) {
        appPreferences.setDailyBriefHour(hour)
    }

    fun setDailyBriefMinute(minute: Int) {
        appPreferences.setDailyBriefMinute(minute)
    }

    /** 切换主题模式（true = 夜间，false = 日间）*/
    fun setDarkTheme(dark: Boolean) {
        appPreferences.setDarkTheme(dark)
    }

    // ── 格言推送 ─────────────────────────────────────────────────────────────

    /** 格言推送开关 */
    val quoteReminderEnabled: StateFlow<Boolean> = appPreferences.quoteReminderEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 格言推送间隔（小时）：2 / 4 / 8 */
    val quoteReminderIntervalHours: StateFlow<Int> = appPreferences.quoteReminderIntervalHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    /** 格言推送起始时间（小时，0~23） */
    val quoteReminderStartHour: StateFlow<Int> = appPreferences.quoteReminderStartHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8)

    /** 用户收藏的格言数量（Flow，实时更新，用于判断是否达到开启门槛） */
    val favoriteCount: StateFlow<Int> = quoteRepository.getAllFavorites()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setQuoteReminderEnabled(enabled: Boolean) {
        appPreferences.setQuoteReminderEnabled(enabled)
    }

    fun setQuoteReminderIntervalHours(hours: Int) {
        appPreferences.setQuoteReminderIntervalHours(hours)
    }

    fun setQuoteReminderStartHour(hour: Int) {
        appPreferences.setQuoteReminderStartHour(hour)
    }

    // ── 清除本地数据 ────────────────────────────────────────────────────────

    private val _isClearingData = MutableStateFlow(false)
    val isClearingData: StateFlow<Boolean> = _isClearingData

    /** 清除本地全部使用记录（不影响限额设置和账号信息）*/
    fun clearLocalUsageData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isClearingData.value = true
            usageRecordRepository.deleteAllRecords()
            _isClearingData.value = false
            onDone()
        }
    }

    // ── 激活码兑换 ────────────────────────────────────────────────────────

    private val _isRedeemingCode = MutableStateFlow(false)
    /** 是否正在兑换激活码（用于显示加载状态） */
    val isRedeemingCode: StateFlow<Boolean> = _isRedeemingCode

    /**
     * 使用激活码开通会员。
     * @param code 用户输入的激活码
     * @param onResult 结果回调：(isSuccess, message)
     */
    fun redeemActivationCode(code: String, onResult: (isSuccess: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            _isRedeemingCode.value = true
            when (val result = vipRepository.redeemActivationCode(code)) {
                is VipResult.Success -> onResult(true, result.message)
                is VipResult.Error   -> onResult(false, result.message)
            }
            _isRedeemingCode.value = false
        }
    }
}
