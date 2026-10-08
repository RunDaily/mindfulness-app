package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import kotlin.math.abs

/**
 * 将全日系统前台段 + 监控 usage_records 合并为「使用日志」场景流（正序：早→晚）。
 *
 * 监控典型叙事：意图拦截 → 进入使用 → 跳到 → 回到 → 结束 → 回到桌面
 * 非监控：打开 / 回到桌面（屏保剔除；「跳到」目标不再重复「打开」）
 */
object UsageTransitionBuilder {

    data class SystemSegment(
        val packageName: String,
        val startMs: Long,
        val endMs: Long,
        val ongoing: Boolean = false
    )

    /** 回到桌面 / 锁屏不用，短于此时长不进日志 */
    private const val HOME_WORTH_MS = 3 * 60_000L

    /** 非监控同包连续「打开」合并窗口 */
    private const val OPEN_COLLAPSE_MS = 3 * 60_000L

    /**
     * 使用中「跳到 / 回到」最小离开时长。
     * 进程列表切换常抖出亚秒级前后台，短于此时长的片段忽略。
     */
    private const val MIN_AWAY_MS = 2_000L

    /** 「打开」与「跳到」视为同一动作的时间容差 */
    private const val JUMP_OPEN_ABSORB_MS = 5_000L

    /**
     * 同包同访次合并窗口：熄屏亮屏重展门 / 误开会话，不应拆成两次「意图拦截」「进入使用」。
     */
    private const val SAME_VISIT_COALESCE_MS = 5 * 60_000L

    fun build(
        segments: List<SystemSegment>,
        records: List<UsageRecordEntity>,
        monitoredPackages: Set<String>,
        launcherPackages: Set<String>,
        ownPackageName: String,
        resolveAppName: (String) -> String,
        dayEndMs: Long = System.currentTimeMillis(),
        /** 当前开着意图门的包；未开则不写「离开 / 门外离开 / 意图拦截」 */
        intentGatePackages: Set<String> = emptySet(),
        /** 时段锁拦住次行：按发生时刻解析命中窗口文案（如 `22:00 – 7:00`） */
        resolvePeriodLabel: (packageName: String, atMs: Long) -> String? = { _, _ -> null }
    ): List<UsageTransition> {
        val monitored = monitoredPackages.toSet()
        val launchers = launcherPackages.toSet()
        val jumpBackRows = buildJumpAndBack(
            segments = segments,
            records = records,
            monitored = monitored,
            launchers = launchers,
            ownPackageName = ownPackageName,
            resolveAppName = resolveAppName,
            dayEndMs = dayEndMs
        )
        val systemRows = buildSystemTransitions(
            segments = segments,
            monitored = monitored,
            launchers = launchers,
            ownPackageName = ownPackageName,
            resolveAppName = resolveAppName,
            absorbOpens = jumpBackRows,
            dayEndMs = dayEndMs
        )
        val recordRows = buildMonitoredRecordTransitions(
            records = records,
            resolveAppName = resolveAppName,
            intentGatePackages = intentGatePackages,
            resolvePeriodLabel = resolvePeriodLabel
        )
        return coalesceDesktopHomes(
            coalesceSameVisitNoise(
                (systemRows + recordRows + jumpBackRows)
                    .sortedWith(
                        compareBy({ it.timeMs }, { sceneOrder(it.scene) }, { it.packageName })
                    )
            )
        )
    }

    /** 监控访次摘要：去掉非监控「打开」、空小时与安静缝 */
    fun toMonitoredSummaryItems(
        transitions: List<UsageTransition>,
        endHourExclusive: Int = 24
    ): List<UsageLogListItem> {
        val kept = transitions.filter { it.scene != UsageTransitionScene.OPEN }
        return toListItems(kept, endHourExclusive).filter { item ->
            item !is UsageLogListItem.EmptyHour && item !is UsageLogListItem.QuietRange
        }
    }

