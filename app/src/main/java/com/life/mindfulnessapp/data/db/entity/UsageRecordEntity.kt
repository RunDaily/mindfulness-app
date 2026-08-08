package com.life.mindfulnessapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单次使用记录实体
 * @param id 自增主键
 * @param packageName App 包名
 * @param startTime 开始时间戳（毫秒）
 * @param endTime 结束时间戳（毫秒），-1 表示进行中
 * @param durationSeconds 使用时长（秒）
 * @param endReason 结束原因
 * @param purpose 使用意图文案（意图门进入时填写；直进等可为 null）
 * @param intentKind 意图类型存储值（[com.life.mindfulnessapp.domain.model.IntentKind.name]；旧数据可为 null）
 * @param sessionLimitMinutes 本次会话时长上限（分钟）；0 / null 表示不设单次上限
 * @param sessionExtensionMinutes 本次已续时分钟数（仅允许一次）
 * @param note 用户事后对照意图写下的复盘（可随时编辑，null 表示未填写）
 * @param effectScore 历史字段：效果自评分（0-10），当前结束流程不再采集
 * @param mindfulnessLevel 正念程度（意图回顾三档，见 [MindfulnessLevel]；null 表示未评）
 */
@Entity(tableName = "usage_records")
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long = -1L,
    val durationSeconds: Long = 0L,
    val endReason: String = EndReason.UNKNOWN,
    val purpose: String? = null,
    val intentKind: String? = null,
    val sessionLimitMinutes: Int = 0,
    val sessionExtensionMinutes: Int = 0,
    val note: String? = null,
    val effectScore: Int? = null,
    val mindfulnessLevel: Int? = null
) {
    /**
     * 对照档位：结束时衡量有没有按着意图在用。
     * 数值越大表示越贴近意图。
     */
    object MindfulnessLevel {
        /** 没跑偏 */
        const val ALIGNED = 3
        /** 跑偏了 */
        const val SLIGHT = 2
        /** 跑远了 */
        const val LARGE = 1

        fun isValid(level: Int?): Boolean =
            level == ALIGNED || level == SLIGHT || level == LARGE

        fun tierLabel(level: Int): String = when (level) {
            ALIGNED -> "没跑偏"
            SLIGHT -> "跑偏了"
            LARGE -> "跑远了"
            else -> ""
        }

        fun judgmentLabel(level: Int): String = tierLabel(level)

        /** 时间轴 / 统计处的短展示 */
        fun displayLabel(level: Int): String = tierLabel(level)

        /**
         * 对照区主问：把三档放在「和意图比」之下，避免「有没有跑偏」与「没跑偏」拧着说。
         */
        const val COMPARE_PROMPT = "和意图比，这一次"

        /**
         * 备注输入框占位：随已选档位变化；未选时用中性句。
         */
        fun notePlaceholder(level: Int?): String = when (level) {
            ALIGNED -> "也可以记一句"
            SLIGHT -> "实际去做了什么"
            LARGE -> "最后去了哪里"
            else -> "这一次怎样（可选）"
        }

        /** 备注区小标题 */
        fun noteSectionLabel(level: Int?): String =
            if (level != null && isValid(level)) "补一句（可选）" else "备注（可选）"

        /**
         * 时间轴卡片上、对照后催填备注的入口文案。
         * 没跑偏宜轻；跑偏/跑远才强调「实际发生」。
         */
        fun cardNoteAffordance(level: Int): String = when (level) {
            ALIGNED -> "记一句"
            SLIGHT, LARGE -> "补一句实际发生了什么"
            else -> "记一句"
        }
    }
    object EndReason {
        /** 用户主动通过胶囊结束（标准闭环） */
        const val MANUAL = "MANUAL"
        /**
         * 含意图门：切走后离开倒计时归零，按中断收口。
         * 非胶囊主动结束；时长足够时下次进入可在意图门区「继续上次」。
         */
        const val AWAY_COUNTDOWN = "AWAY_COUNTDOWN"
        /**
         * 历史兼容：未锁屏后台超时自动结束。
         * 新写入请使用 [BACKGROUND_TIMEOUT]。
         */
        const val AUTO_TIMEOUT = "AUTO_TIMEOUT"
        /** 未锁屏切到后台较久后自动结束（多用于纯时长锁静默收口） */
        const val BACKGROUND_TIMEOUT = "BACKGROUND_TIMEOUT"
        /** 息屏宽限期过后自动结束 */
        const val SCREEN_OFF_TIMEOUT = "SCREEN_OFF_TIMEOUT"
        /** 达到日/周时长上限 */
        const val LIMIT_REACHED = "LIMIT_REACHED"
        /** 达到本次会话时长上限 */
        const val SESSION_LIMIT_REACHED = "SESSION_LIMIT_REACHED"
        /** 意图门拦截页上选择离开回桌面（未进入 App） */
        const val GATE_DISMISS = "GATE_DISMISS"
        /** 意图门拦截页上选择打开心锚（未进入目标 App） */
        const val GATE_DISMISS_OWN_APP = "GATE_DISMISS_OWN_APP"
        /** App/服务被关闭或残留会话被清理 */
        const val APP_CLOSED = "APP_CLOSED"
        /**
         * 从当前被监控 App 切到另一个被监控 App 时收口会话。
         * 下次回到原 App 时在标准拦截页意图门区提供「继续上次」。
         */
        const val SWITCHED_AWAY = "SWITCHED_AWAY"
        /**
         * 加入监控时，用系统 UsageStats 回填的「加入前」用量种子。
         * 计入日/周限额，但不算一次打开，也不走中断确认。
         */
        const val SEED_FROM_SYSTEM = "SEED_FROM_SYSTEM"
        const val UNKNOWN = "UNKNOWN"

        /**
         * 是否应在下次进入时于意图门区提供「继续上次」。
         * 仅「非胶囊主动结束 / 非到点 / 非门外守住」且用户确实用过一段时间的会话需要续航入口。
         * [AWAY_COUNTDOWN] 算中断：离开倒计时归零后可续。
         */
        fun shouldOfferResumeConfirm(reason: String): Boolean = when (reason) {
            MANUAL, LIMIT_REACHED, SESSION_LIMIT_REACHED,
            GATE_DISMISS, GATE_DISMISS_OWN_APP, SEED_FROM_SYSTEM -> false
            AWAY_COUNTDOWN,
            BACKGROUND_TIMEOUT, SCREEN_OFF_TIMEOUT, AUTO_TIMEOUT,
            APP_CLOSED, SWITCHED_AWAY, UNKNOWN -> true
            else -> true
        }

        /** 门外停下后是否打开了心锚（相对「离开回桌面」） */
        fun isGateDismissToOwnApp(endReason: String): Boolean =
            endReason == GATE_DISMISS_OWN_APP

        /**
         * 是否为意图门拦住后离开（未真正进入）。
         * 兼容旧数据：曾写入 [APP_CLOSED] + duration=0。
         */
        fun isGateQuit(purpose: String?, endReason: String, durationSeconds: Long): Boolean {
            if (purpose != null) return false
            if (endReason == LIMIT_REACHED || endReason == SEED_FROM_SYSTEM) return false
            if (endReason == GATE_DISMISS || endReason == GATE_DISMISS_OWN_APP) return true
            return durationSeconds == 0L && endReason == APP_CLOSED
        }

        fun isSeed(endReason: String): Boolean = endReason == SEED_FROM_SYSTEM

        /**
         * 面向用户的结束归类（内部 EndReason 可更细，展示层只暴露四类）。
         */
        enum class DisplayKind {
            /** 胶囊主动结束 */
            MANUAL,
            /** 门外守住（未进入） */
            GATE_QUIT,
            /** 单次 / 日周到点 */
            LIMIT,
            /** 切走 / 息屏 / 切换 / 异常等中断 */
            INTERRUPT,
            /** 系统种子等不展示给用户的内部类型 */
            INTERNAL
        }

        fun displayKind(endReason: String): DisplayKind = when (endReason) {
            MANUAL -> DisplayKind.MANUAL
            GATE_DISMISS, GATE_DISMISS_OWN_APP -> DisplayKind.GATE_QUIT
            LIMIT_REACHED, SESSION_LIMIT_REACHED -> DisplayKind.LIMIT
            SEED_FROM_SYSTEM -> DisplayKind.INTERNAL
            AWAY_COUNTDOWN, BACKGROUND_TIMEOUT, AUTO_TIMEOUT,
            SCREEN_OFF_TIMEOUT, SWITCHED_AWAY, APP_CLOSED, UNKNOWN -> DisplayKind.INTERRUPT
            else -> DisplayKind.INTERRUPT
        }

        /** 四类结束的短标签（列表 / 统计用） */
        fun displayKindLabel(endReason: String): String? = when (displayKind(endReason)) {
            DisplayKind.MANUAL -> "主动结束"
            DisplayKind.GATE_QUIT -> "守住"
            DisplayKind.LIMIT -> "到点"
            DisplayKind.INTERRUPT -> softEndReasonLabel(endReason) ?: "中断"
            DisplayKind.INTERNAL -> null
        }

        /** 非标准闭环的轻量结束标注（中断类） */
        fun softEndReasonLabel(endReason: String): String? = when (endReason) {
            AWAY_COUNTDOWN -> "离开后结束"
            SCREEN_OFF_TIMEOUT -> "息屏结束"
            BACKGROUND_TIMEOUT, AUTO_TIMEOUT -> "离开后结束"
            SWITCHED_AWAY -> "切换应用结束"
            APP_CLOSED, UNKNOWN -> "未正常结束"
            SEED_FROM_SYSTEM -> "加入前使用"
            else -> null
        }
    }

    val isGateQuit: Boolean
        get() = EndReason.isGateQuit(purpose, endReason, durationSeconds)

    val isSeed: Boolean
        get() = EndReason.isSeed(endReason)
}
