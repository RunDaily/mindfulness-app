package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange

/**
 * 日洞见：首页 / App 详情共用的「今日清醒」聚合。
 *
 * 四指标：有目的用量 · 守住 · 空转 · 总用量；可选 vs 昨日。
 */
data class MetricDelta(
    val today: Long,
    val yesterday: Long = 0L
) {
    val delta: Long get() = today - yesterday
    val hasYesterday: Boolean get() = yesterday > 0L || today > 0L
}

data class DayInsightSnapshot(
    val mindfulSeconds: MetricDelta,
    val idleSeconds: MetricDelta,
    val totalSeconds: MetricDelta,
    val dismissCount: MetricDelta,
    val enterCount: Int,
    val mindfulEnterCount: Int,
    val ungatedEnterCount: Int,
    val reviewedCount: Int,
    val alignedCount: Int,
    val slightCount: Int,
    val largeCount: Int,
    /** 空转最多的小时 0–23；无信号为 null */
    val idlePeakHour: Int?,
    val headline: String,
    val pitByPackage: Map<String, AppPitInsight>,
    val hasSignal: Boolean
)

data class AppPitInsight(
    val packageName: String,
    val mindfulSeconds: Long,
    val idleSeconds: Long,
    val totalSeconds: Long,
    val dismissCount: Int,
    val mindfulEnterCount: Int
) {
    val structureTotal: Long get() = (mindfulSeconds + idleSeconds).coerceAtLeast(0L)
    val mindfulRatio: Float
        get() {
            val t = structureTotal
            if (t <= 0L) return 0f
            return mindfulSeconds.toFloat() / t.toFloat()
        }
}

data class AppDayInsight(
    val packageName: String,
    val dayOffset: Int,
    val isToday: Boolean,
    val dayLabel: String,
    val mindfulSeconds: MetricDelta,
    val idleSeconds: MetricDelta,
    val totalSeconds: MetricDelta,
    val dismissCount: MetricDelta,
    val enterCount: Int,
    val mindfulEnterCount: Int,
    val reviewedCount: Int,
    val alignedCount: Int,
    val slightCount: Int,
    val largeCount: Int,
    val driftSecondsTotal: Long,
    val idlePeakHour: Int?,
    val headline: String,
    val hasSignal: Boolean
)

object DayInsightBuilder {

    fun buildHomeInsight(
        todayRecords: List<UsageRecordEntity>,
        yesterdayRecords: List<UsageRecordEntity>,
        liveSession: UsageSession? = null,
        nowMs: Long = System.currentTimeMillis()
    ): DayInsightSnapshot {
        val today = withLiveSession(todayRecords, liveSession, nowMs)
        val yesterday = yesterdayRecords.filter { !it.isSeed }

        val mindfulToday = IdleUsageMetrics.totalMindfulSeconds(today)
        val idleToday = IdleUsageMetrics.totalIdleSeconds(today)
        val totalToday = totalUsageSeconds(today)
        val dismissToday = UsageRecordCounts.dismissCount(today).toLong()

        val mindfulY = IdleUsageMetrics.totalMindfulSeconds(yesterday)
        val idleY = IdleUsageMetrics.totalIdleSeconds(yesterday)
        val totalY = totalUsageSeconds(yesterday)
        val dismissY = UsageRecordCounts.dismissCount(yesterday).toLong()

        val enters = today.filter { UsageRecordCounts.isEnter(it) }
        val mindfulEnters = UsageRecordCounts.mindfulEnterCount(today)
        val ungated = enters.count { IdleUsageMetrics.isUngatedEnter(it) || it.purpose.isNullOrBlank() }
        val reviewed = enters.filter {
            UsageRecordEntity.MindfulnessLevel.isValid(it.mindfulnessLevel)
        }
        val aligned = reviewed.count {
            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.ALIGNED
        }
        val slight = reviewed.count {
            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.SLIGHT
        }
        val large = reviewed.count {
            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.LARGE
        }

        val peak = idlePeakHour(today)
        val pits = today
            .groupBy { it.packageName }
            .mapValues { (pkg, list) -> buildPitInsight(pkg, list) }

        val hasSignal = totalToday > 0L || dismissToday > 0L || mindfulEnters > 0 ||
            UsageRecordCounts.positiveExitCount(today) > 0
        val headline = homeHeadline(
            hasSignal = hasSignal,
            mindful = mindfulToday,
            idle = idleToday,
            dismiss = dismissToday.toInt(),
            mindfulDelta = mindfulToday - mindfulY,
            idleDelta = idleToday - idleY,
            peakHour = peak,
            yesterdayHadSignal = totalY > 0L || dismissY > 0L
        )

        return DayInsightSnapshot(
            mindfulSeconds = MetricDelta(mindfulToday, mindfulY),
            idleSeconds = MetricDelta(idleToday, idleY),
            totalSeconds = MetricDelta(totalToday, totalY),
            dismissCount = MetricDelta(dismissToday, dismissY),
            enterCount = enters.size,
            mindfulEnterCount = mindfulEnters,
            ungatedEnterCount = ungated,
            reviewedCount = reviewed.size,
            alignedCount = aligned,
            slightCount = slight,
            largeCount = large,
            idlePeakHour = peak,
            headline = headline,
            pitByPackage = pits,
            hasSignal = hasSignal
        )
    }

