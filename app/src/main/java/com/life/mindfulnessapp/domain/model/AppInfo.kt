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
    /** 历史遗留字段；时间感知已下线，恒为 false */
    val timeAwarenessEnabled: Boolean = false,
    /** 是否启用时长锁 */
    val timeLimitEnabled: Boolean = true,
    /** 超时提醒文案 */
    val overTimeMessage: String = "",
    /**
     * 期望描述：我和这个 App 的意义/价值约定
     * （例如「只用来查攻略，不刷推荐」）。存于 [AppLimitEntity.usageCovenant]。
     */
    val usageCovenant: String = "",
    /** 历史字段：打开时是否提醒约定；当前未使用 */
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
    /** 主动结束后是否弹出意图对照；默认关（历史字段） */
    val intentReviewEnabled: Boolean = false,
    /** 意图门下是否启用对照；默认开 */
    val compareEnabled: Boolean = true,
    /** 对照最低时长（分钟）：5 / 10 / 15 / 20；默认 10 */
    val compareMinMinutes: Int = 10,
    /** 是否启用每日打开次数上限 */
    val dailyOpenLimitEnabled: Boolean = false,
    /** 每日最多放行次数 */
    val dailyOpenLimit: Int = 5,
    /** 是否启用时段锁 */
    val periodLockEnabled: Boolean = false,
    /** 锁定窗口 JSON */
    val periodWindowsJson: String = "",
    /** 时段锁寄语 */
    val periodLockCommitment: String = "",
    /** 该 App 是否已从设备卸载（但仍保留在监控列表中） */
    val isUninstalled: Boolean = false,
    /** 是否将检测到的分身一并按本配置拦截；默认开 */
    val lockClonesEnabled: Boolean = true,
    /**
     * 已下线：发现页屏蔽已移除；字段保留兼容，默认 false。
     */
    val blockDiscoverFeedEnabled: Boolean = false,
    /**
     * 疑似分身所指向的主包名（仅当本包更像分身时非空）。
     * 用于挑选器角标；运行时同锁仍由主 App 的 [lockClonesEnabled] 决定。
     */
    val suspectedCloneOfPackage: String? = null,
    /** 当前设备上检测到的疑似分身包名（相对本包作为主 App） */
    val suspectedClonePackages: List<String> = emptyList(),
    /**
     * 系统分身空间（华为 user 128 / 小米 999 等）是否已安装同包名分身。
     * 与 [suspectedClonePackages] 不同：包名相同，桌面列表扫不出来。
     */
    val hasSystemDualInstance: Boolean = false,
    /**
     * 列表唯一键。系统分身展示行用 `packageName#system_dual`，避免与主应用撞 key。
     */
    val listKey: String = packageName,
    /** 是否为系统分身的展示行（点击仍配置 [packageName] 主应用） */
    val isSystemDualRow: Boolean = false
) {
    fun effectiveDailyLimitMinutes(): Int =
        if (timeLimitEnabled) dailyLimitMinutes else 0

    fun effectiveWeeklyLimitMinutes(): Int =
        if (timeLimitEnabled) weeklyLimitMinutes else 0

    fun effectiveDailyOpenLimit(): Int =
        if (dailyOpenLimitEnabled) dailyOpenLimit.coerceIn(1, 30) else 0
}
