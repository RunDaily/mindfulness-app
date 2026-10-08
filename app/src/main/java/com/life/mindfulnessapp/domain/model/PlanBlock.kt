package com.life.mindfulnessapp.domain.model

import org.json.JSONArray
import java.util.UUID

/** 守计划的场景标签：目标 / 习惯 / 生活节奏。不是独立产品入口。 */
enum class PlanScene {
    GOAL,
    HABIT,
    LIFE;

    val label: String
        get() = when (this) {
            GOAL -> "目标"
            HABIT -> "习惯"
            LIFE -> "生活"
        }

    companion object {
        fun fromStorage(value: String?): PlanScene = when (value) {
            GOAL.name -> GOAL
            HABIT.name -> HABIT
            else -> LIFE
        }
    }
}

/**
 * 日程锁 · 锁定范围。
 * - [MONITORED]：当前所有已监控 App（名单变更时自动跟随）
 * - [SPECIFIC]：仅 [PlanBlock.packageNames]
 * - [BOTH]：监控中的 ∪ [PlanBlock.packageNames]（自定义可含未监控 App）
 */
enum class PlanLockScope {
    MONITORED,
    SPECIFIC,
    BOTH;

    val label: String
        get() = when (this) {
            MONITORED -> "监控中的 App"
            SPECIFIC -> "自定义"
            BOTH -> "监控中的 + 自定义"
        }

    val includesMonitored: Boolean
        get() = this == MONITORED || this == BOTH

    val includesSpecific: Boolean
        get() = this == SPECIFIC || this == BOTH

    companion object {
        fun fromStorage(value: String?): PlanLockScope = when (value) {
            SPECIFIC.name -> SPECIFIC
            BOTH.name -> BOTH
            else -> MONITORED
        }

        /** 由编辑态两开关推导；皆关时返回 null。 */
        fun fromFlags(includeMonitored: Boolean, specificPackages: List<String>): PlanLockScope? {
            val hasSpecific = specificPackages.any { it.isNotBlank() }
            return when {
                includeMonitored && hasSpecific -> BOTH
                includeMonitored -> MONITORED
                hasSpecific -> SPECIFIC
                else -> null
            }
        }
    }
}

/**
 * 日程锁 / 守计划 · 一段按时段限制 App 的契约。
 *
 * 执行时投影为 [PeriodWindow]（message = title），与 App 级时段锁取并集。
 * [scene] / [why] 说明「为了什么」，门上露出为场景副文案。
 */
data class PlanBlock(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int = PeriodDays.EVERY_DAY,
    val enabled: Boolean = true,
    val packageNames: List<String> = emptyList(),
    val lockScope: PlanLockScope = PlanLockScope.MONITORED,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val scene: PlanScene = PlanScene.LIFE,
    val why: String = ""
) {
    init {
        require(startMinute in 0..1439) { "startMinute out of range: $startMinute" }
        require(endMinute in 0..1439) { "endMinute out of range: $endMinute" }
    }

    fun coversPackage(packageName: String, monitoredPackages: Set<String>): Boolean {
        if (packageName.isBlank()) return false
        return when (lockScope) {
            PlanLockScope.MONITORED -> packageName in monitoredPackages
            PlanLockScope.SPECIFIC -> packageName in packageNames
            PlanLockScope.BOTH ->
                packageName in monitoredPackages || packageName in packageNames
        }
    }

    fun scopeLine(appLabels: (String) -> String = { it }): String {
        val names = packageNames.map(appLabels).filter { it.isNotBlank() }
        val specificPart = when {
            names.isEmpty() -> null
            names.size <= 3 -> names.joinToString("、")
            else -> "${names.take(3).joinToString("、")} 等"
        }
        return when (lockScope) {
            PlanLockScope.MONITORED -> "锁 · 监控中的 App"
            PlanLockScope.SPECIFIC ->
                if (specificPart == null) "锁 · 自定义" else "锁 · $specificPart"
            PlanLockScope.BOTH ->
                if (specificPart == null) "锁 · 监控中的 App"
                else "锁 · 监控中的 + $specificPart"
        }
    }

    fun toPeriodWindow(): PeriodWindow = PeriodWindow(
        id = id,
        startMinute = startMinute,
        endMinute = endMinute,
        daysMask = daysMask,
        enabled = enabled,
        message = title.trim().take(PeriodLockPolicy.MESSAGE_MAX_CHARS)
    )

    fun label(): String = toPeriodWindow().label()

    fun daysLabel(): String = PeriodDays.label(daysMask)

    fun whyLine(): String? {
        val w = why.trim()
        return if (w.isEmpty()) null else "为了 · $w"
    }

    companion object {
        const val TITLE_MAX_CHARS = 40
        const val WHY_MAX_CHARS = 24

        fun templates(): List<PlanBlock> = listOf(
            PlanBlock(
                title = "夜间",
                startMinute = 23 * 60,
                endMinute = 7 * 60,
                daysMask = PeriodDays.EVERY_DAY,
                lockScope = PlanLockScope.MONITORED,
                scene = PlanScene.LIFE
            ),
            PlanBlock(
                title = "工作专注",
                startMinute = 9 * 60,
                endMinute = 12 * 60,
                daysMask = PeriodDays.WEEKDAYS,
                lockScope = PlanLockScope.SPECIFIC,
                scene = PlanScene.LIFE
            ),
            PlanBlock(
                title = "午休",
                startMinute = 13 * 60,
                endMinute = 14 * 60,
                daysMask = PeriodDays.WEEKDAYS,
                lockScope = PlanLockScope.MONITORED,
                scene = PlanScene.LIFE
            )
        )
    }
}

object PlanPackagesCodec {
    fun encode(packages: List<String>): String {
        if (packages.isEmpty()) return "[]"
        val arr = JSONArray()
        packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { arr.put(it) }
        return arr.toString()
    }

    fun decode(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i).trim()
                    if (pkg.isNotEmpty()) add(pkg)
                }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
