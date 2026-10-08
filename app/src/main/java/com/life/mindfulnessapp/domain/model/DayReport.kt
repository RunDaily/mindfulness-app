package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

/**
 * 日报统览：对应首页三项简要数据的详细展开。
 * 带着意图 / 守住离开按 App 聚合；组内时间倒序。
 */
data class DayReportMindfulItem(
    val recordId: Long,
    val packageName: String,
    val appName: String,
    val startTime: Long,
    val intentText: String,
    val compareLabel: String?,
    val note: String?,
    val durationSeconds: Long,
    val isOngoing: Boolean
)

data class DayReportHeldItem(
    val recordId: Long,
    val packageName: String,
    val appName: String,
    val startTime: Long,
    /** 门外停下后进了心锚 */
    val toOwnApp: Boolean
)

data class DayReportMindfulGroup(
    val packageName: String,
    val appName: String,
    val items: List<DayReportMindfulItem>
) {
    val count: Int get() = items.size
}

data class DayReportHeldGroup(
    val packageName: String,
    val appName: String,
    val items: List<DayReportHeldItem>
) {
    val count: Int get() = items.size
}

data class DayReportUsageItem(
    val packageName: String,
    val appName: String,
    val durationSeconds: Long,
    val enterCount: Int
)

data class DayReportSnapshot(
    val dateMs: Long,
    val mindfulGroups: List<DayReportMindfulGroup>,
    val heldGroups: List<DayReportHeldGroup>,
    val usageItems: List<DayReportUsageItem>,
    val totalSeconds: Long
) {
    val mindfulCount: Int get() = mindfulGroups.sumOf { it.count }
    val heldCount: Int get() = heldGroups.sumOf { it.count }
}

fun computeDayReport(
    timeline: List<TimelineEvent>,
    summaries: List<AppUsageSummary>,
    monitoredApps: List<AppInfo>,
    dateMs: Long = System.currentTimeMillis()
): DayReportSnapshot {
    val usages = timeline
        .filterIsInstance<TimelineEvent.UsageEvent>()
        .filter { !it.isSeed }

    val monitoredOrder = monitoredApps.map { it.packageName }

    val mindfulFlat = usages
        .filter {
            !it.isGateQuit &&
                !it.purpose.isNullOrBlank() &&
                it.intentKind != IntentKind.PURPOSELESS
        }
        .map { e ->
            val compare = e.mindfulnessLevel
                ?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
                ?.let { UsageRecordEntity.MindfulnessLevel.tierLabel(it) }
            DayReportMindfulItem(
                recordId = e.recordId,
                packageName = e.packageName,
                appName = e.appName,
                startTime = e.startTime,
                intentText = e.intentLine ?: e.purpose!!.trim(),
                compareLabel = compare,
                note = e.note?.trim()?.takeIf { it.isNotEmpty() },
                durationSeconds = e.durationSeconds,
                isOngoing = e.isOngoing
            )
        }

    val heldFlat = usages
        .filter { it.isGateQuit }
        .map { e ->
            DayReportHeldItem(
                recordId = e.recordId,
                packageName = e.packageName,
                appName = e.appName,
                startTime = e.startTime,
                toOwnApp = e.isGateDismissToOwnApp
            )
        }

    val mindfulGroups = groupMindfulByApp(mindfulFlat, monitoredOrder)
    val heldGroups = groupHeldByApp(heldFlat, monitoredOrder)

    val entersByPkg = usages
        .filter { !it.isGateQuit && !it.isPositiveExit }
        .groupBy { it.packageName }

    val summaryMap = summaries.associateBy { it.packageName }
    val nameMap = monitoredApps.associate { it.packageName to it.appName } +
        usages.associate { it.packageName to it.appName }

    val usage = buildList {
        val seen = linkedSetOf<String>()
        for (app in monitoredApps) {
            seen += app.packageName
            val sec = summaryMap[app.packageName]?.todaySeconds
                ?: entersByPkg[app.packageName].orEmpty().sumOf { it.durationSeconds.coerceAtLeast(0L) }
            val enters = entersByPkg[app.packageName]?.size ?: 0
            if (sec > 0L || enters > 0) {
                add(
                    DayReportUsageItem(
                        packageName = app.packageName,
                        appName = app.appName,
                        durationSeconds = sec,
                        enterCount = enters
                    )
                )
            }
        }
        for ((pkg, list) in entersByPkg) {
            if (pkg in seen) continue
            val sec = summaryMap[pkg]?.todaySeconds
                ?: list.sumOf { it.durationSeconds.coerceAtLeast(0L) }
            add(
                DayReportUsageItem(
                    packageName = pkg,
                    appName = nameMap[pkg] ?: pkg,
                    durationSeconds = sec,
                    enterCount = list.size
                )
            )
        }
    }.sortedByDescending { it.durationSeconds }

    return DayReportSnapshot(
        dateMs = dateMs,
        mindfulGroups = mindfulGroups,
        heldGroups = heldGroups,
        usageItems = usage,
        totalSeconds = usage.sumOf { it.durationSeconds }
    )
}

