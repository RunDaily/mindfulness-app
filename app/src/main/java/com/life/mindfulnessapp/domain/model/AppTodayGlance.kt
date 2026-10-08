package com.life.mindfulnessapp.domain.model

/**
 * 单 App 今日轻指标（详情 Peek / 历史页总览）。
 *
 * 产品文案「打开」与 [enterCount] / [openCount] 同口径：真正进门（不含守住、seed）。
 * [openCount] 与 [enterCount] 同值，保留字段方便旧调用方；新代码优先读 [enterCount]。
 *
 * - [enterCount] / [openCount]：今日真正进入次数（含进行中会话）
 * - [dismissCount]：今日守住（与打开互斥）
 * - [mindfulEnterCount]：今日有目的进入（写了意图，含过程中补写；是打开的子集）
 * - [totalSeconds]：与时长锁/胶囊同一口径的今日已用（含加入前 seed、进行中会话）
 */
data class AppTodayGlance(
    val enterCount: Int = 0,
    val openCount: Int,
    val dismissCount: Int,
    val mindfulEnterCount: Int,
    val totalSeconds: Long,
    val requireIntentOnOpen: Boolean
)
