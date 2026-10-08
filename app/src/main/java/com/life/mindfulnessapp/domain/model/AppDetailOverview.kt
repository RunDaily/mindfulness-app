package com.life.mindfulnessapp.domain.model

/**
 * App 详情「概览」：今日 / 本周 / 累计 / 均值 + 正念分布 + 日用量日历。
 */
data class AppUsageOverview(
    val todaySeconds: Long = 0L,
    val weekSeconds: Long = 0L,
    val monthSeconds: Long = 0L,
    /** 累计总会话时长（已结束） */
    val totalSeconds: Long = 0L,
    val todayEnterCount: Int = 0,
    val weekEnterCount: Int = 0,
    val todayMindfulCount: Int = 0,
    val todayDismissCount: Int = 0,
    /** 关系以来放行进入总次数 */
    val totalEnterCount: Int = 0,
    /** 关系以来有意图进入总次数 */
    val totalMindfulCount: Int = 0,
    /** 平均单次会话秒数（已结束放行会话） */
    val avgSessionSeconds: Long = 0L,
    /** 正念对照三档计数 */
    val alignedCount: Int = 0,
    val slightCount: Int = 0,
    val largeCount: Int = 0,
    /** dayStartMs → 当日已结束会话秒数 */
    val daySecondsByDayStart: Map<Long, Long> = emptyMap()
) {
    val reviewedCount: Int get() = alignedCount + slightCount + largeCount
}

enum class GuardEventKind {
    /** 意图门前守住离开 */
    GateQuit,
    /** 时长锁拦住 / 到点 */
    TimeLock,
    /** 时段锁拦住 */
    PeriodLock
}

data class GuardEvent(
    val recordId: Long,
    val startTime: Long,
    val kind: GuardEventKind,
    val durationSeconds: Long = 0L,
    val purpose: String? = null
) {
    val label: String
        get() = when (kind) {
            GuardEventKind.GateQuit -> "守住了，没进去"
            GuardEventKind.TimeLock -> "时长锁拦截"
            GuardEventKind.PeriodLock -> "时段锁拦截"
        }
}

data class GuardOverview(
    val gateQuitCount: Int = 0,
    val timeLockCount: Int = 0,
    val periodLockCount: Int = 0,
    val recent: List<GuardEvent> = emptyList()
) {
    val totalCount: Int get() = gateQuitCount + timeLockCount + periodLockCount
}
