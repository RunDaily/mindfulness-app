package com.life.mindfulnessapp.domain.model

/**
 * 过去 7 个完整自然日（不含今天）的系统用量汇总。
 * 用于批量选 App 页展示与排序。
 */
data class AppWeeklySystemUsage(
    val totalSeconds: Long,
    val totalLaunches: Int
) {
    /** 日历日均：总用量 ÷ 7 */
    val avgDailySeconds: Long get() = totalSeconds / DAYS

    /** 日历日均打开次数（四舍五入到整数） */
    val avgDailyLaunches: Int get() = ((totalLaunches * 2 + DAYS) / (DAYS * 2))

    companion object {
        const val DAYS = 7
        val Zero = AppWeeklySystemUsage(0L, 0)
    }
}

enum class BatchPickSortMode {
    /** 近 7 日日均使用时长（降序） */
    Duration,
    /** 近 7 日日均打开次数（降序） */
    Launches
}

data class BatchPickAppRow(
    val app: AppInfo,
    val usage: AppWeeklySystemUsage?
)
