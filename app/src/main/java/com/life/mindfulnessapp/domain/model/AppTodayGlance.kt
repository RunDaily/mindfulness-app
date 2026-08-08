package com.life.mindfulnessapp.domain.model

/**
 * 配置页顶部的今日轻指标（点进该 App 历史记录）。
 */
data class AppTodayGlance(
    val dismissCount: Int,
    val mindfulEnterCount: Int,
    val totalSeconds: Long,
    val requireIntentOnOpen: Boolean
)
