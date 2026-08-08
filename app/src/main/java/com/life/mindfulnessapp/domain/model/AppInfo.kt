package com.life.mindfulnessapp.domain.model

import android.graphics.drawable.Drawable

/**
 * 已安装 App 的展示信息
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isMonitored: Boolean = false,
    val dailyLimitMinutes: Int = 60,
    val weeklyLimitMinutes: Int = 0,
    /** 是否启用时长锁 */
    val timeLimitEnabled: Boolean = true,
    /** 超时提醒文案 */
    val overTimeMessage: String = "",
    /** 历史字段：用法约定；MVP 未使用，保留供日后观照扩展 */
    val usageCovenant: String = "",
    /** 历史字段：打开时是否提醒约定；MVP 未使用 */
    val remindCovenantOnOpen: Boolean = true,
    /** 打开前是否要求填写单次意图（意图门） */
    val requireIntentOnOpen: Boolean = true,
    /** 是否启用单次意图时长契约 */
    val sessionLimitEnabled: Boolean = true,
    /** 是否启用意图关键词检验（无目的词表由用户自定） */
    val intentQualityCheckEnabled: Boolean = false,
    /** 限制关键词 JSON；检验开启且非空时才拦 */
    val intentBlockKeywordsJson: String = "",
    /** 默认单次时长（分钟） */
    val defaultSessionLimitMinutes: Int = 15,
    /** 主动结束后是否弹出意图对照；默认关 */
    val intentReviewEnabled: Boolean = false,
    /** 是否启用每日打开次数上限 */
    val dailyOpenLimitEnabled: Boolean = false,
    /** 每日最多放行次数 */
    val dailyOpenLimit: Int = 5,
    /** 是否启用时段锁 */
    val periodLockEnabled: Boolean = false,
    /** 锁定窗口 JSON */
    val periodWindowsJson: String = "",
    /** 时段锁承诺文案 */
    val periodLockCommitment: String = "",
    /** 该 App 是否已从设备卸载（但仍保留在监控列表中） */
    val isUninstalled: Boolean = false
) {
    fun effectiveDailyLimitMinutes(): Int =
        if (timeLimitEnabled) dailyLimitMinutes else 0

    fun effectiveWeeklyLimitMinutes(): Int =
        if (timeLimitEnabled) weeklyLimitMinutes else 0

    fun effectiveDailyOpenLimit(): Int =
        if (dailyOpenLimitEnabled) dailyOpenLimit.coerceIn(1, 30) else 0
}