    fun buildAppDayInsight(
        packageName: String,
        dayRecords: List<UsageRecordEntity>,
        yesterdayRecords: List<UsageRecordEntity>,
        dayOffset: Int,
        liveSession: UsageSession? = null,
        nowMs: Long = System.currentTimeMillis()
    ): AppDayInsight {
        val live = liveSession?.takeIf {
            dayOffset == 0 && it.packageName == packageName
        }
        val day = withLiveSession(
            dayRecords.filter { it.packageName == packageName && !it.isSeed },
            live,
            nowMs
        )
        val yday = yesterdayRecords.filter { it.packageName == packageName && !it.isSeed }

        val mindful = IdleUsageMetrics.totalMindfulSeconds(day)
        val idle = IdleUsageMetrics.totalIdleSeconds(day)
        val total = totalUsageSeconds(day)
        val dismiss = UsageRecordCounts.dismissCount(day).toLong()

        val mindfulY = IdleUsageMetrics.totalMindfulSeconds(yday)
        val idleY = IdleUsageMetrics.totalIdleSeconds(yday)
        val totalY = totalUsageSeconds(yday)
        val dismissY = UsageRecordCounts.dismissCount(yday).toLong()

        val enters = day.filter { UsageRecordCounts.isEnter(it) }
        val reviewed = enters.filter {
            UsageRecordEntity.MindfulnessLevel.isValid(it.mindfulnessLevel)
        }
        val driftTotal = day.sumOf {
            DriftSecondsPolicy.resolveStored(
                it.mindfulnessLevel,
                it.driftSeconds,
                it.durationSeconds
            ) ?: 0L
        }
        val peak = idlePeakHour(day)
        val (todayStart, _) = getDayRange(nowMs)
        val dayStart = todayStart + dayOffset.coerceAtMost(0) * 24L * 60 * 60 * 1000
        val hasSignal = total > 0L || dismiss > 0L
        val headline = appHeadline(
            hasSignal = hasSignal,
            isToday = dayOffset == 0,
            mindful = mindful,
            idle = idle,
            dismiss = dismiss.toInt(),
            mindfulDelta = if (dayOffset == 0) mindful - mindfulY else 0L,
            idleDelta = if (dayOffset == 0) idle - idleY else 0L,
            peakHour = peak,
            driftSeconds = driftTotal,
            compareYesterday = dayOffset == 0 && (totalY > 0L || dismissY > 0L)
        )

        return AppDayInsight(
            packageName = packageName,
            dayOffset = dayOffset.coerceAtMost(0),
            isToday = dayOffset == 0,
            dayLabel = formatRelationDayLabel(dayStart, todayStart),
            mindfulSeconds = MetricDelta(mindful, mindfulY),
            idleSeconds = MetricDelta(idle, idleY),
            totalSeconds = MetricDelta(total, totalY),
            dismissCount = MetricDelta(dismiss, dismissY),
            enterCount = enters.size,
            mindfulEnterCount = UsageRecordCounts.mindfulEnterCount(day),
            reviewedCount = reviewed.size,
            alignedCount = reviewed.count {
                it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.ALIGNED
            },
            slightCount = reviewed.count {
                it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.SLIGHT
            },
            largeCount = reviewed.count {
                it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.LARGE
            },
            driftSecondsTotal = driftTotal,
            idlePeakHour = peak,
            headline = headline,
            hasSignal = hasSignal
        )
    }

