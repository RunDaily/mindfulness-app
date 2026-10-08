package com.life.mindfulnessapp.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 监控存活心跳：用于「被清理后恢复」检测。
 *
 * - 服务运行中定期 [touchAlive]
 * - 再次拉起时若距上次心跳过久 → 记一次中断（首页横幅）
 * - 强停后等用户打开 App 时用 [peekOrDetectColdInterrupt] 补提示
 */
object MonitorHealthStore {

    const val ACTION_OPEN_KEEP_ALIVE_GUIDE =
        "com.life.mindfulnessapp.action.OPEN_KEEP_ALIVE_GUIDE"

    private const val PREFS = "mindfulness_prefs"
    private const val KEY_LAST_ALIVE_AT = "monitor_last_alive_at"
    private const val KEY_HAD_EVER_ALIVE = "monitor_had_ever_alive"
    private const val KEY_INTERRUPT_PENDING = "monitor_interrupt_pending"
    private const val KEY_INTERRUPT_GAP_MS = "monitor_interrupt_gap_ms"

    /** 超过此时长视为监控曾中断（略长于短暂进程抖动） */
    const val INTERRUPT_THRESHOLD_MS = 3 * 60 * 1000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun touchAlive(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit {
            putLong(KEY_LAST_ALIVE_AT, now)
            putBoolean(KEY_HAD_EVER_ALIVE, true)
        }
    }

    fun lastAliveAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_ALIVE_AT, 0L)

    fun hadEverAlive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAD_EVER_ALIVE, false)

    fun isInterruptPending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INTERRUPT_PENDING, false)

    fun pendingInterruptGapMs(context: Context): Long =
        prefs(context).getLong(KEY_INTERRUPT_GAP_MS, 0L)

    fun clearInterruptPending(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_INTERRUPT_PENDING, false)
            putLong(KEY_INTERRUPT_GAP_MS, 0L)
        }
    }

    /**
     * 监控服务即将/正在启动时调用：若距上次心跳过久，标记中断供首页横幅展示。
     * 首次安装（从未存活）不计中断。
     */
    fun onMonitorStarting(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST_ALIVE_AT, 0L)
        val had = p.getBoolean(KEY_HAD_EVER_ALIVE, false)
        if (!had || last <= 0L) {
            touchAlive(context)
            return
        }
        val gap = now - last
        if (gap < INTERRUPT_THRESHOLD_MS) {
            touchAlive(context)
            return
        }
        p.edit {
            putBoolean(KEY_INTERRUPT_PENDING, true)
            putLong(KEY_INTERRUPT_GAP_MS, gap)
            putLong(KEY_LAST_ALIVE_AT, now)
            putBoolean(KEY_HAD_EVER_ALIVE, true)
        }
    }

    /**
     * 打开 App 时：服务未在跑且心跳已过期 → 补记中断（强停后无恢复通知的场景）。
     */
    fun peekOrDetectColdInterrupt(context: Context, monitorRunning: Boolean): Boolean {
        if (isInterruptPending(context)) return true
        if (monitorRunning) return false
        val p = prefs(context)
        if (!p.getBoolean(KEY_HAD_EVER_ALIVE, false)) return false
        val last = p.getLong(KEY_LAST_ALIVE_AT, 0L)
        if (last <= 0L) return false
        val gap = System.currentTimeMillis() - last
        if (gap < INTERRUPT_THRESHOLD_MS) return false
        p.edit {
            putBoolean(KEY_INTERRUPT_PENDING, true)
            putLong(KEY_INTERRUPT_GAP_MS, gap)
        }
        return true
    }
}
