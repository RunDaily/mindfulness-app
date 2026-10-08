package com.life.mindfulnessapp.overlay

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.UsageRecordCounts

/** Pulse / InApp 会话脊：心锚记录，不是系统前台段 */
enum class DesktopPulseSessionKind { Enter, GateQuit }

data class DesktopPulseSession(
    val startMs: Long,
    val endMs: Long,
    val durationSeconds: Long,
    val purpose: String? = null,
    val ongoing: Boolean = false,
    val kind: DesktopPulseSessionKind = DesktopPulseSessionKind.Enter,
)

/** 圆球在面板窗坐标系中的位置，用于把 Hub 锚在球旁。 */
data class DesktopAnchorOrbAnchor(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 1,
    val height: Int = 1,
    val parentWidth: Int = 1,
    val parentHeight: Int = 1,
    val topInset: Int = 0,
    val bottomInset: Int = 0,
)

data class DesktopAnchorTopApp(
    val packageName: String,
    val appName: String,
    val totalSeconds: Long,
    val openCount: Int = 0,
    val inSlot: Boolean = false,
)

/** 使用中：桌面心锚切换为转圈图标 + 当前 App 用量（心锚账或系统账） */
data class DesktopAnchorInAppGlance(
    val packageName: String,
    val appName: String,
    val todayTotalSeconds: Long,
    val sessionSeconds: Long,
    val sessions: List<DesktopPulseSession> = emptyList(),
    val openCount: Int = 0,
    val dismissCount: Int = 0,
    val source: DesktopInAppSource = DesktopInAppSource.HeartAnchor,
    /** 今日 24 小时桶（秒）；系统账用热力带，心锚账可空 */
    val hourlySeconds: LongArray = LongArray(24),
    /** 未进规则且适合添加时为 true（微信等为 false） */
    val canAddToPlan: Boolean = false,
    /** 当前段开始时刻；系统账用来估「本次」秒数 */
    val sessionStartedAtMs: Long = 0L,
)

/** Hub → App Pulse 二级详情（桌面点图标 / 使用中点详情共用） */
data class DesktopAnchorPulseGlance(
    val packageName: String,
    val appName: String,
    val todayTotalSeconds: Long,
    val openCount: Int,
    val sessionSeconds: Long,
    val live: Boolean,
    val sessions: List<DesktopPulseSession>,
    val dismissCount: Int = 0,
    val inSlot: Boolean = false,
    val source: DesktopInAppSource = DesktopInAppSource.HeartAnchor,
    val hourlySeconds: LongArray = LongArray(24),
    val canAddToPlan: Boolean = false,
)

/** 意图门点开陪伴条：与桌面同一套 Hub 骨架，会话头改成意图优先 */
data class IntentSessionHubGlance(
    val packageName: String,
    val appName: String,
    val purpose: String,
    val sessionSeconds: Long,
    val todayTotalSeconds: Long,
    val todayEnterCount: Int,
    val remainSeconds: Long = 0L,
    val hasSessionLimit: Boolean = false,
    val sessionLimitMinutes: Int = 0,
    val overLimit: Boolean = false,
)

/** 将今日心锚记录收成 Pulse 会话脊（进入段）；守住次数另计。 */
fun usageRecordsToPulseSessions(
    records: List<UsageRecordEntity>,
    liveSessionSeconds: Long? = null,
): List<DesktopPulseSession> =
    records.mapNotNull { r ->
        when {
            r.isSeed || r.isPositiveExit || r.isGateQuit -> null
            else -> {
                val ongoing = r.endTime <= 0L
                val duration = when {
                    ongoing && liveSessionSeconds != null -> liveSessionSeconds
                    else -> r.durationSeconds.coerceAtLeast(0L)
                }
                DesktopPulseSession(
                    startMs = r.startTime,
                    endMs = if (ongoing) System.currentTimeMillis() else r.endTime,
                    durationSeconds = duration,
                    purpose = r.purpose?.trim()?.takeIf { it.isNotEmpty() },
                    ongoing = ongoing,
                    kind = DesktopPulseSessionKind.Enter,
                )
            }
        }
    }.sortedBy { it.startMs }

fun usageRecordsTodaySeconds(records: List<UsageRecordEntity>, liveSessionSeconds: Long? = null): Long {
    var total = 0L
    var hasOpen = false
    for (r in records) {
        if (!UsageRecordCounts.isEnter(r)) continue
        if (r.endTime <= 0L) {
            hasOpen = true
            total += (liveSessionSeconds ?: r.durationSeconds).coerceAtLeast(0L)
        } else {
            total += r.durationSeconds.coerceAtLeast(0L)
        }
    }
    if (!hasOpen && liveSessionSeconds != null && liveSessionSeconds > 0L) {
        total += liveSessionSeconds
    }
    return total
}
