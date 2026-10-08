package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.util.AppUsageFormat
import kotlin.math.roundToInt

data class UsageInsights(
    /** 有记录 App 的日均时长合计（秒） */
    val summedDailyAvgSeconds: Long,
    val topByDuration: RankedUsage?,
    val topByLaunches: RankedUsage?,
    val trackedAppCount: Int
) {
    data class RankedUsage(
        val app: AppInfo,
        val usage: AppWeeklySystemUsage
    )
}

/**
 * 基于近 7 日系统用量的能力匹配评估。
 *
 * 三个信号维度：
 * - **打开频率**（日均次数）→ 无意识顺手点开
 * - **总投入**（日均时长）→ 时间黑洞
 * - **单次深度**（总时长 ÷ 打开次数）→ 短刷 vs 沉浸
 */
enum class CapabilityFitLevel {
    /** 匹配度 ≥ 70 */
    Strong,
    /** 50–69，可考虑 */
    Moderate,
    /** 30–49，非首选 */
    Low,
    /** < 30，暂不推荐 */
    Unlikely
}

data class CapabilityFitDimension(
    val kind: CapabilityKind,
    /** 0–100 */
    val score: Int,
    val level: CapabilityFitLevel,
    val reasons: List<String>
)

data class AppCapabilityFit(
    val app: AppInfo,
    val usage: AppWeeklySystemUsage,
    /** 近 7 日平均单次前台时长（秒） */
    val avgSessionSeconds: Long,
    val dimensions: List<CapabilityFitDimension>,
    val primary: CapabilityKind,
    val secondary: CapabilityKind?,
    /** 一句话结论 */
    val headline: String,
    /** 数据快照，如「日均 23 次 · 单次约 2 分 · 日均 52 分」 */
    val dataSnapshot: String,
    /** 综合推荐强度，用于排序 */
    val overallScore: Int
) {
    fun dimension(kind: CapabilityKind): CapabilityFitDimension? =
        dimensions.find { it.kind == kind }

    val primaryDimension: CapabilityFitDimension
        get() = dimensions.first { it.kind == primary }
}

/** @deprecated 兼容旧引用；请用 [AppCapabilityFit] */
typealias AppCapabilityRecommendation = AppCapabilityFit

object CapabilityFitAssessor {

    private const val MIN_LAUNCHES_FOR_SESSION = 1
    private const val SHORT_SESSION_SEC = 5 * 60L
    private const val LONG_SESSION_SEC = 12 * 60L
    private const val HIGH_DAILY_SEC = 45 * 60L
    private const val VERY_HIGH_DAILY_SEC = 90 * 60L

    fun buildInsights(
        apps: List<AppInfo>,
        usageByPackage: Map<String, AppWeeklySystemUsage>
    ): UsageInsights? = CapabilityRecommender.buildInsights(apps, usageByPackage)

    fun assess(
        app: AppInfo,
        usage: AppWeeklySystemUsage
    ): AppCapabilityFit? {
        if (usage.totalSeconds <= 0L && usage.totalLaunches <= 0) return null

        val avgSec = usage.avgDailySeconds
        val avgLaunch = usage.avgDailyLaunches
        val launches = usage.totalLaunches.coerceAtLeast(MIN_LAUNCHES_FOR_SESSION)
        val avgSession = (usage.totalSeconds / launches).coerceAtLeast(1L)

        val intent = scoreIntentGate(avgSec, avgLaunch, avgSession)
        val time = scoreTimeLock(avgSec, avgLaunch, avgSession)
        val period = scorePeriodLock(avgSec, avgLaunch, avgSession)

        val dimensions = listOf(intent, time, period).sortedByDescending { it.score }
        val primary = dimensions.first().kind
        val secondary = dimensions.getOrNull(1)?.takeIf { it.score >= 50 }?.kind

        val headline = buildHeadline(primary, secondary, intent, time, period)
        val snapshot = buildDataSnapshot(avgSec, avgLaunch, avgSession)
        val overall = dimensions.maxOf { it.score }

        return AppCapabilityFit(
            app = app,
            usage = usage,
            avgSessionSeconds = avgSession,
            dimensions = listOf(intent, time, period),
            primary = primary,
            secondary = secondary,
            headline = headline,
            dataSnapshot = snapshot,
            overallScore = overall
        )
    }

    fun recommend(
        apps: List<AppInfo>,
        usageByPackage: Map<String, AppWeeklySystemUsage>,
        topN: Int = 3
    ): List<AppCapabilityFit> {
        val appByPkg = apps.associateBy { it.packageName }
        return usageByPackage.mapNotNull { (pkg, usage) ->
            val app = appByPkg[pkg] ?: return@mapNotNull null
            if (app.isMonitored) return@mapNotNull null
            assess(app, usage)?.takeIf { it.overallScore >= 45 }
        }
            .sortedByDescending { it.overallScore }
            .take(topN)
    }

