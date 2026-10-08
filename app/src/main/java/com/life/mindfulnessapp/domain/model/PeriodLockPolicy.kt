package com.life.mindfulnessapp.domain.model

import java.util.Calendar

/** 新时段与已有时段的冲突类型 */
enum class PeriodWindowConflict {
    Duplicate,
    Overlap
}

/**
 * 时段锁判定与解锁相关时刻。
 *
 * 优先级约定（由调用方保证）：时段硬锁 > 日限额 > 意图门。
 * 拦截页不提供破界；已武装时关闭 / 删除 / 改动走 [BreathCostPolicy] 呼吸代价（不要求此刻落在窗内）。
 * 硬门可留「紧急进入」计次出口（见 [EXEMPTIONS_PER_DAY]）：选时长进门，不拆整段锁。
 */
object PeriodLockPolicy {

    private const val MINUTES_PER_DAY = 1440

    /** 每个 App 每个自然日可紧急进入硬门的次数 */
    const val EXEMPTIONS_PER_DAY = 1

    /** @deprecated MVP 已统一为 [BreathCostPolicy.DURATION_MS]；保留以免旧引用编译失败 */
    const val BREAK_HOLD_MS = BreathCostPolicy.DURATION_MS

    /** 硬门上次要入口文案；有剩余次数才展示 */
    fun exemptionEnterLabel(@Suppress("UNUSED_PARAMETER") remaining: Int = EXEMPTIONS_PER_DAY): String =
        "紧急进入"

    /** 每段寄语最长字数（可选；出现在该段拦截页与关闭门槛） */
    const val MESSAGE_MAX_CHARS = 120

    /** @deprecated 使用 [MESSAGE_MAX_CHARS]；保留以免旧引用编译失败 */
    const val COMMITMENT_MAX_CHARS = MESSAGE_MAX_CHARS

    /** @deprecated 寄语改为可选，不再强制最短字数 */
    const val COMMITMENT_MIN_CHARS = 0

    /**
     * 当前是否处于任一**已开启**锁定窗口内。
     * @return 命中的窗口；未命中则 null
     */
    fun activeWindow(
        windows: List<PeriodWindow>,
        nowMillis: Long = System.currentTimeMillis()
    ): PeriodWindow? {
        if (windows.isEmpty()) return null
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todayBit = PeriodDays.fromCalendar(cal.get(Calendar.DAY_OF_WEEK))
        // 跨午夜窗口：若仍在「昨日开始、今日清晨」段，应用昨日的 daysMask
        val yesterdayBit = run {
            val y = cal.clone() as Calendar
            y.add(Calendar.DAY_OF_YEAR, -1)
            PeriodDays.fromCalendar(y.get(Calendar.DAY_OF_WEEK))
        }

        for (w in windows) {
            if (!w.enabled) continue
            if (isInWindow(w, minuteOfDay, todayBit, yesterdayBit)) return w
        }
        return null
    }

    /**
     * 某段窗口（不论子开关）此刻是否落在其时间范围内。
     * 用于：生效期间关闭该段时触发解锁门槛。
     */
    fun wouldBeActiveNow(
        window: PeriodWindow,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todayBit = PeriodDays.fromCalendar(cal.get(Calendar.DAY_OF_WEEK))
        val yesterdayBit = run {
            val y = cal.clone() as Calendar
            y.add(Calendar.DAY_OF_YEAR, -1)
            PeriodDays.fromCalendar(y.get(Calendar.DAY_OF_WEEK))
        }
        return isInWindow(window, minuteOfDay, todayBit, yesterdayBit)
    }

