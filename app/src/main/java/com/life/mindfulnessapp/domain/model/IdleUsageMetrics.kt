package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

/**
 * 空转时长：无意义使用的可操作定义。
 *
 * = 直进时长（无意图进入）
 * + 对照采集的跑偏秒数 [UsageRecordEntity.driftSeconds]
 *
 * 「守住」不计空转；未对照的有意图会话暂不计空转（避免误伤）。
 */
object IdleUsageMetrics {

    fun isGateDismiss(record: UsageRecordEntity): Boolean =
        record.endReason == UsageRecordEntity.EndReason.GATE_DISMISS ||
            record.endReason == UsageRecordEntity.EndReason.GATE_DISMISS_OWN_APP ||
            record.endReason == UsageRecordEntity.EndReason.GATE_PASSIVE ||
            record.endReason == UsageRecordEntity.EndReason.GATE_POSITIVE_EXIT

    fun isSeed(record: UsageRecordEntity): Boolean =
        record.endReason == UsageRecordEntity.EndReason.SEED_FROM_SYSTEM

    /** 真正进入且无有效意图文案 → 直进 */
    fun isUngatedEnter(record: UsageRecordEntity): Boolean {
        if (isGateDismiss(record) || isSeed(record)) return false
        // 进行中 / 已结束：无意图文案即直进
        return record.purpose.isNullOrBlank()
    }

    fun idleSeconds(record: UsageRecordEntity): Long {
        if (isGateDismiss(record) || isSeed(record)) return 0L
        if (record.durationSeconds <= 0L) return 0L
        if (isUngatedEnter(record)) return record.durationSeconds
        return DriftSecondsPolicy.resolveStored(
            level = record.mindfulnessLevel,
            driftSeconds = record.driftSeconds,
            durationSeconds = record.durationSeconds
        ) ?: 0L
    }

    fun totalIdleSeconds(records: Iterable<UsageRecordEntity>): Long =
        records.sumOf { idleSeconds(it) }

    fun totalMindfulSeconds(records: Iterable<UsageRecordEntity>): Long =
        records.sumOf { r ->
            if (isGateDismiss(r) || isSeed(r) || isUngatedEnter(r)) return@sumOf 0L
            val idle = idleSeconds(r)
            (r.durationSeconds - idle).coerceAtLeast(0L)
        }
}
