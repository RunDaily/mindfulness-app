package com.life.mindfulnessapp.domain.model

import java.util.Calendar

/**
 * 时段锁判定与解锁相关时刻。
 *
 * 优先级约定（由调用方保证）：时段硬锁 > 日限额 > 意图门。
 * 拦截页不提供破界；生效期间关闭配置才走解锁门槛。
 */
object PeriodLockPolicy {

    /** 生效中关闭时，长按需持续的毫秒数 */
    const val BREAK_HOLD_MS = 2_500L

    /** 承诺文案最短字数（开启时段锁时） */
    const val COMMITMENT_MIN_CHARS = 2

    /** 承诺文案最长字数 */
    const val COMMITMENT_MAX_CHARS = 40

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

    private fun isInWindow(
        window: PeriodWindow,
        minuteOfDay: Int,
        todayBit: Int,
        yesterdayBit: Int
    ): Boolean {
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
