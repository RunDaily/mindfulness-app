package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

/**
 * 使用记录计数口径（展示、限额、周报共用）。
 *
 * - **放行 [enterCount]**：真正进入目标 App 的会话（排除门外离开、加入前 seed）
 * - **克制 [dismissCount]**：拦截页离开（点离开 / 打开心锚 / Home 切走）
 * - **有意图放行 [mindfulEnterCount]**：放行且写了有效意图
 * - **意图门打开 [intentOpenCount]**：带意图进入 + 离开（一次拦截的两种结果）
 */
object UsageRecordCounts {

    fun isEnter(record: UsageRecordEntity): Boolean =
        !record.isGateQuit && !record.isSeed

    fun isMindfulEnter(record: UsageRecordEntity): Boolean =
        isEnter(record) && !record.purpose.isNullOrBlank()

    fun enterCount(records: List<UsageRecordEntity>): Int =
        records.count { isEnter(it) }

    fun dismissCount(records: List<UsageRecordEntity>): Int =
        records.count { it.isGateQuit }

    fun mindfulEnterCount(records: List<UsageRecordEntity>): Int =
        records.count { isMindfulEnter(it) }

    /** 意图门「打开 App」次数 = 带意图进入 + 离开 */
    fun intentOpenCount(records: List<UsageRecordEntity>): Int =
        mindfulEnterCount(records) + dismissCount(records)

    /** 有意图打开占比；无放行时返回 null */
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
