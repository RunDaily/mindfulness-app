package com.life.mindfulnessapp.domain.model

/**
 * 陪伴条左标形态（三路差分）。
 */
enum class CapsuleMarkStyle {
    /** 意图：App 图标静置 / 轻呼吸（桌面圆球仍转圈） */
    APP,
    /** 随意浏览：计时器 */
    TIMER,
    /** 搜索：查找静置 */
    SEARCH;

    companion object {
        fun from(path: CompanionPath): CapsuleMarkStyle = when (path) {
            CompanionPath.INTENT -> APP
            CompanionPath.BROWSE -> TIMER
            CompanionPath.SEARCH -> SEARCH
        }
    }
}
