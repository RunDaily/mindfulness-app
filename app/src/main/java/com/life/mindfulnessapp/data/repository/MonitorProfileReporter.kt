package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.analytics.AnalyticsBuckets
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.network.AnalyticsMonitoredAppDto
import com.life.mindfulnessapp.data.network.AnalyticsPermissionSnapshotDto
import com.life.mindfulnessapp.data.network.AnalyticsPrefsSnapshotDto
import com.life.mindfulnessapp.domain.model.IntentBlockKeywords
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.service.MonitorForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监控配置画像上报：变更事件 + 配置快照（管理台当前态真相源）。
 * 快照 debounce，避免连续编辑打爆接口。
 */
@Singleton
class MonitorProfileReporter @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val appLimitRepository: AppLimitRepository,
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val appPreferences: AppPreferences,
    private val vipRepository: VipRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pendingReason: String = HaEvents.SnapshotReason.SYNC
    private var debounceJob: Job? = null

    /** 冷启动 / 手动纠偏：尽快上报全量快照。 */
    fun pushSnapshotNow(reason: String = HaEvents.SnapshotReason.COLD_START) {
        scope.launch {
            mutex.withLock {
                debounceJob?.cancel()
                debounceJob = null
                pendingReason = reason
            }
            flushSnapshot()
        }
    }

    /** 配置变更后 debounce 上报。 */
    fun scheduleSnapshot(reason: String) {
        scope.launch {
            mutex.withLock {
                pendingReason = reason
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    flushSnapshot()
                }
            }
        }
    }

    fun trackCapabilityBind(
        app: String,
        pkg: String,
        intent: Boolean,
        time: Boolean,
        period: Boolean,
        session: Boolean = false,
        keywords: Boolean = false,
        dailyLimitMinutes: Int = 0,
        defaultSessionMin: Int = 0,
        periodWindowsJson: String? = null,
        keywordCount: Int = 0,
        isNew: Boolean,
        bindSlot: Int? = null
    ) {
        analyticsRepository.trackCapabilityBind(
            app = app,
            pkg = pkg,
            intent = intent,
            time = time,
            period = period,
            session = session,
            keywords = keywords,
            dailyLimitMinutes = dailyLimitMinutes,
            defaultSessionMin = defaultSessionMin,
            periodWindowsJson = periodWindowsJson,
            keywordCount = keywordCount,
            isNew = isNew,
            bindSlot = bindSlot,
            source = HaEvents.Source.BIND
        )
        scheduleSnapshot(HaEvents.SnapshotReason.BIND)
    }

    fun trackCapabilityEdit(
        app: String,
        pkg: String,
        intent: Boolean,
        time: Boolean,
        period: Boolean,
        session: Boolean = false,
        keywords: Boolean = false,
        dailyLimitMinutes: Int = 0,
        defaultSessionMin: Int = 0,
        periodWindowsJson: String? = null,
        keywordCount: Int = 0,
        source: String = HaEvents.Source.EDIT
    ) {
        analyticsRepository.trackCapabilityEdit(
            app = app,
            pkg = pkg,
            intent = intent,
            time = time,
            period = period,
            session = session,
            keywords = keywords,
            dailyLimitMinutes = dailyLimitMinutes,
            defaultSessionMin = defaultSessionMin,
            periodWindowsJson = periodWindowsJson,
            keywordCount = keywordCount,
            source = source
        )
        val reason = when (source) {
            HaEvents.Source.HOME -> HaEvents.SnapshotReason.HOME_LIMIT
            else -> HaEvents.SnapshotReason.EDIT
        }
        scheduleSnapshot(reason)
    }

    fun trackCapabilityUnbind(
        app: String = "",
        pkg: String = "",
        intent: Boolean? = null,
        time: Boolean? = null,
        period: Boolean? = null,
        session: Boolean? = null,
        monitoredLeft: Int? = null
    ) {
        analyticsRepository.trackCapabilityUnbind(
            app = app,
            pkg = pkg,
            intent = intent,
            time = time,
            period = period,
            session = session,
            monitoredLeft = monitoredLeft
        )
        scheduleSnapshot(HaEvents.SnapshotReason.UNBIND)
    }

    fun trackMonitorToggle(enabled: Boolean, monitoredCount: Int) {
        analyticsRepository.trackMonitorToggle(enabled, monitoredCount)
        scheduleSnapshot(HaEvents.SnapshotReason.MONITOR_TOGGLE)
    }

    fun trackMonitorReorder(monitoredCount: Int) {
        analyticsRepository.trackMonitorReorder(monitoredCount)
        scheduleSnapshot(HaEvents.SnapshotReason.REORDER)
    }

    private suspend fun flushSnapshot() {
        val reason = mutex.withLock {
            debounceJob = null
            pendingReason
        }
        val limits = appLimitRepository.getAllLimitsOnce()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }, { it.packageName }))
        val perm = checkPermissionsUseCase()
        analyticsRepository.pushConfigSnapshot(
            reason = reason,
            monitorServiceOn = MonitorForegroundService.isRunning,
            vipActive = vipRepository.isVip(),
            monitoredCount = limits.size,
            permission = AnalyticsPermissionSnapshotDto(
                overlay = perm.hasOverlay,
                usage = perm.hasUsageStats,
                battery = perm.hasBatteryOptimizationIgnored,
                notification = perm.hasNotification,
                accessibility = perm.hasAccessibilityKeepAlive
            ),
            prefs = AnalyticsPrefsSnapshotDto(
                theme_mode = buildString {
                    append(appPreferences.getThemePack().storageKey)
                    if (appPreferences.isThemeFollowSystem()) append("+system")
                },
                enhanced_keep_alive = appPreferences.isEnhancedKeepAliveEnabled(),
                capsule_mini_size = appPreferences.getCapsuleMiniSize()
            ),
            apps = limits.map { it.toSnapshotDto() }
        )
    }

    private fun AppLimitEntity.toSnapshotDto(): AnalyticsMonitoredAppDto {
        val keywordsOn = requireIntentOnOpen && intentQualityCheckEnabled
        val keywordCount = if (keywordsOn) {
            IntentBlockKeywords.decode(intentBlockKeywordsJson).size
        } else {
            0
        }
        return AnalyticsMonitoredAppDto(
            pkg = AnalyticsBuckets.truncatePkg(packageName),
            app = AnalyticsBuckets.truncateApp(appName),
            enabled = isEnabled,
            sort_order = sortOrder,
            cap_intent = requireIntentOnOpen,
            cap_time = timeLimitEnabled,
            cap_period = periodLockEnabled,
            cap_session = requireIntentOnOpen && sessionLimitEnabled,
            cap_keywords = keywordsOn,
            daily_limit_min = if (timeLimitEnabled) dailyLimitMinutes else 0,
            default_session_min = if (requireIntentOnOpen && sessionLimitEnabled) {
                defaultSessionLimitMinutes
            } else {
                0
            },
            keyword_count = keywordCount,
            period_window_count = if (periodLockEnabled) {
                AnalyticsBuckets.periodWindowCount(periodWindowsJson)
            } else {
                0
            },
            period_lock_hours = if (periodLockEnabled) {
                AnalyticsBuckets.averageDailyPeriodLockHoursFromJson(periodWindowsJson)
            } else {
                "0"
            }
        )
    }

    companion object {
        private const val DEBOUNCE_MS = 800L
    }
}
