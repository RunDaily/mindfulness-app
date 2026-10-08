package com.life.mindfulnessapp.domain.model

import java.util.UUID

/**
 * 一天的五个时段。事项按开始时间归入；无开始时间时用添加时点选的时段。
 *
 * 区间半开 [startMinute, endMinuteExclusive)。
 */
enum class DaySlot(
    val label: String,
    val startMinute: Int,
    val endMinuteExclusive: Int
) {
    MORNING("早晨", 5 * 60, 8 * 60),
    FORENOON("上午", 8 * 60, 12 * 60),
    NOON("中午", 12 * 60, 14 * 60),
    AFTERNOON("下午", 14 * 60, 18 * 60),
    EVENING("晚上", 18 * 60, 24 * 60);

    fun rangeLabel(): String {
        val end = if (endMinuteExclusive >= 24 * 60) "24:00" else PeriodWindow.formatHm(endMinuteExclusive)
        return "${PeriodWindow.formatHm(startMinute)}–$end"
    }

    companion object {
        fun fromStorage(value: String?): DaySlot =
            entries.find { it.name == value } ?: FORENOON

        /** 开始时间归入时段；0:00–05:00 归早晨。 */
        fun forStartMinute(startMinute: Int): DaySlot {
            val m = startMinute.coerceIn(0, 1439)
            return entries.find { m >= it.startMinute && m < it.endMinuteExclusive }
                ?: MORNING
        }

        fun containingNow(minuteOfDay: Int = currentMinuteOfDay()): DaySlot =
            forStartMinute(minuteOfDay)

        fun currentMinuteOfDay(): Int {
            val cal = java.util.Calendar.getInstance()
            return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        }
    }
}

/**
 * 日程事项：某一自然日上的一件事。
 *
 * [startMinute] / [endMinute] 可空；有开始时间时 [slot] 应与开始时间一致。
 * 跨时段事项只在开始时段展示一行。
 */
data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val dayKey: String,
    val title: String,
    val note: String = "",
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val slot: DaySlot = DaySlot.FORENOON,
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun resolvedSlot(): DaySlot =
        startMinute?.let { DaySlot.forStartMinute(it) } ?: slot

    companion object {
        const val TITLE_MAX_CHARS = 40
        const val NOTE_MAX_CHARS = 48
    }
}

/** 某日某时段在列表里的一组事项 */
data class ScheduleSlotSection(
    val slot: DaySlot,
    val items: List<ScheduleItem>,
    val isCurrent: Boolean
)
