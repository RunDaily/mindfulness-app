package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import java.util.Calendar

/**
 * 「用量」总览：默认滚动近 7 日（含今天）监控 App 心锚会话时长。
 * 与「这一周」次数总览同级、分家；门口等待不计。
 */
data class UsageOverviewSnapshot(
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val totalDurationSeconds: Long,
    val byApp: List<UsageOverviewAppRow>,
    val byDay: List<UsageOverviewDayRow>
) {
    val activeAppCount: Int get() = byApp.count { it.durationSeconds > 0L }

    fun avgDailySeconds(): Long {
        if (totalDurationSeconds <= 0L) return 0L
        return totalDurationSeconds / 7L
    }

    fun glanceLine(): String = when {
        totalDurationSeconds <= 0L -> "还没有用量"
        else -> formatOverviewDuration(totalDurationSeconds)
    }
}

data class UsageOverviewAppRow(
    val packageName: String,
    val appName: String,
    val durationSeconds: Long,
    val enterCount: Int
)

data class UsageOverviewDayRow(
    val dayStartMs: Long,
    val durationSeconds: Long,
    val topAppName: String = ""
)

enum class UsageOverviewCut {
    TOTAL,
    APP,
    DAY
}

fun computeUsageOverview(
    records: List<UsageRecordEntity>,
    rangeStartMs: Long,
    rangeEndMs: Long,
    appNames: Map<String, String> = emptyMap()
): UsageOverviewSnapshot {
    data class Acc(
        var duration: Long = 0L,
        var enter: Int = 0,
        var name: String = ""
    )

    val byApp = linkedMapOf<String, Acc>()
    val dayStarts = (0 until 7).map { i -> rangeStartMs + i * DAY_MS }
    val byDay = dayStarts.associateWith { Acc() }.toMutableMap()
    /** 日 → 包名 → 时长，用于「偏多」提示 */
    val dayAppDur = linkedMapOf<Long, MutableMap<String, Long>>()

    var total = 0L

    for (r in records) {
        if (r.isSeed || r.isPositiveExit) continue
        if (!UsageRecordCounts.isEnter(r)) continue
        if (r.startTime < rangeStartMs || r.startTime >= rangeEndMs) continue
        val dur = r.durationSeconds.coerceAtLeast(0L)
        if (dur <= 0L) continue

        val dayStart = dayStartOf(r.startTime)
        if (dayStart < rangeStartMs || dayStart >= rangeEndMs) continue

        total += dur
        val dayAcc = byDay.getOrPut(dayStart) { Acc() }
        dayAcc.duration += dur

        val appAcc = byApp.getOrPut(r.packageName) {
            Acc(
                name = appNames[r.packageName].orEmpty()
                    .ifBlank { r.packageName.substringAfterLast('.') }
            )
        }
        if (appAcc.name.isBlank()) {
            appAcc.name = appNames[r.packageName].orEmpty()
                .ifBlank { r.packageName.substringAfterLast('.') }
        }
        appAcc.duration += dur
        appAcc.enter++

        dayAppDur.getOrPut(dayStart) { linkedMapOf() }
            .merge(r.packageName, dur) { a, b -> a + b }
    }

    val appRows = byApp.entries
        .map { (pkg, a) ->
            UsageOverviewAppRow(
                packageName = pkg,
                appName = a.name,
                durationSeconds = a.duration,
                enterCount = a.enter
            )
        }
        .sortedByDescending { it.durationSeconds }

    val dayRows = dayStarts.map { start ->
        val a = byDay[start] ?: Acc()
        val topPkg = dayAppDur[start]
            ?.maxByOrNull { it.value }
            ?.key
            .orEmpty()
        val topName = when {
            topPkg.isBlank() -> ""
            else -> appNames[topPkg]
                ?: byApp[topPkg]?.name
                ?: topPkg.substringAfterLast('.')
        }
        UsageOverviewDayRow(
            dayStartMs = start,
            durationSeconds = a.duration,
            topAppName = topName
        )
    }

    return UsageOverviewSnapshot(
        rangeStartMs = rangeStartMs,
        rangeEndMs = rangeEndMs,
        totalDurationSeconds = total,
        byApp = appRows,
        byDay = dayRows
    )
}

/** 总览用时长文案：不足 1 时用「分」，否则「时」（可一位小数）。 */
fun formatOverviewDuration(seconds: Long): String {
    if (seconds <= 0L) return "0 分"
    val totalMin = seconds / 60L
    return when {
        totalMin < 60L -> "$totalMin 分"
        totalMin % 60L == 0L -> "${totalMin / 60L} 时"
        else -> String.format("%.1f 时", totalMin / 60.0)
    }
}

fun formatOverviewRangeLabel(startMs: Long, endMs: Long): String {
    if (startMs <= 0L || endMs <= startMs) return ""
    return "${formatOverviewMonthDay(startMs)} – ${formatOverviewMonthDay(endMs - 1L)}"
}

fun formatOverviewMonthDay(timeMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return "${cal.get(Calendar.MONTH) + 1} 月 ${cal.get(Calendar.DAY_OF_MONTH)} 日"
}

fun formatOverviewWeekdayShort(timeMs: Long): String {
    val week = arrayOf("日", "一", "二", "三", "四", "五", "六")
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return week[cal.get(Calendar.DAY_OF_WEEK) - 1]
}

private const val DAY_MS = 24L * 60L * 60L * 1000L

private fun dayStartOf(timeMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timeMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
