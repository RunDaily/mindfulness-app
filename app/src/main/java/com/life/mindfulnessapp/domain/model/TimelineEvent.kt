package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

/**
 * 首页时间轴上的事件节点
 *
 * 两种类型：
 * - [UsageEvent]：一次完整的 App 使用记录（从打开到结束）
 * - [LimitResetEvent]：用户在超时后主动重新设定了限额（需要特殊高亮标注）
 *
 * [timeMs] 用于统一排序，代表事件的关键时间点。
 *
 * 展示层另见 [collapseTimelineForDisplay]：连续门外停下可视觉合并。
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
        /** 用户事后对照意图写下的复盘（null 表示未填写）*/
        val note: String? = null,
        /** 正念程度三档（见 UsageRecordEntity.MindfulnessLevel；null 表示未评）*/
        val mindfulnessLevel: Int? = null,
        /** 意图类型；旧数据可为 null；PURPOSELESS 仅历史旁路遗留 */
        val intentKind: IntentKind? = null,
        /** 本条会话是否启用了意图门 */
        val hasIntentGate: Boolean = false,
        /** 本条会话是否启用了时长锁 */
        val hasTimeLock: Boolean = false,
        /** 本次会话时长上限（分钟）；0 表示未设单次上限 */
        val sessionLimitMinutes: Int = 0,
        /** 本次已续时分钟数 */
        val sessionExtensionMinutes: Int = 0
    ) : TimelineEvent() {
        override val timeMs: Long get() = startTime

        val isLimitReached: Boolean
            get() = endReason == UsageRecordEntity.EndReason.LIMIT_REACHED ||
                endReason == UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED

        /** 单次意图时长锁触顶 */
        val isSessionLimitReached: Boolean
            get() = endReason == UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED

        /**
         * 相对「原定单次上限」的超出秒数（续时也计入超出）；无法计算或未超出时为 null。
         * 用于时长后的黄色 `+xx:yy`。
         */
        val sessionOvertimeSeconds: Long?
            get() {
                if (!isSessionLimitReached || sessionLimitMinutes <= 0) return null
                val over = durationSeconds - sessionLimitMinutes * 60L
                return over.takeIf { it > 0L }
            }

        val isOngoing: Boolean
            get() = endTime == -1L

        /** 意图门拦下后离开，未真正进入 */
        val isGateQuit: Boolean
            get() = UsageRecordEntity.EndReason.isGateQuit(purpose, endReason, durationSeconds)

        /** 门外停下后打开了心锚（相对离开回桌面） */
        val isGateDismissToOwnApp: Boolean
            get() = isGateQuit &&
                UsageRecordEntity.EndReason.isGateDismissToOwnApp(endReason)

        /** 系统用量种子（加入监控前的回填） */
        val isSeed: Boolean
            get() = UsageRecordEntity.EndReason.isSeed(endReason)

        /** 有意图并进入（含进行中的有意图会话） */
        val isMindful: Boolean
            get() = purpose != null

        /** 无意图的实际使用（仅时长锁直进等）；缺意图 ≠ 克制 */
        val isDirectEntry: Boolean
            get() = !isOngoing && !isLimitReached && !isGateQuit && !isSeed &&
                purpose == null && intentKind != IntentKind.PURPOSELESS

        /**
         * 门外停下的一行说明文案。
         * 非门外停下返回 null。
         */
        val gateQuitLine: String?
            get() = when {
                !isGateQuit -> null
                isGateDismissToOwnApp -> "到了心锚 · $appName"
                else -> "离开了 · $appName"
            }

        /**
         * 意图门情景下的意图行文案；非意图门情景返回 null。
         * 历史无目的旁路记录固定为「没有目的」。
         */
        val intentLine: String?
            get() {
                if (isGateQuit || isSeed || !hasIntentGate) return null
                val trimmed = purpose?.trim().orEmpty()
                if (trimmed.isNotEmpty() && intentKind != IntentKind.PURPOSELESS) return trimmed
                return "没有目的"
            }

        /**
         * 非标准闭环的轻量结束标注；标准结束（主动结束 / 触顶 / 门外停下 / 种子）不展示。
         * 优先用 [UsageRecordEntity.EndReason.displayKindLabel] 的中断短因。
         */
        val softEndReasonLabel: String?
            get() {
                if (isOngoing || isGateQuit || isLimitReached || isSeed) return null
                if (endReason == UsageRecordEntity.EndReason.MANUAL) return null
                return UsageRecordEntity.EndReason.displayKindLabel(endReason)
                    ?: UsageRecordEntity.EndReason.softEndReasonLabel(endReason)
            }
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

        /** 日限额延长了多少分钟 */
        val extendedMinutes: Int get() = newDailyLimitMinutes - oldDailyLimitMinutes

        /** 周限额延长了多少分钟 */
        val extendedWeeklyMinutes: Int get() = newWeeklyLimitMinutes - oldWeeklyLimitMinutes

        val dailyChanged: Boolean get() = oldDailyLimitMinutes != newDailyLimitMinutes
        val weeklyChanged: Boolean get() = oldWeeklyLimitMinutes != newWeeklyLimitMinutes
    }
}