    /** 当前是否有任一**已开启**窗口处于锁定中 */
    fun hasActiveEnabledWindow(
        windows: List<PeriodWindow>,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = activeWindow(windows, nowMillis) != null

    fun isLockedNow(
        enabled: Boolean,
        windows: List<PeriodWindow>,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = enabled && activeWindow(windows, nowMillis) != null

    /**
     * 当前命中窗口的本轮结束时刻（用于「约 X 后解锁」文案）。
     * 若此刻未命中窗口，返回 [nowMillis]。
     */
    fun exemptionUntilMillis(
        window: PeriodWindow?,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (window == null) return nowMillis
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val endCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        endCal.set(Calendar.SECOND, 0)
        endCal.set(Calendar.MILLISECOND, 0)

        if (window.isAllDay) {
            // 全天：本轮至次日 00:00 结束（若次日仍全天命中，由下一次判定续上）
            endCal.add(Calendar.DAY_OF_YEAR, 1)
            endCal.set(Calendar.HOUR_OF_DAY, 0)
            endCal.set(Calendar.MINUTE, 0)
            return endCal.timeInMillis.coerceAtLeast(nowMillis)
        }

        if (!window.crossesMidnight) {
            // 同日窗口：结束于今日 endMinute
            endCal.set(Calendar.HOUR_OF_DAY, window.endMinute / 60)
            endCal.set(Calendar.MINUTE, window.endMinute % 60)
            return endCal.timeInMillis.coerceAtLeast(nowMillis)
        }

        // 跨午夜：若当前在 start…24:00，结束于明日 end；若在 0…end，结束于今日 end
        if (minuteOfDay >= window.startMinute) {
            endCal.add(Calendar.DAY_OF_YEAR, 1)
            endCal.set(Calendar.HOUR_OF_DAY, window.endMinute / 60)
            endCal.set(Calendar.MINUTE, window.endMinute % 60)
        } else {
            endCal.set(Calendar.HOUR_OF_DAY, window.endMinute / 60)
            endCal.set(Calendar.MINUTE, window.endMinute % 60)
        }
        return endCal.timeInMillis.coerceAtLeast(nowMillis)
    }

    /** 距本轮窗口结束的剩余毫秒；未锁定时为 0 */
    fun remainingMillis(
        window: PeriodWindow?,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (window == null) return 0L
        return (exemptionUntilMillis(window, nowMillis) - nowMillis).coerceAtLeast(0L)
    }

    /**
     * 硬挡页标题。
     * 日程锁有名时用「{名}\n先不进去」；否则全天说「今天」，其余说「这段时间」。
     */
    fun doorTitle(window: PeriodWindow, scheduleTitle: String? = null): String {
        val name = scheduleTitle?.trim().orEmpty()
        if (name.isNotEmpty()) return "$name\n先不进去"
        return if (window.isAllDay) "今天\n先不进去" else "这段时间\n先不进去"
    }

    /** 硬挡页英雄：时间窗本身，用短横线。 */
    fun doorHero(window: PeriodWindow): String =
        window.label().replace(" – ", "–").replace(" - ", "–").trim()

    /**
     * 硬挡页何时再开。
     * 全天写「明天 0 点打开」；不足一小时写「N 分后打开」；否则写「每天 · 07:00 打开」。
     */
    fun doorWhenLine(
        window: PeriodWindow,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (window.isAllDay) return "明天 0 点打开"
        val remainMs = exemptionUntilMillis(window, nowMillis) - nowMillis
        if (remainMs <= 0L) return "即将打开"
        val totalMin = ((remainMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        if (totalMin < 60) return "$totalMin 分后打开"
        return "${window.daysLabel()} · ${PeriodWindow.formatHm(window.endMinute)} 打开"
    }

    /** 如「约 3 小时后解锁」「约 25 分钟后解锁」 */
    fun remainingUnlockLabel(
        window: PeriodWindow?,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val ms = remainingMillis(window, nowMillis)
        if (ms <= 0L) return "即将解锁"
        val totalMin = ((ms + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        return when {
            totalMin < 60 -> "约 ${totalMin} 分钟后解锁"
            else -> {
                val h = totalMin / 60
                val m = totalMin % 60
                if (m == 0) "约 ${h} 小时后解锁" else "约 ${h} 小时 ${m} 分后解锁"
            }
        }
    }

    /**
     * [candidate] 与已有时段是否冲突（重复或重叠）。
     * 编辑时传入 [excludeId] 以忽略自身。
     */
    fun conflictWith(
        candidate: PeriodWindow,
        existing: List<PeriodWindow>,
        excludeId: String? = null
    ): PeriodWindowConflict? {
        val (cs, ce) = PeriodWindow.normalizeRange(candidate.startMinute, candidate.endMinute)
        val cDays = candidate.daysMask and PeriodDays.EVERY_DAY
        for (w in existing) {
            if (w.id == candidate.id || w.id == excludeId) continue
            val (ws, we) = PeriodWindow.normalizeRange(w.startMinute, w.endMinute)
            val wDays = w.daysMask and PeriodDays.EVERY_DAY
            if (cs == ws && ce == we && cDays == wDays) return PeriodWindowConflict.Duplicate
            if (rangesOverlap(candidate, w)) return PeriodWindowConflict.Overlap
        }
        return null
    }

    /** 列表内部是否已有互相重叠（不含完全相同）的时段 */
    fun hasInternalOverlap(windows: List<PeriodWindow>): Boolean {
        for (i in windows.indices) {
            for (j in (i + 1) until windows.size) {
                val a = windows[i]
                val b = windows[j]
                if (sameRange(a, b)) continue
                if (rangesOverlap(a, b)) return true
            }
        }
        return false
    }

    fun sameRange(a: PeriodWindow, b: PeriodWindow): Boolean {
        val (as_, ae) = PeriodWindow.normalizeRange(a.startMinute, a.endMinute)
        val (bs, be) = PeriodWindow.normalizeRange(b.startMinute, b.endMinute)
        return as_ == bs &&
            ae == be &&
            (a.daysMask and PeriodDays.EVERY_DAY) == (b.daysMask and PeriodDays.EVERY_DAY)
    }

    /**
     * 两段在任一星期几上的分钟区间是否相交（半开区间）。
     * 相邻不冲突：如 09:00–12:00 与 12:00–18:00。
     */
    fun rangesOverlap(a: PeriodWindow, b: PeriodWindow): Boolean {
        val sa = coveredSpans(a)
        val sb = coveredSpans(b)
        for (x in sa) {
            for (y in sb) {
                if (x.day != y.day) continue
                if (x.startMinute < y.endMinute && y.startMinute < x.endMinute) return true
            }
        }
        return false
    }

    private data class DaySpan(val day: Int, val startMinute: Int, val endMinute: Int)

    /**
     * 把窗口展开成一周内各天的半开分钟区间。周一 = 0。
     * 跨午夜：今晚落在 [daysMask] 当天，清晨落在次日。
     */
    private fun coveredSpans(window: PeriodWindow): List<DaySpan> {
        val mask = window.daysMask and PeriodDays.EVERY_DAY
        val days = (0..6).filter { mask and (1 shl it) != 0 }
        if (days.isEmpty()) return emptyList()
        val (start, end) = PeriodWindow.normalizeRange(window.startMinute, window.endMinute)
        val normalized = window.copy(startMinute = start, endMinute = end)
        if (normalized.isAllDay) {
            return days.map { DaySpan(it, 0, MINUTES_PER_DAY) }
        }
        if (!normalized.crossesMidnight) {
            if (start >= end) return emptyList()
            return days.map { DaySpan(it, start, end) }
        }
        return days.flatMap { d ->
            buildList {
                if (start < MINUTES_PER_DAY) add(DaySpan(d, start, MINUTES_PER_DAY))
                if (end > 0) add(DaySpan((d + 1) % 7, 0, end))
            }
        }
    }

    private fun isInWindow(
        window: PeriodWindow,
        minuteOfDay: Int,
        todayBit: Int,
        yesterdayBit: Int
    ): Boolean {
        if (window.isAllDay) {
            return window.daysMask and todayBit != 0
        }
        if (!window.crossesMidnight) {
            if (window.daysMask and todayBit == 0) return false
            return minuteOfDay >= window.startMinute && minuteOfDay < window.endMinute
        }
        // 跨午夜：今晚段用今日 mask；清晨段用昨日 mask
        return when {
            minuteOfDay >= window.startMinute ->
                window.daysMask and todayBit != 0
            minuteOfDay < window.endMinute ->
                window.daysMask and yesterdayBit != 0
            else -> false
        }
    }
}
