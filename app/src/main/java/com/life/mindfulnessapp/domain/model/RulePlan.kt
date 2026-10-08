package com.life.mindfulnessapp.domain.model

/**
 * 方案目标。须由对话 Agent 聊清后 confirmed=true，配置页与校准只读。
 * label 例：晚上早点放下 · 有个清静的早晨；intensity：light 留口子 / mid 适中 / tight 严一点。
 */
data class RuleGoal(
    val label: String = "",
    val intensity: String = "mid",
    val scope: String = "",
    val confirmed: Boolean = false,
    val updatedAt: Long = 0L
) {
    fun displayLine(): String = buildList {
        if (label.isNotBlank()) add(label)
        add(intensityLabel())
        if (scope.isNotBlank()) add(scope)
    }.joinToString(" · ")

    fun intensityLabel(): String = when (intensity) {
        "light" -> "留口子"
        "tight" -> "严一点"
        else -> "适中"
    }
}

/**
 * 一条可执行规则包。
 * null / 0 表示该器械关闭。
 *
 * 方案配置主约束：日限额（[dailyMinutes] ⊃ [browseCasualDailyMinutes]）+ 时段锁。
 * [dailyOpenLimit] 已从配置 UI 撤下；保存新稿时写 null 以关闭次数。
 */
data class RulePack(
    val packageName: String,
    val appName: String,
    val dailyOpenLimit: Int? = null,
    val dailyMinutes: Int? = null,
    /**
     * 随意浏览日限额（分钟）。0 = 不限（只受总日限约束）；null = 沿用库内默认策略。
     */
    val browseCasualDailyMinutes: Int? = null,
    val periodStartMinute: Int? = null,
    val periodEndMinute: Int? = null,
    val rationale: String = "",
    val selected: Boolean = true,
    /** 已下线：发现页屏蔽功能已移除；列保留兼容，恒为 false */
    val blockDiscoverFeed: Boolean = false,
    /**
     * 方案侧：是否允许搜索直达。默认 true。
     * 仅当目录具备可靠带词落地时门口才会真正出现。
     * 关了：仍可出「随意浏览」（同属刷逛型）；普通 App 无可靠链则两路都不出，只留写下意图。
     */
    val searchDirectEnabled: Boolean = true,
) {
    val periodLabel: String?
        get() {
            val s = periodStartMinute ?: return null
            val e = periodEndMinute ?: return null
            return formatPeriodRange(s, e)
        }

    fun hasInstrument(): Boolean =
        dailyMinutes != null || periodLabel != null

    fun summary(): String = buildList {
        dailyMinutes?.let { total ->
            val browse = browseCasualDailyMinutes
            if (browse != null && browse > 0) {
                add("日限 ${total} 分 · 刷 ${browse}")
            } else {
                add("日限 ${total} 分")
            }
        }
        periodLabel?.let { add(it) }
        if (isEmpty()) add("未配器械")
    }.joinToString(" · ")

    companion object {
        /** 跨午夜写「次日」；同日写起止。 */
        fun formatPeriodRange(startMinute: Int, endMinute: Int): String {
            val s = fmtMinute(startMinute)
            val e = fmtMinute(endMinute)
            return if (startMinute > endMinute) "$s — 次日 $e" else "$s — $e"
        }

        fun fmtMinute(minute: Int): String {
            val h = ((minute / 60) % 24 + 24) % 24
            val m = ((minute % 60) + 60) % 60
            return "%02d:%02d".format(h, m)
        }
    }
}

data class UsageDigestRow(
    val packageName: String,
    val appName: String,
    val avgDailyMinutes: Int,
    val avgDailyOpens: Int,
    val nightSharePercent: Int,
    /** 够得上默认配规则；轻用量仍展示，但不默认交给硬规则。 */
    val eligible: Boolean = true
)

/**
 * 按时长累计切到 [threshold]（默认 80%）所需的最少行数。
 * 用于今日机上热副文「八成在前 N 个」。
 */
fun paretoCoreCountByDuration(
    rows: List<UsageDigestRow>,
    threshold: Float = 0.8f
): Int {
    if (rows.isEmpty()) return 0
    val total = rows.sumOf { it.avgDailyMinutes.toLong().coerceAtLeast(0L) }
    if (total <= 0L) return 0
    var cumulative = 0L
    rows.forEachIndexed { index, row ->
        cumulative += row.avgDailyMinutes.coerceAtLeast(0)
        if (cumulative.toFloat() / total.toFloat() >= threshold) return index + 1
    }
    return rows.size
}

/** 配置页顶部：近 7 个完整日。热力为 24 小时秒数。 */
data class InstrumentUsageGlance(
    val avgMinutes: Int,
    val avgOpens: Int,
    val hourSeconds: List<Long>,
    val nightPercent: Int,
    val empty: Boolean
)

data class TodayAppEffect(
    val packageName: String,
    val appName: String,
    val attempts: Int,
    val entered: Int,
    val held: Int,
    val periodBlocks: Int
)

enum class AppDiaryVisitKind { Search, Write, Open, Hold }

