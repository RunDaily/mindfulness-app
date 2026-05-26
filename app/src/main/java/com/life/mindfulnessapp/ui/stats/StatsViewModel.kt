package com.life.mindfulnessapp.ui.stats

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getLastWeekRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getWeekRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getYesterdayRange
import com.life.mindfulnessapp.domain.model.AppDetailStats
import com.life.mindfulnessapp.domain.model.DailyStats
import com.life.mindfulnessapp.domain.model.GlobalDayStats
import com.life.mindfulnessapp.domain.model.WeekTrendStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val systemUsageRepository: SystemUsageRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0) // 0=今日 1=本周
    val selectedTab: StateFlow<Int> = _selectedTab

    // ── 今日原始记录 ──────────────────────────────────────────────────────────

    val todayRecords: StateFlow<List<UsageRecordEntity>> = flow {
        val (start, end) = getDayRange(System.currentTimeMillis())
        usageRecordRepository.getDayRecords(start, end).collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 本周原始记录 ──────────────────────────────────────────────────────────

    val weekRecords: StateFlow<List<UsageRecordEntity>> = flow {
        val (start, end) = getWeekRange(System.currentTimeMillis())
        usageRecordRepository.getWeekRecords(start, end).collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 本周每日统计（用于柱状图）────────────────────────────────────────────

    val weekDailyStats: StateFlow<List<DailyStats>> = flow {
        val now = System.currentTimeMillis()
        val (weekStart, weekEnd) = getWeekRange(now)
        usageRecordRepository.getWeekRecords(weekStart, weekEnd).collect { records ->
            val stats = buildWeekDailyStats(records, weekStart)
            emit(stats)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 今日全局统计（时段热力图 + App 占比）────────────────────────────────

    private val _globalDayStats = MutableStateFlow<GlobalDayStats?>(null)
    val globalDayStats: StateFlow<GlobalDayStats?> = _globalDayStats

    // ── 本周 vs 上周趋势对比 ──────────────────────────────────────────────────

    private val _weekTrend = MutableStateFlow<WeekTrendStats?>(null)
    val weekTrend: StateFlow<WeekTrendStats?> = _weekTrend

    // ── 今日各 App 详情列表 ──────────────────────────────────────────────────

    private val _todayAppDetails = MutableStateFlow<List<AppDetailStats>>(emptyList())
    val todayAppDetails: StateFlow<List<AppDetailStats>> = _todayAppDetails

    // ── 当前选中 App 详情（跳转详情页使用）──────────────────────────────────

    private val _selectedAppDetail = MutableStateFlow<AppDetailStats?>(null)
    val selectedAppDetail: StateFlow<AppDetailStats?> = _selectedAppDetail

    /** 当前选中 App 的真实图标 */
    private val _selectedAppIcon = MutableStateFlow<Drawable?>(null)
    val selectedAppIcon: StateFlow<Drawable?> = _selectedAppIcon

    /** 当前选中 App 是否已被卸载（仍保留在监控列表中）*/
    private val _selectedAppIsUninstalled = MutableStateFlow(false)
    val selectedAppIsUninstalled: StateFlow<Boolean> = _selectedAppIsUninstalled

    /** 当前选中 App 的 entity（用于详情页操作） */
    private val _selectedAppEntity = MutableStateFlow<AppLimitEntity?>(null)
    val selectedAppEntity: StateFlow<AppLimitEntity?> = _selectedAppEntity

    /**
     * 近 30 天每日使用时长（秒），Map<dateLabel(yyyy-MM-dd), seconds>
     * 用于详情页日历热力图
     */
    private val _last30DayUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val last30DayUsage: StateFlow<Map<String, Long>> = _last30DayUsage

    /** 受监控的 App 列表（含真实图标，供组件网格页使用）*/
    val monitoredAppsWithIcon: StateFlow<List<android.graphics.drawable.Drawable?>> =
        appLimitRepository.getEnabledAppLimits()
            .map { limits ->
                withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    limits.map { limit ->
                        try { pm.getApplicationIcon(limit.packageName) }
                        catch (e: android.content.pm.PackageManager.NameNotFoundException) { null }
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 初始化加载 ────────────────────────────────────────────────────────────

    init {
        // 监听今日记录变化，实时重算所有衍生数据
        viewModelScope.launch {
            todayRecords.collectLatest { records ->
                refreshTodayDerivedStats(records)
            }
        }
        // 监听本周记录变化，实时重算趋势
        viewModelScope.launch {
            weekRecords.collectLatest {
                refreshWeekTrend()
            }
        }
        // 监听被监控 app 列表变化，重新刷新组件网格（确保新添加的 app 立即出现）
        viewModelScope.launch {
            appLimitRepository.getAllAppLimits().collectLatest {
                refreshTodayDerivedStats(todayRecords.value)
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    /** 点击某个 App 查看详情 */
    fun selectAppForDetail(packageName: String) {
        viewModelScope.launch {
            val detail = buildAppDetailStats(packageName)
            _selectedAppDetail.value = detail
            _selectedAppEntity.value = appLimitRepository.getLimit(packageName)
            val (icon, isUninstalled) = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val appIcon = try { pm.getApplicationIcon(packageName) } catch (e: PackageManager.NameNotFoundException) { null }
                val uninstalled = try { pm.getApplicationInfo(packageName, 0); false } catch (e: PackageManager.NameNotFoundException) { true }
                appIcon to uninstalled
            }
            _selectedAppIcon.value = icon
            _selectedAppIsUninstalled.value = isUninstalled
            // 加载近 30 天每日使用时长（日历热力图用）
            _last30DayUsage.value = buildLast30DayUsage(packageName)
        }
    }

    fun clearSelectedApp() {
        _selectedAppDetail.value = null
        _selectedAppIcon.value = null
        _selectedAppIsUninstalled.value = false
        _selectedAppEntity.value = null
        _last30DayUsage.value = emptyMap()
    }

    /** 详情页修改时限 */
    fun updateLimitFromDetail(
        packageName: String,
        newDailyMinutes: Int,
        newWeeklyMinutes: Int,
        timeLimitEnabled: Boolean? = null,
        overTimeMessage: String? = null
    ) {
        viewModelScope.launch {
            val existing = appLimitRepository.getLimit(packageName) ?: return@launch
            appLimitRepository.saveAppLimit(
                existing.copy(
                    dailyLimitMinutes = newDailyMinutes,
                    weeklyLimitMinutes = newWeeklyMinutes,
                    timeLimitEnabled = timeLimitEnabled ?: existing.timeLimitEnabled,
                    overTimeMessage = overTimeMessage ?: existing.overTimeMessage
                )
            )
            _selectedAppEntity.value = appLimitRepository.getLimit(packageName)
            val detail = buildAppDetailStats(packageName)
            _selectedAppDetail.value = detail
        }
    }

    /** 更新某条使用记录的备注，传入 null 表示清空 */
    fun updateRecordNote(recordId: Long, note: String?) {
        viewModelScope.launch {
            usageRecordRepository.updateNote(recordId, note?.trim()?.ifBlank { null })
        }
    }

    /** 详情页删除监控，完成后回调导航 */
    fun removeFromMonitorAndNavigateBack(packageName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            appLimitRepository.deleteAppLimit(packageName)
            clearSelectedApp()
            onDone()
        }
    }


    // ── 私有构建方法 ──────────────────────────────────────────────────────────

    private suspend fun refreshTodayDerivedStats(records: List<UsageRecordEntity>) {
        val now = System.currentTimeMillis()
        val (dayStart, dayEnd) = getDayRange(now)

        // 1. 所有被监控 App 列表
        val allLimits = appLimitRepository.getAllLimitsOnce()

        // 2. 各 App 系统真实今日时长（用于全局统计 + 接近上限判断）
        val appTotalMap = allLimits.associate { limit ->
            limit.packageName to systemUsageRepository.getTodayUsageSeconds(limit.packageName)
        }
        val totalAll = appTotalMap.values.sum().takeIf { it > 0 } ?: 1L
        val appShareRatios = appTotalMap.mapValues { it.value.toFloat() / totalAll }

        // 3. 接近上限数量（基于系统真实时长）
        val limitMap = allLimits.associate { it.packageName to it.dailyLimitMinutes * 60L }
        val nearLimitCount = limitMap.count { (pkg, limitSec) ->
            limitSec > 0 && (appTotalMap[pkg] ?: 0L).toFloat() / limitSec >= 0.8f
        }

        // 4. 全局热力图（系统真实数据，所有监控 App 合并）
        val globalHourlyMap = LongArray(24) { 0L }
        allLimits.forEach { limit ->
            val dist = systemUsageRepository.getHourlyDistribution(limit.packageName, dayStart, dayEnd)
            dist.forEach { h -> globalHourlyMap[h.hour] += h.totalSeconds }
        }
        val hourlyDist = globalHourlyMap.mapIndexed { hour, sec ->
            com.life.mindfulnessapp.data.db.dao.HourlyUsage(hour = hour, totalSeconds = sec)
        }.filter { it.totalSeconds > 0 }

        _globalDayStats.value = GlobalDayStats(
            totalSeconds = appTotalMap.values.sum(),
            activeAppCount = appTotalMap.count { it.value > 0 },
            nearLimitCount = nearLimitCount,
            hourlyDistribution = hourlyDist,
            appShareRatios = appShareRatios
        )

        // 4. 各 App 详情列表
        // 先处理今日有记录的 app
        val appGroups = records.groupBy { it.packageName }
        val detailsFromRecords = appGroups.map { (pkg, recs) ->
            buildAppDetailStatsFromRecords(pkg, recs, dayStart, dayEnd)
        }
        // 再补充所有被监控但今日还没有使用记录的 app（让它们也出现在组件页）
        val allMonitoredLimits = appLimitRepository.getAllLimitsOnce()
        val recordedPackages = appGroups.keys
        val detailsFromMonitored = allMonitoredLimits
            .filter { it.packageName !in recordedPackages }
            .map { limit ->
                buildAppDetailStatsFromRecords(limit.packageName, emptyList(), dayStart, dayEnd)
            }
        val details = (detailsFromRecords + detailsFromMonitored)
            .sortedByDescending { it.todaySeconds }
        _todayAppDetails.value = details
    }

    private suspend fun refreshWeekTrend() {
        val now = System.currentTimeMillis()
        val (thisWeekStart, thisWeekEnd) = getWeekRange(now)
        val (lastWeekStart, lastWeekEnd) = getLastWeekRange(now)

        val thisWeekRecords = usageRecordRepository.getWeekRecords(thisWeekStart, thisWeekEnd).first()
        val lastWeekRecords = usageRecordRepository.getWeekRecords(lastWeekStart, lastWeekEnd).first()

        val thisWeekDaily = buildDailyTotals(thisWeekRecords, thisWeekStart)
        val lastWeekDaily = buildDailyTotals(lastWeekRecords, lastWeekStart)

        _weekTrend.value = WeekTrendStats(
            thisWeekDailySeconds = thisWeekDaily,
            lastWeekDailySeconds = lastWeekDaily,
            thisWeekTotal = thisWeekDaily.sum(),
            lastWeekTotal = lastWeekDaily.sum()
        )
    }

    /** 根据包名构建 AppDetailStats（用于详情页，实时查询数据库）*/
    private suspend fun buildAppDetailStats(packageName: String): AppDetailStats {
        val now = System.currentTimeMillis()
        val (dayStart, dayEnd) = getDayRange(now)

        val records = usageRecordRepository
            .getAppRecordsByPeriod(packageName, dayStart, dayEnd)
            .first()

        return buildAppDetailStatsFromRecords(packageName, records, dayStart, dayEnd)
    }

    private suspend fun buildAppDetailStatsFromRecords(
        packageName: String,
        records: List<UsageRecordEntity>,
        dayStart: Long,
        dayEnd: Long
    ): AppDetailStats {
        val now = System.currentTimeMillis()
        val appName = records.firstOrNull()?.packageName?.substringAfterLast(".") ?: packageName

        // 从数据库获取 appName（取第一条记录的包名或从 limits 中查）
        val limitEntity = appLimitRepository.getLimit(packageName)
        val realAppName = limitEntity?.appName ?: appName

        // ── 自有数据库记录（用于打开次数、目的统计等行为数据）────────────────
        val openCount = records.size
        val avgSession = if (openCount > 0) records.sumOf { it.durationSeconds } / openCount else 0L
        val longestSec = records.maxOfOrNull { it.durationSeconds } ?: 0L
        val purposefulCount = records.count { it.purpose != null }

        // ── 系统真实使用时长（替代自有数据库时长）───────────────────────────
        val todaySec = systemUsageRepository.getTodayUsageSeconds(packageName)
        val weekSec = systemUsageRepository.getWeekUsageSeconds(packageName)
        val yesterdaySec = systemUsageRepository.getYesterdayUsageSeconds(packageName)

        // ── 系统真实热力图（今日按小时分布）──────────────────────────────────
        val hourlyDist = systemUsageRepository.getHourlyDistribution(packageName, dayStart, dayEnd)

        val dailyLimitSec = (limitEntity?.dailyLimitMinutes ?: 60) * 60L
        val weeklyLimitSec = (limitEntity?.weeklyLimitMinutes ?: 0) * 60L

        // ── 本周每日数据（系统真实数据）──────────────────────────────────────
        val weekDailySeconds = systemUsageRepository.getWeekDailyUsageSeconds(packageName)

        // ── 本周自有记录（用于记录 Tab 展示）─────────────────────────────────
        val (weekStart, weekEnd) = getWeekRange(now)
        val weekRecs = usageRecordRepository.getAppRecordsByPeriod(packageName, weekStart, weekEnd).first()
        val weekOpenCount = weekRecs.size

        return AppDetailStats(
            packageName = packageName,
            appName = realAppName,
            todaySeconds = todaySec,
            weekSeconds = weekSec,
            dailyLimitSeconds = dailyLimitSec,
            weeklyLimitSeconds = weeklyLimitSec,
            todayOpenCount = openCount,
            avgSessionSeconds = avgSession,
            longestSessionSeconds = longestSec,
            hourlyDistribution = hourlyDist,
            purposefulOpenCount = purposefulCount,
            timeLimitEnabled = limitEntity?.timeLimitEnabled ?: true,
            overTimeMessage = limitEntity?.overTimeMessage ?: "",
            yesterdaySeconds = yesterdaySec,
            weekDailySeconds = weekDailySeconds,
            weekOpenCount = weekOpenCount,
            todayRecords = records.sortedByDescending { it.startTime },
            weekRecords = weekRecs.sortedByDescending { it.startTime }
        )
    }

    private fun buildWeekDailyStats(
        records: List<UsageRecordEntity>,
        weekStartMs: Long
    ): List<DailyStats> {
        val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val result = mutableListOf<DailyStats>()

        for (i in 0..6) {
            val dayStart = weekStartMs + i * 24 * 60 * 60 * 1000L
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L

            val dayRecords = records.filter { it.startTime >= dayStart && it.startTime < dayEnd }
            val appBreakdown = dayRecords.groupBy { it.packageName }
                .mapValues { entry -> entry.value.sumOf { it.durationSeconds } }
            val total = dayRecords.sumOf { it.durationSeconds }

            result.add(
                DailyStats(
                    dateLabel = dayLabels[i],
                    dateMs = dayStart,
                    totalSeconds = total,
                    appBreakdown = appBreakdown
                )
            )
        }
        return result
    }

    /** 按周起点把记录映射到 7 天的每日总量列表 */
    private fun buildDailyTotals(
        records: List<UsageRecordEntity>,
        weekStartMs: Long
    ): List<Long> {
        return (0..6).map { i ->
            val dayStart = weekStartMs + i * 24 * 60 * 60 * 1000L
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L
            records.filter { it.startTime in dayStart until dayEnd }.sumOf { it.durationSeconds }
        }
    }

    /**
     * 构建近 30 天每日使用秒数 Map，key 为 "yyyy-MM-dd"。
     * 用于详情页的日历热力图，数据来源为系统 UsageStatsManager。
     */
    private suspend fun buildLast30DayUsage(packageName: String): Map<String, Long> {
        return systemUsageRepository.getLast30DayUsageMap(packageName)
    }
}