private fun groupMindfulByApp(
    items: List<DayReportMindfulItem>,
    monitoredOrder: List<String>
): List<DayReportMindfulGroup> {
    if (items.isEmpty()) return emptyList()
    val byPkg = items.groupBy { it.packageName }
    return orderPackages(byPkg.keys, monitoredOrder, byPkg.mapValues { (_, v) ->
        v.maxOf { it.startTime }
    }).map { pkg ->
        val list = byPkg.getValue(pkg)
            .sortedWith(compareByDescending<DayReportMindfulItem> { it.startTime }.thenByDescending { it.recordId })
        DayReportMindfulGroup(
            packageName = pkg,
            appName = list.first().appName,
            items = list
        )
    }
}

private fun groupHeldByApp(
    items: List<DayReportHeldItem>,
    monitoredOrder: List<String>
): List<DayReportHeldGroup> {
    if (items.isEmpty()) return emptyList()
    val byPkg = items.groupBy { it.packageName }
    return orderPackages(byPkg.keys, monitoredOrder, byPkg.mapValues { (_, v) ->
        v.maxOf { it.startTime }
    }).map { pkg ->
        val list = byPkg.getValue(pkg)
            .sortedWith(compareByDescending<DayReportHeldItem> { it.startTime }.thenByDescending { it.recordId })
        DayReportHeldGroup(
            packageName = pkg,
            appName = list.first().appName,
            items = list
        )
    }
}

/**
 * 门口进入分型（现网：搜索 / 写下意图 / 随意浏览）。
 */
enum class DayEnterKind {
    SEARCH,
    WRITE,
    BROWSE;

    val label: String
        get() = when (this) {
            SEARCH -> "搜索"
            WRITE -> "写下意图"
            BROWSE -> BrowseCasualIntent.DISPLAY_LABEL
        }
}

fun classifyDayEnterKind(intentKind: IntentKind?, purpose: String?): DayEnterKind {
    if (intentKind == IntentKind.SEARCH) return DayEnterKind.SEARCH
    if (intentKind?.isUrgeNaming == true) return DayEnterKind.BROWSE
    if (BrowseCasualIntent.isBrowseLike(purpose.orEmpty())) return DayEnterKind.BROWSE
    return DayEnterKind.WRITE
}

/**
 * 日报统览：按时间正序的一条时间线（非按 App 聚合、非分 Tab）。
 */
sealed class DayReportTimelineEntry {
    abstract val recordId: Long
    abstract val timeMs: Long
    abstract val packageName: String
    abstract val appName: String

    /** 意图门拦住，未真正进入 */
    data class HeldBack(
        override val recordId: Long,
        override val timeMs: Long,
        override val packageName: String,
        override val appName: String,
        val toOwnApp: Boolean
    ) : DayReportTimelineEntry()

