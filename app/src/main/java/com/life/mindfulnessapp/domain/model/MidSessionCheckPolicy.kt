package com.life.mindfulnessapp.domain.model

/**
 * 使用中觉察节奏：锚提示（短暂看见）+ 觉察问题（可交互）。
 *
 * 契约见 `.design/awareness-practice-opt-in-sketch.html`：
 * - 觉察练习默认关；开后意图 / 搜索按节奏轻问，随意浏览全程不问。
 * - 仅意图 ≈30s 锚提示（条隐独占）；搜索 / 随意浏览不播进门锚提示。
 * - 搜索安静窗（= 节奏间隔）内不出轻问；到点前可仍有胶囊落成（30s）。
 */
object MidSessionCheckPolicy {

    /** 每场最多几次中途觉察 */
    const val MAX_CHECKS_PER_SESSION = 6

    /** 轻问岛无操作自动收（毫秒） */
    const val AUTO_COLLAPSE_MS = 5_000L

    /** 锚提示停留（毫秒）——条隐后短胶囊动画消费 */
    const val ANCHOR_HINT_HOLD_MS = 2_500L

    /** 意图进门回声锚提示时刻 */
    const val ANCHOR_HINT_SEC = 30L

    /**
     * 搜索路径：此前不出胶囊，到点再光点落成。
     * 与 [ANCHOR_HINT_SEC] 同秒，但叙事相反（搜索是首次出现，意图是条隐换回声）。
     */
    const val SEARCH_CAPSULE_APPEAR_SEC = 30L

    /** 「常」节奏间隔（旧默认） */
    const val GAP_NORMAL_SEC = 5 * 60L

    /** 「疏」节奏间隔 */
    const val GAP_SPARSE_SEC = 10 * 60L

    /** @deprecated 用 [gapSec] / [AwarenessPracticeRhythm.gapSec] */
    const val QUIET_AFTER_ENTER_SEC = GAP_NORMAL_SEC

    /** @deprecated 用 [gapSec] */
    const val FIRST_DEFAULT_SEC = GAP_NORMAL_SEC

    /** @deprecated 用 [gapSec] */
    const val FIRST_MIN_SEC = GAP_NORMAL_SEC

    /** @deprecated 用 [gapSec] */
    const val FIRST_MAX_SEC = GAP_NORMAL_SEC

    /** @deprecated 用 [gapSec] */
    const val SECOND_GAP_SEC = GAP_NORMAL_SEC

    /**
     * 单次时长 ≤ 此分钟数：跳过使用中轻问（短会话）。
     * 结束对照仍可按 [ComparePolicy] 出现（且练习总闸开着）。
     */
    const val SHORT_SESSION_SKIP_MAX_MIN = 5

    /** 会话剩余不足此时长：不抛（快到点了，器械信号优先） */
    const val MIN_REMAINING_SESSION_SEC = 90L

    /** 日限剩余 ≤ 此时长：不抛（与胶囊紧急态对齐） */
    const val SKIP_DAILY_REMAIN_URGENT_SEC = 5 * 60L

    fun gapSec(rhythm: AwarenessPracticeRhythm): Long = rhythm.gapSec

    /**
     * 本场是否允许出现觉察问题（与是否已到点无关）。
     * 练习关 / 随意浏览不抛；搜索 / 意图可抛。
     */
    fun isEligible(
        intentGate: Boolean,
        intentKind: IntentKind?,
        sessionLimitMinutes: Int = 0,
        purpose: String? = null,
        hasSessionLimit: Boolean = sessionLimitMinutes > 0,
        practiceEnabled: Boolean = true,
    ): Boolean {
        if (!practiceEnabled) return false
        if (!intentGate) return false
        val path = CompanionPath.resolve(intentKind, purpose, hasSessionLimit)
        if (path == CompanionPath.BROWSE) return false
        val limitMin = sessionLimitMinutes.coerceAtLeast(0)
        if (limitMin in 1..SHORT_SESSION_SKIP_MAX_MIN) return false
        return true
    }

    /**
     * 点开陪伴面板时是否附带轻问。
     * 搜索在安静窗内只给一眼时间；过了 [gapSec] 再出。
     */
    fun shouldOfferInspectAsk(
        intentGate: Boolean,
        intentKind: IntentKind?,
        purpose: String?,
        hasSessionLimit: Boolean,
        sessionSeconds: Long,
        completedChecks: Int = 0,
        sessionLimitMinutes: Int = 0,
        practiceEnabled: Boolean = true,
        gapSec: Long = GAP_NORMAL_SEC,
    ): Boolean {
        if (completedChecks >= MAX_CHECKS_PER_SESSION) return false
        if (!isEligible(
                intentGate = intentGate,
                intentKind = intentKind,
                sessionLimitMinutes = sessionLimitMinutes,
                purpose = purpose,
                hasSessionLimit = hasSessionLimit,
                practiceEnabled = practiceEnabled,
            )
        ) {
            return false
        }
        val path = CompanionPath.resolve(intentKind, purpose, hasSessionLimit)
        val quiet = gapSec.coerceAtLeast(1L)
        if (path == CompanionPath.SEARCH &&
            sessionSeconds.coerceAtLeast(0L) < quiet
        ) {
            return false
        }
        return true
    }

