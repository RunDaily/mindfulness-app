package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

/**
 * 某次「非标准闭环」结束后，等待用户下次进入该 App 时确认的中断快照。
 *
 * 标准闭环 = 用户主动通过胶囊结束（[UsageRecordEntity.EndReason.MANUAL]）。
 * 其余结束方式写入本快照；下次打开时在意图输入旁以「最近操作」条呈现，
 * 展示相对时刻并可一键继续，或重写意图开始新的一次。
 *
 * 超过 [RESUME_CONFIRM_MAX_AGE_MS] 后视为过期：意图已变，不再提供「继续上次」。
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

    /** 面向用户的标题（完整句） */
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

    /** 拦截页内嵌用的短因标签 */
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

    /** 面向用户的说明（写清原因） */
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
                "你打开了另一个受监控的应用，上次会话已结束。可以选择接着上次的目的继续，或重新开始。"
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
        /** 「继续上次」最长有效期：超时后清掉快照，走普通拦截 */
        const val RESUME_CONFIRM_MAX_AGE_MS = 10 * 60 * 1000L
    }
}
