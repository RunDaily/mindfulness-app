package com.life.mindfulnessapp.domain.model

data class RecentPurposeStat(
    val purpose: String,
    val useCount: Int,
    val lastUsedAt: Long,
    /** 今日该意图累计时长（秒） */
    val todaySeconds: Long = 0L
)
