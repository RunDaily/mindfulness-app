package com.life.mindfulnessapp.data.analytics

import com.life.mindfulnessapp.domain.model.PeriodDays
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import java.util.Calendar
import java.util.UUID
import kotlin.math.roundToInt

/** 埋点分桶与公共属性拼装。 */
object AnalyticsBuckets {

    fun newInterceptId(): String =
        UUID.randomUUID().toString().replace("-", "").take(16)

    fun truncateApp(name: String?): String =
        name?.trim()?.take(40).orEmpty()

    fun truncatePkg(pkg: String?): String =
        pkg?.trim()?.take(120).orEmpty()

    fun truncatePurpose(purpose: String?): String =
        purpose?.trim()?.take(80).orEmpty()

    fun purposeClarity(purpose: String?): String {
        val text = purpose?.trim().orEmpty()
        if (text.isEmpty()) return HaEvents.Clarity.EMPTY
        if (text.length < 2) return HaEvents.Clarity.VAGUE
        val normalized = text
            .lowercase()
            .replace(Regex("[\\s\\p{Punct}]+"), "")
        if (normalized.isEmpty()) return HaEvents.Clarity.VAGUE
        val vagueExact = setOf(
            "看看", "刷刷", "无聊", "随便", "不知道", "无", "没有",
            "玩玩", "溜达", "消遣", "放松一下", "杀时间", "划划"
        )
        if (vagueExact.any { it == normalized || it == text }) return HaEvents.Clarity.VAGUE
        if (text.length <= 4 && listOf("看看", "刷刷", "玩玩", "随便").any { normalized.contains(it) }) {
            return HaEvents.Clarity.VAGUE
        }
        return HaEvents.Clarity.CONCRETE
    }

    /** 时长分钟分桶：0 / 1_3 / 3_10 / 10_30 / 30_60 / 60p */
    fun durationBucket(durationSeconds: Long): String {
        val min = (durationSeconds / 60.0).roundToInt()
        return when {
            min <= 0 -> "0"
            min <= 3 -> "1_3"
            min <= 10 -> "3_10"
            min <= 30 -> "10_30"
            min <= 60 -> "30_60"
            else -> "60p"
        }
    }

    fun dailyMinBucket(minutes: Int): String = when {
        minutes <= 0 -> "0"
        minutes <= 15 -> "1_15"
        minutes <= 30 -> "16_30"
        minutes <= 60 -> "31_60"
        minutes <= 120 -> "61_120"
        else -> "120p"
    }

    fun hourBucket(nowMs: Long = System.currentTimeMillis()): String {
        val hour = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> "night"
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            else -> "evening"
        }
    }

    fun weekday(nowMs: Long = System.currentTimeMillis()): Int =
        Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.DAY_OF_WEEK) - 1

    fun appCaps(
        intent: Boolean? = null,
        time: Boolean? = null,
        period: Boolean? = null,
        session: Boolean? = null
    ): Map<String, Any?> = buildMap {
        if (intent != null) put(HaEvents.Prop.CAP_INTENT, intent)
        if (time != null) put(HaEvents.Prop.CAP_TIME, time)
        if (period != null) put(HaEvents.Prop.CAP_PERIOD, period)
        if (session != null) put(HaEvents.Prop.CAP_SESSION, session)
    }

    /** 单段锁定时长（分钟）；全天按 1440。 */
    fun windowDurationMinutes(window: PeriodWindow): Int = when {
        window.isAllDay -> 24 * 60
        window.crossesMidnight -> (24 * 60 - window.startMinute) + window.endMinute
        else -> (window.endMinute - window.startMinute).coerceAtLeast(0)
    }

    /**
     * 周内日均锁定小时（开启的窗口 × 生效日数 / 7）。
     * 返回一位小数字符串，便于事件 props / 快照字段一致。
     */
    fun averageDailyPeriodLockHours(windows: List<PeriodWindow>): String {
        val enabled = windows.filter { it.enabled }
        if (enabled.isEmpty()) return "0"
        var totalMinWeek = 0
        for (w in enabled) {
            val days = Integer.bitCount(w.daysMask and PeriodDays.EVERY_DAY).coerceAtLeast(0)
            totalMinWeek += windowDurationMinutes(w) * days
        }
        val hours = totalMinWeek / 60.0 / 7.0
        return String.format("%.1f", hours)
    }

    fun averageDailyPeriodLockHoursFromJson(json: String?): String =
        averageDailyPeriodLockHours(PeriodWindowsCodec.decode(json))

    fun periodWindowCount(json: String?): Int =
        PeriodWindowsCodec.decode(json).count { it.enabled }

    /** bind/edit 共用的能力细节 props（不含 app/pkg）。 */
    fun capabilityDetailProps(
        intent: Boolean,
        time: Boolean,
        period: Boolean,
        session: Boolean,
        keywords: Boolean,
        dailyLimitMinutes: Int,
        defaultSessionMin: Int = 0,
        periodWindowsJson: String? = null,
        keywordCount: Int = 0,
        source: String? = null
    ): Map<String, Any?> = buildMap {
        put(HaEvents.Prop.INTENT, intent)
        put(HaEvents.Prop.TIME, time)
        put(HaEvents.Prop.PERIOD, period)
        put(HaEvents.Prop.SESSION, session)
        put(HaEvents.Prop.KEYWORDS, keywords)
        put(HaEvents.Prop.DAILY_MIN_BUCKET, dailyMinBucket(dailyLimitMinutes))
        put(HaEvents.Prop.DAILY_LIMIT_MIN, dailyLimitMinutes.coerceAtLeast(0))
        if (defaultSessionMin > 0) {
            put(HaEvents.Prop.DEFAULT_SESSION_MIN, defaultSessionMin)
        }
        val windowCount = periodWindowCount(periodWindowsJson)
        put(HaEvents.Prop.PERIOD_WINDOW_COUNT, windowCount)
        put(
            HaEvents.Prop.PERIOD_LOCK_HOURS,
            if (period) averageDailyPeriodLockHoursFromJson(periodWindowsJson) else "0"
        )
        put(HaEvents.Prop.KEYWORD_COUNT, keywordCount.coerceAtLeast(0))
        if (!source.isNullOrBlank()) put(HaEvents.Prop.SOURCE, source)
    }
}
