package com.life.mindfulnessapp.ui.applist

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.usecase.SeedMonitorUsageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val appLimitRepository: AppLimitRepository,
    private val vipRepository: VipRepository,
    private val seedMonitorUsageUseCase: SeedMonitorUsageUseCase,
    private val systemUsageRepository: SystemUsageRepository,
    private val usageRecordRepository: UsageRecordRepository
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

    /**
     * 管理页专用：只读 Room 已启用监控 + 图标，不扫全机。
     * 顺序与首页坑位一致（sortOrder）。
     */
    val monitoredApps: StateFlow<List<AppInfo>> = appLimitRepository
        .getEnabledAppLimits()
        .map { limits -> loadMonitoredAppInfos(limits) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前监控 App 数量（挑选器扫包 / 管理页订阅 两条路径都会更新） */
    private val _monitoredCount = MutableStateFlow(0)
    val monitoredCount: StateFlow<Int> = _monitoredCount

    /**
     * 是否已达免费版 App 上限。
     * 免费公测期（FREE_PERIOD_ENABLED = true）时始终为 false，不触发限制弹窗。
     */
    val isAtFreeLimit: StateFlow<Boolean> = combine(
        monitoredApps,
        vipRepository.vipLevel,
        _monitoredCount
    ) { monitored, level, pickerCount ->
        val count = maxOf(monitored.size, pickerCount)
        !AppPreferences.FREE_PERIOD_ENABLED && level <= 0 && count >= AppPreferences.FREE_MONITOR_LIMIT
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 触发 VIP 升级引导弹窗 */
    private val _showVipUpgradeDialog = MutableStateFlow(false)
    val showVipUpgradeDialog: StateFlow<Boolean> = _showVipUpgradeDialog

    fun dismissVipUpgradeDialog() { _showVipUpgradeDialog.value = false }

    init {
        // 每周上限已下线：启动配置相关页时清掉历史值，避免继续拦截
        viewModelScope.launch {
            appLimitRepository.clearAllWeeklyLimits()
        }
    }

    /** 全机挑选器：需要扫已安装列表（管理页请用 [monitoredApps]，勿走此路径） */
    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val allApps = getInstalledAppsUseCase()
            _apps.value = allApps
            _monitoredCount.value = allApps.count { it.isMonitored }
            _isLoading.value = false
        }
    }

    /** 监控配置页只需要单个 App，避免整机扫包造成长时间 loading。 */
    fun loadApp(packageName: String) {
        viewModelScope.launch {
            val cached = _apps.value.find { it.packageName == packageName }
                ?: monitoredApps.value.find { it.packageName == packageName }
            if (cached == null) _isLoading.value = true
            val app = getInstalledAppsUseCase.getApp(packageName)
            // 单 App 加载时也刷新监控数量，供 VIP 门禁与「首次添加」哲学文案判断
            _monitoredCount.value = appLimitRepository.getEnabledPackageNames().size
            if (app != null) {
                val next = _apps.value.toMutableList()
                val index = next.indexOfFirst { it.packageName == packageName }
                if (index >= 0) next[index] = app else next.add(app)
                _apps.value = next
            }
            _isLoading.value = false
        }
        _glancePackageName.value = packageName
    }

    private val _glancePackageName = MutableStateFlow<String?>(null)

    /** 编辑页顶部：该 App 今日守住 / 有意图进入 / 时长 */
    val todayGlance: StateFlow<AppTodayGlance?> = _glancePackageName
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(null)
            } else {
                combine(
                    todayRangeFlow(),
                    appLimitRepository.getAllAppLimits()
                ) { range, limits -> range to limits.find { it.packageName == pkg } }
                    .flatMapLatest { (range, limit) ->
                        val (start, end) = range
                        usageRecordRepository.getAppRecordsByPeriod(pkg, start, end).map { records ->
                            buildTodayGlance(
                                records = records,
                                requireIntentOnOpen = limit?.requireIntentOnOpen == true
                            )
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun todayRangeFlow() = flow {
        while (true) {
            emit(getDayRange(System.currentTimeMillis()))
            delay(60_000L)
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

    /** 管理页拖拽排序：按包名顺序写回 sortOrder */
    fun reorderMonitored(orderedPackageNames: List<String>) {
        if (orderedPackageNames.isEmpty()) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                appLimitRepository.updateSortOrders(orderedPackageNames)
            }
        }
    }

    /**
     * 添加或更新 App 监控配置（可挂起，调用方应 await 后再离开页面，避免 ViewModel 销毁取消写入）。
     * - 新添加时做 VIP 数量门禁
     * - 已在监控中时仅更新配置，不受门禁影响
     * @return true=成功，false=触发免费版上限（仅新增时）
     */
    suspend fun saveMonitorConfig(
        appInfo: AppInfo,
        dailyLimitMinutes: Int,
        timeLimitEnabled: Boolean = true,
        requireIntentOnOpen: Boolean = true,
        sessionLimitEnabled: Boolean = true,
        intentQualityCheckEnabled: Boolean = false,
        intentBlockKeywordsJson: String = "",
        defaultSessionLimitMinutes: Int = 15,
        intentReviewEnabled: Boolean = false,
        overTimeMessage: String = "",
        periodLockEnabled: Boolean = false,
        periodWindowsJson: String = "",
        periodLockCommitment: String = ""
    ): Boolean {
        val alreadyMonitored = _apps.value.any { it.packageName == appInfo.packageName && it.isMonitored } ||
            appInfo.isMonitored
        if (!alreadyMonitored && !vipRepository.canAddMoreApps(_monitoredCount.value)) {
            _showVipUpgradeDialog.value = true
            return false
        }
        // 至少开启意图门 / 时长锁 / 时段锁之一
        val intentOn = requireIntentOnOpen
        val timeOn = timeLimitEnabled
        val periodOn = periodLockEnabled
        if (!intentOn && !timeOn && !periodOn) return false
        if (periodOn) {
            val windows = com.life.mindfulnessapp.domain.model.PeriodWindowsCodec.decode(periodWindowsJson)
            val commitment = periodLockCommitment.trim()
            if (windows.isEmpty()) return false
            if (commitment.length < com.life.mindfulnessapp.domain.model.PeriodLockPolicy.COMMITMENT_MIN_CHARS) {
                return false
            }
        }

        // NonCancellable：即使随后立刻 pop 销毁本页 ViewModel，写入也不会被取消
        withContext(NonCancellable) {
            val existing = appLimitRepository.getAppLimit(appInfo.packageName)
            // 新加入或从停用恢复：用系统今日/本周用量补齐「加入前」缺口，并冻结系锚前一周日均
            val shouldSeed = existing == null || !existing.isEnabled
            val sortOrder = when {
                existing != null && existing.isEnabled -> existing.sortOrder
                else -> appLimitRepository.nextSortOrder()
            }
            val now = System.currentTimeMillis()
            val (baselineAvg, baselineAt) = if (shouldSeed) {
                val total = systemUsageRepository.getPrecedingCompleteDaysUsageSeconds(
                    appInfo.packageName,
                    days = 7
                )
                (total / 7L) to now
            } else {
                (existing?.baselineDailyAvgSeconds ?: 0L) to (existing?.baselineCapturedAt ?: 0L)
            }
            appLimitRepository.saveAppLimit(
                (existing ?: AppLimitEntity(
                    packageName = appInfo.packageName,
                    appName = appInfo.appName
                )).copy(
                    appName = appInfo.appName,
                    dailyLimitMinutes = dailyLimitMinutes,
                    weeklyLimitMinutes = 0,
                    isEnabled = true,
                    timeLimitEnabled = timeOn,
                    requireIntentOnOpen = intentOn,
                    sessionLimitEnabled = sessionLimitEnabled,
                    intentQualityCheckEnabled = intentOn && intentQualityCheckEnabled,
                    intentBlockKeywordsJson = if (intentOn && intentQualityCheckEnabled) {
                        com.life.mindfulnessapp.domain.model.IntentBlockKeywords.encode(
                            com.life.mindfulnessapp.domain.model.IntentBlockKeywords.decode(
                                intentBlockKeywordsJson
                            )
                        )
                    } else {
                        ""
                    },
                    defaultSessionLimitMinutes = defaultSessionLimitMinutes.coerceIn(1, 60),
                    intentReviewEnabled = intentOn && intentReviewEnabled,
                    dailyOpenLimitEnabled = false,
                    allowPurposelessEntry = false,
                    overTimeMessage = overTimeMessage,
                    sortOrder = sortOrder,
                    periodLockEnabled = periodOn,
                    periodWindowsJson = if (periodOn) periodWindowsJson else "",
                    periodLockCommitment = if (periodOn) {
                        periodLockCommitment.trim().take(
                            com.life.mindfulnessapp.domain.model.PeriodLockPolicy.COMMITMENT_MAX_CHARS
                        )
                    } else {
                        existing?.periodLockCommitment ?: ""
                    },
                    baselineDailyAvgSeconds = baselineAvg,
                    baselineCapturedAt = baselineAt
                    // usageCovenant / remindCovenantOnOpen：MVP 不再写入；保留库内旧值供日后扩展
                )
            )
            if (shouldSeed) {
                seedMonitorUsageUseCase(appInfo.packageName)
            }
        }
        // 刷新本页缓存中的该 App（不依赖整机扫包）
        val refreshed = withContext(Dispatchers.IO) {
            getInstalledAppsUseCase.getApp(appInfo.packageName)
        }
        if (refreshed != null) {
            val next = _apps.value.toMutableList()
            val index = next.indexOfFirst { it.packageName == appInfo.packageName }
            if (index >= 0) next[index] = refreshed else next.add(refreshed)
            _apps.value = next
            _monitoredCount.value = next.count { it.isMonitored }
        }
        return true
    }

    fun removeFromMonitor(packageName: String) {
        viewModelScope.launch {
            stopMonitoring(packageName)
        }
    }

    /** 停止监控并刷新本页缓存；可供编辑页在确认后 await 再返回。 */
    suspend fun stopMonitoring(packageName: String) {
        withContext(NonCancellable) {
            appLimitRepository.deleteAppLimit(packageName)
        }
        val next = _apps.value.toMutableList()
        val index = next.indexOfFirst { it.packageName == packageName }
        if (index >= 0) {
            next[index] = next[index].copy(isMonitored = false)
            _apps.value = next
        }
        _monitoredCount.value = next.count { it.isMonitored }
    }

    private fun loadMonitoredAppInfos(limits: List<AppLimitEntity>): List<AppInfo> {
        val pm = context.packageManager
        return limits.map { limit ->
            val icon = try {
                pm.getApplicationIcon(limit.packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(limit.packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                limit.appName
            }
            val isUninstalled = try {
                pm.getApplicationInfo(limit.packageName, 0)
                false
            } catch (_: PackageManager.NameNotFoundException) {
                true
            }
            AppInfo(
                packageName = limit.packageName,
                appName = appName,
                icon = icon,
                isMonitored = true,
                dailyLimitMinutes = limit.dailyLimitMinutes,
                weeklyLimitMinutes = limit.weeklyLimitMinutes,
                timeLimitEnabled = limit.timeLimitEnabled,
                overTimeMessage = limit.overTimeMessage,
                usageCovenant = limit.usageCovenant,
                remindCovenantOnOpen = limit.remindCovenantOnOpen,
                requireIntentOnOpen = limit.requireIntentOnOpen,
                sessionLimitEnabled = limit.sessionLimitEnabled,
                intentQualityCheckEnabled = limit.intentQualityCheckEnabled,
                intentBlockKeywordsJson = limit.intentBlockKeywordsJson,
                defaultSessionLimitMinutes = limit.defaultSessionLimitMinutes,
                intentReviewEnabled = limit.intentReviewEnabled,
                dailyOpenLimitEnabled = limit.dailyOpenLimitEnabled,
                dailyOpenLimit = limit.dailyOpenLimit,
                periodLockEnabled = limit.periodLockEnabled,
                periodWindowsJson = limit.periodWindowsJson,
                periodLockCommitment = limit.periodLockCommitment,
                isUninstalled = isUninstalled
            )
        }
    }
}
