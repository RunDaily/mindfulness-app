package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.analytics.AnalyticsBuckets
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel
import kotlin.math.abs

/**
 * 意图对照策略。
 *
 * - 每个开启意图门的 App 可配置是否启用对照，以及对照最低时长（5 / 10 / 15 / 20 分钟）
 * - 达到门槛后主动对照（横条 / 到点 / 结束确认）；首页符合条件的条目标【照】
 */
object ComparePolicy {

    val MIN_MINUTES_PRESETS: List<Int> = listOf(5, 10, 15, 20)

    const val DEFAULT_MIN_MINUTES = 10
    const val FLOOR_MIN_MINUTES = 5
    const val CEILING_MIN_MINUTES = 20

    /** 默认开启对照（与旧版「满 10 分钟主动对照」对齐） */
    const val DEFAULT_ENABLED = true

    fun sanitizeMinMinutes(minutes: Int): Int {
        if (minutes in MIN_MINUTES_PRESETS) return minutes
        return MIN_MINUTES_PRESETS.minByOrNull { abs(it - minutes) } ?: DEFAULT_MIN_MINUTES
    }

    fun minSeconds(compareMinMinutes: Int = DEFAULT_MIN_MINUTES): Long =
        sanitizeMinMinutes(compareMinMinutes) * 60L

    fun shouldOfferInlineCompare(
        hasIntentGate: Boolean,
        purpose: String?,
        durationSeconds: Long,
        compareEnabled: Boolean = DEFAULT_ENABLED,
        compareMinMinutes: Int = DEFAULT_MIN_MINUTES,
        @Suppress("UNUSED_PARAMETER") intentKind: IntentKind? = null,
        /** 觉察练习总闸；关则中途与离开对照都不主动出 */
        practiceEnabled: Boolean = true,
    ): Boolean {
        if (!practiceEnabled) return false
        if (!hasIntentGate) return false
        if (purpose.isNullOrBlank()) return false
        if (!compareEnabled) return false
        if (durationSeconds <= 0L) return false
        return durationSeconds >= minSeconds(compareMinMinutes)
    }

    /**
     * 有意图但不应主动打扰：跳过横条，也不发延后提醒。
     * 未达门槛时首页也不标【照】。
     */
    fun shouldSkipActiveCompare(
        hasIntentGate: Boolean,
        purpose: String?,
        durationSeconds: Long,
        compareEnabled: Boolean = DEFAULT_ENABLED,
        compareMinMinutes: Int = DEFAULT_MIN_MINUTES,
        intentKind: IntentKind? = null,
        practiceEnabled: Boolean = true,
    ): Boolean {
        if (!practiceEnabled) return true
        if (!hasIntentGate || purpose.isNullOrBlank() || durationSeconds <= 0L) return false
        return !shouldOfferInlineCompare(
            hasIntentGate = hasIntentGate,
            purpose = purpose,
            durationSeconds = durationSeconds,
            compareEnabled = compareEnabled,
            compareMinMinutes = compareMinMinutes,
            intentKind = intentKind,
            practiceEnabled = true,
        )
    }

    fun isPurposeVague(purpose: String?): Boolean =
        AnalyticsBuckets.purposeClarity(purpose) == HaEvents.Clarity.VAGUE

    /** 写意图阶段的轻提示；不拦进入，只为结束时更好对照 */
    fun vaguePurposeNudge(purpose: String?): String? {
        val text = purpose?.trim().orEmpty()
        if (text.length < 2) return null
        return if (isPurposeVague(text)) {
            "再具体一点，结束时更好对照"
        } else {
            null
        }
    }
}

/**
 * 跑偏时长：对照时采集，用于空转归类。
 */
object DriftSecondsPolicy {

    fun defaultFor(level: Int?, durationSeconds: Long): Long? {
        val dur = durationSeconds.coerceAtLeast(0L)
        return when (level) {
            MindfulnessLevel.ALIGNED -> 0L
            MindfulnessLevel.SLIGHT -> dur / 2L
            MindfulnessLevel.LARGE -> (dur * 3L / 4L).coerceAtMost(dur)
            else -> null
        }
    }

    /** 写入/统计用：没跑偏恒为 0；跑偏/跑远用用户值或档位默认 */
    fun resolveStored(
        level: Int?,
        driftSeconds: Long?,
        durationSeconds: Long
    ): Long? {
        if (!MindfulnessLevel.isValid(level)) return null
        val dur = durationSeconds.coerceAtLeast(0L)
        if (level == MindfulnessLevel.ALIGNED) return 0L
        val raw = driftSeconds ?: defaultFor(level, dur) ?: return null
        return raw.coerceIn(0L, dur)
    }

    /** 对照 UI 可选比例（相对本次时长） */
    fun fractionPresets(durationSeconds: Long): List<Pair<String, Long>> {
        val dur = durationSeconds.coerceAtLeast(0L)
        if (dur <= 0L) return emptyList()
        return listOf(
            "约¼" to (dur / 4L),
            "约一半" to (dur / 2L),
            "大半" to (dur * 3L / 4L),
            "几乎全部" to dur
        ).distinctBy { it.second }
    }
}
