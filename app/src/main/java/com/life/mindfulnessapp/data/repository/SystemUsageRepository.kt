package com.life.mindfulnessapp.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.life.mindfulnessapp.data.db.dao.HourlyUsage
import com.life.mindfulnessapp.domain.model.ExploreAppUsageDetail
import com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage
import com.life.mindfulnessapp.domain.model.PreJoinDaySnap
import com.life.mindfulnessapp.domain.model.PreJoinUsageSnapshot
import com.life.mindfulnessapp.domain.model.SystemDayPeriodStats
import com.life.mindfulnessapp.domain.model.SystemDayAligned
import com.life.mindfulnessapp.domain.model.SystemForegroundSession
import com.life.mindfulnessapp.domain.model.SystemUsageDayDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
     * 获取今日全局屏幕使用总时长（秒）。
     * 统计所有前台应用的累计使用时间，排除系统级 launcher/桌面包，
     * 用于在拦截页展示「今日手机使用时长」。
     */
    suspend fun getTodayTotalScreenSeconds(): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (start, end) = UsageRecordRepository.getDayRange(now)
        scanForegroundSegments(start, minOf(end, now))
            .sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) } / 1000L
    }

    /** 桌面 Hub：一次扫描产出今日合计、24 小时桶、按包聚合。 */
    data class TodaySystemGlanceApp(
        val packageName: String,
        val totalSeconds: Long,
        val openCount: Int = 0
    )

    data class TodaySystemGlance(
        val totalSeconds: Long,
        val hourlySeconds: LongArray,
        val topApps: List<TodaySystemGlanceApp>
    ) {
        companion object {
            val Empty = TodaySystemGlance(
                totalSeconds = 0L,
                hourlySeconds = LongArray(24),
                topApps = emptyList()
            )
        }
    }

    /**
     * 今日系统用量一瞥（桌面 Hub）。
     *
     * 只计入 [allowedPackages]（Launcher 可见、排除桌面与本应用），
     * 与探索排行同一前台合并口径。一次扫描同时得到合计、小时热力、按包排行。
     */
    suspend fun getTodaySystemGlance(
        allowedPackages: Set<String>
    ): TodaySystemGlance = withContext(Dispatchers.IO) {
        if (allowedPackages.isEmpty()) return@withContext TodaySystemGlance.Empty
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val hourly = LongArray(24)
        val durationMsByPkg = mutableMapOf<String, Long>()
        val opensByPkg = mutableMapOf<String, Int>()
        for (seg in mergedSegments(todayStart, now)) {
            if (seg.packageName !in allowedPackages) continue
            val durMs = (seg.endMs - seg.startMs).coerceAtLeast(0L)
            if (durMs <= 0L) continue
            durationMsByPkg[seg.packageName] =
                (durationMsByPkg[seg.packageName] ?: 0L) + durMs
            // 与 countSessionOpensByPackage 同口径：每段前台计一次打开
            opensByPkg[seg.packageName] = (opensByPkg[seg.packageName] ?: 0) + 1
            accumulateHourly(hourly, seg.startMs, seg.endMs)
        }
        val tops = durationMsByPkg.entries
            .map { (pkg, ms) ->
                val secs = ms / 1000L
                var opens = opensByPkg[pkg] ?: 0
                if (opens < 0) opens = 0
                if (secs >= 5 * 60L && opens == 0) opens = 1
                TodaySystemGlanceApp(pkg, secs, opens)
            }
            .filter { it.totalSeconds > 0L }
            .sortedByDescending { it.totalSeconds }
        TodaySystemGlance(
            totalSeconds = durationMsByPkg.values.sum() / 1000L,
            hourlySeconds = hourly,
            topApps = tops
        )
    }

    /** 今日系统用量快照：与探索 / 7 日排行同口径（前台会话合并 + 打开次数）。 */
    data class TodaySystemUsageSnapshot(
        val totalSeconds: Long,
        val openCount: Int
    )

    /**
     * 指定 App 今日系统用量。
     *
     * - **时长**：前台段合并（与探索详情时长一致）
     * - **打开次数**：与 [getLast7CompleteDaysUsageByPackage] 同源（RESUMED / FOREGROUND，800ms 去重）
     * - [sessionOriginStartMs]：当前会话进入时刻；系统事件有延迟时补计本次打开
     */
    suspend fun getTodaySystemUsageSnapshot(
        packageName: String,
        sessionOriginStartMs: Long = 0L
    ): TodaySystemUsageSnapshot =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val (todayStart, _) = UsageRecordRepository.getDayRange(now)
            val totalSeconds = queryUsageSeconds(packageName, todayStart, now)
            var openCount = todayOpenCountFromSystem(
                packageName = packageName,
                todayStart = todayStart,
                now = now,
                sessionOriginStartMs = sessionOriginStartMs
            )
            if (totalSeconds >= 5 * 60L && openCount == 0) openCount = 1
            TodaySystemUsageSnapshot(
                totalSeconds = totalSeconds.coerceAtLeast(0L),
                openCount = openCount.coerceAtLeast(0)
            )
        }

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
     * 今日之前连续 [days] 个完整自然日的系统使用总秒数（不含今天）。
     * 用于系锚时冻结「前一周」对照基线。
     */
    suspend fun getPrecedingCompleteDaysUsageSeconds(
        packageName: String,
        days: Int = 7
    ): Long {
        val (todayStart, _) = UsageRecordRepository.getDayRange(System.currentTimeMillis())
        return getPrecedingCompleteDaysUsageSecondsBefore(packageName, todayStart, days)
    }

    /**
     * [beforeDayStartMs] 之前连续 [days] 个完整自然日的系统使用总秒数（不含该日）。
     */
    suspend fun getPrecedingCompleteDaysUsageSecondsBefore(
        packageName: String,
        beforeDayStartMs: Long,
        days: Int = 7
    ): Long = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext 0L
        val map = getLast14DayUsageMap(packageName)
        val dayMs = 24 * 60 * 60 * 1000L
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        var total = 0L
        for (i in 1..days) {
            val key = sdf.format(java.util.Date(beforeDayStartMs - i * dayMs))
            total += map[key] ?: 0L
        }
        total
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
        val queryEnd = minOf(endMs, System.currentTimeMillis())
        scanForegroundSegments(startMs, queryEnd)
            .filter { it.packageName == packageName }
            .forEach { seg ->
                accumulateHourly(hourlySeconds, seg.startMs, seg.endMs)
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

    /**
     * 获取指定月份（yyyy-MM）每日系统实际使用时长 Map，key="yyyy-MM-dd"。
     *
     * 数据来源策略：
     * - 近 7 天内的天：queryEvents 精确计算
     * - 7 天前的天：queryUsageStats(INTERVAL_DAILY) 聚合兜底
     *
     * @param packageName   App 包名
     * @param monthKey      格式 "yyyy-MM"，例如 "2025-06"
     * @param extraPastDays 在月份起点之前额外向前拉取的天数（默认 0）。
     *                      用于日历展示监控日期前 N 天系统时长的场景（跨月也支持）。
     */
    suspend fun getMonthUsageMap(
        packageName: String,
        monthKey: String,
        extraPastDays: Int = 0
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val monthSdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
        val result = mutableMapOf<String, Long>()

        // 计算该月的起止时间
        val monthCal = java.util.Calendar.getInstance().apply {
            time = monthSdf.parse(monthKey)!!
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val monthStart = monthCal.timeInMillis
        // 实际查询起点：向前多扩展 extraPastDays 天
        val queryStart = monthStart - extraPastDays * dayMs
        monthCal.add(java.util.Calendar.MONTH, 1)
        val monthEnd = minOf(monthCal.timeInMillis, now)  // 不超过当前时间

        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val sevenDaysAgo = todayStart - 7 * dayMs

        // ── 7 天前的部分：用 queryUsageStats(INTERVAL_DAILY) 批量拉取 ─────────
        val oldEnd = minOf(sevenDaysAgo, monthEnd)
        if (queryStart < oldEnd) {
            try {
                val stats = usageStatsManager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    queryStart,
                    oldEnd
                )
                stats?.filter { it.packageName == packageName }?.forEach { stat ->
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = stat.firstTimeStamp }
                    val dateKey = sdf.format(cal.time)
                    // 不再严格限制必须是本月，extraPastDays 扩展的跨月天数也正常收录
                    result[dateKey] = (result[dateKey] ?: 0L) + stat.totalTimeInForeground / 1000L
                }
            } catch (_: Exception) {}
        }

        // ── 近 7 天（与查询范围有交集的部分）：queryEvents 精确计算 ──────────
        val recentStart = maxOf(queryStart, sevenDaysAgo)
        if (recentStart < monthEnd) {
            var cur = recentStart
            while (cur < monthEnd) {
                val dEnd = minOf(cur + dayMs, monthEnd)
                val seconds = queryUsageSeconds(packageName, cur, dEnd)
                val dateKey = sdf.format(java.util.Date(cur))
                result[dateKey] = seconds
                cur += dayMs
            }
        }

        result
    }

    /**
     * 获取近 14 天每日的系统实际使用时长 Map，key="yyyy-MM-dd"。
     * 数据来源策略与 getLast30DayUsageMap 相同：
     * - 近 7 天：queryEvents 精确计算
     * - 7~14 天前：queryUsageStats(INTERVAL_DAILY) 聚合兜底
     */
    suspend fun getLast14DayUsageMap(packageName: String): Map<String, Long> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val result = mutableMapOf<String, Long>()

        // ── 7~14 天前：从 queryUsageStats(INTERVAL_DAILY) 批量拉取 ────────────
        val oldRangeStart = todayStart - 13 * dayMs
        val oldRangeEnd   = todayStart - 7 * dayMs
        try {
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                oldRangeStart,
                oldRangeEnd
            )
            stats?.filter { it.packageName == packageName }?.forEach { stat ->
                val cal = Calendar.getInstance().apply { timeInMillis = stat.firstTimeStamp }
                val dateKey = sdf.format(cal.time)
                result[dateKey] = (result[dateKey] ?: 0L) + stat.totalTimeInForeground / 1000L
            }
        } catch (_: Exception) { /* 无权限时静默忽略 */ }

        // ── 近 7 天（含今天）：使用 queryEvents 精确计算 ──────────────────────
        for (i in 0..6) {
            val dStart = todayStart - i * dayMs
            val dEnd = dStart + dayMs
            val seconds = queryUsageSeconds(packageName, dStart, minOf(dEnd, now))
            val cal = Calendar.getInstance().apply { timeInMillis = dStart }
            val dateKey = sdf.format(cal.time)
            result[dateKey] = seconds
        }

        result
    }

    /**
     * 过去 7 个完整自然日（不含今天）各 App 的系统用量。
     *
     * - **时长**：`queryUsageStats(INTERVAL_DAILY)` 聚合，与系统「数字健康」口径一致
     * - **打开次数**：`queryEvents` 统计 ACTIVITY_RESUMED / MOVE_TO_FOREGROUND（去重），
     *   兼容部分 OEM 只上报其中一种事件的情况
     */
    suspend fun getLast7CompleteDaysUsageByPackage(): Map<String, AppWeeklySystemUsage> =
        withContext(Dispatchers.IO) {
            val (todayStart, _) = UsageRecordRepository.getDayRange(System.currentTimeMillis())
            val dayMs = 24 * 60 * 60 * 1000L
            val startMs = todayStart - AppWeeklySystemUsage.DAYS * dayMs
            val endMs = todayStart

            val durationSecondsByPkg = mutableMapOf<String, Long>()
            try {
                val stats = usageStatsManager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    startMs,
                    endMs
                )
                stats?.forEach { stat ->
                    val sec = stat.totalTimeInForeground / 1000L
                    if (sec <= 0L) return@forEach
                    durationSecondsByPkg[stat.packageName] =
                        (durationSecondsByPkg[stat.packageName] ?: 0L) + sec
                }
            } catch (_: Exception) { /* 无权限 */ }

            val launchesByPkg = mutableMapOf<String, Int>()
            val lastLaunchMsByPkg = mutableMapOf<String, Long>()
            try {
                val events = usageStatsManager.queryEvents(startMs, endMs)
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val isLaunch = when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> true
                        else -> false
                    }
                    if (!isLaunch) continue
                    val pkg = event.packageName
                    val last = lastLaunchMsByPkg[pkg] ?: 0L
                    if (event.timeStamp - last < launchEventDedupeMs) continue
                    launchesByPkg[pkg] = (launchesByPkg[pkg] ?: 0) + 1
                    lastLaunchMsByPkg[pkg] = event.timeStamp
                }
            } catch (_: Exception) { /* 无权限 */ }

            val packages = durationSecondsByPkg.keys + launchesByPkg.keys
            packages.associateWith { pkg ->
                val totalSeconds = durationSecondsByPkg[pkg] ?: 0L
                var totalLaunches = launchesByPkg[pkg] ?: 0
                // 有时长但事件流未记到打开：至少按 1 次计，避免「0 次却数小时」的违和展示
                if (totalSeconds >= 5 * 60L && totalLaunches == 0) {
                    totalLaunches = 1
                }
                AppWeeklySystemUsage(
                    totalSeconds = totalSeconds,
                    totalLaunches = totalLaunches
                )
            }
        }

    /** 同一次打开可能连续触发 RESUMED + MOVE_TO_FOREGROUND，合并为一次 */
    private val launchEventDedupeMs = 800L

    /** 系统事件写入滞后：会话已开始但 queryEvents 尚未出现对应 FOREGROUND */
    private val launchReconcileWindowMs = 15_000L

    /**
     * 今日打开次数（系统事件流，与 7 日批量挑选统计同源）。
     * 若 [sessionOriginStartMs] 落在今日且附近尚无匹配事件，补 +1 避免刚进入时显示滞后。
     */
    private fun todayOpenCountFromSystem(
        packageName: String,
        todayStart: Long,
        now: Long,
        sessionOriginStartMs: Long
    ): Int {
        val launchTimes = scanSystemLaunches(todayStart, now, packageName).map { it.second }
        var count = launchTimes.size
        if (sessionOriginStartMs > todayStart) {
            val matched = launchTimes.any {
                kotlin.math.abs(it - sessionOriginStartMs) <= launchReconcileWindowMs
            }
            if (!matched) count += 1
        }
        return count
    }

    /** 过短前台闪烁不计入（毫秒） */
    private val minSessionMs = 2_000L

    /**
     * 同包相邻前台段间隔 ≤ 此值则合并为一次打开。
     * 覆盖闪屏 / 桌面闪回 / 同包 Activity 重进导致的「一次打开记两次」。
     */
    private val sessionMergeGapMs = 5_000L

    /**
     * 探索排行：最近 [days] 个自然日的各 App 系统用量。
     *
     * @param includeToday false 时统计「今天之前」连续 [days] 个完整自然日（排行对比更公平）。
     */
    suspend fun getRecentDaysUsageByPackage(
        days: Int = AppWeeklySystemUsage.DAYS,
        includeToday: Boolean = true
    ): Map<String, AppWeeklySystemUsage> = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext emptyMap()
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val dayMs = 24 * 60 * 60 * 1000L
        val startMs = if (includeToday) {
            todayStart - (days - 1) * dayMs
        } else {
            todayStart - days * dayMs
        }
        val queryEnd = if (includeToday) now else todayStart

        val durationMsByPkg = mutableMapOf<String, Long>()
        for (seg in mergedSegments(startMs, queryEnd)) {
            durationMsByPkg[seg.packageName] =
                (durationMsByPkg[seg.packageName] ?: 0L) + (seg.endMs - seg.startMs)
        }
        val launchesByPkg = countSessionOpensByPackage(startMs, queryEnd)

        val packages = durationMsByPkg.keys + launchesByPkg.keys
        packages.associateWith { pkg ->
            val totalSeconds = (durationMsByPkg[pkg] ?: 0L) / 1000L
            var totalLaunches = (launchesByPkg[pkg] ?: 0).coerceAtLeast(0)
            if (totalSeconds >= 5 * 60L && totalLaunches == 0) totalLaunches = 1
            AppWeeklySystemUsage(totalSeconds = totalSeconds, totalLaunches = totalLaunches)
        }
    }

    /**
     * 某自然日全局前台时间线（单前台扫描，未按包合并间隔）。
     * 供「使用日志」推导打开 / 离开 / 返回 / 回桌面。
     */
    suspend fun getDayForegroundTimeline(
        dayStartMs: Long,
        dayEndMs: Long
    ): List<DayForegroundSegment> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val queryEnd = minOf(dayEndMs, now)
        if (queryEnd <= dayStartMs) return@withContext emptyList()
        scanForegroundSegments(dayStartMs, queryEnd)
            .filter { it.endMs - it.startMs >= minSessionMs }
            .map {
                DayForegroundSegment(
                    packageName = it.packageName,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    ongoing = it.ongoing
                )
            }
    }

    data class DayForegroundSegment(
        val packageName: String,
        val startMs: Long,
        val endMs: Long,
        val ongoing: Boolean
    )

    /**
     * 指定 App 今日系统前台会话（与探索详情同口径）。
     * 含仍在进行中的当前段；跨日切开的后半段 [SystemForegroundSession.countsAsOpen] 为 false。
     */
    suspend fun getTodayForegroundSessions(
        packageName: String
    ): List<SystemForegroundSession> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val raw = queryForegroundSessions(packageName, todayStart, now)
        splitSessionsIntoDay(raw, todayStart, now).sortedByDescending { it.startMs }
    }

    /**
     * 指定自然日的系统前台会话（与探索详情同口径）。
     * [dayStartMs] 须为该日 0 点；未到的未来日返回空。
     */
    suspend fun getForegroundSessionsOnDay(
        packageName: String,
        dayStartMs: Long
    ): List<SystemForegroundSession> = withContext(Dispatchers.IO) {
        val dayMs = 24 * 60 * 60 * 1000L
        val dayEnd = dayStartMs + dayMs
        val now = System.currentTimeMillis()
        if (dayStartMs >= now) return@withContext emptyList()
        val queryEnd = minOf(dayEnd, now)
        if (queryEnd <= dayStartMs) return@withContext emptyList()
        val raw = queryForegroundSessions(packageName, dayStartMs, queryEnd)
        splitSessionsIntoDay(raw, dayStartMs, queryEnd).sortedBy { it.startMs }
    }

    /**
     * 多日系统用量对齐（探索同口径）：柱高 = 当日会话时长之和。
     * 一次拉事件再按日切开，避免柱用日聚合、列表用残缺事件对不上。
     */
    suspend fun getSystemDaysAligned(
        packageName: String,
        dayStarts: List<Long>
    ): Map<Long, SystemDayAligned> = withContext(Dispatchers.IO) {
        if (dayStarts.isEmpty()) return@withContext emptyMap()
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val rangeStart = dayStarts.minOrNull()!!
        val rangeEnd = dayStarts.maxOrNull()!! + dayMs
        if (rangeStart >= now) return@withContext dayStarts.associateWith { SystemDayAligned.Empty }
        val queryEnd = minOf(rangeEnd, now)
        val raw = queryForegroundSessions(packageName, rangeStart, queryEnd)
        dayStarts.associateWith { dStart ->
            if (dStart >= now) return@associateWith SystemDayAligned.Empty
            val dEnd = dStart + dayMs
            val end = minOf(dEnd, now)
            if (end <= dStart) return@associateWith SystemDayAligned.Empty
            val sessions = splitSessionsIntoDay(raw, dStart, end).sortedBy { it.startMs }
            val totalSec = sessions.sumOf { it.durationMs } / 1000L
            SystemDayAligned(totalSeconds = totalSec, sessions = sessions)
        }
    }

    /**
     * 系锚瞬间：加入日之前 [days] 个完整自然日的逐日快照（会话时长 + 打开次数）。
     * @return first = 日均秒数（总秒/天数），second = [PreJoinUsageSnapshot] JSON
     */
    suspend fun capturePreJoinUsageSnapshot(
        packageName: String,
        joinDayStartMs: Long,
        days: Int = 7
    ): Pair<Long, String> = withContext(Dispatchers.IO) {
        if (days <= 0) {
            return@withContext 0L to PreJoinUsageSnapshot.encode(joinDayStartMs, emptyList())
        }
        val dayMs = 24L * 60 * 60 * 1000
        val starts = (days downTo 1).map { joinDayStartMs - it * dayMs }
        val aligned = getSystemDaysAligned(packageName, starts)
        val snaps = starts.map { d ->
            val a = aligned[d] ?: SystemDayAligned.Empty
            com.life.mindfulnessapp.domain.model.PreJoinDaySnap(
                dayStartMs = d,
                totalSeconds = a.totalSeconds.coerceAtLeast(0L),
                opens = a.sessions.count { it.countsAsOpen }
            )
        }
        val total = snaps.sumOf { it.totalSeconds }
        val avg = total / days
        avg to PreJoinUsageSnapshot.encode(joinDayStartMs, snaps)
    }

    /**
     * 探索详情：7 个完整自然日。
     * 柱图 / 汇总 / 下方记录列表统一为「前台会话」口径（打开 = 每次进入，5 秒内合并）。
     */
    suspend fun getExploreAppUsageDetail(
        packageName: String,
        days: Int = AppWeeklySystemUsage.DAYS
    ): ExploreAppUsageDetail = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val dayMs = 24 * 60 * 60 * 1000L
        val completeStart = todayStart - days * dayMs

        val rawSessions = queryForegroundSessions(packageName, completeStart, todayStart)
        val completeDays = buildExploreCompleteDays(
            rangeStart = completeStart,
            dayCount = days,
            todayStart = todayStart,
            rawSessions = rawSessions
        )
        val weekUsage = AppWeeklySystemUsage(
            totalSeconds = completeDays.sumOf { it.totalSeconds },
            totalLaunches = completeDays.sumOf { it.openCount }
        )

        ExploreAppUsageDetail(
            completeDays = completeDays,
            weekUsage = weekUsage
        )
    }

    private fun buildExploreCompleteDays(
        rangeStart: Long,
        dayCount: Int,
        todayStart: Long,
        rawSessions: List<SystemForegroundSession>
    ): List<SystemUsageDayDetail> {
        if (dayCount <= 0) return emptyList()
        val dayMs = 24 * 60 * 60 * 1000L
        val weekdayFmt = SimpleDateFormat("M月d日 EEE", Locale.CHINA)
        val chartFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
        val yesterdayStart = todayStart - dayMs

        return (0 until dayCount).map { offset ->
            val dStart = rangeStart + offset * dayMs
            val dEnd = dStart + dayMs
            val queryEnd = minOf(dEnd, todayStart)
            val daySessions = splitSessionsIntoDay(rawSessions, dStart, queryEnd)
                .sortedBy { it.startMs }
            val openSessions = daySessions.filter { it.countsAsOpen }
            val openCount = openSessions.size
            val totalSeconds = daySessions.sumOf { it.durationMs } / 1000L
            val periods = bucketPeriodsFromLaunches(openSessions.map { it.startMs })
            val isYesterday = dStart == yesterdayStart
            val label = when {
                isYesterday -> "昨天"
                else -> weekdayFmt.format(Date(dStart))
            }
            SystemUsageDayDetail(
                dayStartMs = dStart,
                label = label,
                chartDateLabel = chartFmt.format(Date(dStart)),
                isToday = false,
                isYesterday = isYesterday,
                sessions = daySessions,
                totalSeconds = totalSeconds,
                openCount = openCount,
                periods = periods
            )
        }
    }

    /**
     * 探索详情（旧）：含今天的连续 N 天。新详情请用 [getExploreAppUsageDetail]。
     */
    suspend fun getForegroundSessionsByDay(
        packageName: String,
        days: Int = AppWeeklySystemUsage.DAYS
    ): List<SystemUsageDayDetail> = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val dayMs = 24 * 60 * 60 * 1000L
        val rangeStart = todayStart - (days - 1) * dayMs
        val rawSessions = queryForegroundSessions(packageName, rangeStart, now)
        buildDayDetails(
            rangeStart = rangeStart,
            dayCount = days,
            rangeEndExclusive = rangeStart + days * dayMs,
            now = now,
            todayStart = todayStart,
            rawSessions = rawSessions
        ).asReversed()
    }

    private fun buildDayDetails(
        rangeStart: Long,
        dayCount: Int,
        rangeEndExclusive: Long,
        now: Long,
        todayStart: Long,
        rawSessions: List<SystemForegroundSession>
    ): List<SystemUsageDayDetail> {
        if (dayCount <= 0) return emptyList()
        val dayMs = 24 * 60 * 60 * 1000L
        val weekdayFmt = SimpleDateFormat("M月d日 EEE", Locale.CHINA)
        val chartFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
        val yesterdayStart = todayStart - dayMs

        return (0 until dayCount).map { offset ->
            val dStart = rangeStart + offset * dayMs
            if (dStart >= rangeEndExclusive) return@map null
            val dEnd = dStart + dayMs
            val queryEnd = minOf(dEnd, rangeEndExclusive, now)
            if (queryEnd <= dStart) return@map null
            val daySessions = splitSessionsIntoDay(rawSessions, dStart, queryEnd)
                .sortedBy { it.startMs }
            val totalMs = daySessions.sumOf { it.durationMs }
            val openCount = daySessions.count { it.countsAsOpen }
            val periods = bucketPeriodsFromLaunches(
                daySessions.filter { it.countsAsOpen }.map { it.startMs }
            )
            val isToday = dStart == todayStart
            val isYesterday = dStart == yesterdayStart
            val label = when {
                isToday -> "今天"
                isYesterday -> "昨天"
                else -> weekdayFmt.format(Date(dStart))
            }
            SystemUsageDayDetail(
                dayStartMs = dStart,
                label = label,
                chartDateLabel = chartFmt.format(Date(dStart)),
                isToday = isToday,
                isYesterday = isYesterday,
                sessions = daySessions,
                totalSeconds = totalMs / 1000L,
                openCount = openCount,
                periods = periods
            )
        }.filterNotNull()
    }


    /**
     * 时间之尺：多个包名在 [rangeStartMs, rangeEndMs) 内的前台会话（按日裁剪）。
     * 与系统「使用详情」同口径。
     */
    suspend fun getForegroundSessionsForPackages(
        packageNames: Collection<String>,
        rangeStartMs: Long,
        rangeEndMs: Long
    ): Map<String, List<SystemForegroundSession>> = withContext(Dispatchers.IO) {
        if (packageNames.isEmpty() || rangeEndMs <= rangeStartMs) return@withContext emptyMap()
        val dayMs = 24L * 60 * 60 * 1000
        packageNames.associateWith { pkg ->
            val raw = queryForegroundSessions(pkg, rangeStartMs, rangeEndMs)
            val all = mutableListOf<SystemForegroundSession>()
            var day = rangeStartMs
            while (day < rangeEndMs) {
                val dayEnd = minOf(day + dayMs, rangeEndMs)
                all += splitSessionsIntoDay(raw, day, dayEnd)
                day = dayEnd
            }
            all.sortedBy { it.startMs }
        }
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    private data class FgSegment(
        val packageName: String,
        val startMs: Long,
        val endMs: Long,
        val ongoing: Boolean
    )

    /**
     * 全局单前台扫描：同一时刻只认一个前台 App。
     *
     * 关键点：仅按包名配对 FG/BG 时，OEM 漏发 BACKGROUND 会让会话一直挂到 now，
     * 表现为「刷新时长还在涨」「近 7 日上百小时」。切到另一包的 FOREGROUND 时必须收口上一段。
     */
    private fun scanForegroundSegments(startMs: Long, endMs: Long): List<FgSegment> {
        val now = System.currentTimeMillis()
        val queryEnd = minOf(endMs, now)
        if (queryEnd <= startMs) return emptyList()

        val out = mutableListOf<FgSegment>()
        var currentPkg: String? = null
        var fgStart = -1L

        fun closeAt(end: Long, ongoing: Boolean) {
            val pkg = currentPkg ?: return
            if (fgStart > 0L) {
                val clippedStart = fgStart.coerceAtLeast(startMs)
                val clippedEnd = end.coerceAtMost(queryEnd)
                if (clippedEnd > clippedStart) {
                    out += FgSegment(
                        packageName = pkg,
                        startMs = clippedStart,
                        endMs = clippedEnd,
                        ongoing = ongoing
                    )
                }
            }
            currentPkg = null
            fgStart = -1L
        }

        try {
            val events = usageStatsManager.queryEvents(startMs, queryEnd)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when {
                    isForegroundStart(event.eventType) -> {
                        val t = event.timeStamp.coerceIn(startMs, queryEnd)
                        if (currentPkg == pkg && fgStart > 0L) {
                            // 同包重复 FOREGROUND：忽略
                            continue
                        }
                        if (currentPkg != null) {
                            closeAt(t, ongoing = false)
                        }
                        currentPkg = pkg
                        fgStart = t
                    }
                    isForegroundEnd(event.eventType) -> {
                        if (currentPkg == pkg) {
                            closeAt(event.timeStamp.coerceAtMost(queryEnd), ongoing = false)
                        }
                    }
                }
            }
            if (currentPkg != null) {
                closeAt(queryEnd, ongoing = true)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }

    /** 过滤过短段后，按包合并短间隔相邻段，供排行与详情共用。 */
    private fun mergedSegments(startMs: Long, endMs: Long): List<FgSegment> =
        mergeAdjacentSegments(
            scanForegroundSegments(startMs, endMs)
                .filter { it.endMs - it.startMs >= minSessionMs }
        )

    /**
     * 同包按时间排序后，间隔 ≤ [sessionMergeGapMs] 的相邻段合并为一次打开。
     */
    private fun mergeAdjacentSegments(segments: List<FgSegment>): List<FgSegment> {
        if (segments.isEmpty()) return emptyList()
        val merged = mutableListOf<FgSegment>()
        segments.groupBy { it.packageName }.values.forEach { pkgSegs ->
            val sorted = pkgSegs.sortedBy { it.startMs }
            var cur = sorted.first()
            for (i in 1 until sorted.size) {
                val next = sorted[i]
                val gap = next.startMs - cur.endMs
                if (gap in 0L..sessionMergeGapMs) {
                    cur = cur.copy(
                        endMs = next.endMs,
                        ongoing = next.ongoing
                    )
                } else {
                    merged += cur
                    cur = next
                }
            }
            merged += cur
        }
        return merged.sortedBy { it.startMs }
    }

    private fun isForegroundStart(eventType: Int): Boolean =
        eventType == UsageEvents.Event.MOVE_TO_FOREGROUND

    private fun isForegroundEnd(eventType: Int): Boolean =
        eventType == UsageEvents.Event.MOVE_TO_BACKGROUND

    /** 打开计数：兼容部分 OEM 只报 RESUMED 或只报 FOREGROUND */
    private fun isLaunchEvent(eventType: Int): Boolean {
        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
    }

    /**
     * 解析指定 App 在区间内的前台会话列表（未按日切开）。
     * 必须看全局事件流，才能在漏 BACKGROUND 时靠「别的 App 进前台」收口。
     */
    private fun queryForegroundSessions(
        packageName: String,
        startMs: Long,
        endMs: Long
    ): List<SystemForegroundSession> =
        mergedSegments(startMs, endMs)
            .filter { it.packageName == packageName }
            .map { seg ->
                val span = seg.endMs - seg.startMs
                SystemForegroundSession(
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    durationSeconds = span / 1000L,
                    ongoing = seg.ongoing,
                    countsAsOpen = true
                )
            }

    /** 将会话裁剪到某一自然日窗口；跨日会话拆成当日片段。 */
    private fun splitSessionsIntoDay(
        sessions: List<SystemForegroundSession>,
        dayStart: Long,
        dayEnd: Long
    ): List<SystemForegroundSession> {
        if (dayEnd <= dayStart) return emptyList()
        val out = mutableListOf<SystemForegroundSession>()
        for (s in sessions) {
            val clipStart = maxOf(s.startMs, dayStart)
            val clipEnd = minOf(s.endMs, dayEnd)
            if (clipEnd - clipStart < minSessionMs) continue
            out += SystemForegroundSession(
                startMs = clipStart,
                endMs = clipEnd,
                durationSeconds = (clipEnd - clipStart) / 1000L,
                ongoing = s.ongoing && clipEnd == s.endMs,
                // 只有会话真正开始落在该日的才算一次打开
                countsAsOpen = s.startMs >= dayStart
            )
        }
        return out
    }

    private fun bucketPeriodsFromLaunches(launchTimesMs: List<Long>): SystemDayPeriodStats {
        var dawn = 0
        var morning = 0
        var afternoon = 0
        var evening = 0
        val cal = Calendar.getInstance()
        for (t in launchTimesMs) {
            cal.timeInMillis = t
            when (cal.get(Calendar.HOUR_OF_DAY)) {
                in 0 until 6 -> dawn++
                in 6 until 12 -> morning++
                in 12 until 18 -> afternoon++
                else -> evening++
            }
        }
        return SystemDayPeriodStats(dawn, morning, afternoon, evening)
    }

    /**
     * 探索口径的打开次数：与详情会话列表一致。
     * 每次进入前台计 1 次；同包相邻段间隔 ≤ [sessionMergeGapMs] 合并为一次。
     */
    private fun countSessionOpensByPackage(startMs: Long, endMs: Long): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (seg in mergedSegments(startMs, endMs)) {
            counts[seg.packageName] = (counts[seg.packageName] ?: 0) + 1
        }
        return counts
    }

    /**
     * 系统事件流打开次数（RESUMED / FOREGROUND，800ms 去重）。
     * 微信等 App 内 Activity 切换会偏高，探索页已改用 [countSessionOpensByPackage]。
     */
    private fun countSystemLaunchesByPackage(startMs: Long, endMs: Long): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for ((pkg, _) in scanSystemLaunches(startMs, endMs)) {
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }
        return counts
    }

    private fun systemLaunchTimestamps(
        packageName: String,
        startMs: Long,
        endMs: Long
    ): List<Long> =
        scanSystemLaunches(startMs, endMs, packageName).map { it.second }

    private fun scanSystemLaunches(
        startMs: Long,
        endMs: Long,
        packageName: String? = null
    ): List<Pair<String, Long>> {
        val now = System.currentTimeMillis()
        val queryEnd = minOf(endMs, now)
        if (queryEnd <= startMs) return emptyList()
        val out = mutableListOf<Pair<String, Long>>()
        val lastLaunchMsByPkg = mutableMapOf<String, Long>()
        try {
            val events = usageStatsManager.queryEvents(startMs, queryEnd)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (!isLaunchEvent(event.eventType)) continue
                val pkg = event.packageName ?: continue
                if (packageName != null && pkg != packageName) continue
                val t = event.timeStamp
                val last = lastLaunchMsByPkg[pkg] ?: 0L
                if (t - last < launchEventDedupeMs) continue
                lastLaunchMsByPkg[pkg] = t
                out += pkg to t
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }

    /**
     * 通过 queryEvents 精确计算指定 App 在给定时间段内的前台使用时长（秒）。
     *
     * 相比 queryUsageStats（以天为最小粒度），queryEvents 可按任意时间段精确计算，
     * 适合今日、本周等跨天的场景。
     */
    private fun queryUsageSeconds(packageName: String, startMs: Long, endMs: Long): Long =
        mergedSegments(startMs, endMs)
            .filter { it.packageName == packageName }
            .sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) } / 1000L

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