    fun buildPitInsight(packageName: String, records: List<UsageRecordEntity>): AppPitInsight {
        val list = records.filter { !it.isSeed }
        return AppPitInsight(
            packageName = packageName,
            mindfulSeconds = IdleUsageMetrics.totalMindfulSeconds(list),
            idleSeconds = IdleUsageMetrics.totalIdleSeconds(list),
            totalSeconds = totalUsageSeconds(list),
            dismissCount = UsageRecordCounts.dismissCount(list),
            mindfulEnterCount = UsageRecordCounts.mindfulEnterCount(list)
        )
    }

    /** 进行中会话并入当日记录，便于空转/有目的即时更新 */
    fun withLiveSession(
        records: List<UsageRecordEntity>,
        live: UsageSession?,
        nowMs: Long = System.currentTimeMillis()
    ): List<UsageRecordEntity> {
        if (live == null) return records.filter { !it.isSeed }
        val base = records.filter { !it.isSeed }.toMutableList()
        val idx = base.indexOfFirst { it.id == live.recordId }
        val liveDur = live.currentSessionSeconds.coerceAtLeast(0L)
        if (idx >= 0) {
            val existing = base[idx]
            base[idx] = existing.copy(
                durationSeconds = maxOf(existing.durationSeconds, liveDur),
                endTime = if (existing.endTime <= 0L) nowMs else existing.endTime,
                purpose = live.purpose ?: existing.purpose,
                intentKind = live.intentKind?.name ?: existing.intentKind
            )
        } else if (liveDur > 0L) {
            base += UsageRecordEntity(
                id = live.recordId,
                packageName = live.packageName,
                startTime = live.sessionOriginStartMs.takeIf { it > 0L } ?: live.startTime,
                endTime = nowMs,
                durationSeconds = liveDur,
                endReason = UsageRecordEntity.EndReason.UNKNOWN,
                purpose = live.purpose,
                intentKind = live.intentKind?.name
            )
        }
        return base
    }

    fun totalUsageSeconds(records: Iterable<UsageRecordEntity>): Long =
        records.sumOf { r ->
            if (IdleUsageMetrics.isGateDismiss(r) || IdleUsageMetrics.isSeed(r)) 0L
            else r.durationSeconds.coerceAtLeast(0L)
        }