    fun toListItems(
        transitions: List<UsageTransition>,
        endHourExclusive: Int = 24
    ): List<UsageLogListItem> {
        val end = endHourExclusive.coerceIn(1, 24)
        val byHour = Array(24) { ArrayList<UsageTransition>() }
        for (t in transitions) {
            val h = hourOf(t.timeMs)
            if (h in 0 until end) byHour[h] += t
        }

        val out = ArrayList<UsageLogListItem>(end + transitions.size + 8)
        var lastPeriod: String? = null
        var h = 0
        while (h < end) {
            val period = periodNameForHour(h)
            if (period != lastPeriod) {
                out += UsageLogListItem.PeriodHeader(
                    name = period,
                    range = periodRangeLabel(period)
                )
                lastPeriod = period
            }

            if (byHour[h].isEmpty()) {
                var endEmpty = h
                while (
                    endEmpty + 1 < end &&
                    byHour[endEmpty + 1].isEmpty() &&
                    periodNameForHour(endEmpty + 1) == period
                ) {
                    endEmpty++
                }
                if (endEmpty > h) {
                    out += UsageLogListItem.QuietRange(fromHour = h, toHour = endEmpty)
                } else {
                    out += UsageLogListItem.EmptyHour(hour = h)
                }
                h = endEmpty + 1
            } else {
                val rows = byHour[h]
                var lastMinute = -1
                rows.forEachIndexed { index, t ->
                    val minute = minuteOf(t.timeMs)
                    out += UsageLogListItem.Row(
                        transition = t,
                        isHourFirst = index == 0,
                        isMinuteFirst = minute != lastMinute
                    )
                    lastMinute = minute
                }
                h++
            }
        }
        return out
    }

    fun groupByPeriod(items: List<UsageLogListItem>): List<UsageLogPeriodGroup> {
        if (items.isEmpty()) return emptyList()
        val groups = ArrayList<UsageLogPeriodGroup>(4)
        var header: UsageLogListItem.PeriodHeader? = null
        var rows = ArrayList<UsageLogListItem>(16)
        fun flush() {
            val h = header ?: return
            val builtRows = rows.toList()
            groups += UsageLogPeriodGroup(
                header = h,
                rows = builtRows,
                hourChips = buildHourChips(h.name, builtRows)
            )
            rows = ArrayList(16)
        }
        for (item in items) {
            when (item) {
                is UsageLogListItem.PeriodHeader -> {
                    flush()
                    header = item
                }
                else -> rows += item
            }
        }
        flush()
        return groups
    }

    private fun buildHourChips(
        periodName: String,
        rows: List<UsageLogListItem>
    ): List<UsageLogHourChip> {
        val range = periodHourRange(periodName)
        val presentHours = linkedSetOf<Int>()
        for (item in rows) {
            when (item) {
                is UsageLogListItem.Row -> presentHours += item.hour
                is UsageLogListItem.EmptyHour -> presentHours += item.hour
                is UsageLogListItem.QuietRange -> {
                    for (h in item.fromHour..item.toHour) presentHours += h
                }
                else -> Unit
            }
        }
        if (presentHours.isEmpty()) return emptyList()
        val minH = presentHours.minOrNull()!!.coerceAtLeast(range.first)
        val maxH = presentHours.maxOrNull()!!.coerceAtMost(range.last)
        return (minH..maxH).map { hour ->
            val anchor = anchorKeyForHour(rows, hour)
            val hasContent = rows.any { item ->
                item is UsageLogListItem.Row && item.hour == hour
            }
            UsageLogHourChip(
                hour = hour,
                hasContent = hasContent,
                anchorKey = anchor ?: "hour_missing_$hour"
            )
        }
    }

    private fun anchorKeyForHour(rows: List<UsageLogListItem>, hour: Int): String? {
        for (item in rows) {
            when (item) {
                is UsageLogListItem.Row -> if (item.hour == hour) return item.key
                is UsageLogListItem.EmptyHour -> if (item.hour == hour) return item.key
                is UsageLogListItem.QuietRange ->
                    if (hour in item.fromHour..item.toHour) return item.key
                else -> Unit
            }
        }
        return null
    }

    fun periodHourRange(periodName: String): IntRange = when (periodName) {
        "凌晨" -> 0..5
        "上午" -> 6..11
        "下午" -> 12..17
        else -> 18..23
    }

    // ── 非监控：打开 / 回到桌面 ────────────────────────────────────────

