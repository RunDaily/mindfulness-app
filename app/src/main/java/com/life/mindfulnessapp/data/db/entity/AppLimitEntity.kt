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
 * - [timeAwarenessEnabled]：历史遗留列（原「时间感知」）；读写恒为 false，列保留兼容旧库
 * - [sessionLimitEnabled]：进入时是否承诺本次多久（单次上限）；默认开，仅在意图门开启时生效
 * - [intentQualityCheckEnabled]：用用户自定义关键词限制意图；仅意图门开启时生效
 * - [intentBlockKeywordsJson]：限制关键词 JSON 数组；检验开启且词表非空时才拦
 * - [intentReviewEnabled]：历史遗留列，配置 UI 不再暴露；结束对照由胶囊/到点页在有意图时进行
 * - [compareEnabled]：意图门下是否启用对照（主动弹出 + 首页【照】）；默认开
 * - [compareMinMinutes]：对照最低时长（5 / 10 / 15 / 20）；默认 10
 * - [shortSessionCompareEnabled]：历史遗留列，业务勿用
 * - [blockDiscoverFeedEnabled]：已下线（发现页屏蔽已移除）；列保留避免迁移，业务勿用
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
    /** 历史遗留：原时间感知开关；产品已下线，读写恒为 false */
    val timeAwarenessEnabled: Boolean = false,
    /** 是否启用时长锁（日/周限额与超限阻断） */
    val timeLimitEnabled: Boolean = true,
    val overTimeMessage: String = "",
    /**
     * 期望描述：我和这个 App 的意义/价值约定（详情页可编辑）。
     * 时段锁单段寄语见 [periodLockCommitment] / 窗口 message。
     */
    val usageCovenant: String = "",
    /** 历史：打开时是否提醒约定；当前未使用 */
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
    /** 时段锁寄语（拦截页与关闭门槛回显） */
    val periodLockCommitment: String = "",
    /**
     * 系锚瞬间冻结的「前 7 个完整自然日」日均使用秒数（系统 UsageStats）。
     * 0 且 [baselineCapturedAt]==0 表示尚未记下；之后不随滚动窗口改写。
     */
    val baselineDailyAvgSeconds: Long = 0L,
    /** 基线写入时间；0 表示未捕获 */
    val baselineCapturedAt: Long = 0L,
    /**
     * 系锚瞬间冻结的「前 7 个完整自然日」逐日用量 JSON，见 [com.life.mindfulnessapp.domain.model.PreJoinUsageSnapshot]。
     * 走势加入前灰柱只读此快照；空串表示尚未捕获。
     */
    val preJoinUsageJson: String = "",
    /**
     * 器械 / 限额配置最近一次人手改动的时间戳（毫秒）。
     * 0 = 未知；详情配置区用于「今天改过」淡提示。ensureBaseline 等自动补记不改此字段。
     */
    val rulesUpdatedAt: Long = 0L,
    /**
     * 常用意图 JSON（拦截页写意图 Tab）；见 [com.life.mindfulnessapp.domain.model.CommonIntentsCodec]。
     */
    val quickIntentsJson: String = "",
    /**
     * 历史字段：曾控制快捷面板默认展开；功能已移除，保留列以免迁移。
     */
    val quickPanelDefaultExpanded: Boolean = false,
    /**
     * 是否启用意图对照（主动弹出 + 首页【照】）。
     * 仅在意图门开启时有意义；默认开。
     */
    val compareEnabled: Boolean = true,
    /**
     * 对照最低时长（分钟）：须为 5 / 10 / 15 / 20 之一；默认 10。
     * 见 [com.life.mindfulnessapp.domain.model.ComparePolicy]。
     */
    val compareMinMinutes: Int = 10,
    /**
     * 历史遗留：曾表示「短时也要对照」。现由 [compareEnabled] + [compareMinMinutes] 承接。
     * 列保留以兼容旧库；新写入固定 false，业务逻辑勿再读。
     */
    val shortSessionCompareEnabled: Boolean = false,
    /**
     * 意图池配置 JSON，见 [com.life.mindfulnessapp.domain.model.IntentPoolCodec]。
     * 仅在意图门开启时有意义。
     */
    val intentPoolJson: String = "",
    /**
     * 「随意浏览」专属策略 JSON，见 [com.life.mindfulnessapp.domain.model.BrowseCasualPolicyCodec]。
     */
    val browseCasualJson: String = "",
    /**
     * 是否将检测到的应用分身 / 双开一并按本配置拦截。
     * 默认开启；关闭后仅锁定本包名，分身需单独添加监控。
     */
    val lockClonesEnabled: Boolean = true,
    /**
     * 已下线：发现页屏蔽已移除；列保留避免迁移，业务勿用。
     */
    val blockDiscoverFeedEnabled: Boolean = false
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