    /** 门口放行后的使用（搜索 / 写下意图 / 随意浏览） */
    data class MindfulUse(
        override val recordId: Long,
        override val timeMs: Long,
        override val packageName: String,
        override val appName: String,
        val intentText: String,
        val durationSeconds: Long,
        val compareLabel: String?,
        val note: String?,
        val isOngoing: Boolean,
        val enterKind: DayEnterKind = DayEnterKind.WRITE
    ) : DayReportTimelineEntry()

    /** 时段锁拦截（会话被踢或命中硬挡） */
    data class PeriodBlocked(
        override val recordId: Long,
        override val timeMs: Long,
        override val packageName: String,
        override val appName: String,
        val windowLabel: String
    ) : DayReportTimelineEntry()

    /** 时长锁拦截（日/周额度触顶，未实质进入或纯拦截） */
    data class TimeLockBlocked(
        override val recordId: Long,
        override val timeMs: Long,
        override val packageName: String,
        override val appName: String,
        val limitLabel: String
    ) : DayReportTimelineEntry()
}

data class DayReportTimeline(
    val dateMs: Long,
    val entries: List<DayReportTimelineEntry>,
    /** 窄镜：只看这个包；空 = 全日 */
    val filterPackageName: String = "",
    val filterAppName: String = ""
) {
    val isNarrow: Boolean get() = filterPackageName.isNotBlank()
    val heldCount: Int get() = entries.count { it is DayReportTimelineEntry.HeldBack }
    val mindfulCount: Int get() = entries.count { it is DayReportTimelineEntry.MindfulUse }
    /** 进入 = 搜索 + 写下 + 随意浏览 */
    val enterCount: Int get() = mindfulCount
    val searchCount: Int
        get() = entries.filterIsInstance<DayReportTimelineEntry.MindfulUse>()
            .count { it.enterKind == DayEnterKind.SEARCH }
    val writeCount: Int
        get() = entries.filterIsInstance<DayReportTimelineEntry.MindfulUse>()
            .count { it.enterKind == DayEnterKind.WRITE }
    val browseCount: Int
        get() = entries.filterIsInstance<DayReportTimelineEntry.MindfulUse>()
            .count { it.enterKind == DayEnterKind.BROWSE }
    val periodBlockCount: Int get() = entries.count { it is DayReportTimelineEntry.PeriodBlocked }
    val timeLockBlockCount: Int get() = entries.count { it is DayReportTimelineEntry.TimeLockBlocked }
    val mindfulTotalSeconds: Long
        get() = entries.filterIsInstance<DayReportTimelineEntry.MindfulUse>()
            .sumOf { it.durationSeconds.coerceAtLeast(0L) }
}

