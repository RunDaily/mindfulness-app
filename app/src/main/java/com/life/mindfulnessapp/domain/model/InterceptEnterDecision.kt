package com.life.mindfulnessapp.domain.model

/**
 * 拦截页确认进入时的决策结果。
 *
 * @param purpose 意图文案
 * @param intentKind 意图类型（新路径均为 [IntentKind.PURPOSEFUL]）
 * @param sessionLimitMinutes 本次会话时长上限（分钟）；0 表示不设单次上限
 */
data class InterceptEnterDecision(
    val purpose: String,
    val intentKind: IntentKind,
    val sessionLimitMinutes: Int
)
