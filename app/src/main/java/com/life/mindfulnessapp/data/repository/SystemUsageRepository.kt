package com.life.mindfulnessapp.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.life.mindfulnessapp.data.db.dao.HourlyUsage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 封装系统 UsageStatsManager，提供真实的 App 使用时长数据。
 *
 * 与 UsageRecordRepository（自有数据库）不同：
 * - 本类读取 Android 系统的"使用情况访问权限"，反映用户在该 App 的实际使用时长，
 *   包括未经本应用拦截/监控的那部分时间。
 * - 热力图（按小时分布）通过 queryEvents 逐事件计算，精度更高。
 */
@Singleton
class SystemUsageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val usageStatsManager: UsageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    // ── 公开 API ──────────────────────────────────────────────────────────────

    /**
     * 获取指定 App 今日的系统实际使用时长（秒）。
     */
    suspend fun getTodayUsageSeconds(packageName: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (start, end) = UsageRecordRepository.getDayRange(now)
        queryUsageSeconds(packageName, start, end)
    }

    /**
     * 获取指定 App 本周的系统实际使用时长（秒）。
     *
     * 数据获取策略（规避 Android queryEvents 只保留近 7 天的系统限制）：
     * - 距今 7 天以内的天：使用 queryEvents 逐事件精确计算
     * - 7 天以前的天（如本周一~本周某天超出 7 天窗口）：使用 queryUsageStats(INTERVAL_DAILY) 聚合数据
     */
    suspend fun getWeekUsageSeconds(packageName: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (weekStart, _) = UsageRecordRepository.getWeekRange(now)
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val dayMs = 24 * 60 * 60 * 1000L
        val sevenDaysAgo = todayStart - 7 * dayMs

        var total = 0L

        // ── 本周中超出 7 天窗口的部分（用 queryUsageStats 聚合）────────────
        if (weekStart < sevenDaysAgo) {
            try {
                val stats = usageStatsManager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    weekStart,
                    sevenDaysAgo
                )
                stats?.filter { it.packageName == packageName }?.forEach { stat ->
                    total += stat.totalTimeInForeground / 1000L
                }
            } catch (_: Exception) { /* 无权限时忽略 */ }
        }

        // ── 近 7 天（从 max(weekStart, 7天前) 到今天末）用 queryEvents 精确计算 ──
        val recentStart = maxOf(weekStart, sevenDaysAgo)
        val (_, weekEnd) = UsageRecordRepository.getWeekRange(now)
        total += queryUsageSeconds(packageName, recentStart, minOf(weekEnd, now))

        total
    }

    /**
     * 获取指定 App 昨日的系统实际使用时长（秒）。
     */
    suspend fun getYesterdayUsageSeconds(packageName: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (start, end) = UsageRecordRepository.getYesterdayRange(now)
        queryUsageSeconds(packageName, start, end)
    }

    /**
     * 获取指定 App 在指定时间段内的按小时使用分布（用于热力图）。
     *
     * 通过逐事件扫描 MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND 事件，
     * 精确计算每个小时内的在前台时长（秒）。
     *
     * @param packageName App 包名
     * @param startMs     时间段起始时间戳（毫秒）
     * @param endMs       时间段结束时间戳（毫秒）
     * @return 小时分布列表，只含有数据的小时（hour 0-23, totalSeconds）
     */
    suspend fun getHourlyDistribution(
        packageName: String,
        startMs: Long,
        endMs: Long
    ): List<HourlyUsage> = withContext(Dispatchers.IO) {
        val hourlySeconds = LongArray(24) { 0L }
        try {
            val events = usageStatsManager.queryEvents(startMs, endMs)
            val event = UsageEvents.Event()
            var fgStartTime = -1L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue

                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        fgStartTime = event.timeStamp.coerceAtLeast(startMs)
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (fgStartTime > 0) {
                            val fgEnd = event.timeStamp.coerceAtMost(endMs)
                            accumulateHourly(hourlySeconds, fgStartTime, fgEnd)
                            fgStartTime = -1L
                        }
                    }
                }
            }
            // 如果 App 仍在前台（没有 BACKGROUND 事件），计算到 endMs
            if (fgStartTime > 0) {
                accumulateHourly(hourlySeconds, fgStartTime, endMs)
            }
        } catch (e: Exception) {
            // 没有权限时静默返回空列表
        }

        hourlySeconds.mapIndexed { hour, seconds ->
            HourlyUsage(hour = hour, totalSeconds = seconds)
        }.filter { it.totalSeconds > 0 }
    }

    /**
     * 获取本周每日的系统实际使用时长（秒），返回长度为 7 的列表，索引 0=周一。
     *
     * 与 getWeekUsageSeconds 同理，对超出 7 天窗口的天使用 queryUsageStats 兜底。
     */
    suspend fun getWeekDailyUsageSeconds(packageName: String): List<Long> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (weekStart, _) = UsageRecordRepository.getWeekRange(now)
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val dayMs = 24 * 60 * 60 * 1000L
        val sevenDaysAgo = todayStart - 7 * dayMs

        // 对超出 7 天的部分，预先用 queryUsageStats 批量拉取聚合数据
        val statsMap = mutableMapOf<String, Long>()
        if (weekStart < sevenDaysAgo) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val stats = usageStatsManager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    weekStart,
                    sevenDaysAgo
                )
                stats?.filter { it.packageName == packageName }?.forEach { stat ->
                    val key = sdf.format(java.util.Date(stat.firstTimeStamp))
                    statsMap[key] = (statsMap[key] ?: 0L) + stat.totalTimeInForeground / 1000L
                }
            } catch (_: Exception) { /* 无权限时忽略 */ }
        }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        (0..6).map { i ->
            val dStart = weekStart + i * dayMs
            val dEnd = dStart + dayMs
            if (dStart < sevenDaysAgo) {
                // 超出 7 天窗口，用聚合数据
                val key = sdf.format(java.util.Date(dStart))
                statsMap[key] ?: 0L
            } else {
                // 近 7 天，用 queryEvents 精确计算（取到 now 为止）
                queryUsageSeconds(packageName, dStart, minOf(dEnd, now))
            }
        }
    }

    /**
     * 获取近 30 天每日的系统实际使用时长 Map，key="yyyy-MM-dd"。
     *
     * 数据来源策略（规避 Android 系统限制）：
     * - 近 7 天：使用 queryEvents 逐事件扫描，精度最高（秒级）
     * - 7~30 天前：queryEvents 数据已被系统清除，改用 queryUsageStats(INTERVAL_DAILY)
     *   该接口保留约 4 周的每日聚合统计，精度以天为单位，足以支持热力图显示。
     */
    suspend fun getLast30DayUsageMap(packageName: String): Map<String, Long> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val result = mutableMapOf<String, Long>()

        // ── 7 天前以前：从 queryUsageStats(INTERVAL_DAILY) 批量拉取 ──────────
        // 一次性拉取 30 天前～7 天前的聚合数据，减少 IPC 调用次数
        val oldRangeStart = todayStart - 29 * dayMs
        val oldRangeEnd   = todayStart - 7 * dayMs   // 7 天前零点（不含）
        try {
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                oldRangeStart,
                oldRangeEnd
            )
            stats?.filter { it.packageName == packageName }?.forEach { stat ->
                val cal = Calendar.getInstance().apply { timeInMillis = stat.firstTimeStamp }
                val dateKey = sdf.format(cal.time)
                // 同一天可能返回多条（厂商差异），累加处理
                result[dateKey] = (result[dateKey] ?: 0L) + stat.totalTimeInForeground / 1000L
            }
        } catch (_: Exception) { /* 无权限时静默忽略 */ }

        // ── 近 7 天：使用 queryEvents 精确计算 ────────────────────────────────
        for (i in 0..6) {
            val dStart = todayStart - i * dayMs
            val dEnd = dStart + dayMs
            val seconds = queryUsageSeconds(packageName, dStart, dEnd)
            val cal = Calendar.getInstance().apply { timeInMillis = dStart }
            val dateKey = sdf.format(cal.time)
            // queryEvents 结果优先覆盖（精度更高）
            result[dateKey] = seconds
        }

        result
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    /**
     * 通过 queryEvents 精确计算指定 App 在给定时间段内的前台使用时长（秒）。
     *
     * 相比 queryUsageStats（以天为最小粒度），queryEvents 可按任意时间段精确计算，
     * 适合今日、本周等跨天的场景。
     */
    private fun queryUsageSeconds(packageName: String, startMs: Long, endMs: Long): Long {
        var totalMs = 0L
        try {
            val events = usageStatsManager.queryEvents(startMs, endMs)
            val event = UsageEvents.Event()
            var fgStartTime = -1L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue

                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        fgStartTime = event.timeStamp.coerceAtLeast(startMs)
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (fgStartTime > 0) {
                            val fgEnd = event.timeStamp.coerceAtMost(endMs)
                            totalMs += (fgEnd - fgStartTime).coerceAtLeast(0L)
                            fgStartTime = -1L
                        }
                    }
                }
            }
            // App 仍在前台（无 BACKGROUND 事件）：计算到查询终点
            if (fgStartTime > 0) {
                totalMs += (endMs - fgStartTime).coerceAtLeast(0L)
            }
        } catch (e: Exception) {
            // 权限未授予时静默返回 0
        }
        return totalMs / 1000L
    }

    /**
     * 将 [fgStart, fgEnd) 这段前台时间，累加到 hourlySeconds 对应的小时桶中。
     * 如果跨越了多个小时，则分段累加。
     */
    private fun accumulateHourly(hourlySeconds: LongArray, fgStart: Long, fgEnd: Long) {
        if (fgEnd <= fgStart) return
        var cur = fgStart
        while (cur < fgEnd) {
            val cal = Calendar.getInstance().apply { timeInMillis = cur }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            // 该小时结束时间
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val hourEnd = cal.timeInMillis.coerceAtMost(fgEnd)
            hourlySeconds[hour] += (hourEnd - cur) / 1000L
            cur = hourEnd
        }
    }
}
