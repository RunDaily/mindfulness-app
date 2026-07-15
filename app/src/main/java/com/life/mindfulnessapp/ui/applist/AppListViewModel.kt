package com.life.mindfulnessapp.ui.applist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * 单日使用数据，用于弹窗柱状图展示
 * @param dayOffset 相对于今天的天数偏移（0=今天，-1=昨天，...，-13=14天前）
 * @param dateLabel 显示标签，如 "6/5"
 * @param totalMinutes 当天使用分钟数
 */
data class DailyUsageBar(
    val dayOffset: Int,
    val dateLabel: String,
    val totalMinutes: Float
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val appLimitRepository: AppLimitRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val vipRepository: VipRepository
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredApps: StateFlow<List<AppInfo>> get() = _apps

    // ── VIP 状态 ──────────────────────────────────────────────────────────────

    /** 实时 VIP 等级 */
    val vipLevel: StateFlow<Int> = vipRepository.vipLevel

    /** 当前监控 App 数量（已监控的子集） */
    private val _monitoredCount = MutableStateFlow(0)
    val monitoredCount: StateFlow<Int> = _monitoredCount

    /**
     * 是否已达免费版 App 上限。
     * 免费公测期（FREE_PERIOD_ENABLED = true）时始终为 false，不触发限制弹窗。
     */
    val isAtFreeLimit: StateFlow<Boolean> = combine(
        _monitoredCount,
        vipRepository.vipLevel
    ) { count, level ->
        !AppPreferences.FREE_PERIOD_ENABLED && level <= 0 && count >= AppPreferences.FREE_MONITOR_LIMIT
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 触发 VIP 升级引导弹窗 */
    private val _showVipUpgradeDialog = MutableStateFlow(false)
    val showVipUpgradeDialog: StateFlow<Boolean> = _showVipUpgradeDialog

    fun dismissVipUpgradeDialog() { _showVipUpgradeDialog.value = false }

    // ── 弹窗：两周使用柱状图 ──────────────────────────────────────────────────

    /** 正在加载某 App 的两周使用数据 */
    private val _biweeklyLoading = MutableStateFlow(false)
    val biweeklyLoading: StateFlow<Boolean> = _biweeklyLoading

    /** 最近两周（14天）每日使用数据，按日期从旧到新排列 */
    private val _biweeklyData = MutableStateFlow<List<DailyUsageBar>>(emptyList())
    val biweeklyData: StateFlow<List<DailyUsageBar>> = _biweeklyData

    /**
     * 加载指定 App 最近 14 天的每日使用数据（从 DB 的 usage_records 统计）
     */
    fun loadBiweeklyUsage(packageName: String) {
        viewModelScope.launch {
            _biweeklyLoading.value = true
            _biweeklyData.value = emptyList()

            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            // 构建 14 天的数据列表（从 -13 到 0，即最旧到今天）
            val bars = (13 downTo 0).map { daysAgo ->
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                val dayStart = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val dayEnd = cal.timeInMillis

                val totalSeconds = usageRecordRepository
                    .getMonthDbUsageMap(packageName, dayStart, dayEnd)
                    .values.firstOrNull() ?: 0L

                // 日期标签：月/日
                val labelCal = Calendar.getInstance().apply { timeInMillis = dayStart }
                val m = labelCal.get(Calendar.MONTH) + 1
                val d = labelCal.get(Calendar.DAY_OF_MONTH)
                DailyUsageBar(
                    dayOffset = -daysAgo,
                    dateLabel = "$m/$d",
                    totalMinutes = totalSeconds / 60f
                )
            }
            _biweeklyData.value = bars
            _biweeklyLoading.value = false
        }
    }

    /** 清空两周数据（弹窗关闭时调用，避免下次打开闪旧数据） */
    fun clearBiweeklyData() {
        _biweeklyData.value = emptyList()
        _biweeklyLoading.value = false
    }

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val allApps = getInstalledAppsUseCase()
            _apps.value = allApps
            _monitoredCount.value = allApps.count { it.isMonitored }
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
            _monitoredCount.value = allApps.count { it.isMonitored }
        }
    }

    /**
     * 添加 App 到监控列表，带 VIP 数量门禁。
     * @return true=成功添加，false=触发免费版上限
     */
    fun addToMonitor(
        appInfo: AppInfo,
        dailyLimitMinutes: Int,
        weeklyLimitMinutes: Int,
        timeLimitEnabled: Boolean = true,
        overTimeMessage: String = ""
    ): Boolean {
        // 门禁：免费版且已达 3 个上限
        if (!vipRepository.canAddMoreApps(_monitoredCount.value)) {
            _showVipUpgradeDialog.value = true
            return false
        }
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
        return true
    }

    fun removeFromMonitor(packageName: String) {
        viewModelScope.launch {
            appLimitRepository.deleteAppLimit(packageName)
            loadApps()
        }
    }

}
