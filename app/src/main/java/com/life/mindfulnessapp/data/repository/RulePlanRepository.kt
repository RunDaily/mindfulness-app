package com.life.mindfulnessapp.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.plan.RulePlanStore
import com.life.mindfulnessapp.domain.model.BrowseCasualPolicyCodec
import com.life.mindfulnessapp.domain.model.PreJoinUsageSnapshot
import com.life.mindfulnessapp.domain.model.configRecentLabel
import com.life.mindfulnessapp.domain.model.DailyCapFacts
import com.life.mindfulnessapp.domain.model.PeriodDays
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.domain.model.RuleGoal
import com.life.mindfulnessapp.domain.model.RulePack
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.model.InstrumentUsageGlance
import com.life.mindfulnessapp.domain.model.AppDiary
import com.life.mindfulnessapp.domain.model.AppDiaryDay
import com.life.mindfulnessapp.domain.model.AppDiaryDaySessions
import com.life.mindfulnessapp.domain.model.AppDiarySessionEntry
import com.life.mindfulnessapp.domain.model.AppDiarySessionKind
import com.life.mindfulnessapp.domain.model.AppDiaryDaySource
import com.life.mindfulnessapp.domain.model.AppDiaryTrendDay
import com.life.mindfulnessapp.domain.model.AppDiaryVisit
import com.life.mindfulnessapp.domain.model.AppDiaryVisitKind
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.SystemForegroundSession
import com.life.mindfulnessapp.domain.model.SystemDayAligned
import com.life.mindfulnessapp.domain.model.TodayAppEffect
import com.life.mindfulnessapp.domain.model.TodayRuleRow
import com.life.mindfulnessapp.domain.model.UsageDigestRow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class RulePlanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLimitRepository: AppLimitRepository,
    private val systemUsageRepository: SystemUsageRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val store: RulePlanStore,
    private val appPreferences: AppPreferences
) {
    fun goal(): RuleGoal = store.goal()

    suspend fun activePacks(): List<RulePack> {
        return appLimitRepository.getAllLimitsOnce()
            .filter { it.isEnabled }
            .sortedBy { it.sortOrder }
            .map { entity -> entity.toPack() }
    }

    suspend fun todayEffect(packageName: String): TodayAppEffect {
        val pack = activePacks().find { it.packageName == packageName }
        val records = usageRecordRepository.getDayRecordsForApp(packageName)
        val held = records.count {
            UsageRecordEntity.EndReason.isGateQuit(it.purpose, it.endReason, it.durationSeconds)
        }
        val period = records.count { it.endReason == UsageRecordEntity.EndReason.PERIOD_LOCK }
        val entered = records.count {
            !UsageRecordEntity.EndReason.isGateQuit(it.purpose, it.endReason, it.durationSeconds) &&
                it.endReason != UsageRecordEntity.EndReason.PERIOD_LOCK &&
                it.endReason != UsageRecordEntity.EndReason.SEED_FROM_SYSTEM &&
                it.durationSeconds > 0L
        }
        val attempts = entered + held
        return TodayAppEffect(
            packageName = packageName,
            appName = pack?.appName ?: packageName,
            attempts = attempts,
            entered = entered,
            held = held,
            periodBlocks = period
        )
    }

    suspend fun appDiary(packageName: String): AppDiary? {
        val pack = activePacks().find { it.packageName == packageName } ?: return null
        var limit = appLimitRepository.getAppLimit(packageName) ?: return null
        limit = ensureBaseline(limit)
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val dayMs = 24L * 60 * 60 * 1000
        val joinDay = UsageRecordRepository.getDayRange(limit.createdAt).first
        // 监控后只用心锚；今日已用仍含种子（日限公平）；走势柱不含种子
        val recordSec = usageRecordRepository.getDailyUsageSeconds(packageName, now)
        val usedMin = DailyCapFacts.wholeMinutes(DailyCapFacts.usedSeconds(recordSec))
        val todayRecords = usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName, now)
        val held = todayRecords.count { it.isGateQuit }
        val glance = weekGlance(packageName)
        val preJoinByDay = PreJoinUsageSnapshot.byDayStart(limit.preJoinUsageJson)
        val trendStart = joinDay - 7 * dayMs

        // 选日列表仍 7 天；监控后日用心锚，加入前读冻结快照
        val days = (6 downTo 0).map { offset ->
            val start = todayStart - offset * dayMs
            val isToday = offset == 0
            val afterJoin = start >= joinDay
            val dayRecords = when {
                !afterJoin -> emptyList()
                isToday -> todayRecords
                else -> usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName, start)
            }
            val minutes = if (afterJoin) {
                DailyCapFacts.wholeMinutes(anchorTrendSeconds(dayRecords, now, includeOngoing = isToday))
            } else {
                DailyCapFacts.wholeMinutes(preJoinByDay[start]?.totalSeconds ?: 0L)
            }
            AppDiaryDay(
                dayStartMs = start,
                minutes = minutes,
                isToday = isToday,
                weekday = weekdayShort(start, isToday),
                whenLabel = whenLabel(start, isToday),
                visits = dayRecords.mapNotNull { toDiaryVisit(it, now) }
                    .sortedBy { it.startMs }
            )
        }

        val trendDays = mutableListOf<AppDiaryTrendDay>()
        var cursor = trendStart
        while (cursor <= todayStart) {
            val afterJoin = cursor >= joinDay
            val isToday = cursor == todayStart
            val minutes = if (afterJoin) {
                val dayRecords = if (isToday) {
                    todayRecords
                } else {
                    usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName, cursor)
                }
                DailyCapFacts.wholeMinutes(anchorTrendSeconds(dayRecords, now, includeOngoing = isToday))
            } else {
                DailyCapFacts.wholeMinutes(preJoinByDay[cursor]?.totalSeconds ?: 0L)
            }
            val opens = if (afterJoin) {
                val dayRecords = if (isToday) {
                    todayRecords
                } else {
                    usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName, cursor)
                }
                UsageRecordCounts.enterCount(dayRecords)
            } else {
                preJoinByDay[cursor]?.opens ?: 0
            }
            trendDays += AppDiaryTrendDay(
                dayStartMs = cursor,
                minutes = minutes,
                source = if (afterJoin) AppDiaryDaySource.AnchorAfter else AppDiaryDaySource.SystemBefore,
                isJoinDay = cursor == joinDay,
                weekday = weekdayShort(cursor, isToday),
                opens = opens,
                isToday = isToday
            )
            cursor += dayMs
        }

        val baselineMin = limit.baselineDailyAvgSeconds.takeIf { limit.baselineCapturedAt > 0L }
            ?.let { DailyCapFacts.wholeMinutes(it) }
        val nightLine = nightWhisper(nightShareFromHours(glance.hourSeconds), glance.empty)
        val todaySessions = todayRecords.mapNotNull { r ->
            if (r.isSeed || r.isGateQuit || r.isPositiveExit) return@mapNotNull null
            val ongoing = r.endTime <= 0L
            val endMs = if (ongoing) now else r.endTime
            val durSec = if (ongoing) {
                ((now - r.startTime) / 1000L).coerceAtLeast(0L)
            } else {
                r.durationSeconds
            }
            if (durSec <= 0L && !ongoing) return@mapNotNull null
            SystemForegroundSession(
                startMs = r.startTime,
                endMs = endMs,
                durationSeconds = durSec.coerceAtLeast(0L),
                ongoing = ongoing,
                countsAsOpen = true
            )
        }.sortedBy { it.startMs }
        val postJoinAvg = postJoinDailyAvg(trendDays, todayStart)
        val beforeOpens = PreJoinUsageSnapshot.avgOpens(limit.preJoinUsageJson)
            ?: baselineAvgOpens(trendDays)
        return AppDiary(
            packageName = packageName,
            appName = pack.appName,
            usedMinutes = usedMin,
            limitMinutes = pack.dailyMinutes,
            opens = UsageRecordCounts.enterCount(todayRecords),
            openLimit = pack.dailyOpenLimit,
            held = held,
            avgMinutes = postJoinAvg.avgMinutes,
            avgOpens = postJoinAvg.avgOpens,
            avgReady = postJoinAvg.ready,
            vsBeforeRule = vsBeforeRuleMinutes(
                baselineAvgMinutes = baselineMin,
                createdAt = limit.createdAt,
                todayStart = todayStart,
                packageName = packageName
            ),
            hourSeconds = glance.hourSeconds,
            nightLine = nightLine,
            days = days,
            trendDays = trendDays,
            baselineAvgMinutes = baselineMin,
            baselineAvgOpens = beforeOpens,
            joinDayStartMs = joinDay,
            todayDayStartMs = todayStart,
            todaySessions = todaySessions,
            instrumentSummary = pack.summary(),
            configRecentLabel = configRecentLabel(limit.rulesUpdatedAt, now)
        )
    }

    /**
     * 加入后完整日（不含今天）日均：与走势绿色段 / 打开矮柱同口径。
     * 最多取近 7 个完整日；尚无完整日则 ready=false。
     */
    private fun postJoinDailyAvg(
        trendDays: List<AppDiaryTrendDay>,
        todayStart: Long
    ): PostJoinDailyAvg {
        val complete = trendDays
            .filter {
                it.source == AppDiaryDaySource.AnchorAfter && it.dayStartMs < todayStart
            }
            .takeLast(7)
        if (complete.isEmpty()) {
            return PostJoinDailyAvg(avgMinutes = 0, avgOpens = 0, ready = false)
        }
        val n = complete.size
        return PostJoinDailyAvg(
            avgMinutes = complete.sumOf { it.minutes } / n,
            avgOpens = (complete.sumOf { it.opens.toDouble() } / n).roundToInt(),
            ready = true
        )
    }

    /** 监控前打开日均：走势里加入前系统柱的平均。 */
    private fun baselineAvgOpens(trendDays: List<AppDiaryTrendDay>): Int? {
        val before = trendDays.filter { it.source == AppDiaryDaySource.SystemBefore }
        if (before.isEmpty()) return null
        return (before.sumOf { it.opens.toDouble() } / before.size).roundToInt()
    }

    private data class PostJoinDailyAvg(
        val avgMinutes: Int,
        val avgOpens: Int,
        val ready: Boolean
    )

    /**
     * 走势细看 · 选日逐次。
     * 加入前：系统前台段；加入后：心锚记录（门口离开单独标）。
     */
    suspend fun daySessions(packageName: String, dayStartMs: Long): AppDiaryDaySessions? {
        val limit = appLimitRepository.getAppLimit(packageName) ?: return null
        val ensured = ensureBaseline(limit)
        val joinDay = UsageRecordRepository.getDayRange(ensured.createdAt).first
        val dayMs = 24L * 60 * 60 * 1000
        val dayEnd = dayStartMs + dayMs
        val now = System.currentTimeMillis()
        val (todayStart, _) = UsageRecordRepository.getDayRange(now)
        val afterJoin = dayStartMs >= joinDay
        val label = whenLabel(dayStartMs, dayStartMs == todayStart)

        if (afterJoin) {
            val records = usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName, dayStartMs)
            val entries = ArrayList<AppDiarySessionEntry>()
            records.forEach { r ->
                // 走势细看不展示：系统种子、门口离开、正向离开
                if (r.isSeed || r.isGateQuit || r.isPositiveExit) return@forEach
                val end = if (r.endTime > 0L) r.endTime else now
                val durSec = if (r.endTime > 0L) {
                    r.durationSeconds
                } else {
                    ((now - r.startTime) / 1000L).coerceAtLeast(0L)
                }
                when {
                    r.endTime <= 0L -> {
                        if (durSec <= 0L) return@forEach
                        entries += AppDiarySessionEntry(
                            startMs = r.startTime,
                            endMs = end,
                            durationMinutes = DailyCapFacts.wholeMinutes(durSec),
                            durationSeconds = durSec,
                            title = r.purpose?.trim()?.takeIf { it.isNotEmpty() } ?: "使用中",
                            subtitle = "现在",
                            kind = AppDiarySessionKind.Ongoing,
                            source = AppDiaryDaySource.AnchorAfter
                        )
                    }
                    else -> {
                        if (durSec <= 0L) return@forEach
                        entries += AppDiarySessionEntry(
                            startMs = r.startTime,
                            endMs = end,
                            durationMinutes = DailyCapFacts.wholeMinutes(durSec),
                            durationSeconds = durSec,
                            title = r.purpose?.trim()?.takeIf { it.isNotEmpty() } ?: "使用",
                            subtitle = null,
                            kind = AppDiarySessionKind.AnchorUse,
                            source = AppDiaryDaySource.AnchorAfter
                        )
                    }
                }
            }
            entries.sortBy { it.startMs }
            return AppDiaryDaySessions(
                dayStartMs = dayStartMs,
                label = label,
                source = AppDiaryDaySource.AnchorAfter,
                totalMinutes = DailyCapFacts.wholeMinutes(
                    anchorTrendSeconds(records, now, includeOngoing = dayStartMs == todayStart)
                ),
                entries = entries,
                sectionLabel = "心锚"
            )
        }

        val aligned = runCatching {
            systemUsageRepository.getSystemDaysAligned(packageName, listOf(dayStartMs))[dayStartMs]
        }.getOrNull() ?: SystemDayAligned.Empty
        val mapSec = runCatching {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            systemUsageRepository.getLast30DayUsageMap(packageName)[sdf.format(java.util.Date(dayStartMs))] ?: 0L
        }.getOrDefault(0L)

        // 系统事件已清时，退回加入时冻结的逐日快照（仅有日合计）
        if (aligned.sessions.isEmpty() && aligned.totalSeconds <= 0L && mapSec <= 0L) {
            val snap = PreJoinUsageSnapshot.byDayStart(ensured.preJoinUsageJson)[dayStartMs]
            if (snap != null && snap.totalSeconds > 0L) {
                val mins = DailyCapFacts.wholeMinutes(snap.totalSeconds)
                return AppDiaryDaySessions(
                    dayStartMs = dayStartMs,
                    label = label,
                    source = AppDiaryDaySource.SystemBefore,
                    totalMinutes = mins,
                    entries = listOf(
                        AppDiarySessionEntry(
                            startMs = dayStartMs,
                            endMs = dayEnd,
                            durationMinutes = mins,
                            durationSeconds = snap.totalSeconds,
                            title = "仅有日合计",
                            subtitle = "打开 ${snap.opens} 次 · 加入前快照",
                            kind = AppDiarySessionKind.DayTotalOnly,
                            source = AppDiaryDaySource.SystemBefore
                        )
                    ),
                    sectionLabel = "系统 · 快照"
                )
            }
        }

        return buildSystemDaySessions(
            dayStartMs = dayStartMs,
            dayEnd = dayEnd,
            label = label,
            aligned = aligned,
            mapSec = mapSec,
            now = now
        )
    }

    /**
     * 走势用心锚秒数：真实放行使用（含进行中），不含系统种子、门口离开。
     * 与逐次列表同源；日限「今日已用」仍可含种子，不走这里。
     */
    private fun anchorTrendSeconds(
        records: List<UsageRecordEntity>,
        now: Long,
        includeOngoing: Boolean
    ): Long {
        var sum = 0L
        records.forEach { r ->
            if (r.isSeed || r.isGateQuit || r.isPositiveExit) return@forEach
            when {
                r.endTime <= 0L -> {
                    if (includeOngoing) {
                        sum += ((now - r.startTime) / 1000L).coerceAtLeast(0L)
                    }
                }
                else -> sum += r.durationSeconds.coerceAtLeast(0L)
            }
        }
        return sum
    }

    /**
     * 系统日柱高：优先会话之和（与探索 / 逐次同源）；
     * 事件已残缺时才退回日聚合，并在逐次里只展示「仅有日合计」。
     */
    private fun systemDayMinutes(
        dayStartMs: Long,
        aligned: SystemDayAligned?,
        mapSec: Long
    ): Int {
        val sessionSec = aligned?.totalSeconds ?: 0L
        return when {
            !isSystemEventsIncomplete(sessionSec, mapSec) && sessionSec > 0L ->
                DailyCapFacts.wholeMinutes(sessionSec)
            mapSec > 0L -> DailyCapFacts.wholeMinutes(mapSec)
            else -> DailyCapFacts.wholeMinutes(sessionSec)
        }
    }

    /** 事件窗口已清、只剩碎段：会话远少于日聚合。 */
    private fun isSystemEventsIncomplete(sessionSec: Long, mapSec: Long): Boolean {
        if (mapSec < 60L) return false
        if (sessionSec <= 0L) return true
        return sessionSec * 2L < mapSec && (mapSec - sessionSec) >= 120L
    }

    private fun buildSystemDaySessions(
        dayStartMs: Long,
        dayEnd: Long,
        label: String,
        aligned: SystemDayAligned,
        mapSec: Long,
        now: Long
    ): AppDiaryDaySessions {
        val sessionSec = aligned.totalSeconds
        val incomplete = isSystemEventsIncomplete(sessionSec, mapSec)

        if (incomplete || (aligned.sessions.isEmpty() && mapSec > 0L)) {
            val mins = DailyCapFacts.wholeMinutes(mapSec.takeIf { it > 0L } ?: sessionSec)
            val entries = if (mins > 0) {
                listOf(
                    AppDiarySessionEntry(
                        startMs = dayStartMs,
                        endMs = dayEnd,
                        durationMinutes = mins,
                        durationSeconds = (mapSec.takeIf { it > 0L } ?: sessionSec),
                        title = "仅有日合计",
                        subtitle = if (incomplete && sessionSec > 0L) {
                            "系统事件已不完整，逐次不可靠"
                        } else {
                            "系统事件已不可逐次还原"
                        },
                        kind = AppDiarySessionKind.DayTotalOnly,
                        source = AppDiaryDaySource.SystemBefore
                    )
                )
            } else {
                emptyList()
            }
            return AppDiaryDaySessions(
                dayStartMs = dayStartMs,
                label = label,
                source = AppDiaryDaySource.SystemBefore,
                totalMinutes = mins,
                entries = entries,
                sectionLabel = "系统"
            )
        }

        val listed = aligned.sessions
            .filter { it.ongoing || it.durationSeconds > 0L }
            .sortedBy { it.startMs }
        val entries = listed.map { s ->
            val end = if (s.ongoing) now else s.endMs
            AppDiarySessionEntry(
                startMs = s.startMs,
                endMs = end,
                durationMinutes = DailyCapFacts.wholeMinutes(s.durationSeconds),
                durationSeconds = s.durationSeconds.coerceAtLeast(0L),
                title = if (s.ongoing) "前台中" else "前台",
                subtitle = null,
                kind = if (s.ongoing) AppDiarySessionKind.Ongoing else AppDiarySessionKind.SystemForeground,
                source = AppDiaryDaySource.SystemBefore
            )
        }
        // 总数 = 会话秒之和取整（与柱、探索一致）；行上可显示秒，不必强求「分」相加等于总
        return AppDiaryDaySessions(
            dayStartMs = dayStartMs,
            label = label,
            source = AppDiaryDaySource.SystemBefore,
            totalMinutes = DailyCapFacts.wholeMinutes(sessionSec),
            entries = entries,
            sectionLabel = "系统"
        )
    }

    /** 首次加入时冻结前 7 日日均 + 逐日快照；旧包补记一次。 */
    private suspend fun ensureBaseline(limit: AppLimitEntity): AppLimitEntity {
        val needsAvg = limit.baselineCapturedAt <= 0L
        val needsSnap = !PreJoinUsageSnapshot.hasSnapshot(limit.preJoinUsageJson)
        if (!needsAvg && !needsSnap) return limit
        val joinDay = UsageRecordRepository.getDayRange(limit.createdAt).first
        val (avg, json) = runCatching {
            systemUsageRepository.capturePreJoinUsageSnapshot(limit.packageName, joinDay)
        }.getOrElse {
            0L to PreJoinUsageSnapshot.encode(joinDay, emptyList())
        }
        val updated = limit.copy(
            baselineDailyAvgSeconds = if (needsAvg) avg else limit.baselineDailyAvgSeconds,
            baselineCapturedAt = if (needsAvg) {
                System.currentTimeMillis()
            } else {
                limit.baselineCapturedAt
            },
            preJoinUsageJson = if (needsSnap) json else limit.preJoinUsageJson
        )
        appLimitRepository.saveAppLimit(updated)
        return updated
    }

    private suspend fun vsBeforeRuleMinutes(
        baselineAvgMinutes: Int?,
        createdAt: Long,
        todayStart: Long,
        packageName: String
    ): Int? {
        val before = baselineAvgMinutes ?: return null
        if (before <= 0) return null
        val dayMs = 24L * 60 * 60 * 1000
        val createdDay = UsageRecordRepository.getDayRange(createdAt).first
        val completeAfter = ((todayStart - createdDay) / dayMs).toInt()
        if (completeAfter < 3) return null
        val afterDays = (1..minOf(7, completeAfter)).map { todayStart - it * dayMs }
        var sum = 0L
        afterDays.forEach { day ->
            sum += usageRecordRepository.getDailyUsageSeconds(packageName, day)
        }
        val after = DailyCapFacts.wholeMinutes(sum / afterDays.size)
        val diff = after - before
        return if (diff == 0) null else diff
    }

    private fun toDiaryVisit(record: UsageRecordEntity, now: Long): AppDiaryVisit? {
        if (record.isSeed || record.isPositiveExit) return null
        val kind = when {
            record.isGateQuit -> AppDiaryVisitKind.Hold
            IntentKind.fromStorage(record.intentKind) == IntentKind.SEARCH ->
                AppDiaryVisitKind.Search
            record.sessionLimitMinutes > 0 -> AppDiaryVisitKind.Write
            !record.purpose.isNullOrBlank() -> AppDiaryVisitKind.Write
            else -> AppDiaryVisitKind.Open
        }
        val durSec = if (record.endTime <= 0L) {
            ((now - record.startTime) / 1000L).coerceAtLeast(0L)
        } else {
            record.durationSeconds
        }
        val minutes = (durSec / 60L).toInt()
        val what = record.purpose?.trim()?.takeIf { it.isNotEmpty() }
        if (kind == AppDiaryVisitKind.Hold) return null
        if (minutes <= 0 && what == null) return null
        return AppDiaryVisit(
            startMs = record.startTime,
            kind = kind,
            what = what,
            durationMinutes = minutes,
            limitMinutes = record.sessionLimitMinutes
        )
    }

    private fun nightShareFromHours(hours: List<Long>): Int {
        val total = hours.sum()
        if (total <= 0L) return 0
        val night = hours.mapIndexed { hour, sec ->
            if (hour >= 22 || hour < 7) sec else 0L
        }.sum()
        return ((night * 100L) / total).toInt()
    }

    private fun nightWhisper(percent: Int, empty: Boolean): String? {
        if (empty || percent < 30) return null
        if (percent >= 50) return "22 点后过半"
        val word = when (percent / 10) {
            3 -> "三"
            4 -> "四"
            else -> return "22 点后过半"
        }
        return "22 点后占${word}成"
    }

    private fun weekdayShort(dayStartMs: Long, isToday: Boolean): String {
        if (isToday) return "今"
        return when (Calendar.getInstance().apply { timeInMillis = dayStartMs }
            .get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "一"
            Calendar.TUESDAY -> "二"
            Calendar.WEDNESDAY -> "三"
            Calendar.THURSDAY -> "四"
            Calendar.FRIDAY -> "五"
            Calendar.SATURDAY -> "六"
            else -> "日"
        }
    }

    private fun whenLabel(dayStartMs: Long, isToday: Boolean): String {
        if (isToday) return "今天"
        val cal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
        val week = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> "周日"
        }
        return "$week · ${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DATE)}"
    }

    fun consumePendingEdit(): String? = store.consumeEdit()

    fun requestEdit(packageName: String) = store.requestEdit(packageName)

    /** 今日实际进入次数（不含守住 / seed / 正向出口）。 */
    suspend fun todayAdmittedOpens(packageName: String): Int {
        val records = usageRecordRepository.getDayRecordsForAppIncludingOpen(packageName)
        return UsageRecordCounts.enterCount(records)
    }

    suspend fun todayRows(): List<TodayRuleRow> {
        val packs = activePacks()
        return packs.map { pack ->
            val recordSec = usageRecordRepository.getDailyUsageSeconds(pack.packageName)
            val usedSec = DailyCapFacts.usedSeconds(recordSec)
            val configured = pack.dailyMinutes
            val limitSec = if (configured != null && configured > 0) {
                appPreferences.effectiveDailyLimitSeconds(pack.packageName, configured)
            } else {
                0L
            }
            val usedMin = DailyCapFacts.wholeMinutes(usedSec)
            val limitMin = if (limitSec > 0L) DailyCapFacts.wholeMinutes(limitSec) else null
            val opens = todayAdmittedOpens(pack.packageName)
            val openLimit = pack.dailyOpenLimit
            val exhausted = DailyCapFacts.exhausted(usedSec, limitSec) ||
                (openLimit != null && opens >= openLimit)
            TodayRuleRow(
                packageName = pack.packageName,
                appName = pack.appName,
                usedMinutes = usedMin,
                limitMinutes = limitMin,
                opens = opens,
                openLimit = openLimit,
                summary = pack.summary(),
                exhausted = exhausted
            )
        }
    }

    suspend fun addWithMidDefaults(
        packageNames: List<String>,
        names: Map<String, String>
    ) {
        val packs = packageNames.distinct().map { pkg ->
            RulePack(
                packageName = pkg,
                appName = names[pkg].orEmpty().ifBlank { pkg },
                dailyOpenLimit = null,
                dailyMinutes = 30,
                browseCasualDailyMinutes = 20,
                rationale = "中档默认",
                selected = true
            )
        }
        adopt(store.goal(), packs)
    }

    suspend fun adopt(goal: RuleGoal, packs: List<RulePack>) {
        store.saveGoal(
            goal.copy(
                confirmed = goal.label.isNotBlank(),
                updatedAt = System.currentTimeMillis()
            )
        )
        val chosen = packs.filter {
            it.selected &&
                !com.life.mindfulnessapp.domain.model.MonitorSuitability.isUnsuitable(it.packageName)
        }
        if (chosen.isEmpty()) return
        chosen.forEach { pack ->
            val existing = appLimitRepository.getLimit(pack.packageName)
            val periodJson = periodJson(pack)
            val shouldSeedBaseline = existing == null ||
                !existing.isEnabled ||
                existing.baselineCapturedAt <= 0L ||
                !PreJoinUsageSnapshot.hasSnapshot(existing.preJoinUsageJson)
            val now = System.currentTimeMillis()
            val joinDay = UsageRecordRepository.getDayRange(
                existing?.createdAt ?: now
            ).first
            val (baselineAvg, baselineAt, preJoinJson) = if (shouldSeedBaseline) {
                val (avg, json) = runCatching {
                    systemUsageRepository.capturePreJoinUsageSnapshot(pack.packageName, joinDay)
                }.getOrElse {
                    0L to PreJoinUsageSnapshot.encode(joinDay, emptyList())
                }
                val keepAvg = existing != null && existing.baselineCapturedAt > 0L
                Triple(
                    if (keepAvg) existing.baselineDailyAvgSeconds else avg,
                    if (keepAvg) existing.baselineCapturedAt else now,
                    if (existing != null && PreJoinUsageSnapshot.hasSnapshot(existing.preJoinUsageJson)) {
                        existing.preJoinUsageJson
                    } else {
                        json
                    }
                )
            } else {
                Triple(
                    existing.baselineDailyAvgSeconds,
                    existing.baselineCapturedAt,
                    existing.preJoinUsageJson
                )
            }
            val base = existing ?: AppLimitEntity(
                packageName = pack.packageName,
                appName = pack.appName,
                sortOrder = appLimitRepository.nextSortOrder(),
                requireIntentOnOpen = false,
                timeLimitEnabled = false,
                sessionLimitEnabled = false
            )
            val browsePolicy = BrowseCasualPolicyCodec.decode(base.browseCasualJson).let { cur ->
                val minutes = pack.browseCasualDailyMinutes
                if (minutes == null) cur
                else {
                    val capped = minutes.coerceAtLeast(0).let { b ->
                        val total = pack.dailyMinutes
                        if (total != null && b > 0) b.coerceAtMost(total) else b
                    }
                    cur.copy(dailyLimitMinutes = capped)
                }
            }
            appLimitRepository.saveAppLimit(
                base.copy(
                    appName = pack.appName.ifBlank { base.appName },
                    isEnabled = true,
                    requireIntentOnOpen = false,
                    sessionLimitEnabled = false,
                    timeLimitEnabled = pack.dailyMinutes != null,
                    dailyLimitMinutes = pack.dailyMinutes ?: base.dailyLimitMinutes,
                    // 方案配置已撤下次数；新保存一律关闭
                    dailyOpenLimitEnabled = false,
                    dailyOpenLimit = pack.dailyOpenLimit ?: base.dailyOpenLimit,
                    periodLockEnabled = periodJson.isNotEmpty(),
                    periodWindowsJson = periodJson,
                    periodLockCommitment = if (periodJson.isNotEmpty()) "为自己留的时段" else base.periodLockCommitment,
                    browseCasualJson = BrowseCasualPolicyCodec.encode(browsePolicy),
                    baselineDailyAvgSeconds = baselineAvg,
                    baselineCapturedAt = baselineAt,
                    preJoinUsageJson = preJoinJson,
                    rulesUpdatedAt = now,
                    blockDiscoverFeedEnabled = false
                )
            )
            store.saveMeta(
                pack.packageName,
                pack.rationale,
                pack.searchDirectEnabled,
            )
        }
    }

    suspend fun updatePack(pack: RulePack) {
        if (com.life.mindfulnessapp.domain.model.MonitorSuitability.isUnsuitable(pack.packageName)) {
            return
        }
        adopt(store.goal(), listOf(pack.copy(selected = true)))
    }

    suspend fun weekGlance(packageName: String): InstrumentUsageGlance {
        val week = systemUsageRepository.getLast7CompleteDaysUsageByPackage()[packageName]
        val (start, end) = precedingWeekRange()
        val hours = runCatching {
            systemUsageRepository.getHourlyDistribution(packageName, start, end)
        }.getOrDefault(emptyList())
        val buckets = LongArray(24)
        hours.forEach { buckets[it.hour.coerceIn(0, 23)] = it.totalSeconds }
        val total = buckets.sum()
        val night = (0 until 24).sumOf { h ->
            if (h >= 23 || h < 7) buckets[h] else 0L
        }
        val avgMin = ((week?.avgDailySeconds ?: 0L) / 60L).toInt()
        val avgOpens = week?.avgDailyLaunches ?: 0
        return InstrumentUsageGlance(
            avgMinutes = avgMin,
            avgOpens = avgOpens,
            hourSeconds = buckets.toList(),
            nightPercent = if (total <= 0L) 0 else ((night * 100L) / total).toInt(),
            empty = avgMin == 0 && avgOpens == 0 && total == 0L
        )
    }

    suspend fun remove(packageName: String) {
        appLimitRepository.deleteAppLimit(packageName)
        store.clearMeta(packageName)
    }

    suspend fun usageMirror(): List<UsageDigestRow> = rankUsage(withNight = false)

    /** 对话 / 校准用：近 7 日真实用过的包，带夜间占比。只点得名这里出现的。 */
    suspend fun usageForAdvice(): List<UsageDigestRow> = rankUsage(withNight = true)

    private suspend fun digestTop(): List<UsageDigestRow> {
        val all = rankUsage(withNight = true)
        val eligible = all.filter { it.eligible }
        return eligible.ifEmpty { all.take(4) }
    }

    private suspend fun rankUsage(withNight: Boolean): List<UsageDigestRow> = withContext(Dispatchers.IO) {
        val launchers = launcherPackages()
        val week = systemUsageRepository.getLast7CompleteDaysUsageByPackage()
        val pm = context.packageManager
        val ranked = week.entries
            .filter { (pkg, usage) ->
                pkg in launchers &&
                    pkg != context.packageName &&
                    !isUtilityApp(pm, pkg) &&
                    (usage.avgDailySeconds >= 2 * 60 || usage.avgDailyLaunches >= 5)
            }
            .sortedByDescending { (_, usage) ->
                usage.avgDailySeconds + usage.avgDailyLaunches * 45L
            }
            .take(6)
        val (start, end) = precedingWeekRange()
        ranked.map { (pkg, usage) ->
            val name = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            UsageDigestRow(
                packageName = pkg,
                appName = name,
                avgDailyMinutes = (usage.avgDailySeconds / 60L).toInt(),
                avgDailyOpens = usage.avgDailyLaunches,
                nightSharePercent = if (withNight) nightShare(pkg, start, end) else 0,
                eligible = isWorthPlanning(usage.avgDailySeconds, usage.avgDailyLaunches)
            )
        }
    }

    private suspend fun nightShare(packageName: String, startMs: Long, endMs: Long): Int {
        val hours = runCatching {
            systemUsageRepository.getHourlyDistribution(packageName, startMs, endMs)
        }.getOrDefault(emptyList())
        val total = hours.sumOf { it.totalSeconds }
        if (total <= 0L) return 0
        val night = hours.filter { it.hour >= 23 || it.hour < 7 }.sumOf { it.totalSeconds }
        return ((night * 100L) / total).toInt()
    }

    private fun precedingWeekRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = cal.timeInMillis
        val start = end - 7L * 24 * 60 * 60 * 1000
        return start to end
    }

    /** 日均有一段真正的使用，或又勤又不是点一下就走。 */
    private fun isWorthPlanning(avgDailySeconds: Long, avgDailyLaunches: Int): Boolean =
        avgDailySeconds >= 10 * 60 ||
            (avgDailyLaunches >= 8 && avgDailySeconds >= 5 * 60)

    private fun isUtilityApp(pm: PackageManager, packageName: String): Boolean {
        if (UTILITY_PACKAGES.contains(packageName)) return true
        if (UTILITY_PREFIXES.any { packageName.startsWith(it) }) return true
        val flags = runCatching { pm.getApplicationInfo(packageName, 0).flags }.getOrDefault(0)
        val system = flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        return system && !CONTENT_SYSTEM_PACKAGES.contains(packageName)
    }

    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private fun periodJson(pack: RulePack): String {
        val s = pack.periodStartMinute ?: return ""
        val e = pack.periodEndMinute ?: return ""
        return PeriodWindowsCodec.encode(
            listOf(
                PeriodWindow(
                    startMinute = s,
                    endMinute = e,
                    daysMask = PeriodDays.EVERY_DAY,
                    enabled = true
                )
            )
        )
    }

    private fun AppLimitEntity.toPack(): RulePack {
        val windows = PeriodWindowsCodec.decode(periodWindowsJson).filter { it.enabled }
        val window = windows.firstOrNull()
        val browse = BrowseCasualPolicyCodec.decode(browseCasualJson)
        val daily = if (timeLimitEnabled) dailyLimitMinutes else null
        val browseMinutes = browse.dailyLimitMinutes.let { b ->
            if (daily != null && b > 0) b.coerceAtMost(daily) else b
        }
        return RulePack(
            packageName = packageName,
            appName = appName,
            // 次数上限已撤；详情/今日不再展示
            dailyOpenLimit = null,
            dailyMinutes = daily,
            browseCasualDailyMinutes = browseMinutes,
            periodStartMinute = if (periodLockEnabled) window?.startMinute else null,
            periodEndMinute = if (periodLockEnabled) window?.endMinute else null,
            rationale = store.rationale(packageName),
            blockDiscoverFeed = false,
            searchDirectEnabled = store.searchDirectEnabled(packageName),
        )
    }

    companion object {
        private val UTILITY_PACKAGES = setOf(
            "com.android.settings",
            "com.android.systemui",
            "com.android.phone",
            "com.android.dialer",
            "com.android.mms",
            "com.android.messaging",
            "com.android.contacts",
            "com.android.camera",
            "com.android.camera2",
            "com.android.gallery3d",
            "com.android.deskclock",
            "com.android.calculator2",
            "com.sec.android.app.camera",
            "com.sec.android.gallery3d",
            "com.sec.android.app.launcher",
            "com.sec.android.app.sbrowser",
            "com.samsung.android.messaging",
            "com.samsung.android.dialer",
            "com.samsung.android.app.contacts",
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.google.android.apps.photos",
            "com.google.android.calculator",
            "com.google.android.deskclock"
        )
        private val UTILITY_PREFIXES = listOf(
            "com.android.settings",
            "com.android.systemui",
            "com.samsung.android.app.telephony",
            "com.samsung.android.incallui"
        )
        /** 预装但属于内容消费，不因系统标记被排除。 */
        private val CONTENT_SYSTEM_PACKAGES = setOf(
            "com.android.chrome",
            "com.google.android.youtube"
        )
    }
}
