package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

/**
 * 使用记录计数口径（展示、限额、周报共用）。
 *
 * 产品文案「打开」= [enterCount] / [openQuotaCount]（真正进门），不含守住。
 *
 * - **打开 / 放行 [enterCount]**：真正进入目标 App 的会话（排除门外离开、加入前 seed、正向出口）
 * - **日开次配额 [openQuotaCount]**：已放行会话（含进行中）；与硬挡、方案「打开 a/b」对齐
 * - **克制 [dismissCount]**：拦截页离开（点离开 / Home 切走）；与打开互斥
 * - **有意图放行 [mindfulEnterCount]**：放行且写了有效意图（过程中补写也算）
 */
object UsageRecordCounts {

    fun isEnter(record: UsageRecordEntity): Boolean =
        !record.isGateQuit && !record.isSeed && !record.isPositiveExit

    fun isMindfulEnter(record: UsageRecordEntity): Boolean =
        isEnter(record) && !record.purpose.isNullOrBlank() &&
            PositiveExitKind.fromStorage(record.intentKind) == null

    fun enterCount(records: List<UsageRecordEntity>): Int =
        records.count { isEnter(it) }

    /**
     * 日开次。拦截页、次数硬挡、今日页用这一套。
     * 已放行（含进行中）都算；明确的门外离开、seed、正向出口不算。
     * 不用 [isGateQuit] 里 APP_CLOSED+0 秒的兼容分支——软门可无意图进入，
     * 那种会话若被误判成守住，次数会一直停在第 1 次。
     */
    fun openQuotaCount(records: List<UsageRecordEntity>): Int =
        records.count { countsTowardOpenQuota(it) }

    fun countsTowardOpenQuota(record: UsageRecordEntity): Boolean {
        if (record.isSeed || record.isPositiveExit) return false
        return when (record.endReason) {
            UsageRecordEntity.EndReason.GATE_DISMISS,
            UsageRecordEntity.EndReason.GATE_DISMISS_OWN_APP,
            UsageRecordEntity.EndReason.GATE_PASSIVE -> false
            else -> true
        }
    }

    fun dismissCount(records: List<UsageRecordEntity>): Int =
        records.count { it.isGateQuit }

    fun positiveExitCount(records: List<UsageRecordEntity>): Int =
        records.count { it.isPositiveExit }

    fun mindfulEnterCount(records: List<UsageRecordEntity>): Int =
        records.count { isMindfulEnter(it) }

    /** 有意图进入占比；无放行时返回 null */
    fun mindfulRatio(records: List<UsageRecordEntity>): Float? {
        val enters = enterCount(records)
        if (enters == 0) return null
        return mindfulEnterCount(records).toFloat() / enters
    }

    /**
     * 对照达成率：仅 ALIGNED / 有 mindfulnessLevel 的放行会话。
     * 样本不足 [minReviewed] 时返回 null。
     */
    fun alignmentRate(
        records: List<UsageRecordEntity>,
        minReviewed: Int = 3
    ): Float? {
        val reviewed = records.filter {
            isEnter(it) && UsageRecordEntity.MindfulnessLevel.isValid(it.mindfulnessLevel)
        }
        if (reviewed.size < minReviewed) return null
        val aligned = reviewed.count {
            it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.ALIGNED
        }
        return aligned.toFloat() / reviewed.size
    }

    fun reviewedEnterCount(records: List<UsageRecordEntity>): Int =
        records.count {
            isEnter(it) && UsageRecordEntity.MindfulnessLevel.isValid(it.mindfulnessLevel)
        }

    fun alignedEnterCount(records: List<UsageRecordEntity>): Int =
        records.count {
            isEnter(it) && it.mindfulnessLevel == UsageRecordEntity.MindfulnessLevel.ALIGNED
        }
}
