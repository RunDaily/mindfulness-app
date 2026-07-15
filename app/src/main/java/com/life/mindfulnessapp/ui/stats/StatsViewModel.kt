package com.life.mindfulnessapp.ui.stats

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getLastWeekRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getWeekRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getYesterdayRange
import com.life.mindfulnessapp.domain.model.AppDetailStats
import com.life.mindfulnessapp.domain.model.DailyAppStat
import com.life.mindfulnessapp.domain.model.DailyReportData
import com.life.mindfulnessapp.domain.model.DailyStats
import com.life.mindfulnessapp.domain.model.GlobalDayStats
import com.life.mindfulnessapp.domain.model.OverviewDaySummary
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
    private val appLimitRepository: AppLimitRepository
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
     * 日历当前查看的月份（yyyy-MM），默认当月
     */
    private val _calendarMonth = MutableStateFlow(
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
            .format(java.util.Date())
    )
    val calendarMonth: StateFlow<String> = _calendarMonth

    /**
     * 当前日历月份每日本地数据库记录时长，Map<yyyy-MM-dd, seconds>
     */
    private val _calendarMonthDbUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val calendarMonthDbUsage: StateFlow<Map<String, Long>> = _calendarMonthDbUsage

    /**
     * 该 App 被加入监控的日期，格式 "yyyy-MM-dd"
     * 用于日历特殊标记
     */
    private val _addedDateKey = MutableStateFlow<String?>(null)
    val addedDateKey: StateFlow<String?> = _addedDateKey

    /**
     * 日历当前选中的日期（yyyy-MM-dd），null 表示未选中
     */
    private val _selectedCalendarDate = MutableStateFlow<String?>(null)
    val selectedCalendarDate: StateFlow<String?> = _selectedCalendarDate

    /**
     * 当前日历选中日期对应的使用记录列表（来自本地数据库，按时间正序）
     */
    private val _selectedDayRecords = MutableStateFlow<List<UsageRecordEntity>>(emptyList())
    val selectedDayRecords: StateFlow<List<UsageRecordEntity>> = _selectedDayRecords

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
            // 初始化日历月份为当月，并加载当月用量
            val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            val monthKey = todayKey.substring(0, 7) // yyyy-MM
            _calendarMonth.value = monthKey
            val (monthStartMs, monthEndMs) = getMonthRange(monthKey)
            _calendarMonthDbUsage.value = usageRecordRepository.getMonthDbUsageMap(packageName, monthStartMs, monthEndMs)
            // 记录加入监控的日期
            val limitEntity = appLimitRepository.getLimit(packageName)
            _addedDateKey.value = limitEntity?.createdAt?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it))
            }
            // 默认选中今天
            _selectedCalendarDate.value = todayKey
            _selectedDayRecords.value = loadDayRecords(packageName, todayKey)
        }
    }

    fun clearSelectedApp() {
        _selectedAppDetail.value = null
        _selectedAppIcon.value = null
        _selectedAppIsUninstalled.value = false
        _selectedAppEntity.value = null
        _calendarMonthDbUsage.value = emptyMap()
        _addedDateKey.value = null
        _selectedCalendarDate.value = null
        _selectedDayRecords.value = emptyList()
    }

    /**
     * 用户点击日历某一天，切换选中日期并加载对应记录
     */
    fun selectCalendarDate(dateKey: String) {
        val packageName = _selectedAppDetail.value?.packageName ?: return
        viewModelScope.launch {
            _selectedCalendarDate.value = dateKey
            _selectedDayRecords.value = loadDayRecords(packageName, dateKey)
        }
    }

    /**
     * 清除日历选中日期（关闭底部弹窗后调用）
     */
    fun clearSelectedCalendarDate() {
        _selectedCalendarDate.value = null
        _selectedDayRecords.value = emptyList()
    }

    /**
     * 日历切换到上一个月
     */
    fun calendarPrevMonth() {
        val packageName = _selectedAppDetail.value?.packageName ?: return
        viewModelScope.launch {
            val newMonth = shiftMonth(_calendarMonth.value, -1)
            _calendarMonth.value = newMonth
            val (startMs, endMs) = getMonthRange(newMonth)
            _calendarMonthDbUsage.value = usageRecordRepository.getMonthDbUsageMap(packageName, startMs, endMs)
        }
    }

    /**
     * 日历切换到下一个月（不超过当月）
     */
    fun calendarNextMonth() {
        val packageName = _selectedAppDetail.value?.packageName ?: return
        val todayMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
            .format(java.util.Date())
        if (_calendarMonth.value >= todayMonth) return
        viewModelScope.launch {
            val newMonth = shiftMonth(_calendarMonth.value, +1)
            _calendarMonth.value = newMonth
            val (startMs, endMs) = getMonthRange(newMonth)
            _calendarMonthDbUsage.value = usageRecordRepository.getMonthDbUsageMap(packageName, startMs, endMs)
        }
    }

    /** 返回指定月份的起始和结束时间戳（毫秒） */
    private fun getMonthRange(monthKey: String): Pair<Long, Long> {
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance().apply {
            time = sdf.parse(monthKey)!!
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(java.util.Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    /** 将 yyyy-MM 字符串前移或后移 n 个月 */
    private fun shiftMonth(monthKey: String, delta: Int): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance().apply { time = sdf.parse(monthKey)!! }
        cal.add(java.util.Calendar.MONTH, delta)
        return sdf.format(cal.time)
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

        // 2. 各 App 今日 DB 时长（仅使用 App 自身记录）
        val appTotalMap = allLimits.associate { limit ->
            limit.packageName to usageRecordRepository.getAppTotalSeconds(limit.packageName, dayStart, dayEnd)
        }
        val totalAll = appTotalMap.values.sum().takeIf { it > 0 } ?: 1L
        val appShareRatios = appTotalMap.mapValues { it.value.toFloat() / totalAll }

        // 3. 接近上限数量（基于 DB 记录时长）
        val limitMap = allLimits.associate { it.packageName to it.dailyLimitMinutes * 60L }
        val nearLimitCount = limitMap.count { (pkg, limitSec) ->
            limitSec > 0 && (appTotalMap[pkg] ?: 0L).toFloat() / limitSec >= 0.8f
        }

        // 4. 全局热力图（DB 数据，所有监控 App 合并）
        val globalHourlyMap = LongArray(24) { 0L }
        records.forEach { rec ->
            if (allLimits.any { it.packageName == rec.packageName }) {
                val hour = java.util.Calendar.getInstance().apply {
                    timeInMillis = rec.startTime
                }.get(java.util.Calendar.HOUR_OF_DAY)
                globalHourlyMap[hour] += rec.durationSeconds
            }
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

        // ── DB 记录时长 ────────────────────────────────────────────────────
        val todaySec = usageRecordRepository.getAppTotalSeconds(packageName, dayStart, dayEnd)
        val (weekStart2, weekEnd2) = getWeekRange(now)
        val (yesterdayStart, yesterdayEnd) = getYesterdayRange(now)
        val weekSec = usageRecordRepository.getAppTotalSeconds(packageName, weekStart2, weekEnd2)
        val yesterdaySec = usageRecordRepository.getAppTotalSeconds(packageName, yesterdayStart, yesterdayEnd)

        // ── DB 热力图（今日按小时分布）────────────────────────────────────
        val hourlyMap = LongArray(24) { 0L }
        records.forEach { rec ->
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = rec.startTime
            }.get(java.util.Calendar.HOUR_OF_DAY)
            hourlyMap[hour] += rec.durationSeconds
        }
        val hourlyDist = hourlyMap.mapIndexed { hour, sec ->
            com.life.mindfulnessapp.data.db.dao.HourlyUsage(hour = hour, totalSeconds = sec)
        }.filter { it.totalSeconds > 0 }

        val dailyLimitSec = (limitEntity?.dailyLimitMinutes ?: 60) * 60L
        val weeklyLimitSec = (limitEntity?.weeklyLimitMinutes ?: 0) * 60L

        // ── 本周每日数据 + 记录（DB 数据）────────────────────────────────
        val weekRecs = usageRecordRepository.getAppRecordsByPeriod(packageName, weekStart2, weekEnd2).first()
        val weekOpenCount = weekRecs.size
        val weekDailySeconds = (0..6).map { i ->
            val ds = weekStart2 + i * 24 * 60 * 60 * 1000L
            val de = ds + 24 * 60 * 60 * 1000L
            weekRecs.filter { it.startTime in ds until de }.sumOf { it.durationSeconds }
        }

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

    // ════════════════════════════════════════════════════════════════════
    //  日报 —— 日期切换器 + 数据加载
    // ════════════════════════════════════════════════════════════════════

    /**
     * 当前日报查看的「日偏移量」（0 = 今天，-1 = 昨天，-2 = 前天…）
     * 最多往前查 29 天（数据库只保留 30 天）
     */
    private val _reportDayOffset = MutableStateFlow(0)
    val reportDayOffset: StateFlow<Int> = _reportDayOffset

    /** 日报完整数据；null 表示正在加载 */
    private val _dailyReport = MutableStateFlow<DailyReportData?>(null)
    val dailyReport: StateFlow<DailyReportData?> = _dailyReport

    /** 日报是否正在加载 */
    private val _dailyReportLoading = MutableStateFlow(false)
    val dailyReportLoading: StateFlow<Boolean> = _dailyReportLoading

    /** 触发加载/刷新日报 */
    fun loadDailyReport(offset: Int = _reportDayOffset.value) {
        _reportDayOffset.value = offset
        viewModelScope.launch {
            _dailyReportLoading.value = true
            _dailyReport.value = null
            try {
                _dailyReport.value = buildDailyReport(offset)
            } finally {
                _dailyReportLoading.value = false
            }
        }
    }

    /** 日报向前一天（更早） */
    fun dailyReportPrevDay() {
        val next = (_reportDayOffset.value - 1).coerceAtLeast(-29)
        loadDailyReport(next)
    }

    /** 日报向后一天（更近），不超过 0（今天） */
    fun dailyReportNextDay() {
        val next = (_reportDayOffset.value + 1).coerceAtMost(0)
        loadDailyReport(next)
    }

    /**
     * 构建指定日偏移量的日报数据。
     * offset = 0 → 今天；offset = -1 → 昨天；以此类推。
     */
    private suspend fun buildDailyReport(offset: Int): DailyReportData {
        val now = System.currentTimeMillis()
        val offsetMs = offset.toLong() * 24 * 60 * 60 * 1000L
        // 目标日期的任意时刻
        val targetMs = now + offsetMs
        val (dayStart, dayEnd) = getDayRange(targetMs)

        // ── 1. 当日所有记录 ────────────────────────────────────────────
        val dayRecords = usageRecordRepository.getWeekRecords(dayStart, dayEnd).first()

        // ── 2. App 维度汇总 ────────────────────────────────────────────
        val allLimits = appLimitRepository.getAllLimitsOnce()
        val limitNameMap = allLimits.associate { it.packageName to it.appName }
        val pm = context.packageManager

        val appGrouped = dayRecords.groupBy { it.packageName }
        val appSummaries = appGrouped.map { (pkg, recs) ->
            val totalSec = recs.sumOf { it.durationSeconds }
            val openCount = recs.size
            val avgSession = if (openCount > 0) totalSec / openCount else 0L
            val longestSession = recs.maxOfOrNull { it.durationSeconds } ?: 0L
            val mindfulCount = recs.count { it.purpose != null }
            // 克制退出判断：无目的、未超限、时长极短（≤20s），等同于 TimelineEvent.isInterceptedAndQuit()
            val dismissCount = recs.count { rec ->
                rec.purpose == null &&
                rec.endReason != com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.EndReason.LIMIT_REACHED &&
                rec.durationSeconds <= 20L
            }
            val appName = limitNameMap[pkg]
                ?: try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                   catch (_: Exception) { pkg.substringAfterLast(".") }
            val limitSec = (allLimits.find { it.packageName == pkg }?.dailyLimitMinutes ?: 0) * 60L
            DailyAppStat(
                packageName = pkg,
                appName = appName,
                totalSeconds = totalSec,
                openCount = openCount,
                avgSessionSeconds = avgSession,
                longestSessionSeconds = longestSession,
                mindfulOpenCount = mindfulCount,
                dismissCount = dismissCount,
                dailyLimitSeconds = limitSec
            )
        }.sortedByDescending { it.totalSeconds }

        val totalSeconds = appSummaries.sumOf { it.totalSeconds }

        // ── 3. 时段热力图（24 小时分布） ──────────────────────────────
        val hourlyMap = LongArray(24) { 0L }
        dayRecords.forEach { rec ->
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = rec.startTime
            }.get(java.util.Calendar.HOUR_OF_DAY)
            hourlyMap[hour] += rec.durationSeconds
        }

        // ── 4. 峰值时段 ────────────────────────────────────────────────
        val peakHour = hourlyMap.indices.maxByOrNull { hourlyMap[it] }
            ?.takeIf { hourlyMap[it] > 0 }

        // ── 5. 克制退出 & 有意识使用 ──────────────────────────────────
        val totalDismiss = dayRecords.count { rec ->
            rec.purpose == null &&
            rec.endReason != com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.EndReason.LIMIT_REACHED &&
            rec.durationSeconds <= 20L
        }
        val totalMindful = dayRecords.count { it.purpose != null }
        val totalOpenCount = dayRecords.size

        // ── 6. 昨日对比 ────────────────────────────────────────────────
        val (prevStart, prevEnd) = getDayRange(targetMs - 24 * 60 * 60 * 1000L)
        val prevDayRecords = usageRecordRepository.getWeekRecords(prevStart, prevEnd).first()
        val prevTotalSeconds = prevDayRecords.sumOf { it.durationSeconds }

        return DailyReportData(
            dayStartMs = dayStart,
            dayEndMs = dayEnd - 1,
            totalSeconds = totalSeconds,
            totalOpenCount = totalOpenCount,
            appSummaries = appSummaries,
            hourlyDistribution = hourlyMap.toList(),
            peakHour = peakHour,
            dismissCount = totalDismiss,
            mindfulCount = totalMindful,
            prevDayTotalSeconds = prevTotalSeconds
        )
    }

    // ════════════════════════════════════════════════════════════════════
    //  历史总览 —— 近 N 天每日汇总数据
    // ════════════════════════════════════════════════════════════════════

    /** 近30天每日汇总数据，最新（今天）在最后，最旧在最前 */
    private val _overviewDays = MutableStateFlow<List<OverviewDaySummary>>(emptyList())
    val overviewDays: StateFlow<List<OverviewDaySummary>> = _overviewDays

    /** 总览数据是否正在加载 */
    private val _overviewLoading = MutableStateFlow(false)
    val overviewLoading: StateFlow<Boolean> = _overviewLoading

    /**
     * 加载近 [days] 天（默认30天）的每日汇总数据。
     * 结果按日期升序排列（最早在前，今天在最后）。
     */
    fun loadOverview(days: Int = 30) {
        viewModelScope.launch {
            _overviewLoading.value = true
            try {
                val now = System.currentTimeMillis()
                val allLimits = appLimitRepository.getAllLimitsOnce()
                val pm = context.packageManager
                val limitNameMap = allLimits.associate { it.packageName to it.appName }
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

                val result = mutableListOf<OverviewDaySummary>()
                // 从 (days-1) 天前 到 今天，共 days 天
                for (i in (days - 1) downTo 0) {
                    val offsetMs = i.toLong() * 24 * 60 * 60 * 1000L
                    val targetMs = now - offsetMs
                    val (dayStart, dayEnd) = getDayRange(targetMs)
                    val dateKey = sdf.format(java.util.Date(dayStart))

                    // 当日所有记录
                    val dayRecords = usageRecordRepository.getWeekRecords(dayStart, dayEnd).first()

                    if (dayRecords.isEmpty()) {
                        result.add(
                            OverviewDaySummary(
                                dayStartMs = dayStart,
                                dateKey = dateKey,
                                totalSeconds = 0L,
                                activeAppCount = 0,
                                appBreakdown = emptyMap(),
                                appNames = emptyMap(),
                                totalOpenCount = 0,
                                peakHour = null,
                                dismissCount = 0,
                                mindfulCount = 0,
                            )
                        )
                        continue
                    }

                    // 按包名分组汇总
                    val appGrouped = dayRecords.groupBy { it.packageName }
                    val appBreakdown = appGrouped
                        .mapValues { (_, recs) -> recs.sumOf { it.durationSeconds } }
                        .entries
                        .sortedByDescending { it.value }
                        .associate { it.key to it.value }

                    val appNames = appGrouped.keys.associateWith { pkg ->
                        limitNameMap[pkg]
                            ?: try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                               catch (_: Exception) { pkg.substringAfterLast(".") }
                    }

                    // 高峰小时
                    val hourlyMap = LongArray(24) { 0L }
                    dayRecords.forEach { rec ->
                        val hour = java.util.Calendar.getInstance().apply {
                            timeInMillis = rec.startTime
                        }.get(java.util.Calendar.HOUR_OF_DAY)
                        hourlyMap[hour] += rec.durationSeconds
                    }
                    val peakHour = hourlyMap.indices
                        .maxByOrNull { hourlyMap[it] }
                        ?.takeIf { hourlyMap[it] > 0 }

                    // 行为统计
                    val dismissCount = dayRecords.count { rec ->
                        rec.purpose == null &&
                        rec.endReason != com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.EndReason.LIMIT_REACHED &&
                        rec.durationSeconds <= 20L
                    }
                    val mindfulCount = dayRecords.count { it.purpose != null }

                    result.add(
                        OverviewDaySummary(
                            dayStartMs = dayStart,
                            dateKey = dateKey,
                            totalSeconds = dayRecords.sumOf { it.durationSeconds },
                            activeAppCount = appGrouped.size,
                            appBreakdown = appBreakdown,
                            appNames = appNames,
                            totalOpenCount = dayRecords.size,
                            peakHour = peakHour,
                            dismissCount = dismissCount,
                            mindfulCount = mindfulCount,
                        )
                    )
                }
                _overviewDays.value = result
            } finally {
                _overviewLoading.value = false
            }
        }
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
     * 加载指定日期内该 App 的使用记录列表（来自本地数据库）
     * @param dateKey yyyy-MM-dd 格式日期字符串
     */
    private suspend fun loadDayRecords(
        packageName: String,
        dateKey: String
    ): List<UsageRecordEntity> {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val cal = java.util.Calendar.getInstance().apply { time = sdf.parse(dateKey)!! }
            val dayStart = cal.timeInMillis
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis
            usageRecordRepository.getAppDayRecordsAsc(packageName, dayStart, dayEnd)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