fun computeDayReportTimeline(
    timeline: List<TimelineEvent>,
    monitoredApps: List<AppInfo>,
    dateMs: Long = System.currentTimeMillis(),
    filterPackageName: String? = null
): DayReportTimeline {
    val windowsByPkg = monitoredApps.associate { app ->
        app.packageName to PeriodWindowsCodec.decode(app.periodWindowsJson)
    }
    val appByPkg = monitoredApps.associateBy { it.packageName }
    val pkgFilter = filterPackageName?.trim().orEmpty()

    val entries = timeline
        .filterIsInstance<TimelineEvent.UsageEvent>()
        .filter { !it.isSeed }
        .filter { pkgFilter.isEmpty() || it.packageName == pkgFilter }
        .mapNotNull { e ->
            when {
                // 正向出口本阶段不进收据（无「去做了」）
                e.isPositiveExit -> null
                e.isGateQuit -> DayReportTimelineEntry.HeldBack(
                    recordId = e.recordId,
                    timeMs = e.startTime,
                    packageName = e.packageName,
                    appName = e.appName,
                    toOwnApp = e.isGateDismissToOwnApp
                )
                e.endReason == UsageRecordEntity.EndReason.PERIOD_LOCK -> {
                    val windows = windowsByPkg[e.packageName].orEmpty()
                    val window = PeriodLockPolicy.activeWindow(windows, e.startTime)
                    DayReportTimelineEntry.PeriodBlocked(
                        recordId = e.recordId,
                        timeMs = e.startTime,
                        packageName = e.packageName,
                        appName = e.appName,
                        windowLabel = window?.label() ?: "时段锁"
                    )
                }
                isTimeLockIntercept(e) -> {
                    val app = appByPkg[e.packageName]
                    val limitLabel = timeLockLimitLabel(app)
                    DayReportTimelineEntry.TimeLockBlocked(
                        recordId = e.recordId,
                        timeMs = e.startTime,
                        packageName = e.packageName,
                        appName = e.appName,
                        limitLabel = limitLabel
                    )
                }
                e.hasIntentGate -> {
                    val compare = e.mindfulnessLevel
                        ?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
                        ?.let { UsageRecordEntity.MindfulnessLevel.tierLabel(it) }
                    val purpose = e.purpose?.trim().orEmpty()
                    val enterKind = classifyDayEnterKind(e.intentKind, purpose)
                    val intentText = when (enterKind) {
                        DayEnterKind.SEARCH ->
                            e.intentLine ?: purpose.ifBlank { "搜索" }
                        DayEnterKind.BROWSE ->
                            purpose.takeIf { it.isNotBlank() && !BrowseCasualIntent.isBrowseLike(it) }
                                ?: ""
                        DayEnterKind.WRITE ->
                            e.intentLine ?: purpose.ifBlank { "没有目的" }
                    }
                    DayReportTimelineEntry.MindfulUse(
                        recordId = e.recordId,
                        timeMs = e.startTime,
                        packageName = e.packageName,
                        appName = e.appName,
                        intentText = intentText,
                        durationSeconds = e.durationSeconds,
                        compareLabel = compare,
                        note = e.note?.trim()?.takeIf { it.isNotEmpty() },
                        isOngoing = e.isOngoing,
                        enterKind = enterKind
                    )
                }
                else -> null
            }
        }
        .sortedWith(compareBy<DayReportTimelineEntry> { it.timeMs }.thenBy { it.recordId })

    val filterAppName = when {
        pkgFilter.isEmpty() -> ""
        else -> entries.firstOrNull()?.appName
            ?: monitoredApps.firstOrNull { it.packageName == pkgFilter }?.appName
            ?: pkgFilter.substringAfterLast('.')
    }

    return DayReportTimeline(
        dateMs = dateMs,
        entries = entries,
        filterPackageName = pkgFilter,
        filterAppName = filterAppName
    )
}

/** 时长锁硬挡：触顶拦截，非「带意图实质使用」 */
private fun isTimeLockIntercept(e: TimelineEvent.UsageEvent): Boolean {
    if (e.endReason != UsageRecordEntity.EndReason.LIMIT_REACHED) return false
    val hasIntent = !e.purpose.isNullOrBlank() && e.intentKind != IntentKind.PURPOSELESS
    val hasRealUse = e.durationSeconds > 0L
    return !(hasIntent && hasRealUse && e.hasIntentGate)
}

private fun timeLockLimitLabel(app: AppInfo?): String {
    if (app == null || !app.timeLimitEnabled) return "额度用尽"
    val daily = app.effectiveDailyLimitMinutes()
    val weekly = app.effectiveWeeklyLimitMinutes()
    return when {
        daily > 0 && weekly > 0 -> "日 ${daily} 分 · 周 ${weekly / 60} 时"
        daily > 0 -> "每日 ${daily} 分"
        weekly > 0 -> "每周 ${weekly / 60} 时"
        else -> "额度用尽"
    }
}

/** 监控列表序优先；其余按组内最近一条时间倒序。 */
private fun orderPackages(
    packages: Set<String>,
    monitoredOrder: List<String>,
    latestByPkg: Map<String, Long>
): List<String> {
    val monitored = monitoredOrder.filter { it in packages }
    val rest = packages
        .filter { it !in monitored.toSet() }
        .sortedByDescending { latestByPkg[it] ?: 0L }
    return monitored + rest
}
