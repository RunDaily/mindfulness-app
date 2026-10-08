package com.life.mindfulnessapp.overlay

import com.life.mindfulnessapp.domain.model.SystemForegroundSession

/** 使用中桌面球：数字来自心锚进门账，还是系统前台用量。 */
enum class DesktopInAppSource {
    HeartAnchor,
    SystemUsage,
}

/** 将今日系统前台段收成 Pulse 脊线（无意图文案）。 */
fun systemForegroundSessionsToPulseSessions(
    sessions: List<SystemForegroundSession>,
): List<DesktopPulseSession> =
    sessions
        .map { s ->
            DesktopPulseSession(
                startMs = s.startMs,
                endMs = s.endMs,
                durationSeconds = s.durationSeconds.coerceAtLeast(0L),
                purpose = null,
                ongoing = s.ongoing,
                kind = DesktopPulseSessionKind.Enter,
            )
        }
        .sortedBy { it.startMs }

fun hourlyListToArray(hours: List<com.life.mindfulnessapp.data.db.dao.HourlyUsage>): LongArray {
    val out = LongArray(24)
    for (h in hours) {
        val hour = h.hour
        if (hour in 0..23) out[hour] = h.totalSeconds.coerceAtLeast(0L)
    }
    return out
}
