package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

/**
 * 某次「非标准闭环」结束后，等待用户下次进入该 App 时确认的中断快照。
 *
 * 标准闭环 = 用户主动通过胶囊结束（[UsageRecordEntity.EndReason.MANUAL]）。
 * 仅**有名意图 / 搜索直达**会写入并在总门以弱链「刚刚 · 意图」提供续接；
 * **随意浏览**等刷逛路径不进强续（避免无意识连刷）。
 *
 * 超过 [RESUME_CONFIRM_MAX_AGE_MS] 后视为过期：意图已变，不再提供续接。
 */
data class PendingInterrupt(
    val packageName: String,
    val recordId: Long,
    val appName: String,
    val endReason: String,
    val purpose: String?,
    val intentKind: IntentKind? = null,
    val sessionLimitMinutes: Int = 0,
    val sessionExtensionMinutes: Int = 0,
    val durationSeconds: Long,
    val endedAt: Long
) {
    /** 是否已超过可续用窗口 */
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - endedAt > RESUME_CONFIRM_MAX_AGE_MS

    /**
     * 门上是否提供「刚刚 · 意图」强续。
     * 续的是意图本身，不是中断原因；随意浏览 / 空意图 / 过期一律否。
     */
    fun isStrongResumeEligible(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isExpired(nowMs)) return false
        if (!UsageRecordEntity.EndReason.shouldOfferResumeConfirm(endReason)) return false
        return isNamedIntentOrSearch(
            purpose = purpose,
            intentKind = intentKind,
            sessionLimitMinutes = sessionLimitMinutes
        )
    }

    /** 门上弱链用的意图文案（截断）；无资格时为空 */
    fun gateResumePurposeLabel(): String {
        if (!isStrongResumeEligible()) return ""
        val raw = purpose?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        return if (raw.length > GATE_PURPOSE_MAX_CHARS) {
            raw.take(GATE_PURPOSE_MAX_CHARS - 1) + "…"
        } else {
            raw
        }
    }

    /** 面向用户的标题（完整句；时间线 / 横条等，不进总门） */
    val reasonTitle: String
        get() = when (endReason) {
            UsageRecordEntity.EndReason.AWAY_COUNTDOWN -> "上次离开后计时已暂停结束"
            UsageRecordEntity.EndReason.SCREEN_OFF_TIMEOUT -> "上次息屏后未及时回来"
            UsageRecordEntity.EndReason.BACKGROUND_TIMEOUT,
            UsageRecordEntity.EndReason.AUTO_TIMEOUT -> "上次离开后计时已自动暂停"
            UsageRecordEntity.EndReason.SWITCHED_AWAY -> "上次你去了其他应用"
            UsageRecordEntity.EndReason.APP_CLOSED -> "上次使用意外中断"
            else -> "上次使用未正常结束"
        }

    /** 短因标签（时间线等；总门不展示） */
    val reasonShortLabel: String
        get() = when (endReason) {
            UsageRecordEntity.EndReason.AWAY_COUNTDOWN -> "离开后结束"
            UsageRecordEntity.EndReason.SCREEN_OFF_TIMEOUT -> "息屏中断"
            UsageRecordEntity.EndReason.BACKGROUND_TIMEOUT,
            UsageRecordEntity.EndReason.AUTO_TIMEOUT -> "离开后中断"
            UsageRecordEntity.EndReason.SWITCHED_AWAY -> "切换应用中断"
            UsageRecordEntity.EndReason.APP_CLOSED -> "意外中断"
            else -> "未正常结束"
        }

    /** 面向用户的说明（写清原因；总门不展示） */
    val reasonDetail: String
        get() = when (endReason) {
            UsageRecordEntity.EndReason.AWAY_COUNTDOWN ->
                "离开较久后，会话已暂停结束。可以选择接着上次的意图继续，或重新开始。"
            UsageRecordEntity.EndReason.SCREEN_OFF_TIMEOUT ->
                "息屏超过宽限时间后，会话已自动结束。这段时间没有计入使用时长。"
            UsageRecordEntity.EndReason.BACKGROUND_TIMEOUT,
            UsageRecordEntity.EndReason.AUTO_TIMEOUT ->
                "切换到其他应用较久后，计时已自动暂停并结束。后台停留没有计入使用时长。"
            UsageRecordEntity.EndReason.SWITCHED_AWAY ->
                "你打开了另一个受监控的应用，且超过可回切时限后，上次会话已结束。可以选择接着上次的目的继续，或重新开始。"
            UsageRecordEntity.EndReason.APP_CLOSED ->
                "可能因系统回收、进程重启等原因，会话没能完整收尾。"
            else ->
                "这次使用没有通过胶囊主动结束。"
        }

    /** 距结束过去了多久的可读文案（最近操作条用） */
    fun timeAgoLabel(nowMs: Long = System.currentTimeMillis()): String {
        val diff = (nowMs - endedAt).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 60 * 24 -> "${minutes / 60}小时前"
            else -> "${minutes / (60 * 24)}天前"
        }
    }

    companion object {
        /**
         * 「继续上次」最长有效期：超时后清掉快照，走普通拦截。
         * 与产品手册一致：须先满足未主动收束规则，且在此窗口内进入才展示续接入口。
         */
        const val RESUME_CONFIRM_MAX_AGE_MS = 10 * 60 * 1000L

        private const val GATE_PURPOSE_MAX_CHARS = 16

        /**
         * 是否属于可强续的路径：写下意图 / 搜索直达。
         * 随意浏览（含 URGE / 刷类文案）一律否。
         */
        fun isNamedIntentOrSearch(
            purpose: String?,
            intentKind: IntentKind?,
            sessionLimitMinutes: Int = 0
        ): Boolean {
            val p = purpose?.trim().orEmpty()
            if (p.isEmpty()) return false
            if (BrowseCasualIntent.isBrowseLike(p)) return false
            return when (
                CompanionPath.resolve(
                    intentKind = intentKind,
                    purpose = p,
                    hasSessionLimit = sessionLimitMinutes > 0
                )
            ) {
                CompanionPath.SEARCH, CompanionPath.INTENT -> true
                CompanionPath.BROWSE -> false
            }
        }
    }
}
