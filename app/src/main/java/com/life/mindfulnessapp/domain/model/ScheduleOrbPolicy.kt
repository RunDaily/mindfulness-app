package com.life.mindfulnessapp.domain.model

/**
 * 桌面心锚 × 日程锁：只在**生效中**露一眼，不做「下一锁」预告 / 到场岛。
 * 锁靠硬门执行；小球解释当下——段名、时段、锁了谁——不预报未来。
 */
object ScheduleOrbPolicy {

    data class LockedApp(
        val packageName: String,
        val appName: String,
    )

    data class ActiveGlance(
        val planId: String,
        val title: String,
        val startMinute: Int,
        val endMinute: Int,
        val daysMask: Int = PeriodDays.EVERY_DAY,
        /** 如「09:00 – 12:00」或「全天」 */
        val timeLabel: String,
        /** 此刻被这段锁住的 App（已解析显示名，最多 [MAX_SHOWN_APPS]） */
        val lockedApps: List<LockedApp> = emptyList(),
        /** 超出展示上限的数量 */
        val lockedAppOverflow: Int = 0,
        /**
         * 名单为空时的一句说明，如「监控中的 App」。
         * 有具体 App 时为 null，由图标/名承担。
         */
        val emptyScopeHint: String? = null,
    ) {
        fun toPeriodWindow(): PeriodWindow = PeriodWindow(
            id = planId,
            startMinute = startMinute,
            endMinute = endMinute,
            daysMask = daysMask,
            enabled = true,
            message = title
        )
    }

    const val MAX_SHOWN_APPS = 4

    fun activeGlance(
        plans: List<PlanBlock>,
        monitoredPackages: Set<String> = emptySet(),
        appLabel: (String) -> String = { it },
        nowMillis: Long = System.currentTimeMillis(),
    ): ActiveGlance? {
        val plan = PlanBlockPolicy.activeNow(plans, nowMillis) ?: return null
        val window = plan.toPeriodWindow()
        val pkgs = lockedPackages(plan, monitoredPackages)
        val shown = pkgs.take(MAX_SHOWN_APPS).map { pkg ->
            LockedApp(
                packageName = pkg,
                appName = appLabel(pkg).ifBlank { pkg },
            )
        }
        val overflow = (pkgs.size - shown.size).coerceAtLeast(0)
        val emptyHint = when {
            pkgs.isNotEmpty() -> null
            plan.lockScope == PlanLockScope.MONITORED -> "监控中的 App"
            plan.lockScope == PlanLockScope.BOTH -> "监控中的 App"
            else -> "自定义名单为空"
        }
        return ActiveGlance(
            planId = plan.id,
            title = plan.title.trim().ifEmpty { plan.label() },
            startMinute = plan.startMinute,
            endMinute = plan.endMinute,
            daysMask = plan.daysMask,
            timeLabel = window.label(),
            lockedApps = shown,
            lockedAppOverflow = overflow,
            emptyScopeHint = emptyHint,
        )
    }

    fun lockedPackages(plan: PlanBlock, monitoredPackages: Set<String>): List<String> {
        val specific = plan.packageNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val monitored = monitoredPackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return when (plan.lockScope) {
            PlanLockScope.MONITORED -> monitored.sorted()
            PlanLockScope.SPECIFIC -> specific
            PlanLockScope.BOTH -> (monitored + specific).distinct().sorted()
        }
    }
}
