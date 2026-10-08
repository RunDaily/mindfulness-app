package com.life.mindfulnessapp.debug

import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.EndReason
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug 专用：注入「本周一～周日」演示使用记录 + 三个典型 App 的监控配置。
 *
 * 场景覆盖意图门 / 门外守住 / 日限·次限·时段锁 / 各类中断 / 对照三档。
 */
@Singleton
class DebugMockUsageSeeder @Inject constructor(
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository
) {

    data class Result(
        val recordCount: Int,
        val limitCount: Int,
        val weekStartMs: Long,
        val weekEndMs: Long
    )

    suspend fun seedCurrentWeek(nowMs: Long = System.currentTimeMillis()): Result {
        val (weekStart, weekEnd) = UsageRecordRepository.getWeekRange(nowMs)
        val (prevStart, prevEnd) = UsageRecordRepository.getLastWeekRange(nowMs)

        ensureDemoLimits(createdAtMs = weekStart - 14L * DAY_MS)

        usageRecordRepository.deleteInRangeForPackages(DEMO_PACKAGES, prevStart, weekEnd)

        val records = buildList {
            addAll(buildPreviousWeekLight(prevStart))
            addAll(buildCurrentWeek(weekStart, nowMs.coerceAtMost(weekEnd - 1)))
        }
        usageRecordRepository.insertRecords(records)

        return Result(
            recordCount = records.size,
            limitCount = DEMO_PACKAGES.size,
            weekStartMs = weekStart,
            weekEndMs = weekEnd
        )
    }

    private suspend fun ensureDemoLimits(createdAtMs: Long) {
        val orderBase = appLimitRepository.nextSortOrder()
        DEMO_LIMITS.forEachIndexed { index, template ->
            val existing = appLimitRepository.getAppLimit(template.packageName)
            val limit = template.copy(
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: createdAtMs,
                sortOrder = existing?.sortOrder ?: (orderBase + index),
                baselineDailyAvgSeconds = existing?.baselineDailyAvgSeconds
                    ?.takeIf { it > 0L }
                    ?: template.baselineDailyAvgSeconds,
                baselineCapturedAt = existing?.baselineCapturedAt
                    ?.takeIf { it > 0L }
                    ?: createdAtMs
            )
            appLimitRepository.saveAppLimit(limit)
        }
    }

    /**
     * 上周少量数据，供周回顾「对比上周」环亮起来。
     */
    private fun buildPreviousWeekLight(prevWeekStart: Long): List<UsageRecordEntity> = listOf(
        session(
            pkg = WECHAT,
            dayStart = prevWeekStart + 2 * DAY_MS,
            hour = 12, minute = 10,
            durationSec = 9 * 60,
            endReason = EndReason.MANUAL,
            purpose = "回客户消息",
            intentKind = IntentKind.PURPOSEFUL,
            mindfulness = MindfulnessLevel.ALIGNED
        ),
        session(
            pkg = XHS,
            dayStart = prevWeekStart + 4 * DAY_MS,
            hour = 21, minute = 5,
            durationSec = 28 * 60,
            endReason = EndReason.MANUAL,
            purpose = "随便看看",
            intentKind = IntentKind.PURPOSEFUL,
            mindfulness = MindfulnessLevel.LARGE,
            note = "刷到很晚"
        ),
        session(
            pkg = SGAME,
            dayStart = prevWeekStart + 5 * DAY_MS,
            hour = 15, minute = 0,
            durationSec = 40 * 60,
            endReason = EndReason.MANUAL
        )
    )

    private fun buildCurrentWeek(weekStart: Long, latestMs: Long): List<UsageRecordEntity> {
        val out = mutableListOf<UsageRecordEntity>()
        // dayOffset 0=周一 … 6=周日
        fun day(offset: Int): Long = weekStart + offset * DAY_MS

        fun add(record: UsageRecordEntity) {
            if (record.startTime <= latestMs) out += record
        }

        // ── 周一：标准闭环 + 门外守住 + 游戏直进 ───────────────
        add(
            session(
                pkg = WECHAT, dayStart = day(0), hour = 8, minute = 15,
                durationSec = 7 * 60,
                endReason = EndReason.MANUAL,
                purpose = "回妈妈消息",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.ALIGNED,
                note = "说完就收"
            )
        )
        add(
            session(
                pkg = WECHAT, dayStart = day(0), hour = 9, minute = 2,
                durationSec = 0,
                endReason = EndReason.GATE_DISMISS
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(0), hour = 12, minute = 40,
                durationSec = 2 * 60 + 20,
                endReason = EndReason.MANUAL,
                purpose = "刷一下推荐",
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 5
            )
        )
        add(
            session(
                pkg = SGAME, dayStart = day(0), hour = 20, minute = 10,
                durationSec = 35 * 60,
                endReason = EndReason.MANUAL
            )
        )

        // ── 周二：单次上限 + 离开倒计时 + 时段锁踢出 ───────────────────────
        add(
            session(
                pkg = WECHAT, dayStart = day(1), hour = 10, minute = 0,
                durationSec = 15 * 60,
                endReason = EndReason.SESSION_LIMIT_REACHED,
                purpose = "看工作群进度",
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 15,
                mindfulness = MindfulnessLevel.SLIGHT,
                note = "又点开了无关聊天"
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(1), hour = 14, minute = 20,
                durationSec = 11 * 60,
                endReason = EndReason.AWAY_COUNTDOWN,
                purpose = "搜装修灵感",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.ALIGNED
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(1), hour = 18, minute = 5,
                durationSec = 0,
                endReason = EndReason.GATE_DISMISS
            )
        )
        add(
            session(
                pkg = SGAME, dayStart = day(1), hour = 21, minute = 50,
                durationSec = 12 * 60,
                endReason = EndReason.PERIOD_LOCK
            )
        )

        // ── 周三：日限到点 + 切换应用 + 后台超时 ─────────────────
        add(
            session(
                pkg = WECHAT, dayStart = day(2), hour = 9, minute = 30,
                durationSec = 90,
                endReason = EndReason.MANUAL,
                purpose = "发个红包",
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 5
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(2), hour = 11, minute = 0,
                durationSec = 22 * 60,
                endReason = EndReason.MANUAL,
                purpose = "看穿搭合集",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.SLIGHT,
                note = "看完合集又滑了推荐"
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(2), hour = 19, minute = 10,
                durationSec = 25 * 60,
                endReason = EndReason.LIMIT_REACHED,
                purpose = "找晚饭做法",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.ALIGNED
            )
        )
        add(
            session(
                pkg = SGAME, dayStart = day(2), hour = 16, minute = 0,
                durationSec = 18 * 60,
                endReason = EndReason.SWITCHED_AWAY
            )
        )
        add(
            session(
                pkg = WECHAT, dayStart = day(2), hour = 21, minute = 0,
                durationSec = 6 * 60,
                endReason = EndReason.BACKGROUND_TIMEOUT,
                purpose = "确认快递信息",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.ALIGNED
            )
        )

        // ── 周四：长会话对齐 + 息屏结束 + 游戏日限 + 加入前种子 ─────────────
        add(
            session(
                pkg = WECHAT, dayStart = day(3), hour = 20, minute = 0,
                durationSec = 22 * 60,
                endReason = EndReason.MANUAL,
                purpose = "视频通话家人",
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 30,
                mindfulness = MindfulnessLevel.ALIGNED
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(3), hour = 13, minute = 15,
                durationSec = 8 * 60,
                endReason = EndReason.SCREEN_OFF_TIMEOUT,
                purpose = "看一篇探店",
                intentKind = IntentKind.PURPOSEFUL
            )
        )
        add(
            session(
                pkg = SGAME, dayStart = day(3), hour = 14, minute = 0,
                durationSec = 55 * 60,
                endReason = EndReason.MANUAL
            )
        )
        add(
            session(
                pkg = SGAME, dayStart = day(3), hour = 19, minute = 30,
                durationSec = 70 * 60,
                endReason = EndReason.LIMIT_REACHED
            )
        )
        add(
            session(
                pkg = WECHAT, dayStart = day(3), hour = 7, minute = 0,
                durationSec = 15 * 60,
                endReason = EndReason.SEED_FROM_SYSTEM
            )
        )

        // ── 周五：跑远了 + 续时后次限 + 离开中断 ───────────────────────────
        add(
            session(
                pkg = WECHAT, dayStart = day(4), hour = 22, minute = 10,
                durationSec = 28 * 60,
                endReason = EndReason.MANUAL,
                purpose = "刷朋友圈",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.LARGE,
                note = "最后去看广告和八卦了"
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(4), hour = 12, minute = 5,
                durationSec = 20 * 60,
                endReason = EndReason.SESSION_LIMIT_REACHED,
                purpose = "回评",
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 15,
                sessionExtensionMinutes = 5,
                mindfulness = MindfulnessLevel.SLIGHT,
                note = "回完又继续滑"
            )
        )
        add(
            session(
                pkg = SGAME, dayStart = day(4), hour = 17, minute = 20,
                durationSec = 24 * 60,
                endReason = EndReason.AWAY_COUNTDOWN
            )
        )
        add(
            session(
                pkg = WECHAT, dayStart = day(4), hour = 15, minute = 40,
                durationSec = 0,
                endReason = EndReason.GATE_DISMISS
            )
        )

        // ── 周六：周末高峰 + 异常收口 + 多段有意图 ─────────────────────────
        add(
            session(
                pkg = SGAME, dayStart = day(5), hour = 10, minute = 30,
                durationSec = 48 * 60,
                endReason = EndReason.MANUAL
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(5), hour = 11, minute = 20,
                durationSec = 16 * 60,
                endReason = EndReason.MANUAL,
                purpose = "收藏周末去处",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.ALIGNED
            )
        )
        add(
            session(
                pkg = WECHAT, dayStart = day(5), hour = 14, minute = 0,
                durationSec = 12 * 60,
                endReason = EndReason.APP_CLOSED,
                purpose = "约晚饭",
                intentKind = IntentKind.PURPOSEFUL
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(5), hour = 21, minute = 0,
                durationSec = 40 * 60,
                endReason = EndReason.LIMIT_REACHED,
                purpose = "看探店",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.LARGE,
                note = "超限前还在刷"
            )
        )
        add(
            session(
                pkg = SGAME, dayStart = day(5), hour = 22, minute = 5,
                durationSec = 8 * 60,
                endReason = EndReason.PERIOD_LOCK
            )
        )

        // ── 周日：收束日混合（含未评对照）──────────────────────────────────
        add(
            session(
                pkg = WECHAT, dayStart = day(6), hour = 9, minute = 10,
                durationSec = 5 * 60,
                endReason = EndReason.MANUAL,
                purpose = "发周报截图",
                intentKind = IntentKind.PURPOSEFUL,
                mindfulness = MindfulnessLevel.ALIGNED
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(6), hour = 15, minute = 0,
                durationSec = 9 * 60,
                endReason = EndReason.MANUAL,
                purpose = "搜周一穿搭",
                intentKind = IntentKind.PURPOSEFUL
                // 未评对照：验证时间轴「补一句」入口
            )
        )
        add(
            session(
                pkg = XHS, dayStart = day(6), hour = 16, minute = 30,
                durationSec = 0,
                endReason = EndReason.GATE_DISMISS
            )
        )
        add(
            session(
                pkg = SGAME, dayStart = day(6), hour = 18, minute = 0,
                durationSec = 32 * 60,
                endReason = EndReason.MANUAL
            )
        )
        add(
            session(
                pkg = WECHAT, dayStart = day(6), hour = 20, minute = 40,
                durationSec = 4 * 60,
                endReason = EndReason.MANUAL,
                purpose = "确认明天行程",
                intentKind = IntentKind.PURPOSEFUL,
                sessionLimitMinutes = 5
            )
        )

        return out
    }

    private fun session(
        pkg: String,
        dayStart: Long,
        hour: Int,
        minute: Int,
        durationSec: Long,
        endReason: String,
        purpose: String? = null,
        intentKind: IntentKind? = null,
        sessionLimitMinutes: Int = 0,
        sessionExtensionMinutes: Int = 0,
        mindfulness: Int? = null,
        note: String? = null
    ): UsageRecordEntity {
        val start = dayStart + hour * 3_600_000L + minute * 60_000L
        val end = if (durationSec <= 0L && EndReason.isGateQuit(purpose, endReason, durationSec)) {
            start + 800L
        } else {
            start + durationSec * 1000L
        }
        return UsageRecordEntity(
            id = 0L,
            packageName = pkg,
            startTime = start,
            endTime = end,
            durationSeconds = durationSec,
            endReason = endReason,
            purpose = purpose,
            intentKind = intentKind?.name,
            sessionLimitMinutes = sessionLimitMinutes,
            sessionExtensionMinutes = sessionExtensionMinutes,
            note = note,
            mindfulnessLevel = mindfulness
        )
    }

    companion object {
        const val WECHAT = "com.tencent.mm"
        const val XHS = "com.xingin.xhs"
        const val SGAME = "com.tencent.tmgp.sgame"

        val DEMO_PACKAGES = listOf(WECHAT, XHS, SGAME)

        private const val DAY_MS = 24L * 60 * 60 * 1000

        private val DEMO_LIMITS: List<AppLimitEntity> = listOf(
            // 微信：意图门 + 单次上限 + 轻度日限 —— 社交沟通典型
            AppLimitEntity(
                packageName = WECHAT,
                appName = "微信",
                dailyLimitMinutes = 90,
                isEnabled = true,
                timeLimitEnabled = true,
                requireIntentOnOpen = true,
                sessionLimitEnabled = true,
                defaultSessionLimitMinutes = 15,
                periodLockEnabled = false,
                baselineDailyAvgSeconds = 55 * 60L
            ),
            // 小红书：意图门 + 偏紧日限 —— 刷内容 / 超限典型
            AppLimitEntity(
                packageName = XHS,
                appName = "小红书",
                dailyLimitMinutes = 45,
                isEnabled = true,
                timeLimitEnabled = true,
                requireIntentOnOpen = true,
                sessionLimitEnabled = true,
                defaultSessionLimitMinutes = 15,
                periodLockEnabled = false,
                baselineDailyAvgSeconds = 70 * 60L
            ),
            // 王者荣耀：直进（无意图门）+ 日限 + 夜间时段锁 —— 游戏典型
            AppLimitEntity(
                packageName = SGAME,
                appName = "王者荣耀",
                dailyLimitMinutes = 120,
                isEnabled = true,
                timeLimitEnabled = true,
                requireIntentOnOpen = false,
                sessionLimitEnabled = false,
                periodLockEnabled = true,
                periodWindowsJson = PeriodWindowsCodec.encode(
                    listOf(
                        PeriodWindow.defaultSleep().copy(
                            message = "夜深了，明天再战"
                        )
                    )
                ),
                periodLockCommitment = "夜深了，明天再战",
                baselineDailyAvgSeconds = 90 * 60L
            )
        )
    }
}
