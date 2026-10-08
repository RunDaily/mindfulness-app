package com.life.mindfulnessapp.domain.model

/**
 * 使用日志中的一次转换场景。
 * 场景动词是主角；[timeMs] 仅作附带钟点。
 */
data class UsageTransition(
    val scene: UsageTransitionScene,
    val timeMs: Long,
    val packageName: String,
    val appName: String,
    /** 次行：意图文案 / 旁注等 */
    val secondary: String? = null,
    val monitored: Boolean = false,
    val recordId: Long? = null,
    /** 同访次弱连贯：连续同包场景共用 */
    val spineKey: String = packageName,
    /** 非监控同包短时连续打开合并后的次数；1 = 未合并 */
    val repeatCount: Int = 1
) {
    val titleLabel: String
        get() {
            val verb = scene.verbLabel
            return when {
                scene == UsageTransitionScene.HOME -> verb
                appName.isBlank() -> verb
                else -> "$verb · $appName"
            }
        }
}

/**
 * 使用日志场景词表。
 *
 * 监控典型叙事：意图拦截 → 进入使用 → 跳到 → 回到 → 结束 → 回到桌面
 */
enum class UsageTransitionScene {
    /** 非监控：打开 App（非「跳到」目标时） */
    OPEN,
    /** 回到桌面 */
    HOME,
    /** 监控：意图门出现（尚未决定） */
    GATE_HOLD,
    /** 监控：意图门放行后进入使用 */
    ENTER,
    /** 监控：使用中跳到其他 App（主语是目标 App） */
    JUMP,
    /** 监控：从暂停胶囊回到本包 */
    BACK,
    /** 监控：主动结束（胶囊结束） */
    END,
    /** 监控：门外未进入就离开（点离开 / Home / 被动 / 去做了） */
    GATE_LEAVE,
    /** 监控：使用中离开（切监控、软收口等） */
    LEAVE,
    /** 监控：到点结束 */
    LIMIT,
    /** 监控：时段锁拦住 */
    PERIOD_BLOCK;

    val verbLabel: String
        get() = when (this) {
            OPEN -> "打开"
            HOME -> "回到桌面"
            GATE_HOLD -> "意图拦截"
            ENTER -> "进入使用"
            JUMP -> "跳到"
            BACK -> "回到"
            END -> "结束"
            GATE_LEAVE -> "门外离开"
            LEAVE -> "离开"
            LIMIT -> "到点"
            PERIOD_BLOCK -> "时段锁拦住"
        }

    val chipLabel: String
        get() = verbLabel

    /**
     * 点色 / chip 权重：
     * 进入使用·回到 = 蓝（重）；结束 = 绿实心（重）；门外离开 = 淡绿色块；
     * 离开 = 淡绿字；意图拦截 = 空心（最轻）；
     * 打开·跳到 = 灰；到点·时段锁拦住 = 红。
     */
    val dotKind: UsageTransitionDotKind
        get() = when (this) {
            HOME, END -> UsageTransitionDotKind.GREEN
            GATE_LEAVE, LEAVE -> UsageTransitionDotKind.SOFT_GREEN
            ENTER, BACK -> UsageTransitionDotKind.BLUE
            LIMIT, PERIOD_BLOCK -> UsageTransitionDotKind.RED
            GATE_HOLD -> UsageTransitionDotKind.HOLLOW
            OPEN, JUMP -> UsageTransitionDotKind.MUTED
        }
}

enum class UsageTransitionDotKind {
    GREEN, SOFT_GREEN, BLUE, RED, MUTED, HOLLOW
}

/** 使用日志列表展示节点（按时段 / 小时刻度组织） */
sealed class UsageLogListItem {
    abstract val key: String

    data class PeriodHeader(
        val name: String,
        val range: String
    ) : UsageLogListItem() {
        override val key: String get() = "period_${name}_$range"
    }

    data class Row(
        val transition: UsageTransition,
        val isHourFirst: Boolean = false,
        /** 同一分钟内仅首条显示 :mm，后续只留脊点 */
        val isMinuteFirst: Boolean = true
    ) : UsageLogListItem() {
        override val key: String
            get() = "t_${transition.timeMs}_${transition.scene}_" +
                "${transition.packageName}_${transition.recordId}"

        val hour: Int
            get() {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = transition.timeMs
                }
                return cal.get(java.util.Calendar.HOUR_OF_DAY)
            }
    }

    data class EmptyHour(
        val hour: Int
    ) : UsageLogListItem() {
        override val key: String get() = "empty_$hour"
    }

    data class QuietRange(
        val fromHour: Int,
        val toHour: Int
    ) : UsageLogListItem() {
        override val key: String get() = "quiet_${fromHour}_$toHour"
        val label: String
            get() = if (fromHour == toHour) {
                "${fromHour}时 安静"
            } else {
                "${fromHour}–${toHour}时 安静"
            }
    }
}
