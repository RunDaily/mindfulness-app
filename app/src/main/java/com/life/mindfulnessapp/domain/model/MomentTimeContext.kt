package com.life.mindfulnessapp.domain.model

import java.util.Calendar

/**
 * 拦截页时间锚：日期 · 时辰 · 今年第几天。
 * 给搜索型意图门「此刻」语境，不当成工具控件。
 */
data class MomentTimeContext(
    /** 如 9月9日 周三 */
    val dateLine: String,
    /** 如 申时 */
    val shiChenName: String,
    /** 如 15–17点 */
    val shiChenRange: String,
    /** 今年第几天（1..366） */
    val dayOfYear: Int,
    /** 今年总天数 */
    val daysInYear: Int,
) {
    val yearProgress: Float
        get() = if (daysInYear <= 0) 0f else (dayOfYear.toFloat() / daysInYear).coerceIn(0f, 1f)

    /** 日期 · 时辰 */
    val headerLine: String = "$dateLine · $shiChenName"

    /** 今年第 N 天 */
    val yearDayLine: String = "今年第 $dayOfYear 天"
}

object MomentTimeContexts {
    private val WEEKDAYS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    /** 十二时辰：子丑寅卯辰巳午未申酉戌亥，各两小时 */
    private val SHI_CHEN = arrayOf(
        "子时" to "23–1点",
        "丑时" to "1–3点",
        "寅时" to "3–5点",
        "卯时" to "5–7点",
        "辰时" to "7–9点",
        "巳时" to "9–11点",
        "午时" to "11–13点",
        "未时" to "13–15点",
        "申时" to "15–17点",
        "酉时" to "17–19点",
        "戌时" to "19–21点",
        "亥时" to "21–23点",
    )

    fun now(calendar: Calendar = Calendar.getInstance()): MomentTimeContext {
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val weekday = WEEKDAYS[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val shiIndex = shiChenIndex(hour, minute)
        val (name, range) = SHI_CHEN[shiIndex]
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val daysInYear = calendar.getActualMaximum(Calendar.DAY_OF_YEAR)
        return MomentTimeContext(
            dateLine = "${month}月${day}日 $weekday",
            shiChenName = name,
            shiChenRange = range,
            dayOfYear = dayOfYear,
            daysInYear = daysInYear,
        )
    }

    /** 23:00 起算子时；每两小时一辰 */
    internal fun shiChenIndex(hour: Int, minute: Int = 0): Int {
        val totalMinutes = hour * 60 + minute
        // 子时 23:00–00:59 → index 0；丑 1:00–2:59 → 1 …
        val shifted = (totalMinutes + 60) % (24 * 60) // 对齐到 23:00=0
        return (shifted / 120).coerceIn(0, 11)
    }
}
