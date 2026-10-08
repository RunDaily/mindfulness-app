package com.life.mindfulnessapp.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 总门三路教学副句：新 App 按「相对首日」递减挂载，之后只留标题。
 *
 * - 第 1 天：当日第 1～3 次开门挂副句
 * - 第 2 天：当日第 1～2 次
 * - 第 3 天：当日第 1 次
 * - 其余：不挂
 *
 * 「额度用完 / 冷却中」等状态句不受此限，始终可显示。
 */
object GatePathTeach {
    const val INTENT_SUBTITLE = "我知道要做什么"
    const val SEARCH_SUBTITLE = "我知道要搜什么"
    const val BROWSE_SUBTITLE = "我没有明确的目的"

    /** 相对首日的 dayIndex（0=首日）→ 当日最多挂几次 */
    fun showsAllowedOnDay(dayIndex: Int): Int = when (dayIndex) {
        0 -> 3
        1 -> 2
        2 -> 1
        else -> 0
    }

    fun todayYmd(nowMs: Long = System.currentTimeMillis()): String =
        DAY_FMT.format(nowMs)

    /**
     * @param firstYmd 该 App 首次见总门的自然日 yyyyMMdd
     * @param todayYmd 今天
     */
    fun dayIndex(firstYmd: String, todayYmd: String): Int {
        val first = parseDay(firstYmd) ?: return 0
        val today = parseDay(todayYmd) ?: return 0
        val diff = today.timeInMillis - first.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diff).toInt().coerceAtLeast(0)
    }

    /**
     * 本次开门是否应挂教学副句。
     * @param showsAlreadyToday 今日已展示次数（含本次之前）
     */
    fun shouldShowTeachingSubtitles(
        firstYmd: String,
        todayYmd: String,
        showsAlreadyToday: Int
    ): Boolean {
        val limit = showsAllowedOnDay(dayIndex(firstYmd, todayYmd))
        if (limit <= 0) return false
        return showsAlreadyToday < limit
    }

    private val DAY_FMT = SimpleDateFormat("yyyyMMdd", Locale.US)

    private fun parseDay(ymd: String): Calendar? {
        if (ymd.length != 8) return null
        return try {
            val cal = Calendar.getInstance()
            cal.time = DAY_FMT.parse(ymd) ?: return null
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal
        } catch (_: Exception) {
            null
        }
    }
}