    private fun buildSystemTransitions(
        segments: List<SystemSegment>,
        monitored: Set<String>,
        launchers: Set<String>,
        ownPackageName: String,
        resolveAppName: (String) -> String,
        absorbOpens: List<UsageTransition>,
        dayEndMs: Long
    ): List<UsageTransition> {
        if (segments.isEmpty()) return emptyList()
        val jumpPkgs = absorbOpens
            .filter { it.scene == UsageTransitionScene.JUMP }
            .map { it.packageName to it.timeMs }
        val sorted = segments.sortedBy { it.startMs }
        val out = ArrayList<UsageTransition>(sorted.size)
        out += buildDesktopPauses(
            sorted = sorted,
            launchers = launchers,
            ownPackageName = ownPackageName,
            resolveAppName = resolveAppName,
            dayEndMs = dayEndMs
        )
        var lastPkg: String? = null

        for (seg in sorted) {
            val pkg = seg.packageName
            if (pkg == ownPackageName) {
                lastPkg = pkg
                continue
            }
            if (pkg in launchers) {
                lastPkg = pkg
                continue
            }

            val resolvedName = resolveAppName(pkg)
            if (UsageLogDisplayNames.isNoiseSystemSurface(pkg, resolvedName)) {
                lastPkg = pkg
                continue
            }

            if (pkg in monitored) {
                lastPkg = pkg
                continue
            }

            // 已由「跳到」叙述的目标，不再写「打开」
            val absorbed = jumpPkgs.any { (jumpPkg, jumpAt) ->
                jumpPkg == pkg && abs(jumpAt - seg.startMs) <= JUMP_OPEN_ABSORB_MS
            }
            if (absorbed) {
                lastPkg = pkg
                continue
            }

            val last = out.lastOrNull()
            if (last != null &&
                last.scene == UsageTransitionScene.OPEN &&
                last.packageName == pkg &&
                seg.startMs - last.timeMs <= OPEN_COLLAPSE_MS
            ) {
                out[out.lastIndex] = last.copy(repeatCount = last.repeatCount + 1)
            } else {
                out += UsageTransition(
                    scene = UsageTransitionScene.OPEN,
                    timeMs = seg.startMs,
                    packageName = pkg,
                    appName = resolvedName,
                    monitored = false
                )
            }
            lastPkg = pkg
        }
        return out
    }

    /**
     * 回到桌面只在「真的离开了一会儿」时出现：
     * 桌面停留，或两段使用之间的空档（锁屏、未使用）达到 [HOME_WORTH_MS]。
     */
    private fun buildDesktopPauses(
        sorted: List<SystemSegment>,
        launchers: Set<String>,
        ownPackageName: String,
        resolveAppName: (String) -> String,
        dayEndMs: Long
    ): List<UsageTransition> {
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<UsageTransition>()
        var i = 0
        while (i < sorted.size) {
            val seg = sorted[i]
            if (isSkippableNoise(seg, resolveAppName)) {
                i++
                continue
            }
            if (seg.packageName in launchers &&
                !previousMeaningfulIsLauncher(sorted, i, launchers, resolveAppName)
            ) {
                val span = desktopIdleSpan(
                    sorted = sorted,
                    startIndex = i,
                    launchers = launchers,
                    resolveAppName = resolveAppName,
                    dayEndMs = dayEndMs
                )
                if (span.durationMs >= HOME_WORTH_MS) {
                    out += desktopPause(seg.packageName, seg.startMs, span)
                }
                i++
                continue
            }
            if (seg.packageName !in launchers && seg.packageName != ownPackageName) {
                val prevEnd = previousMeaningfulEnd(sorted, i, launchers, resolveAppName)
                if (prevEnd != null && seg.startMs - prevEnd >= HOME_WORTH_MS) {
                    val gapMs = seg.startMs - prevEnd
                    out += desktopPause(
                        packageName = "",
                        timeMs = prevEnd,
                        span = IdleSpan(durationMs = gapMs, launcherMs = 0L)
                    )
                }
            }
            i++
        }
        val tail = sorted.lastOrNull { !isSkippableNoise(it, resolveAppName) }
        if (tail != null &&
            tail.packageName !in launchers &&
            !tail.ongoing &&
            dayEndMs - tail.endMs >= HOME_WORTH_MS
        ) {
            val already = out.any {
                it.scene == UsageTransitionScene.HOME &&
                    it.timeMs >= tail.endMs - 1_000L
            }
            if (!already) {
                out += desktopPause(
                    packageName = "",
                    timeMs = tail.endMs,
                    span = IdleSpan(durationMs = dayEndMs - tail.endMs, launcherMs = 0L)
                )
            }
        }
        return out
    }

    private data class IdleSpan(val durationMs: Long, val launcherMs: Long)

