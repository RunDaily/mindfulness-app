package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getWeekRange
import java.util.Calendar

enum class TimeRulerMode { Day, Week }

/**
 * 一日四段（与常见「使用详情」认知对齐，等分 6 小时，节奏清晰）。
 * 深夜 0–6 · 上午 6–12 · 下午 12–18 · 晚上 18–24
 */
enum class TimeRulerPeriod(
    val label: String,
    val hourStart: Int,
    val hourEnd: Int
) {
    Night("深夜", 0, 6),
    Morning("上午", 6, 12),
    Afternoon("下午", 12, 18),
    Evening("晚上", 18, 24);

    val hourCount: Int get() = hourEnd - hourStart

    companion object {
        fun ofHour(hour: Int): TimeRulerPeriod = when (hour) {
            in 0 until 6 -> Night
            in 6 until 12 -> Morning
            in 12 until 18 -> Afternoon
            else -> Evening
        }
    }
}

/** 系统前台色块（同包内不重叠；多包用分道并排） */
data class TimeRulerBlock(
    val blockId: Long,
    val packageName: String,
    val appName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val dayIndex: Int,
    val countsAsOpen: Boolean
)

/** 单日汇总（所选 App 合计） */
data class TimeRulerDaySummary(
    val totalSeconds: Long,
    val openCount: Int
) {
    companion object {
        val Zero = TimeRulerDaySummary(0L, 0)
    }
}

/** 周视图里的一天摘要 */
data class TimeRulerWeekDay(
    val dayStartMs: Long,
    val dayIndex: Int,
    val weekdayLabel: String,
    val dateLabel: String,
    val isToday: Boolean,
    val isFuture: Boolean,
    val summary: TimeRulerDaySummary,
    val blocks: List<TimeRulerBlock>
)

fun timeRulerBlockId(packageName: String, startTime: Long): Long =
    (packageName.hashCode().toLong() shl 32) xor startTime

fun buildTimeRulerBlocks(
    sessionsByPackage: Map<String, List<SystemForegroundSession>>,
    selectedPackages: Set<String>,
    appNames: Map<String, String>,
    rangeStartMs: Long,
    mode: TimeRulerMode
): List<TimeRulerBlock> {
    val dayMs = 24L * 60 * 60 * 1000
    return selectedPackages
        .asSequence()
        .flatMap { pkg ->
            sessionsByPackage[pkg].orEmpty().asSequence().mapNotNull { session ->
                if (session.endMs <= rangeStartMs) return@mapNotNull null
                val start = session.startMs.coerceAtLeast(rangeStartMs)
                val end = session.endMs
                if (end <= start) return@mapNotNull null
                val dayIndex = when (mode) {
                    TimeRulerMode.Day -> 0
                    TimeRulerMode.Week ->
                        ((start - rangeStartMs) / dayMs).toInt().coerceIn(0, 6)
                }
                TimeRulerBlock(
                    blockId = timeRulerBlockId(pkg, session.startMs),
                    packageName = pkg,
                    appName = appNames[pkg] ?: pkg.substringAfterLast('.'),
                    startTime = start,
                    endTime = end,
                    durationSeconds = session.durationSeconds.coerceAtLeast((end - start) / 1000L),
                    dayIndex = dayIndex,
                    countsAsOpen = session.countsAsOpen
                )
            }
        }
        .sortedBy { it.startTime }
        .toList()
}

fun summarizeTimeRulerBlocks(blocks: List<TimeRulerBlock>): TimeRulerDaySummary =
    TimeRulerDaySummary(
        totalSeconds = blocks.sumOf { it.durationSeconds },
        openCount = blocks.count { it.countsAsOpen }
    )

fun buildTimeRulerWeekDays(
    weekStartMs: Long,
    todayStartMs: Long,
    blocks: List<TimeRulerBlock>
): List<TimeRulerWeekDay> {
    val dayMs = 24L * 60 * 60 * 1000
    val weekdays = arrayOf("一", "二", "三", "四", "五", "六", "日")
    return (0 until 7).map { i ->
        val dayStart = weekStartMs + i * dayMs
        val dayBlocks = blocks.filter { it.dayIndex == i }
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        TimeRulerWeekDay(
            dayStartMs = dayStart,
            dayIndex = i,
            weekdayLabel = "周${weekdays[i]}",
            dateLabel = "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}",
            isToday = dayStart == todayStartMs,
            isFuture = dayStart > todayStartMs,
            summary = summarizeTimeRulerBlocks(dayBlocks),
            blocks = dayBlocks
        )
    }
}

fun timeRulerDayLabel(dayStartMs: Long, todayStartMs: Long): String {
    val dayMs = 24L * 60 * 60 * 1000
    return when (dayStartMs) {
        todayStartMs -> "今天"
        todayStartMs - dayMs -> "昨天"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
            val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            val w = weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
            "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 周$w"
        }
    }
}

fun timeRulerWeekLabel(weekStartMs: Long, weekEndMs: Long): String {
    val s = Calendar.getInstance().apply { timeInMillis = weekStartMs }
    val e = Calendar.getInstance().apply { timeInMillis = weekEndMs - 1L }
    val sm = s.get(Calendar.MONTH) + 1
    val sd = s.get(Calendar.DAY_OF_MONTH)
    val em = e.get(Calendar.MONTH) + 1
    val ed = e.get(Calendar.DAY_OF_MONTH)
    return if (sm == em) "${sm}月${sd}日–${ed}日" else "${sm}月${sd}日–${em}月${ed}日"
}

fun timeRulerClock(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

fun timeRulerDuration(seconds: Long): String {
    if (seconds <= 0L) return "0分"
    val m = seconds / 60L
    val s = seconds % 60L
    return when {
        m <= 0L -> "${s}秒"
        m < 60L && s == 0L -> "${m}分"
        m < 60L -> "${m}分${s}秒"
        else -> {
            val h = m / 60L
            val rm = m % 60L
            if (rm == 0L) "${h}小时" else "${h}小时${rm}分"
        }
    }
}

fun timeRulerRangeLabel(startMs: Long, endMs: Long): String =
    "${timeRulerClock(startMs)} – ${timeRulerClock(endMs)}"

fun shiftTimeRulerAnchor(anchorMs: Long, mode: TimeRulerMode, delta: Int): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
    when (mode) {
        TimeRulerMode.Day -> cal.add(Calendar.DAY_OF_YEAR, delta)
        TimeRulerMode.Week -> cal.add(Calendar.WEEK_OF_YEAR, delta)
    }
    return cal.timeInMillis
}

fun resolveTimeRulerRange(anchorMs: Long, mode: TimeRulerMode): Pair<Long, Long> =
    when (mode) {
        TimeRulerMode.Day -> getDayRange(anchorMs)
        TimeRulerMode.Week -> getWeekRange(anchorMs)
    }
