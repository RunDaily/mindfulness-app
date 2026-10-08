package com.life.mindfulnessapp.data.db.entity

/** Room 聚合：某 App 全量意图统计（含累积时长） */
data class PurposeStatFullRow(
    val purpose: String,
    val useCount: Int,
    val totalSeconds: Long,
    val todaySeconds: Long,
    val firstUsedAt: Long,
    val lastUsedAt: Long
)