    private fun desktopPause(
        packageName: String,
        timeMs: Long,
        span: IdleSpan
    ): UsageTransition {
        val gapMs = (span.durationMs - span.launcherMs).coerceAtLeast(0L)
        val duration = formatSessionDuration(span.durationMs / 1000L)
        val secondary = if (gapMs >= HOME_WORTH_MS && span.launcherMs < 60_000L) {
            "锁屏 $duration"
        } else {
            duration
        }
        return UsageTransition(
            scene = UsageTransitionScene.HOME,
            timeMs = timeMs,
            packageName = packageName.ifBlank { "home" },
            appName = UsageLogDisplayNames.HOME,
            secondary = secondary,
            monitored = false,
            spineKey = "home"
        )
    }

    private fun desktopIdleSpan(
        sorted: List<SystemSegment>,
        startIndex: Int,
        launchers: Set<String>,
        resolveAppName: (String) -> String,
        dayEndMs: Long
    ): IdleSpan {
        val start = sorted[startIndex].startMs
        var launcherMs = 0L
        var j = startIndex
        while (j < sorted.size) {
            val seg = sorted[j]
            if (isSkippableNoise(seg, resolveAppName)) {
                j++
                continue
            }
            if (seg.packageName in launchers) {
                launcherMs += (seg.endMs - seg.startMs).coerceAtLeast(0L)
                j++
                continue
            }
            if (j == startIndex) break
            return IdleSpan(durationMs = seg.startMs - start, launcherMs = launcherMs)
        }
        return IdleSpan(
            durationMs = (dayEndMs - start).coerceAtLeast(0L),
            launcherMs = launcherMs
        )
    }

    private fun previousMeaningfulIsLauncher(
        sorted: List<SystemSegment>,
        index: Int,
        launchers: Set<String>,
        resolveAppName: (String) -> String
    ): Boolean {
        var j = index - 1
        while (j >= 0) {
            val seg = sorted[j]
            if (isSkippableNoise(seg, resolveAppName)) {
                j--
                continue
            }
            return seg.packageName in launchers
        }
        return false
    }

    private fun previousMeaningfulEnd(
        sorted: List<SystemSegment>,
        index: Int,
        launchers: Set<String>,
        resolveAppName: (String) -> String
    ): Long? {
        var j = index - 1
        while (j >= 0) {
            val seg = sorted[j]
            if (isSkippableNoise(seg, resolveAppName)) {
                j--
                continue
            }
            if (seg.packageName in launchers) return null
            return seg.endMs
        }
        return null
    }

    private fun isSkippableNoise(
        seg: SystemSegment,
        resolveAppName: (String) -> String
    ): Boolean = UsageLogDisplayNames.isNoiseSystemSurface(seg.packageName, resolveAppName(seg.packageName))

    // ── 监控：来自 usage_records ───────────────────────────────────────

