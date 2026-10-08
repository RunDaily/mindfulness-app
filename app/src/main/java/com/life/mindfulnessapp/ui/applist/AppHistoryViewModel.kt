package com.life.mindfulnessapp.ui.applist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.service.SessionCompareReminderWorker
import com.life.mindfulnessapp.service.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class AppHistoryDay(
    val dayStartMs: Long,
    val isToday: Boolean,
    val isYesterday: Boolean,
    val label: String,
    val events: List<TimelineEvent.UsageEvent>,
    val openCount: Int,
    val enterCount: Int,
    val totalSeconds: Long,
    val requireIntentOnOpen: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppHistoryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _packageName = MutableStateFlow<String?>(null)

    private val _appInfo = MutableStateFlow<AppInfo?>(null)
    val appInfo: StateFlow<AppInfo?> = _appInfo.asStateFlow()

    /** 历史页顶部：今日一行事实（时长与胶囊/限额同口径） */
    val todayGlance: StateFlow<AppTodayGlance?> = _packageName
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(null)
            } else {
                combine(
                    usageRecordRepository.getRecordsByApp(pkg),
                    appLimitRepository.getAllAppLimits(),
                    sessionManager.currentSession,
                    todayTickFlow()
                ) { records, limits, activeSession, now ->
                    val limit = limits.find { it.packageName == pkg }
                    val (todayStart, todayEnd) = getDayRange(now)
                    val todayRecords = records.filter {
                        it.endTime > 0 && it.startTime in todayStart until todayEnd
                    }
                    val liveSec = activeSession
                        ?.takeIf { it.packageName == pkg }
                        ?.currentSessionSeconds
                        ?: 0L
                    buildTodayGlance(
                        records = todayRecords,
                        requireIntentOnOpen = limit?.requireIntentOnOpen == true,
                        liveSessionSeconds = liveSec
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val days: StateFlow<List<AppHistoryDay>> = _packageName
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(emptyList())
            } else {
                combine(
                    usageRecordRepository.getRecordsByApp(pkg),
                    appLimitRepository.getAllAppLimits(),
                    sessionManager.currentSession,
                    todayTickFlow()
                ) { records, limits, activeSession, now ->
                    val limit = limits.find { it.packageName == pkg }
                    val appName = _appInfo.value?.appName
                        ?: limit?.appName
                        ?: pkg.substringAfterLast(".")
                    val (todayStart, _) = getDayRange(now)
                    val activeId = activeSession
                        ?.takeIf { it.packageName == pkg }
                        ?.takeIf { it.isInBackground && it.hasIntentGate }
                        ?.recordId
                    // 仅意图门暂停胶囊仍在时，未收口记录才当「进行中」展示
                    val visible = records.filter { record ->
                        !record.isSeed &&
                            (record.endTime > 0L || record.id == activeId)
                    }
                    buildHistoryDays(
                        records = visible,
                        appName = appName,
                        requireIntentOnOpen = limit?.requireIntentOnOpen == true,
                        timeLimitEnabled = limit?.timeLimitEnabled == true,
                        compareEnabled = limit?.compareEnabled != false,
                        compareMinMinutes = ComparePolicy.sanitizeMinMinutes(
                            limit?.compareMinMinutes ?: ComparePolicy.DEFAULT_MIN_MINUTES
                        ),
                        todayStartMs = todayStart
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(packageName: String) {
        _packageName.value = packageName
        viewModelScope.launch {
            sessionManager.closeOrphanedOpenRecords()
            _appInfo.value = withContext(Dispatchers.IO) {
                getInstalledAppsUseCase.getApp(packageName)
            }
        }
    }

    fun updateRecordReview(
        recordId: Long,
        note: String?,
        mindfulnessLevel: Int?,
        driftSeconds: Long? = null
    ) {
        viewModelScope.launch {
            val existing = usageRecordRepository.getRecordById(recordId)
            val level = mindfulnessLevel?.takeIf {
                UsageRecordEntity.MindfulnessLevel.isValid(it)
            }
            val resolvedDrift = com.life.mindfulnessapp.domain.model.DriftSecondsPolicy.resolveStored(
                level = level,
                driftSeconds = driftSeconds,
                durationSeconds = existing?.durationSeconds ?: 0L
            )
            usageRecordRepository.updateNoteAndMindfulness(
                id = recordId,
                note = note?.trim()?.ifBlank { null },
                mindfulnessLevel = level,
                driftSeconds = resolvedDrift
            )
            if (UsageRecordEntity.MindfulnessLevel.isValid(mindfulnessLevel)) {
                SessionCompareReminderWorker.cancel(appContext, recordId)
            }
        }
    }
}

fun buildTodayGlance(
    records: List<UsageRecordEntity>,
    requireIntentOnOpen: Boolean,
    liveSessionSeconds: Long = 0L
): AppTodayGlance {
    val nonSeed = records.filter { !it.isSeed }
    // 次数看真实进入；时长与限额/胶囊对齐：含 seed + 进行中会话
    val budgetSeconds = records.sumOf { it.durationSeconds.coerceAtLeast(0L) } +
        liveSessionSeconds.coerceAtLeast(0L)
    val enters = UsageRecordCounts.enterCount(nonSeed) +
        if (liveSessionSeconds > 0L) 1 else 0
    return AppTodayGlance(
        enterCount = enters,
        // 与 enterCount 同口径：产品「打开」= 真正进门，不含守住
        openCount = enters,
        dismissCount = UsageRecordCounts.dismissCount(nonSeed),
        mindfulEnterCount = UsageRecordCounts.mindfulEnterCount(nonSeed),
        totalSeconds = budgetSeconds,
        requireIntentOnOpen = requireIntentOnOpen
    )
}

internal fun toHistoryUsageEvent(
    record: UsageRecordEntity,
    appName: String,
    requireIntentOnOpen: Boolean,
    timeLimitEnabled: Boolean,
    compareEnabled: Boolean = ComparePolicy.DEFAULT_ENABLED,
    compareMinMinutes: Int = ComparePolicy.DEFAULT_MIN_MINUTES
): TimelineEvent.UsageEvent {
    val kind = IntentKind.fromStorage(record.intentKind)
    val gateQuit = record.isGateQuit
    val positiveExit = record.isPositiveExit
    val seed = record.isSeed
    val end = UsageRecordEntity.EndReason
    return TimelineEvent.UsageEvent(
        packageName = record.packageName,
        appName = appName,
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
                requireIntentOnOpen
            ),
        hasTimeLock = !seed && (
            record.endReason == end.LIMIT_REACHED ||
                record.endReason == end.SESSION_LIMIT_REACHED ||
                record.endReason == end.PERIOD_LOCK ||
                record.sessionLimitMinutes > 0 ||
                timeLimitEnabled
            ),
        sessionLimitMinutes = record.sessionLimitMinutes,
        sessionExtensionMinutes = record.sessionExtensionMinutes,
        compareEnabled = compareEnabled,
        compareMinMinutes = ComparePolicy.sanitizeMinMinutes(compareMinMinutes)
    )
}

private fun buildHistoryDays(
    records: List<UsageRecordEntity>,
    appName: String,
    requireIntentOnOpen: Boolean,
    timeLimitEnabled: Boolean,
    compareEnabled: Boolean,
    compareMinMinutes: Int,
    todayStartMs: Long
): List<AppHistoryDay> {
    if (records.isEmpty()) return emptyList()
    val dayMs = 24L * 60 * 60 * 1000
    val grouped = records.groupBy { getDayRange(it.startTime).first }
    return grouped.entries
        .sortedByDescending { it.key }
        .map { (dayStart, dayRecords) ->
            val events = dayRecords
                .sortedByDescending { it.startTime }
                .map {
                    toHistoryUsageEvent(
                        record = it,
                        appName = appName,
                        requireIntentOnOpen = requireIntentOnOpen,
                        timeLimitEnabled = timeLimitEnabled,
                        compareEnabled = compareEnabled,
                        compareMinMinutes = compareMinMinutes
                    )
                }
            val nonSeed = dayRecords.filter { !it.isSeed }
            val enters = UsageRecordCounts.enterCount(nonSeed)
            AppHistoryDay(
                dayStartMs = dayStart,
                isToday = dayStart == todayStartMs,
                isYesterday = dayStart == todayStartMs - dayMs,
                label = formatHistoryDayLabel(dayStart, todayStartMs, dayMs),
                events = events,
                openCount = enters,
                enterCount = enters,
                totalSeconds = dayRecords.sumOf { it.durationSeconds.coerceAtLeast(0L) },
                requireIntentOnOpen = requireIntentOnOpen
            )
        }
}

private fun formatHistoryDayLabel(dayStartMs: Long, todayStartMs: Long, dayMs: Long): String {
    val yesterdayStart = todayStartMs - dayMs
    return when (dayStartMs) {
        todayStartMs -> "今日"
        yesterdayStart -> "昨天"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
            val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val w = weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
            "${m}月${d}日 · 周$w"
        }
    }
}

private fun todayTickFlow() = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000L)
    }
}
