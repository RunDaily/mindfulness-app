package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import java.util.Calendar

/**
 * 周回望：把一周的意图门 / 对照 / 守住收成可感知的觉察结构。
 * 口径对齐首页今日判词；排除系统种子。
 */
data class WeekLookbackSnapshot(
    val weekStartMs: Long,
    val weekEndMs: Long,
    val isCurrentWeek: Boolean,
    val daysSinceFirstAnchor: Int?,
    val pulse: WeekPulse,
    val fulfillment: WeekFulfillment,
    val excerpts: List<WeekExcerpt>,
    /** 周一→周日共 7 天的轻量脉搏，供日分布展开 */
    val dayPulses: List<WeekDayPulse>,
    /** 与上一自然周的安静对照；锚龄不足或不该比时为 null */
    val vsPreviousWeek: WeekVsPrevious?,
    val overLimitDays: Int,
    val totalSeconds: Long,
    val verdict: WeekVerdict,
    val capabilityMode: WeekCapabilityMode
)

/** 单日轻脉搏（周内一格） */
data class WeekDayPulse(
    val dayStartMs: Long,
    /** 一 … 日 */
    val weekdayLabel: String,
    val mindfulEnters: Int,
    val ungatedEnters: Int,
    val dismisses: Int,
    val totalSeconds: Long
) {
    val activityCount: Int
        get() = mindfulEnters + ungatedEnters + dismisses

    val hasSignal: Boolean
        get() = activityCount > 0 || totalSeconds > 0L
}

/**
 * 周环比：只陈述事实差值，不做「进步/退步」评价。
 * [mindfulDelta] / [dismissDelta] / [secondsDelta] = 本周 − 上周。
 */
data class WeekVsPrevious(
    val mindfulDelta: Int?,
    val dismissDelta: Int?,
    val secondsDelta: Long?,
    val previousMindful: Int,
    val previousDismisses: Int,
    val previousSeconds: Long
) {
    val hasAnyComparable: Boolean
        get() = mindfulDelta != null || dismissDelta != null || secondsDelta != null
}

enum class WeekCapabilityMode {
    /** 未系锚 */
    Unmoored,
    /** 至少一款开了意图门 */
    IntentGate,
    /** 无意图门，有时长锁 */
    TimeLockOnly,
    /** 无意图门/时长锁，有时段锁 */
    PeriodLockOnly,
    /** 仅看着（能力全关） */
    WatchOnly
}

data class WeekPulse(
    val mindfulEnters: Int,
    val ungatedEnters: Int,
    val dismisses: Int,
    val enters: Int
) {
    val hasAnySignal: Boolean
        get() = mindfulEnters > 0 || ungatedEnters > 0 || dismisses > 0
}

data class WeekFulfillment(
    val reviewedCount: Int,
    val aligned: Int,
    val slight: Int,
    val large: Int
) {
    val hasAny: Boolean get() = reviewedCount > 0
}

data class WeekExcerpt(
    val recordId: Long,
    val packageName: String,
    val appName: String,
    val purpose: String?,
    val note: String?,
    val mindfulnessLevel: Int,
    val startTime: Long
)

enum class WeekVerdictTone {
    Still, Mindful, Drift, Bound, Alert, Unmoored
}

data class WeekVerdict(
    val kicker: String,
    val headline: String,
    val detail: String?,
    val tone: WeekVerdictTone,
    val collapsedText: String
)

fun resolveWeekCapabilityMode(limits: List<AppLimitEntity>): WeekCapabilityMode {
    if (limits.isEmpty()) return WeekCapabilityMode.Unmoored
    val hasIntent = limits.any { it.requireIntentOnOpen }
    val hasTime = limits.any { it.timeLimitEnabled }
    val hasPeriod = limits.any { it.periodLockEnabled }
    return when {
        hasIntent -> WeekCapabilityMode.IntentGate
        hasTime -> WeekCapabilityMode.TimeLockOnly
        hasPeriod -> WeekCapabilityMode.PeriodLockOnly
        else -> WeekCapabilityMode.WatchOnly
    }
}

