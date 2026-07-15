package com.life.mindfulnessapp.ui.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.LimitResetRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppUsageSummary
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.usecase.GetUsageSummaryUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.service.SessionManager
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
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val vipRepository: VipRepository,
    private val sessionManager: SessionManager
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

    // ── 今日时间轴：合并 usage_records + limit_resets，按时间正序排列 ─────────

    private val _todayTimeline = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val todayTimeline: StateFlow<List<TimelineEvent>> = _todayTimeline

    /**
     * 「有目的使用 + 手动结束」后，需要高亮引导的 recordId。
     * 使用 StateFlow 而非 Channel，保证 HomeScreen 晚订阅也不会丢失事件。
     * null 表示无待高亮；消费后由 HomeScreen 调用 consumeOpenNoteEvent() 清除。
     */
    private val _pendingHighlightId = MutableStateFlow<Long?>(null)
    val pendingHighlightId: StateFlow<Long?> = _pendingHighlightId.asStateFlow()

    /** 今日有意识地打开 App 的次数（即填写了使用目的的次数，purpose NOT NULL） */
    val todayMindfulCount: StateFlow<Int> = run {
        val (dayStart, dayEnd) = getDayRange(System.currentTimeMillis())
        usageRecordRepository.getDayMindfulRecords(dayStart, dayEnd)
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    /**
     * 当前进行中会话的实时有效秒数（已排除后台时间），每秒更新一次。
     * key = recordId，value = currentSessionSeconds；无活跃会话时为 null。
     * HomeScreen 用此值替代 `now - event.startTime` 来显示进行中条目的时长，
     * 避免把后台等待时间也计入显示。
     */
    private val _ongoingSessionSeconds = MutableStateFlow<Pair<Long, Long>?>(null)
    /** (recordId, currentSessionSeconds)，无活跃会话时为 null */
    val ongoingSessionSeconds: StateFlow<Pair<Long, Long>?> = _ongoingSessionSeconds

    init {
        loadData()
        startAutoRefresh()
        observeTodayTimeline()
        startOngoingSessionTicker()
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
     * 监听今日使用记录、重设限额记录以及当前进行中的 session，合并成统一时间轴。
     *
     * 设计要点：
     *   - getDayRecords 的 SQL 过滤了 endTime > 0，进行中的记录（endTime=-1）不会出现。
     *   - 因此额外合并 sessionManager.currentSession：若当前有活跃 session 且属于今天，
     *     则将其作为一条「进行中」的虚拟条目插入时间轴顶部，实时展示。
     *   - 时间轴按 startTime 倒序（最新在最上面）。
     */
    private fun observeTodayTimeline() {
        viewModelScope.launch {
            val (dayStart, dayEnd) = getDayRange(System.currentTimeMillis())

            val usageFlow = usageRecordRepository.getDayRecords(dayStart, dayEnd)
            val resetFlow = limitResetRepository.getResetsByPeriod(dayStart, dayEnd)

            combine(usageFlow, resetFlow, sessionManager.currentSession) { usageRecords, resetRecords, activeSession ->
                Triple(usageRecords, resetRecords, activeSession)
            }.collect { (usageRecords, resetRecords, activeSession) ->
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

                // 若有进行中的 session 且属于今天，插入一条「进行中」虚拟条目
                // endTime = -1L 是 isOngoing 的判断依据（TimelineEvent.UsageEvent.isOngoing）
                val ongoingEvent: TimelineEvent.UsageEvent? = if (activeSession != null &&
                    activeSession.startTime >= dayStart && activeSession.startTime < dayEnd) {
                    // 确保不与已完成记录重复（getDayRecords 的 SQL 排除了 endTime=-1，不会有重复）
                    TimelineEvent.UsageEvent(
                        packageName = activeSession.packageName,
                        appName = activeSession.appName,
                        startTime = activeSession.startTime,
                        endTime = -1L,
                        // durationSeconds 先用 0：进行中条目的实时时长由 ongoingSessionSeconds 驱动，
                        // 不依赖此字段，此处为 0 避免误读
                        durationSeconds = 0L,
                        endReason = "",
                        purpose = activeSession.purpose,
                        recordId = activeSession.recordId,
                        note = null
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
}
