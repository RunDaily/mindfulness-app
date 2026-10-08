package com.life.mindfulnessapp.domain.model

/**
 * 日程锁判定：命中时段、包名锁定、计划块之间冲突。
 *
 * 与 App 级时段锁关系：取并集（任一命中即硬锁）；展示文案优先用计划标题。
 */
object PlanBlockPolicy {

    /**
     * 已开启的计划被 [updated] 做结构性改动（或删除）时，需过呼吸门槛。
     * [updated] 为 null 表示删除。仅改标题不算削弱。
     * 不要求「此刻落在窗内」——窗外改掉也是旁路。
     */
    fun requiresBreathToCommit(
        original: PlanBlock,
        updated: PlanBlock?,
        @Suppress("UNUSED_PARAMETER") nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!original.enabled) return false
        if (updated == null) return true
        return original.startMinute != updated.startMinute ||
            original.endMinute != updated.endMinute ||
            original.daysMask != updated.daysMask ||
            original.enabled != updated.enabled ||
            original.lockScope != updated.lockScope ||
            original.packageNames.toSet() != updated.packageNames.toSet()
    }

    /**
     * 取消监控前是否需过呼吸门槛：该 App 被任一**已开启**日程锁罩住，
     * 或已开 App 时段锁（有开启中的时段）。不要求此刻落在窗内。
     */
    fun needsBreathToStopMonitoring(
        plans: List<PlanBlock>,
        packageName: String,
        monitoredPackages: Set<String>,
        appPeriodLockEnabled: Boolean,
        appPeriodWindows: List<PeriodWindow>
    ): Boolean {
        if (packageName.isBlank()) return false
        val coveredByPlan = plans.any { plan ->
            plan.enabled && plan.coversPackage(packageName, monitoredPackages)
        }
        if (coveredByPlan) return true
        if (!appPeriodLockEnabled) return false
        return appPeriodWindows.any { it.enabled }
    }

    /** @deprecated 使用 [needsBreathToStopMonitoring]；保留旧名以免调用方编译失败 */
    fun isUnderActiveHardLock(
        plans: List<PlanBlock>,
        packageName: String,
        monitoredPackages: Set<String>,
        appPeriodLockEnabled: Boolean,
        appPeriodWindows: List<PeriodWindow>,
        @Suppress("UNUSED_PARAMETER") nowMillis: Long = System.currentTimeMillis()
    ): Boolean = needsBreathToStopMonitoring(
        plans = plans,
        packageName = packageName,
        monitoredPackages = monitoredPackages,
        appPeriodLockEnabled = appPeriodLockEnabled,
        appPeriodWindows = appPeriodWindows
    )

    /** 当前对 [packageName] 生效的计划块（若有多个，取列表中先命中的） */
    fun activeForPackage(
        plans: List<PlanBlock>,
        packageName: String,
        monitoredPackages: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis()
    ): PlanBlock? {
        if (packageName.isBlank() || plans.isEmpty()) return null
        for (plan in plans) {
            if (!plan.enabled) continue
            if (!plan.coversPackage(packageName, monitoredPackages)) continue
            if (PeriodLockPolicy.activeWindow(listOf(plan.toPeriodWindow()), nowMillis) != null) {
                return plan
            }
        }
        return null
    }

    /** 兼容旧调用：第三参为时间戳时按「无已监控名单」处理（仅命中指定 App 范围）。 */
    fun activeForPackage(
        plans: List<PlanBlock>,
        packageName: String,
        nowMillis: Long
    ): PlanBlock? = activeForPackage(plans, packageName, emptySet(), nowMillis)

    fun isPackageLockedNow(
        plans: List<PlanBlock>,
        packageName: String,
        monitoredPackages: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = activeForPackage(plans, packageName, monitoredPackages, nowMillis) != null

    fun isPackageLockedNow(
        plans: List<PlanBlock>,
        packageName: String,
        nowMillis: Long
    ): Boolean = isPackageLockedNow(plans, packageName, emptySet(), nowMillis)

    /**
     * 启用计划里应纳入监控集合的包名。
     * [MONITORED] 范围展开为当前 [monitoredPackages]。
     */
    fun watchedPackages(
        plans: List<PlanBlock>,
        monitoredPackages: Set<String> = emptySet()
    ): Set<String> =
        plans.asSequence()
            .filter { it.enabled }
            .flatMap { plan ->
                when (plan.lockScope) {
                    PlanLockScope.MONITORED -> monitoredPackages.asSequence()
                    PlanLockScope.SPECIFIC -> plan.packageNames.asSequence()
                    PlanLockScope.BOTH ->
                        monitoredPackages.asSequence() + plan.packageNames.asSequence()
                }
            }
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * [candidate] 与已有计划在时间上是否冲突（重复或重叠）。
     * 编辑时传 [excludeId] 忽略自身。
     */
    fun conflictWith(
        candidate: PlanBlock,
        existing: List<PlanBlock>,
        excludeId: String? = null
    ): PeriodWindowConflict? {
        val others = existing
            .filter { it.id != candidate.id && it.id != excludeId }
            .map { it.toPeriodWindow() }
        return PeriodLockPolicy.conflictWith(candidate.toPeriodWindow(), others, excludeId = null)
    }

    fun remainingUnlockLabel(
        plan: PlanBlock?,
        nowMillis: Long = System.currentTimeMillis()
    ): String = PeriodLockPolicy.remainingUnlockLabel(
        plan?.toPeriodWindow()?.takeIf { it.enabled },
        nowMillis
    )

    /** 当前时段正在生效的计划（有多个时取列表中先命中的）。 */
    fun activeNow(
        plans: List<PlanBlock>,
        nowMillis: Long = System.currentTimeMillis()
    ): PlanBlock? {
        if (plans.isEmpty()) return null
        val hit = PeriodLockPolicy.activeWindow(
            plans.map { it.toPeriodWindow() },
            nowMillis
        ) ?: return null
        return plans.firstOrNull { it.id == hit.id }
    }

    /** 下一即将开始的时段；已在生效中则返回当前段。小球 Hub 不再用预告，保留给别处查询。 */
    fun nextOrActive(
        plans: List<PlanBlock>,
        nowMillis: Long = System.currentTimeMillis()
    ): PlanBlock? {
        activeNow(plans, nowMillis)?.let { return it }
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val todayMask = PeriodDays.fromCalendar(cal.get(java.util.Calendar.DAY_OF_WEEK))
        return plans
            .asSequence()
            .filter { it.enabled }
            .filter { (it.daysMask and todayMask) != 0 }
            .filter { window ->
                val w = window.toPeriodWindow()
                !w.isAllDay && !w.crossesMidnight && window.startMinute > nowMin
            }
            .minByOrNull { it.startMinute }
    }
}
