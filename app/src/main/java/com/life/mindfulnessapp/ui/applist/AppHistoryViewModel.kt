package com.life.mindfulnessapp.ui.applist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.TimelineEvent
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class AppHistoryDay(
    val dayStartMs: Long,
    val isToday: Boolean,
    val label: String,
    val events: List<TimelineEvent.UsageEvent>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppHistoryViewModel @Inject constructor(
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _packageName = MutableStateFlow<String?>(null)

    private val _appInfo = MutableStateFlow<AppInfo?>(null)
    val appInfo: StateFlow<AppInfo?> = _appInfo.asStateFlow()

    val days: StateFlow<List<AppHistoryDay>> = _packageName
        .flatMapLatest { pkg ->
            if (pkg == null) {
                flowOf(emptyList())
            } else {
                combine(
                    usageRecordRepository.getRecordsByApp(pkg),
                    appLimitRepository.getAllAppLimits(),
                    todayTickFlow()
                ) { records, limits, now ->
                    val limit = limits.find { it.packageName == pkg }
                    val appName = _appInfo.value?.appName
                        ?: limit?.appName
                        ?: pkg.substringAfterLast(".")
                    val (todayStart, _) = getDayRange(now)
                    buildHistoryDays(
                        records = records.filter { !it.isSeed },
                        appName = appName,
                        requireIntentOnOpen = limit?.requireIntentOnOpen == true,
                        timeLimitEnabled = limit?.timeLimitEnabled == true,
                        todayStartMs = todayStart
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(packageName: String) {
        _packageName.value = packageName
        viewModelScope.launch {
            _appInfo.value = withContext(Dispatchers.IO) {
                getInstalledAppsUseCase.getApp(packageName)
            }
        }
    }

    fun updateRecordReview(recordId: Long, note: String?, mindfulnessLevel: Int?) {
        viewModelScope.launch {
            usageRecordRepository.updateNoteAndMindfulness(
                id = recordId,
                note = note?.trim()?.ifBlank { null },
                mindfulnessLevel = mindfulnessLevel
            )
        }
    }
}

fun buildTodayGlance(
    records: List<UsageRecordEntity>,
    requireIntentOnOpen: Boolean
): AppTodayGlance {
    val nonSeed = records.filter { !it.isSeed }
    return AppTodayGlance(
        dismissCount = UsageRecordCounts.dismissCount(nonSeed),
        mindfulEnterCount = UsageRecordCounts.mindfulEnterCount(nonSeed),
        totalSeconds = nonSeed
            .filter { UsageRecordCounts.isEnter(it) }
            .sumOf { it.durationSeconds },
        requireIntentOnOpen = requireIntentOnOpen
    )
}

internal fun toHistoryUsageEvent(
    record: UsageRecordEntity,
    appName: String,
    requireIntentOnOpen: Boolean,
    timeLimitEnabled: Boolean
): TimelineEvent.UsageEvent {
    val kind = IntentKind.fromStorage(record.intentKind)
    val gateQuit = record.isGateQuit
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
        hasIntentGate = !seed && (
            gateQuit ||
                kind != null ||
                record.purpose != null ||
                requireIntentOnOpen
            ),
        hasTimeLock = !seed && (
            record.endReason == end.LIMIT_REACHED ||
                record.endReason == end.SESSION_LIMIT_REACHED ||
                record.sessionLimitMinutes > 0 ||
                timeLimitEnabled
            ),
        sessionLimitMinutes = record.sessionLimitMinutes,
        sessionExtensionMinutes = record.sessionExtensionMinutes
    )
}

private fun buildHistoryDays(
    records: List<UsageRecordEntity>,
    appName: String,
    requireIntentOnOpen: Boolean,
    timeLimitEnabled: Boolean,
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
                        timeLimitEnabled = timeLimitEnabled
                    )
                }
            AppHistoryDay(
                dayStartMs = dayStart,
                isToday = dayStart == todayStartMs,
                label = formatHistoryDayLabel(dayStart, todayStartMs, dayMs),
                events = events
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
