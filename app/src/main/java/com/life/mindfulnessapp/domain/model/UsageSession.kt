package com.life.mindfulnessapp.domain.model

import android.os.SystemClock

/**
 * 当前正在进行的使用会话（内存中）
 *
 * 计时设计：
 *   - [startTime]：本次「前台段」的墙钟起点（写库 / 展示用）；回前台时重置。
 *   - [segmentElapsedRealtimeMs]：本次前台段的 [SystemClock.elapsedRealtime] 起点；
 *     优先用它算时长，避免墙钟跳变；checkpoint 恢复后可为 0（回退墙钟）。
 *   - [accumulatedActiveMs]：之前所有前台段累计的有效毫秒（后台已排除，不按秒截断）。
 *   - [currentSessionSeconds]：历史段 + 当前段 = 有效使用总秒数（不含后台）。
 */
data class UsageSession(
    val recordId: Long,                       // 对应数据库记录 ID
    val packageName: String,
    val appName: String,
    val startTime: Long,                      // 当前「前台段」开始时间戳（毫秒）
    /**
     * 整次会话的原始开始时间（毫秒）。
     * 前后台切换会重置 [startTime]，但写库 / 时间轴必须以本字段为准。
     */
    val sessionOriginStartMs: Long = startTime,
    val dailyLimitSeconds: Long,              // 今日生效限额（秒，含延长天花板）；时长锁关闭时为 0
    val dailyUsedSeconds: Long,               // 今日历史已用时长（秒，不含本次会话）
    val weeklyLimitSeconds: Long,             // 本周限制时长（秒）；时长锁关闭时为 0
    val weeklyUsedSeconds: Long,              // 本周历史已用时长（秒，不含本次会话）
    /** 本次会话中已结束的前台段累计毫秒（不含当前段） */
    val accumulatedActiveMs: Long = 0L,
    /**
     * 当前前台段起点（[SystemClock.elapsedRealtime]）。
     * 0 = 未设置，回退用 [startTime] 墙钟差。
     */
    val segmentElapsedRealtimeMs: Long = 0L,
    val purpose: String? = null,
    val intentKind: IntentKind? = null,
    /** 本次会话基础时长上限（秒）；0 = 不设单次上限 */
    val sessionLimitSeconds: Long = 0L,
    /** 本次已授予的续时（秒）；最多一次 */
    val sessionExtensionSeconds: Long = 0L,
    /** 是否已使用过一次续时入口 */
    val sessionExtensionUsed: Boolean = false,
    val isInBackground: Boolean = false,
    val backgroundSinceMs: Long = 0L,
    /**
     * 是否为「超限续记」会话。
     * 用户在超限页点击「知道了」后，若 App 仍在前台，系统会自动开启此类 session 继续计时，
     * 以确保超出限额后的实际使用时长也被完整记录。
     * 处于此状态的 session 不再触发超限提示页（避免反复弹出）。
     */
    val isOverLimitSession: Boolean = false,
    /** 本会话对应 App 是否开启意图门（打开前写意图） */
    val requireIntentOnOpen: Boolean = true,
    /** 本会话对应 App 是否开启时长锁（日/周限额与超限阻断） */
    val timeLimitEnabled: Boolean = true,
    /** 今日已进入次数（含本次），供胶囊今日摘要展示 */
    val todayEnterCount: Int = 0,
    /** 历史遗留字段，会话层不再使用；结束对照由有意图文案触发 */
    val intentReviewEnabled: Boolean = false,
    /** 本会话是否启用对照 */
    val compareEnabled: Boolean = true,
    /** 本会话对照最低时长（分钟） */
    val compareMinMinutes: Int = 10,
    /** 配置的基础日限额（秒，不含延长）；胶囊展示 `已用/基础分+N` 用 */
    val dailyBaseLimitSeconds: Long = 0L,
    /** 当日触顶延长秒数（胶囊彩色 +N；生效限额见 [dailyLimitSeconds]） */
    val dailyGraceBonusSeconds: Long = 0L
) {
    /** 胶囊分母：基础日限额；未显式写入时回退为生效限额去掉展示延长 */
    val displayDailyBaseLimitSeconds: Long
        get() = when {
            dailyBaseLimitSeconds > 0L -> dailyBaseLimitSeconds
            dailyGraceBonusSeconds > 0L ->
                (dailyLimitSeconds - dailyGraceBonusSeconds).coerceAtLeast(0L)
            else -> dailyLimitSeconds
        }
    /** 时长锁在场：有生效限额或正处于超限续记 */
    val hasTimeLock: Boolean
        get() = timeLimitEnabled || isOverLimitSession

    /** 意图门在场 */
    val hasIntentGate: Boolean
        get() = requireIntentOnOpen

    /** 诚实冲动 / 历史无目的旁路：不按「事务跑偏」叙事 */
    val isUrgeNaming: Boolean
        get() = intentKind?.isUrgeNaming == true ||
            (intentKind == null && NamingKind.isUrgeNaming(purpose))

    /** 历史无目的旁路会话（新会话不再写入此 kind） */
    val isPurposeless: Boolean
        get() = intentKind == IntentKind.PURPOSELESS

    /** 生效的会话上限（含续时 grant） */
    val effectiveSessionLimitSeconds: Long
        get() = if (sessionLimitSeconds > 0L) {
            sessionLimitSeconds + sessionExtensionSeconds
        } else 0L

    val hasSessionLimit: Boolean
        get() = effectiveSessionLimitSeconds > 0L

    /** 已结束前台段累计秒（向下取整，仅便于日志 / 兼容旧字段语义） */
    val accumulatedActiveSeconds: Long
        get() = accumulatedActiveMs / 1000L

    /**
     * 本次会话有效前台毫秒 = 历史段 + 当前段（后台时不增长）。
     * 毫秒累计，避免来回切换时按秒截断导致显示回跳。
     */
    val currentActiveMs: Long
        get() = if (isInBackground) {
            accumulatedActiveMs
        } else {
            val segmentMs = if (segmentElapsedRealtimeMs > 0L) {
                (SystemClock.elapsedRealtime() - segmentElapsedRealtimeMs).coerceAtLeast(0L)
            } else {
                (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
            }
            accumulatedActiveMs + segmentMs
        }

    /**
     * 本次会话的有效前台使用时长（秒）= 历史段 + 当前段（后台时不增长）
     */
    val currentSessionSeconds: Long
        get() = currentActiveMs / 1000L

    val todayTotalSeconds: Long
        get() = dailyUsedSeconds + currentSessionSeconds

    val weekTotalSeconds: Long
        get() = weeklyUsedSeconds + currentSessionSeconds

    val dailyRemainingSeconds: Long
        get() = if (dailyLimitSeconds > 0) {
            (dailyLimitSeconds - todayTotalSeconds).coerceAtLeast(0)
        } else Long.MAX_VALUE

    val sessionRemainingSeconds: Long
        get() = if (hasSessionLimit) {
            (effectiveSessionLimitSeconds - currentSessionSeconds).coerceAtLeast(0)
        } else Long.MAX_VALUE

    /**
     * 胶囊预警 / 续时使用的「活动预算」剩余秒数：
     * 有单次上限时优先看会话剩余，否则看日剩余。
     */
    val budgetRemainingSeconds: Long
        get() = when {
            hasSessionLimit -> sessionRemainingSeconds
            dailyLimitSeconds > 0 -> dailyRemainingSeconds
            else -> Long.MAX_VALUE
        }

    val isDailyLimitExceeded: Boolean
        get() = dailyLimitSeconds > 0 && todayTotalSeconds >= dailyLimitSeconds

    val isWeeklyLimitExceeded: Boolean
        get() = weeklyLimitSeconds > 0 && weekTotalSeconds >= weeklyLimitSeconds

    val isSessionLimitReached: Boolean
        get() = hasSessionLimit && currentSessionSeconds >= effectiveSessionLimitSeconds

    /** 是否随意浏览陪伴路径（过程少打断；续时只在到点页给）。 */
    val isBrowseCompanion: Boolean
        get() = CompanionPath.resolve(intentKind, purpose, hasSessionLimit) == CompanionPath.BROWSE ||
            BrowseCasualIntent.isBrowseLike(purpose.orEmpty())

    /**
     * 临近结束时可在胶囊续一次：
     * - 有单次上限：按原时长 1/3；
     * - 纯日锁（无单次上限）：按**配置基础日限额** 1/3（不含续时 / 触顶延长，避免越续越大）；
     * 同一会话只允许一次（[sessionExtensionUsed]）。
     * 随意浏览过程中不在胶囊续（到点页见 [canOfferLimitReachedExtension]）。
     */
    val canOfferSessionExtension: Boolean
        get() {
            if (sessionExtensionUsed || isOverLimitSession) return false
            if (isBrowseCompanion) return false
            val baseMin = when {
                hasSessionLimit -> (sessionLimitSeconds / 60L).toInt()
                hasTimeLock && displayDailyBaseLimitSeconds > 0L ->
                    (displayDailyBaseLimitSeconds / 60L).toInt()
                else -> 0
            }
            return SessionLimitPolicy.maxExtensionMinutes(baseMin) > 0
        }

    /**
     * 到点页是否可「续一点时间」：含随意浏览；同一会话只一次。
     * 实际上限由调用方按随意浏览剩余 / 原时长 1/3 再算。
     */
    val canOfferLimitReachedExtension: Boolean
        get() {
            if (sessionExtensionUsed || isOverLimitSession || !hasSessionLimit) return false
            if (isBrowseCompanion) return true
            return SessionLimitPolicy.maxExtensionMinutes((sessionLimitSeconds / 60L).toInt()) > 0
        }

    /**
     * 续时档位所依据的基础分钟数。
     * 单次上限优先；纯日锁用配置基础日限额（不含本会话续时与触顶 +N）。
     */
    val extensionBaseMinutes: Int
        get() = when {
            sessionLimitSeconds > 0L -> (sessionLimitSeconds / 60L).toInt()
            displayDailyBaseLimitSeconds > 0L -> (displayDailyBaseLimitSeconds / 60L).toInt()
            else -> 0
        }
}