    fun idlePeakHour(records: List<UsageRecordEntity>): Int? {
        val buckets = LongArray(24)
        records.forEach { r ->
            val idle = IdleUsageMetrics.idleSeconds(r)
            if (idle <= 0L) return@forEach
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = r.startTime
            }.get(java.util.Calendar.HOUR_OF_DAY)
            if (hour in 0..23) buckets[hour] += idle
        }
        var bestHour = -1
        var best = 0L
        buckets.forEachIndexed { h, s ->
            if (s > best) {
                best = s
                bestHour = h
            }
        }
        return if (bestHour >= 0 && best >= 5 * 60L) bestHour else null
    }

    fun homeHeadline(
        hasSignal: Boolean,
        mindful: Long,
        idle: Long,
        dismiss: Int,
        mindfulDelta: Long,
        idleDelta: Long,
        peakHour: Int?,
        yesterdayHadSignal: Boolean
    ): String {
        if (!hasSignal) return "今天还没有使用记录"
        // 1) 空转高峰最优先
        if (peakHour != null && idle >= 8 * 60L) {
            val peak = formatPeakHourLabel(peakHour) ?: "${peakHour}点"
            return when {
                dismiss >= 2 -> "$peak 前后空转偏多，但守住了 $dismiss 次"
                mindful > idle -> "$peak 有空转，整体仍偏有目的"
                else -> "$peak 前后空转偏多"
            }
        }
        // 2) 日环比：空转恶化 / 有目的变好 / 空转变好
        if (yesterdayHadSignal) {
            when {
                idleDelta >= 12 * 60L ->
                    return "空转比昨天多了 ${formatInsightMinutes(idleDelta)}"
                mindfulDelta >= 12 * 60L && mindfulDelta >= idleDelta ->
                    return "有目的用量比昨天多 ${formatInsightMinutes(mindfulDelta)}"
                idleDelta <= -12 * 60L ->
                    return "空转比昨天少了 ${formatInsightMinutes(-idleDelta)}"
                dismiss >= 3 && idleDelta <= 0L ->
                    return "今天守住了 $dismiss 次"
            }
        }
        // 3) 守住亮眼
        if (dismiss >= 3 && idle <= mindful) {
            return "今天守住了 $dismiss 次"
        }
        // 4) 结构对照
        if (idle > mindful && idle >= 8 * 60L) {
            return "空转 ${formatInsightMinutes(idle)}，有目的 ${formatInsightMinutes(mindful)}"
        }
        if (mindful > 0L) {
            return "有目的用了 ${formatInsightMinutes(mindful)}" +
                if (dismiss > 0) " · 守住 $dismiss" else ""
        }
        if (dismiss > 0) return "守住了 $dismiss 次"
        return "今天用了 ${formatInsightMinutes(mindful + idle)}"
    }

    fun appHeadline(
        hasSignal: Boolean,
        isToday: Boolean,
        mindful: Long,
        idle: Long,
        dismiss: Int,
        mindfulDelta: Long,
        idleDelta: Long,
        peakHour: Int?,
        driftSeconds: Long,
        compareYesterday: Boolean
    ): String {
        if (!hasSignal) {
            return if (isToday) "今天还没打开" else "这天没有记录"
        }
        if (peakHour != null && idle >= 6 * 60L) {
            val start = formatPeakHourLabel(peakHour) ?: "${peakHour}:00"
            val end = formatPeakHourLabel((peakHour + 1) % 24) ?: ""
            return "$start–$end 空转集中" +
                if (dismiss > 0) " · 守住 $dismiss" else ""
        }
        if (isToday && compareYesterday) {
            when {
                idleDelta >= 8 * 60L ->
                    return "空转比昨天多 ${formatInsightMinutes(idleDelta)}"
                mindfulDelta >= 8 * 60L ->
                    return "有目的比昨天多 ${formatInsightMinutes(mindfulDelta)}"
                idleDelta <= -8 * 60L ->
                    return "空转比昨天少 ${formatInsightMinutes(-idleDelta)}"
            }
        }
        if (driftSeconds >= 5 * 60L) {
            return "对照里跑偏约 ${formatInsightMinutes(driftSeconds)}"
        }
        if (dismiss >= 2) return "守住了 $dismiss 次"
        if (idle > mindful && idle >= 5 * 60L) {
            return "空转 ${formatInsightMinutes(idle)}"
        }
        if (mindful > 0L) return "有目的 ${formatInsightMinutes(mindful)}"
        return "用了 ${formatInsightMinutes(mindful + idle)}"
    }
}

fun formatInsightMinutes(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val m = (s + 30L) / 60L
    return when {
        m < 1L -> "不到1分钟"
        m < 60L -> "${m}分钟"
        else -> {
            val h = m / 60L
            val rem = m % 60L
            if (rem == 0L) "${h}小时" else "${h}小时${rem}分"
        }
    }
}

fun formatInsightDeltaArrow(delta: Long): String? {
    if (delta == 0L) return null
    val abs = formatInsightMinutes(kotlin.math.abs(delta))
    return if (delta > 0L) "↑$abs" else "↓$abs"
}

fun formatPeakHourLabel(hour: Int?): String? {
    if (hour == null || hour !in 0..23) return null
    return String.format("%02d:00", hour)
}