    private fun scoreIntentGate(
        avgDailySec: Long,
        avgDailyLaunch: Int,
        avgSessionSec: Long
    ): CapabilityFitDimension {
        val launchScore = (avgDailyLaunch / 30f * 100f).coerceIn(0f, 100f)
        val shortSessionScore = when {
            avgSessionSec <= SHORT_SESSION_SEC ->
                (100f - avgSessionSec / SHORT_SESSION_SEC.toFloat() * 30f)
            avgSessionSec <= LONG_SESSION_SEC -> 55f
            else -> 25f
        }
        val freqNotDurationScore = when {
            avgDailyLaunch >= 8 && avgDailySec < HIGH_DAILY_SEC -> 90f
            avgDailyLaunch >= 5 -> 70f
            avgDailyLaunch >= 3 -> 50f
            else -> 20f
        }
        val raw = launchScore * 0.4f + shortSessionScore * 0.35f + freqNotDurationScore * 0.25f
        val score = raw.roundToInt().coerceIn(0, 100)
        val reasons = buildList {
            if (avgDailyLaunch >= 8) {
                add("日均打开 ${avgDailyLaunch} 次，偏「顺手点开」")
            } else if (avgDailyLaunch >= 4) {
                add("有一定打开频率（日均 ${avgDailyLaunch} 次）")
            }
            if (avgSessionSec <= SHORT_SESSION_SEC) {
                add("单次约 ${formatBriefDuration(avgSessionSec)}，多为短刷")
            }
            if (avgDailyLaunch >= 6 && avgDailySec < HIGH_DAILY_SEC) {
                add("次数多但总时长可控，适合在门口建立觉察")
            }
            if (isEmpty()) {
                add("打开模式较温和，意图门可作预防性觉察")
            }
        }
        return CapabilityFitDimension(
            kind = CapabilityKind.IntentGate,
            score = score,
            level = levelOf(score),
            reasons = reasons.take(3)
        )
    }

    private fun scoreTimeLock(
        avgDailySec: Long,
        avgDailyLaunch: Int,
        avgSessionSec: Long
    ): CapabilityFitDimension {
        val durationScore = (avgDailySec / VERY_HIGH_DAILY_SEC.toFloat() * 100f).coerceIn(0f, 100f)
        val sustainedScore = when {
            avgSessionSec >= LONG_SESSION_SEC -> 85f
            avgSessionSec >= 8 * 60L -> 65f
            else -> (avgSessionSec / (8 * 60f) * 50f)
        }
        val volumeScore = when {
            avgDailySec >= VERY_HIGH_DAILY_SEC -> 95f
            avgDailySec >= HIGH_DAILY_SEC -> 80f
            avgDailySec >= 25 * 60L -> 55f
            else -> (avgDailySec / (25 * 60f).toFloat() * 40f)
        }
        val raw = durationScore * 0.5f + sustainedScore * 0.3f + volumeScore * 0.2f
        val score = raw.roundToInt().coerceIn(0, 100)
        val reasons = buildList {
            if (avgDailySec >= VERY_HIGH_DAILY_SEC) {
                add("日均 ${AppUsageFormat.avgDailyDurationShort(avgDailySec).removeSuffix("/天")}，总时长很高")
            } else if (avgDailySec >= HIGH_DAILY_SEC) {
                add("日均使用超过 45 分钟，适合设日额度")
            } else if (avgDailySec >= 20 * 60L) {
                add("有一定日投入，可先设温和上限")
            }
            if (avgSessionSec >= LONG_SESSION_SEC) {
                add("单次常超过 ${formatBriefDuration(LONG_SESSION_SEC)}，容易沉浸")
            }
            if (avgDailySec >= HIGH_DAILY_SEC && avgDailyLaunch >= 8) {
                add("时长与次数双高，建议时长锁守住底线")
            }
            if (isEmpty()) {
                add("日总时长不高，时长锁优先级较低")
            }
        }
        return CapabilityFitDimension(
            kind = CapabilityKind.TimeLock,
            score = score,
            level = levelOf(score),
            reasons = reasons.take(3)
        )
    }

