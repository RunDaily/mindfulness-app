package com.life.mindfulnessapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 受监控 App 的配置实体
 *
 * 三能力相互独立，至少开启一项才有意义：
 * - [requireIntentOnOpen]：打开前拦截并要求填写意图（意图门）
 * - [timeLimitEnabled]：启用日/周时长上限与超限阻断（时长锁）
 * - [periodLockEnabled]：指定时段硬锁，打开门槛更高（时段锁）
 * - [sessionLimitEnabled]：进入时是否承诺本次多久（单次上限）；默认开，仅在意图门开启时生效
 * - [intentQualityCheckEnabled]：用用户自定义关键词限制意图；仅意图门开启时生效
 * - [intentBlockKeywordsJson]：限制关键词 JSON 数组；检验开启且词表非空时才拦
 * - [intentReviewEnabled]：历史遗留列，配置 UI 不再暴露；结束对照由胶囊/到点页在有意图时进行
 */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 60,
    val weeklyLimitMinutes: Int = 0,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val dailyModifyCount: Int = 0,
    val lastModifiedDate: String = "",
    /** 是否启用时长锁（日/周限额与超限阻断） */
    val timeLimitEnabled: Boolean = true,
    val overTimeMessage: String = "",
    /** 历史：用法约定文案；MVP 未使用（时段锁承诺见 [periodLockCommitment]） */
    val usageCovenant: String = "",
    /** 历史：是否在打开时提醒约定；MVP 未使用 */
    val remindCovenantOnOpen: Boolean = true,
    /** 打开前是否要求填写单次意图（意图门） */
    val requireIntentOnOpen: Boolean = true,
    /**
     * 历史：曾控制「无明确目的」旁路；功能已移除，字段仅保留以兼容 DB schema。
     * 写入时固定为 false。
     */
    val allowPurposelessEntry: Boolean = false,
    /** 是否启用「单次意图时长」契约（进入时选本次上限） */
    val sessionLimitEnabled: Boolean = true,
    /**
     * 是否启用意图关键词检验。
     * 开启后：意图命中 [intentBlockKeywordsJson] 中任一词则不能进入。
     */
    val intentQualityCheckEnabled: Boolean = false,
    /**
     * 用户自定义限制关键词（JSON 字符串数组），见 [com.life.mindfulnessapp.domain.model.IntentBlockKeywords]。
     */
    val intentBlockKeywordsJson: String = "",
    /** 默认单次时长（分钟） */
    val defaultSessionLimitMinutes: Int = 15,
    /**
     * 历史遗留：曾控制全屏意图回顾浮层。现已废弃；结束对照在胶囊 / 到点页进行。
     * 列保留以兼容旧库，新配置始终写 false。
     */
    val intentReviewEnabled: Boolean = false,
    /** 是否启用每日打开次数上限（按放行次数计） */
    val dailyOpenLimitEnabled: Boolean = false,
    /** 每日最多放行进入次数；配合 [dailyOpenLimitEnabled]，建议 1–30 */
    val dailyOpenLimit: Int = 5,
    /** 首页坑位 / 管理列表展示顺序（升序；越小越靠前） */
    val sortOrder: Int = 0,
    /** 是否启用时段锁（指定时段内硬锁） */
    val periodLockEnabled: Boolean = false,
    /** 锁定窗口 JSON，见 [com.life.mindfulnessapp.domain.model.PeriodWindowsCodec] */
    val periodWindowsJson: String = "",
    /** 开启时段锁时写下的承诺（生效中关闭时回显） */
    val periodLockCommitment: String = "",
    /**
     * 系锚瞬间冻结的「前 7 个完整自然日」日均使用秒数（系统 UsageStats）。
     * 0 且 [baselineCapturedAt]==0 表示尚未记下；之后不随滚动窗口改写。
     */
    val baselineDailyAvgSeconds: Long = 0L,
    /** 基线写入时间；0 表示未捕获 */
    val baselineCapturedAt: Long = 0L
) {
    fun effectiveDailyLimitMinutes(): Int =
        if (timeLimitEnabled) dailyLimitMinutes else 0

    fun effectiveWeeklyLimitMinutes(): Int =
        if (timeLimitEnabled) weeklyLimitMinutes else 0

    /** 有效每日打开次数上限；0 表示不限 */
    fun effectiveDailyOpenLimit(): Int =
        if (dailyOpenLimitEnabled) dailyOpenLimit.coerceIn(1, MAX_DAILY_OPEN_LIMIT) else 0

    companion object {
        const val MAX_DAILY_MODIFY_COUNT = 1
        const val MAX_DAILY_OPEN_LIMIT = 30
        const val DEFAULT_DAILY_OPEN_LIMIT = 5
    }
}
