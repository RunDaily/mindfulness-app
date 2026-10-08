package com.life.mindfulnessapp.domain.model

/**
 * 拦截页确认进入时的决策结果。
 *
 * @param purpose 意图文案（门前必填）
 * @param intentKind 意图类型（绿场门按命名分流：[IntentKind.PURPOSEFUL] / [IntentKind.URGE]）
 * @param sessionLimitMinutes 本次会话时长上限（分钟）；0 表示不设单次上限
 * @param landMode 进入后落地；绿场门仅在常用意图已绑定深链时为 [IntentLandMode.DEEPLINK]
 * @param landDeepLinkId 绑定深链 id；自由输入匹配目录时不写入
 */
data class InterceptEnterDecision(
    val purpose: String,
    val intentKind: IntentKind,
    val sessionLimitMinutes: Int,
    val landMode: IntentLandMode = IntentLandMode.NORMAL,
    val landDeepLinkId: String? = null,
)
