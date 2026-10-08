package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import java.util.Calendar

/**
 * 「这一周」总览：默认滚动近 7 日（含今天）门口决策切片。
 */
data class WeekOverviewSnapshot(
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val heldCount: Int,
    val searchCount: Int,
    val writeCount: Int,
    val browseCount: Int,
    val byApp: List<WeekOverviewAppRow>,
    val byDay: List<WeekOverviewDayRow>
) {
    val enterCount: Int get() = searchCount + writeCount + browseCount
    val totalGateEvents: Int get() = heldCount + enterCount

    fun glanceLine(): String = when {
        totalGateEvents <= 0 -> "还没有门口"
        else -> buildString {
            append("守住 $heldCount 次")
            if (browseCount > 0) append(" · 刷 $browseCount 次")
            else if (enterCount > 0) append(" · 进入 $enterCount 次")
        }
    }
}

data class WeekOverviewAppRow(
    val packageName: String,
    val appName: String,
    val heldCount: Int,
    val enterCount: Int,
    val durationSeconds: Long
)

data class WeekOverviewDayRow(
    val dayStartMs: Long,
    val heldCount: Int,
    val enterCount: Int,
    val searchCount: Int,
    val writeCount: Int,
    val browseCount: Int
) {
    val gateTotal: Int get() = heldCount + enterCount
}

enum class WeekOverviewCut {
    EVENT,
    APP,
    DAY
}

/** 含今天在内的滚动 7 个自然日：[start, end)。 */
fun rollingSevenDayRange(nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6)
    }
    val start = cal.timeInMillis
    cal.timeInMillis = nowMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return start to cal.timeInMillis
}

fun computeWeekOverview(
    records: List<UsageRecordEntity>,
    rangeStartMs: Long,
    rangeEndMs: Long,
    appNames: Map<String, String> = emptyMap()
): WeekOverviewSnapshot {
    var held = 0
    var search = 0
    var write = 0
    var browse = 0

    data class Acc(
        var held: Int = 0,
        var enter: Int = 0,
        var search: Int = 0,
        var write: Int = 0,
        var browse: Int = 0,
        var duration: Long = 0L,
        var name: String = ""
    )

    val byApp = linkedMapOf<String, Acc>()
    val dayStarts = (0 until 7).map { i ->
        rangeStartMs + i * DAY_MS
    }
    val byDay = dayStarts.associateWith { Acc() }.toMutableMap()

    for (r in records) {
        if (r.isSeed || r.isPositiveExit) continue
        if (r.startTime < rangeStartMs || r.startTime >= rangeEndMs) continue

        val dayStart = dayStartOf(r.startTime)
        if (dayStart < rangeStartMs || dayStart >= rangeEndMs) continue
        val dayAcc = byDay.getOrPut(dayStart) { Acc() }
        val appAcc = byApp.getOrPut(r.packageName) {
            Acc(name = appNames[r.packageName].orEmpty().ifBlank { r.packageName.substringAfterLast('.') })
        }
        if (appAcc.name.isBlank()) {
            appAcc.name = appNames[r.packageName].orEmpty()
                .ifBlank { r.packageName.substringAfterLast('.') }
        }

        when {
            r.isGateQuit -> {
                held++
                dayAcc.held++
                appAcc.held++
            }
            UsageRecordCounts.isEnter(r) -> {
                val kind = classifyDayEnterKind(
                    IntentKind.fromStorage(r.intentKind),
                    r.purpose
                )
                when (kind) {
                    DayEnterKind.SEARCH -> {
                        search++; dayAcc.search++; appAcc.search++
                    }
                    DayEnterKind.WRITE -> {
                        write++; dayAcc.write++; appAcc.write++
                    }
                    DayEnterKind.BROWSE -> {
                        browse++; dayAcc.browse++; appAcc.browse++
                    }
                }
                dayAcc.enter++
                appAcc.enter++
                val dur = r.durationSeconds.coerceAtLeast(0L)
                appAcc.duration += dur
            }
        }
    }

    val appRows = byApp.entries
        .map { (pkg, a) ->
            WeekOverviewAppRow(
                packageName = pkg,
                appName = a.name,
                heldCount = a.held,
                enterCount = a.enter,
                durationSeconds = a.duration
            )
        }
        .sortedWith(
            compareByDescending<WeekOverviewAppRow> { it.enterCount + it.heldCount }
                .thenByDescending { it.durationSeconds }
        )

    val dayRows = dayStarts.map { start ->
        val a = byDay[start] ?: Acc()
        WeekOverviewDayRow(
            dayStartMs = start,
            heldCount = a.held,
            enterCount = a.enter,
            searchCount = a.search,
            writeCount = a.write,
            browseCount = a.browse
        )
    }

    return WeekOverviewSnapshot(
        rangeStartMs = rangeStartMs,
        rangeEndMs = rangeEndMs,
        heldCount = held,
        searchCount = search,
        writeCount = write,
        browseCount = browse,
        byApp = appRows,
        byDay = dayRows
    )
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
