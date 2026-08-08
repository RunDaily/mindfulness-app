package com.life.mindfulnessapp.domain.model

/**
 * 本次进入的意图类型。
 * - [PURPOSEFUL]：写下意图后进入
 * - [PURPOSELESS]：历史「无明确目的」旁路遗留值；新会话不再写入
 */
enum class IntentKind {
    PURPOSEFUL,
    PURPOSELESS;

    companion object {
        fun fromStorage(value: String?): IntentKind? = when (value) {
            PURPOSEFUL.name -> PURPOSEFUL
            PURPOSELESS.name -> PURPOSELESS
            else -> null
        }
    }
}
