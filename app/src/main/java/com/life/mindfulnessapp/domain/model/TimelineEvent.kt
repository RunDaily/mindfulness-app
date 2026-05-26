package com.life.mindfulnessapp.domain.model

/**
 * 首页时间轴上的事件节点
 *
 * 两种类型：
 * - [UsageEvent]：一次完整的 App 使用记录（从打开到结束）
 * - [LimitResetEvent]：用户在超时后主动重新设定了限额（需要特殊高亮标注）
 *
 * [timeMs] 用于统一排序，代表事件的关键时间点。
 */
sealed class TimelineEvent {
    abstract val timeMs: Long
    abstract val packageName: String
    abstract val appName: String

    /**
     * 一次 App 使用记录
     */
    data class UsageEvent(
        override val packageName: String,
        override val appName: String,
        /** 使用开始时间 */
        val startTime: Long,
        /** 使用结束时间（-1 表示进行中）*/
        val endTime: Long,
        /** 有效使用时长（秒）*/
        val durationSeconds: Long,
        /** 结束原因 */
        val endReason: String,
        /** 使用目的（null 表示未填写目的）*/
        val purpose: String?,
        /** 数据库记录 id */
        val recordId: Long,
        /** 用户事后添加的效果备注（null 表示未填写）*/
        val note: String? = null
    ) : TimelineEvent() {
        override val timeMs: Long get() = startTime

        val isLimitReached: Boolean
            get() = endReason == com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.EndReason.LIMIT_REACHED

        val isOngoing: Boolean
            get() = endTime == -1L
    }

    /**
     * 「超时后重新设定限额」事件——需要在时间轴上特殊标注
     */
    data class LimitResetEvent(
        override val packageName: String,
        override val appName: String,
        /** 重设时间 */
        val resetTime: Long,
        /** 修改前每日限额（分钟）*/
        val oldDailyLimitMinutes: Int,
        /** 修改后每日限额（分钟）*/
        val newDailyLimitMinutes: Int,
        val oldWeeklyLimitMinutes: Int = 0,
        val newWeeklyLimitMinutes: Int = 0,
        val resetId: Long
    ) : TimelineEvent() {
        override val timeMs: Long get() = resetTime

        /** 延长了多少分钟 */
        val extendedMinutes: Int get() = newDailyLimitMinutes - oldDailyLimitMinutes
    }
}
