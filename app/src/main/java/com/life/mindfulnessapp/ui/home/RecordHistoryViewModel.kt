package com.life.mindfulnessapp.ui.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.UsageSession
import com.life.mindfulnessapp.service.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

/**
 * 全局记录历史：按自然日查看流水；月历选天。
 * 可回看最近 [HISTORY_DAY_SPAN] 天。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordHistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val sessionManager: SessionManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val todayStartMs: Long = startOfDay(System.currentTimeMillis())
    private val earliestStartMs: Long = startOfDay(todayStartMs - (HISTORY_DAY_SPAN - 1L) * DAY_MS)

    private val _selectedDayStartMs = MutableStateFlow(todayStartMs)
    val selectedDayStartMs: StateFlow<Long> = _selectedDayStartMs

    /** 月历锚定到该月 1 日 0 点 */
    private val _visibleMonthStartMs = MutableStateFlow(monthStart(todayStartMs))
    val visibleMonthStartMs: StateFlow<Long> = _visibleMonthStartMs

    val isToday: StateFlow<Boolean> = _selectedDayStartMs
        .map { it == todayStartMs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val monitoredApps: StateFlow<List<AppInfo>> = appLimitRepository
        .getEnabledAppLimits()
        .map { limits -> loadAppInfoWithIcons(limits) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timeline: StateFlow<List<TimelineEvent>> = _selectedDayStartMs
        .flatMapLatest { dayStart ->
            val dayEnd = dayStart + DAY_MS
            combine(
                usageRecordRepository.getDayRecords(dayStart, dayEnd),
                appLimitRepository.getAllAppLimits(),
                sessionManager.currentSession
            ) { records, limits, activeSession ->
                buildTimelineEvents(
                    dayStart = dayStart,
                    dayEnd = dayEnd,
                    records = records,
                    limits = limits,
                    activeSession = if (dayStart == todayStartMs) activeSession else null
                )
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前可见月内有记录的日期（日 0 点） */
    val daysWithRecords: StateFlow<Set<Long>> = _visibleMonthStartMs
        .flatMapLatest { monthStart ->
            val monthEnd = nextMonthStart(monthStart)
            usageRecordRepository.getWeekRecords(monthStart, monthEnd).map { records ->
                records.mapTo(HashSet()) { startOfDay(it.startTime) }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun selectDay(dayStartMs: Long) {
        val clamped = dayStartMs.coerceIn(earliestStartMs, todayStartMs)
        _selectedDayStartMs.value = clamped
        _visibleMonthStartMs.value = monthStart(clamped)
    }

    fun shiftMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _visibleMonthStartMs.value
            add(Calendar.MONTH, delta)
        }
        val next = monthStart(cal.timeInMillis)
        val earliestMonth = monthStart(earliestStartMs)
        val latestMonth = monthStart(todayStartMs)
        _visibleMonthStartMs.value = next.coerceIn(earliestMonth, latestMonth)
    }

    fun canShiftMonth(delta: Int): Boolean {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _visibleMonthStartMs.value
            add(Calendar.MONTH, delta)
        }
        val next = monthStart(cal.timeInMillis)
        val earliestMonth = monthStart(earliestStartMs)
        val latestMonth = monthStart(todayStartMs)
        return next in earliestMonth..latestMonth
    }

    val earliestDayStartMs: Long get() = earliestStartMs
    val latestDayStartMs: Long get() = todayStartMs

    private suspend fun buildTimelineEvents(
        dayStart: Long,
        dayEnd: Long,
        records: List<UsageRecordEntity>,
        limits: List<AppLimitEntity>,
        activeSession: UsageSession?
    ): List<TimelineEvent> {
        val limitByPkg = limits.associateBy { it.packageName }
        val end = UsageRecordEntity.EndReason
        val usageEvents = records.map { record ->
            val limit = limitByPkg[record.packageName]
            val kind = IntentKind.fromStorage(record.intentKind)
            val gateQuit = record.isGateQuit
            val positiveExit = record.isPositiveExit
            val seed = record.isSeed
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
                compareEnabled = appPreferences.isAwarenessPracticeEnabled() &&
                    limit?.compareEnabled != false,
                compareMinMinutes = ComparePolicy.sanitizeMinMinutes(
                    limit?.compareMinMinutes ?: ComparePolicy.DEFAULT_MIN_MINUTES
                )
            )
        }

        val originStart = activeSession?.sessionOriginStartMs
            ?: activeSession?.startTime
            ?: 0L
        val ongoingEvent = if (
            activeSession != null &&
            activeSession.isInBackground &&
            activeSession.hasIntentGate &&
            originStart >= dayStart &&
            originStart < dayEnd
        ) {
            TimelineEvent.UsageEvent(
                packageName = activeSession.packageName,
                appName = activeSession.appName,
                startTime = originStart,
                endTime = -1L,
                durationSeconds = 0L,
                endReason = "",
                purpose = activeSession.purpose,
                recordId = activeSession.recordId,
                note = null,
                intentKind = activeSession.intentKind,
                hasIntentGate = activeSession.hasIntentGate,
                hasTimeLock = activeSession.hasTimeLock || activeSession.hasSessionLimit,
                compareEnabled = appPreferences.isAwarenessPracticeEnabled() &&
                    activeSession.compareEnabled,
                compareMinMinutes = activeSession.compareMinMinutes
            )
        } else {
            null
        }

        val all = if (ongoingEvent != null) usageEvents + ongoingEvent else usageEvents
        return all.sortedByDescending { it.timeMs }
    }

    private suspend fun resolveAppName(packageName: String): String =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName.substringAfterLast('.')
            }
        }

    private suspend fun loadAppInfoWithIcons(limits: List<AppLimitEntity>): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            limits.map { limit ->
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
                    isUninstalled = try {
                        pm.getApplicationInfo(limit.packageName, 0)
                        false
                    } catch (_: PackageManager.NameNotFoundException) {
                        true
                    }
                )
            }
        }

    companion object {
        const val HISTORY_DAY_SPAN = 90
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        fun startOfDay(ms: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = ms
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun monthStart(ms: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = ms
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun nextMonthStart(monthStartMs: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = monthStartMs
                add(Calendar.MONTH, 1)
            }
            return cal.timeInMillis
        }
    }
}