    private fun buildMonitoredRecordTransitions(
        records: List<UsageRecordEntity>,
        resolveAppName: (String) -> String,
        intentGatePackages: Set<String>,
        resolvePeriodLabel: (packageName: String, atMs: Long) -> String?
    ): List<UsageTransition> {
        if (records.isEmpty()) return emptyList()
        val gatedPackages = intentGatePackages.toSet()
        val out = ArrayList<UsageTransition>(records.size * 3)
        val sorted = records.sortedWith(compareBy({ it.startTime }, { it.id }))

        for (record in sorted) {
            if (UsageRecordEntity.EndReason.isSeed(record.endReason)) continue
            val appName = resolveAppName(record.packageName)
            val pkg = record.packageName
            val dwellMs = record.gateDwellMs.coerceAtLeast(0L)
            val gated = pkg in gatedPackages
            val periodAtStart = resolvePeriodLabel(pkg, record.startTime)

            when {
                isBarePeriodLock(record, periodAtStart) -> {
                    out += periodBlockTransition(
                        record = record,
                        pkg = pkg,
                        appName = appName,
                        timeMs = record.startTime,
                        periodLabel = periodAtStart
                            ?: record.endTime.takeIf { it > 0L }?.let { resolvePeriodLabel(pkg, it) }
                    )
                }
                record.isGateQuit -> {
                    if (!gated) continue
                    appendGateAppear(out, record, pkg, appName, dwellMs, force = true)
                    out += UsageTransition(
                        scene = UsageTransitionScene.GATE_LEAVE,
                        timeMs = record.startTime,
                        packageName = pkg,
                        appName = appName,
                        secondary = gateLeaveSecondary(record.endReason),
                        monitored = true,
                        recordId = record.id
                    )
                }
                record.isPositiveExit -> {
                    if (!gated) continue
                    appendGateAppear(out, record, pkg, appName, dwellMs, force = true)
                    val title = record.purpose?.trim().orEmpty().ifEmpty { "去做了" }
                    out += UsageTransition(
                        scene = UsageTransitionScene.GATE_LEAVE,
                        timeMs = record.startTime,
                        packageName = pkg,
                        appName = appName,
                        secondary = "「$title」",
                        monitored = true,
                        recordId = record.id
                    )
                }
                else -> {
                    val purpose = record.purpose?.trim().orEmpty()
                    val hasIntent = purpose.isNotEmpty() &&
                        record.intentKind != IntentKind.PURPOSELESS.name
                    // 意图门放行才写「意图拦截」+「进入使用」；直进、纯时段锁不写
                    val throughIntentGate = gated && (hasIntent || dwellMs > 0L)
                    appendGateAppear(
                        out, record, pkg, appName, dwellMs,
                        force = throughIntentGate
                    )
                    if (throughIntentGate) {
                        out += UsageTransition(
                            scene = UsageTransitionScene.ENTER,
                            timeMs = record.startTime,
                            packageName = pkg,
                            appName = appName,
                            secondary = buildEnterSecondary(hasIntent, purpose, dwellMs, record),
                            monitored = true,
                            recordId = record.id
                        )
                    }

                    val endTime = record.endTime
                    if (endTime > 0L) {
                        when (val endScene = endSceneFor(record.endReason)) {
                            null -> Unit
                            UsageTransitionScene.LEAVE -> {
                                if (!gated) Unit else out += UsageTransition(
                                    scene = endScene,
                                    timeMs = endTime,
                                    packageName = pkg,
                                    appName = appName,
                                    secondary = endSecondary(endScene, record),
                                    monitored = true,
                                    recordId = record.id
                                )
                            }
                            else -> out += UsageTransition(
                                scene = endScene,
                                timeMs = endTime,
                                packageName = pkg,
                                appName = appName,
                                secondary = endSecondary(
                                    endScene, record,
                                    periodLabel = resolvePeriodLabel(pkg, endTime)
                                ),
                                monitored = true,
                                recordId = record.id
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * 时段锁硬挡本身：未真正进入就离开。
     * 离开按钮曾误记成意图门离开；窗口命中时也按时段锁展示。
     */
    private fun isBarePeriodLock(
        record: UsageRecordEntity,
        periodLabelAtStart: String?
    ): Boolean {
        if (record.isPositiveExit) return false
        val unused = record.purpose.isNullOrBlank() && record.durationSeconds <= 0L
        if (record.endReason == UsageRecordEntity.EndReason.PERIOD_LOCK && unused) return true
        return record.isGateQuit && periodLabelAtStart != null
    }

    private fun periodBlockTransition(
        record: UsageRecordEntity,
        pkg: String,
        appName: String,
        timeMs: Long,
        periodLabel: String?
    ): UsageTransition = UsageTransition(
        scene = UsageTransitionScene.PERIOD_BLOCK,
        timeMs = timeMs,
        packageName = pkg,
        appName = appName,
        secondary = periodLabel,
        monitored = true,
        recordId = record.id
    )

    private fun buildEnterSecondary(
        hasIntent: Boolean,
        purpose: String,
        dwellMs: Long,
        record: UsageRecordEntity
    ): String? {
        val intentPart = when {
            hasIntent -> "「$purpose」"
            dwellMs > 0L -> "没有目的"
            else -> null
        }
        val durationPart = if (
            hasIntent &&
            record.endTime > 0L &&
            record.durationSeconds > 0L
        ) {
            formatSessionDuration(record.durationSeconds)
        } else {
            null
        }
        return when {
            intentPart != null && durationPart != null -> "$intentPart · $durationPart"
            intentPart != null -> intentPart
            else -> null
        }
    }

    private fun appendGateAppear(
        out: MutableList<UsageTransition>,
        record: UsageRecordEntity,
        pkg: String,
        appName: String,
        dwellMs: Long,
        force: Boolean
    ) {
        if (!force && dwellMs <= 0L) return
        val holdStart = if (dwellMs > 0L) {
            (record.startTime - dwellMs).coerceAtLeast(0L)
        } else {
            record.startTime
        }
        out += UsageTransition(
            scene = UsageTransitionScene.GATE_HOLD,
            timeMs = holdStart,
            packageName = pkg,
            appName = appName,
            monitored = true,
            recordId = record.id
        )
    }

    /**
     * 使用中跳到非监控 App →「跳到」；点暂停胶囊回到本包 →「回到」。
     * 离开时长不足 [MIN_AWAY_MS] 的抖动片段忽略。
     * 回桌面只靠系统「回到桌面」节点，不在此写离开。
     */
    private fun buildJumpAndBack(
        segments: List<SystemSegment>,
        records: List<UsageRecordEntity>,
        monitored: Set<String>,
        launchers: Set<String>,
        ownPackageName: String,
        resolveAppName: (String) -> String,
        dayEndMs: Long
    ): List<UsageTransition> {
        val sessions = records
            .asSequence()
            .filterNot { UsageRecordEntity.EndReason.isSeed(it.endReason) }
            .filterNot { it.isGateQuit || it.isPositiveExit }
            .map { record ->
                val end = when {
                    record.endTime > 0L -> record.endTime
                    else -> dayEndMs
                }
                OpenMonitoredSession(record = record, endMs = end)
            }
            .sortedBy { it.record.startTime }
            .toList()
        if (sessions.isEmpty() || segments.isEmpty()) return emptyList()

        val out = ArrayList<UsageTransition>()
        val sortedSegs = segments.sortedBy { it.startMs }
        var lastPkg: String? = null
        var away: AwayEpisode? = null

        fun commitAwayIfSolid(endMs: Long, returned: Boolean) {
            val ep = away ?: return
            away = null
            if (ep.awayIsLauncher) return
            if (endMs - ep.startMs < MIN_AWAY_MS) return
            val destName = resolveAppName(ep.awayPackageName)
            out += UsageTransition(
                scene = UsageTransitionScene.JUMP,
                timeMs = ep.startMs,
                packageName = ep.awayPackageName,
                appName = destName,
                monitored = true,
                recordId = ep.recordId,
                spineKey = ep.monitoredPackage
            )
            if (returned) {
                out += UsageTransition(
                    scene = UsageTransitionScene.BACK,
                    timeMs = endMs,
                    packageName = ep.monitoredPackage,
                    appName = resolveAppName(ep.monitoredPackage),
                    monitored = true,
                    recordId = ep.recordId
                )
            }
        }

        for (seg in sortedSegs) {
            val pkg = seg.packageName
            val t = seg.startMs

            if (pkg == ownPackageName ||
                UsageLogDisplayNames.isNoiseSystemSurface(pkg, resolveAppName(pkg))
            ) {
                lastPkg = pkg
                continue
            }

            val sessionHere = sessions.lastOrNull { s ->
                t in s.record.startTime until s.endMs && s.record.packageName == pkg
            }

            when {
                pkg in launchers -> {
                    val active = sessions.lastOrNull { s ->
                        lastPkg == s.record.packageName &&
                            t in s.record.startTime until s.endMs
                    }
                    if (active != null && away?.recordId != active.record.id) {
                        // 使用中回桌面：不写「跳到」，留给绿色「回到桌面」
                        away = AwayEpisode(
                            recordId = active.record.id,
                            monitoredPackage = active.record.packageName,
                            awayPackageName = pkg,
                            awayIsLauncher = true,
                            startMs = t
                        )
                    }
                    lastPkg = pkg
                }
                pkg in monitored -> {
                    if (sessionHere != null && away != null &&
                        away!!.recordId == sessionHere.record.id &&
                        away!!.monitoredPackage == pkg
                    ) {
                        commitAwayIfSolid(endMs = t, returned = !away!!.awayIsLauncher)
                    } else if (sessionHere != null && away?.recordId == sessionHere.record.id) {
                        away = null
                    }
                    lastPkg = pkg
                }
                else -> {
                    val active = sessions.lastOrNull { s ->
                        lastPkg == s.record.packageName &&
                            t in s.record.startTime until s.endMs
                    }
                    if (active != null && away?.recordId != active.record.id) {
                        away = AwayEpisode(
                            recordId = active.record.id,
                            monitoredPackage = active.record.packageName,
                            awayPackageName = pkg,
                            awayIsLauncher = false,
                            startMs = t
                        )
                    } else if (away != null &&
                        !away!!.awayIsLauncher &&
                        pkg !in monitored
                    ) {
                        // 暂停期间换非监控目标：仍记首次跳到的 App
                        Unit
                    }
                    lastPkg = pkg
                }
            }
        }

        // 会话结束仍停在他 App：只落「跳到」，不落「回到」
        away?.let { ep ->
            val session = sessions.firstOrNull { it.record.id == ep.recordId }
            val endMs = session?.endMs ?: dayEndMs
            commitAwayIfSolid(endMs = endMs, returned = false)
        }

        return out
    }

    private data class OpenMonitoredSession(
        val record: UsageRecordEntity,
        val endMs: Long
    )

    private data class AwayEpisode(
        val recordId: Long,
        val monitoredPackage: String,
        val awayPackageName: String,
        val awayIsLauncher: Boolean,
        val startMs: Long
    )

    private fun endSceneFor(endReason: String): UsageTransitionScene? = when (endReason) {
        UsageRecordEntity.EndReason.PERIOD_LOCK ->
            UsageTransitionScene.PERIOD_BLOCK
        UsageRecordEntity.EndReason.LIMIT_REACHED,
        UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED ->
            UsageTransitionScene.LIMIT
        UsageRecordEntity.EndReason.MANUAL ->
            UsageTransitionScene.END
        UsageRecordEntity.EndReason.SWITCHED_AWAY,
        UsageRecordEntity.EndReason.GATE_REENTER ->
            UsageTransitionScene.LEAVE
        else -> {
            if (isSoftAwayEnd(endReason)) null else UsageTransitionScene.LEAVE
        }
    }

    private fun isSoftAwayEnd(endReason: String): Boolean = when (endReason) {
        UsageRecordEntity.EndReason.AWAY_COUNTDOWN,
        UsageRecordEntity.EndReason.BACKGROUND_TIMEOUT,
        UsageRecordEntity.EndReason.AUTO_TIMEOUT,
        UsageRecordEntity.EndReason.SCREEN_OFF_TIMEOUT -> true
        else -> false
    }

    private fun endSecondary(
        scene: UsageTransitionScene,
        record: UsageRecordEntity,
        periodLabel: String? = null
    ): String? = when (scene) {
        UsageTransitionScene.LIMIT -> when (record.endReason) {
            UsageRecordEntity.EndReason.SESSION_LIMIT_REACHED -> "本次到点"
            else -> "日限额"
        }
        UsageTransitionScene.PERIOD_BLOCK -> periodLabel
        UsageTransitionScene.END -> null
        UsageTransitionScene.LEAVE, UsageTransitionScene.GATE_LEAVE -> when (record.endReason) {
            UsageRecordEntity.EndReason.SWITCHED_AWAY -> "切到其他监控"
            UsageRecordEntity.EndReason.GATE_REENTER -> "再确认意图"
            else -> softLeaveSecondary(record.endReason)
        }
        else -> null
    }

    private fun gateLeaveSecondary(endReason: String): String? = when (endReason) {
        // 被动离开：门外锁屏等，有「意图拦截→门外离开」已够，不再旁注
        UsageRecordEntity.EndReason.GATE_DISMISS_OWN_APP -> "到了心锚"
        else -> null
    }

    private fun softLeaveSecondary(endReason: String): String? {
        val raw = UsageRecordEntity.EndReason.softEndReasonLabel(endReason) ?: return null
        return when (raw) {
            "未正常结束" -> "未完结"
            "切换应用结束" -> "切到别处"
            else -> raw
        }
    }

    private fun formatSessionDuration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        if (s < 60L) return "${s}秒"
        val minutes = s / 60L
        if (minutes < 60L) return "${minutes}分钟"
        val hours = minutes / 60L
        val rem = minutes % 60L
        return if (rem == 0L) "${hours}小时" else "${hours}小时${rem}分"
    }

    /**
     * 已在桌面就不再重复「回到桌面」。
     * 只有真正打开了别的东西（打开 / 进入）才离开桌面态。
     */
    private fun coalesceDesktopHomes(
        transitions: List<UsageTransition>
    ): List<UsageTransition> {
        if (transitions.isEmpty()) return transitions
        val out = ArrayList<UsageTransition>(transitions.size)
        var onDesktop = false
        for (t in transitions) {
            when (t.scene) {
                UsageTransitionScene.HOME -> {
                    if (onDesktop) continue
                    out += t
                    onDesktop = true
                }
                UsageTransitionScene.OPEN,
                UsageTransitionScene.ENTER -> {
                    out += t
                    onDesktop = false
                }
                else -> out += t
            }
        }
        return out
    }

    /**
     * 同访次去噪：熄屏亮屏重展门 / 误双开会话，合并为一次「意图拦截」+ 一次「进入使用」。
     * 中间允许夹杂回到桌面；若已有离开/结束/跳到/回到则视为新访次。
     */
    private fun coalesceSameVisitNoise(
        transitions: List<UsageTransition>
    ): List<UsageTransition> {
        if (transitions.isEmpty()) return transitions
        val out = ArrayList<UsageTransition>(transitions.size)
        for (t in transitions) {
            when (t.scene) {
                UsageTransitionScene.GATE_HOLD -> {
                    val prevIdx = out.indexOfLast {
                        it.packageName == t.packageName &&
                            it.scene == UsageTransitionScene.GATE_HOLD
                    }
                    if (prevIdx >= 0 &&
                        t.timeMs - out[prevIdx].timeMs <= SAME_VISIT_COALESCE_MS &&
                        !visitBoundaryBetween(out, prevIdx, t.packageName)
                    ) {
                        continue
                    }
                    out += t
                }
                UsageTransitionScene.ENTER -> {
                    val prevIdx = out.indexOfLast {
                        it.packageName == t.packageName &&
                            it.scene == UsageTransitionScene.ENTER
                    }
                    if (prevIdx >= 0 &&
                        t.timeMs - out[prevIdx].timeMs <= SAME_VISIT_COALESCE_MS &&
                        !visitBoundaryBetween(out, prevIdx, t.packageName)
                    ) {
                        out[prevIdx] = preferEnter(out[prevIdx], t)
                        continue
                    }
                    out += t
                }
                else -> out += t
            }
        }
        return out
    }

    /** 两次节点之间是否已切断访次（同包离开/结束/跳到/回到，或跳到/打开了别处） */
    private fun visitBoundaryBetween(
        out: List<UsageTransition>,
        fromIdx: Int,
        packageName: String
    ): Boolean {
        for (i in (fromIdx + 1) until out.size) {
            val x = out[i]
            if (x.packageName == packageName) {
                when (x.scene) {
                    UsageTransitionScene.LEAVE,
                    UsageTransitionScene.GATE_LEAVE,
                    UsageTransitionScene.END,
                    UsageTransitionScene.LIMIT,
                    UsageTransitionScene.PERIOD_BLOCK,
                    UsageTransitionScene.JUMP,
                    UsageTransitionScene.BACK -> return true
                    else -> Unit
                }
            } else {
                when (x.scene) {
                    UsageTransitionScene.OPEN,
                    UsageTransitionScene.JUMP,
                    UsageTransitionScene.ENTER -> return true
                    else -> Unit
                }
            }
        }
        return false
    }

    /** 保留更早时间，优先带「意图」与时长的那条 */
    private fun preferEnter(a: UsageTransition, b: UsageTransition): UsageTransition {
        val better = if (enterQuality(b) > enterQuality(a)) b else a
        val other = if (better === b) a else b
        return better.copy(
            timeMs = minOf(a.timeMs, b.timeMs),
            secondary = better.secondary ?: other.secondary,
            recordId = better.recordId ?: other.recordId
        )
    }

    private fun enterQuality(t: UsageTransition): Int {
        val s = t.secondary?.trim().orEmpty()
        if (s.startsWith("「")) return 2
        if (s.isNotEmpty()) return 1
        return 0
    }

    private fun sceneOrder(scene: UsageTransitionScene): Int = when (scene) {
        UsageTransitionScene.GATE_HOLD -> 0
        UsageTransitionScene.ENTER, UsageTransitionScene.BACK,
        UsageTransitionScene.JUMP, UsageTransitionScene.OPEN -> 1
        UsageTransitionScene.LEAVE, UsageTransitionScene.GATE_LEAVE,
        UsageTransitionScene.END,
        UsageTransitionScene.LIMIT, UsageTransitionScene.PERIOD_BLOCK -> 2
        UsageTransitionScene.HOME -> 3
    }

    private fun hourOf(timeMs: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }

    private fun minuteOf(timeMs: Long): Int {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
        return cal.get(java.util.Calendar.MINUTE)
    }

    private fun periodNameForHour(hour: Int): String = when (hour) {
        in 0 until 6 -> "凌晨"
        in 6 until 12 -> "上午"
        in 12 until 18 -> "下午"
        else -> "晚上"
    }

    private fun periodRangeLabel(name: String): String = when (name) {
        "凌晨" -> "0:00–6:00"
        "上午" -> "6:00–12:00"
        "下午" -> "12:00–18:00"
        else -> "18:00–24:00"
    }
}

data class UsageLogPeriodGroup(
    val header: UsageLogListItem.PeriodHeader,
    val rows: List<UsageLogListItem>,
    val hourChips: List<UsageLogHourChip> = emptyList()
)

data class UsageLogHourChip(
    val hour: Int,
    val hasContent: Boolean,
    val anchorKey: String
)
