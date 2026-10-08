package com.life.mindfulnessapp.util

/**
 * 批量选 App 网格、探索排行等场景下的用量文案。
 */
object AppUsageFormat {

    fun avgDailyDurationShort(avgSeconds: Long): String {
        if (avgSeconds <= 0L) return "0分/天"
        val totalMin = (avgSeconds + 30L) / 60L
        return when {
            totalMin < 60L -> "${totalMin}分/天"
            else -> {
                val hours = totalMin / 60L
                val minutes = totalMin % 60L
                if (minutes == 0L) "${hours}时/天" else "${hours}时${minutes}分/天"
            }
        }
    }

    fun avgDailyLaunchesShort(avgLaunches: Int): String = "${avgLaunches}次/天"

    /** 探索排行 / 详情总时长：向下取整到分，与详情页同一口径 */
    fun totalDurationCompact(seconds: Long): String {
        if (seconds <= 0L) return "0分"
        val totalMin = seconds / 60L
        return when {
            totalMin < 60L -> "${totalMin}分"
            else -> {
                val hours = totalMin / 60L
                val minutes = totalMin % 60L
                if (minutes == 0L) "${hours}时" else "${hours}时${minutes}分"
            }
        }
    }

    /** 单次会话：精确到分，不足 1 分显示秒 */
    fun sessionDuration(seconds: Long): String {
        if (seconds < 60L) return "${seconds}秒"
        val totalMin = seconds / 60L
        val remSec = seconds % 60L
        return when {
            totalMin < 60L -> if (remSec == 0L) "${totalMin}分" else "${totalMin}分${remSec}秒"
            else -> {
                val hours = totalMin / 60L
                val minutes = totalMin % 60L
                if (minutes == 0L) "${hours}时" else "${hours}时${minutes}分"
            }
        }
    }

    fun clockHm(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return String.format(
            java.util.Locale.getDefault(),
            "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }
}
