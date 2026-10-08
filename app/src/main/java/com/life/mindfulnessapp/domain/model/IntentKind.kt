package com.life.mindfulnessapp.domain.model

/**
 * 本次进入的意图类型。
 * - [PURPOSEFUL]：有实际要做的事（回消息、找菜谱…）
 * - [URGE]：诚实冲动命名（就是想刷、有点无聊、手在点了…）
 * - [QUICK]：历史「快捷意图」遗留值；新会话不再写入
 * - [PURPOSELESS]：历史「无明确目的」旁路遗留值；新会话按 [URGE] 语义对待
 * - [SEARCH]：软门搜索直达，purpose 是搜词
 */
enum class IntentKind {
    PURPOSEFUL,
    URGE,
    QUICK,
    PURPOSELESS,
    SEARCH;

    val isUrgeNaming: Boolean
        get() = this == URGE || this == PURPOSELESS

    val isTaskOriented: Boolean
        get() = this == PURPOSEFUL || this == QUICK

    companion object {
        fun fromStorage(value: String?): IntentKind? = when (value) {
            PURPOSEFUL.name -> PURPOSEFUL
            URGE.name -> URGE
            QUICK.name -> QUICK
            PURPOSELESS.name -> PURPOSELESS
            SEARCH.name -> SEARCH
            else -> null
        }
    }
}
