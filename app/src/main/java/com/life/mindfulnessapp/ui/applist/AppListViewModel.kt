package com.life.mindfulnessapp.ui.applist

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.plan.RulePlanStore
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.BetaAccessRepository
import com.life.mindfulnessapp.data.repository.BetaClaimResult
import com.life.mindfulnessapp.data.repository.BetaRedeemResult
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.domain.model.AppDayInsight
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppRelationInsight
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.domain.model.AppUsageOverview
import com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage
import com.life.mindfulnessapp.domain.model.BatchPickAppRow
import com.life.mindfulnessapp.domain.model.BatchPickSortMode
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.DayInsightBuilder
import com.life.mindfulnessapp.domain.model.GuardEvent
import com.life.mindfulnessapp.domain.model.GuardEventKind
import com.life.mindfulnessapp.domain.model.GuardOverview
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.model.buildAppRelationInsight
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.domain.usecase.SeedMonitorUsageUseCase
import com.life.mindfulnessapp.overlay.OverlayManager
import com.life.mindfulnessapp.service.SessionManager
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.vip.AccessGateStep
import com.life.mindfulnessapp.ui.vip.AccessGateUiState
import com.life.mindfulnessapp.util.AppNameSearch
import com.life.mindfulnessapp.util.BatchPickSorting
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val betaAccessRepository: BetaAccessRepository,
    private val appPreferences: AppPreferences,
    private val seedMonitorUsageUseCase: SeedMonitorUsageUseCase,
    private val systemUsageRepository: SystemUsageRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val monitorProfileReporter: com.life.mindfulnessapp.data.repository.MonitorProfileReporter,
    private val sessionManager: SessionManager,
    private val overlayManager: OverlayManager,
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val rulePlanStore: RulePlanStore,
    private val planBlockRepository: PlanBlockRepository
) : ViewModel() {

    /** 全量已安装列表缓存（不受搜索过滤影响） */
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _batchPickSortMode = MutableStateFlow(BatchPickSortMode.Duration)
    val batchPickSortMode: StateFlow<BatchPickSortMode> = _batchPickSortMode.asStateFlow()

    private val _batchPickUsageStats = MutableStateFlow<Map<String, AppWeeklySystemUsage>>(emptyMap())
    val batchPickUsageStats: StateFlow<Map<String, AppWeeklySystemUsage>> = _batchPickUsageStats.asStateFlow()

    private val _batchPickUsageLoading = MutableStateFlow(false)
    val batchPickUsageLoading: StateFlow<Boolean> = _batchPickUsageLoading.asStateFlow()

    val batchPickApps: StateFlow<List<BatchPickAppRow>> = combine(
        _apps,
        _batchPickUsageStats,
        _batchPickSortMode
    ) { apps, stats, sortMode ->
        BatchPickSorting.sort(
            BatchPickSorting.toRows(apps, stats),
            sortMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredApps: StateFlow<List<AppInfo>> get() = _apps

    private val _permissionStatus = MutableStateFlow(PermissionStatus(false, false, false))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    fun refreshPermissions() {
        _permissionStatus.value = checkPermissionsUseCase()
    }

    fun onPermissionPromptShown(source: String) {
        analyticsRepository.trackPermissionPrompt(source)
    }

    fun trackPermissionSkip(source: String) {
        analyticsRepository.trackPermissionSkip(source)
    }

    fun trackPermissionGrantIfNeeded(prev: PermissionStatus, next: PermissionStatus, source: String) {
        if (!prev.hasOverlay && next.hasOverlay) {
            analyticsRepository.trackPermissionGrant(HaEvents.Permission.OVERLAY, source)
        }
        if (!prev.hasUsageStats && next.hasUsageStats) {
            analyticsRepository.trackPermissionGrant(HaEvents.Permission.USAGE, source)
        }
        if (!prev.hasBatteryOptimizationIgnored && next.hasBatteryOptimizationIgnored) {
            analyticsRepository.trackPermissionGrant(HaEvents.Permission.BATTERY, source)
        }
        if (!prev.hasNotification && next.hasNotification) {
            analyticsRepository.trackPermissionGrant(HaEvents.Permission.NOTIFICATION, source)
        }
        if (!prev.hasAccessibilityKeepAlive && next.hasAccessibilityKeepAlive) {
            analyticsRepository.trackPermissionGrant(HaEvents.Permission.ACCESSIBILITY, source)
        }
    }

    fun refreshPermissions(source: String) {
        val prev = _permissionStatus.value
        val next = checkPermissionsUseCase()
        trackPermissionGrantIfNeeded(prev, next, source)
        _permissionStatus.value = next
    }

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

    /** 触发第 4 坑体系底栏（会员码 / 领码 / 会员方案） */
    private val _accessGate = MutableStateFlow(AccessGateUiState())
    val accessGate: StateFlow<AccessGateUiState> = _accessGate.asStateFlow()

    /** @deprecated 兼容旧订阅名；请用 [accessGate] */
    val showVipUpgradeDialog: StateFlow<Boolean> = _accessGate
        .map { it.visible }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun dismissVipUpgradeDialog() {
        val toast = _accessGate.value.unlockToast
        _accessGate.value = AccessGateUiState(unlockToast = toast)
    }

    fun consumeAccessUnlockToast() {
        _accessGate.update { it.copy(unlockToast = null) }
    }

    fun requestVipUpgrade() {
        val claimed = appPreferences.getClaimedInviteCode()
        val unlocked = appPreferences.isBetaUnlocked()
        val startRedeem = claimed.isNotBlank() && !unlocked
        _accessGate.value = AccessGateUiState(
            visible = true,
            step = if (startRedeem) AccessGateStep.Redeem else AccessGateStep.Gate,
            codeInput = if (!unlocked) claimed else "",
            issuedCode = if (!unlocked) claimed else ""
        )
    }

    fun onAccessCodeChange(value: String) {
        _accessGate.update {
            it.copy(
                codeInput = value.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(16),
                error = null
            )
        }
    }

    fun openAccessRedeemStep() {
        val claimed = appPreferences.getClaimedInviteCode()
        _accessGate.update {
            it.copy(
                step = AccessGateStep.Redeem,
                codeInput = it.codeInput.ifBlank { claimed },
                issuedCode = it.issuedCode.ifBlank { claimed },
                error = null,
                infoMessage = null,
                busy = false
            )
        }
    }

    fun redeemAccessCode() {
        val code = _accessGate.value.codeInput
        if (code.isBlank()) {
            _accessGate.update { it.copy(error = "请输入会员码") }
            return
        }
        viewModelScope.launch { redeemAccessInternal(code) }
    }

    fun openAccessClaimStep() {
        _accessGate.update {
            it.copy(
                step = AccessGateStep.Claim,
                error = null,
                infoMessage = null,
                busy = false
            )
        }
    }

    fun onAccessClaimChannelChange(channel: String) {
        _accessGate.update {
            it.copy(
                claimChannel = channel,
                claimContact = "",
                error = null
            )
        }
    }

    fun onAccessClaimContactChange(value: String) {
        _accessGate.update { it.copy(claimContact = value, error = null) }
    }

    fun submitAccessClaim() {
        val state = _accessGate.value
        viewModelScope.launch {
            _accessGate.update { it.copy(busy = true, error = null) }
            when (val result = betaAccessRepository.claim(state.claimChannel, state.claimContact)) {
                is BetaClaimResult.Success -> {
                    _accessGate.update {
                        it.copy(
                            issuedCode = result.code,
                            codeInput = result.code,
                            infoMessage = result.message
                        )
                    }
                    redeemAccessInternal(result.code, fromClaim = true)
                }
                is BetaClaimResult.Error -> {
                    _accessGate.update { it.copy(busy = false, error = result.message) }
                }
            }
        }
    }

    fun redeemIssuedAccessCode() {
        val code = _accessGate.value.issuedCode.ifBlank { _accessGate.value.codeInput }
        if (code.isBlank()) {
            _accessGate.update { it.copy(error = "会员码为空") }
            return
        }
        viewModelScope.launch { redeemAccessInternal(code) }
    }

    fun backToAccessGate() {
        _accessGate.update {
            it.copy(
                step = AccessGateStep.Gate,
                busy = false,
                error = null,
                infoMessage = null
            )
        }
    }

    private suspend fun redeemAccessInternal(code: String, fromClaim: Boolean = false) {
        _accessGate.update { it.copy(busy = true, error = null) }
        when (val result = betaAccessRepository.redeem(code)) {
            is BetaRedeemResult.Success -> {
                analyticsRepository.trackVipCodeRedeem(result.alreadyUnlocked)
                if (!result.alreadyUnlocked) {
                    analyticsRepository.trackBetaUnlock()
                }
                val toast = result.message.ifBlank { "会员已开通" }
                _accessGate.value = AccessGateUiState(unlockToast = toast)
            }
            is BetaRedeemResult.Error -> {
                if (fromClaim) {
                    _accessGate.update {
                        it.copy(
                            busy = false,
                            step = AccessGateStep.Issued,
                            issuedCode = code,
                            codeInput = code,
                            error = result.message,
                            infoMessage = "会员码已生成，但自动开通未完成，请手动兑换。"
                        )
                    }
                } else {
                    _accessGate.update { it.copy(busy = false, error = result.message) }
                }
            }
        }
    }

    /**
     * 今日是否已触顶（已用 ≥ 放宽前的日限额，或已达含延长的生效天花板）。
     * 用于：触顶后放宽日限额时要求呼吸代价。
     */
    suspend fun isDailyLimitHitToday(
        packageName: String,
        dailyLimitMinutes: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (dailyLimitMinutes <= 0) return@withContext false
        val used = usageRecordRepository.getDailyUsageSeconds(
            packageName,
            System.currentTimeMillis()
        )
        val live = sessionManager.currentSession.value
            ?.takeIf { it.packageName == packageName }
            ?.currentSessionSeconds
            ?: 0L
        val usedSec = com.life.mindfulnessapp.domain.model.DailyCapFacts.usedSeconds(used + live)
        val baseSec = dailyLimitMinutes * 60L
        val effectiveSec = appPreferences.effectiveDailyLimitSeconds(packageName, dailyLimitMinutes)
        com.life.mindfulnessapp.domain.model.DailyCapFacts.exhausted(usedSec, baseSec) ||
            com.life.mindfulnessapp.domain.model.DailyCapFacts.exhausted(usedSec, effectiveSec)
    }

    init {
        // 每周上限已下线：启动配置相关页时清掉历史值，避免继续拦截
        viewModelScope.launch {
            appLimitRepository.clearAllWeeklyLimits()
        }
    }

    /** 全机挑选器：需要扫已安装列表（管理页请用 [monitoredApps]，勿走此路径） */
    fun loadApps() {
        viewModelScope.launch { loadAppsInternal() }
    }

    /** 绑定页用：等待扫包结束并返回当前监控数，避免教育步误判。 */
    suspend fun loadAppsAwait(): Int = loadAppsInternal()

    private suspend fun loadAppsInternal(): Int {
        _isLoading.value = true
        return try {
            val allApps = getInstalledAppsUseCase()
            _allApps.value = allApps
            val count = allApps.count { it.isMonitored }
            _monitoredCount.value = count
            applySearchFilter(_searchQuery.value, allApps)
            count
        } finally {
            _isLoading.value = false
        }
    }

    /** 监控配置页只需要单个 App，避免整机扫包造成长时间 loading。 */
    fun loadApp(packageName: String) {
        viewModelScope.launch {
            val cached = _apps.value.find { it.packageName == packageName }
                ?: _allApps.value.find { it.packageName == packageName }
                ?: monitoredApps.value.find { it.packageName == packageName }
            if (cached == null) _isLoading.value = true
            val app = getInstalledAppsUseCase.getApp(packageName)
            // 单 App 加载时也刷新监控数量，供 VIP 门禁与「首次添加」哲学文案判断
            _monitoredCount.value = appLimitRepository.getEnabledPackageNames().size
            if (app != null) {
                fun upsert(list: List<AppInfo>): List<AppInfo> {
                    val next = list.toMutableList()
                    val index = next.indexOfFirst { it.packageName == packageName }
                    if (index >= 0) next[index] = app else next.add(app)
                    return next
                }
                _allApps.value = upsert(_allApps.value)
                applySearchFilter(_searchQuery.value, _allApps.value)
            }
            _isLoading.value = false
        }
        _glancePackageName.value = packageName
        _relationDayOffset.value = 0
    }

    private val _glancePackageName = MutableStateFlow<String?>(null)
    private val _relationDayOffset = MutableStateFlow(0)

    /** 关系页：0=今天，负数为往日 */
    fun shiftRelationDay(delta: Int) {
        _relationDayOffset.update { current ->
            (current + delta).coerceAtMost(0).coerceAtLeast(-90)
        }
    }

    fun resetRelationDay() {
        _relationDayOffset.value = 0
    }

    /** 概览月历选日 → 切到关系页对应日（0=今天） */
    fun setRelationDay(dayStartMs: Long) {
        val todayStart = getDayRange(System.currentTimeMillis()).first
        val dayMs = 24L * 60 * 60 * 1000L
        val offset = ((dayStartMs - todayStart) / dayMs).toInt()
            .coerceAtMost(0)
            .coerceAtLeast(-90)
        _relationDayOffset.value = offset
    }

    /** @deprecated 兼容旧调用，改为按日切换 */
    fun shiftRelationWeek(delta: Int) = shiftRelationDay(delta * 7)

    fun resetRelationWeek() = resetRelationDay()

    /** 编辑页顶部：该 App 今日守住 / 有意图进入 / 时长（与胶囊限额同口径） */
    val todayGlance: StateFlow<AppTodayGlance?> = _glancePackageName
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(null)
            } else {
                combine(
                    todayRangeFlow(),
                    appLimitRepository.getAllAppLimits(),
                    sessionManager.currentSession
                ) { range, limits, activeSession ->
                    Triple(range, limits.find { it.packageName == pkg }, activeSession)
                }
                    .flatMapLatest { (range, limit, activeSession) ->
                        val (start, end) = range
                        val liveSec = activeSession
                            ?.takeIf { it.packageName == pkg }
                            ?.currentSessionSeconds
                            ?: 0L
                        usageRecordRepository.getAppRecordsByPeriod(pkg, start, end).map { records ->
                            buildTodayGlance(
                                records = records,
                                requireIntentOnOpen = limit?.requireIntentOnOpen == true,
                                liveSessionSeconds = liveSec
                            )
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 意图门 App 详情「关系页」：累计英雄指标 + 选定日用量柱图 + 意图叙事。
     * 柱图取系统前台按小时分布；列表取心锚记录。切日时两者同日刷新。
     */
    val relationInsight: StateFlow<AppRelationInsight?> = combine(
        _glancePackageName,
        _relationDayOffset
    ) { pkg, dayOffset -> pkg to dayOffset }
        .flatMapLatest { (pkg, dayOffset) ->
            if (pkg == null) {
                flowOf<AppRelationInsight?>(null)
            } else {
                combine(
                    usageRecordRepository.getRecordsByApp(pkg),
                    sessionManager.currentSession,
                    todayRangeFlow()
                ) { records, activeSession, _ ->
                    Triple(records, activeSession, dayOffset)
                }.flatMapLatest { (records, activeSession, offset) ->
                    flow<AppRelationInsight?> {
                        val liveSec = activeSession
                            ?.takeIf { it.packageName == pkg }
                            ?.currentSessionSeconds
                            ?: 0L
                        val loading = buildAppRelationInsight(
                            records = records,
                            hourlySeconds = LongArray(24),
                            dayOffset = offset,
                            liveSessionSeconds = liveSec,
                            loadingHourly = true
                        )
                        emit(loading)
                        val hourlyList = withContext(Dispatchers.IO) {
                            systemUsageRepository.getHourlyDistribution(
                                packageName = pkg,
                                startMs = loading.dayStartMs,
                                endMs = loading.dayEndMs
                            )
                        }
                        val hours = LongArray(24)
                        hourlyList.forEach { item ->
                            if (item.hour in 0..23) {
                                hours[item.hour] = item.totalSeconds.coerceAtLeast(0L)
                            }
                        }
                        emit(
                            buildAppRelationInsight(
                                records = records,
                                hourlySeconds = hours,
                                dayOffset = offset,
                                liveSessionSeconds = liveSec,
                                loadingHourly = false
                            )
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 单 App 日洞见（有目的 / 守住 / 空转 / vs 昨日），与关系页同日偏移。
     */
    val appDayInsight: StateFlow<AppDayInsight?> = combine(
        _glancePackageName,
        _relationDayOffset
    ) { pkg, dayOffset -> pkg to dayOffset }
        .flatMapLatest { (pkg, dayOffset) ->
            if (pkg == null) {
                flowOf<AppDayInsight?>(null)
            } else {
                combine(
                    usageRecordRepository.getRecordsByApp(pkg),
                    sessionManager.currentSession,
                    todayRangeFlow()
                ) { records, activeSession, range ->
                    val (todayStart, todayEnd) = range
                    val dayMs = todayEnd - todayStart
                    val offset = dayOffset.coerceAtMost(0)
                    val dayStart = todayStart + offset * dayMs
                    val dayEnd = dayStart + dayMs
                    val yStart = dayStart - dayMs
                    val dayRecords = records.filter { it.startTime in dayStart until dayEnd }
                    val yRecords = records.filter { it.startTime in yStart until dayStart }
                    DayInsightBuilder.buildAppDayInsight(
                        packageName = pkg,
                        dayRecords = dayRecords,
                        yesterdayRecords = yRecords,
                        dayOffset = offset,
                        liveSession = activeSession
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 能力应用列表：各监控 App 今日轻指标（按包名）。
     * 用全日记录一次分组，避免逐 App 订阅。
     */
    val todayGlancesByPackage: StateFlow<Map<String, AppTodayGlance>> = combine(
        todayRangeFlow(),
        monitoredApps,
        sessionManager.currentSession
    ) { range, apps, activeSession ->
        Triple(range, apps, activeSession)
    }.flatMapLatest { (range, apps, activeSession) ->
        if (apps.isEmpty()) {
            flowOf(emptyMap())
        } else {
            val (start, end) = range
            usageRecordRepository.getDayRecords(start, end).map { records ->
                val byPkg = records.groupBy { it.packageName }
                apps.associate { app ->
                    val liveSec = activeSession
                        ?.takeIf { it.packageName == app.packageName }
                        ?.currentSessionSeconds
                        ?: 0L
                    app.packageName to buildTodayGlance(
                        records = byPkg[app.packageName].orEmpty(),
                        requireIntentOnOpen = app.requireIntentOnOpen,
                        liveSessionSeconds = liveSec
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun todayRangeFlow() = flow {
        while (true) {
            emit(getDayRange(System.currentTimeMillis()))
            delay(60_000L)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchFilter(query, _allApps.value)
    }

    fun setBatchPickSortMode(mode: BatchPickSortMode) {
        _batchPickSortMode.value = mode
    }

    /** 批量选 App 页：一次扫描近 7 日系统用量（时长 + 打开次数）。 */
    fun loadBatchPickUsageStats() {
        viewModelScope.launch {
            if (!checkPermissionsUseCase().hasUsageStats) {
                _batchPickUsageStats.value = emptyMap()
                return@launch
            }
            _batchPickUsageLoading.value = true
            try {
                _batchPickUsageStats.value = systemUsageRepository.getLast7CompleteDaysUsageByPackage()
            } finally {
                _batchPickUsageLoading.value = false
            }
        }
    }

    private fun applySearchFilter(query: String, source: List<AppInfo>) {
        _apps.value = if (query.isBlank()) {
            source
        } else {
            source.filter { AppNameSearch.matches(it.appName, it.packageName, query) }
        }
    }

    /** 管理页拖拽排序：按包名顺序写回 sortOrder */
    fun reorderMonitored(orderedPackageNames: List<String>) {
        if (orderedPackageNames.isEmpty()) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                appLimitRepository.updateSortOrders(orderedPackageNames)
            }
            monitorProfileReporter.trackMonitorReorder(orderedPackageNames.size)
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
        timeAwarenessEnabled: Boolean = false,
        requireIntentOnOpen: Boolean = true,
        sessionLimitEnabled: Boolean = true,
        intentQualityCheckEnabled: Boolean = false,
        intentBlockKeywordsJson: String = "",
        defaultSessionLimitMinutes: Int = 15,
        intentReviewEnabled: Boolean = false,
        overTimeMessage: String = "",
        periodLockEnabled: Boolean = false,
        periodWindowsJson: String = "",
        periodLockCommitment: String = "",
        compareEnabled: Boolean? = null,
        compareMinMinutes: Int? = null,
        dailyOpenLimitEnabled: Boolean? = null,
        dailyOpenLimit: Int? = null
    ): Boolean {
        val alreadyMonitored = _apps.value.any { it.packageName == appInfo.packageName && it.isMonitored } ||
            appInfo.isMonitored
        if (!alreadyMonitored && !vipRepository.canAddMoreApps(_monitoredCount.value)) {
            requestVipUpgrade()
            return false
        }
        // 至少开启意图门 / 时长锁 / 时段锁之一
        val intentOn = requireIntentOnOpen
        val timeOn = timeLimitEnabled
        val periodOn = periodLockEnabled
        if (!intentOn && !timeOn && !periodOn) return false
        if (periodOn) {
            val windows = com.life.mindfulnessapp.domain.model.PeriodWindowsCodec.decode(periodWindowsJson)
            if (windows.isEmpty()) return false
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
            val (baselineAvg, baselineAt, preJoinJson) = if (shouldSeed) {
                val joinDay = getDayRange(now).first
                val (avg, json) = runCatching {
                    systemUsageRepository.capturePreJoinUsageSnapshot(appInfo.packageName, joinDay)
                }.getOrElse {
                    0L to com.life.mindfulnessapp.domain.model.PreJoinUsageSnapshot.encode(
                        joinDay,
                        emptyList()
                    )
                }
                Triple(avg, now, json)
            } else {
                Triple(
                    existing?.baselineDailyAvgSeconds ?: 0L,
                    existing?.baselineCapturedAt ?: 0L,
                    existing?.preJoinUsageJson.orEmpty()
                )
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
                    timeAwarenessEnabled = false,
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
                    dailyOpenLimitEnabled = dailyOpenLimitEnabled
                        ?: existing?.dailyOpenLimitEnabled
                        ?: false,
                    dailyOpenLimit = (dailyOpenLimit
                        ?: existing?.dailyOpenLimit
                        ?: 5).coerceIn(1, AppLimitEntity.MAX_DAILY_OPEN_LIMIT),
                    allowPurposelessEntry = false,
                    overTimeMessage = overTimeMessage,
                    sortOrder = sortOrder,
                    periodLockEnabled = periodOn,
                    // 寄语已挂在各 PeriodWindow.message；App 级字段仅作旧数据兼容，新写入清空
                    periodWindowsJson = if (periodOn) {
                        com.life.mindfulnessapp.domain.model.PeriodWindowsCodec.encode(
                            com.life.mindfulnessapp.domain.model.PeriodWindowsCodec.withLegacyCommitment(
                                com.life.mindfulnessapp.domain.model.PeriodWindowsCodec.decode(
                                    periodWindowsJson
                                ),
                                periodLockCommitment.ifBlank {
                                    existing?.periodLockCommitment.orEmpty()
                                }
                            )
                        )
                    } else {
                        ""
                    },
                    periodLockCommitment = "",
                    compareEnabled = if (intentOn) {
                        compareEnabled
                            ?: existing?.compareEnabled
                            ?: ComparePolicy.DEFAULT_ENABLED
                    } else {
                        false
                    },
                    compareMinMinutes = if (intentOn) {
                        ComparePolicy.sanitizeMinMinutes(
                            compareMinMinutes
                                ?: existing?.compareMinMinutes
                                ?: ComparePolicy.DEFAULT_MIN_MINUTES
                        )
                    } else {
                        ComparePolicy.DEFAULT_MIN_MINUTES
                    },
                    shortSessionCompareEnabled = false,
                    baselineDailyAvgSeconds = baselineAvg,
                    baselineCapturedAt = baselineAt,
                    preJoinUsageJson = preJoinJson,
                    rulesUpdatedAt = now,
                    lockClonesEnabled = existing?.lockClonesEnabled ?: true,
                    blockDiscoverFeedEnabled = existing?.blockDiscoverFeedEnabled
                        ?: com.life.mindfulnessapp.domain.model.XhsExclusive.isXhsPackage(
                            appInfo.packageName
                        )
                    // usageCovenant / remindCovenantOnOpen：MVP 不再写入；保留库内旧值供日后扩展
                )
            )
            if (shouldSeed) {
                seedMonitorUsageUseCase(appInfo.packageName)
            }
            // 中途改限额时立刻刷新进行中会话，避免胶囊仍卡在旧触顶天花板
            if (sessionManager.currentSession.value?.packageName == appInfo.packageName) {
                sessionManager.refreshLiveBudget()
            }
        }
        if (alreadyMonitored) {
            val keywordsOn = intentOn && intentQualityCheckEnabled
            val keywordCount = if (keywordsOn) {
                com.life.mindfulnessapp.domain.model.IntentBlockKeywords.decode(intentBlockKeywordsJson).size
            } else {
                0
            }
            monitorProfileReporter.trackCapabilityEdit(
                app = appInfo.appName,
                pkg = appInfo.packageName,
                intent = intentOn,
                time = timeOn,
                period = periodOn,
                session = sessionLimitEnabled && intentOn,
                keywords = keywordsOn,
                dailyLimitMinutes = dailyLimitMinutes,
                defaultSessionMin = if (sessionLimitEnabled && intentOn) defaultSessionLimitMinutes else 0,
                periodWindowsJson = periodWindowsJson,
                keywordCount = keywordCount,
                source = HaEvents.Source.EDIT
            )
        } else {
            val keywordsOn = intentOn && intentQualityCheckEnabled
            val keywordCount = if (keywordsOn) {
                com.life.mindfulnessapp.domain.model.IntentBlockKeywords.decode(intentBlockKeywordsJson).size
            } else {
                0
            }
            monitorProfileReporter.trackCapabilityBind(
                app = appInfo.appName,
                pkg = appInfo.packageName,
                intent = intentOn,
                time = timeOn,
                period = periodOn,
                session = sessionLimitEnabled && intentOn,
                keywords = keywordsOn,
                dailyLimitMinutes = dailyLimitMinutes,
                defaultSessionMin = if (sessionLimitEnabled && intentOn) defaultSessionLimitMinutes else 0,
                periodWindowsJson = periodWindowsJson,
                keywordCount = keywordCount,
                isNew = true,
                bindSlot = _monitoredCount.value + 1
            )
        }
        // 刷新本页缓存中的该 App（不依赖整机扫包）
        val refreshed = withContext(Dispatchers.IO) {
            getInstalledAppsUseCase.getApp(appInfo.packageName)
        }
        if (refreshed != null) {
            fun upsert(list: List<AppInfo>): List<AppInfo> {
                val next = list.toMutableList()
                val index = next.indexOfFirst { it.packageName == refreshed.packageName }
                if (index >= 0) next[index] = refreshed else next.add(refreshed)
                return next
            }
            _allApps.value = upsert(_allApps.value)
            applySearchFilter(_searchQuery.value, _allApps.value)
            _monitoredCount.value = _allApps.value.count { it.isMonitored }
        }
        return true
    }

    /**
     * 批量把若干 App 系上 [capability]：新 App 用默认参数新建；已系锚则叠加该能力开关。
     * 免费坑位不足时跳过尚未系锚的项（可叠加项不受影响）。
     */
    suspend fun batchBindCapability(
        capability: CapabilityKind,
        packageNames: List<String>
    ): BatchBindResult {
        if (packageNames.isEmpty()) return BatchBindResult()
        var success = 0
        var skippedLimit = 0
        var failed = 0
        val defaultWindowsJson = PeriodWindowsCodec.encode(listOf(PeriodWindow.defaultSleep()))

        for (pkg in packageNames) {
            val cached = _allApps.value.find { it.packageName == pkg }
                ?: _apps.value.find { it.packageName == pkg }
            val dbLimit = withContext(Dispatchers.IO) {
                appLimitRepository.getAppLimit(pkg)
            }
            val alreadyMonitored = dbLimit?.isEnabled == true ||
                cached?.isMonitored == true

            val app = cached
                ?: withContext(Dispatchers.IO) { getInstalledAppsUseCase.getApp(pkg) }
            if (app == null) {
                failed++
                continue
            }

            if (!alreadyMonitored && !vipRepository.canAddMoreApps(_monitoredCount.value)) {
                skippedLimit++
                continue
            }

            val ok = if (alreadyMonitored) {
                // 以库内配置为准叠加，避免列表缓存标志陈旧
                val intentOn = (dbLimit?.requireIntentOnOpen ?: app.requireIntentOnOpen) ||
                    capability == CapabilityKind.IntentGate
                val timeOn = (dbLimit?.timeLimitEnabled ?: app.timeLimitEnabled) ||
                    capability == CapabilityKind.TimeLock
                val periodOn = (dbLimit?.periodLockEnabled ?: app.periodLockEnabled) ||
                    capability == CapabilityKind.PeriodLock
                val existingWindows = dbLimit?.periodWindowsJson?.takeIf { it.isNotBlank() }
                    ?: app.periodWindowsJson
                val windowsJson = when {
                    !periodOn -> ""
                    existingWindows.isNotBlank() -> existingWindows
                    else -> defaultWindowsJson
                }
                val daily = (dbLimit?.dailyLimitMinutes ?: app.dailyLimitMinutes)
                    .coerceAtLeast(1)
                    .takeIf { it > 0 } ?: 30
                saveMonitorConfig(
                    appInfo = app.copy(isMonitored = true),
                    dailyLimitMinutes = daily,
                    timeAwarenessEnabled = false,
                    timeLimitEnabled = timeOn,
                    requireIntentOnOpen = intentOn,
                    sessionLimitEnabled = intentOn &&
                        (dbLimit?.sessionLimitEnabled ?: app.sessionLimitEnabled),
                    intentQualityCheckEnabled = intentOn &&
                        (dbLimit?.intentQualityCheckEnabled ?: app.intentQualityCheckEnabled),
                    intentBlockKeywordsJson = dbLimit?.intentBlockKeywordsJson
                        ?: app.intentBlockKeywordsJson,
                    defaultSessionLimitMinutes = dbLimit?.defaultSessionLimitMinutes
                        ?: app.defaultSessionLimitMinutes,
                    intentReviewEnabled = intentOn &&
                        (dbLimit?.intentReviewEnabled ?: app.intentReviewEnabled),
                    overTimeMessage = dbLimit?.overTimeMessage ?: app.overTimeMessage,
                    periodLockEnabled = periodOn,
                    periodWindowsJson = windowsJson,
                    periodLockCommitment = dbLimit?.periodLockCommitment
                        ?: app.periodLockCommitment
                )
            } else {
                // 手动主路径中档：日限 30；按所选能力开对应开关
                val intentOn = capability == CapabilityKind.IntentGate
                val timeOn = capability == CapabilityKind.TimeLock
                val periodOn = capability == CapabilityKind.PeriodLock
                val midOpens = timeOn
                saveMonitorConfig(
                    appInfo = app,
                    dailyLimitMinutes = 30,
                    timeAwarenessEnabled = false,
                    timeLimitEnabled = timeOn,
                    requireIntentOnOpen = intentOn,
                    sessionLimitEnabled = false,
                    intentQualityCheckEnabled = false,
                    intentBlockKeywordsJson = "",
                    defaultSessionLimitMinutes = 15,
                    intentReviewEnabled = false,
                    overTimeMessage = "",
                    periodLockEnabled = periodOn,
                    periodWindowsJson = if (periodOn) defaultWindowsJson else "",
                    periodLockCommitment = "",
                    dailyOpenLimitEnabled = midOpens,
                    dailyOpenLimit = if (midOpens) 8 else null
                ).also { saved ->
                    if (saved && timeOn) {
                        rulePlanStore.saveMeta(pkg, "中档默认")
                    }
                }
            }
            if (ok) success++ else {
                if (!alreadyMonitored && !vipRepository.canAddMoreApps(_monitoredCount.value)) {
                    skippedLimit++
                } else {
                    failed++
                }
            }
        }
        return BatchBindResult(
            successCount = success,
            skippedLimitCount = skippedLimit,
            failedCount = failed
        )
    }

    /** 免费版还可新系锚的坑位数；会员 / 公测期视为无限。 */
    fun remainingFreshSlots(): Int {
        if (AppPreferences.FREE_PERIOD_ENABLED || vipRepository.isVip()) return Int.MAX_VALUE
        return (AppPreferences.FREE_MONITOR_LIMIT - _monitoredCount.value).coerceAtLeast(0)
    }

    fun removeFromMonitor(packageName: String) {
        viewModelScope.launch {
            stopMonitoring(packageName)
        }
    }

    /**
     * 取消监控前是否需过呼吸门槛：被已开启的日程锁罩住，或已开 App 时段锁。
     * 不要求此刻落在窗内。
     */
    suspend fun isUnderActivePeriodHardLock(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            val plans = planBlockRepository.getAllOnce()
            val monitored = appLimitRepository.getEnabledPackageNames().toSet()
            val limit = appLimitRepository.getAppLimit(packageName)
            PlanBlockPolicy.needsBreathToStopMonitoring(
                plans = plans,
                packageName = packageName,
                monitoredPackages = monitored,
                appPeriodLockEnabled = limit?.periodLockEnabled == true,
                appPeriodWindows = if (limit?.periodLockEnabled == true) {
                    PeriodWindowsCodec.decode(limit.periodWindowsJson)
                } else {
                    emptyList()
                }
            )
        }

    /** 停止监控并刷新本页缓存；可供编辑页在确认后 await 再返回。 */
    suspend fun stopMonitoring(packageName: String) {
        val existing = withContext(Dispatchers.IO) {
            appLimitRepository.getAppLimit(packageName)
        }
        withContext(NonCancellable) {
            appLimitRepository.deleteAppLimit(packageName)
        }
        val left = _apps.value.count { it.isMonitored && it.packageName != packageName }
        monitorProfileReporter.trackCapabilityUnbind(
            app = existing?.appName
                ?: _apps.value.firstOrNull { it.packageName == packageName }?.appName
                ?: "",
            pkg = packageName,
            intent = existing?.requireIntentOnOpen,
            time = existing?.timeLimitEnabled,
            period = existing?.periodLockEnabled,
            session = existing?.sessionLimitEnabled,
            monitoredLeft = left
        )
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
        val launcherForDetect = run {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val own = context.packageName
            pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.activityInfo.packageName != own }
                .distinctBy { it.activityInfo.packageName }
                .map {
                    com.life.mindfulnessapp.domain.model.DualAppDetector.LauncherApp(
                        packageName = it.activityInfo.packageName,
                        appName = it.loadLabel(pm).toString()
                    )
                }
                .toList()
        }
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
                timeAwarenessEnabled = false,
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
                compareEnabled = limit.compareEnabled,
                compareMinMinutes = ComparePolicy.sanitizeMinMinutes(limit.compareMinMinutes),
                isUninstalled = isUninstalled,
                lockClonesEnabled = limit.lockClonesEnabled,
                blockDiscoverFeedEnabled = limit.blockDiscoverFeedEnabled,
                suspectedCloneOfPackage = com.life.mindfulnessapp.domain.model.DualAppDetector
                    .findPrimaryPackage(launcherForDetect, limit.packageName, appName),
                suspectedClonePackages = com.life.mindfulnessapp.domain.model.DualAppDetector
                    .findClonePackages(launcherForDetect, limit.packageName, appName),
                hasSystemDualInstance = com.life.mindfulnessapp.util.OemDualSpace
                    .hasSystemDualInstance(context, limit.packageName)
            )
        }
    }

    /** 更新期望描述（意义与价值约定） */
    fun updateUsageCovenant(packageName: String, covenant: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val existing = appLimitRepository.getAppLimit(packageName) ?: return@withContext
                appLimitRepository.saveAppLimit(
                    existing.copy(usageCovenant = covenant.trim().take(120))
                )
            }
            refreshMonitoredApp(packageName)
        }
    }

    /** 分身一并锁定开关 */
    fun setLockClonesEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val existing = appLimitRepository.getAppLimit(packageName) ?: return@withContext
                appLimitRepository.saveAppLimit(
                    existing.copy(lockClonesEnabled = enabled)
                )
            }
            refreshMonitoredApp(packageName)
        }
    }

    fun getCompanionAppMode(packageName: String): String =
        appPreferences.getCompanionAppMode(packageName)

    fun setCompanionAppMode(packageName: String, mode: String) {
        appPreferences.setCompanionAppMode(packageName, mode)
        overlayManager.onCompanionAppearanceChanged()
    }

    private suspend fun refreshMonitoredApp(packageName: String) {
        val refreshed = withContext(Dispatchers.IO) {
            getInstalledAppsUseCase.getApp(packageName)
        }
        if (refreshed != null) {
            fun upsert(list: List<AppInfo>): List<AppInfo> {
                val next = list.toMutableList()
                val index = next.indexOfFirst { it.packageName == packageName }
                if (index >= 0) next[index] = refreshed else next.add(refreshed)
                return next
            }
            _allApps.value = upsert(_allApps.value)
            applySearchFilter(_searchQuery.value, _allApps.value)
        }
    }

    /**
     * 使用概览：今日 / 本周 / 累计 / 均值 + 正念分布 + 日用量（详情「概览」Tab）。
     */
    val usageOverview: StateFlow<AppUsageOverview?> = _glancePackageName
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(null)
            } else {
                combine(
                    usageRecordRepository.getRecordsByApp(pkg),
                    todayRangeFlow()
                ) { records, _ ->
                    val now = System.currentTimeMillis()
                    val (dayStart, dayEnd) = getDayRange(now)
                    val (weekStart, weekEnd) = UsageRecordRepository.getWeekRange(now)
                    val (monthStart, monthEnd) = UsageRecordRepository.getMonthRange(now)
                    val nonSeed = records.filter { !it.isSeed }
                    val enters = nonSeed.filter { UsageRecordCounts.isEnter(it) }
                    val endedEnters = enters.filter { it.endTime > 0L }
                    val today = nonSeed.filter { it.startTime in dayStart until dayEnd }
                    val week = nonSeed.filter { it.startTime in weekStart until weekEnd }
                    fun secondsIn(start: Long, end: Long): Long =
                        nonSeed
                            .filter { it.startTime in start until end && it.endTime > 0L }
                            .sumOf { it.durationSeconds.coerceAtLeast(0L) }
                    val totalSeconds = endedEnters.sumOf { it.durationSeconds.coerceAtLeast(0L) }
                    val sessionCount = endedEnters.size
                    val avgSession = if (sessionCount > 0) totalSeconds / sessionCount else 0L
                    val daySeconds = endedEnters
                        .groupBy { getDayRange(it.startTime).first }
                        .mapValues { (_, list) ->
                            list.sumOf { it.durationSeconds.coerceAtLeast(0L) }
                        }
                    val reviewed = enters.filter {
                        UsageRecordEntity.MindfulnessLevel.isValid(it.mindfulnessLevel)
                    }
                    AppUsageOverview(
                        todaySeconds = secondsIn(dayStart, dayEnd),
                        weekSeconds = secondsIn(weekStart, weekEnd),
                        monthSeconds = secondsIn(monthStart, monthEnd),
                        totalSeconds = totalSeconds,
                        todayEnterCount = UsageRecordCounts.enterCount(today),
                        weekEnterCount = UsageRecordCounts.enterCount(week),
                        todayMindfulCount = UsageRecordCounts.mindfulEnterCount(today),
                        todayDismissCount = UsageRecordCounts.dismissCount(today),
                        totalEnterCount = UsageRecordCounts.enterCount(nonSeed),
                        totalMindfulCount = UsageRecordCounts.mindfulEnterCount(nonSeed),
                        avgSessionSeconds = avgSession,
                        alignedCount = reviewed.count {
                            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.ALIGNED
                        },
                        slightCount = reviewed.count {
                            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.SLIGHT
                        },
                        largeCount = reviewed.count {
                            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.LARGE
                        },
                        daySecondsByDayStart = daySeconds
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 守护概览：守住离开 + 时长锁拦住 + 时段锁拦住。
     */
    val guardOverview: StateFlow<GuardOverview> = _glancePackageName
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(GuardOverview())
            } else {
                usageRecordRepository.getRecordsByApp(pkg).map { records ->
                    buildGuardOverview(records)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardOverview())
}

private fun buildGuardOverview(
    records: List<com.life.mindfulnessapp.data.db.entity.UsageRecordEntity>
): GuardOverview {
    val end = com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.EndReason
    val events = records
        .filter { !it.isSeed }
        .mapNotNull { record ->
            val kind = when {
                record.isGateQuit -> GuardEventKind.GateQuit
                record.endReason == end.LIMIT_REACHED -> GuardEventKind.TimeLock
                record.endReason == end.PERIOD_LOCK -> GuardEventKind.PeriodLock
                else -> return@mapNotNull null
            }
            GuardEvent(
                recordId = record.id,
                startTime = record.startTime,
                kind = kind,
                durationSeconds = record.durationSeconds.coerceAtLeast(0L),
                purpose = record.purpose?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        .sortedByDescending { it.startTime }
    return GuardOverview(
        gateQuitCount = events.count { it.kind == GuardEventKind.GateQuit },
        timeLockCount = events.count { it.kind == GuardEventKind.TimeLock },
        periodLockCount = events.count { it.kind == GuardEventKind.PeriodLock },
        recent = events.take(20)
    )
}

/** [AppListViewModel.batchBindCapability] 结果摘要 */
data class BatchBindResult(
    val successCount: Int = 0,
    val skippedLimitCount: Int = 0,
    val failedCount: Int = 0
)