fun computeWeekLookback(
    records: List<UsageRecordEntity>,
    limits: List<AppLimitEntity>,
    weekStartMs: Long,
    weekEndMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    previousWeekRecords: List<UsageRecordEntity>? = null
): WeekLookbackSnapshot {
    val mode = resolveWeekCapabilityMode(limits)
    val appNameMap = limits.associate { it.packageName to it.appName }
    val usable = records.filter { !it.isSeed && it.startTime in weekStartMs until weekEndMs }

    val dismisses = usable.count { it.isGateQuit }
    val enters = usable.filter { !it.isGateQuit && !it.isPositiveExit }
    val mindful = enters.count { isMindfulEnter(it) }
    val ungated = (enters.size - mindful).coerceAtLeast(0)
    val pulse = WeekPulse(
        mindfulEnters = mindful,
        ungatedEnters = ungated,
        dismisses = dismisses,
        enters = enters.size
    )

    val reviewed = enters.filter {
        UsageRecordEntity.MindfulnessLevel.isValid(it.mindfulnessLevel)
    }
    val fulfillment = WeekFulfillment(
        reviewedCount = reviewed.size,
        aligned = reviewed.count {
            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.ALIGNED
        },
        slight = reviewed.count {
            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.SLIGHT
        },
        large = reviewed.count {
            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.LARGE
        }
    )

    val totalSeconds = usable.filter { !it.isGateQuit && !it.isPositiveExit }
        .sumOf { it.durationSeconds.coerceAtLeast(0L) }
    val overLimitDays = countOverLimitDays(usable, limits, weekStartMs, weekEndMs)
    val dayPulses = buildWeekDayPulses(usable, weekStartMs)

    val firstAnchorAt = limits.minOfOrNull { it.createdAt }
    val daysSinceFirstAnchor = firstAnchorAt?.let { created ->
        val (createdDayStart, _) = getDayRange(created)
        val (todayStart, _) = getDayRange(nowMs)
        (((todayStart - createdDayStart) / (24L * 60 * 60 * 1000)).toInt()).coerceAtLeast(0)
    }

    val excerpts = pickExcerpts(enters, appNameMap)

    val vsPrevious = if (
        previousWeekRecords != null &&
        mode != WeekCapabilityMode.Unmoored &&
        (daysSinceFirstAnchor == null || daysSinceFirstAnchor >= 3)
    ) {
        computeVsPrevious(
            mode = mode,
            currentPulse = pulse,
            currentSeconds = totalSeconds,
            previousRecords = previousWeekRecords.filter { !it.isSeed },
            prevWeekStart = weekStartMs - 7L * 24 * 60 * 60 * 1000,
            prevWeekEnd = weekStartMs
        )
    } else {
        null
    }

    val verdict = computeWeekVerdict(
        mode = mode,
        pulse = pulse,
        fulfillment = fulfillment,
        totalSeconds = totalSeconds,
        overLimitDays = overLimitDays,
        daysSinceFirstAnchor = daysSinceFirstAnchor
    )

    return WeekLookbackSnapshot(
        weekStartMs = weekStartMs,
        weekEndMs = weekEndMs,
        isCurrentWeek = nowMs in weekStartMs until weekEndMs,
        daysSinceFirstAnchor = daysSinceFirstAnchor,
        pulse = pulse,
        fulfillment = fulfillment,
        excerpts = excerpts,
        dayPulses = dayPulses,
        vsPreviousWeek = vsPrevious,
        overLimitDays = overLimitDays,
        totalSeconds = totalSeconds,
        verdict = verdict,
        capabilityMode = mode
    )
}

private fun buildWeekDayPulses(
    usable: List<UsageRecordEntity>,
    weekStartMs: Long
): List<WeekDayPulse> {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    return (0 until 7).map { i ->
        val dayStart = weekStartMs + i * 24L * 60 * 60 * 1000
        val dayEnd = dayStart + 24L * 60 * 60 * 1000
        val dayRecords = usable.filter { it.startTime in dayStart until dayEnd }
        val dayDismisses = dayRecords.count { it.isGateQuit }
        val dayEnters = dayRecords.filter { !it.isGateQuit && !it.isPositiveExit }
        val dayMindful = dayEnters.count { isMindfulEnter(it) }
        WeekDayPulse(
            dayStartMs = dayStart,
            weekdayLabel = labels[i],
            mindfulEnters = dayMindful,
            ungatedEnters = (dayEnters.size - dayMindful).coerceAtLeast(0),
            dismisses = dayDismisses,
            totalSeconds = dayEnters.sumOf { it.durationSeconds.coerceAtLeast(0L) }
        )
    }
}