    /**
     * 第 [completedChecks] 次完成后，到下一次的间隔（秒）。
     * 已满 [MAX_CHECKS_PER_SESSION] 返回极大值，调用方应先用 [isEligible] / 次数判断。
     */
    fun nextGapSec(
        completedChecks: Int,
        sessionLimitSec: Long = 0L,
        gapSec: Long = GAP_NORMAL_SEC,
    ): Long {
        if (completedChecks >= MAX_CHECKS_PER_SESSION) return Long.MAX_VALUE / 4
        return gapSec.coerceAtLeast(1L)
    }

    /**
     * 此刻是否应触发觉察问题（调用方已排除暂停 / 已在展示 / 锚提示播放等 UI 互斥）。
     */
    fun shouldTriggerNow(
        intentGate: Boolean,
        intentKind: IntentKind?,
        sessionSeconds: Long,
        completedChecks: Int,
        lastCheckAtSec: Long,
        sessionLimitMinutes: Int = 0,
        sessionLimitSec: Long = sessionLimitMinutes.coerceAtLeast(0) * 60L,
        dailyRemainingSec: Long = Long.MAX_VALUE,
        timeLockEnabled: Boolean = false,
        uiBlocking: Boolean = false,
        urgentChrome: Boolean = false,
        purpose: String? = null,
        practiceEnabled: Boolean = true,
        gapSec: Long = GAP_NORMAL_SEC,
    ): Boolean {
        if (uiBlocking || urgentChrome) return false
        if (completedChecks >= MAX_CHECKS_PER_SESSION) return false
        if (!isEligible(
                intentGate = intentGate,
                intentKind = intentKind,
                sessionLimitMinutes = sessionLimitMinutes,
                purpose = purpose,
                hasSessionLimit = sessionLimitSec > 0L || sessionLimitMinutes > 0,
                practiceEnabled = practiceEnabled,
            )
        ) {
            return false
        }

        val sec = sessionSeconds.coerceAtLeast(0L)
        val gap = nextGapSec(completedChecks, sessionLimitSec, gapSec)
        if (sec < gap) return false
        if (sec - lastCheckAtSec.coerceAtLeast(0L) < gap) return false

        if (sessionLimitSec > 0L) {
            val remainSession = sessionLimitSec - sec
            if (remainSession in 1L until MIN_REMAINING_SESSION_SEC) return false
        }
        if (timeLockEnabled && dailyRemainingSec in 1L..SKIP_DAILY_REMAIN_URGENT_SEC) {
            return false
        }
        return true
    }

    /**
     * 进入后 30s 锚提示：仅意图回声。
     * 搜索 / 随意浏览不走此点（搜索是到点出条；随意浏览靠倒计时）。
     * 不依赖觉察练习总闸（进门看见，属陪伴条叙事）。
     */
    fun shouldTriggerEnterAnchorHint(
        intentGate: Boolean,
        intentKind: IntentKind?,
        sessionSeconds: Long,
        alreadyShown: Boolean,
        purpose: String? = null,
        hasSessionLimit: Boolean = false,
        uiBlocking: Boolean = false,
    ): Boolean {
        if (alreadyShown || uiBlocking || !intentGate) return false
        val path = CompanionPath.resolve(intentKind, purpose, hasSessionLimit)
        if (path != CompanionPath.INTENT) return false
        return sessionSeconds >= ANCHOR_HINT_SEC
    }

    /**
     * 随意浏览临期锚提示：已废弃（倒计时色变即可）。
     * 保留 API 恒 false，避免旧调用方崩溃。
     */
    @Deprecated("Browse near-end hint removed; countdown color is enough.")
    fun shouldTriggerBrowseNearEndHint(
        intentGate: Boolean,
        intentKind: IntentKind?,
        purpose: String?,
        hasSessionLimit: Boolean,
        sessionRemainSec: Long,
        alreadyShown: Boolean,
        uiBlocking: Boolean = false,
    ): Boolean = false

    /** 搜索是否仍处在「不出胶囊」窗 */
    fun shouldDeferSearchCapsule(
        intentGate: Boolean,
        intentKind: IntentKind?,
        purpose: String?,
        hasSessionLimit: Boolean,
        sessionSeconds: Long,
    ): Boolean {
        if (!intentGate) return false
        val path = CompanionPath.resolve(intentKind, purpose, hasSessionLimit)
        if (path != CompanionPath.SEARCH) return false
        return sessionSeconds < SEARCH_CAPSULE_APPEAR_SEC
    }

    /** 埋点 action：还在 / 还想 / 还在搜 / 够了 */
    const val ACTION_STILL = "still"

    /** 埋点 action：已经偏了 / 可以停了 / 找到了 */
    const val ACTION_DRIFT = "drift"

    /** 埋点 action：超时自消 */
    const val ACTION_DISMISS = "dismiss"

    /** SoftExit：结束本次（已不再走 SoftExit 叠层，保留常量兼容埋点） */
    const val SOFT_EXIT_END = "end"

    /** SoftExit：再待一会儿 */
    const val SOFT_EXIT_STAY = "stay"
}
