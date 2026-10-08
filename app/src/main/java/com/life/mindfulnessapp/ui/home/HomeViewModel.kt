package com.life.mindfulnessapp.ui.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.HaPayRepository
import com.life.mindfulnessapp.data.repository.LimitResetRepository
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.DayInsightBuilder
import com.life.mindfulnessapp.domain.model.DayInsightSnapshot
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.UsageLogDisplayNames
import com.life.mindfulnessapp.domain.model.UsageLogPeriodGroup
import com.life.mindfulnessapp.domain.model.UsageSession
import com.life.mindfulnessapp.domain.model.UsageTransitionBuilder
import android.content.Intent
import java.util.Calendar
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.usecase.GetUsageSummaryUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.service.MonitorForegroundService
import com.life.mindfulnessapp.service.SessionCompareReminderWorker
import com.life.mindfulnessapp.service.SessionManager
import com.life.mindfulnessapp.util.MonitorHealthStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getUsageSummaryUseCase: GetUsageSummaryUseCase,
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val appLimitRepository: AppLimitRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val systemUsageRepository: SystemUsageRepository,
    private val limitResetRepository: LimitResetRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val vipRepository: VipRepository,
    private val haPayRepository: HaPayRepository,
    private val sessionManager: SessionManager,
    private val analyticsRepository: AnalyticsRepository,
    private val appPreferences: AppPreferences,
    private val monitorProfileReporter: com.life.mindfulnessapp.data.repository.MonitorProfileReporter,
    private val planBlockRepository: PlanBlockRepository
) : ViewModel() {

    private val _usageSummaries = MutableStateFlow<List<AppUsageSummary>>(emptyList())
    val usageSummaries: StateFlow<List<AppUsageSummary>> = _usageSummaries

    /** 当前生效中的守计划（无则 null） */
    val activePlanBlock: StateFlow<PlanBlock?> = planBlockRepository.observeAll()
        .map { PlanBlockPolicy.activeNow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // 权限检查是同步轻量调用；若先用全 false，首页会闪出权限条/弹窗再被关掉
    private val _permissionStatus = MutableStateFlow(checkPermissionsUseCase())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    private val _monitorInterruptPending = MutableStateFlow(false)
    /** 后台监控曾中断，首页提示去保活指南 */
    val monitorInterruptPending: StateFlow<Boolean> = _monitorInterruptPending

    private val _monitoredAppsLoaded = MutableStateFlow(false)
    private val _timelineReady = MutableStateFlow(false)
    private val _summariesReady = MutableStateFlow(false)

    /**
     * 首页首屏可画：监控轨与时间轴都已有第一次真实数据。
     * 在此之前把空列表当成「未系锚」会闪一句假文案。
     */
    val homeReady: StateFlow<Boolean> = combine(
        _monitoredAppsLoaded,
        _timelineReady,
        _summariesReady
    ) { appsLoaded, timelineReady, summariesReady ->
        appsLoaded && timelineReady && summariesReady
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 受监控的 App 列表（含真实图标，供底部导航带使用）*/
    val monitoredAppsWithIcon: StateFlow<List<AppInfo>> = appLimitRepository
        .getEnabledAppLimits()
        .map { limits -> loadAppInfoWithIcons(limits) }
        .flowOn(Dispatchers.IO)
        .onEach { _monitoredAppsLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 仅用于统计 monitoredCount 等需要 AppLimitEntity 的地方 */
    val monitoredAppCount: StateFlow<List<AppLimitEntity>> = appLimitRepository
        .getEnabledAppLimits()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── VIP 状态 ──────────────────────────────────────────────────────────────

    /** 实时 VIP 等级（0=免费，1=标准，2=高级），用于 UI 门禁判断 */
    val vipLevel: StateFlow<Int> = vipRepository.vipLevel

    /** 是否已达到免费版 App 监控数量上限（免费版3个） */
    val isAtFreeLimit: StateFlow<Boolean> = combine(
        monitoredAppCount,
        vipRepository.vipLevel
    ) { apps, level ->
        !AppPreferences.FREE_PERIOD_ENABLED &&
            level <= 0 &&
            apps.size >= AppPreferences.FREE_MONITOR_LIMIT
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 当前监控的 App 数量是否已超出免费版限制（用于显示升级引导弹窗） */
    private val _showVipUpgradeDialog = MutableStateFlow(false)
    val showVipUpgradeDialog: StateFlow<Boolean> = _showVipUpgradeDialog

    fun dismissVipUpgradeDialog() {
        _showVipUpgradeDialog.value = false
    }

    // ── 今日时间轴：合并 usage_records + limit_resets，按时间倒序排列 ─────────

    private val _todayTimeline = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val todayTimeline: StateFlow<List<TimelineEvent>> = _todayTimeline

    /** 首页记录：与使用日志同一套场景脊线（仅监控访次） */
    private val _homeLogGroups = MutableStateFlow<List<UsageLogPeriodGroup>>(emptyList())
    val homeLogGroups: StateFlow<List<UsageLogPeriodGroup>> = _homeLogGroups

    private val launcherPackages: Set<String> by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private val logNameCache = mutableMapOf<String, String>()

    private val _dayInsight = MutableStateFlow(
        DayInsightBuilder.buildHomeInsight(emptyList(), emptyList())
    )
    /** 首页今日洞见（有目的 / 守住 / 空转 / 总时长 + 结论文案） */
    val dayInsight: StateFlow<DayInsightSnapshot> = _dayInsight

    /**
     * 「有目的使用 + 手动结束」后，需要高亮引导的 recordId。
     * 使用 StateFlow 而非 Channel，保证 HomeScreen 晚订阅也不会丢失事件。
     * null 表示无待高亮；消费后由 HomeScreen 调用 consumeOpenNoteEvent() 清除。
     */
    private val _pendingHighlightId = MutableStateFlow<Long?>(null)
    val pendingHighlightId: StateFlow<Long?> = _pendingHighlightId.asStateFlow()

    /**
     * 离开超时横条「对照一下」：高亮后自动打开对照编辑。
     * 与 [pendingHighlightId] 可同时存在；弹窗打开后由 HomeScreen 调用 [consumeOpenCompareEvent] 清除。
     */
    private val _pendingOpenCompareId = MutableStateFlow<Long?>(null)
    val pendingOpenCompareId: StateFlow<Long?> = _pendingOpenCompareId.asStateFlow()

    /**
     * 当前进行中会话的实时有效秒数（已排除后台时间），每秒更新一次。
     * key = recordId，value = currentSessionSeconds；无活跃会话时为 null。
     * HomeScreen 用此值替代 `now - event.startTime` 来显示进行中条目的时长，
     * 避免把后台等待时间也计入显示。
     */
    private val _ongoingSessionSeconds = MutableStateFlow<Pair<Long, Long>?>(null)
    /** (recordId, currentSessionSeconds)，无活跃会话时为 null */
    val ongoingSessionSeconds: StateFlow<Pair<Long, Long>?> = _ongoingSessionSeconds

    /**
     * 监控轨高亮：任意活跃会话（前台胶囊或暂停胶囊）对应的包名。
     * 与时间轴「进行中」不同——后者仅在意图门暂停胶囊仍在时展示。
     */
    val activeSessionPackageName: StateFlow<String?> = sessionManager.currentSession
        .map { it?.packageName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // 每周上限已下线：清掉历史配置，避免继续按周拦截
            appLimitRepository.clearAllWeeklyLimits()
            // 兜底收口无活跃会话的未结束记录（服务未起来时也能清掉脏「进行中」）
            sessionManager.closeOrphanedOpenRecords()
        }
        loadData()
        startAutoRefresh()
        observeTodayTimeline()
        startOngoingSessionTicker()
    }

    /**
     * 每秒轮询「暂停胶囊仍在」会话的有效秒数，驱动时间轴进行中条目的实时显示。
     * 仅时长锁切走会藏胶囊、静默收口——此时不算列表上的进行中。
     */
    private fun startOngoingSessionTicker() {
        viewModelScope.launch {
            while (isActive) {
                val session = sessionManager.currentSession.value
                _ongoingSessionSeconds.value = if (session != null && isPausedCapsuleSession(session)) {
                    session.recordId to session.currentSessionSeconds
                } else {
                    null
                }
                delay(1_000L)
            }
        }
    }

    /** 意图门切走后桌面仍挂着暂停胶囊的会话 */
    private fun isPausedCapsuleSession(session: UsageSession): Boolean =
        session.isInBackground && session.hasIntentGate

    fun loadData() {
        viewModelScope.launch {
            _permissionStatus.value = checkPermissionsUseCase()
            refreshMonitorHealth()
            try {
                _usageSummaries.value = getUsageSummaryUseCase()
            } finally {
                _summariesReady.value = true
            }
            haPayRepository.syncDeviceVip()
        }
    }

    /** 检测「被清理后」中断：打开 App 时展示首页横幅 */
    fun refreshMonitorHealth() {
        _monitorInterruptPending.value = MonitorHealthStore.peekOrDetectColdInterrupt(
            context,
            monitorRunning = MonitorForegroundService.isRunning
        )
    }

    fun dismissMonitorInterruptBanner() {
        MonitorHealthStore.clearInterruptPending(context)
        _monitorInterruptPending.value = false
    }

    /** 单独刷新权限状态（从权限设置页返回后调用） */
    fun refreshPermissions() {
        val prev = _permissionStatus.value
        val next = checkPermissionsUseCase()
        _permissionStatus.value = next
        viewModelScope.launch {
            trackPermissionGrants(prev, next, HaEvents.Source.HOME)
        }
    }

    /** 首页首次发现缺权限时弹引导，并上报 */
    fun onPermissionPromptShown() {
        analyticsRepository.trackPermissionPrompt(HaEvents.Source.HOME)
    }

    fun trackDayReportView() {
        analyticsRepository.trackDayReportView()
    }

    /**
     * 由 MainActivity 在收到 Intent extra 时调用，
     * 设置需要高亮的 recordId，HomeScreen 会观察并高亮对应条目。
     */
    fun requestOpenNote(recordId: Long) {
        _pendingHighlightId.value = recordId
    }

    /**
     * 离开超时横条「对照一下」：高亮 + 自动打开对照编辑（闭环入口）。
     */
    fun requestOpenCompare(recordId: Long) {
        _pendingHighlightId.value = recordId
        _pendingOpenCompareId.value = recordId
    }

    /** HomeScreen 消费高亮事件后调用，清除待高亮状态 */
    fun consumeOpenNoteEvent() {
        _pendingHighlightId.value = null
    }

    /** HomeScreen 已打开对照弹窗后调用 */
    fun consumeOpenCompareEvent() {
        _pendingOpenCompareId.value = null
    }

    /** 更新某条使用记录的复盘备注，传入 null 表示清空 */
    fun updateRecordNote(recordId: Long, note: String?) {
        viewModelScope.launch {
            usageRecordRepository.updateNote(recordId, note?.trim()?.ifBlank { null })
        }
    }

    /** 更新对照档位、跑偏时长与备注（均可为空） */
    fun updateRecordReview(
        recordId: Long,
        note: String?,
        mindfulnessLevel: Int?,
        driftSeconds: Long? = null
    ) {
        viewModelScope.launch {
            val trimmed = note?.trim()?.ifBlank { null }
            val level = mindfulnessLevel?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
            val existing = usageRecordRepository.getRecordById(recordId)
            val resolvedDrift = com.life.mindfulnessapp.domain.model.DriftSecondsPolicy.resolveStored(
                level = level,
                driftSeconds = driftSeconds,
                durationSeconds = existing?.durationSeconds ?: 0L
            )
            usageRecordRepository.updateNoteAndMindfulness(
                recordId,
                trimmed,
                level,
                resolvedDrift
            )
            if (level != null) {
                SessionCompareReminderWorker.cancel(context, recordId)
            }
            analyticsRepository.trackCompareSave(
                sessionId = recordId,
                source = HaEvents.Source.HOME,
                hasNote = !trimmed.isNullOrBlank(),
                hasLevel = level != null,
                level = level,
                app = existing?.packageName.orEmpty(),
                pkg = existing?.packageName.orEmpty()
            )
            if (level != null) {
                appPreferences.recordCompareOutcome(level)
            }
        }
    }

    private fun trackPermissionGrants(prev: PermissionStatus, next: PermissionStatus, source: String) {
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

    /** 修改已监控 App 的时限与相关配置 */
    fun updateAppLimit(
        packageName: String,
        newDailyMinutes: Int,
        newWeeklyMinutes: Int,
        timeLimitEnabled: Boolean? = null,
        overTimeMessage: String? = null
    ) {
        viewModelScope.launch {
            val existing = appLimitRepository.getAppLimit(packageName) ?: return@launch
            val updated = existing.copy(
                dailyLimitMinutes = newDailyMinutes,
                weeklyLimitMinutes = newWeeklyMinutes,
                timeLimitEnabled = timeLimitEnabled ?: existing.timeLimitEnabled,
                overTimeMessage = overTimeMessage ?: existing.overTimeMessage
            )
            appLimitRepository.saveAppLimit(updated)
            if (sessionManager.currentSession.value?.packageName == packageName) {
                sessionManager.refreshLiveBudget()
            }
            val keywordsOn = updated.requireIntentOnOpen && updated.intentQualityCheckEnabled
            monitorProfileReporter.trackCapabilityEdit(
                app = updated.appName,
                pkg = packageName,
                intent = updated.requireIntentOnOpen,
                time = updated.timeLimitEnabled,
                period = updated.periodLockEnabled,
                session = updated.requireIntentOnOpen && updated.sessionLimitEnabled,
                keywords = keywordsOn,
                dailyLimitMinutes = updated.dailyLimitMinutes,
                defaultSessionMin = if (updated.requireIntentOnOpen && updated.sessionLimitEnabled) {
                    updated.defaultSessionLimitMinutes
                } else {
                    0
                },
                periodWindowsJson = updated.periodWindowsJson,
                keywordCount = if (keywordsOn) {
                    com.life.mindfulnessapp.domain.model.IntentBlockKeywords
                        .decode(updated.intentBlockKeywordsJson).size
                } else {
                    0
                },
                source = HaEvents.Source.HOME
            )
            _usageSummaries.value = getUsageSummaryUseCase()
        }
    }

    /** 移除监控 */
    fun removeFromMonitor(packageName: String) {
        viewModelScope.launch {
            val existing = appLimitRepository.getAppLimit(packageName)
            appLimitRepository.deleteAppLimit(packageName)
            monitorProfileReporter.trackCapabilityUnbind(
                app = existing?.appName.orEmpty(),
                pkg = packageName,
                intent = existing?.requireIntentOnOpen,
                time = existing?.timeLimitEnabled,
                period = existing?.periodLockEnabled,
                session = existing?.sessionLimitEnabled,
                monitoredLeft = (_usageSummaries.value.size - 1).coerceAtLeast(0)
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
                    compareEnabled = limit.compareEnabled,
                    compareMinMinutes = ComparePolicy.sanitizeMinMinutes(limit.compareMinMinutes),
                    periodLockEnabled = limit.periodLockEnabled,
                    periodWindowsJson = limit.periodWindowsJson,
                    periodLockCommitment = limit.periodLockCommitment,
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
     * 监听今日使用记录、重设限额记录以及当前进行中的 session，合并成统一时间轴。
     *
     * 设计要点：
     *   - 日界随日历滚动：跨午夜后自动切换到新一天（见 [todayRangeFlow]）。
     *   - getDayRecords 的 SQL 过滤了 endTime > 0，进行中的记录（endTime=-1）不会出现。
     *   - 因此额外合并 sessionManager.currentSession：仅当意图门暂停胶囊仍在
     *     （切走后台 + hasIntentGate）且属于今天时，插入「进行中」虚拟条目。
     *   - 时间轴按 startTime 倒序（最新在最上面）。
     */
    private fun observeTodayTimeline() {
        viewModelScope.launch {
            todayRangeFlow()
                .flatMapLatest { (dayStart, dayEnd) ->
                    val yStart = dayStart - (dayEnd - dayStart)
                    val yEnd = dayStart
                    combine(
                        combine(
                            usageRecordRepository.getDayRecords(dayStart, dayEnd),
                            usageRecordRepository.getDayRecords(yStart, yEnd),
                            limitResetRepository.getResetsByPeriod(dayStart, dayEnd),
                            sessionManager.currentSession,
                            appLimitRepository.getAllAppLimits()
                        ) { usageRecords, yesterdayRecords, resetRecords, activeSession, limits ->
                            TimelineDaySnapshot(
                                dayStart = dayStart,
                                dayEnd = dayEnd,
                                usageRecords = usageRecords,
                                yesterdayRecords = yesterdayRecords,
                                resetRecords = resetRecords,
                                activeSession = activeSession,
                                limits = limits,
                                practiceEnabled = false
                            )
                        },
                        appPreferences.awarenessPracticeEnabled
                    ) { snap, practiceOn ->
                        snap.copy(practiceEnabled = practiceOn)
                    }
                }
                .collect { snapshot ->
                    val dayStart = snapshot.dayStart
                    val dayEnd = snapshot.dayEnd
                    val usageRecords = snapshot.usageRecords
                    val resetRecords = snapshot.resetRecords
                    val activeSession = snapshot.activeSession
                    val limits = snapshot.limits
                    val practiceOn = snapshot.practiceEnabled

                    _dayInsight.value = DayInsightBuilder.buildHomeInsight(
                        todayRecords = usageRecords,
                        yesterdayRecords = snapshot.yesterdayRecords,
                        liveSession = activeSession?.takeIf { session ->
                            val origin = session.sessionOriginStartMs.takeIf { it > 0L }
                                ?: session.startTime
                            origin in dayStart until dayEnd
                        }
                    )

                    val limitByPkg = limits.associateBy { it.packageName }
                    val usageEvents = withContext(Dispatchers.IO) {
                        usageRecords.map { record ->
                            val limit = limitByPkg[record.packageName]
                            val kind = IntentKind.fromStorage(record.intentKind)
                            val gateQuit = record.isGateQuit
                            val positiveExit = record.isPositiveExit
                            val seed = record.isSeed
                            val end = UsageRecordEntity.EndReason
                            TimelineEvent.UsageEvent(
                                packageName = record.packageName,
                                appName = resolveAppName(record.packageName),
                                startTime = record.startTime,
                                endTime = record.endTime,
                                durationSeconds = record.durationSeconds,
                                endReason = record.endReason,
                                purpose = record.purpose,
                                recordId = record.id,
                                note = record.note,
                                mindfulnessLevel = record.mindfulnessLevel,
                                intentKind = kind,
                                intentKindRaw = record.intentKind,
                                hasIntentGate = !seed && (
                                    gateQuit ||
                                        positiveExit ||
                                        kind != null ||
                                        record.purpose != null ||
                                        limit?.requireIntentOnOpen == true
                                    ),
                                hasTimeLock = !seed && (
                                    record.endReason == end.LIMIT_REACHED ||
                                        record.endReason == end.SESSION_LIMIT_REACHED ||
                                        record.endReason == end.PERIOD_LOCK ||
                                        record.sessionLimitMinutes > 0 ||
                                        limit?.timeLimitEnabled == true
                                    ),
                                sessionLimitMinutes = record.sessionLimitMinutes,
                                sessionExtensionMinutes = record.sessionExtensionMinutes,
                                compareEnabled = practiceOn && limit?.compareEnabled != false,
                                compareMinMinutes = ComparePolicy.sanitizeMinMinutes(
                                    limit?.compareMinMinutes ?: ComparePolicy.DEFAULT_MIN_MINUTES
                                )
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

                    // 仅「意图门暂停胶囊仍在」时插入进行中条目（仅时长锁切走会藏胶囊，不进列表）
                    // endTime = -1L 是 isOngoing 的判断依据（TimelineEvent.UsageEvent.isOngoing）
                    // 用 sessionOriginStartMs：前后台切换会重置 startTime（前台段），不能用来判日界
                    val originStart = activeSession?.sessionOriginStartMs
                        ?: activeSession?.startTime
                        ?: 0L
                    val ongoingEvent: TimelineEvent.UsageEvent? = if (
                        activeSession != null &&
                        isPausedCapsuleSession(activeSession) &&
                        originStart >= dayStart && originStart < dayEnd
                    ) {
                        TimelineEvent.UsageEvent(
                            packageName = activeSession.packageName,
                            appName = activeSession.appName,
                            startTime = originStart,
                            endTime = -1L,
                            // durationSeconds 先用 0：进行中条目的实时时长由 ongoingSessionSeconds 驱动
                            durationSeconds = 0L,
                            endReason = "",
                            purpose = activeSession.purpose,
                            recordId = activeSession.recordId,
                            note = null,
                            intentKind = activeSession.intentKind,
                            hasIntentGate = activeSession.hasIntentGate,
                            hasTimeLock = activeSession.hasTimeLock || activeSession.hasSessionLimit,
                            compareEnabled = practiceOn && activeSession.compareEnabled,
                            compareMinMinutes = activeSession.compareMinMinutes
                        )
                    } else null

                    val allEvents = if (ongoingEvent != null) {
                        usageEvents + ongoingEvent
                    } else {
                        usageEvents
                    }

                    // 合并后按时间倒序（最新的在最上面）——脉搏等仍用这条
                    _todayTimeline.value = (allEvents + resetEvents).sortedByDescending { it.timeMs }
                    _homeLogGroups.value = withContext(Dispatchers.IO) {
                        buildHomeLogGroups(
                            dayStart = dayStart,
                            dayEnd = dayEnd,
                            records = usageRecords,
                            activeSession = activeSession
                        )
                    }
                    _timelineReady.value = true
                }
        }
    }

    private suspend fun buildHomeLogGroups(
        dayStart: Long,
        dayEnd: Long,
        records: List<UsageRecordEntity>,
        activeSession: UsageSession?
    ): List<UsageLogPeriodGroup> {
        val monitored = appLimitRepository.getEnabledPackageNames().toSet()
        val limitsByPkg = appLimitRepository.getAllLimitsOnce()
            .associateBy { it.packageName }
        val plans = planBlockRepository.getAllOnce()
        val segments = systemUsageRepository.getDayForegroundTimeline(dayStart, dayEnd)
            .map {
                UsageTransitionBuilder.SystemSegment(
                    packageName = it.packageName,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    ongoing = it.ongoing
                )
            }
        val originStart = activeSession?.sessionOriginStartMs
            ?: activeSession?.startTime
            ?: 0L
        val logRecords = if (
            activeSession != null &&
            originStart in dayStart until dayEnd &&
            records.none { it.id == activeSession.recordId }
        ) {
            records + UsageRecordEntity(
                id = activeSession.recordId,
                packageName = activeSession.packageName,
                startTime = originStart,
                endTime = -1L,
                purpose = activeSession.purpose,
                intentKind = activeSession.intentKind?.name
            )
        } else {
            records
        }
        val transitions = UsageTransitionBuilder.build(
            segments = segments,
            records = logRecords,
            monitoredPackages = monitored,
            launcherPackages = launcherPackages,
            ownPackageName = context.packageName,
            resolveAppName = ::resolveLogAppName,
            dayEndMs = minOf(dayEnd, System.currentTimeMillis()),
            intentGatePackages = limitsByPkg.values
                .filter { it.requireIntentOnOpen }
                .map { it.packageName }
                .toSet(),
            resolvePeriodLabel = { pkg, atMs ->
                resolvePeriodWindowLabel(
                    packageName = pkg,
                    atMs = atMs,
                    limitsByPkg = limitsByPkg,
                    plans = plans
                )
            }
        )
        val endHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1
        return UsageTransitionBuilder.groupByPeriod(
            UsageTransitionBuilder.toMonitoredSummaryItems(
                transitions = transitions,
                endHourExclusive = endHour
            )
        ).filter { it.rows.isNotEmpty() }
    }

    private fun resolveLogAppName(packageName: String): String {
        logNameCache[packageName]?.let { return it }
        val raw = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
        val label = UsageLogDisplayNames.displayName(packageName, raw)
        logNameCache[packageName] = label
        return label
    }

    private fun resolvePeriodWindowLabel(
        packageName: String,
        atMs: Long,
        limitsByPkg: Map<String, AppLimitEntity>,
        plans: List<PlanBlock>
    ): String? {
        val plan = PlanBlockPolicy.activeForPackage(plans, packageName, atMs)
        if (plan != null) return plan.label()
        val limit = limitsByPkg[packageName] ?: return null
        if (!limit.periodLockEnabled) return null
        val windows = PeriodWindowsCodec.decode(limit.periodWindowsJson)
        return PeriodLockPolicy.activeWindow(windows, atMs)?.label()
    }

    /**
     * 当前自然日的 [dayStart, dayEnd)，跨过午夜后自动 emit 新区间。
     * 首页「今日」相关订阅都应经此滚动，避免进程常驻时日界冻结在创建时刻。
     */
    private fun todayRangeFlow(): Flow<Pair<Long, Long>> = flow {
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val range = getDayRange(now)
            emit(range)
            delay((range.second - now).coerceAtLeast(1_000L))
        }
    }
}

/** 某一自然日内时间轴合并所需的瞬时快照 */
private data class TimelineDaySnapshot(
    val dayStart: Long,
    val dayEnd: Long,
    val usageRecords: List<UsageRecordEntity>,
    val yesterdayRecords: List<UsageRecordEntity> = emptyList(),
    val resetRecords: List<LimitResetEntity>,
    val activeSession: UsageSession?,
    val limits: List<AppLimitEntity>,
    val practiceEnabled: Boolean = false,
)