data class AppDiaryVisit(
    val startMs: Long,
    val kind: AppDiaryVisitKind,
    val what: String?,
    val durationMinutes: Int,
    val limitMinutes: Int
)

data class AppDiaryDay(
    val dayStartMs: Long,
    val minutes: Int,
    val isToday: Boolean,
    val weekday: String,
    val whenLabel: String,
    val visits: List<AppDiaryVisit>
)

enum class AppDiaryDaySource {
    /** 加入监控前：系统 UsageStats */
    SystemBefore,
    /** 加入监控后：心锚会话 */
    AnchorAfter
}

enum class AppDiarySessionKind {
    /** 系统还原的前台段 */
    SystemForeground,
    /** 心锚放行后的使用 */
    AnchorUse,
    /** 门口离开，未进入 */
    GateQuit,
    /** 进行中 */
    Ongoing,
    /** 仅有日合计，无逐次事件 */
    DayTotalOnly
}

data class AppDiaryTrendDay(
    val dayStartMs: Long,
    val minutes: Int,
    val source: AppDiaryDaySource,
    val isJoinDay: Boolean,
    val weekday: String,
    /** 当日打开次数：加入前=系统前台次；加入后=心锚实际进入次（不含守住） */
    val opens: Int = 0,
    /** 今天：未完结日，不进日均、折线不连到此柱 */
    val isToday: Boolean = false
)

data class AppDiarySessionEntry(
    val startMs: Long,
    val endMs: Long,
    /** 展示用整分钟；不足 1 分可为 0，精确值看 [durationSeconds] */
    val durationMinutes: Int,
    val title: String,
    val subtitle: String?,
    val kind: AppDiarySessionKind,
    val source: AppDiaryDaySource,
    /** 原始秒数；系统逐次与总数对齐用 */
    val durationSeconds: Long = durationMinutes * 60L
)

data class AppDiaryDaySessions(
    val dayStartMs: Long,
    val label: String,
    val source: AppDiaryDaySource,
    val totalMinutes: Int,
    val entries: List<AppDiarySessionEntry>,
    val sectionLabel: String
)

/** 少详情日记：时长/打开前|后对照、三图、配置另区。 */
data class AppDiary(
    val packageName: String,
    val appName: String,
    val usedMinutes: Int,
    val limitMinutes: Int?,
    val opens: Int,
    val openLimit: Int?,
    val held: Int,
    /**
     * 加入后完整日的日均时长（分，不含今天，最多近 7 日）。
     * 与走势绿色段同口径（心锚）。
     */
    val avgMinutes: Int,
    /**
     * 加入后完整日的日均打开次数（不含今天，最多近 7 日）。
     * 与打开变化图同口径（心锚实际进入次）。
     */
    val avgOpens: Int = 0,
    /** 是否已有至少 1 个加入后完整日可算右侧日均 */
    val avgReady: Boolean = false,
    val vsBeforeRule: Int?,
    val hourSeconds: List<Long>,
    val nightLine: String?,
    val days: List<AppDiaryDay>,
    /** 加入前 7 日（系统）→ 今日（心锚），连续柱 */
    val trendDays: List<AppDiaryTrendDay> = emptyList(),
    /** 冻结的加入前 7 日日均分钟；null = 尚未记下 */
    val baselineAvgMinutes: Int? = null,
    /** 加入前 7 日日均打开（系统前台次）；与时长基线同窗 */
    val baselineAvgOpens: Int? = null,
    /** 加入日 0 点；用于细看页分界 */
    val joinDayStartMs: Long = 0L,
    /** 今日 0 点；详情「今天」节奏条 */
    val todayDayStartMs: Long = 0L,
    /** 今日前台/放行会话（节奏色块） */
    val todaySessions: List<SystemForegroundSession> = emptyList(),
    /** 配置区器械摘要，如「日限 45 · 次数 25」 */
    val instrumentSummary: String = "",
    /**
     * 配置区淡提示：今天/昨天刚改过规则时非空（如「今天改过」）。
     * 由 [configRecentLabel] 计算。
     */
    val configRecentLabel: String? = null,
    /** 详情页一行淡字：口径说明 */
    val dataSourceNote: String = DATA_SOURCE_NOTE
) {
    companion object {
        const val DATA_SOURCE_NOTE = "加入前按系统用量；加入后按心锚记录"
    }
}

/**
 * 详情配置区「最近更新」：仅今日 / 昨日窗口，过期不提示。
 */
fun configRecentLabel(rulesUpdatedAt: Long, nowMs: Long = System.currentTimeMillis()): String? {
    if (rulesUpdatedAt <= 0L) return null
    val dayMs = 24L * 60 * 60 * 1000
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    val yesterdayStart = todayStart - dayMs
    return when {
        rulesUpdatedAt >= todayStart -> "今天改过"
        rulesUpdatedAt >= yesterdayStart -> "昨天改过"
        else -> null
    }
}

data class TodayRuleRow(
    val packageName: String,
    val appName: String,
    val usedMinutes: Int,
    val limitMinutes: Int?,
    val opens: Int,
    val openLimit: Int?,
    val summary: String,
    val exhausted: Boolean
)
