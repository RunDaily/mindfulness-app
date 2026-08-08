package com.life.mindfulnessapp.domain.model

/**
 * 拦截页「最近意图」快捷 tag 的一条。
 * 只承载可点选填入的意图文案，不附带使用记录信息。
 */
data class RecentPurpose(
    val purpose: String
)
