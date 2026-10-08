package com.life.mindfulnessapp.ui.features

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.AppDiary
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage
import com.life.mindfulnessapp.domain.model.SystemDayPeriodStats
import com.life.mindfulnessapp.domain.model.SystemForegroundSession
import com.life.mindfulnessapp.domain.model.SystemUsageDayDetail
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AppWeekRhythmUi(
    val app: AppInfo? = null,
    val days: List<SystemUsageDayDetail> = emptyList(),
    val rangeLabel: String = "",
    val loading: Boolean = true,
    val hasUsagePermission: Boolean = true,
    val selectedDayStartMs: Long? = null,
    /** true = 心锚会话；false = 系统用量 */
    val fromAnchor: Boolean = false,
    val dataSourceNote: String = ""
) {
    val selectedDay: SystemUsageDayDetail?
        get() {
            val key = selectedDayStartMs ?: return days.lastOrNull()
            return days.firstOrNull { it.dayStartMs == key } ?: days.lastOrNull()
        }
}

@HiltViewModel
class AppWeekRhythmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemUsageRepository: SystemUsageRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val packageName: String = checkNotNull(savedStateHandle["packageName"])

    private val _ui = MutableStateFlow(AppWeekRhythmUi())
    val ui: StateFlow<AppWeekRhythmUi> = _ui.asStateFlow()

    private var tracked = false

    init {
        load()
    }

    fun selectDay(dayStartMs: Long) {
        _ui.value = _ui.value.copy(selectedDayStartMs = dayStartMs)
    }

    fun trackViewIfNeeded() {
        if (tracked) return
        tracked = true
        analyticsRepository.trackAppWeekRhythmView(packageName)
    }

    fun load() {
        viewModelScope.launch {
            val hasPerm = checkPermissionsUseCase().hasUsageStats
            val app = getInstalledAppsUseCase.getApp(packageName)
                ?: AppInfo(
                    packageName = packageName,
                    appName = packageName.substringAfterLast('.'),
                    icon = null
                )
            val monitored = appLimitRepository.getAppLimit(packageName)?.takeIf { it.isEnabled }
            if (monitored == null && !hasPerm) {
                _ui.value = AppWeekRhythmUi(
                    app = app,
                    loading = false,
                    hasUsagePermission = false,
                    fromAnchor = false
                )
                return@launch
            }
            _ui.value = _ui.value.copy(app = app, loading = true, hasUsagePermission = true)
            val days = if (monitored != null) {
                buildAnchorDays()
            } else {
                systemUsageRepository
                    .getForegroundSessionsByDay(packageName, AppWeeklySystemUsage.DAYS)
                    .sortedBy { it.dayStartMs }
            }
            val fromAnchor = monitored != null
            val rangeLabel = formatRange(days)
            val defaultDay = days.lastOrNull()?.dayStartMs
            _ui.value = AppWeekRhythmUi(
                app = app,
                days = days,
                rangeLabel = rangeLabel,
                loading = false,
                hasUsagePermission = true,
                selectedDayStartMs = defaultDay,
                fromAnchor = fromAnchor,
                dataSourceNote = if (fromAnchor) {
                    AppDiary.DATA_SOURCE_NOTE
                } else {
                    "按系统用量统计 · 加入规则后改用心锚记录"
                }
            )
        }
    }

    private suspend fun buildAnchorDays(): List<SystemUsageDayDetail> {
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val dayMs = DAY_MS
        val rangeStart = todayStart - (AppWeeklySystemUsage.DAYS - 1) * dayMs
        val records = usageRecordRepository
            .getAppRecordsByPeriod(packageName, rangeStart, now)
            .first()
            .filter { !it.isSeed && !it.isGateQuit && !it.isPositiveExit }
        val weekdayFmt = SimpleDateFormat("M月d日 EEE", Locale.CHINA)
        val chartFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
        val yesterdayStart = todayStart - dayMs
        return (0 until AppWeeklySystemUsage.DAYS).map { offset ->
            val dStart = rangeStart + offset * dayMs
            val dEnd = dStart + dayMs
            val dayRecords = records.filter { it.startTime in dStart until dEnd }
            val sessions = dayRecords.mapNotNull { r ->
                val ongoing = r.endTime <= 0L
                val endMs = if (ongoing) now.coerceAtMost(dEnd) else r.endTime
                val durSec = if (ongoing) {
                    ((endMs - r.startTime) / 1000L).coerceAtLeast(0L)
                } else {
                    r.durationSeconds
                }
                if (!ongoing && durSec <= 0L) return@mapNotNull null
                if (ongoing && durSec <= 0L) return@mapNotNull null
                SystemForegroundSession(
                    startMs = r.startTime,
                    endMs = endMs,
                    durationSeconds = durSec,
                    ongoing = ongoing,
                    countsAsOpen = true
                )
            }.sortedBy { it.startMs }
            val isToday = dStart == todayStart
            val isYesterday = dStart == yesterdayStart
            val label = when {
                isToday -> "今天"
                isYesterday -> "昨天"
                else -> weekdayFmt.format(Date(dStart))
            }
            SystemUsageDayDetail(
                dayStartMs = dStart,
                label = label,
                chartDateLabel = chartFmt.format(Date(dStart)),
                isToday = isToday,
                isYesterday = isYesterday,
                sessions = sessions,
                totalSeconds = sessions.sumOf { it.durationSeconds },
                openCount = sessions.size,
                periods = SystemDayPeriodStats.Zero
            )
        }
    }

    private fun formatRange(days: List<SystemUsageDayDetail>): String {
        if (days.isEmpty()) return "近 7 日"
        val first = days.first().dayStartMs
        val last = days.last().dayStartMs
        val cal = Calendar.getInstance()
        cal.timeInMillis = first
        val m1 = cal.get(Calendar.MONTH) + 1
        val d1 = cal.get(Calendar.DAY_OF_MONTH)
        cal.timeInMillis = last
        val m2 = cal.get(Calendar.MONTH) + 1
        val d2 = cal.get(Calendar.DAY_OF_MONTH)
        return if (m1 == m2) {
            "${m1}月${d1}日 – ${d2}日"
        } else {
            "${m1}月${d1}日 – ${m2}月${d2}日"
        }
    }

    companion object {
        val DAY_MS: Long = 24L * 60L * 60L * 1000L

        private val weekdayShort = arrayOf("日", "一", "二", "三", "四", "五", "六")

        fun weekdayLetter(dayStartMs: Long): String {
            val cal = Calendar.getInstance()
            cal.timeInMillis = dayStartMs
            return weekdayShort[cal.get(Calendar.DAY_OF_WEEK) - 1]
        }

        fun dayOfMonth(dayStartMs: Long): String {
            val cal = Calendar.getInstance()
            cal.timeInMillis = dayStartMs
            return cal.get(Calendar.DAY_OF_MONTH).toString()
        }

        fun hourNowFraction(nowMs: Long = System.currentTimeMillis()): Float {
            val cal = Calendar.getInstance()
            cal.timeInMillis = nowMs
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            return (minutes / (24f * 60f)).coerceIn(0f, 1f)
        }
    }
}