private fun computeVsPrevious(
    mode: WeekCapabilityMode,
    currentPulse: WeekPulse,
    currentSeconds: Long,
    previousRecords: List<UsageRecordEntity>,
    prevWeekStart: Long,
    prevWeekEnd: Long
): WeekVsPrevious? {
    val prev = previousRecords.filter { it.startTime in prevWeekStart until prevWeekEnd }
    if (prev.isEmpty() && currentPulse.enters == 0 && currentPulse.dismisses == 0 && currentSeconds == 0L) {
        return null
    }
    val prevDismisses = prev.count { it.isGateQuit }
    val prevEnters = prev.filter { !it.isGateQuit && !it.isPositiveExit }
    val prevMindful = prevEnters.count { isMindfulEnter(it) }
    val prevSeconds = prevEnters.sumOf { it.durationSeconds.coerceAtLeast(0L) }

    return when (mode) {
        WeekCapabilityMode.IntentGate -> WeekVsPrevious(
            mindfulDelta = currentPulse.mindfulEnters - prevMindful,
            dismissDelta = currentPulse.dismisses - prevDismisses,
            secondsDelta = null,
            previousMindful = prevMindful,
            previousDismisses = prevDismisses,
            previousSeconds = prevSeconds
        )
        WeekCapabilityMode.TimeLockOnly -> WeekVsPrevious(
            mindfulDelta = null,
            dismissDelta = null,
            secondsDelta = currentSeconds - prevSeconds,
            previousMindful = prevMindful,
            previousDismisses = prevDismisses,
            previousSeconds = prevSeconds
        )
        WeekCapabilityMode.PeriodLockOnly -> WeekVsPrevious(
            mindfulDelta = null,
            dismissDelta = currentPulse.dismisses - prevDismisses,
            secondsDelta = null,
            previousMindful = prevMindful,
            previousDismisses = prevDismisses,
            previousSeconds = prevSeconds
        )
        else -> WeekVsPrevious(
            mindfulDelta = null,
            dismissDelta = null,
            secondsDelta = currentSeconds - prevSeconds,
            previousMindful = prevMindful,
            previousDismisses = prevDismisses,
            previousSeconds = prevSeconds
        )
    }
}

private fun isMindfulEnter(record: UsageRecordEntity): Boolean {
    if (record.purpose.isNullOrBlank()) return false
    val kind = IntentKind.fromStorage(record.intentKind)
    return kind != IntentKind.PURPOSELESS
}

private fun countOverLimitDays(
    records: List<UsageRecordEntity>,
    limits: List<AppLimitEntity>,
    weekStartMs: Long,
    weekEndMs: Long
): Int {
    val locked = limits.filter { it.timeLimitEnabled && it.dailyLimitMinutes > 0 }
    if (locked.isEmpty()) return 0
    val limitSec = locked.associate { it.packageName to it.dailyLimitMinutes * 60L }
    var days = 0
    var dayStart = weekStartMs
    while (dayStart < weekEndMs) {
        val dayEnd = dayStart + 24L * 60 * 60 * 1000
        val dayRecords = records.filter {
            !it.isGateQuit && it.startTime in dayStart until dayEnd
        }
        val hit = limitSec.any { (pkg, limit) ->
            val used = dayRecords.filter { it.packageName == pkg }
                .sumOf { it.durationSeconds.coerceAtLeast(0L) }
            used >= limit
        }
        if (hit) days++
        dayStart = dayEnd
    }
    return days
}

private fun pickExcerpts(
    enters: List<UsageRecordEntity>,
    appNameMap: Map<String, String>
): List<WeekExcerpt> {
    val candidates = enters.filter {
        UsageRecordEntity.MindfulnessLevel.isValid(it.mindfulnessLevel)
    }
    if (candidates.isEmpty()) return emptyList()

    val ranked = candidates.sortedWith(
        compareByDescending<UsageRecordEntity> {
            !it.note.isNullOrBlank()
        }.thenByDescending {
            when (it.mindfulnessLevel) {
                UsageRecordEntity.MindfulnessLevel.LARGE -> 3
                UsageRecordEntity.MindfulnessLevel.SLIGHT -> 2
                UsageRecordEntity.MindfulnessLevel.ALIGNED -> 1
                else -> 0
            }
        }.thenByDescending { it.startTime }
    )

    return ranked.take(2).map { r ->
        WeekExcerpt(
            recordId = r.id,
            packageName = r.packageName,
            appName = appNameMap[r.packageName] ?: r.packageName,
            purpose = r.purpose?.trim()?.takeIf { it.isNotEmpty() },
            note = r.note?.trim()?.takeIf { it.isNotEmpty() },
            mindfulnessLevel = r.mindfulnessLevel!!,
            startTime = r.startTime
        )
    }
}