    private fun scorePeriodLock(
        avgDailySec: Long,
        avgDailyLaunch: Int,
        avgSessionSec: Long
    ): CapabilityFitDimension {
        // 无小时分布时：用「高投入 + 难自控」信号提示时段边界，不作为首绑主推荐
        val addictionSignal = when {
            avgDailySec >= VERY_HIGH_DAILY_SEC || (avgDailyLaunch >= 15 && avgDailySec >= HIGH_DAILY_SEC) -> 70
            avgDailySec >= HIGH_DAILY_SEC || avgDailyLaunch >= 12 -> 52
            avgDailySec >= 30 * 60L -> 38
            else -> 18
        }
        val score = addictionSignal.coerceIn(0, 75) // 上限 75，避免压过意图门/时长锁
        val reasons = buildList {
            add("适合为睡眠、学习等关键时段设「硬边界」")
            if (avgDailySec >= HIGH_DAILY_SEC || avgDailyLaunch >= 10) {
                add("该 App 投入已较高，可配合时段锁保护特定时间块")
            } else {
                add("用量数据无法判断具体时段，需在配置时自选锁定窗口")
            }
            add("常与意图门 / 时长锁叠加使用，而非单独首选")
        }
        return CapabilityFitDimension(
            kind = CapabilityKind.PeriodLock,
            score = score,
            level = levelOf(score),
            reasons = reasons
        )
    }

    private fun buildHeadline(
        primary: CapabilityKind,
        secondary: CapabilityKind?,
        intent: CapabilityFitDimension,
        time: CapabilityFitDimension,
        period: CapabilityFitDimension
    ): String {
        val primaryLabel = kindLabel(primary)
        val base = when (primary) {
            CapabilityKind.IntentGate -> when {
                intent.score >= 75 -> "更像「无意识点开」型，优先意图门"
                else -> "适合用意图门建立打开前的停顿"
            }
            CapabilityKind.TimeLock -> when {
                time.score >= 75 -> "日总时长偏高，优先时长锁设底线"
                else -> "适合用时长锁管住每日总投入"
            }
            CapabilityKind.PeriodLock -> "可配合时段锁保护关键时间（通常作叠加）"
        }
        val second = secondary?.let { kindLabel(it) }
        return if (second != null && secondary != CapabilityKind.PeriodLock) {
            "$base；亦可考虑$second"
        } else {
            base
        }
    }

    private fun buildDataSnapshot(
        avgDailySec: Long,
        avgDailyLaunch: Int,
        avgSessionSec: Long
    ): String = buildString {
        append("日均 ")
        append(avgDailyLaunch)
        append(" 次 · 单次约 ")
        append(formatBriefDuration(avgSessionSec))
        append(" · ")
        append(AppUsageFormat.avgDailyDurationShort(avgDailySec).removeSuffix("/天"))
    }

    private fun formatBriefDuration(seconds: Long): String {
        if (seconds < 60L) return "${seconds}秒"
        val min = seconds / 60L
        return if (min < 60L) "${min}分" else "${min / 60L}时${min % 60L}分"
    }

    private fun levelOf(score: Int): CapabilityFitLevel = when {
        score >= 70 -> CapabilityFitLevel.Strong
        score >= 50 -> CapabilityFitLevel.Moderate
        score >= 30 -> CapabilityFitLevel.Low
        else -> CapabilityFitLevel.Unlikely
    }

    private fun kindLabel(kind: CapabilityKind): String = when (kind) {
        CapabilityKind.IntentGate -> "意图门"
        CapabilityKind.TimeLock -> "时长锁"
        CapabilityKind.PeriodLock -> "时段锁"
    }
}

/** 保留 [CapabilityRecommender] 名称供旧代码过渡 */
object CapabilityRecommender {
    fun buildInsights(
        apps: List<AppInfo>,
        usageByPackage: Map<String, AppWeeklySystemUsage>
    ): UsageInsights? {
        if (usageByPackage.isEmpty()) return null
        val appByPkg = apps.associateBy { it.packageName }
        val ranked = usageByPackage.mapNotNull { (pkg, usage) ->
            val app = appByPkg[pkg] ?: return@mapNotNull null
            if (usage.totalSeconds <= 0L && usage.totalLaunches <= 0) return@mapNotNull null
            app to usage
        }
        if (ranked.isEmpty()) return null
        return UsageInsights(
            summedDailyAvgSeconds = ranked.sumOf { it.second.avgDailySeconds },
            topByDuration = ranked.maxByOrNull { it.second.totalSeconds }?.let {
                UsageInsights.RankedUsage(it.first, it.second)
            },
            topByLaunches = ranked.maxByOrNull { it.second.totalLaunches }?.let {
                UsageInsights.RankedUsage(it.first, it.second)
            },
            trackedAppCount = ranked.size
        )
    }

    fun recommend(
        apps: List<AppInfo>,
        usageByPackage: Map<String, AppWeeklySystemUsage>,
        topN: Int = 3
    ): List<AppCapabilityFit> = CapabilityFitAssessor.recommend(apps, usageByPackage, topN)
}
