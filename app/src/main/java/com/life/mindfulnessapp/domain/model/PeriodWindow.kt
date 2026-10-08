package com.life.mindfulnessapp.domain.model

import java.util.UUID

/**
 * 时段锁的一段锁定窗口（可多段，互不重复、互不重叠）。
 *
 * 时间语义：
 * - **全天**：[startMinute] == [endMinute]（规范存为 0/0），当日 [daysMask] 内全程锁定
 * - **同日**：[startMinute] < [endMinute]，半开区间 [start, end)
 * - **跨午夜**：[endMinute] < [startMinute]，从 start 到次日 end
 *
 * @param id 稳定标识，供列表编辑
 * @param startMinute 起始（从 0 点起的分钟，0–1439）
 * @param endMinute 结束（0–1439）；与 start 相等表示全天
 * @param daysMask 生效日：bit0=周一 … bit6=周日；[PeriodDays.EVERY_DAY]=每天
 * @param enabled 子开关：关闭后本段不参与锁定；在**本段生效中**关闭需过解锁门槛
 * @param message 本段寄语（可选；出现在该段拦截页）
 */
data class PeriodWindow(
    val id: String = UUID.randomUUID().toString(),
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int = PeriodDays.EVERY_DAY,
    val enabled: Boolean = true,
    val message: String = ""
) {
    init {
        require(startMinute in 0..1439) { "startMinute out of range: $startMinute" }
        require(endMinute in 0..1439) { "endMinute out of range: $endMinute" }
    }

    /** 全天锁定（起止相等；展示与判定均按全天，不按跨午夜） */
    val isAllDay: Boolean get() = startMinute == endMinute

    /** 跨午夜；与 [isAllDay] 互斥 */
    val crossesMidnight: Boolean get() = endMinute < startMinute

    fun label(): String = when {
        isAllDay -> "全天"
        else -> "${formatHm(startMinute)} – ${formatHm(endMinute)}"
    }

    fun daysLabel(): String = PeriodDays.label(daysMask)

    /** 列表副行补充：跨午夜提示；全天无需 */
    fun rangeHint(): String? = when {
        isAllDay -> null
        crossesMidnight -> "跨午夜"
        else -> null
    }

    companion object {
        /** 默认：每晚 22:00–07:00 */
        fun defaultSleep(): PeriodWindow = PeriodWindow(
            startMinute = 22 * 60,
            endMinute = 7 * 60,
            daysMask = PeriodDays.EVERY_DAY,
            enabled = true
        )

        /** 全天锁定（规范存 00:00–00:00） */
        fun allDay(daysMask: Int = PeriodDays.EVERY_DAY): PeriodWindow = PeriodWindow(
            startMinute = 0,
            endMinute = 0,
            daysMask = daysMask,
            enabled = true
        )

        /** 兼容旧引用 */
        val DEFAULT_SLEEP: PeriodWindow get() = defaultSleep()

        /**
         * 规范化起止：相等 → 全天 0/0；否则原样夹紧。
         */
        fun normalizeRange(startMinute: Int, endMinute: Int): Pair<Int, Int> {
            val s = startMinute.coerceIn(0, 1439)
            val e = endMinute.coerceIn(0, 1439)
            return if (s == e) 0 to 0 else s to e
        }

        fun formatHm(minuteOfDay: Int): String {
            val m = minuteOfDay.coerceIn(0, 1439)
            val h = m / 60
            val min = m % 60
            return "%02d:%02d".format(h, min)
        }
    }
}

/** 星期位掩码（周一为最低位，与 Calendar.DAY_OF_WEEK 映射见 [PeriodDays.fromCalendar]） */
object PeriodDays {
    const val MON = 1
    const val TUE = 2
    const val WED = 4
    const val THU = 8
    const val FRI = 16
    const val SAT = 32
    const val SUN = 64
    const val EVERY_DAY = 127
    const val WEEKDAYS = MON or TUE or WED or THU or FRI
    const val WEEKENDS = SAT or SUN

    fun fromCalendar(dayOfWeek: Int): Int = when (dayOfWeek) {
        java.util.Calendar.MONDAY -> MON
        java.util.Calendar.TUESDAY -> TUE
        java.util.Calendar.WEDNESDAY -> WED
        java.util.Calendar.THURSDAY -> THU
        java.util.Calendar.FRIDAY -> FRI
        java.util.Calendar.SATURDAY -> SAT
        java.util.Calendar.SUNDAY -> SUN
        else -> EVERY_DAY
    }

    fun label(mask: Int): String = when (mask and EVERY_DAY) {
        EVERY_DAY -> "每天"
        WEEKDAYS -> "工作日"
        WEEKENDS -> "周末"
        else -> {
            val parts = buildList {
                if (mask and MON != 0) add("一")
                if (mask and TUE != 0) add("二")
                if (mask and WED != 0) add("三")
                if (mask and THU != 0) add("四")
                if (mask and FRI != 0) add("五")
                if (mask and SAT != 0) add("六")
                if (mask and SUN != 0) add("日")
            }
            if (parts.isEmpty()) "未选日" else "周${parts.joinToString("")}"
        }
    }
}