fun computeWeekVerdict(
    mode: WeekCapabilityMode,
    pulse: WeekPulse,
    fulfillment: WeekFulfillment,
    totalSeconds: Long,
    overLimitDays: Int,
    daysSinceFirstAnchor: Int? = null
): WeekVerdict {
    val timeText = if (totalSeconds <= 0L) null else formatWeekDuration(totalSeconds)
    val youngHint = if (daysSinceFirstAnchor != null && daysSinceFirstAnchor < 3) {
        "锚刚放下"
    } else {
        null
    }

    if (mode == WeekCapabilityMode.Unmoored) {
        return WeekVerdict(
            kicker = "周回望",
            headline = "还没有系上锚",
            detail = "点今日空坑，选一个要守护的 App",
            tone = WeekVerdictTone.Unmoored,
            collapsedText = "未系锚"
        )
    }

    val base = when (mode) {
        WeekCapabilityMode.IntentGate -> intentGateVerdict(pulse, fulfillment, timeText)
        WeekCapabilityMode.TimeLockOnly -> timeLockVerdict(overLimitDays, totalSeconds, timeText)
        WeekCapabilityMode.PeriodLockOnly -> periodLockVerdict(pulse)
        WeekCapabilityMode.WatchOnly -> WeekVerdict(
            kicker = "周回望",
            headline = if (totalSeconds <= 0L) "只在看着" else "看着 · ${timeText ?: ""}",
            detail = "还没开意图门、时长锁或时段锁",
            tone = WeekVerdictTone.Drift,
            collapsedText = timeText ?: "只在看着"
        )
        WeekCapabilityMode.Unmoored -> error("handled above")
    }

    if (youngHint == null) return base
    val mergedDetail = listOfNotNull(base.detail, youngHint).joinToString(" · ")
    return base.copy(detail = mergedDetail)
}

/**
 * 诚实优先：直进占进入 ≥ 60% 时，主句让位给直进；带着意图退到辅句。
 */
private fun intentGateVerdict(
    pulse: WeekPulse,
    fulfillment: WeekFulfillment,
    timeText: String?
): WeekVerdict {
    if (!pulse.hasAnySignal && timeText == null) {
        return WeekVerdict(
            kicker = "周回望",
            headline = "这一周水面很静",
            detail = "还没打开过受监控的 App",
            tone = WeekVerdictTone.Still,
            collapsedText = "水面静着"
        )
    }

    val ungatedDominates = pulse.enters > 0 &&
        pulse.ungatedEnters * 10 >= pulse.enters * 6

    // 直进主导：即使有带着意图，也不粉饰成「带着意图」周
    if (ungatedDominates && pulse.ungatedEnters > 0) {
        val detail = buildList {
            if (pulse.mindfulEnters > 0) add("带着意图 ${pulse.mindfulEnters}")
            if (pulse.dismisses > 0) add("守住 ${pulse.dismisses}")
            if (timeText != null) add("用了 $timeText")
        }.joinToString(" · ").ifBlank { null }
        return WeekVerdict(
            kicker = "周回望",
            headline = "直进了 ${pulse.ungatedEnters} 次",
            detail = detail,
            tone = WeekVerdictTone.Drift,
            collapsedText = "直进 ${pulse.ungatedEnters}"
        )
    }

    if (pulse.mindfulEnters > 0) {
        val alignedMajority = fulfillment.hasAny &&
            fulfillment.aligned * 2 >= fulfillment.reviewedCount
        val detail = buildList {
            if (pulse.dismisses > 0) add("守住 ${pulse.dismisses}")
            if (pulse.ungatedEnters > 0) add("直进 ${pulse.ungatedEnters}")
            if (timeText != null) add("用了 $timeText")
        }.joinToString(" · ").ifBlank { null }

        if (alignedMajority && fulfillment.reviewedCount >= 3) {
            return WeekVerdict(
                kicker = "周回望",
                headline = "多数没跑偏",
                detail = detail ?: "带着意图 · ${pulse.mindfulEnters}",
                tone = WeekVerdictTone.Mindful,
                collapsedText = "多数没跑偏"
            )
        }
        return WeekVerdict(
            kicker = "周回望",
            headline = "带着意图 · ${pulse.mindfulEnters}",
            detail = detail,
            tone = WeekVerdictTone.Mindful,
            collapsedText = "${pulse.mindfulEnters} 次意图"
        )
    }

    if (pulse.dismisses > 0 && pulse.enters == 0) {
        return WeekVerdict(
            kicker = "周回望",
            headline = "守住了 ${pulse.dismisses} 次",
            detail = "想打开，又在门外停住",
            tone = WeekVerdictTone.Bound,
            collapsedText = "守住 ${pulse.dismisses}"
        )
    }

    if (pulse.ungatedEnters > 0) {
        return WeekVerdict(
            kicker = "周回望",
            headline = "直进了 ${pulse.ungatedEnters} 次",
            detail = buildList {
                if (pulse.dismisses > 0) add("守住 ${pulse.dismisses}")
                if (timeText != null) add("用了 $timeText")
            }.joinToString(" · ").ifBlank { null },
            tone = WeekVerdictTone.Drift,
            collapsedText = "直进 ${pulse.ungatedEnters}"
        )
    }

    return WeekVerdict(
        kicker = "周回望",
        headline = "这一周水面很静",
        detail = timeText?.let { "用了 $it" },
        tone = WeekVerdictTone.Still,
        collapsedText = "水面静着"
    )
}

