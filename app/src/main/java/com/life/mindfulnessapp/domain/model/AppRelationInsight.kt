package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import java.util.Calendar

/**
 * 意图门 App「关系页」：累计英雄指标 + 选定日的用量柱图 + 意图叙事。
 * 柱图 Y 轴触顶 = 该小时系统前台满 60 分钟。
 */
data class AppRelationInsight(
    val mindfulEnter: AppHeroMetric,
    val dismiss: AppHeroMetric,
    val dayStartMs: Long,
    val dayEndMs: Long,
    val dayOffset: Int,
    val isToday: Boolean,
    val canGoNext: Boolean,
    val dayLabel: String,
    /** 0–23 每小时前台秒数（展示时 ≥3600 触顶） */
    val hourlySeconds: LongArray,
    val dayUsageSeconds: Long,
    val dayEvents: List<AppMapEvent>,
    val nowMs: Long,
    val loadingHourly: Boolean = false
) {
    val hasUsageSignal: Boolean get() = dayUsageSeconds > 0L
    val hasNarrative: Boolean get() = dayEvents.isNotEmpty()
}

/** 累计总量 + 今日增量（对应设计稿「105 +12」） */
data class AppHeroMetric(
    val total: Int,
    val todayDelta: Int
)

enum class AppMapEventKind {
    /** 带着意图进入 */
    MindfulEnter,
    /** 守住离开 */
    GateQuit,
    /** 进入但无意图文案（少见） */
    Enter
}

data class AppMapEvent(
    val recordId: Long,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val dayIndex: Int,
    val kind: AppMapEventKind,
    val purpose: String?
) {
    val isGateQuit: Boolean get() = kind == AppMapEventKind.GateQuit
}

/**
 * @param dayOffset 0=今天，-1=昨天，以此类推（不可为正）
 * @param hourlySeconds 长度 24；缺省按空日
 */
fun buildAppRelationInsight(
    records: List<UsageRecordEntity>,
    hourlySeconds: LongArray = LongArray(24),
    nowMs: Long = System.currentTimeMillis(),
    dayOffset: Int = 0,
    liveSessionSeconds: Long = 0L,
    loadingHourly: Boolean = false
): AppRelationInsight {
    val offset = dayOffset.coerceAtMost(0)
    val nonSeed = records.filter { !it.isSeed && it.endTime > 0L }
    val (todayStart, todayEnd) = getDayRange(nowMs)
    val dayMs = 24L * 60 * 60 * 1000
    val dayStart = todayStart + offset * dayMs
    val dayEnd = dayStart + dayMs

    val todayRecords = nonSeed.filter { it.startTime in todayStart until todayEnd }
    val dayRecords = nonSeed.filter { it.startTime in dayStart until dayEnd }

    val liveMindfulBump = if (liveSessionSeconds > 0L && offset == 0) 1 else 0

    val mindfulTotal = UsageRecordCounts.mindfulEnterCount(nonSeed)
    val dismissTotal = UsageRecordCounts.dismissCount(nonSeed)
    val mindfulToday = UsageRecordCounts.mindfulEnterCount(todayRecords) + liveMindfulBump
    val dismissToday = UsageRecordCounts.dismissCount(todayRecords)

    val hours = if (hourlySeconds.size == 24) {
        hourlySeconds.copyOf()
    } else {
        LongArray(24) { i -> hourlySeconds.getOrElse(i) { 0L } }
    }
    val dayUsage = hours.sum()

    return AppRelationInsight(
        mindfulEnter = AppHeroMetric(
            total = mindfulTotal + liveMindfulBump,
            todayDelta = mindfulToday
        ),
        dismiss = AppHeroMetric(
            total = dismissTotal,
            todayDelta = dismissToday
        ),
        dayStartMs = dayStart,
        dayEndMs = dayEnd,
        dayOffset = offset,
        isToday = offset == 0,
        canGoNext = offset < 0,
        dayLabel = formatRelationDayLabel(dayStart, todayStart),
        hourlySeconds = hours,
        dayUsageSeconds = dayUsage,
        dayEvents = buildDayEvents(dayRecords),
        nowMs = nowMs,
        loadingHourly = loadingHourly
    )
}

private fun buildDayEvents(dayRecords: List<UsageRecordEntity>): List<AppMapEvent> {
    return dayRecords.mapNotNull { record ->
        val kind = when {
            record.isGateQuit -> AppMapEventKind.GateQuit
            UsageRecordCounts.isMindfulEnter(record) -> AppMapEventKind.MindfulEnter
            UsageRecordCounts.isEnter(record) -> AppMapEventKind.Enter
            else -> return@mapNotNull null
        }
        val end = if (record.endTime > record.startTime) {
            record.endTime
        } else {
            record.startTime + record.durationSeconds.coerceAtLeast(0L) * 1000L
        }
        AppMapEvent(
            recordId = record.id,
            startTime = record.startTime,
            endTime = end.coerceAtLeast(record.startTime),
            durationSeconds = record.durationSeconds.coerceAtLeast(0L),
            dayIndex = 0,
            kind = kind,
            purpose = record.purpose?.trim()?.takeIf { it.isNotEmpty() }
        )
    }.sortedBy { it.startTime }
}

/** 事件是否与某小时桶有交集（用于点柱筛列表） */
fun AppMapEvent.overlapsHour(dayStartMs: Long, hour: Int): Boolean {
    if (hour !in 0..23) return false
    val hourStart = dayStartMs + hour * 3_600_000L
    val hourEnd = hourStart + 3_600_000L
    val end = if (endTime > startTime) endTime else startTime + 1L
    return startTime < hourEnd && end > hourStart
}

fun formatRelationDayLabel(dayStartMs: Long, todayStartMs: Long): String {
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

fun formatRelationDuration(seconds: Long): String {
    if (seconds <= 0L) return "不到1分"
    val totalMin = seconds / 60L
    return when {
        totalMin < 60L -> "$totalMin 分"
        else -> {
            val h = totalMin / 60L
            val m = totalMin % 60L
            if (m == 0L) "${h} 小时" else "${h} 小时 $m 分"
        }
    }
}

fun formatRelationClock(timeMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
    return String.format(
        "%d:%02d",
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE)
    )
}

fun formatHeroDelta(delta: Int): String =
    if (delta > 0) "+$delta" else ""

/** 柱高比例：满小时 = 1f（触顶） */
fun hourUsageFillRatio(seconds: Long): Float =
    (seconds.toFloat() / 3_600f).coerceIn(0f, 1f)
