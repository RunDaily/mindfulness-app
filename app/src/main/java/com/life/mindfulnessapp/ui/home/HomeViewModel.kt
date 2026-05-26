package com.life.mindfulnessapp.ui.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.LimitResetRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.usecase.GetUsageSummaryUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getUsageSummaryUseCase: GetUsageSummaryUseCase,
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val appLimitRepository: AppLimitRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val limitResetRepository: LimitResetRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _usageSummaries = MutableStateFlow<List<AppUsageSummary>>(emptyList())
    val usageSummaries: StateFlow<List<AppUsageSummary>> = _usageSummaries

    private val _permissionStatus = MutableStateFlow(PermissionStatus(false, false, false))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    /** 受监控的 App 列表（含真实图标，供底部导航带使用）*/
    val monitoredAppsWithIcon: StateFlow<List<AppInfo>> = appLimitRepository
        .getEnabledAppLimits()
        .map { limits -> loadAppInfoWithIcons(limits) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 仅用于统计 monitoredCount 等需要 AppLimitEntity 的地方 */
    val monitoredAppCount: StateFlow<List<AppLimitEntity>> = appLimitRepository
        .getEnabledAppLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 底部 Sheet：已安装 App 列表 + 搜索过滤 ────────────────────────────────

    /** 所有已安装 App（含是否监控状态），用于底部 Sheet 展示 */
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    private val _sheetSearchQuery = MutableStateFlow("")
    val sheetSearchQuery: StateFlow<String> = _sheetSearchQuery

    val filteredInstalledApps: StateFlow<List<AppInfo>> = combine(
        _installedApps, _sheetSearchQuery
    ) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps

    // ── 今日时间轴：合并 usage_records + limit_resets，按时间正序排列 ─────────

    private val _todayTimeline = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val todayTimeline: StateFlow<List<TimelineEvent>> = _todayTimeline

    /** 今日有意识地打开 App 的次数（即填写了使用目的的次数，purpose NOT NULL） */
    val todayMindfulCount: StateFlow<Int> = run {
        val (dayStart, dayEnd) = getDayRange(System.currentTimeMillis())
        usageRecordRepository.getDayMindfulRecords(dayStart, dayEnd)
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    init {
        loadData()
        startAutoRefresh()
        observeTodayTimeline()
    }

    fun loadData() {
        viewModelScope.launch {
            _usageSummaries.value = getUsageSummaryUseCase()
            _permissionStatus.value = checkPermissionsUseCase()
        }
    }

    /** 加载已安装 App 列表（打开 Sheet 时调用） */
    fun loadInstalledApps() {
        if (_isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            _installedApps.value = getInstalledAppsUseCase()
            _isLoadingApps.value = false
        }
    }

    fun setSheetSearchQuery(query: String) {
        _sheetSearchQuery.value = query
    }

    fun clearSheetSearch() {
        _sheetSearchQuery.value = ""
    }

    /** 将 App 加入监控（从 Sheet 选择后调用） */
    fun addToMonitor(appInfo: AppInfo, dailyMinutes: Int, weeklyMinutes: Int = 0) {
        viewModelScope.launch {
            appLimitRepository.saveAppLimit(
                AppLimitEntity(
                    packageName = appInfo.packageName,
                    appName = appInfo.appName,
                    dailyLimitMinutes = dailyMinutes,
                    weeklyLimitMinutes = weeklyMinutes,
                    isEnabled = true
                )
            )
            // 刷新安装列表（更新 isMonitored 状态）
            _installedApps.value = getInstalledAppsUseCase()
            _usageSummaries.value = getUsageSummaryUseCase()
        }
    }

    /** 从监控列表移除 App */
    fun removeFromMonitor(packageName: String) {
        viewModelScope.launch {
            appLimitRepository.deleteAppLimit(packageName)
            _installedApps.value = getInstalledAppsUseCase()
            _usageSummaries.value = getUsageSummaryUseCase()
        }
    }

    /** 更新某条使用记录的效果备注，传入 null 表示清空 */
    fun updateRecordNote(recordId: Long, note: String?) {
        viewModelScope.launch {
            usageRecordRepository.updateNote(recordId, note?.trim()?.ifBlank { null })
        }
    }

    /** 修改已监控 App 的时限 */
    fun updateAppLimit(packageName: String, newDailyMinutes: Int, newWeeklyMinutes: Int) {
        viewModelScope.launch {
            val existing = appLimitRepository.getAppLimit(packageName) ?: return@launch
            appLimitRepository.saveAppLimit(
                existing.copy(
                    dailyLimitMinutes = newDailyMinutes,
                    weeklyLimitMinutes = newWeeklyMinutes
                )
            )
            _usageSummaries.value = getUsageSummaryUseCase()
        }
    }

    /** 从 PackageManager 批量加载图标，避免多次重复读取 */
    private suspend fun loadAppInfoWithIcons(limits: List<AppLimitEntity>): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            limits.map { limit ->
                val icon = try {
                    pm.getApplicationIcon(limit.packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(limit.packageName, 0)).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    limit.appName
                }
                // 通过尝试获取 ApplicationInfo 来判断应用是否已被卸载
                val isUninstalled = try {
                    pm.getApplicationInfo(limit.packageName, 0)
                    false
                } catch (e: PackageManager.NameNotFoundException) {
                    true
                }
                AppInfo(
                    packageName = limit.packageName,
                    appName = appName,
                    icon = icon,
                    isMonitored = true,
                    dailyLimitMinutes = limit.dailyLimitMinutes,
                    weeklyLimitMinutes = limit.weeklyLimitMinutes,
                    isUninstalled = isUninstalled
                )
            }
        }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                _usageSummaries.value = getUsageSummaryUseCase()
            }
        }
    }

    /**
     * 通过 PackageManager 获取应用名称，找不到时回退到数据库存储的名称
     * 必须在 IO 线程中调用
     */
    private suspend fun resolveAppName(packageName: String): String =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                // PackageManager 找不到（已卸载），尝试从数据库中获取已存储的名称
                appLimitRepository.getAppLimit(packageName)?.appName
                    ?: packageName.substringAfterLast(".")
            }
        }

    /**
     * 监听今日使用记录和重设限额记录，合并成统一时间轴
     * 按 timeMs 正序排列（最早的在最上面），与时间流方向一致
     */
    private fun observeTodayTimeline() {
        viewModelScope.launch {
            val (dayStart, dayEnd) = getDayRange(System.currentTimeMillis())

            val usageFlow = usageRecordRepository.getDayRecords(dayStart, dayEnd)
            val resetFlow = limitResetRepository.getResetsByPeriod(dayStart, dayEnd)

            combine(usageFlow, resetFlow) { usageRecords, resetRecords ->
                usageRecords to resetRecords
            }.collect { (usageRecords, resetRecords) ->
                val usageEvents = withContext(Dispatchers.IO) {
                    usageRecords.map { record ->
                        TimelineEvent.UsageEvent(
                            packageName = record.packageName,
                            appName = resolveAppName(record.packageName),
                            startTime = record.startTime,
                            endTime = record.endTime,
                            durationSeconds = record.durationSeconds,
                            endReason = record.endReason,
                            purpose = record.purpose,
                            recordId = record.id,
                            note = record.note
                        )
                    }
                }
                val resetEvents = resetRecords.map { reset ->
                    TimelineEvent.LimitResetEvent(
                        packageName = reset.packageName,
                        appName = reset.appName,
                        resetTime = reset.resetTime,
                        oldDailyLimitMinutes = reset.oldDailyLimitMinutes,
                        newDailyLimitMinutes = reset.newDailyLimitMinutes,
                        oldWeeklyLimitMinutes = reset.oldWeeklyLimitMinutes,
                        newWeeklyLimitMinutes = reset.newWeeklyLimitMinutes,
                        resetId = reset.id
                    )
                }
                // 合并后按时间倒序（最新的在最上面）
                _todayTimeline.value = (usageEvents + resetEvents).sortedByDescending { it.timeMs }
            }
        }
    }
}