private fun timeLockVerdict(
    overLimitDays: Int,
    totalSeconds: Long,
    timeText: String?
): WeekVerdict = when {
    overLimitDays >= 3 -> WeekVerdict(
        kicker = "周回望",
        headline = "${overLimitDays} 天触顶",
        detail = timeText?.let { "已用 $it" },
        tone = WeekVerdictTone.Alert,
        collapsedText = "${overLimitDays}天触顶"
    )
    totalSeconds <= 0L -> WeekVerdict(
        kicker = "周回望",
        headline = "额度还完整",
        detail = "这一周还没打开过",
        tone = WeekVerdictTone.Still,
        collapsedText = "额度完整"
    )
    else -> WeekVerdict(
        kicker = "周回望",
        headline = "额度内",
        detail = timeText?.let { "已用 $it" },
        tone = WeekVerdictTone.Bound,
        collapsedText = timeText ?: "额度内"
    )
}

private fun periodLockVerdict(pulse: WeekPulse): WeekVerdict =
    if (pulse.dismisses > 0) {
        WeekVerdict(
            kicker = "周回望",
            headline = "守住了 ${pulse.dismisses} 次",
            detail = "在锁定时段停住了",
            tone = WeekVerdictTone.Bound,
            collapsedText = "守住 ${pulse.dismisses}"
        )
    } else {
        WeekVerdict(
            kicker = "周回望",
            headline = "时段锁在场",
            detail = "指定时段会硬挡进入",
            tone = WeekVerdictTone.Still,
            collapsedText = "时段锁"
        )
    }

/** 面向回望辅句的时长：6小时20分 / 42分 */
fun formatWeekDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        m > 0 -> "${m}分"
        else -> "不到1分"
    }
}

fun formatWeekRangeLabel(weekStartMs: Long, weekEndMs: Long): String {
    val startCal = Calendar.getInstance().apply { timeInMillis = weekStartMs }
    val endCal = Calendar.getInstance().apply {
        timeInMillis = weekEndMs - 1L
    }
    val startMonth = startCal.get(Calendar.MONTH) + 1
    val startDay = startCal.get(Calendar.DAY_OF_MONTH)
    val endMonth = endCal.get(Calendar.MONTH) + 1
    val endDay = endCal.get(Calendar.DAY_OF_MONTH)
    return if (startMonth == endMonth) {
        "${startMonth}月${startDay}日–${endDay}日"
    } else {
        "${startMonth}月${startDay}日–${endMonth}月${endDay}日"
    }
}

/** 周日 18:00 之后，或周六全天，展示周回望轻提示 */
fun shouldOfferSundayLookbackTip(nowMs: Long = System.currentTimeMillis()): Boolean {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> true
        Calendar.SUNDAY -> cal.get(Calendar.HOUR_OF_DAY) >= 18
        else -> false
    }
}

/** 首页日收束态：21:00 后把 kicker 换成「今日收束」，不改主句事实 */
fun shouldUseDayClosingKicker(nowMs: Long = System.currentTimeMillis()): Boolean {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    return cal.get(Calendar.HOUR_OF_DAY) >= 21
}

/** 环比差值文案：+3 / −2 / 持平；秒差用友好时长 */
fun formatWeekDeltaCount(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "−${-delta}"
    else -> "持平"
}

fun formatWeekDeltaSeconds(delta: Long): String = when {
    delta == 0L -> "持平"
    delta > 0L -> "+${formatWeekDuration(delta)}"
    else -> "−${formatWeekDuration(-delta)}"
}
