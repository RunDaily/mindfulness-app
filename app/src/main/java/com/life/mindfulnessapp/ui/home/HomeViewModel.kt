package com.life.mindfulnessapp.ui.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.LimitResetRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.UsageSession
import com.life.mindfulnessapp.domain.model.WeeklyReportData
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.usecase.GetUsageSummaryUseCase
import com.life.mindfulnessapp.domain.usecase.GetWeekAwarenessUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.service.SessionManager
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
    private val limitResetRepository: LimitResetRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val vipRepository: VipRepository,
    private val sessionManager: SessionManager,
    private val getWeekAwarenessUseCase: GetWeekAwarenessUseCase
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

    // ── VIP 状态 ──────────────────────────────────────────────────────────────

    /** 实时 VIP 等级（0=免费，1=标准，2=高级），用于 UI 门禁判断 */
    val vipLevel: StateFlow<Int> = vipRepository.vipLevel

    /** 是否已达到免费版 App 监控数量上限（免费版3个） */
    val isAtFreeLimit: StateFlow<Boolean> = combine(
        monitoredAppCount,
        vipRepository.vipLevel
    ) { apps, level ->
        level <= 0 && apps.size >= AppPreferences.FREE_MONITOR_LIMIT
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

    /**
     * 「有目的使用 + 手动结束」后，需要高亮引导的 recordId。
     * 使用 StateFlow 而非 Channel，保证 HomeScreen 晚订阅也不会丢失事件。
     * null 表示无待高亮；消费后由 HomeScreen 调用 consumeOpenNoteEvent() 清除。
     */
    private val _pendingHighlightId = MutableStateFlow<Long?>(null)
    val pendingHighlightId: StateFlow<Long?> = _pendingHighlightId.asStateFlow()

    /**
     * 当前进行中会话的实时有效秒数（已排除后台时间），每秒更新一次。
     * key = recordId，value = currentSessionSeconds；无活跃会话时为 null。
     * HomeScreen 用此值替代 `now - event.startTime` 来显示进行中条目的时长，
     * 避免把后台等待时间也计入显示。
     */
    private val _ongoingSessionSeconds = MutableStateFlow<Pair<Long, Long>?>(null)
    /** (recordId, currentSessionSeconds)，无活跃会话时为 null */
    val ongoingSessionSeconds: StateFlow<Pair<Long, Long>?> = _ongoingSessionSeconds

    /** 今日页「本周觉察」轻入口摘要；无足够样本时为 null */
    private val _weekAwarenessPeek = MutableStateFlow<WeeklyReportData?>(null)
    val weekAwarenessPeek: StateFlow<WeeklyReportData?> = _weekAwarenessPeek

    init {
        viewModelScope.launch {
            // 每周上限已下线：清掉历史配置，避免继续按周拦截
            appLimitRepository.clearAllWeeklyLimits()
        }
        loadData()
        startAutoRefresh()
        observeTodayTimeline()
        startOngoingSessionTicker()
        refreshWeekAwarenessPeek()
    }

    fun refreshWeekAwarenessPeek() {
        viewModelScope.launch {
            _weekAwarenessPeek.value = runCatching { getWeekAwarenessUseCase() }.getOrNull()
        }
    }

    /**
     * 每秒轮询当前活跃 session 的有效秒数，驱动时间轴进行中条目的实时显示。
     */
    private fun startOngoingSessionTicker() {
        viewModelScope.launch {
            while (isActive) {
                val session = sessionManager.currentSession.value
                _ongoingSessionSeconds.value = if (session != null) {
                    session.recordId to session.currentSessionSeconds
                } else {
                    null
                }
                delay(1_000L)
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _usageSummaries.value = getUsageSummaryUseCase()
            _permissionStatus.value = checkPermissionsUseCase()
            _weekAwarenessPeek.value = runCatching { getWeekAwarenessUseCase() }.getOrNull()
        }
    }

    /** 单独刷新权限状态（从权限设置页返回后调用） */
    fun refreshPermissions() {
        viewModelScope.launch {
            _permissionStatus.value = checkPermissionsUseCase()
        }
    }

    /** 加载已安装 App 列表（打开 Sheet 时调用） */
    /**
     * 由 MainActivity 在收到 Intent extra 时调用，
     * 设置需要高亮的 recordId，HomeScreen 会观察并高亮对应条目。
     */
    fun requestOpenNote(recordId: Long) {
        _pendingHighlightId.value = recordId
    }

    /** HomeScreen 消费高亮事件后调用，清除待高亮状态 */
    fun consumeOpenNoteEvent() {
        _pendingHighlightId.value = null
    }

    /** 更新某条使用记录的复盘备注，传入 null 表示清空 */
    fun updateRecordNote(recordId: Long, note: String?) {
        viewModelScope.launch {
            usageRecordRepository.updateNote(recordId, note?.trim()?.ifBlank { null })
        }
    }

    /** 更新对照档位与备注（两维度独立，均可为空） */
    fun updateRecordReview(recordId: Long, note: String?, mindfulnessLevel: Int?) {
        viewModelScope.launch {
            usageRecordRepository.updateNoteAndMindfulness(
                recordId,
                note?.trim()?.ifBlank { null },
                mindfulnessLevel?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
            )
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
            appLimitRepository.saveAppLimit(
                existing.copy(
                    dailyLimitMinutes = newDailyMinutes,
                    weeklyLimitMinutes = newWeeklyMinutes,
                    timeLimitEnabled = timeLimitEnabled ?: existing.timeLimitEnabled,
                    overTimeMessage = overTimeMessage ?: existing.overTimeMessage
                )
            )
            _usageSummaries.value = getUsageSummaryUseCase()
        }
    }

    /** 移除监控 */
    fun removeFromMonitor(packageName: String) {
        viewModelScope.launch {
            appLimitRepository.deleteAppLimit(packageName)
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
     *   - 因此额外合并 sessionManager.currentSession：若当前有活跃 session 且属于今天，
     *     则将其作为一条「进行中」的虚拟条目插入时间轴顶部，实时展示。
     *   - 时间轴按 startTime 倒序（最新在最上面）。
     */
    private fun observeTodayTimeline() {
        viewModelScope.launch {
            todayRangeFlow()
                .flatMapLatest { (dayStart, dayEnd) ->
                    combine(
                        usageRecordRepository.getDayRecords(dayStart, dayEnd),
                        limitResetRepository.getResetsByPeriod(dayStart, dayEnd),
                        sessionManager.currentSession,
                        appLimitRepository.getAllAppLimits()
                    ) { usageRecords, resetRecords, activeSession, limits ->
                        TimelineDaySnapshot(
                            dayStart = dayStart,
                            dayEnd = dayEnd,
                            usageRecords = usageRecords,
                            resetRecords = resetRecords,
                            activeSession = activeSession,
                            limits = limits
                        )
                    }
                }
                .collect { snapshot ->
                    val dayStart = snapshot.dayStart
                    val dayEnd = snapshot.dayEnd
                    val usageRecords = snapshot.usageRecords
                    val resetRecords = snapshot.resetRecords
                    val activeSession = snapshot.activeSession
                    val limits = snapshot.limits

                    val limitByPkg = limits.associateBy { it.packageName }
                    val usageEvents = withContext(Dispatchers.IO) {
                        usageRecords.map { record ->
                            val limit = limitByPkg[record.packageName]
                            val kind = IntentKind.fromStorage(record.intentKind)
                            val gateQuit = record.isGateQuit
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
                                hasIntentGate = !seed && (
                                    gateQuit ||
                                        kind != null ||
                                        record.purpose != null ||
                                        limit?.requireIntentOnOpen == true
                                    ),
                                hasTimeLock = !seed && (
                                    record.endReason == end.LIMIT_REACHED ||
                                        record.endReason == end.SESSION_LIMIT_REACHED ||
                                        record.sessionLimitMinutes > 0 ||
                                        limit?.timeLimitEnabled == true
                                    ),
                                sessionLimitMinutes = record.sessionLimitMinutes,
                                sessionExtensionMinutes = record.sessionExtensionMinutes
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

                    // 若有进行中的 session 且属于今天，插入一条「进行中」虚拟条目
                    // endTime = -1L 是 isOngoing 的判断依据（TimelineEvent.UsageEvent.isOngoing）
                    val ongoingEvent: TimelineEvent.UsageEvent? = if (activeSession != null &&
                        activeSession.startTime >= dayStart && activeSession.startTime < dayEnd) {
                        TimelineEvent.UsageEvent(
                            packageName = activeSession.packageName,
                            appName = activeSession.appName,
                            startTime = activeSession.startTime,
                            endTime = -1L,
                            // durationSeconds 先用 0：进行中条目的实时时长由 ongoingSessionSeconds 驱动
                            durationSeconds = 0L,
                            endReason = "",
                            purpose = activeSession.purpose,
                            recordId = activeSession.recordId,
                            note = null,
                            intentKind = activeSession.intentKind,
                            hasIntentGate = activeSession.hasIntentGate,
                            hasTimeLock = activeSession.hasTimeLock || activeSession.hasSessionLimit
                        )
                    } else null

                    val allEvents = if (ongoingEvent != null) {
                        usageEvents + ongoingEvent
                    } else {
                        usageEvents
                    }

                    // 合并后按时间倒序（最新的在最上面）
                    _todayTimeline.value = (allEvents + resetEvents).sortedByDescending { it.timeMs }
                }
        }
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
    val resetRecords: List<LimitResetEntity>,
    val activeSession: UsageSession?,
    val limits: List<AppLimitEntity>
)
