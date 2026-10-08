package com.life.mindfulnessapp.data.db.entity

/**
 * Room 聚合查询：某 App 近期意图统计。
 * @param todaySeconds 今日该意图累计时长（秒）；无今日记录时为 0
 */
data class PurposeStatRow(
    val purpose: String,
    val useCount: Int,
    val lastUsedAt: Long,
    val todaySeconds: Long = 0L
)
