package com.life.mindfulnessapp.ui.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.DayReportTimeline
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.UsageSession
import com.life.mindfulnessapp.domain.model.computeDayReportTimeline
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DayReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val sessionManager: SessionManager,
    private val analyticsRepository: AnalyticsRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val dayCount: Int = DAY_COUNT

    /** 窄镜包名；空 = 全日 */
    val filterPackageName: String =
        savedStateHandle.get<String>(ARG_PACKAGE_NAME)?.trim().orEmpty()

    val isNarrow: Boolean get() = filterPackageName.isNotBlank()

    private val initialDateMs: Long =
        savedStateHandle.get<Long>(ARG_DATE_MS)
            ?: savedStateHandle.get<String>(ARG_DATE_MS)?.toLongOrNull()
            ?: 0L

    private val _selectedPage = MutableStateFlow(pageForDateMs(initialDateMs))
    val selectedPage: StateFlow<Int> = _selectedPage

    private var viewTracked = false

    val monitoredApps: StateFlow<List<AppInfo>> = appLimitRepository
        .getEnabledAppLimits()
        .map { limits -> loadAppInfoWithIcons(limits) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val timelineForPage = _selectedPage.flatMapLatest { page ->
        val dayStart = dayStartMs(page)
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
                activeSession = if (page == DAY_COUNT - 1) activeSession else null
            )
        }
    }.flowOn(Dispatchers.IO)

    val report: StateFlow<DayReportTimeline> = combine(
        _selectedPage,
        timelineForPage,
        monitoredApps
    ) { page, timeline, apps ->
        computeDayReportTimeline(
            timeline = timeline,
            monitoredApps = apps,
            dateMs = dayStartMs(page),
            filterPackageName = filterPackageName.ifBlank { null }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DayReportTimeline(
            dateMs = dayStartMs(DAY_COUNT - 1),
            entries = emptyList(),
            filterPackageName = filterPackageName
        )
    )

    val isToday: StateFlow<Boolean> = _selectedPage
        .map { it == DAY_COUNT - 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun trackViewIfNeeded() {
        if (viewTracked) return
        viewTracked = true
        analyticsRepository.trackDayReportView()
    }

    fun selectPage(page: Int) {
        _selectedPage.value = page.coerceIn(0, dayCount - 1)
    }

    fun goPrevDay() {
        selectPage(_selectedPage.value - 1)
    }

    fun goNextDay() {
        selectPage(_selectedPage.value + 1)
    }

    fun dayStartMs(page: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, page - (dayCount - 1))
        }
        return cal.timeInMillis
    }

    private fun pageForDateMs(dateMs: Long): Int {
        if (dateMs <= 0L) return DAY_COUNT - 1
        val target = Calendar.getInstance().apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        for (p in 0 until DAY_COUNT) {
            if (dayStartMs(p) == target) return p
        }
        return DAY_COUNT - 1
    }

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
                    periodLockEnabled = limit.periodLockEnabled,
                    periodWindowsJson = limit.periodWindowsJson,
                    periodLockCommitment = limit.periodLockCommitment,
                    isUninstalled = isUninstalled
                )
            }
        }

    companion object {
        const val DAY_COUNT = 90
        const val ARG_PACKAGE_NAME = "packageName"
        const val ARG_DATE_MS = "dateMs"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
